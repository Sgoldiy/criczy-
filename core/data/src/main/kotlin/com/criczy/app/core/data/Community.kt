package com.criczy.app.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/*
 * Fan community: short posts and polls in one feed per favourite team plus one for all fans.
 * Members pick only a username. Everything lasts 24 hours, and each member may post once
 * every 24 hours. The server side lives behind [CommunityBackend] (Firestore in the app);
 * posts already seen are kept in Room so a feed only downloads what is new.
 */

enum class PostKind { OPINION, POLL }

/**
 * One post. [counts] holds the votes: for an opinion `[agree, disagree]`, for a poll one
 * count per option. [myVote] is the index this member chose, if any.
 */
data class CommunityPost(
    val id: String,
    val community: String,
    val authorId: String,
    val author: String,
    val text: String,
    val kind: PostKind,
    val options: List<String>,
    val counts: List<Int>,
    val reports: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val myVote: Int? = null,
) {
    val totalVotes: Int get() = counts.sum()
}

data class PostDraft(val community: String, val text: String, val kind: PostKind, val options: List<String> = emptyList())

/** The member as the server knows them; [username] is null until one is chosen. */
data class CommunityUser(val uid: String, val username: String?, val lastPostAt: Long?)

data class CommunityProfile(val uid: String, val username: String, val lastPostAt: Long?) {
    fun nextPostAt(): Long? = lastPostAt?.plus(Communities.LIFETIME_MS)
    fun canPost(now: Long): Boolean = nextPostAt()?.let { now >= it } ?: true
}

class UsernameTakenException : Exception("That username is taken")
class DailyPostLimitException(val nextPostAt: Long?) : Exception("One post every 24 hours")
class CommunityRuleException(message: String) : Exception(message)

object Communities {
    const val ALL = "all"
    const val LIFETIME_MS = 24L * 60 * 60 * 1000
    const val MAX_TEXT = 280
    const val MAX_OPTION = 40
    const val MAX_TEAMS = 5
    /** Posts reported this many times are hidden for everyone. */
    const val HIDE_AFTER_REPORTS = 5
    /** Shown before joining; joining means accepting them. */
    val RULES = listOf(
        "No abuse, hate, threats or harassment of players, fans or anyone else.",
        "No sexual content, violence or anything illegal.",
        "No spam, advertising, betting tips or links to scams.",
        "No personal information about anyone, including yourself.",
        "Disagree with the take, not the person.",
        "Posts that break these rules can be reported. Posts reported by several fans are hidden for everyone.",
    )
    val USERNAME = Regex("^[A-Za-z0-9_]{3,20}$")

    fun team(id: Int) = "team-$id"
    fun teamId(community: String): Int? = community.removePrefix("team-").takeIf { community.startsWith("team-") }?.toIntOrNull()

    /** Why a username cannot be used, or null if it can. */
    fun usernameProblem(name: String): String? = when {
        name.length < 3 -> "At least 3 characters"
        name.length > 20 -> "At most 20 characters"
        !USERNAME.matches(name) -> "Letters, numbers and _ only"
        !ContentFilter.isClean(name) -> "Choose a different username"
        else -> null
    }

    /** Why a draft cannot be posted, or null if it can. */
    fun draftProblem(draft: PostDraft): String? {
        val text = draft.text.trim()
        return when {
            text.isEmpty() -> if (draft.kind == PostKind.POLL) "Write the question" else "Write something"
            text.length > MAX_TEXT -> "At most $MAX_TEXT characters"
            !ContentFilter.isClean(text) -> "Please keep it clean"
            draft.kind == PostKind.POLL && draft.options.map { it.trim() }.filter { it.isNotEmpty() }.size < 2 -> "Add at least 2 options"
            draft.kind == PostKind.POLL && draft.options.size > 4 -> "At most 4 options"
            draft.options.any { it.trim().length > MAX_OPTION } -> "Options can be $MAX_OPTION characters at most"
            draft.options.any { !ContentFilter.isClean(it) } -> "Please keep it clean"
            else -> null
        }
    }
}

/** A small word list: not real moderation, which is reporting, blocking and the 24-hour lifetime. */
object ContentFilter {
    private val blocked = listOf(
        "fuck", "shit", "bitch", "bastard", "cunt", "dick", "pussy", "slut", "whore", "nigger", "nigga", "faggot", "retard",
        "chutiya", "chutiye", "madarchod", "behenchod", "bhenchod", "bhosdike", "bhosdi", "gandu", "lauda", "lavda", "randi", "harami",
    )
    /** Whole words only (with plain endings), so names like Dickwella stay usable. */
    private val pattern = Regex("\\b(" + blocked.joinToString("|") { Regex.escape(it) } + ")(s|es|ing|er|ers|ed|y)?\\b", RegexOption.IGNORE_CASE)

