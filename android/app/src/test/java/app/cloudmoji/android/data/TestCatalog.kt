package app.cloudmoji.android.data

/**
 * The real generated catalogue, read off the JVM test classpath.
 *
 * `app/build.gradle.kts` adds `src/main/assets` as a `test` resources
 * directory, so this is the exact file `EmojiRepositoryLoader.fromAssets`
 * would read on-device — no second copy to drift out of sync.
 */
internal object TestCatalog {
    val json: String by lazy {
        val stream = TestCatalog::class.java.classLoader
            ?.getResourceAsStream(EmojiRepositoryLoader.ASSET_FILE_NAME)
            ?: error(
                "${EmojiRepositoryLoader.ASSET_FILE_NAME} not found on the test classpath — " +
                    "check app/build.gradle.kts's `test` source set resources directory",
            )
        stream.bufferedReader().use { it.readText() }
    }
}
