package dev.dimension.flare.ui.screen.serviceselect

import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xiaohongshuLoginUrl
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.XiaohongshuLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

internal const val XHS_LOGIN_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/145.0.0.0 Safari/537.36"
private const val XHS_LOGIN_VIEWPORT_WIDTH = 1440
private const val XHS_LOGIN_VIEWPORT_HEIGHT = 900

internal val xhsLoginHeaders =
    mapOf(
        "User-Agent" to XHS_LOGIN_USER_AGENT,
        "Sec-CH-UA" to "\"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\", \"Not-A.Brand\";v=\"99\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"macOS\"",
    )

internal val xhsDesktopNavigatorScript =
    """
    (() => {
      const userAgent = "$XHS_LOGIN_USER_AGENT";
      const desktopWidth = $XHS_LOGIN_VIEWPORT_WIDTH;
      const desktopHeight = $XHS_LOGIN_VIEWPORT_HEIGHT;
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

@Composable
internal fun XiaohongshuLoginScreen(
    reloginAccountKey: MicroBlogKey?,
    toHome: () -> Unit,
) {
    val state by producePresenter { presenter(reloginAccountKey, toHome) }

    FlareScaffold {
        if (state.loading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(it),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.error != null) {
            Box(
                modifier =
                    Modifier
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
            XiaohongshuLoginWebView(
                modifier = Modifier.padding(it),
                onCookiesFound = state::onCookiesReceived,
            )
        }
    }
}

@Composable
private fun XiaohongshuLoginWebView(
    modifier: Modifier,
    onCookiesFound: (Map<String, String>) -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        delay(3.seconds)
        while (true) {
            val href = webViewRef?.url.orEmpty()
            val cookieText =
                CookieManager
                    .getInstance()
                    .getCookie("https://$xiaohongshuWebHost")
                    .orEmpty()
            val cookies = cookieText.parseXhsCookies().filterKeys { it in xhsCookieAllowList }
            if (href.isAfterLoginNavigation() && cookies.hasAuthenticatedXhsCookie()) {
                Log.i("XiaohongshuLogin", "cookies ready href=$href ${cookies.sanitizedXhsCookieLog()}")
                onCookiesFound(cookies)
                break
            } else {
                Log.i("XiaohongshuLogin", "waiting href=$href ${cookies.sanitizedXhsCookieLog()}")
            }
            delay(2.seconds)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = XHS_LOGIN_USER_AGENT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setInitialScale(75)
                settings.textZoom = 100
                WebStorage.getInstance().deleteAllData()
                clearCache(true)
                clearHistory()
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                clearXhsCookies()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        xhsDesktopNavigatorScript,
                        setOf("https://$xiaohongshuWebHost"),
                    )
                }
                webViewClient = WebViewClient()
                webViewRef = this
                loadUrl(xiaohongshuLoginUrl, xhsLoginHeaders)
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

internal val xhsCookieAllowList =
    setOf(
        "a1",
        "webId",
        "web_session",
        "web_session_sec",
        "id_token",
        "websectiga",
        "sec_poison_id",
        "xsecappid",
        "gid",
        "abRequestId",
        "webBuild",
        "loadts",
        "acw_tc",
    )

internal fun String.parseXhsCookies(): Map<String, String> =
    split(";")
        .mapNotNull { part ->
            val index = part.indexOf("=")
            if (index <= 0) {
                null
            } else {
                part.substring(0, index).trim() to part.substring(index + 1).trim()
            }
        }.toMap()

private fun clearXhsCookies() {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies(null)
    val expired = "Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/"
    for (name in xhsCookieAllowList) {
        manager.setCookie("https://$xiaohongshuWebHost", "$name=; $expired")
        manager.setCookie("https://$xiaohongshuWebHost", "$name=; Domain=.xiaohongshu.com; $expired")
        manager.setCookie("https://edith.xiaohongshu.com", "$name=; Domain=.xiaohongshu.com; $expired")
    }
    manager.flush()
}

internal fun Map<String, String>.hasAuthenticatedXhsCookie(): Boolean =
    !get("web_session").isNullOrBlank() &&
        (
            !get("id_token").isNullOrBlank() ||
                !get("web_session_sec").isNullOrBlank() ||
                !get("websectiga").isNullOrBlank()
        )

private fun String.isAfterLoginNavigation(): Boolean =
    startsWith("https://$xiaohongshuWebHost", ignoreCase = true) &&
        !contains("/login", ignoreCase = true)

internal fun Map<String, String>.sanitizedXhsCookieLog(): String =
    keys
        .sorted()
        .joinToString(prefix = "keys=[", postfix = "]") { name ->
            "$name:${getValue(name).length}"
        }

@Composable
private fun presenter(
    reloginAccountKey: MicroBlogKey?,
    toHome: () -> Unit,
) = remember(reloginAccountKey) {
    XiaohongshuLoginPresenter(reloginAccountKey, toHome)
}.invoke()
