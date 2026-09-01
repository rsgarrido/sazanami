package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import org.junit.Rule
import org.junit.Test

class DefaultPlayerCustomizationPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewContainsExactlyOneQueueAction() {
        composeRule.setContent {
            MaterialTheme {
                ModernPlayerAppearancePreview(
                    appearance = ModernPlayerAppearance.Default,
                    previewSong = null
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Open queues").assertCountEquals(1)
    }
}
