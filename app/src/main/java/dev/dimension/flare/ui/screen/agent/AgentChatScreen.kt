package dev.dimension.flare.ui.screen.agent

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import dev.dimension.flare.data.agent.AGENT_FAILURE_MESSAGE_PREFIX
import dev.dimension.flare.data.agent.AgentConversationSummary
import dev.dimension.flare.data.agent.AgentConversationItemView
import dev.dimension.flare.data.agent.AgentNativeArtifact
import dev.dimension.flare.data.agent.AgentSourceContext
import dev.dimension.flare.data.agent.AgentTimelineItem
import dev.dimension.flare.data.agent.AgentToolOption
import dev.dimension.flare.data.agent.AgentToolActivityView
import dev.dimension.flare.data.agent.toUiTimelinePost
import dev.dimension.flare.data.network.xiaohongshu.XhsVerificationRequiredException
import dev.dimension.flare.data.repository.VVOCaptchaRequiredException
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
import dev.dimension.flare.ui.screen.home.VvoCaptchaDialog
import dev.dimension.flare.ui.screen.home.XhsVerificationDialog
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Instant
import moe.tlaster.precompose.molecule.producePresenter
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

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
    selectedStatusAuthorName: String?,
    selectedStatusAuthorHandle: String?,
    selectedStatusCreatedAtEpochMillis: Long?,
    selectedStatusDeeplink: String?,
    sourceInstanceId: Long?,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    val sourceContext =
        remember(
            initialText,
            sourceRoute,
            statusKey,
            accountType,
            selectedStatusPlatform,
            selectedStatusText,
            selectedStatusAuthorName,
            selectedStatusAuthorHandle,
            selectedStatusCreatedAtEpochMillis,
            selectedStatusDeeplink,
            sourceInstanceId,
        ) {
            AgentSourceContext(
                selectedText = initialText,
                route = sourceRoute,
                statusKey = statusKey,
                accountType = accountType,
                selectedStatusPlatform = selectedStatusPlatform,
                selectedStatusText = selectedStatusText,
                selectedStatusAuthorName = selectedStatusAuthorName,
                selectedStatusAuthorHandle = selectedStatusAuthorHandle,
                selectedStatusCreatedAtEpochMillis = selectedStatusCreatedAtEpochMillis,
                selectedStatusDeeplink = selectedStatusDeeplink,
            )
        }
    val presenterKey =
        "agent_chat_${conversationId}_${sourceInstanceId}_${statusKey}_${initialText}_${selectedStatusPlatform}_${selectedStatusText.hashCode()}"
    val state by producePresenter(presenterKey) {
        remember(conversationId, sourceContext, initialText, sourceInstanceId) {
            AgentChatPresenter(
                initialConversationId = if (sourceInstanceId != null) null else conversationId,
                sourceContext = sourceContext,
            )
        }.invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showConversationManager by remember { mutableStateOf(false) }
    var showPlatformPicker by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<AgentPromptEditTarget?>(null) }
    var pendingVerification by remember { mutableStateOf<AgentNativeArtifact.VerificationRequiredRef?>(null) }
    var selectedToolActivity by remember { mutableStateOf<AgentToolActivityView?>(null) }
    LaunchedEffect(state.streamingArtifacts) {
        state.streamingArtifacts
            .filterIsInstance<AgentNativeArtifact.VerificationRequiredRef>()
            .lastOrNull()
            ?.let { pendingVerification = it }
    }
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
                        hasStatusContext = state.includeStatusContext && !state.selectedStatusText.isNullOrBlank(),
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
                                        onOpenVerification = { pendingVerification = it },
                                        onEdit =
                                            if (item.value.role == "user") {
                                                {
                                                    editingPrompt =
                                                        AgentPromptEditTarget(
                                                            messageId = item.value.id,
                                                            text = item.value.text,
                                                        )
                                                }
                                            } else {
                                                null
                                            },
                                        onRetry =
                                            if (item.value.role == "assistant" && item.value.text.startsWith(AGENT_FAILURE_MESSAGE_PREFIX)) {
                                                {
                                                    state.retry(item.value.id)
                                                }
                                            } else {
                                                null
                                            },
                                    )

                                is AgentConversationItemView.ToolActivity ->
                                    AgentToolActivityRow(
                                        activity = item.value,
                                        onOpen = { selectedToolActivity = item.value },
                                    )
                            }
                        }
                        if (state.draftStreamingText.isNotBlank() || state.streamingArtifacts.isNotEmpty()) {
                            item {
                                AgentMessageBubble(
                                    role = "assistant",
                                    text = state.draftStreamingText,
                                    artifacts = state.streamingArtifacts,
                                    navigate = navigate,
                                    onOpenVerification = { pendingVerification = it },
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
                    isTranscribingVoice = state.isTranscribingVoice,
                    onPlatformClick = { showPlatformPicker = true },
                    onSend = state::send,
                    onVoiceRecorded = state::transcribeVoice,
                    onCancelVoiceTranscription = state::cancelVoiceTranscription,
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
    selectedToolActivity?.let { activity ->
        AgentToolActivityDetailPage(
            activity = activity,
            navigate = navigate,
            onOpenVerification = { pendingVerification = it },
            onDismiss = { selectedToolActivity = null },
        )
    }
    if (showPlatformPicker) {
        AgentPlatformPickerDialog(
            availablePlatforms = state.availablePlatforms,
            selectedPlatforms = state.selectedPlatforms,
            availableTools = state.availableTools,
            selectedTools = state.selectedTools,
            onDismiss = { showPlatformPicker = false },
            onAllPlatforms = state::clearPlatforms,
            onTogglePlatform = state::togglePlatform,
            onAllTools = state::clearTools,
            onToggleTool = state::toggleTool,
        )
    }
    pendingVerification?.let { artifact ->
        when (artifact.platform) {
            "微博" -> {
                val key = artifact.accountKey
                if (key != null) {
                    VvoCaptchaDialog(
                        exception = VVOCaptchaRequiredException(key, artifact.url),
                        onDismiss = { pendingVerification = null },
                        onVerified = {
                            pendingVerification = null
                            state.resumeAfterVerification()
                        },
                    )
                } else {
                    AgentVerificationUnavailableDialog(
                        platform = artifact.platform,
                        message = artifact.message,
                        onDismiss = { pendingVerification = null },
                    )
                }
            }

            "小红书" -> {
                XhsVerificationDialog(
                    exception = XhsVerificationRequiredException(artifact.accountKey, artifact.message, artifact.url),
                    onDismiss = { pendingVerification = null },
                    onVerified = {
                        pendingVerification = null
                        state.resumeAfterVerification()
                    },
                )
            }

            else -> {
                AgentVerificationUnavailableDialog(
                    platform = artifact.platform,
                    message = artifact.message,
                    onDismiss = { pendingVerification = null },
                )
            }
        }
    }
    editingPrompt?.let { target ->
        AgentPromptEditDialog(
            initialText = target.text,
            onDismiss = { editingPrompt = null },
            onConfirm = { newText ->
                editingPrompt = null
                state.editAndResend(target.messageId, newText)
            },
        )
    }
}

private data class AgentPromptEditTarget(
    val messageId: String,
    val text: String,
)

@Composable
private fun AgentVerificationUnavailableDialog(
    platform: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$platform 验证") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
    )
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

private fun String.compactToolPreview(): String =
    replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length > 120) it.take(120) + "..." else it }

