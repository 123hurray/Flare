package dev.dimension.flare.data.datasource.jike

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.JikeSearchRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class JikeSearchStatusRemoteMediator(
    private val service: JikeService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "jike_search_${accountKey}_$query"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response =
            service.search(
                JikeSearchRequest(
                    keyword = query,
                    limit = pageSize,
                    loadMoreKey = (request as? PagingRequest.Append)?.nextKey.decodeJikeLoadMoreKey(),
                ),
            )
        val data = response.data.orEmpty()
        return PagingResult(
            endOfPaginationReached = response.loadMoreKey == null,
            data = data.toUiTimeline(accountKey, service),
            nextKey = response.loadMoreKey.encodeJikeLoadMoreKey(),
        )
    }
}