    fun isClean(text: String): Boolean = !pattern.containsMatchIn(text.replace(Regex("[^A-Za-z]+"), " "))
}

/** The server. [isAvailable] is false in builds without Firebase configuration. */
interface CommunityBackend {
    val isAvailable: Boolean
    suspend fun currentUser(): CommunityUser
    /** Throws [UsernameTakenException]. */
    suspend fun claimUsername(username: String): CommunityUser
    /** Posts created after [after] (epoch millis), newest first. */
    suspend fun posts(community: String, after: Long, limit: Int): List<CommunityPost>
    /** Throws [DailyPostLimitException]. */
    suspend fun publish(draft: PostDraft, profile: CommunityProfile): CommunityPost
    suspend fun vote(post: CommunityPost, choice: Int)
    suspend fun report(post: CommunityPost)
    suspend fun delete(post: CommunityPost)
}

object UnavailableCommunityBackend : CommunityBackend {
    override val isAvailable = false
    private fun unavailable(): Nothing = throw IllegalStateException("Community is not configured in this build")
    override suspend fun currentUser() = unavailable()
    override suspend fun claimUsername(username: String) = unavailable()
    override suspend fun posts(community: String, after: Long, limit: Int) = unavailable()
    override suspend fun publish(draft: PostDraft, profile: CommunityProfile) = unavailable()
    override suspend fun vote(post: CommunityPost, choice: Int) = unavailable()
    override suspend fun report(post: CommunityPost) = unavailable()
    override suspend fun delete(post: CommunityPost) = unavailable()
}

@Entity(tableName = "community_posts")
data class CommunityPostEntity(
    @PrimaryKey val id: String,
    val community: String,
    val authorId: String,
    val author: String,
    val text: String,
    val kind: String,
    /** Options joined by a line break (options are single-line). */
    val options: String,
    /** Vote counts joined by commas. */
    val counts: String,
    val reports: Int,
    val createdAt: Long,
    val expiresAt: Long,
)

/** This member's own votes, kept apart so a refreshed post does not forget them. */
@Entity(tableName = "community_votes")
data class CommunityVoteEntity(@PrimaryKey val postId: String, val choice: Int, val expiresAt: Long)

@Entity(tableName = "community_blocked")
data class BlockedAuthorEntity(@PrimaryKey val authorId: String, val author: String, val blockedAt: Long)

@Entity(tableName = "community_profile")
data class CommunityProfileEntity(@PrimaryKey val slot: Int = 0, val uid: String, val username: String, val lastPostAt: Long?)

@Dao
interface CommunityDao {
    @Query(
        "SELECT * FROM community_posts WHERE community = :community AND expiresAt > :now AND reports < :hideAfter " +
            "AND authorId NOT IN (SELECT authorId FROM community_blocked) ORDER BY createdAt DESC",
    )
    fun observePosts(community: String, now: Long, hideAfter: Int = Communities.HIDE_AFTER_REPORTS): Flow<List<CommunityPostEntity>>

    @Query("SELECT * FROM community_votes")
    fun observeVotes(): Flow<List<CommunityVoteEntity>>

    @Query("SELECT MAX(createdAt) FROM community_posts WHERE community = :community")
    suspend fun newest(community: String): Long?

    @Query("SELECT * FROM community_posts WHERE id = :id")
    suspend fun post(id: String): CommunityPostEntity?

    @Upsert
    suspend fun upsertPosts(posts: List<CommunityPostEntity>)

    @Upsert
    suspend fun upsertVote(vote: CommunityVoteEntity)

