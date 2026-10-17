package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The production controller must deny access before it can inspect or configure any hardware. */
@RunWith(AndroidJUnit4::class)
class ExperimentalUsbDirectAuthorizationTest {
    @Test fun productionControllerAndEngineRejectOptedOutCallsEvenWithAnOptedInAdapter() = runBlocking {
        withContext(Dispatchers.Main) {
            val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? =
                    if (name == Context.USB_SERVICE) null else super.getSystemService(name)
            }
            val controller = UsbAudioController(context, experimentalOptIn = { false })
            try {
                val owner = UsbActivationOwner(1)
                val callbacks = UsbOutputSelectionCallbacks(experimentalOptIn = { true }) { controller }
                assertEquals("DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED", callbacks.authorize(owner, null, 0))
                assertEquals("DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED", controller.authorizeIntegratedSelection(owner, null, 0))
                var publications = 0
                try {
                    controller.runIntegratedFull(Uri.parse("content://test/blocked"), UsbFullPlaybackControl()) { publications++ }
                    fail("The engine entry point must require consent independently of UI selection")
                } catch (failure: UsbIntegratedStartException) {
                    assertEquals("DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED", failure.reason)
                }
                assertEquals(0, publications)
                assertFalse(controller.rollbackIntegratedSelection(owner))
                assertFalse(controller.releaseIntegratedSelection(owner, cleanupSafe = true))
            } finally { controller.close() }
        }
    }
}
