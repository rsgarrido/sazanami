package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.readableContentWidth

@Composable
fun AboutScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) { context.installedAppVersion().first }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .readableContentWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSubpageHeader(stringResource(R.string.about_title), onBackClick)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.sazanami_about_icon),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppShellTypography.SectionTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSectionSpacer()
        SettingsSection(
            title = stringResource(R.string.about_details_title),
            description = stringResource(R.string.about_details_summary),
            icon = AppShellIcons.Info
        ) {
            SettingsRow(
                title = stringResource(R.string.about_version_title),
                summary = versionName,
                icon = AppShellIcons.Info
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.about_privacy_title),
                summary = stringResource(R.string.about_privacy_body),
                icon = AppShellIcons.MusicNote,
                summaryMaxLines = 5
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.about_license_title),
                summary = stringResource(R.string.about_license_body),
                icon = AppShellIcons.Info
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.about_repository_title),
                summary = stringResource(R.string.about_repository_summary),
                icon = Icons.Filled.Code,
                onClick = { openSettingsUrl(context, SettingsLinks.REPOSITORY) },
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
