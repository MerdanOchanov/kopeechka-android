package app.kopeechka.finance

import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.data.SmsPresets
import app.kopeechka.finance.data.SmsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Настоящие сообщения банков Туркменистана (суммы и номера из примеров пользователя). */
class TmBankSmsTest {

    private fun rule(bank: String): SmsSource {
        val p = SmsPresets.TURKMENISTAN.first { it.name == bank }
        return SmsSource("r", p.name, "x", "card", expenseWords = p.expense, incomeWords = p.income, ignoreWords = p.ignore)
    }

    @Test
    fun rysgal_короткое_списание_с_минусом() {
        val r = SmsParse.parse("Pulyn mocberi -1.15 TMT . Pulyn galyndysy 59.39 TMT", rule("Rysgal"), "TMT")
        assertNotNull(r)
        assertEquals(1.15, r!!.amount, 0.001)
        assertFalse("минус — расход", r.income)
        assertEquals("TMT", r.cur)
    }

    @Test
    fun rysgal_короткое_пополнение() {
        val r = SmsParse.parse("Pulyn mocberi 1396.8 TMT . Pulyn galyndysy 1456.19 TMT", rule("Rysgal"), "TMT")!!
        assertEquals(1396.8, r.amount, 0.001)
        assertTrue(r.income)
    }

    @Test
    fun rysgal_покупка_по_карте() {
        val text = "Kartyn belgisi VISA Domestic MILLI *2099, boyunca amal E-commerce approved. Wagty 16.09.26 17:10. " +
            "Pulyn mocberi: 200.00 TMT satyjy 4814 300000000000007, satyjyn ady: TMCELL,  Yurt: TKM. " +
            "Kesgitleme belgisi: 16169472. Pulyn galyndysy 501.71 TMT."
        val r = SmsParse.parse(text, rule("Rysgal"), "TMT")!!
        assertEquals(200.0, r.amount, 0.001)
        assertFalse(r.income)
        assertEquals("TMCELL", r.title)
        assertEquals("2099", r.mask)
        assertTrue(SmsParse.hasCard(text, "2099"))
    }

    @Test
    fun turkmenbasy_покупка_без_валюты() {
        val text = "17.09.2026 19:16 Sowda 115.00 ***6415 185360 TEL. GURBANOW A.B., TM galyndy 1050.41"
        val r = SmsParse.parse(text, rule("Türkmenbaşy"), "TMT")!!
        assertEquals("не дата и не остаток", 115.0, r.amount, 0.001)
        assertFalse(r.income)
        assertEquals("TMT", r.cur)
        assertEquals("6415", r.mask)
        assertEquals("TEL. GURBANOW A.B", r.title)
    }
}
