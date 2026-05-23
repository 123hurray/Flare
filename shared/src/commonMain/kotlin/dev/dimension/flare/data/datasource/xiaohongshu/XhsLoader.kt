package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

internal class XhsLoader(
    private val accountKey: MicroBlogKey,
    private val service: XhsService,
) : UserLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

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
