package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Instagram
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.instagram.InstagramPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object InstagramPlatformSpec : PlatformSpec {
    override val type = PlatformType.Instagram
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Instagram",
            icon = UiIcon.Instagram,
        )
    override val detector: PlatformDetector = InstagramPlatformDetector

    override fun agreementUrl(host: String): String? = "https://help.instagram.com/581066165581870"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        listOf(instagramWebHost, "instagram.com")
            .flatMap { webHost ->
                listOf(
                    DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$webHost/{handle}")),
                    DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$webHost/p/{id}")),
                    DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$webHost/reel/{id}")),
                )
            }.toImmutableList()

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            Instagram.FollowingTimelineTabItem(
                account = AccountType.Specific(accountKey),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("关注"),
                        icon = IconType.Material(UiIcon.Instagram),
                    ),
            ),
            Instagram.RecommendedTimelineTabItem(
                account = AccountType.Specific(accountKey),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("推荐"),
                        icon = IconType.Material(UiIcon.Home),
                    ),
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
