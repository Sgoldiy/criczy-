package com.criczy.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criczy.app.core.analytics.WagonWheel
import com.criczy.app.core.model.Ball
import com.criczy.app.ui.theme.ScoreFont
import com.criczy.app.ui.theme.cricketColors
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

/** How far the replay starts zoomed in on the pitch. */
private const val ZOOM = 2.4f

// Replay timeline, in milliseconds.
private const val HOLD = 220f
private const val DELIVERY = 640f
private const val CONTACT = HOLD + DELIVERY
private const val ZOOM_OUT = 700f
private const val LABEL_IN = 300f

/**
 * Where the ball pitched, as a share of the pitch from the batter's stumps, from the length the
 * scorers recorded. Null for a full toss or when no length was recorded: no bounce is drawn.
 */
internal fun bounceFraction(length: String?): Float? = when (length?.lowercase()) {
    "yorker" -> 0.06f
    "half volley" -> 0.16f
    "length ball" -> 0.3f
    "back of a length" -> 0.38f
    "short" -> 0.46f
    "half tracker" -> 0.55f
    else -> null
}

/** How far the ball passed from the stumps, in pitch widths towards the off side, from the recorded line. */
internal fun lineOffset(line: String?): Float = when (line?.lowercase()) {
    "wide" -> 0.75f
    "outside off" -> 0.32f
    "off stump" -> 0.12f
    "leg stump" -> -0.12f
    "down leg" -> -0.32f
    "wide down leg" -> -0.75f
    else -> 0f
}

/** What the ball does after it reaches the batter. */
private enum class Follow { SHOT, PAST_BAT, BOWLED, STOP }

private class Timeline(val follow: Follow, distance: Float) {
    val flight = if (follow == Follow.SHOT) 550f + 450f * distance else 340f
    val zoomStart = if (follow == Follow.SHOT) CONTACT else CONTACT + flight + 150f
    val zoomEnd = zoomStart + ZOOM_OUT
    val labelStart = maxOf(CONTACT + flight, zoomEnd)
    val end = labelStart + LABEL_IN
}

private fun timelineFor(ball: Ball): Timeline {
    val story = ballStory(ball)
    val shot = if (story.showsShot) WagonWheel.point(ball) else null
    val follow = when {
        shot != null -> Follow.SHOT
        ball.isWicket && ball.dismissalType?.contains("bowled", ignoreCase = true) == true -> Follow.BOWLED
        story.headline in setOf("MISSED", "LEFT ALONE", "WIDE") || ball.isBye -> Follow.PAST_BAT
        else -> Follow.STOP
    }
    return Timeline(follow, shot?.distance ?: 0f)
}

/** How long [BallGround] takes to replay [ball], so an over can play its balls in turn. */
fun replayMillis(ball: Ball): Long = timelineFor(ball).end.toLong()

private fun phase(ms: Float, start: Float, length: Float) = ((ms - start) / length).coerceIn(0f, 1f)

private fun ease(t: Float) = FastOutSlowInEasing.transform(t)

/**
 * One delivery on a top-down ground, seen from behind the bowler as on a TV broadcast: the
 * batter stands at the upper end of the pitch and the bowler runs in from below, so a
 * right-hander's off side is on the left.
 *
 * With [animate] the ball plays out like a broadcast analysis replay. The view starts close
 * on the pitch: the ball leaves the bowler's hand on the side of the stumps they bowled from,
 * pitches at the recorded length and arrives on the recorded line. Then the camera pulls back
 * while the shot runs out in its recorded direction (dashed when in the air), or the ball goes
 * through to the keeper, hits the stumps, or stops at the bat. A label says what happened.
 * Changing [replayKey] plays it again. Without [animate] the finished picture is drawn.
 *
 * All motion is read in the draw phase, so a replay redraws without recomposing.
 */
