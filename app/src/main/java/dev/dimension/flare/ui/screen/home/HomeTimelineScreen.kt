package dev.dimension.flare.ui.screen.home

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Sliders
import dev.dimension.flare.R
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.Zhihu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.rememberTimelineItemPresenterWithLazyListState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeTimelineScreen(
    accountType: AccountType,
    onCurrentAccountChanged: (AccountType) -> Unit,
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
) {
    val state by producePresenter(key = "home_timeline_$accountType") {
        timelinePresenter(accountType)
    }
    val loggedInState = remember { LoggedInPresenter() }.invoke()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var zhihuDailyDates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val topAppBarScrollBehavior =
        if (LocalAppearanceSettings.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.pagerState.onSuccess { pagerState ->
                        state.tabState.onSuccess { tabs ->
                            if (tabs.any()) {
                                val selectedPage by remember(pagerState, tabs.size) {
                                    derivedStateOf {
                                        pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                                    }
                                }
                                SecondaryScrollableTabRow(
                                    containerColor = Color.Transparent,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    selectedTabIndex = selectedPage,
                                    edgePadding = 0.dp,
                                    divider = {},
                                    indicator = {
                                        TabRowIndicator(
                                            selectedIndex = selectedPage,
                                        )
                                    },
                                    minTabWidth = 48.dp,
                                ) {
                                    state.tabState.onSuccess { tabs ->
                                        tabs.forEachIndexed { index, tab ->
                                            LeadingIconTab(
                                                modifier = Modifier.clip(CircleShape),
                                                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                unselectedContentColor = LocalContentColor.current,
                                                selected = index == selectedPage,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.scrollToPage(index)
                                                    }
                                                },
                                                text = {
                                                    TabTitle(
                                                        tab.metaData.title,
//                                                        modifier =
//                                                            Modifier
//                                                                .padding(8.dp),
                                                    )
                                                },
                                                icon = {
                                                    TabIcon(
                                                        accountType = tab.account,
                                                        icon = tab.metaData.icon,
                                                        title = tab.metaData.title,
                                                    )
                                                },
//                                                colors = FilterChipDefaults.filterChipColors(
//                                                    containerColor = MaterialTheme.colorScheme.surface,
//                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (LocalBottomBarShowing.current) {
                        state.user
                            .onSuccess {
                                IconButton(
                                    onClick = {
                                        toQuickMenu.invoke()
                                    },
                                ) {
                                    AvatarComponent(
                                        it.avatar,
                                        size = 24.dp,
                                    )
                                }
                            }.onError {
                                IconButton(
                                    onClick = {
                                        toQuickMenu.invoke()
                                    },
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Bars,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }.onLoading {
                                IconButton(
                                    onClick = {
                                        toQuickMenu.invoke()
                                    },
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Bars,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                    }
                },
                actions = {
                    state.pagerState.onSuccess { pagerState ->
                        state.tabState.onSuccess { tabs ->
                            val selectedPage by remember(pagerState, tabs.size) {
                                derivedStateOf {
                                    if (tabs.isEmpty()) {
                                        0
                                    } else {
                                        pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                                    }
                                }
                            }
                            val dailyTab = tabs.getOrNull(selectedPage) as? Zhihu.DailyTimelineTabItem
                            if (dailyTab != null) {
                                val dateStateKey = dailyTab.dateStateKey()
                                val selectedDate = zhihuDailyDates[dateStateKey] ?: dailyTab.date
                                TextButton(
                                    onClick = {
                                        showZhihuDailyDatePicker(
                                            context = context,
                                            initialDate = selectedDate,
                                        ) { date ->
                                            zhihuDailyDates = zhihuDailyDates + (dateStateKey to date)
                                        }
                                    },
                                ) {
                                    Text(text = selectedDate?.displayZhihuDailyDate() ?: "日期")
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            toTabSettings.invoke()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Sliders,
                            contentDescription = stringResource(R.string.edit_tab_title),
                        )
                    }
                    if (loggedInState.isLoggedIn.takeSuccess() == false) {
                        TextButton(onClick = toLogin) {
                            Text(text = stringResource(id = R.string.login_button))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            state.pagerState.onSuccess { pagerState ->
                state.tabState.onSuccess { tabs ->
                    if (tabs.size > 1) {
                        val selectedPage by remember(pagerState, tabs.size) {
                            derivedStateOf {
                                pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                            }
                        }
                        TimelineTabFanButton(
                            tabs = tabs,
                            pagerState = pagerState,
                            selectedPage = selectedPage,
                            visible =
                                loggedInState.isLoggedIn.takeSuccess() == true &&
                                    LocalBottomBarHeight.current > 48.dp,
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.pagerState.onSuccess { pagerState ->
            state.tabState.onSuccess { tabState ->
                val selectedPage by remember(pagerState, tabState.size) {
                    derivedStateOf {
                        if (tabState.isEmpty()) {
                            0
                        } else {
                            pagerState.currentPage.coerceIn(0, tabState.lastIndex)
                        }
                    }
                }
                LaunchedEffect(pagerState.currentPage >= tabState.size) {
                    if (pagerState.currentPage >= tabState.size) {
                        scope.launch {
                            pagerState.scrollToPage(0)
                        }
                    }
                }
                LaunchedEffect(accountType, selectedPage, tabState) {
                    val selectedAccountType = tabState.getOrNull(selectedPage)?.account
                    when {
                        selectedAccountType is AccountType.Specific -> onCurrentAccountChanged(selectedAccountType)
                        accountType is AccountType.Specific -> onCurrentAccountChanged(accountType)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    key = { index ->
                        tabState.getOrNull(index)?.withZhihuDailyDate(zhihuDailyDates)?.key ?: "timeline_$index"
                    },
                ) { index ->
                    val item = tabState.getOrNull(index)?.withZhihuDailyDate(zhihuDailyDates)
                    if (item != null) {
                        TimelineItemContent(
                            item = item,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxWidth(),
                            changeLogState = state.changeLogState,
                            isCurrentlyVisible = selectedPage == index,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTabFanButton(
    tabs: List<TimelineTabItem>,
    pagerState: PagerState,
    selectedPage: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var wheelPosition by remember { mutableFloatStateOf(selectedPage.toFloat()) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        label = "timeline_tab_wheel_progress",
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewportSize = 340.dp
    val fabSize = 56.dp
    val wheelBleed = 16.dp
    val viewportSizePx = with(density) { viewportSize.toPx() }
    val centerInsetPx = with(density) { (fabSize / 2).toPx() }
    val wheelBleedPx = with(density) { wheelBleed.toPx() }
    val wheelRadiusPx = with(density) { 100.dp.toPx() }
    val ringStrokePx = with(density) { 72.dp.toPx() }
    val itemHalfWidthPx = with(density) { 36.dp.toPx() }
    val itemHalfHeightPx = with(density) { 22.dp.toPx() }
    val slotAngleDegrees = 26f
    val centerAngleDegrees = 218f
    val ringColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    val ringHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    LaunchedEffect(visible) {
        if (!visible) {
            expanded = false
        }
    }
    LaunchedEffect(expanded, selectedPage, tabs.size) {
        if (expanded) {
            wheelPosition = selectedPage.toFloat()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        Box(
            modifier =
                modifier
                    .size(viewportSize),
            contentAlignment = Alignment.BottomEnd,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.88f),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = wheelBleed, y = wheelBleed)
                        .zIndex(1f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(viewportSize)
                            .pointerInput(tabs.size) {
                                detectDragGestures { change, _ ->
                                    val center =
                                        androidx.compose.ui.geometry.Offset(
                                            size.width - centerInsetPx - wheelBleedPx,
                                            size.height - centerInsetPx - wheelBleedPx,
                                        )
                                    val previous = change.previousPosition - center
                                    val current = change.position - center
                                    val previousAngle = atan2(previous.y, previous.x) * 180f / PI.toFloat()
                                    val currentAngle = atan2(current.y, current.x) * 180f / PI.toFloat()
                                    var delta = currentAngle - previousAngle
                                    if (delta > 180f) {
                                        delta -= 360f
                                    } else if (delta < -180f) {
                                        delta += 360f
                                    }
                                    wheelPosition -= delta / slotAngleDegrees
                                    change.consume()
                                }
                            },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val wheelCenter =
                            androidx.compose.ui.geometry.Offset(
                                size.width - centerInsetPx - wheelBleedPx,
                                size.height - centerInsetPx - wheelBleedPx,
                            )
                        drawCircle(
                            color = ringColor,
                            center = wheelCenter,
                            radius = wheelRadiusPx,
                            style = Stroke(width = ringStrokePx),
                        )
                        drawCircle(
                            color = ringHighlightColor,
                            center = wheelCenter,
                            radius = wheelRadiusPx,
                            style = Stroke(width = ringStrokePx * 0.34f),
                        )
                    }
                    val baseIndex = floor(wheelPosition).toInt()
                    val fraction = wheelPosition - baseIndex
                    (-3..3).forEach { slot ->
                        val index = (baseIndex + slot).floorMod(tabs.size)
                        val tab = tabs[index]
                        val slotProgress = slot - fraction
                        val angleRadians = Math.toRadians((centerAngleDegrees + slotProgress * slotAngleDegrees).toDouble())
                        val centerX =
                            viewportSizePx - centerInsetPx - wheelBleedPx +
                                cos(angleRadians) * wheelRadiusPx * progress
                        val centerY =
                            viewportSizePx - centerInsetPx - wheelBleedPx +
                                sin(angleRadians) * wheelRadiusPx * progress
                        val offsetX = (centerX - itemHalfWidthPx).roundToInt()
                        val offsetY = (centerY - itemHalfHeightPx).roundToInt()
                        Surface(
                            onClick = {
                                expanded = false
                                scope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            },
                            shape = CircleShape,
                            color =
                                if (index == selectedPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            contentColor =
                                if (index == selectedPage) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset(offsetX, offsetY) }
                                    .size(width = 72.dp, height = 44.dp),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TabIcon(
                                    accountType = tab.account,
                                    icon = tab.metaData.icon,
                                    title = tab.metaData.title,
                                    modifier = Modifier.size(20.dp),
                                )
                                TabTitle(
                                    tab.metaData.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    expanded = !expanded
                },
                shape = CircleShape,
                elevation =
                    FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                    ),
                modifier =
                    Modifier
                        .size(56.dp)
                        .zIndex(2f),
            ) {
                val iconColor = LocalContentColor.current
                Canvas(modifier = Modifier.size(22.dp)) {
                    drawCircle(
                        color = iconColor,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size

private fun TimelineTabItem.withZhihuDailyDate(dates: Map<String, String>): TimelineTabItem =
    if (this is Zhihu.DailyTimelineTabItem) {
        withDate(dates[dateStateKey()] ?: date)
    } else {
        this
    }

private fun Zhihu.DailyTimelineTabItem.dateStateKey(): String = "zhihu_daily_$account"

private fun showZhihuDailyDatePicker(
    context: Context,
    initialDate: String?,
    onSelected: (String) -> Unit,
) {
    val date = initialDate?.toZhihuDailyLocalDateOrNull() ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onSelected(LocalDate.of(year, month + 1, dayOfMonth).format(DateTimeFormatter.BASIC_ISO_DATE))
        },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth,
    ).show()
}

private fun String.displayZhihuDailyDate(): String =
    toZhihuDailyLocalDateOrNull()
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ?: this

private fun String.toZhihuDailyLocalDateOrNull(): LocalDate? =
    runCatching {
        LocalDate.parse(filter { it.isDigit() }, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull()

@Composable
internal fun TimelineItemContent(
    item: TimelineTabItem,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    changeLogState: ChangeLogState? = null,
    isCurrentlyVisible: Boolean = true,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val paddingWithStatusBar =
        PaddingValues(
            top = maxOf(WindowInsets.safeContent.asPaddingValues().calculateTopPadding(), contentPadding.calculateTopPadding()),
            bottom = contentPadding.calculateBottomPadding(),
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
        )
    val state =
        rememberTimelineItemPresenterWithLazyListState(
            item = item,
            lazyStaggeredGridState = lazyStaggeredGridState,
        )
    if (isCurrentlyVisible) {
        RegisterTabCallback(
            lazyListState = state.lazyListState,
            onRefresh = {
                state.refreshSync()
                changeLogState?.dismissChangeLog()
            },
        )
    }
    val scope = rememberCoroutineScope()
    RefreshContainer(
        modifier = modifier,
        onRefresh = {
            state.refreshSync()
            changeLogState?.dismissChangeLog()
        },
        isRefreshing = state.isRefreshing,
        indicatorPadding = paddingWithStatusBar,
        content = {
            LazyStatusVerticalStaggeredGrid(
                state = state.lazyListState,
                contentPadding = contentPadding,
                allowGalleryMode = true,
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                changeLogState?.shouldShowChangeLog?.onSuccess {
                    changeLogState.changeLog?.let { changelog ->
                        if (it) {
                            item {
                                Column {
                                    AdaptiveCard {
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        horizontal = screenHorizontalPadding,
                                                    ).padding(top = 16.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.changelog_title),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                stringResource(R.string.changelog_message),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(changelog)
                                            Button(
                                                onClick = {
                                                    changeLogState.dismissChangeLog()
                                                },
                                            ) {
                                                Text(
                                                    stringResource(android.R.string.ok),
                                                )
                                            }
                                        }
                                    }
                                    if (!isBigScreen()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                status(state.listState)
            }
            state.listState.onSuccess {
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier =
                        Modifier
                            .padding(paddingWithStatusBar)
                            .align(Alignment.TopCenter),
                ) {
                    Glassify(
                        onClick = {
                            state.onNewTootsShown()
                            scope.launch {
                                state.lazyListState.scrollToItem(0)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.AnglesUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            val ctx = LocalContext.current
                            val newTootsText =
                                ctx.resources.getQuantityString(
                                    R.plurals.home_timeline_new_toots,
                                    state.newPostsCount,
                                    state.newPostsCount,
                                )
                            Text(text = newTootsText)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun timelinePresenter(accountType: AccountType) =
    run {
        val state = remember(accountType) { HomeTimelineWithTabsPresenter(accountType) }.invoke()

        val pagerState =
            state.tabState.map {
                rememberPagerState { it.size }
            }

        val changeLogState = changeLogPresenter()

        object : HomeTimelineWithTabsPresenter.State by state {
            val pagerState = pagerState
            val changeLogState = changeLogState
        }
    }
