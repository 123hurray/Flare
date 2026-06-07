package dev.dimension.flare.data.agent

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface AgentRuntime {
    fun runConversation(
        request: AgentRunRequest,
        history: List<AgentStoredMessage>,
    ): Flow<FlareAgentEvent>
}

internal class KoogAgentRuntime(
    private val appDataStore: AppDataStore,
    private val tools: FlareAgentTools,
) : AgentRuntime {
    @OptIn(ExperimentalSerializationApi::class)
    private val wireJson =
        Json(JSON) {
            encodeDefaults = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(httpClientEngine) {
            install(HttpTimeout) {
                requestTimeoutMillis = MODEL_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = MODEL_REQUEST_TIMEOUT_MS
                connectTimeoutMillis = MODEL_CONNECT_TIMEOUT_MS
            }
            install(ContentNegotiation) {
                json(wireJson)
            }
        }
    }

    override fun runConversation(
        request: AgentRunRequest,
        history: List<AgentStoredMessage>,
    ): Flow<FlareAgentEvent> =
        flow {
            val config = appDataStore.appSettingsStore.data.first().aiConfig
            val openAIConfig = config.type as? AppSettings.AiConfig.Type.OpenAI
            if (openAIConfig == null || openAIConfig.serverUrl.isBlank() || openAIConfig.apiKey.isBlank() || openAIConfig.model.isBlank()) {
                val message = "AI is not configured. Please set OpenAI-compatible configuration in settings."
                emit(FlareAgentEvent.AssistantTextDelta(message))
                emit(FlareAgentEvent.AssistantTextComplete(message))
                emit(FlareAgentEvent.Completed)
                return@flow
            }

            val systemPrompt = config.agentPrompt.takeIf { it.isNotBlank() } ?: AiPromptDefaults.AGENT_PROMPT
            val messages = buildInitialMessages(request, history, systemPrompt).toMutableList()
            val toolSpecs = tools.specs(request.sourceContext)
            val allArtifacts = mutableListOf<AgentNativeArtifact>()
            repeat(MAX_AGENT_STEPS) {
                emit(FlareAgentEvent.ReasoningStatus("思考中"))
                val stepResult =
                    try {
                        streamOneModelStep(openAIConfig, messages, toolSpecs)
                    } catch (e: Exception) {
                        emit(FlareAgentEvent.Failed(e.message ?: "Model request failed."))
                        return@flow
                    }
                stepResult.events.forEach { emit(it) }
                if (stepResult.toolCalls.isEmpty()) {
                    if (stepResult.text.isBlank()) {
                        emit(FlareAgentEvent.Failed("Model returned an empty response."))
                        return@flow
                    }
                    val finalText = stepResult.text
                    emit(FlareAgentEvent.AssistantTextComplete(finalText))
                    emit(FlareAgentEvent.Completed)
                    return@flow
                }

                messages.add(
                    OpenAIMessage(
                        role = "assistant",
                        content = stepResult.text.ifBlank { null },
                        tool_calls = stepResult.toolCalls.map { it.toWire() },
                    ),
                )
                stepResult.toolCalls.forEach { toolCall ->
                    emit(
                        FlareAgentEvent.ToolCallStarted(
                            id = toolCall.id,
                            name = toolCall.name,
                            arguments = toolCall.arguments,
                            description = toolCall.description.ifBlank { toolCall.name },
                        ),
                    )
                    val result = tools.execute(toolCall, request.sourceContext)
                    allArtifacts += result.artifacts
                    result.artifacts.forEach {
                        emit(FlareAgentEvent.NativeArtifactEmitted(it))
                    }
                    emit(
                        FlareAgentEvent.ToolCallCompleted(
                            id = toolCall.id,
                            name = toolCall.name,
                            description = toolCall.description.ifBlank { toolCall.name },
                            resultPreview = result.text.take(600),
                            artifacts = result.artifacts,
                            isError = result.isError,
                        ),
                    )
                    messages.add(
                        OpenAIMessage(
                            role = "tool",
                            content = result.text,
                            tool_call_id = toolCall.id,
                        ),
                    )
                }
            }
            val prompt = "检索步骤已达到上限。请基于已得到的工具结果总结，或向用户说明还缺少什么。"
            emit(FlareAgentEvent.RequiresUserInput(prompt = prompt, reason = "max_steps"))
        }

    @Suppress("DEPRECATION")
    private suspend fun streamOneModelStep(
        config: AppSettings.AiConfig.Type.OpenAI,
        messages: List<OpenAIMessage>,
        toolSpecs: List<AgentToolSpec>,
    ): ModelStepResult {
        val response =
            client.post(chatCompletionsUrl(config.serverUrl)) {
                bearerAuth(config.apiKey)
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(
                    OpenAIChatRequest(
                        model = config.model,
                        messages = messages,
                        tools = toolSpecs.map { it.toWire() },
                        reasoning_effort = config.reasoningEffort.takeIf { it.isNotBlank() },
                    ),
                )
            }
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Model request failed (${response.status.value}): ${extractErrorMessage(response.bodyAsText())}",
            )
        }
        val channel = response.bodyAsChannel()
        val text = StringBuilder()
        val events = mutableListOf<FlareAgentEvent>()
        val builders = linkedMapOf<Int, ToolCallBuilder>()
        val rawBody = StringBuilder()
        var sawSsePayload = false
        while (true) {
            val rawLine = channel.readUTF8Line() ?: break
            rawBody.append(rawLine).append('\n')
            val line = rawLine.trim()
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            sawSsePayload = true
            val json =
                runCatching { wireJson.parseToJsonElement(payload).jsonObject }
                    .getOrNull()
                    ?: continue
            json.extractErrorMessageOrNull()?.let {
                throw IllegalStateException("Model stream failed: $it")
            }
            val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
            val delta = choice["delta"]?.jsonObject ?: continue
            delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                events += FlareAgentEvent.ReasoningStatus(it)
            }
            delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                text.append(it)
                events += FlareAgentEvent.AssistantTextDelta(it)
            }
            delta["tool_calls"]?.jsonArray?.forEach { element ->
                val toolCall = element.jsonObject
                val index = toolCall["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
                val builder = builders.getOrPut(index) { ToolCallBuilder(index = index) }
                toolCall["id"]?.jsonPrimitive?.contentOrNull?.let { builder.id = it }
                val function = toolCall["function"]?.jsonObject
                function?.get("name")?.jsonPrimitive?.contentOrNull?.let { builder.name.append(it) }
                function?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { builder.arguments.append(it) }
                }
        }
        if (!sawSsePayload) {
            return parseNonStreamingBody(rawBody.toString())
        }
        return ModelStepResult(
            text = text.toString(),
            events = events,
            toolCalls =
                builders.values.mapNotNull { builder ->
                    val name = builder.name.toString()
                    if (name.isBlank()) {
                        null
                    } else {
                        AgentToolCall(
                            id = builder.id ?: "tool-${builder.index}",
                            name = name,
                            arguments = builder.arguments.toString().ifBlank { "{}" },
                            description = builder.arguments.toString().toolDescription(),
                        )
                    }
                },
        )
    }

    private fun parseNonStreamingBody(rawBody: String): ModelStepResult {
        val body = rawBody.trim()
        if (body.isBlank()) {
            return ModelStepResult(text = "", events = emptyList(), toolCalls = emptyList())
        }
        val json =
            runCatching { wireJson.parseToJsonElement(body).jsonObject }
                .getOrElse {
                    throw IllegalStateException("Model returned an unrecognized response.")
                }
        json.extractErrorMessageOrNull()?.let {
            throw IllegalStateException("Model request failed: $it")
        }
        val message =
            json["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?: return ModelStepResult(text = "", events = emptyList(), toolCalls = emptyList())
        val text =
            message["content"]
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        val toolCalls =
            message["tool_calls"]
                ?.jsonArray
                ?.mapIndexedNotNull { index, element ->
                    val toolCall = element.jsonObject
                    val function = toolCall["function"]?.jsonObject ?: return@mapIndexedNotNull null
                    val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                    AgentToolCall(
                        id = toolCall["id"]?.jsonPrimitive?.contentOrNull ?: "tool-$index",
                        name = name,
                        arguments = function["arguments"]?.jsonPrimitive?.contentOrNull?.ifBlank { "{}" } ?: "{}",
                        description = function["arguments"]?.jsonPrimitive?.contentOrNull.toolDescription(),
                    )
                }.orEmpty()
        val events =
            if (text.isBlank()) {
                emptyList()
            } else {
                listOf(FlareAgentEvent.AssistantTextDelta(text))
            }
        return ModelStepResult(
            text = text,
            events = events,
            toolCalls = toolCalls,
        )
    }

    private fun chatCompletionsUrl(serverUrl: String): String {
        val normalized = serverUrl.trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun extractErrorMessage(body: String): String =
        runCatching {
            wireJson.parseToJsonElement(body).jsonObject.extractErrorMessageOrNull()
        }.getOrNull() ?: body.take(600).ifBlank { "empty error body" }

    private fun JsonObject.extractErrorMessageOrNull(): String? {
        val error = this["error"]
        if (error is JsonObject) {
            error["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                return it
            }
            error["type"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        } else if (error is JsonPrimitive) {
            error.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
        this["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        this["detail"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return null
    }

    private fun buildInitialMessages(
        request: AgentRunRequest,
        history: List<AgentStoredMessage>,
        systemPrompt: String,
    ): List<OpenAIMessage> =
        buildList {
            add(OpenAIMessage(role = "system", content = systemPrompt))
            request.sourceContext.statusKey?.let {
                add(OpenAIMessage(role = "system", content = "Current status key: ${it.id}@${it.host}"))
            }
            if (request.sourceContext.allowedPlatforms.isNotEmpty()) {
                add(
                    OpenAIMessage(
                        role = "system",
                        content = "User allowed searchable platforms: ${request.sourceContext.allowedPlatforms.joinToString()}. Do not search other platforms.",
                    ),
                )
            }
            val previousMessages =
                history
                    .takeLast(12)
                    .let { recent ->
                        if (recent.lastOrNull()?.role == "user") {
                            recent.dropLast(1)
                        } else {
                            recent
                        }
                    }
            previousMessages.forEach {
                add(OpenAIMessage(role = it.role, content = it.text))
            }
            add(OpenAIMessage(role = "user", content = request.userText))
        }

    private fun AgentToolSpec.toWire(): OpenAITool =
        OpenAITool(
            function =
                OpenAIFunctionTool(
                    name = name,
                    description = description,
                    parameters = parameters,
                ),
        )

    private fun AgentToolCall.toWire(): OpenAIToolCall =
        OpenAIToolCall(
            id = id,
            function =
                OpenAIFunctionCall(
                    name = name,
                    arguments = arguments,
                ),
        )

    private companion object {
        const val MAX_AGENT_STEPS = 6
        const val MODEL_REQUEST_TIMEOUT_MS = 120_000L
        const val MODEL_CONNECT_TIMEOUT_MS = 30_000L
    }
}

private fun String?.toolDescription(): String =
    this
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                JSON.parseToJsonElement(it).jsonObject["description"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }.orEmpty()

private data class ToolCallBuilder(
    val index: Int,
    var id: String? = null,
    val name: StringBuilder = StringBuilder(),
    val arguments: StringBuilder = StringBuilder(),
)

private data class ModelStepResult(
    val text: String,
    val events: List<FlareAgentEvent>,
    val toolCalls: List<AgentToolCall>,
)

@Serializable
private data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val stream: Boolean = true,
    val tools: List<OpenAITool>,
    val tool_choice: String = "auto",
    val reasoning_effort: String? = null,
)

@Serializable
private data class OpenAIMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<OpenAIToolCall>? = null,
    val tool_call_id: String? = null,
)

@Serializable
private data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunctionTool,
)

@Serializable
private data class OpenAIFunctionTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
private data class OpenAIToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAIFunctionCall,
)

@Serializable
private data class OpenAIFunctionCall(
    val name: String,
    val arguments: String,
)
