package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.example.telegram.data.TelegramSyncManager

class OdinApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            TelegramSyncManager.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // Video frame decoder for local video thumbnails
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35) // Dedicate 35% of heap to fast image memory cache
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("odin_image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB disk cache
                    .build()
            }
            .allowHardware(true) // Direct GPU memory rendering on Android 8.0+
            .allowRgb565(true)   // Optimize thumbnail memory allocation
            .respectCacheHeaders(false) // Local-first offline performance
            .crossfade(false)    // Disable global crossfade to avoid per-frame opacity animation overhead during scroll
            .build()
    }
}
