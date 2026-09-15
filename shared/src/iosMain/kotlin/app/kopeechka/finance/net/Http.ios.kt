package app.kopeechka.finance.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

internal actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
    }
}
