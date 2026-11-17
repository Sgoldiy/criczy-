package app.criczy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.analytics.FieldZone
import app.criczy.core.analytics.ShotPoint
import app.criczy.core.analytics.WagonWheel
import app.criczy.ui.theme.cricketColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Share of the canvas radius taken by the playing area; the rest is the rope and surround. */
private const val FIELD = 0.9f

/** The 30-yard circle, as a share of the boundary distance. */
private const val INNER_CIRCLE = 0.45f

/**
 * A top-down cricket ground with the batter at the centre and the bowler's end up the screen,
 * the same frame as [WagonWheel.point]. [shots] are drawn faintly as context; [focus] is drawn
 * as the ball's flight, replayed every time [focusKey] changes.
 *
 * Only tracked data is drawn: the direction and distance come from the provider's shot angle
 * and magnitude. Shots the provider marks as in the air are drawn on a lofted arc with the
 * ball's shadow on the ground beneath; the height of that arc is illustrative, since the
 * provider records whether the ball was in the air but not how high it went.
 *
 * All animation is read in the draw phase, so a replay redraws the canvas without recomposing.
 */
@Composable
fun CricketGround(
    shots: List<ShotPoint>,
    focus: ShotPoint?,
    focusKey: Any?,
    modifier: Modifier = Modifier,
    showZoneLabels: Boolean = true,
    battingHand: String? = focus?.battingHand,
) {
    val colors = cricketColors
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val flight = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }
    LaunchedEffect(focusKey) {
        if (focus == null) return@LaunchedEffect
        landing.snapTo(0f)
        flight.snapTo(0f)
        // Longer hits take a little longer, as they would on a broadcast replay.
        flight.animateTo(1f, tween((520 + 520 * focus.distance).toInt(), easing = if (focus.inTheAir) LinearOutSlowInEasing else FastOutSlowInEasing))
        landing.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
    }

    val description = focus?.let { "Ground view: ${it.runs} run${if (it.runs == 1) "" else "s"} towards ${it.zone.label.lowercase()}" + if (it.inTheAir) ", in the air" else "" }
        ?: "Ground view of ${shots.size} scoring shots"
    val dark = scheme.surface.red + scheme.surface.green + scheme.surface.blue < 1.5f
    val grass = colors.field
    val grassEdge = lerp(grass, Color.Black, if (dark) 0.35f else 0.12f)
    val stripe = if (dark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.035f)
    val surround = lerp(grass, scheme.surface, 0.55f)
    val rope = if (dark) Color.White.copy(alpha = 0.85f) else Color.White
    val chalk = Color.White.copy(alpha = if (dark) 0.32f else 0.7f)
    val labelColor = if (dark) Color.White.copy(alpha = 0.42f) else Color(0xFF1E3A2B).copy(alpha = 0.55f)
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = labelColor)
    val faint = scheme.onSurface.copy(alpha = if (dark) 0.22f else 0.28f)
    val labels = remember(battingHand, showZoneLabels) {
        if (!showZoneLabels) emptyList() else FieldZone.entries.map { it.label.uppercase() to WagonWheel.screenAngleOf(it, battingHand) }
    }

    androidx.compose.foundation.layout.Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val outer = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = outer * FIELD
                val groundClip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                val stripeWidth = r / 5f
                val measured = labels.map { (text, angle) -> measurer.measure(text, labelStyle) to angle }
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                val grassBrush = Brush.radialGradient(listOf(grass, grass, grassEdge), c, r)
                onDrawBehind {
                    // Surround and boundary rope.
                    drawCircle(surround, outer, c)
                    drawCircle(grassBrush, r, c)
                    clipPath(groundClip) {
                        var x = c.x - r
                        var i = 0
                        while (x < c.x + r) {
                            if (i % 2 == 0) drawRect(stripe, Offset(x, c.y - r), Size(stripeWidth, r * 2))
                            x += stripeWidth; i++
                        }
                    }
                    drawCircle(rope, r, c, style = Stroke(2.5.dp.toPx()))
                    drawCircle(chalk, r * INNER_CIRCLE, c, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                    measured.forEach { (layout, angle) ->
                        val a = Math.toRadians(angle.toDouble())
                        val at = Offset(c.x - cos(a).toFloat() * r * 0.74f, c.y + sin(a).toFloat() * r * 0.74f)
                        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
                    }
                    drawPitch(c, r, colors.pitch, chalk)

                    // Context: earlier scoring shots, quiet enough not to compete with the focus.
                    shots.forEach { s ->
                        if (s === focus) return@forEach
                        val end = Offset(c.x + s.x * r, c.y + s.y * r)
                        val tint = shotColor(s, colors.four, colors.six, colors.wicket, faint)
                        drawLine(tint.copy(alpha = if (s.runs >= 4) 0.45f else tint.alpha), c, end, if (s.runs >= 4) 1.6.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
                        if (s.runs >= 4) drawCircle(tint.copy(alpha = 0.55f), 2.2.dp.toPx(), end)
                    }

                    focus?.let { drawFlight(it, c, r, shotColor(it, colors.four, colors.six, colors.wicket, rope), flight.value, landing.value) }
                    // The batter.
                    drawCircle(Color.Black.copy(alpha = 0.25f), 6.dp.toPx(), c + Offset(0f, 1.5.dp.toPx()))
                    drawCircle(Color.White, 4.5.dp.toPx(), c)
                }
            },
    )
}

