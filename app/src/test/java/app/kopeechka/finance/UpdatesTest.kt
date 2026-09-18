package app.kopeechka.finance

import app.kopeechka.finance.net.Updates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Номера версий сравниваются числами: строкой «1.10» оказалась бы старше «1.9». */
class UpdatesTest {

    @Test
    fun десятая_версия_новее_девятой() {
        assertTrue(Updates.isNewer("1.10", "1.9"))
        assertFalse(Updates.isNewer("1.9", "1.10"))
    }

    @Test
    fun приставка_v_и_лишний_ноль_не_мешают() {
        assertTrue(Updates.isNewer("v1.9", "1.8"))
        assertFalse(Updates.isNewer("1.8.0", "1.8"))
        assertFalse(Updates.isNewer("1.8", "1.8"))
    }
}
