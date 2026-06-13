package dev.dimension.flare.ui.screen.home

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.json.JSONObject

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
    val scope = rememberCoroutineScope()
    fun finishVerificationOrDismiss(dismissWhenUnverified: Boolean) {
        CookieManager.getInstance().flush()
        val chocolate = currentVvoChocolate(currentUrl, state.chocolate)
        if (chocolate != null && !currentUrl.isVvoCaptchaPage()) {
            scope.launch {
                state.updateChocolate(chocolate).join()
                onVerified()
            }
        } else if (dismissWhenUnverified) {
            onDismiss()
        } else {
            message = "完成验证后再点完成"
        }
    }

    Dialog(
        onDismissRequest = { finishVerificationOrDismiss(dismissWhenUnverified = true) },
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
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "微博验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { finishVerificationOrDismiss(dismissWhenUnverified = true) }) {
                        Text("关闭")
                    }
                    TextButton(
                        onClick = {
                            finishVerificationOrDismiss(dismissWhenUnverified = false)
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
                                writeVvoCookies(chocolate) {
                                    loadUrl(
                                        exception.url,
                                        mapOf(
                                            "Cookie" to chocolate,
                                            "User-Agent" to VvoWebUserAgent,
                                        ),
                                    )
                                }
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String,
                                        ) {
                                            currentUrl = url
                                            view.evaluateJavascript(vvoCookieInjectionScript(chocolate), null)
                                        }
                                    }
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

private fun writeVvoCookies(
    chocolate: String,
    onComplete: () -> Unit = {},
) {
    val cookieManager = CookieManager.getInstance()
    val cookies =
        chocolate
        .split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
    val writes =
        cookies.flatMap { cookie ->
            listOf(
                "https://$vvoHost/" to "$cookie; Path=/",
                "https://$vvoHost/" to "$cookie; Domain=.weibo.cn; Path=/",
                "https://weibo.cn/" to "$cookie; Domain=.weibo.cn; Path=/",
            )
        }
    if (writes.isEmpty()) {
        cookieManager.flush()
        onComplete()
        return
    }
    var pending = writes.size
    val callback =
        ValueCallback<Boolean> {
            pending -= 1
            if (pending == 0) {
                cookieManager.flush()
                onComplete()
            }
        }
    writes.forEach { (url, cookie) ->
        cookieManager.setCookie(url, cookie, callback)
    }
}

private fun currentVvoChocolate(
    currentUrl: String?,
    fallbackChocolate: String? = null,
): String? {
    val cookieManager = CookieManager.getInstance()
    val cookies =
        (
            fallbackChocolate
                .orEmpty()
                .split(";")
                .mapNotNull { cookie ->
                    val name = cookie.substringBefore("=", "").trim()
                    val value = cookie.substringAfter("=", "").trim()
                    if (name.isBlank() || value.isBlank()) null else name to value
                } +
            listOfNotNull(
                currentUrl,
                "https://$vvoHost/",
                "https://weibo.cn/",
                "https://m.weibo.cn/",
            ).distinct()
            .flatMap { url ->
                cookieManager
                    .getCookie(url)
                    .orEmpty()
                    .split(";")
                    .mapNotNull { cookie ->
                        val name = cookie.substringBefore("=", "").trim()
                        val value = cookie.substringAfter("=", "").trim()
                        if (name.isBlank() || value.isBlank()) null else name to value
                    }
            }
        ).toMap()
    val chocolate = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    return chocolate.takeIf(::isValidVvoChocolate)
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

private fun String?.isVvoCaptchaPage(): Boolean =
    this == null || contains("/captcha/", ignoreCase = true)

private fun vvoCookieInjectionScript(chocolate: String): String {
    val assignments =
        chocolate
            .split(";")
            .mapNotNull { rawCookie ->
                val name = rawCookie.substringBefore("=", "").trim()
                val value = rawCookie.substringAfter("=", "").trim()
                if (name.isBlank() || value.isBlank()) {
                    null
                } else {
                    "document.cookie=${JSONObject.quote("$name=$value; Domain=.weibo.cn; Path=/")};"
                }
            }
    return assignments.joinToString(prefix = "(function(){try{", postfix = "}catch(e){}})()")
}

private const val VvoWebUserAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0"
