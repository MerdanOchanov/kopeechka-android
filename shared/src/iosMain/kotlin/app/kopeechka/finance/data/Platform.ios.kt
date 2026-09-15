package app.kopeechka.finance.data

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun systemLanguage(): String = NSLocale.currentLocale.languageCode
