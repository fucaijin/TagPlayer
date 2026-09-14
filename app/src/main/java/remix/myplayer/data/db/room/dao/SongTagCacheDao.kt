package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import remix.myplayer.data.db.room.entity.SongTagCache

/**
 * 歌曲标签缓存表 DAO
 */
@Dao
abstract class SongTagCacheDao {

  @Query("SELECT * FROM SongTagCache")
  abstract fun observeAll(): Flow<List<SongTagCache>>

  @Query("SELECT * FROM SongTagCache")
  abstract suspend fun getAll(): List<SongTagCache>

  @Query("SELECT tags FROM SongTagCache WHERE path = :path")
  abstract suspend fun getTags(path: String): String?

  @Query("SELECT * FROM SongTagCache WHERE path = :path")
  abstract suspend fun getByPath(path: String): SongTagCache?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsert(cache: SongTagCache)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertAll(caches: List<SongTagCache>)

  @Query("DELETE FROM SongTagCache WHERE path IN (:paths)")
  abstract suspend fun deleteByPaths(paths: List<String>)

  @Query("DELETE FROM SongTagCache")
  abstract suspend fun clearAll()
}
