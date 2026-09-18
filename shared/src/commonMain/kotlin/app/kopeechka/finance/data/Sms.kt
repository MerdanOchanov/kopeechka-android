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
    /**
     * Последние 4 цифры карты. У одного банка бывает несколько карт, и все
     * сообщения приходят от одного отправителя — по цифрам понятно, какая это
     * карта и в какой счёт писать. Пусто — правило берёт любые сообщения
     * отправителя, для которых не нашлось правила с цифрами.
     */
    val cardMask: String = "",
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
    /**
     * Комиссия за снятие наличных, процент от суммы. Банк берёт её сверх суммы
     * и в смске не пишет — без этого остаток в приложении разойдётся с банком.
     */
    val cashFee: Double = 0.0,
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
    /** Снятие наличных: деньги не потрачены, а переложены с карты в кошелёк. */
    val cash: Boolean = false,
)

/** Слова по умолчанию — русскоязычные банки пишут примерно одинаково. */
object SmsWords {
    const val EXPENSE = "списание, оплата, покупка, снятие, перевод, oplata, pokupka"
    const val INCOME = "зачисление, пополнение, поступление, возврат, zachislenie, popolnenie"
    // «баланс» сюда нельзя: банки пишут остаток в каждой смске о покупке
    const val IGNORE = "код, пароль, otp, акция, скидка, кредит одобрен, бонус"

    /** Снятие наличных — на любом языке правила, слова не настраиваются. */
    const val CASH = "nagt pul almak, nagt pul alyndy, nagt pul çykaryldy, снятие наличных, выдача наличных, " +
        "cash withdrawal, atm withdrawal, naqd pul yechish, қолма-қол ақша алу"
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

    /**
     * Какое правило разбирает сообщение. Сначала ищем правило, чьи цифры карты
     * есть в тексте; не нашлось — правило этого отправителя без цифр. Если все
     * правила отправителя с цифрами и ни одни не подошли — это чужая карта.
     */
    fun pick(sources: List<SmsSource>, sender: String, text: String): SmsSource? {
        val own = sources.filter { it.enabled && matches(it, sender) }
        return own.firstOrNull { it.cardMask.isNotBlank() && hasCard(text, it.cardMask) }
            ?: own.firstOrNull { it.cardMask.isBlank() }
    }

    /** Четыре цифры стоят в тексте отдельно: не часть суммы или другого номера. */
    fun hasCard(text: String, mask: String): Boolean {
        val m = mask.filter { it.isDigit() }.takeLast(4)
        if (m.length < 4) return false
        var i = text.indexOf(m)
        while (i >= 0) {
            val before = if (i > 0) text[i - 1] else ' '
            val after = if (i + m.length < text.length) text[i + m.length] else ' '
            if (!before.isDigit() && !after.isDigit()) return true
            i = text.indexOf(m, i + 1)
        }
        return false
    }

    /** Подходит ли отправитель правилу: сравниваем без учёта регистра и пробелов. */
    fun matches(src: SmsSource, sender: String): Boolean {
        val a = src.sender.trim().lowercase().replace(" ", "")
        val b = sender.trim().lowercase().replace(" ", "")
        return a.isNotEmpty() && (a == b || b.endsWith(a))
    }

