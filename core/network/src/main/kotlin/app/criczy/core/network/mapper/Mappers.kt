package app.criczy.core.network.mapper

import app.criczy.core.model.Ball
import app.criczy.core.model.BatterLine
import app.criczy.core.model.BowlerLine
import app.criczy.core.model.Comment
import app.criczy.core.model.Competition
import app.criczy.core.model.Extras
import app.criczy.core.model.FallOfWicket
import app.criczy.core.model.Highlight
import app.criczy.core.model.HomeFeed
import app.criczy.core.model.Innings
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.InningsScore
import app.criczy.core.model.Match
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.MatchFormat
import app.criczy.core.model.MatchState
import app.criczy.core.model.NewsArticle
import app.criczy.core.model.Official
import app.criczy.core.model.Over
import app.criczy.core.model.Partnership
import app.criczy.core.model.Player
import app.criczy.core.model.PlayerRole
import app.criczy.core.model.StandingColumns
import app.criczy.core.model.StandingRow
import app.criczy.core.model.Standings
import app.criczy.core.model.Team
import app.criczy.core.model.Venue
import app.criczy.core.model.VideoClip
import app.criczy.core.network.dto.BallDto
import app.criczy.core.network.dto.CompetitionDto
import app.criczy.core.network.dto.FixtureDto
import app.criczy.core.network.dto.FixturesDto
import app.criczy.core.network.dto.HighlightDto
import app.criczy.core.network.dto.HomeDto
import app.criczy.core.network.dto.InningsDto
import app.criczy.core.network.dto.NewsDto
import app.criczy.core.network.dto.OverDto
import app.criczy.core.network.dto.PlayerDto
import app.criczy.core.network.dto.ScorecardDto
import app.criczy.core.network.dto.StandingsDto
import app.criczy.core.network.dto.TeamDto
import app.criczy.core.network.dto.VenueDto
import app.criczy.core.network.dto.VideoDto
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private fun String.orNullIfBlank(): String? = trim().takeIf { it.isNotEmpty() }

internal fun parseInstant(value: String): Instant? =
    try {
        value.orNullIfBlank()?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }

private val dayFirstDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** The API sends dates of birth as either `1993-06-13` or `13/06/1993`. */
internal fun parseBirthDate(value: String): LocalDate? {
    val text = value.orNullIfBlank() ?: return null
    return try {
        if ('/' in text) LocalDate.parse(text, dayFirstDate) else LocalDate.parse(text.take(10))
    } catch (_: DateTimeParseException) {
        null
    }
}

private val tagPattern = Regex("<[^>]+>")
private val entityPattern = Regex("&(nbsp|amp|quot|#39|rsquo|lsquo|ndash|mdash);")
private val entities = mapOf(
    "nbsp" to " ", "amp" to "&", "quot" to "\"", "#39" to "'",
    "rsquo" to "’", "lsquo" to "‘", "ndash" to "–", "mdash" to "—",
)

/** Player bios arrive as HTML fragments. Paragraph breaks are kept, everything else is dropped. */
internal fun stripHtml(value: String): String? =
    value
        .replace(Regex("</p>\\s*<p[^>]*>|<br\\s*/?>"), "\n\n")
        .replace(tagPattern, "")
        .replace(entityPattern) { entities.getValue(it.groupValues[1]) }
        .trim()
        .takeIf { it.isNotEmpty() }

/** `PascalCase` identifiers such as `OutsideOff` become `Outside off`. */
internal fun humanise(identifier: String): String? {
    val text = identifier.orNullIfBlank()?.takeUnless { it == "Null" || it == "None" } ?: return null
    return text.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar(Char::uppercase)
}

