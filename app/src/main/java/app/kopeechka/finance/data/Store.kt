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
class Store(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "kopeechka.json"))
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    val current: AppData get() = _data.value

    fun update(f: (AppData) -> AppData) {
        synchronized(lock) {
            val next = f(_data.value)
            Currencies.registerCustom(next.settings.customCurrencies)
            _data.value = next
            persist(next)
        }
    }

    fun replace(d: AppData) = update { d }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), current)

    /** Бросает исключение, если JSON не похож на копию «Копеечки». */
    fun parseBackup(text: String): AppData {
        val d = json.decodeFromString(AppData.serializer(), text)
        require(d.accounts.isNotEmpty() || d.txs.isNotEmpty() || d.categories.isNotEmpty()) { "Пустая копия" }
        return d
    }

    private fun load(): AppData {
        val f = file.baseFile
        if (f.exists()) {
            runCatching {
                val d = json.decodeFromString(AppData.serializer(), String(file.readFully(), Charsets.UTF_8))
                Currencies.registerCustom(d.settings.customCurrencies)
                return d
            }
        }
        return Demo.create().also { persist(it) }
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
