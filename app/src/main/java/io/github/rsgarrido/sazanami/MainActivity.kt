package io.github.rsgarrido.sazanami

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import android.content.pm.PackageManager
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessEffect
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessPolicy
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessState
import io.github.rsgarrido.sazanami.mediaaccess.MediaPermissionCoordinator
import io.github.rsgarrido.sazanami.mediaaccess.MediaPermissionRequest
import io.github.rsgarrido.sazanami.mediaaccess.FolderArtworkAccessState
import io.github.rsgarrido.sazanami.mediaaccess.FolderArtworkAccessStore
import io.github.rsgarrido.sazanami.mediaaccess.MediaPermissions
import io.github.rsgarrido.sazanami.mediaaccess.PermissionAccess
import io.github.rsgarrido.sazanami.ui.MusicRoute
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import io.github.rsgarrido.sazanami.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var mediaAccessState by mutableStateOf(
        MediaAccessPolicy.evaluate(
            sdkInt = Build.VERSION.SDK_INT,
            grantedPermissions = emptySet(),
            requestedPermissions = emptySet(),
            permissionsWithRationale = emptySet()
        )
    )

    private val musicViewModel: MusicViewModel by viewModels()
    private val permissionCoordinator = MediaPermissionCoordinator()
    private var splashExitReason: SplashExitReason? = null
    private var returningFromAppSettings = false
    private val folderArtworkAccessStore by lazy { FolderArtworkAccessStore(this) }
    private var folderArtworkAccessState by mutableStateOf(FolderArtworkAccessState())

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermanentDenial(
            permission = mediaAccessState.requirements.requiredAudioPermissions.singleOrNull(),
            granted = granted
        )
        permissionCoordinator.finishRequest(MediaPermissionRequest.AUDIO)
        evaluateMediaAccess()
    }

    private val folderArtworkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!persisted) return@registerForActivityResult
        folderArtworkAccessStore.setTreeUri(uri)
        folderArtworkAccessState = folderArtworkAccessStore.readState()
        musicViewModel.setFolderArtworkTreeUri(uri)
        // During first-run folder selection there is no normal library to refresh yet. The
        // confirmed core scan will use this same URI when progressive artwork starts.
        if (musicViewModel.libraryUiState.value.initialFolderSelectionCompleted) {
            musicViewModel.refreshFolderArtwork()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartedAt = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Resolve persisted access and start cache restoration before the first draw. This keeps
        // the splash handoff tied to the same state MusicRoute needs rather than an arbitrary
        // short delay.
        restoreFolderArtworkState()
        musicViewModel.setFolderArtworkTreeUri(folderArtworkAccessState.treeUri)
        evaluateMediaAccess()

        // Normal cold starts stay on the system splash only until the cached library and the
        // appearance/home preferences required by MusicRoute are ready. First-run onboarding is
        // never hidden because audio access is not granted yet. A generous failsafe still lets
        // the real UI surface an unexpected startup error instead of trapping the user here.
        splashScreen.setKeepOnScreenCondition {
            val libraryState = musicViewModel.libraryUiState.value
            val libraryOnboardingReady =
                !libraryState.initialFolderSelectionCompleted &&
                        libraryState.initialFolderDiscoveryCompleted
            val stableFirstFrameReady =
                (libraryOnboardingReady || libraryState.hasPublishedInitialLibraryState) &&
                        musicViewModel.playerAppearanceUiState.value.isLoaded &&
                        musicViewModel.libraryAppearanceUiState.value.isLoaded &&
                        musicViewModel.homeCustomizationUiState.value.isLoaded
            val startupCanStillProgress = libraryState.errorMessage == null
            val elapsedMs = SystemClock.elapsedRealtime() - splashStartedAt

            val exitReason = when {
                !mediaAccessState.hasAudioAccess -> SplashExitReason.NO_AUDIO_ACCESS
                stableFirstFrameReady -> SplashExitReason.READY
                !startupCanStillProgress -> SplashExitReason.ERROR
                elapsedMs >= SPLASH_READY_HOLD_LIMIT_MILLIS -> SplashExitReason.TIMEOUT
                else -> null
            }
            if (exitReason != null) {
                splashExitReason = exitReason
            }
            exitReason == null
        }
        splashScreen.setOnExitAnimationListener { provider ->
            val libraryState = musicViewModel.libraryUiState.value
            debugStartupTiming(
                "splash-exit reason=${splashExitReason ?: SplashExitReason.UNKNOWN} " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - splashStartedAt} " +
                        "songs=${libraryState.songs.size} " +
                        "libraryPublished=${libraryState.hasPublishedInitialLibraryState} " +
                        "playerPrefs=${musicViewModel.playerAppearanceUiState.value.isLoaded} " +
                        "libraryPrefs=${musicViewModel.libraryAppearanceUiState.value.isLoaded} " +
                        "homePrefs=${musicViewModel.homeCustomizationUiState.value.isLoaded}"
            )
            val interpolator = DecelerateInterpolator()
            provider.iconView.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(SPLASH_EXIT_DURATION_MILLIS)
                .setInterpolator(interpolator)
                .start()
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_DURATION_MILLIS)
                .setInterpolator(interpolator)
                .withEndAction { provider.remove() }
                .start()
        }

        val transparentSystemBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        enableEdgeToEdge(
            statusBarStyle = transparentSystemBarStyle,
            navigationBarStyle = transparentSystemBarStyle
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        lifecycleScope.launch {
            musicViewModel.mediaAccessFailures.collect {
                evaluateMediaAccess()
            }
        }

        setContent {
            SazanamiTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        MusicRoute(
                            musicViewModel = musicViewModel,
                            mediaAccessState = mediaAccessState,
                            onRequestAudioAccess = ::requestAudioAccess,
                            onRequestArtworkAccess = {},
                            onOpenAppSettings = ::openAppSettings,
                            folderArtworkAccessState = folderArtworkAccessState,
                            onChooseFolderArtwork = ::chooseFolderArtwork,
                            onSkipFolderArtwork = ::skipFolderArtwork,
                            onClearFolderArtwork = ::clearFolderArtwork,
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.fillMaxSize()
                        )

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (returningFromAppSettings) {
            returningFromAppSettings = false
            clearPermanentDenials()
        }
        evaluateMediaAccess()
    }

    private fun requestAudioAccess() {
        evaluateMediaAccess()
        if (mediaAccessState.hasAudioAccess) return
        if (
            mediaAccessState.audioAccess != PermissionAccess.REQUESTABLE &&
            mediaAccessState.audioAccess != PermissionAccess.DENIED
        ) {
            return
        }
        val permission = mediaAccessState.requirements.requiredAudioPermissions.singleOrNull()
            ?: return
        if (!permissionCoordinator.beginRequest(MediaPermissionRequest.AUDIO)) return
        markPermissionRequested(permission)
        audioPermissionLauncher.launch(permission)
    }


    private fun evaluateMediaAccess() {
        val knownPermissions = setOf(
            MediaPermissions.READ_EXTERNAL_STORAGE,
            MediaPermissions.READ_MEDIA_AUDIO
        )
        val granted = knownPermissions.filterTo(mutableSetOf()) { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
        val requested = knownPermissions.filterTo(mutableSetOf(), ::wasPermissionRequested)
        val withRationale = knownPermissions.filterTo(mutableSetOf()) { permission ->
            shouldShowRequestPermissionRationale(permission)
        }
        val permanentlyDenied =
            knownPermissions.filterTo(mutableSetOf(), ::wasPermanentlyDenied)
        val evaluated = MediaAccessPolicy.evaluate(
            sdkInt = Build.VERSION.SDK_INT,
            grantedPermissions = granted,
            requestedPermissions = requested,
            permissionsWithRationale = withRationale,
            permanentlyDeniedPermissions = permanentlyDenied
        )
        mediaAccessState = evaluated
        permissionCoordinator.onStateEvaluated(evaluated).forEach { effect ->
            when (effect) {
                MediaAccessEffect.LOAD_LIBRARY -> musicViewModel.onMediaAccessGranted()
                MediaAccessEffect.REVOKE_LIBRARY_ACCESS ->
                    musicViewModel.onMediaAccessRevoked()
            }
        }
    }

    private fun restoreFolderArtworkState() {
        var state = folderArtworkAccessStore.readState()
        val savedUri = state.treeUri
        if (savedUri != null) {
            val stillGranted = contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == savedUri && permission.isReadPermission
            }
            if (!stillGranted) {
                folderArtworkAccessStore.clearTreeUri()
                state = folderArtworkAccessStore.readState()
            }
        }
        folderArtworkAccessState = state
    }

    private fun chooseFolderArtwork() {
        folderArtworkLauncher.launch(folderArtworkAccessState.treeUri)
    }

    private fun skipFolderArtwork() {
        folderArtworkAccessStore.skipOnboarding()
        folderArtworkAccessState = folderArtworkAccessStore.readState()
    }

    private fun clearFolderArtwork() {
        folderArtworkAccessState.treeUri?.let { uri ->
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        folderArtworkAccessStore.clearTreeUri()
        folderArtworkAccessState = folderArtworkAccessStore.readState()
        musicViewModel.setFolderArtworkTreeUri(null)
        musicViewModel.refreshFolderArtwork()
    }

    private fun markPermissionRequested(permission: String) {
        permissionPreferences.edit().putBoolean(permission, true).apply()
    }

    private fun wasPermissionRequested(permission: String): Boolean {
        return permissionPreferences.getBoolean(permission, false)
    }

    private fun updatePermanentDenial(permission: String?, granted: Boolean) {
        if (permission == null) return
        val permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(permission)
        permissionPreferences.edit()
            .putBoolean(permanentDenialKey(permission), permanentlyDenied)
            .apply()
    }

    private fun wasPermanentlyDenied(permission: String): Boolean {
        return permissionPreferences.getBoolean(permanentDenialKey(permission), false)
    }

    private fun clearPermanentDenials() {
        val editor = permissionPreferences.edit()
        setOf(
            MediaPermissions.READ_EXTERNAL_STORAGE,
            MediaPermissions.READ_MEDIA_AUDIO
        ).forEach { permission ->
            editor.putBoolean(permanentDenialKey(permission), false)
        }
        editor.apply()
    }

    private fun permanentDenialKey(permission: String) = "permanently_denied:$permission"

    private val permissionPreferences by lazy {
        getSharedPreferences("media_access_permissions", MODE_PRIVATE)
    }

    private fun openAppSettings() {
        returningFromAppSettings = true
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    override fun onPause() {
        super.onPause()
        musicViewModel.savePlayerState()
    }

    private fun debugStartupTiming(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(STARTUP_TIMING_TAG, message)
        }
    }

    private enum class SplashExitReason {
        READY,
        ERROR,
        TIMEOUT,
        NO_AUDIO_ACCESS,
        UNKNOWN
    }

    private companion object {
        const val STARTUP_TIMING_TAG = "StartupTiming"
        const val SPLASH_READY_HOLD_LIMIT_MILLIS = 3_000L
        const val SPLASH_EXIT_DURATION_MILLIS = 180L
    }
}
