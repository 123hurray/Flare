package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.jike.JikeService
import dev.dimension.flare.data.network.jike.model.GetSmsCodeRequest
import dev.dimension.flare.data.network.jike.model.SmsLoginRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.plugins.ClientRequestException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * State for the Jike login flow.
 * Supports SMS code login.
 */
@Immutable
public interface JikeLoginState {
    public val loading: Boolean
    public val error: String?
    public val smsSent: Boolean
    public val requireSmsCode: Boolean

    public fun sendSmsCode(phoneNumber: String)
    public fun loginWithSmsCode(
        phoneNumber: String,
        smsCode: String,
    )
    public fun clear()
}

/**
 * Presenter for Jike (即刻) login.
 *
 * Login flow:
 * 1. User enters phone number
 * 2. Call sendSmsCode() to request SMS verification code
 * 3. User enters the SMS code
 * 4. Call loginWithSmsCode() to authenticate
 * 5. On success, account is saved and toHome is called
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
        var smsSent by remember { mutableStateOf(false) }
        var requireSmsCode by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        return object : JikeLoginState {
            override val loading = loading
            override val error = error
            override val smsSent = smsSent
            override val requireSmsCode = requireSmsCode

            override fun sendSmsCode(phoneNumber: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        val service = JikeService()
                        service.getSmsCode(
                            GetSmsCodeRequest(
                                mobilePhoneNumber = phoneNumber,
                            ),
                        )
                        if (true) {
                            smsSent = true
                            requireSmsCode = true
                        }
                    }.onFailure {
                        error = it.message ?: "Failed to send SMS code"
                    }
                    loading = false
                }
            }

            override fun loginWithSmsCode(
                phoneNumber: String,
                smsCode: String,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        val service = JikeService()
                        val response =
                            service.loginWithSmsCode(
                                SmsLoginRequest(
                                    mobilePhoneNumber = phoneNumber,
                                    smsCode = smsCode,
                                ),
                            )

                        if (!response.success || response.data?.user == null) {
                            throw Exception(response.error ?: "Login failed")
                        }

                        val user = response.data.user
                        // Extract tokens from response (they would be in headers in real implementation)
                        // For now, this is a simplified version
                        val accountKey = MicroBlogKey(user.id, "jike")
                        val credential =
                            UiAccount.Jike.Credential(
                                accessToken = "",
                                refreshToken = "",
                            )

                        accountRepository.addAccount(
                            account = UiAccount.Jike(accountKey = accountKey),
                            credential = credential,
                        )

                        toHome.invoke()
                    }.onFailure {
                        error = it.message ?: "Login failed"
                    }
                    loading = false
                }
            }

            override fun clear() {
                error = null
                smsSent = false
                requireSmsCode = false
                loading = false
            }
        }
    }
}
