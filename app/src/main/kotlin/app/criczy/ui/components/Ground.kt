package app.criczy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import app.criczy.core.analytics.WagonWheel
import app.criczy.core.model.Ball
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

enum class BallStoryKind { FOUR, SIX, WICKET, RUNS, EXTRA, DOT }

/**
 * What happened on one delivery, in words a viewer would use: a headline such as SINGLE,
 * FOUR, WICKET or DEFENDED, an optional detail, and whether an arrow shows where the ball went.
 * Everything comes from what the scorers recorded for the ball.
 */
data class BallStory(val headline: String, val detail: String?, val kind: BallStoryKind, val showsShot: Boolean)

private fun runsName(runs: Int): String = when (runs) {
    1 -> "SINGLE"
    2 -> "DOUBLE"
    3 -> "TRIPLE"
    else -> "$runs RUNS"
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

fun ballStory(ball: Ball): BallStory {
    val tracked = ball.shotAngle != null
    val where = ball.fieldingPosition?.lowercase()
    val toWhere = where?.let { "to $it" }
    return when {
        ball.isWicket -> BallStory("WICKET", listOfNotNull(ball.dismissalType, where).joinToString(" · ").ifEmpty { null }, BallStoryKind.WICKET, tracked && ball.dismissalType?.contains("caught", ignoreCase = true) == true)
        ball.isWide -> BallStory("WIDE", "+" + plural(ball.teamRuns, "run"), BallStoryKind.EXTRA, false)
        ball.isNoBall -> BallStory(
            "NO BALL",
            if (ball.batterRuns > 0) "+ ${if (ball.batterRuns == 6) "SIX" else if (ball.batterRuns == 4 && ball.isBoundary) "FOUR" else runsName(ball.batterRuns)}" else "+" + plural(ball.teamRuns, "run"),
            BallStoryKind.EXTRA, tracked && ball.batterRuns > 0,
        )
        ball.batterRuns == 6 -> BallStory("SIX", toWhere, BallStoryKind.SIX, tracked)
        ball.batterRuns == 4 && ball.isBoundary -> BallStory("FOUR", toWhere, BallStoryKind.FOUR, tracked)
        ball.batterRuns > 0 -> BallStory(runsName(ball.batterRuns), toWhere, BallStoryKind.RUNS, tracked)
        (ball.isLegBye || ball.isBye) && ball.teamRuns > 0 ->
            BallStory(if (ball.isLegBye) (if (ball.teamRuns == 1) "LEG BYE" else "${ball.teamRuns} LEG BYES") else (if (ball.teamRuns == 1) "BYE" else "${ball.teamRuns} BYES"), null, BallStoryKind.EXTRA, false)
        else -> dotStory(ball, where)
    }
}

/** A ball with no runs: what the batter did with it, so an empty ground still tells the story. */
private fun dotStory(ball: Ball, where: String?): BallStory {
    val connection = ball.connection?.lowercase().orEmpty()
    val shot = ball.shot?.lowercase().orEmpty()
    val headline = when {
        connection == "missed" || "play and miss" in connection -> "MISSED"
        connection == "left" || connection == "shoulders arms" || shot == "leave" || shot == "shoulders arms" -> "LEFT ALONE"
        "pad" in connection -> "HIT THE PAD"
        connection == "hit body" || connection == "hit helmet" -> "HIT THE BODY"
        "edge" in connection || connection == "gloved" || connection == "spliced" -> "EDGED"
        "defensive" in shot || shot in setOf("pushed", "fended", "dropped") -> "DEFENDED"
        where != null -> "FIELDED"
        else -> "DOT BALL"
    }
    val detail = when (headline) {
        "MISSED" -> ball.shot?.let { "played a ${it.lowercase()}" }
        "FIELDED", "DEFENDED", "EDGED" -> where?.let { "to $it" }
        else -> null
    }
    return BallStory(headline, detail, BallStoryKind.DOT, false)
}

/** Share of the canvas radius taken by the playing area; the rest is the surround. */
private const val FIELD = 0.9f

/** Pitch length as a share of the field radius. */
private const val PITCH = 0.36f

/**
 * One delivery on a top-down ground, seen from behind the bowler as on a TV broadcast: the
 * batter stands at the upper end of the pitch and the bowler runs in from below, so a
 * right-hander's off side is on the left. Runs are an arrow from the batter in the recorded
 * direction (dashed when the ball went in the air) with a label at its tip; a ball with no
 * runs leaves the ground empty and says what the batter did.
 *
 * When [animate] is true the arrow grows from the bat and the label pops in; otherwise the
 * ball is drawn as it finished. Animation is read in the draw phase only.
 */
@Composable
fun BallGround(ball: Ball, modifier: Modifier = Modifier, animate: Boolean = false) {
    val colors = cricketColors
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val story = remember(ball) { ballStory(ball) }
    val shot = remember(ball) { if (story.showsShot) WagonWheel.point(ball) else null }
    val grow = remember(ball) { Animatable(if (animate) 0f else 1f) }
    val pop = remember(ball) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(ball, animate) {
        if (!animate) return@LaunchedEffect
        if (shot != null) grow.animateTo(1f, tween(450 + (400 * shot.distance).toInt(), easing = FastOutSlowInEasing))
        pop.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }

    val leftHander = ball.battingHand.equals("Left", ignoreCase = true)
    val description = listOfNotNull(story.headline.lowercase(), story.detail).joinToString(", ") +
        if (shot != null) ", ball ${if (shot.inTheAir) "in the air" else "along the ground"}" else ""
    val dark = scheme.surface.red + scheme.surface.green + scheme.surface.blue < 1.5f
    val grass = colors.field
    val grassEdge = lerp(grass, Color.Black, if (dark) 0.35f else 0.12f)
    val stripe = if (dark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.035f)
    val surround = lerp(grass, scheme.surface, 0.55f)
    val rope = if (dark) Color.White.copy(alpha = 0.85f) else Color.White
    val chalk = Color.White.copy(alpha = if (dark) 0.3f else 0.7f)
    val sideColor = if (dark) Color.White.copy(alpha = 0.5f) else Color(0xFF1E3A2B).copy(alpha = 0.6f)
    val tone = when (story.kind) {
        BallStoryKind.FOUR -> colors.four
        BallStoryKind.SIX -> colors.six
        BallStoryKind.WICKET -> colors.wicket
        BallStoryKind.RUNS -> colors.accent
        BallStoryKind.EXTRA -> colors.extra
        BallStoryKind.DOT -> Color(0xFF263241)
    }
    val headlineInk = if (story.kind == BallStoryKind.RUNS) Color(0xFF1A1300) else Color.White

    Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val outer = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = outer * FIELD
                val batter = Offset(c.x, c.y - r * PITCH / 2f)
                val groundClip = Path().apply { addOval(Rect(c, r)) }
                val grassBrush = Brush.radialGradient(listOf(grass, grass, grassEdge), c, r)
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                val sideStyle = TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = sideColor)
                val offLabel = measurer.measure("OFF", sideStyle)
                val legLabel = measurer.measure("LEG", sideStyle)
                val headline = measurer.measure(story.headline, TextStyle(fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = (r * 0.16f).toSp(), letterSpacing = 0.5.sp, color = headlineInk))
                val detail = story.detail?.let { measurer.measure(it, TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Medium, color = headlineInk.copy(alpha = 0.85f))) }
                val padX = r * 0.07f
                val padY = r * 0.035f
                val chip = Size(
                    maxOf(headline.size.width, detail?.size?.width ?: 0) + padX * 2,
                    headline.size.height + (detail?.size?.height ?: 0) + padY * 2,
                )

                // Where the shot ends, measured from the batter to the rope in the recorded direction.
                // The provider's frame looks from behind the batter; turning it half a circle gives
                // the view from behind the bowler without swapping off and leg.
                val end: Offset? = shot?.let { s ->
                    shotEnd(s.x, s.y, if (story.kind == BallStoryKind.FOUR || story.kind == BallStoryKind.SIX) 1f else s.distance, batter, c, r)
                }

                onDrawBehind {
                    drawCircle(surround, outer, c)
                    drawCircle(grassBrush, r, c)
                    clipPath(groundClip) {
                        val stripeWidth = r / 5f
                        var x = c.x - r
                        var i = 0
                        while (x < c.x + r) {
                            if (i % 2 == 0) drawRect(stripe, Offset(x, c.y - r), Size(stripeWidth, r * 2))
                            x += stripeWidth; i++
                        }
                    }
                    drawCircle(rope, r, c, style = Stroke(2.dp.toPx()))
                    drawCircle(chalk, r * 0.45f, c, style = Stroke(1.2.dp.toPx(), pathEffect = dash))
                    // Off and leg sides for this batter, as seen from behind the bowler.
                    val (left, right) = if (leftHander) legLabel to offLabel else offLabel to legLabel
                    drawText(left, topLeft = Offset(c.x - r * 0.82f, c.y - left.size.height / 2f))
                    drawText(right, topLeft = Offset(c.x + r * 0.82f - right.size.width, c.y - right.size.height / 2f))
                    drawPitch(c, r, colors.pitch)

                    if (end != null && shot != null) {
                        val t = grow.value
                        val tip = batter + (end - batter) * t
                        val width = 2.6.dp.toPx()
                        if (t >= 1f && (story.kind == BallStoryKind.FOUR || story.kind == BallStoryKind.SIX)) {
                            val angle = Math.toDegrees(atan2((end.y - c.y).toDouble(), (end.x - c.x).toDouble())).toFloat()
                            drawArc(tone.copy(alpha = 0.55f), angle - 12f, 24f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        drawLine(tone, batter, tip, width, cap = StrokeCap.Round, pathEffect = if (shot.inTheAir) dash else null)
                        drawArrowHead(batter, tip, tone, width)
                    }

                    // The batter at the striker's end.
                    drawCircle(Color.Black.copy(alpha = 0.3f), 6.dp.toPx(), batter + Offset(0f, 1.dp.toPx()))
                    drawCircle(Color.White, 4.5.dp.toPx(), batter)

                    // Label: at the tip of the arrow, or in the open ground below the pitch.
                    val p = pop.value
                    if (p > 0f) {
                        val centre = labelSpot(batter, end, c, r)
                        val scale = 0.85f + 0.15f * p
                        val box = Size(chip.width * scale, chip.height * scale)
                        val topLeft = centre - Offset(box.width / 2f, box.height / 2f)
                        drawRoundRect(Color.Black.copy(alpha = 0.25f * p), topLeft + Offset(0f, 1.5.dp.toPx()), box, CornerRadius(box.height / 2.4f))
                        drawRoundRect(tone.copy(alpha = p), topLeft, box, CornerRadius(box.height / 2.4f))
                        if (p > 0.6f) {
                            drawText(headline, topLeft = Offset(centre.x - headline.size.width / 2f, centre.y - chip.height / 2f + padY))
                            detail?.let { drawText(it, topLeft = Offset(centre.x - it.size.width / 2f, centre.y - chip.height / 2f + padY + headline.size.height)) }
                        }
                    }
                }
            },
    )
}

