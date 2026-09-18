<h1 align="center">Копеечка</h1>

<p align="center">
  <b>Деньги семьи — в одном телефоне, а не на чужом сервере.</b><br>
  Счета в любых валютах, бюджеты, общий бюджет на двоих, банковские СМС и чеки без ручного ввода.
</p>

<p align="center">
  <a href="https://github.com/MerdanOchanov/kopeechka-android/releases/latest"><img alt="Последняя версия" src="https://img.shields.io/github/v/release/MerdanOchanov/kopeechka-android?label=%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D1%8F&color=2C455D"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-4F7A5B">
  <img alt="iOS 15+" src="https://img.shields.io/badge/iOS-15%2B-597EA3">
  <img alt="Без рекламы и трекеров" src="https://img.shields.io/badge/%D1%80%D0%B5%D0%BA%D0%BB%D0%B0%D0%BC%D0%B0%20%D0%B8%20%D1%82%D1%80%D0%B5%D0%BA%D0%B5%D1%80%D1%8B-%D0%BD%D0%B5%D1%82-A9762F">
  <img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin-Multiplatform-8A3B5B">
</p>

<p align="center">
  <a href="https://github.com/MerdanOchanov/kopeechka-android/releases/latest"><b>⬇ Скачать APK для Android</b></a>
</p>

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" alt="Главный экран">
  <img src="docs/screenshots/report.png" width="30%" alt="Отчёты">
  <img src="docs/screenshots/currencies.png" width="30%" alt="Валюты и курсы">
</p>

---

## Почему Копеечка

Большинство финансовых приложений сделаны для одной страны, одной валюты и одного
человека. Копеечка — для жизни, как она устроена в СНГ:

- 💱 **Манат, сум, тенге, рубль и доллар — на равных.** У валюты два курса, банковский
  и рыночный: для манатов разница в разы, и итог считается по тому, который вы выбрали.
- 📩 **Банк сам пишет расходы.** Банковские СМС и уведомления превращаются в операции.
  Готовые шаблоны для банков Туркменистана, фильтр по последним цифрам карты.
- 👫 **Бюджет на двоих.** Два телефона ведут одни счета — даже без интернета,
  напрямую по Wi-Fi. Крупные траты с общего счёта ждут «да» второго.
- 🔒 **Без сервера.** Данные лежат только на вашем телефоне. Нет регистрации,
  рекламы, аналитики и трекеров.
- 🌐 **Пять языков**: русский, английский, туркменский, узбекский и казахский.

## Что умеет

### 💰 Деньги
- Карты, наличные, накопления, вклады и **золото в граммах** — в общий итог оно идёт по цене грамма.
- Расходы, доходы и переводы между счетами с пересчётом по курсу; курс пары можно поправить прямо в операции.
- 70 мировых валют и свои собственные. **Официальные курсы одной кнопкой** — ЦБ России,
  Нацбанк Казахстана, ЦБ Узбекистана.
- **Регулярные платежи**: кредиты, рассрочки, подписки, аренда — с напоминанием накануне
  и прогрессом «оплачено 5 из 12».
- Цели и накопления, **долги** (кто кому и сколько, со сроками и заработком)
  и модуль **«Бизнес»**: заказы, клиенты, прайс, выручка и маржа.

### ⚡ Без ручного ввода
| Источник | Как работает | Нужен интернет |
| --- | --- | --- |
| **QR-код с чека** | сумма, дата и возврат прямо из фискального кода | нет |
| **Банковские СМС** *(сборка с GitHub)* | правила по отправителю, счёту и цифрам карты; разбор истории за 90 дней | нет |
| **Уведомления банков** *(Android)* | для банков, которые не шлют СМС | нет |
| **Выписка банка (CSV)** | колонки узнаются по заголовкам, дубли пропускаются | нет |
| **Фото чека** | ИИ читает магазин, позиции, сумму и дату | да, по вашему ключу |

Всё найденное попадает на экран **«На проверку»** — в деньги ничего не пишется без вашего «да».
Категория, выбранная для магазина однажды, запоминается.

### 👫 Вместе
- Общее пространство по коду приглашения; обмен через **Google Диск**, **WebDAV**
  (Яндекс.Диск, Nextcloud) или **напрямую по Wi-Fi**.
- Записи сливаются без потерь: побеждает более поздняя правка, удалённое не воскресает,
  у каждой операции есть автор, возможные дубли подсвечиваются.
- **Заявки на расход**: с общего счёта от заданной суммы трата ждёт одобрения второго.

### 📊 Понимание
- Месячные лимиты по категориям и «сколько можно тратить в день».
- Отчёты за неделю, месяц, квартал и год, с листанием периодов, фильтрами по счёту
  и категории и сравнением с тем же отрезком прошлого периода.
