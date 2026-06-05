package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class XhsVerificationPresenter(
    private val accountKey: MicroBlogKey,
) : PresenterBase<XhsVerificationState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): XhsVerificationState {
        val credential by accountRepository
            .credentialFlow<UiAccount.Xiaohongshu.Credential>(accountKey)
            .collectAsState(initial = null)

        return object : XhsVerificationState {
            override val cookies: Map<String, String> = credential?.cookies.orEmpty()

            override fun updateCookies(cookies: Map<String, String>) {
                val nextCookies =
                    (credential?.cookies.orEmpty() + cookies)
                        .filterValues { it.isNotBlank() }
                accountRepository.updateCredential(
                    accountKey = accountKey,
                    credential =
                        UiAccount.Xiaohongshu.Credential(
                            cookies = nextCookies,
                            savedAt = Clock.System.now().toEpochMilliseconds(),
                            fingerprintVersion = credential?.fingerprintVersion ?: "web-macos-chrome-145",
                        ),
                )
            }
        }
    }
}

@Immutable
public interface XhsVerificationState {
    public val cookies: Map<String, String>

    public fun updateCookies(cookies: Map<String, String>)
}
