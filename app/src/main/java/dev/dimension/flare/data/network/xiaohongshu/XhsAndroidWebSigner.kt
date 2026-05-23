package dev.dimension.flare.data.network.xiaohongshu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.dimension.flare.model.xiaohongshuWebHost
import java.lang.ref.WeakReference
import java.security.MessageDigest
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

private const val XHS_SIGNER_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/145.0.0.0 Safari/537.36"
private const val XHS_SIGNER_VIEWPORT_WIDTH = 1440
private const val XHS_SIGNER_VIEWPORT_HEIGHT = 900

private val xhsSignerHeaders =
    mapOf(
        "User-Agent" to XHS_SIGNER_USER_AGENT,
        "Sec-CH-UA" to "\"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\", \"Not-A.Brand\";v=\"99\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"macOS\"",
    )

private val xhsSignerDesktopNavigatorScript =
    """
    (() => {
      const userAgent = "$XHS_SIGNER_USER_AGENT";
      const desktopWidth = $XHS_SIGNER_VIEWPORT_WIDTH;
      const desktopHeight = $XHS_SIGNER_VIEWPORT_HEIGHT;
      const setGetter = (target, key, value) => {
        try {
          Object.defineProperty(target, key, { get: () => value, configurable: true });
        } catch (_) {}
      };
      const applyDesktopViewport = () => {
        try {
          const parent = document.head || document.getElementsByTagName("head")[0];
          if (!parent) return false;
          let meta = document.querySelector('meta[name="viewport"]');
          if (!meta) {
            meta = document.createElement("meta");
            meta.name = "viewport";
            parent.prepend(meta);
          }
          meta.content = "width=" + desktopWidth + ", initial-scale=1.0";
          document.documentElement.style.minWidth = desktopWidth + "px";
          document.documentElement.style.minHeight = desktopHeight + "px";
          if (document.body) {
            document.body.style.minWidth = desktopWidth + "px";
            document.body.style.minHeight = desktopHeight + "px";
          }
          return true;
        } catch (_) {
          return true;
        }
      };
      const brands = [
        { brand: "Google Chrome", version: "145" },
        { brand: "Chromium", version: "145" },
        { brand: "Not-A.Brand", version: "99" }
      ];
      const userAgentData = {
        brands,
        mobile: false,
        platform: "macOS",
        getHighEntropyValues: async (hints) => {
          const values = {
            architecture: "x86",
            bitness: "64",
            brands,
            fullVersionList: brands.map((item) => ({ brand: item.brand, version: item.version + ".0.0.0" })),
            mobile: false,
            model: "",
            platform: "macOS",
            platformVersion: "15.0.0",
            uaFullVersion: "145.0.0.0"
          };
          return Object.fromEntries(hints.map((hint) => [hint, values[hint]]));
        }
      };
      if (!applyDesktopViewport()) {
        const observer = new MutationObserver(() => {
          if (applyDesktopViewport()) observer.disconnect();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
      }
      try {
        setGetter(Navigator.prototype, "userAgent", userAgent);
        setGetter(Navigator.prototype, "platform", "MacIntel");
        setGetter(Navigator.prototype, "maxTouchPoints", 0);
        setGetter(Navigator.prototype, "userAgentData", userAgentData);
        setGetter(window, "innerWidth", desktopWidth);
        setGetter(window, "outerWidth", desktopWidth);
        setGetter(window, "innerHeight", desktopHeight);
        setGetter(window, "outerHeight", desktopHeight);
        setGetter(screen, "width", desktopWidth);
        setGetter(screen, "availWidth", desktopWidth);
        setGetter(screen, "height", desktopHeight);
        setGetter(screen, "availHeight", desktopHeight);
      } catch (_) {}
    })();
    """.trimIndent()

public object XhsAndroidWebSigner : XhsRuntimeSigner {
    private const val TAG = "XhsAndroidWebSigner"
    private const val SIGN_TIMEOUT_MS = 8_000L
    private const val READY_TIMEOUT_MS = 45_000L

    private lateinit var appContext: Context
    private var activityRef: WeakReference<Activity>? = null
    private var webView: WebView? = null
    private var hostView: FrameLayout? = null
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    public fun install(context: Context) {
        appContext = context.applicationContext
        XhsSigningRuntime.install(this)
    }

