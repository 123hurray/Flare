package dev.dimension.flare.data.datasource.jike

import dev.dimension.flare.data.datasource.microblog.loader.*
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.koin.core.component.KoinComponent

internal class JikeLoader(
    val accountKey: MicroBlogKey,
    private val service: JikeService,
) : NotificationLoader, UserLoader, RelationLoader, EmojiLoader, PostLoader, KoinComponent {
    override val supportedTypes: Set<RelationActionType> = emptySet()
    override suspend fun notificationBadgeCount(): Int = 0

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val response = service.getUserByUsername(username = uiHandle.normalizedRaw)
        val user = response.data ?: error("user not found")
        val userKey = MicroBlogKey(user.id, accountKey.host)
        return UiProfile(
            key = userKey,
            handle = UiHandle(user.username, accountKey.host),
            avatar = user.avatarUrl.orEmpty(),
            nameInternal = user.screenName.ifEmpty { user.username }.toUiPlainText(),
            platformType = PlatformType.Jike,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = user.briefIntro?.toUiPlainText(),
            matrices = UiProfile.Matrices(
                fansCount = user.statsCount?.followedCount?.toLong() ?: 0L,
                followsCount = user.statsCount?.followingCount?.toLong() ?: 0L,
                statusesCount = 0,
            ),
            mark = persistentListOf(),
            bottomContent = null,
        )
    }

    override suspend fun userById(id: String): UiProfile =
        userByHandleAndHost(UiHandle(id, accountKey.host))

    override suspend fun relation(userKey: MicroBlogKey): UiRelation =
        UiRelation(following = false)

    override suspend fun follow(userKey: MicroBlogKey) {}
    override suspend fun unfollow(userKey: MicroBlogKey) {}
    override suspend fun block(userKey: MicroBlogKey) {}
    override suspend fun unblock(userKey: MicroBlogKey) {}
    override suspend fun mute(userKey: MicroBlogKey) {}
    override suspend fun unmute(userKey: MicroBlogKey) {}

    override suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>> = persistentMapOf()

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 = error("not implemented")
    override suspend fun deleteStatus(statusKey: MicroBlogKey) {}
}
