package app.criczy.core.analytics

import app.criczy.core.model.Ball
import kotlin.math.cos
import kotlin.math.sin

/**
 * The eight scoring zones of a wagon wheel, named from the batter's point of view so the same
 * zone means the same thing for left- and right-handers.
 */
enum class FieldZone(val label: String, val isOffSide: Boolean) {
    SQUARE_LEG("Square leg", false),
    FINE_LEG("Fine leg", false),
    THIRD_MAN("Third man", true),
    POINT("Point", true),
    COVER("Cover", true),
    LONG_OFF("Long off", true),
    LONG_ON("Long on", false),
    MID_WICKET("Mid wicket", false),
}

/**
 * A shot placed on a unit ground: the batter is at (0, 0), the bowler's end is towards
 * negative y (up the screen), x runs left to right as seen from behind the batter.
 * [distance] is 0..1 where 1 reaches the boundary.
 */
data class ShotPoint(
    val x: Float,
    val y: Float,
    val distance: Float,
    val zone: FieldZone,
    val runs: Int,
    val isBoundary: Boolean,
    val isWicket: Boolean,
    val batterId: Int,
    val bowlerId: Int,
    /** The provider recorded the shot as hit in the air, controlled or not. */
    val inTheAir: Boolean = false,
    /** Provider angle, unchanged; with [battingHand] it places field labels for this batter. */
    val battingHand: String? = null,
)

data class ZoneSummary(val zone: FieldZone, val shots: Int, val runs: Int, val fours: Int, val sixes: Int)

/**
 * Converts the provider's shot tracking into drawable points and zone totals.
 *
 * The provider reports angles in ground coordinates, measured so that for a right-hander
 * 0° is square on the leg side, 90° is straight behind the wicket, 180° is square on the off
 * side and 270° is straight back past the bowler. This was established from the fielding
 * position recorded alongside each ball (for example "Cover" at 181–207° and "Long On" at
 * 291–297°). A left-hander's leg side is the mirror image, so the angle is reflected first.
 */
object WagonWheel {

    /** Shots hit to the rope are recorded at about this distance; used to scale to 0..1. */
    private const val BOUNDARY_MAGNITUDE = 180f

    private val zonesByOctant = listOf(
        FieldZone.SQUARE_LEG, // 0–45
        FieldZone.FINE_LEG, // 45–90
        FieldZone.THIRD_MAN, // 90–135
        FieldZone.POINT, // 135–180
        FieldZone.COVER, // 180–225
        FieldZone.LONG_OFF, // 225–270
        FieldZone.LONG_ON, // 270–315
        FieldZone.MID_WICKET, // 315–360
    )

    private fun isLeftHander(hand: String?): Boolean = hand.equals("Left", ignoreCase = true)

    /** Angle as it would be for a right-hander, in 0 until 360. */
    internal fun normalisedAngle(angle: Int, battingHand: String?): Int {
        val a = ((angle % 360) + 360) % 360
        return if (isLeftHander(battingHand)) ((180 - a) % 360 + 360) % 360 else a
    }

    /**
     * Where the middle of [zone] is drawn, in the same screen degrees as [point] uses
     * (0° to the left of the batter, 90° down the screen behind the stumps).
     */
    fun screenAngleOf(zone: FieldZone, battingHand: String?): Float {
        val normalised = zonesByOctant.indexOf(zone) * 45f + 22.5f
        return if (isLeftHander(battingHand)) ((180f - normalised) % 360f + 360f) % 360f else normalised
    }

    fun zoneOf(angle: Int, battingHand: String?): FieldZone =
        zonesByOctant[normalisedAngle(angle, battingHand) / 45]

    /** Null when the ball has no shot tracking (leaves, wides, untracked matches). */
    fun point(ball: Ball): ShotPoint? {
        val angle = ball.shotAngle ?: return null
        val magnitude = ball.shotMagnitude ?: return null
        val distance = (magnitude / BOUNDARY_MAGNITUDE).coerceIn(0.05f, 1f)
        // Draw in true ground coordinates so left-handers appear mirrored, as on a broadcast.
        val radians = Math.toRadians((((angle % 360) + 360) % 360).toDouble())
        return ShotPoint(
            x = (-cos(radians) * distance).toFloat(),
            y = (sin(radians) * distance).toFloat(),
            distance = distance,
            zone = zoneOf(angle, ball.battingHand),
            runs = ball.batterRuns,
            isBoundary = ball.isBoundary,
            isWicket = ball.isWicket,
            batterId = ball.batterId,
            bowlerId = ball.bowlerId,
            inTheAir = ball.inTheAir,
            battingHand = ball.battingHand,
        )
    }

    /** Scoring shots only by default: a wagon wheel of dots is noise. */
    fun points(balls: List<Ball>, scoringOnly: Boolean = true): List<ShotPoint> =
        balls.mapNotNull(::point).filter { !scoringOnly || it.runs > 0 || it.isWicket }

    /** Always returns all eight zones, in a fixed order, so charts keep a stable layout. */
    fun zones(balls: List<Ball>): List<ZoneSummary> {
        val byZone = balls.mapNotNull(::point).filter { it.runs > 0 }.groupBy { it.zone }
        return FieldZone.entries.map { zone ->
            val shots = byZone[zone].orEmpty()
            ZoneSummary(
                zone = zone,
                shots = shots.size,
                runs = shots.sumOf { it.runs },
                fours = shots.count { it.runs == 4 && it.isBoundary },
                sixes = shots.count { it.runs == 6 },
            )
        }
    }
}

/** A cell of the pitch map: how deliveries of one line and length fared. */
data class PitchCell(val line: String, val length: String, val balls: Int, val runs: Int, val wickets: Int)

object PitchMap {
    /** Deliveries grouped by the provider's line and length labels; untracked balls are skipped. */
    fun cells(balls: List<Ball>): List<PitchCell> =
        balls.filter { it.line != null && it.length != null }
            .groupBy { it.line!! to it.length!! }
            .map { (key, group) ->
                PitchCell(key.first, key.second, group.size, group.sumOf { it.bowlerRuns }, group.count { it.isWicket })
            }
            .sortedByDescending { it.balls }
}
