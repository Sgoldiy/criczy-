package app.criczy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.criczy.core.model.Competition
import app.criczy.core.model.NewsArticle
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.cricketColors
import coil3.compose.AsyncImage

/** A series' logo, or its initials ("EBT" for England v Bangladesh Test) until it loads. */
@Composable
fun CompetitionBadge(competition: Competition, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Badge(competition.name.filter { it.isUpperCase() || it.isDigit() }.take(3).ifBlank { competition.name }, competition.logoUrl, null, modifier, size)
}

/** A series to jump into: its badge and name, with a pulsing dot while a ball is being bowled in it. */
@Composable
fun SeriesChip(competition: Competition, live: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (live) cricketColors.live.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClickLabel = "Open series", onClick = onClick)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompetitionBadge(competition, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        if (live) {
            Box(Modifier.size(7.dp).background(cricketColors.live, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(competition.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

/** A story as a compact row: picture on the left, headline and when on the right. */
@Composable
fun NewsRow(article: NewsArticle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Read story", onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = 116.dp, height = 74.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (article.imageUrl != null) {
                AsyncImage(model = article.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(article.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (article.isMatchReport) {
                    Text("MATCH REPORT", style = MaterialTheme.typography.labelSmall, color = cricketColors.accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                Formatters.ago(article.published)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
