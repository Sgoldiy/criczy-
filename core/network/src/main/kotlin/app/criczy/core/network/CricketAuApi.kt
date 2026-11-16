package app.criczy.core.network

import app.criczy.core.network.dto.CompetitionsDto
import app.criczy.core.network.dto.FixturesDto
import app.criczy.core.network.dto.HighlightsDto
import app.criczy.core.network.dto.HomeDto
import app.criczy.core.network.dto.MobileCommentsDto
import app.criczy.core.network.dto.NewsListDto
import app.criczy.core.network.dto.PlayersDto
import app.criczy.core.network.dto.ScorecardDto
import app.criczy.core.network.dto.StandingsDto
import app.criczy.core.network.dto.TeamsDto
import app.criczy.core.network.dto.WebCommentsDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The cricket.com.au endpoints the app relies on. Each one was confirmed against the live
 * service; see docs/API.md for parameters, limits and what was probed but does not exist.
 */
internal interface CricketAuApi {

    /** Counts are capped at 100 each by the server and all three are required. */
    @GET("mobile/views/fixtures")
    suspend fun fixtures(
        @Query("UpcomingFixturesCount") upcoming: Int,
        @Query("InProgressFixturesCount") inProgress: Int,
        @Query("CompletedFixturesCount") completed: Int,
        @Query("CompetitionId") competitionId: Int? = null,
        @Query("TeamId") teamId: Int? = null,
    ): FixturesDto

    @GET("mobile/views/scorecard")
    suspend fun scorecard(@Query("FixtureId") fixtureId: Int): ScorecardDto

    /** Returns about ten overs, newest first, older pages via [lastOverNumber]. */
    @GET("mobile/views/comments")
    suspend fun commentsPage(
        @Query("FixtureId") fixtureId: Int,
        @Query("InningNumber") inningNumber: Int,
        @Query("LastOverNumber") lastOverNumber: Int? = null,
    ): MobileCommentsDto

    /** Returns up to [overLimit] overs (server maximum 50) of one innings in a single call. */
    @GET("web/views/comments")
    suspend fun commentsBulk(
        @Query("FixtureId") fixtureId: Int,
        @Query("InningNumber") inningNumber: Int,
        @Query("OverLimit") overLimit: Int,
        @Query("LastOverNumber") lastOverNumber: Int? = null,
    ): WebCommentsDto

    @GET("mobile/views/standings")
    suspend fun standings(@Query("CompetitionId") competitionId: Int): StandingsDto

    @GET("web/competitions")
    suspend fun competitions(@Query("Limit") limit: Int): CompetitionsDto

    @GET("web/teams")
    suspend fun teams(): TeamsDto

    /** Comma-separated ids. The endpoint has no name search. */
    @GET("web/players")
    suspend fun players(@Query("PlayerIds") playerIds: String): PlayersDto

    @GET("mobile/news")
    suspend fun news(@Query("Region") region: String, @Query("Limit") limit: Int): NewsListDto

    @GET("mobile/views/home")
    suspend fun home(@Query("Region") region: String): HomeDto

    @GET("mobile/views/highlights")
    suspend fun highlights(
        @Query("FixtureId") fixtureId: Int,
        @Query("CompetitionId") competitionId: Int,
    ): HighlightsDto
}
