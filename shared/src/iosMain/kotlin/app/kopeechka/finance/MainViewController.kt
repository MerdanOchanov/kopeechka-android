package app.kopeechka.finance

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.kopeechka.finance.ui.KopeechkaRoot
import app.kopeechka.finance.ui.theme.KopeechkaTheme
import platform.UIKit.UIViewController

/**
 * Точка входа iOS: тот же интерфейс, что и на Android, внутри UIViewController.
 * Xcode-проект показывает его как единственный экран приложения.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val storage = remember { IosStorage() }
    val secrets = remember { IosSecrets() }
    val platform = remember { IosPlatform(storage) }
    val vm = remember { AppViewModel(storage, secrets, platform) }

    val data by vm.data.collectAsState()
    KopeechkaTheme(dark = data.settings.dark) {
        KopeechkaRoot(vm, data, onEnableReminder = { vm.setRemind(true) })
    }
}
