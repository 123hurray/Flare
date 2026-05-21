package dev.dimension.flare.data.network.jike.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JikePost(
    @SerialName("id")
    val id: String = "",
    @SerialName("type")
    val type: String = "ORIGINAL_POST",
    @SerialName("content")
    val content: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("user")
    val user: JikeUser? = null,
    @SerialName("pictures")
    val pictures: List<JikePicture> = emptyList(),
    @SerialName("likeCount")
    val likeCount: Int = 0,
    @SerialName("commentCount")
    val commentCount: Int = 0,
    @SerialName("shareCount")
    val shareCount: Int = 0,
    @SerialName("isLiked")
    val isLiked: Boolean = false,
    @SerialName("repostCount")
    val repostCount: Int = 0,
    @SerialName("topic")
    val topic: JikeTopic? = null,
)

@Serializable
internal data class JikeComment(
    @SerialName("id")
    val id: String = "",
    @SerialName("targetId")
    val targetId: String = "",
    @SerialName("targetType")
    val targetType: String = "ORIGINAL_POST",
    @SerialName("threadId")
    val threadId: String? = null,
    @SerialName("level")
    val level: Int = 1,
    @SerialName("content")
    val content: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("user")
    val user: JikeUser? = null,
    @SerialName("pictures")
    val pictures: List<JikePicture> = emptyList(),
    @SerialName("likeCount")
    val likeCount: Int = 0,
    @SerialName("liked")
    val liked: Boolean = false,
    @SerialName("replyCount")
    val replyCount: Int = 0,
)

@Serializable
internal data class JikePicture(
    @SerialName("picUrl")
    val picUrl: String? = null,
    @SerialName("url")
    val url: String = "",
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerialName("smallPicUrl")
    val smallPicUrl: String? = null,
    @SerialName("middlePicUrl")
    val middlePicUrl: String? = null,
    @SerialName("width")
    val width: Int = 0,
    @SerialName("height")
    val height: Int = 0,
) {
    val bestUrl: String
        get() = picUrl ?: middlePicUrl ?: smallPicUrl ?: thumbnailUrl ?: url
}

@Serializable
internal data class JikeTopic(
    @SerialName("id")
    val id: String = "",
    @SerialName("content")
    val content: String? = null,
    @SerialName("title")
    val title: String = "",
) {
    val displayName: String
        get() = content ?: title
}
