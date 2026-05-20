package dev.dimension.flare.data.network.jike.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.jike.model.JikeResponse
import dev.dimension.flare.data.network.jike.model.JikeUser

/**
 * Jike User API endpoints.
 */
internal interface JikeUserApi {
    /**
     * Get user profile by ID.
     * GET /1.0/users/profile
     */
    @GET("1.0/users/profile")
    suspend fun getUserProfile(
        @Query("username") username: String,
    ): JikeResponse<JikeUser>

    /**
     * Get current user's profile.
     * GET /1.0/users/profile (no username = current user)
     */
    @GET("1.0/users/profile")
    suspend fun getSelfProfile(): JikeResponse<JikeUser>
}
