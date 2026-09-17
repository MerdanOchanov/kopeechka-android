plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
    // Kotlin для Android встроен в AGP 9 — отдельный плагин kotlin.android больше не нужен
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
