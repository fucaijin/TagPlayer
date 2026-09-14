package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import remix.myplayer.data.db.room.entity.AppOpenSession

/**
 * 应用使用会话 DAO（数据分析用）。
 */
@Dao
abstract class AppOpenSessionDao {

  @Insert
  abstract suspend fun insert(session: AppOpenSession): Long

  @Query("UPDATE AppOpenSession SET closeTime = :closeTime WHERE id = :id")
  abstract suspend fun close(id: Long, closeTime: Long)

  /** 指定区间内打开的会话 */
  @Query("SELECT * FROM AppOpenSession WHERE openTime BETWEEN :from AND :to ORDER BY openTime")
  abstract suspend fun between(from: Long, to: Long): List<AppOpenSession>

  /** 最近一次会话（用于结束时写入 closeTime） */
  @Query("SELECT * FROM AppOpenSession ORDER BY openTime DESC LIMIT 1")
  abstract suspend fun latest(): AppOpenSession?

  @Query("DELETE FROM AppOpenSession WHERE openTime < :before")
  abstract suspend fun deleteBefore(before: Long)

  @Query("DELETE FROM AppOpenSession")
  abstract suspend fun clearAll()
}
