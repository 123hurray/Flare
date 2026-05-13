package dev.dimension.flare.data.network.jike

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.jikeApiHost
import dev.dimension.flare.model.jikeWebHost

internal data object JikePlatformDetector : PlatformDetector {
    override val priority: Int = 95

    override suspend fun detect(host: String): NodeData? {
        val aliases = listOf(jikeWebHost, jikeApiHost, "okjike.com", "www.okjike.com", "ruguoapp.com")
        if (!aliases.any { it.equals(host, ignoreCase = true) }) {
            return null
        }
        return NodeData(
            host = jikeWebHost,
            platformType = PlatformType.Jike,
            software = PlatformType.Jike.name,
            compatibleMode = false,
        )
    }
}
