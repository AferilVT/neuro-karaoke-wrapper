package com.soul.neurokaraoke.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Pure decision so it can be unit-tested without a Context. */
fun isTelevision(uiModeType: Int, hasLeanback: Boolean): Boolean =
    uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanback

/** True when the app is running on an Android TV / Google TV device. */
fun Context.isTelevision(): Boolean {
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType ?: Configuration.UI_MODE_TYPE_UNDEFINED
    val leanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
    return isTelevision(uiMode, leanback)
}
