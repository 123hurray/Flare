package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

internal fun authenticatedAccountProfileFlow(dataSource: AuthenticatedMicroblogDataSource): Flow<Pair<MicroBlogKey, UiState<UiProfile>>> {
    val accountKey = dataSource.accountKey
    return when (dataSource) {
        is VVODataSource -> dataSource.authenticatedUser()
        is UserDataSource -> dataSource.userHandler.userById(accountKey.id)
        else -> error("Account service is not authenticated user data source")
    }.toUi().map { accountKey to it }
}

internal fun Flow<List<Pair<MicroBlogKey, UiState<UiProfile>>>>.stableAccountProfiles(): Flow<ImmutableList<UiProfile>> =
    scan(AccountProfilesCache()) { cache, states ->
        val order = states.map { it.first }
        val activeKeys = order.toSet()
        val profiles =
            cache.profiles
                .filterKeys { it in activeKeys }
                .toMutableMap()

        states.forEach { (key, state) ->
            state.takeSuccess()?.let { profiles[key] = it }
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
