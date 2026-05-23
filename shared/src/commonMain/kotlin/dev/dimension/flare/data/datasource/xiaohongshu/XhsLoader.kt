package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

internal class XhsLoader(
    private val accountKey: MicroBlogKey,
    private val service: XhsService,
) : UserLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = userById(uiHandle.normalizedRaw)

    override suspend fun userById(id: String): UiProfile {
        if (id == accountKey.id) {
            return requireNotNull(service.me().data) { "Xiaohongshu profile is empty" }.toUiProfile(accountKey)
        }
        return requireNotNull(service.userInfo(id).data) { "Xiaohongshu profile is empty" }
            .toUiProfile(accountKey, requestedUserId = id)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val data = service.userInfo(userKey.id, mapLoginExpired = false).data
        val status = data?.basicInfo?.followStatus ?: data?.followStatus
        return UiRelation(
            following = status in xhsFollowingStatuses,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.followUser(userKey.id)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollowUser(userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) {}

    override suspend fun unblock(userKey: MicroBlogKey) {}

    override suspend fun mute(userKey: MicroBlogKey) {}

    override suspend fun unmute(userKey: MicroBlogKey) {}
}

private val xhsFollowingStatuses = setOf("follows", "followed", "both", "same")
