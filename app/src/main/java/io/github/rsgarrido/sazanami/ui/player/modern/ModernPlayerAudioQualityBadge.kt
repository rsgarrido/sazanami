package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityInfo
import io.github.rsgarrido.sazanami.player.audioquality.toDisplayText

@Composable
internal fun ModernPlayerAudioQualityBadge(
    audioQualityInfo: AudioQualityInfo?,
    style: ModernPlayerStyle,
    modifier: Modifier = Modifier
) {
    val displayText = audioQualityInfo?.toDisplayText()
    val badgeShape = RoundedCornerShape(percent = 50)

    AnimatedContent(
        targetState = displayText,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(ModernPlayerDefaults.SongTransitionDurationMillis)
            ).togetherWith(
                fadeOut(animationSpec = tween(140))
            )
        },
        contentKey = { text -> text },
        modifier = modifier,
        label = "modernPlayerAudioQuality"
    ) { displayedText ->
        if (displayedText != null) {
            Box(
                modifier = Modifier
                    .background(
                        color = style.contentColor.copy(alpha = 0.10f),
                        shape = badgeShape
                    )
                    .border(
                        width = 1.dp,
                        color = style.contentColor.copy(alpha = 0.14f),
                        shape = badgeShape
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = displayedText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.15.sp,
                    color = style.contentColor.copy(alpha = 0.82f),
                    maxLines = 1
                )
            }
        }
    }
}
