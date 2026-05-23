package dev.dimension.flare.data.network.instagram

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.instagramWebHost
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class InstagramService(
    private val accountKey: MicroBlogKey? = null,
    private val cookiesFlow: Flow<Map<String, String>>,
) {
    private val client =
        ktorClient {
            install(ContentNegotiation) {
                json(JSON)
            }
        }

    suspend fun me(): InstagramUser {
        val cookies = cookiesFlow.first()
        val userId = requireNotNull(cookies["ds_user_id"]?.takeIf { it.isNotBlank() }) {
            "Missing ds_user_id cookie"
        }
        return userInfo(userId)
    }

    suspend fun userInfo(userId: String): InstagramUser {
        val root =
            requestJson("https://www.instagram.com/api/v1/users/$userId/info/") {
                parameter("entry_point", "profile")
            }
                .objectOrNull()
                ?: throw IllegalStateException("Instagram profile response is not an object")
        return root["user"].objectOrNull()?.toInstagramUser()
            ?: throw IllegalStateException("Instagram profile is empty")
    }

    suspend fun userByUsername(username: String): InstagramUser {
        val root =
            requestJson("https://www.instagram.com/api/v1/users/web_profile_info/") {
                parameter("username", username.trim().removePrefix("@"))
            }.objectOrNull()
                ?: throw IllegalStateException("Instagram profile response is not an object")
        return root["data"]
            .objectOrNull()
            ?.get("user")
            .objectOrNull()
            ?.toInstagramUser()
            ?: throw IllegalStateException("Instagram profile is empty")
    }

    suspend fun homeFeed(maxId: String? = null): InstagramTimelinePage {
        val root =
            requestJson("https://www.instagram.com/api/v1/feed/timeline/") {
                parameter("count", 12)
                if (!maxId.isNullOrBlank()) {
                    parameter("max_id", maxId)
                }
            }.objectOrNull()
                ?: throw IllegalStateException("Instagram timeline response is not an object")
        return root.toTimelinePage()
    }

    suspend fun userFeed(
        userId: String,
        maxId: String? = null,
    ): InstagramTimelinePage {
        val root =
            requestJson("https://www.instagram.com/api/v1/feed/user/$userId/") {
                parameter("count", 12)
                if (!maxId.isNullOrBlank()) {
                    parameter("max_id", maxId)
                }
            }.objectOrNull()
                ?: throw IllegalStateException("Instagram user timeline response is not an object")
        return root.toTimelinePage()
    }

    suspend fun mediaInfo(mediaId: String): InstagramMedia {
        val root =
            requestJson("https://www.instagram.com/api/v1/media/$mediaId/info/") {
                parameter("entry_point", "profile")
            }.objectOrNull()
                ?: throw IllegalStateException("Instagram media response is not an object")
        return root["items"]
            .arrayOrEmpty()
            .firstOrNull()
            .objectOrNull()
            ?.toInstagramMedia()
            ?: throw IllegalStateException("Instagram media is empty")
    }

    private fun JsonObject.toTimelinePage(): InstagramTimelinePage {
        val feedItems =
            this["items"].arrayOrNull()
                ?: this["feed_items"].arrayOrNull()
                ?: emptyList()
        val items =
            feedItems
                .mapNotNull { item ->
                    val json = item.objectOrNull() ?: return@mapNotNull null
                    json["media_or_ad"].objectOrNull()
                        ?: json["explore_story"].objectOrNull()?.get("media_or_ad").objectOrNull()
                        ?: json
                }.mapNotNull { it.toInstagramMedia() }
        return InstagramTimelinePage(
            items = items,
            nextMaxId = string("next_max_id"),
            moreAvailable = boolean("more_available"),
        )
    }

    private suspend fun requestJson(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): JsonElement {
        val response =
            client.get(url) {
                instagramHeaders(cookiesFlow.first())
                block()
            }
        val body = response.bodyAsText()
        logRawResponse(url = url, response = response, body = body)
        validate(response)
        return JSON.parseToJsonElement(body)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.instagramHeaders(cookies: Map<String, String>) {
        header(HttpHeaders.UserAgent, INSTAGRAM_WEB_USER_AGENT)
        header(HttpHeaders.Accept, "*/*")
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        header("Referer", "https://$instagramWebHost/")
        header("Origin", "https://$instagramWebHost")
        header("X-ASBD-ID", "129477")
        header("X-IG-App-ID", INSTAGRAM_WEB_APP_ID)
        header("X-Requested-With", "XMLHttpRequest")
        cookies["csrftoken"]?.takeIf { it.isNotBlank() }?.let {
            header("X-CSRFToken", it)
        }
        header(HttpHeaders.Cookie, cookies.toCookieHeader())
    }

    private fun validate(response: HttpResponse) {
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            accountKey?.let { throw LoginExpiredException(it, PlatformType.Instagram) }
            throw IllegalStateException("Instagram login expired: HTTP ${response.status.value}")
        }
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Instagram request failed: HTTP ${response.status.value}")
        }
    }

    private fun logRawResponse(
        url: String,
        response: HttpResponse,
        body: String,
    ) {
        val line =
            "InstagramRawResponse url=$url status=${response.status.value} " +
                "contentType=${response.headers[HttpHeaders.ContentType].orEmpty()} body=$body"
        println(line)
    }
}

