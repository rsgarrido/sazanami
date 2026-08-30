package io.github.rsgarrido.sazanami.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers

class LocalLyricsServices private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val ioDispatcher = Dispatchers.IO
    private val rootStore = PersistedLyricsRootStore(
        SharedPreferencesLyricsRootPersistence(appContext)
    )
    private val indexStore = AndroidLyricsIndexStore(appContext, ioDispatcher)
    private val indexer = LyricsIndexer(
        rootStore = rootStore,
        treeDataSource = AndroidLyricsTreeDataSource(appContext.contentResolver),
        indexStore = indexStore,
        ioDispatcher = ioDispatcher
    )

    val folderAccess = AndroidLyricsFolderAccess(appContext.contentResolver)
    val repository: LocalLyricsRepository = DefaultLocalLyricsRepository(
        rootStore = rootStore,
        indexStore = indexStore,
        indexer = indexer,
        documentReader = AndroidLyricsDocumentReader(appContext.contentResolver),
        parser = LrcParser(),
        ioDispatcher = ioDispatcher
    )

    companion object {
        @Volatile
        private var instance: LocalLyricsServices? = null

        fun shared(context: Context): LocalLyricsServices =
            instance ?: synchronized(this) {
                instance ?: LocalLyricsServices(context).also { instance = it }
            }
    }
}
