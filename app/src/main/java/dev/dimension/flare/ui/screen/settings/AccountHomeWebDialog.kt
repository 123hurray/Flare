package dev.dimension.flare.ui.screen.settings

import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.dimension.flare.ui.presenter.settings.AccountsState.AccountHomeWebSession

@Composable
internal fun AccountHomeWebDialog(
    session: AccountHomeWebSession,
    onDismiss: () -> Unit,
) {
    var currentUrl by remember(session.url) { mutableStateOf(session.url) }
    var webViewRef by remember(session) { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            webViewRef?.reload()
                        },
                    ) {
                        Text("刷新")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            session.userAgent?.let { settings.userAgentString = it }
                            setInitialScale(session.initialScale)
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            writeAccountHomeCookies(session)
                            val documentStartScript = session.documentStartScript
                            if (
                                !documentStartScript.isNullOrBlank() &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                            ) {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    documentStartScript,
                                    session.documentStartScriptOrigins.ifEmpty { setOf(session.url.originForDocumentScript()) },
                                )
                            }
                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String,
                                    ) {
                                        currentUrl = url
                                    }
                                }
                            webViewRef = this
                            loadUrl(session.url, session.headers)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (webViewRef !== view) {
                            webViewRef = view
                        }
                    },
                )
            }
        }
    }
}

private fun writeAccountHomeCookies(session: AccountHomeWebSession) {
    if (session.cookies.isEmpty()) return
    val cookieManager = CookieManager.getInstance()
    val urls = (session.cookieUrls + session.url).distinct()
    session.cookies
        .filterKeys { it.isNotBlank() }
        .filterValues { it.isNotBlank() }
        .forEach { (name, value) ->
            urls.forEach { url ->
                cookieManager.setCookie(url, "$name=$value; Path=/")
                session.cookieDomains.forEach { domain ->
                    cookieManager.setCookie(url, "$name=$value; Domain=$domain; Path=/")
                }
            }
        }
    cookieManager.flush()
}

private fun String.originForDocumentScript(): String {
    val uri = Uri.parse(this)
    val scheme = uri.scheme ?: "https"
    val host = uri.host ?: return this
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return "$scheme://$host$port"
}
