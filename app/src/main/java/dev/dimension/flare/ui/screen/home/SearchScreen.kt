package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.datasource.xiaohongshu.cacheXhsNoteContext
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.home.SearchStatusType
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun SearchScreen(
    initialQuery: String,
    accountType: AccountType,
    onUserClick: (AccountType, MicroBlogKey) -> Unit,
    onStatusUrlClick: (AccountType, MicroBlogKey) -> Unit,
) {
    val state by producePresenter("search_${accountType}_$initialQuery") {
        presenter(accountType, initialQuery, onStatusUrlClick)
    }
    val scope = rememberCoroutineScope()
    var dismissedCaptchaUrl by remember { mutableStateOf<String?>(null) }
    var dismissedXhsVerificationUrl by remember { mutableStateOf<String?>(null) }
    val lazyListState = rememberLazyStaggeredGridState()
    val accounts = rememberLatestAccounts(state.searchState.accounts)
    state.searchState.vvoCaptchaException()?.let { exception ->
        if (dismissedCaptchaUrl != exception.url) {
            VvoCaptchaDialog(
                exception = exception,
                onDismiss = {
                    dismissedCaptchaUrl = exception.url
                },
                onVerified = {
                    scope.launch {
                        state.searchState.refreshAfterVvoCaptchaSuspend()
                    }
                },
            )
        }
    }
    state.searchState.xhsVerificationException()?.let { exception ->
        if (dismissedXhsVerificationUrl != exception.url) {
            XhsVerificationDialog(
                exception = exception,
                onDismiss = {
                    dismissedXhsVerificationUrl = exception.url
                },
                onVerified = {
                    scope.launch {
                        state.searchState.refreshAfterXhsVerificationSuspend()
                    }
                },
            )
        }
    }
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
    FlareScaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SearchBar(
                    state = state,
                    onSearch = {
                        state.commitSearch(it)
                    },
                )
            }
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.refreshing,
            onRefresh = state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (accounts != null && accounts.size > 1) {
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                items(accounts.size) { index ->
                                    val profile = accounts[index]
                                    FilterChip(
                                        selected = state.searchState.selectedAccount?.key == profile.key,
                                        onClick = {
                                            state.searchState.setAccount(profile)
                                        },
                                        label = {
                                            Text(profile.handle.canonical)
                                        },
                                        leadingIcon = {
                                            AvatarComponent(
                                                data = profile.avatar,
                                                size = 18.dp,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val selectedSearchHost =
                        state.searchState.selectedAccount?.key?.host
                            ?: (accountType as? AccountType.Specific)?.accountKey?.host
                    if (selectedSearchHost == vvoHost) {
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                val types = SearchStatusType.entries.filterNot { it == SearchStatusType.Following }
                                items(types.size) { index ->
                                    val type = types[index]
                                    FilterChip(
                                        selected = state.searchState.selectedSearchStatusType == type,
                                        onClick = {
                                            state.searchState.setSearchStatusType(type)
                                        },
                                        label = {
                                            Text(type.label)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    searchContent(
                        searchUsers = state.searchState.users,
                        searchStatus = state.searchState.status,
                        toUser = { key ->
                            state.searchState.selectedAccount?.let { account ->
                                onUserClick(AccountType.Specific(account.key), key)
                            }
                        },
                    )
                }
            },
        )
    }
}

private val SearchStatusType.label: String
    get() =
        when (this) {
            SearchStatusType.Comprehensive -> "综合"
            SearchStatusType.Realtime -> "实时"
            SearchStatusType.Video -> "视频"
            SearchStatusType.Image -> "图片"
            SearchStatusType.Following -> "关注"
        }

@Composable
private fun rememberLatestAccounts(accounts: UiState<ImmutableList<UiProfile>>): ImmutableList<UiProfile>? {
    var lastSuccess by remember { mutableStateOf<ImmutableList<UiProfile>?>(null) }
    val current = accounts.takeSuccess()
    LaunchedEffect(current) {
        if (current != null && (lastSuccess == null || current.size >= lastSuccess.orEmpty().size)) {
            lastSuccess = current
        }
    }
    return current
        ?.takeIf { lastSuccess == null || it.size >= lastSuccess.orEmpty().size }
        ?: lastSuccess
}

@Composable
private fun presenter(
    accountType: AccountType,
    initialQuery: String,
    onStatusUrlClick: (AccountType, MicroBlogKey) -> Unit,
) = run {
    val searchBarState = searchBarPresenter(initialQuery)
    val searchState =
        remember(initialQuery, accountType) {
            SearchPresenter(accountType = accountType, initialQuery)
        }.invoke()

    object : SearchBarState by searchBarState {
        val searchState = searchState

        val refreshing =
            searchState.users.isRefreshing ||
                searchState.status.isRefreshing

        fun refresh() {
            searchState.search(queryTextState.text.toString())
        }

        fun commitSearch(new: String) {
            xiaohongshuNoteFromUrl(new)?.let { noteUrl ->
                val targetAccountType =
                    searchState.selectedAccount?.let { AccountType.Specific(it.key) }
                        ?: searchState.accounts
                            .takeSuccess()
                            ?.firstOrNull { it.key.host == xiaohongshuWebHost }
                            ?.let { AccountType.Specific(it.key) }
                        ?: accountType
                val targetAccountKey = (targetAccountType as? AccountType.Specific)?.accountKey
                if (targetAccountKey?.host == xiaohongshuWebHost) {
                    cacheXhsNoteContext(
                        noteId = noteUrl.noteId,
                        xsecToken = noteUrl.xsecToken,
                        xsecSource = noteUrl.xsecSource,
                    )
                    searchBarState.setQuery(new)
                    searchBarState.addSearchHistory(new)
                    onStatusUrlClick(targetAccountType, MicroBlogKey(noteUrl.noteId, targetAccountKey.host))
                    return
                }
            }
            searchBarState.setQuery(new)
            searchBarState.addSearchHistory(new)
            searchState.search(new)
        }
    }
}
