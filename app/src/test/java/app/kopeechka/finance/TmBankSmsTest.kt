package app.kopeechka.finance

import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.CAT_TRANSFER
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Category
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.data.SmsPresets
import app.kopeechka.finance.data.SmsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val cashText = "Nagt pul almak. Kartyn belgisi VISA Domestic MILLI *2099, pulyn mocberi 400.00 TMT v 000000000000062. " +
        "Pulyn galyndysy 88.22 TMT."

    @Test
    fun rysgal_снятие_наличных() {
        val r = SmsParse.parse(cashText, rule("Rysgal"), "TMT")!!
        assertEquals(400.0, r.amount, 0.001)
        assertFalse(r.income)
        assertTrue("это снятие, а не трата", r.cash)
        assertEquals("2099", r.mask)
    }

    @Test
    fun покупка_не_считается_снятием() {
        val r = SmsParse.parse("17.09.2026 19:16 Sowda 115.00 ***6415 185360 TEL. GURBANOW A.B., TM galyndy 1050.41", rule("Türkmenbaşy"), "TMT")!!
        assertFalse(r.cash)
    }

    /** Хранилище в памяти — как настоящее, только без файла. */
    private class MemStore(start: AppData) : Storage {
        private val flow = MutableStateFlow(start)
        override val data: StateFlow<AppData> = flow
        override val current: AppData get() = flow.value
        override fun update(f: (AppData) -> AppData) { flow.value = f(flow.value) }
        override fun replace(d: AppData) { flow.value = d }
        override fun applyMerged(d: AppData) { flow.value = d }
        override fun exportJson() = ""
        override fun parseBackup(text: String) = current
    }

    private fun cashStore(auto: Boolean, withWallet: Boolean = true): MemStore {
        val p = SmsPresets.TURKMENISTAN.first { it.name == "Rysgal" }
        val accounts = listOf(Account("card", "Rysgal", "Карта", "•• 2099", "TMT", 1000.0)) +
            if (withWallet) listOf(Account("cash", "Кошелёк", "Кошелёк", "", "TMT", 0.0)) else emptyList()
        return MemStore(
            AppData(
                accounts = accounts,
                categories = listOf(Category("other", "ПР", "Прочее"), Category("income", "ЗП", "Зарплата", income = true)),
                smsSources = listOf(
                    SmsSource("r", "Rysgal", "Rysgal", "card", expenseWords = p.expense, incomeWords = p.income,
                        ignoreWords = p.ignore, auto = auto, cashFee = 1.0),
                ),
                settings = Settings(mainCur = "TMT", sms = true),
            ),
        )
    }

    @Test
    fun снятие_сразу_переводом_в_кошелёк_и_комиссия_расходом() {
        val store = cashStore(auto = true)
        assertNotNull(SmsInbox.handle(store, "Rysgal", cashText, 1_758_000_000_000, Lang.RU))
        val txs = store.current.txs
        val transfer = txs.single { it.cat == CAT_TRANSFER }
        assertEquals(-400.0, transfer.amount, 0.001)
        assertEquals("cash", transfer.toAcc)
        val fee = txs.single { it.cat != CAT_TRANSFER }
        assertEquals("1% от 400", -4.0, fee.amount, 0.001)
        assertEquals("other", fee.cat)
        val c = Calc(store.current)
        assertEquals("с карты ушло 400 и 4 комиссии", 596.0, c.balance(store.current.accounts[0]), 0.001)
        assertEquals(400.0, c.balance(store.current.accounts[1]), 0.001)
    }

    @Test
    fun снятие_ждёт_проверки_с_кошельком_и_комиссией() {
        val store = cashStore(auto = false)
        SmsInbox.handle(store, "Rysgal", cashText, 1_758_000_000_000, Lang.RU)
        val item = store.current.inbox.single()
        assertTrue(item.cash)
        assertEquals("cash", item.toAcc)
        assertEquals(4.0, item.fee, 0.001)
        assertEquals("Снятие наличных", item.title)
    }

    @Test
    fun без_кошелька_снятие_становится_расходом() {
        val store = cashStore(auto = true, withWallet = false)
        SmsInbox.handle(store, "Rysgal", cashText, 1_758_000_000_000, Lang.RU)
        assertTrue(store.current.txs.none { it.cat == CAT_TRANSFER })
        assertEquals(-404.0, store.current.txs.sumOf { it.amount }, 0.001)
    }

    @Test
    fun вставленное_сообщение_без_отправителя_и_с_датой_из_текста() {
        val store = cashStore(auto = false)
        val text = "Kartyn belgisi VISA Domestic MILLI *2099, boyunca amal E-commerce approved. Wagty 16.09.26 17:10. " +
            "Pulyn mocberi: 200.00 TMT satyjy 4814 300000000000007, satyjyn ady: TMCELL,  Yurt: TKM. Pulyn galyndysy 501.71 TMT."
        // «сейчас» — 18.09.2026, а операция была 16-го
        val now = 1_789_732_800_000
        assertNotNull(SmsInbox.handlePasted(store, text, now, Lang.RU))
        val item = store.current.inbox.single()
        assertEquals(200.0, item.amount, 0.001)
        assertEquals(app.kopeechka.finance.data.localDateOrNull(2026, 9, 16)!!.toEpochDays().toLong(), item.date)
        assertEquals("повторная вставка не плодит дубли", null, SmsInbox.handlePasted(store, text, now, Lang.RU))
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
