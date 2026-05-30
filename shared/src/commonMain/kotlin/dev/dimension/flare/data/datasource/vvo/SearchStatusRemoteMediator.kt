package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.repository.VVOCaptchaRequiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.home.SearchStatusType

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val query: String,
    private val type: SearchStatusType = SearchStatusType.Comprehensive,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(type.name)
            append(accountKey.toString())
        }

    private val containerId by lazy {
        when (type) {
            SearchStatusType.Comprehensive -> "100103type=1&q=$query"
            SearchStatusType.Realtime -> "100103type=61&q=$query&t="
            SearchStatusType.Video -> "100103type=64&q=$query&t="
            SearchStatusType.Image -> "100103type=63&q=$query&t="
            SearchStatusType.Following -> "100103type=1&q=$query"
        }
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                is PagingRequest.Append -> {
                    request.nextKey.toIntOrNull() ?: 1
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh -> {
                    1
                }
            }

        val response =
            service.getContainerIndex(
                containerId = containerId,
                pageType = "searchall",
                page = page.takeIf { request is PagingRequest.Append },
            )
        response.throwCaptchaIfNeeded(accountKey)

        val status =
            response.data
                ?.cards
                ?.flatMap { card -> listOfNotNull(card.mblog) + card.cardGroup?.mapNotNull { it.mblog }.orEmpty() }
                .orEmpty()

        return PagingResult(
            endOfPaginationReached = status.isEmpty(),
            data = status.map { it.render(accountKey) },
            nextKey = (page + 1).toString(),
        )
    }
}

internal fun dev.dimension.flare.data.network.vvo.model.VVOResponse<*>.throwCaptchaIfNeeded(accountKey: MicroBlogKey) {
    if (ok == -100L && url?.contains("/captcha/", ignoreCase = true) == true) {
        throw VVOCaptchaRequiredException(
            accountKey = accountKey,
            url = url,
        )
    }
}
