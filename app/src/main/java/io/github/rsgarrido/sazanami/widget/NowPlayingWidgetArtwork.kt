package io.github.rsgarrido.sazanami.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

internal const val WIDGET_ARTWORK_MAX_EDGE_PX = 256

internal fun widgetArtworkCacheIdentity(uri: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun calculateWidgetArtworkSampleSize(
    width: Int,
    height: Int,
    maxEdgePx: Int = WIDGET_ARTWORK_MAX_EDGE_PX
): Int {
    var sampleSize = 1
    while (max(width / sampleSize, height / sampleSize) > maxEdgePx * 2) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun calculateWidgetArtworkTargetSize(
    width: Int,
    height: Int,
    maxEdgePx: Int = WIDGET_ARTWORK_MAX_EDGE_PX
): Pair<Int, Int> {
    val largest = max(width, height)
    if (largest <= maxEdgePx) return width to height
    val scale = maxEdgePx.toFloat() / largest.toFloat()
    return (width * scale).toInt().coerceAtLeast(1) to
        (height * scale).toInt().coerceAtLeast(1)
}

internal enum class WidgetArtworkPresentation { ARTWORK, PLACEHOLDER }

internal fun widgetArtworkPresentation(decodedArtworkAvailable: Boolean): WidgetArtworkPresentation =
    if (decodedArtworkAvailable) WidgetArtworkPresentation.ARTWORK
    else WidgetArtworkPresentation.PLACEHOLDER

internal class NowPlayingWidgetArtwork(private val context: Context) {
    private val directory = File(context.cacheDir, "now_playing_widget_artwork")

    fun cachedPath(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return cacheFile(uri).takeIf { file -> file.isFile && file.length() > 0L }?.absolutePath
    }

    /** Returns null for absent, revoked, corrupt, or otherwise unreadable artwork. */
    @Synchronized
    fun decodeAndCache(uriString: String): String? = runCatching {
        cachedPath(uriString)?.let { path -> return path }
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateWidgetArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        val bounded = decoded.boundedTo(WIDGET_ARTWORK_MAX_EDGE_PX)
        try {
            directory.mkdirs()
            val target = cacheFile(uriString)
            try {
                FileOutputStream(target).use { output ->
                    if (!bounded.compress(Bitmap.CompressFormat.WEBP, 88, output)) {
                        target.delete()
                        return null
                    }
                }
            } catch (error: Exception) {
                target.delete()
                throw error
            }
            target.absolutePath
        } finally {
            if (bounded !== decoded) bounded.recycle()
            decoded.recycle()
        }
    }.getOrNull()

    private fun cacheFile(uri: String): File = File(directory, "${widgetArtworkCacheIdentity(uri)}.webp")
}

private fun Bitmap.boundedTo(maxEdgePx: Int): Bitmap {
    val target = calculateWidgetArtworkTargetSize(width, height, maxEdgePx)
    if (target.first == width && target.second == height) return this
    return Bitmap.createScaledBitmap(
        this,
        target.first,
        target.second,
        true
    )
}
