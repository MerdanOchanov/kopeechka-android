package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CustomerEdit
import app.kopeechka.finance.ItemDraft
import app.kopeechka.finance.OrderDraft
import app.kopeechka.finance.Page
import app.kopeechka.finance.PaySheet
import app.kopeechka.finance.ProductEdit
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Cut
import app.kopeechka.finance.data.Order
import app.kopeechka.finance.data.OrderStatus
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Product
import app.kopeechka.finance.data.biz
import app.kopeechka.finance.data.customerName
import app.kopeechka.finance.data.orderCost
import app.kopeechka.finance.data.orderCur
import app.kopeechka.finance.data.orderProfit
import app.kopeechka.finance.data.orderTotal
import app.kopeechka.finance.data.ordersOf
import app.kopeechka.finance.data.ordersSorted
import app.kopeechka.finance.ui.theme.T
import kotlin.math.roundToInt

/** Цвет статуса: оплачен — хвойный, отменён — красный, остальные — стальные оттенки. */
@Composable
fun statusColor(status: String): Color = when (status) {
    OrderStatus.PAID -> hexColor("#4F7A5B", T.c.a700)
    OrderStatus.CANCELLED -> T.c.danger
    OrderStatus.DONE -> T.c.a800
    OrderStatus.WORK -> T.c.a600
    else -> T.c.n600
}

@Composable
private fun StatusTag(status: String) {
    val l = T.l
    val color = statusColor(status)
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f))
            .hairline(color.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(l.t(OrderStatus.key(status)).uppercase(), style = T.h(9.5.sp, color, 0.08.em), maxLines = 1)
    }
}

/** Строка заказа в списке. */
@Composable
fun OrderRow(vm: AppViewModel, c: Calc, o: Order) {
    val col = T.c
    val l = T.l
    val cur = c.orderCur(o)
    val profit = c.orderProfit(o)
    Column {
        Row(
            Modifier.fillMaxWidth().tap { vm.openOrder(o) }.padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("№ ${o.no}", style = T.h(11.sp, col.n600, 0.06.em))
                    StatusTag(o.status)
                }
                Text(c.customerName(o.customerId), style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.dayLabel(o.date) + " · " + o.items.joinToString(", ") { it.name }.ifBlank { l.t("biz.noItems") },
                    style = T.b(11.sp, col.n600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.fmt(c.orderTotal(o), cur), style = T.h(15.sp, col.text), maxLines = 1)
                Text(
                    l.t("biz.profitShort", c.fmt(profit, cur)),
                    style = T.b(10.5.sp, if (profit < 0) col.danger else col.a700),
                    maxLines = 1,
                )
            }
        }
        SoftDivider()
    }
}

// ——— Дело: сводка и заказы ———

