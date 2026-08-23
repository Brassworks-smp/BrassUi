package net.swzo.brass.ui.kit.html.internal

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

/**
 * The Ultralight native engine is not a Maven artifact — it is a per-OS zip of the C++ engine
 * (`libUltralight.dylib`/`.so`/`.dll`, WebCore, ICU data, a CA bundle) that must be downloaded once
 * and extracted next to the JVM. This mirrors the original LiquidBounce/UltralightFabric bootstrap
 * with brassui's twists:
 *
 *  - **Arch-aware**: the download URL names the OS *and* the architecture. Ultralight only publishes
 *    x64 bundles, so on a native-arm64 JVM this fails by design — but a host that builds or mirrors
 *    arm64 natives just points [customUrl] at them (the `{os}`/`{arch}` placeholders are filled in)
 *    or drops them into [localDirOverride], and nothing else changes.
 *  - **A pre-placed directory wins**: if `bin/` already contains the natives (`brassui.html.resourcesDir`),
 *    the download is skipped entirely — an offline box, a mod that ships the natives in its jar, or a
 *    CI pipeline that pre-fetched them all hand them over.
 *
 * ### Which machines get HTML, today
 * Windows and Linux are essentially all x64. On Apple Silicon the story is the JVM's: under Rosetta
 * `os.arch` reports x86_64 and the x64 bundle loads; a native arm64 JVM (Minecraft's bundled JRE is
 * now arm64, and the desktop app on a modern Mac JVM is too) cannot load x64 dylibs, and that path
 * needs an arm64 bundle that Ultralight does not publish — the engine then reports unavailable with a
 * message saying exactly that. See ARCHITECTURE.md's HTML section for how to produce one.
 */
internal object HtmlResources {

    private const val LIBRARY_VERSION = "0.4.12"
    private const val ENGINE_VERSION = "0.46"

    /** Everything lives under this folder. `bin` gets the natives, `cache`/`resources` feed Ultralight. */
    var rootDir: File = File(
        System.getProperty("brassui.html.dir") ?: File(System.getProperty("user.home"), ".brassui").absolutePath,
        "ultralight",
    )
        internal set

    /**
     * When set, [rootDir] points straight here and no download happens: the folder must contain
     * `bin/` (and ideally `resources/`). `brassui.html.resourcesDir`.
     */
    private val localDirOverride: String? = System.getProperty("brassui.html.resourcesDir")

    /**
     * Download URL. May contain `{os}` and `{arch}` placeholders; defaults to the public
     * UltralightFabric bundle (x64 only). `brassui.html.resourcesUrl`.
     */
    private val customUrl: String? = System.getProperty("brassui.html.resourcesUrl")

    val binDir: File get() = File(rootDir, "bin")

    internal val resourcesDir: File get() = File(rootDir, "resources")
    internal val cacheDir: File get() = File(rootDir, "cache")
    private val versionFile: File get() = File(rootDir, "VERSION")

    private val osName = System.getProperty("os.name").lowercase()
    private val os: String
        get() = when {
            "win" in osName -> "win"
            "mac" in osName || "darwin" in osName -> "mac"
            "nix" in osName || "nux" in osName || "aix" in osName -> "linux"
            else -> error("unsupported operating system for the Ultralight natives: $osName")
        }

    /** "x64" or "arm64" — what goes in the download URL's `{arch}` slot. */
    private val arch: String
        get() = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            else -> "x64"
        }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    init {
        // A local bundle is authoritative: point straight at it, no download, no version gate.
        if (localDirOverride != null) rootDir = File(localDirOverride)
    }

    fun isReady(): Boolean = binDir.exists() && binDir.listFiles().isNullOrEmpty().not()

    /** The architecture the engine will actually be loaded with (mirrors what the JVM reports). */
    fun archLabel(): String = arch

    /**
     * Ensure the natives are present, downloading and extracting them if not. Throws on failure; the
     * caller turns that into an "unavailable" engine rather than a crash.
     */
    fun ensure() {
        if (isReady()) {
            // A hand-provided bundle skips the version check entirely; a downloaded one re-checks so a
            // binding bump forces a refresh of the engine binaries it drives.
            if (localDirOverride != null || versionFile.readTextOrNull() == "$LIBRARY_VERSION/$ENGINE_VERSION") return
        }

        // A fresh bundle replaces the whole folder so a partial previous download cannot linger.
        if (rootDir.exists()) rootDir.deleteRecursively()
        rootDir.mkdirs()

        val url = (customUrl ?: "https://cloud.liquidbounce.net/LiquidBounce/ultralight_resources/$ENGINE_VERSION/$os-$arch.zip")
            .replace("{os}", os)
            .replace("{arch}", arch)

        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body()
        if (body.isEmpty()) error("ultralight natives download returned an empty body from $url")

        val zip = File(rootDir, "resources.zip").apply { writeBytes(body) }
        extract(zip, rootDir)
        zip.delete()

        versionFile.writeText("$LIBRARY_VERSION/$ENGINE_VERSION")
    }

    private fun extract(zipFile: File, folder: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(folder, entry.name)
                    File(target.parent).mkdirs()
                    FileOutputStream(target).use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
            zip.closeEntry()
        }
    }

    private fun File.readTextOrNull(): String? = if (exists()) readText() else null
}
