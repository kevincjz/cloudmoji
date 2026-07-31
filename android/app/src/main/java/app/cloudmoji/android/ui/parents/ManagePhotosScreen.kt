package app.cloudmoji.android.ui.parents

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.cloudmoji.android.data.PhotoStore
import app.cloudmoji.android.platform.CameraAvailability
import app.cloudmoji.android.platform.CameraPermissionState
import app.cloudmoji.android.platform.PhotoExport
import app.cloudmoji.android.platform.findActivity
import app.cloudmoji.android.platform.openAppSettings
import app.cloudmoji.android.ui.photos.PhotoThumbnails
import app.cloudmoji.android.ui.photos.rememberPhotoBitmap
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.io.File

/** Parent-only chrome throughout, so the 44dp platform floor — `CLAUDE.md`
 * rule 1's own carve-out — rather than the app's 64dp child-facing minimum.
 * Nothing on this screen is for the child. Mirrors iOS
 * `ManagePhotosView.rowHeight`. */
private val RowHeight = 44.dp

private val ThumbnailSide = 56.dp

/**
 * The grown-up's half of Photos: save copies to the phone's gallery, or delete
 * them. Ported from iOS `Views/Photos/ManagePhotosView.swift`.
 *
 * **Its gate is the one it is reached through.** Like iOS — where this is
 * pushed from `SettingsView`, already behind the gate — this is a destination
 * inside [GrownUpsHost], so a grown-up has answered the arithmetic question
 * before the first delete button is on screen. There is deliberately *no*
 * second gate here: adding one would mean a second way into parent-only
 * territory, and the single-doorway discipline is what makes the first one
 * worth anything.
 *
 * That is also the whole reason this screen exists separately from the child's
 * gallery, which has no delete affordance at all: a two-year-old with a delete
 * button is a two-year-old with an empty gallery.
 */
