package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExternalLinkSettingsScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository = koinInject(),
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val appSettings by settingsRepository.appSettings.collectAsState(AppSettings(""))
    val scope = rememberCoroutineScope()
    val config = appSettings.externalLinkConfig
    val items =
        listOf(
            ExternalLinkItem("微博", config.weibo) { copy(weibo = it) },
            ExternalLinkItem("X", config.x) { copy(x = it) },
            ExternalLinkItem("小红书", config.xiaohongshu) { copy(xiaohongshu = it) },
            ExternalLinkItem("Instagram", config.instagram) { copy(instagram = it) },
            ExternalLinkItem("即刻", config.jike) { copy(jike = it) },
            ExternalLinkItem("懂球帝", config.dongqiudi) { copy(dongqiudi = it) },
            ExternalLinkItem("知乎", config.zhihu) { copy(zhihu = it) },
        )

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_external_links_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_external_links_title))
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_external_links_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            items.forEachIndexed { index, item ->
                SegmentedListItem(
                    onClick = {
                        scope.launch {
                            settingsRepository.updateAppSettings {
                                copy(externalLinkConfig = item.update(config, !item.enabled))
                            }
                        }
                    },
                    shapes =
                        if (index == items.lastIndex) {
                            ListItemDefaults.last()
                        } else {
                            ListItemDefaults.item()
                        },
                    content = {
                        Text(text = item.title)
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_external_links_platform_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = item.enabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    settingsRepository.updateAppSettings {
                                        copy(externalLinkConfig = item.update(config, checked))
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

private data class ExternalLinkItem(
    val title: String,
    val enabled: Boolean,
    val update: AppSettings.ExternalLinkConfig.(Boolean) -> AppSettings.ExternalLinkConfig,
)
