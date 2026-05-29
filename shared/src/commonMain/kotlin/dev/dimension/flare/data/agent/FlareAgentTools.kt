package dev.dimension.flare.data.agent

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class FlareAgentTools(
    private val accountRepository: AccountRepository,
) {
    suspend fun specs(sourceContext: AgentSourceContext): List<AgentToolSpec> {
        val platformOptions = platformOptions(sourceContext)
        val platformDescription =
            "Platform parameter choices for this conversation: ${platformOptions.joinToString()}. Use ALL unless the user selected or asked for a specific platform."
        return listOf(
            AgentToolSpec(
                name = "get_current_feed_snapshot",
                description = "Return the compact feed/status items that were visible or captured when the conversation started. $platformDescription",
                parameters =
                    schema(
                        required = listOf("description"),
                        properties =
                            mapOf(
                                "description" to stringSchema("User-visible reason for reading the current feed snapshot."),
                                "platform" to platformSchema(platformDescription, platformOptions),
                            ),
                    ),
            ),
            AgentToolSpec(
                name = "search_statuses",
                description = "Search statuses/feed items across connected accounts. Use this before answering questions that need cross-account retrieval. $platformDescription",
                parameters =
                    schema(
                        required = listOf("query", "description"),
                        properties =
                            mapOf(
                                "query" to stringSchema("Keyword search query. Use segmented keywords or short phrases, not the full natural-language question."),
                                "description" to stringSchema("User-visible reason for this search."),
                                "platform" to platformSchema(platformDescription, platformOptions),
                                "limit" to integerSchema("Maximum number of compact results. Default 12, maximum 30."),
                                "all_accounts" to booleanSchema("Search every connected account when true. Defaults to true."),
                            ),
                    ),
            ),
            AgentToolSpec(
                name = "get_status_detail",
                description = "Load one status/feed item detail by account and status key.",
                parameters =
                    schema(
                        required = listOf("status_id", "status_host", "description"),
                        properties =
                            mapOf(
                                "status_id" to stringSchema("The status key id."),
                                "status_host" to stringSchema("The status key host."),
                                "description" to stringSchema("User-visible reason for loading this detail."),
                                "account_id" to stringSchema("Optional account id to use when opening the source account."),
                                "account_host" to stringSchema("Optional account host to use when opening the source account."),
                            ),
                    ),
            ),
            AgentToolSpec(
                name = "aggregate_subjects",
                description = "Group compact timeline items by author, source, link, or similar topic for cross-account subject aggregation.",
                parameters =
                    schema(
                        required = listOf("items", "description"),
                        properties =
                            mapOf(
                                "items" to arraySchema("Compact timeline item JSON objects returned by other tools."),
                                "description" to stringSchema("User-visible reason for grouping these items."),
                                "limit" to integerSchema("Maximum number of groups. Default 8."),
                            ),
                    ),
            ),
        )
    }

    private suspend fun platformOptions(sourceContext: AgentSourceContext): List<String> {
        val allowed = sourceContext.normalizedAllowedPlatforms()
        if (allowed.isNotEmpty()) {
            return listOf("ALL") + allowed
        }
        val accountPlatforms =
            accountRepository
                .allAccounts
                .first()
                .map { it.platformType.name.toAgentPlatformName() }
        val feedPlatforms = sourceContext.feedSnapshot.mapNotNull { it.platform?.toAgentPlatformName() }
        return listOf("ALL")
            .plus(accountPlatforms)
            .plus(feedPlatforms)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    suspend fun execute(
        call: AgentToolCall,
        sourceContext: AgentSourceContext,
    ): AgentToolResult =
        runCatching {
            when (call.name) {
                "get_current_feed_snapshot" -> {
                    val args = call.arguments.decodeJson<JsonObject>()
                    val platform = args.platformOrNull(sourceContext)
                    val allowedPlatforms = sourceContext.normalizedAllowedPlatforms()
                    val loaded =
                        sourceContext.feedSnapshot
                            .filterByPlatform(platform, allowedPlatforms)
                            .take(30)
                            .let { captured ->
                                if (captured.isNotEmpty()) {
                                    AgentToolLoadResult(items = captured)
                                } else {
                                loadFeedSnapshot(
                                    platform = platform,
                                    allowedPlatforms = allowedPlatforms,
                                    limit = 30,
                                )
                            }
                            }
                    val items = loaded.items
                    AgentToolResult(
                        text =
                            AgentItemsResponse(items = items, warnings = loaded.warnings).encodeJson(),
                        artifacts = items.map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
                    )
                }

                "search_statuses" -> searchStatuses(call.arguments, sourceContext)
                "get_status_detail" -> getStatusDetail(call.arguments, sourceContext)
                "aggregate_subjects" -> aggregateSubjects(call.arguments)
                else -> AgentToolResult("Unknown tool: ${call.name}", isError = true)
            }
        }.getOrElse {
            AgentToolResult(
                text = it.message ?: it::class.simpleName.orEmpty(),
                isError = true,
            )
        }

    private suspend fun searchStatuses(
        arguments: String,
        sourceContext: AgentSourceContext,
    ): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val query = args.string("query")
        val limit = (args.int("limit") ?: 12).coerceIn(1, 30)
        val allAccounts = args.boolean("all_accounts") ?: true
        val platform = args.platformOrNull(sourceContext)
        val allowedPlatforms = sourceContext.normalizedAllowedPlatforms()
        val services =
            if (allAccounts) {
                accountRepository
                    .allAccounts
                    .first()
                    .filter { account ->
                        account.platformType.name.matchesPlatformScope(platform, allowedPlatforms)
                    }.map {
                        it.platformType.name.toAgentPlatformName() to accountRepository.getOrCreateDataSource(it)
                    }
            } else {
                val accountId = args.stringOrNull("account_id")
                val accountHost = args.stringOrNull("account_host")
                val accountType =
                    if (accountId != null && accountHost != null) {
                        AccountType.Specific(MicroBlogKey(accountId, accountHost))
                    } else {
                        AccountType.Guest
                    }
                listOf(platform.orEmpty() to accountServiceFlow(accountType, accountRepository).first())
            }
        val loaded =
            services
                .loadInParallel(action = "搜索") { service ->
                    service
                        .searchStatus(query)
                        .load(pageSize = limit, request = PagingRequest.Refresh)
                        .data
                }
        val items =
            loaded
                .items
                .filterByPlatform(platform, allowedPlatforms)
                .distinctBy { it.id }
                .take(limit)
        return AgentToolResult(
            text =
                AgentSearchResponse(
                    query = query,
                    platform = platform ?: "ALL",
                    items = items,
                    warnings = loaded.warnings,
                ).encodeJson(),
            artifacts = items.take(8).map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
        )
    }

    private suspend fun loadFeedSnapshot(
        platform: String?,
        allowedPlatforms: List<String>,
        limit: Int,
    ): AgentToolLoadResult {
        val services =
            accountRepository
                .allAccounts
                .first()
                .filter { account ->
                    account.platformType.name.matchesPlatformScope(platform, allowedPlatforms)
                }.map {
                    it.platformType.name.toAgentPlatformName() to accountRepository.getOrCreateDataSource(it)
                }
        val loaded =
            services
            .loadInParallel(action = "读取 feed") { service ->
                service
                    .homeTimeline()
                    .load(pageSize = limit.coerceIn(1, 30), request = PagingRequest.Refresh)
                    .data
            }
        return loaded.copy(
            items =
                loaded.items
            .filterByPlatform(platform, allowedPlatforms)
            .distinctBy { it.id }
            .take(limit),
        )
    }

    private suspend fun getStatusDetail(
        arguments: String,
        sourceContext: AgentSourceContext,
    ): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val statusKey = MicroBlogKey(args.string("status_id"), args.string("status_host"))
        val accountType =
            args.stringOrNull("account_id")?.let { id ->
                args.stringOrNull("account_host")?.let { host ->
                    AccountType.Specific(MicroBlogKey(id, host))
                }
            } ?: sourceContext.accountType ?: AccountType.GuestHost(statusKey.host)
        val service = accountServiceFlow(accountType, accountRepository).first()
        val item =
            if (service is PostDataSource) {
                val cacheable = service.postHandler.post(statusKey)
                cacheable.refresh()
                cacheable.data.filterIsInstance<CacheState.Success<UiTimelineV2>>().first().data
            } else {
                null
            } ?: runCatching {
                service.context(statusKey).load(20, PagingRequest.Refresh).data.firstOrNull {
                    it.statusKey == statusKey
                }
            }.getOrNull()
        val compact = item?.toAgentTimelineItem()
        return if (compact == null) {
            AgentToolResult("No detail found for ${statusKey.id}@${statusKey.host}", isError = true)
        } else {
            AgentToolResult(
                text = compact.encodeJson(),
                artifacts =
                    listOf(
                        AgentNativeArtifact.FeedCardRef(
                            id = "artifact-${compact.id}",
                            item = compact,
                        ),
                        AgentNativeArtifact.DetailLinkRef(
                            id = "link-${compact.id}",
                            text = compact.title ?: compact.authorName ?: "打开详情",
                            deeplink = compact.deeplink.orEmpty(),
                        ),
                    ),
            )
        }
    }

    private fun aggregateSubjects(arguments: String): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val limit = (args.int("limit") ?: 8).coerceIn(1, 20)
        val items =
            args["items"]
                ?.jsonArray
                ?.mapNotNull {
                    runCatching {
                        JSON.decodeFromJsonElement(AgentTimelineItem.serializer(), it)
                    }.getOrNull()
                }.orEmpty()
        val groups =
            items
                .groupBy { it.authorHandle ?: it.authorName ?: it.platform ?: it.kind }
                .entries
                .sortedByDescending { it.value.size }
                .take(limit)
                .map { (key, value) ->
                    AgentNativeArtifact.SubjectGroupRef(
                        id = "subject-${key.hashCode()}",
                        title = key,
                        summary = "${value.size} items across ${value.mapNotNull { it.platform }.distinct().joinToString()}",
                        items = value.take(6),
                    )
                }
        return AgentToolResult(
            text =
                AgentSubjectGroupResponse(groups = groups).encodeJson(),
            artifacts = groups,
        )
    }
}

