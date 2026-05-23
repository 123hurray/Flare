package dev.dimension.flare.data.network.xiaohongshu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
internal data class XhsHomeFeedRequest(
    @SerialName("cursor_score")
    val cursorScore: String = "",
    val num: Int = 39,
    @SerialName("refresh_type")
    val refreshType: Int = 1,
    @SerialName("note_index")
    val noteIndex: Int = 0,
    @SerialName("unread_begin_note_id")
    val unreadBeginNoteId: String = "",
    @SerialName("unread_end_note_id")
    val unreadEndNoteId: String = "",
    @SerialName("unread_note_count")
    val unreadNoteCount: Int = 0,
    val category: String = "homefeed_recommend",
    @SerialName("search_key")
    val searchKey: String = "",
    @SerialName("need_num")
    val needNum: Int = 40,
    @SerialName("image_scenes")
    val imageScenes: List<String> = listOf("FD_PRV_WEBP", "FD_WM_WEBP"),
)

@Serializable
internal data class XhsFeedRequest(
    @SerialName("source_note_id")
    val sourceNoteId: String,
    @SerialName("image_formats")
    val imageFormats: List<String> = listOf("jpg", "webp", "avif"),
    val extra: Map<String, String> = mapOf("need_body_topic" to "1"),
    @SerialName("xsec_source")
    val xsecSource: String,
    @SerialName("xsec_token")
    val xsecToken: String,
)

@Serializable
internal data class XhsSearchOneboxRequest(
    val keyword: String,
    @SerialName("search_id")
    val searchId: String,
    @SerialName("biz_type")
    val bizType: String = "web_search_user",
    @SerialName("request_id")
    val requestId: String,
)

@Serializable
internal data class XhsCreatorSearchUsersRequest(
    val keyword: String,
    @SerialName("search_id")
    val searchId: String,
    val page: XhsCreatorSearchPage,
)

@Serializable
internal data class XhsCreatorSearchPage(
    @SerialName("page_size")
    val pageSize: Int = 20,
    val page: Int = 1,
)

@Serializable
internal data class XhsSearchNotesRequest(
    val keyword: String,
    val page: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 20,
    @SerialName("search_id")
    val searchId: String,
    val sort: String = "general",
    @SerialName("note_type")
    val noteType: Int = 0,
    @SerialName("ext_flags")
    val extFlags: List<String> = emptyList(),
    val filters: List<XhsSearchFilter> = XHS_SEARCH_DEFAULT_FILTERS,
    val geo: String = "",
    @SerialName("image_formats")
    val imageFormats: List<String> = listOf("jpg", "webp", "avif"),
)

@Serializable
internal data class XhsSearchFilter(
    val tags: List<String>,
    val type: String,
)

internal val XHS_SEARCH_DEFAULT_FILTERS =
    listOf(
        XhsSearchFilter(tags = listOf("general"), type = "sort_type"),
        XhsSearchFilter(tags = listOf("不限"), type = "filter_note_type"),
        XhsSearchFilter(tags = listOf("不限"), type = "filter_note_time"),
        XhsSearchFilter(tags = listOf("不限"), type = "filter_note_range"),
        XhsSearchFilter(tags = listOf("不限"), type = "filter_pos_distance"),
    )

@Serializable
internal data class XhsUserMeResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsUserMe? = null,
)

@Serializable
internal data class XhsUserMe(
    @SerialName("user_id")
    val userId: String = "",
    val nickname: String = "",
    val images: String? = null,
    val image: String? = null,
)

@Serializable
internal data class XhsHomeFeedResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsHomeFeedData? = null,
)

@Serializable
internal data class XhsHomeFeedData(
    val items: List<XhsFeedItem> = emptyList(),
    @SerialName("cursor_score")
    val cursorScore: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
internal data class XhsFeedResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsFeedData? = null,
)

@Serializable
internal data class XhsFeedData(
    val items: List<XhsFeedItem> = emptyList(),
)

@Serializable
internal data class XhsFeedItem(
    val id: String = "",
    @SerialName("xsec_token")
    val xsecToken: String? = null,
    @SerialName("xsec_source")
    val xsecSource: String? = null,
    @SerialName("note_card")
    val noteCard: XhsNoteCard? = null,
    @SerialName("model_type")
    val modelType: String? = null,
)

@Serializable
internal data class XhsNoteCard(
    @SerialName("note_id")
    val noteId: String? = null,
    @SerialName("xsec_token")
    val xsecToken: String? = null,
    @SerialName("xsec_source")
    val xsecSource: String? = null,
    val type: String? = null,
    @SerialName("display_title")
    val displayTitle: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val user: XhsUser? = null,
    @SerialName("interact_info")
    val interactInfo: XhsInteractInfo? = null,
    @SerialName("image_list")
    val imageList: List<XhsImage> = emptyList(),
    val cover: XhsImage? = null,
    val video: XhsVideo? = null,
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val time: Long = 0L,
    @SerialName("create_time")
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val createTime: Long = 0L,
    @SerialName("last_update_time")
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val lastUpdateTime: Long = 0L,
    @SerialName("timestamp")
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val timestamp: Long = 0L,
    @SerialName("update_time")
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val updateTime: Long = 0L,
)

