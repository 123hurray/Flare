package dev.dimension.flare.data.network.zhihu

public data class ZhihuSearchWebRequest(
    val url: String,
    val cookies: Map<String, String>,
)

public fun interface ZhihuSearchWebRuntime {
    public suspend fun requestJson(request: ZhihuSearchWebRequest): String
}

public object ZhihuSearchRuntime {
    private var runtime: ZhihuSearchWebRuntime? = null

    public val isAvailable: Boolean
        get() = runtime != null

    public fun install(runtime: ZhihuSearchWebRuntime) {
        this.runtime = runtime
    }

    public suspend fun requestJson(request: ZhihuSearchWebRequest): String? = runtime?.requestJson(request)
}
