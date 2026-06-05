package dev.dimension.flare.common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.dimension.flare.data.datastore.model.AppSettings

internal object ExternalLinkAliasManager {
    private val aliases =
        listOf(
            Alias("ExternalLinkWeiboActivity") { it.weibo },
            Alias("ExternalLinkXActivity") { it.x },
            Alias("ExternalLinkXiaohongshuActivity") { it.xiaohongshu },
            Alias("ExternalLinkInstagramActivity") { it.instagram },
            Alias("ExternalLinkJikeActivity") { it.jike },
            Alias("ExternalLinkDongqiudiActivity") { it.dongqiudi },
            Alias("ExternalLinkZhihuActivity") { it.zhihu },
        )

    fun sync(
        context: Context,
        config: AppSettings.ExternalLinkConfig,
    ) {
        aliases.forEach { alias ->
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.${alias.className}"),
                if (alias.enabled(config)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private data class Alias(
        val className: String,
        val enabled: (AppSettings.ExternalLinkConfig) -> Boolean,
    )
}
