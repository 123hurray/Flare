package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.zhihu.ZhihuPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object ZhihuPlatformSpec : PlatformSpec {
    override val type = PlatformType.Zhihu
    override val metadata =
        PlatformTypeMetadata(
            displayName = "知乎",
            icon = UiIcon.Feeds,
        )
    override val detector: PlatformDetector = ZhihuPlatformDetector

    override fun agreementUrl(host: String): String? = "https://www.zhihu.com/term/zhihu-terms"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/question/{handle}/answer/{id}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/question/{id}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://zhuanlan.zhihu.com/p/{id}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/pin/{id}")),
        )

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                account = AccountType.Specific(accountKey),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("知乎"),
                        icon = IconType.Material(UiIcon.Feeds),
                    ),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> = persistentListOf()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source requires login")
}
