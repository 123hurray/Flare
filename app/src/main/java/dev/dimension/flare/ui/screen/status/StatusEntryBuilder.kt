package dev.dimension.flare.ui.screen.status

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.AltTextSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog
import dev.dimension.flare.ui.screen.status.action.StatusShareSheet
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.statusEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Status.Detail> { args ->
        StatusScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack,
            onAgent = { post ->
                navigate(
                    Route.Agent.Chat(
                        sourceRoute = "status",
                        statusKey = args.statusKey,
                        accountType = args.accountType,
                        selectedStatusPlatform = post.platformType.name.toAgentPlatformName(),
                        selectedStatusText = post.content.innerText,
                        selectedStatusAuthorName = post.user?.name?.innerText,
                        selectedStatusAuthorHandle = post.user?.handle?.raw,
                        selectedStatusCreatedAtEpochMillis = post.createdAt.value.toEpochMilliseconds(),
                        selectedStatusDeeplink = DeeplinkRoute.Status.Detail(post.statusKey, post.accountType).toUri(),
                        sourceInstanceId = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            },
        )
    }

    entry<Route.Status.VVOComment> { args ->
        VVOCommentScreen(
            commentKey = args.commentKey,
            accountType = args.accountType,
            originalAuthorKey = args.originalAuthorKey,
            onBack = onBack
        )
    }

    entry<Route.Status.VVOStatus> { args ->
        VVOStatusScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack,
            onAgent = { post ->
                navigate(
                    Route.Agent.Chat(
                        sourceRoute = "status",
                        statusKey = args.statusKey,
                        accountType = args.accountType,
                        selectedStatusPlatform = post.platformType.name.toAgentPlatformName(),
                        selectedStatusText = post.content.innerText,
                        selectedStatusAuthorName = post.user?.name?.innerText,
                        selectedStatusAuthorHandle = post.user?.handle?.raw,
                        selectedStatusCreatedAtEpochMillis = post.createdAt.value.toEpochMilliseconds(),
                        selectedStatusDeeplink = DeeplinkRoute.Status.Detail(post.statusKey, post.accountType).toUri(),
                        sourceInstanceId = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            },
        )
    }

    entry<Route.Status.AddReaction>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        AddReactionSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.AltText>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        AltTextSheet(
            text = args.text,
            onBack = onBack
        )
    }

    entry<Route.Status.BlueskyReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        BlueskyReportStatusDialog(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.DeleteConfirm>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        DeleteStatusConfirmDialog(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.MastodonReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        MastodonReportDialog(
            userKey = args.userKey,
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.MisskeyReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        MisskeyReportDialog(
            userKey = args.userKey,
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.ShareSheet>(
        metadata = BottomSheetSceneStrategy.bottomSheet(
            expandFully = true,
        )
    ) { args ->
        StatusShareSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            shareUrl = args.shareUrl,
            fxShareUrl = args.fxShareUrl,
            fixvxShareUrl = args.fixvxShareUrl,
            onBack = onBack
        )
    }

    entry<Route.TwitterArticle> { args ->
        TwitterArticleScreen(
            accountType = args.accountType,
            tweetId = args.tweetId,
            articleId = args.articleId,
            onBack = onBack
        )
    }
}

private fun String.toAgentPlatformName(): String =
    when (lowercase()) {
        "vvo" -> "微博"
        "xiaohongshu" -> "小红书"
        "xqt" -> "X"
        "jike" -> "即刻"
        "dongqiudi" -> "懂球帝"
        "zhihu" -> "知乎"
        "rss" -> "RSS"
        else -> this
    }
