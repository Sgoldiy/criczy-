package app.criczy.feature.match

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.model.MatchDetail
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val ScoreEventType.label: String
    get() = when (this) {
        ScoreEventType.FOUR -> "FOUR"
        ScoreEventType.SIX -> "SIX"
        ScoreEventType.WICKET -> "WICKET"
        ScoreEventType.WIDE -> "WIDE"
        ScoreEventType.NO_BALL -> "NO BALL"
    }

private val ScoreEventType.isMinor: Boolean get() = this == ScoreEventType.WIDE || this == ScoreEventType.NO_BALL

/** One line under the headline, built only from the delivery the event came from. */
internal fun eventSubtitle(event: ScoreEvent, detail: MatchDetail?): String? {
    val ball = event.ball ?: return null
    val batter = detail?.player(ball.batterId)?.name
    return when (event.type) {
        ScoreEventType.WICKET -> listOfNotNull(batter, ball.dismissalType?.lowercase()).joinToString(" · ").ifEmpty { null }
        ScoreEventType.FOUR, ScoreEventType.SIX -> listOfNotNull(batter, ball.fieldingPosition?.lowercase()?.let { "to $it" }).joinToString(" ").ifEmpty { null }
        ScoreEventType.WIDE, ScoreEventType.NO_BALL -> "+${ball.teamRuns} run${if (ball.teamRuns == 1) "" else "s"}"
    }
}

/**
 * Celebrates a new event over the match centre. Each type moves differently: a four sweeps
 * across, a six bursts up, a wicket lands with a jolt and a red flash, and wides and no-balls
 * drop in as a small banner that does not cover the score. Nothing loops; each run is a few
 * hundred milliseconds of animation and then the overlay is gone.
 */
@Composable
internal fun ScoreEventOverlay(event: ScoreEvent?, detail: MatchDetail?, modifier: Modifier = Modifier) {
    // Remembered across tab switches and returning to the screen, so an event plays once.
    var handled by rememberSaveable { mutableLongStateOf(event?.id ?: Long.MIN_VALUE) }
    var shown by remember { mutableStateOf<ScoreEvent?>(null) }
    LaunchedEffect(event?.id) {
        if (event != null && event.id != handled) {
            handled = event.id
            shown = event
        }
    }
    val current = shown ?: return
    val colors = cricketColors
    val color = when (current.type) {
        ScoreEventType.FOUR -> colors.four
        ScoreEventType.SIX -> colors.six
        ScoreEventType.WICKET -> colors.wicket
        ScoreEventType.WIDE, ScoreEventType.NO_BALL -> colors.extra
    }
    val subtitle = eventSubtitle(current, detail)

    val enter = remember(current.id) { Animatable(0f) }
    val burst = remember(current.id) { Animatable(0f) }
    val shake = remember(current.id) { Animatable(0f) }
    LaunchedEffect(current.id) {
        coroutineScope {
            listOf(
                async {
                    enter.animateTo(1f, spring(if (current.type == ScoreEventType.SIX) Spring.DampingRatioMediumBouncy else Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                },
                async { burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) },
                async {
                    if (current.type == ScoreEventType.WICKET) {
                        shake.animateTo(0f, keyframes { durationMillis = 420; 0f at 0; -14f at 60; 12f at 120; -8f at 190; 5f at 260; -2f at 330; 0f at 420 })
                    }
                },
            ).awaitAll()
        }
        delay(if (current.type.isMinor) 900L else 1300L)
        enter.animateTo(0f, tween(220))
        shown = null
    }

    Box(modifier.fillMaxSize(), contentAlignment = if (current.type.isMinor) Alignment.TopCenter else Alignment.Center) {
        if (current.type == ScoreEventType.WICKET) {
            // A brief red vignette, strongest at the edges.
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = (1f - burst.value) * 0.55f }
                    .background(Brush.radialGradient(listOf(Color.Transparent, color.copy(alpha = 0.7f)), radius = 1400f)),
            )
        }
        if (current.type == ScoreEventType.SIX || current.type == ScoreEventType.FOUR) {
            Canvas(Modifier.size(320.dp)) {
                val t = burst.value
                val rays = if (current.type == ScoreEventType.SIX) 14 else 8
                for (i in 0 until rays) {
                    val a = Math.toRadians(360.0 * i / rays + 8)
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    val inner = size.minDimension * (0.18f + 0.28f * t)
                    val outer = inner + size.minDimension * 0.12f * (1f - t)
                    drawLine(color.copy(alpha = (1f - t) * 0.9f), center + dir * inner, center + dir * outer, 5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Column(
            Modifier
                .padding(top = if (current.type.isMinor) 120.dp else 0.dp)
                .graphicsLayer {
                    val e = enter.value
                    alpha = e.coerceIn(0f, 1f)
                    when (current.type) {
                        ScoreEventType.FOUR -> { translationX = (1f - e) * -size.width; scaleX = 0.9f + 0.1f * e; scaleY = scaleX }
                        ScoreEventType.SIX -> { scaleX = 0.3f + 0.7f * e; scaleY = scaleX; translationY = (1f - burst.value) * 40.dp.toPx() - 20.dp.toPx() * burst.value }
                        ScoreEventType.WICKET -> { scaleX = 1.6f - 0.6f * e; scaleY = scaleX; translationX = shake.value.dp.toPx() }
                        else -> translationY = (1f - e) * -60.dp.toPx()
                    }
                }
                .clip(if (current.type.isMinor) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large)
                .background(color)
                .padding(horizontal = if (current.type.isMinor) 22.dp else 36.dp, vertical = if (current.type.isMinor) 8.dp else 14.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = listOfNotNull(current.type.label.lowercase(), subtitle).joinToString(", ")
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                current.type.label, color = Color.White, fontFamily = ScoreFont, fontWeight = FontWeight.Bold,
                fontSize = if (current.type.isMinor) 24.sp else 56.sp, textAlign = TextAlign.Center,
            )
            subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