private fun UiTimelineV2.toAgentTimelineItem(): AgentTimelineItem =
    when (this) {
        is UiTimelineV2.Feed ->
            AgentTimelineItem(
                id = itemKey ?: url,
                kind = "feed",
                title = title,
                text = description.orEmpty().compactForAgent(),
                authorName = source.name,
                platform = "RSS",
                createdAtEpochMillis = actualCreatedAt?.value?.toEpochMilliseconds(),
                accountType = accountType,
                statusKey = statusKey,
                deeplink = DeeplinkRoute.Rss.Detail(url, descriptionHtml, title).toUri(),
                mediaPreviewUrl = media?.url,
            )

        is UiTimelineV2.Post ->
            AgentTimelineItem(
                id = itemKey ?: "${statusKey.host}:${statusKey.id}",
                kind = "post",
                title = contentWarning?.innerText,
                text = content.innerText.compactForAgent(),
                authorName = user?.name?.innerText,
                authorHandle = user?.handle?.raw,
                platform = platformType.name.toAgentPlatformName(),
                createdAtEpochMillis = createdAt.value.toEpochMilliseconds(),
                accountType = accountType,
                statusKey = statusKey,
                deeplink = DeeplinkRoute.Status.Detail(statusKey, accountType).toUri(),
                mediaPreviewUrl = images.firstOrNull()?.url,
            )

        is UiTimelineV2.User ->
            AgentTimelineItem(
                id = itemKey ?: "${statusKey.host}:${statusKey.id}",
                kind = "user",
                title = value.name.innerText,
                text = value.description?.innerText?.compactForAgent().orEmpty(),
                authorName = value.name.innerText,
                authorHandle = value.handle.raw,
                platform = value.platformType.name.toAgentPlatformName(),
                createdAtEpochMillis = createdAt.value.toEpochMilliseconds(),
                accountType = accountType,
                statusKey = statusKey,
                deeplink = DeeplinkRoute.Profile.User(accountType, value.key).toUri(),
                mediaPreviewUrl = value.avatar,
            )

        is UiTimelineV2.UserList ->
            AgentTimelineItem(
                id = itemKey ?: "${statusKey.host}:${statusKey.id}",
                kind = "user_list",
                title = users.joinToString { it.name.innerText }.take(120),
                text = post?.content?.innerText?.compactForAgent().orEmpty(),
                platform = post?.platformType?.name?.toAgentPlatformName(),
                createdAtEpochMillis = createdAt.value.toEpochMilliseconds(),
                accountType = accountType,
                statusKey = statusKey,
                deeplink = post?.let { DeeplinkRoute.Status.Detail(it.statusKey, it.accountType).toUri() },
                mediaPreviewUrl = users.firstOrNull()?.avatar,
            )

        is UiTimelineV2.Message ->
            AgentTimelineItem(
                id = itemKey ?: "${statusKey.host}:${statusKey.id}",
                kind = "message",
                title = user?.name?.innerText,
                text = type.toString().compactForAgent(),
                authorName = user?.name?.innerText,
                authorHandle = user?.handle?.raw,
                createdAtEpochMillis = createdAt.value.toEpochMilliseconds(),
                accountType = accountType,
                statusKey = statusKey,
                deeplink = DeeplinkRoute.Status.Detail(statusKey, accountType).toUri(),
                mediaPreviewUrl = user?.avatar,
            )
    }

