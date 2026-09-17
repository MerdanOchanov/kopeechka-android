package app.kopeechka.finance.net

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Приём обмена в домашней сети: крошечный HTTP-сервер на одну ручку.
 *
 * Живёт, пока открыт экран «принять с другого телефона». Что делать с
 * пришедшим снимком, решает общий код — здесь только сеть.
 */
class LanHost {

    private var server: EmbeddedServer<*, *>? = null

    /** Возвращает «адрес:порт» или null, если у телефона нет адреса в локальной сети. */
    suspend fun start(onExchange: suspend (code: String, body: String) -> Pair<Int, String>): String? {
        stop()
        val ip = localAddress() ?: return null
        val port = PORTS.firstNotNullOfOrNull { p -> runCatching { launch(p, onExchange) }.getOrNull() } ?: return null
        return "$ip:$port"
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
    }

    private fun launch(port: Int, onExchange: suspend (String, String) -> Pair<Int, String>): Int {
        val s = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            routing {
                post("/exchange") {
                    val code = call.request.headers[LAN_CODE_HEADER].orEmpty()
                    val (status, body) = onExchange(code, call.receiveText())
                    call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
                }
            }
        }
        s.start(wait = false)
        server = s
        return port
    }

    /**
     * Адрес телефона в домашней сети. Берём IPv4 из частных диапазонов: его
     * человек сможет прочитать и ввести на втором телефоне.
     */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private companion object {
        /** Сначала постоянный порт — его легче запомнить; если занят, соседние. */
        val PORTS = listOf(8733, 8734, 8735)
    }
}
