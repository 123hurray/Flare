package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.renderVVOText
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.applyLocalTimelineFilters
import dev.dimension.flare.ui.presenter.home.filterDuplicateCommentPages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class VVOStatusDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<VVOStatusDetailState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val appDataStore: AppDataStore by inject()

    @Composable
    override fun body(): VVOStatusDetailState {
        val service = accountServiceProvider(accountType, repository = accountRepository)
        val status =
            service
                .flatMap {
                    remember(statusKey, accountType) {
                        require(it is VVODataSource)
                        it.status(statusKey)
                    }.collectAsState(UiState.Loading()).value
                }

        status.onSuccess {
            remember(accountType, statusKey, it) {
                LogStatusHistoryPresenter(
                    accountType = accountType,
                    statusKey = statusKey,
                    status = it,
                )
            }.body()
        }

        val extendedText =
            service.flatMap {
                require(it is VVODataSource)
                remember(statusKey, accountType) {
                    it.statusExtendedText(statusKey)
                }.collectAsState(UiState.Loading()).value
            }

        val actualStatus =
            status.flatMap { item ->
                service.map { service ->
                    require(service is VVODataSource)
                    when (extendedText) {
                        is UiState.Error -> {
                            item
                        }

                        is UiState.Loading -> {
                            item
                        }

                        is UiState.Success -> {
                            if (item is UiTimelineV2.Post) {
                                item.copy(
                                    content =
                                        renderVVOText(
                                            extendedText.data,
                                            service.accountKey,
                                        ),
                                )
                            } else {
                                item
                            }
                        }
                    }
                }
            }

        val repost =
            service
                .map {
                    require(it is VVODataSource)
                    remember(statusKey, accountType) {
                        Pager(config = pagingConfig) {
                            it.statusRepost(statusKey = statusKey).toPagingSource()
                        }.flow.map { data ->
                            data.map { item ->
                                if (item is UiTimelineV2.Post) {
                                    item.copy(
                                        quote = persistentListOf(),
                                    )
                                } else {
                                    item
                                }
                            }
                        }.applyLocalTimelineFilters(
                            localFilterRepository = localFilterRepository,
                            appDataStore = appDataStore,
                            filterDuplicateComments = false,
                        )
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        val comment =
            service
                .map {
                    require(it is VVODataSource)
                    remember(statusKey, accountType) {
                        appDataStore.appSettingsStore.data.flatMapLatest { appSettings ->
                            val rawLoader =
                                it.statusComment(
                                    statusKey = statusKey,
                                    filterAiComments = appSettings.localFilterConfig.filterAiComments,
                                )
                            val loader =
                                if (appSettings.localFilterConfig.filterDuplicateComments) {
                                    rawLoader.filterDuplicateCommentPages(appSettings.localFilterConfig.duplicateCommentThreshold)
                                } else {
                                    rawLoader
                                }
                            Pager(config = pagingConfig) {
                                loader.toPagingSource()
                            }.flow
                        }.applyLocalTimelineFilters(
                            localFilterRepository = localFilterRepository,
                            appDataStore = appDataStore,
                            filterDuplicateComments = true,
                        )
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        return object : VVOStatusDetailState {
            override val status = actualStatus
            override val comment = comment
            override val repost = repost
        }
    }
}

@Immutable
public interface VVOStatusDetailState {
    public val status: UiState<UiTimelineV2>
    public val comment: PagingState<UiTimelineV2>
    public val repost: PagingState<UiTimelineV2>
}
