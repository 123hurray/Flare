package dev.dimension.flare.data.datasource.instagram

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class InstagramDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val service by lazy {
        InstagramService(
            accountKey = accountKey,
            cookiesFlow =
                accountRepository
                    .credentialFlow<UiAccount.Instagram.Credential>(accountKey)
                    .map { it.cookies },
        )
    }

    private val loader by lazy {
        InstagramLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        InstagramHomeTimelineRemoteLoader(service, accountKey)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = emptyProfileLoader()

    override fun discoverUsers(): RemoteLoader<UiProfile> = emptyProfileLoader()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = emptyHashtagLoader()

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = emptyProfileLoader()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = emptyProfileLoader()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey, false),
            ),
            ProfileTab.Media,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        throw UnsupportedOperationException("Instagram compose is not supported in v1")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(2200),
            media =
                ComposeConfig.Media(
                    maxCount = 10,
                    canSensitive = false,
                    altTextMaxLength = -1,
                    allowMediaOnly = true,
                ),
        )
}

@OptIn(ExperimentalPagingApi::class)
private class InstagramHomeTimelineRemoteLoader(
    private val service: InstagramService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "instagram_home_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page = service.homeFeed((request as? PagingRequest.Append)?.nextKey)
        val items = page.items.map { it.toUiTimeline(accountKey) }
        return PagingResult(
            endOfPaginationReached = !page.moreAvailable || page.nextMaxId.isNullOrBlank() || items.isEmpty(),
            data = items,
            nextKey = page.nextMaxId,
        )
    }
}

private fun emptyTimelineLoader(): RemoteLoader<UiTimelineV2> =
    object : RemoteLoader<UiTimelineV2> {
        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
    }

private fun emptyProfileLoader(): RemoteLoader<UiProfile> =
    object : RemoteLoader<UiProfile> {
        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiProfile> = PagingResult(endOfPaginationReached = true)
    }

private fun emptyHashtagLoader(): RemoteLoader<UiHashtag> =
    object : RemoteLoader<UiHashtag> {
        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiHashtag> = PagingResult(endOfPaginationReached = true)
    }