@Composable
fun BusinessPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val r = c.range(vm.bizPeriod, vm.bizOffset)
    val stats = c.biz(r)
    val empty = c.d.orders.isEmpty() && c.d.products.isEmpty() && c.d.customers.isEmpty()

    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("biz.title")) { vm.page = null }

        Segments(Period.entries.map { l.t(it.key) }, vm.bizPeriod.ordinal, { vm.selectBizPeriod(Period.entries[it]) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconSquare(Icons.Back, { vm.shiftBizPeriod(-1) }, l.t("report.prev"))
            Text(
                r.title.uppercase(),
                style = T.h(15.sp, col.text, 0.08.em),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Box(Modifier.alpha(if (vm.bizOffset < 0) 1f else 0.35f)) {
                IconSquare(Icons.Forward, { if (vm.bizOffset < 0) vm.shiftBizPeriod(1) }, l.t("report.next"))
            }
        }

        StatGrid(
            listOf(
                Triple(l.t("biz.revenue"), c.fmtMain(stats.revenue), col.a700),
                Triple(l.t("biz.cost"), c.fmtMain(stats.cost), col.text),
                Triple(l.t("biz.profit"), c.fmtMain(stats.profit), if (stats.profit < 0) col.danger else col.text),
            ),
        )
        StatGrid(
            listOf(
                Triple(l.t("biz.paidCount"), stats.paidCount.toString(), col.text),
                Triple(l.t("biz.avgCheck"), c.fmtMain(stats.avg), col.text),
                Triple(l.t("biz.margin"), stats.marginText, if (stats.profit < 0) col.danger else col.a700),
            ),
        )
        if (stats.openCount > 0) {
            Blueprint(Modifier.fillMaxWidth(), PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(l.t("biz.open"))
                        Text(l.n(stats.openCount, "order"), style = T.b(13.sp, col.text))
                    }
                    Text(c.fmtMain(stats.openSum), style = T.h(17.sp, col.a700))
                }
            }
        }

        if (empty) {
            Muted(l.t("biz.emptyHint"), 12f)
            SecondaryButton(l.t("biz.loadDemo"), { vm.loadBizDemo() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        }

        Column {
            val filters = listOf("all" to l.t("biz.filterAll"), "open" to l.t("biz.filterOpen"), "paid" to l.t("biz.filterPaid"))
            SectionTitle(l.t("biz.orders"))
            Spacer(Modifier.height(8.dp))
            Segments(filters.map { it.second }, filters.indexOfFirst { it.first == vm.orderFilter }.coerceAtLeast(0), {
                vm.orderFilter = filters[it].first
            })
            Spacer(Modifier.height(8.dp))
            val orders = c.ordersSorted().filter { o ->
                when (vm.orderFilter) {
                    "open" -> OrderStatus.isOpen(o.status)
                    "paid" -> o.status == OrderStatus.PAID
                    else -> true
                }
            }
            if (orders.isEmpty()) Muted(l.t("biz.noOrders"), 12f)
            orders.take(40).forEach { OrderRow(vm, c, it) }
        }
        AddButton(l.t("biz.newOrder")) { vm.openOrder(null) }

        Column {
            NavRow(Icons.Tags, l.t("biz.price"), l.n(c.d.products.size, "product")) { vm.openPage(Page.PRODUCTS) }
            NavRow(Icons.User, l.t("biz.customers"), l.n(c.d.customers.size, "customer")) { vm.openPage(Page.CUSTOMERS) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(l.t("biz.exportOrders"), { vm.exportOrders() }, size = 12)
            Muted(l.t("biz.note"), 10.5f, color = col.n700)
        }
    }
}

// ——— Прайс ———

@Composable
fun ProductsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("biz.price")) { vm.page = Page.BUSINESS }
        Muted(l.t("biz.priceNote", Currencies.info(c.main).name), 11f)
        if (c.d.products.isEmpty()) Muted(l.t("biz.noProducts"), 12f)
        Column {
            c.d.products.forEach { p ->
                val margin = if (p.price > 0) ((p.price - p.cost) / p.price * 100).roundToInt() else 0
                Column {
                    Row(
                        Modifier.fillMaxWidth().tap { vm.openProduct(p) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CodeBox(p.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "??" }, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    p.unit.ifBlank { null },
                                    if (p.cost > 0) l.t("biz.costShort", c.fmtMain(p.cost)) else null,
                                ).joinToString(" · ").ifBlank { l.t("biz.noCost") },
                                style = T.b(11.sp, col.n600),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(c.fmtMain(p.price), style = T.h(15.sp, col.text))
                            if (p.cost > 0) Text("$margin%", style = T.b(10.5.sp, col.a700))
                        }
                    }
                    SoftDivider()
                }
            }
        }
        AddButton(l.t("biz.addProduct")) { vm.openProduct(null) }
    }
}

// ——— Клиенты ———

@Composable
fun CustomersPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("biz.customers")) { vm.page = Page.BUSINESS }
        if (c.d.customers.isEmpty()) Muted(l.t("biz.noCustomers"), 12f)
        Column {
            c.d.customers.forEach { cst ->
                val orders = c.ordersOf(cst.id)
                val paid = orders.filter { it.status == OrderStatus.PAID }
                val sum = paid.sumOf { c.toMain(c.orderTotal(it), c.orderCur(it)) }
                Column {
                    Row(
                        Modifier.fillMaxWidth().tap { vm.openCustomer(cst) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CodeBox(cst.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "??" }, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cst.name, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(cst.contact.ifBlank { null }, l.n(orders.size, "order")).joinToString(" · "),
                                style = T.b(11.sp, col.n600),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(c.fmtMain(sum), style = T.h(15.sp, col.text))
                    }
                    SoftDivider()
                }
            }
        }
        AddButton(l.t("biz.addCustomer")) { vm.openCustomer(null) }
    }
}

// ——— Редактор заказа ———