    fun parse(text: String, src: SmsSource, defaultCur: String): ParsedSms? {
        val low = text.lowercase()
        // слова об остатке не отбрасывают сообщение, даже если стоят в старых правилах:
        // остаток есть почти в каждой смске о покупке
        val ignore = words(src.ignoreWords).ifEmpty { words(SmsWords.IGNORE) }.filterNot { it in BALANCE_WORDS }
        if (ignore.any { it in low }) return null

        val expense = words(src.expenseWords).ifEmpty { words(SmsWords.EXPENSE) }
        val income = words(src.incomeWords).ifEmpty { words(SmsWords.INCOME) }
        val incomeHit = income.mapNotNull { w -> low.indexOf(w).takeIf { it >= 0 }?.let { it to w } }.minByOrNull { it.first }
        val expenseHit = expense.mapNotNull { w -> low.indexOf(w).takeIf { it >= 0 }?.let { it to w } }.minByOrNull { it.first }

        // Сумма рядом с валютой; нет валюты — первое число с копейками после
        // ключевого слова: «Sowda 115.00 ***6415» (Türkmenbaşy bank валюту не пишет).
        val keyword = listOfNotNull(incomeHit, expenseHit).minByOrNull { it.first }
        val money = findMoney(text)
            ?: keyword?.let { (at, w) -> amountAfter(text, at + w.length) }
            ?: return null

        // Знак у суммы надёжнее слов: «Pulyn mocberi -1.15 TMT» — расход,
        // хотя слов о расходе в сообщении нет (так пишет Rysgal).
        val isIncome = when {
            money.sign < 0 -> false
            money.sign > 0 -> true
            // если встретились оба вида слов, верим тому, что стоит раньше
            incomeHit != null && expenseHit != null -> incomeHit.first < expenseHit.first
            incomeHit != null -> true
            expenseHit != null -> false
            else -> return null
        }
        return ParsedSms(
            amount = money.value,
            cur = money.cur.ifBlank { defaultCur },
            income = isIncome,
            title = merchant(text).ifBlank { src.name },
            mask = findMask(text),
            cash = !isIncome && words(SmsWords.CASH).any { it in low },
        )
    }

    /** Сумма, валюта и знак перед числом: −1 — минус, +1 — плюс, 0 — без знака. */
    private data class Money(val value: Double, val cur: String, val sign: Int)

    private fun signBefore(text: String, start: Int): Int {
        var i = start - 1
        while (i >= 0 && text[i] == ' ') i--
        return when (if (i >= 0) text[i] else ' ') {
            '-', '−', '–' -> -1
            '+' -> 1
            else -> 0
        }
    }

    private fun findMoney(text: String): Money? {
        val m = AMOUNT.findAll(text).firstOrNull() ?: return null
        val value = m.groupValues[1].replace(SPACES, "").replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return Money(value, currencyOf(m.groupValues[2].trim()), signBefore(text, m.range.first))
    }

    /** Первое число с копейками после позиции: даты и время так не спутать с суммой. */
    private fun amountAfter(text: String, from: Int): Money? {
        val m = BARE_AMOUNT.find(text, from) ?: return null
        val value = m.groupValues[1].replace(SPACES, "").replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return Money(value, "", signBefore(text, m.range.first))
    }

    /**
     * Сумма — первое число рядом с валютой. Именно «рядом»: в сообщении есть
     * ещё дата, остаток и четыре цифры карты, и любое из них можно принять
     * за сумму, если искать просто число.
     */
    fun findAmount(text: String): Pair<Double, String>? = findMoney(text)?.let { it.value to it.cur }

    fun findMask(text: String): String = MASK.find(text)?.groupValues?.get(1).orEmpty()

    /** Название магазина: банки пишут его после суммы, в кавычках или прямо подписывают. */
    private fun merchant(text: String): String {
        QUOTED.find(text)?.let { return it.groupValues[1].trim().take(40) }
        // «satyjyn ady: TMCELL,» — Rysgal подписывает продавца
        NAMED_MERCHANT.find(text)?.let { return it.groupValues[1].trim().take(40) }
        // «***6415 185360 TEL. GURBANOW A.B., TM» — после карты и номера терминала
        AFTER_MASK.find(text)?.let { return it.groupValues[1].trim().trimEnd('.').take(40) }
        val tail = MERCHANT.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return tail.take(40)
    }

    private fun currencyOf(token: String): String {
        val t = token.lowercase().trim('.', ',', ' ')
        return CURRENCY_WORDS[t] ?: t.uppercase().take(3)
    }

    private val BALANCE_WORDS = setOf("баланс", "остаток", "galyndy", "balance", "balans")

    private val SPACES = Regex("[\\s ]")

    /** Число с необязательными дробными и следом — валюта словом, кодом или знаком. */
    private val AMOUNT = Regex(
        "(\\d{1,3}(?:[\\s ]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*" +
            "(RUB|RUR|USD|EUR|TMT|KZT|UZS|TRY|AZN|GBP|CNY|р(?:уб)?\\.?|манат|manat|тмт|сум|so'm|som|тенге|теңге|₽|\\$|€|₼|₺|₸)",
        RegexOption.IGNORE_CASE,
    )

