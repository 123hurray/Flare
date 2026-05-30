package dev.dimension.flare.data.agent

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class AgentSourceContext(
    val selectedText: String? = null,
    val route: String? = null,
    val accountType: AccountType? = null,
    val statusKey: MicroBlogKey? = null,
    val selectedStatusPlatform: String? = null,
    val selectedStatusText: String? = null,
    val selectedStatusAuthorName: String? = null,
    val selectedStatusAuthorHandle: String? = null,
    val selectedStatusCreatedAtEpochMillis: Long? = null,
    val selectedStatusDeeplink: String? = null,
    val feedSnapshot: List<AgentTimelineItem> = emptyList(),
    val allowedPlatforms: List<String> = emptyList(),
)

@Serializable
public data class AgentRunRequest(
    val conversationId: String? = null,
    val userText: String,
    val sourceContext: AgentSourceContext = AgentSourceContext(),
)

@Serializable
public sealed interface FlareAgentEvent {
    @Serializable
    @SerialName("assistant_text_delta")
    public data class AssistantTextDelta(
        val text: String,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("assistant_text_complete")
    public data class AssistantTextComplete(
        val text: String,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("reasoning_status")
    public data class ReasoningStatus(
        val text: String,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("tool_call_started")
    public data class ToolCallStarted(
        val id: String,
        val name: String,
        val arguments: String,
        val description: String,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("tool_call_completed")
    public data class ToolCallCompleted(
        val id: String,
        val name: String,
        val description: String,
        val resultPreview: String,
        val artifacts: List<AgentNativeArtifact> = emptyList(),
        val isError: Boolean = false,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("native_artifact_emitted")
    public data class NativeArtifactEmitted(
        val artifact: AgentNativeArtifact,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("requires_user_input")
    public data class RequiresUserInput(
        val prompt: String,
        val reason: String,
    ) : FlareAgentEvent

    @Serializable
    @SerialName("completed")
    public data object Completed : FlareAgentEvent

    @Serializable
    @SerialName("failed")
    public data class Failed(
        val message: String,
    ) : FlareAgentEvent
}

@Serializable
public sealed interface AgentNativeArtifact {
    public val id: String

    @Serializable
    @SerialName("feed_card_ref")
    public data class FeedCardRef(
        override val id: String,
        val item: AgentTimelineItem,
    ) : AgentNativeArtifact

    @Serializable
    @SerialName("detail_link_ref")
    public data class DetailLinkRef(
        override val id: String,
        val text: String,
        val deeplink: String,
    ) : AgentNativeArtifact

    @Serializable
    @SerialName("subject_group_ref")
    public data class SubjectGroupRef(
        override val id: String,
        val title: String,
        val summary: String,
        val items: List<AgentTimelineItem>,
    ) : AgentNativeArtifact

    @Serializable
    @SerialName("status_context_ref")
    public data class StatusContextRef(
        override val id: String,
        val platform: String?,
        val text: String,
        val statusKey: MicroBlogKey? = null,
        val authorName: String? = null,
        val authorHandle: String? = null,
        val createdAtEpochMillis: Long? = null,
        val deeplink: String? = null,
    ) : AgentNativeArtifact

    @Serializable
    @SerialName("text_quote_ref")
    public data class TextQuoteRef(
        override val id: String,
        val text: String,
    ) : AgentNativeArtifact
}

@Serializable
public data class AgentTimelineItem(
    val id: String,
    val referenceId: String? = null,
    val kind: String,
    val title: String? = null,
    val text: String,
    val authorName: String? = null,
    val authorHandle: String? = null,
    val authorAvatarUrl: String? = null,
    val platform: String? = null,
    val createdAtEpochMillis: Long? = null,
    val accountType: AccountType? = null,
    val statusKey: MicroBlogKey? = null,
    val deeplink: String? = null,
    val mediaPreviewUrl: String? = null,
)

internal data class AgentToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

internal data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val description: String = "",
)

internal data class AgentToolResult(
    val text: String,
    val artifacts: List<AgentNativeArtifact> = emptyList(),
    val isError: Boolean = false,
)
