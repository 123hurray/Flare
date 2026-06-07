package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.model.instagramWebUrl
import dev.dimension.flare.model.jikeWebHost
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.model.xiaohongshuExploreUrl
import dev.dimension.flare.model.xiaohongshuWebHost
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.model.zhihuWebHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.fallbackProfile
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AccountsPresenter :
    PresenterBase<AccountsState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val refreshTokens = MutableStateFlow<Map<MicroBlogKey, Int>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsFlow by lazy {
        accountRepository.allAccounts
            .map { accounts ->
                accounts.map { account ->
                    accountServiceFlow(
                        AccountType.Specific(account.accountKey),
                        accountRepository,
                    ).flatMapLatest { service ->
                        refreshTokens
                            .map { it[account.accountKey] ?: 0 }
                            .distinctUntilChanged()
                            .flatMapLatest {
                                val profileFlow = if (service is VVODataSource) {
                                    service.authenticatedUser().toUi().map { user ->
                                        user
                                    }
                                } else if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                                    service.userHandler.userById(account.accountKey.id).toUi().map { user ->
                                        user
                                    }
                                } else {
                                    flowOf(
                                        UiState.Error(IllegalStateException("Account service is not authenticated user data source")),
                                    )
                                }
                                combine(
                                    profileFlow,
                                    account.homeWebSessionFlow(accountRepository),
                                ) { user, homeWebSession ->
                                    val profileError = (user as? UiState.Error)?.throwable
                                    val profile =
                                        when {
                                            profileError == null -> user
                                            profileError is LoginExpiredException -> user
                                            else -> UiState.Success(account.fallbackProfile())
                                        }
                                    AccountsState.AccountItem(
                                        account = account,
                                        profile = profile,
                                        profileError = profileError?.takeUnless { it is LoginExpiredException },
                                        homeWebSession = homeWebSession,
                                    )
                                }
                            }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it.toImmutableList()
            }
    }

    @Composable
    override fun body(): AccountsState {
        val accounts by accountsFlow.collectAsUiState()
        val activeAccount by accountRepository.activeAccount.flattenUiState()
        return object : AccountsState {
            override val accounts = accounts
            override val activeAccount = activeAccount

            override fun setActiveAccount(accountKey: MicroBlogKey) {
                accountRepository.setActiveAccount(accountKey)
            }

            override fun removeAccount(accountKey: MicroBlogKey) {
                accountRepository.delete(accountKey)
            }

            override fun refreshAccount(accountKey: MicroBlogKey) {
                refreshTokens.value =
                    refreshTokens.value.toMutableMap().apply {
                        this[accountKey] = (this[accountKey] ?: 0) + 1
                    }
            }

            override fun updateOrder(newOrder: List<MicroBlogKey>) {
                accountRepository.updateAccountOrder(newOrder)
            }
        }
    }
}

@Immutable
public interface AccountsState {
    public val accounts: UiState<ImmutableList<AccountItem>>
    public val activeAccount: UiState<UiAccount>

    public fun setActiveAccount(accountKey: MicroBlogKey)

    public fun removeAccount(accountKey: MicroBlogKey)

    public fun refreshAccount(accountKey: MicroBlogKey)

    public fun updateOrder(newOrder: List<MicroBlogKey>)

    @Immutable
    public data class AccountItem(
        val account: UiAccount,
        val profile: UiState<UiProfile>,
        val profileError: Throwable? = null,
        val homeWebSession: AccountHomeWebSession,
    )

    @Immutable
    public data class AccountHomeWebSession(
        val url: String,
        val title: String,
        val cookies: Map<String, String> = emptyMap(),
        val cookieDomains: List<String> = emptyList(),
        val cookieUrls: List<String> = listOf(url),
        val headers: Map<String, String> = emptyMap(),
        val userAgent: String? = null,
        val documentStartScript: String? = null,
        val documentStartScriptOrigins: Set<String> = emptySet(),
        val initialScale: Int = 100,
    )
}

