package app.kopeechka.finance

import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.data.SmsSource
import app.kopeechka.finance.data.SmsWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** У одного банка несколько карт: правило выбирается по последним цифрам. */
class SmsRulesTest {

    private fun rule(id: String, acc: String, card: String = "") =
        SmsSource(id = id, name = "Банк", sender = "900", accId = acc, cardMask = card)

    private val salary = rule("r1", "salary", "4821")
    private val family = rule("r2", "family", "1177")
    private val other = rule("r3", "other")

    @Test
    fun смска_уходит_в_правило_своей_карты() {
        val text = "Покупка 350р карта *1177 MAGNIT"
        assertEquals("family", SmsParse.pick(listOf(salary, family, other), "900", text)?.accId)
    }

    @Test
    fun без_подходящих_цифр_берётся_правило_без_карты() {
        val text = "Покупка 350р карта *9999 MAGNIT"
        assertEquals("other", SmsParse.pick(listOf(salary, family, other), "900", text)?.accId)
    }

    @Test
    fun если_все_правила_с_картами_чужая_карта_не_разбирается() {
        val text = "Покупка 350р карта *9999 MAGNIT"
        assertNull(SmsParse.pick(listOf(salary, family), "900", text))
    }

    @Test
    fun цифры_карты_не_путаются_с_суммой() {
        assertFalse("48210 — это сумма, а не карта", SmsParse.hasCard("Покупка 48210р", "4821"))
        assertTrue(SmsParse.hasCard("Карта •4821: покупка 100р", "4821"))
    }

    @Test
    fun остаток_в_смске_не_мешает_разбору() {
        val old = rule("r", "a").copy(ignoreWords = SmsWords.IGNORE + ", баланс")
        val p = SmsParse.parse("Покупка 350р MAGNIT. Баланс: 12 000р", old, "RUB")
        assertNotNull("правило со старым «баланс» в пропуске всё равно разбирает покупку", p)
        assertEquals(350.0, p!!.amount, 0.001)
    }

    @Test
    fun туркменская_смска_с_манатами() {
        val tm = rule("t", "a").copy(
            expenseWords = "tölendi, töleg",
            incomeWords = "gelip gowuşdy",
        )
        val p = SmsParse.parse("Kart *1177: 120.50 TMT tölendi. Galyndy 900 TMT", tm, "TMT")
        assertNotNull(p)
        assertEquals(120.5, p!!.amount, 0.001)
        assertEquals("TMT", p.cur)
        assertFalse(p.income)
    }
}
