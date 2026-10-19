package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import android.net.Uri
import androidx.compose.ui.test.onAllNodesWithTag
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.ArtworkViewerEntryTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DefaultPlayerCustomizationPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun landscapeChoiceUsesItsOwnCallbackAndKeepsTheExistingAppearancePreview() {
        val layout = mutableStateOf(ModernLandscapeLayout.BALANCED)
        val selections = mutableListOf<ModernLandscapeLayout>()
        var appearanceCalls = 0
        composeRule.setContent {
            MaterialTheme {
                DefaultPlayerCustomizationScreen(
                    appearance = ModernPlayerAppearance.Default,
                    activeChoice = ModernAppearanceChoice.MINIMAL,
                    landscapeLayout = layout.value,
                    onLandscapeLayoutSelected = { layout.value = it; selections += it },
                    previewSong = null,
                    onChoiceSelected = { appearanceCalls++ },
                    onAppearanceEdited = { appearanceCalls++ },
                    onReset = { appearanceCalls++ },
                    onBackClick = {}
                )
            }
        }
        val wide = composeRule.activity.getString(R.string.modern_option_landscape_wide_waveform)
        composeRule.onNodeWithText(wide).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(ModernLandscapeLayout.WIDE_WAVEFORM), selections)
            assertEquals(0, appearanceCalls)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.modern_landscape_layout_description)).assertExists()
        // The existing preview remains a single compact appearance preview, without a live viewer.
        composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
    }

    @Test
    fun previewArtworkWithARealUriHasNoLiveViewerAction() {
        val song = Song(1, "Preview", "Artist", "Album", 1, 120_000,
            Uri.parse("content://media/1"), "/music/1.flac", "/music", Uri.parse("content://art/1"))
        composeRule.setContent { MaterialTheme { ModernPlayerAppearancePreview(ModernPlayerAppearance.Default, song) } }
        composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
    }

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
        composeRule.onNodeWithText("Sazanami").assertHasNoClickAction()
        composeRule.onNodeWithText("FLAC / 24-bit").assertHasNoClickAction()
    }
}
