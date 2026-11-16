package app.criczy.core.network

import app.criczy.core.model.Competition
import app.criczy.core.model.Highlight
import app.criczy.core.model.HomeFeed
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.NewsArticle
import app.criczy.core.model.Player
import app.criczy.core.model.Standings
import app.criczy.core.model.Team

/**
 * The seam between the app and whoever supplies cricket data.
 *
 * Everything above this interface speaks only `core:model` types, so replacing the provider
 * means writing one new implementation of this interface and changing one binding.
 * Implementations throw [CricketApiException] and nothing else.
 */
interface CricketRemoteDataSource {

    /** Live, upcoming and completed matches, optionally narrowed to a competition or team. */
    suspend fun matchBoard(
        upcoming: Int = 30,
        live: Int = 30,
        completed: Int = 30,
        competitionId: Int? = null,
        teamId: Int? = null,
    ): MatchBoard

    /** Null when the provider has no such match. */
    suspend fun matchDetail(matchId: Int): MatchDetail?

    /** The most recent overs of an innings, for live commentary. Page back with [olderThanOver]. */
    suspend fun commentaryPage(matchId: Int, inningsNumber: Int, olderThanOver: Int? = null): InningsBallByBall

    /** Every over of an innings bowled so far, for charts and analysis. */
    suspend fun fullInnings(matchId: Int, inningsNumber: Int): InningsBallByBall

    suspend fun standings(competitionId: Int): Standings

    suspend fun competitions(limit: Int = 50): List<Competition>

    suspend fun teams(): List<Team>

    suspend fun players(ids: Collection<Int>): List<Player>

    suspend fun news(limit: Int = 30): List<NewsArticle>

    suspend fun homeFeed(): HomeFeed

    suspend fun highlights(matchId: Int, competitionId: Int): List<Highlight>
}

/** Why a request failed, in terms the UI can act on. */
sealed class CricketApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, DNS failure or timeout: worth retrying, and cached data should be shown. */
    class Network(cause: Throwable) : CricketApiException("Network unavailable", cause)

    /** The server answered with an error status. [retryable] is true for 429 and 5xx. */
    class Server(val code: Int, message: String) : CricketApiException("HTTP $code: $message") {
        val retryable: Boolean get() = code == 429 || code >= 500
    }

    /** The response could not be understood, which usually means the provider changed its format. */
    class Malformed(cause: Throwable) : CricketApiException("Unexpected response format", cause)
}
