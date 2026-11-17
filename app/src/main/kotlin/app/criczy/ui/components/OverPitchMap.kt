package app.criczy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.model.Ball
import app.criczy.ui.theme.ScoreFont

/** Line categories from the off side to the leg side, as the scorers record them. */
internal fun lineColumn(line: String?): Int? = when (line?.lowercase()) {
    "wide" -> 0
    "outside off" -> 1
    "off stump" -> 2
    "middle stump" -> 3
    "leg stump" -> 4
    "down leg" -> 5
    "wide down leg" -> 6
    else -> null
}

/** Length categories from the batter's end towards the bowler; a full toss is above the crease. */
internal fun lengthRow(length: String?): Int? = when (length?.lowercase()) {
    "full toss", "beamer" -> 0
    "yorker" -> 1
    "half volley" -> 2
    "length ball" -> 3
    "back of a length" -> 4
    "short" -> 5
    "half tracker" -> 6
    else -> null
}

private val rowNames = listOf("Full toss", "Yorker", "Half volley", "Length ball", "Back of a length", "Short", "Half tracker")

/**
 * Relative column widths, off side first. The stump lines are narrow as on a real pitch, the
 * lines either side of them wider (outside off is where most balls go), the wides slim.
 */
private val columnWeights = floatArrayOf(0.55f, 1.35f, 0.9f, 0.9f, 0.9f, 1.35f, 0.55f)

/**
 * Where each ball of an over landed, as the cell of the scorers' line and length grid, with
 * balls that share a cell numbered [slot] 0, 1, 2… so they can sit side by side. Null for a
 * ball without both a line and a length.
 */
internal data class PitchPlace(val column: Int, val row: Int, val slot: Int, val sharing: Int)

internal fun pitchPlaces(balls: List<Ball>): List<PitchPlace?> {
    val cells = balls.map { b -> lineColumn(b.line)?.let { c -> lengthRow(b.length)?.let { r -> c to r } } }
    val counts = cells.filterNotNull().groupingBy { it }.eachCount()
    val seen = HashMap<Pair<Int, Int>, Int>()
    return cells.map { cell ->
        cell?.let {
            val slot = seen.getOrDefault(it, 0)
            seen[it] = slot + 1
            PitchPlace(it.first, it.second, slot, counts.getValue(it))
        }
    }
}

/**
 * A broadcast-style pitch map for an over, seen from behind the bowler with the batter's
 * stumps at the top: rows are the recorded lengths, columns the recorded lines, and every ball
 * is a numbered marker in its cell, coloured by what happened. Balls in the same cell sit side
 * by side, so every ball of the over is visible. Markers appear in bowling order.
 */
