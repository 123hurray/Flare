package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.network.jike.api.JikeAuthApi
import dev.dimension.flare.data.network.jike.api.JikePostApi
import dev.dimension.flare.data.network.jike.api.JikeUserApi
import dev.dimension.flare.data.network.jike.api.createJikeAuthApi
import dev.dimension.flare.data.network.jike.api.createJikePostApi
import dev.dimension.flare.data.network.jike.api.createJikeUserApi
import dev.dimension.flare.model.jikeApiHost
import dev.dimension.flare.data.network.ktorfit
import kotlinx.coroutines.flow.Flow

private val baseUrl = "https://$jikeApiHost/"

private fun config(
    url: String = baseUrl,
    accessTokenFlow: Flow<String>? = null,
    refreshTokenFlow: Flow<String>? = null,
) = ktorfit(url) {
    install(JikeAuthPlugin) {
        this.accessTokenFlow = accessTokenFlow
        this.refreshTokenFlow = refreshTokenFlow
    }
}

/**
 * Service for interacting with the Jike (即刻) API.
 *
 * Wraps Ktorfit-generated API implementations for authentication,
 * user management, and post/timeline operations.
 */
internal class JikeService(
    accessTokenFlow: Flow<String>? = null,
    refreshTokenFlow: Flow<String>? = null,
) : JikeAuthApi by config(
    accessTokenFlow = accessTokenFlow,
    refreshTokenFlow = refreshTokenFlow,
).createJikeAuthApi(),
    JikeUserApi by config(
        accessTokenFlow = accessTokenFlow,
        refreshTokenFlow = refreshTokenFlow,
    ).createJikeUserApi(),
    JikePostApi by config(
        accessTokenFlow = accessTokenFlow,
        refreshTokenFlow = refreshTokenFlow,
    ).createJikePostApi()
