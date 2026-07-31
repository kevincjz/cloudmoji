package app.cloudmoji.android.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * One photograph, decoded no larger than it is going to be drawn. Ported from
 * iOS `PhotosView.swift`'s `PhotoThumbnails`.
 *
 * `BitmapFactory.decodeFile(path)` was the obvious first version and is wrong
 * for a grid: it decodes the **full** twelve-megapixel JPEG — about 48MB of
 * bitmap — for a 92dp square. One is imperceptible; a gallery a child has been
 * filling for a month scrolls like treacle and then takes the process out with
 * an `OutOfMemoryError`.
 *
 * `inSampleSize` decodes straight to (about) the size asked for, so the cost
 * is bounded by the *thumbnail* rather than by the photograph. Results are
 * cached, because a `LazyVerticalGrid` re-asks for the same image every time a
 * row comes back on screen.
 *
 * No EXIF orientation handling here, and none needed: every file in the store
 * was written by [app.cloudmoji.android.platform.uprightJpeg], which rotates
 * the pixels and drops the metadata. See that function for why.
 *
 * The one piece of arithmetic in here — how far down to decode — lives in
 * [PhotoGalleryMetrics.thumbnailSampleSize] instead, because this object holds
 * an `android.util.LruCache` and therefore cannot be so much as loaded by a
 * JVM unit test.
 */
object PhotoThumbnails {

    /** Sized in bytes rather than entries, which is what actually runs out.
     * An eighth of the process's heap is the conventional share for an image
     * cache and leaves the rest of the app — the mascot, the emoji grid — room
     * to work. */
    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** [file]'s photograph, decoded for a [maxPixels]-longest-edge draw. Null
     * for a file that will not decode — a picture that cannot be read should
     * not be on screen, and an empty plate is a better answer than a crash. */
    fun image(file: File, maxPixels: Int): Bitmap? {
        val key = "${file.name}|$maxPixels"
        cache.get(key)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoGalleryMetrics.thumbnailSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                maxPixels,
            )
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /** Dropped when photographs are deleted, so a deleted picture does not
     * live on in memory behind a screen that says it is gone. */
    fun forget() {
        cache.evictAll()
    }
}
