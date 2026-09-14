package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每小时播放时长（数据分析用）。
 *
 * 播放会话按秒采样，每段播放时长归入此刻实际所在的整点小时，
 * 因此跨小时的会话会被拆成多条记录，用于"每日播放时长"与时段热力图。
 */
@Entity(tableName = "PlayHourStat", indices = [Index("hourStart")])
data class PlayHourStat(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 所在整点小时的时间戳（本地时区） */
  val hourStart: Long,
  /** 该小时内实际播放时长（毫秒） */
  val playedMs: Long
)