    private val MASK = Regex("\\*{1,4}(\\d{4})")

    /** Число обязательно с копейками: «115.00», «1 050,41». */
    private val BARE_AMOUNT = Regex("(\\d{1,3}(?:[\\s ]\\d{3})+[.,]\\d{2}|\\d+[.,]\\d{2})(?!\\d)")

    private val NAMED_MERCHANT = Regex(
        "(?:satyjyn ady|satyjy ady|merchant|продавец|магазин)\\s*:\\s*([^,.;]{2,40})",
        RegexOption.IGNORE_CASE,
    )

    private val AFTER_MASK = Regex("\\*{2,4}\\d{4}\\s+\\d{3,}\\s+([^,;]{2,40}),")

    private val QUOTED = Regex("[\"«]([^\"»]{2,40})[\"»]")

    /** «... в MAGNIT», «... на АЗС», «оплата TELEGRAM» — берём хвост после предлога. */
    private val MERCHANT = Regex(
        "(?:в|на|у|at|in)\\s+([A-Za-zА-Яа-яЁё0-9][A-Za-zА-Яа-яЁё0-9 .\\-_]{1,39})",
        RegexOption.IGNORE_CASE,
    )

    private val CURRENCY_WORDS = mapOf(
        "р" to "RUB", "руб" to "RUB", "руб." to "RUB", "₽" to "RUB", "rur" to "RUB",
        "манат" to "TMT", "manat" to "TMT", "тмт" to "TMT", "₼" to "TMT",
        "so'm" to "UZS", "som" to "UZS", "теңге" to "KZT", "₸" to "KZT",
        "сум" to "UZS", "тенге" to "KZT",
        "$" to "USD", "€" to "EUR", "₺" to "TRY",
    )
}

/**
 * Готовые правила для банков Туркменистана. Слова собраны по-туркменски
 * и по-русски — банки пишут на обоих языках. Имя отправителя в каждом банке
 * своё и со временем меняется, поэтому его человек вписывает сам: точно так,
 * как оно видно в списке сообщений.
 */
object SmsPresets {
    data class Preset(val name: String, val expense: String, val income: String, val ignore: String)

    private const val TM_EXPENSE =
        "tölendi, töleg, satyn alyş, alyş, sowda, çykaryldy, nagt pul, geçirildi kartdan, " +
            "списание, оплата, покупка, снятие, перевод с карты"
    private const val TM_INCOME =
        "gelip gowuşdy, geçirildi hasabyňyza, hasabyňyza, girdeji, zachislenie, " +
            "зачисление, поступление, пополнение, перевод на карту"
    private const val TM_IGNORE =
        "kod, parol, açar söz, aksiýa, arzanladyş, код, пароль, акция, скидка"

    val TURKMENISTAN = listOf(
        Preset("Halkbank", TM_EXPENSE, TM_INCOME, TM_IGNORE),
        Preset("Senagat", TM_EXPENSE, TM_INCOME, TM_IGNORE),
        // Rysgal: покупка по карте начинается с «Kartyn belgisi … satyjy», а пополнение —
        // короткое «Pulyn mocberi 1396.8 TMT»; у короткого списания сумма с минусом.
        Preset("Rysgal", "kartyn belgisi, satyjy, e-commerce, " + TM_EXPENSE, "pulyn mocberi, pulyň möçberi, " + TM_INCOME, TM_IGNORE),
        // Türkmenbaşy: «Sowda 115.00 ***6415 … galyndy 1050.41» — без валюты.
        Preset("Türkmenbaşy", TM_EXPENSE, TM_INCOME, TM_IGNORE),
        Preset("Türkmenistan", TM_EXPENSE, TM_INCOME, TM_IGNORE),
        Preset("Daýhanbank", TM_EXPENSE, TM_INCOME, TM_IGNORE),
        Preset("TDDB", TM_EXPENSE, TM_INCOME, TM_IGNORE),
    )
}
