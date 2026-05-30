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

public class VVOCaptchaPresenter(
    private val accountKey: MicroBlogKey,
) : PresenterBase<VVOCaptchaState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): VVOCaptchaState {
        val credential by accountRepository
            .credentialFlow<UiAccount.VVo.Credential>(accountKey)
            .collectAsState(initial = null)

        return object : VVOCaptchaState {
            override val chocolate: String? = credential?.chocolate

            override fun updateChocolate(chocolate: String) {
                accountRepository.updateCredential(
                    accountKey = accountKey,
                    credential = UiAccount.VVo.Credential(chocolate = chocolate),
                )
            }
        }
    }
}

@Immutable
public interface VVOCaptchaState {
    public val chocolate: String?

    public fun updateChocolate(chocolate: String)
}
