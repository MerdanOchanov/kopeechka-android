package app.kopeechka.finance

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.kopeechka.finance.ui.KopeechkaRoot
import app.kopeechka.finance.ui.theme.KopeechkaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) vm.onAuthResult(r.data) else vm.onAuthCancelled()
    }

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setRemind(true) else vm.flash(vm.l.t("msg.noNotifyPermission"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.authRequests.collect { authLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            }
        }

        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = data.settings.dark
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BackHandler { if (!vm.back()) finish() }
            KopeechkaTheme(dark = dark) {
                KopeechkaRoot(vm, data, onEnableReminder = ::enableReminder)
            }
        }
    }

    private fun enableReminder() {
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.setRemind(true)
    }
}
