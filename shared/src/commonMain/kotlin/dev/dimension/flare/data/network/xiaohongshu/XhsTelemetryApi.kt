package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.data.network.xiaohongshu.model.XhsTelemetryEnvelope

internal class XhsTelemetryApi(
    private val enabled: Boolean = false,
) {
    val supportedHeaders: Set<String> =
        setOf(
            "X-Client-Build",
            "X-Client-Platform",
            "X-Client-Version",
            "X-Mx-ReqToken",
            "X-Requested-With",
            "X-Sign",
            "Batch",
            "request-from",
            "Biz-Type",
        )

    suspend fun collect(envelope: XhsTelemetryEnvelope): Boolean {
        if (!enabled) return false
        return false
    }

    suspend fun apm(envelope: XhsTelemetryEnvelope): Boolean {
        if (!enabled) return false
        return false
    }
}
