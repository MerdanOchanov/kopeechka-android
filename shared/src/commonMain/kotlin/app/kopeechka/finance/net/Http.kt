package app.kopeechka.finance.net

import io.ktor.client.HttpClient

/**
 * HTTP-клиент общий для всех запросов приложения: ИИ-советник и Google Диск.
 * Движок у каждой платформы свой — OkHttp на Android, Darwin на iOS.
 */
internal expect fun createHttpClient(): HttpClient

internal val http: HttpClient by lazy { createHttpClient() }
