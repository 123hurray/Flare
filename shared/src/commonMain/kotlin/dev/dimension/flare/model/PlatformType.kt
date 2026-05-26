package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Immutable
@Serializable
public enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,

    VVo,
    Nostr,
    Jike,
    Xiaohongshu,
    Instagram,
    Dongqiudi,
    Zhihu,
}

@Immutable
public data class PlatformTypeMetadata(
    val displayName: String,
    val icon: UiIcon,
)

public val xqtOldHost: String =
    buildString {
        append(Base64.decode("dHc=").decodeToString())
        append(Base64.decode("aXR0").decodeToString())
        append(Base64.decode("ZXI=").decodeToString())
        append(Base64.decode("LmNvbQ==").decodeToString())
    }

public val xqtHost: String =
    buildString {
        append("x")
        append(".com")
    }

public val vvo: String =
    buildString {
        append(Base64.decode("d2Vp").decodeToString())
        append(Base64.decode("Ym8=").decodeToString())
    }

public val vvoHost: String =
    buildString {
        append(Base64.decode("bS53").decodeToString())
        append(Base64.decode("ZWli").decodeToString())
        append(Base64.decode("by5jbg==").decodeToString())
    }

public val vvoHostShort: String =
    buildString {
        append(vvo)
        append(Base64.decode("LmNu").decodeToString())
    }

public val vvoHostLong: String =
    buildString {
        append(Base64.decode("d2Vp").decodeToString())
        append(Base64.decode("Ym8uY29t").decodeToString())
    }

public const val jikeApiHost: String = "api.ruguoapp.com"
public const val jikeWebHost: String = "web.okjike.com"

public const val xiaohongshuApiHost: String = "edith.xiaohongshu.com"
public const val xiaohongshuWebHost: String = "www.xiaohongshu.com"
public const val xiaohongshuExploreUrl: String = "https://www.xiaohongshu.com/explore"
public const val xiaohongshuLoginUrl: String = "https://www.xiaohongshu.com/login"

public const val instagramWebHost: String = "www.instagram.com"
public const val instagramWebUrl: String = "https://www.instagram.com/"
public const val instagramApiHost: String = "i.instagram.com"

public const val dongqiudiApiHost: String = "api.dongqiudi.com"
public const val dongqiudiWebHost: String = "www.dongqiudi.com"

public const val zhihuApiHost: String = "api.zhihu.com"
public const val zhihuWebHost: String = "www.zhihu.com"

/**
 * Jike API endpoint identifier (from open-jike/jike-sdk)
 */
public const val jikeEndpointId: String = "4653BFCE-9D54-471C-809C-422AC240DA7B"
