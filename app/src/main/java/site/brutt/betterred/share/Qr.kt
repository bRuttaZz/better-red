package site.brutt.betterred.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Makes and reads QR codes with ZXing (core only: no camera or UI parts of it). */
object Qr {
    /** A black-on-white QR code, [size] pixels square. */
    fun bitmap(text: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    private val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))

    private fun decode(source: LuminanceSource): String? = runCatching {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }.getOrNull() ?: runCatching { // dark mode screenshots: light code on dark background
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
    }.getOrNull()

    /** Reads a QR code from a picture (e.g. a screenshot she sent). */
    fun read(bitmap: Bitmap): String? {
        // Big photos are scaled down first: faster, and ZXing copes better.
        val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return decode(RGBLuminanceSource(b.width, b.height, px))
    }

    /** Reads a QR code from a camera frame's brightness plane (Y of YUV_420_888). */
    fun read(y: ByteArray, width: Int, height: Int): String? =
        decode(PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false))
}
