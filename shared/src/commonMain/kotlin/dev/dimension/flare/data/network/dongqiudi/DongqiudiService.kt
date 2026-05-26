package dev.dimension.flare.data.network.dongqiudi

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
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

    suspend fun hotComments(articleId: String): List<DongqiudiComment> {
        val root = requestJson("https://api.dongqiudi.com/v2/article/$articleId/hot?size=30&version=576")
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

    private suspend fun requestJson(url: String): JsonObject =
        client
            .get(url) {
                header("User-Agent", DONGQIUDI_USER_AGENT)
                header("Accept", "application/json")
            }.body()

    private fun JsonObject.toArticleSummary(): DongqiudiArticle =
        DongqiudiArticle(
            id = string("id").orEmpty(),
            title = string("title").orEmpty(),
            description = string("description").orEmpty(),
            bodyHtml = null,
            thumbnail = string("thumb"),
            commentsTotal = long("comments_total") ?: 0L,
            showTime = long("sort_timestamp") ?: long("show_time") ?: 0L,
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
}

internal data class DongqiudiTimelinePage(
    val items: List<DongqiudiArticle>,
    val nextUrl: String?,
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
