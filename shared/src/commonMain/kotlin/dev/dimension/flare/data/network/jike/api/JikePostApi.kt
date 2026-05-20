package dev.dimension.flare.data.network.jike.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.jike.model.JikePost
import dev.dimension.flare.data.network.jike.model.JikeResponse
import dev.dimension.flare.data.network.jike.model.JikeTimelineRequest
import dev.dimension.flare.data.network.jike.model.JikeUserTimelineRequest

/**
 * Jike Post/Timeline API endpoints.
 */
internal interface JikePostApi {
    /**
     * Get home timeline (following updates).
     * POST /1.0/personalUpdate/followingUpdates
     */
    @POST("1.0/personalUpdate/followingUpdates")
    suspend fun getHomeTimeline(
        @Body request: JikeTimelineRequest,
    ): JikeResponse<List<JikePost>>

    /**
     * Get featured/explore timeline.
     * POST /1.0/recommendFeed/list
     */
    @POST("1.0/recommendFeed/list")
    suspend fun getFeaturedTimeline(
        @Body request: JikeTimelineRequest,
    ): JikeResponse<List<JikePost>>

    /**
     * Get a single post by ID.
     * GET /1.0/originalPosts/get
     */
    @GET("1.0/originalPosts/get")
    suspend fun getPost(
        @Query("id") postId: String,
    ): JikeResponse<JikePost>

    /**
     * Get user's posts.
     * POST /1.0/personalUpdate/single
     */
    @POST("1.0/personalUpdate/single")
    suspend fun getUserPosts(
        @Body request: JikeUserTimelineRequest,
    ): JikeResponse<List<JikePost>>
}