@Composable
fun OrderOverlay(vm: AppViewModel, c: Calc, e: OrderDraft) {
    val col = T.c
    val l = T.l
    val cur = e.cur.ifBlank { c.main }
    val total = vm.draftTotal(e)
    val cost = vm.draftCost(e)
    val profit = total - cost
    val paid = e.status == OrderStatus.PAID

    OverlayScreen(
        if (e.id == null) l.t("biz.newOrder") else l.t("biz.order", e.no),
        l.t("common.cancel"),
        { vm.orderDraft = null },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(l.t("biz.total"))
                        Text(c.fmt(total, cur), style = T.h(22.sp, col.text), maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Kicker(l.t("biz.profit"))
                        Text(c.fmt(profit, cur), style = T.h(17.sp, if (profit < 0) col.danger else col.a700), maxLines = 1)
                    }
                }
                PrimaryButton(l.t("common.save"), { vm.saveOrder() })
                if (e.id != null && !paid) {
                    SecondaryButton(l.t("biz.pay"), { vm.openPay(e.id) }, Modifier.fillMaxWidth(), size = 13, upper = true)
                }
            }
        },
    ) {
        // клиент
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("biz.customer"))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(l.t("biz.noCustomer"), e.customerId.isBlank() && e.newCustomer.isBlank(), {
                    vm.orderDraft = e.copy(customerId = "", newCustomer = "")
                })
                c.d.customers.forEach { cst ->
                    Chip(cst.name, e.customerId == cst.id && e.newCustomer.isBlank(), {
                        vm.orderDraft = e.copy(customerId = cst.id, newCustomer = "")
                    })
                }
            }
            Field(
                null,
                e.newCustomer,
                { vm.orderDraft = e.copy(newCustomer = it) },
                placeholder = l.t("biz.newCustomerHint"),
            )
        }

        // дата
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("add.date"))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0..9).forEach { back ->
                    val day = c.todayDay - back
                    Chip(c.dayLabel(day), e.date == day, { vm.orderDraft = e.copy(date = day) })
                }
            }
        }

        // позиции
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(l.t("biz.items"))
            if (e.items.isEmpty()) Muted(l.t("biz.noItemsHint"), 11.5f)
            e.items.forEachIndexed { i, item ->
                ItemEditor(vm, c, cur, i, item)
            }
            AddButton(l.t("biz.addItem")) { vm.orderDraft = e.copy(picking = true) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                Field(l.t("biz.discount"), e.discount, { vm.orderDraft = e.copy(discount = it) }, numeric = true, placeholder = "0")
            }
            Box(Modifier.weight(1f)) {
                Field(l.t("biz.extraCost"), e.extraCost, { vm.orderDraft = e.copy(extraCost = it) }, numeric = true, placeholder = "0")
            }
        }
        Muted(l.t("biz.costLine", c.fmt(cost, cur)), 11f)

        // статус
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("biz.status"))
            if (paid) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusTag(OrderStatus.PAID)
                    Spacer(Modifier.weight(1f))
                    if (e.id != null) GhostButton(l.t("biz.unpayAction"), { vm.unpay(e.id) }, size = 11)
                }
            } else {
                val steps = listOf(OrderStatus.NEW, OrderStatus.WORK, OrderStatus.DONE, OrderStatus.CANCELLED)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    steps.forEach { s ->
                        Chip(l.t(OrderStatus.key(s)), e.status == s, { vm.orderDraft = e.copy(status = s) }, accent = statusColor(s))
                    }
                }
            }
        }

        Field(l.t("biz.orderNote"), e.note, { vm.orderDraft = e.copy(note = it) }, placeholder = l.t("biz.orderNoteHint"), minLines = 2)

        if (e.id != null) DangerButton(l.t("biz.deleteOrder"), { vm.askDeleteOrder(e.id) })
    }
}

