package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteContext
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

internal object XhsDetailPostCache {
    private val mutex = Mutex()
    private val cached = LinkedHashMap<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<UiTimelineV2.Post?>>()
    private val ttl = 5.minutes
    private const val MAX_SIZE = 64

    fun key(
        accountKey: MicroBlogKey,
        noteId: String,
        context: XhsNoteContext?,
    ): String = listOf(
        accountKey.toString(),
        noteId,
        context?.xsecSource.orEmpty(),
        context?.xsecToken.orEmpty(),
    ).joinToString("|")

    suspend fun getOrLoad(
        key: String,
        load: suspend () -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post? {
        val now = Clock.System.now()
        var pending: CompletableDeferred<UiTimelineV2.Post?>? = null
        var owner: CompletableDeferred<UiTimelineV2.Post?>? = null
        mutex.withLock {
            cached[key]
                ?.takeIf { now - it.createdAt < ttl }
                ?.let {
                    return it.value
                }
            pending = inFlight[key]
            if (pending == null) {
                owner = CompletableDeferred()
                inFlight[key] = owner
            }
        }

        pending?.let {
            return it.await()
        }

        val deferred = owner ?: error("Xiaohongshu detail cache owner is missing")
        return try {
            val value = load()
            mutex.withLock {
                cached[key] = CacheEntry(value, Clock.System.now())
                while (cached.size > MAX_SIZE) {
                    val firstKey = cached.keys.firstOrNull() ?: break
                    cached.remove(firstKey)
                }
                inFlight.remove(key)?.complete(value)
            }
            value
        } catch (t: Throwable) {
            mutex.withLock {
                inFlight.remove(key)?.completeExceptionally(t)
            }
            throw t
        }
    }

    private data class CacheEntry(
        val value: UiTimelineV2.Post?,
        val createdAt: kotlin.time.Instant,
    )
}
