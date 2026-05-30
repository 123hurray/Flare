package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class GlobalStatusCollectionPresenter :
    PresenterBase<GlobalStatusCollectionPresenter.State>(),
    KoinComponent {
    private val cacheDatabase: CacheDatabase by inject()

    @Immutable
    public interface State {
        public fun addFootprint(status: UiTimelineV2)

        public fun addFavorite(status: UiTimelineV2)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        return object : State {
            override fun addFootprint(status: UiTimelineV2) {
                scope.launch {
                    save(status, LogStatusHistoryPresenter.FOOTPRINTS_PAGING_KEY)
                }
            }

            override fun addFavorite(status: UiTimelineV2) {
                scope.launch {
                    save(status, LogStatusHistoryPresenter.FAVORITES_PAGING_KEY)
                }
            }
        }
    }

    private suspend fun save(
        status: UiTimelineV2,
        pagingKey: String,
    ) {
        cacheDatabase.connect {
            saveToDatabase(
                cacheDatabase,
                listOf(
                    TimelinePagingMapper.toDb(
                        data = status.asFeedCardPreview(),
                        pagingKey = pagingKey,
                    ),
                ),
            )
        }
    }
}
