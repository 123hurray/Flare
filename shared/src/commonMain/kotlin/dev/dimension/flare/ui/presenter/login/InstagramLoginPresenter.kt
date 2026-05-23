package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.instagram.InstagramService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Immutable
public interface InstagramLoginState {
    public val loading: Boolean
    public val error: String?

    public fun onCookiesReceived(cookies: Map<String, String>)
}

public class InstagramLoginPresenter(
    private val reloginAccountKey: MicroBlogKey?,
    private val toHome: () -> Unit,
) : PresenterBase<InstagramLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): InstagramLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        return object : InstagramLoginState {
            override val loading = loading
            override val error = error

            override fun onCookiesReceived(cookies: Map<String, String>) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        require(!cookies["sessionid"].isNullOrBlank()) { "Missing sessionid cookie" }
                        require(!cookies["csrftoken"].isNullOrBlank()) { "Missing csrftoken cookie" }
                        require(!cookies["ds_user_id"].isNullOrBlank()) { "Missing ds_user_id cookie" }
                        val savedCookies = cookies.filterValues { it.isNotBlank() }
                        val user = InstagramService(cookiesFlow = flowOf(savedCookies)).me()
                        val userId = user.id.ifBlank { user.username }
                        require(userId.isNotBlank()) { "Instagram user id is empty" }
                        val profileAccountKey = MicroBlogKey(userId, instagramWebHost)
                        val existing =
                            reloginAccountKey
                                ?.let { accountRepository.find(it) }
                                ?.takeIf { it.platformType == PlatformType.Instagram }
                                ?: (accountRepository.activeAccount.firstOrNull() as? UiState.Success)
                                    ?.data
                                    ?.takeIf { it.platformType == PlatformType.Instagram }
                                ?: accountRepository.allAccounts
                                    .first()
                                    .filterIsInstance<UiAccount.Instagram>()
                                    .singleOrNull()
                        val accountKey = existing?.accountKey ?: profileAccountKey
                        accountRepository.addAccount(
                            account = UiAccount.Instagram(accountKey),
                            credential =
                                UiAccount.Instagram.Credential(
                                    cookies = savedCookies,
                                    savedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it.message ?: "Instagram login failed"
                    }
                    loading = false
                }
            }
        }
    }
}
