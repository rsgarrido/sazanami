package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Material3 gestures and production adapter, fake seek sink; no USB or decoder execution. Instrumentation only. */
class DirectSeekSliderGestureTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private var authority=DirectScrubAuthority(true,1,0,true)
    private val style=mutableStateOf(ModernSeekbarStyle.WAVEFORM_PREVIEW)
    private val requests=mutableListOf<Int>()
    private lateinit var environment: DirectSeekGestureEnvironment
    private fun content(customPositionBar: Boolean=false) {
        compose.setContent {
            val preview=remember {mutableStateOf<Int?>(null)}
            environment=remember {DirectSeekGestureEnvironment({authority}) {preview.value=it}}
            MaterialTheme {
                CompositionLocalProvider(LocalDirectSeekGestureEnvironment provides environment) {
                    Box(Modifier.width(360.dp)) {
                        val seek: (Int)->Unit={requests+=it;authority=authority.copy(seekSerial=authority.seekSerial+1)}
                        if(customPositionBar) {
                            val control=rememberDirectSeekGestureControl(seek)
                            Box(Modifier.width(360.dp).height(48.dp).testTag("customSeek").directSeekPositionInput(control,180000))
                        } else ModernPlayerSeekBar(currentPosition=preview.value ?: 0,duration=180000,
                            onSeekChange=seek,appearance=ModernPlayerAppearance.Default.seekbar.copy(style=style.value),
                            waveformSeed="gesture-test",style=ModernPlayerDefaults.style())
                    }
                }
            }
        }
    }
    private fun slider()=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
    @Test fun realWaveformAndClassicDragCommitOnlyTheFinalValueOncePerGesture() {
        content()
        for(value in listOf(ModernSeekbarStyle.WAVEFORM_PREVIEW,ModernSeekbarStyle.CLASSIC_BAR,ModernSeekbarStyle.WAVEFORM_PEAKS)) {
            compose.runOnIdle {style.value=value;requests.clear()}
            slider().performTouchInput {down(Offset(width*.2f,centerY));moveTo(Offset(width*.4f,centerY))}
            compose.runOnIdle {assertTrue("Intermediate drag dispatched a seek",requests.isEmpty())}
            // No idle boundary between the final delta and up: Material3 may still have a queued value callback.
            slider().performTouchInput {moveTo(Offset(width*.8f,centerY));up()}
            compose.runOnIdle {
                assertEquals(1,requests.size)
                assertTrue("The final target must follow the last move, not the earlier 40% preview",requests.single() in 126000..162000)
            }
        }
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun independentTapsAccessibilityAndKeyboardReachTheSinkOnceEach() {
        content()
        // Adjacent taps are injected together; the second must not overwrite an unfinished first interaction.
        slider().performTouchInput {click(Offset(width*.3f,centerY));click(Offset(width*.6f,centerY))}
        compose.runOnIdle {assertEquals(2,requests.size)}
        slider().performSemanticsAction(SemanticsActions.SetProgress) {it(90000f)}
        compose.runOnIdle {assertEquals(3,requests.size);assertEquals(90000,requests.last())}
        slider().performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        slider().performKeyInput {pressKey(Key.DirectionRight)}
        compose.runOnIdle {assertEquals(4,requests.size)}
    }
    @Test fun cancellationAndNewGenerationDoNotCommitOldTouchButNextTapStillWorks() {
        content()
        slider().performTouchInput {down(center);moveTo(Offset(width*.6f,centerY));cancel()}
        compose.runOnIdle {assertTrue(requests.isEmpty())}
        slider().performTouchInput {down(center);moveTo(Offset(width*.7f,centerY))}
        compose.runOnIdle {authority=authority.copy(generation=2);environment.observe()}
        slider().performTouchInput {moveTo(Offset(width*.8f,centerY));up()}
        compose.runOnIdle {assertTrue(requests.isEmpty())}
        slider().performTouchInput {click(Offset(width*.5f,centerY))}
        compose.runOnIdle {assertEquals(1,requests.size)}
    }
    @Test fun twoFingersCancelWithoutACommitAndModeDisableDoesNotLeakIntoNormalPlayback() {
        content()
        slider().performTouchInput {
            down(0,Offset(width*.3f,centerY));moveTo(0,Offset(width*.4f,centerY))
            down(1,Offset(width*.7f,centerY));moveTo(0,Offset(width*.5f,centerY));up(1);up(0)
        }
        compose.runOnIdle {assertTrue(requests.isEmpty())}
        slider().performTouchInput {down(center);moveTo(Offset(width*.6f,centerY))}
        compose.runOnIdle {authority=authority.copy(enabled=false);environment.observe()}
        slider().performTouchInput {moveTo(Offset(width*.8f,centerY));up()}
        compose.runOnIdle {assertTrue(requests.isEmpty())}
        slider().performTouchInput {click(Offset(width*.5f,centerY))}
        compose.runOnIdle {assertEquals(1,requests.size)}
    }
    @Test fun sharedFlipRackCassettePositionHandlerCommitsOnceAndRetainsNormalImmediateChanges() {
        content(customPositionBar=true)
        val bar=compose.onNodeWithTag("customSeek")
        bar.performTouchInput {down(Offset(width*.2f,centerY));moveTo(Offset(width*.4f,centerY))}
        compose.runOnIdle {assertTrue(requests.isEmpty())}
        bar.performTouchInput {moveTo(Offset(width*.8f,centerY));up()}
        compose.runOnIdle {assertEquals(1,requests.size);assertTrue(requests.single() in 143990..144010)}
        compose.runOnIdle {authority=authority.copy(enabled=false);environment.observe();requests.clear()}
        bar.performTouchInput {down(Offset(width*.2f,centerY));moveTo(Offset(width*.4f,centerY))}
        compose.runOnIdle {assertTrue(requests.size>=2)}
        bar.performTouchInput {up()}
        compose.runOnIdle {assertTrue(requests.size>=2)}
    }
}
