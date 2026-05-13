package dev.dimension.flare.data.datasource.jike

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class JikeHomeTimelineRemoteMediator(
    private val service: JikeService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "jike_home_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getHomeTimeline(limit = pageSize)
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(endOfPaginationReached = true)
                }

                is PagingRequest.Append -> {
                    service.getHomeTimeline(
                        limit = pageSize,
                        loadMoreKey = request.nextKey,
                    )
                }
            }

        if (!response.success) {
            throw LoginExpiredException(accountKey, PlatformType.Jike)
        }

        val data = response.data.orEmpty()
        // TODO: Map JikePost to UiTimelineV2
        return PagingResult(
            endOfPaginationReached = data.isEmpty(),
            data = emptyList(),
            nextKey = null,
        )
    }
}
