package app.cloudmoji.android.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens this app's own page in Android Settings — the one place a permission
 * a parent has refused for good can be switched back on.
 *
 * **The only link out of the app that a child's screen can lead to, and it is
 * behind the parental gate.** Android shows its permission dialog once (twice,
 * on some versions); after a final refusal there is no way back from inside
 * the app — Photos would simply have a camera tile that does nothing, with no
 * explanation a parent could act on. This is that way back, and both doors to
 * it (the recovery card in Photos, the row in Manage Photos) sit behind the
 * gate, because a two-year-old must never be able to leave the app.
 *
 * `ACTION_APPLICATION_DETAILS_SETTINGS` with a `package:` URI is the
 * documented, stable way there. A device with no Settings activity for it is
 * not a device this app runs on, but the catch is here anyway for the same
 * reason `AboutScreen`'s mail intent has one: a parent-chrome no-op beats a
 * crash.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing else this control can do.
    }
}
