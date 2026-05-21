package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Jike
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.jike.JikePlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.jikeApiHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object JikePlatformSpec : PlatformSpec {
    override val type = PlatformType.Jike
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Jike",
            icon = UiIcon.Jike,
        )
    override val detector: PlatformDetector = JikePlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                accountKey = accountKey,
                title = "jike",
                icon = IconType.Material(UiIcon.Jike),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            Jike.FeaturedTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Text("广场"),
                    icon = IconType.Mixed(UiIcon.Featured, accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
