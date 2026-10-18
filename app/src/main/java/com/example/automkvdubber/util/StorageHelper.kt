package com.example.automkvdubber.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

data class FileInfo(
    val displayName: String,
    val sizeFormatted: String,
    val sizeBytes: Long
)

data class SafInputHandle(
    val path: String,
    val pfd: ParcelFileDescriptor? = null,
    val tempFile: File? = null
) : AutoCloseable {
    override fun close() {
        try { pfd?.close() } catch (_: Throwable) {}
        try { tempFile?.delete() } catch (_: Throwable) {}
    }
}

object StorageHelper {

    private const val FOLDER_NAME = "Download/FileTranslato"

    /**
     * Safely retrieves the file name and size in a single query with explicit projection.
     */
    fun getFileInfo(context: Context, uri: Uri): FileInfo {
        var name = "movie_${System.currentTimeMillis()}.mkv"
        var sizeBytes = 0L

        try {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        cursor.getString(nameIdx)?.let { if (it.isNotBlank()) name = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            // Fallback: try uri last path segment
            uri.lastPathSegment?.let { segment ->
                val clean = segment.substringAfterLast("/")
                if (clean.isNotBlank()) name = clean
            }
        }

        return FileInfo(
            displayName = name,
            sizeFormatted = if (sizeBytes > 0) formatBytes(sizeBytes) else "অজ্ঞাত সাইজ",
            sizeBytes = sizeBytes
        )
    }

    fun getFileName(context: Context, uri: Uri): String {
        return getFileInfo(context, uri).displayName
    }

    fun getFileSizeFormatted(context: Context, uri: Uri): String {
        return getFileInfo(context, uri).sizeFormatted
    }

    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Obtains a robust input handle for FFmpeg:
     * 1. Direct POSIX file descriptor (/proc/self/fd/<fd>) - Zero memory/disk copy
     * 2. FFmpegKit SAF parameter
     * 3. Safe streaming temp copy fallback
     */
    fun obtainInputHandle(context: Context, uri: Uri, tempDir: File): SafInputHandle {
        // Strategy 1: ParcelFileDescriptor via /proc/self/fd/<fd>
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val fdPath = "/proc/self/fd/${pfd.fd}"
                val testFile = File(fdPath)
                if (testFile.exists() && testFile.canRead()) {
                    return SafInputHandle(path = fdPath, pfd = pfd)
                }
                // Even if File.canRead() returns false on some SELinux policies, pass /proc/self/fd
                return SafInputHandle(path = fdPath, pfd = pfd)
            }
        } catch (_: Throwable) {}

        // Strategy 2: FFmpegKit SAF protocol
        try {
            val safPath = FFmpegKitConfig.getSafParameterForRead(context, uri)
            if (!safPath.isNullOrBlank()) {
                return SafInputHandle(path = safPath)
            }
        } catch (_: Throwable) {}

        // Strategy 3: Streaming copy to temp file in cacheDir
        val tempFile = File(tempDir, "input_stream_${System.currentTimeMillis()}.mkv")
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            FileOutputStream(tempFile).use { outStream ->
                inStream.copyTo(outStream, 64 * 1024)
            }
        }
        return SafInputHandle(path = tempFile.absolutePath, tempFile = tempFile)
    }

    /**
     * Saves a local file into the public Downloads/FileTranslato folder using Scoped Storage on Android 10-15+
     * or direct filesystem on Android 9 and below.
     */
    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String
    ): Uri? {
        if (!sourceFile.exists()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_NAME)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues) ?: return null

            try {
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream, 64 * 1024)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
                itemUri
            } catch (t: Throwable) {
                resolver.delete(itemUri, null, null)
                t.printStackTrace()
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "FileTranslato").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(targetDir, targetFileName)
            try {
                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream, 64 * 1024)
                    }
                }
                Uri.fromFile(targetFile)
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }

    fun createShareIntent(context: Context, uri: Uri, mimeType: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(context: Context, uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
