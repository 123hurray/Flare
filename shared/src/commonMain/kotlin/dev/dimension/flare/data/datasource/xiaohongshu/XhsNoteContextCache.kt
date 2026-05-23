package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteContext

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

internal object XhsNoteContextCache {
    private val values = mutableMapOf<String, XhsNoteContext>()

    fun put(context: XhsNoteContext) {
        values[context.noteId] = context
    }

    fun get(noteId: String): XhsNoteContext? = values[noteId]
}