private fun AgentNativeArtifact.StatusContextRef.statusSubtitle(): String? {
    val account =
        listOfNotNull(
            authorName?.takeIf { it.isNotBlank() },
            authorHandle?.takeIf { it.isNotBlank() }?.let { handle ->
                if (authorName == handle) null else handle
            },
        ).joinToString(" / ")
    val createdAt = createdAtEpochMillis?.let { Instant.fromEpochMilliseconds(it).toString() }
    return listOfNotNull(account, createdAt)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

@Composable
private fun Modifier.agentCardChrome(shape: RoundedCornerShape = RoundedCornerShape(14.dp)): Modifier =
    this
        .shadow(elevation = 3.dp, shape = shape, clip = false)
        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEmptyState(
    hasStatusContext: Boolean,
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
            if (hasStatusContext) {
                AgentPromptPill("查找在Twitter上的原始来源", onPrompt)
                AgentPromptPill("Twitter上是否有相关讨论", onPrompt)
                AgentPromptPill("提取帖子里的链接", onPrompt)
            } else {
                AgentPromptPill("总结微博最近的AI相关帖子", onPrompt)
                AgentPromptPill("足球圈有什么大事", onPrompt)
                AgentPromptPill("知乎有什么值得看的回答", onPrompt)
            }
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
    availableTools: List<AgentToolOption>,
    selectedTools: List<String>,
    onDismiss: () -> Unit,
    onAllPlatforms: () -> Unit,
    onTogglePlatform: (String) -> Unit,
    onAllTools: () -> Unit,
    onToggleTool: (String) -> Unit,
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
                modifier =
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Agent 范围",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "平台",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "工具",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val allToolsSelected = selectedTools.size == availableTools.size
                    FilterChip(
                        selected = allToolsSelected,
                        onClick = onAllTools,
                        label = { Text("全部工具") },
                        leadingIcon =
                            if (allToolsSelected) {
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
                    availableTools.forEach { tool ->
                        val selected = selectedTools.contains(tool.name)
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleTool(tool.name) },
                            label = { Text(tool.label) },
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
    onOpenVerification: (AgentNativeArtifact.VerificationRequiredRef) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
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
                navigate = navigate,
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
            onEdit?.let {
                TextButton(onClick = it) {
                    Text("编辑")
                }
            }
        } else {
            AgentMessageContent(
                text = text,
                artifacts = artifacts,
                navigate = navigate,
                onOpenVerification = onOpenVerification,
            )
            onRetry?.let {
                TextButton(onClick = it) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun AgentPromptEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("编辑提示词")
        },
        text = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 4,
                    maxLines = 8,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                },
                enabled = text.isNotBlank(),
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AgentMessageContextArtifacts(
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
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
                is AgentNativeArtifact.StatusContextRef -> AgentStatusContextCard(artifact, navigate)
                is AgentNativeArtifact.TextQuoteRef -> AgentTextQuoteContextCard(artifact)
                else -> Unit
            }
        }
    }
}

