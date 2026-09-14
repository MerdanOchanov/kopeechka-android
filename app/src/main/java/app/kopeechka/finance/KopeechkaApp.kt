package app.kopeechka.finance

import android.app.Application
import app.kopeechka.finance.data.SecureStore
import app.kopeechka.finance.data.Store
import app.kopeechka.finance.ui.theme.KopeechkaFonts
import app.kopeechka.finance.work.Schedules

class KopeechkaApp : Application() {
    lateinit var store: Store
        private set
    lateinit var secure: SecureStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        secure = SecureStore(this)
        KopeechkaFonts.init(this)
        Schedules.ensureChannel(this)
    }
}
