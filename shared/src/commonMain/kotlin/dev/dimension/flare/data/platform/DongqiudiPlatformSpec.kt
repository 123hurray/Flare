package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.dongqiudi.DongqiudiDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.network.dongqiudi.DongqiudiPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.dongqiudiWebHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object DongqiudiPlatformSpec : PlatformSpec {
    override val type = PlatformType.Dongqiudi
    override val metadata =
        PlatformTypeMetadata(
            displayName = "懂球帝",
            icon = UiIcon.World,
        )
    override val detector: PlatformDetector = DongqiudiPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/article/{id}")),
        )

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                account = AccountType.Specific(accountKey),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("懂球帝"),
                        icon = IconType.Material(UiIcon.World),
                    ),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> = persistentListOf()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = DongqiudiDataSource(MicroBlogKey("anonymous", dongqiudiWebHost))
}
