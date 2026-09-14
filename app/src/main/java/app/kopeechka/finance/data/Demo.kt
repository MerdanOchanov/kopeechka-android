package app.kopeechka.finance.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

object Demo {

    /** Лимиты категорий заданы в долларах и переводятся в основную валюту профиля. */
    private val LIMITS_USD = mapOf(
        "food" to 260.0, "home" to 370.0, "transport" to 65.0, "cafe" to 98.0,
        "fun" to 76.0, "health" to 54.0, "clothes" to 65.0, "other" to 43.0,
    )

    /** Стандартные категории на языке интерфейса; лимиты — в основной валюте. */
    fun categories(l: Lang, cur: String): List<Category> {
        fun limit(key: String): Double {
            val v = (LIMITS_USD[key] ?: 0.0) * Currencies.hintRate("USD", cur)
            return if (abs(v) >= 100) (v / 10).roundToLong() * 10.0 else v.roundToLong().toDouble()
        }
        return listOf(
            Category("food", l.t("demo.code.food"), l.t("demo.cat.food"), limit("food"), color = "#4F7A5B"),
            Category("home", l.t("demo.code.home"), l.t("demo.cat.home"), limit("home"), color = "#2C455D"),
            Category("transport", l.t("demo.code.transport"), l.t("demo.cat.transport"), limit("transport"), color = "#597EA3"),
            Category("cafe", l.t("demo.code.cafe"), l.t("demo.cat.cafe"), limit("cafe"), color = "#B08A4F"),
            Category("fun", l.t("demo.code.fun"), l.t("demo.cat.fun"), limit("fun"), color = "#8A3B5B"),
            Category("health", l.t("demo.code.health"), l.t("demo.cat.health"), limit("health"), color = "#3E8E8A"),
            Category("clothes", l.t("demo.code.clothes"), l.t("demo.cat.clothes"), limit("clothes"), color = "#A9762F"),
            Category("other", l.t("demo.code.other"), l.t("demo.cat.other"), limit("other"), color = "#5D5D60"),
            Category("income", l.t("demo.code.salary"), l.t("demo.cat.salary"), income = true, color = "#416180"),
            Category("side", l.t("demo.code.side"), l.t("demo.cat.side"), income = true, color = "#6B4E8A"),
        )
    }

    /** Курсы по умолчанию относительно выбранной основной валюты. */
    fun rates(cur: String): Map<String, Double> =
        (Currencies.DEFAULT_CODES + cur).distinct().associateWith { Currencies.hintRate(it, cur) } + (cur to 1.0)

    /** Пустые данные: один счёт и стандартные категории. */
    fun empty(settings: Settings = Settings()): AppData {
        val l = Lang.of(settings.lang)
        val cur = settings.mainCur
        return AppData(
            version = Currencies.DATA_VERSION,
            accounts = listOf(Account("card", l.t("acc.type.card"), l.t("acc.type.card"), "", cur, 0.0)),
            categories = categories(l, cur),
            settings = settings.copy(
                rates = rates(cur) + settings.rates.filterKeys { it != cur },
                currencyCodes = (listOf(cur) + settings.currencyCodes).distinct(),
            ),
        )
    }

    private data class Seed(val d: Int, val titleKey: String, val cat: String, val acc: String, val rub: Double)

