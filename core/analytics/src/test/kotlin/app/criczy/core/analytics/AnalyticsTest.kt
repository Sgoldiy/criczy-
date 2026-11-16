package app.criczy.core.analytics

import app.criczy.core.model.Ball
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.Over
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsTest {

    private var nextId = 1L

    private fun ball(
        over: Int,
        n: Int,
        runs: Int = 0,
        striker: Int = 1,
        nonStriker: Int = 2,
        bowler: Int = 9,
        wicket: Boolean = false,
        wide: Boolean = false,
        noBall: Boolean = false,
        legBye: Boolean = false,
        angle: Int? = null,
        hand: String? = "Right",
        dismissal: String? = null,
    ): Ball {
        val extra = if (wide || noBall) 1 else 0
        val offBat = if (wide || legBye) 0 else runs
        return Ball(
            id = nextId++, overNumber = over, ballNumber = n, time = null,
            batterId = striker, nonStrikerId = nonStriker, bowlerId = bowler,
            batterRuns = if (legBye) runs else offBat, teamRuns = runs + extra,
            bowlerRuns = if (legBye) 0 else runs + extra,
            isWicket = wicket, dismissalType = dismissal, isWide = wide, isNoBall = noBall, isBye = false,
            isLegBye = legBye, isBoundary = runs == 4 || runs == 6, isFreeHit = false, isPowerPlay = over <= 6,
            shotAngle = angle, shotMagnitude = angle?.let { if (runs >= 4) 180 else 90 }, battingHand = hand,
            line = "Outside off", length = if (runs >= 4) "Short" else "Good length", shot = null,
            connection = null, footwork = null, fieldingPosition = null, bowlingDetail = null,
            inTheAir = false, commentary = emptyList(),
        )
    }

    private fun innings(vararg overs: List<Ball>, complete: Boolean = true): InningsBallByBall {
        var total = 0
        var wickets = 0
        val models = overs.mapIndexed { index, balls ->
            total += balls.sumOf { it.teamRuns }
            wickets += balls.count { it.isWicket }
            Over(
                number = balls.first().overNumber, runs = balls.sumOf { it.teamRuns },
                wickets = balls.count { it.isWicket }, extras = 0, inningsRuns = total,
                inningsWickets = wickets, runRate = 0.0, balls = balls,
            ).also { check(it.number == index + overs.first().first().overNumber) }
        }
        return InningsBallByBall(1, 1, 10, 20, models, complete, if (complete) null else models.first().number)
    }

    private val sample = innings(
        // Over 1: 1 . 4 wd . 6 2  -> 14 runs, 6 legal balls
        listOf(
            ball(1, 1, 1), ball(1, 2, 0, striker = 2, nonStriker = 1), ball(1, 3, 4, striker = 2, nonStriker = 1),
            ball(1, 4, 0, striker = 2, nonStriker = 1, wide = true), ball(1, 5, 0, striker = 2, nonStriker = 1),
            ball(1, 6, 6, striker = 2, nonStriker = 1), ball(1, 7, 2, striker = 2, nonStriker = 1),
        ),
        // Over 2: W(batter 1) then batter 3 comes in: 1lb . . 4 .
        listOf(
            ball(2, 1, 0, bowler = 8, wicket = true, dismissal = "Bowled"),
            ball(2, 2, 1, striker = 3, nonStriker = 2, bowler = 8, legBye = true),
            ball(2, 3, 0, striker = 2, nonStriker = 3, bowler = 8), ball(2, 4, 0, striker = 2, nonStriker = 3, bowler = 8),
            ball(2, 5, 4, striker = 2, nonStriker = 3, bowler = 8), ball(2, 6, 0, striker = 2, nonStriker = 3, bowler = 8),
        ),
    )

    @Test
    fun `manhattan and worm follow the over totals`() {
        assertEquals(listOf(OverBar(1, 14, 0), OverBar(2, 5, 1)), InningsAnalytics.manhattan(sample))
        val worm = InningsAnalytics.worm(sample)
        assertEquals(listOf(14, 19), worm.map { it.runs })
        assertEquals(listOf(false, true), worm.map { it.wicketFellThisOver })
    }

    @Test
    fun `run rate counts only legal balls`() {
        val rates = InningsAnalytics.runRateProgression(sample)
        assertEquals(14.0, rates[0].runRate, 0.001)
        assertEquals(9.5, rates[1].runRate, 0.001)
    }

    @Test
    fun `scoring breakdown separates extras from runs off the bat`() {
        val s = InningsAnalytics.scoring(sample.balls)
        assertEquals(12, s.legalBalls)
        assertEquals(17, s.runsOffBat)
        assertEquals(2, s.extras, "one wide and one leg bye")
        assertEquals(1, s.singles)
        assertEquals(1, s.twos)
        assertEquals(2, s.fours)
        assertEquals(1, s.sixes)
        assertEquals(6, s.dots)
        assertEquals(19, s.runsOffBat + s.extras)
        assertEquals(14 * 100.0 / 17, s.boundaryPercent, 0.001)
    }

    @Test
    fun `partnerships split on wickets and credit each batter`() {
        val stands = InningsAnalytics.partnerships(sample)
        assertEquals(2, stands.size)
        with(stands[0]) {
            assertEquals(1, wicket)
            assertEquals(14, runs)
            assertEquals(7, balls)
            assertEquals(1 to 2, batterIds)
            assertEquals(1 to 12, contributions)
            assertEquals(false, unbroken)
        }
        with(stands[1]) {
            assertEquals(5, runs)
            assertEquals(0 to 4, contributions, "the leg bye belongs to neither batter")
            assertTrue(unbroken)
        }
        assertEquals(19, stands.sumOf { it.runs })
    }

    @Test
    fun `partnerships are withheld when overs are missing`() {
        val partial = innings(sample.overs[1].balls, complete = false)
        assertTrue(InningsAnalytics.partnerships(partial).isEmpty())
    }

    @Test
    fun `matchups exclude wides and do not credit run outs to the bowler`() {
        val runOut = innings(listOf(ball(1, 1, 1, wicket = true, dismissal = "Run out"), ball(1, 2, 0, wide = true)))
        val m = InningsAnalytics.matchups(runOut.balls).single()
        assertEquals(1, m.balls)
        assertEquals(0, m.dismissals)

        val vsFirstBowler = InningsAnalytics.matchups(sample.balls).first { it.batterId == 2 && it.bowlerId == 9 }
        assertEquals(5, vsFirstBowler.balls)
        assertEquals(12, vsFirstBowler.runs)
        assertEquals(240.0, vsFirstBowler.strikeRate, 0.001)
    }

    @Test
    fun `phases exist only for limited overs formats`() {
        val phases = InningsAnalytics.phases(sample, 20)
        assertEquals(listOf("Powerplay"), phases.map { it.name })
        assertEquals(19, phases.single().runs)
        assertEquals(9.5, phases.single().runRate, 0.001)
        assertTrue(InningsAnalytics.phases(sample, null).isEmpty())
        assertTrue(InningsAnalytics.phases(sample, 90).isEmpty())
    }

    @Test
    fun `projections need an over limit and at least one over`() {
        assertNull(InningsAnalytics.projectedTotals(40, 30, null))
        assertNull(InningsAnalytics.projectedTotals(3, 3, 20))
        assertNull(InningsAnalytics.projectedTotals(160, 120, 20))
        val projected = InningsAnalytics.projectedTotals(80, 60, 20)!!
        assertEquals(160, projected["Current rate"])
        assertEquals(160, projected["At 8 an over"])
        assertEquals(180, projected["At 10 an over"])
    }

    @Test
    fun `wagon wheel zones match recorded fielding positions`() {
        // Angles taken from real balls where the provider also named the fielding position.
        val expected = mapOf(
            19 to FieldZone.SQUARE_LEG, 61 to FieldZone.FINE_LEG, 105 to FieldZone.THIRD_MAN,
            171 to FieldZone.POINT, 199 to FieldZone.COVER, 264 to FieldZone.LONG_OFF,
            292 to FieldZone.LONG_ON, 325 to FieldZone.MID_WICKET,
        )
        expected.forEach { (angle, zone) -> assertEquals(zone, WagonWheel.zoneOf(angle, "Right"), "angle $angle") }
    }

    @Test
    fun `left handers are mirrored so zones stay batter-relative`() {
        assertEquals(FieldZone.COVER, WagonWheel.zoneOf(199, "Right"))
        assertEquals(FieldZone.MID_WICKET, WagonWheel.zoneOf(199, "Left"))
        assertEquals(FieldZone.COVER, WagonWheel.zoneOf(341, "Left"))
        assertEquals(WagonWheel.zoneOf(280, "Right").isOffSide, !WagonWheel.zoneOf(280, "Left").isOffSide)
    }

    @Test
    fun `shot points are drawn in ground coordinates`() {
        val straight = WagonWheel.point(ball(1, 1, 6, angle = 270))!!
        assertEquals(0f, straight.x, 0.001f)
        assertEquals(-1f, straight.y, 0.001f, "straight hits go up the screen, towards the bowler")
        val cover = WagonWheel.point(ball(1, 2, 1, angle = 180))!!
        assertTrue(cover.x > 0 && cover.distance == 0.5f, "a right-hander's off side is on the right")
        assertNull(WagonWheel.point(ball(1, 3, 0)))

        val zones = WagonWheel.zones(listOf(ball(1, 1, 6, angle = 270), ball(1, 2, 4, angle = 275), ball(1, 3, 0, angle = 10)))
        assertEquals(FieldZone.entries.toList(), zones.map { it.zone })
        with(zones.first { it.zone == FieldZone.LONG_ON }) {
            assertEquals(10, runs); assertEquals(1, fours); assertEquals(1, sixes)
        }
        assertEquals(0, zones.first { it.zone == FieldZone.SQUARE_LEG }.shots, "dot balls are not scoring shots")
    }

    @Test
    fun `pitch map groups by line and length`() {
        val cells = PitchMap.cells(sample.balls)
        assertEquals(sample.balls.size, cells.sumOf { it.balls })
        assertEquals(1, cells.sumOf { it.wickets })
        assertEquals("Good length", cells.first().length)
    }
}
