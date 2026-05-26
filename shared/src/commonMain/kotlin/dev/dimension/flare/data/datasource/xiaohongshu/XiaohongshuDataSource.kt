package dev.dimension.flare.data.datasource.xiaohongshu

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
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.data.network.xiaohongshu.XhsSigning
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
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

internal class XiaohongshuDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    PostDataSource,
    UserDataSource,
    RelationDataSource,
    PostEventHandler.Handler,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val specificAccountType = AccountType.Specific(accountKey)

    init {
        XhsStatusMediaLazyResolver.register(specificAccountType) { statusKey ->
            loadPost(statusKey)
        }
    }

    private val service by lazy {
        XhsService(
            accountKey = accountKey,
            cookiesFlow =
                accountRepository
                    .credentialFlow<UiAccount.Xiaohongshu.Credential>(accountKey)
                    .map { it.cookies },
        )
    }

    private val loader by lazy {
        XhsLoader(
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

    override val postHandler by lazy {
        PostHandler(
            accountType = specificAccountType,
            loader = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = specificAccountType,
            handler = this,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val supportedRelationTypes: Set<RelationActionType>
        get() = loader.supportedTypes

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) = Unit

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        XhsHomeTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                statusKey.id.xhsCommentTarget()?.let { target ->
                    return loadCommentContext(
                        noteId = target.noteId,
                        rootCommentId = target.commentId,
                        pageSize = pageSize,
                        request = request,
                    )
                }
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val cached = XhsNoteContextCache.get(statusKey.id)
                if (request is PagingRequest.Append) {
                    val comments =
                        cached?.let {
                            runCatching {
                                service.comments(
                                    noteId = statusKey.id,
                                    xsecToken = it.xsecToken,
                                    cursor = request.nextKey,
                                )
                            }.getOrNull()
                        }?.data
                    return PagingResult(
                        endOfPaginationReached = comments?.hasMore != true || comments.cursor.isNullOrBlank(),
                        data =
                        comments
                            ?.comments
                            .orEmpty()
                            .map { it.toUiTimeline(accountKey, statusKey.id) },
                        nextKey = comments?.cursor,
                    )
                }
                val post = loadPost(statusKey)
                val commentContext = XhsNoteContextCache.get(statusKey.id) ?: cached
                val comments =
                    commentContext?.let {
                        runCatching {
                            service.comments(
                                noteId = statusKey.id,
                                xsecToken = it.xsecToken,
                            )
                        }.getOrNull()
                    }?.data
                val renderedComments =
                    comments
                        ?.comments
                        .orEmpty()
                        .map { it.toUiTimeline(accountKey, statusKey.id) }
                return PagingResult(
                    endOfPaginationReached = comments?.hasMore != true || comments.cursor.isNullOrBlank(),
                    data = listOfNotNull(post) + renderedComments,
                    nextKey = comments?.cursor,
                )
            }
        }

    private suspend fun loadCommentContext(
        noteId: String,
        rootCommentId: String,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val commentContext = XhsNoteContextCache.get(noteId)
            ?: return PagingResult(endOfPaginationReached = true)
        if (request is PagingRequest.Append) {
            val page =
                service
                    .subComments(
                        noteId = noteId,
                        rootCommentId = rootCommentId,
                        xsecToken = commentContext.xsecToken,
                        cursor = request.nextKey,
                        num = pageSize.coerceIn(1, 30),
                    ).data
            return PagingResult(
                endOfPaginationReached = page?.hasMore != true || page.cursor.isNullOrBlank(),
                data =
                    page
                        ?.comments
                        .orEmpty()
                        .map { it.toUiTimeline(accountKey, noteId, includeInlineSubComments = false) },
                nextKey = page?.cursor,
            )
        }
        val rootComment =
            runCatching {
                service
                    .comments(
                        noteId = noteId,
                        xsecToken = commentContext.xsecToken,
                        topCommentId = rootCommentId,
                    ).data
                    ?.comments
                    .orEmpty()
                    .firstOrNull { it.commentIdentity() == rootCommentId }
            }.getOrNull()
        val replies =
            service
                .subComments(
                    noteId = noteId,
                    rootCommentId = rootCommentId,
                    xsecToken = commentContext.xsecToken,
                    num = pageSize.coerceIn(1, 30),
                ).data
        return PagingResult(
            endOfPaginationReached = replies?.hasMore != true || replies.cursor.isNullOrBlank(),
            data =
                listOfNotNull(rootComment?.toUiTimeline(accountKey, noteId, includeInlineSubComments = false)) +
                    replies
                        ?.comments
                        .orEmpty()
                        .map { it.toUiTimeline(accountKey, noteId, includeInlineSubComments = false) },
            nextKey = replies?.cursor,
        )
    }

    private suspend fun loadPost(statusKey: MicroBlogKey): UiTimelineV2.Post? {
        val cached = XhsNoteContextCache.get(statusKey.id)
        return if (cached != null && XhsSigning.IS_MAIN_API_SIGNING_VERIFIED) {
            service
                .feed(
                    XhsFeedRequest(
                        sourceNoteId = statusKey.id,
                        xsecSource = cached.xsecSource,
                        xsecToken = cached.xsecToken,
                    ),
                ).data
                ?.items
                .orEmpty()
                .firstOrNull()
                ?.toUiTimeline(accountKey)
        } else {
            XhsHtmlParser
                .parseNote(statusKey.id, service.noteHtml(statusKey.id))
                ?.toUiTimeline(accountKey, statusKey.id)
        }
    }

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val data =
                    service
                        .userPosted(
                            userId = userKey.id,
                            cursor = (request as? PagingRequest.Append)?.nextKey.orEmpty(),
                        ).data
                val posts =
                    data
                        ?.notes
                        .orEmpty()
                        .mapNotNull { note ->
                            val noteId = note.noteId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            note.toUiTimeline(accountKey, noteId)
                        }.let { posts ->
                            if (mediaOnly) posts.filter { it.images.isNotEmpty() } else posts
                        }
                return PagingResult(
                    endOfPaginationReached = data?.hasMore != true || data.cursor.isNullOrBlank(),
                    data = posts,
                    nextKey = data?.cursor,
                )
            }
        }

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 1
                val data =
                    service
                        .searchNotes(
                            keyword = query,
                            page = page,
                            pageSize = pageSize.coerceIn(1, 20),
                        ).data
                val posts =
                    data
                        ?.items
                        .orEmpty()
                        .mapNotNull { item ->
                            item
                                .copy(xsecSource = item.xsecSource ?: "pc_search")
                                .toUiTimeline(accountKey)
                        }
                return PagingResult(
                    endOfPaginationReached = data?.hasMore != true || posts.isEmpty(),
                    data = posts,
                    nextKey = (page + 1).toString(),
                )
            }
        }

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> {
                if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val users =
                    service
                        .searchUsers(
                            keyword = query,
                            pageSize = pageSize.coerceIn(1, 20),
                        ).map { it.toUiProfile(accountKey) }
                return PagingResult(
                    endOfPaginationReached = true,
                    data = users,
                )
            }
        }

    override fun discoverUsers(): RemoteLoader<UiProfile> = emptyProfileLoader()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = emptyTimelineLoader()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        object : RemoteLoader<UiHashtag> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiHashtag> = PagingResult(endOfPaginationReached = true)
        }

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
        throw UnsupportedOperationException("Xiaohongshu compose is not supported in v1")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(1000),
            media =
                ComposeConfig.Media(
                    maxCount = 9,
                    canSensitive = false,
                    altTextMaxLength = -1,
                    allowMediaOnly = false,
                ),
        )
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

private data class XhsCommentTarget(
    val noteId: String,
    val commentId: String,
)

private fun String.xhsCommentTarget(): XhsCommentTarget? {
    val marker = ":comment:"
    val index = indexOf(marker)
    if (index <= 0) return null
    val noteId = substring(0, index).takeIf { it.isNotBlank() } ?: return null
    val commentId = substring(index + marker.length).takeIf { it.isNotBlank() } ?: return null
    return XhsCommentTarget(noteId, commentId)
}

private fun dev.dimension.flare.data.network.xiaohongshu.model.XhsComment.commentIdentity(): String =
    commentId?.takeIf { it.isNotBlank() } ?: id
