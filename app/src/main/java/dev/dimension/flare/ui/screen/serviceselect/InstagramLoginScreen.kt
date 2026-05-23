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
import dev.dimension.flare.data.network.instagram.INSTAGRAM_WEB_USER_AGENT
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.model.instagramWebUrl
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.InstagramLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

private const val INSTAGRAM_LOGIN_VIEWPORT_WIDTH = 1440
private const val INSTAGRAM_LOGIN_VIEWPORT_HEIGHT = 900

private val instagramLoginHeaders =
    mapOf(
        "User-Agent" to INSTAGRAM_WEB_USER_AGENT,
        "Sec-CH-UA" to "\"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\", \"Not-A.Brand\";v=\"99\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"Windows\"",
    )

private val instagramDesktopNavigatorScript =
    """
    (() => {
      const userAgent = "$INSTAGRAM_WEB_USER_AGENT";
      const desktopWidth = $INSTAGRAM_LOGIN_VIEWPORT_WIDTH;
      const desktopHeight = $INSTAGRAM_LOGIN_VIEWPORT_HEIGHT;
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
        { brand: "Chromium", version: "126" },
        { brand: "Google Chrome", version: "126" },
        { brand: "Not-A.Brand", version: "99" }
      ];
      const userAgentData = {
        brands,
        mobile: false,
        platform: "Windows",
        getHighEntropyValues: async (hints) => {
          const values = {
            architecture: "x86",
            bitness: "64",
            brands,
            fullVersionList: brands.map((item) => ({ brand: item.brand, version: item.version + ".0.0.0" })),
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
internal fun InstagramLoginScreen(
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
            InstagramLoginWebView(
                modifier = Modifier.padding(it),
                onCookiesFound = state::onCookiesReceived,
            )
        }
    }
}

@Composable
private fun InstagramLoginWebView(
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
                    .getCookie("https://$instagramWebHost")
                    .orEmpty()
            val cookies = cookieText.parseCookies().filterKeys { it in instagramCookieAllowList }
            if (cookies.hasAuthenticatedInstagramCookie()) {
                Log.i("InstagramLogin", "cookies ready href=$href ${cookies.sanitizedCookieLog()}")
                onCookiesFound(cookies)
                break
            } else {
                Log.i("InstagramLogin", "waiting href=$href ${cookies.sanitizedCookieLog()}")
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
                settings.userAgentString = INSTAGRAM_WEB_USER_AGENT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setInitialScale(75)
                settings.textZoom = 100
                WebStorage.getInstance().deleteAllData()
                clearCache(true)
                clearHistory()
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                clearInstagramCookies()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        instagramDesktopNavigatorScript,
                        setOf("https://$instagramWebHost"),
                    )
                }
                webViewClient = WebViewClient()
                webViewRef = this
                loadUrl(instagramWebUrl, instagramLoginHeaders)
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

private val instagramCookieAllowList =
    setOf(
        "sessionid",
        "csrftoken",
        "ds_user_id",
        "mid",
        "ig_did",
        "ig_nrcb",
        "rur",
        "shbid",
        "shbts",
        "datr",
    )

private fun String.parseCookies(): Map<String, String> =
    split(";")
        .mapNotNull { part ->
            val index = part.indexOf("=")
            if (index <= 0) {
                null
            } else {
                part.substring(0, index).trim() to part.substring(index + 1).trim()
            }
        }.toMap()

private fun clearInstagramCookies() {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies(null)
    val expired = "Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/"
    for (name in instagramCookieAllowList) {
        manager.setCookie("https://$instagramWebHost", "$name=; $expired")
        manager.setCookie("https://$instagramWebHost", "$name=; Domain=.instagram.com; $expired")
        manager.setCookie("https://i.instagram.com", "$name=; Domain=.instagram.com; $expired")
    }
    manager.flush()
}

private fun Map<String, String>.hasAuthenticatedInstagramCookie(): Boolean =
    !get("sessionid").isNullOrBlank() &&
        !get("csrftoken").isNullOrBlank() &&
        !get("ds_user_id").isNullOrBlank()

private fun Map<String, String>.sanitizedCookieLog(): String =
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
    InstagramLoginPresenter(reloginAccountKey, toHome)
}.invoke()
