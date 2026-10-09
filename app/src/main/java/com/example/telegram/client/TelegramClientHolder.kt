package com.example.telegram.client

import android.content.Context
import com.example.BuildConfig
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import org.drinkless.tdlib.TdApi
import java.io.File

object TelegramClientHolder {
    private var client: TdClient? = null
    private var isInitialized = false

    private val fallbackUpdates = kotlinx.coroutines.flow.MutableSharedFlow<TdApi.Update>()

    @Synchronized
    fun getClient(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO): TdClient {
        client?.let { return it }

        val tdlibDir = File(context.filesDir, "tdlib").apply {
            if (!exists()) mkdirs()
        }

        val apiId = try {
            BuildConfig.TELEGRAM_API_ID.trim().toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
        val apiHash = try {
            BuildConfig.TELEGRAM_API_HASH.trim()
        } catch (e: Exception) {
            ""
        }

        val newClient = TdClient(
            filesDir = tdlibDir.absolutePath,
            verbosityLevel = 1, // Only fatal/error logs, prevents private media/auth logging
            apiId = apiId,
            apiHash = apiHash,
            dispatcher = dispatcher
        )
        try {
            newClient.init()
        } catch (e: Throwable) {
            // In JVM unit test environments, native tdjni is not linked on host JVM
            throw IllegalStateException("TDLib native library unavailable: ${e.message}", e)
        }
        client = newClient
        isInitialized = true
        return newClient
    }

    fun isReady(): Boolean = client != null && isInitialized

    suspend fun <T : TdApi.Object> send(context: Context, function: TdApi.Function<T>): T {
        return getClient(context).send(function)
    }

    suspend fun <T : TdApi.Object> sendWithTimeout(
        context: Context,
        function: TdApi.Function<T>,
        timeoutMillis: Long = 20_000L
    ): T {
        return kotlinx.coroutines.withTimeout(timeoutMillis) {
            getClient(context).send(function)
        }
    }

    fun getUpdates(context: Context): SharedFlow<TdApi.Update> {
        return try {
            getClient(context).updates
        } catch (e: Throwable) {
            fallbackUpdates
        }
    }

    @Synchronized
    fun closeClient() {
        try {
            client?.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        } finally {
            client = null
            isInitialized = false
        }
    }
}
