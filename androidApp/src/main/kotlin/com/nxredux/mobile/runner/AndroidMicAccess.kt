package com.nxredux.mobile.runner

import com.nxredux.mobile.settings.Prefs
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * [MicAccess] on Android. [prompt] launches the RECORD_AUDIO request. A tap asks when Android would still show the
 * prompt (never asked here or in game ([askedInGame]: `AppSettings.micAsked`), or it would explain first), else opens
 * the app's system settings page, so a permanent deny given in game doesn't make the first tap here do nothing. Never
 * writes `AppSettings.micAsked`: that only gates the in-game ask.
 */
class AndroidMicAccess(private val activity: Activity, private val askedInGame: () -> Boolean = { false }, private val prompt: () -> Unit) : MicAccess {
    override val usable: Boolean
        get() = activity.packageManager.run {
            hasSystemFeature(PackageManager.FEATURE_MICROPHONE) && !hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }

    override fun granted() = activity.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    override fun request() {
        val prefs = Prefs.open(activity, PREFS)
        val asked = prefs.getBoolean(ASKED, false) || askedInGame()
        if (!asked || activity.shouldShowRequestPermissionRationale(PERMISSION)) {
            prefs.edit().putBoolean(ASKED, true).apply()
            prompt()
        } else {
            val page = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
            runCatching { activity.startActivity(page) }
        }
    }

    private companion object {
        const val PERMISSION = android.Manifest.permission.RECORD_AUDIO
        const val PREFS = "mic_access"
        const val ASKED = "asked_from_settings"   // not AppSettings.micAsked: that one gates only the in-game ask
    }
}
