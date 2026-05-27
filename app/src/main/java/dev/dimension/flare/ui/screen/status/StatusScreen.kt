package dev.dimension.flare.ui.screen.status

import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import dev.dimension.flare.R
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuWebHost
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.GlobalStatusCollectionPresenter
import dev.dimension.flare.ui.presenter.status.StatusContextPresenter
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val context = LocalContext.current
    val isBigScreen = isBigScreen()
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(accountType = accountType, statusKey = statusKey)
    }
    val collectionState by producePresenter("global_status_collection") {
        remember {
            GlobalStatusCollectionPresenter()
        }.invoke()
    }
    val currentPost = state.state.current.takeSuccess() as? UiTimelineV2.Post
    val isZhihuStatus = currentPost?.platformType == PlatformType.Zhihu || statusKey.host == zhihuWebHost
    val topAppBarScrollBehavior =
        if (LocalAppearanceSettings.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    if (!isZhihuStatus) {
                        Text(text = stringResource(id = R.string.status_title))
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    if (isZhihuStatus) {
                        TextButton(onClick = {}) {
                            Text("邀请回答")
                        }
                        TextButton(onClick = {}) {
                            Text("写回答")
                        }
                        IconButton(onClick = {}) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                                contentDescription = "搜索",
                            )
                        }
                    }
                },
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .threeFingerSwipeRight {
                    currentPost?.let {
                        collectionState.addFavorite(it)
                        Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                    }
                },
    ) {
        RefreshContainer(
            onRefresh = state::refresh,
            isRefreshing = state.isRefreshing,
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = it,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    modifier =
                        Modifier
                            .let {
                                if (isBigScreen) {
                                    it
                                        .fillMaxHeight()
                                        .widthIn(max = 480.dp)
                                } else {
                                    it.fillMaxSize()
                                }
                            },
                    contentPadding = it,
                ) {
                    status(
                        state.state.listState,
                        detailStatusKey = statusKey,
                        commentStyle = true,
                    )
                }
            },
        )
    }
}

private fun Modifier.threeFingerSwipeRight(onSwipeRight: () -> Unit): Modifier =
    pointerInput(onSwipeRight) {
        awaitEachGesture {
            var dragX = 0f
            var dragY = 0f
            var tracking = false
            var triggered = false
            val threshold = 80.dp.toPx()
            val crossAxisSlop = 48.dp.toPx()
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 3) {
                    tracking = true
                    dragX +=
                        pressed
                            .take(3)
                            .map { it.positionChange().x }
                            .average()
                            .toFloat()
                    dragY +=
                        pressed
                            .take(3)
                            .map { it.positionChange().y }
                            .average()
                            .toFloat()
                    pressed.forEach { it.consume() }
                }
                if (tracking && !triggered && dragX >= threshold && kotlin.math.abs(dragY) <= crossAxisSlop) {
                    triggered = true
                    onSwipeRight()
                    break
                }
            }
        }
    }

@Composable
private fun statusPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(statusKey) {
            StatusContextPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.refresh()
                isRefreshing = false
            }
        }
    }
}
