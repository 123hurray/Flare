package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.data.network.xiaohongshu.XhsSigning
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class XhsLoader(
    private val accountKey: MicroBlogKey,
    private val service: XhsService,
) : UserLoader,
    PostLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        statusKey.id.xhsLoaderCommentTarget()?.let { target ->
            val context = XhsNoteContextCache.get(target.noteId)
            val comment =
                context?.let {
                    service
                        .comments(
                            noteId = target.noteId,
                            xsecToken = it.xsecToken,
                            topCommentId = target.commentId,
                        ).data
                        ?.comments
                        .orEmpty()
                        .flatMap { it.flattenWithInlineSubComments() }
                        .firstOrNull { it.loaderCommentIdentity() == target.commentId }
                }
            return comment?.toUiTimeline(accountKey, target.noteId)
                ?: error("Xiaohongshu comment is empty: ${statusKey.id}")
        }
        return loadPost(statusKey)
            ?: error("Xiaohongshu note is empty: ${statusKey.id}")
    }

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = userById(uiHandle.normalizedRaw)

    override suspend fun userById(id: String): UiProfile {
        val profile =
            if (id == accountKey.id) {
                requireNotNull(service.me().data) { "Xiaohongshu profile is empty" }.toUiProfile(accountKey)
            } else {
                requireNotNull(service.userInfo(id).data) { "Xiaohongshu profile is empty" }
                    .toUiProfile(accountKey, requestedUserId = id)
            }
        return profile.withUserPostedCountFallback(id)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val data = service.userInfo(userKey.id, mapLoginExpired = false).data
        val status = data?.basicInfo?.followStatus ?: data?.followStatus
        return UiRelation(
            following = status in xhsFollowingStatuses,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.followUser(userKey.id)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollowUser(userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) {}

    override suspend fun unblock(userKey: MicroBlogKey) {}

    override suspend fun mute(userKey: MicroBlogKey) {}

    override suspend fun unmute(userKey: MicroBlogKey) {}

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        throw UnsupportedOperationException("Xiaohongshu delete is not supported")
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

    private suspend fun UiProfile.withUserPostedCountFallback(userId: String): UiProfile {
        if (matrices.statusesCount > 0L) return this
        val posted = runCatching { service.userPosted(userId).data }.getOrNull() ?: return this
        val count =
            firstPositiveCount(
                posted.total.toXhsCount(),
                posted.totalCount.toXhsCount(),
                posted.noteCount.toXhsCount(),
                posted.notesCount.toXhsCount(),
                posted.notes.size.toLong(),
            )
        if (count <= 0L) return this
        return copy(
            matrices =
                matrices.copy(
                    statusesCount = count,
                ),
        )
    }
}

private data class XhsLoaderCommentTarget(
    val noteId: String,
    val commentId: String,
)

private fun String.xhsLoaderCommentTarget(): XhsLoaderCommentTarget? {
    val marker = ":comment:"
    val index = indexOf(marker)
    if (index <= 0) return null
    val noteId = substring(0, index).takeIf { it.isNotBlank() } ?: return null
    val commentId = substring(index + marker.length).takeIf { it.isNotBlank() } ?: return null
    return XhsLoaderCommentTarget(noteId, commentId)
}

private fun dev.dimension.flare.data.network.xiaohongshu.model.XhsComment.loaderCommentIdentity(): String =
    commentId?.takeIf { it.isNotBlank() } ?: id

private val xhsFollowingStatuses = setOf("follows", "followed", "both", "same")

private fun firstPositiveCount(vararg values: Long): Long =
    values.firstOrNull { it > 0L } ?: 0L

private fun String?.toXhsCount(): Long {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return 0L
    val normalized = raw.replace(",", "")
    if (normalized.endsWith("万+")) {
        return (normalized.removeSuffix("万+").toDoubleOrNull() ?: return 0L).times(10_000).toLong()
    }
    if (normalized.endsWith("万")) {
        return (normalized.removeSuffix("万").toDoubleOrNull() ?: return 0L).times(10_000).toLong()
    }
    return normalized.filter { it.isDigit() }.toLongOrNull() ?: 0L
}