internal fun parseFormat(gameType: String): MatchFormat {
    val text = gameType.lowercase()
    val international = "international" in text || text.endsWith("i")
    return when {
        "test" in text -> MatchFormat.TEST
        "first class" in text || "first-class" in text || "shield" in text || "four day" in text -> MatchFormat.FIRST_CLASS
        "hundred" in text || "100" in text -> MatchFormat.HUNDRED
        "t20" in text || "twenty20" in text || "twenty 20" in text ->
            if (international) MatchFormat.T20I else MatchFormat.T20
        text == "odi" || "one day international" in text -> MatchFormat.ODI
        "one day" in text || "one-day" in text || "list a" in text || "50" in text -> MatchFormat.ONE_DAY
        else -> MatchFormat.OTHER
    }
}

internal fun TeamDto.toModel(): Team = Team(
    id = id,
    name = name,
    shortName = shortName.ifBlank { name.take(3).uppercase() },
    logoUrl = logoUrl.orNullIfBlank(),
    colorHex = teamColor.orNullIfBlank()?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) },
    bannerUrl = backgroundImageUrl.orNullIfBlank(),
    isWomen = isWomensTeam || name.endsWith(" Women"),
)

internal fun CompetitionDto.toModel(): Competition = Competition(
    id = id,
    name = name,
    logoUrl = imageUrl.orNullIfBlank(),
    bannerUrl = bannerImageUrl.orNullIfBlank(),
    start = parseInstant(startDateTime),
    end = parseInstant(endDateTime),
    isWomen = isWomens,
    formatLabel = gameType?.name?.orNullIfBlank() ?: formats.firstOrNull()?.displayName?.orNullIfBlank(),
    tourName = tour?.name?.orNullIfBlank(),
    hasStandings = when (hasStanding.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    },
)

internal fun VenueDto.toModel(): Venue = Venue(
    id = id,
    name = name,
    city = city.orNullIfBlank(),
    country = countryName.orNullIfBlank(),
    latitude = latitude.toDoubleOrNull(),
    longitude = longitude.toDoubleOrNull(),
)

private fun InningsDto.toScore(): InningsScore = InningsScore(
    inningsNumber = inningNumber,
    battingTeamId = battingTeamId,
    runs = runsScored,
    wickets = wickets,
    overs = oversBowled.ifBlank { "0.0" },
    isDeclared = isDeclared,
    isFollowOn = isFollowOn,
)

internal fun FixtureDto.state(): MatchState = when {
    isCompleted -> MatchState.COMPLETED
    isLive -> MatchState.LIVE
    isInProgress -> MatchState.IN_PROGRESS
    else -> MatchState.UPCOMING
}

internal fun FixtureDto.toModel(): Match {
    val home = (homeTeam ?: TeamDto(name = "TBC", shortName = "TBC")).toModel()
    val away = (awayTeam ?: TeamDto(name = "TBC", shortName = "TBC")).toModel()
    val competitionModel = (competition ?: CompetitionDto()).toModel().let { c ->
        if (c.tourName == null && tour?.name?.isNotBlank() == true) c.copy(tourName = tour.name) else c
    }
    return Match(
        id = id,
        name = name,
        competition = competitionModel,
        home = home,
        away = away,
        venue = venue?.takeIf { it.name.isNotBlank() }?.toModel(),
        start = parseInstant(startDateTime),
        end = parseInstant(endDateTime),
        state = state(),
        format = parseFormat(gameType),
        formatLabel = gameType,
        innings = innings.sortedBy { it.inningNumber }.map { it.toScore() },
        statusText = resultText.orNullIfBlank() ?: gameStatus.orNullIfBlank(),
        tossText = tossResult.orNullIfBlank(),
        winnerTeamId = when {
            homeTeam?.isMatchWinner == true -> home.id
            awayTeam?.isMatchWinner == true -> away.id
            else -> null
        },
        day = matchDay.takeIf { it > 0 },
        numberOfDays = numberOfDays.takeIf { it > 0 },
    )
}

internal fun FixturesDto.toModel(): MatchBoard = MatchBoard(
    live = inProgress.map { it.toModel() },
    upcoming = upcoming.map { it.toModel() },
    completed = completed.map { it.toModel() },
)

