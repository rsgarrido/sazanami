package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationController
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationUiState

@Composable internal fun FlacVerificationDiagnosticsSection(controller: FlacVerificationController, state: FlacVerificationUiState) {
    if (!BuildConfig.DEBUG) return
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.verify(uri)
    }
    Text(stringResource(R.string.flac_verification_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.flac_verification_note), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { picker.launch(arrayOf("audio/flac", "audio/x-flac", "application/octet-stream")) },
        enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.running) R.string.flac_verification_running else R.string.flac_verification_action))
    }
    state.result?.let { result ->
        fun metadata(name: String) = if (result.count("streamInfoReceived") == 1L) result.counters[name]?.toString() ?: "—" else "—"
        Text(stringResource(R.string.flac_verification_result, result.status.name), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.flac_verification_format, metadata("sampleRate"), metadata("channels"), metadata("bitsPerSample")))
        Text(stringResource(R.string.flac_verification_counts, metadata("totalSamples"),
            result.counters["decodedStereoFrames"]?.toString() ?: "—", result.counters["cleanEndOfStream"]?.toString() ?: "—",
            result.md5Verification))
        Text(stringResource(R.string.flac_verification_md5, metadata("md5ReferenceAvailable")))
        Text(stringResource(R.string.flac_verification_pcm, result.counters["canonicalPcmBytesHashed"]?.toString() ?: "—",
            result.canonicalHash ?: "—"))
    }
    Text(stringResource(R.string.flac_verification_scope), color = MaterialTheme.colorScheme.error)
}
