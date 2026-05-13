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
    @SerialName("avatarUrl")
    val avatarUrl: String? = null,
    @SerialName("bio")
    val bio: String? = null,
    @SerialName("followersCount")
    val followersCount: Int = 0,
    @SerialName("followingCount")
    val followingCount: Int = 0,
    @SerialName("isVerified")
    val isVerified: Boolean = false,
)