- **ИИ-советник** на ваших данных: Claude, OpenAI, Gemini, **GigaChat**, **YandexGPT**
  или своя локальная модель. Вы сами отмечаете, что отправить. Без ключа — офлайн-разбор.

### 🔒 Данные под контролем
- Один JSON-файл во внутреннем хранилище телефона.
- Резервные копии — в **ваш** Google Диск (вручную или раз в день автоматически)
  или **файлом**: сохранить на телефон, отправить себе в IMO, Telegram или на почту.
- Ключи ИИ и пароль WebDAV шифруются Android Keystore / iOS Keychain и в копии не попадают.
- Что и когда уходит в сеть — честно расписано в [политике конфиденциальности](docs/PRIVACY.md).

## Установка

**Android.** Скачайте `kopeechka-*.apk` со страницы
[последнего выпуска](https://github.com/MerdanOchanov/kopeechka-android/releases/latest)
и откройте файл — телефон попросит разрешить установку из этого источника.
Новая версия ставится поверх старой, данные сохраняются, а о следующих версиях
приложение сообщит само.

**Google Play.** Идёт закрытое тестирование. Версия из Play не читает СМС (так требует
политика Google; уведомления банков работают) и поверх APK с GitHub не ставится —
перед переходом сохраните копию файлом.

**iPhone.** Собирается и проверяется в облаке на каждое изменение; в App Store пока нет.

## Для разработчиков

Одна кодовая база на Kotlin Multiplatform и Compose Multiplatform: данные, расчёты,
языки, сеть и все экраны — в общем модуле, платформам остаются только их особенности.

```bash
gradlew.bat :app:assembleFullDebug          # APK со всеми функциями
gradlew.bat :app:testFullDebugUnitTest      # тесты: слияние, курсы, СМС, QR, выписки…
gradlew.bat :app:bundlePlayRelease          # AAB для Google Play
```

Нужны JDK 17+ и Android SDK 37. Kotlin 2.4, AGP 9.4, Gradle 9.7 (ставится обёрткой).
Подробности — в [docs/BUILD.md](docs/BUILD.md).

```
shared/src/commonMain/   общий код
  data/   модель, расчёты, слияние, СМС, чеки, выписки, регулярные платежи, языки
  net/    ИИ-провайдеры, Google Диск, WebDAV и Wi-Fi, курсы ЦБ, проверка обновлений
  ui/     все экраны на Compose Multiplatform
  AppViewModel.kt · Ports.kt (границы с платформой)
shared/src/iosMain/      файл данных, Keychain, камера и QR, файлы
app/                     Android: СМС и уведомления, сервер обмена по Wi-Fi, фоновые задачи, тесты
iosApp/                  SwiftUI-обёртка и project.yml для XcodeGen
```

| Документ | О чём |
| --- | --- |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | из чего собрано приложение и почему так |
| [DATA_MODEL](docs/DATA_MODEL.md) | формат хранения и резервной копии, поле за полем |
| [SYNC](docs/SYNC.md) | общий бюджет: слияние, способы обмена, заявки |
| [AI_ADVISOR](docs/AI_ADVISOR.md) | провайдеры, ключи, что уходит в запрос |
| [BUSINESS](docs/BUSINESS.md) · [DEBTS](docs/DEBTS.md) | как считаются выручка, прибыль и долги |
| [BUILD](docs/BUILD.md) · [PLAY](docs/PLAY.md) · [GOOGLE_DRIVE](docs/GOOGLE_DRIVE.md) | сборка, подпись, публикация, OAuth |
| [PRIVACY](docs/PRIVACY.md) · [CHANGELOG](CHANGELOG.md) | конфиденциальность и история версий |

---

<details>
<summary><b>English</b></summary>

**Kopeechka** is a personal and family finance app for Android and iOS with no server
of its own: data stays on your phone, with no ads, analytics or sign-up.

- Accounts in any currency — including Turkmen manat, Uzbek som and Kazakh tenge —
  with bank and market rates, official central bank rates in one tap, and gold in grams.
- Hands-free input: bank SMS and notifications, receipt QR codes, bank statement CSV
  and AI receipt photos, all landing in a review queue first.
- A shared budget for two over Google Drive, WebDAV or directly over Wi-Fi,
  with spending requests on shared accounts.
- Budgets, reports, goals, debts, recurring payments and a small-business module.
- AI advisor on your own key: Claude, OpenAI, Gemini, GigaChat, YandexGPT or a local model.
- Five languages: Russian, English, Turkmen, Uzbek and Kazakh.

[Download the latest APK](https://github.com/MerdanOchanov/kopeechka-android/releases/latest).
</details>

<p align="center"><sub>Интерфейс вырос из прототипов, собранных в <a href="https://claude.ai/design">Claude Design</a>: строгая «чертёжная» система Industry — квадратные углы, волосяные рамки, шрифт Barlow.</sub></p>
