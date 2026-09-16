package app.kopeechka.finance.data

import kotlinx.serialization.Serializable

/**
 * Правило чтения банковских СМС: от кого приходят, к какому счёту относятся
 * и по каким словам понимать, расход это или приход.
 *
 * Слова хранятся строкой через запятую — так их и показывает поле ввода,
 * а списки нужны только внутри разбора.
 */
@Serializable
data class SmsSource(
    val id: String,
    /** Как называть в списке: «Халкбанк». */
    val name: String,
    /** От кого приходит: номер 900 или имя отправителя HalkBank. */
    val sender: String,
    /** Счёт в приложении, к которому относятся эти сообщения. */
    val accId: String,
    val expenseWords: String = "",
    val incomeWords: String = "",
    /** Слова, по которым сообщение вообще не про деньги: код, пароль, реклама. */
    val ignoreWords: String = "",
    /**
     * Записывать сразу, без подтверждения. Включать стоит только для отправителя,
     * на котором правило уже показало себя — банки шлют много постороннего.
     */
    val auto: Boolean = false,
    val enabled: Boolean = true,
)

/** Сообщение, как его отдала платформа. */
data class SmsMessage(val sender: String, val text: String, val at: Long)

/** Что удалось вычитать из сообщения. */
data class ParsedSms(
    val amount: Double,
    val cur: String,
    val income: Boolean,
    val title: String,
    /** Последние цифры карты, если банк их указал. */
    val mask: String,
)

/** Слова по умолчанию — русскоязычные банки пишут примерно одинаково. */
object SmsWords {
    const val EXPENSE = "списание, оплата, покупка, снятие, перевод, oplata, pokupka"
    const val INCOME = "зачисление, пополнение, поступление, возврат, zachislenie, popolnenie"
    const val IGNORE = "код, пароль, otp, баланс, акция, скидка, кредит одобрен, бонус"
}

/**
 * Разбор банковской СМС.
 *
 * Работает на словах и числах, без ИИ: сообщения банка однотипные, а платить
 * за распознавание каждой смски и отправлять их наружу незачем.
 */
object SmsParse {

    fun words(s: String): List<String> =
        s.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Подходит ли отправитель правилу: сравниваем без учёта регистра и пробелов. */
    fun matches(src: SmsSource, sender: String): Boolean {
        val a = src.sender.trim().lowercase().replace(" ", "")
        val b = sender.trim().lowercase().replace(" ", "")
        return a.isNotEmpty() && (a == b || b.endsWith(a))
    }

    fun parse(text: String, src: SmsSource, defaultCur: String): ParsedSms? {
        val low = text.lowercase()
        val ignore = words(src.ignoreWords).ifEmpty { words(SmsWords.IGNORE) }
        if (ignore.any { it in low }) return null

        val expense = words(src.expenseWords).ifEmpty { words(SmsWords.EXPENSE) }
        val income = words(src.incomeWords).ifEmpty { words(SmsWords.INCOME) }
        val incomeAt = income.mapNotNull { w -> low.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val expenseAt = expense.mapNotNull { w -> low.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        // если встретились оба вида слов, верим тому, что стоит раньше
        val isIncome = when {
            incomeAt != null && expenseAt != null -> incomeAt < expenseAt
            incomeAt != null -> true
            expenseAt != null -> false
            else -> return null
        }

        val money = findAmount(text) ?: return null
        return ParsedSms(
            amount = money.first,
            cur = money.second.ifBlank { defaultCur },
            income = isIncome,
            title = merchant(text).ifBlank { src.name },
            mask = findMask(text),
        )
    }

    /**
     * Сумма — первое число рядом с валютой. Именно «рядом»: в сообщении есть
     * ещё дата, остаток и четыре цифры карты, и любое из них можно принять
     * за сумму, если искать просто число.
     */
    fun findAmount(text: String): Pair<Double, String>? {
        val m = AMOUNT.findAll(text).firstOrNull() ?: return null
        val digits = m.groupValues[1].replace(SPACES, "").replace(',', '.')
        val value = digits.toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return value to currencyOf(m.groupValues[2].trim())
    }

    fun findMask(text: String): String = MASK.find(text)?.groupValues?.get(1).orEmpty()

    /** Название магазина: банки пишут его после суммы или в кавычках. */
    private fun merchant(text: String): String {
        QUOTED.find(text)?.let { return it.groupValues[1].trim().take(40) }
        val tail = MERCHANT.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return tail.take(40)
    }

    private fun currencyOf(token: String): String {
        val t = token.lowercase().trim('.', ',', ' ')
        return CURRENCY_WORDS[t] ?: t.uppercase().take(3)
    }

    private val SPACES = Regex("[\\s ]")

    /** Число с необязательными дробными и следом — валюта словом, кодом или знаком. */
    private val AMOUNT = Regex(
        "(\\d{1,3}(?:[\\s ]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*" +
            "(RUB|RUR|USD|EUR|TMT|KZT|UZS|TRY|AZN|GBP|CNY|р(?:уб)?\\.?|манат|тмт|сум|тенге|₽|\\$|€|₼|₺)",
        RegexOption.IGNORE_CASE,
    )

    private val MASK = Regex("\\*{1,4}(\\d{4})")

    private val QUOTED = Regex("[\"«]([^\"»]{2,40})[\"»]")

    /** «... в MAGNIT», «... на АЗС», «оплата TELEGRAM» — берём хвост после предлога. */
    private val MERCHANT = Regex(
        "(?:в|на|у|at|in)\\s+([A-Za-zА-Яа-яЁё0-9][A-Za-zА-Яа-яЁё0-9 .\\-_]{1,39})",
        RegexOption.IGNORE_CASE,
    )

    private val CURRENCY_WORDS = mapOf(
        "р" to "RUB", "руб" to "RUB", "руб." to "RUB", "₽" to "RUB", "rur" to "RUB",
        "манат" to "TMT", "тмт" to "TMT", "₼" to "TMT",
        "сум" to "UZS", "тенге" to "KZT",
        "$" to "USD", "€" to "EUR", "₺" to "TRY",
    )
}
