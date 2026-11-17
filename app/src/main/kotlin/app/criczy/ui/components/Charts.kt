package app.criczy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.analytics.OverBar
import app.criczy.core.analytics.PartnershipStand
import app.criczy.core.analytics.PitchCell
import app.criczy.core.analytics.ShotPoint
import app.criczy.core.analytics.WormPoint
import app.criczy.core.analytics.ZoneSummary
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import kotlin.math.ceil
import kotlin.math.max

/** One innings' data for a comparative chart. */
data class ChartSeries<T>(val label: String, val color: Color, val points: List<T>)

@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun niceCeiling(value: Int, step: Int): Int = (ceil(max(value, 1) / step.toDouble()).toInt() * step).coerceAtLeast(step)

private fun DrawScope.axisLabel(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle, alignRight: Boolean = false, centre: Boolean = false) {
    val layout = measurer.measure(text, style)
    val x = when {
        alignRight -> at.x - layout.size.width
        centre -> at.x - layout.size.width / 2f
        else -> at.x
    }
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

/** Runs per over as bars, with a marker for each wicket that fell in the over. */
@Composable
fun ManhattanChart(series: List<ChartSeries<OverBar>>, modifier: Modifier = Modifier) {
    val grid = cricketColors.chartGrid
    val wicket = cricketColors.wicket
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val maxOver = series.maxOfOrNull { s -> s.points.maxOfOrNull { it.over } ?: 0 } ?: 0
    // A multiple of four keeps the five grid lines on whole numbers.
    val maxRuns = niceCeiling(series.maxOfOrNull { s -> s.points.maxOfOrNull { it.runs } ?: 0 } ?: 0, 4)
    val summary = series.joinToString(". ") { s -> "${s.label}: ${s.points.sumOf { it.runs }} runs in ${s.points.size} overs" }
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = "Runs per over. $summary" }) {
        if (maxOver == 0) return@Canvas
        val left = 26.dp.toPx(); val bottom = 18.dp.toPx(); val top = 10.dp.toPx()
        val plotW = size.width - left; val plotH = size.height - bottom - top
        for (i in 0..4) {
            val y = top + plotH * (1 - i / 4f)
            drawLine(grid, Offset(left, y), Offset(size.width, y), 1f)
            axisLabel(measurer, (maxRuns * i / 4).toString(), Offset(left - 6.dp.toPx(), y), labelStyle, alignRight = true)
        }
        val slot = plotW / maxOver
        val barW = (slot * 0.8f / series.size).coerceAtMost(18.dp.toPx())
        series.forEachIndexed { index, s ->
            s.points.forEach { bar ->
                val x = left + slot * (bar.over - 1) + (slot - barW * series.size) / 2f + barW * index
                val h = plotH * bar.runs / maxRuns
                drawRoundRect(s.color, Offset(x, top + plotH - h), Size(barW * 0.92f, h), CornerRadius(2.dp.toPx()))
                repeat(bar.wickets) { w ->
                    drawCircle(wicket, 3.dp.toPx(), Offset(x + barW * 0.46f, top + plotH - h - 5.dp.toPx() - w * 8.dp.toPx()))
                }
            }
        }
        val every = when { maxOver <= 20 -> 5; maxOver <= 50 -> 10; else -> 20 }
        for (over in every..maxOver step every) {
            axisLabel(measurer, over.toString(), Offset(left + slot * (over - 0.5f), size.height - bottom / 2f), labelStyle, centre = true)
        }
    }
}

/** Cumulative runs by over, one line per innings, with wickets marked on the line. */
@Composable
fun WormChart(series: List<ChartSeries<WormPoint>>, modifier: Modifier = Modifier) {
    val grid = cricketColors.chartGrid
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val maxOver = series.maxOfOrNull { s -> s.points.maxOfOrNull { it.over } ?: 0 } ?: 0
    val peak = series.maxOfOrNull { s -> s.points.maxOfOrNull { it.runs } ?: 0 } ?: 0
    val maxRuns = niceCeiling(peak, if (peak <= 200) 20 else 100)
    val summary = series.joinToString(". ") { s -> "${s.label} reached ${s.points.lastOrNull()?.runs ?: 0}" }
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = "Run progression. $summary" }) {
        if (maxOver == 0) return@Canvas
        val left = 30.dp.toPx(); val bottom = 18.dp.toPx(); val top = 10.dp.toPx()
        val plotW = size.width - left - 6.dp.toPx(); val plotH = size.height - bottom - top
        fun pos(over: Int, runs: Int) = Offset(left + plotW * over / maxOver, top + plotH * (1 - runs / maxRuns.toFloat()))
        for (i in 0..4) {
            val y = top + plotH * (1 - i / 4f)
            drawLine(grid, Offset(left, y), Offset(size.width, y), 1f)
            axisLabel(measurer, (maxRuns * i / 4).toString(), Offset(left - 6.dp.toPx(), y), labelStyle, alignRight = true)
        }
        series.forEach { s ->
            if (s.points.isEmpty()) return@forEach
            val path = Path().apply {
                moveTo(pos(0, 0).x, pos(0, 0).y)
                s.points.forEach { p -> pos(p.over, p.runs).let { lineTo(it.x, it.y) } }
            }
            drawPath(path, s.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            s.points.filter { it.wicketFellThisOver }.forEach { p ->
                val at = pos(p.over, p.runs)
                drawCircle(surface, 4.5.dp.toPx(), at)
                drawCircle(s.color, 4.5.dp.toPx(), at, style = Stroke(2.dp.toPx()))
            }
        }
        val every = when { maxOver <= 20 -> 5; maxOver <= 50 -> 10; else -> 20 }
        for (over in every..maxOver step every) {
            axisLabel(measurer, over.toString(), Offset(pos(over, 0).x, size.height - bottom / 2f), labelStyle, centre = true)
        }
    }
}

