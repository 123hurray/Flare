package dev.dimension.flare.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Weibo
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.solid.BookOpen
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.icons.Jike
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.icons.Nostr

public val PlatformType.brandIcon: ImageVector
    get() =
        when (this) {
            PlatformType.Nostr -> FontAwesomeIcons.Brands.Nostr
            PlatformType.Mastodon -> FontAwesomeIcons.Brands.Mastodon
            PlatformType.Misskey -> FontAwesomeIcons.Brands.Misskey
            PlatformType.Bluesky -> FontAwesomeIcons.Brands.Bluesky
            PlatformType.xQt -> FontAwesomeIcons.Brands.XTwitter
            PlatformType.VVo -> FontAwesomeIcons.Brands.Weibo
            PlatformType.Jike -> FontAwesomeIcons.Brands.Jike
            PlatformType.Xiaohongshu -> FontAwesomeIcons.Solid.BookOpen
            PlatformType.Instagram -> FontAwesomeIcons.Brands.Instagram
            PlatformType.Dongqiudi -> FontAwesomeIcons.Solid.BookOpen
            PlatformType.Zhihu -> FontAwesomeIcons.Solid.BookOpen
        }
