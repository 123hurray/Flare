package dev.dimension.flare.data.network.instagram

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.instagramApiHost
import dev.dimension.flare.model.instagramWebHost

internal data object InstagramPlatformDetector : PlatformDetector {
    override val priority: Int = 95

    override suspend fun detect(host: String): NodeData? {
        val normalized = host.lowercase()
        val aliases = setOf(instagramWebHost, "instagram.com", instagramApiHost)
        if (normalized !in aliases) {
            return null
        }
        return NodeData(
            host = instagramWebHost,
            platformType = PlatformType.Instagram,
            software = PlatformType.Instagram.name,
            compatibleMode = false,
        )
    }
}
