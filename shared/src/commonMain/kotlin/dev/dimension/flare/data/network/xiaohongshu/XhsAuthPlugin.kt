package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.common.encodeJsonWithDefaults
import dev.dimension.flare.data.network.xiaohongshu.model.XhsHomeFeedRequest
import dev.dimension.flare.model.xiaohongshuWebHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal class XhsAuthPlugin(
    private val cookiesFlow: Flow<Map<String, String>>,
) {
    suspend fun headers(
        method: String,
        path: String,
        body: String,
    ): Map<String, String> {
        val cookies = cookiesFlow.first().filterValues { it.isNotBlank() }
        val cookieHeader = XhsCookieHeaderBuilder.build(cookies)
        return baseHeaders(cookieHeader) + XhsSigning.sign(method, path, body, cookies)
    }

    suspend fun creatorHeaders(
        path: String,
        body: String,
    ): Map<String, String> {
        val cookies = cookiesFlow.first().filterValues { it.isNotBlank() }
        val cookieHeader = XhsCookieHeaderBuilder.build(cookies)
        return baseHeaders(
            cookieHeader = cookieHeader,
            origin = "https://creator.xiaohongshu.com",
            referer = "https://creator.xiaohongshu.com/",
        ) + XhsCreatorSigning.sign(path, body, cookies)
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/145.0.0.0 Safari/537.36"
        const val SEC_CH_UA = "\"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\", \"Not-A.Brand\";v=\"99\""

        fun baseHeaders(
            cookieHeader: String,
            origin: String = "https://$xiaohongshuWebHost",
            referer: String = "https://$xiaohongshuWebHost/",
        ): Map<String, String> =
            mapOf(
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/json;charset=UTF-8",
                "Cookie" to cookieHeader,
                "Origin" to origin,
                "Referer" to referer,
                "sec-ch-ua" to SEC_CH_UA,
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"macOS\"",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-site",
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "DNT" to "1",
                "priority" to "u=1, i",
            )
    }
}

internal fun XhsHomeFeedRequest.bodyForSigning(): String = encodeJsonWithDefaults()