/** Where the runs were scored: a line per scoring shot from the batter to where the ball went. */
@Composable
fun WagonWheelChart(shots: List<ShotPoint>, modifier: Modifier = Modifier) {
    val colors = cricketColors
    val outline = MaterialTheme.colorScheme.outline
    val other = MaterialTheme.colorScheme.onSurfaceVariant
    val description = "Wagon wheel of ${shots.size} scoring shots: ${shots.count { it.runs == 4 }} fours and ${shots.count { it.runs == 6 }} sixes"
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val radius = size.minDimension / 2f * 0.96f
        val c = center
        drawCircle(colors.field, radius, c)
        drawCircle(outline, radius, c, style = Stroke(1.5.dp.toPx()))
        drawCircle(outline.copy(alpha = 0.45f), radius * 0.5f, c, style = Stroke(1.dp.toPx()))
        // Zone dividers every 45°.
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            drawLine(outline.copy(alpha = 0.25f), c, Offset(c.x + radius * kotlin.math.cos(a).toFloat(), c.y + radius * kotlin.math.sin(a).toFloat()), 1f)
        }
        val pitchW = radius * 0.07f; val pitchH = radius * 0.3f
        drawRoundRect(colors.pitch, Offset(c.x - pitchW / 2, c.y - pitchH * 0.75f), Size(pitchW, pitchH), CornerRadius(2.dp.toPx()))
        // Draw ones and twos first so boundaries stay visible on top.
        shots.sortedBy { it.runs }.forEach { shot ->
            val color = when {
                shot.isWicket -> colors.wicket
                shot.runs == 6 -> colors.six
                shot.runs == 4 -> colors.four
                else -> other.copy(alpha = 0.7f)
            }
            val end = Offset(c.x + shot.x * radius, c.y + shot.y * radius)
            drawLine(color, c, end, if (shot.runs >= 4) 2.2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
            if (shot.runs >= 4 || shot.isWicket) drawCircle(color, 2.6.dp.toPx(), end)
        }
    }
}

@Composable
fun ZoneBreakdown(zones: List<ZoneSummary>, modifier: Modifier = Modifier) {
    val total = zones.sumOf { it.runs }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        zones.sortedByDescending { it.runs }.forEach { zone ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(zone.zone.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(
                        Modifier.fillMaxWidth(zone.runs / total.toFloat()).height(8.dp)
                            .background(if (zone.zone.isOffSide) cricketColors.four else cricketColors.accent, RoundedCornerShape(50)),
                    )
                }
                Text(
                    "${zone.runs}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

/** Line against length as a grid; darker cells were bowled more often. */
@Composable
fun PitchMapGrid(cells: List<PitchCell>, modifier: Modifier = Modifier) {
    val lengths = cells.groupBy { it.length }.entries.sortedByDescending { e -> e.value.sumOf { it.balls } }.map { it.key }.take(6)
    val lines = cells.groupBy { it.line }.entries.sortedByDescending { e -> e.value.sumOf { it.balls } }.map { it.key }.take(5)
    val byKey = cells.associateBy { it.line to it.length }
    val peak = cells.maxOfOrNull { it.balls }?.coerceAtLeast(1) ?: 1
    val heat = cricketColors.accent
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Spacer(Modifier.width(92.dp))
            lines.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
        lengths.forEach { length ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(length, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp), maxLines = 1)
                lines.forEach { line ->
                    val cell = byKey[line to length]
                    val strength = (cell?.balls ?: 0) / peak.toFloat()
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(6.dp))
                            .background(heat.copy(alpha = 0.08f + 0.6f * strength))
                            .semantics { contentDescription = "$line, $length: ${cell?.balls ?: 0} balls, ${cell?.runs ?: 0} runs, ${cell?.wickets ?: 0} wickets" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${cell.runs}/${cell.balls}", fontFamily = ScoreFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (cell.wickets > 0) Text("${cell.wickets}w", style = MaterialTheme.typography.labelSmall, color = cricketColors.wicket)
                            }
                        }
                    }
                }
            }
        }
        Text("Runs conceded / balls bowled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PartnershipBars(stands: List<PartnershipStand>, nameOf: (Int) -> String, modifier: Modifier = Modifier) {
    val peak = stands.maxOfOrNull { it.runs }?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stands.forEach { stand ->
            val a = nameOf(stand.batterIds.first); val b = nameOf(stand.batterIds.second)
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Wicket ${stand.wicket}: $a and $b, ${stand.runs} runs from ${stand.balls} balls" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${a.substringAfterLast(' ')} ${stand.contributions.first}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${stand.runs}${if (stand.unbroken) "*" else ""} (${stand.balls})", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${stand.contributions.second} ${b.substringAfterLast(' ')}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                val share = stand.contributions.first / (stand.contributions.first + stand.contributions.second).coerceAtLeast(1).toFloat()
                Row(Modifier.fillMaxWidth(0.25f + 0.75f * stand.runs / peak).align(Alignment.CenterHorizontally).height(7.dp).clip(RoundedCornerShape(50))) {
                    if (share > 0f) Box(Modifier.weight(share).height(7.dp).background(cricketColors.accent))
                    if (share < 1f) Box(Modifier.weight(1f - share).height(7.dp).background(cricketColors.four))
                }
            }
        }
    }
}
