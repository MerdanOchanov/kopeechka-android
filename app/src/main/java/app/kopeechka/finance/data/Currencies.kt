package app.kopeechka.finance.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Currencies {
    data class Info(val code: String, val sym: String, val name: String, val inName: String)

    /** Базовая валюта курсов: 1 RUB = 1. */
    const val BASE = "RUB"

    /** Валюты, включённые в новом профиле. */
    val DEFAULT_CODES = listOf("RUB", "USD", "EUR", "KZT", "TMT")

    val DEFAULT_RATES = mapOf("RUB" to 1.0, "USD" to 92.0, "EUR" to 100.0, "KZT" to 0.18, "TMT" to 26.3)

    /** Каталог мировых валют: любую можно добавить в настройках. */
    val CATALOG: List<Info> = listOf(
        Info("RUB", "₽", "российские рубли", "рублях"),
        Info("USD", "$", "доллары США", "долларах"),
        Info("EUR", "€", "евро", "евро"),
        Info("KZT", "₸", "казахстанские тенге", "тенге"),
        Info("TMT", "m", "туркменские манаты", "манатах"),
        Info("AZN", "₼", "азербайджанские манаты", "манатах"),
        Info("UZS", "soʻm", "узбекские сумы", "сумах"),
        Info("KGS", "с", "киргизские сомы", "сомах"),
        Info("TJS", "SM", "таджикские сомони", "сомони"),
        Info("BYN", "Br", "белорусские рубли", "рублях"),
        Info("UAH", "₴", "украинские гривны", "гривнах"),
        Info("GEL", "₾", "грузинские лари", "лари"),
        Info("AMD", "֏", "армянские драмы", "драмах"),
        Info("MDL", "L", "молдавские леи", "леях"),
        Info("TRY", "₺", "турецкие лиры", "лирах"),
        Info("GBP", "£", "фунты стерлингов", "фунтах"),
        Info("CHF", "Fr", "швейцарские франки", "франках"),
        Info("CNY", "¥", "китайские юани", "юанях"),
        Info("JPY", "¥", "японские иены", "иенах"),
        Info("KRW", "₩", "южнокорейские воны", "вонах"),
        Info("INR", "₹", "индийские рупии", "рупиях"),
        Info("AED", "AED", "дирхамы ОАЭ", "дирхамах"),
        Info("SAR", "SAR", "саудовские риялы", "риялах"),
        Info("QAR", "QAR", "катарские риалы", "риалах"),
        Info("ILS", "₪", "израильские шекели", "шекелях"),
        Info("EGP", "EGP", "египетские фунты", "фунтах"),
        Info("THB", "฿", "таиландские баты", "батах"),
        Info("VND", "₫", "вьетнамские донги", "донгах"),
        Info("IDR", "Rp", "индонезийские рупии", "рупиях"),
        Info("MYR", "RM", "малайзийские ринггиты", "ринггитах"),
        Info("SGD", "S$", "сингапурские доллары", "долларах"),
        Info("HKD", "HK$", "гонконгские доллары", "долларах"),
        Info("AUD", "A$", "австралийские доллары", "долларах"),
        Info("NZD", "NZ$", "новозеландские доллары", "долларах"),
        Info("CAD", "C$", "канадские доллары", "долларах"),
        Info("MXN", "MX$", "мексиканские песо", "песо"),
        Info("BRL", "R$", "бразильские реалы", "реалах"),
        Info("ARS", "AR$", "аргентинские песо", "песо"),
        Info("CLP", "CL$", "чилийские песо", "песо"),
        Info("COP", "CO$", "колумбийские песо", "песо"),
        Info("PEN", "S/", "перуанские соли", "солях"),
        Info("ZAR", "R", "южноафриканские рэнды", "рэндах"),
        Info("NGN", "₦", "нигерийские найры", "найрах"),
        Info("KES", "KSh", "кенийские шиллинги", "шиллингах"),
        Info("MAD", "DH", "марокканские дирхамы", "дирхамах"),
        Info("TND", "DT", "тунисские динары", "динарах"),
        Info("PLN", "zł", "польские злотые", "злотых"),
        Info("CZK", "Kč", "чешские кроны", "кронах"),
        Info("HUF", "Ft", "венгерские форинты", "форинтах"),
        Info("RON", "lei", "румынские леи", "леях"),
        Info("BGN", "лв", "болгарские левы", "левах"),
        Info("RSD", "дин", "сербские динары", "динарах"),
        Info("SEK", "kr", "шведские кроны", "кронах"),
        Info("NOK", "kr", "норвежские кроны", "кронах"),
        Info("DKK", "kr", "датские кроны", "кронах"),
        Info("ISK", "kr", "исландские кроны", "кронах"),
        Info("PKR", "₨", "пакистанские рупии", "рупиях"),
        Info("BDT", "৳", "бангладешские таки", "таках"),
        Info("LKR", "Rs", "шри-ланкийские рупии", "рупиях"),
        Info("NPR", "Rs", "непальские рупии", "рупиях"),
        Info("MNT", "₮", "монгольские тугрики", "тугриках"),
        Info("PHP", "₱", "филиппинские песо", "песо"),
        Info("TWD", "NT$", "тайваньские доллары", "долларах"),
        Info("IRR", "IRR", "иранские риалы", "риалах"),
        Info("IQD", "IQD", "иракские динары", "динарах"),
        Info("JOD", "JD", "иорданские динары", "динарах"),
        Info("KWD", "KD", "кувейтские динары", "динарах"),
        Info("BHD", "BD", "бахрейнские динары", "динарах"),
        Info("OMR", "OMR", "оманские риалы", "риалах"),
        Info("LBP", "LBP", "ливанские фунты", "фунтах"),
        Info("AFN", "AFN", "афганские афгани", "афгани"),
    )

    /**
     * Ориентировочный курс в рублях за единицу — подставляется при добавлении валюты.
     * Курсы в приложении задаются вручную, это лишь стартовое значение.
     */
    val RATE_HINTS: Map<String, Double> = mapOf(
        "RUB" to 1.0, "USD" to 92.0, "EUR" to 100.0, "KZT" to 0.18, "TMT" to 26.3,
        "AZN" to 54.0, "UZS" to 0.0073, "KGS" to 1.05, "TJS" to 8.4, "BYN" to 28.0,
        "UAH" to 2.2, "GEL" to 34.0, "AMD" to 0.24, "MDL" to 5.2, "TRY" to 2.7,
        "GBP" to 117.0, "CHF" to 104.0, "CNY" to 12.7, "JPY" to 0.6, "KRW" to 0.068,
        "INR" to 1.1, "AED" to 25.0, "SAR" to 24.5, "QAR" to 25.0, "ILS" to 25.0,
        "EGP" to 1.9, "THB" to 2.6, "VND" to 0.0037, "IDR" to 0.0057, "MYR" to 20.0,
        "SGD" to 68.0, "HKD" to 11.8, "AUD" to 60.0, "NZD" to 55.0, "CAD" to 67.0,
        "MXN" to 4.6, "BRL" to 17.0, "ARS" to 0.1, "CLP" to 0.1, "COP" to 0.023,
        "PEN" to 25.0, "ZAR" to 5.0, "NGN" to 0.06, "KES" to 0.7, "MAD" to 9.2,
        "TND" to 30.0, "PLN" to 23.0, "CZK" to 4.0, "HUF" to 0.25, "RON" to 20.0,
        "BGN" to 51.0, "RSD" to 0.85, "SEK" to 8.7, "NOK" to 8.6, "DKK" to 13.4,
        "ISK" to 0.67, "PKR" to 0.33, "BDT" to 0.78, "LKR" to 0.31, "NPR" to 0.69,
        "MNT" to 0.027, "PHP" to 1.6, "TWD" to 2.9, "IRR" to 0.002, "IQD" to 0.07,
        "JOD" to 130.0, "KWD" to 300.0, "BHD" to 244.0, "OMR" to 239.0, "LBP" to 0.001,
        "AFN" to 1.3,
    )

    private val byCode = CATALOG.associateBy { it.code }

    /** Валюты, которые пользователь завёл вручную (их нет в каталоге). */
    @Volatile
    private var custom: Map<String, Info> = emptyMap()

    fun registerCustom(defs: List<CurrencyDef>) {
        custom = defs.associate {
            it.code to Info(it.code, it.sym.ifBlank { it.code }, it.name.ifBlank { it.code }, it.inName.ifBlank { it.name })
        }
    }

    fun info(code: String): Info = custom[code] ?: byCode[code] ?: Info(code, code, code, code)
    fun sym(code: String) = info(code).sym
    fun inCatalog(code: String) = byCode.containsKey(code)
    fun defaultRate(code: String) = RATE_HINTS[code] ?: 1.0

    /** Поиск по каталогу для экрана добавления валюты. */
    fun search(query: String, exclude: Set<String>): List<Info> {
        val q = query.trim().lowercase()
        return CATALOG.filter { it.code !in exclude }
            .filter { q.isEmpty() || it.code.lowercase().startsWith(q) || it.name.lowercase().contains(q) || it.sym.lowercase() == q }
    }

    private val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    private val whole = DecimalFormat("#,##0", symbols)
    private val cents = DecimalFormat("#,##0.00", symbols)

    /** 112 480 ₽ — без знака, модуль берёт вызывающий. */
    fun fmt(v: Double, cur: String, kopecks: Boolean = false): String {
        val n = if (kopecks) cents.format(v) else whole.format(v.roundToLong())
        return n + " " + sym(cur)
    }

    fun fmtNumber(v: Double): String = whole.format(v.roundToLong())

    /** «−1 840 ₽» / «+96 400 ₽» */
    fun fmtSigned(v: Double, cur: String, kopecks: Boolean = false): String =
        (if (v > 0) "+" else if (v < 0) "−" else "") + fmt(abs(v), cur, kopecks)

    /** «12к» для подписей столбиков */
    fun short(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1_000_000 -> (v / 1_000_000).roundToLong().toString() + "м"
            a >= 1000 -> (v / 1000).roundToLong().toString() + "к"
            else -> v.roundToLong().toString()
        }
    }
}
