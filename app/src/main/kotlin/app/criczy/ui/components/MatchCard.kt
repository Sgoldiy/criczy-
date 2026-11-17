package app.criczy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.model.Match
import app.criczy.core.model.MatchState
import app.criczy.core.model.Team
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import app.criczy.ui.theme.parseTeamColor

/** Spoken summary of a match card, so screen readers get one sentence instead of fragments. */
fun matchDescription(match: Match): String = buildString {
    append(match.home.name).append(" versus ").append(match.away.name).append(". ")
    for (team in listOf(match.home, match.away)) {
        val scores = match.inningsFor(team.id)
        if (scores.isNotEmpty()) {
            append(team.name).append(' ').append(scores.joinToString(" and ") { "${it.runs} for ${it.wickets}" }).append(". ")
        }
    }
    append(Formatters.statusLine(match))
}

@Composable
private fun TeamScoreRow(match: Match, team: Team, emphasise: Boolean) {
    val scores = match.inningsFor(team.id)
    val batting = match.state != MatchState.COMPLETED && match.currentInnings?.battingTeamId == team.id
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Badge(team.shortName, team.logoUrl, team.colorHex, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasise) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (scores.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = scores.joinToString(" & ") { it.scoreText },
                fontFamily = ScoreFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (emphasise || batting) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "(${scores.last().overs})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MatchCard(
    match: Match,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
) {
    val description = matchDescription(match)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Open match", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row {
            // Accent rail in the two team colours: a quick visual identity for the fixture.
            val top = parseTeamColor(match.home.colorHex) ?: MaterialTheme.colorScheme.primary
            val bottom = parseTeamColor(match.away.colorHex) ?: MaterialTheme.colorScheme.tertiary
            Box(Modifier.width(4.dp).height(118.dp).background(Brush.verticalGradient(listOf(top, bottom))))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOf(match.name, match.competition.name).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFavourite) {
                        Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = cricketColors.accent, modifier = Modifier.padding(start = 6.dp).height(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    when (match.state) {
                        MatchState.LIVE -> LivePill()
                        MatchState.IN_PROGRESS -> Pill(match.day?.let { "DAY $it" } ?: "IN PROGRESS", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                        else -> Pill(match.format.label.uppercase())
                    }
                }
                TeamScoreRow(match, match.home, emphasise = match.winnerTeamId == match.home.id)
                TeamScoreRow(match, match.away, emphasise = match.winnerTeamId == match.away.id)
                Text(
                    text = Formatters.statusLine(match),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (match.state) {
                        MatchState.LIVE -> cricketColors.live
                        MatchState.COMPLETED -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (match.state == MatchState.COMPLETED) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Large hero card for live matches at the top of the home screen. */
@Composable
fun FeaturedMatchCard(match: Match, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val home = parseTeamColor(match.home.colorHex) ?: MaterialTheme.colorScheme.primary
    val away = parseTeamColor(match.away.colorHex) ?: MaterialTheme.colorScheme.tertiary
    val scrim = Color(0xFF0B1018)
    val description = matchDescription(match)
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(home, away)))
            .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.55f), scrim.copy(alpha = 0.88f))))
            .clickable(onClickLabel = "Open match", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    match.competition.name, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (match.state == MatchState.LIVE) LivePill() else Pill(match.day?.let { "DAY $it" } ?: match.format.label.uppercase(), container = Color.White.copy(alpha = 0.16f), content = Color.White)
            }
            for (team in listOf(match.home, match.away)) {
                val scores = match.inningsFor(team.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(team.shortName, team.logoUrl, team.colorHex, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(team.shortName, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White, modifier = Modifier.weight(1f))
                    if (scores.isNotEmpty()) {
                        Text(scores.joinToString(" & ") { it.scoreText }, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("(${scores.last().overs})", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    } else {
                        Text("Yet to bat", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
            Text(
                Formatters.statusLine(match), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
