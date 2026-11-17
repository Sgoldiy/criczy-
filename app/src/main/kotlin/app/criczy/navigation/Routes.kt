package app.criczy.navigation

import android.net.Uri

/** Every destination in the app. Arguments are ids only; screens load their own data. */
object Routes {
    const val HOME = "home"
    const val SERIES = "series"
    const val NEWS = "news"
    const val FAVOURITES = "favourites"
    const val MORE = "more"

    const val SEARCH = "search"
    const val TEAMS = "teams"
    const val PRIVACY = "privacy"
    const val ONBOARDING = "onboarding"

    const val MATCH = "match/{matchId}"
    const val COMPETITION = "competition/{competitionId}?name={name}"
    const val TEAM = "team/{teamId}"
    const val PLAYER = "player/{playerId}"

    fun match(id: Int) = "match/$id"
    fun competition(id: Int, name: String = "") = "competition/$id?name=${Uri.encode(name)}"
    fun team(id: Int) = "team/$id"
    fun player(id: Int) = "player/$id"

    /** Deep link used by notifications and the widget. */
    const val MATCH_DEEP_LINK = "criczy://match/{matchId}"
    fun matchDeepLink(id: Int): Uri = Uri.parse("criczy://match/$id")

    val topLevel = listOf(HOME, SERIES, NEWS, FAVOURITES, MORE)
}
