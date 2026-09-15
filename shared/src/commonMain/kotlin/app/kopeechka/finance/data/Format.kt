package app.kopeechka.finance.data

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong

/**
 * Форматирование чисел без java.text: одинаково на Android и iOS.
 * Разделитель разрядов задаётся языком (9 024 против 9,024).
 */

/** «1 234 567» — целое с разделителем разрядов. */
fun groupNumber(value: Long, separator: Char): String {
    val negative = value < 0
    val digits = abs(value).toString()
    val sb = StringBuilder()
    for ((i, ch) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(separator)
        sb.append(ch)
    }
    return if (negative) "−$sb" else sb.toString()
}

/** «1 234 567,89» — два знака после запятой. */
fun groupDecimal(value: Double, separator: Char, decimal: Char): String {
    val negative = value < 0
    val cents = round(abs(value) * 100).toLong()
    val whole = groupNumber(cents / 100, separator)
    val frac = (cents % 100).toString().padStart(2, '0')
    return (if (negative) "−" else "") + whole + decimal + frac
}

/** Замена String.format("%.2f") — используется в CSV, где разделитель всегда точка. */
fun fixed2(value: Double): String {
    val negative = value < 0
    val cents = round(abs(value) * 100).toLong()
    return (if (negative) "-" else "") + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
}

/** Число без хвоста, если оно целое: «12» вместо «12.0». */
fun plainNumber(value: Double): String =
    if (value == value.roundToLong().toDouble()) value.roundToLong().toString() else value.toString()
