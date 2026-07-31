package app.cloudmoji.android.ui.parents

/**
 * The two outbound destinations the Grown-ups area may open, and nothing
 * else — `CLAUDE.md`'s "no network calls" rule for this app allows an
 * Android intent handing off to the mail app or the browser, never an
 * in-app fetch. [SUPPORT_EMAIL] and [PRIVACY_URL] mirror iOS `AboutView`'s
 * `supportEmail`/`supportURL`/`privacyURL` exactly, so a parent using either
 * app reaches the identical address and the same canonical policy page.
 *
 * Kept as plain strings rather than `android.net.Uri`/`android.content.Intent`
 * so this object stays a pure JVM unit a test can assert against directly —
 * `ui/parents/AboutScreen.kt` is where a string here becomes a real `Intent`
 * and a `Context.startActivity` call.
 */
object SupportLinks {
    const val SUPPORT_EMAIL: String = "kevin.chan@sproutlearn.co"
    const val SUPPORT_MAILTO: String = "mailto:$SUPPORT_EMAIL?subject=Cloudmoji%20Support"
    const val PRIVACY_URL: String = "https://cloudmoji.app/privacy"
}
