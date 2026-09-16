package app.kopeechka.finance.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Откуда взялся черновик. */
const val INBOX_PHOTO = "photo"
const val INBOX_SMS = "sms"

/**
 * Черновик операции: приложение о нём догадалось само — по фотографии чека
 * или по банковской СМС, — но в бюджет он попадёт только после подтверждения.
 *
 * Тихая запись догадок в деньги хуже, чем её отсутствие: ошибётся распознавание
 * или придёт рекламная рассылка от банка — и человек будет искать, откуда взялся
 * лишний расход.
 *
 * Черновики живут только на этом телефоне: в резервную копию и в общий файл
 * синхронизации они не попадают (см. [AppData.forExport]) — там может лежать
 * текст банковской СМС.
 */
@Serializable
data class InboxItem(
    val id: Long,
    val source: String,
    /** Когда появился, миллисекунды — по нему сортируем список. */
    val at: Long,
    /** Предполагаемая дата операции, день от 1970-01-01. */
    val date: Long,
    val title: String = "",
    /** Всегда положительная; направление — в [income]. */
    val amount: Double = 0.0,
    val cur: String = "",
    val income: Boolean = false,
    val accId: String = "",
    val cat: String = "",
    val note: String = "",
    /** Исходник: текст СМС или строка позиций чека — чтобы человек мог проверить. */
    val raw: String = "",
)

/** Что модель вычитала из чека. */
data class ReceiptScan(
    val merchant: String,
    val date: Long?,
    val cur: String,
    val total: Double,
    val items: List<String>,
    val cat: String,
)

/**
 * Разбор ответа модели. Просим строгий JSON, но модели любят обернуть его
 * в ```json или добавить пару слов до и после — поэтому берём то, что между
 * первой фигурной скобкой и последней.
 */
object Receipt {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(answer: String): ReceiptScan? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        val total = num(obj, "total") ?: return null
        if (total <= 0) return null
        return ReceiptScan(
            merchant = str(obj, "merchant").take(60),
            date = str(obj, "date").takeIf { it.isNotBlank() }?.let { parseIsoDate(it)?.toEpochDay() },
            cur = str(obj, "cur").uppercase().take(4),
            total = total,
            items = obj["items"]?.let { el ->
                runCatching {
                    el.jsonArray.mapNotNull { row ->
                        val o = row.jsonObject
                        val name = str(o, "name").ifBlank { return@mapNotNull null }
                        val price = num(o, "price")
                        if (price == null) name else "$name — ${decimalString(price, 2)}"
                    }
                }.getOrNull()
            }.orEmpty().take(40),
            cat = str(obj, "category"),
        )
    }

    private fun str(o: JsonObject, key: String): String =
        runCatching { o[key]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty().trim()

    private fun num(o: JsonObject, key: String): Double? = runCatching {
        val p = o[key]?.jsonPrimitive ?: return null
        p.doubleOrNull ?: p.contentOrNull?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull()
    }.getOrNull()
}
