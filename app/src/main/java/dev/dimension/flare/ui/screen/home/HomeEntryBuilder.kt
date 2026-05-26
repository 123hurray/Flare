package dev.dimension.flare.ui.screen.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.model.jikeWebHost
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.model.zhihuWebHost
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.homeEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    openDrawer: () -> Unit,
    uriHandler: UriHandler,
) {
    entry<Route.Home>(
        metadata = ListDetailSceneStrategy.detailPane(
            "home",
        )
    ) { args ->
        HomeTimelineScreen(
            accountType = args.accountType,
            toCompose = {
                navigate(Route.Compose.New)
            },
            toQuickMenu = {
                openDrawer.invoke()
            },
            toLogin = {
                navigate(args.accountType.reloginRoute())
            },
            toTabSettings = {
                navigate(Route.TabSettings)
            },
        )
    }
    entry<Route.Timeline> { args ->
        TimelineScreen(
            tabItem = args.tabItem,
            toLogin = {
                navigate(args.tabItem.account.reloginRoute())
            },
            onBack = onBack,
        )
    }
    entry<Route.Discover> { args ->
        DiscoverScreen(
            onUserClick = { accountType, userKey ->
                navigate(Route.Profile.User(accountType, userKey))
            },
            onStatusUrlClick = { accountType, statusKey ->
                navigate(Route.Status.Detail(statusKey = statusKey, accountType = accountType))
            },
        )
    }
    entry<Route.Notification> {
        NotificationScreen()
    }
    entry<Route.Search> { args ->
        SearchScreen(
            initialQuery = args.query,
            accountType = args.accountType,
            onUserClick = { accountType, userKey ->
                navigate(Route.Profile.User(accountType, userKey))
            },
            onStatusUrlClick = { accountType, statusKey ->
                navigate(Route.Status.Detail(statusKey = statusKey, accountType = accountType))
            },
        )
    }
    entry<Route.TabSettings>(
        metadata = ListDetailSceneStrategy.extraPane(
            "home",
        )
    ) { args ->
        TabSettingScreen(
            onBack = onBack,
            toAddRssSource = {
                navigate(Route.Rss.Create)
            },
            toGroupConfig = {
                navigate(Route.TabGroupConfig(it))
            }
        )
    }
    entry<Route.TabGroupConfig>(
        metadata = ListDetailSceneStrategy.extraPane(
            "home",
        )
    ) { args ->
        GroupConfigScreen(
            item = args.item,
            onBack = onBack,
            toAddRssSource = {
                navigate(Route.Rss.Create)
            },
        )
    }
    entry<Route.AccountSelection>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        AccountSelectionModal(
            onBack = onBack,
            navigate = navigate,
        )
    }
    entry<Route.DeepLinkAccountPicker>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        CompositionLocalProvider(
            LocalUriHandler provides uriHandler
        ) {
            DeepLinkAccountPickerModal(
                originalUrl = it.originalUrl,
                data = it.data,
                onNavigate = navigate,
                onDismissRequest = onBack,
            )
        }
    }
}

private fun AccountType.reloginRoute(): Route =
    when (this) {
        is AccountType.Specific ->
            when (accountKey.host) {
                jikeWebHost -> Route.ServiceSelect.JikeLogin(accountKey)
                xiaohongshuWebHost -> Route.ServiceSelect.XiaohongshuLogin(accountKey)
                instagramWebHost -> Route.ServiceSelect.InstagramLogin(accountKey)
                zhihuWebHost -> Route.ServiceSelect.ZhihuLogin(accountKey)
                else -> Route.ServiceSelect.Selection
            }

        else -> Route.ServiceSelect.Selection
    }
