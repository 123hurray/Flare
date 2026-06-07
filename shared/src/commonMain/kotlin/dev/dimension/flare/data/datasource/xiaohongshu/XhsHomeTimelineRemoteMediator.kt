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
    override val replaceCacheOnRefresh: Boolean = true

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val requestName =
            when (request) {
                is PagingRequest.Append -> "Append"
                is PagingRequest.Prepend -> "Prepend"
                PagingRequest.Refresh -> "Refresh"
            }
        val response =
            try {
                service.homeFeed(
                    XhsHomeFeedRequest(
                        cursorScore = (request as? PagingRequest.Append)?.nextKey.orEmpty(),
                        refreshType = if (request is PagingRequest.Append) 3 else 1,
                    ),
                )
            } catch (throwable: Throwable) {
                logDiagnostic(
                    "XhsHomeTimeline: request=$requestName failed " +
                        "type=${throwable::class.simpleName ?: throwable::class.qualifiedName} " +
                        "message=${throwable.message.orEmpty()}",
                )
                throw throwable
            }
        if (response.success != true || response.code != 0) {
            logDiagnostic(
                "XhsHomeTimeline: request=$requestName response success=${response.success} " +
                    "code=${response.code} msg=${response.msg.orEmpty()} data=${response.data != null}",
            )
        }
        val data = response.data
        val rawItems = data?.items.orEmpty()
        val items =
            rawItems.mapIndexedNotNull { index, item ->
                item.toUiTimeline(accountKey).also { mapped ->
                    if (mapped == null) {
                        logDiagnostic(
                            "XhsHomeTimeline: mapDrop index=$index id=${item.id} " +
                                "model=${item.modelType.orEmpty()} hasCard=${item.noteCard != null} " +
                                "cardNoteId=${item.noteCard?.noteId.orEmpty()} " +
                                "cardType=${item.noteCard?.type.orEmpty()} " +
                                "hasUser=${item.noteCard?.user != null} " +
                                "titleLen=${item.noteCard?.displayTitle?.length ?: item.noteCard?.title?.length ?: 0} " +
                                "images=${item.noteCard?.imageList?.size ?: 0} " +
                                "hasVideo=${item.noteCard?.video != null} " +
                                "hasXsec=${!item.xsecToken.isNullOrBlank() || !item.noteCard?.xsecToken.isNullOrBlank()}",
                        )
                    }
                }
            }
        if (rawItems.isEmpty()) {
            logDiagnostic(
                "XhsHomeTimeline: request=$requestName emptyRaw success=${response.success} " +
                    "code=${response.code} msg=${response.msg.orEmpty()} " +
                    "hasMore=${data?.hasMore} cursor=${data?.cursorScore?.isNotBlank() == true}",
            )
        } else if (items.isEmpty()) {
            logDiagnostic(
                "XhsHomeTimeline: request=$requestName allItemsDropped raw=${rawItems.size} " +
                    "success=${response.success} code=${response.code} msg=${response.msg.orEmpty()}",
            )
        }
        val line =
            "XhsHomeTimeline: request=$requestName raw=${rawItems.size} " +
                "mapped=${items.size} hasMore=${data?.hasMore} cursor=${data?.cursorScore?.isNotBlank() == true}"
        logDiagnostic(line)
        return PagingResult(
            endOfPaginationReached = data?.cursorScore.isNullOrBlank() || items.isEmpty(),
            data = items,
            nextKey = data?.cursorScore,
        )
    }

    private fun logDiagnostic(line: String) {
        println(line)
        DebugRepository.log(line)
    }
}