private fun AgentTimelineItem.withFallbackPlatform(platform: String): AgentTimelineItem =
    if (this.platform.isNullOrBlank() && platform.isNotBlank()) {
        copy(platform = platform)
    } else {
        this
    }

private suspend fun List<Pair<String, MicroblogDataSource>>.loadInParallel(
    action: String,
    loader: suspend (MicroblogDataSource) -> List<UiTimelineV2>,
): AgentToolLoadResult =
    coroutineScope {
        map { (servicePlatform, service) ->
            async {
                val result =
                    withTimeoutOrNull(AGENT_TOOL_SERVICE_TIMEOUT_MS) {
                        runCatching {
                            loader(service)
                        }
                    } ?: return@async AgentToolLoadResult(
                        warnings = listOf("$servicePlatform ${action}超时，已跳过该平台。"),
                    )
                result.fold(
                    onSuccess = { rows ->
                        AgentToolLoadResult(
                            items = rows.map { it.toAgentTimelineItem().withFallbackPlatform(servicePlatform) },
                        )
                    },
                    onFailure = {
                        AgentToolLoadResult(
                            warnings = listOf("$servicePlatform ${action}失败：${it.agentToolMessage()}"),
                        )
                    },
                )
            }
        }.awaitAll().let { results ->
            AgentToolLoadResult(
                items = results.flatMap { it.items },
                warnings = results.flatMap { it.warnings },
            )
        }
    }

