package app.cloudmoji.android.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * How long the shutter ignores itself after a press. Ported from iOS
 * `CameraController.captureDebounce`.
 *
 * A toddler holds the button down and taps it forty times; without this the
 * disk fills with forty near-identical photographs of a carpet. Pure and
 * separate from the camera, so the window can be tested without one — and so
 * the *caller* can ask before it lights the flash.
 */
object CaptureDebounce {
    const val WINDOW_MS: Long = 1_000L

    fun accepts(nowMillis: Long, lastCaptureAtMillis: Long?): Boolean {
        val last = lastCaptureAtMillis ?: return true
        return nowMillis - last >= WINDOW_MS
    }
}

/** What a re-encoded photograph is written at. High enough that a printed
 * scrapbook page still looks like a photograph, low enough that a gallery a
 * child has been filling for a month is not a gigabyte. */
private const val JPEG_QUALITY = 92

private const val TAG = "CameraCapture"

/**
 * The longest edge a stored photograph keeps.
 *
 * **This is a memory bound, not a quality preference.** A modern phone's
 * twelve-megapixel capture is about 48MB once decoded, and [uprightJpeg] holds
 * two bitmaps at once while it rotates — near 100MB at the peak, on the one
 * control a toddler hammers, on hardware that may only have a gigabyte to
 * spend. At 2048 the same peak is under 25MB, and the picture is still larger
 * than any screen it will be shown on, before or after a parent exports it.
 */
const val MAX_STORED_EDGE_PIXELS = 2_048

/**
 * The `BitmapFactory` `inSampleSize` that decodes a
 * [sourceWidth]×[sourceHeight] JPEG as small as possible **without** its
 * longest edge falling below [minPixels].
 *
 * This is the *thumbnail* rounding, and the direction matters: a grid cell
 * drawn from a bitmap smaller than itself is visibly soft, so the last
 * halving that would go under the draw size is the one not taken. See
 * [jpegSampleSizeNoLargerThan] for the opposite intent, which is a genuinely
 * different function rather than the same one read carelessly.
 *
 * Powers of two only — `BitmapFactory` rounds anything else down anyway — and
 * never below 1, which is what an image already smaller than the target needs.
 */
fun jpegSampleSizeNoSmallerThan(sourceWidth: Int, sourceHeight: Int, minPixels: Int): Int {
    if (minPixels <= 0) return 1
    val longest = maxOf(sourceWidth, sourceHeight)
    var sample = 1
    while (longest / (sample * 2) >= minPixels) sample *= 2
    return sample
}

/**
 * The `BitmapFactory` `inSampleSize` that brings a
 * [sourceWidth]×[sourceHeight] JPEG's longest edge to **at or below**
 * [maxPixels].
 *
 * The *cap* rounding, used by [uprightJpeg]: [MAX_STORED_EDGE_PIXELS] is a
 * memory bound, and a bound that can be exceeded is not one. Halving once more
 * than strictly necessary costs a slightly smaller photograph;
 * [jpegSampleSizeNoSmallerThan]'s rounding here would leave a 4032-pixel
 * capture decoded at 4032 and the bound doing nothing at all.
 */
fun jpegSampleSizeNoLargerThan(sourceWidth: Int, sourceHeight: Int, maxPixels: Int): Int {
    if (maxPixels <= 0) return 1
    val longest = maxOf(sourceWidth, sourceHeight)
    var sample = 1
    while (longest / sample > maxPixels) sample *= 2
    return sample
}

/**
 * Turns a freshly captured JPEG into the bytes that get written to disk:
 * rotated upright, and carrying no metadata whatsoever.
 *
 * **The re-encode is deliberate, and it is the privacy half of this file.**
 * The sensor's own JPEG arrives with an EXIF block — orientation, but also
 * make, model, exposure, lens, and on some devices a timestamp. Decoding it to
 * a bitmap and compressing a fresh JPEG drops all of it: `Bitmap.compress`
 * writes pixels and nothing else. That is what makes "no metadata beyond the
 * image itself" a property of the file rather than a promise about which tags
 * we remembered to strip. There is no GPS tag to strip in the first place —
 * this app holds no location permission, and CameraX only writes one when
 * handed a `Location` through `OutputFileOptions.Metadata`, which nothing here
 * uses.
 *
 * Rotating rather than recording an orientation tag costs one decode/encode
 * per photograph, on a background executor, and buys a file that is upright
 * for *every* reader — this app's own thumbnail loader, the parent's gallery
 * after an export, and anything the parent later shares it with. An EXIF
 * orientation tag is honoured by some of those and ignored by others.
 *
 * The decode is bounded by [maxEdgePixels] — see [MAX_STORED_EDGE_PIXELS] for
 * why that bound exists at all.
 *
 * Returns the original bytes unchanged if they will not decode, which is the
 * honest failure: a picture that cannot be read is better handed on as it
 * arrived than replaced with nothing.
 */
