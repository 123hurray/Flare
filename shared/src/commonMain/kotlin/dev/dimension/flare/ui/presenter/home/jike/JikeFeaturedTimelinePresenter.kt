package dev.dimension.flare.ui.presenter.home.jike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Immutable
internal data class JikeFeaturedTimelineState(
    val timelineItems: Flow<List<UiTimelineV2>> = emptyFlow(),
    val isRefreshing: Boolean = false,
    val isLoadMore: Boolean = false,
    val error: Throwable? = null,
    val onRefresh: suspend () -> Unit = {},
    val onLoadMore: suspend () -> Unit = {},
)

internal class JikeFeaturedTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    @Composable
    override fun body(): JikeFeaturedTimelineState = JikeFeaturedTimelineState()
}
