package app.kopeechka.finance.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Currencies {
    /**
     * Язык названий и формата чисел. Ставится из Store при загрузке и каждом изменении
     * настроек, чтобы `info(...)` и `fmt(...)` можно было звать без лишних параметров.
     */
    @Volatile
    var lang: Lang = Lang.RU
        private set

    data class Info(
        val code: String,
        val sym: String,
        private val ru: String,
        private val ruIn: String,
        private val en: String,
        private val tk: String = "",
    ) {
        /** Название на текущем языке: «доллары США» / «US dollars» / «ABŞ dollary». */
        val name: String
            get() = when (lang.code) {
                "en" -> en
                "tk" -> tk.ifBlank { en }
                else -> ru
            }

        /** Предложный падеж — нужен только русскому («в рублях»). */
        val inName: String get() = if (lang.code == "ru") ruIn else name
    }

    /** Базовая валюта курсов: 1 RUB = 1. */
    const val BASE = "RUB"

    /** Валюты, включённые в новом профиле. */
    val DEFAULT_CODES = listOf("RUB", "USD", "EUR", "KZT", "TMT")

    val DEFAULT_RATES = mapOf("RUB" to 1.0, "USD" to 92.0, "EUR" to 100.0, "KZT" to 0.18, "TMT" to 26.3)

    /** Каталог мировых валют: любую можно добавить в настройках. */
    val CATALOG: List<Info> = listOf(
        Info("RUB", "₽", "российские рубли", "рублях", "Russian roubles", "rus rubly"),
        Info("USD", "$", "доллары США", "долларах", "US dollars", "ABŞ dollary"),
        Info("EUR", "€", "евро", "евро", "euros", "ýewro"),
        Info("KZT", "₸", "казахстанские тенге", "тенге", "Kazakh tenge", "gazak teňňesi"),
        Info("TMT", "m", "туркменские манаты", "манатах", "Turkmen manat", "türkmen manady"),
        Info("AZN", "₼", "азербайджанские манаты", "манатах", "Azerbaijani manat", "azerbaýjan manady"),
        Info("UZS", "soʻm", "узбекские сумы", "сумах", "Uzbek som", "özbek somy"),
        Info("KGS", "с", "киргизские сомы", "сомах", "Kyrgyz som", "gyrgyz somy"),
        Info("TJS", "SM", "таджикские сомони", "сомони", "Tajik somoni", "täjik somonisi"),
        Info("BYN", "Br", "белорусские рубли", "рублях", "Belarusian roubles", "belarus rubly"),
        Info("UAH", "₴", "украинские гривны", "гривнах", "Ukrainian hryvnia", "ukrain grywnasy"),
        Info("GEL", "₾", "грузинские лари", "лари", "Georgian lari", "gruzin larisi"),
        Info("AMD", "֏", "армянские драмы", "драмах", "Armenian dram", "ermeni dramy"),
        Info("MDL", "L", "молдавские леи", "леях", "Moldovan leu"),
        Info("TRY", "₺", "турецкие лиры", "лирах", "Turkish lira", "türk lirasy"),
        Info("GBP", "£", "фунты стерлингов", "фунтах", "pounds sterling", "iňlis funty"),
        Info("CHF", "Fr", "швейцарские франки", "франках", "Swiss francs"),
        Info("CNY", "¥", "китайские юани", "юанях", "Chinese yuan", "hytaý ýuany"),
        Info("JPY", "¥", "японские иены", "иенах", "Japanese yen", "ýapon ýenasy"),
        Info("KRW", "₩", "южнокорейские воны", "вонах", "South Korean won"),
        Info("INR", "₹", "индийские рупии", "рупиях", "Indian rupees", "hindi rupiýasy"),
        Info("AED", "AED", "дирхамы ОАЭ", "дирхамах", "UAE dirham", "BAE dirhemi"),
        Info("SAR", "SAR", "саудовские риялы", "риялах", "Saudi riyal"),
        Info("QAR", "QAR", "катарские риалы", "риалах", "Qatari riyal"),
        Info("ILS", "₪", "израильские шекели", "шекелях", "Israeli shekel"),
        Info("EGP", "EGP", "египетские фунты", "фунтах", "Egyptian pounds"),
        Info("THB", "฿", "таиландские баты", "батах", "Thai baht"),
        Info("VND", "₫", "вьетнамские донги", "донгах", "Vietnamese dong"),
        Info("IDR", "Rp", "индонезийские рупии", "рупиях", "Indonesian rupiah"),
        Info("MYR", "RM", "малайзийские ринггиты", "ринггитах", "Malaysian ringgit"),
        Info("SGD", "S$", "сингапурские доллары", "долларах", "Singapore dollars"),
        Info("HKD", "HK$", "гонконгские доллары", "долларах", "Hong Kong dollars"),
        Info("AUD", "A$", "австралийские доллары", "долларах", "Australian dollars"),
        Info("NZD", "NZ$", "новозеландские доллары", "долларах", "New Zealand dollars"),
        Info("CAD", "C$", "канадские доллары", "долларах", "Canadian dollars"),
        Info("MXN", "MX$", "мексиканские песо", "песо", "Mexican pesos"),
        Info("BRL", "R$", "бразильские реалы", "реалах", "Brazilian real"),
        Info("ARS", "AR$", "аргентинские песо", "песо", "Argentine pesos"),
        Info("CLP", "CL$", "чилийские песо", "песо", "Chilean pesos"),
        Info("COP", "CO$", "колумбийские песо", "песо", "Colombian pesos"),
        Info("PEN", "S/", "перуанские соли", "солях", "Peruvian sol"),
        Info("ZAR", "R", "южноафриканские рэнды", "рэндах", "South African rand"),
        Info("NGN", "₦", "нигерийские найры", "найрах", "Nigerian naira"),
        Info("KES", "KSh", "кенийские шиллинги", "шиллингах", "Kenyan shilling"),
        Info("MAD", "DH", "марокканские дирхамы", "дирхамах", "Moroccan dirham"),
        Info("TND", "DT", "тунисские динары", "динарах", "Tunisian dinar"),
        Info("PLN", "zł", "польские злотые", "злотых", "Polish zloty"),
        Info("CZK", "Kč", "чешские кроны", "кронах", "Czech koruna"),
        Info("HUF", "Ft", "венгерские форинты", "форинтах", "Hungarian forint"),
        Info("RON", "lei", "румынские леи", "леях", "Romanian leu"),
        Info("BGN", "лв", "болгарские левы", "левах", "Bulgarian lev"),
        Info("RSD", "дин", "сербские динары", "динарах", "Serbian dinar"),
        Info("SEK", "kr", "шведские кроны", "кронах", "Swedish krona"),
        Info("NOK", "kr", "норвежские кроны", "кронах", "Norwegian krone"),
        Info("DKK", "kr", "датские кроны", "кронах", "Danish krone"),
        Info("ISK", "kr", "исландские кроны", "кронах", "Icelandic krona"),
        Info("PKR", "₨", "пакистанские рупии", "рупиях", "Pakistani rupees", "pakistan rupiýasy"),
        Info("BDT", "৳", "бангладешские таки", "таках", "Bangladeshi taka"),
        Info("LKR", "Rs", "шри-ланкийские рупии", "рупиях", "Sri Lankan rupees"),
        Info("NPR", "Rs", "непальские рупии", "рупиях", "Nepalese rupees"),
        Info("MNT", "₮", "монгольские тугрики", "тугриках", "Mongolian tugrik"),
        Info("PHP", "₱", "филиппинские песо", "песо", "Philippine pesos"),
        Info("TWD", "NT$", "тайваньские доллары", "долларах", "Taiwan dollars"),
        Info("IRR", "IRR", "иранские риалы", "риалах", "Iranian rial", "eýran rialy"),
        Info("IQD", "IQD", "иракские динары", "динарах", "Iraqi dinar"),
        Info("JOD", "JD", "иорданские динары", "динарах", "Jordanian dinar"),
        Info("KWD", "KD", "кувейтские динары", "динарах", "Kuwaiti dinar"),
        Info("BHD", "BD", "бахрейнские динары", "динарах", "Bahraini dinar"),
        Info("OMR", "OMR", "оманские риалы", "риалах", "Omani rial"),
        Info("LBP", "LBP", "ливанские фунты", "фунтах", "Lebanese pounds"),
        Info("AFN", "AFN", "афганские афгани", "афгани", "Afghan afghani", "owgan afganisi"),
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
            val name = it.name.ifBlank { it.code }
            it.code to Info(it.code, it.sym.ifBlank { it.code }, name, it.inName.ifBlank { name }, name, name)
        }
    }

    /** Язык названий и разделителей в числах. */
    fun setLang(l: Lang) {
        if (l.code == lang.code) return
        lang = l
        buildFormats()
    }

    fun info(code: String): Info = custom[code] ?: byCode[code] ?: Info(code, code, code, code, code, code)
    fun sym(code: String) = info(code).sym
    fun inCatalog(code: String) = byCode.containsKey(code)
    fun defaultRate(code: String) = RATE_HINTS[code] ?: 1.0

    /** Поиск по каталогу для экрана добавления валюты. */
    fun search(query: String, exclude: Set<String>): List<Info> {
        val q = query.trim().lowercase()
        return CATALOG.filter { it.code !in exclude }
            .filter { q.isEmpty() || it.code.lowercase().startsWith(q) || it.name.lowercase().contains(q) || it.sym.lowercase() == q }
    }

    private var whole = DecimalFormat("#,##0")
    private var cents = DecimalFormat("#,##0.00")

    private fun buildFormats() {
        val symbols = DecimalFormatSymbols(Locale(lang.code)).apply {
            groupingSeparator = if (lang.code == "en") ',' else ' '
            decimalSeparator = if (lang.code == "en") '.' else ','
        }
        whole = DecimalFormat("#,##0", symbols)
        cents = DecimalFormat("#,##0.00", symbols)
    }

    init {
        buildFormats()
    }

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
        val suffix = if (lang.code == "en") "k" else "к"
        val big = if (lang.code == "en") "m" else "м"
        return when {
            a >= 1_000_000 -> (v / 1_000_000).roundToLong().toString() + big
            a >= 1000 -> (v / 1000).roundToLong().toString() + suffix
            else -> v.roundToLong().toString()
        }
    }
}
