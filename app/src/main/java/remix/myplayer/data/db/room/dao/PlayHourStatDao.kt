package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import remix.myplayer.data.db.room.entity.PlayHourStat

/**
 * 每小时播放时长 DAO（数据分析用）。
 */
@Dao
abstract class PlayHourStatDao {

  @Insert
  abstract suspend fun insertAll(stats: List<PlayHourStat>)

  /** 时间区间内的小时统计（按整点小时时间戳） */
  @Query("SELECT * FROM PlayHourStat WHERE hourStart BETWEEN :from AND :to")
  abstract suspend fun between(from: Long, to: Long): List<PlayHourStat>

  @Query("DELETE FROM PlayHourStat WHERE hourStart < :before")
  abstract suspend fun deleteBefore(before: Long)

  @Query("DELETE FROM PlayHourStat")
  abstract suspend fun clearAll()
}
