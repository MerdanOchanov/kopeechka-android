# Политика конфиденциальности «Копеечки»

*Действует с 17 сентября 2026 года.*

«Копеечка» — приложение для учёта личных и семейных финансов. У приложения
**нет своего сервера**: разработчик не получает, не хранит и не видит ваши данные.

## Что хранится и где

Счета, операции, бюджеты, цели, долги, заказы и настройки хранятся **только на вашем
телефоне**, в личной папке приложения. Приложение не показывает рекламу, не содержит
аналитики и трекеров и никому не передаёт данные для маркетинга.

Данные уходят с телефона только в перечисленных ниже случаях — и только если вы
сами включили соответствующую функцию.

## Когда данные покидают телефон

**Резервные копии в Google Диск.** Если вы подключили Google Диск, приложение кладёт
копию данных в папку на **вашем** Диске. Доступ запрашивается к файлам, которые создало
само приложение (`drive.file`), — остальное содержимое Диска приложению недоступно.
Ключи ИИ-провайдеров в копию не попадают.

**ИИ-советник и чеки по фото.** Работают, только если вы указали свой ключ выбранного
провайдера (Anthropic, OpenAI, Google или адрес собственного сервера). Когда вы задаёте
вопрос советнику, провайдеру отправляются те данные, которые вы отметили в настройках
советника. Когда вы распознаёте чек, провайдеру отправляется фотография чека
и список ваших категорий. Данные обрабатываются по правилам этого провайдера.
Ключ хранится в защищённом хранилище телефона (Android Keystore / iOS Keychain).

**Общий бюджет («Вместе»).** Если вы завели общее пространство с другим человеком,
ваши счета и операции передаются ему — через хранилище, которое вы выбрали
(ваш Google Диск, ваш WebDAV-сервер), или напрямую по вашей локальной сети.
Черновики, правила чтения СМС и ключи не передаются. Пароль WebDAV хранится
в защищённом хранилище телефона.

**Экспорт CSV.** Файл сохраняется туда, куда вы укажете в системном диалоге.

## Разрешения

- **Интернет** — для резервных копий, ИИ-советника и общего бюджета.
- **Уведомления** — для вечернего напоминания, если вы его включили.
- **Камера и фото** — приложение не запрашивает доступ к камере напрямую: снимок
  делает системная камера, фото выбирается системным окном, и приложению передаётся
  только выбранный снимок.
- **СМС** — только в версии, распространяемой через GitHub. Сообщения банков
  разбираются на телефоне и никуда не отправляются. В версии из Google Play
  этой функции и разрешения нет.

## Удаление данных

Все данные удаляются вместе с приложением. Резервные копии на Google Диске вы можете
удалить сами в папке «Kopeechka» на своём Диске. Данные, отправленные ИИ-провайдеру,
удаляются по правилам этого провайдера.

## Дети

Приложение не предназначено для детей младше 13 лет и сознательно не собирает
их данные.

## Изменения и связь

Изменения этой политики публикуются в этом файле; история правок видна в репозитории.
Вопросы можно задать через раздел Issues:
https://github.com/MerdanOchanov/kopeechka-android/issues

---

# Privacy policy (English)

Kopeechka is a personal finance app with **no server of its own**: the developer does not
receive, store or see your data. Everything stays on your phone, with no ads, analytics
or trackers.

Data leaves the phone only when you turn on a feature that needs it:

- **Google Drive backups** go to a folder in your own Drive; the app can only access
  files it created (`drive.file`). AI keys are never included.
- **AI advisor and receipt scanning** work only with your own provider key. Your selected
  data, or the receipt photo with your category list, is sent to that provider and handled
  under its terms.
- **Shared budget** sends your accounts and operations to the other member through the
  storage you chose (your Google Drive, your WebDAV server) or directly over your local
  network. Drafts, SMS rules and keys are not shared.
- **CSV export** is saved where you choose.

The app does not request camera access directly: photos come from the system camera or
picker. SMS reading exists only in the GitHub build and never leaves the phone; the
Google Play build has neither the feature nor the permission.

Uninstalling the app deletes its data. Contact: https://github.com/MerdanOchanov/kopeechka-android/issues
