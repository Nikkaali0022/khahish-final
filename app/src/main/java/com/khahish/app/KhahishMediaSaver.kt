package com.khahish.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.Locale

internal object KhahishMediaSaver {
    fun save(context: Context, source: Uri, mediaType: String, suggestedName: String, onResult: (Result<String>) -> Unit) {
        Thread {
            val result = runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(source) ?: guessMime(source, mediaType)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: source.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                    ?: if (mediaType == "video") "mp4" else if (mediaType == "image") "jpg" else "bin"
                val base = suggestedName.substringBeforeLast('.', suggestedName).replace(Regex("[^A-Za-z0-9._-]"), "_").take(70).ifBlank { "Khahish_" + System.currentTimeMillis() }
                val displayName = if (base.endsWith(".$extension", true)) base else "$base.$extension"
                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                val input = when (source.scheme?.lowercase(Locale.ROOT)) {
                    "http", "https" -> URL(source.toString()).openStream()
                    "file" -> FileInputStream(File(source.path ?: error("File path unavailable")))
                    else -> resolver.openInputStream(source) ?: error("Attachment is not available on this device")
                }
                input.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val collection = when {
                            isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }
                        val path = when {
                            isImage -> Environment.DIRECTORY_PICTURES + "/Khahish"
                            isVideo -> Environment.DIRECTORY_MOVIES + "/Khahish"
                            isAudio -> Environment.DIRECTORY_MUSIC + "/Khahish"
                            else -> Environment.DIRECTORY_DOWNLOADS + "/Khahish"
                        }
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val destination = resolver.insert(collection, values) ?: error("Android could not create the destination file")
                        try {
                            resolver.openOutputStream(destination, "w")?.use { output -> stream.copyTo(output) } ?: error("Could not write the saved file")
                            resolver.update(destination, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                            destination.toString()
                        } catch (error: Exception) {
                            resolver.delete(destination, null, null)
                            throw error
                        }
                    } else {
                        val type = when { isImage -> Environment.DIRECTORY_PICTURES; isVideo -> Environment.DIRECTORY_MOVIES; isAudio -> Environment.DIRECTORY_MUSIC; else -> Environment.DIRECTORY_DOWNLOADS }
                        val directory = File(context.getExternalFilesDir(type) ?: context.filesDir, "Khahish")
                        if (!directory.exists() && !directory.mkdirs()) error("Could not create the save folder")
                        val destination = File(directory, displayName)
                        destination.outputStream().use { output -> stream.copyTo(output) }
                        destination.absolutePath
                    }
                }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(result) }
        }.start()
    }

    private fun guessMime(uri: Uri, mediaType: String): String {
        if (mediaType == "image") return "image/jpeg"
        if (mediaType == "video") return "video/mp4"
        if (mediaType == "audio") return "audio/mp4"
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
