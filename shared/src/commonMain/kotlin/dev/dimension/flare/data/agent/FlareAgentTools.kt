package dev.dimension.flare.data.agent

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
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
                                "pages" to integerSchema("Number of pages to load when the captured snapshot is empty. Default 1, maximum 5."),
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
                                "pages" to integerSchema("Number of result pages to load per account. Default 1, maximum 5."),
                                "all_accounts" to booleanSchema("Search every connected account when true. Defaults to true."),
                            ),
                    ),
            ),
            AgentToolSpec(
                name = "search_users",
                description = "Search users across connected social media accounts. $platformDescription",
                parameters =
                    schema(
                        required = listOf("query", "description"),
                        properties =
                            mapOf(
                                "query" to stringSchema("User keyword, display name, or handle."),
                                "description" to stringSchema("User-visible reason for this user search."),
                                "platform" to platformSchema(platformDescription, platformOptions),
                                "limit" to integerSchema("Maximum number of users. Default 12, maximum 30."),
                                "pages" to integerSchema("Number of result pages to load per account. Default 1, maximum 5."),
                                "all_accounts" to booleanSchema("Search every connected account when true. Defaults to true."),
                                "account_id" to stringSchema("Optional account id when all_accounts is false."),
                                "account_host" to stringSchema("Optional account host when all_accounts is false."),
                            ),
                    ),
            ),
            AgentToolSpec(
                name = "search_user_profile_statuses",
                description = "Search posts from a specific user's profile timeline by locally filtering loaded profile pages. Useful for Weibo, X, Xiaohongshu, and other platforms with profile timelines. $platformDescription",
                parameters =
                    schema(
                        required = listOf("query", "user_id", "user_host", "description"),
                        properties =
                            mapOf(
                                "query" to stringSchema("Keyword to find inside the user's profile posts."),
                                "user_id" to stringSchema("The target profile key id returned by search_users or a profile item."),
                                "user_host" to stringSchema("The target profile key host returned by search_users or a profile item."),
                                "description" to stringSchema("User-visible reason for this profile-post search."),
                                "platform" to platformSchema(platformDescription, platformOptions),
                                "limit" to integerSchema("Maximum number of compact results. Default 12, maximum 30."),
                                "pages" to integerSchema("Number of profile pages to load. Default 3, maximum 10."),
                                "account_id" to stringSchema("Optional account id to use when opening the source account."),
                                "account_host" to stringSchema("Optional account host to use when opening the source account."),
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
                name = "get_status_comments",
                description = "Load comments or a comment thread for one status/feed item by account and status key.",
                parameters =
                    schema(
                        required = listOf("status_id", "status_host", "description"),
                        properties =
                            mapOf(
                                "status_id" to stringSchema("The status key id."),
                                "status_host" to stringSchema("The status key host."),
                                "description" to stringSchema("User-visible reason for loading comments."),
                                "account_id" to stringSchema("Optional account id to use when opening the source account."),
                                "account_host" to stringSchema("Optional account host to use when opening the source account."),
                                "limit" to integerSchema("Maximum number of compact comments. Default 20, maximum 50."),
                                "pages" to integerSchema("Number of comment pages to load. Default 1, maximum 10."),
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
                    val pages = (args.int("pages") ?: 1).coerceIn(1, 5)
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
                                    pages = pages,
                                )
                            }
                            }
                    val items = loaded.items.withReferenceIds()
                    AgentToolResult(
                        text =
                            AgentItemsResponse(items = items.toModelItems(), warnings = loaded.warnings).encodeJson(),
                        artifacts = items.map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
                    )
                }

                "search_statuses" -> searchStatuses(call.arguments, sourceContext)
                "search_users" -> searchUsers(call.arguments, sourceContext)
                "search_user_profile_statuses" -> searchUserProfileStatuses(call.arguments, sourceContext)
                "get_status_detail" -> getStatusDetail(call.arguments, sourceContext)
                "get_status_comments" -> getStatusComments(call.arguments, sourceContext)
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
        val pages = (args.int("pages") ?: 1).coerceIn(1, 5)
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
                        .loadPages(pageSize = limit, pages = pages)
                }
        val items =
            loaded
                .items
                .filterByPlatform(platform, allowedPlatforms)
                .distinctBy { it.id }
                .take(limit)
                .withReferenceIds()
        return AgentToolResult(
            text =
                AgentSearchResponse(
                    query = query,
                    platform = platform ?: "ALL",
                    items = items.toModelItems(),
                    warnings = loaded.warnings,
                ).encodeJson(),
            artifacts = items.map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
        )
    }

    private suspend fun searchUsers(
        arguments: String,
        sourceContext: AgentSourceContext,
    ): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val query = args.string("query")
        val limit = (args.int("limit") ?: 12).coerceIn(1, 30)
        val pages = (args.int("pages") ?: 1).coerceIn(1, 5)
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
            services.loadProfilesInParallel(action = "搜索用户") { service ->
                service
                    .searchUser(query)
                    .loadProfilePages(pageSize = limit, pages = pages)
            }
        val users =
            loaded
                .items
                .filter { it.platform.matchesPlatformScope(platform, allowedPlatforms) }
                .distinctBy { "${it.userId}@${it.userHost}" }
                .take(limit)
        return AgentToolResult(
            text =
                AgentUserSearchResponse(
                    query = query,
                    platform = platform ?: "ALL",
                    users = users,
                    warnings = loaded.warnings,
                ).encodeJson(),
        )
    }

    private suspend fun searchUserProfileStatuses(
        arguments: String,
        sourceContext: AgentSourceContext,
    ): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val query = args.string("query")
        val limit = (args.int("limit") ?: 12).coerceIn(1, 30)
        val pages = (args.int("pages") ?: 3).coerceIn(1, 10)
        val platform = args.platformOrNull(sourceContext)
        val allowedPlatforms = sourceContext.normalizedAllowedPlatforms()
        val userKey = MicroBlogKey(args.string("user_id"), args.string("user_host"))
        val services =
            args.stringOrNull("account_id")?.let { id ->
                args.stringOrNull("account_host")?.let { host ->
                    listOf(platform.orEmpty() to accountServiceFlow(AccountType.Specific(MicroBlogKey(id, host)), accountRepository).first())
                }
            } ?: accountRepository
                .allAccounts
                .first()
                .filter { account ->
                    account.platformType.name.matchesPlatformScope(platform, allowedPlatforms)
                }.map {
                    it.platformType.name.toAgentPlatformName() to accountRepository.getOrCreateDataSource(it)
                }
        val loaded =
            services.loadInParallel(action = "搜索用户主页") { service ->
                service
                    .userTimeline(userKey, mediaOnly = false)
                    .loadPages(pageSize = limit, pages = pages)
                    .filter { item ->
                        item.searchText.orEmpty().contains(query, ignoreCase = true)
                    }
            }
        val items =
            loaded
                .items
                .filterByPlatform(platform, allowedPlatforms)
                .distinctBy { it.id }
                .take(limit)
                .withReferenceIds()
        return AgentToolResult(
            text =
                AgentSearchResponse(
                    query = query,
                    platform = platform ?: "ALL",
                    items = items.toModelItems(),
                    warnings = loaded.warnings,
                ).encodeJson(),
            artifacts = items.map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
        )
    }

    private suspend fun loadFeedSnapshot(
        platform: String?,
        allowedPlatforms: List<String>,
        limit: Int,
        pages: Int,
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
                    .loadPages(pageSize = limit.coerceIn(1, 30), pages = pages)
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
        val compact = item?.toAgentTimelineItem()?.withReferenceId(0, mutableSetOf())
        return if (compact == null) {
            AgentToolResult("No detail found for ${statusKey.id}@${statusKey.host}", isError = true)
        } else {
            AgentToolResult(
                text = AgentDetailResponse(item = compact.toModelItem()).encodeJson(),
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

    private suspend fun getStatusComments(
        arguments: String,
        sourceContext: AgentSourceContext,
    ): AgentToolResult {
        val args = arguments.decodeJson<JsonObject>()
        val statusKey = MicroBlogKey(args.string("status_id"), args.string("status_host"))
        val limit = (args.int("limit") ?: 20).coerceIn(1, 50)
        val pages = (args.int("pages") ?: 1).coerceIn(1, 10)
        val accountType =
            args.stringOrNull("account_id")?.let { id ->
                args.stringOrNull("account_host")?.let { host ->
                    AccountType.Specific(MicroBlogKey(id, host))
                }
            } ?: sourceContext.accountType ?: AccountType.GuestHost(statusKey.host)
        val service = accountServiceFlow(accountType, accountRepository).first()
        val comments =
            service
                .context(statusKey)
                .loadPages(pageSize = limit, pages = pages)
                .filter { it.statusKey != statusKey }
                .map { it.toAgentTimelineItem() }
                .distinctBy { it.id }
                .take(limit)
                .withReferenceIds()
        return AgentToolResult(
            text =
                AgentCommentsResponse(
                    statusId = statusKey.id,
                    statusHost = statusKey.host,
                    items = comments.toModelItems(),
                ).encodeJson(),
            artifacts = comments.map { AgentNativeArtifact.FeedCardRef(id = "artifact-${it.id}", item = it) },
        )
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
                authorAvatarUrl = source.icon,
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
                authorAvatarUrl = user?.avatar,
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
                authorAvatarUrl = value.avatar,
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
                authorName = users.firstOrNull()?.name?.innerText,
                authorHandle = users.firstOrNull()?.handle?.raw,
                authorAvatarUrl = users.firstOrNull()?.avatar,
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
                authorAvatarUrl = user?.avatar,
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

private suspend fun List<Pair<String, MicroblogDataSource>>.loadProfilesInParallel(
    action: String,
    loader: suspend (MicroblogDataSource) -> List<UiProfile>,
): AgentProfileLoadResult =
    coroutineScope {
        map { (servicePlatform, service) ->
            async {
                val result =
                    withTimeoutOrNull(AGENT_TOOL_SERVICE_TIMEOUT_MS) {
                        runCatching {
                            loader(service)
                        }
                    } ?: return@async AgentProfileLoadResult(
                        warnings = listOf("$servicePlatform ${action}超时，已跳过该平台。"),
                    )
                result.fold(
                    onSuccess = { rows ->
                        AgentProfileLoadResult(
                            items = rows.map { it.toAgentProfileItem(servicePlatform) },
                        )
                    },
                    onFailure = {
                        AgentProfileLoadResult(
                            warnings = listOf("$servicePlatform ${action}失败：${it.agentToolMessage()}"),
                        )
                    },
                )
            }
        }.awaitAll().let { results ->
            AgentProfileLoadResult(
                items = results.flatMap { it.items },
                warnings = results.flatMap { it.warnings },
            )
        }
    }

private suspend fun RemoteLoader<UiTimelineV2>.loadPages(
    pageSize: Int,
    pages: Int,
): List<UiTimelineV2> {
    val items = mutableListOf<UiTimelineV2>()
    var result = load(pageSize = pageSize, request = PagingRequest.Refresh)
    items += result.data
    var nextKey = result.nextKey
    var loadedPages = 1
    while (!nextKey.isNullOrBlank() && loadedPages < pages) {
        result = load(pageSize = pageSize, request = PagingRequest.Append(nextKey))
        items += result.data
        nextKey = result.nextKey
        loadedPages += 1
    }
    return items
}

private suspend fun RemoteLoader<UiProfile>.loadProfilePages(
    pageSize: Int,
    pages: Int,
): List<UiProfile> {
    val items = mutableListOf<UiProfile>()
    var result = load(pageSize = pageSize, request = PagingRequest.Refresh)
    items += result.data
    var nextKey = result.nextKey
    var loadedPages = 1
    while (!nextKey.isNullOrBlank() && loadedPages < pages) {
        result = load(pageSize = pageSize, request = PagingRequest.Append(nextKey))
        items += result.data
        nextKey = result.nextKey
        loadedPages += 1
    }
    return items
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

private data class AgentProfileLoadResult(
    val items: List<AgentProfileItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentItemsResponse(
    val items: List<AgentModelTimelineItem>,
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentSearchResponse(
    val query: String,
    val platform: String,
    val items: List<AgentModelTimelineItem>,
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentUserSearchResponse(
    val query: String,
    val platform: String,
    val users: List<AgentProfileItem>,
    val warnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class AgentDetailResponse(
    val item: AgentModelTimelineItem,
)

@kotlinx.serialization.Serializable
private data class AgentCommentsResponse(
    val statusId: String,
    val statusHost: String,
    val items: List<AgentModelTimelineItem>,
)

@kotlinx.serialization.Serializable
private data class AgentProfileItem(
    val userId: String,
    val userHost: String,
    val platform: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val fansCount: Long = 0,
    val followsCount: Long = 0,
    val statusesCount: Long = 0,
)

@kotlinx.serialization.Serializable
private data class AgentModelTimelineItem(
    val id: String,
    val kind: String,
    val platform: String? = null,
    val statusId: String? = null,
    val statusHost: String? = null,
    val accountId: String? = null,
    val accountHost: String? = null,
    val deeplink: String? = null,
    val title: String? = null,
    val text: String,
    val authorName: String? = null,
    val authorHandle: String? = null,
    val authorAvatarUrl: String? = null,
    val createdAtEpochMillis: Long? = null,
    val createdAtIso: String? = null,
    val mediaPreviewUrl: String? = null,
)

@kotlinx.serialization.Serializable
private data class AgentSubjectGroupResponse(
    val groups: List<AgentNativeArtifact.SubjectGroupRef>,
)

private fun List<AgentTimelineItem>.toModelItems(): List<AgentModelTimelineItem> = map { it.toModelItem() }

private fun AgentTimelineItem.toModelItem(): AgentModelTimelineItem =
    AgentModelTimelineItem(
        id = referenceId ?: id,
        kind = kind,
        platform = platform,
        statusId = statusKey?.id,
        statusHost = statusKey?.host,
        accountId = (accountType as? AccountType.Specific)?.accountKey?.id,
        accountHost = (accountType as? AccountType.Specific)?.accountKey?.host,
        deeplink = deeplink,
        title = title,
        text = text,
        authorName = authorName,
        authorHandle = authorHandle,
        authorAvatarUrl = authorAvatarUrl,
        createdAtEpochMillis = createdAtEpochMillis,
        createdAtIso = createdAtEpochMillis?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString() },
        mediaPreviewUrl = mediaPreviewUrl,
    )

private fun UiProfile.toAgentProfileItem(platform: String): AgentProfileItem =
    AgentProfileItem(
        userId = key.id,
        userHost = key.host,
        platform = platform,
        name = name.innerText,
        handle = handle.raw,
        avatarUrl = avatar.takeIf { it.isNotBlank() },
        description = description?.innerText?.compactForAgent(),
        fansCount = matrices.fansCount,
        followsCount = matrices.followsCount,
        statusesCount = matrices.statusesCount,
    )

private fun List<AgentTimelineItem>.withReferenceIds(): List<AgentTimelineItem> {
    val used = mutableSetOf<String>()
    return mapIndexed { index, item ->
        item.withReferenceId(index, used)
    }
}

private fun AgentTimelineItem.withReferenceId(
    index: Int,
    used: MutableSet<String>,
): AgentTimelineItem {
    var attempt = index
    while (true) {
        val value = shortReferenceId(id, platform, attempt)
        if (used.add(value)) {
            return copy(referenceId = value)
        }
        attempt += 1
    }
}

private fun shortReferenceId(
    id: String,
    platform: String?,
    salt: Int,
): String {
    val hash = "$platform:$id:$salt".hashCode() and Int.MAX_VALUE
    val adjective = referenceAdjectives[hash % referenceAdjectives.size]
    val noun = referenceNouns[(hash / referenceAdjectives.size) % referenceNouns.size]
    return "${adjective}_$noun"
}

private val referenceAdjectives =
    listOf(
        "amber",
        "bright",
        "calm",
        "clear",
        "cosmic",
        "crisp",
        "daring",
        "eager",
        "fancy",
        "fresh",
        "gentle",
        "golden",
        "happy",
        "lively",
        "lucky",
        "magic",
        "merry",
        "nimble",
        "quiet",
        "rapid",
        "royal",
        "silver",
        "sunny",
        "tidy",
        "urban",
        "velvet",
        "vivid",
        "warm",
        "wise",
        "zesty",
    )

private val referenceNouns =
    listOf(
        "anchor",
        "bridge",
        "cloud",
        "comet",
        "delta",
        "field",
        "forest",
        "harbor",
        "island",
        "lantern",
        "maple",
        "meadow",
        "meteor",
        "orbit",
        "paper",
        "pearl",
        "pilot",
        "pixel",
        "river",
        "signal",
        "stone",
        "summit",
        "thread",
        "tower",
        "valley",
        "violet",
        "wave",
        "window",
        "winter",
        "zephyr",
    )
