package com.example.cdplaya.player.equalizer

import androidx.media3.exoplayer.ExoPlayer
import com.example.cdplaya.player.PlaybackService
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EqualizerPlaybackArchitectureTest {
    @Test
    fun playbackServiceDeclaresOnePlayerAndOneEqualizerProcessor() {
        val instanceFields = PlaybackService::class.java.declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }

        assertEquals(
            1,
            instanceFields.count { field ->
                field.type == EqualizerAudioProcessor::class.java
            }
        )
        assertEquals(
            1,
            instanceFields.count { field ->
                field.type == EqualizerDspRuntime::class.java
            }
        )
        assertEquals(
            1,
            instanceFields.count { field ->
                field.type ==
                    AppPreferencesRepository::class.java
            }
        )
        assertEquals(
            1,
            instanceFields.count { field ->
                field.type == ExoPlayer::class.java
            }
        )
    }

    @Test
    fun playbackServiceDeclaresOnePersistedEqualizerCollector() {
        assertEquals(
            1,
            PlaybackService::class.java.declaredMethods.count {
                    method ->
                method.name == "observeEqualizerPreferences"
            }
        )
    }

    @Test
    fun runtimeBridgeOwnsNoPlayerQueueOrPersistenceObjects() {
        val forbiddenTypeFragments = listOf(
            "ExoPlayer",
            "MediaItem",
            "PlaybackQueue",
            "DataStore",
            "Repository",
            "Room"
        )

        EqualizerRuntimeBridge::class.java.declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .forEach { field ->
                forbiddenTypeFragments.forEach { fragment ->
                    assertFalse(
                        "${field.name} exposes $fragment",
                        field.type.name.contains(fragment)
                    )
                }
            }
    }

    @Test
    fun sharedBridgeCannotBeUsedAsAProcessorRuntime() {
        assertFalse(
            EqualizerProcessorRuntime::class.java.isAssignableFrom(
                EqualizerRuntimeBridge::class.java
            )
        )
    }

    @Test
    fun preparedPlanModelsContainNoAndroidTypes() {
        listOf(
            EqualizerRuntimeSnapshot::class.java,
            EqualizerProcessorFormat::class.java,
            EqualizerRuntimeState::class.java,
            PreparedEqualizerPlan::class.java
        ).flatMap { type ->
            type.declaredFields.filterNot { field ->
                Modifier.isStatic(field.modifiers)
            }
        }.forEach { field ->
            assertFalse(field.type.name.startsWith("android."))
            assertFalse(field.type.name.startsWith("androidx.media3."))
        }
    }

}
