package dev.dimension.flare.ui.presenter.home.zhihu

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.zhihu.ZhihuDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ZhihuDailyTimelinePresenter(
    private val accountType: AccountType,
    private val date: String?,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            require(service is ZhihuDataSource)
            service.dailyTimeline(date)
        }
    }
}
