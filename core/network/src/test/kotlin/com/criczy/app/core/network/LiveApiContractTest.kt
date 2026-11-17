package com.criczy.app.core.network

import com.criczy.app.core.model.MatchState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Calls the real API to detect format changes. Skipped unless run with `-PliveApi=true`,
 * so ordinary builds stay offline and deterministic.
 */
class LiveApiContractTest {

    private val source: CricketRemoteDataSource = CricketAuRemoteDataSource.create(OkHttpClient())

    @Before
    fun onlyWhenRequested() = assumeTrue(System.getProperty("criczy.liveApi") == "true")

    @Test
    fun `every endpoint answers and maps`() = runBlocking {
        val board = source.matchBoard(upcoming = 10, live = 10, completed = 10)
        assertTrue(board.upcoming.isNotEmpty() && board.completed.isNotEmpty())

        val finished = board.completed.first { it.innings.isNotEmpty() }
        val detail = assertNotNull(source.matchDetail(finished.id))
        assertEquals(finished.id, detail.match.id)
        assertEquals(MatchState.COMPLETED, detail.match.state)
        assertTrue(detail.players.isNotEmpty())

        val innings = source.fullInnings(finished.id, 1)
        assertTrue(innings.isComplete && innings.overs.isNotEmpty())
        assertEquals(detail.innings.first().score.runs, innings.overs.last().inningsRuns)
        assertEquals(detail.innings.first().score.runs, innings.balls.sumOf { it.teamRuns })

        assertTrue(source.commentaryPage(finished.id, 1).overs.isNotEmpty())
        assertTrue(source.competitions(20).size > 10)
        assertTrue(source.teams().size > 50)
        assertEquals(1, source.players(listOf(detail.players.first().id)).size)
        assertTrue(source.news(10).isNotEmpty())
        assertTrue(source.homeFeed().news.isNotEmpty())

        val filtered = source.matchBoard(competitionId = finished.competition.id)
        assertTrue(filtered.all.all { it.competition.id == finished.competition.id })
        Unit
    }

    @Test
    fun `a multi-day innings pages back to the first over`() = runBlocking {
        val board = source.matchBoard(upcoming = 1, live = 30, completed = 60)
        val long = (board.live + board.completed).firstOrNull { m ->
            m.format.isMultiDay && m.innings.any { (it.overs.toDoubleOrNull() ?: 0.0) > 60 }
        }
        assumeTrue("no long innings available right now", long != null)
        val score = long!!.innings.first { (it.overs.toDoubleOrNull() ?: 0.0) > 60 }
        val innings = source.fullInnings(long.id, score.inningsNumber)
        assertTrue(innings.isComplete)
        assertEquals(1, innings.overs.first().number)
        assertEquals(innings.overs.size, innings.overs.map { it.number }.distinct().size)
        assertEquals(score.runs, innings.overs.last().inningsRuns)
    }
}
