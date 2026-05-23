package dev.dimension.flare.data.datasource.xiaohongshu

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.data.network.xiaohongshu.model.XhsHomeFeedRequest
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class XhsHomeTimelineRemoteMediator(
    private val service: XhsService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "xiaohongshu_home_v2_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val response =
            service.homeFeed(
                XhsHomeFeedRequest(
                    cursorScore = (request as? PagingRequest.Append)?.nextKey.orEmpty(),
                    refreshType = if (request is PagingRequest.Append) 3 else 1,
                ),
        )
        val data = response.data
        val rawItems = data?.items.orEmpty()
        val items = rawItems.mapNotNull { it.toUiTimeline(accountKey) }
        val requestName =
            when (request) {
                is PagingRequest.Append -> "Append"
                is PagingRequest.Prepend -> "Prepend"
                PagingRequest.Refresh -> "Refresh"
            }
        val line =
            "XhsHomeTimeline: request=$requestName raw=${rawItems.size} " +
                "mapped=${items.size} cursor=${data?.cursorScore?.isNotBlank() == true}"
        println(line)
        DebugRepository.log(line)
        return PagingResult(
            endOfPaginationReached = data?.cursorScore.isNullOrBlank() || items.isEmpty(),
            data = items,
            nextKey = data?.cursorScore,
        )
    }
}
