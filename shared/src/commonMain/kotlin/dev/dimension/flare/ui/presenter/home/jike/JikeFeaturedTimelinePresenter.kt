package dev.dimension.flare.ui.presenter.home.jike

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class JikeFeaturedTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    override val loader: Flow<RemoteLoader<UiTimelineV2>> = emptyFlow()
}
