package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class LogStatusHistoryPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
    private val status: UiTimelineV2? = null,
) : PresenterBase<LogStatusHistoryPresenter.State>(),
    KoinComponent {
    public companion object {
        public const val FOOTPRINTS_PAGING_KEY: String = "status_history"
        public const val FAVORITES_PAGING_KEY: String = "global_status_favorites"

        @Deprecated("Use FOOTPRINTS_PAGING_KEY instead.")
        public const val PAGING_KEY: String = FOOTPRINTS_PAGING_KEY
    }

    private val cacheDatabase: CacheDatabase by inject()

    @Immutable
    public interface State

    @Composable
    override fun body(): State {
        LaunchedEffect(statusKey, accountType, status) {
            if (statusKey.id.startsWith("comment:")) {
                return@LaunchedEffect
            }
            cacheDatabase.connect {
                val current = status
                if (current != null) {
                    saveToDatabase(
                        cacheDatabase,
                        listOf(
                            TimelinePagingMapper.toDb(
                                data = current,
                                pagingKey = FOOTPRINTS_PAGING_KEY,
                            ),
                        ),
                    )
                } else {
                    cacheDatabase.pagingTimelineDao().insertAll(
                        listOf(
                            DbPagingTimeline(
                                statusId = DbStatus.createId(accountType as DbAccountType, statusKey),
                                pagingKey = FOOTPRINTS_PAGING_KEY,
                                sortId = Clock.System.now().toEpochMilliseconds(),
                            ),
                        ),
                    )
                }
            }
        }

        return object : State {
        }
    }
}
