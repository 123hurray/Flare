package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.GlobalStatusTimelinePresenter
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
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
    val searchBarState = searchBarPresenter()
    val searchText = searchBarState.queryTextState.text.toString()
    BackHandler(enabled = searchBarState.expanded || searchText.isNotEmpty()) {
        searchBarState.setQuery("")
        searchBarState.setExpanded(false)
        state.setQuery("")
    }
    LaunchedEffect(searchText) {
        if (searchText.isEmpty() && state.query.isNotEmpty()) {
            state.setQuery("")
        }
    }
    val lazyListState = rememberLazyStaggeredGridState()
    val canDelete = pagingKey == LogStatusHistoryPresenter.FAVORITES_PAGING_KEY
    val previewMaxLines = LocalComponentAppearance.current.lineLimit
    var deleteTarget by remember { mutableStateOf<UiTimelineV2?>(null) }
    FlareScaffold(
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SearchBar(
                    state = searchBarState,
                    onSearch = state::setQuery,
                )
            }
        },
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
                    FavoriteMenuItem(
                        item = item,
                        index = index,
                        totalCount = listState.itemCount,
                        maxLines = previewMaxLines,
                        onShowMenu = {
                            deleteTarget = item
                        },
                    )
                }
            } else {
                status(
                    pagingState = state.listState,
                    maxLines = previewMaxLines,
                )
            }
        }
    }
    deleteTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = {
                deleteTarget = null
            },
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(id = R.string.delete))
                },
                leadingContent = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Trash,
                        contentDescription = null,
                    )
                },
                modifier =
                    Modifier.clickable(
                        onClick = {
                            state.delete(target)
                            deleteTarget = null
                        },
                    ),
            )
        }
    }
}

@Composable
private fun FavoriteMenuItem(
    item: UiTimelineV2?,
    index: Int,
    totalCount: Int,
    maxLines: Int,
    onShowMenu: () -> Unit,
) {
    AdaptiveCard(
        index = index,
        totalCount = totalCount,
        respectTimelineMode = true,
        modifier =
            Modifier.pointerInput(item?.itemKey) {
                if (item == null) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress != null) {
                        longPress.consume()
                        onShowMenu()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        content = {
            StatusItem(
                item = item,
                maxLines = maxLines,
            )
        },
    )
}
