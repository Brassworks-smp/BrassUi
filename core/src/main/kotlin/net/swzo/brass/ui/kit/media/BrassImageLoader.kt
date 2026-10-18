package net.swzo.brass.ui.kit.media

import net.swzo.brass.ui.kit.media.BrassImageLoader.Entry
import net.swzo.brass.ui.kit.media.BrassImageLoader.FAILURE_TTL_MS
import net.swzo.brass.ui.kit.media.BrassImageLoader.load
import gg.essential.elementa.components.UIImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.awt.image.BufferedImage
import java.security.MessageDigest
import java.util.WeakHashMap
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * Shared fetch-and-decode for [BrassImage]: one request per source for the life of the process, on
 * a small pool of daemon threads.
 * Kept separate from the widget because the cache has to outlive it - a list that rebuilds its rows
 * every time a tab is clicked would otherwise re-download every icon.
 *
 * Remote (`https`) images are also kept on disk in [diskDir], so the next launch shows them at once:
 * the saved copy is served first and checked against the server in the background (ETag /
 * Last-Modified); a changed image replaces it and bumps [generation], which redraws live widgets.
 */
object BrassImageLoader {

    const val MAX_BYTES = 4L * 1024 * 1024

    private val TIMEOUT: Duration = Duration.ofSeconds(10)

    private val threads = AtomicInteger()

    /**
     * Daemon threads, so a pending download can never keep the game from exiting, and a small fixed
     * pool so a screenful of icons cannot open a hundred sockets at once.
     */
    private val pool = Executors.newFixedThreadPool(4) {
        Thread(it, "brassui-image-${threads.incrementAndGet()}").apply { isDaemon = true }
    }

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    const val MAX_ENTRIES = 256

    private val FAILURE_TTL_MS = 30_000L

    private class Entry(val future: CompletableFuture<BufferedImage?>, val at: Long)

    /**
     * Decoded images by URL, in access order so the eldest entry is the one to drop.
     * Access-ordered LinkedHashMap rather than a ConcurrentHashMap: the map is only touched
     * from [load] (render thread) and the eviction hook, so a plain synchronized map is both
     * sufficient and the only structure that gives LRU ordering for free. The *futures* inside it
     * still complete on the worker pool, which is where the concurrency actually is.
     */
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * Fetch and decode [url], or return the in-flight / completed future for it.
     * Never throws and never completes exceptionally: a failure completes with null, which the widget
     * renders as its placeholder. A UI that explodes because a CDN is down is worse than one with a
     * gap in it.
     * A cached failure expires after [FAILURE_TTL_MS] and is retried. Remembering one forever meant a
     * single network blip permanently blanked that image for the rest of the session, with no way
     * short of restarting to recover.
     */
    @Synchronized
    fun load(url: String): CompletableFuture<BufferedImage?> {
        val now = System.currentTimeMillis()
        cache[url]?.let { entry ->
            val failed = entry.future.isDone && entry.future.getNow(null) == null
            if (!failed || now - entry.at < FAILURE_TTL_MS) return entry.future
            cache.remove(url)
        }
        val future = CompletableFuture.supplyAsync({ fetch(url) }, pool)
        cache[url] = Entry(future, now)
        return future
    }

    @Synchronized
    fun clear() {
        cache.clear()
        textures.clear()
    }

    /**
     * Where remote images are saved between launches; null turns the disk cache off. The NeoForge mod
     * points it into the game folder (`cache/brassui/images`); elsewhere it is relative to the working
     * directory unless the `brassui.imageCache` system property names another place.
     */
    @Volatile
    var diskDir: Path? = runCatching { Path.of(System.getProperty("brassui.imageCache") ?: "cache/brassui/images") }.getOrNull()

    /** The disk cache is trimmed to this size (oldest first) once per launch. */
    const val MAX_DISK_BYTES = 64L * 1024 * 1024

    /** Bumped whenever a cached image is replaced by a newer one from its server. */
    @Volatile
    var generation: Int = 0
        private set

    /**
     * One uploaded texture per decoded image, shared by every widget that shows it, so a list that
     * rebuilds its rows (or a page that is rebuilt on a tab switch) draws at once instead of uploading
     * again. Keyed by the image's identity and weak, so a texture goes when its image leaves [cache].
     * Render thread only.
     */
    private val textures = WeakHashMap<BufferedImage, UIImage>()

    /** The shared texture for [image] (render thread). */
    fun texture(image: BufferedImage): UIImage = textures.getOrPut(image) {
        UIImage(CompletableFuture.completedFuture(image)).also {
            // Icons are small and land on the pixel grid; NEAREST keeps them crisp rather than smeared
            // at the scales a Minecraft GUI runs at.
            it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
            it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
        }
    }

    @Volatile
    private var trimmed = false

