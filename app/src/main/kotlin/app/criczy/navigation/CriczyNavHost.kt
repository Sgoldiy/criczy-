package app.criczy.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.criczy.ui.theme.cricketColors
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import app.criczy.ui.components.LocalBottomBarInset
import androidx.navigation.NavBackStackEntry
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
import androidx.compose.runtime.getValue

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

/** Outlined and filled icons cross-fade, and the selected one lifts slightly. */
@Composable
private fun TopLevelIcon(item: TopLevel, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navIconScale",
    )
    Crossfade(selected, animationSpec = tween(TAB_FADE_MS), label = "navIcon") { isSelected ->
        Icon(
            if (isSelected) item.selectedIcon else item.icon, contentDescription = null,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

/**
 * A floating, rounded bar that sits above the system navigation area. A pill slides under the
 * selected icon; every item keeps its label so the bar reads the same to screen readers.
 */
@Composable
private fun FloatingBottomBar(current: String?, onSelect: (String) -> Unit) {
    val accent = cricketColors.accent
    val selectedIndex = destinations.indexOfFirst { it.route == current }.coerceAtLeast(0)
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 8.dp, top = 4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                val slot = maxWidth / destinations.size
                val pillWidth = 52.dp
                val offset by animateDpAsState(
                    targetValue = slot * selectedIndex + (slot - pillWidth) / 2,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    label = "navPill",
                )
                // The sliding pill, drawn once beneath the icons.
                Box(
                    Modifier.offset(x = offset, y = 6.dp).size(pillWidth, 28.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                )
                Row(Modifier.fillMaxSize()) {
                    destinations.forEach { item ->
                        val selected = current == item.route
                        val tint by animateColorAsState(
                            if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, tween(TAB_FADE_MS), label = "navTint",
                        )
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.route) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.padding(top = 6.dp).height(28.dp), contentAlignment = Alignment.Center) {
                                CompositionLocalProvider(LocalContentColor provides tint) { TopLevelIcon(item, selected) }
                            }
                            Text(
                                item.label, color = tint, maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Screen transitions. Switching tabs fades through, the way the bottom bar suggests; opening a
// detail screen slides it in a short distance from the side and back out again.
private const val TAB_FADE_MS = 200
private const val PUSH_MS = 260

private typealias Transition = AnimatedContentTransitionScope<NavBackStackEntry>

private fun Transition.isTabSwitch() =
    initialState.destination.route in Routes.topLevel && targetState.destination.route in Routes.topLevel

private fun Transition.enter(forward: Boolean): EnterTransition = when {
    isTabSwitch() -> fadeIn(tween(TAB_FADE_MS, delayMillis = 60)) + scaleIn(tween(TAB_FADE_MS, delayMillis = 60, easing = FastOutSlowInEasing), initialScale = 0.98f)
    forward -> fadeIn(tween(PUSH_MS)) + slideInHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it / 8 }
    else -> fadeIn(tween(PUSH_MS))
}

private fun Transition.exit(forward: Boolean): ExitTransition = when {
    isTabSwitch() -> fadeOut(tween(90))
    forward -> fadeOut(tween(PUSH_MS / 2))
    else -> fadeOut(tween(PUSH_MS / 2)) + slideOutHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it / 8 }
}

@Composable
fun CriczyNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // Null only before the first destination is set, which is a top-level one.
    val showBars = current == null || current in Routes.topLevel

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
                // The bar slides away under detail screens instead of vanishing.
                AnimatedVisibility(
                    visible = showBars && !useRail,
                    enter = expandVertically(tween(PUSH_MS)) + fadeIn(tween(PUSH_MS)),
                    exit = shrinkVertically(tween(PUSH_MS)) + fadeOut(tween(PUSH_MS / 2)),
                ) {
                    FloatingBottomBar(current) { navController.toTopLevel(it) }
                }
            },
        ) { padding ->
            // On phones the screen scrolls on behind the floating bar instead of stopping above a
            // plain strip; top-level screens add LocalBottomBarInset at the end of their lists.
            val floatingBar = showBars && !useRail
            val layoutDirection = LocalLayoutDirection.current
            val bodyPadding = if (floatingBar) {
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                )
            } else {
                padding
            }
            val barInset = if (floatingBar) padding.calculateBottomPadding() else 0.dp
            Row(Modifier.fillMaxSize().padding(bodyPadding).consumeWindowInsets(padding)) {
                AnimatedVisibility(
                    visible = showBars && useRail,
                    enter = expandHorizontally(tween(PUSH_MS)) + fadeIn(tween(PUSH_MS)),
                    exit = shrinkHorizontally(tween(PUSH_MS)) + fadeOut(tween(PUSH_MS / 2)),
                ) {
                    NavigationRail {
                        destinations.forEach { item ->
                            NavigationRailItem(
                                selected = current == item.route,
                                onClick = { navController.toTopLevel(item.route) },
                                icon = { TopLevelIcon(item, current == item.route) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                  CompositionLocalProvider(LocalBottomBarInset provides barInset) {
                    NavHost(
                        navController, startDestination = Routes.HOME,
                        enterTransition = { enter(forward = true) },
                        exitTransition = { exit(forward = true) },
                        popEnterTransition = { enter(forward = false) },
                        popExitTransition = { exit(forward = false) },
                    ) {
                        composable(Routes.HOME) { HomeRoute(onMatch, onSearch, onCompetition, onUrl) { navController.toTopLevel(Routes.NEWS) } }
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
                        ) { MatchRoute(it.arguments!!.getInt("matchId"), onBack, onPlayer, onTeam, onCompetition, onUrl, onMatch) }
                        composable(
                            Routes.COMPETITION,
                            arguments = listOf(navArgument("competitionId") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" }),
                        ) { CompetitionRoute(it.arguments!!.getInt("competitionId"), it.arguments?.getString("name").orEmpty(), onBack, onMatch, onTeam, onPlayer) }
                        composable(Routes.TEAM, arguments = listOf(navArgument("teamId") { type = NavType.IntType })) {
                            TeamRoute(it.arguments!!.getInt("teamId"), onBack, onMatch)
                        }
                        composable(Routes.PLAYER, arguments = listOf(navArgument("playerId") { type = NavType.IntType })) {
                            PlayerRoute(it.arguments!!.getInt("playerId"), onBack, onMatch, onUrl)
                        }
                    }
                  }
                }
            }
        }
    }
}
