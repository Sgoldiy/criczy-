package com.criczy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.criczy.app.core.data.Communities
import com.criczy.app.core.data.CommunityPost
import com.criczy.app.core.data.CommunityProfile
import com.criczy.app.core.data.PostKind
import com.criczy.app.core.model.MatchState
import com.criczy.app.feature.community.ALL_FANS
import com.criczy.app.feature.community.CommunityActions
import com.criczy.app.feature.community.CommunityContent
import com.criczy.app.feature.community.CommunityPhase
import com.criczy.app.feature.community.CommunitySpace
import com.criczy.app.feature.community.CommunityUiState
import com.criczy.app.feature.match.CommentaryTab
import com.criczy.app.feature.match.CommentaryUiState
import com.criczy.app.feature.match.MatchActions
import com.criczy.app.navigation.CriczyNavHost
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the screens used for the Play Store listing, with the captured responses rewritten so the
 * featured matches are India v Australia. Skipped unless run with `-Pstore=true`; the PNGs land in
 * app/build/screenshots/store and are framed with captions by docs/store/make_store_images.js.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same phone as the other UI tests at a higher density, so the zoomed callouts stay sharp.
@Config(qualifiers = "w411dp-h891dp-560dpi")
class StoreScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun indiaVAustralia() {
        assumeTrue(System.getProperty("criczy.store") == "true")
        FixtureServer.offline = false
        FixtureServer.rewrite = ::storeData
    }

    @After
    fun restore() {
        FixtureServer.rewrite = { it }
    }

    private fun shot(name: String) = compose.screenshot("store/$name")

    /** The launcher icon as Android draws it from the vector drawables, to check against docs/store/icon.svg. */
    @Test
    fun `0 launcher icon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun save(name: String, size: Int, draw: (Canvas) -> Unit) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            File("build/screenshots/store").mkdirs()
            File("build/screenshots/store/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun Drawable.drawAt(canvas: Canvas, size: Int) = apply { setBounds(0, 0, size, size) }.draw(canvas)
        val launcher = checkNotNull(context.getDrawable(R.mipmap.ic_launcher))
        save("icon-launcher", 432) { launcher.drawAt(it, 432) }
        val mono = checkNotNull(context.getDrawable(R.drawable.ic_launcher_monochrome)).mutate().apply { setTint(0xFF1B3A7A.toInt()) }
        save("icon-themed", 432) { it.drawColor(0xFFD7E3FF.toInt()); mono.drawAt(it, 432) }
        val stat = checkNotNull(context.getDrawable(R.drawable.ic_stat_criczy))
        save("icon-notification", 96) { it.drawColor(0xFF11151C.toInt()); stat.drawAt(it, 96) }
    }

    private fun launchApp() = compose.setCriczyContent { CriczyNavHost() }

    @Test
    fun `1 home`() {
        launchApp()
        compose.awaitText("India")
        compose.waitForIdle()
        shot("1-home")
    }

    @Test
    fun `2 live test match`() {
        launchApp()
        compose.awaitText("India")
        compose.onAllNodesWithText("India v Australia Tests", substring = true).onFirst().performClick()
        compose.awaitText("Target")
        shot("2-live-test")
    }

    @Test
    fun `3 shot replay`() {
        val remote = FixtureServer.remote()
        val (detail, page) = runBlocking { remote.matchDetail(40644)!! to remote.commentaryPage(40644, inningsNumber = 2) }
        val live = detail.copy(match = detail.match.copy(state = MatchState.LIVE))
        val feed = CommentaryUiState(inningsNumber = 2, overs = page.overs.sortedByDescending { it.number }, loading = false)
        val none = MatchActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
        compose.setCriczyContent { Surface { CommentaryTab(live, feed, none) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("See shot · WICKET").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("See shot · FOUR"))
        compose.onAllNodesWithText("See shot · FOUR").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("four", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        shot("3-shot-replay")
    }

    private fun openT20() {
        launchApp()
        compose.awaitText("India")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Australia win by 6 runs")
        compose.onAllNodesWithText("Australia win by 6 runs").onFirst().performClick()
        compose.awaitText("Scorecard")
    }

    @Test
    fun `4 analysis`() {
        openT20()
        compose.onNodeWithText("Analysis").performScrollTo().performClick()
        compose.awaitText("WORM")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("WAGON WHEEL", substring = true))
        shot("4-analysis-wagon")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("CONTROL, PACE V SPIN", substring = true))
        shot("4-analysis-control")
    }

    @Test
    fun `5 live scorecard`() {
        launchApp()
        compose.awaitText("India")
        compose.onAllNodesWithText("India v Australia Tests", substring = true).onFirst().performClick()
        compose.awaitText("Target")
        compose.onNodeWithText("Scorecard").performClick()
        compose.awaitText("Yashasvi Jaiswal")
        shot("5-scorecard")
    }

    @Test
    fun `6 community`() {
        val now = 1_800_000_000_000L
        val hour = 3_600_000L
        val live = runBlocking { FixtureServer.remote().matchBoard() }.live.first { it.id == 41015 }
        val team = CommunitySpace(Communities.team(live.home.id), live.home.name, live.home.shortName, live.home.logoUrl, live.home.colorHex)
        val australia = CommunitySpace(Communities.team(live.away.id), live.away.name, live.away.shortName, live.away.logoUrl, live.away.colorHex)
        fun post(id: String, author: String, text: String, ageHours: Double, kind: PostKind = PostKind.OPINION, options: List<String> = emptyList(), counts: List<Int> = listOf(0, 0), myVote: Int? = null) =
            CommunityPost(id, team.id, "uid-$author", author, text, kind, options, counts, 0, now - (ageHours * hour).toLong(), now - (ageHours * hour).toLong() + Communities.LIFETIME_MS, myVote)
        val posts = listOf(
            CommunityPost(
                "match-41015-win-${team.id}", team.id, Communities.SYSTEM_UID, Communities.SYSTEM_NAME, "Who wins? ${live.title}", PostKind.POLL,
                listOf(live.home.name, live.away.name, "Draw"), listOf(184, 121, 37), 0, now - hour, now + 23 * hour, myVote = 0,
            ),
            post("1", "bumrah_army", "Bumrah with the new ball tomorrow morning. Two quick wickets and this chase is over.", 0.5, counts = listOf(96, 14), myVote = 0),
            post("2", "stats_guru", "Who scores the most runs tomorrow?", 2.0, PostKind.POLL, listOf("Shubman Gill", "Rishabh Pant", "Steve Smith"), listOf(58, 41, 23)),
        )
        compose.setCriczyContent {
            Surface {
                CommunityContent(
                    CommunityUiState(
                        phase = CommunityPhase.READY, profile = CommunityProfile("me", "cover_drive", now - 2 * hour),
                        spaces = listOf(ALL_FANS, team, australia), selected = team.id, posts = posts, featured = live, now = now,
                    ),
                    CommunityActions(),
                )
            }
        }
        compose.awaitText("Who wins?")
        shot("6-community")
    }

    // ---------------------------------------------------------------- tablets, landscape, at xhdpi

    private fun tabletMatchCentre(prefix: String) {
        launchApp()
        compose.awaitText("India")
        shot("$prefix-1-home")
        compose.onAllNodesWithText("India v Australia Tests", substring = true).onFirst().performClick()
        compose.awaitText("Target")
        shot("$prefix-2-match")
        compose.onNodeWithText("Scorecard").performClick()
        compose.awaitText("Yashasvi Jaiswal")
        shot("$prefix-3-scorecard")
    }

    private fun tabletAnalysis(prefix: String) {
        openT20()
        compose.onNodeWithText("Analysis").performScrollTo().performClick()
        compose.awaitText("WORM")
        shot("$prefix-4-analysis")
    }

    @Test
    @Config(qualifiers = TABLET_7)
    fun `7 inch tablet, home and match centre`() = tabletMatchCentre("tablet7")

    @Test
    @Config(qualifiers = TABLET_7)
    fun `7 inch tablet, analysis`() = tabletAnalysis("tablet7")

    @Test
    @Config(qualifiers = TABLET_10)
    fun `10 inch tablet, home and match centre`() = tabletMatchCentre("tablet10")

    @Test
    @Config(qualifiers = TABLET_10)
    fun `10 inch tablet, analysis`() = tabletAnalysis("tablet10")
}

private const val TABLET_7 = "w960dp-h600dp-xhdpi"
private const val TABLET_10 = "w1280dp-h800dp-xhdpi"

/** Old name to new name, longest first so a full name is replaced before its surname. */
private val players = listOf(
    // T20 (scorecard.json, commentary): Melbourne Stars become Australia, Sydney Thunder become India.
    "Amy Jones" to "Josh Inglis", "Meg Lanning" to "Travis Head", "Carly Leeson" to "Matthew Short", "Zoe Samuel" to "Nathan Ellis",
    "Naomi Stalenberg" to "Glenn Maxwell", "Charlie Dean" to "Adam Zampa", "Danielle Gibson" to "Tim David", "Dani Gibson" to "Tim David",
    "Georgia Prestwidge" to "Pat Cummins", "Rhys McKenna" to "Marcus Stoinis", "Kim Garth" to "Josh Hazlewood", "Sophie Day" to "Mitchell Starc",
    "Phoebe Litchfield" to "Abhishek Sharma", "Georgia Voll" to "Shubman Gill", "Anika Learoyd" to "Tilak Varma", "Heather Knight" to "Suryakumar Yadav",
    "Tahlia Wilson" to "Sanju Samson", "Laura Harris" to "Shivam Dube", "Nadine de Klerk" to "Hardik Pandya", "Jemma Barsby" to "Axar Patel",
    "Nicola Hancock" to "Arshdeep Singh", "Lauren Bell" to "Jasprit Bumrah", "Sam Bates" to "Varun Chakaravarthy",
    // Test (summary_test_match.json): Afghanistan become India, Zimbabwe become Australia.
    "Sediq Atal" to "Yashasvi Jaiswal", "Abdul Malik" to "KL Rahul", "Rahmanullah Zadran" to "Sai Sudharsan", "Hashmatullah Shahidi" to "Shubman Gill",
    "Rahmat Shah" to "Devdutt Padikkal", "Afsar Zazai" to "Rishabh Pant", "Ikram Alikhil" to "Dhruv Jurel", "Naseer Khan Maroofkhil" to "Ravindra Jadeja",
    "Yamin Ahmadzai" to "Jasprit Bumrah", "Mohammad Saleem" to "Mohammed Siraj", "Zia Sharifi" to "Kuldeep Yadav",
    "Innocent Kaia" to "Usman Khawaja", "Ben Curran" to "Sam Konstas", "Brendan Taylor" to "Marnus Labuschagne", "Brian Bennett" to "Steve Smith",
    "Wessly Madhevere" to "Travis Head", "Antum Naqvi" to "Cameron Green", "Tafadzwa Tsiga" to "Alex Carey", "Brad Evans" to "Pat Cummins",
    "Graeme Cremer" to "Nathan Lyon", "Vincent Masekesa" to "Josh Hazlewood", "Blessing Muzarabani" to "Mitchell Starc",
)

private val teamsAndSeries = listOf(
    "New Zealand v India Tests 2026 - Men" to "New Zealand v West Indies Tests 2026", "India Men" to "West Indies",
    "Afghanistan v Zimbabwe Tests 2026 - Men" to "India v Australia Tests 2026",
    "Afghanistan Men" to "India", "Zimbabwe Men" to "Australia", "Afghanistan" to "India", "Zimbabwe" to "Australia",
    "Sydney Thunder Women" to "India", "Melbourne Stars Women" to "Australia", "Adelaide Strikers Women" to "Australia", "Sydney Sixers Women" to "India",
    "Weber Women's Big Bash League" to "India v Australia T20Is 2026", "Weber WBBL|12" to "India v Australia T20Is 2026",
    "Women's competitive Twenty20" to "Twenty20 International", "Match 24" to "3rd T20I", "Match 25" to "4th T20I",
    "Sharjah Cricket Association Stadium" to "Narendra Modi Stadium", "Drummoyne Oval" to "Wankhede Stadium",
    " - Men\"" to "\"", " Men\"" to "\"",
)

// "India Men" (New Zealand's opponent) became West Indies above, so its code changes first.
private val shortNames = mapOf("IND" to "WI", "THU" to "IND", "SIX" to "IND", "AFG" to "IND", "STA" to "AUS", "STR" to "AUS", "ZIM" to "AUS")
private val colours = mapOf(
    "#97D700" to "#1F4BB4", "#1143EE" to "#1F4BB4", "#ff00a0" to "#1F4BB4", // India
    "#00B140" to "#FFCD00", "#EE1C25" to "#FFCD00", "#0085CA" to "#FFCD00", "#085251" to "#FFCD00", // Australia
    "#28C2F5" to "#7B0041", // West Indies
)

internal fun storeData(json: String): String {
    var s = json
    teamsAndSeries.forEach { (from, to) -> s = s.replace(from, to) }
    players.forEach { (from, to) -> s = s.replace(from, to) }
    // Surnames on their own, as commentary writes them ("Bell to Jones"); "Day 3" is a day of a Test, not a player.
    players.forEach { (from, to) ->
        val old = from.substringAfterLast(' ')
        val new = to.substringAfterLast(' ')
        s = s.replace(Regex("\\b${Regex.escape(old)}\\b(?! \\d)"), new)
    }
    // First names only where they sit next to the (already renamed) surname, since several old players share one.
    players.forEach { (from, to) ->
        val oldFirst = Regex.escape(from.substringBefore(' '))
        val newLast = Regex.escape(to.substringAfterLast(' '))
        s = s.replace(Regex("(\"FirstName\"\\s*:\\s*\")$oldFirst(\"\\s*,\\s*\"LastName\"\\s*:\\s*\"$newLast\")"), "$1${to.substringBefore(' ')}$2")
    }
    shortNames.forEach { (from, to) -> s = s.replace(Regex("(\"[A-Za-z]*ShortName\"\\s*:\\s*\")$from\""), "$1$to\"") }
    colours.forEach { (from, to) -> s = s.replace(from, to, ignoreCase = true) }
    s = s.replace(Regex("\"IsWomens(Match|Team)\"\\s*:\\s*true"), "\"IsWomens$1\": false")
    return s
}
