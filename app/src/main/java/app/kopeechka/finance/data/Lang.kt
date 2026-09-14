package app.kopeechka.finance.data

import java.util.Locale
import kotlin.math.abs

/**
 * Языки приложения: русский, английский, туркменский.
 *
 * Тексты лежат в карте «ключ → строка». Подстановки — `{0}`, `{1}`;
 * формы множественного числа разделены `|` и живут под ключами `pl.*`;
 * списки (месяцы, дни недели) — тоже через `|`.
 */
class Lang(val code: String, private val map: Map<String, String>, private val fallback: Lang? = null) {

    fun raw(key: String): String? = map[key] ?: fallback?.raw(key)

    fun t(key: String): String = raw(key) ?: "⟨$key⟩"

    fun t(key: String, vararg args: Any?): String {
        var s = t(key)
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
        return s
    }

    fun list(key: String): List<String> = t(key).split("|")

    /** Форма существительного при числе: `pl(3, "day")` → «дня». */
    fun pl(n: Int, key: String): String {
        val forms = list("pl.$key")
        val i = when (code) {
            "ru" -> {
                val a = abs(n) % 100
                val b = a % 10
                when {
                    a in 11..14 -> 2
                    b == 1 -> 0
                    b in 2..4 -> 1
                    else -> 2
                }
            }
            "en" -> if (n == 1) 0 else 1
            else -> 0 // в туркменском существительное после числа не меняется
        }
        return forms.getOrElse(i) { forms.last() }
    }

    /** «3 дня» / «3 days» / «3 gün» */
    fun n(n: Int, key: String) = "$n ${pl(n, key)}"

    val months: List<String> get() = list("date.months")
    val monthsGen: List<String> get() = list("date.monthsGen")
    val monthsShort: List<String> get() = list("date.monthsShort")
    val week: List<String> get() = list("date.week")
    val weekFull: List<String> get() = list("date.weekFull")

    companion object {
        val RU = Lang("ru", RU_STRINGS)
        val EN = Lang("en", EN_STRINGS, RU)
        val TK = Lang("tk", TK_STRINGS, EN)
        val ALL = listOf(RU, EN, TK)

        /** Коды для переключателя в настройках: «как в системе» плюс три языка. */
        val CODES = listOf("auto", "ru", "en", "tk")

        fun of(code: String): Lang = when (code) {
            "ru" -> RU
            "en" -> EN
            "tk" -> TK
            else -> fromSystem()
        }

        fun fromSystem(): Lang = when (Locale.getDefault().language) {
            "en" -> EN
            "tk" -> TK
            else -> RU
        }

        /** Название языка на нём самом — для переключателя. */
        fun title(code: String): String = when (code) {
            "ru" -> "Русский"
            "en" -> "English"
            "tk" -> "Türkmençe"
            else -> RU.t("set.lang.auto")
        }
    }
}

