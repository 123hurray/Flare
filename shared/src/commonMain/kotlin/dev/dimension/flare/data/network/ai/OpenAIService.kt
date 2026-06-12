package dev.dimension.flare.data.network.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.Effort
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import dev.dimension.flare.common.BuildConfig
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.FlareLogger
import dev.dimension.flare.data.network.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes

internal class OpenAIService {
    private val transcriptionClient by lazy {
        HttpClient(httpClientEngine) {
            install(Logging) {
                logger = FlareLogger
                level =
                    if (BuildConfig.debug) {
                        LogLevel.ALL
                    } else {
                        LogLevel.BODY
                    }
            }
        }
    }

    suspend fun models(
        serverUrl: String,
        apiKey: String,
    ): List<String> =
        createClient(
            serverUrl = serverUrl,
            apiKey = apiKey,
        ).models()
            .map { it.id.id }
            .sorted()

    suspend fun chatCompletion(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ): String =
        createClient(
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
        ).chatCompletion(
            request =
                ChatCompletionRequest(
                    model = ModelId(config.model),
                    messages =
                        listOf(
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt,
                            ),
                        ),
                    reasoningEffort = config.reasoningEffort.takeIf { it.isNotBlank() }?.let(::Effort),
                ),
        ).choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
            .trim()

    suspend fun chatCompletionOrNull(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ): String? =
        if (config.serverUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            null
        } else {
            chatCompletion(
                config = config,
                prompt = prompt,
            )
        }

    suspend fun transcribeAudio(
        config: AppSettings.AiConfig.Type.OpenAI,
        audio: ByteArray,
        fileName: String,
    ): String {
        val speechModel = config.speechModel.ifBlank { config.model }.trim()
        if (config.serverUrl.isBlank() || config.apiKey.isBlank() || speechModel.isBlank()) {
            throw IllegalStateException("AI speech model is not configured.")
        }
        if (speechModel.contains("qwen3-asr", ignoreCase = true) && !speechModel.contains("filetrans", ignoreCase = true)) {
            return transcribeQwenAsr(
                config = config,
                model = speechModel,
                audio = audio,
                fileName = fileName,
            )
        }
        val response =
            transcriptionClient.post(transcriptionsUrl(config.serverUrl)) {
                bearerAuth(config.apiKey)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("model", speechModel)
                            append(
                                key = "file",
                                value = audio,
                                headers =
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                        append(HttpHeaders.ContentType, fileName.audioMimeType())
                                    },
                            )
                        },
                    ),
                )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Speech transcription failed (${response.status.value}): ${body.take(600).ifBlank { "empty error body" }}",
            )
        }
        return extractTranscriptionText(body).ifBlank { body.trim() }
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    private suspend fun transcribeQwenAsr(
        config: AppSettings.AiConfig.Type.OpenAI,
        model: String,
        audio: ByteArray,
        fileName: String,
    ): String {
        val dataUrl = "data:${fileName.audioMimeType()};base64,${Base64.encode(audio)}"
        val request =
            JsonObject(
                mapOf(
                    "model" to JsonPrimitive(model.trim()),
                    "stream" to JsonPrimitive(false),
                    "messages" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "role" to JsonPrimitive("user"),
                                        "content" to
                                            JsonArray(
                                                listOf(
                                                    JsonObject(
                                                        mapOf(
                                                            "type" to JsonPrimitive("input_audio"),
                                                            "input_audio" to
                                                                JsonObject(
                                                                    mapOf(
                                                                        "data" to JsonPrimitive(dataUrl),
                                                                    ),
                                                                ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    ),
                                ),
                            ),
                        ),
                    "asr_options" to
                        JsonObject(
                            mapOf(
                                "enable_itn" to JsonPrimitive(false),
                            ),
                        ),
                ),
            )
        val response =
            transcriptionClient.post(chatCompletionsUrl(config.serverUrl)) {
                bearerAuth(config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(dev.dimension.flare.common.JSON.encodeToString(JsonElement.serializer(), request))
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Qwen ASR failed (${response.status.value}): ${body.take(600).ifBlank { "empty error body" }}",
            )
        }
        return extractTranscriptionText(body)
            .ifBlank {
                throw IllegalStateException("Qwen ASR returned no text: ${body.take(600)}")
            }
    }

    private fun createClient(
        serverUrl: String,
        apiKey: String,
    ): OpenAI =
        OpenAI(
            OpenAIConfig(
                host = OpenAIHost(baseUrl = serverUrl),
                token = apiKey,
                engine = httpClientEngine,
                timeout =
                    Timeout(
                        request = 1.minutes,
                        socket = 1.minutes,
                        connect = 1.minutes,
                    ),
                httpClientConfig = {
                    install(Logging) {
                        logger = FlareLogger
                        level =
                            if (BuildConfig.debug) {
                                LogLevel.ALL
                            } else {
                                LogLevel.BODY
                            }
                    }
                },
            ),
        )

    private fun transcriptionsUrl(serverUrl: String): String {
        val normalized = serverUrl.trimEnd('/')
        return if (normalized.endsWith("/audio/transcriptions")) {
            normalized
        } else {
            "$normalized/audio/transcriptions"
        }
    }

    private fun chatCompletionsUrl(serverUrl: String): String {
        val normalized = serverUrl.trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun extractTranscriptionText(body: String): String =
        runCatching {
            val root = dev.dimension.flare.common.JSON.parseToJsonElement(body).jsonObject
            root["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: root["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.let { choice ->
                        choice["message"]
                            ?.jsonObject
                            ?.get("content")
                            ?.contentTextOrNull()
                            ?: choice["delta"]
                                ?.jsonObject
                                ?.get("content")
                                ?.contentTextOrNull()
                    }
                ?: root["output"]
                    ?.jsonObject
                    ?.get("text")
                    ?.contentTextOrNull()
                ?: ""
        }.getOrDefault("")

    private fun JsonElement.contentTextOrNull(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonArray ->
                mapNotNull { element ->
                    runCatching {
                        element.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                }.joinToString("").takeIf { it.isNotBlank() }

            else -> null
        }

    private fun String.audioMimeType(): String =
        when (substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            else -> "audio/wav"
        }
}
