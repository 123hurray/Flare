package dev.dimension.flare.data.datasource.jike

import dev.dimension.flare.data.datasource.microblog.loader.*
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import org.koin.core.component.KoinComponent

internal class JikeLoader(
    val accountKey: MicroBlogKey,
    private val service: JikeService,
) : NotificationLoader, UserLoader, RelationLoader, EmojiLoader, PostLoader, KoinComponent {
    override val supportedTypes: Set<RelationActionType> = emptySet()
    override suspend fun notificationBadgeCount(): Int = 0

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val response = service.getUserProfile(username = uiHandle.normalizedRaw)
        val user = response.user ?: response.data ?: error("user not found")
        return user.toUiProfile(accountKey.host)
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

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val response = service.getPost(statusKey.id)
        val post = response.data ?: error("post not found")
        return post.toUiTimeline(accountKey)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {}
}
