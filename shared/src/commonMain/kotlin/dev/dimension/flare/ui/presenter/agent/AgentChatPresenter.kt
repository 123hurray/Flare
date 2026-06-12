package dev.dimension.flare.ui.presenter.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.agent.AgentConversationRepository
import dev.dimension.flare.data.agent.AgentConversationStatus
import dev.dimension.flare.data.agent.AgentConversationSummary
import dev.dimension.flare.data.agent.AgentConversationItemView
import dev.dimension.flare.data.agent.AgentNativeArtifact
import dev.dimension.flare.data.agent.AgentRunRequest
import dev.dimension.flare.data.agent.AgentRuntime
import dev.dimension.flare.data.agent.AgentSourceContext
import dev.dimension.flare.data.agent.AgentToolCatalog
import dev.dimension.flare.data.agent.AgentToolOption
import dev.dimension.flare.data.agent.FlareAgentEvent
import dev.dimension.flare.data.agent.AGENT_FAILURE_MESSAGE_PREFIX
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

public class AgentChatPresenter(
    private val initialConversationId: String? = null,
    private val sourceContext: AgentSourceContext = AgentSourceContext(),
) : PresenterBase<AgentChatPresenter.State>(),
    KoinComponent {
    private val repository: AgentConversationRepository by inject()
    private val accountRepository: AccountRepository by inject()
    private val runtime: AgentRuntime by inject()
    private val appDataStore: AppDataStore by inject()
    private val openAIService: OpenAIService by inject()

    @Immutable
    public interface State {
        public val conversationId: String?
        public val conversations: ImmutableList<AgentConversationSummary>
        public val conversationItems: ImmutableList<AgentConversationItemView>
        public val draftStreamingText: String
        public val streamingArtifacts: ImmutableList<AgentNativeArtifact>
        public val availablePlatforms: ImmutableList<String>
        public val selectedPlatforms: ImmutableList<String>
        public val availableTools: ImmutableList<AgentToolOption>
        public val selectedTools: ImmutableList<String>
        public val selectedStatusPlatform: String?
        public val selectedStatusText: String?
        public val selectedText: String?
        public val includeStatusContext: Boolean
        public val includeSelectedTextContext: Boolean
        public val isRunning: Boolean
        public val isTranscribingVoice: Boolean
        public val statusText: String?
        public val errorText: String?

        public fun send(text: String)

        public fun transcribeVoice(
            fileName: String,
            audio: ByteArray,
            onText: (String) -> Unit,
        )

        public fun cancelVoiceTranscription()

        public fun resumeAfterVerification()

        public fun editAndResend(
            messageId: String,
            text: String,
        )

        public fun retry(messageId: String)

        public fun togglePlatform(platform: String)

        public fun clearPlatforms()

        public fun toggleTool(toolName: String)

        public fun clearTools()

        public fun dismissStatusContext()

        public fun dismissSelectedTextContext()

        public fun openConversation(id: String)

        public fun startNewConversation()

        public fun deleteConversation(id: String)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        var conversationId by remember { mutableStateOf(initialConversationId) }
        val conversations by repository.conversations.collectAsState(emptyList())
        val conversationItems by repository.conversationItems(conversationId).collectAsState(emptyList())
        val accounts by accountRepository.allAccounts.collectAsState(emptyList())
        val availablePlatforms =
            remember(accounts, sourceContext.feedSnapshot) {
                accounts
                    .map { it.platformType.name.toAgentPlatformName() }
                    .plus(sourceContext.feedSnapshot.mapNotNull { it.platform?.toAgentPlatformName() })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
            }
        var draftStreamingText by remember { mutableStateOf("") }
        val streamingArtifacts = remember { mutableStateListOf<AgentNativeArtifact>() }
        var isRunning by remember { mutableStateOf(false) }
        var isTranscribingVoice by remember { mutableStateOf(false) }
        var voiceTranscriptionJob by remember { mutableStateOf<Job?>(null) }
        var verificationPauseMessageId by remember { mutableStateOf<String?>(null) }
        var statusText by remember { mutableStateOf<String?>(null) }
        var errorText by remember { mutableStateOf<String?>(null) }
        val selectedPlatforms = remember { mutableStateListOf<String>() }
        val selectedTools =
            remember {
                mutableStateListOf<String>().apply {
                    addAll(AgentToolCatalog.options.map { it.name })
                }
            }
        var includeStatusContext by remember(sourceContext.selectedStatusText) {
            mutableStateOf(!sourceContext.selectedStatusText.isNullOrBlank())
        }
        var includeSelectedTextContext by remember(sourceContext.selectedText) {
            mutableStateOf(!sourceContext.selectedText.isNullOrBlank())
        }

        suspend fun runConversation(
            id: String,
            userTextForModel: String,
        ) {
            val history = repository.history(id)
            draftStreamingText = ""
            streamingArtifacts.clear()
            statusText = null
            errorText = null
            isRunning = true
            repository.setStatus(id, AgentConversationStatus.Active)
            val completedText = StringBuilder()
            val completedArtifacts = mutableListOf<AgentNativeArtifact>()
            runtime
                .runConversation(
                    request =
                        AgentRunRequest(
                            conversationId = id,
                            userText = userTextForModel,
                            sourceContext =
                                sourceContext.copy(
                                    selectedText = null,
                                    allowedPlatforms = selectedPlatforms.toList(),
                                    allowedTools = selectedTools.toList(),
                                ),
                        ),
                    history = history,
                ).collect { event ->
                    repository.recordEvent(id, null, event)
                    when (event) {
                        is FlareAgentEvent.AssistantTextDelta -> {
                            draftStreamingText += event.text
                            completedText.append(event.text)
                        }

                        is FlareAgentEvent.AssistantTextComplete -> {
                            draftStreamingText = event.text
                            completedText.clear()
                            completedText.append(event.text)
                        }

                        is FlareAgentEvent.NativeArtifactEmitted -> {
                            streamingArtifacts += event.artifact
                            completedArtifacts += event.artifact
                        }

                        is FlareAgentEvent.ReasoningStatus -> {
                            statusText = event.text
                        }

                        is FlareAgentEvent.ToolCallStarted -> {
                            statusText = event.description
                        }

                        is FlareAgentEvent.ToolCallCompleted -> {
                            statusText = if (event.isError) "${event.description}失败" else event.description
                        }

                        is FlareAgentEvent.RequiresUserInput -> {
                            val messageId = repository.addAssistantMessage(id, event.prompt, completedArtifacts)
                            if (event.reason == "verification_required") {
                                verificationPauseMessageId = messageId
                            }
                            repository.setStatus(id, AgentConversationStatus.Paused)
                            draftStreamingText = ""
                            isRunning = false
                        }

                        FlareAgentEvent.Completed -> {
                            repository.addAssistantMessage(id, completedText.toString(), completedArtifacts)
                            repository.setStatus(id, AgentConversationStatus.Completed)
                            draftStreamingText = ""
                            streamingArtifacts.clear()
                            statusText = null
                            isRunning = false
                        }

                        is FlareAgentEvent.Failed -> {
                            val message = event.message
                            errorText = message
                            repository.addAssistantMessage(id, "$AGENT_FAILURE_MESSAGE_PREFIX$message", completedArtifacts)
                            repository.setStatus(id, AgentConversationStatus.Failed)
                            draftStreamingText = ""
                            streamingArtifacts.clear()
                            statusText = null
                            isRunning = false
                        }
                    }
                }
            isRunning = false
        }

        fun sendInternal(text: String) {
            if (text.isBlank() || isRunning) return
            scope.launch {
                val includeStatusInThisRun = includeStatusContext && !sourceContext.selectedStatusText.isNullOrBlank()
                val includeSelectedTextInThisRun = includeSelectedTextContext && !sourceContext.selectedText.isNullOrBlank()
                val contextArtifacts =
                    buildContextArtifacts(
                        platform = sourceContext.selectedStatusPlatform,
                        statusText = sourceContext.selectedStatusText,
                        authorName = sourceContext.selectedStatusAuthorName,
                        authorHandle = sourceContext.selectedStatusAuthorHandle,
                        createdAtEpochMillis = sourceContext.selectedStatusCreatedAtEpochMillis,
                        deeplink = sourceContext.selectedStatusDeeplink,
                        includeStatusContext = includeStatusInThisRun,
                        selectedText = sourceContext.selectedText,
                        includeSelectedTextContext = includeSelectedTextInThisRun,
                        statusKey = sourceContext.statusKey,
                    )
                val userTextForModel = text.withAgentContext(contextArtifacts)
                val id = repository.ensureConversation(conversationId, text)
                conversationId = id
                repository.addUserMessage(id, text, contextArtifacts)
                includeStatusContext = false
                includeSelectedTextContext = false
                runConversation(id, userTextForModel)
            }
        }

        return object : State {
            override val conversationId = conversationId
            override val conversations = conversations.toImmutableList()
            override val conversationItems = conversationItems.toImmutableList()
            override val draftStreamingText = draftStreamingText
            override val streamingArtifacts = streamingArtifacts.toImmutableList()
            override val availablePlatforms = availablePlatforms.toImmutableList()
            override val selectedPlatforms = selectedPlatforms.toImmutableList()
            override val availableTools = AgentToolCatalog.options.toImmutableList()
            override val selectedTools = selectedTools.toImmutableList()
            override val selectedStatusPlatform = sourceContext.selectedStatusPlatform
            override val selectedStatusText = sourceContext.selectedStatusText
            override val selectedText = sourceContext.selectedText
            override val includeStatusContext = includeStatusContext
            override val includeSelectedTextContext = includeSelectedTextContext
            override val isRunning = isRunning
            override val isTranscribingVoice = isTranscribingVoice
            override val statusText = statusText
            override val errorText = errorText

            override fun send(text: String) {
                sendInternal(text)
            }

            override fun transcribeVoice(
                fileName: String,
                audio: ByteArray,
                onText: (String) -> Unit,
            ) {
                if (isTranscribingVoice || isRunning || audio.isEmpty()) return
                voiceTranscriptionJob =
                    scope.launch {
                        isTranscribingVoice = true
                        statusText = "正在识别语音"
                        errorText = null
                        val text =
                            runCatching {
                                val config = appDataStore.appSettingsStore.data.first().aiConfig.type as? AppSettings.AiConfig.Type.OpenAI
                                    ?: error("AI speech model is not configured.")
                                openAIService.transcribeAudio(
                                    config = config,
                                    audio = audio,
                                    fileName = fileName,
                                )
                            }.getOrElse {
                                if (it is CancellationException) {
                                    ""
                                } else {
                                    errorText = it.message ?: "语音识别失败"
                                    ""
                                }
                            }
                        if (text.isNotBlank()) {
                            onText(text)
                        }
                        statusText = null
                        isTranscribingVoice = false
                        voiceTranscriptionJob = null
                    }
            }

            override fun cancelVoiceTranscription() {
                voiceTranscriptionJob?.cancel()
                voiceTranscriptionJob = null
                isTranscribingVoice = false
                if (statusText == "正在识别语音") {
                    statusText = null
                }
            }

            override fun resumeAfterVerification() {
                val messageId = verificationPauseMessageId ?: return
                verificationPauseMessageId = null
                retry(messageId)
            }

            override fun editAndResend(
                messageId: String,
                text: String,
            ) {
                if (text.isBlank() || isRunning) return
                scope.launch {
                    val target = repository.replaceUserMessageFrom(messageId, text) ?: return@launch
                    conversationId = target.conversationId
                    runConversation(target.conversationId, target.text.withAgentContext(target.artifacts))
                }
            }

            override fun retry(messageId: String) {
                if (isRunning) return
                scope.launch {
                    val target = repository.retryFromAssistantMessage(messageId) ?: return@launch
                    conversationId = target.conversationId
                    runConversation(target.conversationId, target.text.withAgentContext(target.artifacts))
                }
            }

            override fun togglePlatform(platform: String) {
                if (selectedPlatforms.contains(platform)) {
                    selectedPlatforms.remove(platform)
                } else {
                    selectedPlatforms += platform
                }
            }

            override fun clearPlatforms() {
                selectedPlatforms.clear()
            }

            override fun toggleTool(toolName: String) {
                if (selectedTools.contains(toolName)) {
                    if (selectedTools.size > 1) {
                        selectedTools.remove(toolName)
                    }
                } else {
                    selectedTools += toolName
                }
            }

            override fun clearTools() {
                selectedTools.clear()
                selectedTools.addAll(AgentToolCatalog.options.map { it.name })
            }

            override fun dismissStatusContext() {
                includeStatusContext = false
            }

            override fun dismissSelectedTextContext() {
                includeSelectedTextContext = false
            }

            override fun openConversation(id: String) {
                if (isRunning) return
                conversationId = id
                draftStreamingText = ""
                streamingArtifacts.clear()
                statusText = null
                errorText = null
            }

            override fun startNewConversation() {
                if (isRunning) return
                conversationId = null
                draftStreamingText = ""
                streamingArtifacts.clear()
                statusText = null
                errorText = null
            }

            override fun deleteConversation(id: String) {
                scope.launch {
                    repository.deleteConversation(id)
                    if (conversationId == id) {
                        conversationId = null
                        draftStreamingText = ""
                        streamingArtifacts.clear()
                        statusText = null
                        errorText = null
                    }
                }
            }
        }
    }
}

