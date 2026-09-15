package app.kopeechka.finance.data

import java.util.Locale

actual fun systemLanguage(): String = Locale.getDefault().language
