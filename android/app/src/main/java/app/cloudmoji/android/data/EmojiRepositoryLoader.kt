package app.cloudmoji.android.data

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The generated catalogue could not be turned into an [EmojiRepository]. */
class EmojiCatalogException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Reads and decodes `EmojiData.json`. The only code that knows the asset file
 * name and the JSON decoder configuration.
 *
 * [fromJson] takes no `Context`, so JVM unit tests can hand it the real
 * generated file's text (read off the test classpath — see
 * `app/build.gradle.kts`'s `test` source set) without any Android
 * dependency or Robolectric.
 */
object EmojiRepositoryLoader {
    const val ASSET_FILE_NAME = "EmojiData.json"

    /**
     * `ignoreUnknownKeys` stays at its default of `false`: an unrecognised key
     * means the generator and this port have drifted, and that must fail
     * loudly rather than decode a silently incomplete repository.
     */
    private val json = Json

    fun fromAssets(context: Context, fileName: String = ASSET_FILE_NAME): EmojiRepository {
        val raw = try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw EmojiCatalogException("$fileName could not be read from assets: ${e.message}", e)
        }
        return fromJson(raw)
    }

    fun fromJson(raw: String): EmojiRepository {
        val dto = try {
            json.decodeFromString(EmojiDataDto.serializer(), raw)
        } catch (e: SerializationException) {
            throw EmojiCatalogException("EmojiData.json could not be decoded: ${e.message}", e)
        }
        return EmojiRepository.from(dto)
    }
}
