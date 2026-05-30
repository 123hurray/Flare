package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

public actual val httpClientEngine: HttpClientEngine =
    OkHttp.create {
        config {
            addInterceptor { chain ->
                val request = chain.request()
                val normalizedRequest =
                    if (request.url.host == "m.weibo.cn") {
                        request
                            .newBuilder()
                            .header("Accept", "application/json, text/plain, */*")
                            .build()
                    } else {
                        request
                    }
                chain.proceed(normalizedRequest)
            }
        }
    }
