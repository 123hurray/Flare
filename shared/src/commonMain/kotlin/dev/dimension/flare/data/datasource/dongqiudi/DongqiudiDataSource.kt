package dev.dimension.flare.data.datasource.dongqiudi

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.dongqiudi.DongqiudiArticle
import dev.dimension.flare.data.network.dongqiudi.DongqiudiService
import dev.dimension.flare.data.network.dongqiudi.DongqiudiUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.dongqiudiWebHost
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal class DongqiudiDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    PostDataSource,
    UserDataSource,
    PostEventHandler.Handler {
    private val service by lazy { DongqiudiService() }

    private val loader by lazy {
        DongqiudiLoader(
            accountKey = accountKey,
            service = service,
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

    override val userHandler by lazy {
        UserHandler(
            host = dongqiudiWebHost,
            loader = loader,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) = Unit

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        DongqiudiHomeTimelineRemoteLoader(
            accountKey = accountKey,
            service = service,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        DongqiudiContextRemoteLoader(
            accountKey = accountKey,
            service = service,
            statusKey = statusKey,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = homeTimeline()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey),
            ),
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = notSupported()

    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        throw UnsupportedOperationException("Dongqiudi compose is not supported")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig = ComposeConfig()
}

private class DongqiudiLoader(
    private val accountKey: MicroBlogKey,
    private val service: DongqiudiService,
) : PostLoader,
    UserLoader {
    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        service
            .articleDetail(statusKey.id)
            .withResolvedAuthor(service)
            .toUiTimeline(accountKey, detail = true)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = userById(uiHandle.normalizedRaw)

    override suspend fun userById(id: String): UiProfile {
        if (id == "anonymous" || id == "dongqiudi") {
            return DongqiudiUser(
                id = id,
                name = "懂球帝",
                avatar = "",
                description = "",
                fansCount = 0L,
                followsCount = 0L,
                statusesCount = 0L,
            ).toUiProfile(accountKey, requestedId = id)
        }
        return service.userProfile(id).toUiProfile(accountKey, requestedId = id)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        throw UnsupportedOperationException("Dongqiudi delete is not supported")
    }
}

private class DongqiudiHomeTimelineRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: DongqiudiService,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page = service.homeFeed((request as? PagingRequest.Append)?.nextKey)
        val items =
            page.items
                .filter { it.id.isNotBlank() }
                .map { it.withResolvedAuthor(service) }
                .map { it.toUiTimeline(accountKey, detail = false) }
        return PagingResult(
            data = items,
            nextKey = page.nextUrl,
            endOfPaginationReached = page.nextUrl.isNullOrBlank() || items.isEmpty(),
        )
    }
}

private class DongqiudiContextRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: DongqiudiService,
    private val statusKey: MicroBlogKey,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request !is PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val detail =
            service
                .articleDetail(statusKey.id)
                .withResolvedAuthor(service)
                .toUiTimeline(accountKey, detail = true)
        val comments = service.hotComments(statusKey.id).map { it.toUiTimeline(accountKey) }
        return PagingResult(
            data = listOf(detail) + comments,
            endOfPaginationReached = true,
        )
    }
}

private suspend fun DongqiudiArticle.withResolvedAuthor(service: DongqiudiService): DongqiudiArticle {
    val resolvedUserId = userId.takeIf { it.isNotBlank() && it != "dongqiudi" } ?: return this
    if (authorAvatar.isNotBlank()) return this
    val profile =
        runCatching {
            service.userProfile(resolvedUserId)
        }.getOrElse {
            println("DongqiudiDataSource: user profile unavailable userId=$resolvedUserId message=${it.message}")
            return this
        }
    return copy(
        writer = if (writer.isBlank() || writer == "懂球帝") profile.name.ifBlank { writer } else writer,
        userId = profile.id.ifBlank { userId },
        authorAvatar = profile.avatar,
    )
}
