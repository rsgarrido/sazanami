package com.example.cdplaya.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PlaylistCollageAsset(
    val identity: VisualAssetIdentity,
    val thumbnailFile: File,
    val displayFile: File
) {
    fun file(variant: VisualAssetVariant): File = when (variant) {
        VisualAssetVariant.THUMBNAIL -> thumbnailFile
        VisualAssetVariant.DISPLAY -> displayFile
    }
}

/** Persistent automatic collage cache. All public work is shifted off the main thread. */
class PlaylistCollageStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val imageLoader = Coil.imageLoader(appContext)
    private val root = File(appContext.filesDir, "visual_assets/playlist-collage")

    fun expected(playlistId: Long, signature: String): PlaylistCollageAsset {
        val directory = File(File(root, playlistId.toString()), signature)
        return PlaylistCollageAsset(
            identity = VisualAssetIdentity(
                VisualAssetOwnerType.PLAYLIST_COLLAGE,
                playlistId.toString(),
                signature
            ),
            thumbnailFile = File(directory, VisualAssetVariant.THUMBNAIL.fileName),
            displayFile = File(directory, VisualAssetVariant.DISPLAY.fileName)
        )
    }

    fun deletePlaylist(playlistId: Long) {
        requestedSignatures.remove(playlistId)
        File(root, playlistId.toString()).deleteRecursively()
    }

    suspend fun ensure(
        playlistId: Long,
        signature: String,
        orderedArtworkUris: List<Uri>
    ): PlaylistCollageAsset? = withContext(Dispatchers.IO) {
        requestedSignatures[playlistId] = signature
        val expected = expected(playlistId, signature)
        if (expected.thumbnailFile.isFile && expected.displayFile.isFile) return@withContext expected
        if (orderedArtworkUris.isEmpty()) return@withContext null
        val ownerDirectory = expected.displayFile.parentFile?.parentFile ?: return@withContext null
        val staging = File(ownerDirectory, ".$signature-${System.nanoTime()}.tmp")
        if (!staging.mkdirs()) return@withContext null
        try {
            val sources = orderedArtworkUris.take(4).mapNotNull { uri ->
                val result = imageLoader.execute(
                    ImageRequest.Builder(appContext)
                        .data(uri)
                        .size(COLLAGE_DISPLAY_SIZE_PX)
                        .allowHardware(false)
                        .build()
                ) as? SuccessResult
                result?.drawable?.toBitmap()
            }
            if (sources.isEmpty()) return@withContext null
            val display = renderCollage(sources, COLLAGE_DISPLAY_SIZE_PX)
            sources.forEach { if (it !== display) it.recycle() }
            val thumbnail = Bitmap.createScaledBitmap(
                display,
                VisualAssetVariant.THUMBNAIL.maximumDimensionPx,
                VisualAssetVariant.THUMBNAIL.maximumDimensionPx,
                true
            )
            writeWebp(display, File(staging, VisualAssetVariant.DISPLAY.fileName))
            writeWebp(thumbnail, File(staging, VisualAssetVariant.THUMBNAIL.fileName))
            display.recycle()
            thumbnail.recycle()
            if (requestedSignatures[playlistId] != signature) return@withContext null
            val destination = expected.displayFile.parentFile ?: return@withContext null
            if (destination.exists()) destination.deleteRecursively()
            if (!staging.renameTo(destination)) return@withContext null
            if (requestedSignatures[playlistId] != signature) {
                destination.deleteRecursively()
                return@withContext null
            }
            ownerDirectory.listFiles()
                ?.filter { it.isDirectory && it.name != signature && !it.name.startsWith(".") }
                ?.forEach(File::deleteRecursively)
            expected
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun renderCollage(sources: List<Bitmap>, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val half = size / 2
        val cells = when (sources.size) {
            1 -> listOf(Rect(0, 0, size, size))
            2 -> listOf(Rect(0, 0, half, size), Rect(half, 0, size, size))
            3 -> listOf(Rect(0, 0, half, size), Rect(half, 0, size, half), Rect(half, half, size, size))
            else -> listOf(
                Rect(0, 0, half, half), Rect(half, 0, size, half),
                Rect(0, half, half, size), Rect(half, half, size, size)
            )
        }
        cells.forEachIndexed { index, destination ->
            drawCenterCrop(canvas, sources[index.coerceAtMost(sources.lastIndex)], destination)
        }
        return output
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, destination: Rect) {
        val targetAspect = destination.width().toFloat() / destination.height()
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val source = if (sourceAspect > targetAspect) {
            val width = (bitmap.height * targetAspect).toInt()
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / targetAspect).toInt()
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun writeWebp(bitmap: Bitmap, file: File) {
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        check(file.outputStream().buffered().use { bitmap.compress(format, 86, it) })
    }

    private companion object {
        const val COLLAGE_DISPLAY_SIZE_PX = 1024
        val requestedSignatures = ConcurrentHashMap<Long, String>()
    }
}
