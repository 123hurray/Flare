package dev.dimension.flare.data.network.jike.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JikePost(
    @SerialName("id")
    val id: String = "",
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
    @SerialName("topic")
    val topic: JikeTopic? = null,
)

@Serializable
internal data class JikePicture(
    @SerialName("url")
    val url: String = "",
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerialName("width")
    val width: Int = 0,
    @SerialName("height")
    val height: Int = 0,
)

@Serializable
internal data class JikeTopic(
    @SerialName("id")
    val id: String = "",
    @SerialName("title")
    val title: String = "",
)
