package com.pulsefin.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's Baseline Profile: AOT-compiles the startup path plus the Songs-list
 * scroll so the first fling after a cold start doesn't pay JIT cost on low-end devices.
 *
 * Run manually against a connected device/emulator:
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * Login caveat: the app gates content behind a Jellyfin login. For the profile to cover the
 * populated-list render path (not just startup), generate it on a device that is already
 * logged in with library data. If the login screen is showing instead, no scrollable list is
 * found and we simply skip the fling — startup/navigation classes are still captured.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.pulsefin.app") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Fling the Songs list a few times each way if it's on screen (i.e. we're logged in).
        val list = device.wait(Until.findObject(By.scrollable(true)), 5_000)
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
