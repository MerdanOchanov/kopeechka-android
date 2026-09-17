import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Ключ загрузки для Google Play лежит вне репозитория; без файла собирается всё, кроме подписанного релиза
val uploadKey = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.kopeechka.finance"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kopeechka.finance"
        minSdk = 26
        // Google Play с 2026 года принимает только targetSdk 36 и выше
        targetSdk = 36
        versionCode = 10
        versionName = "1.8"
    }

    signingConfigs {
        if (uploadKey.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    // full — сборка для GitHub со всеми функциями.
    // play — для Google Play: без чтения СМС. Разрешения на SMS Play пропускает
    // только после одобрения отдельной декларации, а без неё выпуск не пройдёт проверку.
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            buildConfigField("boolean", "SMS_ENABLED", "true")
        }
        create("play") {
            dimension = "store"
            // под этим именем приложение заведено в Play Console — поменять его там уже нельзя
            applicationId = "com.arassanusga.kopeechka"
            buildConfigField("boolean", "SMS_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                // Windows-библиотека из зависимостей сервера Ktor: на Android не грузится,
                // а Play из-за неё просит символы отладки нативного кода
                "org/fusesource/jansi/internal/native/**",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Без этого зелёная сборка не отличается от сборки, где тестов не нашлось
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Compose приходит из :shared (Compose Multiplatform), отдельный BOM больше не нужен

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google Sign-In / авторизация для резервных копий в Google Диске
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // HTTP для Диска и ИИ-советника живёт в :shared на Ktor

    // Приём обмена напрямую по Wi-Fi: маленький сервер на одну ручку
    implementation("io.ktor:ktor-server-cio:3.0.3")

    // Тесты слияния: логика общая, но гонять её достаточно на JVM
    testImplementation("junit:junit:4.13.2")
}
