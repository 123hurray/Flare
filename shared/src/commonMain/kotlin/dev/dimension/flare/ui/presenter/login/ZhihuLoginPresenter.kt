package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.zhihu.ZhihuService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.zhihuWebHost
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
public interface ZhihuLoginState {
    public val loading: Boolean
    public val error: String?

    public fun onCookiesReceived(cookies: Map<String, String>)
}

public class ZhihuLoginPresenter(
    private val reloginAccountKey: MicroBlogKey?,
    private val toHome: () -> Unit,
) : PresenterBase<ZhihuLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): ZhihuLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        return object : ZhihuLoginState {
            override val loading = loading
            override val error = error

            override fun onCookiesReceived(cookies: Map<String, String>) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        require(!cookies["z_c0"].isNullOrBlank()) { "Missing z_c0 cookie" }
                        val savedCookies = cookies.filterValues { it.isNotBlank() }
                        val viewer =
                            runCatching {
                                ZhihuService(
                                    accountKey = reloginAccountKey,
                                    cookiesFlow = flowOf(savedCookies),
                                ).me()
                            }.getOrNull()
                        val profileAccountKey =
                            MicroBlogKey(
                                viewer?.id?.takeIf { it.isNotBlank() }
                                    ?: viewer?.urlToken?.takeIf { it.isNotBlank() }
                                    ?: savedCookies.stableZhihuCookieId(),
                                zhihuWebHost,
                            )
                        val existing =
                            reloginAccountKey
                                ?.let { accountRepository.find(it) }
                                ?.takeIf { it.platformType == PlatformType.Zhihu }
                                ?: (accountRepository.activeAccount.firstOrNull() as? UiState.Success)
                                    ?.data
                                    ?.takeIf { it.platformType == PlatformType.Zhihu }
                                ?: accountRepository.allAccounts
                                    .first()
                                    .filterIsInstance<UiAccount.Zhihu>()
                                    .singleOrNull()
                        val accountKey = existing?.accountKey ?: profileAccountKey
                        accountRepository.addAccount(
                            account = UiAccount.Zhihu(accountKey),
                            credential =
                                UiAccount.Zhihu.Credential(
                                    cookies = savedCookies,
                                    savedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it.message ?: "Zhihu login failed"
                    }
                    loading = false
                }
            }
        }
    }
}

private fun Map<String, String>.stableZhihuCookieId(): String =
    listOfNotNull(
        get("d_c0"),
        get("SESSIONID"),
        get("z_c0"),
    ).firstOrNull { it.isNotBlank() }
        ?.filter { it.isLetterOrDigit() }
        ?.take(24)
        ?.takeIf { it.isNotBlank() }
        ?: "account"