private fun UiAccount.homeWebSessionFlow(accountRepository: AccountRepository) =
    when (this) {
        is UiAccount.VVo ->
            accountHomeCredentialFlow<UiAccount.VVo.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = "https://$vvoHost/",
                    title = "微博主页",
                    cookies = it.chocolate.cookieHeaderToMap(),
                    cookieUrls =
                        listOf(
                            "https://$vvoHost/",
                            "https://$vvoHostShort/",
                            "https://$vvoHostLong/",
                            "https://weibo.cn/",
                            "https://m.weibo.cn/",
                        ),
                    cookieDomains = listOf(".weibo.cn", ".weibo.com"),
                )
            }

        is UiAccount.XQT ->
            accountHomeCredentialFlow<UiAccount.XQT.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = "https://$xqtHost/home",
                    title = "X 主页",
                    cookies = it.chocolate.cookieHeaderToMap(),
                    cookieUrls =
                        listOf(
                            "https://$xqtHost/",
                            "https://$xqtOldHost/",
                        ),
                    cookieDomains = listOf(".x.com", ".twitter.com"),
                )
            }

        is UiAccount.Jike ->
            accountHomeCredentialFlow<UiAccount.Jike.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = "https://$jikeWebHost/",
                    title = "即刻主页",
                    userAgent = JIKE_HOME_USER_AGENT,
                    headers = desktopHeaders(JIKE_HOME_USER_AGENT, platform = "Windows", chromeVersion = "126"),
                    documentStartScript =
                        """
                        (() => {
                          try {
                            localStorage.setItem("JK_ACCESS_TOKEN", ${it.accessToken.encodeJson()});
                            localStorage.setItem("JK_REFRESH_TOKEN", ${it.refreshToken.encodeJson()});
                            ${it.deviceId?.let { deviceId -> "localStorage.setItem(\"JK_DEVICE_ID\", ${deviceId.encodeJson()});" }.orEmpty()}
                          } catch (_) {}
                        })();
                        """.trimIndent(),
                    documentStartScriptOrigins = setOf("https://$jikeWebHost"),
                    initialScale = 75,
                )
            }

        is UiAccount.Xiaohongshu ->
            accountHomeCredentialFlow<UiAccount.Xiaohongshu.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = xiaohongshuExploreUrl,
                    title = "小红书主页",
                    cookies = it.cookies,
                    cookieUrls =
                        listOf(
                            "https://$xiaohongshuWebHost",
                            "https://edith.xiaohongshu.com",
                        ),
                    cookieDomains = listOf(".xiaohongshu.com"),
                    userAgent = XHS_HOME_USER_AGENT,
                    headers = desktopHeaders(XHS_HOME_USER_AGENT, platform = "macOS", chromeVersion = "145"),
                    documentStartScript = desktopNavigatorScript(XHS_HOME_USER_AGENT, platform = "macOS", desktopWidth = 1440, desktopHeight = 900),
                    documentStartScriptOrigins = setOf("https://$xiaohongshuWebHost"),
                    initialScale = 75,
                )
            }

        is UiAccount.Instagram ->
            accountHomeCredentialFlow<UiAccount.Instagram.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = instagramWebUrl,
                    title = "Instagram 主页",
                    cookies = it.cookies,
                    cookieUrls =
                        listOf(
                            "https://$instagramWebHost",
                            "https://i.instagram.com",
                        ),
                    cookieDomains = listOf(".instagram.com"),
                    userAgent = INSTAGRAM_HOME_USER_AGENT,
                    headers = desktopHeaders(INSTAGRAM_HOME_USER_AGENT, platform = "Windows", chromeVersion = "126"),
                    documentStartScript = desktopNavigatorScript(INSTAGRAM_HOME_USER_AGENT, platform = "Windows", desktopWidth = 1440, desktopHeight = 900),
                    documentStartScriptOrigins = setOf("https://$instagramWebHost"),
                    initialScale = 75,
                )
            }

        is UiAccount.Zhihu ->
            accountHomeCredentialFlow<UiAccount.Zhihu.Credential>(accountRepository) {
                AccountsState.AccountHomeWebSession(
                    url = "https://$zhihuWebHost/",
                    title = "知乎主页",
                    cookies = it.cookies,
                    cookieUrls =
                        listOf(
                            "https://$zhihuWebHost",
                            "https://api.zhihu.com",
                            "https://zhuanlan.zhihu.com",
                        ),
                    cookieDomains = listOf(".zhihu.com"),
                    userAgent = ZHIHU_HOME_USER_AGENT,
                    headers = desktopHeaders(ZHIHU_HOME_USER_AGENT, platform = "Windows", chromeVersion = "126"),
                    documentStartScript = desktopNavigatorScript(ZHIHU_HOME_USER_AGENT, platform = "Windows", desktopWidth = 1440, desktopHeight = 900),
                    documentStartScriptOrigins = setOf("https://$zhihuWebHost"),
                    initialScale = 75,
                )
            }

        else ->
            flowOf(
                AccountsState.AccountHomeWebSession(
                    url = "https://${accountKey.host}/",
                    title = "${platformType.name} 主页",
                ),
            )
    }

