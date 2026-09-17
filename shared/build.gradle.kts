plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 не сочетает kotlin.multiplatform с com.android.library — нужен свой плагин
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "app.kopeechka.finance.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Apple-цели собираются только на macOS; на Windows они просто не участвуют в сборке.
    // iosX64 (симулятор на Intel) Compose Multiplatform 1.12 больше не поддерживает.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        // Clock и Instant живут в kotlin.time с kotlinx-datetime 0.7
        all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("io.ktor:ktor-client-core:3.0.3")

            // интерфейс общий для обеих платформ
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }
    }
}

