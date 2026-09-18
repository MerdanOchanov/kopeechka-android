# Сборка и запуск

## Что нужно

| Инструмент | Версия | Зачем |
| --- | --- | --- |
| JDK | 17 или новее (проверено на 21) | Gradle, AGP 9 и компилятор Kotlin |
| Android SDK | platform 37, platform-tools | сборка и установка (`compileSdk`/`targetSdk` 37, `minSdk` 26) |
| Gradle | 9.7.1 — ставится обёрткой `gradlew` | — |

Плагины: Android Gradle Plugin 9.4, Kotlin 2.4.20, Compose Multiplatform 1.12.
Модуль `:shared` подключает Android через плагин `com.android.kotlin.multiplatform.library`.

Путь к SDK указывается в `local.properties` (файл не в репозитории):

```
sdk.dir=C\:\\Users\\<имя>\\AppData\\Local\\Android\\Sdk
```

Без Android Studio SDK ставится command-line tools:

```bash
sdkmanager --install "platform-tools" "platforms;android-37"
```

## Варианты приложения

| Вариант | Пакет | Для чего |
| --- | --- | --- |
| `play` | `com.arassanusga.kopeechka` | **то, что получают люди** — и в Google Play, и на GitHub. Чтение СМС ждёт одобрения декларации (см. [PLAY.md](PLAY.md)), до тех пор СМС вставляют вручную |
| `full` | `app.kopeechka.finance` | разработка на своём телефоне: все функции сразу, включая чтение СМС |

На GitHub выкладывается не своя сборка, а **подписанный Google универсальный APK**
из Play Console (Проводник App Bundle → версия → «Скачать» → «Подписанный
универсальный APK»). Поэтому пакет, подпись, название и версия у GitHub и Play
совпадают, и одно ставится поверх другого. Проверку обновлений на GitHub
приложение включает само, только если его поставили не из Play.

```bash
gradlew.bat :app:assembleFullDebug        # app/build/outputs/apk/full/debug/app-full-debug.apk
gradlew.bat :app:bundlePlayRelease        # app/build/outputs/bundle/playRelease/app-play-release.aab
```

Установка на телефон с отладкой по USB или по Wi-Fi:

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

Номер версии — `versionCode` и `versionName` в `app/build.gradle.kts`, для iOS —
`CFBundleVersion` и `CFBundleShortVersionString` в `iosApp/project.yml`. Меняйте их вместе.

## Тесты

```bash
gradlew.bat :app:testFullDebugUnitTest
```

Тесты общего кода лежат в `app/src/test` и покрывают слияние общего бюджета, курсы
(включая золото и два курса), разбор СМС реальных банков, регулярные платежи, QR-чеки,
выписки CSV, курсы ЦБ и сравнение версий. Те же тесты гоняет CI (`.github/workflows/android.yml`)
на каждое изменение.

## Общий код и iOS

Проверить, что общий код не зацепил платформенных API, можно и на Windows:

```bash
gradlew.bat :shared:compileCommonMainKotlinMetadata
```

Эта сборка компилирует `commonMain` без привязки к платформе и падает на всём, что
доступно только на JVM (например, `@Volatile` из `kotlin.jvm` — в общем коде нужен
`kotlin.concurrent.Volatile`, а время — из `kotlin.time`).

Цели `iosArm64` и `iosSimulatorArm64` компилируются только на macOS с Xcode, поэтому
iOS собирается в облаке: workflow `.github/workflows/ios.yml` линкует `Shared.framework`,
генерирует Xcode-проект из `iosApp/project.yml` утилитой XcodeGen, собирает приложение,
запускает его на симуляторе и выкладывает скриншот артефактом. Вручную:
**Actions → iOS → Run workflow**. На macOS локально:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodegen generate && open Kopeechka.xcodeproj
```

Грабли, на которые уже наступили: `gradlew` должен быть исполняемым
(`git update-index --chmod=+x gradlew`); путь поиска фреймворка в Xcode задаётся через
`$(PLATFORM_NAME)` — в `$(SDK_NAME)` есть версия SDK, и папка не находится; цель `iosX64`
(Intel-симулятор) Compose Multiplatform 1.12 больше не поддерживает.

## Release-сборка

Подпись, ключ загрузки и выпуск в Google Play описаны в [PLAY.md](PLAY.md).
Коротко: ключ лежит вне репозитория, путь и пароль — в `keystore.properties`
(в `.gitignore`), без этого файла релиз собирается неподписанным.

SHA-1 ключа, которым подписано приложение у пользователя, нужно добавить
Android-клиентом OAuth в Google Cloud, иначе Диск не авторизуется —
см. [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md).

## Проверка на устройстве

```bash
adb logcat -c && adb shell am start -n app.kopeechka.finance/.MainActivity
adb logcat -d | findstr /i "AndroidRuntime FATAL"
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

Телефон с отладкой по Wi-Fi находится командой `adb mdns services`. В Git Bash путям
вида `/sdcard/...` нужен `MSYS_NO_PATHCONV=1`, иначе они превратятся в пути Windows.

## Иконка

Эскиз иконки лежит в [`tools/icon-preview.html`](../tools/icon-preview.html) — тот же
рисунок, что и в `res/drawable/ic_launcher_foreground.xml`, но в SVG и в нескольких масках.

## Если сборка падает с «Unable to establish loopback connection»

Симптом: любая команда `gradlew` обрывается сразу, а в `--stacktrace` видно
`java.net.SocketException: Invalid argument: connect` внутри
`sun.nio.ch.PipeImpl$Initializer$LoopbackConnector`.

Это не Gradle и не Java. `Selector.open()` на Windows открывает служебное соединение
через Unix-сокет, а файл такого сокета — точка повторного разбора. Если каталог, куда
JVM его кладёт (`jdk.net.unixdomain.tmpdir`, по умолчанию `%TEMP%`), этого не позволяет,
`bind` проходит, файла нет, и `connect` возвращает «Invalid argument». Обычные сетевые
соединения при этом работают, поэтому легко подумать на файрвол.

Лечится переносом сокетов в каталог, где они создаются. Свойство нужно **всем**
процессам сборки — клиенту, демону Gradle и демону Kotlin, — поэтому проще всего
задать его переменной окружения (каталог должен существовать):

```
JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<вы>\.gradle\sockets
```

Класть его в `org.gradle.jvmargs` пользовательского `~/.gradle/gradle.properties`
бесполезно: проектный `gradle.properties` эту строку целиком перекрывает. Побочный
эффект — строка «Picked up JAVA_TOOL_OPTIONS» в выводе каждой Java-программы.
