package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Trash
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
import dev.dimension.flare.ui.presenter.status.GlobalStatusTimelinePresenter
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalStatusTimelineScreen(
    title: String,
    pagingKey: String,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "global_status_timeline_$pagingKey") {
        remember(pagingKey) {
            GlobalStatusTimelinePresenter(pagingKey)
        }.invoke()
    }
    val lazyListState = rememberLazyStaggeredGridState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchActive by rememberSaveable(pagingKey) { androidx.compose.runtime.mutableStateOf(false) }
    val canDelete = pagingKey == LogStatusHistoryPresenter.FAVORITES_PAGING_KEY
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    if (searchActive) {
                        TextField(
                            value = state.query,
                            onValueChange = state::setQuery,
                            singleLine = true,
                            placeholder = {
                                Text("搜索$title")
                            },
                        )
                    } else {
                        Text(title)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    if (searchActive) {
                        IconButton(
                            onClick = {
                                state.setQuery("")
                                searchActive = false
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Xmark,
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                searchActive = true
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                                contentDescription = "搜索",
                            )
                        }
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyStatusVerticalStaggeredGrid(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (canDelete && state.listState is PagingState.Success) {
                val listState = state.listState as PagingState.Success<UiTimelineV2>
                items(
                    count = listState.itemCount,
                    key =
                        listState.itemKey {
                            it.itemKey ?: it.hashCode()
                        },
                    contentType =
                        listState.itemContentType {
                            it.itemType
                        },
                ) { index ->
                    val item = listState[index]
                    FavoriteSwipeItem(
                        item = item,
                        index = index,
                        totalCount = listState.itemCount,
                        onDelete = {
                            item?.let(state::delete)
                        },
                    )
                }
            } else {
                status(state.listState)
            }
        }
    }
}

@Composable
private fun FavoriteSwipeItem(
    item: UiTimelineV2?,
    index: Int,
    totalCount: Int,
    onDelete: () -> Unit,
) {
    val swipeState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { distance -> distance * 0.9f },
            confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    true
                } else {
                    false
                }
            },
        )
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = item != null,
        backgroundContent = {
            if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Trash,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        },
    ) {
        AdaptiveCard(
            index = index,
            totalCount = totalCount,
            respectTimelineMode = true,
            content = {
                StatusItem(item)
            },
        )
    }
}