private fun roleOf(type: String, isWicketKeeper: Boolean): PlayerRole {
    val text = type.lowercase()
    return when {
        isWicketKeeper || "keeper" in text -> PlayerRole.WICKET_KEEPER
        "all" in text -> PlayerRole.ALL_ROUNDER
        "bowl" in text -> PlayerRole.BOWLER
        "bat" in text -> PlayerRole.BATTER
        else -> PlayerRole.UNKNOWN
    }
}

internal fun PlayerDto.toModel(): Player = Player(
    id = id,
    name = displayName.ifBlank { "$firstName $lastName".trim() },
    firstName = firstName.orNullIfBlank(),
    lastName = lastName.orNullIfBlank(),
    teamId = teamId,
    role = roleOf(type, isWicketKeeper),
    roleLabel = type.orNullIfBlank(),
    battingHand = battingHand.orNullIfBlank(),
    bowlingHand = bowlingHand.orNullIfBlank(),
    bowlingType = bowlingType.orNullIfBlank(),
    dateOfBirth = parseBirthDate(dob),
    birthPlace = birthPlace.orNullIfBlank(),
    nationality = nationality.orNullIfBlank(),
    height = height.orNullIfBlank(),
    imageUrl = imageUrl.orNullIfBlank() ?: details.firstNotNullOfOrNull { it.imageUrl.orNullIfBlank() },
    bio = stripHtml(bio),
    didYouKnow = stripHtml(didYouKnow),
    isCaptain = isCaptain,
    isWicketKeeper = isWicketKeeper,
    isSubstitute = isTwelfthMan,
    isPlayerOfTheMatch = isManOfTheMatch,
    order = order,
)

private fun InningsDto.toModel(names: Map<Int, String>): Innings {
    fun name(id: Int) = names[id] ?: "Player $id"
    return Innings(
        number = inningNumber,
        battingTeamId = battingTeamId,
        bowlingTeamId = bowlingTeamId,
        score = toScore(),
        runRate = currentRunRate,
        // The API sends 0 for "not applicable" in non-chasing innings.
        requiredRunRate = requiredRunRate?.takeIf { it > 0.0 },
        ballsRemaining = ballsRemaining?.takeIf { it > 0 },
        batters = batsmen.sortedBy { it.battingOrder }.map {
            BatterLine(
                playerId = it.playerId,
                name = name(it.playerId),
                runs = it.runsScored,
                balls = it.ballsFaced,
                fours = it.fours,
                sixes = it.sixes,
                strikeRate = it.strikeRate,
                minutes = it.battingMinutes?.takeIf { m -> m > 0 },
                isOut = it.isOut,
                dismissal = it.dismissalText.orNullIfBlank(),
                isAtCrease = it.isBatting,
                isOnStrike = it.isOnStrike,
                order = it.battingOrder,
            )
        },
        bowlers = bowlers.sortedBy { it.order }.map {
            BowlerLine(
                playerId = it.playerId,
                name = name(it.playerId),
                overs = it.oversBowled.ifBlank { "0" },
                maidens = it.maidens,
                runs = it.runsConceded,
                wickets = it.wickets,
                economy = it.economy,
                dots = it.dotBalls,
                wides = it.wides,
                noBalls = it.noBalls,
                isBowlingNow = it.isOnStrike,
                order = it.order,
            )
        },
        fallOfWickets = fallOfWickets.sortedBy { it.order }.map {
            FallOfWicket(it.order, it.playerId, name(it.playerId), it.runs, it.over)
        },
        extras = Extras(totalExtras, wides, noBalls, byes, legByes, penalties),
        currentPartnership = partnership?.takeIf { it.firstPlayerId != 0 || it.secondPlayerId != 0 }?.let {
            Partnership(
                firstPlayerId = it.firstPlayerId,
                firstName = name(it.firstPlayerId),
                firstRuns = it.firstRuns,
                firstBalls = it.firstBalls,
                secondPlayerId = it.secondPlayerId,
                secondName = name(it.secondPlayerId),
                secondRuns = it.secondRuns,
                secondBalls = it.secondBalls,
                runs = it.totalRuns,
                balls = it.totalBalls,
                runRate = it.runRate,
                isCurrent = it.isCurrent,
            )
        },
    )
}

