package dev.dimension.flare.data.agent

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAgentArtifact
import dev.dimension.flare.data.database.app.model.DbAgentConversation
import dev.dimension.flare.data.database.app.model.DbAgentEvent
import dev.dimension.flare.data.database.app.model.DbAgentMessage
import dev.dimension.flare.data.database.cache.connect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class AgentConversationRepository(
    private val database: AppDatabase,
) {
    val conversations: Flow<List<AgentConversationSummary>> =
        database.agentDao().conversations().map { rows ->
            rows.map {
                AgentConversationSummary(
                    id = it.id,
                    title = it.title,
                    status = it.status,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at,
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun messages(conversationId: String?): Flow<List<AgentMessageView>> =
        if (conversationId == null) {
            flowOf(emptyList())
        } else {
            database.agentDao().messages(conversationId).flatMapLatest { messages ->
                val ids = messages.map { it.id }
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    database.agentDao().artifacts(ids).map { artifacts ->
                        val artifactsByMessage = artifacts.groupBy { it.message_id }
                        messages.map { message ->
                            AgentMessageView(
                                id = message.id,
                                conversationId = message.conversation_id,
                                role = message.role,
                                text = message.text,
                                createdAt = message.created_at,
                                artifacts =
                                    artifactsByMessage[message.id]
                                        .orEmpty()
                                        .mapNotNull { artifact ->
                                            runCatching {
                                                artifact.payload_json.decodeJson(AgentNativeArtifact.serializer())
                                            }.getOrNull()
                                        },
                            )
                        }
                    }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun conversationItems(conversationId: String?): Flow<List<AgentConversationItemView>> =
        if (conversationId == null) {
            flowOf(emptyList())
        } else {
            combine(
                database.agentDao().messages(conversationId),
                database.agentDao().events(conversationId),
            ) { messages, events ->
                messages to events
            }.flatMapLatest { (messages, events) ->
                val ids = messages.map { it.id }
                val artifactsFlow =
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        database.agentDao().artifacts(ids)
                    }
                artifactsFlow.map { artifacts ->
                    val artifactsByMessage = artifacts.groupBy { it.message_id }
                    val messageItems =
                        messages.map { message ->
                            AgentConversationItemView.Message(
                                value =
                                    AgentMessageView(
                                        id = message.id,
                                        conversationId = message.conversation_id,
                                        role = message.role,
                                        text = message.text,
                                        createdAt = message.created_at,
                                        artifacts =
                                            artifactsByMessage[message.id]
                                                .orEmpty()
                                                .mapNotNull { artifact ->
                                                    runCatching {
                                                        artifact.payload_json.decodeJson(AgentNativeArtifact.serializer())
                                                    }.getOrNull()
                                                },
                                    ),
                            )
                        }
                    val toolItems = events.toToolActivityItems()
                    (messageItems + toolItems).sortedWith(
                        compareBy<AgentConversationItemView> { it.createdAt }
                            .thenBy { it.sortOrder },
                    )
                }
            }
        }

    suspend fun ensureConversation(
        conversationId: String?,
        titleSeed: String,
    ): String {
        if (conversationId != null && database.agentDao().conversation(conversationId) != null) {
            return conversationId
        }
        val id = newId()
        val now = now()
        database.agentDao().insertConversation(
            DbAgentConversation(
                id = id,
                title = titleSeed.trim().take(32).ifBlank { "新对话" },
                status = AgentConversationStatus.Active.value,
                created_at = now,
                updated_at = now,
            ),
        )
        return id
    }

    suspend fun history(conversationId: String): List<AgentStoredMessage> =
        database.agentDao().messageSnapshot(conversationId).map {
            AgentStoredMessage(
                role = it.role,
                text = it.text,
            )
        }

    suspend fun replaceUserMessageFrom(
        messageId: String,
        text: String,
    ): AgentUserMessageRunTarget? {
        val message = database.agentDao().message(messageId)
            ?.takeIf { it.role == AgentMessageRole.User.value }
            ?: return null
        val artifacts = database.agentDao().artifactSnapshot(message.id).decodeArtifacts()
        database.connect {
            database.agentDao().deleteEventsFrom(message.conversation_id, message.created_at)
            database.agentDao().deleteMessagesFrom(message.conversation_id, message.created_at)
        }
        val newMessageId = addUserMessage(message.conversation_id, text, artifacts)
        setStatus(message.conversation_id, AgentConversationStatus.Active)
        return AgentUserMessageRunTarget(
            conversationId = message.conversation_id,
            messageId = newMessageId,
            text = text,
            artifacts = artifacts,
        )
    }

    suspend fun retryFromAssistantMessage(messageId: String): AgentUserMessageRunTarget? {
        val message = database.agentDao().message(messageId)
            ?.takeIf { it.role == AgentMessageRole.Assistant.value }
            ?: return null
        val previousUser =
            database.agentDao().previousMessage(
                conversationId = message.conversation_id,
                role = AgentMessageRole.User.value,
                createdAt = message.created_at,
            ) ?: return null
        return replaceUserMessageFrom(previousUser.id, previousUser.text)
    }

    suspend fun addUserMessage(
        conversationId: String,
        text: String,
        artifacts: List<AgentNativeArtifact> = emptyList(),
    ): String {
        val id = addMessage(
            conversationId = conversationId,
            role = AgentMessageRole.User.value,
            text = text,
        )
        insertArtifacts(id, artifacts)
        return id
    }

    suspend fun addAssistantMessage(
        conversationId: String,
        text: String,
        artifacts: List<AgentNativeArtifact>,
    ): String {
        val id =
            addMessage(
                conversationId = conversationId,
                role = AgentMessageRole.Assistant.value,
                text = text,
            )
        insertArtifacts(id, artifacts)
        return id
    }

    private suspend fun insertArtifacts(
        messageId: String,
        artifacts: List<AgentNativeArtifact>,
    ) {
        artifacts.forEach { artifact ->
            database.agentDao().insertArtifact(
                DbAgentArtifact(
                    id = newId(),
                    message_id = messageId,
                    type = artifact::class.simpleName ?: "artifact",
                    payload_json = artifact.encodeJson(AgentNativeArtifact.serializer()),
                    created_at = now(),
                ),
            )
        }
    }

    suspend fun recordEvent(
        conversationId: String,
        messageId: String?,
        event: FlareAgentEvent,
    ) {
        database.agentDao().insertEvent(
            DbAgentEvent(
                id = newId(),
                conversation_id = conversationId,
                message_id = messageId,
                type = event::class.simpleName ?: "event",
                payload_json = event.encodeJson(FlareAgentEvent.serializer()),
                created_at = now(),
            ),
        )
    }

    suspend fun setStatus(
        conversationId: String,
        status: AgentConversationStatus,
    ) {
        database.agentDao().updateConversationStatus(
            id = conversationId,
            status = status.value,
            updatedAt = now(),
        )
    }

    suspend fun touch(
        conversationId: String,
        title: String? = null,
        status: AgentConversationStatus = AgentConversationStatus.Active,
    ) {
        val current = database.agentDao().conversation(conversationId) ?: return
        database.agentDao().updateConversation(
            id = conversationId,
            title = title ?: current.title,
            status = status.value,
            updatedAt = now(),
        )
    }

    suspend fun deleteConversation(conversationId: String) {
        database.agentDao().deleteConversation(conversationId)
    }

    private suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
    ): String {
        val id = newId()
        val now = now()
        database.agentDao().insertMessage(
            DbAgentMessage(
                id = id,
                conversation_id = conversationId,
                role = role,
                text = text,
                created_at = now,
            ),
        )
        touch(conversationId)
        return id
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()
}

public const val AGENT_FAILURE_MESSAGE_PREFIX: String = "生成失败："

internal data class AgentUserMessageRunTarget(
    val conversationId: String,
    val messageId: String,
    val text: String,
    val artifacts: List<AgentNativeArtifact>,
)

private fun List<DbAgentArtifact>.decodeArtifacts(): List<AgentNativeArtifact> =
    mapNotNull { artifact ->
        runCatching {
            artifact.payload_json.decodeJson(AgentNativeArtifact.serializer())
        }.getOrNull()
    }

private fun List<DbAgentEvent>.toToolActivityItems(): List<AgentConversationItemView.ToolActivity> {
    val activities = linkedMapOf<String, AgentToolActivityView>()
    forEach { row ->
        val event =
            runCatching {
                row.payload_json.decodeJson(FlareAgentEvent.serializer())
            }.getOrNull()
        when (event) {
            is FlareAgentEvent.ToolCallStarted -> {
                activities[event.id] =
                    AgentToolActivityView(
                        id = event.id,
                        name = event.name,
                        description = event.description,
                        arguments = event.arguments,
                        createdAt = row.created_at,
                        isRunning = true,
                    )
            }

            is FlareAgentEvent.ToolCallCompleted -> {
                val previous = activities[event.id]
                activities[event.id] =
                    AgentToolActivityView(
                        id = event.id,
                        name = event.name,
                        description = event.description,
                        arguments = previous?.arguments.orEmpty(),
                        resultPreview = event.resultPreview,
                        artifacts = event.artifacts,
                        createdAt = previous?.createdAt ?: row.created_at,
                        isRunning = false,
                        isError = event.isError,
                    )
            }

            else -> Unit
        }
    }
    return activities.values.map { AgentConversationItemView.ToolActivity(it) }
}

public data class AgentConversationSummary(
    val id: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

public data class AgentMessageView(
    val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val artifacts: List<AgentNativeArtifact>,
)

public sealed interface AgentConversationItemView {
    public val id: String
    public val createdAt: Long
    public val sortOrder: Int

    public data class Message(
        val value: AgentMessageView,
    ) : AgentConversationItemView {
        override val id: String = value.id
        override val createdAt: Long = value.createdAt
        override val sortOrder: Int = if (value.role == AgentMessageRole.User.value) 0 else 2
    }

    public data class ToolActivity(
        val value: AgentToolActivityView,
    ) : AgentConversationItemView {
        override val id: String = value.id
        override val createdAt: Long = value.createdAt
        override val sortOrder: Int = 1
    }
}

public data class AgentToolActivityView(
    val id: String,
    val name: String,
    val description: String,
    val arguments: String,
    val resultPreview: String? = null,
    val artifacts: List<AgentNativeArtifact> = emptyList(),
    val createdAt: Long,
    val isRunning: Boolean = false,
    val isError: Boolean = false,
)

internal data class AgentStoredMessage(
    val role: String,
    val text: String,
)

internal enum class AgentMessageRole(val value: String) {
    User("user"),
    Assistant("assistant"),
}

internal enum class AgentConversationStatus(val value: String) {
    Active("active"),
    Paused("paused"),
    Completed("completed"),
    Failed("failed"),
}
