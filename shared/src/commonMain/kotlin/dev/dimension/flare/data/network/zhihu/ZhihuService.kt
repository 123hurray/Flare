package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.RequireReLoginException
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuWebHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
                        requestJson("https://www.zhihu.com/api/v4/articles/${key.id}?include=author,content,voteup_count,comment_count,excerpt,created")
                    ZhihuContentType.Pin ->
                        requestJson("https://www.zhihu.com/api/v4/pins/${key.id}")
                    ZhihuContentType.Question -> error("Question detail should be loaded from answers feed")
                }
            root.throwIfZhihuError("detail", statusId)
            root.toDetailContent(key)
        }.getOrElse {
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

    suspend fun comments(statusId: String): List<ZhihuComment> =
        runCatching {
            val key = ZhihuContentKey.parse(statusId)
            if (key.type == ZhihuContentType.Question) {
                return@runCatching emptyList()
            }
            val contentType =
                when (key.type) {
                    ZhihuContentType.Answer -> "answers"
                    ZhihuContentType.Article -> "articles"
                    ZhihuContentType.Pin -> "pins"
                    ZhihuContentType.Question -> "questions"
                }
            val root =
                requestJson(
                    "https://api.zhihu.com/comment_v5/$contentType/${key.id}/root_comment?order_by=score&limit=20",
                )
            root.throwIfZhihuError("comments", statusId)
            root["data"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toComment() }
        }.getOrElse {
            println("ZhihuService: comments unavailable statusId=$statusId message=${it.message}")
            emptyList()
        }

    private suspend fun requestJson(url: String): JsonObject {
        val cookies = cookiesFlow.first().filterValues { it.isNotBlank() }
        if (cookies["z_c0"].isNullOrBlank()) {
            throw RequireReLoginException(
                accountKey = accountKey ?: MicroBlogKey("account", zhihuWebHost),
                platformType = PlatformType.Zhihu,
            )
        }
        return client
            .get(url) {
                zhihuHeaders(url, cookies)
            }.body()
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
        header("x-api-version", "3.1.8")
        header("x-app-version", "10.12.0")
        header("x-app-bundleid", "com.zhihu.android")
        header("x-app-za", "OS=Android&VersionName=10.12.0&VersionCode=21210")
        header("Referer", "https://www.zhihu.com/")
        header("Origin", "https://www.zhihu.com")
        header("x-requested-with", "fetch")
        header(HttpHeaders.Cookie, cookies.toCookieHeader())
    }
}

internal data class ZhihuViewer(
    val id: String,
    val urlToken: String,
    val name: String,
    val avatar: String,
    val headline: String,
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
)

internal enum class ZhihuContentType(val prefix: String) {
    Answer("answer"),
    Article("article"),
    Pin("pin"),
    Question("question"),
}

internal data class ZhihuContentKey(
    val type: ZhihuContentType,
    val id: String,
) {
    companion object {
        fun parse(statusId: String): ZhihuContentKey {
            val prefix = statusId.substringBefore(':', missingDelimiterValue = "question")
            val id = statusId.substringAfter(':', missingDelimiterValue = statusId)
            val type =
                when (prefix) {
                    "answer" -> ZhihuContentType.Answer
                    "article" -> ZhihuContentType.Article
                    "pin" -> ZhihuContentType.Pin
                    else -> ZhihuContentType.Question
                }
            return ZhihuContentKey(type, id)
        }
    }
}

