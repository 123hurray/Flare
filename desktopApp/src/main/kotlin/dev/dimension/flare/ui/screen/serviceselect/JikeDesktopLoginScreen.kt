package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop Jike login screen.
 * Loads web.okjike.com and polls localStorage for auth tokens.
 *
 * Note: The kdroidfilter WebView library doesn't expose JS evaluation directly,
 * so we poll cookies as a fallback. Jike sets auth tokens in localStorage
 * (JK_ACCESS_TOKEN, JK_REFRESH_TOKEN), but we can't access that from the
 * desktop WebView yet. For now, this shows the login page and users must
 * manually complete auth. A proper implementation would require adding
 * JS evaluation support to the desktop WebView wrapper.
 */
@Composable
internal fun JikeDesktopLoginScreen(
    onTokensReceived: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val state =
        rememberWebViewState("https://web.okjike.com/") {
            desktopWebSettings.incognito = true
        }

    // Poll for tokens - Jike uses localStorage which we can't access from desktop WebView
    // For now, this is a placeholder that requires a different approach
    LaunchedEffect(Unit) {
        while (true) {
            delay(2.seconds)
            // TODO: Implement JS evaluation to extract localStorage tokens
            // For now, the desktop Jike login is incomplete
        }
    }

    WebView(
        state,
        modifier = Modifier.fillMaxSize(),
    )
}
