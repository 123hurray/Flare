package dev.dimension.flare.data.network.jike.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.jike.model.JikeComment
import dev.dimension.flare.data.network.jike.model.JikeCommentsRequest
import dev.dimension.flare.data.network.jike.model.JikeMediaMetaResponse
import dev.dimension.flare.data.network.jike.model.JikePost
import dev.dimension.flare.data.network.jike.model.JikePostActionRequest
import dev.dimension.flare.data.network.jike.model.JikePostActionResponse
import dev.dimension.flare.data.network.jike.model.JikeResponse
import dev.dimension.flare.data.network.jike.model.JikeSearchRequest
import dev.dimension.flare.data.network.jike.model.JikeThreadCommentsRequest
import dev.dimension.flare.data.network.jike.model.JikeTimelineRequest
import dev.dimension.flare.data.network.jike.model.JikeTopic
import dev.dimension.flare.data.network.jike.model.JikeTopicTimelineRequest
import dev.dimension.flare.data.network.jike.model.JikeUserTimelineRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

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
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikePost>>

    /**
     * Get featured/explore timeline.
     * POST /1.0/recommendFeed/list
     */
    @POST("1.0/recommendFeed/list")
    suspend fun getFeaturedTimeline(
        @Body request: JikeTimelineRequest,
        @Header("Content-Type") contentType: String = "application/json",
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
     * Get a single repost by ID.
     * GET /1.0/reposts/get
     */
    @GET("1.0/reposts/get")
    suspend fun getRepost(
        @Query("id") postId: String,
    ): JikeResponse<JikePost>

    /**
     * Get user's posts.
     * POST /1.0/personalUpdate/single
     */
    @POST("1.0/personalUpdate/single")
    suspend fun getUserPosts(
        @Body request: JikeUserTimelineRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikePost>>

    /**
     * Get primary comments for a post/repost.
     * POST /1.0/comments/listPrimary
     */
    @POST("1.0/comments/listPrimary")
    suspend fun getPrimaryComments(
        @Body request: JikeCommentsRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikeComment>>

    /**
     * Get comments in a comment thread.
     * POST /1.0/comments/list
     */
    @POST("1.0/comments/list")
    suspend fun getThreadComments(
        @Body request: JikeThreadCommentsRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikeComment>>

    /**
     * Get a playable video URL for a post/repost with video metadata.
     * POST /1.0/mediaMeta/interactive?id=...&type=...
     */
    @POST("1.0/mediaMeta/interactive")
    suspend fun getMediaMeta(
        @Query("id") id: String,
        @Query("type") type: String,
        @Body request: JsonObject = buildJsonObject { },
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeMediaMetaResponse

    /**
     * Search across Jike resources.
     * POST /1.0/search/integrate
     */
    @POST("1.0/search/integrate")
    suspend fun search(
        @Body request: JikeSearchRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikePost>>

    @POST("1.0/collections/list")
    suspend fun getCollections(
        @Body request: JikeTimelineRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikePost>>

    @GET("1.0/topics/getDetail")
    suspend fun getTopicDetail(
        @Query("id") topicId: String,
    ): JikeResponse<JikeTopic>

    @POST("1.0/topics/tabs/{tab}/feed")
    suspend fun getTopicFeed(
        @Path("tab") tab: String,
        @Body request: JikeTopicTimelineRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikeResponse<List<JikePost>>

    @POST("1.0/originalPosts/collect")
    suspend fun collectPost(
        @Body request: JikePostActionRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikePostActionResponse

    @POST("1.0/originalPosts/uncollect")
    suspend fun uncollectPost(
        @Body request: JikePostActionRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikePostActionResponse

    @POST("1.0/reposts/collect")
    suspend fun collectRepost(
        @Body request: JikePostActionRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikePostActionResponse

    @POST("1.0/reposts/uncollect")
    suspend fun uncollectRepost(
        @Body request: JikePostActionRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): JikePostActionResponse
}
