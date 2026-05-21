package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.network.jike.api.JikeAuthApi
import dev.dimension.flare.data.network.jike.api.JikePostApi
import dev.dimension.flare.data.network.jike.api.JikeUserApi
import dev.dimension.flare.data.network.jike.api.createJikeAuthApi
import dev.dimension.flare.data.network.jike.api.createJikePostApi
import dev.dimension.flare.data.network.jike.api.createJikeUserApi
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.jikeApiHost
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow

private val baseUrl = "https://$jikeApiHost/"

private fun config(
    url: String = baseUrl,
    accountKey: MicroBlogKey? = null,
    accessTokenFlow: Flow<String>? = null,
    refreshTokenFlow: Flow<String>? = null,
    deviceIdFlow: Flow<String?>? = null,
) = ktorfit(url) {
    install(JikeAuthPlugin) {
        this.accountKey = accountKey
        this.accessTokenFlow = accessTokenFlow
        this.refreshTokenFlow = refreshTokenFlow
        this.deviceIdFlow = deviceIdFlow
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized && accountKey != null) {
                throw LoginExpiredException(accountKey, PlatformType.Jike)
            }
        }
    }
}

/**
 * Service for interacting with the Jike (即刻) API.
 *
 * Wraps Ktorfit-generated API implementations for authentication,
 * user management, and post/timeline operations.
 */
internal class JikeService(
    accountKey: MicroBlogKey? = null,
    accessTokenFlow: Flow<String>? = null,
    refreshTokenFlow: Flow<String>? = null,
    deviceIdFlow: Flow<String?>? = null,
) : JikeAuthApi by config(
    accountKey = accountKey,
    accessTokenFlow = accessTokenFlow,
    refreshTokenFlow = refreshTokenFlow,
    deviceIdFlow = deviceIdFlow,
).createJikeAuthApi(),
    JikeUserApi by config(
        accountKey = accountKey,
        accessTokenFlow = accessTokenFlow,
        refreshTokenFlow = refreshTokenFlow,
        deviceIdFlow = deviceIdFlow,
    ).createJikeUserApi(),
    JikePostApi by config(
        accountKey = accountKey,
        accessTokenFlow = accessTokenFlow,
        refreshTokenFlow = refreshTokenFlow,
        deviceIdFlow = deviceIdFlow,
    ).createJikePostApi()
