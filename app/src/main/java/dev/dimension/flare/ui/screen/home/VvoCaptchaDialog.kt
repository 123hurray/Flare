package dev.dimension.flare.ui.screen.home

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.paging.LoadState
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
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.repository.VVOCaptchaRequiredException
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.presenter.home.VVOCaptchaPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun VvoCaptchaDialog(
    exception: VVOCaptchaRequiredException,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    val state by producePresenter("vvo_captcha_${exception.accountKey}") {
        remember(exception.accountKey) {
            VVOCaptchaPresenter(exception.accountKey)
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
                        text = "微博验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    TextButton(
                        onClick = {
                            val chocolate = currentVvoChocolate(currentUrl)
                            if (chocolate != null) {
                                state.updateChocolate(chocolate)
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
                val chocolate = state.chocolate
                if (chocolate == null) {
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
                                settings.userAgentString = VvoWebUserAgent
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                writeVvoCookies(chocolate)
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String,
                                        ) {
                                            currentUrl = url
                                        }
                                    }
                                loadUrl(exception.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

internal fun SearchState.vvoCaptchaException(): VVOCaptchaRequiredException? =
    users.vvoCaptchaException()
        ?: status.vvoCaptchaException()

internal suspend fun SearchState.refreshAfterVvoCaptchaSuspend() {
    users.refreshAfterVvoCaptcha()
    status.refreshAfterVvoCaptcha()
}

private fun PagingState<*>.vvoCaptchaException(): VVOCaptchaRequiredException? =
    when (this) {
        is PagingState.Error -> error as? VVOCaptchaRequiredException
        is PagingState.Success -> (appendState as? LoadState.Error)?.error as? VVOCaptchaRequiredException
        else -> null
    }

private suspend fun PagingState<*>.refreshAfterVvoCaptcha() {
    when (this) {
        is PagingState.Error -> onRetry()
        is PagingState.Empty -> refresh()
        is PagingState.Success -> refreshSuspend()
        is PagingState.Loading -> Unit
    }
}

private fun writeVvoCookies(chocolate: String) {
    val cookieManager = CookieManager.getInstance()
    chocolate
        .split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
        .forEach { cookie ->
            cookieManager.setCookie("https://$vvoHost/", "$cookie; Path=/")
            cookieManager.setCookie("https://weibo.cn/", "$cookie; Domain=.weibo.cn; Path=/")
        }
    cookieManager.flush()
}

private fun currentVvoChocolate(currentUrl: String?): String? {
    val cookieManager = CookieManager.getInstance()
    return listOfNotNull(
        currentUrl,
        "https://$vvoHost/",
        "https://weibo.cn/",
        "https://m.weibo.cn/",
    ).distinct()
        .mapNotNull { cookieManager.getCookie(it) }
        .firstOrNull(::isValidVvoChocolate)
}

private fun isValidVvoChocolate(chocolate: String): Boolean =
    chocolate
        .split(";")
        .mapNotNull { cookie ->
            val name = cookie.substringBefore("=", "").trim()
            val value = cookie.substringAfter("=", "").trim()
            if (name.isBlank()) null else name to value
        }.toMap()
        .let { it["MLOGIN"] == "1" && it.containsKey("SUB") && it.containsKey("XSRF-TOKEN") }

private const val VvoWebUserAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0"
