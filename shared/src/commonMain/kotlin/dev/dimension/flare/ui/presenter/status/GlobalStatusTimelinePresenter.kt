package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.translation.TranslationSettingsSupport
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class GlobalStatusTimelinePresenter(
    private val pagingKey: String,
) : PresenterBase<GlobalStatusTimelinePresenter.State>(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val appDataStore: AppDataStore by inject()

    @Immutable
    public interface State {
        public val listState: PagingState<UiTimelineV2>
        public val query: String

        public fun setQuery(query: String)

        public fun delete(status: UiTimelineV2)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        var query by remember(pagingKey) { mutableStateOf("") }
        val updateQuery: (String) -> Unit = {
            query = it
        }
        val normalizedQuery = query.trim()
        val listState =
            remember(pagingKey, normalizedQuery) {
                TranslationSettingsSupport
                    .displayOptionsFlow(appDataStore)
                    .flatMapLatest { translationDisplayOptions ->
                        Pager(
                            config = pagingConfig,
                        ) {
                            if (normalizedQuery.isBlank()) {
                                database
                                    .pagingTimelineDao()
                                    .getStatusHistoryPagingSource(pagingKey = pagingKey)
                            } else {
                                database
                                    .pagingTimelineDao()
                                    .searchStatusHistoryPagingSource(
                                        pagingKey = pagingKey,
                                        query = "%$normalizedQuery%",
                                    )
                            }
                        }.flow.map { pagingData ->
                            pagingData.map {
                                TimelinePagingMapper.toUi(
                                    item = it,
                                    pagingKey = pagingKey,
                                    translationDisplayOptions = translationDisplayOptions,
                                )
                            }
                        }
                    }
            }.collectAsLazyPagingItems()
                .toPagingState()

        return object : State {
            override val listState: PagingState<UiTimelineV2> = listState
            override val query: String = query

            override fun setQuery(query: String) {
                updateQuery(query)
            }

            override fun delete(status: UiTimelineV2) {
                scope.launch {
                    val statusId =
                        TimelinePagingMapper
                            .toDb(status, pagingKey)
                            .timeline
                            .statusId
                    database
                        .pagingTimelineDao()
                        .deleteByPagingKeyAndStatusId(
                            pagingKey = pagingKey,
                            statusId = statusId,
                        )
                }
            }
        }
    }
}