internal val RU_STRINGS: Map<String, String> = mapOf(
    // ——— общее ———
    "app.name" to "Копеечка",
    "common.cancel" to "Отмена",
    "common.cancelUpper" to "ОТМЕНА",
    "common.save" to "Сохранить",
    "common.delete" to "Удалить",
    "common.close" to "Закрыть",
    "common.all" to "Все",
    "common.add" to "Добавить",
    "common.back" to "Назад",
    "common.dash" to "—",
    "common.wait" to "Подождите…",
    "common.refresh" to "Обновить",
    "common.restore" to "Восстановить",
    "common.from" to "из {0}",
    "common.ready" to "готов",

    // ——— навигация ———
    "nav.home" to "Обзор",
    "nav.ops" to "Операции",
    "nav.budget" to "Бюджет",
    "nav.reports" to "Отчёты",
    "nav.settings" to "Настройки",
    "nav.themeLight" to "Светлая тема",
    "nav.themeDark" to "Тёмная тема",

    // ——— заставка ———
    "onb.1.title" to "Все деньги в одном месте",
    "onb.1.body" to "Карты, наличные и накопления в разных валютах считаются вместе. Баланс всегда под рукой.",
    "onb.2.title" to "Расход — за пять секунд",
    "onb.2.body" to "Сумма, категория, счёт. Кнопка «плюс» всегда в центре экрана.",
    "onb.3.title" to "Бюджет подскажет, где притормозить",
    "onb.3.body" to "Лимиты по категориям, отчёты в четырёх разрезах, цели и ИИ-советник по вашим данным.",
    "onb.next" to "Дальше",
    "onb.start" to "Начать с примерами",
    "onb.skip" to "Пропустить",
    "onb.clean" to "Начать с чистого листа",

    // ——— главный экран ———
    "home.total" to "Всего на счетах · {0}",
    "home.income" to "Доходы",
    "home.expense" to "Расходы",
    "home.free" to "Свободно",
    "home.saldo" to "Сальдо",
    "home.accounts" to "Счета",
    "home.transfer" to "Перевод",
    "home.budget" to "Бюджет месяца",
    "home.daysLeft" to "осталось {0}",
    "home.noLimits" to "Лимиты не заданы — настройте их в разделе «Бюджет»",
    "home.reports" to "Отчёты",
    "home.goals" to "Цели",
    "home.recent" to "Последние операции",
    "home.empty" to "Пока пусто. Нажмите «+», чтобы записать первую операцию.",
    "home.cats.title" to "Куда уходят деньги",
    "home.cats.hint" to "за месяц",
    "home.cats.empty" to "В этом месяце расходов пока нет",
    "home.days.title" to "Расходы по дням",
    "home.days.hint" to "эта неделя",
    "home.days.today" to "Сегодня",
    "home.days.below" to "ниже среднего дня на {0}",
    "home.days.above" to "выше среднего дня на {0}",
    "home.months.title" to "Динамика по месяцам",
    "home.months.hint" to "6 месяцев",
    "home.io.title" to "Доходы и расходы",
    "home.io.hint" to "за месяц",

    // ——— операции ———
    "ops.all" to "Все",
    "ops.out" to "Расходы",
    "ops.in" to "Доходы",
    "ops.transfers" to "Переводы",
    "ops.empty" to "Операций нет",
    "ops.transferMeta" to "Перевод · {0} → {1}",

    // ——— бюджет ———
    "budget.limit" to "Лимит на месяц",
    "budget.over" to "Лимит месяца превышен на {0}",
    "budget.perDay" to "Можно тратить {0} в день до конца месяца",
    "budget.rest" to "остаток {0}",
    "budget.overCat" to "перерасход {0}",
    "budget.noLimit" to "Без лимита: {0}",
    "budget.cats" to "Категории и лимиты",

    // ——— отчёты ———
    "period.week" to "Неделя",
    "period.month" to "Месяц",
    "period.quarter" to "Квартал",
    "period.year" to "Год",
    "period.note.week" to "за неделю",
    "period.note.month" to "за месяц",
    "period.note.quarter" to "за квартал",
    "period.note.year" to "за год",
    "cut.cats" to "Категории",
    "cut.accs" to "Счета",
    "cut.days" to "Дни недели",
    "cut.io" to "Доходы / расходы",
    "cut.title.cats" to "Разрез по категориям",
    "cut.title.accs" to "Разрез по счетам",
    "cut.title.days" to "Разрез по дням недели",
    "cut.title.io" to "Доходы против расходов",
    "report.prev" to "Предыдущий период",
    "report.next" to "Следующий период",
    "report.current" to "текущий период",
    "report.backN" to "{0} назад",
    "report.returnCurrent" to "Вернуться к текущему периоду",
    "report.compare" to "К прошлому периоду",
    "report.was" to "было",
    "report.noCompare" to "Сравнивать не с чем: за прошлый период расходов нет",
    "report.expenses" to "Расходы",
    "report.incomes" to "Доходы",
    "report.saldo" to "Сальдо",
    "report.empty" to "За этот период расходов нет",
    "report.dynamics" to "Динамика",
    "report.askAi" to "Спросить ИИ про этот отчёт",
    "report.fact.avg" to "Средний расход в день",
    "report.fact.topCat" to "Самая дорогая категория",
    "report.fact.biggest" to "Крупнейшая трата",
    "report.fact.count" to "Операций за период",

    // ——— настройки ———
    "set.sections" to "Разделы",
    "set.accounts" to "Счета и карты",
    "set.categories" to "Категории и лимиты",
    "set.categoriesSub" to "{0} · цвета и лимиты",
    "set.goals" to "Цели и накопления",
    "set.goalsEmpty" to "Пока нет целей",
    "set.currencies" to "Валюты и курсы",
    "set.currenciesSub" to "{0} · основная {1}",
    "set.backup" to "Резервная копия",
    "set.advisor" to "ИИ-советник",
    "set.advisorNoKey" to "без ключа — офлайн-разбор",
    "set.mainCur" to "Основная валюта",
    "set.mainCurNote" to "В ней считаются баланс, бюджеты и отчёты, и относительно неё задаются курсы остальных валют. При смене курсы пересчитываются автоматически.",
    "set.addCur" to "Добавить валюту",
    "set.accCur" to "Валюта счетов",
    "set.inMainCur" to "в основной валюте",
    "set.look" to "Оформление",
    "set.light" to "Светлая",
    "set.dark" to "Тёмная",
    "set.kopecks" to "Показывать копейки",
    "set.kopecksSub" to "В суммах, списках и отчётах",
    "set.lang" to "Язык",
    "set.lang.auto" to "Как в системе",
    "set.langNote" to "Меняет язык интерфейса, отчётов и демо-данных.",
    "set.reminders" to "Напоминания",
    "set.remind" to "Напоминать записать расходы",
    "set.remindSub" to "Каждый вечер в {0}:00",
    "set.profile" to "Профиль",
    "set.name" to "Как к вам обращаться",
    "set.nameHint" to "Например, Алина",
    "set.data" to "Данные",
    "set.loadDemo" to "Загрузить демо-данные",
    "set.clearAll" to "Очистить все данные",
    "set.about" to "Копеечка 1.0 · данные хранятся только на этом телефоне. В интернет уходят лишь резервные копии (в ваш Google Диск) и вопросы ИИ-советнику — когда вы сами их отправляете.",

    // ——— счета ———
    "acc.title" to "Счета и карты",
    "acc.sum" to "Сумма по счетам в общем балансе",
    "acc.inTotal" to "в общем балансе",
    "acc.notInTotal" to "не учитывается",
    "acc.add" to "+ Добавить счёт",
    "acc.note" to "Нажмите на счёт, чтобы изменить название, баланс или валюту. «В общем балансе» — учитывать ли счёт в сумме на главном экране.",
    "acc.new" to "Новый счёт",
    "acc.one" to "Счёт",
    "acc.name" to "Название",
    "acc.nameHint" to "Например, Карта · 4417",
    "acc.type" to "Тип",
    "acc.mask" to "Пометка",
    "acc.maskHint" to "•• 4417 или «вклад»",
    "acc.cur" to "Валюта",
    "acc.changeCur" to "Сменить валюту",
    "acc.balance" to "Текущий баланс, {0}",
    "acc.balanceNote" to "Разница уйдёт в стартовый остаток — история операций не меняется.",
    "acc.inTotalToggle" to "Учитывать в общем балансе",
    "acc.delete" to "Удалить счёт",
    "acc.type.card" to "Карта",
    "acc.type.cash" to "Кошелёк",
    "acc.type.savings" to "Накопления",
    "acc.type.deposit" to "Вклад",
    "acc.type.other" to "Другое",
    "acc.short" to "СЧ",

    // ——— категории ———
    "cat.title" to "Категории и лимиты",
    "cat.note" to "Нажмите на категорию, чтобы задать лимит и выбрать цвет — он используется в бюджете, отчётах и списках.",
    "cat.expenses" to "Расходы",
    "cat.incomes" to "Доходы",
    "cat.thisMonth" to "за этот месяц",
    "cat.limitMonth" to "лимит {0} в месяц",
    "cat.noLimit" to "без лимита",
    "cat.addExpense" to "+ Категория расходов",
    "cat.addIncome" to "+ Категория доходов",
    "cat.one" to "Категория",
    "cat.code.transfer" to "ПВ",
    "cat.code.goal" to "ЦЛ",
    "cat.newExpense" to "Категория расходов",
    "cat.newIncome" to "Категория дохода",
    "cat.name" to "Название",
    "cat.nameHintExpense" to "Например, Питомцы",
    "cat.nameHintIncome" to "Например, Кэшбэк",
    "cat.code" to "Код (две буквы)",
    "cat.codeNote" to "Показывается в квадратике рядом с операцией",
    "cat.codeHint" to "ПТ",
    "cat.limit" to "Лимит в месяц, {0}",
    "cat.limitHint" to "0 — без лимита",
    "cat.color" to "Цвет категории",
    "cat.colorNote" to "Выбранный цвет: {0} · виден в бюджете, отчётах и списке операций",
    "cat.colorOwn" to "свой",
    "cat.delete" to "Удалить категорию",

    // ——— цели ———
    "goal.title" to "Цели и накопления",
    "goal.empty" to "Целей пока нет. Добавьте первую — например, «Отпуск» или «Резервный фонд».",
    "goal.target" to "цель {0}",
    "goal.done" to "Цель достигнута",
    "goal.left" to "осталось {0}",
    "goal.put" to "Отложить",
    "goal.add" to "+ Новая цель",
    "goal.note" to "«Отложить» списывает сумму с выбранного счёта и добавляет её к цели. Нажмите на название цели, чтобы изменить её.",
    "goal.new" to "Новая цель",
    "goal.one" to "Цель",
    "goal.name" to "Название",
    "goal.nameHint" to "Например, Отпуск в Грузии",
    "goal.amount" to "Сколько нужно, {0}",
    "goal.cur" to "Валюта цели",
    "goal.hint" to "Заметка",
    "goal.hintHint" to "Например, хочу поехать в мае",
    "goal.delete" to "Удалить цель",
    "goal.sheetTitle" to "Отложить на цель",
    "goal.sheetSub" to "{0} · {1} из {2}",
    "goal.sum" to "Сумма",
    "goal.fromAcc" to "Со счёта",
    "goal.putSum" to "Отложить {0}",

    // ——— валюты ———
    "cur.title" to "Валюты и курсы",
    "cur.note" to "Курс — сколько единиц основной валюты ({0}) стоит одна единица другой. Основная валюта и есть база: её курс всегда 1. Курсы задаются вручную, приложение никуда за ними не ходит.",
    "cur.base" to "база",
    "cur.main" to "основная",
    "cur.own" to "своя",
    "cur.usedIn" to "используется в {0}",
    "cur.remove" to "Убрать валюту",
    "cur.add" to "+ Добавить валюту",
    "cur.footer" to "Курсы считаются относительно основной валюты, поэтому её курс всегда 1. Туркменский манат (TMT) уже в списке. Любую другую мировую валюту можно выбрать из каталога, а если её там нет — завести свою с собственным кодом и символом.",
    "cur.addTitle" to "Добавить валюту",
    "cur.search" to "Поиск: код или название (например, TMT или манат)",
    "cur.rateNote" to "Курс подставится ориентировочный — проверьте и поправьте его в списке валют.",
    "cur.approxRate" to "{0} · ориентировочно {1} {2}",
    "cur.notFound" to "В каталоге ничего не нашлось — заведите свою валюту ниже",
    "cur.ownTitle" to "Своя валюта",
    "cur.ownStart" to "Завести валюту вручную",
    "cur.code" to "Код",
    "cur.codeHint" to "Например, TMT",
    "cur.sym" to "Символ",
    "cur.symHint" to "m, ₼, $…",
    "cur.name" to "Название",
    "cur.rate" to "Курс: сколько {0} за единицу",
    "cur.sheetMain" to "Основная валюта приложения",
    "cur.sheetAcc" to "Валюта счёта «{0}»",
    "cur.sheetAccNote" to "Баланс и операции счёта пересчитаются по курсу из настроек.",
    "cur.sheetMainNote" to "Отчёты и бюджеты пересчитаются в выбранную валюту.",

    // ——— резервная копия ———
    "backup.title" to "Резервная копия",
    "backup.drive" to "Google Диск",
    "backup.linked" to "Подключён",
    "backup.notLinked" to "Не подключён",
    "backup.driveNotLinked" to "Google Диск не подключён",
    "backup.driveLinked" to "Google Диск подключён",
    "backup.last" to "Последняя копия: {0}",
    "backup.never" to "Копий ещё не было",
    "backup.note" to "Копия — один JSON-файл со всеми счетами, операциями, категориями, целями и настройками. API-ключи ИИ в копию не попадают. Файлы лежат в папке «Копеечка — резервные копии» на вашем Google Диске, хранятся 10 последних.",
    "backup.now" to "Сохранить копию сейчас",
    "backup.connect" to "Подключить Диск и сохранить",
    "backup.auto" to "Автоматически раз в день",
    "backup.autoOn" to "В фоне, по Wi-Fi",
    "backup.autoOff" to "Сработает после подключения Диска",
    "backup.list" to "Копии на Диске",
    "backup.listEmpty" to "Копий пока нет или список ещё загружается",
    "backup.listNotLinked" to "Подключите Диск, чтобы увидеть копии",
    "backup.unlink" to "Отключить Диск в приложении",
    "backup.folder" to "Копеечка — резервные копии",

    // ——— новая операция ———
    "add.new" to "Новая операция",
    "add.one" to "Операция",
    "add.transfer" to "Перевод между счетами",
    "kind.expense" to "Расход",
    "kind.income" to "Доход",
    "kind.transfer" to "Перевод",
    "add.expenseLower" to "расход",
    "add.incomeLower" to "доход",
    "add.transferLower" to "перевод",
    "add.approxMain" to "≈ {0} в основной валюте",
    "add.account" to "Счёт",
    "add.fromAcc" to "Откуда",
    "add.toAcc" to "Куда",
    "add.willGet" to "Зачислится",
    "add.category" to "Категория",
    "add.incomeCategory" to "Категория дохода",
    "add.date" to "Дата",
    "add.noteHint" to "Название или комментарий (необязательно)",
    "add.noteTransferHint" to "Комментарий к переводу",
    "add.saveTransfer" to "Перевести",

    // ——— ИИ-советник ———
    "ai.title" to "ИИ-советник",
    "ai.agent" to "Агент",
    "ai.key" to "API-ключ {0}",
    "ai.keyNote" to "{0} · шифруется и хранится только на телефоне",
    "ai.keySet" to "Ключ введён ({0} символов)",
    "ai.keyNone" to "Ключ не задан — ответ соберёт офлайн-разбор",
    "ai.keyOptional" to "Ключ не обязателен",
    "ai.model" to "Модель",
    "ai.modelNote" to "Пусто — по умолчанию {0}",
    "ai.endpoint" to "Адрес endpoint (OpenAI-совместимый)",
    "ai.send" to "Что отправить агенту",
    "ai.payloadNote" to "В запрос уйдёт {0} строк данных, примерно {1} токенов. Период — как на экране отчётов ({2}).",
    "ai.question" to "Вопрос",
    "ai.questionHint" to "Например: где я перетрачиваю и на чём реально сэкономить?",
    "ai.ask" to "Отправить агенту",
    "ai.thinking" to "Агент думает…",
    "ai.answer" to "Ответ · {0}",
    "ai.set.ops" to "Операции",
    "ai.set.budgets" to "Бюджеты",
    "ai.set.accounts" to "Счета и валюты",
    "ai.set.goals" to "Цели",
    "ai.set.report" to "Текущий отчёт",
    "ai.prompt.1" to "Где я перетрачиваю?",
    "ai.prompt.2" to "Как накопить 300 000 за полгода?",
    "ai.prompt.3" to "Стоит ли держать накопления в валюте?",
    "ai.reportQuestion" to "Объясни этот отчёт и скажи, где я перетрачиваю.",
    "ai.provider.custom" to "Свой endpoint",
    "ai.provider.customVendor" to "OpenAI-совместимый",
    "ai.offline" to "офлайн-разбор",
    "ai.errorFrom" to "ошибка запроса",
    "ai.offlineAfterError" to "\n\nПока — офлайн-разбор:\n",

    // запрос к модели
    "ai.systemPrompt" to "Ты финансовый советник. Данные пользователя:\n\n{0}\n\nВопрос: {1}\n\nОтветь по-русски, коротко, 3-5 пунктов с конкретными суммами. Только простой текст: без Markdown, без заголовков, без звёздочек и решёток. Пункты нумеруй как «1.», «2.».",
    "ai.payload.accounts" to "СЧЕТА (основная валюта {0}):",
    "ai.payload.ops" to "ОПЕРАЦИИ {0} ({1}), всего {2}:",
    "ai.payload.budgets" to "БЮДЖЕТЫ месяца (до конца месяца {0}):",
    "ai.payload.goals" to "ЦЕЛИ:",
    "ai.payload.report" to "ОТЧЁТ ({0}, {1}):",
    "ai.payload.compare" to "СРАВНЕНИЕ: за тот же отрезок прошлого периода {0}, изменение {1}%.",

    // офлайн-разбор
    "advice.intro" to "Разбор {0} ({1}), всего расходов {2}.",
    "advice.top" to "1. Основная статья — «{0}»: {1}, это {2}% всех трат.",
    "advice.second" to " Вторая — «{0}» ({1}).",
    "advice.over" to "2. Превышены лимиты: {0}. Верните их в рамки в первую очередь.",
    "advice.ok" to "2. Лимиты месяца соблюдены — можно поднять цель по накоплениям.",
    "advice.cut" to "3. Если срезать «{0}» на 15%, освободится примерно {1}. Это разумный первый шаг.",
    "advice.bigItem" to "крупную статью",
    "advice.multiCur" to "4. Мультивалютность: держите подушку в той валюте, в которой тратите, чтобы не терять на конвертации.",
    "advice.save" to "4. Откладывайте фиксированную сумму сразу после зарплаты — так цели растут без усилий.",

    // ——— сообщения и подтверждения ———
    "msg.enterAmount" to "Введите сумму",
    "msg.noAccount" to "Сначала добавьте счёт",
    "msg.pickToAcc" to "Выберите счёт зачисления",
    "msg.sameAccounts" to "Выберите разные счета",
    "msg.notEnough" to "На «{0}» недостаточно средств",
    "msg.notEnoughGoal" to "На «{0}» не хватает средств",
    "msg.transferDone" to "Перевод {0} → {1}",
    "msg.incomeDone" to "Доход {0} · {1}",
    "msg.expenseDone" to "Расход {0} · {1}",
    "msg.changed" to "Изменено: {0} · {1}",
    "msg.txDeleted" to "Операция удалена",
    "msg.deleteTx" to "Удалить операцию",
    "msg.deleteTxText" to "Операция исчезнет из истории, балансы пересчитаются.",
    "msg.goalContribution" to "Взнос на цель",
    "msg.deleteContribution" to "Удалить «{0}»? Деньги вернутся на счёт, а прогресс цели уменьшится.",
    "msg.transferTitle" to "Перевод: {0} → {1}",
    "msg.goalTitle" to "На цель: {0}",
    "msg.goalDone" to "Отложено {0} на «{1}»",
    "msg.mainCurSet" to "Основная валюта — {0}, курсы пересчитаны",
    "msg.mainCurTitle" to "Сменить основную валюту",
    "msg.mainCurText" to "Основной валютой станет {0}. Курсы пересчитаются относительно неё, лимиты категорий переведутся в новую валюту. Остатки счетов и суммы операций не изменятся.",
    "msg.mainCurAction" to "Сменить",
    "onb.cur.title" to "Основная валюта",
    "onb.cur.body" to "В ней считается общий баланс, бюджеты и отчёты. Курсы остальных валют задаются относительно неё.",
    "onb.cur.more" to "Другие валюты добавляются в настройках, основную можно сменить позже.",
    "msg.accCurSet" to "«{0}» теперь в {1}",
    "msg.enterAccName" to "Введите название счёта",
    "msg.accAdded" to "Счёт «{0}» добавлен",
    "msg.accSaved" to "Счёт сохранён",
    "msg.accDeleted" to "Счёт удалён",
    "msg.needOneAccount" to "Нужен хотя бы один счёт",
    "msg.deleteAccTitle" to "Удалить счёт",
    "msg.deleteAccWithTx" to "Вместе со счётом «{0}» удалятся {1} по нему.",
    "msg.deleteAccPlain" to "Удалить счёт «{0}»?",
    "msg.enterCatName" to "Введите название категории",
    "msg.catCreated" to "Категория «{0}» создана",
    "msg.catSaved" to "Категория сохранена",
    "msg.catDeleted" to "Категория удалена",
    "msg.deleteCatTitle" to "Удалить категорию",
    "msg.deleteCatMove" to "{0} из «{1}» перейдут в «{2}».",
    "msg.deleteCatPlain" to "Удалить «{0}»?",
    "msg.lastCatExpense" to "Это последняя категория расходов",
    "msg.lastCatIncome" to "Это последняя категория доходов",
    "msg.enterGoal" to "Укажите название и сумму цели",
    "msg.goalCreated" to "Цель «{0}» создана",
    "msg.goalSaved" to "Цель сохранена",
    "msg.goalDeleted" to "Цель удалена",
    "msg.deleteGoalTitle" to "Удалить цель",
    "msg.deleteGoalText" to "Цель «{0}» исчезнет. Операции взносов останутся в истории.",
    "msg.curAdded" to "Добавлены {0} — проверьте курс",
    "msg.curAddedCode" to "Валюта {0} добавлена",
    "msg.curRemoved" to "Валюта {0} убрана",
    "msg.curCodeLen" to "Код валюты — от 2 до 6 символов",
    "msg.curExists" to "Такая валюта уже добавлена",
    "msg.curRate" to "Укажите курс в {0}",
    "msg.curBase" to "{0} — основная валюта и база курсов, её убрать нельзя",
    "msg.curIsMain" to "Это основная валюта приложения",
    "msg.curUsedAcc" to "Валюта используется на счёте",
    "msg.curUsedGoal" to "Валюта используется в цели",
    "msg.demoTitle" to "Демо-данные",
    "msg.demoText" to "Текущие операции, счета и цели заменятся примерами. Сначала можно сделать резервную копию.",
    "msg.demoReplace" to "Заменить",
    "msg.demoLoaded" to "Загружены демо-данные",
    "msg.clearTitle" to "Очистить данные",
    "msg.clearText" to "Удалятся все операции, счета, цели и свои категории. Настройки останутся.",
    "msg.clearAction" to "Очистить",
    "msg.cleared" to "Данные очищены",
    "msg.remindSet" to "Напомню в {0}:00",
    "msg.noNotifyPermission" to "Без разрешения на уведомления напоминание не придёт",
    "msg.askQuestion" to "Напишите вопрос агенту",
    "msg.pickData" to "Выберите данные для отправки",
    "msg.backupSaved" to "Копия сохранена: {0}",
    "msg.restoreTitle" to "Восстановить копию",
    "msg.restoreText" to "Все текущие данные заменятся копией от {0}. Текущее состояние сохранится на телефоне в before-restore.json.",
    "msg.restored" to "Данные восстановлены из копии",
    "msg.driveUnlinked" to "Google Диск отключён в приложении",
    "msg.driveCancelled" to "Подключение Google Диска отменено",
    "msg.driveDenied" to "Доступ к Google Диску не выдан",
    "msg.driveAuthError" to "Google: ошибка авторизации ({0}). Проверьте OAuth-клиент в Google Cloud.",
    "msg.driveError" to "Google Диск: {0}",
    "msg.noToken" to "Google не выдал токен",

    // ——— ошибки сервисов ———
    "drive.err.expired" to "доступ истёк, подключите Диск заново",
    "drive.err.forbidden" to "Google запретил доступ (проверьте, что Drive API включён в проекте Google Cloud)",
    "drive.err.code" to "ошибка {0}",
    "drive.err.offline" to "нет связи с Google Диском",
    "drive.err.download" to "не удалось скачать копию ({0})",
    "drive.err.empty" to "пустой файл",
    "ai.err.claudeKey" to "Claude: неверный API-ключ",
    "ai.err.claudeForbidden" to "Claude: у ключа нет доступа к модели {0}",
    "ai.err.claudeModel" to "Claude: модель «{0}» не найдена или недоступна",
    "ai.err.claudeRate" to "Claude: слишком много запросов, попробуйте через минуту",
    "ai.err.claudeApi" to "Claude: ошибка {0} — {1}",
    "ai.err.claudeOffline" to "Нет связи с api.anthropic.com",
    "ai.err.claudeEmpty" to "Claude вернул пустой ответ",
    "ai.err.refusal" to "Модель отказалась отвечать на этот запрос. Переформулируйте вопрос.",
    "ai.err.empty" to "{0} вернул пустой ответ",
    "ai.err.key" to "{0}: неверный ключ или нет доступа",
    "ai.err.model" to "{0}: модель или адрес не найдены",
    "ai.err.rate" to "{0}: превышен лимит запросов",
    "ai.err.code" to "{0}: ошибка {1} — {2}",
    "ai.err.offline" to "{0}: нет связи ({1})",
    "ai.err.parse" to "{0}: непонятный ответ сервера",
    "ai.err.noEndpoint" to "Укажите адрес своего endpoint, например http://192.168.1.10:11434/v1",

    // ——— уведомление ———
    "notify.channel" to "Напоминания",
    "notify.text" to "Запишите сегодняшние расходы — это пара секунд",

    // ——— даты ———
    "date.today" to "Сегодня",
    "date.yesterday" to "Вчера",
    "date.dm" to "{0} {1}",
    "date.dmy" to "{0} {1} {2}",
    "date.weekRange" to "{0}–{1} {2}",
    "date.weekRangeCross" to "{0} {1} – {2} {3}",
    "date.quarter" to "{0} квартал",
    "date.year" to "{0} год",
    "date.months" to "Январь|Февраль|Март|Апрель|Май|Июнь|Июль|Август|Сентябрь|Октябрь|Ноябрь|Декабрь",
    "date.monthsGen" to "января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря",
    "date.monthsShort" to "янв|фев|мар|апр|май|июн|июл|авг|сен|окт|ноя|дек",
    "date.week" to "пн|вт|ср|чт|пт|сб|вс",
    "date.weekFull" to "понедельник|вторник|среда|четверг|пятница|суббота|воскресенье",

    // ——— множественное число ———
    "pl.day" to "день|дня|дней",
    "pl.op" to "операция|операции|операций",
    "pl.account" to "счёт|счёта|счетов",
    "pl.category" to "категория|категории|категорий",
    "pl.goal" to "цель|цели|целей",
    "pl.currency" to "валюта|валюты|валют",
    "pl.period" to "период|периода|периодов",
    "pl.place" to "месте|местах|местах",

    // ——— цвета палитры ———
    "color.steel" to "Стальной",
    "color.night" to "Ночной",
    "color.sky" to "Небесный",
    "color.teal" to "Бирюзовый",
    "color.pine" to "Хвойный",
    "color.olive" to "Оливковый",
    "color.sand" to "Песочный",
    "color.ochre" to "Охра",
    "color.brick" to "Кирпичный",
    "color.cherry" to "Вишнёвый",
    "color.plum" to "Сливовый",
    "color.graphite" to "Графитовый",

    // ——— демо-данные ———
    "demo.cur" to "RUB",
    "demo.acc.card" to "Карта · 4417",
    "demo.acc.cash" to "Наличные",
    "demo.acc.save" to "Накопления",
    "demo.acc.cardMask" to "•• 4417",
    "demo.acc.saveMask" to "вклад",
    "demo.cat.food" to "Продукты",
    "demo.cat.home" to "Жильё",
    "demo.cat.transport" to "Транспорт",
    "demo.cat.cafe" to "Кафе и бары",
    "demo.cat.fun" to "Развлечения",
    "demo.cat.health" to "Здоровье",
    "demo.cat.clothes" to "Одежда",
    "demo.cat.other" to "Прочее",
    "demo.cat.salary" to "Зарплата",
    "demo.cat.side" to "Подработка",
    "demo.code.food" to "ПР",
    "demo.code.home" to "ЖЛ",
    "demo.code.transport" to "ТР",
    "demo.code.cafe" to "КФ",
    "demo.code.fun" to "РЗ",
    "demo.code.health" to "ЗД",
    "demo.code.clothes" to "ОД",
    "demo.code.other" to "ПЧ",
    "demo.code.salary" to "ЗП",
    "demo.code.side" to "ПД",
    "demo.tx.grocery1" to "Пятёрочка",
    "demo.tx.grocery2" to "ВкусВилл",
    "demo.tx.grocery3" to "Лента",
    "demo.tx.grocery4" to "Магнит",
    "demo.tx.grocery5" to "Перекрёсток",
    "demo.tx.metro" to "Метро",
    "demo.tx.coffee" to "Кофейня «Цех»",
    "demo.tx.rent" to "Аренда квартиры",
    "demo.tx.pharmacy" to "Аптека",
    "demo.tx.taxi" to "Такси",
    "demo.tx.salary" to "Зарплата",
    "demo.tx.cinema" to "Кинотеатр",
    "demo.tx.internet" to "Интернет и связь",
    "demo.tx.freelance" to "Фриланс-проект",
    "demo.tx.gym" to "Спортзал",
    "demo.tx.bar" to "Бар «Контур»",
    "demo.tx.jacket" to "Куртка",
    "demo.tx.books" to "Книги",
    "demo.tx.carsharing" to "Каршеринг",
    "demo.tx.concert" to "Концерт",
    "demo.tx.dentist" to "Стоматолог",
    "demo.tx.restaurant" to "Ресторан",
    "demo.tx.utilities" to "Коммунальные",
    "demo.tx.tickets" to "Отпуск: билеты",
    "demo.tx.weekGrocery" to "Продукты за неделю",
    "demo.tx.friends" to "Кафе с друзьями",
    "demo.tx.doctor" to "Врач",
    "demo.goal.trip" to "Отпуск в Грузии",
    "demo.goal.tripHint" to "Хочу поехать в мае",
    "demo.goal.laptop" to "Новый ноутбук",
    "demo.goal.laptopHint" to "Осталось два взноса",
    "demo.goal.fund" to "Резервный фонд",
    "demo.goal.fundHint" to "Три месяца расходов",

    // ——— курс прямо в операции ———
    "add.rate" to "Курс",
    "add.rateSaved" to "Курс: 1 {0} = {1}",

    // ——— CSV ———
    "csv.section" to "CSV",
    "csv.export" to "Выгрузить в CSV",
    "csv.import" to "Загрузить из CSV",
    "csv.template" to "Сохранить шаблон",
    "csv.periodTitle" to "Что выгрузить",
    "csv.all" to "Все операции",
    "csv.exported" to "Выгружено {0} в {1}",
    "csv.templateSaved" to "Шаблон сохранён: {0}",
    "csv.fileError" to "Не удалось открыть файл",
    "csv.importTitle" to "Загрузка из CSV",
    "csv.found" to "Подходящих строк: {0}",
    "csv.errorsFound" to "Строк с ошибками: {0}",
    "csv.newAccounts" to "Будут созданы счета: {0}",
    "csv.newCats" to "Будут созданы категории: {0}",
    "csv.apply" to "Загрузить {0}",
    "csv.imported" to "Загружено {0}",
    "csv.nothing" to "Подходящих строк не нашлось",
    "csv.line" to "Строка {0}: {1}",
    "csv.errDate" to "не разобрана дата",
    "csv.errAmount" to "не разобрана сумма",
    "csv.errColumns" to "мало колонок",
    "csv.errAccount" to "не указан счёт",
    "csv.note" to "Колонки шаблона: дата;тип;сумма;валюта;счёт;счёт-получатель;сумма зачисления;категория;комментарий. Тип — expense, income или transfer. Разделитель — точка с запятой или запятая, дата в виде 2026-09-14 или 14.09.2026. Неизвестные счета и категории создадутся автоматически.",

)
