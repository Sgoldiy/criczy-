package com.criczy.app

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.criczy.app.core.data.Communities
import com.criczy.app.core.data.CommunityPost
import com.criczy.app.core.data.CommunityProfile
import com.criczy.app.core.data.Favourite
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.core.data.PostDraft
import com.criczy.app.core.data.PostKind
import com.criczy.app.feature.community.ALL_FANS
import com.criczy.app.feature.community.CommunityActions
import com.criczy.app.feature.community.CommunityContent
import com.criczy.app.feature.community.CommunityPhase
import com.criczy.app.feature.community.CommunitySpace
import com.criczy.app.feature.community.CommunityUiState
import com.criczy.app.feature.community.ago
import com.criczy.app.feature.community.nextPostText
import com.criczy.app.feature.favourites.FavouritesScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class CommunityScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val india = CommunitySpace(Communities.team(6), "India Men", "IND", null, "#1F4BB4")
    private val australia = CommunitySpace(Communities.team(2), "Australia Men", "AUS", null, "#FFCD00")

    private fun post(id: String, author: String, text: String, ageHours: Double, kind: PostKind = PostKind.OPINION, options: List<String> = emptyList(), counts: List<Int> = listOf(0, 0), myVote: Int? = null, authorId: String = "uid-$author") =
        CommunityPost(id, india.id, authorId, author, text, kind, options, counts, 0, now - (ageHours * hour).toLong(), now - (ageHours * hour).toLong() + Communities.LIFETIME_MS, myVote)

    private fun ready(profile: CommunityProfile, posts: List<CommunityPost>) = CommunityUiState(
        phase = CommunityPhase.READY, profile = profile, spaces = listOf(ALL_FANS, india, australia), selected = india.id, posts = posts, now = now,
    )

    @Test
    fun `joining asks only for a username and checks it as you type`() {
        var joined: String? = null
        compose.setCriczyContent { Surface { CommunityContent(CommunityUiState(phase = CommunityPhase.NEEDS_USERNAME), CommunityActions(onJoin = { joined = it })) } }
        compose.onNodeWithText("Join the fan community").assertIsDisplayed()
        compose.onNodeWithText("Username").performTextInput("ab")
        compose.onNodeWithText("At least 3 characters").assertIsDisplayed()
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNodeWithText("ab").performTextInput("d_fan")
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNodeWithText("I agree to the community rules").performScrollTo().performClick()
        compose.screenshot("community-join-phone-dark")
        compose.onNodeWithText("Join").performScrollTo().performClick()
        assertEquals("abd_fan", joined)
    }

    @Test
    fun `team feed shows opinions and polls, before and after voting`() {
        val profile = CommunityProfile("me", "cover_drive", null)
        val posts = listOf(
            post("1", "bumrah_army", "Bumrah should open the bowling in every format. Nobody else comes close with the new ball.", 0.5),
            post("2", "stats_guru", "Who should bat at No. 4 in the final?", 2.0, PostKind.POLL, listOf("Shreyas Iyer", "KL Rahul", "Hardik Pandya"), listOf(42, 31, 12), myVote = 0),
            post("3", "chennai_kid", "Best fielding side in the tournament so far.", 5.0, counts = listOf(18, 6), myVote = 0),
            post("4", "leg_slip", "Spin or pace on this pitch?", 8.0, PostKind.POLL, listOf("Spin", "Pace"), listOf(3, 9)),
        )
        val votes = mutableListOf<Pair<String, Int>>()
        compose.setCriczyContent {
            Surface { CommunityContent(ready(profile, posts), CommunityActions(onVote = { p, c -> votes += p.id to c })) }
        }
        compose.onNodeWithText("India Men").assertIsDisplayed()
        compose.onNodeWithText("@bumrah_army").assertIsDisplayed()
        compose.screenshot("community-feed-phone-dark")
        compose.onNodeWithContentDescription("Agree, 0").performClick()
        assertEquals(listOf("1" to 0), votes)
    }

    @Test
    fun `composer writes a poll for the selected team and shows the daily limit`() {
        val profile = CommunityProfile("me", "cover_drive", null)
        var draft: PostDraft? = null
        compose.setCriczyContent(dark = false) {
            Surface { CommunityContent(ready(profile, emptyList()), CommunityActions(onPost = { draft = it })) }
        }
        compose.onNodeWithText("No posts in India Men", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Post").performClick()
        compose.onNodeWithText("Poll").performClick()
        compose.onNodeWithText("Question").performTextInput("Who wins the series?")
        compose.onNodeWithText("Option 1").performTextInput("India")
        compose.onNodeWithText("Option 2").performTextInput("Australia")
        compose.screenshot("community-composer-poll-phone-light")
        compose.onNodeWithText("Post to IND").performClick()
        assertEquals(PostDraft(india.id, "Who wins the series?", PostKind.POLL, listOf("India", "Australia")), draft)
    }

    @Test
    fun `after posting, the next post time is shown instead of the post button`() {
        val profile = CommunityProfile("me", "cover_drive", now - 3 * hour)
        val mine = post("9", "cover_drive", "Kohli to score a hundred today.", 3.0, authorId = "me", counts = listOf(7, 2))
        compose.setCriczyContent { Surface { CommunityContent(ready(profile, listOf(mine)), CommunityActions()) } }
        compose.onNodeWithText("Next post in 21h 0m").assertIsDisplayed()
        compose.onNodeWithText("@cover_drive · you").assertIsDisplayed()
        compose.onNodeWithContentDescription("More for @cover_drive's post").performClick()
        compose.onNodeWithText("Delete post").assertIsDisplayed()
        compose.screenshot("community-posted-today-phone-dark")
    }

    @Test
    fun `favourites have a community tab, unavailable without Firebase`() {
        val team = Favourite(FavouriteType.TEAM, 6, "India Men", "IND", null, "#1F4BB4")
        compose.setCriczyContent {
            FavouritesScreen(listOf(team), {}, {}, { _, _ -> }, {}, {}, {}, initialTab = 1, community = {
                CommunityContent(CommunityUiState(phase = CommunityPhase.UNAVAILABLE), CommunityActions())
            })
        }
        compose.onNodeWithText("Community unavailable").assertIsDisplayed()
        compose.screenshot("favourites-community-unavailable-phone-dark")
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("India Men").assertIsDisplayed()
    }

    @Test
    fun `time labels`() {
        assertEquals("now", ago(now, now))
        assertEquals("45m ago", ago(now - 45 * 60_000, now))
        assertEquals("3h ago", ago(now - 3 * hour - 1, now))
        assertEquals("Next post in 1h 30m", nextPostText(now + 90 * 60_000, now))
        assertEquals("You can post now", nextPostText(now - 1, now))
    }
}
