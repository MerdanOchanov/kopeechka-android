package app.kopeechka.finance

import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Category
import app.kopeechka.finance.data.INBOX_SMS
import app.kopeechka.finance.data.InboxItem
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.SmsSource
import app.kopeechka.finance.data.Sync
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.data.forSync

import org.junit.Assert
import org.junit.Test

// У JUnit сообщение идёт первым аргументом, и от этого проверки читаются
// задом наперёд. Обёртки возвращают привычный порядок: что проверяем, потом почему.
private fun assertTrue(actual: Boolean, message: String = "") = Assert.assertTrue(message, actual)

private fun assertEquals(expected: Any?, actual: Any?, message: String = "") = Assert.assertEquals(message, expected, actual)

private fun assertNull(actual: Any?) = Assert.assertNull(actual)

/**
 * Слияние — единственное место, где ошибка тихо портит чужие деньги,
 * поэтому проверяется тестами, а не глазами.
 */
class SyncTest {

    private val t0 = 1_000_000_000_000L

    private fun tx(id: Long, title: String, amount: Double, changedAt: Long, by: String = "a") =
        Tx(id = id, date = 20000, title = title, cat = "food", acc = "acc1", amount = amount, by = by, changedAt = changedAt)

    private fun base() = AppData(
        accounts = listOf(Account(id = "acc1", name = "Карта", changedAt = t0)),
        categories = listOf(Category(id = "food", code = "food", name = "Еда", changedAt = t0)),
    )

    @Test
    fun слоты_разводят_номера_разных_телефонов() {
        val mine = Sync.slotStart(1)
        val theirs = Sync.slotStart(2)
        assertTrue(theirs - mine > 100_000_000, "между слотами должен быть запас на триллион записей")
        assertTrue(Sync.slotStart(0) < mine, "нулевой слот — для записей, сделанных до общего пространства")
    }

    @Test
    fun чужие_записи_добавляются_свои_остаются() {
        val mine = base().copy(txs = listOf(tx(1001, "моя", -100.0, t0)))
        val theirs = base().copy(txs = listOf(tx(Sync.slotStart(2), "чужая", -50.0, t0, by = "b")))

        val merged = Sync.merge(mine, theirs, t0 + 1000)

        assertEquals(2, merged.txs.size)
        assertTrue(merged.txs.any { it.title == "моя" })
        assertTrue(merged.txs.any { it.title == "чужая" })
    }

    @Test
    fun при_расхождении_побеждает_более_поздняя_правка() {
        val mine = base().copy(txs = listOf(tx(1001, "старое имя", -100.0, t0)))
        val theirs = base().copy(txs = listOf(tx(1001, "новое имя", -100.0, t0 + 5000)))

        assertEquals("новое имя", Sync.merge(mine, theirs, t0 + 9000).txs.single().title)
        // и в обратную сторону — порядок слияния ничего не меняет
        assertEquals("новое имя", Sync.merge(theirs, mine, t0 + 9000).txs.single().title)
    }

    @Test
    fun удалённое_не_возвращается() {
        val before = base().copy(txs = listOf(tx(1001, "покупка", -100.0, t0)))
        // я удалил запись — надгробие ставится при сохранении
        val after = Sync.stamp(before, before.copy(txs = emptyList()), t0 + 2000)
        assertEquals(t0 + 2000, after.deleted["tx:1001"])

        // у второго участника она ещё есть
        val theirs = before
        val merged = Sync.merge(after, theirs, t0 + 3000)
        assertTrue(merged.txs.isEmpty(), "union без надгробий воскресил бы удалённое")
    }

    @Test
    fun запись_созданная_после_удаления_остаётся() {
        val deleted = mapOf("tx:1001" to t0 + 2000)
        val mine = base().copy(deleted = deleted)
        val theirs = base().copy(txs = listOf(tx(1001, "снова завели", -100.0, t0 + 5000)))

        assertEquals(1, Sync.merge(mine, theirs, t0 + 6000).txs.size)
    }

    @Test
    fun слияние_не_зависит_от_порядка_и_повторов() {
        val mine = base().copy(txs = listOf(tx(1001, "моя", -100.0, t0), tx(1002, "вторая", -20.0, t0)))
        val theirs = base().copy(
            txs = listOf(tx(1001, "правленая", -110.0, t0 + 100), tx(Sync.slotStart(2), "чужая", -50.0, t0, by = "b")),
        )

        val once = Sync.merge(mine, theirs, t0 + 500)
        val twice = Sync.merge(once, theirs, t0 + 600)
        val other = Sync.merge(theirs, mine, t0 + 500)

        assertEquals(once.txs.map { it.id to it.title }.toSet(), twice.txs.map { it.id to it.title }.toSet())
        assertEquals(once.txs.map { it.id to it.title }.toSet(), other.txs.map { it.id to it.title }.toSet())
    }

    @Test
    fun отметка_времени_ставится_только_изменённым() {
        val before = base().copy(txs = listOf(tx(1001, "покупка", -100.0, t0), tx(1002, "вторая", -20.0, t0)))
        val edited = before.copy(txs = before.txs.map { if (it.id == 1001L) it.copy(title = "другое") else it })

        val after = Sync.stamp(before, edited, t0 + 7000)

        assertEquals(t0 + 7000, after.txs.first { it.id == 1001L }.changedAt)
        assertEquals(t0, after.txs.first { it.id == 1002L }.changedAt, "нетронутую запись переставлять нельзя")
    }

    @Test
    fun денежная_модель_общая_остальные_настройки_свои() {
        val mine = base().copy(settings = Settings(mainCur = "TMT", dark = true, moneyAt = t0))
        val theirs = base().copy(settings = Settings(mainCur = "RUB", dark = false, moneyAt = t0 + 100))

        val merged = Sync.merge(mine, theirs, t0 + 200)

        assertEquals("RUB", merged.settings.mainCur, "валюта общая — берём более позднюю")
        assertTrue(merged.settings.dark, "тема личная — чужая не приезжает")
    }

    @Test
    fun черновики_и_правила_смс_не_уходят_наружу() {
        val d = base().copy(
            inbox = listOf(InboxItem(id = 1, source = INBOX_SMS, at = t0, date = 20000, raw = "секретный текст банка")),
            smsSources = listOf(SmsSource(id = "s1", name = "Банк", sender = "900", accId = "acc1")),
            merchantCats = mapOf("магазин" to "food"),
        )

        val out = d.forSync()

        assertTrue(out.inbox.isEmpty())
        assertTrue(out.smsSources.isEmpty())
        assertTrue(out.merchantCats.isEmpty())
        assertNull(out.space)
    }

    @Test
    fun одинаковая_покупка_от_двоих_помечается_как_возможный_дубль() {
        val d = base().copy(
            txs = listOf(tx(1001, "продукты", -100.0, t0, by = "a"), tx(Sync.slotStart(2), "продукты", -100.0, t0, by = "b")),
        )
        assertEquals(1, Sync.duplicates(d).size)

        val same = base().copy(txs = listOf(tx(1001, "продукты", -100.0, t0, by = "a"), tx(1002, "продукты", -100.0, t0, by = "a")))
        assertTrue(Sync.duplicates(same).isEmpty(), "две свои одинаковые записи — дело хозяйское")
    }
}
