package dev.dimension.flare.data.network.dongqiudi

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.dongqiudiApiHost
import dev.dimension.flare.model.dongqiudiWebHost

internal data object DongqiudiPlatformDetector : PlatformDetector {
    override val priority: Int = 95

    override suspend fun detect(host: String): NodeData? {
        val normalized = host.lowercase()
        if (normalized !in setOf(dongqiudiWebHost, "dongqiudi.com", dongqiudiApiHost, "n.dongqiudi.com")) {
            return null
        }
        return NodeData(
            host = dongqiudiWebHost,
            platformType = PlatformType.Dongqiudi,
            software = PlatformType.Dongqiudi.name,
            compatibleMode = false,
        )
    }
}
