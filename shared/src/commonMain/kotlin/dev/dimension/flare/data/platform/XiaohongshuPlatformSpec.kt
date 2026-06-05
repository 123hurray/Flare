package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.xiaohongshu.XiaohongshuPlatformDetector
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object XiaohongshuPlatformSpec : PlatformSpec {
    override val type = PlatformType.Xiaohongshu
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Xiaohongshu",
            icon = UiIcon.Bookmark,
        )
    override val detector: PlatformDetector = XiaohongshuPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        (
            listOf(xiaohongshuWebHost, "xiaohongshu.com")
                .flatMap { webHost ->
                    listOf(
                        DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("https://$webHost/explore/{id}?xsec_token={xsec_token}&xsec_source={xsec_source}&type={type}")),
                        DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("https://$webHost/discovery/item/{id}?xsec_token={xsec_token}&xsec_source={xsec_source}&type={type}")),
                        DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$webHost/user/profile/{handle}")),
                    )
                } +
                listOf(
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://item/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://item/discovery.{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://video_feed/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://video_feed/discovery.{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://guang/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://guang/discovery.{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://multi_note/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://multi_note/discovery.{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.XiaohongshuPost.serializer(), Url("xhsdiscover://comments/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("xhsdiscover://user/{handle}")),
                    DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("xhsdiscover://user/user.{handle}")),
                )
        ).toImmutableList()

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                accountKey = accountKey,
                title = "小红书",
                icon = IconType.Material(UiIcon.Bookmark),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> = persistentListOf()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
