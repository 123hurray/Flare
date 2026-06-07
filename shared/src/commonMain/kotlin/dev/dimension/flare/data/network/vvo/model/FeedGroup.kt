package dev.dimension.flare.data.network.vvo.model

internal data class VVOFeedGroup(
    val gid: String,
    val listId: String,
    val title: String,
)

internal data class VVOGroupTimelinePage(
    val statuses: List<Status>,
    val nextKey: String?,
)
