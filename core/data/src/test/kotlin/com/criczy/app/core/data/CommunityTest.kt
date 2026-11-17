package com.criczy.app.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.criczy.app.core.model.Team
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An in-memory server that behaves like the Firestore rules: unique names, one post a day. */
class FakeCommunityBackend(private val clock: () -> Long) : CommunityBackend {
    override val isAvailable = true
    val uid = "me"
    var username: String? = null
    var lastPostAt: Long? = null
    val taken = mutableSetOf("taken")
    val posts = mutableListOf<CommunityPost>()
    var fetches = mutableListOf<Long>()
    val reported = mutableListOf<String>()

    override suspend fun currentUser() = CommunityUser(uid, username, lastPostAt)

    override suspend fun claimUsername(username: String): CommunityUser {
        if (username.lowercase() in taken) throw UsernameTakenException()
        taken += username.lowercase()
        this.username = username
        return CommunityUser(uid, username, null)
    }

    override suspend fun posts(community: String, after: Long, limit: Int): List<CommunityPost> {
        fetches += after
        return posts.filter { it.community == community && it.createdAt > after }.sortedByDescending { it.createdAt }.take(limit)
    }

    var lastMatchPostAt: Long? = null
    val polls = mutableListOf<MatchPoll>()

    override suspend fun publish(draft: PostDraft, profile: CommunityProfile, matchPost: Boolean): CommunityPost {
        val now = clock()
        if (matchPost) {
            requireNotNull(draft.matchId)
            lastMatchPostAt?.let { if (now < it + Communities.MATCH_POST_GAP_MS) throw DailyPostLimitException(null) }
            lastMatchPostAt = now
        } else {
            lastPostAt?.let { if (now < it + Communities.LIFETIME_MS) throw DailyPostLimitException(it + Communities.LIFETIME_MS) }
            lastPostAt = now
        }
        val post = CommunityPost(
            "p${posts.size + 1}", draft.community, uid, profile.username, draft.text, draft.kind, draft.options,
            List(if (draft.kind == PostKind.POLL) draft.options.size else 2) { 0 }, 0, now, now + Communities.LIFETIME_MS,
        )
        posts += post
        return post
    }

    override suspend fun ensureMatchPoll(poll: MatchPoll): CommunityPost {
        polls += poll
        return posts.firstOrNull { it.id == poll.id } ?: CommunityPost(
            poll.id, poll.community, Communities.SYSTEM_UID, Communities.SYSTEM_NAME, poll.question, PostKind.POLL, poll.options,
            List(poll.options.size) { 0 }, 0, clock(), clock() + Communities.LIFETIME_MS,
        ).also { posts += it }
    }

    override suspend fun vote(post: CommunityPost, choice: Int) {
        val index = posts.indexOfFirst { it.id == post.id }
        if (index >= 0) posts[index] = posts[index].copy(counts = posts[index].counts.toMutableList().also { it[choice] += 1 })
    }

    override suspend fun report(post: CommunityPost) { reported += post.id }
    override suspend fun delete(post: CommunityPost) { posts.removeAll { it.id == post.id } }