@Composable
private fun AgentStatusContextCard(
    artifact: AgentNativeArtifact.StatusContextRef,
    navigate: (Route) -> Unit,
) {
    AgentContextSnapshotCard(
        label = artifact.platform?.takeIf { it.isNotBlank() } ?: "帖子",
        title = "当前用户选择的帖子",
        subtitle = artifact.statusSubtitle(),
        text = artifact.text,
        deeplink = artifact.deeplink,
        navigate = navigate,
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
    subtitle: String? = null,
    deeplink: String? = null,
    navigate: (Route) -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
        modifier =
            Modifier
                .agentCardChrome(shape)
                .clickable(enabled = !deeplink.isNullOrBlank()) {
                    val value = deeplink ?: return@clickable
                    Route.parse(value)?.let(navigate) ?: uriHandler.openUri(value)
                },
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
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
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
    onOpenVerification: (AgentNativeArtifact.VerificationRequiredRef) -> Unit = {},
) {
    val segments = remember(text) { text.toAgentMarkdownSegments() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        artifacts.filterIsInstance<AgentNativeArtifact.VerificationRequiredRef>().forEach { artifact ->
            AgentVerificationCard(artifact, onOpenVerification)
        }
        segments.forEach { segment ->
            when (segment) {
                is AgentMarkdownSegment.Card -> {
                    val item = artifacts.resolveItem(segment.token.platform, segment.token.id)
                    if (item != null) {
                        AgentTimelineCard(item, navigate)
                    } else {
                        AgentUnresolvedCard(segment.token)
                    }
                }

                is AgentMarkdownSegment.Markdown -> {
                    AgentMarkdownText(segment.text, artifacts, navigate)
                }
            }
        }
    }
}

@Composable
private fun AgentVerificationCard(
    artifact: AgentNativeArtifact.VerificationRequiredRef,
    onOpenVerification: (AgentNativeArtifact.VerificationRequiredRef) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformContextBadge(artifact.platform)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${artifact.platform} 需要验证",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = artifact.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = { onOpenVerification(artifact) }) {
                Text("打开")
            }
        }
    }
}