public const val INSTAGRAM_WEB_APP_ID: String = "936619743392459"
public const val INSTAGRAM_WEB_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

internal data class InstagramTimelinePage(
    val items: List<InstagramMedia>,
    val nextMaxId: String?,
    val moreAvailable: Boolean,
)

internal data class InstagramUser(
    val id: String,
    val username: String,
    val fullName: String,
    val biography: String,
    val profilePicUrl: String,
    val followerCount: Long,
    val followingCount: Long,
    val mediaCount: Long,
    val isPrivate: Boolean,
    val isVerified: Boolean,
)

internal data class InstagramMedia(
    val id: String,
    val code: String,
    val caption: String,
    val takenAt: Long,
    val user: InstagramUser?,
    val images: List<InstagramImage>,
)

internal data class InstagramImage(
    val url: String,
    val width: Float,
    val height: Float,
)

private fun JsonObject.toInstagramUser(): InstagramUser =
    InstagramUser(
        id = string("pk") ?: string("id").orEmpty(),
        username = string("username").orEmpty(),
        fullName = string("full_name").orEmpty(),
        biography = string("biography").orEmpty(),
        profilePicUrl = string("profile_pic_url_hd") ?: string("profile_pic_url").orEmpty(),
        followerCount = number("follower_count") ?: number("edge_followed_by.count") ?: 0L,
        followingCount = number("following_count") ?: number("edge_follow.count") ?: 0L,
        mediaCount = number("media_count") ?: number("edge_owner_to_timeline_media.count") ?: 0L,
        isPrivate = boolean("is_private"),
        isVerified = boolean("is_verified"),
    )

private fun JsonObject.toInstagramMedia(): InstagramMedia? {
    val id = string("id") ?: string("pk") ?: return null
    val images =
        this["carousel_media"]
            .arrayOrEmpty()
            .mapNotNull { it.objectOrNull()?.bestImage() }
            .ifEmpty { listOfNotNull(bestImage()) }
    return InstagramMedia(
        id = id,
        code = string("code").orEmpty(),
        caption = captionText(),
        takenAt = number("taken_at") ?: number("taken_at_timestamp") ?: 0L,
        user = this["user"].objectOrNull()?.toInstagramUser(),
        images = images,
    )
}

private fun JsonObject.bestImage(): InstagramImage? {
    val candidates =
        this["image_versions2"]
            .objectOrNull()
            ?.get("candidates")
            .arrayOrEmpty()
            .mapNotNull { candidate ->
                val json = candidate.objectOrNull() ?: return@mapNotNull null
                val url = json.string("url") ?: return@mapNotNull null
                InstagramImage(
                    url = url,
                    width = (json.number("width") ?: 1L).toFloat(),
                    height = (json.number("height") ?: 1L).toFloat(),
                )
            }
    return candidates.maxByOrNull { it.width * it.height }
        ?: string("display_url")?.let { InstagramImage(it, 1f, 1f) }
}

private fun JsonObject.captionText(): String =
    string("caption.text")
        ?: this["caption"].objectOrNull()?.string("text")
        ?: this["edge_media_to_caption"]
            .objectOrNull()
            ?.get("edges")
            .arrayOrEmpty()
            .firstOrNull()
            .objectOrNull()
            ?.get("node")
            .objectOrNull()
            ?.string("text")
        ?: ""

private fun JsonObject.string(path: String): String? =
    path.split(".").fold(this as JsonElement?) { element, key ->
        element.objectOrNull()?.get(key)
    }.primitiveOrNull()?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.number(path: String): Long? =
    string(path)?.toLongOrNull()
        ?: path.split(".").fold(this as JsonElement?) { element, key ->
            element.objectOrNull()?.get(key)
        }.primitiveOrNull()?.contentOrNull?.toLongOrNull()

private fun JsonObject.boolean(path: String): Boolean =
    path.split(".").fold(this as JsonElement?) { element, key ->
        element.objectOrNull()?.get(key)
    }.primitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull() == true

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = arrayOrNull().orEmpty()

private fun JsonElement?.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun Map<String, String>.toCookieHeader(): String =
    entries.joinToString("; ") { (name, value) -> "$name=$value" }
