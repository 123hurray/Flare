package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.JikeTimelineRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.jikeWebHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * State for the Jike WebView login flow.
 *
 * Login flow:
 * 1. User opens WebView to web.okjike.com
 * 2. User logs in via web UI (phone/SMS/password/etc)
 * 3. App polls localStorage for JK_ACCESS_TOKEN and refresh token
 * 4. onTokensReceived() is called with the extracted tokens
 * 5. Account is saved via AccountRepository and toHome is called
 */
@Immutable
public interface JikeLoginState {
    public val loading: Boolean
    public val error: String?

    /** Called when WebView polls tokens from localStorage */
    public fun onTokensReceived(
        accessToken: String,
        refreshToken: String,
        deviceId: String,
    )
}

/**
 * Presenter for Jike (即刻) login via WebView.
 */
public class JikeLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<JikeLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): JikeLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        return object : JikeLoginState {
            override val loading = loading
            override val error = error

            override fun onTokensReceived(
                accessToken: String,
                refreshToken: String,
                deviceId: String,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        val requiredDeviceId =
                            requireNotNull(deviceId.takeIf { it.isNotBlank() }) {
                                "Jike device id is missing"
                            }
                        val service = JikeService(
                            accessTokenFlow = flowOf(accessToken),
                            refreshTokenFlow = flowOf(refreshToken),
                            deviceIdFlow = flowOf(requiredDeviceId),
                        )
                        val profile = service.getSelfProfile()
                        val user =
                            requireNotNull(profile.user ?: profile.data) {
                                "Profile data is null"
                            }
                        val username = user.username.ifEmpty { user.id }
                        require(username.isNotEmpty()) { "Username is empty" }
                        val accountKey =
                            MicroBlogKey(
                                id = username,
                                host = jikeWebHost,
                            )
                        JikeService(
                            accountKey = accountKey,
                            accessTokenFlow = flowOf(accessToken),
                            refreshTokenFlow = flowOf(refreshToken),
                            deviceIdFlow = flowOf(requiredDeviceId),
                        ).getHomeTimeline(JikeTimelineRequest(limit = 1))

                        accountRepository.addAccount(
                            account =
                                UiAccount.Jike(
                                    accountKey = accountKey,
                                ),
                            credential =
                                UiAccount.Jike.Credential(
                                    accessToken = accessToken,
                                    refreshToken = refreshToken,
                                    deviceId = requiredDeviceId,
                                ),
                        )

                        toHome.invoke()
                    }.onFailure {
                        error = it.message ?: "Login failed"
                    }
                    loading = false
                }
            }
        }
    }
}
