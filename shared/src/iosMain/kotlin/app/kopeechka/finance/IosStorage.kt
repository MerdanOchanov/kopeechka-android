package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Demo
import app.kopeechka.finance.data.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Файл данных на iOS: тот же JSON, что и на Android, в папке Documents.
 * Запись идёт целиком и сразу — файл маленький, а так проще ничего не потерять.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosStorage : Storage {

    private val path: String by lazy {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = dirs.firstOrNull() as? String ?: NSFileManager.defaultManager.currentDirectoryPath
        "$dir/kopeechka.json"
    }

    private val _data = MutableStateFlow(load())
    override val data: StateFlow<AppData> = _data
    override val current: AppData get() = _data.value

    override fun update(f: (AppData) -> AppData) {
        val next = f(_data.value)
        Currencies.setLang(Lang.of(next.settings.lang))
        Currencies.registerCustom(next.settings.customCurrencies)
        _data.value = next
        persist(next)
    }

    override fun replace(d: AppData) = update { d }

    override fun exportJson(): String = json.encodeToString(AppData.serializer(), current)

    override fun parseBackup(text: String): AppData {
        val d = json.decodeFromString(AppData.serializer(), text)
        require(d.accounts.isNotEmpty() || d.txs.isNotEmpty() || d.categories.isNotEmpty()) { "Пустая копия" }
        return migrate(d)
    }

    /** Старые форматы: курсы и лимиты приводятся к основной валюте — как на Android. */
    private fun migrate(d: AppData): AppData {
        if (d.version >= Currencies.DATA_VERSION) return d
        val main = d.settings.mainCur
        val div = d.settings.rates[main]?.takeIf { it > 0 } ?: 1.0
        val rates = d.settings.rates.mapValues { (_, v) -> v / div } + (main to 1.0)
        return d.copy(
            version = Currencies.DATA_VERSION,
            categories = d.categories.map { it.copy(limitBase = it.limitBase / div) },
            settings = d.settings.copy(
                rates = rates,
                currencyCodes = (listOf(main) + d.settings.currencyCodes).distinct(),
            ),
        )
    }

    private fun load(): AppData {
        val text = readText(path)
        if (text != null) {
            runCatching {
                val raw = json.decodeFromString(AppData.serializer(), text)
                val d = migrate(raw)
                Currencies.setLang(Lang.of(d.settings.lang))
                Currencies.registerCustom(d.settings.customCurrencies)
                if (d.version != raw.version) persist(d)
                return d
            }
        }
        return Demo.create(Lang.fromSystem()).also { persist(it) }
    }

    private fun persist(d: AppData) {
        writeText(path, json.encodeToString(AppData.serializer(), d))
    }

    /** Копия состояния перед восстановлением — рядом с данными. */
    fun saveBeforeRestore(jsonText: String) {
        writeText(path.removeSuffix("kopeechka.json") + "before-restore.json", jsonText)
    }

    private fun readText(file: String): String? =
        NSString.stringWithContentsOfFile(file, NSUTF8StringEncoding, null) as String?

    private fun writeText(file: String, text: String) {
        NSString.create(string = text)
            .writeToFile(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
