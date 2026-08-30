package io.github.rsgarrido.sazanami.architecture

import io.github.rsgarrido.sazanami.controller.LibraryController
import io.github.rsgarrido.sazanami.controller.SleepTimerController
import io.github.rsgarrido.sazanami.controller.ListeningAnalyticsController
import io.github.rsgarrido.sazanami.player.PlaybackController
import java.lang.reflect.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateFlowArchitectureTest {
    @Test
    fun controllerMutableFlowsArePrivateAndPublicGettersReturnStateFlowInterfaces() {
        val controllers = listOf(
            LibraryController::class.java,
            PlaybackController::class.java,
            SleepTimerController::class.java,
            ListeningAnalyticsController::class.java
        )

        controllers.forEach { controller ->
            val mutableFlowFields = controller.declaredFields.filter { field ->
                MutableStateFlow::class.java.isAssignableFrom(field.type)
            }
            assertTrue("${controller.simpleName} should own MutableStateFlow", mutableFlowFields.isNotEmpty())
            assertTrue(
                "${controller.simpleName} mutable flows must be private",
                mutableFlowFields.all { field -> Modifier.isPrivate(field.modifiers) }
            )
            val publicFlowGetters = controller.declaredMethods.filter { method ->
                Modifier.isPublic(method.modifiers) && !method.isSynthetic &&
                    StateFlow::class.java.isAssignableFrom(method.returnType)
            }
            assertTrue("${controller.simpleName} should expose StateFlow", publicFlowGetters.isNotEmpty())
            assertFalse(
                publicFlowGetters.any { method ->
                    MutableStateFlow::class.java.isAssignableFrom(method.returnType)
                }
            )
        }
    }

    @Test
    fun nonUiControllersDoNotOwnComposeSnapshotState() {
        val controllers = listOf(
            LibraryController::class.java,
            PlaybackController::class.java,
            SleepTimerController::class.java,
            ListeningAnalyticsController::class.java
        )

        controllers.flatMap { it.declaredFields.toList() }.forEach { field ->
            assertFalse(
                "Controller field ${field.name} leaked Compose state",
                field.type.name.startsWith("androidx.compose.runtime")
            )
        }
    }
}
