package dev.dimension.flare.ui.screen.serviceselect

import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.JikeLoginPresenter
import dev.dimension.flare.model.jikeWebHost
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

private const val JIKE_LOGIN_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val JIKE_LOGIN_VIEWPORT_WIDTH = 960

private val jikeLoginHeaders =
    mapOf(
        "User-Agent" to JIKE_LOGIN_USER_AGENT,
        "Sec-CH-UA" to "\"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\", \"Not-A.Brand\";v=\"99\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"Windows\"",
    )

private val jikeDesktopNavigatorScript =
    """
    (() => {
      const userAgent = "$JIKE_LOGIN_USER_AGENT";
      const desktopWidth = $JIKE_LOGIN_VIEWPORT_WIDTH;
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
          if (document.body) {
            document.body.style.minWidth = desktopWidth + "px";
          }
          return true;
        } catch (_) {
          return true;
        }
      };
      const userAgentData = {
        brands: [
          { brand: "Chromium", version: "126" },
          { brand: "Google Chrome", version: "126" },
          { brand: "Not-A.Brand", version: "99" }
        ],
        mobile: false,
        platform: "Windows",
        getHighEntropyValues: async (hints) => {
          const values = {
            architecture: "x86",
            bitness: "64",
            brands: userAgentData.brands,
            fullVersionList: userAgentData.brands.map((item) => ({ brand: item.brand, version: item.version + ".0.0.0" })),
            mobile: false,
            model: "",
            platform: "Windows",
            platformVersion: "10.0.0",
            uaFullVersion: "126.0.0.0"
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
        setGetter(Navigator.prototype, "platform", "Win32");
        setGetter(Navigator.prototype, "maxTouchPoints", 0);
        setGetter(Navigator.prototype, "userAgentData", userAgentData);
        setGetter(window, "innerWidth", desktopWidth);
        setGetter(window, "outerWidth", desktopWidth);
        setGetter(screen, "width", desktopWidth);
        setGetter(screen, "availWidth", desktopWidth);
      } catch (_) {}
    })();
    """.trimIndent()

@Composable
internal fun JikeLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { presenter(toHome) }

    FlareScaffold {
        if (state.loading) {
            // Show loading spinner after tokens are found, hide WebView
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (state.error != null) {
            // Show error message
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            // Show WebView for login
                JikeLoginWebView(
                    modifier = Modifier.padding(it),
                onTokensFound = { accessToken, refreshToken, deviceId ->
                    state.onTokensReceived(accessToken, refreshToken, deviceId)
                },
            )
        }
    }
}

@Composable
private fun JikeLoginWebView(
    modifier: Modifier,
    onTokensFound: (String, String, String?) -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Poll localStorage for Jike web credentials via JS eval
    LaunchedEffect(Unit) {
        while (true) {
            val wv = webViewRef
            if (wv != null) {
                val result = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    wv.post {
                        wv.evaluateJavascript(
                            """
                            (function() {
                                var a = localStorage.getItem('JK_ACCESS_TOKEN');
                                var r = localStorage.getItem('JK_REFRESH_TOKEN') || localStorage.getItem('REFRESH_TOKEN');
                                var d = localStorage.getItem('JK_DEVICE_ID');
                                var keys = [];
                                for (var i = 0; i < localStorage.length; i++) {
                                  keys.push(localStorage.key(i));
                                }
                                return JSON.stringify({
                                  href: location.href,
                                  keys: keys.filter(function(k) {
                                    return /TOKEN|DEVICE|JK_/i.test(k);
                                  }),
                                  accessLength: a ? a.length : 0,
                                  refreshLength: r ? r.length : 0,
                                  deviceLength: d ? d.length : 0,
                                  payload: (a && r) ? ('|||' + a + '|||' + r + '|||' + (d || '')) : ''
                                });
                            })()
                            """.trimIndent(),
                        ) { value ->
                            cont.resume(value, null)
                        }
                    }
                }
                if (!result.isNullOrEmpty() && result != "null") {
                    val jsonText = result.decodeJavascriptStringResult()
                    val json = runCatching { JSONObject(jsonText) }.getOrNull()
                    if (json != null) {
                        Log.i(
                            "JikeLogin",
                            "poll href=${json.optString("href")} " +
                                "keys=${json.optJSONArray("keys")} " +
                                "accessLength=${json.optInt("accessLength")} " +
                                "refreshLength=${json.optInt("refreshLength")} " +
                                "deviceLength=${json.optInt("deviceLength")}",
                        )
                    }
                    val unquoted = json?.optString("payload").orEmpty()
                    if (unquoted.startsWith("|||")) {
                        // Payload is "|||<access>|||<refresh>|||<device>".
                        val payload = unquoted.substring(3)
                        val idx = payload.indexOf("|||")
                        if (idx > 0) {
                            val accessToken = payload.substring(0, idx)
                            val rest = payload.substring(idx + 3)
                            val deviceIdx = rest.indexOf("|||")
                            val refreshToken =
                                if (deviceIdx >= 0) rest.substring(0, deviceIdx) else rest
                            val deviceId =
                                if (deviceIdx >= 0) {
                                    rest.substring(deviceIdx + 3).takeIf { it.isNotBlank() }
                                } else {
                                    null
                                }
                            if (accessToken.isNotEmpty() && refreshToken.isNotEmpty()) {
                                Log.i(
                                    "JikeLogin",
                                    "credentials ready access=${accessToken.redactedLengthAndHash()} " +
                                        "refresh=${refreshToken.redactedLengthAndHash()} " +
                                        "device=${deviceId?.redactedDeviceId() ?: "missing"}",
                                )
                                onTokensFound(accessToken, refreshToken, deviceId)
                                break
                            }
                        }
                    }
                }
            }
            delay(2.seconds)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = JIKE_LOGIN_USER_AGENT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setInitialScale(50)
                settings.textZoom = 100
                CookieManager.getInstance().removeAllCookies(null)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        jikeDesktopNavigatorScript,
                        setOf("https://$jikeWebHost"),
                    )
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            if (uri.scheme == "jike") {
                                view.loadUrl("https://$jikeWebHost/", jikeLoginHeaders)
                                return true
                            }
                            return false
                        }
                    }
                webViewRef = this
                loadUrl("https://$jikeWebHost/", jikeLoginHeaders)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { wv ->
            if (webViewRef !== wv) {
                webViewRef = wv
            }
        },
    )
}

private fun String.decodeJavascriptStringResult(): String =
    removePrefix("\"")
        .removeSuffix("\"")
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")

private fun String.redactedLengthAndHash(): String = "len=$length,fp=${hashCode().toUInt().toString(16)}"

private fun String.redactedDeviceId(): String = "len=$length,tail=${takeLast(4)}"

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { JikeLoginPresenter(toHome) }.invoke()
    }