fun uprightJpeg(
    bytes: ByteArray,
    rotationDegrees: Int,
    quality: Int = JPEG_QUALITY,
    maxEdgePixels: Int = MAX_STORED_EDGE_PIXELS,
): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = jpegSampleSizeNoLargerThan(bounds.outWidth, bounds.outHeight, maxEdgePixels)
    }
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
    val normalized = ((rotationDegrees % 360) + 360) % 360
    val upright = if (normalized == 0) {
        source
    } else {
        Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(normalized.toFloat()) },
            true,
        )
    }
    val out = ByteArrayOutputStream(bytes.size)
    upright.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (upright !== source) upright.recycle()
    source.recycle()
    return out.toByteArray()
}

/**
 * The camera, as much of it as a toddler needs. Ported from iOS
 * `Views/Photos/CameraController.swift`.
 *
 * **Deliberately thin.** Everything with a decision in it — the debounce
 * window ([CaptureDebounce]), the permission state machine
 * ([CameraPermissionState]), the EXIF-free re-encode ([uprightJpeg]), where
 * the file goes ([app.cloudmoji.android.data.PhotoStore]) — lives outside this
 * class and has host-run tests. What is left is CameraX wiring that no JVM
 * test can execute at all, and it is kept small for exactly that reason.
 *
 * **Still image only, never video or audio.** There is no `VideoCapture` use
 * case and no `RECORD_AUDIO` permission anywhere in this app, and there must
 * never be one: the privacy copy says Cloudmoji never listens, and a still
 * capture needs no audio input, so that costs nothing.
 *
 * [unbind] is called from three places — leaving the camera screen, the
 * composition being disposed, and the Activity pausing — deliberately
 * redundantly. A camera indicator that outlives the mini-app it belongs to is
 * a trust catastrophe in a kids app, and the redundancy is cheaper than being
 * right once. (CameraX's own lifecycle binding would release the camera at
 * `ON_STOP` regardless; this releases it at `ON_PAUSE`, one step earlier.)
 */
class CameraCapture(private val poster: CallbackPoster = AndroidMainThreadPoster()) {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lastCaptureAtMillis: Long? = null

    /** One background thread for the capture callback: the JPEG re-encode in
     * [uprightJpeg] must not happen on the main thread, and CameraX delivers
     * on whichever executor it is handed. */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Builds the preview + capture graph and binds it to [owner]'s lifecycle.
     * Safe to call again; a re-bind replaces the previous graph rather than
     * stacking a second one, which is what CameraX's own
     * `unbindAll`-then-`bind` contract requires.
     */
    fun bind(context: Context, owner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull()
            if (cameraProvider == null) {
                Log.e(TAG, "the camera provider never arrived; the viewfinder stays dark")
                return@addListener
            }
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                // A child's scrapbook wants the picture that was taken when
                // the button was pressed, not the sharpest possible one a
                // beat later.
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.onFailure {
                Log.e(TAG, "the camera would not bind", it)
                return@addListener
            }

            imageCapture = capture
        }, executor)
    }

    /**
     * Takes one photograph. Returns `false` when the shutter was pressed a
     * moment ago and this press was swallowed — in which case **[onResult] is
     * never called**, which is exactly why the answer has to come back.
     *
     * iOS learnt this the hard way and its comment is worth keeping: the
     * caller used to raise its white flash *before* asking, so a toddler doing
     * what toddlers do to a big white button left the flash up with no
     * completion coming to take it down. The whole failure lived in the gap
     * between "I asked" and "I was refused".
     *
     * A press landing in the fraction of a second before the graph is bound is
     * *accepted*, and answers with `null`. That is iOS's behaviour too (its
     * `guard box.session.isRunning else { delegate.finish(nil) }`), and it is
     * the "no failure states" answer: the child gets the buzz and the flash
     * and there is simply nothing new in the gallery, rather than a big white
     * button that visibly did nothing at all.
     */
    fun capture(nowMillis: Long, onResult: (ByteArray?) -> Unit): Boolean {
        if (!CaptureDebounce.accepts(nowMillis, lastCaptureAtMillis)) return false
        lastCaptureAtMillis = nowMillis

        val capture = imageCapture
        if (capture == null) {
            poster.post { onResult(null) }
            return true
        }

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = runCatching { image.toJpegBytes() }.getOrNull()
                    image.close()
                    poster.post { onResult(bytes) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "the shutter fired but no photograph arrived", exception)
                    poster.post { onResult(null) }
                }
            },
        )
        return true
    }

    /** Hands the camera back. Idempotent, and called from every exit. */
    fun unbind() {
        imageCapture = null
        provider?.unbindAll()
        provider = null
    }

    /** The composition is going away for good. */
    fun release() {
        unbind()
        executor.shutdown()
    }
}

/** The captured frame's JPEG bytes, rotated upright and stripped — see
 * [uprightJpeg]. CameraX hands back a single-plane JPEG buffer for an
 * `ImageCapture` use case, which is what makes this one `get` rather than a
 * YUV conversion. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)
    return uprightJpeg(raw, imageInfo.rotationDegrees)
}
