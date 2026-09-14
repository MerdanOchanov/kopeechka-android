package app.kopeechka.finance.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CAT_TRANSFER = "transfer"
const val CAT_GOAL = "goal"

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: String = "Карта",
    val mask: String = "",
    val cur: String = "RUB",
    /** Стартовый остаток в валюте счёта. Текущий баланс = initial + сумма операций. */
    val initial: Double = 0.0,
    val inTotal: Boolean = true,
)

@Serializable
data class Category(
    val id: String,
    val code: String,
    val name: String,
    /**
     * Лимит на месяц в базовой валюте курсов (доллар). 0 — без лимита.
     * Имя поля в JSON осталось прежним, чтобы открывались старые копии:
     * при загрузке версии 1 значение пересчитывается из рублей в доллары.
     */
    @SerialName("limitRub")
    val limitBase: Double = 0.0,
    val income: Boolean = false,
    /** Цвет из палитры, например "#597EA3". Пусто — цвет подберётся автоматически. */
    val color: String = "",
)

/** Валюта, добавленная пользователем вручную (её нет в каталоге). */
@Serializable
data class CurrencyDef(
    val code: String,
    val sym: String,
    val name: String,
    val inName: String = "",
)

@Serializable
data class Tx(
    val id: Long,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val title: String,
    /** id категории либо CAT_TRANSFER / CAT_GOAL */
    val cat: String,
    val acc: String,
    /** В валюте счёта acc; отрицательная — списание. */
    val amount: Double,
    val note: String = "",
    val toAcc: String? = null,
    /** Сколько зачислено на toAcc, в его валюте. */
    val toAmount: Double? = null,
    val goal: String? = null,
)

@Serializable
data class Goal(
    val id: String,
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val cur: String = "RUB",
    val hint: String = "",
)

@Serializable
data class Settings(
    val mainCur: String = "RUB",
    val dark: Boolean = false,
    /** "auto" — как в системе, либо "ru" / "en" / "tk". */
    val lang: String = "auto",
    /** Сколько рублей стоит единица валюты. */
    val rates: Map<String, Double> = Currencies.DEFAULT_RATES,
    /** Валюты, включённые в приложении (первая всегда RUB — база курсов). */
    val currencyCodes: List<String> = Currencies.DEFAULT_CODES,
    /** Валюты, заведённые вручную сверх каталога. */
    val customCurrencies: List<CurrencyDef> = emptyList(),
    val onboarded: Boolean = false,
    val userName: String = "",
    val remind: Boolean = false,
    val remindHour: Int = 21,
    val showKopecks: Boolean = false,
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0,
    val driveLinked: Boolean = false,
    val aiProvider: String = "claude",
    val aiModels: Map<String, String> = emptyMap(),
    val customEndpoint: String = "",
    val aiSets: Set<String> = setOf("ops", "budgets", "accounts"),
)

@Serializable
data class AppData(
    val version: Int = 1, // 1 — курсы в рублях, 2 — в долларах (см. Store.migrate)
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txs: List<Tx> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val settings: Settings = Settings(),
    val nextId: Long = 1000,
)
