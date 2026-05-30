package dev.dimension.flare.ui.screen.agent

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderScope<NavKey>.agentEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Agent.Chat> { args ->
        AgentChatScreen(
            conversationId = args.conversationId,
            initialText = args.initialText,
            sourceRoute = args.sourceRoute,
            statusKey = args.statusKey,
            accountType = args.accountType,
            selectedStatusPlatform = args.selectedStatusPlatform,
            selectedStatusText = args.selectedStatusText,
            selectedStatusAuthorName = args.selectedStatusAuthorName,
            selectedStatusAuthorHandle = args.selectedStatusAuthorHandle,
            selectedStatusCreatedAtEpochMillis = args.selectedStatusCreatedAtEpochMillis,
            selectedStatusDeeplink = args.selectedStatusDeeplink,
            sourceInstanceId = args.sourceInstanceId,
            onBack = onBack,
            navigate = navigate,
        )
    }
}
