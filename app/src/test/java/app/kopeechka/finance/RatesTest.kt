package app.kopeechka.finance

import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.RateMode
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.rebaseRates
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * Два курса у валюты. В Туркменистане официальный доллар стоит около 3,5 маната,
 * на руках — около 19,5, и от выбора курса итог сбережений меняется в разы.
 */
class RatesTest {

    private val tm = Settings(
        mainCur = "TMT",
        rates = mapOf("TMT" to 1.0, "USD" to 3.5, "RUB" to 0.0385),
        marketRates = mapOf("USD" to 19.5),
    )

    private fun near(expected: Double, actual: Double, why: String) =
        assertEquals(why, expected, actual, 1e-9 * maxOf(1.0, abs(expected)))

    @Test
    fun итог_по_банку_и_по_рынку_различается() {
        val d = AppData(accounts = listOf(Account(id = "usd", name = "Доллары", cur = "USD", initial = 1000.0)))

        near(3500.0, Calc(d.copy(settings = tm)).totalMain, "по банку 1000 $ = 3 500 манат")
        near(19500.0, Calc(d.copy(settings = tm.copy(rateMode = RateMode.MARKET))).totalMain, "по рынку 1000 $ = 19 500 манат")
    }

    @Test
    fun золото_в_граммах_входит_в_итог_по_цене_грамма() {
        val d = AppData(
            accounts = listOf(Account(id = "gold", name = "Золото", cur = "XAU", initial = 12.5)),
            settings = tm.copy(rates = tm.rates + ("XAU" to 400.0)),
        )
        near(5000.0, Calc(d).totalMain, "12,5 г по 400 манат за грамм")
    }

    @Test
    fun без_рыночного_курса_валюта_считается_по_банку() {
        val c = Calc(AppData(settings = tm.copy(rateMode = RateMode.MARKET)))
        near(0.0385, c.rate("RUB"), "у рубля рыночного курса нет — берётся банковский")
        near(19.5, c.rate("USD"), "у доллара есть — берётся рыночный")
    }

    @Test
    fun смена_основной_валюты_сохраняет_оба_курса() {
        val (rates, market) = rebaseRates(tm, "USD")

        near(1.0, rates.getValue("USD"), "новая основная всегда 1")
        near(1.0 / 3.5, rates.getValue("TMT"), "манат по банку")
        near(0.0385 / 3.5, rates.getValue("RUB"), "рубль по банку")
        near(1.0 / 19.5, market.getValue("TMT"), "манат по рынку — свой делитель, разница не пропадает")
        assertEquals("у новой основной своего рыночного курса нет", false, market.containsKey("USD"))
    }

    @Test
    fun без_рыночных_курсов_пересчёт_прежний() {
        val (rates, market) = rebaseRates(tm.copy(marketRates = emptyMap()), "USD")
        near(1.0 / 3.5, rates.getValue("TMT"), "банковский пересчёт")
        assertEquals(emptyMap<String, Double>(), market)
    }
}
