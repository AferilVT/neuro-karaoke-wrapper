package com.soul.neurokaraoke.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the app's cold-start + catalog-browse hot path.
 *
 * Run against a connected API 28+ device/emulator:
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * The resulting ART profile is written under app/src/release/generated/baselineProfiles
 * and merged into release builds automatically via the baselineProfile() dependency.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.neurokaraoke",
        // Also feed the startup profile so the earliest frames are covered.
        includeInStartupProfile = true
    ) {
        // MainActivity is launchMode=singleTask; killing the process each iteration
        // forces a genuine cold start so am start reports a launch (not "brought to front").
        killProcess()
        pressHome()
        startActivityAndWait()

        // The song catalog loads over the network on first launch — wait for a
        // scrollable to appear, then fling it a few times to capture the list /
        // image-loading / romaji-mapping hot paths.
        device.wait(Until.hasObject(By.scrollable(true)), 10_000)
        val list = device.findObject(By.scrollable(true))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            list.fling(Direction.UP)
        } else {
            // Fallback: raw screen swipes if no scrollable is exposed to a11y.
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2, (device.displayHeight * 0.8).toInt(),
                    device.displayWidth / 2, (device.displayHeight * 0.2).toInt(), 12
                )
                device.waitForIdle()
            }
        }
    }
}