private fun officialRole(type: String): String = when (type) {
    "OnField" -> "Umpire"
    "ThirdUmpire", "TvUmpire", "TVUmpire" -> "TV umpire"
    "Referee", "MatchReferee" -> "Match referee"
    "Reserve", "ReserveUmpire", "FourthUmpire" -> "Reserve umpire"
    else -> humanise(type) ?: "Official"
}

/** Returns null when the response carries no fixture, which the API does for unknown ids. */
internal fun ScorecardDto.toModel(): MatchDetail? {
    val fixtureDto = fixture?.takeIf { it.id != 0 } ?: return null
    val playerModels = players.map { it.toModel() }
    val names = playerModels.associate { it.id to it.name }
    return MatchDetail(
        match = fixtureDto.toModel(),
        innings = fixtureDto.innings.sortedBy { it.inningNumber }.map { it.toModel(names) },
        players = playerModels,
        officials = fixtureDto.officials.map {
            Official(name = "${it.firstName} ${it.lastName}".trim(), role = officialRole(it.type))
        }.filter { it.name.isNotEmpty() },
        oversPerInnings = fixtureDto.totalOvers.toIntOrNull()?.takeIf { it > 0 },
        oversRemaining = fixtureDto.oversRemaining.orNullIfBlank(),
        ticketUrl = fixtureDto.ticketUrl.orNullIfBlank()?.takeIf { it.startsWith("https://") },
        hasCommentary = hasCommentary ?: true,
    )
}

private fun BallDto.toModel(overNumber: Int): Ball = Ball(
    id = id,
    overNumber = overNumber,
    ballNumber = ballNumber,
    time = parseInstant(ballDateTime),
    batterId = batterId,
    nonStrikerId = nonStrikerId,
    bowlerId = bowlerId,
    batterRuns = runsScored,
    // TeamRuns is absent on some older fixtures; Runs carries the same total there.
    teamRuns = maxOf(teamRuns, runs),
    bowlerRuns = runsConceded,
    isWicket = isWicket,
    dismissalType = humanise(dismissalType.ifBlank { dismissalTypeId }),
    isWide = isWide,
    isNoBall = isNoBall,
    isBye = isByes,
    isLegBye = isLegByes,
    isBoundary = isBoundary,
    isFreeHit = isFreeHit,
    isPowerPlay = isPowerPlay,
    // Angle 0 with no distance is the API's "no shot recorded" value.
    shotAngle = shotAngle?.takeIf { (shotMagnitude ?: 0) > 0 },
    shotMagnitude = shotMagnitude?.takeIf { it > 0 },
    battingHand = battingHandId.orNullIfBlank(),
    line = humanise(lineTypeId),
    length = humanise(lengthTypeId),
    shot = humanise(shotTypeId),
    connection = humanise(connectionId),
    footwork = humanise(feetId),
    fieldingPosition = fieldingPosition.orNullIfBlank(),
    bowlingDetail = humanise(bowlingDetailId),
    inTheAir = isAirControlled || isAirNotControlled,
    commentary = comments.sortedBy { it.order }.map { Comment(it.type, it.message.trim(), it.order) },
)

internal fun OverDto.toModel(): Over = Over(
    number = overNumber,
    runs = totalRuns.takeIf { it > 0 } ?: (runsConceded + runsExtras),
    wickets = wickets,
    extras = runsExtras,
    inningsRuns = totalInningRuns,
    inningsWickets = totalInningWickets,
    runRate = runRate,
    balls = balls.sortedBy { it.ballNumber }.map { it.toModel(overNumber) },
)