private fun String.withAgentContext(artifacts: List<AgentNativeArtifact>): String =
    buildString {
        artifacts.forEach { artifact ->
            when (artifact) {
                is AgentNativeArtifact.StatusContextRef -> {
                    append("当前用户选择的帖子：")
                    append("平台：")
                    append(artifact.platform?.takeIf { it.isNotBlank() } ?: "未知")
                    append('\n')
                    append("账号：")
                    append(formatAgentAccount(artifact.authorName, artifact.authorHandle))
                    append('\n')
                    append("发帖时间：")
                    append(artifact.createdAtEpochMillis?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知")
                    append('\n')
                    append("正文：")
                    append(artifact.text)
                    append("\n\n")
                }

                is AgentNativeArtifact.TextQuoteRef -> {
                    append("用户引用的文本：")
                    append(artifact.text)
                    append("\n\n")
                }

                else -> Unit
            }
        }
        append(this@withAgentContext)
    }

private fun buildContextArtifacts(
    platform: String?,
    statusText: String?,
    authorName: String?,
    authorHandle: String?,
    createdAtEpochMillis: Long?,
    deeplink: String?,
    includeStatusContext: Boolean,
    selectedText: String?,
    includeSelectedTextContext: Boolean,
    statusKey: MicroBlogKey?,
): List<AgentNativeArtifact> =
    buildList {
        if (includeStatusContext && !statusText.isNullOrBlank()) {
            add(
                AgentNativeArtifact.StatusContextRef(
                    id = "status-context-${statusKey?.host.orEmpty()}-${statusKey?.id.orEmpty()}-${statusText.hashCode()}",
                    platform = platform,
                    text = statusText,
                    statusKey = statusKey,
                    authorName = authorName,
                    authorHandle = authorHandle,
                    createdAtEpochMillis = createdAtEpochMillis,
                    deeplink = deeplink,
                ),
            )
        }
        if (includeSelectedTextContext && !selectedText.isNullOrBlank()) {
            add(
                AgentNativeArtifact.TextQuoteRef(
                    id = "text-quote-${selectedText.hashCode()}",
                    text = selectedText,
                ),
            )
        }
    }

private fun formatAgentAccount(
    authorName: String?,
    authorHandle: String?,
): String {
    val name = authorName?.takeIf { it.isNotBlank() }
    val handle = authorHandle?.takeIf { it.isNotBlank() }
    return when {
        name != null && handle != null && name != handle -> "$name ($handle)"
        name != null -> name
        handle != null -> handle
        else -> "未知"
    }
}

private fun String.toAgentPlatformName(): String =
    when (lowercase()) {
        "vvo" -> "微博"
        "xiaohongshu" -> "小红书"
        "xqt" -> "X"
        "jike" -> "即刻"
        "dongqiudi" -> "懂球帝"
        "zhihu" -> "知乎"
        "rss" -> "RSS"
        else -> this
    }
