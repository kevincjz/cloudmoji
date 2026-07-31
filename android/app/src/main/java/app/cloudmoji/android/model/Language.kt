package app.cloudmoji.android.model

enum class Language(val code: String) {
    English("en"),
    Chinese("zh"),
    Malay("ms"),
    Japanese("ja"),
    Tagalog("tl"),
    ;

    companion object {
        fun fromCode(code: String): Language? = entries.firstOrNull { it.code == code }
    }
}

