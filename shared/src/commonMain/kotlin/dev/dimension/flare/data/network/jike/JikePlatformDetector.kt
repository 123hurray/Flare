package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

/**
 * Platform detector for Jike (即刻).
 * Jike is a fixed-domain platform, so detection is straightforward.
 */
internal object JikePlatformDetector : PlatformDetector {
    override val type: PlatformType = PlatformType.Jike

    override suspend fun detect(url: String): Boolean = false

    override fun isMatch(url: String): Boolean = false
}
