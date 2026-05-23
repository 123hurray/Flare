package dev.dimension.flare.data.network.xiaohongshu

internal object XhsCookieHeaderBuilder {
    private val preferredOrder =
        listOf(
            "a1",
            "webId",
            "web_session",
            "web_session_sec",
            "id_token",
            "websectiga",
            "sec_poison_id",
            "xsecappid",
            "gid",
            "abRequestId",
            "webBuild",
            "loadts",
            "acw_tc",
        )

    fun build(cookies: Map<String, String>): String =
        preferredOrder
            .mapNotNull { name -> cookies[name]?.takeIf { it.isNotBlank() }?.let { name to it } }
            .plus(
                cookies
                    .filterKeys { it !in preferredOrder }
                    .filterValues { it.isNotBlank() }
                    .toList()
                    .sortedBy { it.first },
            ).joinToString("; ") { (name, value) -> "$name=$value" }

    fun sanitizedSummary(cookies: Map<String, String>): String =
        cookies
            .filterValues { it.isNotBlank() }
            .keys
            .sorted()
            .joinToString(prefix = "cookieKeys=[", postfix = "]")
}
