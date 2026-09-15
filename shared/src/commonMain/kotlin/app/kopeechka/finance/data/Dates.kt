package app.kopeechka.finance.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Работа с датами поверх kotlinx-datetime.
 *
 * Имена повторяют java.time, от которого приложение ушло ради общего кода
 * с iOS: так вся прежняя логика читается без изменений.
 */

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** День по номеру от 1970-01-01 — в этом виде даты лежат в файле данных. */
fun epochDate(day: Long): LocalDate = LocalDate.fromEpochDays(day.toInt())

fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()

val LocalDate.monthValue: Int get() = monthNumber

/** Понедельник — 1, воскресенье — 7, как в ISO. */
val LocalDate.dayOfWeekValue: Int get() = dayOfWeek.isoDayNumber

fun LocalDate.plusDays(n: Long): LocalDate = plus(DatePeriod(days = n.toInt()))
fun LocalDate.minusDays(n: Long): LocalDate = minus(DatePeriod(days = n.toInt()))
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(DatePeriod(days = (n * 7).toInt()))
fun LocalDate.plusMonths(n: Long): LocalDate = plus(DatePeriod(months = n.toInt()))
fun LocalDate.minusMonths(n: Long): LocalDate = minus(DatePeriod(months = n.toInt()))
fun LocalDate.plusYears(n: Long): LocalDate = plus(DatePeriod(years = n.toInt()))

fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

fun LocalDate.lengthOfMonth(): Int = when (monthNumber) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (isLeap(year)) 29 else 28
}

fun LocalDate.lengthOfYear(): Int = if (isLeap(year)) 366 else 365

fun LocalDate.withDayOfMonth(day: Int): LocalDate =
    LocalDate(year, monthNumber, day.coerceIn(1, lengthOfMonth()))

fun LocalDate.withDayOfYear(day: Int): LocalDate =
    LocalDate(year, 1, 1).plusDays((day - 1).toLong())

/** Понедельник той же недели. */
fun LocalDate.startOfWeek(): LocalDate = minusDays((dayOfWeekValue - 1).toLong())

/** «2026-09-14» — формат дат в CSV. */
fun LocalDate.isoString(): String =
    year.toString().padStart(4, '0') + "-" +
        monthNumber.toString().padStart(2, '0') + "-" +
        dayOfMonth.toString().padStart(2, '0')

/** Разбор «2026-09-14»; всё остальное — забота вызывающего. */
fun parseIsoDate(s: String): LocalDate? {
    val p = s.trim().split('-')
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return runCatching { LocalDate(y, m, d) }.getOrNull()
}

fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()