/**
 * Where a shot finishes on screen. [x] and [y] are the provider's direction in the frame seen
 * from behind the batter; turned half a circle, that is the view from behind the bowler. The
 * ball travels [reach] of the way from the [batter] to the rope of the ground centred at [c].
 */
internal fun shotEnd(x: Float, y: Float, reach: Float, batter: Offset, c: Offset, r: Float): Offset? {
    val len = hypot(x, y)
    if (len == 0f) return null
    val dir = Offset(-x / len, -y / len)
    val rel = batter - c
    val b = dir.x * rel.x + dir.y * rel.y
    val toRope = -b + sqrt(b * b - (rel.x * rel.x + rel.y * rel.y - r * r))
    return batter + dir * (toRope * reach)
}

/**
 * The label sits in open grass below the pitch, or above the batter when the ball went that
 * way, so it never hides the arrow or the off and leg markers.
 */
private fun labelSpot(batter: Offset, end: Offset?, c: Offset, r: Float): Offset {
    val below = Offset(c.x, c.y + r * 0.56f)
    if (end == null) return below
    val above = Offset(c.x, c.y - r * 0.6f)
    return if (distanceToSegment(above, batter, end) > distanceToSegment(below, batter, end)) above else below
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lengthSquared = ab.x * ab.x + ab.y * ab.y
    if (lengthSquared == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lengthSquared).coerceIn(0f, 1f)
    val closest = a + ab * t
    return hypot(p.x - closest.x, p.y - closest.y)
}

