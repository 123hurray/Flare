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
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
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
    val seen = mutableSetOf<String>()
    return filter { item ->
        val key = item.duplicateCommentKey() ?: return@filter true
        seen.add(key)
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
    @Suppress("UNUSED_PARAMETER") threshold: Int,
) {
    private val seen = mutableSetOf<String>()

    fun reset() {
        seen.clear()
    }

    fun filter(items: List<UiTimelineV2>): List<UiTimelineV2> {
        return items.filter { item ->
            val key = item.duplicateCommentKey() ?: return@filter true
            seen.add(key)
        }
    }
}

private fun UiTimelineV2.duplicateCommentKey(): String? {
    val content =
        when (this) {
            is UiTimelineV2.Post -> content
            else -> return null
        }
    val normalizedText = content.normalizeDuplicateCommentContent()
    return when {
        normalizedText.isNotEmpty() -> normalizedText
        content.isEmpty -> null
        else -> EmptyNormalizedCommentKey
    }
}

private fun UiRichText.normalizeDuplicateCommentContent(): String =
    renderRuns
        .joinToString(separator = "") { content ->
            when (content) {
                is RenderContent.BlockImage -> ""
                is RenderContent.Text ->
                    content.runs.joinToString(separator = "") { run ->
                        when (run) {
                            is RenderRun.Image -> ""
                            is RenderRun.Text -> run.text
                        }
                    }
            }
        }.normalizeDuplicateCommentText()

internal fun String.normalizeDuplicateCommentText(): String {
    val withoutBracketEmoji = replace(Regex("""\[[^\[\]\s]{1,8}]"""), "")
    val withoutPunctuation = buildString {
        var index = 0
        while (index < withoutBracketEmoji.length) {
            val char = withoutBracketEmoji[index]
            val nextChar = withoutBracketEmoji.getOrNull(index + 1)
            val codePoint = char.toCodePointOrNull(nextChar)
            if (char.isKeycapBase() && withoutBracketEmoji.hasKeycapSequenceAt(index)) {
                index += withoutBracketEmoji.keycapSequenceLengthAt(index)
                continue
            }
            if (codePoint != null) {
                if (!codePoint.isEmojiCodePoint()) {
                    append(char)
                    append(nextChar)
                }
                index += 2
                continue
            }
            when {
                char.isWhitespace() -> Unit
                char.isCommonPunctuation() -> Unit
                char.code.isEmojiCodePoint() -> Unit
                else -> append(char.lowercaseChar())
            }
            index += 1
        }
    }
    val withoutParticles = buildString {
        withoutPunctuation.forEach { char ->
            if (!char.isCommonChineseModalParticle()) {
                append(char)
            }
        }
    }
    return withoutParticles.ifEmpty { withoutPunctuation }
}

private const val EmptyNormalizedCommentKey = "\u0000empty-normalized-comment"

private fun Char.toCodePointOrNull(nextChar: Char?): Int? {
    val high = code
    val low = nextChar?.code ?: return null
    if (high !in 0xD800..0xDBFF || low !in 0xDC00..0xDFFF) {
        return null
    }
    return 0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
}

private fun Char.isKeycapBase(): Boolean =
    this in '0'..'9' || this == '#' || this == '*'

private fun String.hasKeycapSequenceAt(index: Int): Boolean {
    val nextIndex =
        when (getOrNull(index + 1)?.code) {
            0xFE0E, 0xFE0F -> index + 2
            else -> index + 1
        }
    return getOrNull(nextIndex)?.code == 0x20E3
}

private fun String.keycapSequenceLengthAt(index: Int): Int =
    when (getOrNull(index + 1)?.code) {
        0xFE0E, 0xFE0F -> 3
        else -> 2
    }

private fun Int.isEmojiCodePoint(): Boolean =
    when (this) {
        in 0x1F000..0x1FAFF,
        in 0x2600..0x27BF,
        in 0x2300..0x23FF,
        in 0x2B00..0x2BFF,
        in 0xFE00..0xFE0F,
        0x00A9,
        0x00AE,
        0x200D,
        0x20E3,
        0x3030,
        0x303D,
        0x3297,
        0x3299,
        -> true
        else -> false
    }

private fun Char.isCommonPunctuation(): Boolean =
    when (this) {
        '.', ',', '!', '?', ':', ';', '\'', '"', '`',
        '~', '-', '_', '+', '=', '*', '/', '\\', '|',
        '@', '#', '$', '%', '^', '&',
        '(', ')', '[', ']', '{', '}', '<', '>',
        '。', '，', '！', '？', '：', '；', '、', '·',
        '…', '—', '～', '《', '》', '〈', '〉', '「', '」',
        '『', '』', '（', '）', '【', '】', '〔', '〕',
        '“', '”', '‘', '’', '￥',
        -> true
        else -> false
    }

private fun Char.isCommonChineseModalParticle(): Boolean =
    this in chineseModalParticles

private val chineseModalParticles =
    setOf(
        '了', '啦', '喽', '咯', '啰', '咧',
        '啊', '呀', '哇', '呐', '呢', '嘛',
        '吗', '么', '吧', '罢',
        '哎', '唉', '哟', '呦', '哦', '噢',
        '额', '呃', '嗯', '恩', '诶', '欸',
        '得', '地', '的', '着', '过',
    )
