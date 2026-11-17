package com.criczy.app.community

import android.content.Context
import com.criczy.app.core.data.CommunityBackend
import com.criczy.app.core.data.CommunityPost
import com.criczy.app.core.data.CommunityProfile
import com.criczy.app.core.data.CommunityUser
import com.criczy.app.core.data.Communities
import com.criczy.app.core.data.DailyPostLimitException
import com.criczy.app.core.data.MatchPoll
import com.criczy.app.core.data.PostDraft
import com.criczy.app.core.data.PostKind
import com.criczy.app.core.data.UsernameTakenException
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Firestore layout (rules in `firebase/firestore.rules`):
 * - `usernames/{lowercase name}` `{uid}`: makes usernames unique.
 * - `users/{uid}` `{username, createdAt, lastPostAt}`: lastPostAt enforces one post per 24 hours.
 * - `communities/{all | team-<id>}/posts/{id}`: text, kind, options, vote counts c0..c3,
 *   reports, createdAt, expireAt. A TTL policy on expireAt deletes posts after 24 hours.
 * - `.../posts/{id}/votes/{uid}` and `.../reports/{uid}`: one vote and one report per member.
 *
 * Members sign in anonymously: no email, phone or password, only the username they pick.
 */
class FirestoreCommunityBackend(context: Context) : CommunityBackend {
    private val app: FirebaseApp? = FirebaseApp.getApps(context).firstOrNull()
        ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()

    override val isAvailable: Boolean = app != null

    private val auth by lazy { FirebaseAuth.getInstance(requireNotNull(app)) }

    private val db by lazy {
        FirebaseFirestore.getInstance(requireNotNull(app)).apply {
            // Posts are cached in Room; Firestore's own disk cache would only duplicate them.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    private suspend fun uid(): String = auth.currentUser?.uid ?: requireNotNull(auth.signInAnonymously().await().user).uid

    private fun user(uid: String) = db.collection("users").document(uid)
    private fun posts(community: String) = db.collection("communities").document(community).collection("posts")

    override suspend fun currentUser(): CommunityUser {
        val uid = uid()
        val doc = user(uid).get().await()
        return CommunityUser(uid, doc.getString("username"), doc.getTimestamp("lastPostAt")?.toDate()?.time, doc.getTimestamp("lastMatchPostAt")?.toDate()?.time)
    }

    override suspend fun claimUsername(username: String): CommunityUser {
        val uid = uid()
        val nameRef = db.collection("usernames").document(username.lowercase())
        try {
            db.runTransaction { tx ->
                if (tx.get(nameRef).exists()) throw UsernameTakenException()
                tx.set(nameRef, mapOf("uid" to uid))
                tx.set(user(uid), mapOf("username" to username, "createdAt" to FieldValue.serverTimestamp()))
            }.await()
        } catch (e: Exception) {
            // Taken, or claimed by someone else between the read and the write (the rules refuse it).
            if (e is UsernameTakenException || e.cause is UsernameTakenException) throw UsernameTakenException()
            if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw UsernameTakenException()
            throw e
        }
        return CommunityUser(uid, username, null)
    }

    override suspend fun posts(community: String, after: Long, limit: Int): List<CommunityPost> =
        posts(community)
            .whereGreaterThan("createdAt", Timestamp(Date(after)))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
            .documents.mapNotNull { it.toPost(community) }

    override suspend fun publish(draft: PostDraft, profile: CommunityProfile, matchPost: Boolean): CommunityPost {
        val uid = uid()
        val now = System.currentTimeMillis()
        val expires = now + Communities.LIFETIME_MS
        val ref = posts(draft.community).document()
        val voteSlots = if (draft.kind == PostKind.POLL) draft.options.size else 2
        val data = buildMap {
            put("uid", uid)
            put("username", profile.username)
            put("text", draft.text)
            put("kind", draft.kind.name.lowercase())
            put("options", draft.options)
            (0 until 4).forEach { put("c$it", 0) }
            put("reports", 0)
            put("createdAt", FieldValue.serverTimestamp())
            put("expireAt", Timestamp(Date(expires)))
            if (matchPost) put("matchId", requireNotNull(draft.matchId))
        }
        val allowance = if (matchPost) "lastMatchPostAt" else "lastPostAt"
        try {
            db.batch()
                .set(user(uid), mapOf(allowance to FieldValue.serverTimestamp()), SetOptions.merge())
                .set(ref, data)
                .commit().await()
        } catch (e: FirebaseFirestoreException) {
            // The rules refuse a second post within 24 hours; anything else is a real error.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw DailyPostLimitException(profile.nextPostAt())
            throw e
        }
        return CommunityPost(
            ref.id, draft.community, uid, profile.username, draft.text, draft.kind, draft.options,
            List(voteSlots) { 0 }, 0, now, expires,
        )
    }

    override suspend fun ensureMatchPoll(poll: MatchPoll): CommunityPost? {
        uid()
        val ref = posts(poll.community).document(poll.id)
        val expires = System.currentTimeMillis() + Communities.LIFETIME_MS
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) {
                tx.set(
                    ref,
                    buildMap {
                        put("uid", Communities.SYSTEM_UID)
                        put("username", Communities.SYSTEM_NAME)
                        put("text", poll.question)
                        put("kind", "poll")
                        put("options", poll.options)
                        (0 until 4).forEach { put("c$it", 0) }
                        put("reports", 0)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("expireAt", Timestamp(Date(expires)))
                        put("matchId", poll.matchId)
                    },
                )
            }
        }.await()
        return ref.get().await().toPost(poll.community)
    }

    override suspend fun vote(post: CommunityPost, choice: Int) {
        val uid = uid()
        val ref = posts(post.community).document(post.id)
        db.batch()
            .set(ref.collection("votes").document(uid), mapOf("choice" to choice, "expireAt" to Timestamp(Date(post.expiresAt))))
            .update(ref, "c$choice", FieldValue.increment(1))
            .commit().await()
    }

    override suspend fun report(post: CommunityPost) {
        val uid = uid()
        val ref = posts(post.community).document(post.id)
        db.batch()
            .set(ref.collection("reports").document(uid), mapOf("expireAt" to Timestamp(Date(post.expiresAt))))
            .update(ref, "reports", FieldValue.increment(1))
            .commit().await()
    }

    override suspend fun delete(post: CommunityPost) {
        posts(post.community).document(post.id).delete().await()
    }

    private fun DocumentSnapshot.toPost(community: String): CommunityPost? {
        val created = getTimestamp("createdAt")?.toDate()?.time ?: return null
        val kind = if (getString("kind") == "poll") PostKind.POLL else PostKind.OPINION
        @Suppress("UNCHECKED_CAST")
        val options = (get("options") as? List<Any?>)?.mapNotNull { it as? String }.orEmpty()
        val slots = if (kind == PostKind.POLL) options.size else 2
        return CommunityPost(
            id = id,
            community = community,
            authorId = getString("uid") ?: return null,
            author = getString("username") ?: return null,
            text = getString("text") ?: return null,
            kind = kind,
            options = if (kind == PostKind.POLL) options else emptyList(),
            counts = List(slots) { (getLong("c$it") ?: 0L).toInt() },
            reports = (getLong("reports") ?: 0L).toInt(),
            createdAt = created,
            expiresAt = getTimestamp("expireAt")?.toDate()?.time ?: (created + Communities.LIFETIME_MS),
        )
    }
}
