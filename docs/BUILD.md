# Сборка и запуск

## Что нужно

| Инструмент | Версия | Зачем |
| --- | --- | --- |
| JDK | 17 или новее (проверено на 21) | Gradle и компилятор Kotlin |
| Android SDK | platform 35, build-tools 35.0.0, platform-tools | сборка и установка |
| Gradle | 8.11.1 — ставится обёрткой `gradlew` | — |

Путь к SDK указывается в `local.properties` (файл не в репозитории):

```
sdk.dir=C\:\\Users\\<имя>\\AppData\\Local\\Android\\Sdk
```

Если SDK нет, поставить без Android Studio можно command-line tools:

```bash
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## Общий код и iOS

Ядро, сеть и часть интерфейса лежат в модуле `:shared` (Kotlin Multiplatform,
Compose Multiplatform). Android собирается как обычно, а вот проверить, что общий
код действительно не зацепил платформенных API, можно и на Windows:

```bash
gradlew.bat :shared:compileCommonMainKotlinMetadata
```

Эта сборка компилирует `commonMain` без привязки к платформе и падает на всём,
что доступно только на JVM (например, на `@Volatile` из `kotlin.jvm` — в общем
коде нужен `kotlin.concurrent.Volatile`). Android-сборка такие места пропускает,
поэтому запускать её стоит после каждой правки общего кода.

Цели `iosX64`, `iosArm64` и `iosSimulatorArm64` компилируются только на macOS
с Xcode, поэтому iOS собирается в облаке: workflow `.github/workflows/ios.yml`
на macOS-раннере линкует `Shared.framework`, генерирует Xcode-проект из
`iosApp/project.yml` утилитой XcodeGen, собирает приложение, запускает его
на симуляторе и выкладывает скриншот артефактом. Запустить вручную:
**Actions → iOS → Run workflow**.

На macOS то же самое делается локально:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodegen generate && open Kopeechka.xcodeproj
```

Две грабли, на которые уже наступили: `gradlew` должен быть исполняемым
(`git update-index --chmod=+x gradlew`), а путь поиска фреймворка в Xcode
задаётся через `$(PLATFORM_NAME)` — в `$(SDK_NAME)` есть версия SDK,
и папка не находится.

## Сборка

У приложения два варианта: `full` — со всеми функциями, для GitHub и своего телефона,
и `play` — для Google Play, без чтения СМС (почему — в [PLAY.md](PLAY.md)).

```bash
gradlew.bat :app:assembleFullDebug
```

Готовый файл: `app/build/outputs/apk/full/debug/app-full-debug.apk`.

Установка на телефон с включённой отладкой по USB или по Wi-Fi:

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

Тесты слияния:

```bash
gradlew.bat :app:testFullDebugUnitTest
```

## Известная особенность Windows

На некоторых машинах Gradle падает ещё до компиляции:

```
java.io.IOException: Unable to establish loopback connection
```

Причина не в Gradle: JDK 16+ на Windows создаёт внутренний канал через AF_UNIX-сокет
во временной папке, и в профиле пользователя такие сокеты могут не подключаться
(это ловится и обычным .NET-клиентом, значит дело в окружении, а не в Java).
Лечится короткой папкой в корне диска:

```bash
mkdir C:\gtmp
set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\gtmp -Djava.io.tmpdir=C:\gtmp
gradlew.bat assembleDebug
```

## Release-сборка

Подпись, ключ загрузки и выпуск в Google Play описаны в [PLAY.md](PLAY.md).
Коротко: ключ лежит вне репозитория, путь и пароль — в `keystore.properties`
(в `.gitignore`), сборка для Play — `gradlew.bat :app:bundlePlayRelease`.

SHA-1 ключа, которым подписано приложение у пользователя, нужно добавить
Android-клиентом OAuth в Google Cloud, иначе Диск не авторизуется —
см. [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md).

## Проверка на устройстве

Слияние общего бюджета покрыто тестами (запускаются в CI), остальное проверяется руками. Полезные команды:

```bash
adb logcat -c && adb shell am start -n app.kopeechka.finance/.MainActivity
adb logcat -d | findstr /i "AndroidRuntime FATAL"
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

## Иконка

Эскиз иконки лежит в [`tools/icon-preview.html`](../tools/icon-preview.html) — это тот же
рисунок, что и в `res/drawable/ic_launcher_foreground.xml`, но в SVG и сразу в нескольких
масках. Удобно править форму, глядя в браузер, и лишь потом переносить пути в вектор Android.

## Если сборка падает с «Unable to establish loopback connection»

Симптом: любая команда `gradlew` обрывается сразу, а в `--stacktrace` видно
`java.net.SocketException: Invalid argument: connect` внутри
`sun.nio.ch.PipeImpl$Initializer$LoopbackConnector`.

Это не Gradle и не Java. `Selector.open()` на Windows открывает служебное
соединение через Unix-сокет (`WindowsSelectorImpl` явно просит AF_UNIX), а файл
такого сокета — точка повторного разбора. Если каталог, куда JVM его кладёт,
этого не позволяет, `bind` проходит, файла нет, и `connect` возвращает
«Invalid argument». Обычные сетевые соединения при этом работают, поэтому на
проблему легко подумать на файрвол.

Каталог берётся из `jdk.net.unixdomain.tmpdir`, а по умолчанию — из
`java.io.tmpdir`, то есть `%TEMP%`. Проверить одной программой:

```java
var ssc = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
ssc.bind(null);
SocketChannel.open(ssc.getLocalAddress());   // здесь и падает
```

Лечится переносом сокетов в каталог, где они создаются. Свойство нужно **всем**
процессам сборки — клиенту, демону Gradle и демону Kotlin, — поэтому проще всего
задать его переменной окружения:

```
JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<вы>\.gradle\sockets
```

Класть его в `org.gradle.jvmargs` пользовательского `~/.gradle/gradle.properties`
бесполезно: проектный `gradle.properties` эту строку целиком перекрывает.
Каталог должен существовать. Побочный эффект переменной — строка
«Picked up JAVA_TOOL_OPTIONS» в выводе каждой Java-программы.
