package app.kopeechka.finance.data

import java.time.LocalDate
import kotlin.math.roundToLong

object Demo {

    /** Стандартные категории на языке интерфейса. Лимиты в долларах — базе курсов. */
    fun categories(l: Lang) = listOf(
        Category("food", l.t("demo.code.food"), l.t("demo.cat.food"), 260.0, color = "#4F7A5B"),
        Category("home", l.t("demo.code.home"), l.t("demo.cat.home"), 370.0, color = "#2C455D"),
        Category("transport", l.t("demo.code.transport"), l.t("demo.cat.transport"), 65.0, color = "#597EA3"),
        Category("cafe", l.t("demo.code.cafe"), l.t("demo.cat.cafe"), 98.0, color = "#B08A4F"),
        Category("fun", l.t("demo.code.fun"), l.t("demo.cat.fun"), 76.0, color = "#8A3B5B"),
        Category("health", l.t("demo.code.health"), l.t("demo.cat.health"), 54.0, color = "#3E8E8A"),
        Category("clothes", l.t("demo.code.clothes"), l.t("demo.cat.clothes"), 65.0, color = "#A9762F"),
        Category("other", l.t("demo.code.other"), l.t("demo.cat.other"), 43.0, color = "#5D5D60"),
        Category("income", l.t("demo.code.salary"), l.t("demo.cat.salary"), income = true, color = "#416180"),
        Category("side", l.t("demo.code.side"), l.t("demo.cat.side"), income = true, color = "#6B4E8A"),
    )

    /** Пустые данные: один счёт и стандартные категории. */
    fun empty(settings: Settings = Settings()): AppData {
        val l = Lang.of(settings.lang)
        val cur = settings.mainCur
        return AppData(
            accounts = listOf(Account("card", l.t("acc.type.card"), l.t("acc.type.card"), "", cur, 0.0)),
            categories = categories(l),
            settings = settings,
        )
    }

    private data class Seed(val d: Int, val titleKey: String, val cat: String, val acc: String, val rub: Double)

    /** Суммы демо заданы в рублях и переводятся в валюту языка по курсу из настроек. */
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
        Seed(4, "demo.tx.freelance", "side", "save", -0.0 + 17480.0),
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

    fun create(l: Lang = Lang.RU, today: LocalDate = LocalDate.now()): AppData {
        val cur = l.t("demo.cur")
        val rubInUsd = Currencies.DEFAULT_RATES["RUB"] ?: 0.0109
        val rate = Currencies.DEFAULT_RATES[cur] ?: 1.0
        // суммы заданы в рублях: переводим в валюту демо и округляем до «круглого» значения
        fun money(rub: Double): Double {
            val v = rub * rubInUsd / rate
            return when {
                kotlin.math.abs(v) >= 1000 -> (v / 10).roundToLong() * 10.0
                kotlin.math.abs(v) >= 100 -> v.roundToLong().toDouble()
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
            accounts = accounts,
            categories = categories(l),
            txs = txs,
            goals = goals,
            settings = Settings(mainCur = cur, lang = if (l.code == Lang.fromSystem().code) "auto" else l.code),
            nextId = 1000,
        )
    }
}
