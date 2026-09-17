package app.kopeechka.finance

import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Category
import app.kopeechka.finance.data.INBOX_SMS
import app.kopeechka.finance.data.InboxItem
import app.kopeechka.finance.data.RequestStatus
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.SpendRequest
import app.kopeechka.finance.data.SyncMember
import app.kopeechka.finance.data.SyncSpace
import app.kopeechka.finance.data.needsApproval
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

    private fun space(me: String, members: Int = 2) = SyncSpace(
        id = "SPACE1",
        name = "Общий",
        memberId = me,
        memberName = me,
        slot = 1,
        members = (1..members).map { SyncMember(if (it == 1) me else "other$it", "имя$it", it) },
    )

    @Test
    fun новая_операция_подписывается_автором_а_старые_нет() {
        val before = base().copy(space = space("me"), txs = listOf(tx(1001, "старая", -10.0, t0, by = "")))
        val after = Sync.stamp(before, before.copy(txs = before.txs + tx(1002, "новая", -20.0, 0, by = "")), t0 + 100)

        assertEquals("me", after.txs.first { it.id == 1002L }.by)
        assertEquals("", after.txs.first { it.id == 1001L }.by, "задним числом авторство не приписываем")
    }

    @Test
    fun решение_по_заявке_доезжает_до_автора() {
        val req = SpendRequest(id = 5001, by = "a", accId = "acc1", amount = 300.0, cat = "food", title = "продукты", date = 20000, changedAt = t0)
        val mine = base().copy(requests = listOf(req))
        val theirs = base().copy(
            requests = listOf(req.copy(status = RequestStatus.APPROVED, decidedBy = "b", decidedAt = t0 + 50, changedAt = t0 + 50)),
        )

        assertEquals(RequestStatus.APPROVED, Sync.merge(mine, theirs, t0 + 100).requests.single().status)
        assertEquals(RequestStatus.APPROVED, Sync.merge(theirs, mine, t0 + 100).requests.single().status)
    }

    @Test
    fun согласие_спрашивается_только_с_общего_счёта_от_порога_и_при_втором_участнике() {
        val acc = Account(id = "acc1", name = "Общая", shared = true, approveFrom = 500.0)
        val d = AppData(accounts = listOf(acc), space = space("me"))

        assertTrue(d.needsApproval("acc1", 500.0))
        assertTrue(!d.needsApproval("acc1", 499.0), "ниже порога — пишется сразу")
        assertTrue(!d.copy(accounts = listOf(acc.copy(shared = false))).needsApproval("acc1", 1000.0), "личный счёт")
        assertTrue(!d.copy(space = space("me", members = 1)).needsApproval("acc1", 1000.0), "второго ещё нет — спрашивать некого")
        assertTrue(!d.copy(space = null).needsApproval("acc1", 1000.0))
    }
}
