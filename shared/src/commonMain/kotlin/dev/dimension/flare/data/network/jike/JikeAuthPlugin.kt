package dev.dimension.flare.data.network.jike

import dev.dimension.flare.model.jikeApiHost
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Configuration for Jike authentication plugin.
 */
internal class JikeAuthConfig {
    var accessTokenFlow: Flow<String>? = null
    var refreshTokenFlow: Flow<String>? = null
}

/**
 * Ktor client plugin for Jike API authentication.
 *
 * Jike API requires custom headers:
 * - x-access-token: Access token for API authentication
 * - x-refresh-token: Refresh token for session renewal
 *
 * Plus device/environment headers for API compatibility.
 */
internal val JikeAuthPlugin =
    createClientPlugin("JikeAuthPlugin", ::JikeAuthConfig) {
        val accessTokenFlow = pluginConfig.accessTokenFlow
        val refreshTokenFlow = pluginConfig.refreshTokenFlow

        onRequest { request, _ ->
            accessTokenFlow?.let { flow ->
                val token = flow.firstOrNull()
                if (token != null) {
                    request.headers.append("x-access-token", token)
                }
            }
            refreshTokenFlow?.let { flow ->
                val token = flow.firstOrNull()
                if (token != null) {
                    request.headers.append("x-refresh-token", token)
                }
            }
            // Required device headers (from open-jike/jike-sdk)
            request.headers.appendIfNotPresent("manufacturer", "Apple")
            request.headers.appendIfNotPresent("os", "ios")
            request.headers.appendIfNotPresent("os-version", "Version 14.7 (Build 18G5033e)")
            request.headers.appendIfNotPresent("Referer", "https://$jikeWebHost/")
        }
    }

private fun io.ktor.http.HeadersBuilder.appendIfNotPresent(
    name: String,
    value: String,
) {
    if (!contains(name)) {
        append(name, value)
    }
}
