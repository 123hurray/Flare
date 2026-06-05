package dev.dimension.flare.ui.screen.home

import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.network.xiaohongshu.XhsVerificationRequiredException
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.presenter.home.XhsVerificationPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.serviceselect.XHS_LOGIN_USER_AGENT
import dev.dimension.flare.ui.screen.serviceselect.hasAuthenticatedXhsCookie
import dev.dimension.flare.ui.screen.serviceselect.parseXhsCookies
import dev.dimension.flare.ui.screen.serviceselect.sanitizedXhsCookieLog
import dev.dimension.flare.ui.screen.serviceselect.xhsCookieAllowList
import dev.dimension.flare.ui.screen.serviceselect.xhsDesktopNavigatorScript
import dev.dimension.flare.ui.screen.serviceselect.xhsLoginHeaders
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun XhsVerificationDialog(
    exception: XhsVerificationRequiredException,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    val accountKey = exception.accountKey
    if (accountKey == null) {
        XhsVerificationUnavailableDialog(onDismiss = onDismiss)
        return
    }

    val state by producePresenter("xhs_verification_$accountKey") {
        remember(accountKey) {
            XhsVerificationPresenter(accountKey)
        }.invoke()
    }
    var currentUrl by remember(exception.url) { mutableStateOf(exception.url) }
    var message by remember(exception.url) { mutableStateOf<String?>(null) }

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
                ) {
                    Text(
                        text = "小红书验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    TextButton(
                        onClick = {
                            val cookies = currentXhsCookies(currentUrl)
                            if (cookies.hasAuthenticatedXhsCookie()) {
                                Log.i("XhsVerification", "cookies ready ${cookies.sanitizedXhsCookieLog()}")
                                state.updateCookies(cookies)
                                onVerified()
                            } else {
                                message = "完成验证后再点完成"
                            }
                        },
                    ) {
                        Text("完成")
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (state.cookies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在准备验证页面")
                    }
                } else {
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
                                settings.userAgentString = XHS_LOGIN_USER_AGENT
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.textZoom = 100
                                setInitialScale(75)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                writeXhsCookies(state.cookies)
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                    WebViewCompat.addDocumentStartJavaScript(
                                        this,
                                        xhsDesktopNavigatorScript,
                                        setOf("https://$xiaohongshuWebHost"),
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
                                loadUrl(exception.url, xhsLoginHeaders)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun XhsVerificationUnavailableDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("小红书需要验证，但当前请求没有账号信息。")
            }
        }
    }
}

internal fun SearchState.xhsVerificationException(): XhsVerificationRequiredException? =
    users.xhsVerificationException()
        ?: status.xhsVerificationException()

internal suspend fun SearchState.refreshAfterXhsVerificationSuspend() {
    users.refreshAfterXhsVerification()
    status.refreshAfterXhsVerification()
}

internal fun DiscoverState.xhsVerificationException(): XhsVerificationRequiredException? =
    users.xhsVerificationException()
        ?: status.xhsVerificationException()
        ?: hashtags.xhsVerificationException()

internal suspend fun DiscoverState.refreshAfterXhsVerificationSuspend() {
    users.refreshAfterXhsVerification()
    status.refreshAfterXhsVerification()
    hashtags.refreshAfterXhsVerification()
}

private fun PagingState<*>.xhsVerificationException(): XhsVerificationRequiredException? =
    when (this) {
        is PagingState.Error -> error as? XhsVerificationRequiredException
        is PagingState.Success -> (appendState as? LoadState.Error)?.error as? XhsVerificationRequiredException
        else -> null
    }

private suspend fun PagingState<*>.refreshAfterXhsVerification() {
    when (this) {
        is PagingState.Error -> onRetry()
        is PagingState.Empty -> refresh()
        is PagingState.Success -> refreshSuspend()
        is PagingState.Loading -> Unit
    }
}

private fun writeXhsCookies(cookies: Map<String, String>) {
    val cookieManager = CookieManager.getInstance()
    cookies
        .filterKeys { it in xhsCookieAllowList }
        .filterValues { it.isNotBlank() }
        .forEach { (name, value) ->
            val cookie = "$name=$value; Domain=.xiaohongshu.com; Path=/"
            cookieManager.setCookie("https://$xiaohongshuWebHost", cookie)
            cookieManager.setCookie("https://edith.xiaohongshu.com", cookie)
        }
    cookieManager.flush()
}

private fun currentXhsCookies(currentUrl: String?): Map<String, String> {
    val cookieManager = CookieManager.getInstance()
    return listOfNotNull(
        currentUrl,
        "https://$xiaohongshuWebHost",
        "https://edith.xiaohongshu.com",
    ).distinct()
        .flatMap { url ->
            cookieManager
                .getCookie(url)
                .orEmpty()
                .parseXhsCookies()
                .entries
        }.associate { it.key to it.value }
        .filterKeys { it in xhsCookieAllowList }
        .filterValues { it.isNotBlank() }
}
