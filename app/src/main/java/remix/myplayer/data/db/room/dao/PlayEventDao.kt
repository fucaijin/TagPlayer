package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import remix.myplayer.data.db.room.entity.PlayEvent

/**
 * 播放会话 DAO（数据分析用）。
 */
@Dao
abstract class PlayEventDao {

  @Insert
  abstract suspend fun insert(event: PlayEvent): Long

  /** 指定时间区间内的播放会话（按开始时间） */
  @Query("SELECT * FROM PlayEvent WHERE startTime BETWEEN :from AND :to ORDER BY startTime")
  abstract suspend fun between(from: Long, to: Long): List<PlayEvent>

  /** 有播放活动（或暂停）落在区间内的会话 */
  @Query(
    "SELECT * FROM PlayEvent WHERE lastActiveTime BETWEEN :from AND :to " +
        "OR endTime BETWEEN :from AND :to ORDER BY lastActiveTime"
  )
  abstract suspend fun betweenByActiveTime(from: Long, to: Long): List<PlayEvent>

  @Query("SELECT * FROM PlayEvent")
  abstract suspend fun all(): List<PlayEvent>

  /** 最近 N 天的播放记录（用于按日期聚合） */
  @Query("SELECT * FROM PlayEvent WHERE startTime >= :from ORDER BY startTime")
  abstract suspend fun since(from: Long): List<PlayEvent>

  /** 最早的一条播放记录时间，无记录返回 null */
  @Query("SELECT MIN(startTime) FROM PlayEvent")
  abstract suspend fun earliestStartTime(): Long?

  @Query("DELETE FROM PlayEvent WHERE startTime < :before")
  abstract suspend fun deleteBefore(before: Long)

  @Query("SELECT COUNT(*) FROM PlayEvent")
  abstract suspend fun count(): Int
}
