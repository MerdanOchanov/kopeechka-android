package app.kopeechka.finance

import app.kopeechka.finance.net.BankRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Разбор ответов центробанков и пересчёт в основную валюту. */
class BankRatesTest {

    private val cbr = """
        <ValCurs Date="17.09.2026" name="Foreign Currency Market">
        <Valute ID="R01235"><NumCode>840</NumCode><CharCode>USD</CharCode><Nominal>1</Nominal><Name>Доллар США</Name><Value>92,5000</Value></Valute>
        <Valute ID="R01710"><NumCode>934</NumCode><CharCode>TMT</CharCode><Nominal>1</Nominal><Name>Манат</Name><Value>26,4286</Value></Valute>
        <Valute ID="R01335"><NumCode>398</NumCode><CharCode>KZT</CharCode><Nominal>100</Nominal><Name>Тенге</Name><Value>19,2000</Value></Valute>
        </ValCurs>
    """.trimIndent()

    @Test
    fun цб_россии_учитывает_номинал() {
        val r = BankRates.parseCbr(cbr)
        assertEquals(92.5, r.getValue("USD"), 1e-9)
        assertEquals("100 тенге = 19,2 рубля", 0.192, r.getValue("KZT"), 1e-9)
    }

    @Test
    fun доллар_в_манатах_через_рубль() {
        val r = BankRates.parseCbr(cbr) + ("RUB" to 1.0)
        val tm = BankRates.toMain(r, "TMT", listOf("USD", "RUB", "TMT"))!!
        assertEquals(92.5 / 26.4286, tm.getValue("USD"), 1e-9)
        assertEquals(1 / 26.4286, tm.getValue("RUB"), 1e-9)
    }

    @Test
    fun без_основной_валюты_в_источнике_пересчитать_нельзя() {
        assertNull(BankRates.toMain(mapOf("USD" to 92.5, "RUB" to 1.0), "GEL", listOf("USD")))
    }

    @Test
    fun нацбанк_казахстана() {
        val xml = "<rss><channel><item><title>USD</title><pubDate>17.09.26</pubDate><description>470.12</description><quant>1</quant></item>" +
            "<item><title>RUB</title><description>5.10</description><quant>1</quant></item></channel></rss>"
        val r = BankRates.parseNbk(xml)
        assertEquals(470.12, r.getValue("USD"), 1e-9)
        assertEquals(5.10, r.getValue("RUB"), 1e-9)
    }

    @Test
    fun цб_узбекистана() {
        val r = BankRates.parseCbu("""[{"id":69,"Code":"840","Ccy":"USD","Rate":"12650.50","Nominal":"1"}]""")
        assertEquals(12650.5, r.getValue("USD"), 1e-9)
    }
}
