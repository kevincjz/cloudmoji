package app.cloudmoji.android.model

enum class MiniApp(
    val route: String,
    val icon: String,
    val requiresFull: Boolean,
    private val labels: Map<Language, String>,
) {
    Words(
        route = "words",
        icon = "🗣️",
        requiresFull = false,
        labels = mapOf(
            Language.English to "Words",
            Language.Chinese to "词语",
            Language.Malay to "Perkataan",
            Language.Japanese to "ことば",
            Language.Tagalog to "Mga Salita",
        ),
    ),
    Count(
        route = "count",
        icon = "🧮",
        requiresFull = false,
        labels = mapOf(
            Language.English to "Count",
            Language.Chinese to "数数",
            Language.Malay to "Kira",
            Language.Japanese to "かぞえる",
            Language.Tagalog to "Bilang",
        ),
    ),
    FlashCards(
        route = "flashcards",
        icon = "⚡",
        requiresFull = true,
        labels = mapOf(
            Language.English to "Flash Cards",
            Language.Chinese to "闪卡",
            Language.Malay to "Kad Kilat",
            Language.Japanese to "カード",
            Language.Tagalog to "Flash Card",
        ),
    ),
    Music(
        route = "instrument",
        icon = "🎹",
        requiresFull = true,
        labels = mapOf(
            Language.English to "Music",
            Language.Chinese to "音乐",
            Language.Malay to "Muzik",
            Language.Japanese to "おんがく",
            Language.Tagalog to "Musika",
        ),
    ),
    Animals(
        route = "animalsounds",
        icon = "🔊",
        requiresFull = true,
        labels = mapOf(
            Language.English to "Animals",
            Language.Chinese to "动物",
            Language.Malay to "Haiwan",
            Language.Japanese to "どうぶつ",
            Language.Tagalog to "Hayop",
        ),
    ),
    Photos(
        route = "photos",
        icon = "📷",
        requiresFull = true,
        labels = mapOf(
            Language.English to "Photos",
            Language.Chinese to "照片",
            Language.Malay to "Gambar",
            Language.Japanese to "しゃしん",
            Language.Tagalog to "Mga Litrato",
        ),
    ),
    Sleepy(
        route = "sleepy",
        icon = "🌙",
        requiresFull = true,
        labels = mapOf(
            Language.English to "Sleepy Cloud",
            Language.Chinese to "瞌睡云",
            Language.Malay to "Awan Mengantuk",
            Language.Japanese to "ねむいくも",
            Language.Tagalog to "Inaantok na Ulap",
        ),
    );

    fun label(language: Language): String =
        labels[language] ?: labels.getValue(Language.English)

    companion object {
        fun fromRoute(route: String): MiniApp? = entries.firstOrNull { it.route == route }
    }
}

