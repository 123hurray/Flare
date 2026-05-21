package dev.dimension.flare.data.repository

import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Jike
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.jikeWebHost
import dev.dimension.flare.model.spec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class AccountTabSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    init {
        coroutineScope.launch {
            removeTabsForDeletedAccounts()
        }
        coroutineScope.launch {
            normalizeJikeHomeTabs()
        }
        coroutineScope.launch {
            accountRepository.onAdded.collect { account ->
                addDefaultTabs(account)
            }
        }
        coroutineScope.launch {
            accountRepository.onRemoved.collect { accountKey ->
                removeTabsForAccount(accountKey)
            }
        }
    }

    private suspend fun removeTabsForDeletedAccounts() {
        val existingAccounts =
            accountRepository.allAccounts
                .first()
                .mapTo(linkedSetOf()) { it.accountKey }
        settingsRepository.updateTabSettings {
            cleanupForExistingAccounts(existingAccounts).sanitizeDuplicateTabKeys()
        }
    }

    private suspend fun addDefaultTabs(account: UiAccount) {
        val defaultTabs = account.platformType.spec.defaultTimelineTabs(account.accountKey)
        if (defaultTabs.isEmpty()) {
            return
        }
        settingsRepository.updateTabSettings {
            val newTabs =
                (mainTabs + defaultTabs)
                    .distinctBy { it.key }
            val newSettings = copy(mainTabs = newTabs).sanitizeDuplicateTabKeys()
            if (newSettings == this) {
                this
            } else {
                newSettings
            }
        }
    }

    private suspend fun removeTabsForAccount(accountKey: MicroBlogKey) {
        settingsRepository.updateTabSettings {
            cleanupForExistingAccounts(setOf(accountKey), retainAccounts = false).sanitizeDuplicateTabKeys()
        }
    }

    private suspend fun normalizeJikeHomeTabs() {
        settingsRepository.updateTabSettings {
            val normalizedTabs =
                mainTabs.map { tab ->
                    val accountKey = (tab.account as? AccountType.Specific)?.accountKey
                    if (tab is HomeTimelineTabItem && accountKey?.host == jikeWebHost) {
                        tab.copy(
                            metaData =
                                TabMetaData(
                                    title = TitleType.Text("jike"),
                                    icon = IconType.Material(UiIcon.Jike),
                                ),
                        )
                    } else {
                        tab
                    }
                }.toImmutableList()
            val normalizedSecondaryItems =
                secondaryItems?.map { tab ->
                    val accountKey = (tab.account as? AccountType.Specific)?.accountKey
                    if (tab is Jike.FeaturedTimelineTabItem && accountKey?.host == jikeWebHost) {
                        tab.copy(
                            metaData =
                                TabMetaData(
                                    title = TitleType.Text("广场"),
                                    icon = IconType.Mixed(UiIcon.Featured, accountKey),
                                ),
                        )
                    } else {
                        tab
                    }
                }
            if (normalizedTabs == mainTabs) {
                if (normalizedSecondaryItems == secondaryItems) {
                    this
                } else {
                    copy(secondaryItems = normalizedSecondaryItems)
                }
            } else {
                copy(
                    mainTabs = normalizedTabs,
                    secondaryItems = normalizedSecondaryItems,
                )
            }
        }
    }

    private fun TabSettings.cleanupForExistingAccounts(
        accountKeys: Set<MicroBlogKey>,
        retainAccounts: Boolean = true,
    ): TabSettings {
        val newTabs =
            mainTabs
                .mapNotNull { tab ->
                    tab.cleanup(accountKeys, retainAccounts)
                }.distinctBy { it.key }
        return if (newTabs == mainTabs) {
            this
        } else {
            copy(mainTabs = newTabs)
        }
    }

    private fun TimelineTabItem.cleanup(
        accountKeys: Set<MicroBlogKey>,
        retainAccounts: Boolean,
    ): TimelineTabItem? =
        when (this) {
            is MixedTimelineTabItem -> {
                val cleanedSubTabs =
                    subTimelineTabItem
                        .mapNotNull {
                            it.cleanup(accountKeys, retainAccounts)
                        }.distinctBy { it.key }
                        .toImmutableList()
                if (cleanedSubTabs.isEmpty()) {
                    null
                } else if (cleanedSubTabs == subTimelineTabItem) {
                    this
                } else {
                    copy(subTimelineTabItem = cleanedSubTabs)
                }
            }

            else -> {
                val accountKey = (account as? AccountType.Specific)?.accountKey ?: return this
                val shouldRetain = accountKey in accountKeys
                if (shouldRetain == retainAccounts) {
                    this
                } else {
                    null
                }
            }
        }
}

internal fun TabSettings.sanitizeDuplicateTabKeys(): TabSettings {
    val sanitizedTabs =
        mainTabs
            .mapNotNull { it.sanitizeDuplicateTabKeys() }
            .distinctBy { it.key }
    return if (sanitizedTabs == mainTabs) {
        this
    } else {
        copy(mainTabs = sanitizedTabs)
    }
}

private fun TimelineTabItem.sanitizeDuplicateTabKeys(): TimelineTabItem? =
    when (this) {
        is MixedTimelineTabItem -> {
            val sanitizedSubTabs =
                subTimelineTabItem
                    .mapNotNull { it.sanitizeDuplicateTabKeys() }
                    .distinctBy { it.key }
                    .toImmutableList()
            if (sanitizedSubTabs.isEmpty()) {
                null
            } else if (sanitizedSubTabs == subTimelineTabItem) {
                this
            } else {
                copy(subTimelineTabItem = sanitizedSubTabs)
            }
        }

        else -> {
            this
        }
    }
