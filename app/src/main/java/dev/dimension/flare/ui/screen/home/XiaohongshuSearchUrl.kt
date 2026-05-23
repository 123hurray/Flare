package dev.dimension.flare.ui.screen.home

import android.net.Uri

internal data class XiaohongshuNoteUrl(
    val noteId: String,
    val xsecToken: String?,
    val xsecSource: String?,
)

internal val xiaohongshuNoteUrlRegex =
    Regex("""https?://(?:www\.)?xiaohongshu\.com/(?:discovery/)?(?:item|explore)/([0-9a-fA-F]{24})""")

internal fun xiaohongshuNoteFromUrl(value: String): XiaohongshuNoteUrl? {
    val trimmed = value.trim()
    val noteId = xiaohongshuNoteUrlRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
    return XiaohongshuNoteUrl(
        noteId = noteId,
        xsecToken = uri?.getQueryParameter("xsec_token"),
        xsecSource = uri?.getQueryParameter("xsec_source"),
    )
}

internal fun xiaohongshuNoteIdFromUrl(value: String): String? =
    xiaohongshuNoteFromUrl(value)?.noteId
