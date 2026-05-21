package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.jikeWebHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

private const val JIKE_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

/**
 * Configuration for Jike authentication plugin.
 */
internal class JikeAuthPlugin(
    private val accountKey: MicroBlogKey?,
    private val accessTokenFlow: Flow<String>?,
    private val refreshTokenFlow: Flow<String>?,
    private val deviceIdFlow: Flow<String?>?,
) {
    internal class Config {
        var accountKey: MicroBlogKey? = null
        var accessTokenFlow: Flow<String>? = null
        var refreshTokenFlow: Flow<String>? = null
        var deviceIdFlow: Flow<String?>? = null
    }

    companion object : HttpClientPlugin<Config, JikeAuthPlugin> {
        override val key = AttributeKey<JikeAuthPlugin>("JikeAuthPlugin")

        override fun prepare(block: Config.() -> Unit): JikeAuthPlugin {
            val config = Config().apply(block)
            return JikeAuthPlugin(
                accountKey = config.accountKey,
                accessTokenFlow = config.accessTokenFlow,
                refreshTokenFlow = config.refreshTokenFlow,
                deviceIdFlow = config.deviceIdFlow,
            )
        }

        override fun install(plugin: JikeAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend.Plugin).intercept { request ->
                plugin.addHeaders(request)
                execute(request).also { call ->
                    if (
                        call.response.status == HttpStatusCode.Unauthorized &&
                        plugin.accountKey != null
                    ) {
                        throw LoginExpiredException(plugin.accountKey, PlatformType.Jike)
                    }
                }
            }
        }
    }

    private suspend fun addHeaders(request: HttpRequestBuilder) {
        val isRefreshRequest = request.url.encodedPath.endsWith("app_auth_tokens.refresh")
        accessTokenFlow?.let { flow ->
            val token = flow.firstOrNull()
            if (token != null) {
                request.headers.append("x-jike-access-token", token)
            }
        }
        var hasDeviceId = false
        deviceIdFlow?.let { flow ->
            val deviceId = flow.firstOrNull()
            if (!deviceId.isNullOrBlank()) {
                request.headers.append("x-jike-device-id", deviceId)
                hasDeviceId = true
            }
        }
        if (!isRefreshRequest && accountKey != null && !hasDeviceId) {
            throw LoginExpiredException(accountKey, PlatformType.Jike)
        }
        if (isRefreshRequest) {
            refreshTokenFlow?.let { flow ->
                val token = flow.firstOrNull()
                if (token != null) {
                    request.headers.append("x-jike-refresh-token", token)
                }
            }
        }
        request.headers.appendIfNotPresent("platform", "web")
        request.headers.appendIfNotPresent("Referer", "https://$jikeWebHost/")
        request.headers.appendIfNotPresent("Origin", "https://$jikeWebHost")
        request.headers.appendIfNotPresent("User-Agent", JIKE_WEB_USER_AGENT)
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
