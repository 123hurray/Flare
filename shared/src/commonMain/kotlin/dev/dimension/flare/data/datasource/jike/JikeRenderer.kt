package dev.dimension.flare.data.datasource.jike

import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.JikePost
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

internal suspend fun JikePost.toUiTimeline(
    accountKey: MicroBlogKey,
    service: JikeService,
): UiTimelineV2.Post {
    val targetPost =
        target
            ?.takeIf { targetType == "ORIGINAL_POST" || targetType == "REPOST" }
            ?.toUiTimeline(accountKey, service)
    return toUiTimeline(
        accountKey = accountKey,
        videoUrl = video?.let { service.videoUrl(id, type) },
        targetPost = targetPost,
    )
}

internal suspend fun List<JikePost>.toUiTimeline(
    accountKey: MicroBlogKey,
    service: JikeService,
): List<UiTimelineV2.Post> =
    filter { it.type == "ORIGINAL_POST" || it.type == "REPOST" }
        .map { it.toUiTimeline(accountKey, service) }

internal suspend fun JikeService.getPostOrRepost(id: String): JikePost? {
    val post = runCatching { getPost(id).data }.getOrNull()
    if (post != null) {
        return post
    }
    return runCatching { getRepost(id).data }.getOrNull()
}

private suspend fun JikeService.videoUrl(
    id: String,
    type: String,
): String? =
    runCatching {
        getMediaMeta(id = id, type = type).url
    }.getOrNull()
