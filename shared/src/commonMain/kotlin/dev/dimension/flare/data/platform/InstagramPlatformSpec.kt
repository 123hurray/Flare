package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.network.instagram.InstagramPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/{handle}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/p/{id}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/reel/{id}")),
        )

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                accountKey = accountKey,
                title = "Instagram",
                icon = IconType.Material(UiIcon.Instagram),
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
