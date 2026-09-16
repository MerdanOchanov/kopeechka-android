package app.kopeechka.finance.net

import app.kopeechka.finance.data.Lang
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Ошибка с ключом перевода: текст собирает вызывающий на языке интерфейса. */
/** Картинка для запроса к ИИ: содержимое в base64 и тип, например image/jpeg. */
data class AiImage(val base64: String, val mime: String = "image/jpeg")

class AiError(val key: String, val args: List<Any?> = emptyList()) : Exception(key) {
    fun text(l: Lang) = l.t(key, *args.toTypedArray())
}

/**
 * ИИ-советник: Claude, OpenAI, Gemini или свой endpoint.
 *
 * Запросы идут напрямую по HTTP через Ktor — общий код для Android и iOS.
 * Ключ пользователя нигде не сохраняется этим слоем: он приходит параметром.
 */
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

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(
        providerKey: String,
        model: String,
        apiKey: String,
        endpoint: String,
        prompt: String,
        images: List<AiImage> = emptyList(),
    ): String =
        when (providerKey) {
            "claude" -> askClaude(model, apiKey, prompt, images)
            "openai" -> askOpenAiCompatible("https://api.openai.com/v1", model, apiKey, prompt, images, "OpenAI")
            "gemini" -> askGemini(model, apiKey, prompt, images)
            else -> {
                if (endpoint.isBlank()) throw AiError("ai.err.noEndpoint")
                askOpenAiCompatible(endpoint.trim().trimEnd('/'), model, apiKey, prompt, images, "Endpoint")
            }
        }

    /** Claude: Messages API напрямую. */
    private suspend fun askClaude(model: String, apiKey: String, prompt: String, images: List<AiImage>): String {
        // Серверный откат на другую модель, если основная откажется отвечать (Opus 5 / Fable 5).
        val fallback = model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5")
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    // без картинок содержимое остаётся строкой — так же, как было до чеков
                    if (images.isEmpty()) {
                        put("content", prompt)
                    } else {
                        put("content", buildJsonArray {
                            images.forEach { img ->
                                add(buildJsonObject {
                                    put("type", "image")
                                    put("source", buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", img.mime)
                                        put("data", img.base64)
                                    })
                                })
                            }
                            add(buildJsonObject { put("type", "text"); put("text", prompt) })
                        })
                    }
                })
            })
            if (fallback) put("fallbacks", "default")
        }
        val obj = execute("Claude", "https://api.anthropic.com/v1/messages", body, model) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            if (fallback) header("anthropic-beta", "server-side-fallback-2026-07-01")
        }
        if (obj["stop_reason"]?.jsonPrimitive?.contentOrNull == "refusal") throw AiError("ai.err.refusal")
        val text = obj["content"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n").trim()
        if (text.isEmpty()) throw AiError("ai.err.claudeEmpty")
        return text
    }

    private suspend fun askOpenAiCompatible(
        base: String,
        model: String,
        apiKey: String,
        prompt: String,
        images: List<AiImage>,
        label: String,
    ): String {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    if (images.isEmpty()) {
                        put("content", prompt)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", prompt) })
                            images.forEach { img ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", "data:${img.mime};base64,${img.base64}") })
                                })
                            }
                        })
                    }
                })
            })
        }
        val obj = execute(label, "$base/chat/completions", body) {
            if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
        }
        return obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AiError("ai.err.empty", listOf(label))
    }

    private suspend fun askGemini(model: String, apiKey: String, prompt: String, images: List<AiImage>): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        images.forEach { img ->
                            add(
                                buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", img.mime)
                                        put("data", img.base64)
                                    })
                                },
                            )
                        }
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
        }
        val obj = execute("Gemini", "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body) {
            header("x-goog-api-key", apiKey)
        }
        val parts = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
        return parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("\n")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw AiError("ai.err.empty", listOf("Gemini"))
    }

    /** Общий разбор ответа: коды ошибок переводятся в понятные сообщения. */
    private suspend fun execute(
        label: String,
        url: String,
        body: JsonObject,
        model: String = "",
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): JsonObject {
        val claude = label == "Claude"
        val resp = try {
            http.post(url) {
                contentType(ContentType.Application.Json)
                configure()
                setBody(body.toString())
            }
        } catch (e: AiError) {
            throw e
        } catch (e: Exception) {
            throw if (claude) AiError("ai.err.claudeOffline") else AiError("ai.err.offline", listOf(label, e.message))
        }
        val text = resp.bodyAsText()
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = resp.status.value
        if (code !in 200..299) {
            val msg = obj?.get("error")?.let { e ->
                runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: e.toString()
            } ?: text.take(200)
            throw when {
                claude && code == 401 -> AiError("ai.err.claudeKey")
                claude && code == 403 -> AiError("ai.err.claudeForbidden", listOf(model))
                claude && code == 404 -> AiError("ai.err.claudeModel", listOf(model))
                claude && code == 429 -> AiError("ai.err.claudeRate")
                claude -> AiError("ai.err.claudeApi", listOf(code, msg))
                code == 401 || code == 403 -> AiError("ai.err.key", listOf(label))
                code == 404 -> AiError("ai.err.model", listOf(label))
                code == 429 -> AiError("ai.err.rate", listOf(label))
                else -> AiError("ai.err.code", listOf(label, code, msg))
            }
        }
        return obj ?: throw AiError("ai.err.parse", listOf(label))
    }
}
