package dev.dimension.flare.data.datasource.dongqiudi

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.dongqiudi.DongqiudiArticle
import dev.dimension.flare.data.network.dongqiudi.DongqiudiAttachment
import dev.dimension.flare.data.network.dongqiudi.DongqiudiComment
import dev.dimension.flare.data.network.dongqiudi.DongqiudiCommentUser
import dev.dimension.flare.data.network.dongqiudi.DongqiudiUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.dongqiudiWebHost
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

internal fun DongqiudiArticle.toUiTimeline(
    accountKey: MicroBlogKey,
    detail: Boolean,
): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(id, dongqiudiWebHost)
    val accountType = AccountType.Specific(accountKey)
    val content =
        bodyHtml
            ?.toArticleHtml()
            ?.let { parseHtml("<h2>$title</h2>$it").toUi(sourceLanguages) }
            ?: listOf(title, description)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .toUiPlainText(sourceLanguages)
    val images =
        if (detail) {
            emptyList()
        } else {
            thumbnail?.let {
                listOf(
                    UiMedia.Image(
                        url = it,
                        previewUrl = it,
                        description = title,
                        height = 0f,
                        width = 0f,
                        sensitive = false,
                    ),
                )
            }.orEmpty()
        }
    return UiTimelineV2.Post(
        platformType = PlatformType.Dongqiudi,
        images = images.toImmutableList(),
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
                    count = UiNumber(commentsTotal),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                accountType = accountType,
                                statusKey = statusKey,
                            ),
                        ),
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
                                            shareUrl = shareUrl ?: "https://$dongqiudiWebHost/article/$id",
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(showTime.coerceAtLeast(0L) * 1000L)),
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

internal fun DongqiudiComment.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val accountType = AccountType.Specific(accountKey)
    return UiTimelineV2.Post(
        platformType = PlatformType.Dongqiudi,
        images = attachments.map { it.toUiMedia() }.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user.toUiProfile(accountKey),
        sourceLanguages = sourceLanguages.toImmutableList(),
        content = content.toUiPlainText(sourceLanguages),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(upCount),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(replyTotal),
                ),
            ),
        poll = null,
        statusKey = MicroBlogKey("comment:$id", dongqiudiWebHost),
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(showTime.coerceAtLeast(0L) * 1000L)),
        clickEvent = ClickEvent.Noop,
        accountType = accountType,
    )
}

private fun DongqiudiArticle.toAuthorProfile(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = MicroBlogKey(userId, dongqiudiWebHost),
        handle = UiHandle(userId, "dongqiudi"),
        avatar = authorAvatar,
        nameInternal = writer.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Dongqiudi,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

internal fun DongqiudiUser.toUiProfile(
    accountKey: MicroBlogKey,
    requestedId: String? = null,
): UiProfile {
    val userId = id.ifBlank { requestedId ?: "dongqiudi" }
    val userKey = MicroBlogKey(userId, dongqiudiWebHost)
    return UiProfile(
        key = userKey,
        handle = UiHandle(userId, "dongqiudi"),
        avatar = avatar,
        nameInternal = name.ifBlank { "懂球帝" }.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Dongqiudi,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = description.takeIf { it.isNotBlank() }?.toUiPlainText(sourceLanguages),
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices =
            UiProfile.Matrices(
                fansCount = fansCount,
                followsCount = followsCount,
                statusesCount = statusesCount,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun DongqiudiCommentUser.toUiProfile(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = MicroBlogKey(id, dongqiudiWebHost),
        handle = UiHandle(id, "dongqiudi"),
        avatar = avatar,
        nameInternal = name.toUiPlainText(sourceLanguages),
        platformType = PlatformType.Dongqiudi,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        sourceLanguages = sourceLanguages.toImmutableList(),
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

private fun DongqiudiAttachment.toUiMedia(): UiMedia.Image =
    UiMedia.Image(
        url = url,
        previewUrl = previewUrl,
        description = null,
        height = height.toFloat(),
        width = width.toFloat(),
        sensitive = false,
    )

private fun String.toArticleHtml(): String =
    replace("data-src=", "src=")
        .replace(
            Regex("<p>\\s*(<img\\b[^>]*?/?>)\\s*</p>", RegexOption.IGNORE_CASE),
            "<figure>${'$'}1</figure>",
        )

private val sourceLanguages = listOf("zh-CN")
