package app.cloudmoji.android.ui.photos

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.cloudmoji.android.platform.CameraCapture
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.findActivity
import app.cloudmoji.android.ui.common.CloudHomeButton
import app.cloudmoji.android.ui.common.HomeButtonMetrics
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** How long the white flash stays up. See [CameraScreen]'s own note on why it
 * lowers itself rather than waiting for the capture to come back. */
private const val FLASH_MILLIS = 260L

/**
 * The viewfinder and one enormous button. Ported from iOS
 * `Views/Photos/CameraView.swift`.
 *
 * Presented over the gallery rather than routed through the launcher, so it
 * keeps its own way out: a child who opened the camera by accident gets the
 * same cloud he gets everywhere else, and it does the same thing.
 *
 * **The flash lowers itself.** iOS lowers it in the capture completion, which
 * is what produced the bug its own comment records — a debounced press never
 * calls back, so a toddler drumming on the shutter left the viewfinder white
 * with nothing coming to take it down. iOS fixed it by asking first and
 * lighting the flash only for an accepted capture; this does that *and* gives
 * the flash its own short life, so there is no code path at all — a dropped
 * callback, a camera that never answers — that can leave a child looking at a
 * white screen. The flash is feedback for the press, not a progress bar for
 * the file.
 */
@Composable
internal fun CameraScreen(
    caption: String,
    hapticFeedback: HapticFeedback,
    onCapture: (ByteArray?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    val camera = remember { CameraCapture() }
    // Built once and handed to `AndroidView` as-is, rather than created in its
    // `factory` and captured into state: writing Compose state from a factory
    // schedules a recomposition from inside the layout pass that created the
    // node, and the effect below would then have to cope with a null view for
    // one frame. There is exactly one viewfinder per visit to this screen and
    // it dies with the composition.
    val previewView = remember(context) { PreviewView(context) }

    var flashToken by remember { mutableIntStateOf(0) }
    var isFlashing by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "cameraFlash",
    )

    LaunchedEffect(flashToken) {
        if (flashToken == 0) return@LaunchedEffect
        delay(FLASH_MILLIS)
        isFlashing = false
    }

    // **Three exits, deliberately redundant** — the composition going away,
    // the Activity pausing, and the cloud home button below. A camera
    // indicator that outlives the mini-app it belongs to is a trust
    // catastrophe in a kids app, and the redundancy is cheaper than being
    // right once. CameraX's own `bindToLifecycle` would release at `ON_STOP`;
    // unbinding at `ON_PAUSE` gives the camera back one step earlier, the
    // moment this screen stops being the thing in front of the child.
    DisposableEffect(previewView, lifecycleOwner) {
        val owner = lifecycleOwner ?: return@DisposableEffect onDispose { camera.release() }

        fun bind() = camera.bind(context, owner, previewView)

        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) bind()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> bind()
                Lifecycle.Event.ON_PAUSE -> camera.unbind()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
            camera.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera-panel"),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(flashAlpha)
                .background(Color.White)
                .clearAndSetSemantics {},
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Text(
                text = caption,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HomeButtonMetrics.inset, vertical = HomeButtonMetrics.inset),
            ) {
                CloudHomeButton(
                    onClick = {
                        camera.unbind()
                        onDone()
                    },
                    accent = Coral,
                )
                Spacer(modifier = Modifier.weight(1f))
                Shutter(
                    onClick = {
                        // **Ask first, then light the flash.** The order is the
                        // whole of iOS's own fix — see this file's doc.
                        val accepted = camera.capture(System.currentTimeMillis(), onCapture)
                        if (!accepted) return@Shutter
                        // The reward pattern rather than the tap knock: a
                        // photograph is a thing finished.
                        hapticFeedback.reward()
                        flashToken += 1
                        isFlashing = true
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                // A spacer the width of the home button, so the shutter is
                // centred on the screen rather than centred on what is left of
                // it. An off-centre shutter is a shutter a child misses.
                Spacer(modifier = Modifier.size(HomeButtonMetrics.side))
            }
        }
    }
}

/** The one control on the viewfinder. [PhotoGalleryMetrics.shutterSide] is
 * 88dp — see that constant's own note. */
@Composable
private fun Shutter(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(PhotoGalleryMetrics.shutterSide)
            .pressScale(interactionSource, 0.9f)
            .background(Color.White, CircleShape)
            .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "Take a picture"
            }
            .testTag("camera-shutter"),
    )
}
