package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlin.time.Duration.Companion.minutes
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
            delay(VvoRiskControlWindow)
            pendingChocolate?.let {
                state.login(it)
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
            state.login(chocolate)
        } else {
            onBack()
        }
    }
    FlareScaffold {
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
                // Clear stale login cookies before starting a new WebView session.
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
                    javaScriptEnabled = true
//                    domStorageEnabled = true
//                    databaseEnabled = true
//                    javaScriptCanOpenWindowsAutomatically = false
//                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            },
        )
    }
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
    ).distinct()
        .mapNotNull { cookieManager.getCookie(it) }
        .firstOrNull(isValid)
}

private val VvoRiskControlWindow = 2.minutes

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { VVOLoginPresenter(toHome) }.invoke()
    }
