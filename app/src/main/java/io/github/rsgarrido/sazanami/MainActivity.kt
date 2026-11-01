package io.github.rsgarrido.sazanami

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import io.github.rsgarrido.sazanami.localization.AppLocalePresentationRefresh
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.rsgarrido.sazanami.player.PlaybackService
import io.github.rsgarrido.sazanami.player.androidAutoVoiceItem
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
import io.github.rsgarrido.sazanami.ui.lyrics.LocalLyricsEditor
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsEditorHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.rsgarrido.sazanami.ui.LocalSystemBarSurfaceSetter
import io.github.rsgarrido.sazanami.ui.SystemBarIconAppearanceEffect
import io.github.rsgarrido.sazanami.ui.SystemBarSurface
import io.github.rsgarrido.sazanami.ui.shouldUseDarkSystemBarIcons
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import io.github.rsgarrido.sazanami.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Direct delegate integration preserves ComponentActivity and its Compose/state owners.
    private val appLocaleDelegate by lazy { AppCompatDelegate.create(this, null) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(appLocaleDelegate.attachBaseContext2(newBase))
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        appLocaleDelegate.setTheme(resId)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        appLocaleDelegate.onPostCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        appLocaleDelegate.onStart()
    }

    override fun onPostResume() {
        super.onPostResume()
        appLocaleDelegate.onPostResume()
    }

    override fun onStop() {
        super.onStop()
        appLocaleDelegate.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        appLocaleDelegate.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appLocaleDelegate.onConfigurationChanged(newConfig)
        AppLocalePresentationRefresh.request(this)
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        appLocaleDelegate.setTitle(title)
    }

    override fun setContentView(layoutResID: Int) {
        initializeViewTreeOwners()
        appLocaleDelegate.setContentView(layoutResID)
    }

    override fun setContentView(view: View) {
        initializeViewTreeOwners()
        appLocaleDelegate.setContentView(view)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        initializeViewTreeOwners()
        appLocaleDelegate.setContentView(view, params)
    }

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        initializeViewTreeOwners()
        appLocaleDelegate.addContentView(view, params)
    }

    private var voiceControllerFuture: ListenableFuture<MediaController>? = null

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
        // confirmed core scan will use this same URI when progressive enrichment starts.
        if (musicViewModel.libraryUiState.value.initialFolderSelectionCompleted) {
            musicViewModel.refreshFolderArtwork()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartedAt = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()

        appLocaleDelegate.installViewFactory()
        appLocaleDelegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        // attachBaseContext2 has synchronized compatibility storage before background reads.
        AppLocalePresentationRefresh.request(this)
        if (savedInstanceState == null) handleVoiceIntent(intent)

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
            val appFont by musicViewModel.appFont.collectAsStateWithLifecycle()
            val appAppearance by musicViewModel.appAppearance.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val shellIsDark = appAppearance.isDark(systemIsDark)
            val systemBarSurface = remember { mutableStateOf(SystemBarSurface.SHELL) }
            val setSystemBarSurface = remember {
                { surface: SystemBarSurface -> systemBarSurface.value = surface }
            }
            SystemBarIconAppearanceEffect(
                window = window,
                darkIcons = shouldUseDarkSystemBarIcons(shellIsDark, systemBarSurface.value)
            )
            SazanamiTheme(
                darkTheme = shellIsDark,
                appFont = appFont
            ) {
                val snackbarHostState = remember { SnackbarHostState() }

                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                    LocalSystemBarSurfaceSetter provides setSystemBarSurface,
                    LocalLyricsEditor provides musicViewModel.lyricsEditor
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

                        LyricsEditorHost(musicViewModel.lyricsEditor)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent) {
        val item = intent.androidAutoVoiceItem() ?: return
        val future = voiceControllerFuture ?: MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync().also { voiceControllerFuture = it }
        future.addListener({
            if (isDestroyed || future.isCancelled) return@addListener
            try {
                future.get().apply {
                    setMediaItem(item)
                    prepare()
                    play()
                }
            } catch (error: Exception) {
                if (voiceControllerFuture === future) voiceControllerFuture = null
                MediaController.releaseFuture(future)
                Log.w("SazanamiVoiceSearch", "Voice controller connection failed", error)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        voiceControllerFuture?.let(MediaController::releaseFuture)
        voiceControllerFuture = null
        super.onDestroy()
        appLocaleDelegate.onDestroy()
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
        folderArtworkAccessState = folderArtworkAccessStore.readValidatedState { savedUri ->
            contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == savedUri && permission.isReadPermission
            }
        }
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
