package dev.dimension.flare.data.network.jike.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JikeUser(
    @SerialName("id")
    val id: String = "",
    @SerialName("username")
    val username: String = "",
    @SerialName("screenName")
    val screenName: String = "",
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("isVerified")
    val isVerified: Boolean = false,
    @SerialName("verifyMessage")
    val verifyMessage: String? = null,
    @SerialName("briefIntro")
    val briefIntro: String? = null,
    @SerialName("avatarImage")
    val avatarImage: JikeAvatarImage? = null,
    @SerialName("profileImageUrl")
    val profileImageUrl: String? = null,
    @SerialName("statsCount")
    val statsCount: JikeStatsCount? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("isBannedForever")
    val isBannedForever: Boolean = false,
    @SerialName("isSponsor")
    val isSponsor: Boolean = false,
    @SerialName("sponsorExpiresAt")
    val sponsorExpiresAt: String? = null,
) {
    /** Convenience property for avatar URL (prefers profileImageUrl, then avatarImage) */
    val avatarUrl: String?
        get() = profileImageUrl ?: avatarImage?.smallPicUrl
}

/**
 * Avatar image with multiple sizes.
 */
@Serializable
internal data class JikeAvatarImage(
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerialName("smallPicUrl")
    val smallPicUrl: String? = null,
    @SerialName("middlePicUrl")
    val middlePicUrl: String? = null,
    @SerialName("picUrl")
    val picUrl: String? = null,
    @SerialName("format")
    val format: String? = null,
)

/**
 * User statistics counts.
 */
@Serializable
internal data class JikeStatsCount(
    @SerialName("topicSubscribed")
    val topicSubscribed: Int = 0,
    @SerialName("topicCreated")
    val topicCreated: Int = 0,
    @SerialName("followedCount")
    val followedCount: Int = 0,
    @SerialName("followingCount")
    val followingCount: Int = 0,
    @SerialName("highlightedPersonalUpdates")
    val highlightedPersonalUpdates: Int = 0,
    @SerialName("liked")
    val liked: Int = 0,
    @SerialName("respectedCount")
    val respectedCount: Int = 0,
)
