package dev.dimension.flare.data.network.dongqiudi

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal class DongqiudiService {
    private val client =
        ktorClient {
            install(ContentNegotiation) {
                json(JSON)
            }
        }
    private val userProfileCache = mutableMapOf<String, DongqiudiUser>()

    suspend fun homeFeed(nextUrl: String? = null): DongqiudiTimelinePage {
        val root =
            requestJson(nextUrl ?: "https://api.dongqiudi.com/app/tabs/android/1.json")
        return DongqiudiTimelinePage(
            items =
                root["articles"]
                    .arrayOrEmpty()
                    .mapNotNull { it.objectOrNull()?.toArticleSummary() },
            nextUrl = root.string("next"),
        )
    }

    suspend fun articleDetail(id: String): DongqiudiArticle {
        val root = requestJson("https://api.dongqiudi.com/v2/article/detail/$id")
        return root["data"]
            .objectOrNull()
            ?.toArticleDetail()
            ?: error("Dongqiudi detail is empty: $id")
    }

    suspend fun userProfile(userId: String): DongqiudiUser {
        userProfileCache[userId]?.let { return it }
        val root = requestJson("https://api.dongqiudi.com/users/profile/$userId")
        val user =
            root["user"]
                .objectOrNull()
                ?.toUserProfile()
                ?: error("Dongqiudi user profile is empty: $userId")
        userProfileCache[userId] = user
        return user
    }

    suspend fun searchArticles(
        query: String,
        page: Int,
    ): DongqiudiTimelinePage {
        val root =
            requestJson(
                "https://api.dongqiudi.com/search?keywords=${query.encodeURLParameter()}&type=all&page=$page",
            )
        val items =
            root["news"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toArticleSummary() }
        return DongqiudiTimelinePage(
            items = items,
            nextUrl = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }

    suspend fun searchUsers(
        query: String,
        page: Int,
    ): DongqiudiUserPage {
        val root =
            requestJson(
                "https://api.dongqiudi.com/search?keywords=${query.encodeURLParameter()}&type=user&page=$page",
            )
        val users =
            root["users"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toSearchUser() }
        return DongqiudiUserPage(
            items = users,
            nextPage = if (users.isEmpty()) null else page + 1,
        )
    }

    suspend fun hotComments(articleId: String): List<DongqiudiComment> {
        val root = requestJson("https://api.dongqiudi.com/v2/article/$articleId/hot?size=30&version=576")
        root.logCommentStructure("hot", articleId)
        val data = root["data"].objectOrNull() ?: return emptyList()
        val users =
            data["user_list"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toCommentUser() }
                .associateBy { it.id }
        return data["comment_list"]
            .arrayOrEmpty()
            .mapNotNull { it.objectOrNull()?.toComment(users) }
    }

    suspend fun comments(
        articleId: String,
        nextUrl: String? = null,
    ): DongqiudiCommentPage {
        val root =
            requestJson(
                nextUrl
                    ?: "https://api.dongqiudi.com/v2/article/$articleId/comment?sort=up&size=30&version=576&platform=h5",
            )
        root.logCommentStructure("root", articleId)
        val data = root["data"].objectOrNull() ?: return DongqiudiCommentPage(emptyList(), null, true)
        val users =
            data["user_list"]
                .arrayOrEmpty()
                .mapNotNull { it.objectOrNull()?.toCommentUser() }
                .associateBy { it.id }
        return DongqiudiCommentPage(
            comments =
                data["comment_list"]
                    .arrayOrEmpty()
                    .mapNotNull { it.objectOrNull()?.toComment(users) },
            nextUrl = data.string("next"),
            isEnd = data.string("next").isNullOrBlank(),
        )
    }

    suspend fun comment(
        articleId: String,
        commentId: String,
    ): DongqiudiComment? {
        val hot = hotComments(articleId).firstOrNull { it.id == commentId }
        if (hot != null) return hot
        var nextUrl: String? = null
        repeat(3) {
            val page = comments(articleId, nextUrl)
            page.comments.firstOrNull { it.id == commentId }?.let { return it }
            if (page.isEnd) return null
            nextUrl = page.nextUrl
        }
        return null
    }

    suspend fun commentReplies(
        articleId: String,
        rootCommentId: String,
    ): List<DongqiudiComment> {
        val candidates =
            listOf(
                "https://api.dongqiudi.com/v2/comment/$rootCommentId?article_id=$articleId&size=30&version=576",
            )
        candidates.forEach { url ->
            val comments =
                runCatching {
                    val root = requestJson(url)
                    root.logCommentStructure("reply", "$articleId:$rootCommentId")
                    val data = root["data"].objectOrNull() ?: root
                    val users =
                        data["user_list"]
                            .arrayOrEmpty()
                            .mapNotNull { it.objectOrNull()?.toCommentUser() }
                            .associateBy { it.id }
                    val replies =
                        data["reply_list"]
                            .arrayOrEmpty()
                            .ifEmpty { data["comment_list"].arrayOrEmpty() }
                    replies
                        .mapNotNull { it.objectOrNull()?.toComment(users) }
                }.getOrElse {
                    println("DongqiudiService: comment replies unavailable articleId=$articleId commentId=$rootCommentId url=$url message=${it.message}")
                    emptyList()
                }
            if (comments.isNotEmpty()) {
                return comments
            }
        }
        return emptyList()
    }

    private suspend fun requestJson(url: String): JsonObject {
        val body =
            client
                .get(url) {
                header("User-Agent", DONGQIUDI_USER_AGENT)
                header("Accept", "application/json")
                }.bodyAsText()
        if (url.contains("/comment") || url.contains("/hot")) {
            logRawCommentBody(
                scope = "DongqiudiService",
                id = url,
                body = body,
            )
        }
        return JSON.decodeFromString(body)
    }

    private fun JsonObject.toArticleSummary(): DongqiudiArticle =
        DongqiudiArticle(
            id = string("id").orEmpty(),
            title = string("title").orEmpty().stripDongqiudiHighlightHtml(),
            description = string("description").orEmpty().stripDongqiudiHighlightHtml(),
            bodyHtml = null,
            thumbnail = string("thumb"),
            commentsTotal = long("comments_total") ?: 0L,
            showTime = long("show_time") ?: long("sort_timestamp") ?: 0L,
            writer = string("author_name") ?: string("media_name") ?: string("author") ?: string("writer") ?: "懂球帝",
            userId = string("showid") ?: string("user_id") ?: "dongqiudi",
            authorAvatar = string("author_avatar").orEmpty(),
            shareUrl = string("share"),
        )

    private fun JsonObject.toArticleDetail(): DongqiudiArticle =
        DongqiudiArticle(
            id = string("article_id").orEmpty(),
            title = string("title").orEmpty(),
            description = string("description").orEmpty(),
            bodyHtml = string("body"),
            thumbnail = null,
            commentsTotal = long("comments_total") ?: 0L,
            showTime = long("show_time") ?: 0L,
            writer = string("writer") ?: string("author_name") ?: "懂球帝",
            userId = string("user_id") ?: string("showid") ?: string("writer_url")?.substringAfterLast("/") ?: "dongqiudi",
            authorAvatar = string("author_avatar").orEmpty(),
            shareUrl = "https://www.dongqiudi.com/article/${string("article_id").orEmpty()}",
        )

    private fun JsonObject.toUserProfile(): DongqiudiUser =
        DongqiudiUser(
            id = string("id").orEmpty(),
            name = string("username2") ?: string("username") ?: string("name").orEmpty(),
            avatar = string("avatar").orEmpty(),
            description = string("description").orEmpty(),
            fansCount = long("fans_count") ?: long("fans") ?: 0L,
            followsCount = long("follows_count") ?: long("follows") ?: 0L,
            statusesCount = long("articles_count") ?: long("posts_count") ?: 0L,
        )

    private fun JsonObject.toCommentUser(): DongqiudiCommentUser? {
        val id = string("id") ?: return null
        return DongqiudiCommentUser(
            id = id,
            name = string("username2") ?: string("username") ?: id,
            avatar = string("avatar").orEmpty(),
            )
    }

    private fun JsonObject.toSearchUser(): DongqiudiUser? {
        val id = string("id")?.takeIf { it.isNotBlank() } ?: return null
        return DongqiudiUser(
            id = id,
            name = (string("username") ?: string("name") ?: id).stripDongqiudiHighlightHtml(),
            avatar = string("avatar").orEmpty(),
            description = string("region").orEmpty(),
            fansCount = 0L,
            followsCount = 0L,
            statusesCount = long("up_total") ?: 0L,
        )
    }

    private fun JsonObject.toComment(users: Map<String, DongqiudiCommentUser>): DongqiudiComment? {
        val id = string("id") ?: return null
        val userId = string("user_id").orEmpty()
        return DongqiudiComment(
            id = id,
            articleId = string("article_id").orEmpty(),
            content = string("content").orEmpty(),
            showTime = long("show_time") ?: 0L,
            upCount = long("up") ?: 0L,
            replyTotal = long("reply_total") ?: 0L,
            user = users[userId] ?: DongqiudiCommentUser(userId.ifBlank { "unknown" }, userId.ifBlank { "懂球帝用户" }, ""),
            attachments =
                get("attachments")
                    .arrayOrEmpty()
                    .mapNotNull { it.objectOrNull()?.toAttachment() },
        )
    }

    private fun JsonObject.toAttachment(): DongqiudiAttachment? {
        val url = string("url") ?: string("thumb") ?: return null
        return DongqiudiAttachment(
            url = url,
            previewUrl = string("thumb") ?: url,
            width = long("width") ?: 0L,
            height = long("height") ?: 0L,
        )
    }

    private fun JsonObject.logCommentStructure(
        kind: String,
        id: String,
    ) {
        val data = this["data"].objectOrNull() ?: this
        val comments =
            data["comment_list"]
                .arrayOrEmpty()
                .ifEmpty { data["reply_list"].arrayOrEmpty() }
        println(
            "DongqiudiService: commentRawSummary kind=$kind id=$id rootKeys=${keys.sorted()} " +
                "dataKeys=${data.keys.sorted()} count=${comments.size} next=${data.string("next").orEmpty()}",
        )
        comments
            .take(3)
            .forEachIndexed { index, item ->
                val comment = item.objectOrNull() ?: return@forEachIndexed
                println("DongqiudiService: commentRawSample kind=$kind index=$index ${comment.commentStructureForLog()}")
            }
    }

    private fun JsonObject.commentStructureForLog(): String {
        val attachments = get("attachments").arrayOrEmpty()
        val statementList = get("comment_statement_list").arrayOrEmpty()
        return "keys=${keys.sorted()} id=${string("id").orEmpty()} articleId=${string("article_id").orEmpty()} " +
            "userId=${string("user_id").orEmpty()} replyTotal=${string("reply_total") ?: long("reply_total")?.toString().orEmpty()} " +
            "parent=${string("parent_id").orEmpty()} root=${string("root_id").orEmpty()} " +
            "attachments=${attachments.size} statementList=${statementList.size} " +
            "statementSampleKeys=${statementList.firstOrNull()?.objectOrNull()?.keys?.sorted().orEmpty()}"
    }
}

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

internal data class DongqiudiTimelinePage(
    val items: List<DongqiudiArticle>,
    val nextUrl: String?,
)

internal data class DongqiudiCommentPage(
    val comments: List<DongqiudiComment>,
    val nextUrl: String?,
    val isEnd: Boolean,
)

internal data class DongqiudiUserPage(
    val items: List<DongqiudiUser>,
    val nextPage: Int?,
)

internal data class DongqiudiArticle(
    val id: String,
    val title: String,
    val description: String,
    val bodyHtml: String?,
    val thumbnail: String?,
    val commentsTotal: Long,
    val showTime: Long,
    val writer: String,
    val userId: String,
    val authorAvatar: String,
    val shareUrl: String?,
)

internal data class DongqiudiUser(
    val id: String,
    val name: String,
    val avatar: String,
    val description: String,
    val fansCount: Long,
    val followsCount: Long,
    val statusesCount: Long,
)

internal data class DongqiudiComment(
    val id: String,
    val articleId: String,
    val content: String,
    val showTime: Long,
    val upCount: Long,
    val replyTotal: Long,
    val user: DongqiudiCommentUser,
    val attachments: List<DongqiudiAttachment>,
)

internal data class DongqiudiCommentUser(
    val id: String,
    val name: String,
    val avatar: String,
)

internal data class DongqiudiAttachment(
    val url: String,
    val previewUrl: String,
    val width: Long,
    val height: Long,
)

private const val DONGQIUDI_USER_AGENT = "Dongqiudi/8.4.0 Android"

private fun String.stripDongqiudiHighlightHtml(): String =
    replace(Regex("</?(?:font|em)\\b[^>]*>", RegexOption.IGNORE_CASE), "")

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

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()
