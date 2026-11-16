package app.pavilion.core.model

import java.time.Instant

data class StandingRow(
    val position: Int,
    val team: Team,
    val group: String?,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val drawn: Int,
    val noResult: Int,
    val points: Int,
    val netRunRate: Double?,
    val bonus: Int,
    val deductions: Int,
)

/** Which columns the competition actually uses, so tables do not show dead columns. */
data class StandingColumns(
    val drawn: Boolean,
    val tied: Boolean,
    val noResult: Boolean,
    val bonus: Boolean,
    val netRunRate: Boolean,
    val deductions: Boolean,
)

data class Standings(
    val competitionId: Int,
    val columns: StandingColumns,
    val rows: List<StandingRow>,
) {
    val groups: Map<String?, List<StandingRow>>
        get() = rows.groupBy { it.group?.takeIf(String::isNotBlank) }
}

data class NewsArticle(
    val id: Long,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    /** Opens on the publisher's site; the article body is not redistributed in the app. */
    val url: String,
    val published: Instant?,
    val isMatchReport: Boolean,
    val competitionIds: List<Int>,
    val matchIds: List<Int>,
    val teamIds: List<Int>,
)

data class VideoClip(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val url: String?,
    val durationSeconds: Int?,
    val published: Instant?,
)

/** A key moment in a match: a wicket, boundary or milestone. */
data class Highlight(
    val inningsNumber: Int,
    val type: String,
    val title: String,
    val detail: String?,
    val score: String?,
    val over: String?,
    val thumbnailUrl: String?,
)

data class HomeFeed(
    val news: List<NewsArticle>,
    val videos: List<VideoClip>,
    val featured: List<Match>,
)