private fun shotColor(s: ShotPoint, four: Color, six: Color, wicket: Color, other: Color): Color = when {
    s.isWicket -> wicket
    s.runs == 6 -> six
    s.runs == 4 -> four
    else -> other
}

private fun DrawScope.drawPitch(c: Offset, r: Float, pitch: Color, chalk: Color) {
    val w = r * 0.075f
    val h = r * 0.36f
    // The batter's crease is at the centre; the strip runs up towards the bowler.
    val top = c.y - h
    drawRoundRect(pitch, Offset(c.x - w / 2, top - w * 0.4f), Size(w, h + w * 0.8f), CornerRadius(2.dp.toPx()))
    val crease = Color.White.copy(alpha = 0.85f)
    val line = 1.dp.toPx()
    drawLine(crease, Offset(c.x - w * 0.8f, c.y), Offset(c.x + w * 0.8f, c.y), line)
    drawLine(crease, Offset(c.x - w * 0.8f, top), Offset(c.x + w * 0.8f, top), line)
    // Stumps at the bowler's end.
    drawCircle(chalk, 1.6.dp.toPx(), Offset(c.x, top - w * 0.15f))
}

/** Point on the ball's path at [t]: a straight line along the ground, or a bowed arc if lofted. */
private fun flightPoint(start: Offset, end: Offset, control: Offset?, t: Float): Offset {
    if (control == null) return start + (end - start) * t
    val u = 1 - t
    return Offset(
        u * u * start.x + 2 * u * t * control.x + t * t * end.x,
        u * u * start.y + 2 * u * t * control.y + t * t * end.y,
    )
}

private fun DrawScope.drawFlight(shot: ShotPoint, c: Offset, r: Float, color: Color, t: Float, landed: Float) {
    val end = Offset(c.x + shot.x * r, c.y + shot.y * r)
    val length = hypot(end.x - c.x, end.y - c.y)
    // A lofted ball bows sideways on screen to suggest height; its shadow keeps the true line.
    val control = if (shot.inTheAir && length > 0f) {
        val mid = (c + end) / 2f
        val normal = Offset(-(end.y - c.y) / length, (end.x - c.x) / length)
        val side = if (normal.y > 0) -1f else 1f
        mid + normal * (length * 0.32f * side)
    } else {
        null
    }
    val width = (if (shot.runs >= 4 || shot.isWicket) 3.2f else 2.4f).dp.toPx()

    // Trail behind the ball, brightest at the ball.
    val steps = 28
    var previous = c
    for (i in 1..steps) {
        val s = t * i / steps
        val p = flightPoint(c, end, control, s)
        drawLine(color.copy(alpha = 0.25f + 0.65f * (i.toFloat() / steps)), previous, p, width, cap = StrokeCap.Round)
        previous = p
    }
    if (control != null && t < 1f) {
        // Ground shadow under a lofted ball.
        drawLine(Color.Black.copy(alpha = 0.18f), c, c + (end - c) * t, 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.28f), 3.dp.toPx(), c + (end - c) * t)
    }
    val ball = flightPoint(c, end, control, t)
    val lift = if (control != null) 1f + 0.7f * sin(PI.toFloat() * t) else 1f
    if (t < 1f) {
        drawCircle(color.copy(alpha = 0.3f), 7.dp.toPx() * lift, ball)
        drawCircle(Color.White, 4.dp.toPx() * lift, ball)
        return
    }

    // Landed: a ripple where the ball finished, the rope lit up for a boundary.
    if (landed < 1f) drawCircle(color.copy(alpha = 0.6f * (1f - landed)), (6 + 18 * landed).dp.toPx(), end, style = Stroke(2.dp.toPx()))
    if (shot.isBoundary || shot.runs == 6) {
        val angle = Math.toDegrees(kotlin.math.atan2((end.y - c.y).toDouble(), (end.x - c.x).toDouble())).toFloat()
        drawArc(
            color.copy(alpha = 0.95f - 0.45f * landed), angle - 14f, 28f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    if (shot.isWicket) {
        val k = 5.dp.toPx()
        drawLine(color, end + Offset(-k, -k), end + Offset(k, k), 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, end + Offset(-k, k), end + Offset(k, -k), 2.5.dp.toPx(), cap = StrokeCap.Round)
    } else {
        drawCircle(color, 4.5.dp.toPx(), end)
        drawCircle(Color.White, 1.8.dp.toPx(), end)
    }
}
