package dev.dimension.flare.data.network.jike.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.jike.model.JikePost
import dev.dimension.flare.data.network.jike.model.JikeResponse

/**
 * Jike Post/Timeline API endpoints.
 */
internal interface JikePostApi {
    /**
     * Get home timeline (following feed).
     * GET /1.0/timeline
     */
    @GET("1.0/timeline")
    suspend fun getHomeTimeline(
        @Query("limit") limit: Int = 20,
        @Query("loadMoreKey") loadMoreKey: String? = null,
    ): JikeResponse<List<JikePost>>

    /**
     * Get featured/explore timeline.
     * GET /1.0/timeline/featured
     */
    @GET("1.0/timeline/featured")
    suspend fun getFeaturedTimeline(
        @Query("limit") limit: Int = 20,
        @Query("loadMoreKey") loadMoreKey: String? = null,
    ): JikeResponse<List<JikePost>>

    /**
     * Get a single post by ID.
     * GET /1.0/posts/{id}
     */
    @GET("1.0/posts/{postId}")
    suspend fun getPost(
        @Path("postId") postId: String,
    ): JikeResponse<JikePost>

    /**
     * Get user's posts.
     * GET /1.0/posts/by-user
     */
    @GET("1.0/posts/by-user")
    suspend fun getUserPosts(
        @Query("username") username: String,
        @Query("limit") limit: Int = 20,
        @Query("loadMoreKey") loadMoreKey: String? = null,
    ): JikeResponse<List<JikePost>>
}
