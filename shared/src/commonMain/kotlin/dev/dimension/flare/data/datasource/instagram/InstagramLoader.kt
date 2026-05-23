package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

internal class InstagramLoader(
    private val accountKey: MicroBlogKey,
    private val service: InstagramService,
) : UserLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        return service.userByUsername(uiHandle.normalizedRaw).toUiProfile(accountKey)
    }

    override suspend fun userById(id: String): UiProfile {
        return if (id == accountKey.id) {
            service.me().toUiProfile(accountKey)
        } else {
            service.userInfo(id).toUiProfile(accountKey)
        }
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun follow(userKey: MicroBlogKey) = Unit

    override suspend fun unfollow(userKey: MicroBlogKey) = Unit

    override suspend fun block(userKey: MicroBlogKey) = Unit

    override suspend fun unblock(userKey: MicroBlogKey) = Unit

    override suspend fun mute(userKey: MicroBlogKey) = Unit

    override suspend fun unmute(userKey: MicroBlogKey) = Unit
}
