package net.swzo.brass.ui.desktop.net

import net.swzo.brass.ui.kit.net.BrassActionSets
import java.io.File
import java.net.URI
import java.util.jar.JarFile

/**
 * The desktop end of action-set discovery: scan the classpath for `@BrassActionSet` classes and let
 * [BrassActionSets.load] initialise them (which is what registers their actions).
 *
 * Where NeoForge reuses FML's scan data, the desktop app has no mod loader, so it scans its own
 * classpath - the build output directories during `./gradlew :desktop:run`, the shadow jar in the
 * packaged app. To avoid initialising every class on the classpath, candidate classes are found by
 * searching each `.class` file's bytes for the annotation's descriptor string first; only hits are
 * actually loaded, and [BrassActionSets.load] re-verifies before touching anything.
 */
object DesktopNetDiscovery {

    private val MARKER = "Lnet/swzo/brass/ui/kit/net/BrassActionSet;".toByteArray(Charsets.UTF_8)

    /** Scan the classpath and load every action set found; returns how many were loaded. */
    fun discoverAndLoad(): Int = BrassActionSets.loadAll(classNames())

    private fun classNames(): List<String> {
        val names = LinkedHashSet<String>()
        for (root in classpathRoots()) {
            val file = runCatching { File(URI.create(root)) }.getOrNull()
                ?: runCatching { File(root) }.getOrNull()
                ?: continue
            when {
                file.isDirectory -> scanDirectory(file, names)
                file.isFile && file.name.endsWith(".jar") -> scanJar(file, names)
            }
        }
        return names.toList()
    }

    private fun scanDirectory(dir: File, out: MutableSet<String>) {
        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") }
            .forEach { classFile ->
                if (contains(classFile.readBytes(), MARKER)) {
                    out += classFile.relativeTo(dir).path
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                }
            }
    }

    private fun scanJar(jar: File, out: MutableSet<String>) {
        JarFile(jar).use { jarFile ->
            jarFile.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .forEach { entry ->
                    jarFile.getInputStream(entry).use { input ->
                        if (contains(input.readBytes(), MARKER)) {
                            out += entry.name.removeSuffix(".class").replace('/', '.')
                        }
                    }
                }
        }
    }

    /** Naive byte search - class files are small, so a windowed scan is not worth the complexity. */
    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (start in 0..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private fun classpathRoots(): List<String> {
        val roots = LinkedHashSet<String>()
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .forEach { roots += it }
        // The classloader's own locations cover setups where java.class.path is not the whole story
        // (IDE run configs, a custom launcher). Deduped against the system classpath above.
        listOf(DesktopNetDiscovery::class.java, BrassActionSets::class.java).forEach { clazz ->
            clazz.protectionDomain?.codeSource?.location?.toString()?.let { roots += it }
        }
        return roots.toList()
    }
}
