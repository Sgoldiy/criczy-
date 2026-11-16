package app.pavilion.core.network

import app.pavilion.core.model.Competition
import app.pavilion.core.model.Highlight
import app.pavilion.core.model.HomeFeed
import app.pavilion.core.model.InningsBallByBall
import app.pavilion.core.model.MatchBoard
import app.pavilion.core.model.MatchDetail
import app.pavilion.core.model.NewsArticle
import app.pavilion.core.model.Player
import app.pavilion.core.model.Standings
import app.pavilion.core.model.Team
import app.pavilion.core.network.dto.ErrorEnvelopeDto
import app.pavilion.core.network.dto.InningsDto
import app.pavilion.core.network.mapper.toBallByBall
import app.pavilion.core.network.mapper.toModel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/** [CricketRemoteDataSource] backed by the cricket.com.au API. */
class CricketAuRemoteDataSource internal constructor(
    private val api: CricketAuApi,
    private val region: String,
) : CricketRemoteDataSource {

    override suspend fun matchBoard(
        upcoming: Int,
        live: Int,
        completed: Int,
        competitionId: Int?,
        teamId: Int?,
    ): MatchBoard = call {
        api.fixtures(
            upcoming = upcoming.coerceIn(1, MAX_FIXTURES),
            inProgress = live.coerceIn(1, MAX_FIXTURES),
            completed = completed.coerceIn(1, MAX_FIXTURES),
            competitionId = competitionId,
            teamId = teamId,
        ).toModel()
    }

    override suspend fun matchDetail(matchId: Int): MatchDetail? = call { api.scorecard(matchId).toModel() }

    override suspend fun commentaryPage(matchId: Int, inningsNumber: Int, olderThanOver: Int?): InningsBallByBall =
        call {
            val innings = api.commentsPage(matchId, inningsNumber, olderThanOver)
                .innings.firstOrNull { it.inningNumber == inningsNumber }
            (innings ?: InningsDto(inningNumber = inningsNumber)).toBallByBall(matchId)
        }

    override suspend fun fullInnings(matchId: Int, inningsNumber: Int): InningsBallByBall = call {
        var merged = fetchBulk(matchId, inningsNumber, null)
        // Long innings come back in pages of BULK_OVERS; walk back until over 1 is reached.
        var pages = 1
        while (merged.olderThanOver != null && pages < MAX_BULK_PAGES) {
            val older = fetchBulk(matchId, inningsNumber, merged.olderThanOver)
            if (older.overs.isEmpty() || older.overs.first().number >= merged.overs.first().number) break
            val known = merged.overs.mapTo(HashSet()) { it.number }
            merged = older.copy(overs = older.overs.filterNot { it.number in known } + merged.overs)
            pages++
        }
        merged
    }

    private suspend fun fetchBulk(matchId: Int, inningsNumber: Int, lastOver: Int?): InningsBallByBall {
        val dto = api.commentsBulk(matchId, inningsNumber, BULK_OVERS, lastOver).inning
        return (dto ?: InningsDto(inningNumber = inningsNumber)).toBallByBall(matchId)
    }

    override suspend fun standings(competitionId: Int): Standings =
        call { api.standings(competitionId).toModel(competitionId) }

    override suspend fun competitions(limit: Int): List<Competition> =
        call { api.competitions(limit).competitions.map { it.toModel() } }

    override suspend fun teams(): List<Team> =
        call { api.teams().teams.filter { it.isActive }.map { it.toModel() } }

    override suspend fun players(ids: Collection<Int>): List<Player> {
        if (ids.isEmpty()) return emptyList()
        return call {
            ids.distinct().chunked(PLAYER_BATCH).flatMap { batch ->
                api.players(batch.joinToString(",")).players.map { it.toModel() }
            }
        }
    }

    override suspend fun news(limit: Int): List<NewsArticle> =
        call { api.news(region, limit).news.mapNotNull { it.toModel() } }

    override suspend fun homeFeed(): HomeFeed = call { api.home(region).toModel() }

    override suspend fun highlights(matchId: Int, competitionId: Int): List<Highlight> =
        call { api.highlights(matchId, competitionId).videos.map { it.toModel() } }

    /** Translates transport and parsing failures into [CricketApiException]. */
    private inline fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw CricketApiException.Server(e.code(), e.serverMessage())
        } catch (e: IOException) {
            throw CricketApiException.Network(e)
        } catch (e: SerializationException) {
            throw CricketApiException.Malformed(e)
        }

    private fun HttpException.serverMessage(): String {
        val body = try {
            response()?.errorBody()?.string()
        } catch (_: IOException) {
            null
        }
        val parsed = body?.let {
            try {
                json.decodeFromString<ErrorEnvelopeDto>(it).status?.message
            } catch (_: SerializationException) {
                null
            }
        }
        return parsed?.takeIf { it.isNotBlank() } ?: message()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://apiv2.cricket.com.au/"
        private const val MAX_FIXTURES = 100
        private const val BULK_OVERS = 50
        private const val MAX_BULK_PAGES = 6
        private const val PLAYER_BATCH = 40

        internal val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        /**
         * @param client supply the app's shared client so the connection pool and cache are reused.
         * @param region two-letter region used for news and the home feed.
         */
        fun create(
            client: OkHttpClient,
            baseUrl: String = DEFAULT_BASE_URL,
            region: String = "AU",
        ): CricketAuRemoteDataSource {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return CricketAuRemoteDataSource(retrofit.create(CricketAuApi::class.java), region)
        }
    }
}
