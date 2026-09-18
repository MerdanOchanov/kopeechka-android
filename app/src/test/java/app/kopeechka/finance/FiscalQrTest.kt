package app.kopeechka.finance

import app.kopeechka.finance.data.FiscalQr
import app.kopeechka.finance.data.localDateOrNull
import app.kopeechka.finance.data.toEpochDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscalQrTest {

    private fun day(y: Int, m: Int, d: Int) = localDateOrNull(y, m, d)!!.toEpochDay()

    @Test
    fun российский_чек_покупка() {
        val r = FiscalQr.parse("t=20260917T1530&s=1234.50&fn=9960440300000000&i=12345&fp=3456789012&n=1")!!
        assertEquals(day(2026, 9, 17), r.date)
        assertEquals(1234.5, r.amount, 0.001)
        assertFalse(r.income)
    }

    @Test
    fun возврат_покупки_это_приход() {
        assertTrue(FiscalQr.parse("t=20260917T153012&s=500.00&fn=1&i=2&fp=3&n=2")!!.income)
    }

    @Test
    fun казахстанская_ссылка_с_теми_же_параметрами() {
        val r = FiscalQr.parse("http://consumer.oofd.kz?i=123456&f=010101012345&s=1500.00&t=20260101T120000")!!
        assertEquals(day(2026, 1, 1), r.date)
        assertEquals(1500.0, r.amount, 0.001)
    }

    @Test
    fun подпись_без_копеек_не_принимается_за_сумму() {
        val r = FiscalQr.parse("https://ofd.soliq.uz/check?t=EZ000000000000&r=123&c=20260917123456&s=987654321")!!
        assertEquals(day(2026, 9, 17), r.date)
        assertEquals(0.0, r.amount, 0.001)
    }

    @Test
    fun обычная_ссылка_это_не_чек() {
        assertNull(FiscalQr.parse("https://example.com/menu"))
        assertNull(FiscalQr.parse("просто текст"))
    }
}
