package app.criczy.feature.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    Page(Icons.Outlined.SportsCricket, "Every ball, as it happens", "Live scores, full scorecards and ball-by-ball commentary for international and domestic cricket."),
    Page(Icons.Outlined.Insights, "See the match, not just the score", "Worm and Manhattan graphs, wagon wheels, pitch maps, partnerships and batter-v-bowler match-ups, built from real ball data."),
    Page(Icons.Outlined.NotificationsActive, "Follow what matters to you", "Star your teams, series and matches to see them first, get match alerts, and keep reading when you're offline."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onFinish) { Text("Skip") } }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(132.dp).background(cricketColors.accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(page.icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = cricketColors.accent)
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 20.dp)) {
                repeat(pages.size) { index ->
                    Box(Modifier.size(if (index == pager.currentPage) 10.dp else 8.dp).background(if (index == pager.currentPage) cricketColors.accent else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
            }
            val last = pager.currentPage == pages.lastIndex
            Button(onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (last) "Get started" else "Next")
            }
        }
    }
}