    public fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        val view = webView ?: ensureWebView()
        attachToActivityHost(view)
        if (view.url?.contains(xiaohongshuWebHost) != true) {
            view.loadUrl("https://$xiaohongshuWebHost/explore", xhsSignerHeaders)
        }
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

    override suspend fun sign(request: XhsSigningRequest): Map<String, String> =
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                require(::appContext.isInitialized) { "Xiaohongshu Android signer is not installed" }
                require(request.cookies["a1"].isNullOrBlank().not()) { "Missing a1 cookie" }
                syncCookies(request.cookies)
                val webView = ensureWebView()
                ensureRuntimeReady(webView)
                val payload = signInWebView(webView, request)
                parseHeaders(payload).also { headers ->
                    Log.i(
                        TAG,
                        "signed path=${request.path} bodyLen=${request.body.length} " +
                            "xsLen=${headers["x-s"].orEmpty().length} commonLen=${headers["x-s-common"].orEmpty().length}",
                    )
                }
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
            settings.userAgentString = XHS_SIGNER_USER_AGENT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.blockNetworkLoads = false
            settings.textZoom = 100
            settings.mediaPlaybackRequiresUserGesture = true
            addJavascriptInterface(SignBridge(), "FlareXhsSignBridge")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    xhsSignerDesktopNavigatorScript,
                    setOf("https://$xiaohongshuWebHost"),
                )
            }
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
                Log.i(TAG, "attached hidden signer WebView host")
            }
        host.addView(webView, ViewGroup.LayoutParams(1, 1))
    }

    private suspend fun ensureRuntimeReady(webView: WebView) {
        if (webView.url?.contains(xiaohongshuWebHost) != true) {
            webView.loadUrl("https://$xiaohongshuWebHost/explore", xhsSignerHeaders)
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
                      mnsv2: typeof window.mnsv2,
                      b1Len: (localStorage.getItem("b1") || "").length,
                      dsl: String(window._dsl || "").length,
                      ua: navigator.userAgent,
                      platform: navigator.platform
                    })
                    """.trimIndent(),
                    ).decodeJavascriptString()
            if (state != lastState) {
                Log.i(TAG, "runtime state=$state")
                lastState = state
            }
            val ready = state.contains(""""mnsv2":"function"""") && !state.contains(""""b1Len":0""")
            if (ready) return
            delay(250L)
        }
        Log.w(TAG, "sign runtime not ready url=${webView.url} state=$lastState")
        throw IllegalStateException("Xiaohongshu Web signing runtime is not ready")
    }

    private suspend fun signInWebView(
        webView: WebView,
        request: XhsSigningRequest,
    ): String {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pending[id] = deferred
        webView.evaluateJavascript(buildSignScript(id, request), null)
        return try {
            withTimeout(SIGN_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    private fun parseHeaders(payload: String): Map<String, String> {
        val root = JSONObject(payload)
        if (!root.optBoolean("ok")) {
            throw IllegalStateException(root.optString("error", "Xiaohongshu Web signing failed"))
        }
        val headers = root.getJSONObject("headers")
        return buildMap {
            headers.keys().forEach { key ->
                put(key, headers.getString(key))
            }
        }
    }

    private fun syncCookies(cookies: Map<String, String>) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        cookies
            .filterValues { it.isNotBlank() }
            .forEach { (name, value) ->
                val cookie = "$name=$value; Domain=.xiaohongshu.com; Path=/"
                manager.setCookie("https://$xiaohongshuWebHost", cookie)
                manager.setCookie("https://edith.xiaohongshu.com", cookie)
            }
        manager.flush()
    }

    private fun buildSignScript(
        id: String,
        request: XhsSigningRequest,
    ): String {
        val method = request.method.uppercase()
        val path = request.path
        val body = request.body
        val content = if (method == "POST") path + body else path
        return """
            (function() {
              const requestId = ${JSONObject.quote(id)};
              const path = ${JSONObject.quote(path)};
              const method = ${JSONObject.quote(method)};
              const body = ${JSONObject.quote(body)};
              const content = ${JSONObject.quote(content)};
              const contentMd5 = ${JSONObject.quote(content.md5())};
              const pathMd5 = ${JSONObject.quote(path.md5())};
              const a1 = ${JSONObject.quote(request.cookies["a1"].orEmpty())};
              const report = (payload) => {
                try {
                  window.FlareXhsSignBridge.onSigned(requestId, JSON.stringify(payload));
                } catch (_) {}
              };
              const stdAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
              const xhsAlphabet = "ZmserbBoHQtNP+wOcza/LpngG8yJq42KWYj0DSfdikx3VT16IlUAFM97hECvuRX5";
              const customBase64 = (text) => {
                const bytes = unescape(encodeURIComponent(text));
                return btoa(bytes).replace(/[A-Za-z0-9+/]/g, (ch) => xhsAlphabet[stdAlphabet.indexOf(ch)]);
              };
              const crc32 = (text) => {
                let crc = -1;
                for (let i = 0; i < text.length; i++) {
                  crc = (crc >>> 8) ^ table[(crc ^ text.charCodeAt(i)) & 255];
                }
                return (crc ^ -1) | 0;
              };
              const table = (() => {
                const out = [];
                for (let n = 0; n < 256; n++) {
                  let c = n;
                  for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
                  out[n] = c >>> 0;
                }
                return out;
              })();
              const randomHex = (length) => {
                const bytes = new Uint8Array(Math.ceil(length / 2));
                crypto.getRandomValues(bytes);
                return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("").slice(0, length);
              };
              const needsMiniUa = /api\/sns\/web\/v1\/(homefeed|feed|search\/notes|comment\/post)${'$'}/.test(path) ||
                path.indexOf("api/sns/web/v1/user_posted") >= 0;
              const getX8 = () => new Promise((resolve) => {
                const fallback = localStorage.getItem("b1") || "";
                if (!needsMiniUa || !window.xhsFingerprintV3 || typeof window.xhsFingerprintV3.getCurMiniUa !== "function") {
                  resolve(fallback);
                  return;
                }
                let done = false;
                const finish = (value) => {
                  if (!done) {
                    done = true;
                    resolve(value || fallback);
                  }
                };
                try {
                  window.xhsFingerprintV3.getCurMiniUa(finish);
                } catch (_) {
                  finish(fallback);
                }
                setTimeout(() => finish(fallback), 1500);
              });
              (async () => {
                if (typeof window.mnsv2 !== "function") throw new Error("window.mnsv2 missing");
                const x3 = window.mnsv2(content, contentMd5, pathMd5);
                const platform = window.xsecplatform || "Mac OS";
                const xsPayload = {
                  x0: "4.3.5",
                  x1: "xhs-pc-web",
                  x2: platform,
                  x3,
                  x4: method === "POST" && body ? "object" : ""
                };
                const x8 = await getX8();
                const empty = "";
                const commonPayload = {
                  s0: 3,
                  s1: "",
                  x0: localStorage.getItem("b1b1") || "1",
                  x1: "4.3.5",
                  x2: platform,
                  x3: "xhs-pc-web",
                  x4: window.xsecappvers || "6.12.1",
                  x5: a1,
                  x6: empty,
                  x7: empty,
                  x8,
                  x9: crc32(empty + empty + x8),
                  x10: 0,
                  x11: "normal",
                  x12: (localStorage.getItem("dsllt") || "") + ";" + (window._dsl || "")
                };
                const now = Date.now().toString();
                report({
                  ok: true,
                  headers: {
                    "x-s": "XYS_" + customBase64(JSON.stringify(xsPayload)),
                    "x-s-common": customBase64(JSON.stringify(commonPayload)),
                    "x-t": now,
                    "x-b3-traceid": randomHex(16),
                    "x-xray-traceid": randomHex(32)
                  }
                });
              })().catch((error) => report({ ok: false, error: String(error && error.message || error) }));
            })();
        """.trimIndent()
    }

    private suspend fun WebView.evaluate(script: String): String =
        suspendCoroutine { continuation ->
            evaluateJavascript(script) { continuation.resume(it ?: "null") }
        }

    private class SignBridge {
        @JavascriptInterface
        fun onSigned(
            id: String,
            payload: String,
        ) {
            pending.remove(id)?.complete(payload)
        }
    }
}

private fun String.md5(): String {
    val digest = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun String.decodeJavascriptString(): String =
    if (startsWith("\"") && endsWith("\"")) {
        runCatching {
            JSONObject("""{"value":$this}""").getString("value")
        }.getOrDefault(this)
    } else {
        this
    }
