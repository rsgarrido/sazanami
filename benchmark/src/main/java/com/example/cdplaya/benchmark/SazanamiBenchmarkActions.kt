package com.example.cdplaya.benchmark

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal class SazanamiBenchmarkActions(
    private val device: UiDevice = UiDevice.getInstance(
        InstrumentationRegistry.getInstrumentation()
    )
) {
    fun MacrobenchmarkScope.launchAndWaitForShell() {
        grantMediaPermissionsIfNeeded()
        startActivityAndWait()
        waitForAppShell()
    }

    fun selectHome() = clickLabel("Home")

    fun selectLibrary() = clickLabel("Library")

    fun selectSearch() = clickLabel("Search")

    fun openSettings() {
        val settings = device.wait(Until.findObject(By.descContains("Settings")), WAIT_TIMEOUT_MS)
            ?: error("Settings action was not visible")
        settings.click()
        waitForText("Settings")
    }

    fun selectLibrarySection(label: String) = clickLabel(label)

    fun selectExpandedPlayerTheme(displayName: String) {
        openSettings()
        scrollToAndClickLabel("Player Theme")
        clickLabel(displayName)
        device.pressBack()
        waitForAppShell()
    }

    fun selectLibraryView(option: String) {
        val viewOptions = device.wait(
            Until.findObject(By.descStartsWith("View options, currently")),
            WAIT_TIMEOUT_MS
        ) ?: error("Library view options were not visible")
        val desiredState = when (option) {
            "List" -> "currently list"
            else -> "currently grid, ${option.filter(Char::isDigit)} columns"
        }
        if (viewOptions.contentDescription.orEmpty().contains(desiredState, ignoreCase = true)) {
            return
        }
        viewOptions.click()
        clickLabel(option)
    }

    fun openFirstLibraryItem() {
        val artwork = listOf("Artwork for ", "Album art for ")
            .firstNotNullOfOrNull { prefix ->
                device.wait(
                    Until.findObject(By.descStartsWith(prefix)),
                    SHORT_WAIT_TIMEOUT_MS
                )
            } ?: error("No library item artwork was visible")
        artwork.click()
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    fun startPlaybackFromFirstSong() {
        if (device.hasObject(By.descStartsWith("Open player for "))) return
        selectLibrary()
        selectLibrarySection("Songs")
        selectLibraryView("List")
        val firstSongArtwork = device.wait(
            Until.findObject(By.descStartsWith("Album art for ")),
            WAIT_TIMEOUT_MS
        ) ?: error("No playable song was visible; the device library must be non-empty")
        firstSongArtwork.click()
        check(device.wait(Until.hasObject(By.descStartsWith("Open player for ")), WAIT_TIMEOUT_MS)) {
            "Mini player did not appear after selecting the first song"
        }
    }

    fun expandPlayer() {
        val nowPlaying = device.wait(
            Until.findObject(By.descStartsWith("Open player for ")),
            WAIT_TIMEOUT_MS
        ) ?: device.wait(
            Until.findObject(By.descContains("Expand")),
            SHORT_WAIT_TIMEOUT_MS
        ) ?: error("Expanded-player trigger was not visible; start playback first")
        nowPlaying.click()
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    fun collapsePlayer() {
        val collapse = device.wait(
            Until.findObject(By.desc("Collapse player")),
            SHORT_WAIT_TIMEOUT_MS
        )
        if (collapse != null) {
            collapse.click()
        } else {
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(
                width / 2,
                (height * 0.18f).toInt(),
                width / 2,
                (height * 0.88f).toInt(),
                24
            )
        }
        waitForAppShell()
    }

    fun openQueue() = clickDescription("Open up next queue", "Up Next")

    fun clickNextTrack() = clickDescription("Next track", "Next song", "Next")

    fun clickPlayPause() = clickDescription("Play", "Pause")

    fun ensurePlaybackRunning() {
        device.findObject(By.desc("Play"))?.let { play ->
            play.click()
            device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
        }
        check(device.wait(Until.hasObject(By.desc("Pause")), WAIT_TIMEOUT_MS)) {
            "Expanded player did not enter the playing state"
        }
    }

    fun swipeExpandedPlayerLeft() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(
            (width * 0.82f).toInt(),
            (height * 0.46f).toInt(),
            (width * 0.18f).toInt(),
            (height * 0.46f).toInt(),
            20
        )
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    fun keepAnimatedPlayerVisible() {
        // This is the measured sustained-visibility workload, not a synchronization delay.
        // A monotonic five-second window is required because the static control can become
        // idle immediately while the animated themes continue producing frames.
        SystemClock.sleep(SUSTAINED_VISIBILITY_MS)
    }

    fun exerciseStaticPlayerControl() {
        // Classic Wheel has no continuous visualizer, so a completely static window can
        // legitimately render zero frames (which FrameTimingMetric cannot summarize).
        // Pause/resume supplies a bounded control interaction, followed by the same total
        // observation duration used by the animated themes.
        clickPlayPause()
        SystemClock.sleep(STATIC_CONTROL_PAUSE_MS)
        clickPlayPause()
        SystemClock.sleep(
            SUSTAINED_VISIBILITY_MS - STATIC_CONTROL_PAUSE_MS
        )
    }

    fun scrollForward(repetitions: Int = 3) {
        check(device.wait(Until.hasObject(By.scrollable(true)), WAIT_TIMEOUT_MS)) {
            "No scrollable content was visible"
        }
        val scrollable = device.findObjects(By.scrollable(true))
            .maxByOrNull { it.visibleBounds.height() }
            ?: error("No scrollable content was visible")
        val direction = if (scrollable.visibleBounds.height() >= device.displayHeight / 2) {
            Direction.DOWN
        } else {
            Direction.RIGHT
        }
        repeat(repetitions) {
            scrollable.scroll(direction, 0.75f)
        }
    }

    fun enterSearchQuery(query: String) {
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_TIMEOUT_MS)
            ?: error("Search input was not visible")
        input.text = query
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    fun waitForText(text: String) {
        check(device.wait(Until.hasObject(By.text(text)), WAIT_TIMEOUT_MS)) {
            "Timed out waiting for text: $text"
        }
    }

    private fun waitForAppShell() {
        check(device.wait(Until.hasObject(By.desc("Home")), WAIT_TIMEOUT_MS)) {
            "Timed out waiting for the app shell"
        }
    }

    private fun clickLabel(label: String) {
        val target = findLabel(label) ?: listOf(
            By.desc(label),
            By.text(label),
            By.text(label.uppercase())
        ).firstNotNullOfOrNull { selector ->
            device.wait(Until.findObject(selector), SHORT_WAIT_TIMEOUT_MS)
        } ?: error("Timed out waiting for label: $label")
        target.click()
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    private fun scrollToAndClickLabel(label: String) {
        findLabel(label)?.let { target ->
            target.click()
            device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
            return
        }
        val scrollable = device.wait(Until.findObject(By.scrollable(true)), WAIT_TIMEOUT_MS)
            ?: error("No scrollable container was visible while looking for: $label")
        repeat(6) {
            scrollable.scroll(Direction.DOWN, 0.7f)
            findLabel(label)?.let { target ->
                target.click()
                device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
                return
            }
        }
        error("Timed out scrolling to label: $label")
    }

    private fun findLabel(label: String) = listOf(
        By.desc(label),
        By.text(label),
        By.text(label.uppercase())
    ).firstNotNullOfOrNull(device::findObject)

    private fun clickDescription(vararg descriptions: String) {
        val target = descriptions.firstNotNullOfOrNull { description ->
            device.wait(Until.findObject(By.descContains(description)), SHORT_WAIT_TIMEOUT_MS)
        } ?: error("Timed out waiting for one of: ${descriptions.joinToString()}")
        target.click()
        device.waitForIdle(SHORT_WAIT_TIMEOUT_MS)
    }

    private fun grantMediaPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                TARGET_PACKAGE,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            instrumentation.uiAutomation.grantRuntimePermission(
                TARGET_PACKAGE,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(
                    TARGET_PACKAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
        }
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 10_000L
        const val SHORT_WAIT_TIMEOUT_MS = 2_000L
        const val SUSTAINED_VISIBILITY_MS = 5_000L
        const val STATIC_CONTROL_PAUSE_MS = 1_000L
    }
}
