package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.fallbackProfile
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

internal fun authenticatedAccountProfileFlow(
    account: UiAccount,
    dataSource: AuthenticatedMicroblogDataSource,
): Flow<AccountProfileState> {
    val accountKey = dataSource.accountKey
    val fallback = account.fallbackProfile()
    val profileState =
        when (dataSource) {
            is VVODataSource -> dataSource.authenticatedUser().toUi()
            is UserDataSource -> dataSource.userHandler.userById(accountKey.id).toUi()
            else -> flowOf(UiState.Error(IllegalStateException("Account service is not authenticated user data source")))
        }
    return profileState.map { AccountProfileState(fallback = fallback, state = it) }
}

internal data class AccountProfileState(
    val fallback: UiProfile,
    val state: UiState<UiProfile>,
)

internal fun Flow<List<AccountProfileState>>.stableAccountProfiles(): Flow<ImmutableList<UiProfile>> =
    scan(AccountProfilesCache()) { cache, states ->
        val order = states.map { it.fallback.key }
        val activeKeys = order.toSet()
        val profiles =
            cache.profiles
                .filterKeys { it in activeKeys }
                .toMutableMap()

        states.forEach { item ->
            val key = item.fallback.key
            profiles[key] = item.state.takeSuccess() ?: profiles[key] ?: item.fallback
        }

        AccountProfilesCache(
            order = order,
            profiles = profiles,
        )
    }.drop(1)
        .map { cache ->
            cache.order
                .mapNotNull { cache.profiles[it] }
                .toImmutableList()
        }

private data class AccountProfilesCache(
    val order: List<MicroBlogKey> = emptyList(),
    val profiles: Map<MicroBlogKey, UiProfile> = emptyMap(),
)
