package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.ui.theme.T

fun Modifier.hairline(color: Color, width: Dp = 1.dp) = border(width, color)

/** Регистрационные метки «+» по углам — фирменный знак Industry (.blueprint .corner). */
fun Modifier.blueprintMarks(color: Color) = drawWithContent {
    drawContent()
    val arm = 5.5.dp.toPx()
    val w = 1.dp.toPx()
    listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { c ->
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), w)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), w)
    }
}

@Composable
fun Blueprint(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(14.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = T.c
    Column(
        modifier
            .hairline(c.divider)
            .blueprintMarks(c.text.copy(alpha = 0.55f))
            .padding(padding),
        content = content,
    )
}

@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

@Composable
fun Modifier.tapNoRipple(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = T.h(15.sp, T.c.text, 0.06.em))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Kicker(text: String, color: Color = T.c.n600, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.kicker(color), modifier = modifier)
}

@Composable
fun Muted(text: String, size: Float = 11f, modifier: Modifier = Modifier, color: Color = T.c.n600, align: TextAlign? = null) {
    Text(text, style = T.b(size.sp, color), modifier = modifier, textAlign = align)
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, size: Int = 12) {
    Text(
        text,
        style = T.h(size.sp, T.c.a700),
        modifier = Modifier
            .tap(onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
    )
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 12, upper: Boolean = false) {
    val c = T.c
    Box(
        modifier
            .hairline(c.divider)
            .tap(onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (upper) text.uppercase() else text, style = T.h(size.sp, c.text, if (upper) 0.1.em else 0.sp), textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = T.c
    Box(
        modifier
            .fillMaxWidth()
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.45f))
            .hairline(c.a700)
            .blueprintMarks(c.text.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = T.h(14.sp, Color(0xFFF2F2F3), 0.14.em))
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = T.c
    Box(
        modifier
            .fillMaxWidth()
            .hairline(c.danger.copy(alpha = 0.5f))
            .tap(onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = T.h(13.sp, c.danger, 0.1.em))
    }
}

data class OptStyle(val bg: Color, val fg: Color, val border: Color)

@Composable
fun opt(on: Boolean): OptStyle {
    val c = T.c
    return if (on) OptStyle(c.a200, c.a800, c.a600) else OptStyle(Color.Transparent, c.n700, c.divider)
}

@Composable
fun solid(on: Boolean): OptStyle {
    val c = T.c
    return if (on) OptStyle(c.hero, c.onAccent, c.divider) else OptStyle(Color.Transparent, c.n700, c.divider)
}

/** Сегментная кнопка «Неделя / Месяц / Год», «Все / Расходы…» — заливка hero у выбранной. */
@Composable
fun Segments(items: List<String>, selected: Int, onSelect: (Int) -> Unit, size: Float = 11f, spacing: Float = 0.1f) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEachIndexed { i, label ->
            val st = solid(i == selected)
            Box(
                Modifier
                    .weight(1f)
                    .background(st.bg)
                    .hairline(st.border)
                    .tap { onSelect(i) }
                    .padding(vertical = 8.dp, horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = T.h(size.sp, st.fg, spacing.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Слитный переключатель в рамке (Расход | Доход | Перевод). */
@Composable
fun JoinedSegments(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = T.c
    Row(Modifier.fillMaxWidth().hairline(c.divider)) {
        items.forEachIndexed { i, label ->
            val st = solid(i == selected)
            if (i > 0) Box(Modifier.width(1.dp).height(38.dp).background(c.divider))
            Box(
                Modifier
                    .weight(1f)
                    .background(st.bg)
                    .tap { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = T.h(11.5.sp, st.fg, 0.1.em))
            }
        }
    }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, code: String? = null, modifier: Modifier = Modifier, accent: Color? = null) {
    val st = if (on && accent != null) OptStyle(accent.copy(alpha = 0.14f), T.c.text, accent.copy(alpha = 0.6f)) else opt(on)
    Row(
        modifier
            .background(st.bg)
            .hairline(st.border)
            .tap(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (code != null) Text(code, style = T.h(9.5.sp, st.fg.copy(alpha = 0.7f), 0.06.em))
        Text(label, style = T.b(12.5.sp, st.fg))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun CodeBox(code: String, size: Dp = 28.dp, color: Color = T.c.a700, tinted: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .then(if (tinted) Modifier.background(color.copy(alpha = 0.12f)) else Modifier)
            .hairline(if (tinted) color.copy(alpha = 0.55f) else T.c.divider),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = T.h(10.sp, color, 0.04.em))
    }
}

@Composable
fun ProgressLine(fraction: Float, color: Color, height: Dp = 7.dp) {
    val c = T.c
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(c.n200)
            .hairline(c.divider),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
fun TxLine(
    code: String,
    title: String,
    meta: String,
    amount: String,
    alt: String,
    tone: Color,
    topBorder: Boolean = false,
    codeColor: Color = T.c.a700,
    tinted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = T.c
    Column {
        if (topBorder) Box(Modifier.fillMaxWidth().height(1.dp).background(c.soft))
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CodeBox(code, 28.dp, codeColor, tinted)
            Column(Modifier.weight(1f)) {
                Text(title, style = T.b(13.5.sp, c.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = T.b(11.sp, c.n600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = T.h(15.sp, tone), maxLines = 1)
                if (alt.isNotEmpty()) Text(alt, style = T.b(10.sp, c.n600), maxLines = 1)
            }
        }
        if (!topBorder) Box(Modifier.fillMaxWidth().height(1.dp).background(c.soft))
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(T.c.divider))
}

@Composable
fun SoftDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(T.c.soft))
}

/**
 * Цифровая клавиатура. `fill = true` растягивает её на всю доступную высоту —
 * так экран новой операции обходится без вертикальной прокрутки.
 */
@Composable
fun Keypad(modifier: Modifier = Modifier, fill: Boolean = false, onKey: (String) -> Unit) {
    val c = T.c
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "⌫")
    Column(modifier.fillMaxWidth().background(c.divider).hairline(c.divider)) {
        keys.chunked(3).forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(1.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (fill) Modifier.weight(1f) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .then(if (fill) Modifier.fillMaxHeight() else Modifier.heightIn(min = 52.dp))
                            .background(c.bg)
                            .tap { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, style = T.h(22.sp, c.text))
                    }
                }
            }
        }
    }
}

@Composable
fun Field(
    label: String?,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    minLines: Int = 1,
    numeric: Boolean = false,
    note: String? = null,
) {
    val c = T.c
    Column(modifier.fillMaxWidth()) {
        if (label != null) Text(label, style = T.b(12.sp, c.text.copy(alpha = 0.7f)), modifier = Modifier.padding(bottom = 5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp + ((minLines - 1) * 20).dp)
                .background(c.surface)
                .hairline(c.divider)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, style = T.b(14.sp, c.n500))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = T.b(14.sp, c.text),
                cursorBrush = SolidColor(c.accent),
                singleLine = minLines == 1,
                minLines = minLines,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = when {
                    password -> KeyboardOptions(keyboardType = KeyboardType.Password)
                    numeric -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    else -> KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (note != null) Text(note, style = T.b(10.5.sp, c.n600), modifier = Modifier.padding(top = 5.dp))
    }
}

/** Квадратный переключатель в стиле чертежа. */
@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = T.c
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .background(if (on) c.a200 else Color.Transparent)
            .hairline(if (on) c.a600 else c.divider)
            .tap { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).background(if (on) c.a700 else c.n400))
    }
}

@Composable
fun SettingRow(title: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = T.c
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.b(13.5.sp, c.text))
                if (sub != null) Text(sub, style = T.b(11.sp, c.n600))
            }
            trailing()
        }
        SoftDivider()
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = T.c
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap(onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).hairline(c.divider), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.a700, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = T.b(14.sp, c.text))
                Text(sub, style = T.b(11.sp, c.n600))
            }
            Icon(Icons.Chevron, null, tint = c.n500, modifier = Modifier.size(16.dp))
        }
        SoftDivider()
    }
}

