package dev.dimension.flare.ui.presenter.home.jike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.jike.JikeDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineItem
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Immutable
internal data class JikeFeaturedTimelineState(
    val timelineItems: Flow<List<UiTimelineItem>> = emptyFlow(),
    val isRefreshing: Boolean = false,
    val isLoadMore: Boolean = false,
    val error: Throwable? = null,
    val onRefresh: suspend () -> Unit = {},
    val onLoadMore: suspend () -> Unit = {},
)

internal class JikeFeaturedTimelinePresenter(
    private val accountType: AccountType,
) : PresenterBase<JikeFeaturedTimelineState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): JikeFeaturedTimelineState {
        return JikeFeaturedTimelineState()
    }
}
