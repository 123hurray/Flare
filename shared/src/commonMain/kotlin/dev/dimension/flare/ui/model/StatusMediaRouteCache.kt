package dev.dimension.flare.ui.model

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

public object StatusMediaRouteCache {
    private const val MaxSize = 256
    private val posts = mutableMapOf<String, UiTimelineV2.Post>()

    public fun put(post: UiTimelineV2.Post) {
        val key = key(post.statusKey, post.accountType)
        posts[key] = post
        if (posts.size > MaxSize) {
            posts.remove(posts.keys.first())
        }
    }

    public fun get(
        statusKey: MicroBlogKey,
        accountType: AccountType,
    ): UiTimelineV2.Post? = posts[key(statusKey, accountType)]

    private fun key(
        statusKey: MicroBlogKey,
        accountType: AccountType,
    ): String = "${accountType}::$statusKey"
}
