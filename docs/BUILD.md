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

```bash
gradlew.bat assembleDebug
```

Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

Установка на подключённый телефон с включённой отладкой по USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
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

1. Создайте ключ (храните файл и пароль надёжно — без него не выпустить обновление):

```bash
keytool -genkeypair -v -keystore kopeechka-release.jks -alias kopeechka -keyalg RSA -keysize 4096 -validity 10000
```

2. Положите рядом `keystore.properties` (он в `.gitignore`):

```
storeFile=../kopeechka-release.jks
storePassword=…
keyAlias=kopeechka
keyPassword=…
```

3. Добавьте в `app/build.gradle.kts` `signingConfigs` и подключите его к `release`,
   затем соберите `gradlew.bat assembleRelease` или `bundleRelease` для Google Play.

4. SHA-1 нового ключа добавьте вторым Android-клиентом в Google Cloud, иначе
   резервные копии на Диск перестанут авторизоваться — см. [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md).

## Проверка на устройстве

Специальных тестов нет; проверка ручная. Полезные команды:

```bash
adb logcat -c && adb shell am start -n app.kopeechka.finance/.MainActivity
adb logcat -d | findstr /i "AndroidRuntime FATAL"
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

## Иконка

Эскиз иконки лежит в [`tools/icon-preview.html`](../tools/icon-preview.html) — это тот же
рисунок, что и в `res/drawable/ic_launcher_foreground.xml`, но в SVG и сразу в нескольких
масках. Удобно править форму, глядя в браузер, и лишь потом переносить пути в вектор Android.
