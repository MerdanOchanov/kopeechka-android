package app.kopeechka.finance.data

/** Что удалось прочитать из QR-кода кассового чека. */
data class FiscalReceipt(
    /** День покупки; null — в коде даты нет. */
    val date: Long?,
    /** Сумма чека; 0 — в коде её нет, человек впишет сам. */
    val amount: Double,
    /** Деньги пришли к покупателю: возврат покупки. */
    val income: Boolean,
)

/**
 * QR-код кассового чека.
 *
 * В России на каждом чеке есть строка вида
 * `t=20260917T1530&s=1234.00&fn=…&i=…&fp=…&n=1`: дата, сумма и тип операции.
 * Этого хватает, чтобы записать покупку без интернета и без ИИ. Позиции чека
 * в коде нет — их отдаёт только сервис налоговой по личному кабинету.
 *
 * Казахстанские операторы фискальных данных кладут в код ссылку с теми же
 * параметрами `t` и `s`. Для остальных стран разбираем что есть: если сумма
 * не нашлась, черновик придёт без неё.
 */
object FiscalQr {

    fun parse(qr: String): FiscalReceipt? {
        val params = params(qr)
        if (params.isEmpty()) return null
        val date = params["t"]?.let(::date) ?: params["c"]?.let(::date)
        // сумма бывает только с копейками через точку: так её не спутать с подписью чека
        val amount = params["s"]?.takeIf { MONEY.matches(it) }?.toDoubleOrNull() ?: 0.0
        if (date == null && amount <= 0) return null
        // n: 1 — покупка, 2 — возврат покупки, 3 — продажа покупателем (скупка), 4 — возврат такой продажи
        val income = when (params["n"]) {
            "2", "3" -> true
            else -> false
        }
        return FiscalReceipt(date, amount, income)
    }

    private fun params(qr: String): Map<String, String> {
        val query = qr.trim().substringAfter('?', qr.trim())
        return query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).lowercase() to part.substring(eq + 1)
        }.toMap()
    }

    /** «20260917T1530», «20260917T153012» или «20260917153012». */
    private fun date(raw: String): Long? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return null
        val y = digits.substring(0, 4).toIntOrNull() ?: return null
        val m = digits.substring(4, 6).toIntOrNull() ?: return null
        val d = digits.substring(6, 8).toIntOrNull() ?: return null
        return localDateOrNull(y, m, d)?.toEpochDay()
    }

    private val MONEY = Regex("\\d+\\.\\d{1,2}")
}
