package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.RequireReLoginException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuWebHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

internal class ZhihuService(
    private val accountKey: MicroBlogKey? = null,
    private val cookiesFlow: Flow<Map<String, String>> = flowOf(emptyMap()),
) {
    private var cachedZseCk: String? = null

    private val client =
        ktorClient {
            install(ContentNegotiation) {
                json(JSON)
            }
    }

    suspend fun me(): ZhihuViewer {
        val root = requestJson("https://www.zhihu.com/api/v4/me?include=name,avatar_url,url_token,id,headline")
        root.throwIfZhihuError("me", "self")
        return ZhihuViewer(
            id = root.string("id").orEmpty(),
            urlToken = root.string("url_token").orEmpty(),
            name = root.string("name").orEmpty(),
            avatar = root.string("avatar_url").orEmpty(),
            headline = root.string("headline").orEmpty(),
        )
    }

    suspend fun userProfile(urlToken: String): ZhihuViewer {
        val root =
            requestJson(
                "https://www.zhihu.com/api/v4/members/${urlToken.encodeURLParameter()}" +
                    "?include=name,avatar_url,url_token,id,headline,follower_count,following_count,answer_count,articles_count",
            )
        root.throwIfZhihuError("userProfile", urlToken)
        return root.toViewer(fallbackId = urlToken)
    }

    suspend fun searchContent(
        query: String,
        offset: Int,
        limit: Int,
    ): ZhihuTimelinePage {
        val boundedLimit = limit.coerceIn(1, ZHIHU_SEARCH_LIMIT)
        val root =
            requestSearchJson(
                "https://www.zhihu.com/api/v4/search_v3?gk_version=gz-gaokao&t=general" +
                    "&q=${query.encodeURLParameter()}" +
                    "&correction=1&offset=$offset&limit=$boundedLimit&filter_fields=&lc_idx=$offset&show_all_topics=0&search_source=Normal",
            )
        root.throwIfZhihuError("searchContent", query)
        val items =
            root["data"]
                .arrayOrEmpty()
                .mapNotNull { item ->
                    val json = item.objectOrNull() ?: return@mapNotNull null
                    json["object"].objectOrNull()?.toTargetContent()
                        ?: json["target"].objectOrNull()?.toTargetContent()
                        ?: json.toTargetContent()
                }
        val paging = root["paging"].objectOrNull()
        return ZhihuTimelinePage(
            items = items,
            nextUrl = ((offset + boundedLimit).toString()).takeUnless { paging?.boolean("is_end") == true || items.isEmpty() },
            isEnd = paging?.boolean("is_end") ?: items.isEmpty(),
        )
    }

    suspend fun searchUsers(
        query: String,
        offset: Int,
        limit: Int,
    ): ZhihuUserPage {
        val boundedLimit = limit.coerceIn(1, ZHIHU_SEARCH_LIMIT)
        val root =
            requestSearchJson(
                "https://www.zhihu.com/api/v4/search_v3?gk_version=gz-gaokao&t=people" +
                    "&q=${query.encodeURLParameter()}" +
                    "&correction=1&offset=$offset&limit=$boundedLimit&filter_fields=&lc_idx=$offset&show_all_topics=0&search_source=Normal",
            )
        root.throwIfZhihuError("searchUsers", query)
        val users =
            root["data"]
                .arrayOrEmpty()
                .mapNotNull { item ->
                    val json = item.objectOrNull() ?: return@mapNotNull null
                    json["object"].objectOrNull()?.toSearchUser()
                        ?: json["target"].objectOrNull()?.toSearchUser()
                        ?: json.toSearchUser()
                }
        val paging = root["paging"].objectOrNull()
        return ZhihuUserPage(
            items = users,
            nextOffset = (offset + boundedLimit).takeUnless { paging?.boolean("is_end") == true || users.isEmpty() },
            isEnd = paging?.boolean("is_end") ?: users.isEmpty(),
        )
    }

    suspend fun recommend(nextUrl: String? = null): ZhihuTimelinePage {
        val root =
            requestJson(
                nextUrl
                    ?: "https://api.zhihu.com/topstory/recommend",
            )
        root.throwIfZhihuError("recommend", "home")
        return ZhihuTimelinePage(
            items =
                root["data"]
                    .arrayOrEmpty()
                    .mapNotNull { it.objectOrNull()?.toFeedContent() },
            nextUrl = root["paging"].objectOrNull()?.string("next"),
            isEnd = root["paging"].objectOrNull()?.boolean("is_end") ?: true,
        )
    }

    suspend fun daily(date: String? = null): ZhihuTimelinePage {
        val url =
            if (date.isNullOrBlank()) {
                "https://news-at.zhihu.com/api/4/news/latest"
            } else {
                "https://news-at.zhihu.com/api/4/news/before/${date.zhihuDailyBeforeDate()}"
            }
        return dailyFromUrl(url, date ?: "latest")
    }

    suspend fun dailyBefore(beforeDate: String): ZhihuTimelinePage =
        dailyFromUrl(
            url = "https://news-at.zhihu.com/api/4/news/before/$beforeDate",
            logId = "before:$beforeDate",
        )

    private suspend fun dailyFromUrl(
        url: String,
        logId: String,
    ): ZhihuTimelinePage {
        val root = requestPublicJson(url)
        root.throwIfZhihuError("daily", logId)
        val responseDate = root.string("date")
        val stories =
            root["stories"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toDailyContent(responseDate) }
        return ZhihuTimelinePage(
            items = stories,
            nextUrl = responseDate,
            isEnd = stories.isEmpty() || responseDate.isNullOrBlank(),
        )
    }

    suspend fun contentDetail(statusId: String): ZhihuContent =
        runCatching {
            val key = ZhihuContentKey.parse(statusId)
            if (key.type == ZhihuContentType.Question) {
                return@runCatching questionHeader(key.id)
            }
            val root =
                when (key.type) {
                    ZhihuContentType.Answer ->
                        requestJson("https://www.zhihu.com/api/v4/answers/${key.id}?include=author,content,voteup_count,comment_count,excerpt,question,created_time")
                    ZhihuContentType.Article ->
                        runCatching {
                            requestFirstJson(
                                "detail",
                                statusId,
                                listOf(
                                    "https://api.zhihu.com/v4/articles/${key.id}?include=author,content,voteup_count,comment_count,excerpt,created,updated",
                                    "https://www.zhihu.com/api/v4/articles/${key.id}?include=author,content,voteup_count,comment_count,excerpt,created,updated",
                                    "https://api.zhihu.com/v4/articles/${key.id}",
                                    "https://www.zhihu.com/api/v4/articles/${key.id}",
                                ),
                            )
                        }.getOrElse {
                            println("ZhihuService: article api detail unavailable id=${key.id} message=${it.message}")
                            return@runCatching articleDetailFromWeb(key.id)
                        }
                    ZhihuContentType.Daily ->
                        requestPublicJson("https://news-at.zhihu.com/api/4/news/${key.id}")
                    ZhihuContentType.Pin ->
                        requestJson("https://www.zhihu.com/api/v4/pins/${key.id}")
                    ZhihuContentType.Question -> error("Question detail should be loaded from answers feed")
                }
            root.throwIfZhihuError("detail", statusId)
            root.toDetailContent(key)
        }.getOrElse {
            if (it is CancellationException) {
                throw it
            }
            println("ZhihuService: detail fallback statusId=$statusId message=${it.message}")
            ZhihuContent.placeholder(statusId)
        }

    suspend fun questionAnswers(
        questionId: String,
        nextUrl: String? = null,
    ): ZhihuTimelinePage =
        runCatching {
            val root =
                requestJson(
                    nextUrl
                        ?: "https://www.zhihu.com/api/v4/questions/$questionId/feeds?include=badge%5B*%5D.topics,comment_count,excerpt,voteup_count,created_time,updated_time&limit=20&order=default",
                )
            root.throwIfZhihuError("questionAnswers", questionId)
            val targets =
                root["data"]
                    .arrayOrEmpty()
                    .mapNotNull { it.objectOrNull()?.get("target").objectOrNull() }
            val header =
                targets
                    .mapNotNull { it.get("question").objectOrNull()?.toQuestionContent(questionId) }
                    .firstOrNull()
            ZhihuTimelinePage(
                items = targets.mapNotNull { it.toAnswerContent(questionId) },
                nextUrl = root["paging"].objectOrNull()?.string("next"),
                isEnd = root["paging"].objectOrNull()?.boolean("is_end") ?: true,
                header = header,
            )
        }.getOrElse {
            println("ZhihuService: question answers unavailable questionId=$questionId message=${it.message}")
            ZhihuTimelinePage(emptyList(), nextUrl = null, isEnd = true, header = ZhihuContent.questionPlaceholder(questionId))
        }

    suspend fun questionHeader(questionId: String): ZhihuContent =
        questionAnswers(questionId).header ?: ZhihuContent.questionPlaceholder(questionId)

    suspend fun comments(
        statusId: String,
        nextUrl: String? = null,
    ): ZhihuCommentPage =
        runCatching {
            val key = ZhihuContentKey.parse(statusId)
            if (key.type == ZhihuContentType.Question) {
                return@runCatching ZhihuCommentPage(emptyList(), null, true)
            }
            val contentType =
                when (key.type) {
                    ZhihuContentType.Answer -> "answers"
                    ZhihuContentType.Article -> "articles"
                    ZhihuContentType.Daily -> return@runCatching ZhihuCommentPage(emptyList(), null, true)
                    ZhihuContentType.Pin -> "pins"
                    ZhihuContentType.Question -> "questions"
                }
            val root =
                requestJson(
                    nextUrl ?: "https://api.zhihu.com/comment_v5/$contentType/${key.id}/root_comment?order_by=score&limit=20",
                )
            root.throwIfZhihuError("comments", statusId)
            root.logCommentStructure("root", statusId)
            root.toCommentPage()
        }.getOrElse {
            println("ZhihuService: comments unavailable statusId=$statusId message=${it.message}")
            ZhihuCommentPage(emptyList(), null, true)
        }

    suspend fun comment(
        statusId: String,
        commentId: String,
    ): ZhihuComment? {
        var nextUrl: String? = null
        repeat(3) {
            val page = comments(statusId, nextUrl)
            page.comments.firstOrNull { it.id == commentId }?.let { return it }
            if (page.isEnd) return null
            nextUrl = page.nextUrl
        }
        return null
    }

    suspend fun childComments(
        rootCommentId: String,
        nextUrl: String? = null,
    ): ZhihuCommentPage =
        runCatching {
            val root =
                requestJson(
                    nextUrl ?: "https://api.zhihu.com/comment_v5/comment/$rootCommentId/child_comment?order_by=ts&limit=20",
                )
            root.throwIfZhihuError("childComments", rootCommentId)
            root.logCommentStructure("child", rootCommentId)
            root.toCommentPage()
        }.getOrElse {
            println("ZhihuService: child comments unavailable commentId=$rootCommentId message=${it.message}")
            ZhihuCommentPage(emptyList(), null, true)
        }

    private suspend fun requestJson(url: String): JsonObject {
        val cookies =
            cookiesFlow
                .first()
                .filterValues { it.isNotBlank() }
                .let { if (url.needsZhihuWebSignature()) it.withZseCk() else it }
        if (cookies["z_c0"].isNullOrBlank()) {
            throw RequireReLoginException(
                accountKey = accountKey ?: MicroBlogKey("account", zhihuWebHost),
                platformType = PlatformType.Zhihu,
            )
        }
        val response =
            client.get(url) {
                zhihuHeaders(url, cookies)
            }
        val body = response.bodyAsText()
        if (url.isZhihuSearchUrl()) {
            println(
                "ZhihuService: searchResponse status=${response.status.value} " +
                    "url=$url body=${body.take(ZHIHU_DEBUG_BODY_LIMIT)}",
            )
        }
        if (url.contains("/comment_v5/")) {
            logRawCommentBody(
                scope = "ZhihuService",
                id = url,
                body = body,
            )
        }
        return JSON.decodeFromString(body)
    }

    private suspend fun requestPublicJson(url: String): JsonObject =
        runCatching {
            requestPublicJsonOnce(url)
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            if (url.startsWith("https://news-at.zhihu.com/")) {
                val fallbackUrl = url.replaceFirst("https://", "http://")
                println("ZhihuService: publicHttpsFallback url=$url message=${error.message}")
                requestPublicJsonOnce(fallbackUrl)
            } else {
                throw error
            }
        }

    private suspend fun requestPublicJsonOnce(url: String): JsonObject {
        val response =
            client.get(url) {
                header(HttpHeaders.UserAgent, ZHIHU_WEB_USER_AGENT)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
                header("Referer", "https://daily.zhihu.com/")
            }
        val body = response.bodyAsText()
        println(
            "ZhihuService: publicResponse status=${response.status.value} " +
                "url=$url body=${body.take(ZHIHU_DEBUG_BODY_LIMIT)}",
        )
        return JSON.decodeFromString(body)
    }

    private suspend fun requestSearchJson(url: String): JsonObject {
        val root = requestJson(url)
        if (root.zhihuErrorCode() != "40362") {
            return root
        }
        val cookies =
            cookiesFlow
                .first()
                .filterValues { it.isNotBlank() }
        val fallback =
            runCatching {
                ZhihuSearchRuntime.requestJson(
                    ZhihuSearchWebRequest(
                        url = url,
                        cookies = cookies,
                    ),
                )
            }.onFailure {
                println("ZhihuService: searchWebFallback failed url=$url message=${it.message}")
            }.getOrNull()
        if (fallback.isNullOrBlank()) {
            return root
        }
        println("ZhihuService: searchWebFallback url=$url body=${fallback.take(ZHIHU_DEBUG_BODY_LIMIT)}")
        return JSON.decodeFromString(fallback)
    }

    private suspend fun requestFirstJson(
        scope: String,
        id: String,
        urls: List<String>,
    ): JsonObject {
        var lastError: Throwable? = null
        urls.forEach { url ->
            runCatching {
                val root = requestJson(url)
                root.throwIfZhihuError(scope, id)
                return root
            }.onFailure {
                println("ZhihuService: $scope candidate failed id=$id url=$url message=${it.message}")
                lastError = it
            }
        }
        throw lastError ?: IllegalStateException("No Zhihu $scope candidates for $id")
    }

    private suspend fun articleDetailFromWeb(articleId: String): ZhihuContent {
        val html = requestHtml("https://zhuanlan.zhihu.com/p/$articleId")
        val marker = """<script id="js-initialData" type="text/json">"""
        val initialData =
            html
                .substringAfter(marker, missingDelimiterValue = "")
                .substringBefore("</script>", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?: error("Zhihu article web initial data missing")
        val root = JSON.decodeFromString<JsonObject>(initialData)
        val article =
            root["initialState"]
                .objectOrNull()
                ?.get("entities")
                .objectOrNull()
                ?.get("articles")
                .objectOrNull()
                ?.let { articles ->
                    articles[articleId].objectOrNull()
                        ?: articles.values.firstOrNull()?.objectOrNull()
                }
                ?: error("Zhihu article web entity missing")
        return article.toWebArticleContent(articleId)
    }

    private suspend fun requestHtml(url: String): String {
        val cookies = cookiesFlow.first()
        return client
            .get(url) {
                header(HttpHeaders.UserAgent, ZHIHU_WEB_USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.Cookie, cookies.toCookieHeader())
                header("Referer", "https://www.zhihu.com/")
            }.bodyAsText()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.zhihuHeaders(
        url: String,
        cookies: Map<String, String>,
    ) {
        header(HttpHeaders.UserAgent, ZHIHU_WEB_USER_AGENT)
        header("Accept", "application/json, text/plain, */*")
        header("x-udid", ZHIHU_DEVICE_ID)
        if (url.startsWith("https://api.zhihu.com/topstory/recommend")) {
            header(HttpHeaders.Cookie, cookies.toCookieHeader())
            return
        }
        if (url.needsZhihuWebSignature()) {
            val apiPath = url.zhihuApiPath()
            val dC0 = cookies["d_c0"].orEmpty()
            val xZse93 = ZHIHU_X_ZSE_93
            val xZst81 = ZHIHU_SEARCH_X_ZST_81.takeIf { url.isZhihuSearchUrl() }
            header("x-api-version", "3.0.91")
            header("x-app-za", "OS=Web")
            header("x-zse-93", xZse93)
            if (!xZst81.isNullOrBlank()) {
                header("x-zst-81", xZst81)
            }
            if (dC0.isNotBlank()) {
                header("x-zse-96", ZhihuZse96.sign(apiPath = apiPath, dC0 = dC0, xZse93 = xZse93, xZst81 = xZst81))
            }
            if (url.isZhihuSearchUrl()) {
                println(
                    "ZhihuService: searchHeaders apiPath=$apiPath " +
                        "hasDC0=${dC0.isNotBlank()} " +
                        "hasZseCk=${!cookies["__zse_ck"].isNullOrBlank()} " +
                        "hasXZst81=${!xZst81.isNullOrBlank()}",
                )
            }
            header("Referer", "https://www.zhihu.com/")
            header("Origin", "https://www.zhihu.com")
            header("x-requested-with", "fetch")
            header(HttpHeaders.Cookie, cookies.toCookieHeader())
            return
        }
        header("x-api-version", "3.1.8")
        header("x-app-version", "10.12.0")
        header("x-app-bundleid", "com.zhihu.android")
        header("x-app-za", "OS=Android&VersionName=10.12.0&VersionCode=21210")
        header("Referer", "https://www.zhihu.com/")
        header("Origin", "https://www.zhihu.com")
        header("x-requested-with", "fetch")
        header(HttpHeaders.Cookie, cookies.toCookieHeader())
    }

    private suspend fun Map<String, String>.withZseCk(): Map<String, String> {
        if (!get("__zse_ck").isNullOrBlank()) {
            return this
        }
        val zseCk =
            cachedZseCk
                ?: runCatching {
                    client
                        .get("https://static.zhihu.com/zse-ck/v3.js") {
                            header(HttpHeaders.UserAgent, ZHIHU_WEB_USER_AGENT)
                        }.bodyAsText()
                        .let { ZSE_CK_REGEX.find(it)?.groupValues?.getOrNull(1).orEmpty() }
                        .also { if (it.isNotBlank()) cachedZseCk = it }
                }.getOrDefault("")
        return if (zseCk.isBlank()) {
            this
        } else {
            this + ("__zse_ck" to zseCk)
        }
    }
}

private const val ZHIHU_DEBUG_BODY_LIMIT = 3500

private fun logRawCommentBody(
    scope: String,
    id: String,
    body: String,
) {
    val chunks = body.chunked(3500)
    println("$scope: rawCommentBodyBegin id=$id chunks=${chunks.size} length=${body.length}")
    chunks.forEachIndexed { index, chunk ->
        println("$scope: rawCommentBody[$index/${chunks.size}] $chunk")
    }
    println("$scope: rawCommentBodyEnd id=$id")
}

internal data class ZhihuViewer(
    val id: String,
    val urlToken: String,
    val name: String,
    val avatar: String,
    val headline: String,
    val followerCount: Long = 0L,
    val followingCount: Long = 0L,
    val statusesCount: Long = 0L,
)

internal data class ZhihuTimelinePage(
    val items: List<ZhihuContent>,
    val nextUrl: String?,
    val isEnd: Boolean,
    val header: ZhihuContent? = null,
)

internal data class ZhihuContent(
    val id: String,
    val type: ZhihuContentType,
    val title: String,
    val excerpt: String,
    val contentHtml: String?,
    val imageUrls: List<String>,
    val authorName: String,
    val authorId: String,
    val authorAvatar: String,
    val authorHeadline: String,
    val questionId: String?,
    val answerCount: Long,
    val followerCount: Long,
    val voteupCount: Long,
    val commentCount: Long,
    val createdTime: Long,
    val url: String?,
) {
    val statusId: String
        get() = "${type.prefix}:$id"

    companion object {
        fun placeholder(statusId: String): ZhihuContent {
            val key = ZhihuContentKey.parse(statusId)
            return ZhihuContent(
                id = key.id,
                type = key.type,
                title = "知乎内容",
                excerpt = "详情接口当前受限，已保留入口并在日志中记录失败原因。",
                contentHtml = null,
                imageUrls = emptyList(),
                authorName = "知乎",
                authorId = "zhihu",
                authorAvatar = "",
                authorHeadline = "",
                questionId = null,
                answerCount = 0,
                followerCount = 0,
                voteupCount = 0,
                commentCount = 0,
                createdTime = Clock.System.now().epochSeconds,
                url = null,
            )
        }

        fun questionPlaceholder(questionId: String): ZhihuContent =
            ZhihuContent(
                id = questionId,
                type = ZhihuContentType.Question,
                title = "知乎问题",
                excerpt = "",
                contentHtml = null,
                imageUrls = emptyList(),
                authorName = "知乎",
                authorId = "zhihu",
                authorAvatar = "",
                authorHeadline = "",
                questionId = questionId,
                answerCount = 0,
                followerCount = 0,
                voteupCount = 0,
                commentCount = 0,
                createdTime = Clock.System.now().epochSeconds,
                url = "https://www.zhihu.com/question/$questionId",
            )
    }
}

internal data class ZhihuComment(
    val id: String,
    val content: String,
    val authorName: String,
    val authorId: String,
    val authorAvatar: String,
    val likeCount: Long,
    val replyCount: Long,
    val createdTime: Long,
    val childComments: List<ZhihuComment> = emptyList(),
)

internal data class ZhihuCommentPage(
    val comments: List<ZhihuComment>,
    val nextUrl: String?,
    val isEnd: Boolean,
)

internal data class ZhihuUserPage(
    val items: List<ZhihuViewer>,
    val nextOffset: Int?,
    val isEnd: Boolean,
)

internal enum class ZhihuContentType(val prefix: String) {
    Answer("answer"),
    Article("article"),
    Daily("daily"),
    Pin("pin"),
    Question("question"),
}

internal data class ZhihuContentKey(
    val type: ZhihuContentType,
    val id: String,
) {
    companion object {
        fun parse(statusId: String): ZhihuContentKey {
            parseOrNull(statusId)?.let { return it }
            val prefix = statusId.substringBefore(':', missingDelimiterValue = "question")
            val id = statusId.substringAfter(':', missingDelimiterValue = statusId)
            val type =
                when (prefix) {
                    "answer" -> ZhihuContentType.Answer
                    "article" -> ZhihuContentType.Article
                    "daily" -> ZhihuContentType.Daily
                    "pin" -> ZhihuContentType.Pin
                    else -> ZhihuContentType.Question
                }
            return ZhihuContentKey(type, id)
        }

        fun parseOrNull(statusId: String): ZhihuContentKey? {
            val normalized = statusId.substringBefore('?').substringBefore('#')
            Regex("""(?:zhuanlan\.zhihu\.com/p/|/p/)(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Article, it) }
            Regex("""/(?:v4/)?articles/(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Article, it) }
            Regex("""(?:daily\.zhihu\.com/story/|news-at\.zhihu\.com/api/4/news/)(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Daily, it) }
            Regex("""/question/\d+/answer/(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Answer, it) }
            Regex("""/pin/(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Pin, it) }
            Regex("""/question/(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return ZhihuContentKey(ZhihuContentType.Question, it) }
            return null
        }
    }
}

private fun JsonObject.toFeedContent(): ZhihuContent? {
    get("target").objectOrNull()?.toTargetContent()?.let { return it }

    val extra = get("extra").objectOrNull()
    val feedContent = get("common_card").objectOrNull()?.get("feed_content").objectOrNull() ?: return null
    val title = feedContent["title"].objectOrNull()?.string("panel_text").orEmpty().stripZhihuHighlightHtml()
    val excerpt = feedContent["content"].objectOrNull()?.string("panel_text").orEmpty().stripZhihuHighlightHtml()
    val sourceLine = feedContent["source_line"].objectOrNull()
    val elements = sourceLine?.get("elements").arrayOrEmpty()
    val authorName =
        elements
            .mapNotNull { it.objectOrNull()?.get("text").objectOrNull()?.string("panel_text") }
            .firstOrNull { it.isNotBlank() }
            ?: "知乎用户"
    val avatar =
        elements
            .mapNotNull { it.objectOrNull()?.get("avatar").objectOrNull()?.get("image").objectOrNull()?.string("image_url") }
            .firstOrNull()
            .orEmpty()
    val intentUrl =
        get("common_card")
            .objectOrNull()
            ?.get("action")
            .objectOrNull()
            ?.string("intent_url")
            ?: feedContent["title"].objectOrNull()?.get("action").objectOrNull()?.string("intent_url")
    val intentKey =
        listOfNotNull(
            intentUrl,
            extra?.string("url"),
            extra?.string("target_url"),
            extra?.string("redirect_url"),
        ).firstNotNullOfOrNull { ZhihuContentKey.parseOrNull(it) }
    val type = intentKey?.type ?: extra?.string("type")?.toZhihuContentType() ?: return null
    val id = intentKey?.id ?: extra?.string("id")?.normalizeZhihuContentId(type) ?: return null
    val questionId = intentUrl?.let { Regex("/question/(\\d+)").find(it)?.groupValues?.getOrNull(1) }
    val createdTime =
        string("id")
            ?.substringAfter('_', "")
            ?.substringBefore('.', "")
            ?.toLongOrNull()
            ?: Clock.System.now().epochSeconds
    return ZhihuContent(
        id = id,
        type = type,
        title = title,
        excerpt = excerpt,
        contentHtml = null,
        imageUrls = feedContent.thumbnailImageUrls(),
        authorName = authorName,
        authorId = authorName,
        authorAvatar = avatar,
        authorHeadline = "",
        questionId = questionId,
        answerCount = 0,
        followerCount = 0,
        voteupCount = 0,
        commentCount = 0,
        createdTime = createdTime,
        url = intentUrl,
    )
}

private fun JsonObject.toTargetContent(): ZhihuContent? {
    val urlKey = zhihuContentKeyFromUrls()
    val type = urlKey?.type ?: string("type")?.toZhihuContentType() ?: return null
    val id = urlKey?.id ?: string("id")?.normalizeZhihuContentId(type) ?: return null
    val question = get("question").objectOrNull()
    val author = get("author").objectOrNull()
    val pinContent = if (type == ZhihuContentType.Pin) pinContentHtml() else null
    return ZhihuContent(
        id = id,
        type = type,
        title =
            when (type) {
                ZhihuContentType.Pin -> string("title").orEmpty()
                else -> question?.string("title") ?: string("title") ?: "知乎内容"
            }.stripZhihuHighlightHtml(),
        excerpt = (pinContent ?: string("excerpt") ?: string("excerpt_new") ?: string("detail") ?: string("content") ?: "").stripZhihuHighlightHtml(),
        contentHtml = pinContent,
        imageUrls = (thumbnailImageUrls() + pinImageUrls()).distinct(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        authorHeadline = author?.string("headline").orEmpty(),
        questionId =
            when (type) {
                ZhihuContentType.Question -> id
                ZhihuContentType.Answer -> question?.string("id")
                else -> null
            },
        answerCount = question?.long("answer_count") ?: if (type == ZhihuContentType.Question) long("answer_count") ?: 0L else 0L,
        followerCount = question?.long("follower_count") ?: if (type == ZhihuContentType.Question) long("follower_count") ?: 0L else 0L,
        voteupCount = long("voteup_count") ?: long("vote_count") ?: 0L,
        commentCount = long("comment_count") ?: 0L,
        createdTime = long("created_time") ?: long("created") ?: Clock.System.now().epochSeconds,
        url = string("url"),
    )
}

private fun JsonObject.toDetailContent(key: ZhihuContentKey): ZhihuContent {
    if (key.type == ZhihuContentType.Daily) {
        return toDailyDetailContent(key.id)
    }
    val question = get("question").objectOrNull()
    val author = get("author").objectOrNull()
    val pinContent = if (key.type == ZhihuContentType.Pin) pinContentHtml() else null
    return ZhihuContent(
        id = key.id,
        type = key.type,
        title =
            when (key.type) {
                ZhihuContentType.Pin -> string("title").orEmpty()
                else -> question?.string("title") ?: string("title") ?: "知乎内容"
            }.stripZhihuHighlightHtml(),
        excerpt = (pinContent ?: string("excerpt") ?: string("detail") ?: "").stripZhihuHighlightHtml(),
        contentHtml = pinContent ?: string("content") ?: string("detail"),
        imageUrls = (thumbnailImageUrls() + pinImageUrls()).distinct(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        authorHeadline = author?.string("headline").orEmpty(),
        questionId = question?.string("id"),
        answerCount = question?.long("answer_count") ?: long("answer_count") ?: 0L,
        followerCount = question?.long("follower_count") ?: long("follower_count") ?: 0L,
        voteupCount = long("voteup_count") ?: 0L,
        commentCount = long("comment_count") ?: 0L,
        createdTime = long("created_time") ?: long("created") ?: Clock.System.now().epochSeconds,
        url = string("url") ?: string("url_token"),
    )
}

private fun JsonObject.toDailyContent(date: String?): ZhihuContent? {
    val id = string("id") ?: long("id")?.toString() ?: return null
    val hint = string("hint").orEmpty().stripZhihuHighlightHtml()
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Daily,
        title = string("title").orEmpty().stripZhihuHighlightHtml(),
        excerpt = "",
        contentHtml = null,
        imageUrls = thumbnailImageUrls(),
        authorName = hint.takeIf { it.isNotBlank() } ?: "知乎日报",
        authorId = "zhihu-daily",
        authorAvatar = "",
        authorHeadline = "",
        questionId = null,
        answerCount = 0L,
        followerCount = 0L,
        voteupCount = 0L,
        commentCount = 0L,
        createdTime = date.zhihuDailyEpochSecondsOrNow(),
        url = string("url") ?: string("share_url") ?: "https://daily.zhihu.com/story/$id",
    )
}

private fun JsonObject.toDailyDetailContent(fallbackId: String): ZhihuContent {
    val id = string("id") ?: long("id")?.toString() ?: fallbackId
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Daily,
        title = string("title").orEmpty().stripZhihuHighlightHtml(),
        excerpt = "",
        contentHtml = string("body")?.cleanZhihuDailyBodyHtml(),
        imageUrls = thumbnailImageUrls(),
        authorName = string("image_source").orEmpty().takeIf { it.isNotBlank() } ?: "知乎日报",
        authorId = "zhihu-daily",
        authorAvatar = "",
        authorHeadline = "",
        questionId = null,
        answerCount = 0L,
        followerCount = 0L,
        voteupCount = 0L,
        commentCount = 0L,
        createdTime = Clock.System.now().epochSeconds,
        url = string("share_url") ?: string("url") ?: "https://daily.zhihu.com/story/$id",
    )
}

private fun JsonObject.toWebArticleContent(fallbackId: String): ZhihuContent {
    val id = string("id") ?: fallbackId
    val author = get("author").objectOrNull()
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Article,
        title = (string("title") ?: "知乎文章").stripZhihuHighlightHtml(),
        excerpt = (string("excerptTitle") ?: string("excerpt") ?: "").stripZhihuHighlightHtml(),
        contentHtml = string("content") ?: string("excerpt"),
        imageUrls =
            listOfNotNull(
                string("titleImage"),
                string("imageUrl"),
            ).filter { it.isNotBlank() } + thumbnailImageUrls(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        authorHeadline = author?.string("headline").orEmpty(),
        questionId = null,
        answerCount = 0L,
        followerCount = 0L,
        voteupCount = long("voteupCount") ?: long("voteup_count") ?: long("likedCount") ?: 0L,
        commentCount = long("commentCount") ?: long("comment_count") ?: 0L,
        createdTime = long("created") ?: long("created_time") ?: Clock.System.now().epochSeconds,
        url = string("url") ?: "https://zhuanlan.zhihu.com/p/$id",
    )
}

private fun JsonObject.zhihuContentKeyFromUrls(): ZhihuContentKey? =
    listOfNotNull(
        string("url"),
        string("target_url"),
        string("redirect_url"),
        string("share_url"),
        string("origin_url"),
        string("canonical_url"),
        string("url_token"),
        get("action").objectOrNull()?.string("intent_url"),
        get("action").objectOrNull()?.string("url"),
        get("action").objectOrNull()?.string("target_url"),
    ).firstNotNullOfOrNull { ZhihuContentKey.parseOrNull(it) }

private fun JsonObject.toAnswerContent(questionId: String): ZhihuContent? {
    val id = string("id") ?: return null
    val author = get("author").objectOrNull()
    val question = get("question").objectOrNull()
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Answer,
        title = (question?.string("title") ?: string("question") ?: "知乎回答").stripZhihuHighlightHtml(),
        excerpt = string("excerpt").orEmpty().stripZhihuHighlightHtml(),
        contentHtml = string("content"),
        imageUrls = thumbnailImageUrls(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url").orEmpty(),
        authorHeadline = author?.string("headline").orEmpty(),
        questionId = questionId,
        answerCount = question?.long("answer_count") ?: 0L,
        followerCount = question?.long("follower_count") ?: 0L,
        voteupCount = long("voteup_count") ?: 0L,
        commentCount = long("comment_count") ?: 0L,
        createdTime = long("created_time") ?: Clock.System.now().epochSeconds,
        url = null,
    )
}

private fun JsonObject.toComment(): ZhihuComment? {
    val id = string("id") ?: return null
    val author = get("author").objectOrNull()?.get("member").objectOrNull() ?: get("author").objectOrNull()
    val replyToAuthor =
        get("reply_to_author")
            .objectOrNull()
            ?.get("member")
            .objectOrNull()
            ?: get("reply_to_author").objectOrNull()
    val rawContent = string("content").orEmpty()
    val replyToName = replyToAuthor?.string("name")?.takeIf { it.isNotBlank() }
    return ZhihuComment(
        id = id,
        content =
            if (replyToName.isNullOrBlank() || rawContent.contains("@$replyToName")) {
                rawContent
            } else {
                "@$replyToName $rawContent"
            },
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        likeCount = long("like_count") ?: long("vote_count") ?: 0L,
        replyCount = long("child_comment_count") ?: 0L,
        createdTime = long("created_time") ?: Clock.System.now().epochSeconds,
        childComments =
            get("child_comments")
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toComment() },
    )
}

private fun JsonObject.toCommentPage(): ZhihuCommentPage =
    ZhihuCommentPage(
        comments =
            get("data")
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toComment() },
        nextUrl = get("paging").objectOrNull()?.string("next"),
        isEnd = get("paging").objectOrNull()?.boolean("is_end") ?: true,
    )

private fun JsonObject.toViewer(fallbackId: String): ZhihuViewer =
    ZhihuViewer(
        id = string("id") ?: string("url_token") ?: fallbackId,
        urlToken = string("url_token") ?: string("id") ?: fallbackId,
        name = string("name").orEmpty().stripZhihuHighlightHtml(),
        avatar = string("avatar_url") ?: string("avatarUrl") ?: "",
        headline = string("headline").orEmpty().stripZhihuHighlightHtml(),
        followerCount = long("follower_count") ?: 0L,
        followingCount = long("following_count") ?: 0L,
        statusesCount = (long("answer_count") ?: 0L) + (long("articles_count") ?: 0L),
    )

private fun JsonObject.toSearchUser(): ZhihuViewer? {
    val member = get("member").objectOrNull() ?: this
    val token = member.string("url_token") ?: member.string("id") ?: return null
    return member.toViewer(fallbackId = token)
}

private fun JsonObject.logCommentStructure(
    kind: String,
    id: String,
) {
    val data = get("data").arrayOrEmpty()
    val paging = get("paging").objectOrNull()
    println(
        "ZhihuService: commentRawSummary kind=$kind id=$id rootKeys=${keys.sorted()} " +
            "pagingKeys=${paging?.keys?.sorted().orEmpty()} count=${data.size} " +
            "isEnd=${paging?.valueForLog("is_end")} next=${paging?.string("next")?.take(80).orEmpty()}",
    )
    data
        .take(3)
        .forEachIndexed { index, item ->
            val comment = item.objectOrNull() ?: return@forEachIndexed
            println("ZhihuService: commentRawSample kind=$kind index=$index ${comment.commentStructureForLog()}")
        }
}

private fun JsonObject.commentStructureForLog(): String {
    val childComments = get("child_comments").arrayOrEmpty()
    val replyToAuthor = get("reply_to_author").objectOrNull()
    val author = get("author").objectOrNull()
    return "keys=${keys.sorted()} id=${valueForLog("id")} " +
        "childCommentCount=${valueForLog("child_comment_count")} childComments=${childComments.size} " +
        "replyToKeys=${replyToAuthor?.keys?.sorted().orEmpty()} " +
        "authorKeys=${author?.keys?.sorted().orEmpty()} " +
        "childSampleKeys=${childComments.firstOrNull()?.objectOrNull()?.keys?.sorted().orEmpty()}"
}

private fun JsonObject.toQuestionContent(fallbackQuestionId: String): ZhihuContent {
    val author = get("author").objectOrNull()
    val id = string("id") ?: fallbackQuestionId
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Question,
        title = string("title") ?: "知乎问题",
        excerpt = string("excerpt").orEmpty(),
        contentHtml = string("detail")?.takeIf { it.isNotBlank() },
        imageUrls = thumbnailImageUrls(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        authorHeadline = author?.string("headline").orEmpty(),
        questionId = id,
        answerCount = long("answer_count") ?: 0L,
        followerCount = long("follower_count") ?: 0L,
        voteupCount = 0L,
        commentCount = long("comment_count") ?: 0L,
        createdTime = long("created") ?: long("created_time") ?: Clock.System.now().epochSeconds,
        url = string("url"),
    )
}

private fun JsonObject.thumbnailImageUrls(): List<String> =
    buildList {
        string("thumbnail")?.let(::add)
        get("thumbnails")
            .arrayOrEmpty()
            .mapNotNull { it.imageUrlOrNull() }
            .forEach(::add)
        get("thumbnail_info").objectOrNull()?.imageUrlOrNull()?.let(::add)
        get("thumbnail_info")
            .arrayOrEmpty()
            .mapNotNull { it.imageUrlOrNull() }
            .forEach(::add)
        get("image_list")
            .arrayOrEmpty()
            .mapNotNull { it.imageUrlOrNull() }
            .forEach(::add)
        get("images")
            .arrayOrEmpty()
            .mapNotNull { it.imageUrlOrNull() }
            .forEach(::add)
        get("image").objectOrNull()?.firstImageUrl()?.let(::add)
        get("cover").objectOrNull()?.firstImageUrl()?.let(::add)
        string("image_url")?.let(::add)
        string("imageUrl")?.let(::add)
        string("thumbnail_url")?.let(::add)
    }.filter { it.isNotBlank() }
        .distinct()

private fun JsonObject.pinContentHtml(): String? {
    val content = get("content") ?: return null
    return when (content) {
        is JsonPrimitive -> content.contentOrNull
        is JsonObject ->
            content.string("content")
                ?: content.string("text")
                ?: content.string("title")
                ?: content.firstImageUrl()?.let { """<img src="$it"/>""" }
        is JsonArray ->
            content
                .mapNotNull { item ->
                    val obj = item.objectOrNull() ?: return@mapNotNull (item as? JsonPrimitive)?.contentOrNull
                    obj.string("content")
                        ?: obj.string("text")
                        ?: obj.string("title")
                        ?: obj.firstImageUrl()?.let { """<figure><img src="$it"/></figure>""" }
                }.joinToString("")
                .takeIf { it.isNotBlank() }
    }?.takeIf { it.isNotBlank() }
}

private fun JsonObject.pinImageUrls(): List<String> =
    get("content")
        .arrayOrEmpty()
        .mapNotNull { it.objectOrNull()?.firstImageUrl() }

private fun JsonElement.imageUrlOrNull(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> firstImageUrl()
        else -> null
    }

private fun JsonObject.firstImageUrl(): String? =
    string("url")
        ?: string("image_url")
        ?: string("imageUrl")
        ?: string("src")
        ?: string("thumbnail")
        ?: string("thumbnail_url")
        ?: string("original")
        ?: string("original_url")
        ?: get("image").objectOrNull()?.firstImageUrl()

private fun JsonObject.throwIfZhihuError(
    scope: String,
    id: String,
) {
    val error = get("error").objectOrNull() ?: return
    error("Zhihu $scope error id=$id code=${error.string("code")} message=${error.string("message")}")
}

private fun JsonObject.zhihuErrorCode(): String? = get("error").objectOrNull()?.string("code")

private fun String.stripZhihuHighlightHtml(): String = replace(Regex("</?em>", RegexOption.IGNORE_CASE), "")

private fun String.cleanZhihuDailyBodyHtml(): String =
    replace(
        Regex("""<div class="headline">.*?<div class="content-inner">""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        """<div class="content-inner">""",
    ).replace(
        Regex("""<div class="meta">.*?</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        "",
    ).replace(
        Regex("""<h2 class="question-title">\s*</h2>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        "",
    ).replace(
        Regex(""">\s+<"""),
        "><",
    )

private fun String.toZhihuContentType(): ZhihuContentType? =
    when (lowercase()) {
        "answer" -> ZhihuContentType.Answer
        "article" -> ZhihuContentType.Article
        "daily" -> ZhihuContentType.Daily
        "pin" -> ZhihuContentType.Pin
        "question" -> ZhihuContentType.Question
        else ->
            when {
                contains("answer", ignoreCase = true) -> ZhihuContentType.Answer
                contains("article", ignoreCase = true) || contains("zhuanlan", ignoreCase = true) -> ZhihuContentType.Article
                contains("daily", ignoreCase = true) -> ZhihuContentType.Daily
                contains("pin", ignoreCase = true) -> ZhihuContentType.Pin
                contains("question", ignoreCase = true) -> ZhihuContentType.Question
                else -> null
            }
    }

private fun String.normalizeZhihuContentId(type: ZhihuContentType): String =
    when (type) {
        ZhihuContentType.Answer,
        ZhihuContentType.Article,
        ZhihuContentType.Daily,
        ZhihuContentType.Pin,
        ZhihuContentType.Question,
        -> Regex("""\d+""").find(this)?.value ?: this
    }

internal fun String?.zhihuDailyEpochSecondsOrNow(): Long =
    this?.toZhihuDailyDateOrNull()
        ?.let { LocalDateTime(it.year, it.month.ordinal + 1, it.day, 0, 0).toInstant(TimeZone.UTC).epochSeconds }
        ?: Clock.System.now().epochSeconds

internal fun String.zhihuDailyBeforeDate(): String =
    toZhihuDailyDateOrNull()
        ?.plus(DatePeriod(days = 1))
        ?.formatZhihuDailyDate()
        ?: this

private fun String.toZhihuDailyDateOrNull(): LocalDate? {
    val value = filter { it.isDigit() }
    if (value.length != 8) return null
    val year = value.substring(0, 4).toIntOrNull() ?: return null
    val month = value.substring(4, 6).toIntOrNull() ?: return null
    val day = value.substring(6, 8).toIntOrNull() ?: return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

private fun LocalDate.formatZhihuDailyDate(): String =
    year.toString().padStart(4, '0') +
        (month.ordinal + 1).toString().padStart(2, '0') +
        day.toString().padStart(2, '0')

private fun Map<String, String>.toCookieHeader(): String =
    entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .joinToString("; ") { (name, value) -> "$name=$value" }

private fun String.needsZhihuWebSignature(): Boolean =
    isZhihuSearchUrl() ||
        startsWith("https://www.zhihu.com/api/v4/members/")

private fun String.isZhihuSearchUrl(): Boolean = startsWith("https://www.zhihu.com/api/v4/search_v3")

private fun String.zhihuApiPath(): String =
    when {
        startsWith("https://www.zhihu.com") -> removePrefix("https://www.zhihu.com")
        startsWith("https://api.zhihu.com") -> removePrefix("https://api.zhihu.com")
        else -> this
    }

private fun JsonObject.string(key: String): String? =
    when (val primitive = get(key) as? JsonPrimitive) {
        null -> null
        else -> primitive.contentOrNull
    }

private fun JsonObject.long(key: String): Long? =
    when (val primitive = get(key) as? JsonPrimitive) {
        null -> null
        else -> primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
    }

private fun JsonObject.boolean(key: String): Boolean? =
    when (val primitive = get(key) as? JsonPrimitive) {
        null -> null
        else -> primitive.contentOrNull?.toBooleanStrictOrNull()
    }

private fun JsonObject.valueForLog(key: String): String =
    when (val value = this[key]) {
        null -> "<missing>"
        is JsonObject -> "{keys=${value.keys.sorted()}}"
        is JsonArray -> "[size=${value.size}]"
        is JsonPrimitive -> value.contentOrNull.orEmpty().take(80)
    }

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()

public const val ZHIHU_WEB_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

private const val ZHIHU_DEVICE_ID: String = "FlareAndroidZhihuClient000000000000="
private const val ZHIHU_SEARCH_LIMIT: Int = 20

// Fixed x-zst-81 value from fake-light's maintained Zhihu search_v3 reverse-engineering sample:
// https://github.com/fake-light/LearningDoc/tree/main/%E9%80%86%E5%90%91/%E7%9F%A5%E4%B9%8E
private const val ZHIHU_SEARCH_X_ZST_81: String =
    "3_2.0aR_sn77yn6O92wOB8hPZnQr0EMYxc4f18wNBUgpTQ6nxERFZm0Y0-4Lm-h3_tufIwJS8gcxTgJS_AuPZNcXCTwxI78YxEM20s4PGDwN8gGcYAupMWufIeQuK7AFpS6O1vukyQ_R0rRnsyukMGvxBEqeCiRnxEL2ZZrxmDucmqhPXnXFMTAoTF6RhRuLPFMYBMiN_ZcNGwrO1IrH0xGC_6XxLhcxMS_xfwJXBAqxLSiO_c_V94qC0X9STvLXOkXtm7GYYbQUmJ9N_hGo0UuVC24NfwuFBTvxMPCxCagg9qwFpquwmiqo_XJSBfqCYFCOONGFG9COM6hXOkLeCPCe0thU8BuFMQ8VB5wF_sDU_V9F9Oqfzdh302iCqSieXUbxmiU3foC3qQ9w_wDe9KM29eDg_gwCG6LLYYHLYhhg_2wHKoUOCbwe8HvN8S8oMJX304h30pJHLxgV1FhN8wUL0Sco1rMXOQTLC18CC"

private val ZSE_CK_REGEX = Regex("""__g\.ck\|\|"([\w+/=\\]*?)",_=""")
