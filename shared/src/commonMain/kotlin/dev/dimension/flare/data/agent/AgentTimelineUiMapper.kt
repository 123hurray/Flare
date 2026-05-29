package dev.dimension.flare.data.agent

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock
import kotlin.time.Instant

public fun AgentTimelineItem.toUiTimelinePost(): UiTimelineV2.Post {
    val platformType = platform.toAgentPlatformType()
    val key = statusKey ?: MicroBlogKey(id = id, host = platformType.name)
    val account = accountType ?: AccountType.GuestHost(key.host)
    val author = authorHandle?.takeIf { it.isNotBlank() } ?: authorName.orEmpty()
    val userKey = MicroBlogKey(id = author.ifBlank { key.id }, host = key.host)
    return UiTimelineV2.Post(
        message = null,
        platformType = platformType,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user =
            UiProfile(
                key = userKey,
                handle = UiHandle(raw = author, host = key.host),
                avatar = "",
                nameInternal = (authorName ?: author).toUiPlainText(),
                platformType = platformType,
                clickEvent = ClickEvent.Noop,
                banner = null,
                description = null,
                matrices =
                    UiProfile.Matrices(
                        fansCount = 0,
                        followsCount = 0,
                        statusesCount = 0,
                    ),
                mark = persistentListOf(),
                bottomContent = null,
            ),
        quote = persistentListOf(),
        content = text.toUiPlainText(),
        actions = persistentListOf(),
        poll = null,
        statusKey = key,
        card = null,
        createdAt = createdAtEpochMillis?.let { Instant.fromEpochMilliseconds(it).toUi() } ?: Clock.System.now().toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        parents = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        accountType = account,
        itemKey = id,
    )
}

private fun String?.toAgentPlatformType(): PlatformType =
    when (this?.lowercase()) {
        "x", "twitter", "xqt" -> PlatformType.xQt
        "微博", "weibo", "vvo" -> PlatformType.VVo
        "即刻", "jike" -> PlatformType.Jike
        "小红书", "xiaohongshu" -> PlatformType.Xiaohongshu
        "instagram" -> PlatformType.Instagram
        "懂球帝", "dongqiudi" -> PlatformType.Dongqiudi
        "知乎", "zhihu" -> PlatformType.Zhihu
        "bluesky" -> PlatformType.Bluesky
        "mastodon" -> PlatformType.Mastodon
        "misskey" -> PlatformType.Misskey
        "nostr" -> PlatformType.Nostr
        else -> PlatformType.xQt
    }
