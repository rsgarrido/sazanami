package io.github.rsgarrido.sazanami.lyrics

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LyricsRootPersistence {
    fun read(): String?
    fun write(value: String)
}

class PersistedLyricsRootStore(
    private val persistence: LyricsRootPersistence
) : LyricsRootStore {
    private val mutex = Mutex()
    private val _roots = MutableStateFlow(decodeRoots(persistence.read()))
    override val roots = _roots.asStateFlow()

    override suspend fun addRoot(root: LyricsRoot) {
        if (!isValidRootUri(root.uri)) return
        mutex.withLock {
            if (_roots.value.none { it.uri == root.uri }) {
                update((_roots.value + root).sortedBy(LyricsRoot::uri))
            }
        }
    }

    override suspend fun removeRoot(rootUri: String) {
        mutex.withLock {
            update(_roots.value.filterNot { it.uri == rootUri })
        }
    }

    private fun update(roots: List<LyricsRoot>) {
        persistence.write(lyricsStorageJson.encodeToString(roots))
        _roots.value = roots
    }
}

class SharedPreferencesLyricsRootPersistence(context: Context) : LyricsRootPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(): String? = preferences.getString(ROOTS_KEY, null)

    override fun write(value: String) {
        preferences.edit().putString(ROOTS_KEY, value).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "lyrics_roots"
        const val ROOTS_KEY = "roots_json"
    }
}

internal fun decodeRoots(encoded: String?): List<LyricsRoot> {
    if (encoded.isNullOrBlank()) return emptyList()
    return runCatching {
        lyricsStorageJson.decodeFromString(
            ListSerializer(LyricsRoot.serializer()),
            encoded
        )
    }.getOrDefault(emptyList())
        .filter { root -> isValidRootUri(root.uri) }
        .distinctBy(LyricsRoot::uri)
        .sortedBy(LyricsRoot::uri)
}

internal fun isValidRootUri(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme.equals("content", ignoreCase = true) && !uri.schemeSpecificPart.isNullOrBlank()
}.getOrDefault(false)

internal val lyricsStorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
