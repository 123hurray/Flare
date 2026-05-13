package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.login.JikeLoginPresenter
import dev.dimension.flare.ui.presenter.login.VVOLoginPresenter
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent
import dev.dimension.flare.model.jikeWebHost
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun ServiceSelectScreen(
    onBack: () -> Unit,
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val xqtLoginState by producePresenter("xqt_login_state") {
        remember {
            XQTLoginPresenter(toHome = onBack)
        }.body()
    }
    val jikeLoginState by producePresenter("jike_login_state") {
        remember {
            JikeLoginPresenter(toHome = onBack)
        }.body()
    }
    val vvoLoginState by producePresenter("vvo_login_state") {
        remember {
            VVOLoginPresenter(toHome = onBack)
        }.body()
    }
    ServiceSelectionScreenContent(
        contentPadding = LocalWindowPadding.current,
        onXQT = {
            onWebViewLogin.invoke(
                "https://${UiApplication.XQT.host}",
                { cookies ->
                    if (cookies.isNullOrEmpty()) {
                        false
                    } else {
                        xqtLoginState.checkChocolate(cookies).also {
                            if (it) {
                                xqtLoginState.login(cookies)
                            }
                        }
                    }
                },
            )
        },
        onVVO = {
            onWebViewLogin.invoke(
                UiApplication.VVo.loginUrl,
                { cookies ->
                    if (cookies.isNullOrEmpty()) {
                        false
                    } else {
                        vvoLoginState.checkChocolate(cookies).also {
                            if (it) {
                                vvoLoginState.login(cookies)
                            }
                        }
                    }
                },
            )
        },
        onJike = {
            // Jike uses localStorage (JK_ACCESS_TOKEN, JK_REFRESH_TOKEN)
            // Desktop WebView doesn't support JS evaluation yet
            // For now, show a placeholder
            println("Jike desktop login requires JS evaluation support in WebView")
        },
        openUri = uriHandler::openUri,
        registerDeeplinkCallback = { callback ->
            OnDeepLink {
                callback(it)
                true
            }
        },
        onBack = onBack,
    )
}
