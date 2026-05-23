package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xiaohongshuWebHost

internal data object XiaohongshuPlatformDetector : PlatformDetector {
    override val priority: Int = 95

    override suspend fun detect(host: String): NodeData? {
        val normalized = host.lowercase()
        if (normalized != xiaohongshuWebHost && !normalized.endsWith(".xiaohongshu.com")) {
            return null
        }
        return NodeData(
            host = xiaohongshuWebHost,
            platformType = PlatformType.Xiaohongshu,
            software = PlatformType.Xiaohongshu.name,
            compatibleMode = false,
        )
    }
}
