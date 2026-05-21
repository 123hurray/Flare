package dev.dimension.flare.data.datasource.jike

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.JikeUserTimelineRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class JikeUserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: JikeService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "jike_user_${userKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh ->
                    service.getUserPosts(
                        JikeUserTimelineRequest(
                            username = userKey.id,
                            limit = pageSize,
                        ),
                    )

                is PagingRequest.Prepend ->
                    return PagingResult(endOfPaginationReached = true)

                is PagingRequest.Append ->
                    service.getUserPosts(
                        JikeUserTimelineRequest(
                            username = userKey.id,
                            limit = pageSize,
                            loadMoreKey = request.nextKey.decodeJikeLoadMoreKey(),
                        ),
                    )
            }

        val data = response.data.orEmpty()
        return PagingResult(
            endOfPaginationReached = data.isEmpty(),
            data = data.toUiTimeline(accountKey, service),
            nextKey = response.loadMoreKey.encodeJikeLoadMoreKey(),
        )
    }
}