@Composable
fun BallGround(ball: Ball, modifier: Modifier = Modifier, animate: Boolean = false, replayKey: Any? = null) {
    val colors = cricketColors
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val story = remember(ball) { ballStory(ball) }
    val shot = remember(ball) { if (story.showsShot) WagonWheel.point(ball) else null }
    val timeline = remember(ball) { timelineFor(ball) }
    val clock = remember(ball) { Animatable(if (animate) 0f else timeline.end) }
    LaunchedEffect(ball, animate, replayKey) {
        if (!animate) {
            clock.snapTo(timeline.end)
            return@LaunchedEffect
        }
        clock.snapTo(0f)
        clock.animateTo(timeline.end, tween(timeline.end.toInt(), easing = LinearEasing))
    }

    val leftHander = ball.battingHand.equals("Left", ignoreCase = true)
    val description = listOfNotNull(story.headline.lowercase(), story.detail).joinToString(", ") +
        if (shot != null) ", ball ${if (shot.inTheAir) "in the air" else "along the ground"}" else ""
    val look = fieldLook()
    val tone = storyColor(story.kind, forLabel = true)
    val headlineInk = if (story.kind == BallStoryKind.RUNS) Color(0xFF1A1300) else Color.White
    val wicketRed = colors.wicket

    Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val outer = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = outer * FIELD
                val length = r * PITCH
                val w = r * 0.075f
                val batterY = c.y - length / 2f
                val bowlerY = c.y + length / 2f
                val batter = Offset(c.x, batterY)
                val view = Path().apply { addOval(Rect(c, r + 2.dp.toPx())) }
                val field = FieldPaint(look, measurer, c, r, leftHander, this)
                val dash = field.dash
                val headline = measurer.measure(story.headline, TextStyle(fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = (r * 0.16f).toSp(), letterSpacing = 0.5.sp, color = headlineInk))
                val detail = story.detail?.let { measurer.measure(it, TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Medium, color = headlineInk.copy(alpha = 0.85f))) }
                // While the camera is on the pitch, the delivery is captioned as on a broadcast.
                val caption = listOfNotNull(ball.length, ball.line).joinToString(" · ").takeIf { it.isNotEmpty() && animate }
                    ?.let { measurer.measure(it, TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.SemiBold, color = Color.White)) }
                val padX = r * 0.07f
                val padY = r * 0.035f
                val chip = Size(
                    maxOf(headline.size.width, detail?.size?.width ?: 0) + padX * 2,
                    headline.size.height + (detail?.size?.height ?: 0) + padY * 2,
                )

                // The delivery: from the bowler's hand, over or round the wicket, to the batter.
                val delivery = deliveryOf(ball, c, r)
                val release = delivery.release
                val arrival = delivery.arrival
                val bounceAt = delivery.bounceAt
                val bounce = delivery.bounce
                val keeper = arrival + (arrival - release) * 0.28f
                val stumps = Offset(c.x, batterY - w * 0.3f)
                val rebound = arrival + Offset(0f, length * 0.08f)
                val end: Offset? = shot?.let { s ->
                    shotEnd(s.x, s.y, if (story.kind == BallStoryKind.FOUR || story.kind == BallStoryKind.SIX) 1f else s.distance, batter, c, r)
                }
                val labelCentre = labelSpot(batter, end, c, r)

                onDrawBehind {
                    val ms = clock.value
                    val zoom = 1f + (ZOOM - 1f) * (1f - ease(phase(ms, timeline.zoomStart, ZOOM_OUT)))
                    val ballRadius = 2.6.dp.toPx()

                    drawCircle(look.surround, outer, c)
                    clipPath(view) {
                        withTransform({ scale(zoom, zoom, c) }) {
                            val bowledNow = timeline.follow == Follow.BOWLED && ms >= CONTACT + 120f
                            drawField(field, if (bowledNow) wicketRed else null)

                            // Bowler at the far end.
                            drawCircle(Color.Black.copy(alpha = 0.35f), 3.6.dp.toPx(), release + Offset(0f, w * 0.9f))

                            // Delivery path and where it pitched.
                            val d = phase(ms, HOLD, DELIVERY)
                            val inFlight = release + (arrival - release) * d
                            drawLine(Color.White.copy(alpha = 0.35f), release, inFlight, 1.2.dp.toPx(), cap = StrokeCap.Round)
                            if (bounce != null && bounceAt != null && d >= bounceAt) {
                                drawCircle(Color.White.copy(alpha = 0.85f), 2.2.dp.toPx(), bounce, style = Stroke(1.dp.toPx()))
                                val ripple = phase(ms, HOLD + DELIVERY * bounceAt, 420f)
                                if (ripple < 1f) drawCircle(Color.White.copy(alpha = 0.7f * (1f - ripple)), (2.2f + 7f * ripple).dp.toPx(), bounce, style = Stroke(1.2.dp.toPx()))
                            }

                            // Where the ball is now, with its shadow while it is in the air.
                            var ballAt = inFlight
                            var height = 0f
                            if (ms < CONTACT) {
                                height = when {
                                    bounceAt == null -> 0.6f
                                    d < bounceAt -> 1f - d / bounceAt
                                    else -> 0.35f * sin(PI.toFloat() * ((d - bounceAt) / (1f - bounceAt)).coerceIn(0f, 1f))
                                }
                            } else {
                                val f = ease(phase(ms, CONTACT, timeline.flight))
                                when (timeline.follow) {
                                    Follow.SHOT -> if (end != null) {
                                        ballAt = arrival + (end - arrival) * f
                                        height = if (shot?.inTheAir == true) sin(PI.toFloat() * f) else 0f
                                        val tip = ballAt
                                        if (story.kind == BallStoryKind.FOUR || story.kind == BallStoryKind.SIX) {
                                            if (f >= 1f) {
                                                val angle = Math.toDegrees(atan2((end.y - c.y).toDouble(), (end.x - c.x).toDouble())).toFloat()
                                                drawArc(tone.copy(alpha = 0.55f), angle - 12f, 24f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                                            }
                                        }
                                        drawLine(tone, arrival, tip, 2.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (shot?.inTheAir == true) dash else null)
                                        if (f >= 1f) drawArrowHead(arrival, tip, tone, 2.6.dp.toPx())
                                    }
                                    Follow.PAST_BAT -> ballAt = arrival + (keeper - arrival) * f
                                    Follow.BOWLED -> ballAt = arrival + (stumps - arrival) * ease(phase(ms, CONTACT, 120f))
                                    Follow.STOP -> ballAt = arrival + (rebound - arrival) * f
                                }
                            }

                            // The batter.
                            drawCircle(Color.Black.copy(alpha = 0.3f), 6.dp.toPx(), batter + Offset(0f, 1.dp.toPx()))
                            drawCircle(Color.White, 4.5.dp.toPx(), batter)

                            val arrived = timeline.follow == Follow.SHOT && ms >= CONTACT + timeline.flight
                            if (!arrived || shot?.isWicket == true) {
                                val lift = 1f + 0.5f * height
                                drawCircle(Color.Black.copy(alpha = 0.3f), ballRadius, ballAt + Offset(1.5f, 2.5f) * (1f + 3f * height))
                                drawCircle(Color(0xFFF4F1E8), ballRadius * lift, ballAt)
                                drawCircle(Color(0xFFB3261E), ballRadius * lift, ballAt, style = Stroke(0.8.dp.toPx()))
                            }
                            if (arrived && end != null && shot?.isWicket == true) {
                                val k = 5.dp.toPx()
                                drawLine(tone, end + Offset(-k, -k), end + Offset(k, k), 2.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(tone, end + Offset(-k, k), end + Offset(k, -k), 2.5.dp.toPx(), cap = StrokeCap.Round)
                            }
                            if (bowledNow) {
                                val burst = phase(ms, CONTACT + 120f, 450f)
                                drawCircle(wicketRed.copy(alpha = 0.8f * (1f - burst)), (4f + 12f * burst).dp.toPx(), stumps, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }

                    caption?.let { text ->
                        val shown = phase(ms, 120f, 220f) * (1f - phase(ms, timeline.zoomStart, 260f))
                        if (shown > 0f) {
                            val box = Size(text.size.width + padX * 1.6f, text.size.height + padY * 1.6f)
                            val topLeft = Offset(c.x - box.width / 2f, c.y + r * 0.74f - box.height / 2f)
                            drawRoundRect(Color.Black.copy(alpha = 0.55f * shown), topLeft, box, CornerRadius(box.height / 2f))
                            drawText(text, topLeft = topLeft + Offset(padX * 0.8f, padY * 0.8f), alpha = shown)
                        }
                    }

                    // The label, once the camera has pulled back.
                    val p = ease(phase(ms, timeline.labelStart, LABEL_IN))
                    if (p > 0f) {
                        val scale = 0.85f + 0.15f * p
                        val box = Size(chip.width * scale, chip.height * scale)
                        val topLeft = labelCentre - Offset(box.width / 2f, box.height / 2f)
                        drawRoundRect(Color.Black.copy(alpha = 0.25f * p), topLeft + Offset(0f, 1.5.dp.toPx()), box, CornerRadius(box.height / 2.4f))
                        drawRoundRect(tone.copy(alpha = p), topLeft, box, CornerRadius(box.height / 2.4f))
                        if (p > 0.6f) {
                            drawText(headline, topLeft = Offset(labelCentre.x - headline.size.width / 2f, labelCentre.y - chip.height / 2f + padY))
                            detail?.let { drawText(it, topLeft = Offset(labelCentre.x - it.size.width / 2f, labelCentre.y - chip.height / 2f + padY + headline.size.height)) }
                        }
                    }
                }
            },
    )
}

/** Colours of the ground, shared by the single-ball replay and the over summary. */
internal class FieldLook(
    val grass: Color, val grassEdge: Color, val stripe: Color, val surround: Color,
    val rope: Color, val chalk: Color, val side: Color, val pitch: Color,
)

@Composable
internal fun fieldLook(): FieldLook {
    val colors = cricketColors
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.red + surface.green + surface.blue < 1.5f
    val grass = colors.field
    return remember(grass, surface, colors.pitch) {
        FieldLook(
            grass = grass,
            grassEdge = lerp(grass, Color.Black, if (dark) 0.35f else 0.12f),
            stripe = if (dark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.035f),
            surround = lerp(grass, surface, 0.55f),
            rope = if (dark) Color.White.copy(alpha = 0.85f) else Color.White,
            chalk = Color.White.copy(alpha = if (dark) 0.3f else 0.7f),
            side = if (dark) Color.White.copy(alpha = 0.5f) else Color(0xFF1E3A2B).copy(alpha = 0.6f),
            pitch = colors.pitch,
        )
    }
}

/** Everything about the ground that depends only on its size, built once per size. */
internal class FieldPaint(val look: FieldLook, measurer: TextMeasurer, val c: Offset, val r: Float, leftHander: Boolean, density: Density) {
    val grassBrush = Brush.radialGradient(listOf(look.grass, look.grass, look.grassEdge), c, r)
    val grassClip = Path().apply { addOval(Rect(c, r)) }
    val dash = with(density) { PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) }
    private val sideStyle = with(density) { TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = look.side) }
    private val off = measurer.measure("OFF", sideStyle)
    private val leg = measurer.measure("LEG", sideStyle)
    val left: TextLayoutResult = if (leftHander) leg else off
    val right: TextLayoutResult = if (leftHander) off else leg
}

