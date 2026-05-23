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
        return service.userByUsername(uiHandle.normalizedRaw).toUiProfile(accountKey)
    }

    override suspend fun userById(id: String): UiProfile {
        return if (id == accountKey.id) {
            service.me().toUiProfile(accountKey)
        } else {
            service.userInfo(id).toUiProfile(accountKey)
        }
    }
}
