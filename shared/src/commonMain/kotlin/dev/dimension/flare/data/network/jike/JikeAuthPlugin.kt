package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.jikeWebHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val JIKE_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"
private const val JIKE_API_BASE_URL = "https://api.ruguoapp.com/"

/**
 * Configuration for Jike authentication plugin.
 */
internal class JikeAuthPlugin(
    private val accountKey: MicroBlogKey?,
    private val accessTokenFlow: Flow<String>?,
    private val refreshTokenFlow: Flow<String>?,
    private val deviceIdFlow: Flow<String?>?,
    private val onTokenRefresh: (suspend (accessToken: String, refreshToken: String) -> Unit)?,
) {
    internal class Config {
        var accountKey: MicroBlogKey? = null
        var accessTokenFlow: Flow<String>? = null
        var refreshTokenFlow: Flow<String>? = null
        var deviceIdFlow: Flow<String?>? = null
        var onTokenRefresh: (suspend (accessToken: String, refreshToken: String) -> Unit)? = null
    }

    companion object : HttpClientPlugin<Config, JikeAuthPlugin> {
        override val key = AttributeKey<JikeAuthPlugin>("JikeAuthPlugin")
        private val json =
            Json {
                ignoreUnknownKeys = true
            }

        override fun prepare(block: Config.() -> Unit): JikeAuthPlugin {
            val config = Config().apply(block)
            return JikeAuthPlugin(
                accountKey = config.accountKey,
                accessTokenFlow = config.accessTokenFlow,
                refreshTokenFlow = config.refreshTokenFlow,
                deviceIdFlow = config.deviceIdFlow,
                onTokenRefresh = config.onTokenRefresh,
            )
        }

        override fun install(plugin: JikeAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend.Plugin).intercept { request ->
                plugin.addHeaders(request)
                val isRefreshRequest = request.url.encodedPath.endsWith("app_auth_tokens.refresh")
                var call = execute(request)
                if (
                    call.response.status == HttpStatusCode.Unauthorized &&
                    plugin.accountKey != null &&
                    !isRefreshRequest
                ) {
                    plugin.refreshTokens(scope)?.let { tokens ->
                        request.headers.remove("x-jike-access-token")
                        request.headers.append("x-jike-access-token", tokens.accessToken)
                        call = execute(request)
                    }
                }
                call.also { result ->
                    logJikeAuth(
                        "response path=${request.url.encodedPath} " +
                            "status=${result.response.status.value} " +
                            "contentType=${result.response.headers["Content-Type"] ?: "null"}",
                    )
                    if (
                        result.response.status == HttpStatusCode.Unauthorized &&
                        plugin.accountKey != null
                    ) {
                        throw LoginExpiredException(plugin.accountKey, PlatformType.Jike)
                    }
                }
            }
        }
    }

    private suspend fun refreshTokens(scope: HttpClient): RefreshedJikeTokens? {
        val refreshToken = refreshTokenFlow?.firstOrNull().takeUnless { it.isNullOrBlank() } ?: return null
        return runCatching {
            val response =
                scope.post("${JIKE_API_BASE_URL}app_auth_tokens.refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            if (response.status != HttpStatusCode.OK) {
                return null
            }
            val tokens = json.decodeFromString<RefreshedJikeTokens>(response.bodyAsText())
            if (tokens.accessToken.isBlank() || tokens.refreshToken.isBlank()) {
                null
            } else {
                onTokenRefresh?.invoke(tokens.accessToken, tokens.refreshToken)
                tokens
            }
        }.getOrElse {
            logJikeAuth("refresh failed: ${it.message ?: it::class.simpleName}")
            null
        }
    }

    private suspend fun addHeaders(request: HttpRequestBuilder) {
        val isRefreshRequest = request.url.encodedPath.endsWith("app_auth_tokens.refresh")
        var accessTokenLog = "missing"
        accessTokenFlow?.let { flow ->
            val token = flow.firstOrNull()
            if (token != null) {
                request.headers.append("x-jike-access-token", token)
                accessTokenLog = token.redactedTokenLog()
            }
        }
        var deviceIdLog = "missing"
        deviceIdFlow?.let { flow ->
            val deviceId = flow.firstOrNull()
            if (!deviceId.isNullOrBlank()) {
                request.headers.append("x-jike-device-id", deviceId)
                deviceIdLog = deviceId.redactedDeviceIdLog()
            }
        }
        var refreshTokenLog = "missing"
        if (isRefreshRequest) {
            refreshTokenFlow?.let { flow ->
                val token = flow.firstOrNull()
                if (token != null) {
                    request.headers.append("x-jike-refresh-token", token)
                    refreshTokenLog = token.redactedTokenLog()
                }
            }
        }
        request.headers.appendIfNotPresent("platform", "web")
        request.headers.appendIfNotPresent("Referer", "https://$jikeWebHost/")
        request.headers.appendIfNotPresent("Origin", "https://$jikeWebHost")
        request.headers.appendIfNotPresent("User-Agent", JIKE_WEB_USER_AGENT)
        logJikeAuth(
            "request path=${request.url.encodedPath} " +
                "refreshRequest=$isRefreshRequest " +
                "accountKeyPresent=${accountKey != null} " +
                "access=$accessTokenLog refresh=$refreshTokenLog device=$deviceIdLog",
        )
    }
}

@Serializable
private data class RefreshedJikeTokens(
    @SerialName("x-jike-access-token")
    val accessToken: String = "",
    @SerialName("x-jike-refresh-token")
    val refreshToken: String = "",
)

private fun io.ktor.http.HeadersBuilder.appendIfNotPresent(
    name: String,
    value: String,
) {
    if (!contains(name)) {
        append(name, value)
    }
}

private fun String.redactedTokenLog(): String = "present(len=$length,fp=${hashCode().toUInt().toString(16)})"

private fun String.redactedDeviceIdLog(): String {
    val tail = takeLast(4)
    return "present(len=$length,tail=$tail)"
}

private fun logJikeAuth(message: String) {
    val line = "JikeAuth: $message"
    println(line)
    DebugRepository.log(line)
}
