package dev.dimension.flare.ui.presenter.home

import androidx.paging.PagingData
import androidx.paging.filter
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<PagingData<UiTimelineV2>>.applyLocalTimelineFilters(
    localFilterRepository: LocalFilterRepository,
    appDataStore: AppDataStore,
    filterDuplicateComments: Boolean,
): Flow<PagingData<UiTimelineV2>> =
    flatMapLatest { pagingData ->
        combine(
            localFilterRepository.getFlow(forTimeline = true),
            appDataStore.appSettingsStore.data.map { it.localFilterConfig },
        ) { filters, localFilterConfig ->
            pagingData
                .filterLocalKeywords(filters)
                .filterDuplicateComments(
                    config = localFilterConfig,
                    enabledForList = filterDuplicateComments,
                )
        }
    }

internal fun PagingData<UiTimelineV2>.filterLocalKeywords(filters: List<UiKeywordFilter>): PagingData<UiTimelineV2> =
    if (filters.isEmpty()) {
        this
    } else {
        filter { item ->
            !filters.any { filter -> filter.matches(item.localFilterText()) }
        }
    }

internal fun UiTimelineV2.filteredByLocalKeywords(filters: List<UiKeywordFilter>): Boolean =
    filters.any { filter -> filter.matches(localFilterText()) }

internal fun RemoteLoader<UiTimelineV2>.filterDuplicateCommentPages(threshold: Int): RemoteLoader<UiTimelineV2> {
    val filter = DuplicateCommentPageFilter(threshold)
    return if (this is CacheableRemoteLoader<UiTimelineV2>) {
        val delegate = this
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String = delegate.pagingKey

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Refresh) {
                    filter.reset()
                }
                val result = delegate.load(pageSize, request)
                return result.copy(data = filter.filter(result.data))
            }
        }
    } else {
        val delegate = this
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Refresh) {
                    filter.reset()
                }
                val result = delegate.load(pageSize, request)
                return result.copy(data = filter.filter(result.data))
            }
        }
    }
}

private fun PagingData<UiTimelineV2>.filterDuplicateComments(
    config: AppSettings.LocalFilterConfig,
    enabledForList: Boolean,
): PagingData<UiTimelineV2> {
    if (!enabledForList || !config.filterDuplicateComments) {
        return this
    }
    val threshold = config.duplicateCommentThreshold.coerceAtLeast(2)
    val seen = mutableMapOf<String, Int>()
    return filter { item ->
        val key = item.duplicateCommentKey() ?: return@filter true
        val count = seen.getOrElse(key) { 0 } + 1
        seen[key] = count
        count < threshold
    }
}

private fun UiKeywordFilter.matches(text: String): Boolean {
    if (keyword.isBlank() || text.isBlank()) {
        return false
    }
    return if (isRegex) {
        runCatching {
            Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.getOrElse {
            println("LocalTimelineFilterSupport: invalid regex filter length=${keyword.length}")
            false
        }
    } else {
        text.contains(keyword, ignoreCase = true)
    }
}

private fun UiTimelineV2.localFilterText(): String =
    buildString {
        searchText?.let {
            append(it)
            append(' ')
        }
        when (this@localFilterText) {
            is UiTimelineV2.Feed -> Unit
            is UiTimelineV2.Message -> {
                user?.let { appendProfile(it) }
                when (val messageType = type) {
                    is UiTimelineV2.Message.Type.Raw -> append(messageType.content)
                    is UiTimelineV2.Message.Type.Localized -> append(messageType.data.name)
                    is UiTimelineV2.Message.Type.Unknown -> append(messageType.rawType)
                }
            }
            is UiTimelineV2.Post -> {
                user?.let { appendProfile(it) }
                quote.forEach {
                    append(it.localFilterText())
                    append(' ')
                }
                internalRepost?.let {
                    append(it.localFilterText())
                    append(' ')
                }
                parents.forEach {
                    append(it.localFilterText())
                    append(' ')
                }
            }
            is UiTimelineV2.User -> appendProfile(value)
            is UiTimelineV2.UserList -> {
                users.forEach(::appendProfile)
                post?.let {
                    append(it.localFilterText())
                }
            }
        }
    }

private fun StringBuilder.appendProfile(profile: dev.dimension.flare.ui.model.UiProfile) {
    append(profile.name.raw)
    append(' ')
    append(profile.handle.raw)
    append(' ')
    profile.description?.raw?.let {
        append(it)
        append(' ')
    }
}

private class DuplicateCommentPageFilter(
    threshold: Int,
) {
    private val threshold = threshold.coerceAtLeast(2)
    private val seen = mutableMapOf<String, Int>()

    fun reset() {
        seen.clear()
    }

    fun filter(items: List<UiTimelineV2>): List<UiTimelineV2> {
        val pageCounts =
            items
                .mapNotNull { it.duplicateCommentKey() }
                .groupingBy { it }
                .eachCount()
        val filtered =
            items.filter { item ->
                val key = item.duplicateCommentKey() ?: return@filter true
                val previousCount = seen.getOrElse(key) { 0 }
                val pageCount = pageCounts.getOrElse(key) { 0 }
                val totalCount = previousCount + pageCount
                when {
                    previousCount == 0 && pageCount >= threshold -> false
                    previousCount > 0 && totalCount >= threshold -> false
                    else -> true
                }
            }
        pageCounts.forEach { (key, pageCount) ->
            seen[key] = seen.getOrElse(key) { 0 } + pageCount
        }
        return filtered
    }
}

private fun UiTimelineV2.duplicateCommentKey(): String? {
    val text =
        when (this) {
            is UiTimelineV2.Post -> content.innerText
            else -> return null
        }.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    return text.takeIf { it.isNotEmpty() }
}
