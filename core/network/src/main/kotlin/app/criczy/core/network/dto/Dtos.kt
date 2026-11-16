@file:Suppress("PropertyName")

package app.criczy.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the cricket.com.au API. Every field has a default: the API omits fields freely
// between endpoints and match states, and a missing field must never fail a whole response.

@Serializable
internal data class ErrorEnvelopeDto(
    @SerialName("ResponseError") val responseError: Boolean = false,
    @SerialName("ResponseStatus") val status: ResponseStatusDto? = null,
)

@Serializable
internal data class ResponseStatusDto(
    @SerialName("ErrorCode") val errorCode: String = "",
    @SerialName("Message") val message: String = "",
)

@Serializable
internal data class FixturesDto(
    @SerialName("UpcomingFixtures") val upcoming: List<FixtureDto> = emptyList(),
    @SerialName("InProgressFixtures") val inProgress: List<FixtureDto> = emptyList(),
    @SerialName("CompletedFixtures") val completed: List<FixtureDto> = emptyList(),
)

@Serializable
internal data class FixtureDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Competition") val competition: CompetitionDto? = null,
    @SerialName("HomeTeam") val homeTeam: TeamDto? = null,
    @SerialName("AwayTeam") val awayTeam: TeamDto? = null,
    @SerialName("Venue") val venue: VenueDto? = null,
    @SerialName("Innings") val innings: List<InningsDto> = emptyList(),
    @SerialName("StartDateTime") val startDateTime: String = "",
    @SerialName("EndDateTime") val endDateTime: String = "",
    @SerialName("GameType") val gameType: String = "",
    @SerialName("IsLive") val isLive: Boolean = false,
    @SerialName("IsCompleted") val isCompleted: Boolean = false,
    @SerialName("IsInProgress") val isInProgress: Boolean = false,
    @SerialName("MatchDay") val matchDay: Int = 0,
    @SerialName("NumberOfDays") val numberOfDays: Int = 0,
    @SerialName("ResultText") val resultText: String = "",
    @SerialName("GameStatus") val gameStatus: String = "",
    @SerialName("TossResult") val tossResult: String = "",
    @SerialName("TicketUrl") val ticketUrl: String = "",
    @SerialName("OversRemaining") val oversRemaining: String = "",
    @SerialName("TotalOvers") val totalOvers: String = "",
    @SerialName("IsWomensMatch") val isWomensMatch: Boolean = false,
    @SerialName("Tour") val tour: TourRefDto? = null,
    @SerialName("Officials") val officials: List<OfficialDto> = emptyList(),
)

@Serializable
internal data class TourRefDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
)

@Serializable
internal data class CompetitionDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("ImageUrl") val imageUrl: String = "",
    @SerialName("BannerImageUrl") val bannerImageUrl: String = "",
    @SerialName("StartDateTime") val startDateTime: String = "",
    @SerialName("EndDateTime") val endDateTime: String = "",
    @SerialName("IsWomensCompetition") val isWomens: Boolean = false,
    @SerialName("Formats") val formats: List<FormatDto> = emptyList(),
    @SerialName("GameType") val gameType: GameTypeDto? = null,
    @SerialName("Tour") val tour: TourRefDto? = null,
    /** The API sends this as the strings "true" / "false" / "". */
    @SerialName("HasStanding") val hasStanding: String = "",
)

@Serializable
internal data class FormatDto(@SerialName("DisplayName") val displayName: String = "")

@Serializable
internal data class GameTypeDto(@SerialName("Name") val name: String = "")

@Serializable
internal data class CompetitionsDto(@SerialName("Competitions") val competitions: List<CompetitionDto> = emptyList())

@Serializable
internal data class TeamDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("ShortName") val shortName: String = "",
    @SerialName("TeamColor") val teamColor: String = "",
    @SerialName("LogoUrl") val logoUrl: String = "",
    @SerialName("BackgroundImageUrl") val backgroundImageUrl: String = "",
    @SerialName("IsMatchWinner") val isMatchWinner: Boolean = false,
    @SerialName("IsWomensTeam") val isWomensTeam: Boolean = false,
    @SerialName("IsActive") val isActive: Boolean = true,
)

@Serializable
internal data class TeamsDto(@SerialName("Teams") val teams: List<TeamDto> = emptyList())

@Serializable
internal data class VenueDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("City") val city: String = "",
    @SerialName("CountryName") val countryName: String = "",
    @SerialName("Latitude") val latitude: String = "",
    @SerialName("Longitude") val longitude: String = "",
)

