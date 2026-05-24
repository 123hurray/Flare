package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.encodeJsonWithDefaults
import dev.dimension.flare.data.network.httpClientEngine
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.data.network.xiaohongshu.model.XhsCommentPageResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsCreatorSearchPage
import dev.dimension.flare.data.network.xiaohongshu.model.XhsCreatorSearchUsersRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFollowResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsHomeFeedRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsHomeFeedResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsSearchNotesRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsSearchNotesResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsSearchOneboxRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsSearchUser
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUserInfoResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUserMeResponse
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUserPostedResponse
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xiaohongshuApiHost
import dev.dimension.flare.model.xiaohongshuWebHost
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

internal class XhsService(
    private val accountKey: MicroBlogKey? = null,
    cookiesFlow: Flow<Map<String, String>>,
) {
    private val client = HttpClient(httpClientEngine)
    private val auth = XhsAuthPlugin(cookiesFlow)
    private val rateLimiter = XhsRateLimiter()
    private val searchSessions = mutableMapOf<SearchSessionKey, String>()

    suspend fun me(): XhsUserMeResponse =
        getApi(
            path = "/api/sns/web/v2/user/me",
            decode = { JSON.decodeFromString<XhsUserMeResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }

    suspend fun homeFeed(request: XhsHomeFeedRequest): XhsHomeFeedResponse {
        val path = "/api/sns/web/v1/homefeed"
        requireVerifiedMainApiSigning(path)
        return postApi(
            path = path,
            body = request.encodeJsonWithDefaults(),
            decode = { JSON.decodeFromString<XhsHomeFeedResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun feed(request: XhsFeedRequest): XhsFeedResponse {
        val path = "/api/sns/web/v1/feed"
        requireVerifiedMainApiSigning(path)
        return postApi(
            path = path,
            body = request.encodeJsonWithDefaults(),
            decode = { JSON.decodeFromString<XhsFeedResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun comments(
        noteId: String,
        xsecToken: String,
        cursor: String = "",
        topCommentId: String = "",
    ): XhsCommentPageResponse {
        val path =
            buildPathWithQuery(
                "/api/sns/web/v2/comment/page",
                listOf(
                    "note_id" to noteId,
                    "cursor" to cursor,
                    "top_comment_id" to topCommentId,
                    "image_formats" to "jpg,webp,avif",
                    "xsec_token" to xsecToken,
                ),
            )
        requireVerifiedMainApiSigning(path)
        return getApi(
            path = path,
            decode = { JSON.decodeFromString<XhsCommentPageResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun subComments(
        noteId: String,
        rootCommentId: String,
        cursor: String = "",
        num: Int = 30,
    ): XhsCommentPageResponse {
        val path =
            buildPathWithQuery(
                "/api/sns/web/v2/comment/sub/page",
                listOf(
                    "note_id" to noteId,
                    "root_comment_id" to rootCommentId,
                    "num" to num.toString(),
                    "cursor" to cursor,
                ),
            )
        requireVerifiedMainApiSigning(path)
        return getApi(
            path = path,
            decode = { JSON.decodeFromString<XhsCommentPageResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun userInfo(
        userId: String,
        mapLoginExpired: Boolean = true,
    ): XhsUserInfoResponse {
        val path =
            buildPathWithQuery(
                "/api/sns/web/v1/user/otherinfo",
                listOf("target_user_id" to userId),
            )
        return getApi(
            path = path,
            decode = { JSON.decodeFromString<XhsUserInfoResponse>(it) },
            mapLoginExpired = mapLoginExpired,
        ).also { response ->
            XhsErrorMapper.map(if (mapLoginExpired) accountKey else null, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun userPosted(
        userId: String,
        cursor: String = "",
    ): XhsUserPostedResponse {
        val path =
            buildPathWithQuery(
                "/api/sns/web/v1/user_posted",
                listOf(
                    "num" to "30",
                    "cursor" to cursor,
                    "user_id" to userId,
                    "image_scenes" to "FD_WM_WEBP",
                ),
            )
        return getApi(
            path = path,
            decode = { JSON.decodeFromString<XhsUserPostedResponse>(it) },
        ).also { response ->
            XhsErrorMapper.map(accountKey, response.code, response.msg)?.let { throw it }
        }
    }

    suspend fun searchNotes(
        keyword: String,
        page: Int,
        pageSize: Int = 20,
        sort: String = "general",
        noteType: Int = 0,
    ): XhsSearchNotesResponse {
        val sessionKey = SearchSessionKey(keyword.trim(), sort, noteType)
        val existingSearchId = searchSessions[sessionKey]
        val searchId = existingSearchId ?: generateSearchId().also { searchSessions[sessionKey] = it }
        if (existingSearchId == null) {
            runCatching {
                postApi(
                    path = "/api/sns/web/v1/search/onebox",
                    body =
                        XhsSearchOneboxRequest(
                            keyword = keyword,
                            searchId = searchId,
                            requestId = searchRequestId(),
                        ).encodeJsonWithDefaults(),
                    decode = { JSON.decodeFromString<JsonElement>(it) },
                )
                getApi(
                    path =
                        buildPathWithQuery(
                            "/api/sns/web/v1/search/filter",
                            listOf(
                                "keyword" to keyword,
                                "search_id" to searchId,
                            ),
                        ),
                    decode = { JSON.decodeFromString<JsonElement>(it) },
                )
            }
        }
        val response =
            postApi(
                path = "/api/sns/web/v1/search/notes",
                body =
                    XhsSearchNotesRequest(
                        keyword = keyword,
                        page = page,
                        pageSize = pageSize,
                        searchId = searchId,
                        sort = sort,
                        noteType = noteType,
                    ).encodeJsonWithDefaults(),
                decode = { JSON.decodeFromString<XhsSearchNotesResponse>(it) },
            ).also { result ->
                XhsErrorMapper.map(accountKey, result.code, result.msg)?.let { throw it }
            }
        if (existingSearchId == null) {
            runCatching {
                getApi(
                    path =
                        buildPathWithQuery(
                            "/api/sns/web/v1/search/recommend",
                            listOf("keyword" to keyword),
                        ),
                    decode = { JSON.decodeFromString<JsonElement>(it) },
                )
            }
        }
        return response
    }

    suspend fun searchUsers(
        keyword: String,
        pageSize: Int = 20,
    ): List<XhsSearchUser> {
        val path = "/web_api/sns/v1/search/user_info"
        val root =
            postCreatorApi(
                path = path,
                body =
                    XhsCreatorSearchUsersRequest(
                        keyword = keyword,
                        searchId = Clock.System.now().toEpochMilliseconds().toString(),
                        page = XhsCreatorSearchPage(pageSize = pageSize.coerceIn(1, 20)),
                    ).encodeJsonWithDefaults(),
                decode = { JSON.decodeFromString<JsonElement>(it) },
            )
        return root.collectSearchUsers(pageSize.coerceIn(1, 20))
    }

    suspend fun followUser(userId: String): XhsFollowResponse =
        userRelationPost("/api/sns/web/v1/user/follow", userId)

    suspend fun unfollowUser(userId: String): XhsFollowResponse =
        userRelationPost("/api/sns/web/v1/user/unfollow", userId)

    suspend fun noteHtml(noteId: String): String {
        rateLimiter.awaitTurn()
        val response =
            client.get("https://$xiaohongshuWebHost/explore/$noteId") {
                headers {
                    append(HttpHeaders.UserAgent, XhsAuthPlugin.USER_AGENT)
                    append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    append(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
                }
            }
        return response.bodyAsText()
    }

    private suspend fun <T> getApi(
        path: String,
        decode: (String) -> T,
        mapLoginExpired: Boolean = true,
    ): T {
        requireVerifiedMainApiSigning(path)
        val headers = auth.headers("GET", path, "")
        val response =
            executeWithBackoff {
                rateLimiter.awaitTurn()
                client.get("https://$xiaohongshuApiHost$path") {
                    headers {
                        headers.forEach { (name, value) -> append(name, value) }
                    }
                }
            }
        val body = response.bodyAsText()
        logResponse(path, response.status.value, body)
        if (mapLoginExpired && response.status == HttpStatusCode.Unauthorized) {
            XhsErrorMapper.map(accountKey, -100, null)?.let { throw it }
        }
        throwIfHttpError(response.status.value, body, mapLoginExpired)
        return decode(body).also { throwIfGenericError(body, mapLoginExpired) }
    }

    private suspend fun <T> postApi(
        path: String,
        body: String,
        decode: (String) -> T,
        mapLoginExpired: Boolean = true,
    ): T {
        val headers = auth.headers("POST", path, body)
        val response =
            executeWithBackoff {
                rateLimiter.awaitTurn()
                client.post("https://$xiaohongshuApiHost$path") {
                    headers {
                        headers.forEach { (name, value) -> append(name, value) }
                    }
                    setBody(body)
                }
            }
        val responseBody = response.bodyAsText()
        logResponse(path, response.status.value, responseBody)
        if (mapLoginExpired && response.status == HttpStatusCode.Unauthorized) {
            XhsErrorMapper.map(accountKey, -100, null)?.let { throw it }
        }
        throwIfHttpError(response.status.value, responseBody, mapLoginExpired)
        return decode(responseBody).also { throwIfGenericError(responseBody, mapLoginExpired) }
    }

    private suspend fun <T> postCreatorApi(
        path: String,
        body: String,
        decode: (String) -> T,
    ): T {
        val headers = auth.creatorHeaders(path, body)
        val response =
            executeWithBackoff {
                rateLimiter.awaitTurn()
                client.post("https://$xiaohongshuApiHost$path") {
                    headers {
                        headers.forEach { (name, value) -> append(name, value) }
                    }
                    setBody(body)
                }
            }
        val responseBody = response.bodyAsText()
        logResponse(path, response.status.value, responseBody)
        if (response.status == HttpStatusCode.Unauthorized) {
            XhsErrorMapper.map(accountKey, -100, null)?.let { throw it }
        }
        throwIfHttpError(response.status.value, responseBody)
        return decode(responseBody).also { throwIfGenericError(responseBody) }
    }

    private suspend fun userRelationPost(
        path: String,
        userId: String,
    ): XhsFollowResponse {
        val response =
            postApi(
                path = path,
                body =
                    buildJsonObject {
                        put("target_user_id", userId)
                    }.toString(),
                decode = { JSON.decodeFromString<XhsFollowResponse>(it) },
                mapLoginExpired = false,
            )
        XhsErrorMapper.map(null, response.code, response.msg)?.let { throw it }
        if (response.success) {
            logRelationResponse(path, response)
            return response
        }
        throw XhsHttpException(
            status = HttpStatusCode.OK.value,
            code = response.code,
            message = response.msg ?: "Xiaohongshu follow request failed",
        )
    }

    private fun logRelationResponse(
        path: String,
        response: XhsFollowResponse,
    ) {
        val line =
            buildString {
                append("XhsService: relation path=")
                append(path)
                append(" success=")
                append(response.success)
                append(" code=")
                append(response.code)
                append(" fstatus=")
                append(response.data?.followStatus.orEmpty())
            }
        println(line)
        DebugRepository.log(line)
    }

    private suspend fun executeWithBackoff(block: suspend () -> HttpResponse): HttpResponse {
        var attempt = 0
        var delayMs = 1_000L
        while (true) {
            val response = block()
            if (!response.status.shouldBackoff() || attempt >= 3) {
                return response
            }
            delay((delayMs + Random.nextLong(0L, 250L)).milliseconds)
            delayMs *= 2
            attempt += 1
        }
    }

    private fun HttpStatusCode.shouldBackoff(): Boolean = this == HttpStatusCode.TooManyRequests || value in 500..599

    private fun logResponse(
        path: String,
        status: Int,
        body: String,
    ) {
        val line =
            buildString {
                append("XhsService: response path=")
                append(path)
                append(" status=")
                append(status)
                append(" bodyLen=")
                append(body.length)
                append(" bodyFp=")
                append(body.hashCode().toUInt().toString(16))
                errorLogDetail(body)?.let {
                    append(" error=")
                    append(it)
                }
            }
        println(line)
        DebugRepository.log(line)
        logXhsDataSummary(path, body)
    }

    private fun logXhsDataSummary(
        path: String,
        body: String,
    ) {
        if (path.contains("/web_api/sns/v1/search/user_info")) {
            val root = runCatching { JSON.decodeFromString<JsonElement>(body) }.getOrNull() ?: return
            val rootObject = root as? JsonObject
            val data = rootObject?.get("data") as? JsonObject
            val users = root.collectSearchUsers(3)
            logDiagnostic(
                "XhsService: searchUserSummary rootKeys=${rootObject?.keys?.sorted().orEmpty()} " +
                    "dataKeys=${data?.keys?.sorted().orEmpty()} count=${root.collectSearchUsers(Int.MAX_VALUE).size} " +
                    "samples=${users.joinToString(prefix = "[", postfix = "]") { user ->
                        "{id=${firstNotBlank(user.userId, user.id, user.redId).sanitizeLogValue()}," +
                            "name=${firstNotBlank(user.nickname, user.userNickname, user.nickName, user.name).sanitizeLogValue()}}"
                    }}",
            )
            return
        }
        if (!path.contains("/api/sns/web/")) return
        val root = runCatching { JSON.decodeFromString<JsonElement>(body) }.getOrNull() as? JsonObject ?: return
        val data = root["data"] as? JsonObject ?: return
        when {
            path.contains("/homefeed") || path.contains("/search/notes") -> {
                (data["items"] as? JsonArray)
                    ?.take(3)
                    ?.forEachIndexed { index, item ->
                        val itemObject = item as? JsonObject ?: return@forEachIndexed
                        val note = itemObject["note_card"] as? JsonObject ?: return@forEachIndexed
                        logDiagnostic("XhsService: noteSummary source=$path index=$index ${note.summaryFields()}")
                    }
            }
            path.contains("/user_posted") -> {
                (data["notes"] as? JsonArray)
                    ?.take(3)
                    ?.forEachIndexed { index, item ->
                        val note = item as? JsonObject ?: return@forEachIndexed
                        logDiagnostic("XhsService: userPostSummary index=$index ${note.summaryFields()}")
                    }
            }
            path.contains("/user/otherinfo") -> {
                val basic = data["basic_info"] as? JsonObject
                val interactions =
                    (data["interactions"] as? JsonArray)
                        ?.take(8)
                        ?.joinToString(prefix = "[", postfix = "]") { item ->
                            val interaction = item as? JsonObject
                            "{type=${interaction?.valueForLog("type")},name=${interaction?.valueForLog("name")}," +
                                "title=${interaction?.valueForLog("title")},count=${interaction?.valueForLog("count")}}"
                        }
                        .orEmpty()
                logDiagnostic(
                    "XhsService: userInfoSummary dataKeys=${data.keys.sorted()} " +
                        "basicKeys=${basic?.keys?.sorted().orEmpty()} " +
                        "fstatus=${data.valueForLog("fstatus")} basicFstatus=${basic?.valueForLog("fstatus").orEmpty()} " +
                        "direct=${data.profileCountFields()} basic=${basic?.profileCountFields().orEmpty()} " +
                        "interactions=$interactions",
                )
            }
        }
    }

    private fun JsonObject.summaryFields(): String {
        val timeFields =
            keys
                .filter { key -> key.contains("time", ignoreCase = true) || key.contains("date", ignoreCase = true) }
                .sorted()
                .joinToString(prefix = "{", postfix = "}") { key -> "$key=${valueForLog(key)}" }
        val interact = this["interact_info"] as? JsonObject
        return "id=${valueForLog("note_id")} type=${valueForLog("type")} time=$timeFields " +
            "interactKeys=${interact?.keys?.sorted().orEmpty()} " +
            "sticky=${interact?.valueForLog("sticky").orEmpty()} " +
            "interact=${interact?.profileCountFields().orEmpty()}"
    }

    private fun JsonElement.collectSearchUsers(limit: Int): List<XhsSearchUser> {
        val users = mutableListOf<XhsSearchUser>()
        fun visit(element: JsonElement) {
            if (users.size >= limit) return
            when (element) {
                is JsonObject -> {
                    element.decodeCreatorSearchUserOrNull()?.let { user ->
                        users.add(user)
                    }
                    element.decodeSearchUserOrNull()?.let { user ->
                        users.add(user)
                    }
                    element.values.forEach(::visit)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }
        visit(this)
        return users
            .distinctBy { firstNotBlank(it.userId, it.id, it.redId, it.nickname, it.userNickname, it.nickName, it.name) }
            .take(limit)
    }

    private fun JsonObject.decodeCreatorSearchUserOrNull(): XhsSearchUser? {
        val baseObject = this["user_base_dto"] as? JsonObject ?: return null
        val base = baseObject.decodeSearchUserOrNull() ?: return null
        return base.copy(
            desc = firstNotBlank(base.desc, stringValue("desc"), stringValue("description")),
            fans = firstNotBlank(base.fans, stringValue("fans_total")),
            fansCount = firstNotBlank(base.fansCount, stringValue("fans_count")),
            followers = firstNotBlank(base.followers, stringValue("followers")),
            followersCount = firstNotBlank(base.followersCount, stringValue("followers_count")),
            fansTotal = firstNotBlank(base.fansTotal, stringValue("fans_total")),
            follows = firstNotBlank(base.follows, stringValue("follows_total"), stringValue("follows")),
            followCount = firstNotBlank(base.followCount, stringValue("follow_count")),
            followsTotal = firstNotBlank(base.followsTotal, stringValue("follows_total")),
            notes = firstNotBlank(base.notes, stringValue("note_total"), stringValue("notes")),
            noteCount = firstNotBlank(base.noteCount, stringValue("note_count")),
            noteTotal = firstNotBlank(base.noteTotal, stringValue("note_total")),
        )
    }

    private fun JsonObject.decodeSearchUserOrNull(): XhsSearchUser? {
        val hasUserIdentity =
            containsKey("user_id") ||
                containsKey("userId") ||
                containsKey("red_id") ||
                containsKey("user_nickname") ||
                (containsKey("id") && (containsKey("nickname") || containsKey("nick_name") || containsKey("name")))
        if (!hasUserIdentity) return null
        val user = runCatching { JSON.decodeFromJsonElement<XhsSearchUser>(this) }.getOrNull() ?: return null
        val id = firstNotBlank(user.userId, user.id, user.redId)
        val name = firstNotBlank(user.nickname, user.userNickname, user.nickName, user.name)
        return user.takeIf { id.isNotBlank() && name.isNotBlank() }
    }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun firstNotBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    private fun JsonObject.profileCountFields(): String {
        val fields =
            keys
                .filter { key ->
                    key.contains("fan", ignoreCase = true) ||
                        key.contains("follow", ignoreCase = true) ||
                        key.contains("note", ignoreCase = true) ||
                        key.contains("post", ignoreCase = true) ||
                        key.contains("count", ignoreCase = true)
                }
                .sorted()
                .joinToString(prefix = "{", postfix = "}") { key -> "$key=${valueForLog(key)}" }
        return fields
    }

    private fun JsonObject.valueForLog(key: String): String =
        when (val value = this[key]) {
            null -> "<missing>"
            is JsonObject -> "{keys=${value.keys.sorted()}}"
            is JsonArray -> "[size=${value.size}]"
            else -> value.jsonPrimitive.contentOrNull.orEmpty().take(80).sanitizeLogValue()
        }

    private fun logDiagnostic(line: String) {
        println(line)
        DebugRepository.log(line)
    }

    private fun throwIfHttpError(
        status: Int,
        body: String,
        mapLoginExpired: Boolean = true,
    ) {
        if (status in 200..299) return
        val (code, message) = errorCodeAndMessage(body)
        XhsErrorMapper.map(if (mapLoginExpired) accountKey else null, code, message)?.let { throw it }
        if (status == HttpStatusCode.NotAcceptable.value) {
            throw XhsSignatureException(message ?: "Xiaohongshu request rejected: HTTP $status")
        }
        throw XhsHttpException(
            status = status,
            code = code,
            message = message ?: "Xiaohongshu request failed: HTTP $status",
        )
    }

    private fun throwIfGenericError(
        body: String,
        mapLoginExpired: Boolean = true,
    ) {
        val parsed = runCatching { JSON.decodeFromString<JsonElement>(body).jsonObject }.getOrNull() ?: return
        val code = parsed["code"]?.jsonPrimitive?.content?.toIntOrNull()
        val msg = parsed["msg"]?.jsonPrimitive?.content
        XhsErrorMapper.map(if (mapLoginExpired) accountKey else null, code, msg)?.let { throw it }
    }

    private fun requireVerifiedMainApiSigning(path: String) {
        if (XhsSigning.IS_MAIN_API_SIGNING_VERIFIED) return
        val line = "XhsService: blocked path=$path reason=unverified_main_api_signing"
        println(line)
        DebugRepository.log(line)
        throw XhsSignatureException("Xiaohongshu main API signing is not verified; refusing to send $path")
    }

    private fun errorLogDetail(body: String): String? {
        val (code, message) = errorCodeAndMessage(body)
        return buildString {
            if (code != null) {
                append("code:")
                append(code)
            }
            if (!message.isNullOrBlank()) {
                if (isNotEmpty()) append(",")
                append("msg:")
                append(message.take(120).sanitizeLogValue())
            }
            if (isEmpty() && body.length <= 160) {
                val preview = body.trim().replace(Regex("\\s+"), " ").sanitizeLogValue()
                if (preview.isNotBlank()) {
                    append("body:")
                    append(preview)
                }
            }
        }.ifBlank { null }
    }

    private fun errorCodeAndMessage(body: String): Pair<Int?, String?> {
        val parsed = runCatching { JSON.decodeFromString<JsonElement>(body).jsonObject }.getOrNull()
            ?: return null to null
        val code =
            listOf("code", "errCode", "error_code")
                .firstNotNullOfOrNull { key ->
                    parsed[key]?.jsonPrimitive?.content?.toIntOrNull()
                }
        val message =
            listOf("msg", "message", "errMsg", "error_msg", "error")
                .firstNotNullOfOrNull { key ->
                    parsed[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                }
        return code to message
    }

    private fun String.sanitizeLogValue(): String =
        replace(Regex("(?i)(web_session|web_session_sec|id_token|websectiga|sec_poison_id|x-s-common|x-s|x-t|cookie|token)=?[^,;\\s\"]+"), "$1=<redacted>")

    private fun buildPathWithQuery(
        path: String,
        query: List<Pair<String, String>>,
    ): String {
        val encoded =
            query.joinToString("&") { (key, value) ->
                "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
        return "$path?$encoded"
    }

    private data class SearchSessionKey(
        val keyword: String,
        val sort: String,
        val noteType: Int,
    )

    private fun searchRequestId(): String =
        "${Random.nextInt(1_000_000_000, Int.MAX_VALUE)}-${Clock.System.now().toEpochMilliseconds()}"

    private fun generateSearchId(): String {
        var high = Clock.System.now().toEpochMilliseconds().toULong()
        var low = Random.nextInt(0, Int.MAX_VALUE).toUInt().toULong()
        if (high == 0UL && low == 0UL) return "0"
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val output = StringBuilder()
        while (high != 0UL || low != 0UL) {
            val divided = divideUnsigned128By36(high, low)
            output.append(chars[divided.remainder])
            high = divided.high
            low = divided.low
        }
        return output.reverse().toString()
    }

    private data class Div128(
        val high: ULong,
        val low: ULong,
        val remainder: Int,
    )

    private fun divideUnsigned128By36(
        high: ULong,
        low: ULong,
    ): Div128 {
        val qHigh = high / 36UL
        var remainder = (high % 36UL).toInt()
        var qLow = 0UL
        for (bit in 63 downTo 0) {
            remainder = (remainder shl 1) + (((low shr bit) and 1UL).toInt())
            if (remainder >= 36) {
                qLow = qLow or (1UL shl bit)
                remainder -= 36
            }
        }
        return Div128(qHigh, qLow, remainder)
    }
}

private class XhsRateLimiter {
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun awaitTurn() {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val nextAt = lastRequestAt + 1000L + Random.nextLong(0L, 350L)
            val waitMs = min(1500L, nextAt - now)
            if (waitMs > 0L) {
                delay(waitMs.milliseconds)
            }
            lastRequestAt = Clock.System.now().toEpochMilliseconds()
        }
    }
}
