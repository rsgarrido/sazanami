package com.example.cdplaya

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import android.provider.Settings
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
import com.example.cdplaya.mediaaccess.MediaAccessEffect
import com.example.cdplaya.mediaaccess.MediaAccessPolicy
import com.example.cdplaya.mediaaccess.MediaAccessState
import com.example.cdplaya.mediaaccess.MediaPermissionCoordinator
import com.example.cdplaya.mediaaccess.MediaPermissionRequest
import com.example.cdplaya.mediaaccess.FolderArtworkAccessState
import com.example.cdplaya.mediaaccess.FolderArtworkAccessStore
import com.example.cdplaya.mediaaccess.MediaPermissions
import com.example.cdplaya.mediaaccess.PermissionAccess
import com.example.cdplaya.ui.MusicRoute
import com.example.cdplaya.ui.theme.CdplayaTheme
import com.example.cdplaya.viewmodel.MusicViewModel
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
        musicViewModel.refreshFolderArtwork()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartedAt = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Hide only the brief, incomplete normal-launch frame while the cached library is
        // restored. First-run onboarding is never held behind the splash, and the timeout
        // guarantees that a slow/failed restore cannot trap the user on the launch screen.
        splashScreen.setKeepOnScreenCondition {
            mediaAccessState.hasAudioAccess &&
                    !musicViewModel.libraryUiState.value.hasPublishedInitialLibraryState &&
                    SystemClock.elapsedRealtime() - splashStartedAt < SPLASH_CACHE_HOLD_LIMIT_MILLIS
        }
        splashScreen.setOnExitAnimationListener { provider ->
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

        restoreFolderArtworkState()
        musicViewModel.setFolderArtworkTreeUri(folderArtworkAccessState.treeUri)
        evaluateMediaAccess()
        lifecycleScope.launch {
            musicViewModel.mediaAccessFailures.collect {
                evaluateMediaAccess()
            }
        }

        setContent {
            CdplayaTheme {
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

    private companion object {
        const val SPLASH_CACHE_HOLD_LIMIT_MILLIS = 800L
        const val SPLASH_EXIT_DURATION_MILLIS = 180L
    }
}
