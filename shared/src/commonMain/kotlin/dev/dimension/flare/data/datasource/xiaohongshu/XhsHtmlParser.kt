package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteCard

internal object XhsHtmlParser {
    fun parseNote(
        noteId: String,
        html: String,
    ): XhsNoteCard? {
        val title = findJsonString(html, "title") ?: findMeta(html, "og:title")
        val desc = findJsonString(html, "desc") ?: findMeta(html, "description")
        if (title.isNullOrBlank() && desc.isNullOrBlank()) return null
        return XhsNoteCard(
            noteId = noteId,
            displayTitle = title.orEmpty(),
            desc = desc.orEmpty(),
        )
    }

    private fun findJsonString(
        input: String,
        key: String,
    ): String? =
        Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")

    private fun findMeta(
        input: String,
        name: String,
    ): String? =
        Regex("""<meta[^>]+(?:property|name)=["']$name["'][^>]+content=["']([^"']+)["']""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
}
