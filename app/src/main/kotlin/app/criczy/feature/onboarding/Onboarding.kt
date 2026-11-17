package app.criczy.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.R
import app.criczy.core.analytics.FieldZone
import app.criczy.core.analytics.ShotPoint
import app.criczy.ui.components.Badge
import app.criczy.ui.components.LivePill
import app.criczy.ui.components.Pill
import app.criczy.ui.components.WagonWheelChart
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

private data class Page(val eyebrow: String, val title: String, val body: String, val art: @Composable () -> Unit)

private val pages = listOf(
    Page("LIVE", "Every ball, as it happens", "Live scores, full scorecards and ball-by-ball commentary for international and domestic cricket.") { LiveScoreArt() },
    Page("ANALYSIS", "See the match, not just the score", "Worm and Manhattan graphs, wagon wheels, pitch maps and batter-v-bowler match-ups, built from real ball data.") { AnalysisArt() },
    Page("YOURS", "Follow what matters to you", "Star your teams, series and matches to see them first, get match alerts, and keep reading when you're offline.") { FollowArt() },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val accent = cricketColors.accent
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // A floodlight glow that drifts across as the pages change.
            val drift = (pager.currentPage + pager.currentPageOffsetFraction) / (pages.size - 1).toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val centre = Offset(size.width * (0.2f + 0.6f * drift), size.height * 0.26f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), centre, size.width * 0.85f), size.width * 0.85f, centre)
            }
            Column(Modifier.systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The mark on its own dark tile, as on the launcher, so it reads in both themes.
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1B2C52), Color(0xFF070B12)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(56.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Criczy" }) {
                        Text("CRIC", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.sp)
                        Text("ZY", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.sp, color = accent)
                    }
                    if (pager.currentPage < pages.lastIndex) TextButton(onClick = onFinish) { Text("Skip") }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { index ->
                    val page = pages[index]
                    // Fade and shrink the page as it slides away, so the illustration feels layered.
                    val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    Column(
                        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset; scaleX = 1f - 0.08f * offset; scaleY = 1f - 0.08f * offset },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.weight(1f, fill = false).widthIn(max = 380.dp).fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) { page.art() }
                        Spacer(Modifier.height(28.dp))
                        Text(page.eyebrow, style = MaterialTheme.typography.labelLarge, color = accent, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
                    }
                }
                PageDots(pager, Modifier.padding(vertical = 18.dp))
                val last = pager.currentPage == pages.lastIndex
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (pager.currentPage > 0) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, modifier = Modifier.height(52.dp)) { Text("Back") }
                    }
                    Button(
                        onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cricketColors.onAccent),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        AnimatedContent(last, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "cta") { isLast ->
                            Text(if (isLast) "Get started" else "Next", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pages.size) { index ->
            val selected = index == pager.currentPage
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) cricketColors.accent else MaterialTheme.colorScheme.outlineVariant, label = "dotColor")
            Box(Modifier.height(8.dp).width(width).background(color, CircleShape))
        }
    }
}

// ---- illustrations -------------------------------------------------------------------------
// Drawn with the app's own components and invented teams, so they show what the app looks like
// without presenting any real result.

@Composable
private fun ArtCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 10.dp, content = content,
    )
}

