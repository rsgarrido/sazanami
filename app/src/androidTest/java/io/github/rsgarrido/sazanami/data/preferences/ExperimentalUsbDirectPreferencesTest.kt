package io.github.rsgarrido.sazanami.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.PlayerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExperimentalUsbDirectPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun freshInstallAndMigratedPreferencesWithoutAcknowledgmentStayDisabled() = runBlocking {
        val suffix = System.nanoTime()
        val legacyName = "usb_opt_in_upgrade_$suffix"
        context.getSharedPreferences(legacyName, Context.MODE_PRIVATE).edit()
            .putString("selected_player_theme", PlayerTheme.RETRO_RACK.id)
            .putBoolean("smooth_play_pause_enabled", false).commit()
        val job = SupervisorJob()
        try {
            val scope = CoroutineScope(job + Dispatchers.IO)
            val fresh = AppPreferencesRepository.create(context, scope,
                "usb_opt_in_fresh_$suffix.preferences_pb", legacyStores = emptyList())
            assertFalse(fresh.state.value.experimentalUsbDirectEnabled)
            assertFalse(fresh.isExperimentalUsbDirectAllowed)
            assertFalse(withTimeout(5_000) { fresh.awaitLoadedState() }.experimentalUsbDirectEnabled)

            val migrated = AppPreferencesRepository.create(context, scope,
                "usb_opt_in_migrated_$suffix.preferences_pb", legacyStores = listOf(legacyName))
            val loaded = withTimeout(5_000) { migrated.awaitLoadedState() }
            assertEquals(PlayerTheme.RETRO_RACK, loaded.selectedPlayerTheme)
            assertFalse(loaded.smoothPlayPauseEnabled)
            assertFalse(loaded.experimentalUsbDirectEnabled)
        } finally { job.cancelAndJoin() }
    }

    @Test fun explicitAcknowledgmentPersistsAcrossRepositoryRestartAndCanBeWithdrawn() = runBlocking {
        val fileName = "usb_opt_in_restart_${System.nanoTime()}.preferences_pb"
        suspend fun open(job: kotlinx.coroutines.Job) = AppPreferencesRepository.create(context,
            CoroutineScope(job + Dispatchers.IO), fileName, legacyStores = emptyList())

        val firstJob = SupervisorJob()
        try {
            val first = open(firstJob)
            assertFalse(withTimeout(5_000) { first.awaitLoadedState() }.experimentalUsbDirectEnabled)
            first.acknowledgeExperimentalUsbDirect()
            assertTrue(withTimeout(5_000) {
                first.state.first { it.experimentalUsbDirectEnabled }
            }.experimentalUsbDirectEnabled)
            assertTrue(withTimeout(5_000) { first.experimentalUsbDirectAccess.first { it } })
            assertTrue(first.isExperimentalUsbDirectAllowed)
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        try {
            val second = open(secondJob)
            assertTrue(withTimeout(5_000) { second.awaitLoadedState() }.experimentalUsbDirectEnabled)
            assertTrue(withTimeout(5_000) { second.experimentalUsbDirectAccess.first { it } })
            val disabling = launch(start = CoroutineStart.UNDISPATCHED) { second.disableExperimentalUsbDirect() }
            assertFalse("Consent must be withdrawn before asynchronous persistence completes", second.isExperimentalUsbDirectAllowed)
            assertFalse(withTimeout(5_000) { second.experimentalUsbDirectAccess.first { !it } })
            disabling.join()
            assertFalse(withTimeout(5_000) {
                second.state.first { !it.experimentalUsbDirectEnabled }
            }.experimentalUsbDirectEnabled)
            second.acknowledgeExperimentalUsbDirect()
            assertTrue(withTimeout(5_000) { second.experimentalUsbDirectAccess.first { it } })
            assertTrue(second.isExperimentalUsbDirectAllowed)
            second.disableExperimentalUsbDirect()
            assertFalse(withTimeout(5_000) { second.experimentalUsbDirectAccess.first { !it } })
        } finally { secondJob.cancelAndJoin() }

        val thirdJob = SupervisorJob()
        try {
            val third = open(thirdJob)
            assertFalse(withTimeout(5_000) { third.awaitLoadedState() }.experimentalUsbDirectEnabled)
        } finally { thirdJob.cancelAndJoin() }
    }
}
