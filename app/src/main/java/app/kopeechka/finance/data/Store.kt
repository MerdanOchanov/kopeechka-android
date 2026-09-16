package app.kopeechka.finance.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * Все данные приложения живут в одном JSON-файле во внутреннем хранилище.
 * Запись идёт атомарно (AtomicFile) в фоновом потоке, в памяти — StateFlow.
 * Этот же JSON уходит в резервную копию на Google Диск.
 */
class Store(context: Context) : app.kopeechka.finance.Storage {
    private val file = AtomicFile(File(context.filesDir, "kopeechka.json"))
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private val _data = MutableStateFlow(load())
    override val data: StateFlow<AppData> = _data

    override val current: AppData get() = _data.value

    override fun update(f: (AppData) -> AppData) {
        synchronized(lock) {
            val before = _data.value
            // отметки времени и надгробия ставятся здесь, а не в каждом обработчике:
            // так их нельзя забыть, а удаления запоминаются сами (см. Sync)
            val next = Sync.stamp(before, f(before), System.currentTimeMillis())
            Currencies.setLang(Lang.of(next.settings.lang))
            Currencies.registerCustom(next.settings.customCurrencies)
            _data.value = next
            persist(next)
        }
    }

    override fun replace(d: AppData) = update { d }

    override fun applyMerged(d: AppData) {
        synchronized(lock) {
            Currencies.setLang(Lang.of(d.settings.lang))
            Currencies.registerCustom(d.settings.customCurrencies)
            _data.value = d
            persist(d)
        }
    }

    override fun exportJson(): String = json.encodeToString(AppData.serializer(), current.forExport())

    /** Бросает исключение, если JSON не похож на копию «Копеечки». */
    override fun parseBackup(text: String): AppData {
        val d = json.decodeFromString(AppData.serializer(), text)
        require(d.accounts.isNotEmpty() || d.txs.isNotEmpty() || d.categories.isNotEmpty()) { "Пустая копия" }
        return migrate(d)
    }

    /**
     * Версия 1 хранила курсы и лимиты в рублях, версия 2 — в долларах.
     * Пересчитываем по курсу доллара, который был записан в самих данных.
     */
    private fun migrate(d: AppData): AppData {
        if (d.version >= Currencies.DATA_VERSION) return d
        // В версиях 1 и 2 курсы и лимиты были в рубле и долларе соответственно.
        // Теперь база — основная валюта пользователя: делим на её прежний курс.
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
        val f = file.baseFile
        if (f.exists()) {
            runCatching {
                val raw = json.decodeFromString(AppData.serializer(), String(file.readFully(), Charsets.UTF_8))
                val d = migrate(raw)
                Currencies.setLang(Lang.of(d.settings.lang))
                Currencies.registerCustom(d.settings.customCurrencies)
                // после пересчёта сразу сохраняем, чтобы на диске лежал новый формат
                if (d.version != raw.version) persist(d)
                return d
            }
        }
        return Demo.create(Lang.fromSystem()).also { persist(it) }
    }

    private fun persist(d: AppData) {
        val bytes = json.encodeToString(AppData.serializer(), d).toByteArray(Charsets.UTF_8)
        writer.execute {
            synchronized(file) {
                val out = file.startWrite()
                try {
                    out.write(bytes)
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                }
            }
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
