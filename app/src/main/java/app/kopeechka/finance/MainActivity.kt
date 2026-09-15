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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kopeechka.finance.ui.KopeechkaRoot
import app.kopeechka.finance.ui.theme.KopeechkaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val host by lazy { application as KopeechkaApp }

    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(host.store, host.secure, host.platform) } }
    }

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) host.platform.onAuthResult(r.data) else host.platform.onAuthCancelled()
    }

    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        host.platform.onFileChosen(uri)
    }

    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        host.platform.onFileChosen(uri)
    }

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setRemind(true) else vm.flash(vm.l.t("msg.noNotifyPermission"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.platform.authRequests.collect { authLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.platform.prompts.collect { req ->
                    if (req.kind == "create") {
                        createCsv.launch(req.name)
                    } else {
                        openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
                    }
                }
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
