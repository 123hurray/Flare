package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.zhihu.ZhihuComment
import dev.dimension.flare.data.network.zhihu.ZhihuContent
import dev.dimension.flare.data.network.zhihu.ZhihuContentType
import dev.dimension.flare.data.network.zhihu.ZhihuViewer
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuWebHost
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

internal fun ZhihuContent.toUiTimeline(
    accountKey: MicroBlogKey,
    detail: Boolean,
): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(statusId, zhihuWebHost)
    val accountType = AccountType.Specific(accountKey)
    val content =
        contentHtml
            ?.let { parseHtml("<h2>$title</h2>$it").toUi(sourceLanguages) }
            ?: listOf(title, excerpt)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .toUiPlainText(sourceLanguages)
    return UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images =
            if (detail) {
                content.imageUrls.map { url ->
                    UiMedia.Image(
                        url = url,
                        previewUrl = url,
                        description = null,
                        height = 0f,
                        width = 0f,
                        sensitive = false,
                    )
                }.toImmutableList()
            } else {
                persistentListOf()
            },
        sensitive = false,
        contentWarning = null,
        user = toAuthorProfile(accountKey),
        sourceLanguages = sourceLanguages.toImmutableList(),
        content = content,
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(commentCount),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                accountType = accountType,
                                statusKey = statusKey,
                            ),
                        ),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(voteupCount),
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        persistentListOf(
                            ActionMenu.Item(
                                icon = UiIcon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.ShareSheet(
                                            statusKey = statusKey,
                                            accountType = accountType,
                                            shareUrl = shareUrl(),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(createdTime.coerceAtLeast(0L) * 1000L)),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    accountType = accountType,
                    statusKey = statusKey,
                ),
            ),
        accountType = accountType,
    )
}

internal fun ZhihuComment.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val accountType = AccountType.Specific(accountKey)
    val content = parseHtml(content).toUi(sourceLanguages)
    return UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = toAuthorProfile(accountKey),
        sourceLanguages = sourceLanguages.toImmutableList(),
        content = content,
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(likeCount),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(replyCount),
                ),
            ),
        poll = null,
        statusKey = MicroBlogKey("comment:$id", zhihuWebHost),
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(createdTime.coerceAtLeast(0L) * 1000L)),
        clickEvent = ClickEvent.Noop,
        accountType = accountType,
    )
}

private fun ZhihuContent.toAuthorProfile(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = MicroBlogKey(authorId, zhihuWebHost),
        handle = UiHandle(authorId, "zhihu"),
        avatar = authorAvatar,
        nameInternal = authorName.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Zhihu,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

private fun ZhihuComment.toAuthorProfile(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = MicroBlogKey(authorId, zhihuWebHost),
        handle = UiHandle(authorId, "zhihu"),
        avatar = authorAvatar,
        nameInternal = authorName.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Zhihu,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

internal fun ZhihuViewer.toUiProfile(
    accountKey: MicroBlogKey,
    requestedId: String? = null,
): UiProfile {
    val userId = id.ifBlank { requestedId ?: urlToken.ifBlank { "zhihu" } }
    val userKey = MicroBlogKey(requestedId ?: userId, zhihuWebHost)
    return UiProfile(
        key = userKey,
        handle = UiHandle(urlToken.ifBlank { userId }, "zhihu"),
        avatar = avatar,
        nameInternal = name.ifBlank { "知乎" }.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Zhihu,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = headline.takeIf { it.isNotBlank() }?.toUiPlainText(sourceLanguages),
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun ZhihuContent.shareUrl(): String =
    url ?: when (type) {
        ZhihuContentType.Answer -> "https://$zhihuWebHost/question/${questionId.orEmpty()}/answer/$id"
        ZhihuContentType.Article -> "https://zhuanlan.zhihu.com/p/$id"
        ZhihuContentType.Pin -> "https://$zhihuWebHost/pin/$id"
        ZhihuContentType.Question -> "https://$zhihuWebHost/question/$id"
    }

private val sourceLanguages = listOf("zh-CN")