/** Grass, rope, inner circle, off and leg markers and the pitch, in field coordinates. */
internal fun DrawScope.drawField(f: FieldPaint, batterStumps: Color? = null, showSides: Boolean = true) {
    val c = f.c
    val r = f.r
    drawCircle(f.grassBrush, r, c)
    clipPath(f.grassClip) {
        val stripeWidth = r / 5f
        var x = c.x - r
        var i = 0
        while (x < c.x + r) {
            if (i % 2 == 0) drawRect(f.look.stripe, Offset(x, c.y - r), Size(stripeWidth, r * 2))
            x += stripeWidth; i++
        }
    }
    drawCircle(f.look.rope, r, c, style = Stroke(2.dp.toPx()))
    drawCircle(f.look.chalk, r * 0.45f, c, style = Stroke(1.2.dp.toPx(), pathEffect = f.dash))
    if (showSides) {
        drawText(f.left, topLeft = Offset(c.x - r * 0.82f, c.y - f.left.size.height / 2f))
        drawText(f.right, topLeft = Offset(c.x + r * 0.82f - f.right.size.width, c.y - f.right.size.height / 2f))
    }
    drawPitch(c, r, f.look.pitch, batterStumps)
}

/** The path of a delivery on screen: release, where it pitched (if recorded) and where it reached the batter. */
internal class Delivery(val release: Offset, val arrival: Offset, val bounceAt: Float?, val bounce: Offset?)

