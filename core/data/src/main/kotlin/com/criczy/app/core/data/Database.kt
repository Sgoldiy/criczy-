package com.criczy.app.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class FavouriteType { TEAM, PLAYER, MATCH, COMPETITION }

@Entity(tableName = "favourites", primaryKeys = ["type", "refId"])
data class FavouriteEntity(
    val type: String,
    val refId: Int,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val colorHex: String?,
    val addedAt: Long,
)

/** Players seen in scorecards. The provider has no name search, so this is the search index. */
@Entity(tableName = "seen_players")
data class SeenPlayerEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val teamId: Int?,
    val teamName: String?,
    val role: String?,
    val imageUrl: String?,
    val lastSeenAt: Long,
)

/** What the alert worker last knew about a match, so each event is notified once. */
@Entity(tableName = "alert_state")
data class AlertStateEntity(
    @PrimaryKey val matchId: Int,
    val state: String,
    val inningsNumber: Int,
    val wickets: Int,
    val startingSoonSent: Boolean,
    val updatedAt: Long,
)

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM favourites")
    suspend fun all(): List<FavouriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE type = :type AND refId = :refId)")
    fun observeIs(type: String, refId: Int): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favourites WHERE type = :type")
    suspend fun count(type: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE type = :type AND refId = :refId)")
    suspend fun exists(type: String, refId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE type = :type AND refId = :refId")
    suspend fun delete(type: String, refId: Int)
}

@Dao
interface SeenPlayerDao {
    @Upsert
    suspend fun upsert(players: List<SeenPlayerEntity>)

    @Query("SELECT * FROM seen_players WHERE name LIKE '%' || :query || '%' ORDER BY lastSeenAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<SeenPlayerEntity>

    @Query("SELECT * FROM seen_players WHERE id = :id")
    suspend fun byId(id: Int): SeenPlayerEntity?

    @Query("SELECT COUNT(*) FROM seen_players")
    suspend fun count(): Int
}

@Dao
interface AlertStateDao {
    @Query("SELECT * FROM alert_state WHERE matchId IN (:ids)")
    suspend fun forMatches(ids: List<Int>): List<AlertStateEntity>

    @Upsert
    suspend fun upsert(states: List<AlertStateEntity>)

    @Query("DELETE FROM alert_state WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(
    entities = [
        FavouriteEntity::class, SeenPlayerEntity::class, AlertStateEntity::class,
        CommunityPostEntity::class, CommunityVoteEntity::class, BlockedAuthorEntity::class, CommunityProfileEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CriczyDatabase : RoomDatabase() {
    abstract fun favourites(): FavouriteDao
    abstract fun seenPlayers(): SeenPlayerDao
    abstract fun alertState(): AlertStateDao
    abstract fun community(): CommunityDao

    companion object {
        fun create(context: Context): CriczyDatabase =
            Room.databaseBuilder(context.applicationContext, CriczyDatabase::class.java, "criczy.db").build()

        fun inMemory(context: Context): CriczyDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, CriczyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
