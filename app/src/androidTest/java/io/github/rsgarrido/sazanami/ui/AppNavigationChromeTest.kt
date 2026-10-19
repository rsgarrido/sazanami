package io.github.rsgarrido.sazanami.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class AppNavigationChromeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bothPresentationsDispatchIdenticalDestinationsAndKeepNavigationAndScrollState() {
        val width = mutableStateOf(1280.dp)
        val actions = mutableListOf<MainDestination>()
        lateinit var navigation: MusicNavigationState
        lateinit var scroll: LazyListState
        composeRule.setContent {
            navigation = rememberMusicNavigationState()
            scroll = rememberLazyListState()
            val layout = resolveMusicShellLayout(width.value, 800.dp)
            SazanamiTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().musicContentPane(layout)) {
                        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("list")) {
                            items(100) { Text("Item $it", Modifier.height(48.dp)) }
                        }
                    }
                    AppNavigationChrome(layout, true, navigation.mainDestination.value) {
                        actions += it
                        navigation.mainDestination.value = it
                    }
                }
            }
        }
        val owner = navigation
        val scrollOwner = scroll
        composeRule.onNodeWithTag("list").performScrollToIndex(40)
        composeRule.runOnIdle {
            navigation.mainDestination.value = MainDestination.SEARCH
            navigation.selectedLibraryTab.value = LibraryTab.ALBUMS
            navigation.searchQuery.value = "retained query"
        }
        for (windowWidth in listOf(412.dp, 1280.dp, 412.dp)) {
            composeRule.runOnIdle { width.value = windowWidth }
            composeRule.onNodeWithContentDescription(label(MainDestination.SEARCH)).assertIsSelected()
            composeRule.runOnIdle {
                assertSame(owner, navigation)
                assertSame(scrollOwner, scroll)
                assertEquals(40, scroll.firstVisibleItemIndex)
                assertEquals(LibraryTab.ALBUMS, navigation.selectedLibraryTab.value)
                assertEquals("retained query", navigation.searchQuery.value)
                assertEquals(emptyList<MainDestination>(), actions)
            }
        }
        for (windowWidth in listOf(1280.dp, 412.dp)) {
            composeRule.runOnIdle { width.value = windowWidth }
            for (destination in MainDestination.entries) {
                composeRule.onNodeWithContentDescription(label(destination)).performClick().assertIsSelected()
            }
        }
        composeRule.runOnIdle {
            assertEquals(MainDestination.entries.toList() + MainDestination.entries.toList(), actions)
        }
    }

    private fun label(destination: MainDestination) = composeRule.activity.getString(when (destination) {
        MainDestination.HOME -> R.string.navigation_home
        MainDestination.LIBRARY -> R.string.navigation_library
        MainDestination.SEARCH -> R.string.navigation_search
    })
}
