package com.soul.neurokaraoke.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for the Android Auto custom UI. Replaces the default
 * MediaBrowser-template browse experience with our own tabbed layout.
 *
 * Playback still flows through MediaPlaybackService — this service only
 * owns the browse/UI side.
 */
class NeuroKaraokeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NeuroKaraokeCarSession()
}
