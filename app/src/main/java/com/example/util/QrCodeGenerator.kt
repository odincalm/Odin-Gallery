package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

object QrCodeGenerator {

    private val qrCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Retrieves an already-generated QR code bitmap from the in-memory cache if available.
     */
    fun getCached(content: String?): Bitmap? {
        if (content.isNullOrBlank()) return null
        val cached = qrCache[content]
        return if (cached != null && !cached.isRecycled) cached else null
    }

    /**
     * Safely generates an offline QR code Bitmap from the given string.
     * Results are cached in memory for instantaneous subsequent lookups.
     * Returns null if generation fails or input is blank, but NEVER throws an exception.
     */
    fun generateQrBitmap(content: String?, sizePx: Int = 512): Bitmap? {
        if (content.isNullOrBlank() || sizePx <= 0) return null

        val cached = qrCache[content]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1) // 1-module quiet zone margin
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height

            if (width <= 0 || height <= 0) return null

            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            // Direct immutable native bitmap allocation from pixels
            val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            qrCache[content] = bitmap
            bitmap
        } catch (e: Throwable) {
            // Guard against OutOfMemoryError, NoClassDefFoundError, WriterException, IllegalArgumentException
            null
        }
    }
}