private inline fun <reified T : UiAccount.Credential> UiAccount.accountHomeCredentialFlow(
    accountRepository: AccountRepository,
    crossinline create: (T) -> AccountsState.AccountHomeWebSession,
) = accountRepository
    .credentialFlow<T>(accountKey)
    .map { create(it) }
    .catch {
        emit(
            AccountsState.AccountHomeWebSession(
                url = "https://${accountKey.host}/",
                title = "${platformType.name} 主页",
            ),
        )
}

private fun String.cookieHeaderToMap(): Map<String, String> =
    split(";")
        .mapNotNull { part ->
            val index = part.indexOf("=")
            if (index <= 0) {
                null
            } else {
                part.substring(0, index).trim().takeIf { it.isNotBlank() } to part.substring(index + 1).trim()
            }
        }.mapNotNull { pair ->
            val name = pair.first ?: return@mapNotNull null
            name to pair.second
        }.toMap()

private const val XHS_HOME_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
private const val INSTAGRAM_HOME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val ZHIHU_HOME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val JIKE_HOME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

private fun desktopHeaders(
    userAgent: String,
    platform: String,
    chromeVersion: String,
): Map<String, String> =
    mapOf(
        "User-Agent" to userAgent,
        "Sec-CH-UA" to "\"Chromium\";v=\"$chromeVersion\", \"Google Chrome\";v=\"$chromeVersion\", \"Not-A.Brand\";v=\"99\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"$platform\"",
    )

private fun desktopNavigatorScript(
    userAgent: String,
    platform: String,
    desktopWidth: Int,
    desktopHeight: Int,
): String {
    val navigatorPlatform = if (platform == "macOS") "MacIntel" else "Win32"
    return """
        (() => {
          const setGetter = (target, key, value) => {
            try { Object.defineProperty(target, key, { get: () => value, configurable: true }); } catch (_) {}
          };
          try {
            setGetter(Navigator.prototype, "userAgent", ${userAgent.encodeJson()});
            setGetter(Navigator.prototype, "platform", "$navigatorPlatform");
            setGetter(Navigator.prototype, "maxTouchPoints", 0);
            setGetter(window, "innerWidth", $desktopWidth);
            setGetter(window, "outerWidth", $desktopWidth);
            setGetter(window, "innerHeight", $desktopHeight);
            setGetter(window, "outerHeight", $desktopHeight);
            setGetter(screen, "width", $desktopWidth);
            setGetter(screen, "availWidth", $desktopWidth);
            setGetter(screen, "height", $desktopHeight);
            setGetter(screen, "availHeight", $desktopHeight);
            const parent = document.head || document.getElementsByTagName("head")[0];
            if (parent) {
              let meta = document.querySelector('meta[name="viewport"]');
              if (!meta) {
                meta = document.createElement("meta");
                meta.name = "viewport";
                parent.prepend(meta);
              }
              meta.content = "width=$desktopWidth, initial-scale=1.0";
            }
            document.documentElement.style.minWidth = "${desktopWidth}px";
            document.documentElement.style.minHeight = "${desktopHeight}px";
            if (document.body) {
              document.body.style.minWidth = "${desktopWidth}px";
              document.body.style.minHeight = "${desktopHeight}px";
            }
          } catch (_) {}
        })();
    """.trimIndent()
}
