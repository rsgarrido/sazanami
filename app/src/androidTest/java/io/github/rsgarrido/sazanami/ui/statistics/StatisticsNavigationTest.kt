package io.github.rsgarrido.sazanami.ui.statistics

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatisticsNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun visibilityActivatesAndDeactivatesOnceWithoutRecompositionDuplicates() {
        val visible = mutableStateOf(false)
        val unrelated = mutableIntStateOf(0)
        val calls = mutableListOf<Boolean>()
        composeRule.setContent {
            ListeningAnalyticsVisibilityEffect(visible.value) { calls += it }
            Text(unrelated.intValue.toString())
        }
        composeRule.waitForIdle()
        assertEquals(listOf(false), calls)

        composeRule.runOnIdle { unrelated.intValue++ }
        composeRule.waitForIdle()
        assertEquals(listOf(false), calls)

        composeRule.runOnIdle { visible.value = true }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), calls)

        composeRule.runOnIdle { unrelated.intValue++ }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), calls)

        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true, false), calls)
    }

    @Test
    fun leavingCompositionWhileVisibleDeactivatesAnalyticsObserver() {
        val showEffect = mutableStateOf(true)
        val calls = mutableListOf<Boolean>()
        composeRule.setContent {
            if (showEffect.value) {
                ListeningAnalyticsVisibilityEffect(true) { calls += it }
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(true), calls)

        composeRule.runOnIdle { showEffect.value = false }
        composeRule.waitForIdle()
        assertEquals(listOf(true, false), calls)
    }
}
