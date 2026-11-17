package app.criczy.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.criczy.feature.favourites.FavouritesRoute
import app.criczy.feature.home.HomeRoute
import app.criczy.feature.match.MatchRoute
import app.criczy.feature.news.NewsRoute
import app.criczy.feature.player.PlayerRoute
import app.criczy.feature.search.SearchRoute
import app.criczy.feature.series.CompetitionRoute
import app.criczy.feature.series.SeriesListRoute
import app.criczy.feature.settings.PrivacyPolicyScreen
import app.criczy.feature.settings.SettingsRoute
import app.criczy.feature.teams.TeamRoute
import app.criczy.feature.teams.TeamsRoute

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

private val destinations = listOf(
    TopLevel(Routes.HOME, "Matches", Icons.Outlined.Home, Icons.Filled.Home),
    TopLevel(Routes.SERIES, "Series", Icons.Outlined.EmojiEvents),
    TopLevel(Routes.NEWS, "News", Icons.Outlined.Newspaper),
    TopLevel(Routes.FAVOURITES, "Favourites", Icons.Outlined.StarBorder, Icons.Filled.Star),
    TopLevel(Routes.MORE, "More", Icons.Outlined.Menu),
)

/** Opens web links in a Custom Tab and everything else (maps, tickets apps) with the system. */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        if (uri.scheme == "https" || uri.scheme == "http") {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (_: ActivityNotFoundException) {
        // No browser or maps app installed; there is nothing to open the link with.
    }
}

private fun NavHostController.toTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun CriczyNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBars = current in Routes.topLevel

    val onMatch: (Int) -> Unit = { navController.navigate(Routes.match(it)) }
    val onTeam: (Int) -> Unit = { navController.navigate(Routes.team(it)) }
    val onPlayer: (Int) -> Unit = { navController.navigate(Routes.player(it)) }
    val onCompetition: (Int, String) -> Unit = { id, name -> navController.navigate(Routes.competition(id, name)) }
    val onSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }
    val onBack: () -> Unit = { navController.popBackStack() }
    val onUrl: (String) -> Unit = { openUrl(context, it) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones get a bottom bar; tablets, foldables and landscape get a side rail.
        val useRail = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (showBars && !useRail) {
                    NavigationBar {
                        destinations.forEach { item ->
                            NavigationBarItem(
                                selected = current == item.route,
                                onClick = { navController.toTopLevel(item.route) },
                                icon = { Icon(if (current == item.route) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (showBars && useRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            NavigationRailItem(
                                selected = current == item.route,
                                onClick = { navController.toTopLevel(item.route) },
                                icon = { Icon(if (current == item.route) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    NavHost(navController, startDestination = Routes.HOME) {
                        composable(Routes.HOME) { HomeRoute(onMatch, onSearch) }
                        composable(Routes.SERIES) { SeriesListRoute(onCompetition, onSearch) }
                        composable(Routes.NEWS) { NewsRoute(onUrl) }
                        composable(Routes.FAVOURITES) { FavouritesRoute(onMatch, onTeam, onCompetition, onPlayer) { navController.navigate(Routes.TEAMS) } }
                        composable(Routes.MORE) { SettingsRoute(onTeams = { navController.navigate(Routes.TEAMS) }, onPrivacy = { navController.navigate(Routes.PRIVACY) }) }
                        composable(Routes.SEARCH) { SearchRoute(onBack, onMatch, onTeam, onCompetition, onPlayer) }
                        composable(Routes.TEAMS) { TeamsRoute(onBack, onTeam) }
                        composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack) }
                        composable(
                            Routes.MATCH,
                            arguments = listOf(navArgument("matchId") { type = NavType.IntType }),
                            deepLinks = listOf(navDeepLink { uriPattern = Routes.MATCH_DEEP_LINK }),
                        ) { MatchRoute(it.arguments!!.getInt("matchId"), onBack, onPlayer, onTeam, onCompetition, onUrl) }
                        composable(
                            Routes.COMPETITION,
                            arguments = listOf(navArgument("competitionId") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" }),
                        ) { CompetitionRoute(it.arguments!!.getInt("competitionId"), it.arguments?.getString("name").orEmpty(), onBack, onMatch, onTeam, onPlayer) }
                        composable(Routes.TEAM, arguments = listOf(navArgument("teamId") { type = NavType.IntType })) {
                            TeamRoute(it.arguments!!.getInt("teamId"), onBack, onMatch)
                        }
                        composable(Routes.PLAYER, arguments = listOf(navArgument("playerId") { type = NavType.IntType })) {
                            PlayerRoute(it.arguments!!.getInt("playerId"), onBack)
                        }
                    }
                }
            }
        }
    }
}