internal fun InningsDto.toBallByBall(matchId: Int): InningsBallByBall {
    val overModels = overs.map { it.toModel() }.sortedBy { it.number }
    val lowest = overModels.firstOrNull()?.number
    return InningsBallByBall(
        matchId = fixtureId.takeIf { it != 0 } ?: matchId,
        inningsNumber = inningNumber,
        battingTeamId = battingTeamId,
        bowlingTeamId = bowlingTeamId,
        overs = overModels,
        isComplete = lowest == null || lowest <= 1,
        olderThanOver = lowest?.takeIf { it > 1 },
    )
}

internal fun StandingsDto.toModel(competitionId: Int): Standings = Standings(
    competitionId = competitionId,
    columns = StandingColumns(
        drawn = flags?.hasDrawn ?: standings.any { it.drawn > 0 },
        tied = flags?.hasTied ?: standings.any { it.tied > 0 },
        noResult = flags?.hasNoResult ?: standings.any { it.noResult > 0 },
        bonus = flags?.hasBonus ?: standings.any { it.bonus > 0 },
        netRunRate = flags?.hasNetRunRate ?: standings.any { it.netRunRate != null },
        deductions = flags?.hasDeductedPoints ?: standings.any { it.deductions > 0 },
    ),
    rows = standings.sortedBy { it.position }.map {
        StandingRow(
            position = it.position,
            team = (it.team ?: TeamDto(id = it.teamId, name = it.teamName, shortName = it.shortName)).toModel(),
            group = it.groupName.orNullIfBlank(),
            played = it.played,
            won = it.won,
            lost = it.lost,
            tied = it.tied,
            drawn = it.drawn,
            noResult = it.noResult,
            points = it.points,
            netRunRate = it.netRunRate,
            bonus = it.bonus,
            deductions = it.deductions,
        )
    },
)

internal fun NewsDto.toModel(): NewsArticle? {
    val link = contentUrl.orNullIfBlank()?.takeIf { it.startsWith("https://") } ?: return null
    return NewsArticle(
        id = id,
        title = title.trim(),
        summary = standFirst.orNullIfBlank(),
        imageUrl = imageUrl.orNullIfBlank(),
        url = link,
        published = parseInstant(publishedDate),
        isMatchReport = isMatchReport,
        competitionIds = competitions.map { it.id },
        matchIds = fixtures,
        teamIds = teams,
    )
}

/** Accepts `HH:mm:ss` (home feed) or a millisecond count (highlights). */
internal fun parseDurationSeconds(value: String): Int? {
    val text = value.orNullIfBlank() ?: return null
    if (':' in text) {
        val parts = text.split(':').map { it.toIntOrNull() ?: return null }
        return parts.fold(0) { total, part -> total * 60 + part }
    }
    return text.toLongOrNull()?.let { (it / 1000).toInt() }
}

internal fun VideoDto.toModel(): VideoClip = VideoClip(
    id = videoId,
    title = title.trim(),
    thumbnailUrl = thumbnailUrl.orNullIfBlank(),
    url = videoUrl.orNullIfBlank()?.takeIf { it.startsWith("https://") },
    durationSeconds = parseDurationSeconds(duration),
    published = parseInstant(publishedDate),
)

internal fun HomeDto.toModel(): HomeFeed = HomeFeed(
    news = news.mapNotNull { it.toModel() },
    videos = videos.map { it.toModel() },
    featured = fixtures.map { it.toModel() },
)

internal fun HighlightDto.toModel(): Highlight = Highlight(
    inningsNumber = inningNumber,
    type = type,
    title = title.trim(),
    detail = dismissalText.orNullIfBlank(),
    score = score.orNullIfBlank(),
    over = over.orNullIfBlank(),
    thumbnailUrl = video?.thumbnailUrl?.orNullIfBlank(),
)
