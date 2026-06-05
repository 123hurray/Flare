package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LocalFilterPresenter :
    PresenterBase<LocalFilterState>(),
    KoinComponent {
    private val repository by inject<LocalFilterRepository>()
    private val settingsRepository by inject<SettingsRepository>()

    @Composable
    override fun body(): LocalFilterState {
        val all by remember { repository.getAllFlow() }.collectAsUiState()
        val appSettings by remember { settingsRepository.appSettings }.collectAsUiState()
        val scope = rememberCoroutineScope()
        return object : LocalFilterState {
            override val items = all
            override val filterDuplicateComments =
                appSettings
                    .takeSuccess()
                    ?.localFilterConfig
                    ?.filterDuplicateComments ?: false
            override val filterAiComments =
                appSettings
                    .takeSuccess()
                    ?.localFilterConfig
                    ?.filterAiComments ?: false

            override fun add(item: UiKeywordFilter) {
                repository.add(
                    keyword = item.keyword,
                    isRegex = item.isRegex,
                    forTimeline = item.forTimeline,
                    forNotification = item.forNotification,
                    forSearch = item.forSearch,
                    expiredAt = item.expiredAt,
                )
            }

            override fun delete(keyword: String) {
                repository.delete(keyword)
            }

            override fun update(item: UiKeywordFilter) {
                repository.update(
                    keyword = item.keyword,
                    isRegex = item.isRegex,
                    forTimeline = item.forTimeline,
                    forNotification = item.forNotification,
                    forSearch = item.forSearch,
                    expiredAt = item.expiredAt,
                )
            }

            override fun setFilterDuplicateComments(value: Boolean) {
                scope.launch {
                    settingsRepository.updateAppSettings {
                        copy(
                            localFilterConfig =
                                localFilterConfig.copy(
                                    filterDuplicateComments = value,
                                ),
                        )
                    }
                }
            }

            override fun setFilterAiComments(value: Boolean) {
                scope.launch {
                    settingsRepository.updateAppSettings {
                        copy(
                            localFilterConfig =
                                localFilterConfig.copy(
                                    filterAiComments = value,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Immutable
public interface LocalFilterState {
    public val items: UiState<ImmutableList<UiKeywordFilter>>
    public val filterDuplicateComments: Boolean
    public val filterAiComments: Boolean

    public fun delete(keyword: String)

    public fun add(item: UiKeywordFilter)

    public fun update(item: UiKeywordFilter)

    public fun setFilterDuplicateComments(value: Boolean)

    public fun setFilterAiComments(value: Boolean)
}
