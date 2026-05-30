package dev.dimension.flare.ui.presenter.status

import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.plainText
import dev.dimension.flare.ui.render.uiRichTextOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal fun UiTimelineV2.asFeedCardPreview(): UiTimelineV2 =
    when (this) {
        is UiTimelineV2.Post ->
            when (platformType) {
                PlatformType.Zhihu -> toCompactArticlePreview()
                else -> this
            }
        else -> this
    }

private fun UiTimelineV2.Post.toCompactArticlePreview(): UiTimelineV2.Post {
    val textBlocks =
        content
            .renderRuns
            .filterIsInstance<RenderContent.Text>()
            .filter { it.plainText().isNotBlank() }
    val title = textBlocks.firstOrNull()
    val bodyText =
        textBlocks
            .drop(if (title != null) 1 else 0)
            .joinToString("\n") { it.plainText().trim() }
            .trim()
            .takeIf { it.isNotBlank() }
            ?.ellipsize(220)
    val renderRuns =
        listOfNotNull(
            title,
            bodyText?.let {
                RenderContent.Text(
                    runs = persistentListOf(RenderRun.Text(it)),
                )
            },
        )
    if (renderRuns.isEmpty()) {
        return copy(
            images = images.take(6).toImmutableList(),
            sourceChannel = null,
        )
    }
    val raw = listOf(title?.plainText().orEmpty(), bodyText.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
    return copy(
        content =
            uiRichTextOf(
                renderRuns = renderRuns,
                raw = raw,
                innerText = raw,
                imageUrls = persistentListOf(),
                sourceLanguages = sourceLanguages,
            ),
        images = images.take(6).toImmutableList(),
        sourceChannel = null,
    )
}

private fun String.ellipsize(maxChars: Int): String =
    if (length <= maxChars) {
        this
    } else {
        take(maxChars).trimEnd() + "..."
    }
