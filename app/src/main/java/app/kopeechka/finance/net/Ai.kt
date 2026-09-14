package app.kopeechka.finance.net

import app.kopeechka.finance.data.Lang
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ошибка с ключом перевода: текст собирает вызывающий на языке интерфейса. */
class AiError(val key: String, val args: List<Any?> = emptyList()) : Exception(key) {
    fun text(l: Lang) = l.t(key, *args.toTypedArray())
}

object Ai {
    data class Provider(
        val key: String,
        val name: String,
        val vendor: String,
        val defaultModel: String,
        val keyPrefix: String,
        val needsKey: Boolean = true,
        val nameKey: String? = null,
        val vendorKey: String? = null,
    ) {
        fun name(l: Lang) = nameKey?.let { l.t(it) } ?: name
        fun vendor(l: Lang) = vendorKey?.let { l.t(it) } ?: vendor
    }

    val PROVIDERS = listOf(
        Provider("claude", "Claude", "Anthropic", "claude-opus-5", "sk-ant-"),
        Provider("openai", "OpenAI", "GPT", "gpt-5-mini", "sk-"),
        Provider("gemini", "Gemini", "Google", "gemini-2.5-flash", "AIza"),
        Provider(
            key = "custom",
            name = "Custom endpoint",
            vendor = "OpenAI-compatible",
            defaultModel = "llama3.1",
            keyPrefix = "",
            needsKey = false,
            nameKey = "ai.provider.custom",
            vendorKey = "ai.provider.customVendor",
        ),
    )

    fun provider(key: String) = PROVIDERS.firstOrNull { it.key == key } ?: PROVIDERS[0]

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(providerKey: String, model: String, apiKey: String, endpoint: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            when (providerKey) {
                "claude" -> askClaude(model, apiKey, prompt)
                "openai" -> askOpenAiCompatible("https://api.openai.com/v1", model, apiKey, prompt, "OpenAI")
                "gemini" -> askGemini(model, apiKey, prompt)
                else -> {
                    if (endpoint.isBlank()) throw AiError("ai.err.noEndpoint")
                    askOpenAiCompatible(endpoint.trim().trimEnd('/'), model, apiKey, prompt, "Endpoint")
                }
            }
        }

    /** Claude — через официальный Java SDK Anthropic. */
    private fun askClaude(model: String, apiKey: String, prompt: String): String {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .addUserMessage(prompt)
            // Серверный откат на другую модель, если основная откажется отвечать (для Opus 5 / Fable 5).
            if (model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5")) {
                b.addBeta("server-side-fallback-2026-07-01")
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val msg = client.beta().messages().create(b.build())
            if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") {
                throw AiError("ai.err.refusal")
            }
            val text = msg.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("\n").trim()
            if (text.isEmpty()) throw AiError("ai.err.claudeEmpty")
            return text
        } catch (e: UnauthorizedException) {
            throw AiError("ai.err.claudeKey")
        } catch (e: PermissionDeniedException) {
            throw AiError("ai.err.claudeForbidden", listOf(model))
        } catch (e: NotFoundException) {
            throw AiError("ai.err.claudeModel", listOf(model))
        } catch (e: RateLimitException) {
            throw AiError("ai.err.claudeRate")
        } catch (e: AnthropicServiceException) {
            throw AiError("ai.err.claudeApi", listOf(e.statusCode(), e.message))
        } catch (e: AnthropicIoException) {
            throw AiError("ai.err.claudeOffline")
        } finally {
            client.close()
        }
    }

    private fun askOpenAiCompatible(base: String, model: String, apiKey: String, prompt: String, label: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        val req = Request.Builder()
            .url("$base/chat/completions")
            .post(body.toString().toRequestBody(jsonType))
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .build()
        val obj = execute(req, label)
        return obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AiError("ai.err.empty", listOf(label))
    }

    private fun askGemini(model: String, apiKey: String, prompt: String): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                })
            })
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val obj = execute(req, "Gemini")
        val parts = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
        return parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("\n")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw AiError("ai.err.empty", listOf("Gemini"))
    }

    private fun execute(req: Request, label: String): JsonObject {
        try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (!resp.isSuccessful) {
                    val msg = obj?.get("error")?.let { e ->
                        runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: e.toString()
                    } ?: text.take(200)
                    throw when (resp.code) {
                        401, 403 -> AiError("ai.err.key", listOf(label))
                        404 -> AiError("ai.err.model", listOf(label))
                        429 -> AiError("ai.err.rate", listOf(label))
                        else -> AiError("ai.err.code", listOf(label, resp.code, msg))
                    }
                }
                return obj ?: throw AiError("ai.err.parse", listOf(label))
            }
        } catch (e: IOException) {
            throw AiError("ai.err.offline", listOf(label, e.message))
        }
    }
}
