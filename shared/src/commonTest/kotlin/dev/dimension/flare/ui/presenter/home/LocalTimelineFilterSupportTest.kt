package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class LocalTimelineFilterSupportTest {
    @Test
    fun normalizeDuplicateCommentText_removesWhitespaceBracketEmojiPunctuationAndParticles() {
        assertEquals(
            "好球",
            " 好 球 了啊！！[鼓掌] ".normalizeDuplicateCommentText(),
        )
        assertEquals(
            "nba赢麻",
            "NBA，赢麻了～～[doge]".normalizeDuplicateCommentText(),
        )
        assertEquals(
            "哈哈哈",
            "哈 哈 哈！！！".normalizeDuplicateCommentText(),
        )
        assertEquals(
            "好球",
            "好球😄🔥❤️1️⃣".normalizeDuplicateCommentText(),
        )
        assertEquals(
            "",
            "[鼓掌]🔥❤️".normalizeDuplicateCommentText(),
        )
    }

    @Test
    fun filterDuplicateCommentPages_keepsOnlyFirstNormalizedDuplicateAcrossPages() =
        runTest {
            val pages =
                listOf(
                    listOf(
                        comment("1", "好球了！！[鼓掌]"),
                        comment("2", "好 球啊"),
                        comment("3", "不一样"),
                    ),
                    listOf(
                        comment("4", "好球吗？"),
                        comment("5", "不一样呀"),
                        comment("6", "新评论"),
                    ),
                )
            val loader = PageLoader(pages).filterDuplicateCommentPages(threshold = 3)

            val firstPage = loader.load(pageSize = 20, request = PagingRequest.Refresh)
            val secondPage = loader.load(pageSize = 20, request = PagingRequest.Append("1"))

            assertEquals(listOf("1", "3"), firstPage.data.map { it.statusKey.id })
            assertEquals(listOf("6"), secondPage.data.map { it.statusKey.id })
        }

    @Test
    fun filterDuplicateCommentPages_deduplicatesCommentsThatNormalizeToEmpty() =
        runTest {
            val loader =
                PageLoader(
                    listOf(
                        listOf(
                            comment("1", "[鼓掌]"),
                            comment("2", "🔥❤️"),
                            comment("3", "正文🔥"),
                        ),
                        listOf(
                            comment("4", "！！！"),
                            comment("5", "正文"),
                        ),
                    ),
                ).filterDuplicateCommentPages(threshold = 3)

            assertEquals(
                listOf("1", "3"),
                loader.load(pageSize = 20, request = PagingRequest.Refresh).data.map { it.statusKey.id },
            )
            assertEquals(
                emptyList(),
                loader.load(pageSize = 20, request = PagingRequest.Append("1")).data.map { it.statusKey.id },
            )
        }

    @Test
    fun filterDuplicateCommentPages_deduplicatesInlineImageEmojiComments() =
        runTest {
            val loader =
                PageLoader(
                    listOf(
                        listOf(
                            comment("1", inlineEmojiContent("抱抱")),
                            comment("2", inlineEmojiContent("抱抱", "抱抱", "抱抱")),
                            comment("3", inlineEmojiContent("抱抱", "鼓掌")),
                            comment("4", inlineEmojiContent("抱抱", text = "好球")),
                            comment("5", "好球"),
                        ),
                    ),
                ).filterDuplicateCommentPages(threshold = 3)

            assertEquals(
                listOf("1", "4"),
                loader.load(pageSize = 20, request = PagingRequest.Refresh).data.map { it.statusKey.id },
            )
        }

    @Test
    fun filterDuplicateCommentPages_refreshResetsSeenComments() =
        runTest {
            val loader =
                PageLoader(
                    listOf(
                        listOf(comment("1", "可以的")),
                        listOf(comment("2", "可以了的啊")),
                    ),
                ).filterDuplicateCommentPages(threshold = 3)

            assertEquals(
                listOf("1"),
                loader.load(pageSize = 20, request = PagingRequest.Refresh).data.map { it.statusKey.id },
            )
            assertEquals(
                emptyList(),
                loader.load(pageSize = 20, request = PagingRequest.Append("1")).data.map { it.statusKey.id },
            )
            assertEquals(
                listOf("1"),
                loader.load(pageSize = 20, request = PagingRequest.Refresh).data.map { it.statusKey.id },
            )
        }

    private class PageLoader(
        private val pages: List<List<UiTimelineV2>>,
    ) : RemoteLoader<UiTimelineV2> {
        private var appendIndex = 1

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> =
            when (request) {
                PagingRequest.Refresh -> {
                    appendIndex = 1
                    PagingResult(data = pages.firstOrNull().orEmpty(), nextKey = "1")
                }
                is PagingRequest.Append -> {
                    val data = pages.getOrNull(appendIndex).orEmpty()
                    appendIndex += 1
                    PagingResult(data = data, nextKey = appendIndex.toString())
                }
                is PagingRequest.Prepend -> PagingResult(endOfPaginationReached = true)
            }
    }

    private fun comment(
        id: String,
        text: String,
    ): UiTimelineV2.Post =
        comment(id, text.toUiPlainText())

    private fun comment(
        id: String,
        content: dev.dimension.flare.ui.render.UiRichText,
    ): UiTimelineV2.Post {
        val accountKey = MicroBlogKey("account", "example.com")
        return UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = content,
            actions = persistentListOf<ActionMenu>(),
            poll = null,
            statusKey = MicroBlogKey(id, "example.com"),
            card = null,
            createdAt = Clock.System.now().toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )
    }

    private fun inlineEmojiContent(
        vararg emojiAlts: String,
        text: String = "",
    ) = uiRichTextOf(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs =
                        (
                            emojiAlts.map { alt ->
                                RenderRun.Image(
                                    url = "https://example.com/$alt.png",
                                    alt = alt,
                                )
                            } +
                                listOfNotNull(
                                    text.takeIf { it.isNotEmpty() }?.let {
                                        RenderRun.Text(text = it)
                                    },
                                )
                        ).toPersistentList(),
                ),
            ),
    )
}
