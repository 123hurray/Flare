package dev.dimension.flare.data.network.zhihu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import dev.dimension.flare.model.zhihuWebHost
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val ZHIHU_WEB_SEARCH_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0"

public object ZhihuAndroidWebSearch : ZhihuSearchWebRuntime {
    private const val TAG = "ZhihuAndroidWebSearch"
    private const val READY_TIMEOUT_MS = 20_000L
    private const val FETCH_TIMEOUT_MS = 12_000L
    private const val FETCH_BODY_PREVIEW_LIMIT = 800

    private lateinit var appContext: Context
    private var activityRef: WeakReference<Activity>? = null
    private var webView: WebView? = null
    private var hostView: FrameLayout? = null
    private val mutex = Mutex()
    private val pendingFetches = ConcurrentHashMap<String, CompletableDeferred<String>>()

    public fun install(context: Context) {
        appContext = context.applicationContext
        ZhihuSearchRuntime.install(this)
    }

    public fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        val view = webView ?: ensureWebView()
        attachToActivityHost(view)
    }

    public fun detach(activity: Activity) {
        if (activityRef?.get() !== activity) return
        hostView?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
            host.removeAllViews()
        }
        webView?.destroy()
        hostView = null
        webView = null
        activityRef = null
    }

    override suspend fun requestJson(request: ZhihuSearchWebRequest): String =
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                require(::appContext.isInitialized) { "Zhihu Android Web search runtime is not installed" }
                syncCookies(request.cookies)
                val webView = ensureWebView()
                ensureSearchPageReady(webView, request.url)
                fetchInPage(webView, request.url)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val context = activityRef?.get() ?: appContext
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = ZHIHU_WEB_SEARCH_USER_AGENT
            settings.blockNetworkLoads = false
            settings.textZoom = 100
            addJavascriptInterface(FetchBridge(), "FlareZhihuFetchBridge")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String,
                    ) {
                        Log.i(TAG, "page finished url=$url")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            Log.w(TAG, "page error code=${error.errorCode} desc=${error.description}")
                        }
                    }
                }
            webView = this
            attachToActivityHost(this)
        }
    }

    private fun attachToActivityHost(webView: WebView) {
        if (webView.parent != null) return
        val activity = activityRef?.get() ?: return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val host =
            hostView ?: FrameLayout(activity).also { container ->
                container.alpha = 0f
                container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                decor.addView(container, ViewGroup.LayoutParams(1, 1))
                hostView = container
                Log.i(TAG, "attached hidden WebView host")
            }
        host.addView(webView, ViewGroup.LayoutParams(1, 1))
    }

    private fun syncCookies(cookies: Map<String, String>) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        cookies
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .forEach { (name, value) ->
                manager.setCookie("https://$zhihuWebHost", "$name=$value; Domain=.zhihu.com; Path=/")
            }
        manager.flush()
    }

    private suspend fun ensureSearchPageReady(
        webView: WebView,
        requestUrl: String,
    ) {
        val query = Uri.parse(requestUrl).getQueryParameter("q").orEmpty()
        val pageUrl = "https://$zhihuWebHost/search?type=content&q=${Uri.encode(query)}"
        if (webView.url?.startsWith(pageUrl) != true) {
            webView.loadUrl(pageUrl)
        }
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        var lastState = ""
        while (System.currentTimeMillis() < deadline) {
            val state =
                webView
                    .evaluate(
                        """
                        JSON.stringify({
                          href: location.href,
                          readyState: document.readyState,
                          hasFetch: typeof fetch,
                          title: document.title
                        })
                        """.trimIndent(),
                    ).decodeJavascriptString()
            if (state != lastState) {
                Log.i(TAG, "runtime state=$state")
                lastState = state
            }
            val loadedSearchPage =
                state.contains(""""href":"https://$zhihuWebHost/search""") ||
                    state.contains(""""href":"https://$zhihuWebHost/search?""")
            if (loadedSearchPage &&
                state.contains(""""readyState":"complete"""") &&
                state.contains(""""hasFetch":"function"""")
            ) {
                delay(2_000L)
                return
            }
            delay(250L)
        }
        Log.w(TAG, "search page not fully ready url=${webView.url} state=$lastState")
    }

    private suspend fun fetchInPage(
        webView: WebView,
        requestUrl: String,
    ): String =
        withTimeout(FETCH_TIMEOUT_MS) {
            val paths = requestUrl.toSearchPaths()
            var lastFailure: String? = null
            for (pathAndQuery in paths) {
                val payload = fetchPathInPage(webView, pathAndQuery)
                Log.i(TAG, "fetch payload=${payload.take(FETCH_BODY_PREVIEW_LIMIT)}")
                val json = JSONObject(payload)
                if (json.has("error")) {
                    lastFailure = json.optString("error")
                    Log.w(TAG, "fetch error path=$pathAndQuery error=$lastFailure")
                    continue
                }
                val status = json.optInt("status")
                val body = json.optString("body")
                val type = json.optString("type")
                val responseUrl = json.optString("responseUrl")
                Log.i(TAG, "fetch status=$status type=$type path=$pathAndQuery responseUrl=$responseUrl bodyLen=${body.length}")
                if (status in 200..299 && body.isNotBlank()) {
                    return@withTimeout body
                }
                lastFailure = "status=$status type=$type body=${body.take(FETCH_BODY_PREVIEW_LIMIT)}"
            }
            error("Zhihu web search fetch failed: $lastFailure")
        }

    private suspend fun fetchPathInPage(
        webView: WebView,
        pathAndQuery: String,
    ): String {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pendingFetches[id] = deferred
        webView.evaluateJavascript(buildFetchScript(id, pathAndQuery), null)
        return try {
            deferred.await()
        } finally {
            pendingFetches.remove(id)
        }
    }

    private fun buildFetchScript(
        id: String,
        pathAndQuery: String,
    ): String =
        """
        (function() {
          const requestId = ${JSONObject.quote(id)};
          const path = ${JSONObject.quote(pathAndQuery)};
          const report = (payload) => {
            try {
              window.FlareZhihuFetchBridge.onFetched(requestId, JSON.stringify(payload));
            } catch (_) {}
          };
          fetch(path, { credentials: "include" })
            .then(async (response) => {
              const body = await response.text();
              report({
                ok: response.ok,
                status: response.status,
                type: response.type,
                redirected: response.redirected,
                responseUrl: response.url,
                body
              });
            })
            .catch((error) => {
              report({
                error: String(error),
                stack: error && error.stack ? String(error.stack) : ""
              });
            });
        })();
        """.trimIndent()

    private suspend fun WebView.evaluate(script: String): String =
        suspendCoroutine { continuation ->
            evaluateJavascript(script) { continuation.resume(it ?: "null") }
        }

    private class FetchBridge {
        @JavascriptInterface
        fun onFetched(
            id: String,
            payload: String,
        ) {
            ZhihuAndroidWebSearch.pendingFetches.remove(id)?.complete(payload)
        }
    }
}

private fun String.toSearchPaths(): List<String> {
    val uri = Uri.parse(this)
    val original = "${uri.path}?${uri.encodedQuery.orEmpty()}"
    val query = uri.getQueryParameter("q").orEmpty()
    val type = uri.getQueryParameter("t").orEmpty().ifBlank { "general" }
    val offset = uri.getQueryParameter("offset").orEmpty().ifBlank { "0" }
    val limit = uri.getQueryParameter("limit").orEmpty().ifBlank { "20" }
    val simple =
        "/api/v4/search_v3?q=${Uri.encode(query)}&t=${Uri.encode(type)}" +
            "&offset=${Uri.encode(offset)}&limit=${Uri.encode(limit)}"
    return listOf(original, simple).distinct()
}

private fun String.decodeJavascriptString(): String =
    when {
        this == "null" -> ""
        length >= 2 && first() == '"' && last() == '"' ->
            JSONObject("{\"value\":$this}").optString("value")
        else -> this
    }