@Composable
private fun AgentUnresolvedCard(token: AgentReferenceToken) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    val visibleText = remember(text) { text.withoutTrailingPartialAgentToken().trim() }
    if (visibleText.isBlank()) {
        return
    }
    val document = remember(visibleText) { agentMarkdownParser.parse(visibleText) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        document.children().forEach { child ->
            AgentMarkdownBlock(child, artifacts, navigate)
        }
    }
}

@Composable
private fun AgentMarkdownBlock(
    node: Node,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
    depth: Int = 0,
) {
    when (node) {
        is Document -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children().forEach { AgentMarkdownBlock(it, artifacts, navigate, depth) }
            }
        }

        is Paragraph -> {
            AgentMarkdownInlineText(
                nodes = node.children().toList(),
                artifacts = artifacts,
                navigate = navigate,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is Heading -> {
            val style =
                when (node.level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    3 -> MaterialTheme.typography.titleSmall
                    else -> MaterialTheme.typography.bodyLarge
                }.copy(fontWeight = FontWeight.SemiBold)
            AgentMarkdownInlineText(
                nodes = node.children().toList(),
                artifacts = artifacts,
                navigate = navigate,
                style = style,
            )
        }

        is BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.children().filterIsInstance<ListItem>().forEach { item ->
                    AgentMarkdownListItem("•", item, artifacts, navigate, depth)
                }
            }
        }

        is OrderedList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                var number = node.startNumber
                node.children().filterIsInstance<ListItem>().forEach { item ->
                    AgentMarkdownListItem("${number}.", item, artifacts, navigate, depth)
                    number += 1
                }
            }
        }

        is BlockQuote -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    node.children().forEach { AgentMarkdownBlock(it, artifacts, navigate, depth + 1) }
                }
            }
        }

        is FencedCodeBlock -> AgentMarkdownCodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> AgentMarkdownCodeBlock(node.literal, null)
        is ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is HtmlBlock -> {
            Text(
                text = node.literal,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is TableBlock -> AgentMarkdownTable(node, artifacts, navigate)
        is TableHead, is TableBody, is TableRow, is TableCell -> {
            node.children().forEach { AgentMarkdownBlock(it, artifacts, navigate, depth) }
        }

        else -> {
            if (node.hasChildren()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    node.children().forEach { AgentMarkdownBlock(it, artifacts, navigate, depth) }
                }
            }
        }
    }
}

@Composable
private fun AgentMarkdownListItem(
    marker: String,
    item: ListItem,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
    depth: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item.children().forEach { AgentMarkdownBlock(it, artifacts, navigate, depth + 1) }
        }
    }
}