@Composable
fun ManagePhotosScreen(
    store: PhotoStore,
    cameraPermission: CameraPermissionState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    val cameraAvailability by cameraPermission.availability.collectAsState()

    var photos by remember(store) { mutableStateOf(store.photos) }
    var pendingDeletion by remember { mutableStateOf<File?>(null) }
    var isConfirmingDeleteAll by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PhotoExport.Outcome?>(null) }
    var queued by remember { mutableStateOf<List<File>>(emptyList()) }

    fun runExport(files: List<File>) {
        if (isSaving || files.isEmpty()) return
        isSaving = true
        scope.launch {
            outcome = PhotoExport.export(context, files)
            isSaving = false
        }
    }

    // Only ever launched on API 28 and below — see
    // `PhotoExport.needsLegacyStoragePermission`. A refusal is reported as the
    // same "permission denied" outcome an insert failure would be, so there is
    // one explanation rather than two spellings of it.
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val files = queued
        queued = emptyList()
        if (granted) runExport(files) else outcome = PhotoExport.Outcome.PermissionDenied
    }

    fun save(files: List<File>) {
        if (isSaving || files.isEmpty()) return
        val needsPermission = PhotoExport.needsLegacyStoragePermission(Build.VERSION.SDK_INT) &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            queued = files
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runExport(files)
        }
    }

    LaunchedEffect(store) {
        photos = store.photos
        cameraPermission.refresh()
    }

    // Returning from Android Settings is how the camera row changes, so it has
    // to go away again on the way back rather than on the next launch.
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraPermission.refresh()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("manage-photos-panel"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParentBackBar(title = "Photos", onBack = onBack, backTag = "manage-photos-back")

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        text = if (photos.isEmpty()) {
                            "No photos on this device."
                        } else {
                            "${photos.size} photo${if (photos.size == 1) "" else "s"} on this device."
                        },
                        color = TextPrimary,
                        fontFamily = CloudmojiBodyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(min = RowHeight).testTag("manage-photos-count"),
                    )
                }
                item {
                    Footnote(
                        "Photos your child takes stay inside Cloudmoji unless you choose to save a " +
                            "copy to your gallery. Cloudmoji's own copies are excluded from Android " +
                            "backup and device transfer, and uninstalling the app deletes them.",
                    )
                }
                item { Spacer(Modifier.height(14.dp)) }

                if (cameraAvailability == CameraAvailability.Denied) {
                    item { ManageSectionHeader("Camera") }
                    item {
                        ManageRow(
                            icon = "📷",
                            label = "Allow camera access",
                            testTag = "manage-photos-camera-settings",
                            onClick = { openAppSettings(context) },
                        )
                    }
                    item {
                        Footnote(
                            "Cloudmoji does not have permission to use the camera. Photos shows a " +
                                "grown-up recovery card, and this opens Android Settings where you " +
                                "can switch access back on.",
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }

                if (photos.isNotEmpty()) {
                    item { ManageSectionHeader("Gallery") }
                    item {
                        ManageRow(
                            icon = "⬇️",
                            label = if (isSaving) "Saving…" else "Save all ${photos.size} to my gallery",
                            testTag = "manage-photos-save-all",
                            enabled = !isSaving,
                            onClick = { save(photos) },
                        )
                    }
                    item {
                        Footnote(
                            "This adds copies to your phone's own gallery, in a folder called " +
                                "${PhotoExport.ALBUM}. The originals stay inside Cloudmoji until you " +
                                "delete them.",
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }

                    item { ManageSectionHeader("Photos") }
                    items(photos, key = { it.name }) { photo ->
                        PhotoRow(
                            photo = photo,
                            enabled = !isSaving,
                            onSave = { save(listOf(photo)) },
                            onDelete = { pendingDeletion = photo },
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }

                    item {
                        ManageRow(
                            icon = "🗑️",
                            label = "Delete all photos",
                            testTag = "manage-photos-delete-all",
                            enabled = !isSaving,
                            tint = Coral,
                            onClick = { isConfirmingDeleteAll = true },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // A confirmation, because deleting is the one irreversible thing a parent
    // can do in this app and the photographs are somebody's afternoon.
    pendingDeletion?.let { photo ->
        ConfirmDialog(
            title = "Delete this photo?",
            message = "This photo will be permanently deleted from Cloudmoji. This cannot be undone.",
            confirmLabel = "Delete photo",
            testTag = "manage-photos-confirm-delete",
            onConfirm = {
                store.delete(photo)
                photos = store.photos
                PhotoThumbnails.forget()
                pendingDeletion = null
            },
            onDismiss = { pendingDeletion = null },
        )
    }

    if (isConfirmingDeleteAll) {
        ConfirmDialog(
            title = "Delete all photos?",
            message = "This cannot be undone.",
            confirmLabel = "Delete all",
            testTag = "manage-photos-confirm-delete-all",
            onConfirm = {
                store.deleteAll()
                photos = store.photos
                PhotoThumbnails.forget()
                isConfirmingDeleteAll = false
            },
            onDismiss = { isConfirmingDeleteAll = false },
        )
    }

    outcome?.let { result ->
        OutcomeDialog(outcome = result, onDismiss = { outcome = null }, onOpenSettings = { openAppSettings(context) })
    }
}

@Composable
private fun ManageSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ManageRow(
    icon: String,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { role = Role.Button; contentDescription = label }
            .testTag(testTag),
    ) {
        Text(text = icon, fontSize = 16.sp, modifier = Modifier.width(30.dp))
        Text(
            text = label,
            color = if (enabled) tint else TextTertiary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One photograph, with the two things a grown-up can do to it. */
@Composable
private fun PhotoRow(
    photo: File,
    enabled: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(vertical = 4.dp)
            .testTag("manage-photo-${photo.name}"),
    ) {
        val bitmap = rememberPhotoBitmap(photo, ThumbnailSide)
        Box(
            modifier = Modifier
                .size(ThumbnailSide)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface, RoundedCornerShape(10.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
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
        Spacer(Modifier.weight(1f))
        RowAction(label = "Save", tint = Teal, enabled = enabled, testTag = "manage-photo-save", onClick = onSave)
        Spacer(Modifier.width(8.dp))
        RowAction(label = "Delete", tint = Coral, enabled = enabled, testTag = "manage-photo-delete", onClick = onDelete)
    }
}

@Composable
private fun RowAction(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = RowHeight)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { role = Role.Button; contentDescription = label }
            .testTag(testTag)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) tint else TextTertiary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    testTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundPrimary,
        title = { Text(text = title, color = TextPrimary, fontFamily = CloudmojiBodyFont) },
        text = { Text(text = message, color = TextSecondary, fontFamily = CloudmojiBodyFont) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(testTag)) {
                Text(text = confirmLabel, color = Coral, fontFamily = CloudmojiBodyFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextSecondary, fontFamily = CloudmojiBodyFont)
            }
        },
    )
}

/** How an export ended, said once. Mirrors iOS `ManagePhotosView.SaveAlert`'s
 * three cases plus the empty one, which iOS cannot reach because its button is
 * hidden when there is nothing to save. */
@Composable
private fun OutcomeDialog(
    outcome: PhotoExport.Outcome,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (title, message) = when (outcome) {
        PhotoExport.Outcome.Saved ->
            "Saved" to "The copies are in your gallery, in a folder called ${PhotoExport.ALBUM}."

        PhotoExport.Outcome.NoPhotos ->
            "Nothing to save" to "There are no photos on this device yet."

        PhotoExport.Outcome.PermissionDenied ->
            "Allow gallery access" to
                "Cloudmoji needs permission to add these photos to your gallery. You can allow it in Android Settings."

        PhotoExport.Outcome.Failed ->
            "Couldn't save" to "No photos were removed from Cloudmoji. Please try again."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundPrimary,
        title = { Text(text = title, color = TextPrimary, fontFamily = CloudmojiBodyFont) },
        text = { Text(text = message, color = TextSecondary, fontFamily = CloudmojiBodyFont) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("manage-photos-outcome-ok")) {
                Text(text = "OK", color = Teal, fontFamily = CloudmojiBodyFont)
            }
        },
        dismissButton = if (outcome == PhotoExport.Outcome.PermissionDenied) {
            {
                TextButton(onClick = { onDismiss(); onOpenSettings() }) {
                    Text(text = "Open Settings", color = Teal, fontFamily = CloudmojiBodyFont)
                }
            }
        } else {
            null
        },
    )
}
