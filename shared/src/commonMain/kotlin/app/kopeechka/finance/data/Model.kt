package app.kopeechka.finance.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CAT_TRANSFER = "transfer"
const val CAT_GOAL = "goal"

/** Каким курсом переводить валюты в основную. */
object RateMode {
    const val BANK = "bank"
    const val MARKET = "market"
}

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
    /** Общий счёт: расходы с него от порога спрашивают согласия второго участника. */
    val shared: Boolean = false,
    /** С какой суммы спрашивать, в валюте счёта. 0 — всегда. */
    val approveFrom: Double = 0.0,
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
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
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
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
    /** Кто записал — id участника общего пространства. Пусто, если человек один. */
    val by: String = "",
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

@Serializable
data class Goal(
    val id: String,
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val cur: String = "RUB",
    val hint: String = "",
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

@Serializable
data class Settings(
    val mainCur: String = "RUB",
    val dark: Boolean = false,
    /** "auto" — как в системе, либо "ru" / "en" / "tk". */
    val lang: String = "auto",
    /** Сколько основной валюты стоит единица валюты — банковский (официальный) курс. */
    val rates: Map<String, Double> = Currencies.DEFAULT_RATES,
    /**
     * Рыночный курс там, где он расходится с банковским. В Туркменистане доллар
     * по официальному курсу стоит в разы дешевле, чем на руках, и один курс
     * на валюту делает итоги бессмысленными. Нет записи — рынок совпадает с банком.
     */
    val marketRates: Map<String, Double> = emptyMap(),
    /** По какому курсу считать итоги: RateMode.BANK или RateMode.MARKET. Личная настройка. */
    val rateMode: String = RateMode.BANK,
    /** Когда и откуда последний раз брали официальные курсы: для строки «обновлено». */
    val ratesAt: Long = 0,
    val ratesSource: String = "",
    /** Валюты, включённые в приложении (первая всегда RUB — база курсов). */
    val currencyCodes: List<String> = Currencies.DEFAULT_CODES,
    /** Валюты, заведённые вручную сверх каталога. */
    val customCurrencies: List<CurrencyDef> = emptyList(),
    val onboarded: Boolean = false,
    val userName: String = "",
    val remind: Boolean = false,
    val remindHour: Int = 21,
    val showKopecks: Boolean = false,
    /** Разрешить уводить счета в минус: без галочки приложение не даст потратить больше остатка. */
    val allowNegative: Boolean = false,
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0,
    val driveLinked: Boolean = false,
    val aiProvider: String = "claude",
    val aiModels: Map<String, String> = emptyMap(),
    val customEndpoint: String = "",
    /** Каталог Yandex Cloud для YandexGPT: без него модель не найти. */
    val yandexFolder: String = "",
    val aiSets: Set<String> = setOf("ops", "budgets", "accounts"),
    /** Модуль «Дело»: прайс, клиенты, заказы. Выключен — приложение выглядит как прежде. */
    val business: Boolean = false,
    /** Чтение банковских СМС. Только Android: iOS доступа к сообщениям не даёт. */
    val sms: Boolean = false,
    /** Чтение уведомлений банковских приложений. Только Android. */
    val bankPush: Boolean = false,
    /** Экран разрешений первого запуска уже показан. */
    val permsAsked: Boolean = false,
    /**
     * Когда последний раз меняли денежную модель — валюту, курсы, список валют.
     * Единственная часть настроек, общая для участников: см. Sync.
     */
    val moneyAt: Long = 0,
)

@Serializable
data class AppData(
    val version: Int = 1, // 1 — курсы в рублях, 2 — в долларах (см. Store.migrate)
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txs: List<Tx> = emptyList(),
    val goals: List<Goal> = emptyList(),
    /** Долги: кому должен я и кто должен мне. */
    val debts: List<Debt> = emptyList(),
    /** Прайс, клиенты и заказы модуля «Дело» — пустые, пока он выключен. */
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val orders: List<Order> = emptyList(),
    /** Заявки на расход с общих счетов. */
    val requests: List<SpendRequest> = emptyList(),
    /** Регулярные платежи и рассрочки. */
    val recurring: List<Recurring> = emptyList(),
    /** Общее пространство с другим человеком; null — веду один. */
    val space: SyncSpace? = null,
    /**
     * Что удалено и когда: «tx:1042» → миллисекунды. Без этого слияние вернуло бы
     * записи, которые второй участник уже стёр. Чистится через полгода.
     */
    val deleted: Map<String, Long> = emptyMap(),
    /** Правила чтения банковских СМС и уведомлений: от кого приходят и к какому счёту относятся. */
    val smsSources: List<SmsSource> = emptyList(),
    /**
     * Приложения, присылавшие уведомления с суммой: пакет → название. Чтобы
     * в правиле банк выбирался из списка. Живёт только на этом телефоне.
     */
    val pushApps: Map<String, String> = emptyMap(),
    /**
     * Магазин → категория: приложение запоминает выбор человека и в следующий раз
     * подставляет ту же категорию само.
     */
    val merchantCats: Map<String, String> = emptyMap(),
    /**
     * Черновики из чеков и СМС — ждут подтверждения человеком.
     * Не уходят ни в копию, ни в синхронизацию: см. [forExport].
     */
    val inbox: List<InboxItem> = emptyList(),
    val settings: Settings = Settings(),
    val nextId: Long = 1000,
)

/**
 * Состояние, которое не стыдно отдать наружу — в резервную копию или другому
 * человеку при синхронизации. Черновики остаются на телефоне: в них лежит
 * текст банковских СМС, которому в общей папке не место.
 */
fun AppData.forExport(): AppData = copy(inbox = emptyList(), pushApps = emptyMap())

/**
 * Состояние для общего пространства. Кроме черновиков убираем то, что относится
 * к одному телефону: правила чтения СМС и память о магазинах.
 */
fun AppData.forSync(): AppData = copy(
    inbox = emptyList(),
    smsSources = emptyList(),
    pushApps = emptyMap(),
    merchantCats = emptyMap(),
    space = null,
)