@Composable
fun OverPitchMap(balls: List<Ball>, modifier: Modifier = Modifier, animate: Boolean = false, replayKey: Any? = null) {
    val measurer = rememberTextMeasurer()
    val look = fieldLook()
    val places = remember(balls) { pitchPlaces(balls) }
    val stories = remember(balls) { balls.map { ballStory(it) } }
    val tones = stories.map { storyColor(it.kind, forLabel = true) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Off side on the left, as for a right-hander from behind the bowler; flipped only when
    // every ball of the over was to a left-hander, so the picture stays physically right.
    val offOnLeft = !balls.all { it.battingHand.equals("Left", ignoreCase = true) }
    val showFullToss = places.any { it?.row == 0 }
    val firstRow = if (showFullToss) 0 else 1
    val rows = 7 - firstRow
    val untracked = places.count { it == null }
    val progress = remember(balls) { Animatable(if (animate) 0f else balls.size.toFloat()) }
    LaunchedEffect(balls, animate, replayKey) {
        if (!animate) {
            progress.snapTo(balls.size.toFloat())
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(balls.size.toFloat(), tween(160 * balls.size + 200, easing = LinearEasing))
    }
    val description = remember(balls) {
        "Pitch map: " + balls.mapIndexed { i, b ->
            val where = listOfNotNull(b.length?.lowercase(), b.line?.lowercase()).joinToString(", ").ifEmpty { "not recorded" }
            "ball ${i + 1} $where"
        }.joinToString("; ")
    }

    Box(
        modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = labelColor)
                val sideStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = labelColor)
                val rowLabels = (firstRow..6).map { measurer.measure(rowNames[it], labelStyle) }
                val off = measurer.measure("OFF SIDE", sideStyle)
                val leg = measurer.measure("LEG SIDE", sideStyle)
                val note = untracked.takeIf { it > 0 }?.let { measurer.measure("$it ball${if (it == 1) "" else "s"} without a recorded line and length", labelStyle) }
                val numbers = balls.indices.map {
                    measurer.measure("${it + 1}", TextStyle(fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White))
                }
                val smallNumbers = balls.indices.map {
                    measurer.measure("${it + 1}", TextStyle(fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White))
                }

                val labelWidth = rowLabels.maxOf { it.size.width } + 10.dp.toPx()
                val top = 18.dp.toPx() // room for the stumps
                val bottom = off.size.height + 10.dp.toPx() + (note?.size?.height ?: 0)
                val left = labelWidth
                val gridWidth = size.width - left
                val weights = if (offOnLeft) columnWeights else columnWeights.reversedArray()
                val unit = gridWidth / weights.sum()
                val starts = FloatArray(weights.size) { i -> left + unit * weights.take(i).sum() }
                fun screenColumn(column: Int) = if (offOnLeft) column else weights.size - 1 - column
                fun columnX(column: Int): Float = screenColumn(column).let { starts[it] + unit * weights[it] / 2f }
                fun columnWidth(column: Int): Float = unit * weights[screenColumn(column)]
                val rowHeight = (size.height - top - bottom) / rows
                fun rowY(row: Int) = top + rowHeight * (row - firstRow + 0.5f)
                val creaseY = top + rowHeight * (1 - firstRow)
                val stripLeft = starts[1]
                val stripWidth = starts[6] - starts[1]
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val dot = minOf(unit * 1.35f, rowHeight) * 0.3f
                val gap = 2.dp.toPx()

                onDrawBehind {
                    // Grass, the pitch strip (outside off to down leg) and the wide guides.
                    drawRoundRect(look.grass, Offset(left, 0f), Size(gridWidth, size.height - bottom), CornerRadius(10.dp.toPx()))
                    drawRect(look.pitch, Offset(stripLeft, 0f), Size(stripWidth, size.height - bottom))
                    drawLine(look.chalk, Offset(stripLeft, 0f), Offset(stripLeft, size.height - bottom), 1.dp.toPx(), pathEffect = dash)
                    drawLine(look.chalk, Offset(stripLeft + stripWidth, 0f), Offset(stripLeft + stripWidth, size.height - bottom), 1.dp.toPx(), pathEffect = dash)
                    // Batting crease and the stumps on off, middle and leg.
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(stripLeft, creaseY), Offset(stripLeft + stripWidth, creaseY), 1.5.dp.toPx())
                    val wood = Color(0xFF5B4630)
                    for (column in 2..4) {
                        val x = columnX(column)
                        drawLine(wood, Offset(x, creaseY - 14.dp.toPx()), Offset(x, creaseY - 2.dp.toPx()), 3.dp.toPx())
                    }
                    // Length bands with their names.
                    for (row in firstRow..6) {
                        val y = top + rowHeight * (row - firstRow)
                        if (row > firstRow) drawLine(grid.copy(alpha = 0.35f), Offset(left, y), Offset(size.width, y), 1.dp.toPx())
                        val label = rowLabels[row - firstRow]
                        drawText(label, topLeft = Offset(0f, y + rowHeight / 2f - label.size.height / 2f))
                    }
                    val sideY = size.height - bottom + 6.dp.toPx()
                    val (leftSide, rightSide) = if (offOnLeft) off to leg else leg to off
                    drawText(leftSide, topLeft = Offset(left, sideY))
                    drawText(rightSide, topLeft = Offset(size.width - rightSide.size.width, sideY))
                    note?.let { drawText(it, topLeft = Offset(left, sideY + off.size.height + 2.dp.toPx())) }

                    // One numbered marker per ball, side by side when they share a cell.
                    val shown = progress.value
                    places.forEachIndexed { i, place ->
                        if (place == null) return@forEachIndexed
                        val appear = (shown - i).coerceIn(0f, 1f)
                        if (appear <= 0f) return@forEachIndexed
                        // Balls sharing a cell form a small grid: 2 side by side, 4 as two pairs, and so on.
                        val perRow = kotlin.math.ceil(kotlin.math.sqrt(place.sharing.toFloat())).toInt().coerceAtLeast(1)
                        val lines = (place.sharing + perRow - 1) / perRow
                        val width = columnWidth(place.column) - gap * 2
                        val radius = minOf(dot, (width - gap * (perRow - 1)) / (perRow * 2), (rowHeight - gap * (lines + 1)) / (lines * 2))
                        val inRow = place.slot % perRow
                        val line = place.slot / perRow
                        val countInLine = minOf(perRow, place.sharing - line * perRow)
                        val step = radius * 2 + gap
                        val centre = Offset(
                            columnX(place.column) + (inRow - (countInLine - 1) / 2f) * step,
                            rowY(place.row) + (line - (lines - 1) / 2f) * step,
                        )
                        val r = radius * (0.6f + 0.4f * appear)
                        drawCircle(Color.Black.copy(alpha = 0.3f * appear), r, centre + Offset(0f, 1.dp.toPx()))
                        drawCircle(tones[i].copy(alpha = appear), r, centre)
                        drawCircle(Color.White.copy(alpha = 0.9f * appear), r, centre, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        if (appear >= 1f) {
                            val n = if (radius < dot * 0.8f) smallNumbers[i] else numbers[i]
                            drawText(n, topLeft = centre - Offset(n.size.width / 2f, n.size.height / 2f))
                        }
                    }
                }
            },
    )
}
