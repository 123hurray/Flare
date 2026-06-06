package dev.dimension.flare.data.datasource.dongqiudi

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.dongqiudi.DongqiudiArticle
import dev.dimension.flare.data.network.dongqiudi.DongqiudiArticleMedia
import dev.dimension.flare.data.network.dongqiudi.DongqiudiAttachment
import dev.dimension.flare.data.network.dongqiudi.DongqiudiComment
import dev.dimension.flare.data.network.dongqiudi.DongqiudiCommentUser
import dev.dimension.flare.data.network.dongqiudi.DongqiudiGifMedia
import dev.dimension.flare.data.network.dongqiudi.DongqiudiRelatedEntity
import dev.dimension.flare.data.network.dongqiudi.DongqiudiUser
import dev.dimension.flare.data.network.dongqiudi.DongqiudiUserType
import dev.dimension.flare.data.network.dongqiudi.DongqiudiVideoMedia
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
            ?.let {
                parseHtml(
                    if (detail) {
                        it
                    } else {
                        "<h5>$title</h5>$it"
                    },
                ).toUi(sourceLanguages)
            }
            ?: listOf(if (detail) "" else title, description)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .toUiPlainText(sourceLanguages)
    val images =
        if (detail) {
            medias.filterIsInstance<DongqiudiVideoMedia>().map { it.toUiMedia() }
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
        contentWarning = if (detail) title.toUiPlainText(sourceLanguages) else null,
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
        relatedProfiles =
            if (detail) {
                relatedEntities.map { it.toUiProfile(accountKey) }.toImmutableList()
            } else {
                persistentListOf()
            },
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
    val statusKey = MicroBlogKey("comment:$articleId:$id", dongqiudiWebHost)
    val detailStatusKey =
        if (replyTotal > 0L) {
            statusKey
        } else {
            MicroBlogKey(articleId, dongqiudiWebHost)
        }
    return UiTimelineV2.Post(
        platformType = PlatformType.Dongqiudi,
        images = attachments.map { it.toUiMedia() }.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user.toUiProfile(accountKey),
        sourceLanguages = sourceLanguages.toImmutableList(),
        content = parseHtml(content).toUi(sourceLanguages),
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
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(showTime.coerceAtLeast(0L) * 1000L)),
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
    val userId = profileKeyId(requestedId)
    val userKey = MicroBlogKey(userId, dongqiudiWebHost)
    val profileDescription =
        listOfNotNull(
            description.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    return UiProfile(
        key = userKey,
        handle =
            UiHandle(
                raw =
                    when (type) {
                        DongqiudiUserType.User -> id.ifBlank { requestedId ?: "dongqiudi" }
                        DongqiudiUserType.Player,
                        DongqiudiUserType.Team,
                        -> ""
                    },
                host = "dongqiudi",
            ),
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
        description = profileDescription.takeIf { it.isNotBlank() }?.toUiPlainText(sourceLanguages),
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

private fun DongqiudiRelatedEntity.toUiProfile(accountKey: MicroBlogKey): UiProfile =
    DongqiudiUser(
        id = id,
        name = name,
        avatar = avatar,
        description = "",
        fansCount = 0L,
        followsCount = 0L,
        statusesCount = 0L,
        type = type,
    ).toUiProfile(accountKey)

internal data class DongqiudiProfileEntity(
    val type: DongqiudiUserType,
    val id: String,
    val name: String,
)

internal fun DongqiudiUser.profileKeyId(requestedId: String? = null): String =
    when (type) {
        DongqiudiUserType.User -> id.ifBlank { requestedId ?: "dongqiudi" }
        DongqiudiUserType.Player -> "dqdp_${id}_${name.ifBlank { id }}"
        DongqiudiUserType.Team -> "dqdt_${id}_${name.ifBlank { id }}"
    }

internal fun String.dongqiudiProfileEntity(): DongqiudiProfileEntity? {
    val type =
        when {
            startsWith("dqdp_") -> DongqiudiUserType.Player
            startsWith("dqdt_") -> DongqiudiUserType.Team
            else -> return null
        }
    val payload = removePrefix(if (type == DongqiudiUserType.Player) "dqdp_" else "dqdt_")
    val separator = payload.indexOf('_')
    if (separator <= 0) return null
    val id = payload.substring(0, separator).takeIf { it.isNotBlank() } ?: return null
    val name = payload.substring(separator + 1).takeIf { it.isNotBlank() } ?: id
    return DongqiudiProfileEntity(
        type = type,
        id = id,
        name = name,
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

private fun DongqiudiArticleMedia.toUiMedia(): UiMedia =
    when (this) {
        is DongqiudiVideoMedia ->
            UiMedia.Video(
                url = url,
                thumbnailUrl = thumb,
                description = title,
                height = height.toFloat(),
                width = width.toFloat(),
                variants =
                    listOf(
                        UiMedia.VideoVariant(
                            url = url,
                            width = width.toFloat(),
                            height = height.toFloat(),
                        ),
                    ),
            )
        is DongqiudiGifMedia ->
            UiMedia.Gif(
                url = url,
                previewUrl = preview,
                description = title,
                height = height.toFloat(),
                width = width.toFloat(),
            )
    }

private fun String.toArticleHtml(): String =
    replace(dongqiudiVideoNodeRegex, "")
        .replace(dongqiudiImageTagRegex) { match ->
            val attrs = match.value.htmlAttrs()
            val gifUrl =
                listOf(
                    attrs["data-gif-src"],
                    attrs["gif-src"],
                    attrs["data-original-gif"],
                    attrs["src"]?.takeIf { it.substringBefore("?").endsWith(".gif", ignoreCase = true) },
                    attrs["data-src"]?.takeIf { it.substringBefore("?").endsWith(".gif", ignoreCase = true) },
                ).firstOrNull { !it.isNullOrBlank() }?.decodeHtmlEntities()
            if (gifUrl.isNullOrBlank()) {
                match.value
            } else {
                val alt = attrs["alt"]?.decodeHtmlEntities()?.takeIf { it.isNotBlank() } ?: "GIF"
                """<img src="${gifUrl.escapeHtml()}#flare-gif" alt="${alt.escapeHtml()}">"""
            }
        }
        .replace("data-src=", "src=")
        .replace(
            Regex("<p>\\s*(<img\\b[^>]*?/?>)\\s*</p>", RegexOption.IGNORE_CASE),
            "<figure>${'$'}1</figure>",
        )

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private val dongqiudiVideoNodeRegex =
    Regex("""<div\b[^>]*class\s*=\s*["'][^"']*\bvideo\b[^"']*["'][^>]*>\s*</div>""", RegexOption.IGNORE_CASE)

private val dongqiudiImageTagRegex =
    Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

private val htmlAttrRegex =
    Regex("""([\w:-]+)\s*=\s*(['"])(.*?)\2""")

private fun String.htmlAttrs(): Map<String, String> =
    htmlAttrRegex
        .findAll(this)
        .associate { it.groupValues[1].lowercase() to it.groupValues[3] }

private fun String.decodeHtmlEntities(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

private val sourceLanguages = listOf("zh-CN")