/** Одна позиция заказа: название, количество и цена правятся на месте. */
@Composable
private fun ItemEditor(vm: AppViewModel, c: Calc, cur: String, index: Int, item: ItemDraft) {
    val col = T.c
    val l = T.l
    val sum = (item.qty.replace(',', '.').toDoubleOrNull() ?: 0.0) * (item.price.replace(',', '.').toDoubleOrNull() ?: 0.0)
    Column(
        Modifier.fillMaxWidth().hairline(col.divider).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Field(null, item.name, { vm.setItem(index, item.copy(name = it)) }, placeholder = l.t("biz.itemName"))
            }
            Text(l.t("common.remove"), style = T.h(11.sp, col.danger), modifier = Modifier.tap { vm.removeItem(index) }.padding(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(72.dp)) {
                Field(null, item.qty, { vm.setItem(index, item.copy(qty = it)) }, numeric = true, placeholder = "1")
            }
            Text("×", style = T.b(13.sp, col.n600))
            Box(Modifier.weight(1f)) {
                Field(null, item.price, { vm.setItem(index, item.copy(price = it)) }, numeric = true, placeholder = l.t("biz.itemPrice"))
            }
            Text(c.fmt(sum, cur), style = T.h(13.sp, col.text), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(l.t("biz.itemCost"), style = T.b(11.sp, col.n600))
            Box(Modifier.width(96.dp)) {
                Field(null, item.cost, { vm.setItem(index, item.copy(cost = it)) }, numeric = true, placeholder = "0")
            }
        }
    }
}

/** Выбор позиции: из прайса или своя строка. */
@Composable
fun ItemPickerSheet(vm: AppViewModel, c: Calc) {
    val l = T.l
    BottomSheet({ vm.orderDraft = vm.orderDraft?.copy(picking = false) }) {
        Text(l.t("biz.pickItem").uppercase(), style = T.h(13.sp, T.c.text, 0.12.em))
        if (c.d.products.isEmpty()) Muted(l.t("biz.noProducts"), 11.5f)
        c.d.products.filterNot { it.archived }.take(20).forEach { p: Product ->
            Row(
                Modifier.fillMaxWidth().tap { vm.addItem(p) }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(p.name, style = T.b(14.sp, T.c.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.fmtMain(p.price), style = T.h(13.sp, T.c.text))
            }
            SoftDivider()
        }
        SecondaryButton(l.t("biz.customItem"), { vm.addItem(null) }, Modifier.fillMaxWidth(), size = 13, upper = true)
    }
}

// ——— Приём оплаты ———

@Composable
fun PaySheetView(vm: AppViewModel, c: Calc, ps: PaySheet) {
    val col = T.c
    val l = T.l
    val o = c.d.orders.firstOrNull { it.id == ps.orderId } ?: return
    val cur = c.orderCur(o)
    val acc = c.acc(ps.acc)
    val accCur = acc?.cur ?: c.main
    val total = c.conv(c.orderTotal(o), cur, accCur)
    val cost = c.conv(c.orderCost(o), cur, accCur)

    BottomSheet({ vm.paySheet = null }) {
        Text(l.t("biz.payTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Text(c.fmt(total, accCur), style = T.h(26.sp, col.text))
        Muted(l.t("biz.payDate", c.dayLabel(o.date)), 11.5f)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("biz.payTo"))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                c.d.accounts.forEach { a ->
                    Chip("${a.name} · ${a.cur}", a.id == ps.acc, { vm.paySheet = ps.copy(acc = a.id) })
                }
            }
        }
        if (cost > 0) {
            SettingRow(l.t("biz.writeCost"), l.t("biz.writeCostSub", c.fmt(cost, accCur))) {
                Toggle(ps.writeCost) { vm.paySheet = ps.copy(writeCost = it) }
            }
        }
        PrimaryButton(l.t("biz.payAction"), { vm.confirmPay() })
    }
}

// ——— Прайс и клиент: редакторы ———

@Composable
fun ProductOverlay(vm: AppViewModel, c: Calc, e: ProductEdit) {
    val l = T.l
    OverlayScreen(
        if (e.id == null) l.t("biz.newProduct") else l.t("biz.product"),
        l.t("common.cancel"),
        { vm.productEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveProduct() }) },
    ) {
        Field(l.t("biz.productName"), e.name, { vm.productEdit = e.copy(name = it) }, placeholder = l.t("biz.productNameHint"))
        Field(
            l.t("biz.priceField", Currencies.sym(c.main)),
            e.price,
            { vm.productEdit = e.copy(price = it) },
            numeric = true,
            placeholder = "0",
        )
        Field(
            l.t("biz.costField", Currencies.sym(c.main)),
            e.cost,
            { vm.productEdit = e.copy(cost = it) },
            numeric = true,
            placeholder = "0",
            note = l.t("biz.costNote"),
        )
        Field(l.t("biz.unit"), e.unit, { vm.productEdit = e.copy(unit = it) }, placeholder = l.t("biz.unitHint"))
        if (e.id != null) DangerButton(l.t("biz.deleteProduct"), { vm.askDeleteProduct(e.id) })
    }
}