    fun other(id: String, community: String, at: Long, author: String = "fan", kind: PostKind = PostKind.OPINION, options: List<String> = emptyList()) =
        CommunityPost(id, community, "uid-$author", author, "Post $id", kind, options, List(if (kind == PostKind.POLL) options.size else 2) { 0 }, 0, at, at + Communities.LIFETIME_MS)
            .also { posts += it }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunityTest {
    private lateinit var db: CriczyDatabase
    private var now = 10 * Communities.LIFETIME_MS
    private lateinit var backend: FakeCommunityBackend
    private lateinit var repo: CommunityRepository

    @Before
    fun setUp() {
        db = CriczyDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        backend = FakeCommunityBackend { now }
        repo = CommunityRepository(backend, db.community()) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `joining needs a valid, free username and is remembered`() = runTest {
        assertNull(repo.connect())
        assertFailsWith<CommunityRuleException> { repo.claimUsername("ab") }
        assertFailsWith<CommunityRuleException> { repo.claimUsername("bad name") }
        assertFailsWith<UsernameTakenException> { repo.claimUsername("Taken") }
        assertEquals("kohli_fan", repo.claimUsername("kohli_fan").username)
        assertEquals("kohli_fan", repo.profile.first()?.username)
        assertEquals("kohli_fan", repo.connect()?.username)
    }

    @Test
    fun `a username already on the server is restored without choosing again`() = runTest {
        backend.username = "returning"
        assertEquals("returning", repo.connect()?.username)
    }

    @Test
    fun `one post a day, rejected locally before reaching the server`() = runTest {
        repo.claimUsername("fan1")
        repo.publish(PostDraft(Communities.ALL, "  Great chase!  ", PostKind.OPINION))
        assertEquals("Great chase!", repo.feed(Communities.ALL).first().single().text)
        now += 60_000
        val refused = assertFailsWith<DailyPostLimitException> { repo.publish(PostDraft(Communities.ALL, "Again", PostKind.OPINION)) }
        assertEquals(now - 60_000 + Communities.LIFETIME_MS, refused.nextPostAt)
        assertEquals(1, backend.posts.size)
        now += Communities.LIFETIME_MS
        repo.publish(PostDraft(Communities.team(4), "Next day", PostKind.OPINION))
        assertEquals(2, backend.posts.size)
    }

    @Test
    fun `polls need two to four options and clean words`() = runTest {
        repo.claimUsername("fan1")
        assertFailsWith<CommunityRuleException> { repo.publish(PostDraft(Communities.ALL, "Who wins?", PostKind.POLL, listOf("India", " "))) }
        assertFailsWith<CommunityRuleException> { repo.publish(PostDraft(Communities.ALL, "You are a bastard", PostKind.OPINION)) }
        val poll = repo.publish(PostDraft(Communities.ALL, "Who wins?", PostKind.POLL, listOf("India", "Australia", "")))
        assertEquals(listOf("India", "Australia"), poll.options)
        assertEquals(listOf(0, 0), repo.feed(Communities.ALL).first().single().counts)
    }

    @Test
    fun `feeds download only what is new and drop posts after 24 hours`() = runTest {
        repo.claimUsername("fan1")
        val team = Communities.team(2)
        backend.other("a", team, now - 3_600_000)
        backend.other("b", team, now - 60_000)
        backend.other("x", Communities.ALL, now - 60_000)
        repo.refresh(team)
        assertEquals(listOf("b", "a"), repo.feed(team).first().map { it.id })
        assertEquals(now - Communities.LIFETIME_MS, backend.fetches.last())

        repo.refresh(team)
        assertEquals(now - 60_000, backend.fetches.last(), "second fetch asks only for posts after the newest stored")

        now += Communities.LIFETIME_MS - 30 * 60_000 // "a" is now 24.5 hours old
        repo.refresh(team)
        assertEquals(listOf("b"), repo.feed(team).first().map { it.id })
    }

    @Test
    fun `full refresh updates counts and forgets deleted posts`() = runTest {
        repo.claimUsername("fan1")
        backend.other("a", Communities.ALL, now - 1000)
        backend.other("b", Communities.ALL, now - 500)
        repo.refresh(Communities.ALL)
        backend.posts.removeAll { it.id == "a" }
        backend.posts[backend.posts.indexOfFirst { it.id == "b" }] = backend.posts.first { it.id == "b" }.copy(counts = listOf(5, 2))
        repo.refresh(Communities.ALL, full = true)
        val feed = repo.feed(Communities.ALL).first()
        assertEquals(listOf("b"), feed.map { it.id })
        assertEquals(listOf(5, 2), feed.single().counts)
    }

    @Test
    fun `one vote per post, kept across refreshes`() = runTest {
        repo.claimUsername("fan1")
        backend.other("poll", Communities.ALL, now - 1000, kind = PostKind.POLL, options = listOf("Yes", "No", "Maybe"))
        repo.refresh(Communities.ALL)
        repo.vote(repo.feed(Communities.ALL).first().single(), 2)
        val voted = repo.feed(Communities.ALL).first().single()
        assertEquals(2, voted.myVote)
        assertEquals(listOf(0, 0, 1), voted.counts)
        repo.vote(voted, 0)
        assertEquals(listOf(0, 0, 1), backend.posts.single().counts, "second vote is ignored")
        repo.refresh(Communities.ALL, full = true)
        assertEquals(2, repo.feed(Communities.ALL).first().single().myVote)
    }

    @Test
    fun `reported posts and blocked authors disappear from this phone`() = runTest {
        repo.claimUsername("fan1")
        backend.other("a", Communities.ALL, now - 3000, author = "troll")
        backend.other("b", Communities.ALL, now - 2000, author = "troll")
        backend.other("c", Communities.ALL, now - 1000, author = "nice")
        repo.refresh(Communities.ALL)
        repo.report(repo.feed(Communities.ALL).first().first { it.id == "c" })
        assertEquals(listOf("c"), backend.reported)
        repo.block(repo.feed(Communities.ALL).first().first { it.author == "troll" })
        assertTrue(repo.feed(Communities.ALL).first().isEmpty())
    }

    @Test
    fun `posts reported by several fans are hidden for everyone`() = runTest {
        repo.claimUsername("fan1")
        backend.other("a", Communities.ALL, now - 1000)
        backend.posts[0] = backend.posts[0].copy(reports = Communities.HIDE_AFTER_REPORTS)
        repo.refresh(Communities.ALL)
        assertTrue(repo.feed(Communities.ALL).first().isEmpty())
    }

    @Test
    fun `the word filter catches whole words but not cricketers' names`() {
        assertFalse(ContentFilter.isClean("what a SHIT shot"))
        assertFalse(ContentFilter.isClean("chutiya_99"))
        assertTrue(ContentFilter.isClean("Dickwella hit this shot"))
        assertTrue(ContentFilter.isClean("Scunthorpe and Shitij"))
        assertEquals(2, Communities.teamId("team-2"))
        assertNull(Communities.teamId(Communities.ALL))
    }

    @Test
    fun `one extra post during a live match, at most every four hours`() = runTest {
        repo.claimUsername("fan1")
        repo.publish(PostDraft(Communities.team(6), "Daily", PostKind.OPINION))
        assertFailsWith<DailyPostLimitException> { repo.publish(PostDraft(Communities.team(6), "No match", PostKind.OPINION)) }
        repo.publish(PostDraft(Communities.team(6), "What a catch!", PostKind.OPINION, matchId = 41015))
        assertEquals(2, backend.posts.size)
        assertEquals(now, repo.profile.first()?.lastMatchPostAt)
        now += 60 * 60_000
        assertFailsWith<DailyPostLimitException> { repo.publish(PostDraft(Communities.team(6), "Again", PostKind.OPINION, matchId = 41015)) }
        now += 3 * 60 * 60_000
        repo.publish(PostDraft(Communities.team(6), "Four hours later", PostKind.OPINION, matchId = 41015))
        assertEquals(3, backend.posts.size)
    }

    @Test
    fun `match polls are opened once and then served from this phone`() = runTest {
        repo.claimUsername("fan1")
        val poll = MatchPoll("match-1-win-team-6", Communities.team(6), 1, "Who wins? IND v AUS", listOf("India", "Australia"))
        repo.ensureMatchPolls(listOf(poll))
        repo.ensureMatchPolls(listOf(poll))
        assertEquals(1, backend.polls.size)
        val shown = repo.feed(Communities.team(6)).first().single()
        assertTrue(shown.isSystem)
        assertEquals(listOf("India", "Australia"), shown.options)
    }

    @Test
    fun `match poll builder picks matches around now and standouts from the scorecard`() {
        val t = java.time.Instant.parse("2026-11-18T10:00:00Z")
        val ind = Team(6, "India Men", "IND", null, null)
        val aus = Team(2, "Australia Men", "AUS", null, null)
        val comp = com.criczy.app.core.model.Competition(1, "Border-Gavaskar Trophy", null)
        fun match(id: Int, state: com.criczy.app.core.model.MatchState, start: java.time.Instant, format: com.criczy.app.core.model.MatchFormat = com.criczy.app.core.model.MatchFormat.ODI) =
            com.criczy.app.core.model.Match(id, "", comp, ind, aus, null, start, start.plusSeconds(8 * 3600), state, format, format.label, emptyList(), null)
        val live = match(1, com.criczy.app.core.model.MatchState.LIVE, t.minusSeconds(3600), com.criczy.app.core.model.MatchFormat.TEST)
        val soon = match(2, com.criczy.app.core.model.MatchState.UPCOMING, t.plusSeconds(5 * 3600))
        val later = match(3, com.criczy.app.core.model.MatchState.UPCOMING, t.plusSeconds(3 * 86400))
        val done = match(4, com.criczy.app.core.model.MatchState.COMPLETED, t.minusSeconds(12 * 3600))
        val old = match(5, com.criczy.app.core.model.MatchState.COMPLETED, t.minusSeconds(5 * 86400))
        val all = listOf(live, soon, later, done, old)

        assertEquals(live, MatchPolls.featured(6, all, t))
        assertEquals(soon, MatchPolls.featured(6, listOf(soon, later), t))
        val polls = MatchPolls.winPolls(6, all, t)
        assertEquals(listOf("match-1-win-team-6", "match-2-win-team-6"), polls.map { it.id })
        assertEquals(listOf("India Men", "Australia Men", "Draw"), polls[0].options)
        assertEquals(listOf("India Men", "Australia Men"), polls[1].options)
        assertEquals(listOf(done), MatchPolls.recentlyFinished(6, all, t))
        assertTrue(MatchPolls.winPolls(99, all, t).isEmpty())
    }

    @Test
    fun `at most five teams can be followed`() = runTest {
        val favourites = FavouritesRepository(db.favourites()) { 1L }
        (1..5).forEach { assertTrue(favourites.toggle(FavouritesRepository.of(Team(it, "Team $it", "T$it", null, null)))) }
        assertFalse(favourites.toggle(FavouritesRepository.of(Team(6, "Team 6", "T6", null, null))))
        assertEquals(5, favourites.all.first().size)
        // Other favourites are not limited, and removing a team frees a place.
        assertFalse(favourites.toggle(FavouritesRepository.of(Team(1, "Team 1", "T1", null, null))))
        assertTrue(favourites.toggle(FavouritesRepository.of(Team(6, "Team 6", "T6", null, null))))
    }
}