    @Query("DELETE FROM community_posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Query("DELETE FROM community_posts WHERE community = :community AND createdAt > :after AND id NOT IN (:keep)")
    suspend fun deleteMissing(community: String, after: Long, keep: List<String>)

    @Query("DELETE FROM community_posts WHERE expiresAt <= :now")
    suspend fun deleteExpiredPosts(now: Long)

    @Query("DELETE FROM community_votes WHERE expiresAt <= :now")
    suspend fun deleteExpiredVotes(now: Long)

    @Upsert
    suspend fun block(author: BlockedAuthorEntity)

    @Query("SELECT * FROM community_profile WHERE slot = 0")
    fun observeProfile(): Flow<CommunityProfileEntity?>

    @Query("SELECT * FROM community_profile WHERE slot = 0")
    suspend fun profile(): CommunityProfileEntity?

    @Upsert
    suspend fun saveProfile(profile: CommunityProfileEntity)
}

class CommunityRepository(
    private val backend: CommunityBackend,
    private val dao: CommunityDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val isAvailable: Boolean get() = backend.isAvailable

    val profile: Flow<CommunityProfile?> = dao.observeProfile().map { it?.let { p -> CommunityProfile(p.uid, p.username, p.lastPostAt) } }

    /** The feed as stored on this phone: unexpired, not blocked, not hidden by reports. */
    fun feed(community: String): Flow<List<CommunityPost>> =
        combine(dao.observePosts(community, now()), dao.observeVotes()) { posts, votes ->
            val mine = votes.associate { it.postId to it.choice }
            posts.map { it.toModel(mine[it.id]) }
        }

    /**
     * Restores the member's username from the server when this phone has none stored (for
     * example after clearing data while the anonymous account survived). Returns the profile,
     * or null if a username still has to be chosen.
     */
    suspend fun connect(): CommunityProfile? {
        dao.profile()?.let { return CommunityProfile(it.uid, it.username, it.lastPostAt) }
        val user = backend.currentUser()
        val name = user.username ?: return null
        dao.saveProfile(CommunityProfileEntity(uid = user.uid, username = name, lastPostAt = user.lastPostAt))
        return CommunityProfile(user.uid, name, user.lastPostAt)
    }

    suspend fun claimUsername(username: String): CommunityProfile {
        val name = username.trim()
        Communities.usernameProblem(name)?.let { throw CommunityRuleException(it) }
        val user = backend.claimUsername(name)
        dao.saveProfile(CommunityProfileEntity(uid = user.uid, username = user.username ?: name, lastPostAt = user.lastPostAt))
        return CommunityProfile(user.uid, user.username ?: name, user.lastPostAt)
    }

    /**
     * Downloads posts newer than the newest one stored. With [full], downloads the whole last
     * 24 hours instead, which updates vote counts and drops posts deleted by their authors.
     */
    suspend fun refresh(community: String, full: Boolean = false) {
        val time = now()
        prune(time)
        val windowStart = time - Communities.LIFETIME_MS
        val after = if (full) windowStart else maxOf(dao.newest(community) ?: windowStart, windowStart)
        val posts = backend.posts(community, after, FETCH_LIMIT)
        dao.upsertPosts(posts.map { it.toEntity() })
        if (full && posts.size < FETCH_LIMIT) dao.deleteMissing(community, windowStart, posts.map { it.id })
    }

    suspend fun publish(draft: PostDraft): CommunityPost {
        val profile = dao.profile()?.let { CommunityProfile(it.uid, it.username, it.lastPostAt) }
            ?: throw CommunityRuleException("Choose a username first")
        if (!profile.canPost(now())) throw DailyPostLimitException(profile.nextPostAt())
        Communities.draftProblem(draft)?.let { throw CommunityRuleException(it) }
        val clean = draft.copy(
            text = draft.text.trim(),
            options = if (draft.kind == PostKind.POLL) draft.options.map { it.trim().replace('\n', ' ') }.filter { it.isNotEmpty() } else emptyList(),
        )
        val post = backend.publish(clean, profile)
        dao.upsertPosts(listOf(post.toEntity()))
        dao.saveProfile(CommunityProfileEntity(uid = profile.uid, username = profile.username, lastPostAt = post.createdAt))
        return post
    }

    /** One vote per post; a second vote is ignored. */
    suspend fun vote(post: CommunityPost, choice: Int) {
        if (post.myVote != null || choice !in post.counts.indices) return
        backend.vote(post, choice)
        dao.upsertVote(CommunityVoteEntity(post.id, choice, post.expiresAt))
        dao.post(post.id)?.let { stored ->
            val counts = stored.counts.toCounts().toMutableList().also { if (choice in it.indices) it[choice] += 1 }
            dao.upsertPosts(listOf(stored.copy(counts = counts.joinToString(","))))
        }
    }

    /** Reports the post and hides it on this phone. */
    suspend fun report(post: CommunityPost) {
        backend.report(post)
        dao.deletePost(post.id)
    }

    /** Hides everything by this author on this phone. */
    suspend fun block(post: CommunityPost) = dao.block(BlockedAuthorEntity(post.authorId, post.author, now()))

    suspend fun delete(post: CommunityPost) {
        backend.delete(post)
        dao.deletePost(post.id)
    }

    suspend fun prune(time: Long = now()) {
        dao.deleteExpiredPosts(time)
        dao.deleteExpiredVotes(time)
    }

    private fun CommunityPostEntity.toModel(myVote: Int?) = CommunityPost(
        id, community, authorId, author, text,
        PostKind.entries.firstOrNull { it.name == kind } ?: PostKind.OPINION,
        if (options.isEmpty()) emptyList() else options.split('\n'),
        counts.toCounts(), reports, createdAt, expiresAt, myVote,
    )

    private fun CommunityPost.toEntity() = CommunityPostEntity(
        id, community, authorId, author, text, kind.name, options.joinToString("\n"), counts.joinToString(","),
        reports, createdAt, expiresAt,
    )

    private fun String.toCounts(): List<Int> = if (isEmpty()) emptyList() else split(',').map { it.toIntOrNull() ?: 0 }

    companion object {
        const val FETCH_LIMIT = 100
    }
}