private fun DrawScope.drawArrowHead(from: Offset, tip: Offset, color: Color, width: Float) {
    val v = tip - from
    val len = hypot(v.x, v.y)
    if (len < 1f) return
    val dir = v / len
    val normal = Offset(-dir.y, dir.x)
    val size = 7.dp.toPx()
    val back = tip - dir * size
    drawLine(color, tip, back + normal * (size * 0.6f), width, cap = StrokeCap.Round)
    drawLine(color, tip, back - normal * (size * 0.6f), width, cap = StrokeCap.Round)
}

private fun DrawScope.drawPitch(c: Offset, r: Float, pitch: Color) {
    val w = r * 0.075f
    val h = r * PITCH
    val top = c.y - h / 2f
    val bottom = c.y + h / 2f
    drawRoundRect(pitch, Offset(c.x - w / 2, top - w * 0.5f), Size(w, h + w), CornerRadius(2.dp.toPx()))
    val crease = Color.White.copy(alpha = 0.85f)
    val line = 1.dp.toPx()
    drawLine(crease, Offset(c.x - w * 0.8f, top), Offset(c.x + w * 0.8f, top), line)
    drawLine(crease, Offset(c.x - w * 0.8f, bottom), Offset(c.x + w * 0.8f, bottom), line)
    // Stumps behind the batter at the top, and at the bowler's end below.
    val stumps = Color(0xFF5B4630)
    drawLine(stumps, Offset(c.x - w * 0.25f, top - w * 0.3f), Offset(c.x + w * 0.25f, top - w * 0.3f), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(stumps, Offset(c.x - w * 0.25f, bottom + w * 0.3f), Offset(c.x + w * 0.25f, bottom + w * 0.3f), 2.dp.toPx(), cap = StrokeCap.Round)
}
