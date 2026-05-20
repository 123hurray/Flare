package dev.dimension.flare.data.datasource.jike

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.jike.model.JikePost
import dev.dimension.flare.data.network.jike.model.JikeUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.Instant

internal fun JikeUser.toUiProfile(accountHost: String): UiProfile {
    val handle = username.ifEmpty { id }
    return UiProfile(
        key = MicroBlogKey(handle, accountHost),
        handle = UiHandle(handle, accountHost),
        avatar = avatarUrl.orEmpty(),
        nameInternal = screenName.ifEmpty { handle }.toUiPlainText(),
        platformType = PlatformType.Jike,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = (briefIntro ?: "").toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = statsCount?.followedCount?.toLong() ?: 0L,
                followsCount = statsCount?.followingCount?.toLong() ?: 0L,
                statusesCount = statsCount?.highlightedPersonalUpdates?.toLong() ?: 0L,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun JikePost.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(id, accountKey.host)
    val sourceLanguages = persistentListOf<String>()
    return UiTimelineV2.Post(
        platformType = PlatformType.Jike,
        images =
            pictures
                .mapNotNull { picture ->
                    picture.bestUrl.takeIf { it.isNotBlank() }?.let { url ->
                        UiMedia.Image(
                            url = url,
                            previewUrl = picture.thumbnailUrl ?: picture.smallPicUrl ?: url,
                            description = null,
                            height = picture.height.toFloat(),
                            width = picture.width.toFloat(),
                            sensitive = false,
                        )
                    }
                }.toPersistentList(),
        sensitive = false,
        contentWarning = null,
        user = user?.toUiProfile(accountKey.host),
        sourceLanguages = sourceLanguages,
        quote = persistentListOf(),
        content =
            Element("body")
                .apply {
                    appendChild(TextNode(content.orEmpty()))
                }.toUi(sourceLanguages),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(commentCount.toLong()),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Retweet,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                    count = UiNumber(repostCount.toLong()),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (isLiked) {
                                ActionMenu.Item.Text.Localized.Type.Unlike
                            } else {
                                ActionMenu.Item.Text.Localized.Type.Like
                            },
                        ),
                    count = UiNumber(likeCount.toLong()),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Share,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                    count = UiNumber(shareCount.toLong()),
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt =
            createdAt
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.toUi()
                ?: Instant.fromEpochMilliseconds(0L).toUi(),
        sourceChannel =
            topic?.let {
                UiTimelineV2.Post.SourceChannel(
                    id = it.id,
                    name = it.displayName,
                )
            },
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(accountKey),
    )
}
