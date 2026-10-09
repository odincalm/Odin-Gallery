package com.example.telegram.security

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

object TelegramKeyManager {
    private const val KEY_FILE_NAME = ".td_db.key"
    private const val KEY_SIZE_BYTES = 32

    @Synchronized
    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        val keyFile = File(context.filesDir, KEY_FILE_NAME)
        if (keyFile.exists() && keyFile.length() == KEY_SIZE_BYTES.toLong()) {
            return keyFile.readBytes()
        }

        val secureRandom = SecureRandom()
        val newKey = ByteArray(KEY_SIZE_BYTES)
        secureRandom.nextBytes(newKey)

        FileOutputStream(keyFile).use { fos ->
            fos.write(newKey)
            fos.flush()
        }
        return newKey
    }
}
