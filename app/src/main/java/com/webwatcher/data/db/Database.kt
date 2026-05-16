package com.webwatcher.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.data.model.WatchTarget

// ─── DAOs ───────────────────────────────────────────────────────────────────

@Dao
interface WatchTargetDao {

    @Query("SELECT * FROM watch_targets ORDER BY createdAt DESC")
    fun getAllLive(): LiveData<List<WatchTarget>>

    @Query("SELECT * FROM watch_targets ORDER BY createdAt DESC")
    suspend fun getAll(): List<WatchTarget>

    @Query("SELECT * FROM watch_targets WHERE isActive = 1")
    suspend fun getActive(): List<WatchTarget>

    @Query("SELECT * FROM watch_targets WHERE id = :id")
    suspend fun getById(id: Long): WatchTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(target: WatchTarget): Long

    @Update
    suspend fun update(target: WatchTarget)

    @Delete
    suspend fun delete(target: WatchTarget)

    @Query("UPDATE watch_targets SET lastCheckedAt = :time, lastContentHash = :hash WHERE id = :id")
    suspend fun updateCheckInfo(id: Long, time: Long, hash: String)

    @Query("UPDATE watch_targets SET lastChangedAt = :time WHERE id = :id")
    suspend fun updateChangedAt(id: Long, time: Long)

    @Query("UPDATE watch_targets SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}

@Dao
interface AccessHistoryDao {

    @Query("SELECT * FROM access_history WHERE targetId = :targetId ORDER BY accessedAt DESC")
    fun getByTargetLive(targetId: Long): LiveData<List<AccessHistory>>

    @Query("SELECT * FROM access_history WHERE targetId = :targetId ORDER BY accessedAt DESC")
    suspend fun getByTarget(targetId: Long): List<AccessHistory>

    @Query("SELECT * FROM access_history WHERE id = :id")
    suspend fun getById(id: Long): AccessHistory?

    @Query("""
        SELECT * FROM access_history 
        WHERE targetId = :targetId 
        ORDER BY accessedAt DESC 
        LIMIT 2
    """)
    suspend fun getLatestTwo(targetId: Long): List<AccessHistory>

    @Insert
    suspend fun insert(history: AccessHistory): Long

    @Query("DELETE FROM access_history WHERE targetId = :targetId AND id NOT IN (SELECT id FROM access_history WHERE targetId = :targetId ORDER BY accessedAt DESC LIMIT :keepCount)")
    suspend fun deleteOldHistory(targetId: Long, keepCount: Int = 50)

    @Query("DELETE FROM access_history WHERE targetId = :targetId")
    suspend fun deleteByTarget(targetId: Long)
}

// ─── Database ────────────────────────────────────────────────────────────────

@Database(
    entities = [WatchTarget::class, AccessHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchTargetDao(): WatchTargetDao
    abstract fun accessHistoryDao(): AccessHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webwatcher.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
