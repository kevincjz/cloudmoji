package app.cloudmoji.android.model

/**
 * How the cloud is feeling. The child never sets this directly — it follows
 * taps and speech, and [Beaming] (a milestone) outranks everything else.
 *
 * Ported from iOS `CloudMascot.swift`'s `MascotMood`. The raw values there are
 * a contract with UI tests via an accessibility identifier; here the same
 * observability comes from `MascotMoodMachine.mood`, a `StateFlow` a Compose
 * test can read directly rather than parsing out of an identifier string.
 */
enum class MascotMood {
    Happy,
    Excited,
    Speaking,
    Beaming,
    ;

    companion object {
        /**
         * A milestone or round-completion celebration outranks everything.
         *
         * `CLAUDE.md` rule 11. Without this, the very tap that *earns* the
         * celebration — and the speech finishing right after it — pull the
         * beaming face straight back off, and the reward the whole thing
         * exists for is a flicker.
         *
         * Every mood change [MascotMoodMachine] makes goes through here. The
         * only assignment that does not is a celebration ending its own
         * hold — nothing else is allowed to lower that flag.
         */
        fun arbitrate(current: MascotMood, requested: MascotMood): MascotMood =
            if (current == Beaming && requested != Beaming) current else requested
    }
}