private fun Throwable.agentToolMessage(): String =
    message
        ?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "未知错误"

private fun List<AgentTimelineItem>.filterByPlatform(
    platform: String?,
    allowedPlatforms: List<String>,
): List<AgentTimelineItem> =
    filter { it.platform.orEmpty().matchesPlatformScope(platform, allowedPlatforms) }

private fun JsonObject.platformOrNull(sourceContext: AgentSourceContext): String? {
    val requested = stringOrNull("platform")?.takeUnless { it.equals("ALL", ignoreCase = true) }
    val allowed = sourceContext.normalizedAllowedPlatforms()
    val platform = requested ?: allowed.singleOrNull()
    if (platform != null && allowed.isNotEmpty() && allowed.none { it.equals(platform, ignoreCase = true) }) {
        error("Platform $platform is not allowed by the user. Allowed platforms: ${allowed.joinToString()}")
    }
    return platform
}

private fun AgentSourceContext.normalizedAllowedPlatforms(): List<String> =
    allowedPlatforms
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.toAgentPlatformName() }

private fun String.matchesPlatformScope(
    platform: String?,
    allowedPlatforms: List<String>,
): Boolean {
    val current = toAgentPlatformName()
    if (platform != null) {
        return current.equals(platform.toAgentPlatformName(), ignoreCase = true)
    }
    return allowedPlatforms.isEmpty() || allowedPlatforms.any { current.equals(it.toAgentPlatformName(), ignoreCase = true) }
}

private fun String.toAgentPlatformName(): String =
    when (trim().lowercase()) {
        "all" -> "ALL"
        "vvo", "weibo", "微博", "新浪微博" -> "微博"
        "xiaohongshu", "xhs", "小红书", "小紅書" -> "小红书"
        "xqt", "twitter", "x" -> "X"
        "jike", "即刻" -> "即刻"
        "dongqiudi", "懂球帝" -> "懂球帝"
        "zhihu", "知乎" -> "知乎"
        "rss" -> "RSS"
        "mastodon" -> "Mastodon"
        "misskey" -> "Misskey"
        "bluesky" -> "Bluesky"
        "nostr" -> "Nostr"
        "instagram" -> "Instagram"
        else -> trim()
    }

private fun JsonObject.string(name: String): String =
    stringOrNull(name) ?: error("Missing string argument: $name")

private fun JsonObject.stringOrNull(name: String): String? =
    this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.int(name: String): Int? =
    this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull

private fun String.compactForAgent(maxLength: Int = 1200): String =
    replace(Regex("\\s+"), " ").trim().let {
        if (it.length > maxLength) it.take(maxLength) + "..." else it
    }

private fun schema(
    required: List<String> = emptyList(),
    properties: Map<String, JsonElement>,
): JsonObject =
    JsonObject(
        buildMap {
            put("type", JsonPrimitive("object"))
            put("properties", JsonObject(properties))
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }
        },
    )

private fun stringSchema(description: String): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("string"),
            "description" to JsonPrimitive(description),
        ),
    )

private fun platformSchema(
    description: String,
    options: List<String>,
): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("string"),
            "description" to JsonPrimitive(description),
            "enum" to JsonArray(options.map { JsonPrimitive(it) }),
        ),
    )

private fun integerSchema(description: String): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("integer"),
            "description" to JsonPrimitive(description),
        ),
    )

private fun booleanSchema(description: String): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("boolean"),
            "description" to JsonPrimitive(description),
        ),
    )

private fun arraySchema(description: String): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("array"),
            "description" to JsonPrimitive(description),
        ),
    )

private const val AGENT_TOOL_SERVICE_TIMEOUT_MS = 8_000L

private data class AgentToolLoadResult(
    val items: List<AgentTimelineItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentItemsResponse(
    val items: List<AgentTimelineItem>,
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentSearchResponse(
    val query: String,
    val platform: String,
    val items: List<AgentTimelineItem>,
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentSubjectGroupResponse(
    val groups: List<AgentNativeArtifact.SubjectGroupRef>,
)
