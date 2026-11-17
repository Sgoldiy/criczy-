package app.criczy.feature.match

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.analytics.Leaderboard
import app.criczy.core.analytics.Leaderboards
import app.criczy.core.model.Highlight
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.Player
import app.criczy.ui.components.Badge
import app.criczy.ui.components.Panel
import app.criczy.ui.components.Pill
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors

@Composable
internal fun SquadsTab(detail: MatchDetail, onPlayer: (Int) -> Unit) {
    if (detail.players.isEmpty()) return TabEmpty("Squads not announced", "Teams usually appear here shortly before the toss.")
    var comparing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail.innings.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { comparing = true }) {
                    Icon(Icons.Outlined.CompareArrows, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compare two players in this match")
                }
            }
        }
        for (team in listOf(detail.match.home, detail.match.away)) {
            val squad = detail.squad(team.id)
            if (squad.isEmpty()) continue
            item(key = "squad-${team.id}") {
                Panel {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Badge(team.shortName, team.logoUrl, team.colorHex, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(team.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${squad.count { !it.isSubstitute }} players", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        squad.forEach { PlayerRow(it, onPlayer) }
                    }
                }
            }
        }
    }
    if (comparing) {
        val board = remember(detail) { Leaderboards.compute(listOf(detail)) }
        ComparePlayersSheet(board, "Figures from this match", onDismiss = { comparing = false })
    }
}

@Composable
private fun PlayerRow(player: Player, onPlayer: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open player") { onPlayer(player.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(player.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), player.imageUrl, null, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(player.roleLabel, player.battingStyle, player.bowlingStyle).joinToString(" · ").ifBlank { "Player" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (player.isCaptain) Pill("C", container = cricketColors.accent, content = cricketColors.onAccent)
            if (player.isWicketKeeper) Pill("WK")
            if (player.isSubstitute) Pill("SUB")
            if (player.isPlayerOfTheMatch) Pill("POTM", container = cricketColors.win.copy(alpha = 0.2f), content = cricketColors.win)
        }
    }
}