@Composable
fun IconSquare(icon: ImageVector, onClick: () -> Unit, contentDesc: String? = null) {
    val c = T.c
    Box(
        Modifier
            .size(28.dp)
            .hairline(c.divider)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDesc, tint = c.a700, modifier = Modifier.size(15.dp))
    }
}

/** Полноэкранный оверлей: шапка с заголовком и кнопкой справа + прокручиваемое тело. */
@Composable
fun OverlayScreen(
    title: String,
    action: String,
    onAction: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = T.c
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.uppercase(), style = T.h(15.sp, c.text, 0.12.em), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            GhostButton(action, onAction, size = 13)
        }
        Divider()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollSafe()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = body,
        )
        if (footer != null) {
            Divider()
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) { footer() }
        }
    }
}

/** Экран-страница с кнопкой «назад» (для разделов настроек). */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    val c = T.c
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconSquare(Icons.Back, onBack, T.l.t("common.back"))
        Text(title.uppercase(), style = T.h(17.sp, c.text, 0.08.em))
    }
}

@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = T.c
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .tapNoRipple(onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .tapNoRipple { }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            content()
        }
    }
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    val c = T.c
    Row(
        modifier
            .fillMaxWidth()
            .background(c.hero)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(c.a300))
        Text(text, style = T.b(12.5.sp, c.onAccent))
    }
}

@Composable
fun StatGrid(cells: List<Triple<String, String, Color>>, onClick: ((Int) -> Unit)? = null) {
    val c = T.c
    Column(Modifier.fillMaxWidth().background(c.divider).hairline(c.divider)) {
        cells.chunked(3).forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(1.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEachIndexed { i, cell ->
                    val idx = r * 3 + i
                    Column(
                        Modifier
                            .weight(1f)
                            .background(c.bg)
                            .then(if (onClick != null) Modifier.tap { onClick(idx) } else Modifier)
                            .padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(cell.first.uppercase(), style = T.b(9.5.sp, c.n600, 0.12.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(cell.second, style = T.h(15.sp, cell.third), maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f).background(c.bg)) }
            }
        }
    }
}

@Composable
fun BoxScope.CenterText(text: String) {
    Text(text, style = T.b(13.sp, T.c.n600), modifier = Modifier.align(Alignment.Center))
}

@Composable
fun Modifier.verticalScrollSafe(): Modifier = this.verticalScroll(rememberScrollState())

@Composable
fun MaxWidth(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), content = content)
}
