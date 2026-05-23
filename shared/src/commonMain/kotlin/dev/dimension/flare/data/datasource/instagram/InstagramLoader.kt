package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class InstagramLoader(
    private val accountKey: MicroBlogKey,
    private val service: InstagramService,
    private val profileResolver: InstagramProfileResolver,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        return profileResolver.userByUsername(uiHandle.normalizedRaw)
    }

    override suspend fun userById(id: String): UiProfile {
        return profileResolver.userById(id)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun follow(userKey: MicroBlogKey) = Unit

    override suspend fun unfollow(userKey: MicroBlogKey) = Unit

    override suspend fun block(userKey: MicroBlogKey) = Unit

    override suspend fun unblock(userKey: MicroBlogKey) = Unit

    override suspend fun mute(userKey: MicroBlogKey) = Unit

    override suspend fun unmute(userKey: MicroBlogKey) = Unit

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val media = service.mediaInfo(statusKey.id)
        val userOverride =
            media.user
                ?.id
                ?.takeIf { it.isNotBlank() }
                ?.let { profileResolver.userById(it) }
        return media.toUiTimeline(accountKey, userOverride = userOverride)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) = Unit
}
