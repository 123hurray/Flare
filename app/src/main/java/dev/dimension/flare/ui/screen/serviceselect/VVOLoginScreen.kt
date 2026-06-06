package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.VVOLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun VVOLoginScreen(
    toHome: () -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter { presenter(toHome) }
    val webViewState = rememberWebViewState(UiApplication.VVo.loginUrl)
    var pendingChocolate by remember { mutableStateOf<String?>(null) }
    var waitingForRiskWindow by remember { mutableStateOf(false) }
    var submittedChocolate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!state.loading && waitingForRiskWindow.not()) {
                currentVvoChocolate(webViewState.lastLoadedUrl, state::checkChocolate)?.let {
                    pendingChocolate = it
                    waitingForRiskWindow = true
                }
            }
            delay(2.seconds)
        }
    }
    LaunchedEffect(waitingForRiskWindow) {
        if (waitingForRiskWindow) {
            delay(VvoLoginCompletionDelay)
            pendingChocolate?.let {
                if (submittedChocolate != it) {
                    submittedChocolate = it
                    state.login(it)
                }
            }
            waitingForRiskWindow = false
        }
    }
    BackHandler {
        if (state.loading) {
            return@BackHandler
        }
        if (state.error != null) {
            onBack()
            return@BackHandler
        }
        val chocolate = currentVvoChocolate(webViewState.lastLoadedUrl, state::checkChocolate) ?: pendingChocolate
        if (chocolate != null) {
            pendingChocolate = chocolate
            waitingForRiskWindow = false
            submittedChocolate = chocolate
            state.login(chocolate)
        } else {
            onBack()
        }
    }
    FlareScaffold {
        Box(Modifier.fillMaxSize()) {
            WebView(
                webViewState,
                layoutParams =
                    FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    ),
                modifier =
                    Modifier
                        .alpha(0.99f)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(it)
                        .fillMaxSize(),
                onCreated = {
                    clearVvoCookies()
                    with(it.settings) {
                        javaScriptEnabled = true
//                    domStorageEnabled = true
//                    databaseEnabled = true
//                    javaScriptCanOpenWindowsAutomatically = false
//                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                },
            )
            if (pendingChocolate != null || state.loading || state.error != null) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        state.errorMessage?.let { message ->
                            Text(
                                text = "保存失败：$message",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (state.errorMessage != null) {
                            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            enabled = !state.loading,
                            onClick = {
                                val chocolate =
                                    currentVvoChocolate(webViewState.lastLoadedUrl, state::checkChocolate)
                                        ?: pendingChocolate
                                if (chocolate != null) {
                                    pendingChocolate = chocolate
                                    waitingForRiskWindow = false
                                    submittedChocolate = chocolate
                                    state.login(chocolate)
                                }
                            },
                        ) {
                            Text(
                                when {
                                    state.loading -> "正在保存登录态"
                                    state.error != null -> "重试保存登录态"
                                    else -> "完成并返回主页"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clearVvoCookies() {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    val urls =
        listOf(
            UiApplication.VVo.loginUrl,
            "https://${UiApplication.VVo.host}/",
            "https://$vvoHostShort/",
            "https://$vvoHostLong/",
            "https://weibo.cn/",
            "https://m.weibo.cn/",
        )
    val names =
        urls
            .flatMap { url ->
                cookieManager
                    .getCookie(url)
                    .orEmpty()
                    .split(";")
                    .mapNotNull { cookie ->
                        cookie.substringBefore("=", "").trim().takeIf { it.isNotBlank() }
                    }
            }.toSet()
    val expired = "Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
    names.forEach { name ->
        urls.forEach { url ->
            cookieManager.setCookie(url, "$name=; $expired")
        }
        cookieManager.setCookie("https://weibo.cn/", "$name=; Domain=.weibo.cn; $expired")
        cookieManager.setCookie("https://m.weibo.cn/", "$name=; Domain=.weibo.cn; $expired")
    }
    cookieManager.flush()
}

private fun currentVvoChocolate(
    currentUrl: String?,
    isValid: (String) -> Boolean,
): String? {
    val cookieManager = CookieManager.getInstance()
    return listOfNotNull(
        currentUrl,
        UiApplication.VVo.loginUrl,
        "https://${UiApplication.VVo.host}/",
        "https://$vvoHostShort/",
        "https://$vvoHostLong/",
        "https://weibo.cn/",
        "https://m.weibo.cn/",
    ).distinct()
        .mapNotNull { cookieManager.getCookie(it) }
        .firstOrNull(isValid)
}

private val VvoLoginCompletionDelay = 3.seconds

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { VVOLoginPresenter(toHome) }.invoke()
    }
