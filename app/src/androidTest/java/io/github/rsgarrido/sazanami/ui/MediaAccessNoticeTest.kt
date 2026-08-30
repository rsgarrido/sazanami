package io.github.rsgarrido.sazanami.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessPolicy
import io.github.rsgarrido.sazanami.mediaaccess.MediaPermissions
import org.junit.Rule
import org.junit.Test

class MediaAccessNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialStateExplainsAndOffersAudioGrant() {
        composeRule.setContent {
            MediaAccessNotice(
                state = state(),
                onRequestAudioAccess = {},
                onRequestArtworkAccess = {},
                onOpenAppSettings = {}
            )
        }
        composeRule.onNodeWithText("Audio access needed").assertIsDisplayed()
        composeRule.onNodeWithText("Grant audio access").assertIsDisplayed()
    }

    @Test
    fun permanentDenialOffersSettingsRecovery() {
        composeRule.setContent {
            MediaAccessNotice(
                state = state(requested = setOf(MediaPermissions.READ_MEDIA_AUDIO)),
                onRequestAudioAccess = {},
                onRequestArtworkAccess = {},
                onOpenAppSettings = {}
            )
        }
        composeRule.onNodeWithText("Open app settings").assertIsDisplayed()
    }

    private fun state(
        granted: Set<String> = emptySet(),
        requested: Set<String> = emptySet(),
        permanentlyDenied: Set<String> = if (requested.isEmpty()) emptySet() else requested
    ) = MediaAccessPolicy.evaluate(
        sdkInt = 33,
        grantedPermissions = granted,
        requestedPermissions = requested,
        permissionsWithRationale = emptySet(),
        permanentlyDeniedPermissions = permanentlyDenied
    )
}
