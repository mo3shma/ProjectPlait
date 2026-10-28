package app.plait

import android.app.Application
import android.content.res.Configuration
import app.plait.i18n.AppLocale

/** Sets the app's language up before any screen, service, receiver or widget runs, so they all speak the same one. */
class PlaitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocale.init(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.refresh(this, newConfig.locales)
    }
}
