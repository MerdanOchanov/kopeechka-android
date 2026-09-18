package app.kopeechka.finance.net

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Официальные курсы центральных банков.
 *
 * Каждый банк даёт курсы в своей валюте: ЦБ России — в рублях, Нацбанк
 * Казахстана — в тенге, ЦБ Узбекистана — в сумах. Чтобы получить курс
 * в основной валюте приложения, делим одно на другое: доллар в манатах =
 * доллар в рублях / манат в рублях. Поэтому годится любой источник, в чьём
 * списке есть и нужная валюта, и основная.
 *
 * Это банковский курс. Рыночный — там, где он есть, — по-прежнему вводится
 * руками: его не публикует ни один банк.
 */
object BankRates {

    /** Откуда брать курсы. */
    enum class Source(val base: String, val url: String) {
        CBR("RUB", "https://www.cbr.ru/scripts/XML_daily.asp"),
        NBK("KZT", "https://nationalbank.kz/rss/rates_all.xml"),
        CBU("UZS", "https://cbu.uz/uz/arkhiv-kursov-valyut/json/"),
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Курсы источника: валюта → сколько единиц базовой валюты источника за 1. */
    suspend fun fetch(source: Source): Map<String, Double> {
        // ЦБ России отдаёт windows-1251; нам нужны только латинские коды и цифры,
        // поэтому читаем байты как есть, не завися от поддержки кодировки на iOS
        val bytes = try {
            http.get(source.url).bodyAsBytes()
        } catch (e: Exception) {
            println("Kopeechka/Rates: ${source.name} не ответил — $e")
            throw SyncError("rates.err.offline")
        }
        val text = CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() }.concatToString()
        val rates = when (source) {
            Source.CBR -> parseCbr(text)
            Source.NBK -> parseNbk(text)
            Source.CBU -> parseCbu(text)
        }
        if (rates.isEmpty()) throw SyncError("rates.err.empty")
        return rates + (source.base to 1.0)
    }

    /**
     * Курсы в основной валюте [main] для валют [codes].
     * null — в списке источника нет основной валюты, пересчитать не из чего.
     */
    fun toMain(rates: Map<String, Double>, main: String, codes: Collection<String>): Map<String, Double>? {
        val mainInBase = rates[main]?.takeIf { it > 0 } ?: return null
        return codes.filter { it != main }
            .mapNotNull { code -> rates[code]?.let { code to it / mainInBase } }
            .toMap()
    }

    /** ЦБ России: <Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>92,1234</Value>. */
    fun parseCbr(xml: String): Map<String, Double> =
        VALUTE.findAll(xml).mapNotNull { m ->
            val block = m.groupValues[1]
            val code = tag(block, "CharCode") ?: return@mapNotNull null
            val nominal = tag(block, "Nominal")?.toDoubleOrNull() ?: 1.0
            val value = tag(block, "Value")?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            code to value / nominal
        }.toMap()

    /** Нацбанк Казахстана: <item><title>USD</title><description>470.12</description><quant>1</quant>. */
    fun parseNbk(xml: String): Map<String, Double> =
        ITEM.findAll(xml).mapNotNull { m ->
            val block = m.groupValues[1]
            val code = tag(block, "title") ?: return@mapNotNull null
            val value = tag(block, "description")?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            val quant = tag(block, "quant")?.toDoubleOrNull() ?: 1.0
            code to value / quant
        }.toMap()

    /** ЦБ Узбекистана: [{"Ccy":"USD","Rate":"12650.50","Nominal":"1"}]. */
    fun parseCbu(text: String): Map<String, Double> = runCatching {
        json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val code = o["Ccy"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val rate = o["Rate"]?.jsonPrimitive?.contentOrNull?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            val nominal = o["Nominal"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 1.0
            code to rate / nominal
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun tag(block: String, name: String): String? =
        Regex("<$name>\\s*([^<]*?)\\s*</$name>").find(block)?.groupValues?.get(1)

    private val VALUTE = Regex("<Valute[^>]*>([\\s\\S]*?)</Valute>")
    private val ITEM = Regex("<item>([\\s\\S]*?)</item>")
}