@Serializable
internal data class InningsDto(
    @SerialName("InningNumber") val inningNumber: Int = 0,
    @SerialName("FixtureId") val fixtureId: Int = 0,
    @SerialName("BattingTeamId") val battingTeamId: Int = 0,
    @SerialName("BowlingTeamId") val bowlingTeamId: Int = 0,
    @SerialName("RunsScored") val runsScored: Int = 0,
    @SerialName("NumberOfWicketsFallen") val wickets: Int = 0,
    @SerialName("OversBowled") val oversBowled: String = "",
    @SerialName("BallsRemaining") val ballsRemaining: Int? = null,
    @SerialName("CurrentRunRate") val currentRunRate: Double = 0.0,
    @SerialName("RequiredRunRate") val requiredRunRate: Double? = null,
    @SerialName("IsDeclared") val isDeclared: Boolean = false,
    @SerialName("IsFollowOn") val isFollowOn: Boolean = false,
    @SerialName("TotalExtras") val totalExtras: Int = 0,
    @SerialName("ByesRuns") val byes: Int = 0,
    @SerialName("LegByesRuns") val legByes: Int = 0,
    @SerialName("NoBalls") val noBalls: Int = 0,
    @SerialName("WideBalls") val wides: Int = 0,
    @SerialName("Penalties") val penalties: Int = 0,
    @SerialName("Batsmen") val batsmen: List<BatsmanDto> = emptyList(),
    @SerialName("Bowlers") val bowlers: List<BowlerDto> = emptyList(),
    @SerialName("Wickets") val fallOfWickets: List<WicketDto> = emptyList(),
    @SerialName("Partnership") val partnership: PartnershipDto? = null,
    @SerialName("Overs") val overs: List<OverDto> = emptyList(),
)

@Serializable
internal data class BatsmanDto(
    @SerialName("PlayerId") val playerId: Int = 0,
    @SerialName("RunsScored") val runsScored: Int = 0,
    @SerialName("BallsFaced") val ballsFaced: Int = 0,
    @SerialName("FoursScored") val fours: Int = 0,
    @SerialName("SixesScored") val sixes: Int = 0,
    @SerialName("StrikeRate") val strikeRate: Double = 0.0,
    @SerialName("BattingMinutes") val battingMinutes: Int? = null,
    @SerialName("DismissalText") val dismissalText: String = "",
    @SerialName("IsOut") val isOut: Boolean = false,
    @SerialName("IsBatting") val isBatting: Boolean = false,
    @SerialName("IsOnStrike") val isOnStrike: Boolean = false,
    @SerialName("BattingOrder") val battingOrder: Int = 0,
)

@Serializable
internal data class BowlerDto(
    @SerialName("PlayerId") val playerId: Int = 0,
    @SerialName("OversBowled") val oversBowled: String = "",
    @SerialName("MaidensBowled") val maidens: Int = 0,
    @SerialName("RunsConceded") val runsConceded: Int = 0,
    @SerialName("WicketsTaken") val wickets: Int = 0,
    @SerialName("Economy") val economy: Double = 0.0,
    @SerialName("DotBalls") val dotBalls: Int = 0,
    @SerialName("WideBalls") val wides: Int = 0,
    @SerialName("NoBalls") val noBalls: Int = 0,
    @SerialName("IsOnStrike") val isOnStrike: Boolean = false,
    @SerialName("Order") val order: Int = 0,
)

@Serializable
internal data class WicketDto(
    @SerialName("PlayerId") val playerId: Int = 0,
    @SerialName("Order") val order: Int = 0,
    @SerialName("OverBallDisplay") val over: String = "",
    @SerialName("Runs") val runs: Int = 0,
)

@Serializable
internal data class PartnershipDto(
    @SerialName("FirstPlayerId") val firstPlayerId: Int = 0,
    @SerialName("FirstPlayerRunsScored") val firstRuns: Int = 0,
    @SerialName("FirstPlayerBallsFaced") val firstBalls: Int = 0,
    @SerialName("SecondPlayerId") val secondPlayerId: Int = 0,
    @SerialName("SecondPlayerRunsScored") val secondRuns: Int = 0,
    @SerialName("SecondPlayerBallsFaced") val secondBalls: Int = 0,
    @SerialName("TotalRunsScored") val totalRuns: Int = 0,
    @SerialName("TotalBallsFaced") val totalBalls: Int = 0,
    @SerialName("Runrate") val runRate: Double = 0.0,
    @SerialName("IsCurrent") val isCurrent: Boolean = false,
)

@Serializable
internal data class OfficialDto(
    @SerialName("FirstName") val firstName: String = "",
    @SerialName("LastName") val lastName: String = "",
    @SerialName("UmpireType") val type: String = "",
)

