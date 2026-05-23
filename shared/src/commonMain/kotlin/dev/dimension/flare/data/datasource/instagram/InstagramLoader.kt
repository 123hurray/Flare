package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile

internal class InstagramLoader(
    private val accountKey: MicroBlogKey,
    private val service: InstagramService,
) : UserLoader {
    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val currentUser = service.me().toUiProfile(accountKey)
        if (
            uiHandle.normalizedRaw == currentUser.handle.normalizedRaw ||
            uiHandle.normalizedRaw == currentUser.key.id
        ) {
            return currentUser
        }
        throw UnsupportedOperationException("Instagram profile lookup is not supported in v1")
    }

    override suspend fun userById(id: String): UiProfile {
        val currentUser = service.me().toUiProfile(accountKey)
        if (id == accountKey.id || id == currentUser.key.id || id == currentUser.handle.normalizedRaw) {
            return currentUser
        }
        throw UnsupportedOperationException("Instagram profile lookup is not supported in v1")
    }
}
