package dev.dimension.flare.ui.screen.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.data.agent.AgentConversationSummary
import dev.dimension.flare.data.agent.AgentConversationItemView
import dev.dimension.flare.data.agent.AgentNativeArtifact
import dev.dimension.flare.data.agent.AgentSourceContext
import dev.dimension.flare.data.agent.AgentTimelineItem
import dev.dimension.flare.data.agent.AgentToolActivityView
import dev.dimension.flare.data.agent.toUiTimelinePost
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.presenter.agent.AgentChatPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatScreen(
    conversationId: String?,
    initialText: String?,
    sourceRoute: String?,
    statusKey: MicroBlogKey?,
    accountType: AccountType?,
    selectedStatusPlatform: String?,
    selectedStatusText: String?,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    val sourceContext =
        remember(initialText, sourceRoute, statusKey, accountType, selectedStatusPlatform, selectedStatusText) {
            AgentSourceContext(
                selectedText = initialText,
                route = sourceRoute,
                statusKey = statusKey,
                accountType = accountType,
                selectedStatusPlatform = selectedStatusPlatform,
                selectedStatusText = selectedStatusText,
            )
        }
    val presenterKey =
        "agent_chat_${conversationId}_${statusKey}_${initialText}_${selectedStatusPlatform}_${selectedStatusText.hashCode()}"
    val state by producePresenter(presenterKey) {
        remember(conversationId, sourceContext, initialText) {
            AgentChatPresenter(
                initialConversationId = conversationId,
                sourceContext = sourceContext,
            )
        }.invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showConversationManager by remember { mutableStateOf(false) }
    var showPlatformPicker by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        FlareScaffold(
            topBar = {
                FlareTopAppBar(
                    title = { Text("Flare Agent") },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(onClick = { showConversationManager = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Bars,
                                contentDescription = "会话",
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                val showEmptyState =
                    state.conversationItems.isEmpty() &&
                        state.draftStreamingText.isBlank() &&
                        state.streamingArtifacts.isEmpty() &&
                        state.errorText == null &&
                        !state.isRunning
                if (showEmptyState) {
                    AgentEmptyState(
                        onPrompt = state::send,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.conversationItems, key = { it.id }) { item ->
                            when (item) {
                                is AgentConversationItemView.Message ->
                                    AgentMessageBubble(
                                        role = item.value.role,
                                        text = item.value.text,
                                        artifacts = item.value.artifacts,
                                        navigate = navigate,
                                    )

                                is AgentConversationItemView.ToolActivity ->
                                    AgentToolActivityRow(item.value, navigate)
                            }
                        }
                        if (state.draftStreamingText.isNotBlank() || state.streamingArtifacts.isNotEmpty()) {
                            item {
                                AgentMessageBubble(
                                    role = "assistant",
                                    text = state.draftStreamingText,
                                    artifacts = state.streamingArtifacts,
                                    navigate = navigate,
                                )
                            }
                        }
                        state.statusText?.let { status ->
                            item {
                                AgentStatusRow(status = status, running = state.isRunning)
                            }
                        }
                        state.errorText?.let { error ->
                            item {
                                Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                AgentContextBars(
                    selectedStatusPlatform = state.selectedStatusPlatform,
                    selectedStatusText = state.selectedStatusText,
                    includeStatusContext = state.includeStatusContext,
                    selectedText = state.selectedText,
                    includeSelectedTextContext = state.includeSelectedTextContext,
                    onDismissStatus = state::dismissStatusContext,
                    onDismissSelectedText = state::dismissSelectedTextContext,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                )
                AgentInputBar(
                    enabled = !state.isRunning,
                    selectedPlatforms = state.selectedPlatforms,
                    onPlatformClick = { showPlatformPicker = true },
                    onSend = state::send,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (showConversationManager) {
            AgentConversationDrawer(
                conversations = state.conversations,
                currentConversationId = state.conversationId,
                onDismiss = { showConversationManager = false },
                onNewConversation = {
                    state.startNewConversation()
                    showConversationManager = false
                },
                onOpenConversation = {
                    state.openConversation(it)
                    showConversationManager = false
                },
                onDeleteConversation = state::deleteConversation,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
    if (showPlatformPicker) {
        AgentPlatformPickerDialog(
            availablePlatforms = state.availablePlatforms,
            selectedPlatforms = state.selectedPlatforms,
            onDismiss = { showPlatformPicker = false },
            onAllPlatforms = state::clearPlatforms,
            onTogglePlatform = state::togglePlatform,
        )
    }
}

@Composable
private fun AgentContextBars(
    selectedStatusPlatform: String?,
    selectedStatusText: String?,
    includeStatusContext: Boolean,
    selectedText: String?,
    includeSelectedTextContext: Boolean,
    onDismissStatus: () -> Unit,
    onDismissSelectedText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (includeStatusContext && !selectedStatusText.isNullOrBlank()) {
            AgentContextBar(
                label = selectedStatusPlatform?.takeIf { it.isNotBlank() } ?: "帖子",
                text = selectedStatusText,
                onDismiss = onDismissStatus,
            )
        }
        if (includeSelectedTextContext && !selectedText.isNullOrBlank()) {
            AgentContextBar(
                label = "引用",
                text = selectedText,
                onDismiss = onDismissSelectedText,
            )
        }
    }
}

@Composable
private fun AgentContextBar(
    label: String,
    text: String,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformContextBadge(label)
            Text(
                text = text.compactContextPreview(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

@Composable
private fun PlatformContextBadge(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label.take(1),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun String.compactContextPreview(): String =
    replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length > 80) it.take(80) + "..." else it }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEmptyState(
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "有什么可以帮忙的？",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(28.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentPromptPill("总结微博最近的AI相关帖子", onPrompt)
            AgentPromptPill("足球圈有什么大事", onPrompt)
            AgentPromptPill("知乎有什么值得看的回答", onPrompt)
        }
    }
}

@Composable
private fun AgentPromptPill(
    text: String,
    onPrompt: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.clickable { onPrompt(text) },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentPlatformPickerDialog(
    availablePlatforms: List<String>,
    selectedPlatforms: List<String>,
    onDismiss: () -> Unit,
    onAllPlatforms: () -> Unit,
    onTogglePlatform: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "搜索平台",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedPlatforms.isEmpty(),
                        onClick = onAllPlatforms,
                        label = { Text("全部平台") },
                        leadingIcon =
                            if (selectedPlatforms.isEmpty()) {
                                {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    availablePlatforms.forEach { platform ->
                        val selected = selectedPlatforms.contains(platform)
                        FilterChip(
                            selected = selected,
                            onClick = { onTogglePlatform(platform) },
                            label = { Text(platform) },
                            leadingIcon =
                                if (selected) {
                                    {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun AgentConversationDrawer(
    conversations: List<AgentConversationSummary>,
    currentConversationId: String?,
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            modifier =
                modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .clickable { },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "会话",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNewConversation) {
                        Text("新对话")
                    }
                }
                if (conversations.isEmpty()) {
                    Text(
                        text = "还没有历史会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(conversations, key = { it.id }) { conversation ->
                            AgentConversationRow(
                                conversation = conversation,
                                selected = conversation.id == currentConversationId,
                                onOpen = { onOpenConversation(conversation.id) },
                                onDelete = { onDeleteConversation(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentConversationRow(
    conversation: AgentConversationSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = conversation.title.ifBlank { "新对话" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    text = conversation.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Trash,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AgentStatusRow(
    status: String,
    running: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AgentMessageBubble(
    role: String,
    text: String,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
) {
    val isUser = role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isUser) {
            AgentMessageContextArtifacts(
                artifacts = artifacts,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = text.ifBlank { "..." },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            AgentMessageContent(
                text = text.ifBlank { "..." },
                artifacts = artifacts,
                navigate = navigate,
            )
        }
    }
}

@Composable
private fun AgentMessageContextArtifacts(
    artifacts: List<AgentNativeArtifact>,
    modifier: Modifier = Modifier,
) {
    val contextArtifacts =
        artifacts.filter {
            it is AgentNativeArtifact.StatusContextRef || it is AgentNativeArtifact.TextQuoteRef
        }
    if (contextArtifacts.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        contextArtifacts.forEach { artifact ->
            when (artifact) {
                is AgentNativeArtifact.StatusContextRef -> AgentStatusContextCard(artifact)
                is AgentNativeArtifact.TextQuoteRef -> AgentTextQuoteContextCard(artifact)
                else -> Unit
            }
        }
    }
}

@Composable
private fun AgentStatusContextCard(artifact: AgentNativeArtifact.StatusContextRef) {
    AgentContextSnapshotCard(
        label = artifact.platform?.takeIf { it.isNotBlank() } ?: "帖子",
        title = "当前用户选择的帖子",
        text = artifact.text,
    )
}

@Composable
private fun AgentTextQuoteContextCard(artifact: AgentNativeArtifact.TextQuoteRef) {
    AgentContextSnapshotCard(
        label = "引",
        title = "用户引用的文本",
        text = artifact.text,
    )
}

@Composable
private fun AgentContextSnapshotCard(
    label: String,
    title: String,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PlatformContextBadge(label)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = text.compactContextPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun AgentMessageContent(
    text: String,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var cursor = 0
        cardRegex.findAll(text).forEach { match ->
            val before = text.substring(cursor, match.range.first)
            if (before.isNotBlank()) {
                AgentMarkdownText(before, artifacts, navigate)
            }
            val token = match.toReferenceToken()
            val item = artifacts.resolveItem(token.platform, token.id)
            if (item != null) {
                AgentTimelineCard(item, navigate)
            } else {
                AgentUnresolvedCard(token)
            }
            cursor = match.range.last + 1
        }
        val tail = text.substring(cursor)
        if (tail.isNotBlank() || cursor == 0) {
            AgentMarkdownText(tail, artifacts, navigate)
        }
    }
}

@Composable
private fun AgentUnresolvedCard(token: AgentReferenceToken) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "未找到对应帖子",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "${token.platform} / ${token.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun AgentReferenceLink(
    label: String,
    item: AgentTimelineItem?,
    deeplink: String? = item?.deeplink,
    navigate: (Route) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier.clickable(enabled = !deeplink.isNullOrBlank()) {
                val value = deeplink ?: return@clickable
                Route.parse(value)?.let(navigate) ?: uriHandler.openUri(value)
            },
    )
}

@Composable
private fun AgentMarkdownText(
    text: String,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text
            .trim()
            .lines()
            .forEach { rawLine ->
                val line = rawLine.trimEnd()
                if (line.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                } else {
                    val (content, style) = line.markdownLineStyle()
                    val annotatedContent = content.markdownInline(artifacts)
                    ClickableText(
                        text = annotatedContent,
                        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
                        onClick = { offset ->
                            annotatedContent
                                .getStringAnnotations(INLINE_DEEPLINK_TAG, offset, offset)
                                .firstOrNull()
                                ?.item
                                ?.let { value ->
                                    Route.parse(value)?.let(navigate) ?: uriHandler.openUri(value)
                                }
                        },
                    )
                }
            }
    }
}

@Composable
private fun String.markdownLineStyle(): Pair<String, TextStyle> =
    when {
        startsWith("# ") ->
            drop(2) to MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        startsWith("## ") ->
            drop(3) to MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        startsWith("### ") ->
            drop(4) to MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        startsWith("- ") ->
            "• ${drop(2)}" to MaterialTheme.typography.bodyMedium
        else ->
            this to MaterialTheme.typography.bodyMedium
    }

@Composable
private fun String.markdownInline(artifacts: List<AgentNativeArtifact>): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var index = 0
        while (index < this@markdownInline.length) {
            val linkMatch = linkRegex.matchAt(this@markdownInline, index)
            if (linkMatch != null) {
                val token = linkMatch.toReferenceToken()
                val item = artifacts.resolveItem(token.platform, token.id)
                val label = token.label ?: item?.title ?: item?.authorName ?: token.id
                val deeplink = item?.deeplink
                val start = length
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(label)
                }
                if (!deeplink.isNullOrBlank()) {
                    addStringAnnotation(
                        tag = INLINE_DEEPLINK_TAG,
                        annotation = deeplink,
                        start = start,
                        end = length,
                    )
                }
                index = linkMatch.range.last + 1
                continue
            }
            when {
                startsWith("**", index) -> {
                    val end = indexOf("**", startIndex = index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(this@markdownInline[index])
                        index += 1
                    }
                }

                startsWith("`", index) -> {
                    val end = indexOf("`", startIndex = index + 1)
                    if (end > index) {
                        withStyle(
                            SpanStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = androidx.compose.ui.graphics.Color.Unspecified,
                            ),
                        ) {
                            append(substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(this@markdownInline[index])
                        index += 1
                    }
                }

                startsWith("*", index) -> {
                    val end = indexOf("*", startIndex = index + 1)
                    if (end > index) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(this@markdownInline[index])
                        index += 1
                    }
                }

                else -> {
                    append(this@markdownInline[index])
                    index += 1
                }
            }
        }
    }
}

@Composable
private fun AgentToolActivityRow(
    activity: AgentToolActivityView,
    navigate: (Route) -> Unit,
) {
    var expanded by remember(activity.id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activity.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = activity.description.ifBlank { activity.name },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ">",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (activity.artifacts.isNotEmpty()) {
                    AgentArtifactsContent(activity.artifacts, navigate)
                } else {
                    Text(
                        text = activity.resultPreview?.takeIf { it.isNotBlank() } ?: "没有可展示的卡片结果",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (activity.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentArtifactsContent(
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        artifacts.forEach { artifact ->
            when (artifact) {
                is AgentNativeArtifact.FeedCardRef -> AgentTimelineCard(artifact.item, navigate)
                is AgentNativeArtifact.DetailLinkRef ->
                    AgentReferenceLink(
                        label = artifact.text,
                        item = null,
                        deeplink = artifact.deeplink,
                        navigate = navigate,
                    )

                is AgentNativeArtifact.SubjectGroupRef -> AgentSubjectGroupCard(artifact, navigate)
                is AgentNativeArtifact.StatusContextRef -> AgentStatusContextCard(artifact)
                is AgentNativeArtifact.TextQuoteRef -> AgentTextQuoteContextCard(artifact)
            }
        }
    }
}

@Composable
private fun AgentTimelineCard(
    item: AgentTimelineItem,
    navigate: (Route) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AdaptiveCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !item.deeplink.isNullOrBlank()) {
                    val deeplink = item.deeplink ?: return@clickable
                    Route.parse(deeplink)?.let(navigate) ?: uriHandler.openUri(deeplink)
                },
        index = 0,
        totalCount = 1,
        respectTimelineMode = true,
    ) {
        StatusItem(item.toUiTimelinePost())
    }
}

@Composable
private fun AgentSubjectGroupCard(
    group: AgentNativeArtifact.SubjectGroupRef,
    navigate: (Route) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = group.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.items.take(4).forEach { item ->
                AgentTimelineCard(item, navigate)
            }
        }
    }
}

@Composable
private fun AgentInputBar(
    enabled: Boolean,
    selectedPlatforms: List<String>,
    onPlatformClick: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            onClick = onPlatformClick,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 1.dp,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = selectedPlatforms.platformSummary(),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 1.dp,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 9.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (text.isBlank()) {
                        Text(
                            text = "询问跨账号内容、聚合主体或总结 feed",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    onClick = {
                        val value = text
                        text = ""
                        onSend(value)
                    },
                    enabled = enabled && text.isNotBlank(),
                    shape = CircleShape,
                    color =
                        if (enabled && text.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SendArrowIcon(
                            color =
                                if (enabled && text.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendArrowIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 3.dp.toPx()
        val centerX = size.width / 2f
        val topY = size.height * 0.2f
        val bottomY = size.height * 0.82f
        val armY = size.height * 0.42f
        val armX = size.width * 0.26f
        drawLine(
            color = color,
            start = Offset(centerX, bottomY),
            end = Offset(centerX, topY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, topY),
            end = Offset(centerX - armX, armY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, topY),
            end = Offset(centerX + armX, armY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun List<String>.platformSummary(): String =
    if (isEmpty()) {
        "选择搜索平台，当前为全部平台"
    } else {
        "选择搜索平台，当前为${joinToString()}"
    }

private data class AgentReferenceToken(
    val kind: String,
    val platform: String,
    val id: String,
    val label: String?,
)

private const val INLINE_DEEPLINK_TAG = "agent_deeplink"

private val referenceRegex = Regex("""\{\{(card|link):([^:|}]+):([^|}]+)(?:\|([^}]+))?\}\}""")
private val cardRegex = Regex("""\{\{(card):([^:|}]+):([^|}]+)\}\}""")
private val linkRegex = Regex("""\{\{(link):([^:|}]+):([^|}]+)\|([^}]+)\}\}""")

private fun Regex.matchAt(
    input: String,
    index: Int,
): MatchResult? = find(input, index)?.takeIf { it.range.first == index }

private fun MatchResult.toReferenceToken(): AgentReferenceToken =
    AgentReferenceToken(
        kind = groupValues[1],
        platform = groupValues[2],
        id = groupValues[3],
        label = groupValues.getOrNull(4)?.takeIf { it.isNotBlank() },
    )

private fun List<AgentNativeArtifact>.resolveItem(
    platform: String,
    id: String,
): AgentTimelineItem? {
    val items =
        asSequence()
        .flatMap {
            when (it) {
                is AgentNativeArtifact.FeedCardRef -> sequenceOf(it.item)
                is AgentNativeArtifact.SubjectGroupRef -> it.items.asSequence()
                is AgentNativeArtifact.DetailLinkRef -> emptySequence()
                is AgentNativeArtifact.StatusContextRef -> emptySequence()
                is AgentNativeArtifact.TextQuoteRef -> emptySequence()
            }
        }
        .toList()
    return items.firstOrNull { it.matchesReference(platform, id) }
        ?: items.firstOrNull { it.matchesReferenceId(id) }
}

private fun AgentTimelineItem.matchesReference(
    platform: String,
    id: String,
): Boolean =
    this.platform.matchesPlatform(platform) && matchesReferenceId(id)

private fun AgentTimelineItem.matchesReferenceId(id: String): Boolean =
    referenceId == id ||
        this.id == id ||
        statusKey?.id == id ||
        statusKey?.let { "${it.host}:${it.id}" } == id

private fun String?.matchesPlatform(platform: String): Boolean {
    val left = this?.normalizeAgentPlatform() ?: return false
    val right = platform.normalizeAgentPlatform()
    return left == right
}

private fun String.normalizeAgentPlatform(): String =
    when (lowercase()) {
        "x", "twitter", "xqt" -> "x"
        "微博", "weibo", "vvo" -> "weibo"
        "小红书", "xiaohongshu" -> "xiaohongshu"
        "即刻", "jike" -> "jike"
        "懂球帝", "dongqiudi" -> "dongqiudi"
        "知乎", "zhihu" -> "zhihu"
        else -> lowercase()
    }
