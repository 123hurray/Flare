package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.ContainerInfo
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val mediaOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("user_timeline")
            if (mediaOnly) {
                append("_mediaOnly")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    private var containerid: String? = null

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (mediaOnly) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }

        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }
        val st = config.data.st

        if (containerid == null) {
            containerid = service.getContainerIndex(type = "uid", value = userKey.id).data?.timelineContainerId(userKey.id)
        }

        var response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                        sinceId = request.nextKey,
                    )
                }
            }

        var statuses =
            response.data
                ?.cards
                .orEmpty()
                .extractStatuses()

        if (request is PagingRequest.Refresh && statuses.isEmpty()) {
            val fallbackContainerId = fallbackTimelineContainerId(userKey.id)
            if (containerid != fallbackContainerId) {
                val fallback =
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = fallbackContainerId,
                    )
                val fallbackStatuses = fallback.data?.cards.orEmpty().extractStatuses()
                if (fallbackStatuses.isNotEmpty()) {
                    containerid = fallbackContainerId
                    response = fallback
                    statuses = fallbackStatuses
                }
            }
        }

        val data =
            statuses
                .orEmpty()
                .map { it.render(accountKey) }

        return PagingResult(
            endOfPaginationReached = response.data?.cardlistInfo?.sinceID == null || data.isEmpty(),
            data = data,
            nextKey =
                response.data
                    ?.cardlistInfo
                    ?.sinceID
                    ?.toString(),
        )
    }
}

private fun ContainerInfo.timelineContainerId(userId: String): String =
    tabsInfo
        ?.tabs
        .orEmpty()
        .firstOrNull {
            it.containerid?.isNotBlank() == true &&
                (
                    it.tabType == vvo ||
                        it.tabKey == vvo ||
                        it.title == "微博" ||
                        it.title == "主页"
                )
        }?.containerid
        ?: cardlistInfo?.containerid?.takeIf { it.isNotBlank() }
        ?: fallbackTimelineContainerId(userId)

private fun fallbackTimelineContainerId(userId: String): String = "107603$userId"

private fun List<dev.dimension.flare.data.network.vvo.model.Card>.extractStatuses(): List<Status> =
    flatMap { card ->
        listOfNotNull(card.mblog) + card.cardGroup.orEmpty().mapNotNull { it.mblog }
    }
