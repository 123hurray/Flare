package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InstagramProfileResolver(
    private val accountKey: MicroBlogKey,
    private val service: InstagramService,
) {
    private val mutex = Mutex()
    private val profilesById = mutableMapOf<String, UiProfile>()
    private val profilesByUsername = mutableMapOf<String, UiProfile>()

    suspend fun userById(id: String): UiProfile =
        mutex.withLock {
            profilesById[id]?.let { return@withLock it }
            val profile =
                if (id == accountKey.id) {
                    service.me().toUiProfile(accountKey)
                } else {
                    service.userInfo(id).toUiProfile(accountKey)
                }
            profile.remember()
        }

    suspend fun userByUsername(username: String): UiProfile =
        mutex.withLock {
            val normalized = username.trim().removePrefix("@")
            profilesByUsername[normalized]?.let { return@withLock it }
            service.userByUsername(normalized).toUiProfile(accountKey).remember()
        }

    private fun UiProfile.remember(): UiProfile {
        profilesById[key.id] = this
        profilesByUsername[handle.normalizedRaw] = this
        return this
    }
}