    /** Суммы демо заданы в рублях и переводятся в валюту профиля по ориентировочному курсу. */
    private val SEED = listOf(
        Seed(0, "demo.tx.grocery1", "food", "card", -1840.0),
        Seed(0, "demo.tx.metro", "transport", "cash", -62.0),
        Seed(0, "demo.tx.coffee", "cafe", "card", -420.0),
        Seed(1, "demo.tx.rent", "home", "card", -32000.0),
        Seed(1, "demo.tx.pharmacy", "health", "cash", -1260.0),
        Seed(1, "demo.tx.taxi", "transport", "card", -540.0),
        Seed(2, "demo.tx.salary", "income", "card", 96400.0),
        Seed(2, "demo.tx.cinema", "fun", "card", -1100.0),
        Seed(3, "demo.tx.grocery2", "food", "card", -3210.0),
        Seed(3, "demo.tx.internet", "home", "card", -700.0),
        Seed(4, "demo.tx.freelance", "side", "save", 17480.0),
        Seed(4, "demo.tx.gym", "health", "card", -2400.0),
        Seed(5, "demo.tx.grocery3", "food", "card", -5380.0),
        Seed(5, "demo.tx.bar", "cafe", "card", -2650.0),
        Seed(6, "demo.tx.jacket", "clothes", "card", -4990.0),
        Seed(8, "demo.tx.books", "other", "card", -2190.0),
        Seed(9, "demo.tx.grocery4", "food", "cash", -2640.0),
        Seed(12, "demo.tx.carsharing", "transport", "card", -1480.0),
        Seed(14, "demo.tx.concert", "fun", "card", -4200.0),
        Seed(18, "demo.tx.dentist", "health", "card", -8600.0),
        Seed(21, "demo.tx.grocery5", "food", "card", -4310.0),
        Seed(24, "demo.tx.restaurant", "cafe", "card", -5120.0),
        Seed(27, "demo.tx.utilities", "home", "card", -6400.0),
        // прошлые месяцы — чтобы отчёты за квартал и год были не пустыми
        Seed(35, "demo.tx.rent", "home", "card", -32000.0),
        Seed(38, "demo.tx.weekGrocery", "food", "card", -9800.0),
        Seed(44, "demo.tx.tickets", "fun", "card", -21400.0),
        Seed(52, "demo.tx.salary", "income", "card", 96400.0),
        Seed(66, "demo.tx.rent", "home", "card", -32000.0),
        Seed(70, "demo.tx.weekGrocery", "food", "card", -11200.0),
        Seed(82, "demo.tx.salary", "income", "card", 96400.0),
        Seed(97, "demo.tx.rent", "home", "card", -32000.0),
        Seed(101, "demo.tx.weekGrocery", "food", "card", -8700.0),
        Seed(128, "demo.tx.rent", "home", "card", -32000.0),
        Seed(133, "demo.tx.friends", "cafe", "card", -6900.0),
        Seed(158, "demo.tx.rent", "home", "card", -32000.0),
        Seed(163, "demo.tx.doctor", "health", "card", -5400.0),
    )

    fun create(l: Lang = Lang.RU, cur: String = l.t("demo.cur"), today: LocalDate = LocalDate.now()): AppData {
        val k = Currencies.hintRate("RUB", cur)
        fun money(rub: Double): Double {
            val v = rub * k
            return when {
                abs(v) >= 1000 -> (v / 10).roundToLong() * 10.0
                abs(v) >= 100 -> v.roundToLong().toDouble()
                else -> (v * 10).roundToLong() / 10.0
            }
        }

        val txs = SEED.mapIndexed { i, s ->
            Tx(
                id = (i + 1).toLong(),
                date = today.minusDays(s.d.toLong()).toEpochDay(),
                title = l.t(s.titleKey),
                cat = s.cat,
                acc = s.acc,
                amount = money(s.rub),
            )
        }

        // Стартовые остатки подобраны так, чтобы текущие балансы были «круглыми».
        fun initial(acc: String, target: Double) = target - txs.filter { it.acc == acc }.sumOf { it.amount }
        val accounts = listOf(
            Account("card", l.t("demo.acc.card"), l.t("acc.type.card"), l.t("demo.acc.cardMask"), cur, initial("card", money(112480.0))),
            Account("cash", l.t("demo.acc.cash"), l.t("acc.type.cash"), "", cur, initial("cash", money(9840.0))),
            Account("save", l.t("demo.acc.save"), l.t("acc.type.savings"), l.t("demo.acc.saveMask"), cur, initial("save", money(115000.0))),
        )

        val goals = listOf(
            Goal("g1", l.t("demo.goal.trip"), money(180000.0), money(68000.0), cur, l.t("demo.goal.tripHint")),
            Goal("g2", l.t("demo.goal.laptop"), money(140000.0), money(104000.0), cur, l.t("demo.goal.laptopHint")),
            Goal("g3", l.t("demo.goal.fund"), money(300000.0), money(122000.0), cur, l.t("demo.goal.fundHint")),
        )

        return AppData(
            version = Currencies.DATA_VERSION,
            accounts = accounts,
            categories = categories(l, cur),
            txs = txs,
            goals = goals,
            settings = Settings(
                mainCur = cur,
                lang = if (l.code == Lang.fromSystem().code) "auto" else l.code,
                rates = rates(cur),
                currencyCodes = (listOf(cur) + Currencies.DEFAULT_CODES).distinct(),
            ),
            nextId = 1000,
        )
    }
}
