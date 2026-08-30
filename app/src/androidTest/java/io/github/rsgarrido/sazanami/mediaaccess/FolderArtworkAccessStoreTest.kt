package io.github.rsgarrido.sazanami.mediaaccess

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderArtworkAccessStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPersistedState() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun skippingOptionalAccessCompletesArtworkOnboardingWithoutTreeAccess() {
        val store = FolderArtworkAccessStore(context)

        store.skipOnboarding()
        val state = store.readState()

        assertTrue(state.onboardingComplete)
        assertFalse(state.hasFolderAccess)
        assertNull(state.treeUri)
    }

    @Test
    fun onboardingGrantAndSettingsReadTheSamePersistedTreeState() {
        val selectedTree = Uri.parse("content://documents/tree/primary%3AMusic")
        FolderArtworkAccessStore(context).setTreeUri(selectedTree)

        val settingsState = FolderArtworkAccessStore(context).readState()

        assertTrue(settingsState.onboardingComplete)
        assertTrue(settingsState.hasFolderAccess)
        assertEquals(selectedTree, settingsState.treeUri)
    }

    @Test
    fun restoredTreeWithoutPersistedReadPermissionIsResetToNotGranted() {
        val restoredTree = Uri.parse("content://documents/tree/primary%3AMusic")
        val store = FolderArtworkAccessStore(context)
        store.setTreeUri(restoredTree)

        val validated = store.readValidatedState { false }

        assertFalse(validated.hasFolderAccess)
        assertFalse(validated.onboardingComplete)
        assertNull(store.readState().treeUri)
    }

    @Test
    fun validPersistedReadPermissionKeepsRestoredTreeAccess() {
        val restoredTree = Uri.parse("content://documents/tree/primary%3AMusic")
        val store = FolderArtworkAccessStore(context)
        store.setTreeUri(restoredTree)

        val validated = store.readValidatedState { uri -> uri == restoredTree }

        assertTrue(validated.hasFolderAccess)
        assertTrue(validated.onboardingComplete)
        assertEquals(restoredTree, validated.treeUri)
    }

    private companion object {
        const val PREFERENCES_NAME = "folder_artwork_access"
    }
}
