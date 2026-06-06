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
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

internal fun ZhihuContent.toUiTimeline(
    accountKey: MicroBlogKey,
    detail: Boolean,
    includeTitle: Boolean = true,
): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(statusId, zhihuWebHost)
    val accountType = AccountType.Specific(accountKey)
    val content = toUiContent(accountKey, detail, includeTitle)
    val media =
        if (detail) {
            content.imageUrls
        } else {
            imageUrls.take(6)
        }.map {
            UiMedia.Image(
                url = it,
                previewUrl = it,
                description = null,
                height = 0f,
                width = 0f,
                sensitive = false,
            )
        }.toImmutableList()
    val primaryCount = if (type == ZhihuContentType.Question) answerCount else commentCount
    val secondaryCount = if (type == ZhihuContentType.Question) followerCount else voteupCount
    return UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = media,
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
                    count = UiNumber(primaryCount),
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
                    count = UiNumber(secondaryCount),
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
        sourceChannel =
            if (detail && type in setOf(ZhihuContentType.Answer, ZhihuContentType.Question)) {
                questionMetaText()
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        UiTimelineV2.Post.SourceChannel(
                            id = "zhihu-question-meta:$statusId",
                            name = it,
                        )
                    }
            } else {
                null
            },
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

private fun ZhihuContent.toUiContent(
    accountKey: MicroBlogKey,
    detail: Boolean,
    includeTitle: Boolean,
): UiRichText {
    val titleContent = if (includeTitle) toTitleContent(accountKey, detail) else null
    val bodyContent =
        contentHtml
            ?.takeIf { it.isNotBlank() }
            ?.let { parseHtml(it).toUi(sourceLanguages) }

    if (bodyContent != null) {
        val renderContents = listOfNotNull(titleContent) + bodyContent.renderRuns
        return uiRichTextOf(
            renderRuns = renderContents,
            raw = listOf(title, bodyContent.raw).filter { it.isNotBlank() }.joinToString("\n\n"),
            innerText = listOf(title, bodyContent.innerText).filter { it.isNotBlank() }.joinToString("\n\n"),
            imageUrls = bodyContent.imageUrls,
            sourceLanguages = sourceLanguages,
        )
    }

    val renderContents =
        listOfNotNull(
            titleContent,
            excerpt
                .takeIf { it.isNotBlank() }
                ?.let {
                    RenderContent.Text(
                        runs = persistentListOf(RenderRun.Text(it)),
                    )
                },
        )
    return uiRichTextOf(
        renderRuns = renderContents,
        raw = listOf(title, excerpt).filter { it.isNotBlank() }.joinToString("\n\n"),
        innerText = listOf(title, excerpt).filter { it.isNotBlank() }.joinToString("\n\n"),
        sourceLanguages = sourceLanguages,
    )
}

private fun ZhihuContent.toQuestionLink(accountKey: MicroBlogKey): String? {
    val id = questionId?.takeIf { it.isNotBlank() } ?: return null
    if (type == ZhihuContentType.Question) return null
    return DeeplinkRoute.Status.Detail(
        accountType = AccountType.Specific(accountKey),
        statusKey = MicroBlogKey("question:$id", zhihuWebHost),
    ).toUri()
}

private fun ZhihuContent.toTitleContent(
    accountKey: MicroBlogKey,
    detail: Boolean,
): RenderContent.Text? {
    if (type == ZhihuContentType.Pin) return null
    if (title.isBlank()) return null
    return RenderContent.Text(
        runs =
            persistentListOf(
                RenderRun.Text(
                    text = title,
                    style =
                        RenderTextStyle(
                            link = if (detail) toQuestionLink(accountKey) else null,
                            bold = true,
                        ),
                ),
            ),
        block = RenderBlockStyle(headingLevel = if (detail) 5 else 4),
    )
}

internal fun ZhihuComment.toUiTimeline(
    accountKey: MicroBlogKey,
    parentStatusId: String,
    includeInlineChildComments: Boolean = true,
): UiTimelineV2.Post {
    val accountType = AccountType.Specific(accountKey)
    val content = parseHtml(content).toUi(sourceLanguages)
    val statusKey = MicroBlogKey("comment:$parentStatusId:$id", zhihuWebHost)
    val detailStatusKey =
        if (replyCount > 0L) {
            statusKey
        } else {
            MicroBlogKey(parentStatusId, zhihuWebHost)
        }
    return UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = toAuthorProfile(accountKey),
        quote =
            if (includeInlineChildComments) {
                childComments
                    .map { it.toUiTimeline(accountKey, parentStatusId, includeInlineChildComments = false) }
                    .toImmutableList()
            } else {
                persistentListOf()
            },
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
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                accountType = accountType,
                                statusKey = detailStatusKey,
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
                    statusKey = detailStatusKey,
                ),
            ),
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
        description = authorHeadline.takeIf { it.isNotBlank() }?.toUiPlainText(sourceLanguages),
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
        matrices =
            UiProfile.Matrices(
                fansCount = followerCount,
                followsCount = followingCount,
                statusesCount = statusesCount,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun ZhihuContent.shareUrl(): String =
    url ?: when (type) {
        ZhihuContentType.Answer -> "https://$zhihuWebHost/question/${questionId.orEmpty()}/answer/$id"
        ZhihuContentType.Article -> "https://zhuanlan.zhihu.com/p/$id"
        ZhihuContentType.Daily -> "https://daily.zhihu.com/story/$id"
        ZhihuContentType.Pin -> "https://$zhihuWebHost/pin/$id"
        ZhihuContentType.Question -> "https://$zhihuWebHost/question/$id"
    }

private fun ZhihuContent.questionMetaText(): String =
    buildList {
        add("知乎")
        answerCount.takeIf { it > 0 }?.let { add("${it} 个回答") }
        followerCount.takeIf { it > 0 }?.let { add("${it} 个关注") }
    }.joinToString(" · ").takeIf { it.isNotBlank() }?.plus(" ›").orEmpty()

private val sourceLanguages = listOf("zh-CN")
