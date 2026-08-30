package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.ListeningHistoryProjectionResolver
import io.github.rsgarrido.sazanami.data.ProductionListeningHistoryProjections
import io.github.rsgarrido.sazanami.data.ResolvedProductionListeningHistory
import io.github.rsgarrido.sazanami.data.SongReferenceIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

internal data class IndexedLibrarySnapshot(
    val index: SongReferenceIndex,
    val visibleMembershipKeys: Set<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun collectProductionListeningHistory(
    history: Flow<ProductionListeningHistoryProjections>,
    library: Flow<IndexedLibrarySnapshot>,
    publish: (ResolvedProductionListeningHistory) -> Unit
) {
    history.combine(library) { projections, librarySnapshot ->
        projections to librarySnapshot
    }.mapLatest { (projections, librarySnapshot) ->
        withContext(Dispatchers.Default) {
            ListeningHistoryProjectionResolver.resolve(
                projections = projections,
                index = librarySnapshot.index,
                visibleMembershipKeys = librarySnapshot.visibleMembershipKeys
            )
        }
    }.collect(publish)
}
