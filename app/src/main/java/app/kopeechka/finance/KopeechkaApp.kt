package app.kopeechka.finance

import android.app.Application
import app.kopeechka.finance.data.SecureStore
import app.kopeechka.finance.data.Store
import app.kopeechka.finance.ui.theme.initAndroidFonts
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
        initAndroidFonts(this, R.font.barlow_condensed_semibold, R.font.barlow_regular)
        Schedules.ensureChannel(this)
    }
}
