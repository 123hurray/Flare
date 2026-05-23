package dev.dimension.flare.data.network.xiaohongshu

public data class XhsSigningRequest(
    val method: String,
    val path: String,
    val body: String,
    val cookies: Map<String, String>,
)

public fun interface XhsRuntimeSigner {
    public suspend fun sign(request: XhsSigningRequest): Map<String, String>
}

public object XhsSigningRuntime {
    private var signer: XhsRuntimeSigner? = null

    public val isAvailable: Boolean
        get() = signer != null

    public fun install(signer: XhsRuntimeSigner) {
        this.signer = signer
    }

    public suspend fun sign(request: XhsSigningRequest): Map<String, String>? = signer?.sign(request)

    internal fun resetForTests() {
        signer = null
    }
}
