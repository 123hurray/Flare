package dev.dimension.flare.data.network.jike.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.jike.model.GetSmsCodeRequest
import dev.dimension.flare.data.network.jike.model.JikeResponse
import dev.dimension.flare.data.network.jike.model.PasswordLoginRequest
import dev.dimension.flare.data.network.jike.model.RefreshTokenRequest
import dev.dimension.flare.data.network.jike.model.SmsLoginRequest
import io.ktor.http.Headers

/**
 * Jike Authentication API endpoints.
 * Based on open-jike/jike-sdk and legacy Jike API documentation.
 */
internal interface JikeAuthApi {
    /**
     * Send SMS verification code to phone number.
     * POST /1.0/users/getSmsCode
     */
    @POST("1.0/users/getSmsCode")
    suspend fun getSmsCode(
        @Body request: GetSmsCodeRequest,
    ): JikeResponse<Unit>

    /**
     * Login with SMS code.
     * POST /1.0/users/mixLoginWithPhone
     *
     * Response headers contain:
     * - x-access-token
     * - x-refresh-token
     */
    @POST("1.0/users/mixLoginWithPhone")
    suspend fun loginWithSmsCode(
        @Body request: SmsLoginRequest,
        @Header("x-access-token") accessToken: String? = null,
        @Header("x-refresh-token") refreshToken: String? = null,
    ): JikeResponse<LoginResponse>

    /**
     * Login with phone and password.
     * POST /1.0/users/loginWithPhoneAndPassword
     */
    @POST("1.0/users/loginWithPhoneAndPassword")
    suspend fun loginWithPassword(
        @Body request: PasswordLoginRequest,
    ): JikeResponse<LoginResponse>

    /**
     * Refresh access token.
     * POST /app_auth_tokens.refresh
     */
    @POST("app_auth_tokens.refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest,
    ): JikeResponse<RefreshResponse>
}

/**
 * Response body for login endpoints.
 * Contains user profile info. Token is in response headers.
 */
@kotlinx.serialization.Serializable
internal data class LoginResponse(
    @kotlinx.serialization.SerialName("user")
    val user: dev.dimension.flare.data.network.jike.model.JikeUser? = null,
)

/**
 * Response body for token refresh.
 */
@kotlinx.serialization.Serializable
internal data class RefreshResponse(
    @kotlinx.serialization.SerialName("accessToken")
    val accessToken: String? = null,
)
