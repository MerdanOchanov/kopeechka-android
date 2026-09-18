package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Every
import app.kopeechka.finance.data.Recurring
import app.kopeechka.finance.data.Recurrings
import app.kopeechka.finance.data.SyncMember
import app.kopeechka.finance.data.SyncSpace
import app.kopeechka.finance.data.dateOf
import app.kopeechka.finance.data.leftAmount
import app.kopeechka.finance.data.localDateOrNull
import app.kopeechka.finance.data.next
import app.kopeechka.finance.data.toEpochDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {

    private fun day(y: Int, m: Int, d: Int) = localDateOrNull(y, m, d)!!.toEpochDay()

    private fun rec(start: Long, every: String = Every.MONTH, total: Int = 0, auto: Boolean = true, by: String = "") =
        Recurring(id = "r", title = "Кредит", amount = 1000.0, accId = "acc", cat = "loan", every = every, start = start, total = total, auto = auto, by = by)

    private val title: (Recurring, Int) -> String = { r, n -> "${r.title} $n/${r.total}" }

    @Test
    fun месячный_платёж_31_числа_не_уплывает_после_февраля() {
        val r = rec(day(2026, 1, 31))
        assertEquals("в феврале — последний день", day(2026, 2, 28), r.dateOf(1))
        assertEquals("в марте снова 31-е", day(2026, 3, 31), r.dateOf(2))
    }

    @Test
    fun пропущенные_платежи_проводятся_каждый_своей_датой() {
        val d = AppData(recurring = listOf(rec(day(2026, 6, 10))))
        val run = Recurrings.due(d, day(2026, 9, 10), "", title)

        assertEquals(4, run.made.size)
        assertEquals(
            listOf(day(2026, 6, 10), day(2026, 7, 10), day(2026, 8, 10), day(2026, 9, 10)),
            run.data.txs.map { it.date }.sorted(),
        )
        assertEquals(4, run.data.recurring.single().done)
        assertEquals(day(2026, 10, 10), run.data.recurring.single().next)
    }

    @Test
    fun рассрочка_заканчивается_на_последнем_платеже() {
        val d = AppData(recurring = listOf(rec(day(2026, 1, 5), total = 3)))
        val run = Recurrings.due(d, day(2026, 12, 31), "", title)

        assertEquals(3, run.made.size)
        assertEquals("Кредит 3/3", run.made.last())
        assertNull("выплачено — следующего нет", run.data.recurring.single().next)
        assertEquals(0.0, run.data.recurring.single().leftAmount!!, 0.001)
    }

    @Test
    fun без_записи_сразу_платёж_ждёт_в_черновиках() {
        val d = AppData(recurring = listOf(rec(day(2026, 9, 1), auto = false)))
        val run = Recurrings.due(d, day(2026, 9, 1), "", title)

        assertTrue(run.data.txs.isEmpty())
        assertEquals(1, run.data.inbox.size)
    }

    @Test
    fun в_общем_бюджете_платёж_проводит_только_автор() {
        val space = SyncSpace(id = "S", name = "", memberId = "me", memberName = "", slot = 1, members = listOf(SyncMember("me", "", 1), SyncMember("wife", "", 2)))
        val d = AppData(space = space, recurring = listOf(rec(day(2026, 9, 1), by = "wife")))

        assertTrue("чужой платёж мой телефон не трогает", Recurrings.due(d, day(2026, 9, 5), "me", title).made.isEmpty())
    }

    @Test
    fun напоминание_один_раз_за_день_до_платежа() {
        val r = rec(day(2026, 9, 10)).copy(remindDays = 1)
        val d = AppData(recurring = listOf(r))

        val (after, hits) = Recurrings.reminders(d, day(2026, 9, 9), "")
        assertEquals(1, hits.size)
        assertTrue("второй раз не напоминаем", Recurrings.reminders(after, day(2026, 9, 9), "").second.isEmpty())
        assertTrue("за три дня рано", Recurrings.reminders(d, day(2026, 9, 7), "").second.isEmpty())
    }
}