@Serializable
internal data class PlayerDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("TeamId") val teamId: Int? = null,
    @SerialName("DisplayName") val displayName: String = "",
    @SerialName("FirstName") val firstName: String = "",
    @SerialName("LastName") val lastName: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("BattingHand") val battingHand: String = "",
    @SerialName("BowlingHand") val bowlingHand: String = "",
    @SerialName("BowlingType") val bowlingType: String = "",
    @SerialName("DOB") val dob: String = "",
    @SerialName("BirthPlace") val birthPlace: String = "",
    @SerialName("Nationality") val nationality: String = "",
    @SerialName("Height") val height: String = "",
    @SerialName("ImageUrl") val imageUrl: String = "",
    @SerialName("Bio") val bio: String = "",
    @SerialName("DidyouKnow") val didYouKnow: String = "",
    @SerialName("IsCaptain") val isCaptain: Boolean = false,
    @SerialName("IsWicketKeeper") val isWicketKeeper: Boolean = false,
    @SerialName("IsTwelthMan") val isTwelfthMan: Boolean = false,
    @SerialName("IsManOfTheMatch") val isManOfTheMatch: Boolean = false,
    @SerialName("Order") val order: Int = 0,
    @SerialName("PlayerDetails") val details: List<PlayerDetailDto> = emptyList(),
)

@Serializable
internal data class PlayerDetailDto(@SerialName("ImageUrl") val imageUrl: String = "")

@Serializable
internal data class PlayersDto(@SerialName("Players") val players: List<PlayerDto> = emptyList())

@Serializable
internal data class ScorecardDto(
    @SerialName("Fixture") val fixture: FixtureDto? = null,
    @SerialName("Players") val players: List<PlayerDto> = emptyList(),
    @SerialName("HasCommentary") val hasCommentary: Boolean? = null,
)

/** `mobile/views/comments`: all innings, the requested one carrying a page of overs. */
@Serializable
internal data class MobileCommentsDto(
    @SerialName("Innings") val innings: List<InningsDto> = emptyList(),
    @SerialName("NextPage") val nextPage: String = "",
)

/** `web/views/comments`: a single innings with up to `OverLimit` overs. */
@Serializable
internal data class WebCommentsDto(@SerialName("Inning") val inning: InningsDto? = null)

@Serializable
internal data class OverDto(
    @SerialName("OverNumber") val overNumber: Int = 0,
    @SerialName("RunsConceded") val runsConceded: Int = 0,
    @SerialName("Wickets") val wickets: Int = 0,
    @SerialName("RunsExtras") val runsExtras: Int = 0,
    @SerialName("TotalRuns") val totalRuns: Int = 0,
    @SerialName("TotalInningRuns") val totalInningRuns: Int = 0,
    @SerialName("TotalInningWickets") val totalInningWickets: Int = 0,
    @SerialName("Runrate") val runRate: Double = 0.0,
    @SerialName("Balls") val balls: List<BallDto> = emptyList(),
)

@Serializable
internal data class BallDto(
    @SerialName("Id") val id: Long = 0,
    @SerialName("BallDateTime") val ballDateTime: String = "",
    @SerialName("BallNumber") val ballNumber: Int = 0,
    @SerialName("BowlerPlayerId") val bowlerId: Int = 0,
    @SerialName("BattingPlayerId") val batterId: Int = 0,
    @SerialName("NonStrikeBattingPlayerId") val nonStrikerId: Int = 0,
    @SerialName("RunsScored") val runsScored: Int = 0,
    @SerialName("TeamRuns") val teamRuns: Int = 0,
    @SerialName("Runs") val runs: Int = 0,
    @SerialName("RunsConceded") val runsConceded: Int = 0,
    @SerialName("IsWicket") val isWicket: Boolean = false,
    @SerialName("DismissalTypeId") val dismissalTypeId: String = "",
    @SerialName("DismissalType") val dismissalType: String = "",
    @SerialName("IsWide") val isWide: Boolean = false,
    @SerialName("IsNoBall") val isNoBall: Boolean = false,
    @SerialName("IsByes") val isByes: Boolean = false,
    @SerialName("IsLegByes") val isLegByes: Boolean = false,
    @SerialName("IsBoundry") val isBoundary: Boolean = false,
    @SerialName("IsFreeHit") val isFreeHit: Boolean = false,
    @SerialName("IsPowerPlay") val isPowerPlay: Boolean = false,
    @SerialName("IsAirControlled") val isAirControlled: Boolean = false,
    @SerialName("IsAirNotControlled") val isAirNotControlled: Boolean = false,
    @SerialName("ShotAngle") val shotAngle: Int? = null,
    @SerialName("ShotMagnitude") val shotMagnitude: Int? = null,
    @SerialName("BattingHandId") val battingHandId: String = "",
    @SerialName("LineTypeId") val lineTypeId: String = "",
    @SerialName("LengthTypeId") val lengthTypeId: String = "",
    @SerialName("BattingShotTypeId") val shotTypeId: String = "",
    @SerialName("BattingConnectionId") val connectionId: String = "",
    @SerialName("BattingFeetId") val feetId: String = "",
    @SerialName("FieldingPosition") val fieldingPosition: String = "",
    @SerialName("BowlingDetailId") val bowlingDetailId: String = "",
    @SerialName("Comments") val comments: List<CommentDto> = emptyList(),
)

