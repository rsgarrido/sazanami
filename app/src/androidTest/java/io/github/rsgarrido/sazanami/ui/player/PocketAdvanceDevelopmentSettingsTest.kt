package io.github.rsgarrido.sazanami.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PocketAdvanceDevelopmentSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accessibleChoicesAndSavedStateSurviveCompositionRecreation() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: PocketAdvanceDevelopmentState
        restoration.setContent {
            state = rememberPocketAdvanceDevelopmentState()
            MaterialTheme {
                CompositionLocalProvider(LocalPocketAdvanceDevelopmentState provides state) {
                    PocketAdvanceDevelopmentSettings()
                }
            }
        }
        composeRule.onNodeWithText("Tablet landscape player").performClick()
        composeRule.onNodeWithText("Pocket Advance").performClick()
        lateinit var outgoing: PocketAdvanceDevelopmentState
        composeRule.runOnIdle {
            outgoing = state
            assertTrue(state.enabled)
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertNotSame(outgoing, state)
            assertTrue(state.enabled)
        }
        composeRule.onNodeWithText("Tablet landscape player").performClick()
        composeRule.onNodeWithText("Primary player").performClick()
        composeRule.runOnIdle { assertFalse(state.enabled) }
    }

    @Test
    fun unavailableSelectorExposesNoDevelopmentUiOrNomination() {
        val state = PocketAdvanceDevelopmentState(false, mutableStateOf(true))
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPocketAdvanceDevelopmentState provides state) {
                    PocketAdvanceDevelopmentSettings()
                }
            }
        }
        composeRule.onNodeWithText("Development").assertDoesNotExist()
        composeRule.onNodeWithText("Tablet landscape player").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(state.enabled) }
    }
}
