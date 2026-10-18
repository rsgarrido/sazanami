package io.github.rsgarrido.sazanami.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.readableContentWidth

@Composable
fun TipsHelpScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .readableContentWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSubpageHeader(stringResource(R.string.tips_title), onBackClick)

        SettingsSection(
            title = stringResource(R.string.tips_playback_category),
            description = stringResource(R.string.tips_playback_category_summary),
            icon = AppShellIcons.MusicNote
        ) {
            HelpTip(R.string.tips_saved_queues_title, R.string.tips_saved_queues_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_play_next_title, R.string.tips_play_next_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_player_album_title, R.string.tips_player_album_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_lyrics_title, R.string.tips_lyrics_body)
        }

        SettingsSectionSpacer()
        SettingsSection(
            title = stringResource(R.string.tips_library_category),
            description = stringResource(R.string.tips_library_category_summary),
            icon = AppShellIcons.AlbumStack
        ) {
            HelpTip(R.string.tips_quick_rate_title, R.string.tips_quick_rate_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_filters_title, R.string.tips_filters_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_smart_playlists_title, R.string.tips_smart_playlists_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_metadata_title, R.string.tips_metadata_body)
        }

        SettingsSectionSpacer()
        SettingsSection(
            title = stringResource(R.string.tips_personalize_category),
            description = stringResource(R.string.tips_personalize_category_summary),
            icon = AppShellIcons.Palette
        ) {
            HelpTip(R.string.tips_my_player_title, R.string.tips_my_player_body)
            SettingsDivider(startPadding = 18.dp)
            HelpTip(R.string.tips_backup_title, R.string.tips_backup_body)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HelpTip(@StringRes title: Int, @StringRes body: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
