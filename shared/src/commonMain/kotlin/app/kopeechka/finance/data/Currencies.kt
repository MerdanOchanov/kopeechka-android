package app.kopeechka.finance.data

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

    /**
     * Базы с фиксированной валютой больше нет: курсы задаются относительно основной
     * валюты пользователя (`settings.mainCur`), её собственный курс всегда 1.
     * Таблица RATE_HINTS ниже хранится в долларах и служит только источником
     * ориентировочных значений — см. [hintRate].
     */
    const val HINT_BASE = "USD"

    /** Версия формата: 3 — курсы и лимиты в основной валюте (1 — в рублях, 2 — в долларах). */
    const val DATA_VERSION = 3

    /** Валюты, включённые в новом профиле. */
    val DEFAULT_CODES = listOf("USD", "RUB", "EUR", "KZT", "TMT")

    /** Сколько долларов стоит единица валюты. */
    val DEFAULT_RATES = mapOf("USD" to 1.0, "RUB" to 0.0109, "EUR" to 1.087, "KZT" to 0.00196, "TMT" to 0.2857)

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
     * Ориентировочный курс в долларах за единицу — подставляется при добавлении валюты.
     * Курсы в приложении задаются вручную, это лишь стартовое значение.
     */
    val RATE_HINTS: Map<String, Double> = mapOf(
        "RUB" to 0.0109, "USD" to 1.0, "EUR" to 1.087, "KZT" to 0.002, "TMT" to 0.2859,
        "AZN" to 0.587, "UZS" to 0.000079, "KGS" to 0.0114, "TJS" to 0.0913, "BYN" to 0.3043,
        "UAH" to 0.0239, "GEL" to 0.3696, "AMD" to 0.0026, "MDL" to 0.0565, "TRY" to 0.0293,
        "GBP" to 1.272, "CHF" to 1.13, "CNY" to 0.138, "JPY" to 0.0065, "KRW" to 0.000739,
        "INR" to 0.012, "AED" to 0.2717, "SAR" to 0.2663, "QAR" to 0.2717, "ILS" to 0.2717,
        "EGP" to 0.0207, "THB" to 0.0283, "VND" to 0.00004, "IDR" to 0.000062, "MYR" to 0.2174,
        "SGD" to 0.7391, "HKD" to 0.1283, "AUD" to 0.6522, "NZD" to 0.5978, "CAD" to 0.7283,
        "MXN" to 0.05, "BRL" to 0.1848, "ARS" to 0.0011, "CLP" to 0.0011, "COP" to 0.00025,
        "PEN" to 0.2717, "ZAR" to 0.0543, "NGN" to 0.000652, "KES" to 0.0076, "MAD" to 0.1,
        "TND" to 0.3261, "PLN" to 0.25, "CZK" to 0.0435, "HUF" to 0.0027, "RON" to 0.2174,
        "BGN" to 0.5543, "RSD" to 0.0092, "SEK" to 0.0946, "NOK" to 0.0935, "DKK" to 0.1457,
        "ISK" to 0.0073, "PKR" to 0.0036, "BDT" to 0.0085, "LKR" to 0.0034, "NPR" to 0.0075,
        "MNT" to 0.000293, "PHP" to 0.0174, "TWD" to 0.0315, "IRR" to 0.000022, "IQD" to 0.000761,
        "JOD" to 1.413, "KWD" to 3.261, "BHD" to 2.652, "OMR" to 2.598, "LBP" to 0.000011,
        "AFN" to 0.0141,
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

    /** Ориентировочный курс `code` в единицах валюты `main`. */
    fun hintRate(code: String, main: String): Double {
        val c = RATE_HINTS[code] ?: return 1.0
        val m = RATE_HINTS[main]?.takeIf { it > 0 } ?: return c
        return c / m
    }

    fun info(code: String): Info = custom[code] ?: byCode[code] ?: Info(code, code, code, code, code, code)
    fun sym(code: String) = info(code).sym
    fun inCatalog(code: String) = byCode.containsKey(code)
    fun defaultRate(code: String, main: String) = hintRate(code, main)

    /** Поиск по каталогу для экрана добавления валюты. */
    fun search(query: String, exclude: Set<String>): List<Info> {
        val q = query.trim().lowercase()
        return CATALOG.filter { it.code !in exclude }
            .filter { q.isEmpty() || it.code.lowercase().startsWith(q) || it.name.lowercase().contains(q) || it.sym.lowercase() == q }
    }

    /** Разделители зависят от языка: 9 024,50 против 9,024.50 */
    private var groupSep = ' '
    private var decimalSep = ','

    private fun buildFormats() {
        groupSep = if (lang.code == "en") ',' else ' '
        decimalSep = if (lang.code == "en") '.' else ','
    }

    init {
        buildFormats()
    }

    /** 112 480 ₽ — без знака, модуль берёт вызывающий. */
    fun fmt(v: Double, cur: String, kopecks: Boolean = false): String {
        val n = if (kopecks) groupDecimal(v, groupSep, decimalSep) else groupNumber(v.roundToLong(), groupSep)
        return n + " " + sym(cur)
    }

    fun fmtNumber(v: Double): String = groupNumber(v.roundToLong(), groupSep)

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