@Composable
private fun AgentMarkdownCodeBlock(
    literal: String,
    info: String?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            info?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = literal.trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AgentMarkdownTable(
    table: TableBlock,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        table.children().forEach { section ->
            section.children().filterIsInstance<TableRow>().forEach { row ->
                Row {
                    row.children().filterIsInstance<TableCell>().forEach { cell ->
                        Surface(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.widthIn(min = 120.dp, max = 220.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                AgentMarkdownInlineText(
                                    nodes = cell.children().toList(),
                                    artifacts = artifacts,
                                    navigate = navigate,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMarkdownInlineText(
    nodes: List<Node>,
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
    style: TextStyle,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val annotatedContent =
        remember(nodes, artifacts, linkColor, codeBackground) {
            buildAnnotatedString {
                nodes.forEach {
                    appendMarkdownInlineNode(
                        node = it,
                        artifacts = artifacts,
                        style = AgentInlineStyle(),
                        linkColor = linkColor,
                        codeBackground = codeBackground,
                    )
                }
            }
        }
    if (annotatedContent.text.isNotBlank()) {
        ClickableText(
            text = annotatedContent,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
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

private fun AnnotatedString.Builder.appendMarkdownInlineNode(
    node: Node,
    artifacts: List<AgentNativeArtifact>,
    style: AgentInlineStyle,
    linkColor: Color,
    codeBackground: Color,
) {
    when (node) {
        is MarkdownTextNode -> appendMarkdownLiteral(node.literal, artifacts, style, linkColor, codeBackground)
        is SoftLineBreak -> append("\n")
        is HardLineBreak -> append("\n")
        is Code -> appendStyledText(node.literal, style.copy(code = true), linkColor, codeBackground)
        is Emphasis -> node.children().forEach {
            appendMarkdownInlineNode(it, artifacts, style.copy(italic = true), linkColor, codeBackground)
        }

        is StrongEmphasis -> node.children().forEach {
            appendMarkdownInlineNode(it, artifacts, style.copy(bold = true), linkColor, codeBackground)
        }

        is Strikethrough -> node.children().forEach {
            appendMarkdownInlineNode(it, artifacts, style.copy(strike = true), linkColor, codeBackground)
        }

        is Link -> {
            val nextStyle = style.copy(url = node.destination)
            node.children().forEach { appendMarkdownInlineNode(it, artifacts, nextStyle, linkColor, codeBackground) }
        }

        is Image -> {
            appendStyledText(node.title?.takeIf { it.isNotBlank() } ?: "图片", style, linkColor, codeBackground)
        }

        is HtmlInline -> appendStyledText(node.literal, style, linkColor, codeBackground)
        else -> {
            if (node.hasChildren()) {
                node.children().forEach { appendMarkdownInlineNode(it, artifacts, style, linkColor, codeBackground) }
            }
        }
    }
}

@Composable
private fun AgentToolActivityRow(
    activity: AgentToolActivityView,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = shape,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
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
                )
            }
            Text(
                text = "工具：${activity.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            activity.arguments.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "参数：${it.compactToolPreview()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AgentToolActivityDetailPage(
    activity: AgentToolActivityView,
    navigate: (Route) -> Unit,
    onOpenVerification: (AgentNativeArtifact.VerificationRequiredRef) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = activity.description.ifBlank { activity.name },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "工具：${activity.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (activity.isRunning) "状态：调用中" else if (activity.isError) "状态：失败" else "状态：完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activity.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AgentToolDetailSection("调用参数", activity.arguments.ifBlank { "{}" })
                AgentToolDetailSection(
                    title = "调用结果",
                    value =
                        activity.result
                            ?.takeIf { it.isNotBlank() }
                            ?: activity.resultPreview
                            ?: if (activity.isRunning) "等待工具返回结果" else "没有文本结果",
                    error = activity.isError,
                )
                if (activity.artifacts.isNotEmpty()) {
                    Text(
                        text = "结果卡片",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    AgentArtifactsContent(activity.artifacts, navigate, onOpenVerification)
                }
            }
        }
    }
}

@Composable
private fun AgentToolDetailSection(
    title: String,
    value: String,
    error: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        AgentMarkdownCodeBlock(value.trim(), null)
    }
}

@Composable
private fun AgentArtifactsContent(
    artifacts: List<AgentNativeArtifact>,
    navigate: (Route) -> Unit,
    onOpenVerification: (AgentNativeArtifact.VerificationRequiredRef) -> Unit,
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
                is AgentNativeArtifact.StatusContextRef -> AgentStatusContextCard(artifact, navigate)
                is AgentNativeArtifact.TextQuoteRef -> AgentTextQuoteContextCard(artifact)
                is AgentNativeArtifact.VerificationRequiredRef -> AgentVerificationCard(artifact, onOpenVerification)
            }
        }
    }
}

@Composable
private fun AgentTimelineCard(
    item: AgentTimelineItem,
    navigate: (Route) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val uriHandler = LocalUriHandler.current
    AdaptiveCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .agentCardChrome(shape)
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
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    isTranscribingVoice: Boolean,
    onPlatformClick: () -> Unit,
    onSend: (String) -> Unit,
    onVoiceRecorded: (String, ByteArray, (String) -> Unit) -> Unit,
    onCancelVoiceTranscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf<AgentVoiceRecording?>(null) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var waveformLevels by remember { mutableStateOf(List(38) { 0.04f }) }
    fun startRecording() {
        if (!enabled || isTranscribingVoice || recording != null) return
        startAgentVoiceRecording(context)?.let {
            recording = it
            recordingSeconds = 0
            waveformLevels = List(38) { 0.04f }
        }
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            }
        }
    fun requestOrStartRecording() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun stopRecording() {
        val session = recording ?: return
        recording = null
        val bytes = stopAgentVoiceRecording(session)
        if (bytes.isNotEmpty()) {
            onVoiceRecorded(session.fileName, bytes) { recognizedText ->
                text = text.appendRecognizedVoiceText(recognizedText)
            }
        }
    }
    fun cancelRecording() {
        recording?.let {
            cancelAgentVoiceRecording(it)
        }
        recording = null
    }
    DisposableEffect(Unit) {
        onDispose {
            recording?.let {
                cancelAgentVoiceRecording(it)
            }
        }
    }
    LaunchedEffect(recording?.startedAtMillis) {
        var smoothedLevel = 0.04f
        while (recording != null) {
            val currentRecording = recording
            recordingSeconds = ((System.currentTimeMillis() - (currentRecording?.startedAtMillis ?: 0L)) / 1000L).toInt()
            val amplitude =
                currentRecording
                    ?.level
                    ?: 0f
            val level = kotlin.math.sqrt(amplitude.coerceIn(0f, 1f))
            smoothedLevel = (smoothedLevel * 0.25f + level * 0.75f).coerceIn(0.04f, 1f)
            waveformLevels = waveformLevels.drop(1) + smoothedLevel
            delay(50L)
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            recording != null -> {
                AgentRecordingBar(
                    seconds = recordingSeconds,
                    waveformLevels = waveformLevels,
                    onCancel = ::cancelRecording,
                    onStop = ::stopRecording,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            isTranscribingVoice -> {
                AgentVoiceTranscribingBar(
                    onCancel = onCancelVoiceTranscription,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                Row(
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
                                onClick = ::requestOrStartRecording,
                                enabled = enabled,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AgentMicrophoneIcon(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
    }
}

private class AgentVoiceRecording(
    val fileName: String,
    val audioRecord: AudioRecord,
    val running: AtomicBoolean,
    val pcmBuffer: ByteArrayOutputStream,
    val thread: Thread,
    val startedAtMillis: Long,
) {
    @Volatile
    var level: Float = 0f
}

@SuppressLint("MissingPermission")
private fun startAgentVoiceRecording(context: Context): AgentVoiceRecording? =
    runCatching {
        val sampleRate = 16_000
        val minBufferSize =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        val bufferSize = maxOf(minBufferSize, sampleRate / 5)
        val audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        val running = AtomicBoolean(true)
        val pcmBuffer = ByteArrayOutputStream()
        lateinit var session: AgentVoiceRecording
        val thread =
            Thread {
                val samples = ShortArray(bufferSize / 2)
                audioRecord.startRecording()
                while (running.get()) {
                    val count = audioRecord.read(samples, 0, samples.size)
                    if (count > 0) {
                        var sum = 0.0
                        synchronized(pcmBuffer) {
                            repeat(count) { index ->
                                val sample = samples[index].toInt()
                                sum += sample * sample
                                pcmBuffer.write(sample and 0xFF)
                                pcmBuffer.write((sample shr 8) and 0xFF)
                            }
                        }
                        val rms = kotlin.math.sqrt(sum / count) / 32768.0
                        session.level = (rms * 12.0).toFloat().coerceIn(0f, 1f)
                    }
                }
            }.apply {
                name = "agent-voice-recorder"
                isDaemon = true
            }
        session =
            AgentVoiceRecording(
                fileName = "agent_voice_${System.currentTimeMillis()}.wav",
                audioRecord = audioRecord,
                running = running,
                pcmBuffer = pcmBuffer,
                thread = thread,
                startedAtMillis = System.currentTimeMillis(),
            )
        thread.start()
        session
    }.getOrNull()

private fun stopAgentVoiceRecording(session: AgentVoiceRecording): ByteArray {
    session.running.set(false)
    runCatching { session.thread.join(500L) }
    runCatching { session.audioRecord.stop() }
    runCatching { session.audioRecord.release() }
    val pcm =
        synchronized(session.pcmBuffer) {
            session.pcmBuffer.toByteArray()
        }
    return pcm.toWavBytes(sampleRate = 16_000, channels = 1)
}

private fun cancelAgentVoiceRecording(session: AgentVoiceRecording) {
    session.running.set(false)
    runCatching { session.thread.join(300L) }
    runCatching { session.audioRecord.stop() }
    runCatching { session.audioRecord.release() }
}

private fun ByteArray.toWavBytes(
    sampleRate: Int,
    channels: Int,
): ByteArray {
    val byteRate = sampleRate * channels * 2
    val dataSize = size
    val output = ByteArrayOutputStream(44 + dataSize)
    output.writeAscii("RIFF")
    output.writeIntLe(36 + dataSize)
    output.writeAscii("WAVE")
    output.writeAscii("fmt ")
    output.writeIntLe(16)
    output.writeShortLe(1)
    output.writeShortLe(channels)
    output.writeIntLe(sampleRate)
    output.writeIntLe(byteRate)
    output.writeShortLe(channels * 2)
    output.writeShortLe(16)
    output.writeAscii("data")
    output.writeIntLe(dataSize)
    output.write(this)
    return output.toByteArray()
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    value.forEach { write(it.code) }
}

private fun ByteArrayOutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}

private fun ByteArrayOutputStream.writeShortLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}

@Composable
private fun AgentRecordingBar(
    seconds: Int,
    waveformLevels: List<Float>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(72.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White.copy(alpha = 0.86f))
            }
            AgentRecordingWaveform(
                levels = waveformLevels,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(30.dp),
            )
            Text(
                text = seconds.formatRecordingSeconds(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
            )
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AgentStopIcon(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AgentVoiceTranscribingBar(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(72.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                text = "正在识别语音",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun AgentRecordingWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val bars = levels.size.coerceAtLeast(1)
        val gap = size.width / bars
        levels.forEachIndexed { index, level ->
            val height = size.height * (0.12f + level.coerceIn(0f, 1f) * 0.88f)
            val x = index * gap
            drawLine(
                color = Color.White.copy(alpha = 0.42f + level.coerceIn(0f, 1f) * 0.5f),
                start = Offset(x, centerY - height / 2f),
                end = Offset(x, centerY + height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AgentMicrophoneIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        val centerX = size.width / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.36f, size.height * 0.1f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.14f, size.width * 0.14f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.68f),
            end = Offset(centerX, size.height * 0.88f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * 0.88f),
            end = Offset(size.width * 0.66f, size.height * 0.88f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.22f, size.height * 0.32f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.44f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun AgentStopIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.3f, size.height * 0.3f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
    }
}

private fun Int.formatRecordingSeconds(): String = "${this / 60}:${(this % 60).toString().padStart(2, '0')}"

private fun String.appendRecognizedVoiceText(value: String): String {
    val recognized = value.trim()
    if (recognized.isBlank()) return this
    return if (isBlank()) {
        recognized
    } else {
        trimEnd() + "\n" + recognized
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

private sealed interface AgentMarkdownSegment {
    data class Markdown(
        val text: String,
    ) : AgentMarkdownSegment

    data class Card(
        val token: AgentReferenceToken,
    ) : AgentMarkdownSegment
}

private data class AgentInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

private const val INLINE_DEEPLINK_TAG = "agent_deeplink"

private val agentMarkdownParser: Parser by lazy {
    Parser
        .builder()
        .extensions(
            listOf(
                StrikethroughExtension.create(),
                TablesExtension.create(),
            ),
        )
        .build()
}
private val cardRegex = Regex("""\{\{(card):([^:|}]+):([^|}]+)\}\}""")
private val linkRegex = Regex("""\{\{(link):([^:|}]+):([^|}]+)\|([^}]+)\}\}""")

private fun String.toAgentMarkdownSegments(): List<AgentMarkdownSegment> {
    val visibleText = withoutTrailingPartialAgentToken()
    if (visibleText.isBlank()) {
        return emptyList()
    }
    val segments = mutableListOf<AgentMarkdownSegment>()
    var cursor = 0
    cardRegex.findAll(visibleText).forEach { match ->
        val before = visibleText.substring(cursor, match.range.first)
        if (before.isNotBlank()) {
            segments += AgentMarkdownSegment.Markdown(before)
        }
        segments += AgentMarkdownSegment.Card(match.toReferenceToken())
        cursor = match.range.last + 1
    }
    val tail = visibleText.substring(cursor)
    if (tail.isNotBlank()) {
        segments += AgentMarkdownSegment.Markdown(tail)
    }
    return segments
}

private fun String.withoutTrailingPartialAgentToken(): String {
    val tokenStart = lastIndexOf("{{")
    if (tokenStart < 0) {
        return this
    }
    val tokenEnd = indexOf("}}", startIndex = tokenStart)
    return if (tokenEnd < 0) {
        substring(0, tokenStart)
    } else {
        this
    }
}

private fun MatchResult.toReferenceToken(): AgentReferenceToken =
    AgentReferenceToken(
        kind = groupValues[1],
        platform = groupValues[2],
        id = groupValues[3],
        label = groupValues.getOrNull(4)?.takeIf { it.isNotBlank() },
    )

private fun Node.children(): Sequence<Node> =
    sequence {
        var child = firstChild
        while (child != null) {
            yield(child)
            child = child.next
        }
    }

private fun Node.hasChildren(): Boolean = firstChild != null

private fun AnnotatedString.Builder.appendMarkdownLiteral(
    literal: String,
    artifacts: List<AgentNativeArtifact>,
    style: AgentInlineStyle,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < literal.length) {
        val linkMatch = linkRegex.find(literal, index)?.takeIf { it.range.first == index }
        if (linkMatch != null) {
            val token = linkMatch.toReferenceToken()
            val item = artifacts.resolveItem(token.platform, token.id)
            val label = token.label ?: item?.title ?: item?.authorName ?: token.id
            val deeplink = item?.deeplink
            appendStyledText(
                text = label,
                style = style.copy(url = deeplink),
                linkColor = linkColor,
                codeBackground = codeBackground,
            )
            index = linkMatch.range.last + 1
        } else {
            val nextToken = literal.indexOf("{{link:", startIndex = index).takeIf { it >= 0 } ?: literal.length
            if (nextToken == index) {
                appendStyledText(
                    text = literal.substring(index),
                    style = style,
                    linkColor = linkColor,
                    codeBackground = codeBackground,
                )
                return
            }
            appendStyledText(
                text = literal.substring(index, nextToken),
                style = style,
                linkColor = linkColor,
                codeBackground = codeBackground,
            )
            index = nextToken
        }
    }
}

private fun AnnotatedString.Builder.appendStyledText(
    text: String,
    style: AgentInlineStyle,
    linkColor: Color,
    codeBackground: Color,
) {
    if (text.isEmpty()) {
        return
    }
    val start = length
    val spanStyle =
        SpanStyle(
            color = if (style.url != null) linkColor else Color.Unspecified,
            fontWeight = if (style.bold) FontWeight.SemiBold else null,
            fontStyle = if (style.italic) FontStyle.Italic else null,
            fontFamily = if (style.code) FontFamily.Monospace else null,
            background = if (style.code) codeBackground else Color.Unspecified,
            textDecoration = style.textDecoration(),
        )
    withStyle(spanStyle) {
        append(text)
    }
    style.url?.takeIf { it.isNotBlank() }?.let { url ->
        addStringAnnotation(
            tag = INLINE_DEEPLINK_TAG,
            annotation = url,
            start = start,
            end = length,
        )
    }
}

private fun AgentInlineStyle.textDecoration(): TextDecoration? =
    when {
        url != null && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        url != null -> TextDecoration.Underline
        strike -> TextDecoration.LineThrough
        else -> null
    }

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
                is AgentNativeArtifact.VerificationRequiredRef -> emptySequence()
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
