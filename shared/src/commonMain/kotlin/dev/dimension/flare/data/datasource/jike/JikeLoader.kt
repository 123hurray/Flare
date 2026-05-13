package dev.dimension.flare.data.datasource.jike

import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Loader for Jike API operations.
 * Implements the various loader interfaces required by Flare's architecture.
 */
internal class JikeLoader(
    val accountKey: MicroBlogKey,
    private val service: JikeService,
) : NotificationLoader,
    UserLoader,
    RelationLoader,
    EmojiLoader,
    PostLoader,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val supportedTypes: Set<RelationActionType> = emptySet()

    override suspend fun notificationBadgeCount(): Int = 0

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val response = service.getUserByUsername(username = uiHandle.normalizedRaw)
        response.data?.let { user ->
            return UiProfile(
                userKey = MicroBlogKey(user.id, accountKey.host),
                displayName = user.screenName.ifEmpty { user.username },
                handle = UiHandle(user.username, accountKey.host),
                description = user.bio,
                avatarUrl = user.avatarUrl,
                followersCount = user.followersCount,
                followingCount = user.followingCount,
                isVerified = user.isVerified,
            )
        }
        throw Exception("User not found")
    }

    override suspend fun userById(id: String): UiProfile {
        // Jike uses username-based lookup
        return userByHandleAndHost(UiHandle(id, accountKey.host))
    }

    override suspend fun currentUser(): UiProfile {
        val response = service.getSelfProfile()
        response.data?.let { user ->
            return UiProfile(
                userKey = MicroBlogKey(user.id, accountKey.host),
                displayName = user.screenName.ifEmpty { user.username },
                handle = UiHandle(user.username, accountKey.host),
                description = user.bio,
                avatarUrl = user.avatarUrl,
                followersCount = user.followersCount,
                followingCount = user.followingCount,
                isVerified = user.isVerified,
            )
        }
        throw LoginExpiredException(accountKey, PlatformType.Jike)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation =
        UiRelation(
            following = false,
            followedBy = false,
        )

    override suspend fun follow(userKey: MicroBlogKey) {
        // TODO: Implement follow
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        // TODO: Implement unfollow
    }

    override suspend fun emojis(): ImmutableList<UiEmoji> = emptyList<UiEmoji>().toImmutableList()

    override suspend fun customEmojis(): ImmutableMap<String, UiEmoji> = persistentMapOf()
}
