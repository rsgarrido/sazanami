package io.github.rsgarrido.sazanami.viewmodel

import io.github.rsgarrido.sazanami.controller.LibraryController
import io.github.rsgarrido.sazanami.controller.SleepTimerController
import io.github.rsgarrido.sazanami.controller.ListeningAnalyticsController
import io.github.rsgarrido.sazanami.player.MusicPlayer
import io.github.rsgarrido.sazanami.player.PlaybackController
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicViewModelBoundaryTest {
    @Test
    fun controllersArePrivateAndNoPublicApiReturnsImplementationOwners() {
        val forbiddenTypes = setOf(
            PlaybackController::class.java,
            LibraryController::class.java,
            SleepTimerController::class.java,
            ListeningAnalyticsController::class.java,
            MusicPlayer::class.java
        )
        val controllerFields = MusicViewModel::class.java.declaredFields.filter { field ->
            field.type in forbiddenTypes
        }

        assertTrue(controllerFields.isNotEmpty())
        assertTrue(controllerFields.all { field -> Modifier.isPrivate(field.modifiers) })
        assertFalse(
            MusicViewModel::class.java.declaredMethods.any { method ->
                Modifier.isPublic(method.modifiers) && !method.isSynthetic &&
                    method.returnType in forbiddenTypes
            }
        )
        assertFalse(
            MusicViewModel::class.java.declaredMethods.any { method ->
                Modifier.isPublic(method.modifiers) && !method.isSynthetic &&
                    method.returnType.simpleName.endsWith("Repository")
            }
        )
    }
}
