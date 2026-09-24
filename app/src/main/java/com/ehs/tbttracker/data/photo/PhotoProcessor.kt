package com.ehs.tbttracker.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.ehs.tbttracker.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Turns a raw CameraX capture into upload-ready evidence:
 * EXIF-correct orientation -> downscale (max 1600 px) -> date/time/GPS watermark -> JPEG q80.
 * A 12 MP capture (~4 MB) typically ends up at 250-450 KB.
 */
@Singleton
class PhotoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun process(rawCapture: File, siteLabel: String? = null): File = withContext(io) {
        val location = locationProvider.currentLocation()
        val bitmap = decodeScaled(rawCapture, MAX_EDGE_PX)
        val oriented = rotateToExif(bitmap, rawCapture)
        val stamped = oriented.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== oriented) oriented.recycle() }
        drawWatermark(stamped, watermarkLines(LocalDateTime.now(clock), location, siteLabel))

        val out = File(context.cacheDir, "tbt_capture_${clock.millis()}.jpg")
        out.outputStream().use { stamped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        stamped.recycle()
        rawCapture.delete()
        out
    }

    private fun decodeScaled(file: File, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode captured photo")
        val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    private fun rotateToExif(bitmap: Bitmap, source: File): Bitmap {
        val degrees = when (ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { bitmap.recycle() }
    }

    private fun drawWatermark(bitmap: Bitmap, lines: List<String>) {
        val canvas = Canvas(bitmap)
        val textSize = bitmap.width / 32f
        val padding = textSize * 0.6f
        val lineHeight = textSize * 1.3f
        val bandHeight = lineHeight * lines.size + padding * 2
        val top = bitmap.height - bandHeight
        canvas.drawRect(0f, top, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply { color = Color.argb(150, 11, 31, 58) })
        canvas.drawRect(0f, top, textSize * 0.25f, bitmap.height.toFloat(), Paint().apply { color = Color.rgb(16, 185, 129) })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, padding * 1.5f, top + padding + lineHeight * (i + 1) - (lineHeight - textSize), paint)
        }
    }

    companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 80
        private val STAMP_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss", Locale.ENGLISH)

        /** Pure so it is unit-testable. */
        fun watermarkLines(time: LocalDateTime, location: GeoPoint?, siteLabel: String?): List<String> = buildList {
            add("EHS TBT  |  ${time.format(STAMP_FORMAT)}")
            add(
                location?.let {
                    "GPS ${"%.6f".format(Locale.US, it.latitude)}, ${"%.6f".format(Locale.US, it.longitude)}" +
                        (it.accuracyMeters?.let { a -> "  (±${a.toInt()} m)" } ?: "")
                } ?: "GPS unavailable",
            )
            siteLabel?.takeIf { it.isNotBlank() }?.let { add(it.take(60)) }
        }
    }
}
