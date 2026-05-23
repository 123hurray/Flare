package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.xiaohongshu.XhsService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xiaohongshuWebHost
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
public interface XiaohongshuLoginState {
    public val loading: Boolean
    public val error: String?

    public fun onCookiesReceived(cookies: Map<String, String>)
}

public class XiaohongshuLoginPresenter(
    private val reloginAccountKey: MicroBlogKey?,
    private val toHome: () -> Unit,
) : PresenterBase<XiaohongshuLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): XiaohongshuLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        return object : XiaohongshuLoginState {
            override val loading = loading
            override val error = error

            override fun onCookiesReceived(cookies: Map<String, String>) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        require(cookies["a1"].isNullOrBlank().not()) { "Missing a1 cookie" }
                        require(cookies["web_session"].isNullOrBlank().not()) { "Missing web_session cookie" }
                        val service = XhsService(cookiesFlow = flowOf(cookies))
                        val me = service.me()
                        val user = requireNotNull(me.data) { me.msg ?: "Xiaohongshu profile is empty" }
                        val userId = user.userId.ifBlank { user.nickname }
                        require(userId.isNotBlank()) { "Xiaohongshu user id is empty" }
                        val profileAccountKey = MicroBlogKey(userId, xiaohongshuWebHost)
                        val existing =
                            reloginAccountKey
                                ?.let { accountRepository.find(it) }
                                ?.takeIf { it.platformType == PlatformType.Xiaohongshu }
                                ?: (accountRepository.activeAccount.firstOrNull() as? UiState.Success)
                                    ?.data
                                    ?.takeIf { it.platformType == PlatformType.Xiaohongshu }
                                ?: accountRepository.allAccounts
                                    .first()
                                    .filterIsInstance<UiAccount.Xiaohongshu>()
                                    .singleOrNull()
                        val accountKey = existing?.accountKey ?: profileAccountKey
                        accountRepository.addAccount(
                            account = UiAccount.Xiaohongshu(accountKey),
                            credential =
                                UiAccount.Xiaohongshu.Credential(
                                    cookies = cookies.filterValues { it.isNotBlank() },
                                    savedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it.message ?: "Xiaohongshu login failed"
                    }
                    loading = false
                }
            }
        }
    }
}