@Serializable
internal data class XhsUser(
    @SerialName("user_id")
    val userId: String = "",
    val nickname: String = "",
    @SerialName("nick_name")
    val nickName: String = "",
    val avatar: String? = null,
    val image: String? = null,
    val images: String? = null,
    @SerialName("red_id")
    val redId: String? = null,
    val desc: String? = null,
    @SerialName("ip_location")
    val ipLocation: String? = null,
)

@Serializable
internal data class XhsSearchUser(
    @SerialName("user_id")
    val userId: String? = null,
    val id: String? = null,
    val nickname: String? = null,
    @SerialName("user_nickname")
    val userNickname: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    @SerialName("user_avatar")
    val userAvatar: String? = null,
    val image: String? = null,
    val images: String? = null,
    @SerialName("red_id")
    val redId: String? = null,
    val desc: String? = null,
    @SerialName("fans")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val fans: String? = null,
    @SerialName("fans_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val fansCount: String? = null,
    @SerialName("followers")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val followers: String? = null,
    @SerialName("followers_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val followersCount: String? = null,
    @SerialName("fans_total")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val fansTotal: String? = null,
    @SerialName("follows")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val follows: String? = null,
    @SerialName("follow_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val followCount: String? = null,
    @SerialName("follows_total")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val followsTotal: String? = null,
    @SerialName("notes")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val notes: String? = null,
    @SerialName("note_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val noteCount: String? = null,
    @SerialName("note_total")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val noteTotal: String? = null,
)

@Serializable
internal data class XhsInteractInfo(
    @SerialName("liked_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val likedCount: String? = null,
    @SerialName("collected_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val collectedCount: String? = null,
    @SerialName("comment_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val commentCount: String? = null,
    @SerialName("share_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val shareCount: String? = null,
    @SerialName("sticky")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val sticky: String? = null,
)

@Serializable
internal data class XhsImage(
    @SerialName("url_default")
    val urlDefault: String? = null,
    @SerialName("url_pre")
    val urlPre: String? = null,
    val url: String? = null,
    @SerialName("url_list")
    val urlList: List<String> = emptyList(),
    @SerialName("info_list")
    val infoList: List<XhsImageInfo> = emptyList(),
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
internal data class XhsImageInfo(
    val url: String? = null,
    @SerialName("image_scene")
    val imageScene: String? = null,
)

@Serializable
internal data class XhsVideo(
    val media: XhsVideoMedia? = null,
    @SerialName("image")
    val cover: XhsImage? = null,
)

@Serializable
internal data class XhsVideoMedia(
    @SerialName("stream")
    val stream: XhsVideoStream? = null,
)

@Serializable
internal data class XhsVideoStream(
    @SerialName("h264")
    val h264: List<XhsVideoStreamItem> = emptyList(),
    @SerialName("h265")
    val h265: List<XhsVideoStreamItem> = emptyList(),
    @SerialName("h266")
    val h266: List<XhsVideoStreamItem> = emptyList(),
    @SerialName("av1")
    val av1: List<XhsVideoStreamItem> = emptyList(),
)

@Serializable
internal data class XhsVideoStreamItem(
    @SerialName("master_url")
    val masterUrl: String? = null,
    val url: String? = null,
    @SerialName("backup_urls")
    val backupUrls: List<String> = emptyList(),
    val height: Int? = null,
    val width: Int? = null,
)

internal data class XhsNoteContext(
    val noteId: String,
    val xsecToken: String,
    val xsecSource: String,
)

@Serializable
internal data class XhsSearchNotesResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsSearchNotesData? = null,
)

@Serializable
internal data class XhsSearchNotesData(
    val items: List<XhsFeedItem> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
internal data class XhsUserInfoResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsUserInfoData? = null,
)

@Serializable
internal data class XhsUserInfoData(
    @SerialName("basic_info")
    val basicInfo: XhsUserBasicInfo? = null,
    val interactions: List<XhsUserInteraction> = emptyList(),
    @SerialName("user_id")
    val userId: String? = null,
    val nickname: String? = null,
    @SerialName("red_id")
    val redId: String? = null,
    val desc: String? = null,
    @SerialName("ip_location")
    val ipLocation: String? = null,
    @SerialName("fstatus")
    val followStatus: String? = null,
    @SerialName("fans")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fans: String = "0",
    @SerialName("fans_total")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fansTotal: String = "0",
    @SerialName("fans_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fansCount: String = "0",
    @SerialName("followers")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followers: String = "0",
    @SerialName("followers_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followersCount: String = "0",
    @SerialName("follows")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val follows: String = "0",
    @SerialName("follow")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val follow: String = "0",
    @SerialName("follow_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followCount: String = "0",
    @SerialName("following")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val following: String = "0",
    @SerialName("following_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followingCount: String = "0",
    @SerialName("notes")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val notes: String = "0",
    @SerialName("note_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val noteCount: String = "0",
    @SerialName("notes_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val notesCount: String = "0",
    @SerialName("posted_note_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val postedNoteCount: String = "0",
    @SerialName("posted_notes_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val postedNotesCount: String = "0",
)

@Serializable
internal data class XhsUserBasicInfo(
    @SerialName("user_id")
    val userId: String = "",
    val nickname: String = "",
    @SerialName("nick_name")
    val nickName: String = "",
    @SerialName("red_id")
    val redId: String = "",
    val desc: String = "",
    @SerialName("ip_location")
    val ipLocation: String = "",
    val avatar: String? = null,
    val image: String? = null,
    val images: String? = null,
    @SerialName("fstatus")
    val followStatus: String? = null,
    @SerialName("fans")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fans: String = "0",
    @SerialName("fans_total")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fansTotal: String = "0",
    @SerialName("fans_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val fansCount: String = "0",
    @SerialName("followers")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followers: String = "0",
    @SerialName("followers_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followersCount: String = "0",
    @SerialName("follows")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val follows: String = "0",
    @SerialName("follow")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val follow: String = "0",
    @SerialName("follow_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followCount: String = "0",
    @SerialName("following")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val following: String = "0",
    @SerialName("following_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val followingCount: String = "0",
    @SerialName("notes")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val notes: String = "0",
    @SerialName("note_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val noteCount: String = "0",
    @SerialName("notes_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val notesCount: String = "0",
    @SerialName("posted_note_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val postedNoteCount: String = "0",
    @SerialName("posted_notes_count")
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val postedNotesCount: String = "0",
)

@Serializable
internal data class XhsUserInteraction(
    val type: String = "",
    val name: String = "",
    val title: String = "",
    @Serializable(with = XhsFlexibleStringSerializer::class)
    val count: String = "0",
)

@Serializable
internal data class XhsUserPostedResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsUserPostedData? = null,
)

@Serializable
internal data class XhsUserPostedData(
    val notes: List<XhsNoteCard> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean = false,
    @SerialName("total")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val total: String? = null,
    @SerialName("total_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val totalCount: String? = null,
    @SerialName("note_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val noteCount: String? = null,
    @SerialName("notes_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val notesCount: String? = null,
)

@Serializable
internal data class XhsFollowResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsFollowData? = null,
)

@Serializable
internal data class XhsFollowData(
    @SerialName("fstatus")
    val followStatus: String? = null,
)

@Serializable
internal data class XhsCommentPageResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val data: XhsCommentPageData? = null,
)

@Serializable
internal data class XhsCommentPageData(
    val comments: List<XhsComment> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
internal data class XhsComment(
    val id: String = "",
    @SerialName("comment_id")
    val commentId: String? = null,
    val content: String = "",
    @SerialName("create_time")
    @Serializable(with = XhsFlexibleLongSerializer::class)
    val createTime: Long = 0L,
    @SerialName("like_count")
    @Serializable(with = XhsFlexibleNullableStringSerializer::class)
    val likeCount: String? = null,
    @SerialName("liked")
    val liked: Boolean = false,
    @SerialName("user_info")
    val userInfo: XhsUser? = null,
    @SerialName("sub_comment_count")
    val subCommentCount: String? = null,
    @SerialName("sub_comments")
    val subComments: List<XhsComment> = emptyList(),
    @SerialName("target_comment")
    val targetComment: XhsComment? = null,
    @SerialName("image_list")
    val imageList: List<XhsImage>? = null,
    val pictures: List<XhsImage>? = null,
    val image: XhsImage? = null,
)

@Serializable
internal data class XhsTelemetryEnvelope(
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

internal object XhsFlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XhsFlexibleString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: String,
    ) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.booleanOrNull?.toString().orEmpty()
            else -> runCatching { decoder.decodeString() }.getOrDefault("")
        }
    }
}

internal object XhsFlexibleNullableStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XhsFlexibleNullableString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: String?,
    ) {
        encoder.encodeString(value.orEmpty())
    }

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.booleanOrNull?.toString()
            else -> runCatching { decoder.decodeString() }.getOrNull()
        }
    }
}

internal object XhsFlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XhsFlexibleLong", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: Long,
    ) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.longOrNull ?: element.contentOrNull?.trim()?.toLongOrNull() ?: 0L
            else -> runCatching { decoder.decodeLong() }.getOrDefault(0L)
        }
    }
}
