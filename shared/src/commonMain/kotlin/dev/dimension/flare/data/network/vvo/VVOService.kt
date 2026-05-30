package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.vvo.api.ConfigApi
import dev.dimension.flare.data.network.vvo.api.StatusApi
import dev.dimension.flare.data.network.vvo.api.TimelineApi
import dev.dimension.flare.data.network.vvo.api.UserApi
import dev.dimension.flare.data.network.vvo.api.createConfigApi
import dev.dimension.flare.data.network.vvo.api.createStatusApi
import dev.dimension.flare.data.network.vvo.api.createTimelineApi
import dev.dimension.flare.data.network.vvo.api.createUserApi
import dev.dimension.flare.data.network.vvo.model.EmojiData
import dev.dimension.flare.data.network.vvo.model.UploadResponse
import dev.dimension.flare.model.vvoHost
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.minutes

private val baseUrl = "https://$vvoHost/"

private fun config(
    url: String = baseUrl,
    chocolateFlow: Flow<String>,
    onChocolateUpdated: ((String) -> Unit)? = null,
) = ktorfit(url) {
    install(VVOHeaderPlugin) {
        this.chocolateFlow = chocolateFlow
        this.onChocolateUpdated = onChocolateUpdated
    }
}

internal class VVOService(
    private val chocolateFlow: Flow<String>,
    onChocolateUpdated: ((String) -> Unit)? = null,
) : TimelineApi by config(chocolateFlow = chocolateFlow, onChocolateUpdated = onChocolateUpdated).createTimelineApi(),
    UserApi by config(chocolateFlow = chocolateFlow, onChocolateUpdated = onChocolateUpdated).createUserApi(),
    ConfigApi by config(chocolateFlow = chocolateFlow, onChocolateUpdated = onChocolateUpdated).createConfigApi(),
    StatusApi by config(chocolateFlow = chocolateFlow, onChocolateUpdated = onChocolateUpdated).createStatusApi() {
    companion object {
        fun checkChocolates(chocolate: String): Boolean =
            chocolate
                .split(';')
                .mapNotNull {
                    val res = it.split('=')
                    val key = res.getOrNull(0)?.trim()
                    val value = res.getOrNull(1)?.trim()
                    if (key != null && value != null) {
                        key to value
                    } else {
                        null
                    }
                }.toMap()
                .let {
                    it.containsKey("MLOGIN") && it["MLOGIN"] == "1"
                }
    }

    suspend fun getUid(screenName: String): String? {
        val response =
            ktorClient {
                followRedirects = false
                install(VVOHeaderPlugin) {
                    this.chocolateFlow = this@VVOService.chocolateFlow
                }
            }.get("https://$vvoHost/n/$screenName")
        return response.headers["Location"]?.let {
            return it.split('/').last()
        }
    }

    suspend fun uploadPic(
        st: String,
        filename: String,
        bytes: ByteArray,
        xsrfToken: String = st,
        type: String = "json",
    ): UploadResponse =
        ktorClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 2.minutes.inWholeMilliseconds
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
                socketTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
            install(VVOHeaderPlugin) {
                this.chocolateFlow = this@VVOService.chocolateFlow
            }
        }.submitFormWithBinaryData(
            url = "https://$vvoHost/api/statuses/uploadPic",
            formData =
                formData {
                    append("type", type)
                    append(
                        "pic",
                        filename,
                        bodyBuilder = {
                            writeFully(bytes)
                        },
                        size = bytes.size.toLong(),
                        contentType = ContentType.Image.JPEG,
                    )

                    append("st", st)
                },
            block = {
                header("X-Xsrf-Token", xsrfToken)
            },
        ).bodyAsText()
            .decodeJson<UploadResponse>()

    suspend fun emojis(): EmojiData = ktorClient().get("https://flareapp.moe/emoji.json").body()
}

private class VVOHeaderConfig {
    var chocolateFlow: Flow<String>? = null
    var onChocolateUpdated: ((String) -> Unit)? = null
}

private val VVOHeaderPlugin =
    createClientPlugin("VVOHeaderPlugin", ::VVOHeaderConfig) {
        val chocolateFlow = pluginConfig.chocolateFlow
        val onChocolateUpdated = pluginConfig.onChocolateUpdated
        onRequest { request, _ ->
            chocolateFlow?.let { flow ->
                val chocolate = flow.firstOrNull()
                if (chocolate != null) {
                    request.headers.append("Cookie", chocolate)
                    chocolate
                        .split(";")
                        .firstOrNull { it.trim().startsWith("XSRF-TOKEN=") }
                        ?.substringAfter("=")
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            request.headers.append("x-xsrf-token", it)
                        }
                }
            }
            request.headers.append(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0",
            )
            request.headers.remove(HttpHeaders.Accept)
            request.headers.remove("accept")
            request.headers.append(HttpHeaders.Accept, "application/json, text/plain, */*")
            request.headers.append("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
            request.headers.append("mweibo-pwa", "1")
            request.headers.append("priority", "u=1, i")
            request.headers.append("sec-ch-ua", "\"Chromium\";v=\"148\", \"Microsoft Edge\";v=\"148\", \"Not/A)Brand\";v=\"99\"")
            request.headers.append("sec-ch-ua-mobile", "?0")
            request.headers.append("sec-ch-ua-platform", "\"macOS\"")
            request.headers.append("sec-fetch-dest", "empty")
            request.headers.append("sec-fetch-mode", "cors")
            request.headers.append("sec-fetch-site", "same-origin")
            request.headers.append("x-requested-with", "XMLHttpRequest")
            val containerId = request.url.parameters["containerid"]
            val query = containerId?.substringAfter("q=", "")?.substringBefore("&")?.takeIf { it.isNotBlank() }
            val referer =
                if (request.url.toString().contains("/api/container/getIndex") && containerId?.startsWith("100103") == true) {
                    val refererContainerId =
                        if (query != null) {
                            "100103type=1&q=$query"
                        } else {
                            containerId
                        }
                    "https://$vvoHost/search?containerid=${refererContainerId.encodeURLParameter()}"
                } else {
                    "https://$vvoHost/"
                }
            request.headers.append("Referer", referer)
        }
        onResponse { response ->
            val currentChocolate = chocolateFlow?.firstOrNull() ?: return@onResponse
            val updatedChocolate =
                mergeSetCookies(
                    currentChocolate = currentChocolate,
                    setCookies = response.headers.getAll("Set-Cookie").orEmpty(),
                )
            if (updatedChocolate != currentChocolate) {
                onChocolateUpdated?.invoke(updatedChocolate)
            }
        }
    }

private fun mergeSetCookies(
    currentChocolate: String,
    setCookies: List<String>,
): String {
    if (setCookies.isEmpty()) return currentChocolate
    val cookies =
        currentChocolate
            .split(";")
            .mapNotNull { cookie ->
                val name = cookie.substringBefore("=", "").trim()
                val value = cookie.substringAfter("=", "").trim()
                if (name.isNotBlank()) name to value else null
            }.toMap()
            .toMutableMap()
    setCookies.forEach { setCookie ->
        val cookie = setCookie.substringBefore(";")
        val name = cookie.substringBefore("=", "").trim()
        val value = cookie.substringAfter("=", "").trim()
        if (name.isBlank()) return@forEach
        val isExpired =
            setCookie.contains("Max-Age=0", ignoreCase = true) ||
                setCookie.contains("Expires=Thu, 01 Jan 1970", ignoreCase = true)
        if (value.isBlank() || isExpired) {
            cookies.remove(name)
        } else {
            cookies[name] = value
        }
    }
    return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
