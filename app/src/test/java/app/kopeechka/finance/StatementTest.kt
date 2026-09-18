package app.kopeechka.finance

import app.kopeechka.finance.data.Statement
import app.kopeechka.finance.data.localDateOrNull
import app.kopeechka.finance.data.toEpochDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Выписки разных банков: колонки узнаются по заголовкам, суммы и даты — в любом написании. */
class StatementTest {

    private fun day(y: Int, m: Int, d: Int) = localDateOrNull(y, m, d)!!.toEpochDay()

    @Test
    fun выписка_с_одной_колонкой_суммы() {
        val csv = """
            "Дата операции";"Дата платежа";"Номер карты";"Статус";"Сумма операции";"Валюта операции";"Описание"
            "17.09.2026 15:30:00";"18.09.2026";"*1177";"OK";"-350,00";"RUB";"Магнит"
            "16.09.2026 09:10:00";"16.09.2026";"*1177";"OK";"45 000,00";"RUB";"Зарплата"
        """.trimIndent()
        val rows = Statement.split(csv)
        val layout = Statement.guess(rows.first())

        assertEquals("из двух дат берём дату операции", 0, layout.date)
        val parsed = Statement.parse(rows, layout, "RUB")
        assertEquals(2, parsed.size)
        assertEquals(day(2026, 9, 17), parsed[0].date)
        assertEquals(-350.0, parsed[0].amount, 0.001)
        assertEquals("Магнит", parsed[0].text)
        assertEquals(45000.0, parsed[1].amount, 0.001)
    }

    @Test
    fun выписка_с_колонками_расход_и_приход() {
        val csv = "Date,Description,Debit,Credit\n2026-09-17,Coffee,4.50,\n2026-09-18,Salary,,1200.00\n"
        val rows = Statement.split(csv)
        val parsed = Statement.parse(rows, Statement.guess(rows.first()), "USD")

        assertEquals(-4.5, parsed[0].amount, 0.001)
        assertEquals(1200.0, parsed[1].amount, 0.001)
    }

    @Test
    fun суммы_в_разных_написаниях() {
        assertEquals(-1234.56, Statement.amount("−1 234,56")!!, 0.001)
        assertEquals(-1234.56, Statement.amount("-1,234.56")!!, 0.001)
        assertEquals(1234.0, Statement.amount("1 234 ₽")!!, 0.001)
        assertEquals(-500.0, Statement.amount("(500.00)")!!, 0.001)
    }

    @Test
    fun кавычки_и_разделитель_внутри_описания() {
        val rows = Statement.split("Дата;Сумма;Описание\n17.09.2026;-100;\"ООО \"\"Ромашка\"\"; кафе\"\n")
        assertEquals("ООО \"Ромашка\"; кафе", rows[1][2])
    }

    @Test
    fun строки_без_даты_или_суммы_пропускаются() {
        val csv = "Дата;Сумма\n17.09.2026;-100\nИтого;-100\n;\n"
        val rows = Statement.split(csv)
        assertTrue(Statement.parse(rows, Statement.guess(rows.first()), "RUB").size == 1)
    }
}
