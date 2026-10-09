package com.example.telegram.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object TelegramFileUtil {

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } ?: return ""
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getOrPrepareLocalFilePath(context: Context, uriString: String, fileName: String): File? {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (file.exists()) return file
        }

        // Try getting path from MediaStore column
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val path = cursor.getString(columnIndex)
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) return file
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore Scoped Storage exception on newer Android versions
        }

        // If direct file path not accessible, stream into a cache file for TDLib
        try {
            val cacheFolder = File(context.cacheDir, "tdlib_upload").apply { if (!exists()) mkdirs() }
            val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(cacheFolder, "${System.currentTimeMillis()}_$sanitizedName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
