package dev.dimension.flare.data.datasource.jike

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.EmojiHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.JikeCommentsRequest
import dev.dimension.flare.data.network.jike.model.JikeSearchRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class JikeDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    NotificationDataSource,
    UserDataSource,
    RelationDataSource,
    PostDataSource,
    PostEventHandler.Handler {
    private val accountRepository: AccountRepository by inject()

    private val service by lazy {
        JikeService(
            accountKey = accountKey,
            accessTokenFlow =
                accountRepository
                    .credentialFlow<UiAccount.Jike.Credential>(accountKey)
                    .map { it.accessToken },
            refreshTokenFlow =
                accountRepository
                    .credentialFlow<UiAccount.Jike.Credential>(accountKey)
                    .map { it.refreshToken },
            deviceIdFlow =
                accountRepository
                    .credentialFlow<UiAccount.Jike.Credential>(accountKey)
                    .map { it.deviceId },
            onTokenRefresh = { accessToken, refreshToken ->
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Jike.Credential>(accountKey)
                        .first()
                accountRepository.updateCredential(
                    accountKey,
                    credential.copy(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                    ),
                )
            },
        )
    }

    private val loader by lazy {
        JikeLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    private val emojiHandler by lazy {
        EmojiHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val notificationHandler: NotificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
            fetchBadgeCount = { 0 },
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            dataSource = loader,
            accountType = AccountType.Specific(accountKey),
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = emptySet()

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Jike)
        // TODO: Implement post event handling
    }

    override fun homeTimeline() =
        JikeHomeTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = JikeUserTimelineRemoteMediator(
        userKey = userKey,
        service = service,
        accountKey = accountKey,
    )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }

                val post = service.getPostOrRepost(statusKey.id) ?: error("post not found")
                val comments =
                    service.getPrimaryComments(
                        JikeCommentsRequest(
                            targetId = post.id,
                            targetType = post.type,
                            limit = pageSize,
                            loadMoreKey = (request as? PagingRequest.Append)?.nextKey.decodeJikeLoadMoreKey(),
                        ),
                    )
                val renderedComments = comments.data.orEmpty().map { it.toUiTimeline(accountKey) }
                return PagingResult(
                    endOfPaginationReached = comments.loadMoreKey == null,
                    data =
                        when (request) {
                            is PagingRequest.Append -> renderedComments
                            PagingRequest.Refresh -> listOf(post.toUiTimeline(accountKey, service)) + renderedComments
                            is PagingRequest.Prepend -> emptyList()
                        },
                    nextKey = comments.loadMoreKey.encodeJikeLoadMoreKey(),
                )
            }
        }

    override fun searchStatus(query: String) =
        JikeSearchStatusRemoteMediator(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> {
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
                val users =
                    response.data
                        .orEmpty()
                        .flatMap { it.items }
                        .map { it.toUiProfile(accountKey) }
                return PagingResult(
                    endOfPaginationReached = response.loadMoreKey == null,
                    data = users,
                    nextKey = response.loadMoreKey.encodeJikeLoadMoreKey(),
                )
            }
        }

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override fun discoverStatuses() =
        JikeFeaturedRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        object : RemoteLoader<UiHashtag> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiHashtag> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey, false),
            ),
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        // TODO: Implement compose
        TODO("Jike compose not yet implemented")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(2000),
            media =
                ComposeConfig.Media(
                    maxCount = 9,
                    canSensitive = false,
                    altTextMaxLength = -1,
                    allowMediaOnly = false,
                ),
            emoji =
                ComposeConfig.Emoji(
                    emoji = emojiHandler.emoji,
                    mergeTag = "jike@${accountKey.host}",
                    accountKey = accountKey,
                ),
        )

    /**
     * Get the featured/explore timeline.
     */
    fun featuredTimeline() =
        JikeFeaturedRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    /**
     * Get the user's liked timeline.
     */
    fun likedTimeline() =
        JikeLikedRemoteMediator(
            service = service,
            accountKey = accountKey,
        )
}