    /**
     * Supported sources:
     * - `https://...` - remote image (the original behaviour; plain `http` is still rejected).
     * - `file:///…` or a plain filesystem path (`/absolute/path.png`, `relative/icon.png`) - a
     *   local image file.
     * - `:…` (a bare path that is not a file also falls back to it) - a Java classpath resource,
     *   so a mod or app can ship images inside its own jar (`:/assets/mod/icon.png`).
     *
     * All sources share the same size cap and decoded-image cache. A file whose bytes change on
     * disk after the first load keeps the cached copy, exactly like a URL that changes server-side.
     */
    private fun fetch(url: String): BufferedImage? = runCatching {
        val bytes = when {
            url.startsWith(":") -> readResource(url.substring(1))
            url.startsWith("file:", ignoreCase = true) -> {
                val uri = URI.create(url)
                if (uri.scheme?.equals("file", ignoreCase = true) != true) return@runCatching null
                readPath(Path.of(uri))
            }
            url.startsWith("https:", ignoreCase = true) -> remoteBytes(url)
            else -> readPathOrResource(url)
        }
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching null

        // ImageIO returns null rather than throwing for a format it has no reader for - WebP, most
        // often - so this is the failure path for those, not an exception.
        ByteArrayInputStream(bytes).use(ImageIO::read)
    }.getOrNull()

    /** Read a classpath resource (`classpath:assets/...` or `classpath:/assets/...`). */
    private fun readResource(name: String): ByteArray? {
        val normalized = name.removePrefix("/")
        val loader = BrassImageLoader::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
        return loader.getResourceAsStream(normalized)?.use { it.readBytes() }
    }

    private fun readPath(path: Path): ByteArray? = runCatching {
        if (!Files.isRegularFile(path)) null
        else if (Files.size(path) > MAX_BYTES) null
        else Files.readAllBytes(path)
    }.getOrNull()

    /** Bare path without a scheme: try the filesystem first, then fall back to a classpath resource. */
    private fun readPathOrResource(url: String): ByteArray? {
        val asFile = runCatching { readPath(Path.of(url)) }.getOrNull()
        if (asFile != null) return asFile
        return readResource(url)
    }

    /** A remote image: the copy saved on disk if there is one (checked again in the background), else the network. */
    private fun remoteBytes(url: String): ByteArray? {
        val saved = diskFiles(url)?.let { (img, _) -> readPath(img) }
        if (saved != null && saved.isNotEmpty()) {
            pool.execute { revalidate(url) }
            return saved
        }
        val fetched = fetchRemote(URI.create(url), null) ?: return null
        fetched.bytes?.let { save(url, it, fetched.validator) }
        return fetched.bytes
    }

    /** Asks the server whether the saved copy of [url] is still current; a newer image replaces it. */
    private fun revalidate(url: String) {
        val (_, meta) = diskFiles(url) ?: return
        val validator = runCatching { Files.readString(meta) }.getOrNull()
        val fetched = fetchRemote(URI.create(url), validator) ?: return
        val bytes = fetched.bytes ?: return // 304: still current
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return
        val image = runCatching { ByteArrayInputStream(bytes).use(ImageIO::read) }.getOrNull() ?: return
        save(url, bytes, fetched.validator)
        synchronized(this) { cache[url] = Entry(CompletableFuture.completedFuture(image), System.currentTimeMillis()) }
        generation++
    }

    /** The saved image and its validator file for [url], or null with the disk cache off. */
    private fun diskFiles(url: String): Pair<Path, Path>? {
        val dir = diskDir ?: return null
        val key = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return dir.resolve("$key.img") to dir.resolve("$key.meta")
    }

    private fun save(url: String, bytes: ByteArray, validator: String) {
        val (img, meta) = diskFiles(url) ?: return
        runCatching {
            Files.createDirectories(img.parent)
            trimDisk(img.parent)
            // Written next to it and moved into place, so a half-written file is never served.
            val tmp = Files.createTempFile(img.parent, "dl", ".tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, img, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Files.writeString(meta, validator)
        }
    }

    /** Once per launch: drops the oldest saved images while the folder is over [MAX_DISK_BYTES]. */
    private fun trimDisk(dir: Path) {
        if (trimmed) return
        trimmed = true
        runCatching {
            val files = Files.list(dir).use { s -> s.filter { it.toString().endsWith(".img") }.toList() }
                .sortedBy { Files.getLastModifiedTime(it).toMillis() }
            var total = files.sumOf { Files.size(it) }
            for (f in files) {
                if (total <= MAX_DISK_BYTES) break
                total -= Files.size(f)
                Files.deleteIfExists(f)
                Files.deleteIfExists(f.resolveSibling(f.fileName.toString().removeSuffix(".img") + ".meta"))
            }
        }
    }

    /** bytes null means "not modified"; validator is the response's ETag / Last-Modified (one per line). */
    private class Fetched(val bytes: ByteArray?, val validator: String)

    private fun fetchRemote(uri: URI, validator: String?): Fetched? = runCatching {
        // HTTPS only. This widget exists to pull images from the open internet, and doing that over
        // plaintext would let anything on the path swap what the UI displays.
        if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching null
        val request = HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("User-Agent", USER_AGENT)
        validator?.lines()?.let { v ->
            v.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let { request.header("If-None-Match", it) }
            v.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { request.header("If-Modified-Since", it) }
        }
        val response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        val headers = response.headers()
        val next = (headers.firstValue("ETag").orElse("")) + "\n" + headers.firstValue("Last-Modified").orElse("")
        when (response.statusCode()) {
            304 -> Fetched(null, validator ?: next)
            200 -> Fetched(response.body(), next)
            else -> null
        }
    }.getOrNull()

    private const val USER_AGENT = "brassui (BrassSync UI toolkit)"
}
