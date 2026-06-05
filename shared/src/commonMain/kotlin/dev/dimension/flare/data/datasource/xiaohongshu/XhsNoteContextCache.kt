package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteContext
import io.ktor.http.decodeURLPart

public fun cacheXhsNoteContext(
    noteId: String,
    xsecToken: String?,
    xsecSource: String?,
) {
    val token = xsecToken?.takeIf { it.isNotBlank() } ?: return
    XhsNoteContextCache.put(
        XhsNoteContext(
            noteId = noteId,
            xsecToken = token,
            xsecSource = xsecSource?.takeIf { it.isNotBlank() } ?: "pc_search",
        ),
    )
}

internal data class XhsNoteStatusContext(
    val noteId: String,
    val xsecToken: String? = null,
    val xsecSource: String? = null,
) {
    fun toNoteContext(): XhsNoteContext? {
        val token = xsecToken?.takeIf { it.isNotBlank() } ?: return null
        return XhsNoteContext(
            noteId = noteId,
            xsecToken = token,
            xsecSource = xsecSource?.takeIf { it.isNotBlank() } ?: "pc_search",
        )
    }
}

internal fun String.xhsNoteStatusContext(): XhsNoteStatusContext {
    val noteId = substringBefore('?').takeIf { it.isNotBlank() } ?: this
    val query = substringAfter('?', missingDelimiterValue = "")
    val params =
        query
            .split('&')
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull {
                val key = it.substringBefore('=').takeIf { key -> key.isNotBlank() } ?: return@mapNotNull null
                val value = it.substringAfter('=', missingDelimiterValue = "").decodeURLPart()
                key to value
            }.toMap()
    return XhsNoteStatusContext(
        noteId = noteId,
        xsecToken = params["xsec_token"]?.takeIf { it.isNotBlank() },
        xsecSource = params["xsec_source"]?.takeIf { it.isNotBlank() },
    )
}

internal object XhsNoteContextCache {
    private val values = mutableMapOf<String, XhsNoteContext>()

    fun put(context: XhsNoteContext) {
        values[context.noteId] = context
    }

    fun get(noteId: String): XhsNoteContext? = values[noteId]
}