private fun JsonObject.toFeedContent(): ZhihuContent? {
    get("target").objectOrNull()?.toTargetContent()?.let { return it }

    val extra = get("extra").objectOrNull()
    val type = extra?.string("type")?.toZhihuContentType() ?: return null
    val id = extra.string("id") ?: return null
    val feedContent = get("common_card").objectOrNull()?.get("feed_content").objectOrNull() ?: return null
    val title = feedContent["title"].objectOrNull()?.string("panel_text").orEmpty()
    val excerpt = feedContent["content"].objectOrNull()?.string("panel_text").orEmpty()
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
    val type = string("type")?.toZhihuContentType() ?: return null
    val id = string("id") ?: return null
    val question = get("question").objectOrNull()
    val author = get("author").objectOrNull()
    return ZhihuContent(
        id = id,
        type = type,
        title = question?.string("title") ?: string("title") ?: "知乎内容",
        excerpt = string("excerpt") ?: string("excerpt_new") ?: string("detail") ?: string("content") ?: "",
        contentHtml = null,
        imageUrls = thumbnailImageUrls(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        authorHeadline = author?.string("headline").orEmpty(),
        questionId = question?.string("id") ?: if (type == ZhihuContentType.Question) id else null,
        answerCount = question?.long("answer_count") ?: if (type == ZhihuContentType.Question) long("answer_count") ?: 0L else 0L,
        followerCount = question?.long("follower_count") ?: if (type == ZhihuContentType.Question) long("follower_count") ?: 0L else 0L,
        voteupCount = long("voteup_count") ?: long("vote_count") ?: 0L,
        commentCount = long("comment_count") ?: 0L,
        createdTime = long("created_time") ?: long("created") ?: Clock.System.now().epochSeconds,
        url = string("url"),
    )
}

private fun JsonObject.toDetailContent(key: ZhihuContentKey): ZhihuContent {
    val question = get("question").objectOrNull()
    val author = get("author").objectOrNull()
    return ZhihuContent(
        id = key.id,
        type = key.type,
        title = question?.string("title") ?: string("title") ?: "知乎内容",
        excerpt = string("excerpt") ?: string("detail") ?: "",
        contentHtml = string("content") ?: string("detail"),
        imageUrls = thumbnailImageUrls(),
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

private fun JsonObject.toAnswerContent(questionId: String): ZhihuContent? {
    val id = string("id") ?: return null
    val author = get("author").objectOrNull()
    val question = get("question").objectOrNull()
    return ZhihuContent(
        id = id,
        type = ZhihuContentType.Answer,
        title = question?.string("title") ?: string("question") ?: "知乎回答",
        excerpt = string("excerpt").orEmpty(),
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
    return ZhihuComment(
        id = id,
        content = string("content").orEmpty(),
        authorName = author?.string("name") ?: "知乎用户",
        authorId = author?.string("url_token") ?: author?.string("id") ?: "zhihu",
        authorAvatar = author?.string("avatar_url") ?: author?.string("avatarUrl") ?: "",
        likeCount = long("like_count") ?: long("vote_count") ?: 0L,
        replyCount = long("child_comment_count") ?: 0L,
        createdTime = long("created_time") ?: Clock.System.now().epochSeconds,
    )
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
        get("image").objectOrNull()?.firstImageUrl()?.let(::add)
        get("cover").objectOrNull()?.firstImageUrl()?.let(::add)
        string("image_url")?.let(::add)
        string("imageUrl")?.let(::add)
    }.filter { it.isNotBlank() }
        .distinct()

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
        ?: get("image").objectOrNull()?.firstImageUrl()

private fun JsonObject.throwIfZhihuError(
    scope: String,
    id: String,
) {
    val error = get("error").objectOrNull() ?: return
    error("Zhihu $scope error id=$id code=${error.string("code")} message=${error.string("message")}")
}

private fun String.toZhihuContentType(): ZhihuContentType? =
    when (this) {
        "answer" -> ZhihuContentType.Answer
        "article" -> ZhihuContentType.Article
        "pin" -> ZhihuContentType.Pin
        "question" -> ZhihuContentType.Question
        else -> null
    }

private fun Map<String, String>.toCookieHeader(): String =
    entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .joinToString("; ") { (name, value) -> "$name=$value" }

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

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()

public const val ZHIHU_WEB_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

private const val ZHIHU_DEVICE_ID: String = "FlareAndroidZhihuClient000000000000="
