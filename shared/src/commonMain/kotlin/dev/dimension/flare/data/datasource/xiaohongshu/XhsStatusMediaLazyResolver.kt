package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

public object XhsStatusMediaLazyResolver {
    private val resolvers = mutableMapOf<String, suspend (MicroBlogKey) -> UiTimelineV2.Post?>()

    public fun register(
        accountType: AccountType,
        resolver: suspend (MicroBlogKey) -> UiTimelineV2.Post?,
    ) {
        resolvers[accountType.toString()] = resolver
    }

    public suspend fun resolve(post: UiTimelineV2.Post): UiTimelineV2.Post? =
        resolvers[post.accountType.toString()]?.invoke(post.statusKey)
}