internal fun deliveryOf(ball: Ball, c: Offset, r: Float): Delivery {
    val length = r * PITCH
    val w = r * 0.075f
    val offSign = if (ball.battingHand.equals("Left", ignoreCase = true)) 1f else -1f
    val releaseSign = when {
        ball.bowlingArm == null || ball.bowlingFrom == null -> 0f
        ball.bowlingArm.equals("Left", true) == ball.bowlingFrom.equals("Round", true) -> -1f
        else -> 1f
    }
    val release = Offset(c.x + releaseSign * w * 0.35f, c.y + length / 2f + w * 0.2f)
    val arrival = Offset(c.x + offSign * lineOffset(ball.line) * w, c.y - length / 2f)
    val bounceAt = bounceFraction(ball.length)?.let { 1f - it }
    return Delivery(release, arrival, bounceAt, bounceAt?.let { release + (arrival - release) * it })
}

/**
 * An over at a glance, as a broadcast shows it when the over ends: every shot of the over from
 * the batter's end, numbered in the order bowled. With [animate] the shots are drawn in turn.
 */
@Composable
fun OverGround(balls: List<Ball>, modifier: Modifier = Modifier, animate: Boolean = false, replayKey: Any? = null) {
    val measurer = rememberTextMeasurer()
    val look = fieldLook()
    val stories = remember(balls) { balls.map { ballStory(it) } }
    val shots = remember(balls) { balls.mapIndexed { i, b -> if (stories[i].showsShot) WagonWheel.point(b) else null } }
    val tones = stories.map { storyColor(it.kind, forLabel = true) }
    val hands = remember(balls) { balls.mapNotNull { it.battingHand?.lowercase() }.toSet() }
    val leftHander = hands == setOf("left")
    val progress = remember(balls) { Animatable(if (animate) 0f else balls.size.toFloat()) }
    LaunchedEffect(balls, animate, replayKey) {
        if (!animate) {
            progress.snapTo(balls.size.toFloat())
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(balls.size.toFloat(), tween(380 * balls.size, easing = LinearEasing))
    }
    val description = remember(balls) {
        val runs = balls.sumOf { it.teamRuns }
        listOfNotNull(
            "over summary",
            "$runs run${if (runs == 1) "" else "s"}",
            balls.count { it.isWicket }.takeIf { it > 0 }?.let { "$it wicket${if (it == 1) "" else "s"}" },
            stories.count { it.kind == BallStoryKind.FOUR }.takeIf { it > 0 }?.let { "$it four${if (it == 1) "" else "s"}" },
            stories.count { it.kind == BallStoryKind.SIX }.takeIf { it > 0 }?.let { "$it six${if (it == 1) "" else "es"}" },
        ).joinToString(", ")
    }

    Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val outer = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = outer * FIELD
                val field = FieldPaint(look, measurer, c, r, leftHander, this)
                val batter = Offset(c.x, c.y - r * PITCH / 2f)
                val deliveries = balls.map { deliveryOf(it, c, r) }
                val ends = shots.mapIndexed { i, s ->
                    s?.let { shotEnd(it.x, it.y, if (stories[i].kind == BallStoryKind.FOUR || stories[i].kind == BallStoryKind.SIX) 1f else it.distance, batter, c, r) }
                }
                val numbers = balls.indices.map {
                    measurer.measure("${it + 1}", TextStyle(fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = (r * 0.075f).toSp(), color = Color.White))
                }
                onDrawBehind {
                    val shown = progress.value
                    drawCircle(look.surround, outer, c)
                    drawField(field, showSides = hands.size == 1)
                    // Each shot, growing from the bat in turn, numbered at its tip.
                    shots.forEachIndexed { i, s ->
                        val e = ends[i] ?: return@forEachIndexed
                        if (s == null) return@forEachIndexed
                        val t = ease((shown - i).coerceIn(0f, 1f))
                        if (t <= 0f) return@forEachIndexed
                        val from = deliveries[i].arrival
                        val tip = from + (e - from) * t
                        if (t >= 1f && (stories[i].kind == BallStoryKind.FOUR || stories[i].kind == BallStoryKind.SIX)) {
                            val angle = Math.toDegrees(atan2((e.y - c.y).toDouble(), (e.x - c.x).toDouble())).toFloat()
                            drawArc(tones[i].copy(alpha = 0.45f), angle - 10f, 20f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        }
                        drawLine(tones[i], from, tip, 2.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (s.inTheAir) field.dash else null)
                        if (t >= 1f) {
                            val badge = r * 0.065f
                            drawCircle(Color.Black.copy(alpha = 0.3f), badge, tip + Offset(0f, 1.dp.toPx()))
                            drawCircle(tones[i], badge, tip)
                            val n = numbers[i]
                            drawText(n, topLeft = tip - Offset(n.size.width / 2f, n.size.height / 2f))
                        }
                    }
                    drawCircle(Color.Black.copy(alpha = 0.3f), 6.dp.toPx(), batter + Offset(0f, 1.dp.toPx()))
                    drawCircle(Color.White, 4.5.dp.toPx(), batter)
                }
            },
    )
}

/** The colour that goes with each kind of ball, shared by the ground label and the "See shot" link. */
@Composable
fun storyColor(kind: BallStoryKind, forLabel: Boolean = false): Color {
    val colors = cricketColors
    return when (kind) {
        BallStoryKind.FOUR -> colors.four
        BallStoryKind.SIX -> colors.six
        BallStoryKind.WICKET -> colors.wicket
        BallStoryKind.RUNS -> colors.accent
        BallStoryKind.EXTRA -> colors.extra
        BallStoryKind.DOT -> if (forLabel) Color(0xFF263241) else MaterialTheme.colorScheme.onSurfaceVariant
    }
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

private fun DrawScope.drawPitch(c: Offset, r: Float, pitch: Color, batterStumps: Color?) {
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
    val wood = Color(0xFF5B4630)
    drawLine(batterStumps ?: wood, Offset(c.x - w * 0.25f, top - w * 0.3f), Offset(c.x + w * 0.25f, top - w * 0.3f), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(wood, Offset(c.x - w * 0.25f, bottom + w * 0.3f), Offset(c.x + w * 0.25f, bottom + w * 0.3f), 2.dp.toPx(), cap = StrokeCap.Round)
}
