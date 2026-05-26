package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuApiHost
import dev.dimension.flare.model.zhihuWebHost

internal data object ZhihuPlatformDetector : PlatformDetector {
    override val priority: Int = 95

    override suspend fun detect(host: String): NodeData? {
        val normalized = host.lowercase()
        if (normalized !in setOf(zhihuWebHost, "zhihu.com", zhihuApiHost)) {
            return null
        }
        return NodeData(
            host = zhihuWebHost,
            platformType = PlatformType.Zhihu,
            software = PlatformType.Zhihu.name,
            compatibleMode = false,
        )
    }
}
