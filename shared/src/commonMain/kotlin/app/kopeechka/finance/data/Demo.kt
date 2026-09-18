package app.kopeechka.finance.data

import kotlinx.datetime.LocalDate
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

    /** Демо-суммы заданы в рублях: переводим в валюту профиля и округляем до «человеческих». */
    fun inCur(rub: Double, cur: String): Double {
        val v = rub * Currencies.hintRate("RUB", cur)
        return when {
            abs(v) >= 1000 -> (v / 10).roundToLong() * 10.0
            abs(v) >= 100 -> v.roundToLong().toDouble()
            else -> (v * 10).roundToLong() / 10.0
        }
    }

    // ——— «Дело»: прайс, клиенты, заказы ———

    private data class ProdSeed(val id: String, val nameKey: String, val unitKey: String, val price: Double, val cost: Double)

    private val PRODUCTS = listOf(
        ProdSeed("p1", "demo.prod.cake", "demo.unit.piece", 4500.0, 1800.0),
        ProdSeed("p2", "demo.prod.cupcakes", "demo.unit.set", 2200.0, 900.0),
        ProdSeed("p3", "demo.prod.gingerbread", "demo.unit.set", 1500.0, 600.0),
        ProdSeed("p4", "demo.prod.delivery", "demo.unit.piece", 400.0, 150.0),
    )

    /** Заказ: сколько дней назад, клиент, позиции «товар × количество», скидка, статус. */
    private data class OrderSeed(val d: Int, val customer: String, val items: List<Pair<String, Double>>, val discount: Double, val status: String)

    private val ORDERS = listOf(
        OrderSeed(0, "cl1", listOf("p2" to 1.0), 0.0, OrderStatus.NEW),
        OrderSeed(1, "cl2", listOf("p1" to 2.0, "p4" to 1.0), 0.0, OrderStatus.WORK),
        OrderSeed(2, "cl1", listOf("p1" to 1.0, "p4" to 1.0), 0.0, OrderStatus.PAID),
        OrderSeed(5, "cl2", listOf("p2" to 3.0), 500.0, OrderStatus.PAID),
        OrderSeed(9, "cl3", listOf("p3" to 2.0, "p4" to 1.0), 0.0, OrderStatus.PAID),
        OrderSeed(16, "cl1", listOf("p1" to 1.0, "p2" to 1.0), 0.0, OrderStatus.PAID),
        OrderSeed(34, "cl3", listOf("p1" to 1.0), 0.0, OrderStatus.PAID),
        OrderSeed(41, "cl2", listOf("p2" to 2.0, "p3" to 1.0), 0.0, OrderStatus.PAID),
    )

    /** Категории продаж и себестоимости — без них оплаченному заказу некуда записать доход. */
    fun bizCategories(l: Lang): List<Category> = listOf(
        Category(CAT_SALE, l.t("demo.code.sale"), l.t("biz.catSale"), income = true, color = "#3E8E8A"),
        Category(CAT_COST, l.t("demo.code.cost"), l.t("biz.catCost"), color = "#9A5B4A"),
    )

    /** Категории заработка на долге и переплаты по нему. */
    fun debtCategories(l: Lang): List<Category> = listOf(
        Category(CAT_DEBT_GAIN, l.t("demo.code.debtGain"), l.t("debt.catGain"), income = true, color = "#7E8B4F"),
        Category(CAT_DEBT_COST, l.t("demo.code.debtCost"), l.t("debt.catCost"), color = "#8A3B5B"),
    )

    /** Прайс, клиенты, заказы и операции дохода по оплаченным заказам. */
    data class Biz(val products: List<Product>, val customers: List<Customer>, val orders: List<Order>, val txs: List<Tx>)

    fun bizData(l: Lang, cur: String, today: LocalDate = today(), acc: String = "card"): Biz {
        fun money(rub: Double) = inCur(rub, cur)
        val products = PRODUCTS.map { Product(it.id, l.t(it.nameKey), money(it.price), money(it.cost), l.t(it.unitKey)) }
        val customers = listOf(
            Customer("cl1", l.t("demo.cust.1"), l.t("demo.cust.1contact")),
            Customer("cl2", l.t("demo.cust.2"), l.t("demo.cust.2contact")),
            Customer("cl3", l.t("demo.cust.3"), l.t("demo.cust.3contact")),
        )
        val byId = products.associateBy { it.id }
        val txs = mutableListOf<Tx>()
        val orders = ORDERS.sortedBy { it.d }.mapIndexed { i, s ->
            val date = today.minusDays(s.d.toLong()).toEpochDay()
            val items = s.items.map { (pid, qty) ->
                val p = byId.getValue(pid)
                OrderItem(p.id, p.name, qty, p.price, p.cost)
            }
            val no = "${today.year}-" + (ORDERS.size - i).toString().padStart(3, '0')
            val total = (items.sumOf { it.qty * it.price } - money(s.discount)).coerceAtLeast(0.0)
            var incomeTxId: Long? = null
            if (s.status == OrderStatus.PAID) {
                incomeTxId = 500L + i
                txs += Tx(incomeTxId, date, l.t("biz.txTitle", no), CAT_SALE, acc, total)
            }
            Order(
                id = 600L + i,
                no = no,
                customerId = s.customer,
                date = date,
                items = items,
                discount = money(s.discount),
                cur = cur,
                status = s.status,
                incomeTxId = incomeTxId,
            )
        }
        return Biz(products, customers, orders, txs)
    }

    fun create(
        l: Lang = Lang.RU,
        cur: String = l.t("demo.cur"),
        today: LocalDate = today(),
        business: Boolean = false,
    ): AppData {
        fun money(rub: Double) = inCur(rub, cur)
        val biz = if (business) bizData(l, cur, today) else Biz(emptyList(), emptyList(), emptyList(), emptyList())

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

        val all = txs + biz.txs

        // Стартовые остатки подобраны так, чтобы текущие балансы были «круглыми».
        fun initial(acc: String, target: Double) = target - all.filter { it.acc == acc }.sumOf { it.amount }
        val accounts = listOf(
            Account("card", l.t("demo.acc.card"), l.t("acc.type.card"), l.t("demo.acc.cardMask"), cur, initial("card", money(112480.0))),
            Account("cash", l.t("demo.acc.cash"), l.t("acc.type.cash"), "", cur, initial("cash", money(9840.0))),
            Account("save", l.t("demo.acc.save"), l.t("acc.type.savings"), l.t("demo.acc.saveMask"), cur, initial("save", money(115000.0))),
        ) + if (cur == "USD") {
            emptyList()
        } else {
            // Сбережения в долларах: на них видно, как работают курсы.
            listOf(Account("usd", l.t("demo.acc.usd"), l.t("acc.type.savings"), "", "USD", 800.0))
        }

        // Регулярные платежи с ближайшими датами — чтобы на главной были «ближайшие платежи».
        val recurring = listOf(
            Recurring(
                id = "rec-phone",
                title = l.t("demo.rec.phone"),
                amount = money(4500.0),
                accId = "card",
                cat = "other",
                start = today.plusDays(5).minusMonths(5).toEpochDay(),
                total = 12,
                done = 5,
            ),
            Recurring(
                id = "rec-net",
                title = l.t("demo.rec.net"),
                amount = money(650.0),
                accId = "card",
                cat = "home",
                start = today.plusDays(2).minusMonths(7).toEpochDay(),
                done = 7,
                auto = true,
            ),
        )

        val goals = listOf(
            Goal("g1", l.t("demo.goal.trip"), money(180000.0), money(68000.0), cur, l.t("demo.goal.tripHint")),
            Goal("g2", l.t("demo.goal.laptop"), money(140000.0), money(104000.0), cur, l.t("demo.goal.laptopHint")),
            Goal("g3", l.t("demo.goal.fund"), money(300000.0), money(122000.0), cur, l.t("demo.goal.fundHint")),
        )

        return AppData(
            version = Currencies.DATA_VERSION,
            accounts = accounts,
            categories = categories(l, cur) + if (business) bizCategories(l) else emptyList(),
            txs = all.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id }),
            goals = goals,
            recurring = recurring,
            products = biz.products,
            customers = biz.customers,
            orders = biz.orders,
            settings = Settings(
                mainCur = cur,
                lang = if (l.code == Lang.fromSystem().code) "auto" else l.code,
                rates = rates(cur),
                // В манатах доллар на рынке в разы дороже официального — показываем оба курса.
                marketRates = if (cur == "TMT") mapOf("USD" to 19.5) else emptyMap(),
                currencyCodes = (listOf(cur) + Currencies.DEFAULT_CODES).distinct(),
                business = business,
            ),
            nextId = 1000,
        )
    }
}
