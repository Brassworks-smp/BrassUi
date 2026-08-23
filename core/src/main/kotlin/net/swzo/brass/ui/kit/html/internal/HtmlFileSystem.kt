package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.plugin.filesystem.UltralightFileSystem
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Ultralight's view of the local filesystem, ported from UltralightFabric. Plain `file://` reads go
 * straight to the JVM's NIO; there is no sandboxing here, so a page can read whatever the process can
 * read (the same trust model the original had).
 */
internal class HtmlFileSystem : UltralightFileSystem {

    private val logger = java.util.logging.Logger.getLogger("brassui.html.fs")

    private var nextHandle: Long = 0
    private val openFiles = HashMap<Long, FileChannel>()

    override fun fileExists(path: String): Boolean {
        val real = pathOf(path) ?: return false
        return Files.exists(real)
    }

    override fun getFileSize(handle: Long): Long {
        val channel = openFiles[handle] ?: return -1
        return try {
            channel.size()
        } catch (e: IOException) {
            logger.log(java.util.logging.Level.WARNING, "Failed to get size of file handle " + handle, e)
            -1
        }
    }

    override fun getFileMimeType(path: String): String? {
        val real = pathOf(path) ?: return null
        return try {
            Files.probeContentType(real)
        } catch (e: IOException) {
            logger.log(java.util.logging.Level.WARNING, "Failed to probe mime type of " + path, e)
            null
        }
    }

    override fun openFile(path: String, openForWriting: Boolean): Long {
        val real = pathOf(path) ?: return UltralightFileSystem.INVALID_FILE_HANDLE
        return try {
            val channel = FileChannel.open(
                real,
                if (openForWriting) StandardOpenOption.WRITE else StandardOpenOption.READ,
            )
            if (nextHandle == UltralightFileSystem.INVALID_FILE_HANDLE) nextHandle = UltralightFileSystem.INVALID_FILE_HANDLE + 1
            val handle = nextHandle++
            openFiles[handle] = channel
            handle
        } catch (e: IOException) {
            logger.log(java.util.logging.Level.WARNING, "Failed to open " + path, e)
            UltralightFileSystem.INVALID_FILE_HANDLE
        }
    }

    override fun closeFile(handle: Long) {
        val channel = openFiles.remove(handle) ?: return
        try {
            channel.close()
        } catch (e: IOException) {
            logger.log(java.util.logging.Level.WARNING, "Failed to close file handle " + handle, e)
        }
    }

    override fun readFromFile(handle: Long, data: ByteBuffer, length: Long): Long {
        val channel = openFiles[handle] ?: return -1
        if (length > Int.MAX_VALUE) {
            UnsupportedOperationException("ultralight can only read < 2GB files through the Java bridge")
                .printStackTrace()
            return -1
        }
        return try {
            channel.read(data.slice().limit(length.toInt()) as ByteBuffer).toLong()
        } catch (e: IOException) {
            logger.log(java.util.logging.Level.WARNING, "Failed to read " + length + " bytes from handle " + handle, e)
            -1
        }
    }

    private fun pathOf(path: String): Path? = try {
        Paths.get(path)
    } catch (e: InvalidPathException) {
        null
    }
}
