package dev.dimension.flare.data.network.jike.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Base response wrapper for Jike API responses.
 */
@Serializable
internal data class JikeResponse<T>(
    @SerialName("data")
    val data: T? = null,
    @SerialName("user")
    val user: T? = null,
    @SerialName("loadMoreKey")
    val loadMoreKey: JsonObject? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("success")
    val success: Boolean = false,
)

@Serializable
internal data class JikeTimelineRequest(
    @SerialName("limit")
    val limit: Int = 20,
    @SerialName("loadMoreKey")
    val loadMoreKey: JsonObject? = null,
)

@Serializable
internal data class JikeUserTimelineRequest(
    @SerialName("username")
    val username: String,
    @SerialName("limit")
    val limit: Int = 20,
    @SerialName("loadMoreKey")
    val loadMoreKey: JsonObject? = null,
)

/**
 * Request body for sending SMS verification code.
 */
@Serializable
internal data class GetSmsCodeRequest(
    @SerialName("areaCode")
    val areaCode: String = "+86",
    @SerialName("mobilePhoneNumber")
    val mobilePhoneNumber: String,
)

/**
 * Request body for SMS code login.
 */
@Serializable
internal data class SmsLoginRequest(
    @SerialName("areaCode")
    val areaCode: String = "+86",
    @SerialName("mobilePhoneNumber")
    val mobilePhoneNumber: String,
    @SerialName("smsCode")
    val smsCode: String,
)

/**
 * Request body for password login.
 */
@Serializable
internal data class PasswordLoginRequest(
    @SerialName("areaCode")
    val areaCode: String = "+86",
    @SerialName("mobilePhoneNumber")
    val mobilePhoneNumber: String,
    @SerialName("password")
    val password: String,
)

/**
 * Request body for refreshing access token.
 */
@Serializable
internal data class RefreshTokenRequest(
    @SerialName("refreshToken")
    val refreshToken: String,
)
