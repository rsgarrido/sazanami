package io.github.rsgarrido.sazanami.player.equalizer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class EqualizerRenderersFactoryTest {
    @Test
    fun factoryBuildsSinkWithPersistentProcessorInstance() {
        val context = ApplicationProvider
            .getApplicationContext<Context>()
        val processor = EqualizerAudioProcessor()
        val factory = EqualizerRenderersFactory(
            context = context,
            equalizerAudioProcessor = processor
        )

        val sink = factory.buildAudioSinkForTest(context)

        assertNotNull(sink)
        assertSame(processor, factory.processorInstanceForTest())
        sink.reset()
    }
}
