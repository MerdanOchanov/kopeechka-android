package app.kopeechka.finance.data

import java.time.LocalDate

object Demo {
    val DEFAULT_CATEGORIES = listOf(
        Category("food", "ПР", "Продукты", 24000.0, color = "#4F7A5B"),
        Category("home", "ЖЛ", "Жильё", 34000.0, color = "#2C455D"),
        Category("transport", "ТР", "Транспорт", 6000.0, color = "#597EA3"),
        Category("cafe", "КФ", "Кафе и бары", 9000.0, color = "#B08A4F"),
        Category("fun", "РЗ", "Развлечения", 7000.0, color = "#8A3B5B"),
        Category("health", "ЗД", "Здоровье", 5000.0, color = "#3E8E8A"),
        Category("clothes", "ОД", "Одежда", 6000.0, color = "#A9762F"),
        Category("other", "ПЧ", "Прочее", 4000.0, color = "#5D5D60"),
        Category("income", "ДХ", "Зарплата", income = true, color = "#416180"),
        Category("side", "ПД", "Подработка", income = true, color = "#6B4E8A"),
    )

    /** Пустые данные: один счёт и стандартные категории. */
    fun empty(settings: Settings = Settings()) = AppData(
        accounts = listOf(Account("card", "Карта", "Карта", "", "RUB", 0.0)),
        categories = DEFAULT_CATEGORIES,
        settings = settings,
    )

    private data class Seed(val d: Int, val title: String, val cat: String, val acc: String, val amount: Double)

    fun create(today: LocalDate = LocalDate.now()): AppData {
        val seed = listOf(
            Seed(0, "Пятёрочка", "food", "card", -1840.0),
            Seed(0, "Метро", "transport", "cash", -62.0),
            Seed(0, "Кофейня «Цех»", "cafe", "card", -420.0),
            Seed(1, "Аренда квартиры", "home", "card", -32000.0),
            Seed(1, "Аптека", "health", "cash", -1260.0),
            Seed(1, "Такси", "transport", "card", -540.0),
            Seed(2, "Зарплата", "income", "card", 96400.0),
            Seed(2, "Кинотеатр", "fun", "card", -1100.0),
            Seed(3, "ВкусВилл", "food", "card", -3210.0),
            Seed(3, "Интернет и связь", "home", "card", -700.0),
            Seed(4, "Фриланс-проект", "side", "save", 190.0),
            Seed(4, "Спортзал", "health", "card", -2400.0),
            Seed(5, "Лента", "food", "card", -5380.0),
            Seed(5, "Бар «Контур»", "cafe", "card", -2650.0),
            Seed(6, "Куртка", "clothes", "card", -4990.0),
            Seed(8, "Книги", "other", "card", -2190.0),
            Seed(9, "Магнит", "food", "cash", -2640.0),
            Seed(12, "Каршеринг", "transport", "card", -1480.0),
            Seed(14, "Концерт", "fun", "card", -4200.0),
            Seed(18, "Стоматолог", "health", "card", -8600.0),
            Seed(21, "Перекрёсток", "food", "card", -4310.0),
            Seed(24, "Ресторан", "cafe", "card", -5120.0),
            Seed(27, "Коммунальные", "home", "card", -6400.0),
            // прошлые месяцы — для отчёта «Год»
            Seed(35, "Аренда квартиры", "home", "card", -32000.0),
            Seed(38, "Ашан", "food", "card", -9800.0),
            Seed(44, "Отпуск: билеты", "fun", "card", -21400.0),
            Seed(52, "Зарплата", "income", "card", 96400.0),
            Seed(66, "Аренда квартиры", "home", "card", -32000.0),
            Seed(70, "Продукты за неделю", "food", "card", -11200.0),
            Seed(82, "Зарплата", "income", "card", 96400.0),
            Seed(97, "Аренда квартиры", "home", "card", -32000.0),
            Seed(101, "Продукты за неделю", "food", "card", -8700.0),
            Seed(128, "Аренда квартиры", "home", "card", -32000.0),
            Seed(133, "Кафе с друзьями", "cafe", "card", -6900.0),
            Seed(158, "Аренда квартиры", "home", "card", -32000.0),
            Seed(163, "Врач", "health", "card", -5400.0),
        )
        val txs = seed.mapIndexed { i, s ->
            Tx(id = (i + 1).toLong(), date = today.minusDays(s.d.toLong()).toEpochDay(), title = s.title, cat = s.cat, acc = s.acc, amount = s.amount)
        }
        // Стартовые остатки подобраны так, чтобы текущие балансы совпали с макетом.
        fun initial(acc: String, target: Double) = target - txs.filter { it.acc == acc }.sumOf { it.amount }
        val accounts = listOf(
            Account("card", "Карта · 4417", "Карта", "•• 4417", "RUB", initial("card", 112480.0)),
            Account("cash", "Наличные", "Кошелёк", "", "RUB", initial("cash", 9840.0)),
            Account("save", "Накопления", "Накопления", "вклад", "USD", initial("save", 1250.0)),
        )
        val goals = listOf(
            Goal("g1", "Отпуск в Грузии", 180000.0, 68000.0, "RUB", "Хочу поехать в мае"),
            Goal("g2", "Новый ноутбук", 140000.0, 104000.0, "RUB", "Осталось два взноса"),
            Goal("g3", "Резервный фонд", 300000.0, 122000.0, "RUB", "Три месяца расходов"),
        )
        return AppData(
            accounts = accounts,
            categories = DEFAULT_CATEGORIES,
            txs = txs,
            goals = goals,
            nextId = 1000,
        )
    }
}