@Composable
fun CustomerOverlay(vm: AppViewModel, c: Calc, e: CustomerEdit) {
    val col = T.c
    val l = T.l
    OverlayScreen(
        if (e.id == null) l.t("biz.newCustomer") else l.t("biz.customer"),
        l.t("common.cancel"),
        { vm.customerEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveCustomer() }) },
    ) {
        Field(l.t("biz.customerName"), e.name, { vm.customerEdit = e.copy(name = it) }, placeholder = l.t("biz.customerNameHint"))
        Field(l.t("biz.contact"), e.contact, { vm.customerEdit = e.copy(contact = it) }, placeholder = l.t("biz.contactHint"))
        Field(l.t("biz.customerNote"), e.note, { vm.customerEdit = e.copy(note = it) }, minLines = 2)

        if (e.id != null) {
            val orders = c.ordersOf(e.id)
            val paid = orders.filter { it.status == OrderStatus.PAID }
            val sum = paid.sumOf { c.toMain(c.orderTotal(it), c.orderCur(it)) }
            val profit = paid.sumOf { c.toMain(c.orderProfit(it), c.orderCur(it)) }
            StatGrid(
                listOf(
                    Triple(l.t("biz.orders"), orders.size.toString(), col.text),
                    Triple(l.t("biz.bought"), c.fmtMain(sum), col.text),
                    Triple(l.t("biz.profit"), c.fmtMain(profit), if (profit < 0) col.danger else col.a700),
                ),
            )
            Column {
                orders.take(10).forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().tap { vm.openOrder(o) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusTag(o.status)
                        Text(c.dayLabel(o.date), style = T.b(12.sp, col.n600), modifier = Modifier.weight(1f), maxLines = 1)
                        Text(c.fmt(c.orderTotal(o), c.orderCur(o)), style = T.h(13.sp, col.text))
                    }
                    SoftDivider()
                }
                orders.firstOrNull()?.let { last ->
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(l.t("biz.repeat"), { vm.repeatOrder(last) }, Modifier.fillMaxWidth(), size = 12, upper = true)
                }
            }
            DangerButton(l.t("biz.deleteCustomer"), { vm.askDeleteCustomer(e.id) })
        }
    }
}

// ——— Карточка «Дело» на главном экране ———

@Composable
fun BusinessCard(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val r = c.range(Period.MONTH, 0)
    val stats = c.biz(r)
    ReportCard(l.t("biz.title"), l.t("biz.cardHint"), { vm.openPage(Page.BUSINESS) }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Kicker(l.t("biz.revenue"))
                Text(c.fmtMain(stats.revenue), style = T.h(20.sp, col.a700), maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Kicker(l.t("biz.profit"))
                Text(
                    c.fmtMain(stats.profit),
                    style = T.h(20.sp, if (stats.profit < 0) col.danger else col.text),
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Kicker(l.t("biz.open"))
                Text(stats.openCount.toString(), style = T.h(20.sp, col.text), maxLines = 1)
            }
        }
        if (stats.paidCount > 0 || stats.openCount > 0) {
            Spacer(Modifier.height(10.dp))
            Muted(
                l.t("biz.cardLine", l.n(stats.paidCount, "order"), c.fmtMain(stats.avg), stats.marginText),
                11f,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Muted(l.t("biz.cardEmpty"), 11.5f)
        }
    }
}

/** Разрезы «Дела» в отчётах: столбики выручки и список товаров или клиентов. */
@Composable
fun BizCutStats(c: Calc, cut: Cut, stats: app.kopeechka.finance.data.BizStats) {
    val col = T.c
    val l = T.l
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatGrid(
            listOf(
                Triple(l.t("biz.revenue"), c.fmtMain(stats.revenue), col.a700),
                Triple(l.t("biz.cost"), c.fmtMain(stats.cost), col.text),
                Triple(l.t("biz.profit"), c.fmtMain(stats.profit), if (stats.profit < 0) col.danger else col.text),
            ),
        )
        Muted(
            if (cut == Cut.PRODUCTS) l.t("biz.cutProductsNote") else l.t("biz.cutClientsNote"),
            10.5f,
            color = col.n700,
        )
    }
}
