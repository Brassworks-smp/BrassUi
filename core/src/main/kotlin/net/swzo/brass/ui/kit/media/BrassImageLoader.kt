package net.swzo.brass.ui.kit.media

import net.swzo.brass.ui.kit.media.BrassImageLoader.Entry
import net.swzo.brass.ui.kit.media.BrassImageLoader.FAILURE_TTL_MS
import net.swzo.brass.ui.kit.media.BrassImageLoader.load
import java.awt.image.BufferedImage
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
 * Shared fetch-and-decode for [BrassImage]: one request per URL for the life of the process, on a
 * small pool of daemon threads.
 *
 * Kept separate from the widget because the cache has to outlive it - a list that rebuilds its rows
 * every time a tab is clicked would otherwise re-download every icon.
 */
object BrassImageLoader {

    /** Largest response accepted, in bytes. An icon is a few KB; anything huge is a mistake or a trap. */
    const val MAX_BYTES = 4L * 1024 * 1024

    /** How long to wait for a response before giving up. */
    private val TIMEOUT: Duration = Duration.ofSeconds(10)

    private val threads = AtomicInteger()

    /**
     * Daemon threads, so a pending download can never keep the game from exiting, and a small fixed
     * pool so a screenful of icons cannot open a hundred sockets at once.
     */
    private val pool = Executors.newFixedThreadPool(
        4,
        ThreadFactory { r ->
            Thread(r, "brassui-image-${threads.incrementAndGet()}").apply { isDaemon = true }
        },
    )

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /**
     * Largest number of URLs kept. Beyond this the least recently *requested* entry is evicted.
     *
     * The cache used to be unbounded, which is fine for a fixed set of mod icons and wrong for
     * anything that shows user- or server-supplied URLs: a long-lived client browsing a big list
     * would hold every image it had ever seen, decoded, for the process lifetime.
     */
    const val MAX_ENTRIES = 256

    /** How long a *failure* is remembered before the URL may be tried again. */
    private val FAILURE_TTL_MS = 30_000L

    private class Entry(val future: CompletableFuture<BufferedImage?>, val at: Long)

    /**
     * Decoded images by URL, in access order so the eldest entry is the one to drop.
     *
     * Access-ordered [LinkedHashMap] rather than a [ConcurrentHashMap]: the map is only touched
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
     *
     * Never throws and never completes exceptionally: a failure completes with null, which the widget
     * renders as its placeholder. A UI that explodes because a CDN is down is worse than one with a
     * gap in it.
     *
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

    /** Drop everything, so a long-running client is not holding decoded images forever. */
    @Synchronized
    fun clear() = cache.clear()

    private fun fetch(url: String): BufferedImage? = runCatching {
        val uri = URI.create(url)
        // HTTPS only. This widget exists to pull images from the open internet, and doing that over
        // plaintext would let anything on the path swap what the UI displays.
        if (!uri.scheme.equals("https", ignoreCase = true)) return null

        val response = client.send(
            HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        if (response.statusCode() != 200) return null

        val bytes = response.body()
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BYTES) return null

        // ImageIO returns null rather than throwing for a format it has no reader for - WebP, most
        // often - so this is the failure path for those, not an exception.
        ByteArrayInputStream(bytes).use(ImageIO::read)
    }.getOrNull()

    private const val USER_AGENT = "brassui (BrassSync UI toolkit)"
}
