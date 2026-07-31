package app.cloudmoji.android.ui.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.platform.CameraAvailability
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The way to take another picture — or, once a grown-up has said no, the way
 * back to allowing it. Ported from iOS `PhotosView.cameraTile` /
 * `cameraPermissionTile`.
 *
 * The two states share one tile rather than one appearing where the other
 * disappears: a child who taps where the camera was must always land
 * somewhere, and "ask a grown-up" is somewhere.
 */
@Composable
internal fun CameraTile(
    availability: CameraAvailability,
    language: Language,
    isExpandedPad: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(26.dp)
    val isDenied = availability == CameraAvailability.Denied
    val takeOne = PhotosUiText.text(PhotosUiText.takeOne, language)
    val askGrownUp = PhotosUiText.text(PhotosUiText.askGrownUp, language)
    val label = if (isDenied) "$askGrownUp. ${PhotosUiText.CAMERA_DENIED}" else takeOne

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 22.dp else 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = if (isExpandedPad) PhotoGalleryMetrics.padCameraMaxWidth else Dp.Unspecified)
            .height(PhotoGalleryMetrics.cameraSide(isExpandedPad))
            .pressScale(interactionSource, PhotoGalleryMetrics.PRESSED_SCALE)
            .clip(shape)
            .background(
                if (isDenied) {
                    // Flat and dark: the recovery card is a parent-facing
                    // explanation, and should not look like the cheerful thing
                    // it replaced.
                    SolidColor(BackgroundPrimary.copy(alpha = 0.72f))
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Coral.copy(alpha = 0.18f)),
                    )
                },
                shape,
            )
            .border(2.dp, if (isDenied) Coral.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.22f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag(if (isDenied) "photos-camera-permission-btn" else "photos-camera-btn")
            .padding(horizontal = if (isExpandedPad) 34.dp else 24.dp),
    ) {
        // The lens: a dark disc with a bright pupil and a catchlight, which is
        // what a two-year-old recognises as "the camera". iOS builds the same
        // thing out of three concentric circles.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (isExpandedPad) 92.dp else 76.dp)
                .background(BackgroundPrimary, CircleShape)
                .border(3.dp, Moonlight.copy(alpha = 0.44f), CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .size(if (isExpandedPad) 62.dp else 50.dp)
                    .background(
                        Brush.radialGradient(listOf(Moonlight.copy(alpha = 0.68f), BackgroundMid)),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopStart)
                    .background(Color.White.copy(alpha = 0.72f), CircleShape),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(text = if (isDenied) "🔒" else "📷", fontSize = if (isExpandedPad) 28.sp else 22.sp)
            Text(
                text = if (isDenied) askGrownUp else takeOne,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isExpandedPad) 19.sp else 15.sp,
                maxLines = 2,
            )
            if (isDenied) {
                Text(
                    text = PhotosUiText.CAMERA_DENIED,
                    color = TextTertiary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isExpandedPad) 15.sp else 12.sp,
                )
            }
        }
    }
}

/**
 * Why the camera button is not there on hardware with no usable camera.
 * Ported from iOS `PhotosView.cameraNote`.
 *
 * The button itself is **absent** rather than disabled here — see
 * [PhotosScreen]'s own doc. This is the only explanation a parent gets for
 * that, so it must stay readable over the scrapbook.
 */
@Composable
internal fun CameraNote(modifier: Modifier = Modifier) {
    Text(
        text = PhotosUiText.NO_CAMERA,
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .testTag("photos-camera-note"),
    )
}

/**
 * Even a device with no camera gets a composed empty scrapbook rather than a
 * line of copy floating in a dark room. Ported from iOS
 * `PhotosView.emptyScrapbook`.
 */
@Composable
internal fun EmptyScrapbook(language: Language, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = PhotosUiText.text(PhotosUiText.empty, language)
            }
            .testTag("photos-empty"),
    ) {
        // Two blank cards behind, tilted opposite ways: an album waiting to be
        // filled rather than an error.
        Box(
            modifier = Modifier
                .size(width = 132.dp, height = 110.dp)
                .graphicsLayer { rotationZ = -7f; translationX = -18f }
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
        )
        Box(
            modifier = Modifier
                .size(width = 132.dp, height = 110.dp)
                .graphicsLayer { rotationZ = 7f; translationX = 18f }
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
            modifier = Modifier
                .size(width = 150.dp, height = 116.dp)
                .background(BackgroundPrimary.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
                .border(2.dp, Coral.copy(alpha = 0.24f), RoundedCornerShape(18.dp)),
        ) {
            Text(text = "🖼️", fontSize = 30.sp)
            Text(
                text = PhotosUiText.text(PhotosUiText.empty, language),
                color = TextTertiary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One photograph in the scrapbook. Ported from iOS `PhotosView.thumbnail`.
 *
 * A tilted white card with a little mark under the picture, not a bare square:
 * the pictures are somebody's afternoon and they should look like it. The
 * whole card answers a tap, so most of what a toddler aims at is not dead
 * space around a small image.
 */
@Composable
internal fun PhotoThumbnail(
    photo: File,
    index: Int,
    side: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(PhotoGalleryMetrics.cornerRadius)
    val bitmap = rememberPhotoBitmap(photo, side)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource, PhotoGalleryMetrics.PRESSED_SCALE)
            .graphicsLayer { rotationZ = PhotoGalleryMetrics.tiltDegrees(index) }
            .shadow(9.dp, RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.90f), RoundedCornerShape(15.dp))
            .border(1.dp, Color.White.copy(alpha = 0.66f), RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "Photo"
            }
            .testTag("photo-${photo.name}")
            .padding(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(side)
                .clip(shape)
                .background(Surface),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = if (PhotoGalleryMetrics.isHearted(index)) "❤️" else "✨",
            fontSize = 11.sp,
            modifier = Modifier.height(14.dp),
        )
    }
}

/**
 * A photograph, big. Tap anywhere to go back — there is no close button,
 * because there is no small target a toddler has to find. Ported from iOS
 * `PhotosView.EnlargedPhoto`.
 */
@Composable
internal fun EnlargedPhoto(photo: File, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // Bounded even here: full-screen is a far bigger decode than a thumbnail
    // but still a long way off the full twelve megapixels.
    val bitmap = rememberPhotoBitmap(photo, 1_400.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClose,
            )
            .semantics {
                role = Role.Button
                contentDescription = "Photo. Tap to close."
            }
            .testTag("photo-full"),
    ) {
        // A file that will not decode leaves the plate black — it should not
        // be on screen at all, but a black rectangle one tap dismisses is a
        // better answer than a crash.
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Decodes [photo] for a draw no bigger than [side], off the main thread.
 *
 * `produceState` rather than a plain `remember`: a JPEG decode is tens of
 * milliseconds even sampled down, and a lazy grid asks for a dozen of them in
 * one scroll — on the main thread that is a visible stall in a screen a child
 * is flicking through. [PhotoThumbnails] caches the result, so a row coming
 * back into view is a map lookup rather than a second decode.
 */
@Composable
internal fun rememberPhotoBitmap(photo: File, side: Dp): ImageBitmap? {
    val maxPixels = with(LocalDensity.current) { side.roundToPx() }
    val state = produceState<ImageBitmap?>(initialValue = null, photo, maxPixels) {
        value = withContext(Dispatchers.IO) {
            PhotoThumbnails.image(photo, maxPixels)?.asImageBitmap()
        }
    }
    return state.value
}
