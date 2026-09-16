package app.kopeechka.finance.data

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Малое дело: прайс, клиенты, заказы.
 *
 * Складского учёта здесь нет намеренно: товар — это строка прайса с ценой и
 * себестоимостью, а не единица на складе. Деньги появляются только тогда, когда
 * заказ оплачен: приложение создаёт обычную операцию дохода, поэтому выручка
 * попадает в баланс, бюджеты и все прежние отчёты.
 */

/** Категория дохода от продаж и категория себестоимости — создаются при включении модуля. */
const val CAT_SALE = "sale"
const val CAT_COST = "cost"

/** Статусы заказа. Хранятся строкой: незнакомое значение не уронит чтение старого файла. */
object OrderStatus {
    const val NEW = "new"
    const val WORK = "work"
    const val DONE = "done"
    const val PAID = "paid"
    const val CANCELLED = "cancelled"

    val ALL = listOf(NEW, WORK, DONE, PAID, CANCELLED)

    /** Заказы, которые ещё в работе: их деньги пока только ожидаются. */
    val OPEN = listOf(NEW, WORK, DONE)

    fun key(status: String) = "biz.status." + (if (status in ALL) status else NEW)
    fun isOpen(status: String) = status in OPEN
}

@Serializable
data class Product(
    val id: String,
    val name: String,
    /** Цена за единицу в основной валюте приложения. */
    val price: Double = 0.0,
    /** Себестоимость за единицу в основной валюте; 0 — услуга без прямых затрат. */
    val cost: Double = 0.0,
    /** Единица измерения: шт, час, кг. Пусто — без единицы. */
    val unit: String = "",
    val archived: Boolean = false,
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

@Serializable
data class Customer(
    val id: String,
    val name: String,
    val contact: String = "",
    val note: String = "",
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

/** Позиция заказа. Цена и себестоимость зафиксированы на момент заказа — в валюте заказа. */
@Serializable
data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val qty: Double = 1.0,
    val price: Double = 0.0,
    val cost: Double = 0.0,
)

@Serializable
data class Order(
    val id: Long,
    /** Номер для человека: «2026-014». */
    val no: String = "",
    val customerId: String = "",
    /** LocalDate.toEpochDay() */
    val date: Long = 0,
    /** Срок исполнения, если он есть. */
    val due: Long? = null,
    val items: List<OrderItem> = emptyList(),
    /** Скидка на весь заказ в валюте заказа. */
    val discount: Double = 0.0,
    /** Расходы по заказу сверх себестоимости позиций: доставка, материалы. */
    val extraCost: Double = 0.0,
    val cur: String = "",
    val status: String = OrderStatus.NEW,
    /** Операция дохода, созданная при оплате. */
    val incomeTxId: Long? = null,
    /** Операция расхода, если себестоимость списывали со счёта. */
    val costTxId: Long? = null,
    val note: String = "",
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

/** Итоги дела за отрезок времени. */
data class BizStats(
    val revenue: Double,
    val cost: Double,
    val profit: Double,
    val paidCount: Int,
    val openCount: Int,
    val openSum: Double,
) {
    val avg get() = if (paidCount > 0) revenue / paidCount else 0.0
    val margin get() = if (revenue > 0) profit / revenue * 100 else 0.0
    val marginText get() = "${margin.roundToInt()}%"
}

// ——— расчёты (в основной валюте, как и всё остальное в отчётах) ———

fun Calc.product(id: String?): Product? = d.products.firstOrNull { it.id == id }
fun Calc.customer(id: String?): Customer? = d.customers.firstOrNull { it.id == id }

fun Calc.customerName(id: String?): String =
    customer(id)?.name ?: l.t("biz.noCustomer")

fun Calc.orderCur(o: Order): String = o.cur.ifBlank { main }

/** Сумма заказа в его валюте: позиции минус скидка. */
fun Calc.orderTotal(o: Order): Double =
    (o.items.sumOf { it.qty * it.price } - o.discount).coerceAtLeast(0.0)

/** Себестоимость заказа в его валюте: позиции плюс расходы по заказу. */
fun Calc.orderCost(o: Order): Double = o.items.sumOf { it.qty * it.cost } + o.extraCost

fun Calc.orderProfit(o: Order): Double = orderTotal(o) - orderCost(o)

fun Calc.orderTotalMain(o: Order): Double = toMain(orderTotal(o), orderCur(o))
fun Calc.orderCostMain(o: Order): Double = toMain(orderCost(o), orderCur(o))
fun Calc.orderProfitMain(o: Order): Double = toMain(orderProfit(o), orderCur(o))

/** Заказы по убыванию даты — как их видит человек. */
fun Calc.ordersSorted(): List<Order> =
    d.orders.sortedWith(compareByDescending<Order> { it.date }.thenByDescending { it.id })

fun Calc.ordersIn(r: Range): List<Order> = d.orders.filter { it.date in r.from..r.to }

/**
 * Выручкой считаются оплаченные заказы: «сколько заработал» — это полученные деньги.
 * Неоплаченные висят отдельной строкой «ожидается».
 */
fun Calc.biz(r: Range): BizStats {
    val within = ordersIn(r)
    val paid = within.filter { it.status == OrderStatus.PAID }
    val open = within.filter { OrderStatus.isOpen(it.status) }
    val revenue = paid.sumOf { orderTotalMain(it) }
    val cost = paid.sumOf { orderCostMain(it) }
    return BizStats(
        revenue = revenue,
        cost = cost,
        profit = revenue - cost,
        paidCount = paid.size,
        openCount = open.size,
        openSum = open.sumOf { orderTotalMain(it) },
    )
}

/** Выручка по товарам за отрезок — для отчёта и карточки товара. */
fun Calc.byProduct(r: Range): List<Slice> {
    val paid = ordersIn(r).filter { it.status == OrderStatus.PAID }
    val sums = HashMap<String, Double>()
    paid.forEach { o ->
        val k = rate(orderCur(o))
        o.items.forEach { i ->
            val name = i.name.ifBlank { product(i.productId)?.name ?: l.t("biz.item") }
            sums[name] = (sums[name] ?: 0.0) + i.qty * i.price * k
        }
    }
    val total = sums.values.sum()
    return sums.entries.sortedByDescending { it.value }.map {
        Slice(it.key, it.value, pctOf(it.value, total))
    }
}

/** Выручка по клиентам за отрезок. */
fun Calc.byCustomer(r: Range): List<Slice> {
    val paid = ordersIn(r).filter { it.status == OrderStatus.PAID }
    val sums = HashMap<String, Double>()
    paid.forEach { o ->
        val name = customerName(o.customerId)
        sums[name] = (sums[name] ?: 0.0) + orderTotalMain(o)
    }
    val total = sums.values.sum()
    return sums.entries.sortedByDescending { it.value }.map {
        Slice(it.key, it.value, pctOf(it.value, total))
    }
}

fun pctOf(v: Double, total: Double): String =
    (if (total > 0) (v / total * 100).roundToInt() else 0).toString() + "%"

/** Выручка за тот же отрезок прошлого периода и изменение в процентах. */
fun Calc.bizCompare(p: Period, offset: Int): Pair<Double, Double>? {
    val cur = range(p, offset)
    val prev = range(p, offset - 1)
    val prevTo = if (isCurrent(cur)) min(prev.to, prev.from + (todayDay - cur.from)) else prev.to
    val was = d.orders
        .filter { it.status == OrderStatus.PAID && it.date in prev.from..prevTo }
        .sumOf { orderTotalMain(it) }
    if (was <= 0.0) return null
    val now = biz(cur).revenue
    return was to (now - was) / was * 100
}

/** Короткие факты по делу — вместо расходных, когда выбран разрез «Бизнеса». */
fun Calc.bizFacts(r: Range): List<Pair<String, String>> {
    val stats = biz(r)
    val topProduct = byProduct(r).firstOrNull()
    val topCustomer = byCustomer(r).firstOrNull()
    return listOf(
        l.t("biz.avgCheck") to fmtMain(stats.avg),
        l.t("biz.fact.topProduct") to (topProduct?.let { "${it.name} · ${fmtMain(it.value)}" } ?: l.t("common.dash")),
        l.t("biz.fact.topCustomer") to (topCustomer?.let { "${it.name} · ${fmtMain(it.value)}" } ?: l.t("common.dash")),
        l.t("biz.fact.orders") to ordersIn(r).size.toString(),
    )
}

/** Заказы одного клиента, свежие сверху. */
fun Calc.ordersOf(customerId: String): List<Order> =
    ordersSorted().filter { it.customerId == customerId }

/** Следующий номер заказа: «2026-007» в пределах года. */
fun Calc.nextOrderNo(): String {
    val year = today.year
    val prefix = "$year-"
    val last = d.orders.mapNotNull { it.no.takeIf { n -> n.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull() }
        .maxOrNull() ?: 0
    return prefix + (last + 1).toString().padStart(3, '0')
}
