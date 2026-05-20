package dev.dimension.flare.data.network.jike

import dev.dimension.flare.model.jikeApiHost
import dev.dimension.flare.model.jikeWebHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
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
    private val accessTokenFlow: Flow<String>?,
    private val refreshTokenFlow: Flow<String>?,
) {
    internal class Config {
        var accessTokenFlow: Flow<String>? = null
        var refreshTokenFlow: Flow<String>? = null
    }

    companion object : HttpClientPlugin<Config, JikeAuthPlugin> {
        override val key = AttributeKey<JikeAuthPlugin>("JikeAuthPlugin")

        override fun prepare(block: Config.() -> Unit): JikeAuthPlugin {
            val config = Config().apply(block)
            return JikeAuthPlugin(
                accessTokenFlow = config.accessTokenFlow,
                refreshTokenFlow = config.refreshTokenFlow,
            )
        }

        override fun install(plugin: JikeAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend.Plugin).intercept { request ->
                plugin.addHeaders(request)
                execute(request)
            }
        }
    }

    private suspend fun addHeaders(request: HttpRequestBuilder) {
        accessTokenFlow?.let { flow ->
            val token = flow.firstOrNull()
            if (token != null) {
                request.headers.append("x-jike-access-token", token)
            }
        }
        refreshTokenFlow?.let { flow ->
            val token = flow.firstOrNull()
            if (token != null) {
                request.headers.append("x-jike-refresh-token", token)
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