@Serializable
internal data class CommentDto(
    @SerialName("CommentTypeId") val type: String = "",
    @SerialName("Message") val message: String = "",
    @SerialName("Order") val order: Int = 0,
)

@Serializable
internal data class StandingsDto(
    @SerialName("StandingsFlag") val flags: StandingsFlagDto? = null,
    @SerialName("Standings") val standings: List<StandingDto> = emptyList(),
)

@Serializable
internal data class StandingsFlagDto(
    @SerialName("HasDrawn") val hasDrawn: Boolean = false,
    @SerialName("HasTied") val hasTied: Boolean = false,
    @SerialName("HasNoResult") val hasNoResult: Boolean = false,
    @SerialName("HasBonus") val hasBonus: Boolean = false,
    @SerialName("HasNetRunRate") val hasNetRunRate: Boolean = false,
    @SerialName("HasDeductedPoints") val hasDeductedPoints: Boolean = false,
)

@Serializable
internal data class StandingDto(
    @SerialName("Team") val team: TeamDto? = null,
    @SerialName("TeamId") val teamId: Int = 0,
    @SerialName("TeamName") val teamName: String = "",
    @SerialName("ShortName") val shortName: String = "",
    @SerialName("GroupName") val groupName: String = "",
    @SerialName("Position") val position: Int = 0,
    @SerialName("Played") val played: Int = 0,
    @SerialName("Won") val won: Int = 0,
    @SerialName("Lost") val lost: Int = 0,
    @SerialName("MatchTied") val tied: Int = 0,
    @SerialName("Drawn") val drawn: Int = 0,
    @SerialName("NoResult") val noResult: Int = 0,
    @SerialName("Points") val points: Int = 0,
    @SerialName("NetRunRate") val netRunRate: Double? = null,
    @SerialName("Bonus") val bonus: Int = 0,
    @SerialName("Deductions") val deductions: Int = 0,
)

@Serializable
internal data class NewsListDto(@SerialName("News") val news: List<NewsDto> = emptyList())

@Serializable
internal data class NewsDto(
    @SerialName("Id") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("StandFirst") val standFirst: String = "",
    @SerialName("ImageUrl") val imageUrl: String = "",
    @SerialName("ContentUrl") val contentUrl: String = "",
    @SerialName("PublishedDate") val publishedDate: String = "",
    @SerialName("IsMatchReport") val isMatchReport: Boolean = false,
    @SerialName("Competitions") val competitions: List<IdRefDto> = emptyList(),
    @SerialName("Fixtures") val fixtures: List<Int> = emptyList(),
    @SerialName("Teams") val teams: List<Int> = emptyList(),
)

@Serializable
internal data class IdRefDto(@SerialName("Id") val id: Int = 0)

@Serializable
internal data class VideoDto(
    @SerialName("VideoId") val videoId: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("VideoUrl") val videoUrl: String = "",
    @SerialName("ThumbnailUrl") val thumbnailUrl: String = "",
    /** `HH:mm:ss` on the home feed, milliseconds on highlights. */
    @SerialName("Duration") val duration: String = "",
    @SerialName("PublishedDate") val publishedDate: String = "",
)

@Serializable
internal data class HomeDto(
    @SerialName("News") val news: List<NewsDto> = emptyList(),
    @SerialName("Videos") val videos: List<VideoDto> = emptyList(),
    @SerialName("Fixtures") val fixtures: List<FixtureDto> = emptyList(),
)

@Serializable
internal data class HighlightsDto(@SerialName("Videos") val videos: List<HighlightDto> = emptyList())

@Serializable
internal data class HighlightDto(
    @SerialName("InningNumber") val inningNumber: Int = 0,
    @SerialName("HighlightType") val type: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("DismissalText") val dismissalText: String = "",
    @SerialName("Score") val score: String = "",
    @SerialName("Over") val over: String = "",
    @SerialName("Video") val video: VideoDto? = null,
)
