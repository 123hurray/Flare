package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.VVOFeedGroup
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class GroupTimelineRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val group: VVOFeedGroup,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "vvo_group_${accountKey}_${group.listId}"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.groupTimeline(
                        group = group,
                        count = pageSize,
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.groupTimeline(
                        group = group,
                        maxId = request.nextKey,
                        count = pageSize,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.nextKey == null,
            data = response.statuses.map { it.render(accountKey) },
            nextKey = response.nextKey,
        )
    }
}
