package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.JikeLoginPresenter
import dev.dimension.flare.model.jikeWebHost
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun JikeLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { presenter(toHome) }

    FlareScaffold {
        JikeLoginWebView(
            modifier = Modifier.padding(it),
            onTokensFound = { accessToken, refreshToken ->
                state.onTokensReceived(accessToken, refreshToken)
            },
        )
    }
}

@Composable
private fun JikeLoginWebView(
    modifier: Modifier,
    onTokensFound: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Poll localStorage for Jike tokens via JS eval
    LaunchedEffect(Unit) {
        while (true) {
            val wv = webViewRef
            if (wv != null) {
                val result = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    wv.post {
                        wv.evaluateJavascript(
                            "(function() { var a = localStorage.getItem('JK_ACCESS_TOKEN'); var r = localStorage.getItem('JK_REFRESH_TOKEN'); if (a && r) { return '|||' + a + '|||' + r; } return ''; })()",
                        ) { value ->
                            cont.resume(value, null)
                        }
                    }
                }
                if (!result.isNullOrEmpty() && result != "null") {
                    // Result is a JSON string like "\"|||<access>|||<refresh>\""
                    val unquoted = result.removePrefix("\"").removeSuffix("\"")
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                    if (unquoted.startsWith("|||")) {
                        val payload = unquoted.substring(3)
                        val idx = payload.indexOf("|||")
                        if (idx > 0) {
                            val accessToken = payload.substring(0, idx)
                            val refreshToken = payload.substring(idx + 3)
                            if (accessToken.isNotEmpty() && refreshToken.isNotEmpty()) {
                                onTokensFound(accessToken, refreshToken)
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
                CookieManager.getInstance().removeAllCookies(null)
                webViewClient = WebViewClient()
                webViewRef = this
                loadUrl("https://$jikeWebHost/")
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

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { JikeLoginPresenter(toHome) }.invoke()
    }
