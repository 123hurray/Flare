package dev.dimension.flare.data.datasource.instagram

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.ResponseCookieUpdate
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiDateTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

internal class InstagramDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val service by lazy {
        InstagramService(
            accountKey = accountKey,
            cookiesFlow =
                accountRepository
                    .credentialFlow<UiAccount.Instagram.Credential>(accountKey)
                    .map { it.cookies },
            onCookiesUpdated = ::updateCredentialCookies,
        )
    }

    private suspend fun updateCredentialCookies(update: ResponseCookieUpdate) {
        val credential = accountRepository.credentialFlow<UiAccount.Instagram.Credential>(accountKey).first()
        val nextCookies =
            (credential.cookies - update.removed + update.updated)
                .filterValues { it.isNotBlank() }
        if (nextCookies == credential.cookies) {
            return
        }
        accountRepository.updateCredential(
            accountKey = accountKey,
            credential =
                credential.copy(
                    cookies = nextCookies,
                    savedAt = Clock.System.now().toEpochMilliseconds(),
                ),
        )
    }

    private val profileResolver by lazy {
        InstagramProfileResolver(
            accountKey = accountKey,
            service = service,
        )
    }

    private val loader by lazy {
        InstagramLoader(
            accountKey = accountKey,
            service = service,
            profileResolver = profileResolver,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) = Unit

    override val supportedRelationTypes: Set<RelationActionType>
        get() = loader.supportedTypes

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        followingTimeline()

    fun followingTimeline(): RemoteLoader<UiTimelineV2> =
        InstagramHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            type = InstagramHomeTimelineType.Following,
        )

    fun recommendedTimeline(): RemoteLoader<UiTimelineV2> =
        InstagramHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            type = InstagramHomeTimelineType.Recommended,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        InstagramUserTimelineRemoteLoader(
            service = service,
            profileResolver = profileResolver,
            accountKey = accountKey,
            userKey = userKey,
            mediaOnly = mediaOnly,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        InstagramStatusContextRemoteLoader(
            loader = loader,
            statusKey = statusKey,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        InstagramSearchStatusRemoteLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        InstagramSearchUserRemoteLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

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
    private val type: InstagramHomeTimelineType,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        when (type) {
            InstagramHomeTimelineType.Following -> "instagram_following_web_$accountKey"
            InstagramHomeTimelineType.Recommended -> "instagram_recommended_$accountKey"
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        println(
            "InstagramHomeTimelineRemoteLoader: start type=$type request=${request::class.simpleName} " +
                "pageSize=$pageSize pagingKey=$pagingKey",
        )
        if (request is PagingRequest.Prepend) {
            println("InstagramHomeTimelineRemoteLoader: skip prepend type=$type")
            return PagingResult(endOfPaginationReached = true)
        }
        val page = try {
            when (type) {
                InstagramHomeTimelineType.Following -> service.followingFeed((request as? PagingRequest.Append)?.nextKey)
                InstagramHomeTimelineType.Recommended -> service.recommendedFeed((request as? PagingRequest.Append)?.nextKey)
            }
        } catch (e: Throwable) {
            println(
                "InstagramHomeTimelineRemoteLoader: error type=$type request=${request::class.simpleName} " +
                    "message=${e.message}",
            )
            throw e
        }
        val items = page.items.map { it.toUiTimeline(accountKey) }
        println(
            "InstagramHomeTimelineRemoteLoader: success type=$type request=${request::class.simpleName} " +
                "items=${items.size} more=${page.moreAvailable} next=${page.nextMaxId.orEmpty()} " +
                "first=${items.firstOrNull()?.statusKey?.id.orEmpty()}",
        )
        return PagingResult(
            endOfPaginationReached = !page.moreAvailable || page.nextMaxId.isNullOrBlank() || items.isEmpty(),
            data = items,
            nextKey = page.nextMaxId,
        )
    }
}

private enum class InstagramHomeTimelineType {
    Following,
    Recommended,
}

@OptIn(ExperimentalPagingApi::class)
private class InstagramUserTimelineRemoteLoader(
    private val service: InstagramService,
    private val profileResolver: InstagramProfileResolver,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val mediaOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "instagram_user_${userKey}_media_$mediaOnly"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page = service.userFeed(userKey.id, (request as? PagingRequest.Append)?.nextKey)
        val profile = profileResolver.userById(userKey.id)
        val items = page.items.map { it.toUiTimeline(accountKey, userOverride = profile) }
        return PagingResult(
            endOfPaginationReached = !page.moreAvailable || page.nextMaxId.isNullOrBlank() || items.isEmpty(),
            data = items,
            nextKey = page.nextMaxId,
        )
    }
}

private class InstagramStatusContextRemoteLoader(
    private val loader: InstagramLoader,
    private val statusKey: MicroBlogKey,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request !is PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        return PagingResult(
            endOfPaginationReached = true,
            data = listOf(loader.status(statusKey)),
        )
    }
}

private class InstagramSearchUserRemoteLoader(
    private val service: InstagramService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request !is PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        return PagingResult(
            endOfPaginationReached = true,
            data = service.searchUsers(query).take(pageSize).map { it.toUiProfile(accountKey) },
        )
    }
}

private class InstagramSearchStatusRemoteLoader(
    private val service: InstagramService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request !is PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val accountType = AccountType.Specific(accountKey)
        val createdAt = UiDateTime(Clock.System.now())
        return PagingResult(
            endOfPaginationReached = true,
            data =
                service.searchUsers(query).take(pageSize).map { user ->
                    val profile = user.toUiProfile(accountKey)
                    UiTimelineV2.User(
                        value = profile,
                        createdAt = createdAt,
                        statusKey = MicroBlogKey(profile.key.id, instagramWebHost),
                        accountType = accountType,
                    )
                },
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