/**
 * Side-by-side figures for two players, drawn from a [Leaderboard] so the same sheet serves a
 * single match and a whole tournament. [scope] tells the user which matches the numbers cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparePlayersSheet(board: Leaderboard, scope: String, onDismiss: () -> Unit) {
    val names = remember(board) {
        (board.batting.map { it.playerId to it.name } + board.bowling.map { it.playerId to it.name }).distinctBy { it.first }.sortedBy { it.second }
    }
    var first by rememberSaveable { mutableStateOf(board.mostRuns.firstOrNull()?.playerId) }
    var second by rememberSaveable { mutableStateOf(board.mostRuns.getOrNull(1)?.playerId) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text("COMPARE PLAYERS", style = MaterialTheme.typography.titleLarge)
            Text(scope, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(1.dp).padding(top = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerPicker(names, first, Modifier.weight(1f)) { first = it }
                Text("v", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 6.dp))
                PlayerPicker(names, second, Modifier.weight(1f), alignEnd = true) { second = it }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val a = first; val b = second
            if (a == null || b == null) {
                Text("Choose two players to compare.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val batA = board.batting.firstOrNull { it.playerId == a }; val batB = board.batting.firstOrNull { it.playerId == b }
            val bowlA = board.bowling.firstOrNull { it.playerId == a }; val bowlB = board.bowling.firstOrNull { it.playerId == b }
            if (batA != null || batB != null) {
                CompareHeading("Batting")
                CompareRow("Innings", batA?.innings, batB?.innings)
                CompareRow("Runs", batA?.runs, batB?.runs)
                CompareRow("Balls", batA?.balls, batB?.balls, higherIsBetter = null)
                CompareRow("Highest", batA?.highest, batB?.highest)
                CompareRowDecimal("Average", batA?.average, batB?.average)
                CompareRowDecimal("Strike rate", batA?.strikeRate, batB?.strikeRate)
                CompareRow("Fours", batA?.fours, batB?.fours)
                CompareRow("Sixes", batA?.sixes, batB?.sixes)
            }
            if (bowlA != null || bowlB != null) {
                CompareHeading("Bowling")
                CompareRow("Wickets", bowlA?.wickets, bowlB?.wickets)
                CompareText("Overs", bowlA?.overs, bowlB?.overs)
                CompareRow("Runs conceded", bowlA?.runs, bowlB?.runs, higherIsBetter = false)
                CompareRowDecimal("Economy", bowlA?.economy, bowlB?.economy, higherIsBetter = false)
                CompareText("Best", bowlA?.best, bowlB?.best)
                CompareRow("Maidens", bowlA?.maidens, bowlB?.maidens)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        }
    }
}

@Composable
private fun PlayerPicker(names: List<Pair<Int, String>>, selected: Int?, modifier: Modifier, alignEnd: Boolean = false, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                names.firstOrNull { it.first == selected }?.second ?: "Choose player",
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false }) }
        }
    }
}

@Composable
private fun CompareHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun CompareText(label: String, a: String?, b: String?, aWins: Boolean = false, bWins: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a ?: "–", fontFamily = ScoreFont, fontSize = 19.sp, fontWeight = if (aWins) FontWeight.Bold else FontWeight.Medium, color = if (aWins) cricketColors.accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1.3f))
        Text(b ?: "–", fontFamily = ScoreFont, fontSize = 19.sp, fontWeight = if (bWins) FontWeight.Bold else FontWeight.Medium, color = if (bWins) cricketColors.accent else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompareRow(label: String, a: Int?, b: Int?, higherIsBetter: Boolean? = true) {
    val decided = higherIsBetter != null && a != null && b != null && a != b
    CompareText(label, a?.toString(), b?.toString(), aWins = decided && (a!! > b!!) == higherIsBetter, bWins = decided && (b!! > a!!) == higherIsBetter)
}

@Composable
private fun CompareRowDecimal(label: String, a: Double?, b: Double?, higherIsBetter: Boolean = true) {
    val decided = a != null && b != null && a != b
    CompareText(label, a?.let(Formatters::rate), b?.let(Formatters::rate), aWins = decided && (a!! > b!!) == higherIsBetter, bWins = decided && (b!! > a!!) == higherIsBetter)
}

// ---- info ----------------------------------------------------------------------------------

@Composable
internal fun InfoTab(detail: MatchDetail, highlights: List<Highlight>, actions: MatchActions) {
    val match = detail.match
    LazyColumn(Modifier.fillMaxSize(), contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Column(Modifier.padding(bottom = 8.dp)) {
                    PanelTitle("Match")
                    InfoRow("Match", listOf(match.name, match.formatLabel).filter { it.isNotBlank() }.joinToString(" · "))
                    InfoRow("Series", match.competition.name, onClick = { actions.onCompetition(match.competition.id, match.competition.name) })
                    InfoRow("Starts", Formatters.fullDateTime(match.start))
                    match.numberOfDays?.takeIf { it > 1 }?.let { InfoRow("Scheduled", "$it days" + (match.day?.let { d -> " · day $d" } ?: "")) }
                    detail.oversPerInnings?.takeUnless { match.format.isMultiDay }?.let { InfoRow("Overs", "$it per side") }
                    match.tossText?.let { InfoRow("Toss", it) }
                    InfoRow(match.home.shortName, match.home.name, onClick = { actions.onTeam(match.home.id) })
                    InfoRow(match.away.shortName, match.away.name, onClick = { actions.onTeam(match.away.id) })
                }
            }
        }
        match.venue?.let { venue ->
            item {
                Panel {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        PanelTitle("Venue")
                        InfoRow("Ground", venue.name)
                        listOfNotNull(venue.city, venue.country).joinToString(", ").takeIf { it.isNotBlank() }?.let { InfoRow("Location", it) }
                        if (venue.latitude != null && venue.longitude != null) {
                            InfoRow("Map", "Open in maps", onClick = { actions.onOpenUrl("geo:${venue.latitude},${venue.longitude}?q=${venue.latitude},${venue.longitude}(${android.net.Uri.encode(venue.name)})") })
                        }
                        detail.ticketUrl?.let { InfoRow("Tickets", "Buy tickets", onClick = { actions.onOpenUrl(it) }) }
                    }
                }
            }
        }
        if (detail.officials.isNotEmpty()) {
            item {
                Panel {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        PanelTitle("Officials")
                        detail.officials.groupBy { it.role }.forEach { (role, people) ->
                            InfoRow(if (people.size > 1) "${role}s" else role, people.joinToString(", ") { it.name })
                        }
                    }
                }
            }
        }
        if (highlights.isNotEmpty()) {
            item {
                Panel {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        PanelTitle("Key moments", "${highlights.size}")
                        highlights.take(30).forEach { moment ->
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(moment.over ?: "", fontFamily = ScoreFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.width(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(moment.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(moment.detail, moment.score).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Pill(moment.type.removeSuffix("s").uppercase())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

// ---- sharing -------------------------------------------------------------------------------

object ScorecardSharer {
    /** Shares a scorecard image with a text fallback, through the system share sheet. */
    fun share(context: Context, detail: MatchDetail) {
        val text = Formatters.shareText(detail.match) + "\n\nShared from Criczy"
        val image = try {
            ScorecardImage.write(context, detail)
        } catch (_: Exception) {
            null // Text alone is still a useful share.
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, detail.match.title)
            if (image != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share scorecard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