@Composable
private fun ArtBall(text: String, color: Color, filled: Boolean) {
    Box(Modifier.size(30.dp).background(if (filled) color else color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ArtTeamRow(short: String, colorHex: String, score: String, overs: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(short, null, colorHex, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(short, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f))
        Text(score, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        if (overs != null) {
            Spacer(Modifier.width(6.dp))
            Text(overs, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LiveScoreArt() {
    val c = cricketColors
    // Bottom padding reserves the space the overlapping bubble hangs into.
    Box(Modifier.padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
        ArtCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Final · City Cup", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    LivePill()
                }
                ArtTeamRow("LIO", "#1565C0", "182/6", "(20.0)")
                ArtTeamRow("HAW", "#C62828", "171/4", "(18.2)")
                Text("Hawks need 12 runs from 10 balls", style = MaterialTheme.typography.bodySmall, color = c.live)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ov 19", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                    ArtBall("1", MaterialTheme.colorScheme.onSurfaceVariant, false)
                    ArtBall("4", c.four, true)
                    ArtBall("•", c.dot, false)
                    ArtBall("6", c.six, true)
                    ArtBall("W", c.wicket, true)
                }
            }
        }
        // A commentary bubble overlapping the card's corner.
        ArtCard(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 48.dp).widthIn(max = 230.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtBall("6", c.six, true)
                Spacer(Modifier.width(10.dp))
                Text("SIX! Picked up over deep mid wicket.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A fixed, plausible spread of shots for the illustration. */
private fun sampleShots(): List<ShotPoint> {
    val spec = listOf(
        200 to 4, 188 to 1, 215 to 4, 172 to 1, 250 to 2, 268 to 6, 292 to 4, 318 to 1, 330 to 6, 344 to 2,
        20 to 1, 58 to 4, 110 to 1, 150 to 2, 205 to 1, 300 to 1, 236 to 4, 352 to 1, 282 to 2, 128 to 4,
    )
    return spec.map { (angle, runs) ->
        val distance = if (runs >= 4) 1f else 0.35f + 0.1f * runs
        val r = Math.toRadians(angle.toDouble())
        ShotPoint((-cos(r) * distance).toFloat(), (sin(r) * distance).toFloat(), distance, FieldZone.COVER, runs, runs >= 4, false, 0, 0)
    }
}

@Composable
private fun AnalysisArt() {
    val c = cricketColors
    val shots = remember { sampleShots() }
    val bars = remember { listOf(6, 11, 4, 9, 2, 13, 7, 5, 10, 8, 3, 12, 6, 15) }
    val wickets = remember { setOf(4, 10) }
    val grid = c.chartGrid
    Box(Modifier.padding(bottom = 104.dp), contentAlignment = Alignment.Center) {
        ArtCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MANHATTAN", style = MaterialTheme.typography.titleLarge, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Pill("RUNS PER OVER")
                }
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 10.dp)) {
                    val slot = size.width / bars.size
                    for (i in 0..3) drawLine(grid, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
                    bars.forEachIndexed { index, runs ->
                        val h = size.height * runs / 16f
                        val x = slot * index + slot * 0.18f
                        drawRoundRect(if (runs >= 12) c.accent else c.four, Offset(x, size.height - h), Size(slot * 0.64f, h), CornerRadius(4f))
                        if (index in wickets) drawCircle(c.wicket, 3.dp.toPx(), Offset(x + slot * 0.32f, size.height - h - 6.dp.toPx()))
                    }
                }
            }
        }
        ArtCard(Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 96.dp).rotate(-4f)) {
            WagonWheelChart(shots, Modifier.padding(10.dp).size(112.dp))
        }
        ArtCard(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 78.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("PARTNERSHIP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("86 (52)", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Row(Modifier.width(120.dp).height(7.dp).clip(RoundedCornerShape(50))) {
                    Box(Modifier.weight(0.62f).height(7.dp).background(c.accent))
                    Box(Modifier.weight(0.38f).height(7.dp).background(c.four))
                }
            }
        }
    }
}

@Composable
private fun FollowArt() {
    val c = cricketColors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A match alert, as it appears in the notification shade.
        ArtCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_stat_criczy), contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wicket · LIO v HAW", style = MaterialTheme.typography.titleSmall)
                    Text("HAW 142/4 (17.2)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Followed teams.
            ArtCard(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FOLLOWING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(Triple("LIO", "#1565C0", "Lions"), Triple("HAW", "#C62828", "Hawks")).forEach { (short, hex, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(short, null, hex, size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Star, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            // The home-screen widget.
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(22.dp), color = Color(0xFF0B1018), border = BorderStroke(1.dp, Color(0xFF283344)), shadowElevation = 10.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WIDGET", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA9B3C3), modifier = Modifier.weight(1f))
                        Text("● LIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5449))
                    }
                    listOf("LIO" to "182/6", "HAW" to "171/4").forEach { (team, score) ->
                        Row {
                            Text(team, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Text(score, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("WORKS OFFLINE", container = c.accent.copy(alpha = 0.18f), content = c.accent)
            Pill("NO ACCOUNT NEEDED")
        }
    }
}
