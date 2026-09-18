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
 * ИИ-советник: Claude, OpenAI, Gemini, GigaChat, YandexGPT или свой endpoint.
 *
 * GigaChat и YandexGPT работают из России без VPN и оплачиваются рублями —
 * для многих в СНГ это единственный доступный вариант.
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
        /** Умеет ли модель читать фотографии — от этого зависит кнопка «чек по фото». */
        val images: Boolean = true,
    ) {
        fun name(l: Lang) = nameKey?.let { l.t(it) } ?: name
        fun vendor(l: Lang) = vendorKey?.let { l.t(it) } ?: vendor
    }

    val PROVIDERS = listOf(
        Provider("claude", "Claude", "Anthropic", "claude-opus-5", "sk-ant-"),
        Provider("openai", "OpenAI", "GPT", "gpt-5-mini", "sk-"),
        Provider("gemini", "Gemini", "Google", "gemini-2.5-flash", "AIza"),
        Provider("gigachat", "GigaChat", "Sber", "GigaChat-2", "", vendorKey = "ai.provider.sber", images = false),
        Provider("yandex", "YandexGPT", "Yandex Cloud", "yandexgpt-lite", "AQVN", images = false),
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
        folder: String = "",
    ): String =
        when (providerKey) {
            "claude" -> askClaude(model, apiKey, prompt, images)
            "openai" -> askOpenAiCompatible("https://api.openai.com/v1", model, apiKey, prompt, images, "OpenAI")
            "gemini" -> askGemini(model, apiKey, prompt, images)
            "gigachat" -> askGigaChat(model, apiKey, prompt)
            "yandex" -> askYandex(model, apiKey, folder, prompt)
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

    // ——— GigaChat ———

    /**
     * Ключ GigaChat — не токен, а «Authorization key» (base64 от client_id:secret).
     * Его меняют на токен доступа, который живёт 30 минут; держим токен в памяти.
     */
    private var gigaToken = ""
    private var gigaUntil = 0L
    private var gigaFor = 0

    private suspend fun gigaChatToken(authKey: String): String {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val key = authKey.trim().removePrefix("Basic ").trim()
        if (gigaToken.isNotEmpty() && gigaFor == key.hashCode() && now < gigaUntil - 60_000) return gigaToken
        val resp = try {
            http.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
                header("Authorization", "Basic $key")
                header("RqUID", rqUid())
                header("Accept", "application/json")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("scope=GIGACHAT_API_PERS")
            }
        } catch (e: Exception) {
            throw offline("GigaChat", e)
        }
        val obj = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
        if (resp.status.value in listOf(400, 401, 403)) throw AiError("ai.err.key", listOf("GigaChat"))
        val token = obj?.get("access_token")?.jsonPrimitive?.contentOrNull
            ?: throw AiError("ai.err.code", listOf("GigaChat", resp.status.value, obj?.toString()?.take(200).orEmpty()))
        gigaToken = token
        gigaUntil = obj["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: (now + 25 * 60_000)
        gigaFor = key.hashCode()
        return token
    }

    private suspend fun askGigaChat(model: String, authKey: String, prompt: String): String {
        val token = gigaChatToken(authKey)
        return try {
            askOpenAiCompatible("https://gigachat.devices.sberbank.ru/api/v1", model, token, prompt, emptyList(), "GigaChat")
        } catch (e: AiError) {
            // токен могли отозвать раньше срока — в следующий раз возьмём новый
            if (e.key == "ai.err.key") gigaToken = ""
            throw e
        }
    }

    /** Сбер требует уникальный идентификатор запроса в формате UUID. */
    private fun rqUid(): String {
        val hex = "0123456789abcdef"
        val s = (1..32).map { hex[kotlin.random.Random.nextInt(16)] }.joinToString("")
        return "${s.substring(0, 8)}-${s.substring(8, 12)}-4${s.substring(13, 16)}-a${s.substring(17, 20)}-${s.substring(20)}"
    }

    // ——— YandexGPT ———

    /**
     * Foundation Models API: ключ сервисного аккаунта и каталог. Модель — короткое
     * имя (`yandexgpt-lite`, `yandexgpt`, `yandexgpt/rc`) или полный `gpt://…`.
     */
    private suspend fun askYandex(model: String, apiKey: String, folder: String, prompt: String): String {
        val f = folder.trim()
        val uri = when {
            model.startsWith("gpt://") -> model
            f.isEmpty() -> throw AiError("ai.err.noFolder")
            '/' in model -> "gpt://$f/$model"
            else -> "gpt://$f/$model/latest"
        }
        val body = buildJsonObject {
            put("modelUri", uri)
            put("completionOptions", buildJsonObject {
                put("stream", false)
                put("temperature", 0.3)
                put("maxTokens", "4000")
            })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("text", prompt) })
            })
        }
        val obj = execute("YandexGPT", "https://llm.api.cloud.yandex.net/foundationModels/v1/completion", body) {
            header("Authorization", "Api-Key ${apiKey.trim()}")
            if (f.isNotEmpty()) header("x-folder-id", f)
        }
        return obj["result"]?.jsonObject?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AiError("ai.err.empty", listOf("YandexGPT"))
    }

    /**
     * Нет связи. Отдельно ловим сертификат: GigaChat подписан корневым
     * сертификатом Минцифры, которому телефоны по умолчанию не доверяют.
     */
    private fun offline(label: String, e: Exception): AiError {
        val text = listOfNotNull(e::class.simpleName, e.message, e.cause?.let { it::class.simpleName }, e.cause?.message)
            .joinToString(" ")
        val cert = listOf("SSL", "certif", "Certif", "trust", "Trust", "CertPath").any { it in text }
        return if (label == "GigaChat" && cert) AiError("ai.err.gigachatCert") else AiError("ai.err.offline", listOf(label, e.message))
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
            throw if (claude) AiError("ai.err.claudeOffline") else offline(label, e)
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
