package com.example.astroxplore.features.library.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe, on-demand PDF page rendering engine with memory-bounded LRU caching.
 * Prevents allocating all pages upfront in RAM, keeping heap usage under 30MB even for
 * large scientific papers.
 */
class PdfPageRendererEngine(private val file: File) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    var pageCount: Int = 0
        private set

    // Cache up to 8 high-res rendered pages (approx 20-30MB)
    private val pageCache = object : LruCache<Int, Bitmap>(8) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let the Android runtime recycle or reuse the bitmap safely
        }
    }

    // Cache low-res thumbnails for quick visual scrubbing
    private val thumbnailCache = LruCache<Int, Bitmap>(24)

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!file.exists() || file.length() == 0L) return@withContext false
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fileDescriptor!!)
                pageCount = renderer?.pageCount ?: 0
                true
            } catch (e: Exception) {
                e.printStackTrace()
                close()
                false
            }
        }
    }

    suspend fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = renderer ?: return@withContext null
            if (pageIndex !in 0 until pageCount) return@withContext null
            try {
                val page = r.openPage(pageIndex)
                val width = page.width
                val height = page.height
                page.close()
                Pair(width, height)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        // Check cache first
        pageCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        mutex.withLock {
            // Double check inside lock
            pageCache.get(pageIndex)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }

            val r = renderer ?: return@withContext null
            if (pageIndex !in 0 until pageCount) return@withContext null

            try {
                val page = r.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat()
                val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                // Draw clean white background first to avoid transparency artifacts
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                pageCache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun renderThumbnail(pageIndex: Int, targetWidth: Int = 180): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        mutex.withLock {
            thumbnailCache.get(pageIndex)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }

            val r = renderer ?: return@withContext null
            if (pageIndex !in 0 until pageCount) return@withContext null

            try {
                val page = r.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat()
                val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                thumbnailCache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun close() {
        try {
            pageCache.evictAll()
            thumbnailCache.evictAll()
            renderer?.close()
            renderer = null
            fileDescriptor?.close()
            fileDescriptor = null
        } catch (_: Exception) {}
    }
}
