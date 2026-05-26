package dev.dimension.flare.data.datasource.zhihu

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
import dev.dimension.flare.data.network.zhihu.ZhihuContentKey
import dev.dimension.flare.data.network.zhihu.ZhihuContentType
import dev.dimension.flare.data.network.zhihu.ZhihuService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.zhihuWebHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class ZhihuDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    PostDataSource,
    UserDataSource,
    PostEventHandler.Handler {
    private val accountRepository: AccountRepository by inject()

    private val service by lazy {
        ZhihuService(
            accountKey = accountKey,
            cookiesFlow =
                accountRepository
                    .credentialFlow<UiAccount.Zhihu.Credential>(accountKey)
                    .map { it.cookies },
        )
    }

    private val loader by lazy {
        ZhihuLoader(
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
            host = zhihuWebHost,
            loader = loader,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) = Unit

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        ZhihuHomeTimelineRemoteLoader(
            accountKey = accountKey,
            service = service,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        statusKey.id.zhihuCommentTarget()?.let { target ->
            ZhihuCommentThreadRemoteLoader(
                accountKey = accountKey,
                service = service,
                target = target,
            )
        } ?: when (ZhihuContentKey.parse(statusKey.id).type) {
            ZhihuContentType.Question ->
                ZhihuQuestionAnswerRemoteLoader(
                    accountKey = accountKey,
                    service = service,
                    questionId = ZhihuContentKey.parse(statusKey.id).id,
                )
            else ->
                ZhihuCommentRemoteLoader(
                    accountKey = accountKey,
                    service = service,
                    statusKey = statusKey,
                )
        }

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

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = notSupported()

    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        throw UnsupportedOperationException("Zhihu compose is not supported")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig = ComposeConfig()
}

private class ZhihuLoader(
    private val accountKey: MicroBlogKey,
    private val service: ZhihuService,
) : PostLoader,
    UserLoader {
    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        statusKey.id.zhihuCommentTarget()?.let { target ->
            service
                .comment(target.parentStatusId, target.commentId)
                ?.toUiTimeline(accountKey, target.parentStatusId)
                ?: error("Zhihu comment is empty: ${statusKey.id}")
        } ?: service
            .contentDetail(statusKey.id)
            .toUiTimeline(accountKey, detail = true)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = userById(uiHandle.normalizedRaw)

    override suspend fun userById(id: String): UiProfile =
        service
            .me()
            .toUiProfile(accountKey, requestedId = id)

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        throw UnsupportedOperationException("Zhihu delete is not supported")
    }
}

private class ZhihuHomeTimelineRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: ZhihuService,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page = service.recommend((request as? PagingRequest.Append)?.nextKey)
        return PagingResult(
            data = page.items.map { it.toUiTimeline(accountKey, detail = false) },
            nextKey = page.nextUrl,
            endOfPaginationReached = page.isEnd || page.nextUrl.isNullOrBlank(),
        )
    }
}

private class ZhihuQuestionAnswerRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: ZhihuService,
    private val questionId: String,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page = service.questionAnswers(questionId, (request as? PagingRequest.Append)?.nextKey)
        val answers = page.items.map { it.toUiTimeline(accountKey, detail = false, includeTitle = false) }
        return PagingResult(
            data =
                when (request) {
                    is PagingRequest.Append -> answers
                    PagingRequest.Refresh ->
                        listOf((page.header ?: service.questionHeader(questionId)).toUiTimeline(accountKey, detail = true)) +
                            answers
                    is PagingRequest.Prepend -> emptyList()
                },
            nextKey = page.nextUrl,
            endOfPaginationReached = page.isEnd || page.nextUrl.isNullOrBlank(),
        )
    }
}

private class ZhihuCommentRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: ZhihuService,
    private val statusKey: MicroBlogKey,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request !is PagingRequest.Refresh) {
            if (request is PagingRequest.Append) {
                val page = service.comments(statusKey.id, request.nextKey)
                return PagingResult(
                    data =
                        page.comments
                            .flatMap { it.flattenWithInlineChildComments() }
                            .map { it.toUiTimeline(accountKey, statusKey.id) },
                    nextKey = page.nextUrl,
                    endOfPaginationReached = page.isEnd || page.nextUrl.isNullOrBlank(),
                )
            }
            return PagingResult(endOfPaginationReached = true)
        }
        val comments = service.comments(statusKey.id)
        return PagingResult(
            data =
                listOf(service.contentDetail(statusKey.id).toUiTimeline(accountKey, detail = true)) +
                    comments.comments
                        .flatMap { it.flattenWithInlineChildComments() }
                        .map { it.toUiTimeline(accountKey, statusKey.id) },
            nextKey = comments.nextUrl,
            endOfPaginationReached = comments.isEnd || comments.nextUrl.isNullOrBlank(),
        )
    }
}

private class ZhihuCommentThreadRemoteLoader(
    private val accountKey: MicroBlogKey,
    private val service: ZhihuService,
    private val target: ZhihuCommentTarget,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        if (request is PagingRequest.Append) {
            val page = service.childComments(target.commentId, request.nextKey)
            return PagingResult(
                data =
                    page.comments
                        .flatMap { it.flattenWithInlineChildComments() }
                        .map { it.toUiTimeline(accountKey, target.parentStatusId) },
                nextKey = page.nextUrl,
                endOfPaginationReached = page.isEnd || page.nextUrl.isNullOrBlank(),
            )
        }
        val rootComment = service.comment(target.parentStatusId, target.commentId)
        val replies = service.childComments(target.commentId)
        return PagingResult(
            data =
                listOfNotNull(rootComment?.toUiTimeline(accountKey, target.parentStatusId)) +
                    replies.comments
                        .flatMap { it.flattenWithInlineChildComments() }
                        .map { it.toUiTimeline(accountKey, target.parentStatusId) },
            nextKey = replies.nextUrl,
            endOfPaginationReached = replies.isEnd || replies.nextUrl.isNullOrBlank(),
        )
    }
}

private data class ZhihuCommentTarget(
    val parentStatusId: String,
    val commentId: String,
)

private fun String.zhihuCommentTarget(): ZhihuCommentTarget? {
    val parts = split(':')
    if (parts.size != 4 || parts[0] != "comment") return null
    val parentType = parts[1].takeIf { it.isNotBlank() } ?: return null
    val parentId = parts[2].takeIf { it.isNotBlank() } ?: return null
    val commentId = parts[3].takeIf { it.isNotBlank() } ?: return null
    return ZhihuCommentTarget("$parentType:$parentId", commentId)
}
