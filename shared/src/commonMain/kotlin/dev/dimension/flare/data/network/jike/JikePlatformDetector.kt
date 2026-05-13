package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector

internal object JikePlatformDetector : PlatformDetector {
    override suspend fun detect(host: String): NodeData? = null
}
