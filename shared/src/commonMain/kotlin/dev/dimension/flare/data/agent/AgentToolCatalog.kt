package dev.dimension.flare.data.agent

public data class AgentToolOption(
    val name: String,
    val label: String,
    val description: String,
)

public object AgentToolCatalog {
    public const val FEED_SNAPSHOT: String = "get_current_feed_snapshot"
    public const val HOME_TIMELINE: String = "load_home_timeline"
    public const val USER_PROFILE_TIMELINE: String = "load_user_profile_statuses"
    public const val DISCOVER_STATUSES: String = "load_discover_statuses"
    public const val DISCOVER_USERS: String = "load_discover_users"
    public const val DISCOVER_HASHTAGS: String = "load_discover_hashtags"
    public const val SEARCH_STATUSES: String = "search_statuses"
    public const val SEARCH_USERS: String = "search_users"
    public const val SEARCH_USER_PROFILE_STATUSES: String = "search_user_profile_statuses"
    public const val STATUS_DETAIL: String = "get_status_detail"
    public const val STATUS_COMMENTS: String = "get_status_comments"
    public const val AGGREGATE_SUBJECTS: String = "aggregate_subjects"

    public val options: List<AgentToolOption> =
        listOf(
            AgentToolOption(FEED_SNAPSHOT, "当前快照", "读取进入 Agent 时捕获的当前 feed 内容。"),
            AgentToolOption(HOME_TIMELINE, "首页 Feed", "读取各平台账号首页时间线或推荐流。"),
            AgentToolOption(USER_PROFILE_TIMELINE, "用户主页", "读取指定用户主页动态。"),
            AgentToolOption(DISCOVER_STATUSES, "发现帖子", "读取平台发现页/热门帖子流。"),
            AgentToolOption(DISCOVER_USERS, "发现用户", "读取平台发现页推荐用户。"),
            AgentToolOption(DISCOVER_HASHTAGS, "发现话题", "读取平台发现页话题/热搜。"),
            AgentToolOption(SEARCH_STATUSES, "帖子搜索", "按关键词搜索帖子，微博支持综合、实时、视频、图片。"),
            AgentToolOption(SEARCH_USERS, "用户搜索", "按关键词搜索用户、球员、球队或账号。"),
            AgentToolOption(SEARCH_USER_PROFILE_STATUSES, "主页内搜索", "在指定用户主页动态中搜索关键词。"),
            AgentToolOption(STATUS_DETAIL, "帖子详情", "读取单条帖子详情。"),
            AgentToolOption(STATUS_COMMENTS, "帖子评论", "读取帖子评论或评论线程，支持指定页数。"),
            AgentToolOption(AGGREGATE_SUBJECTS, "主题聚合", "对工具返回的帖子按来源、作者、链接或主题分组。"),
        )

    public fun enabledNames(allowedTools: List<String>): Set<String> {
        val known = options.map { it.name }.toSet()
        val normalized = allowedTools.filter { it in known }.toSet()
        return normalized.ifEmpty { known }
    }

    public fun isEnabled(
        name: String,
        allowedTools: List<String>,
    ): Boolean = name in enabledNames(allowedTools)
}
