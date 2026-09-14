package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次歌曲播放会话（数据分析用）。
 *
 * 同一首歌"暂停后继续"只累加到同一条会话（[playedMs]/[maxPositionMs] 持续累加），
 * 换歌或退出应用时结束并写入数据库。
 */
@Entity(tableName = "PlayEvent", indices = [Index("startTime")])
data class PlayEvent(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 歌曲文件路径 */
  val path: String,
  val title: String,
  val artist: String,
  val album: String,
  /** 本次会话开始时间 */
  val startTime: Long,
  /** 本次会话结束时间（换歌/退出时） */
  val endTime: Long,
  /** 最后一次有声播放或暂停的时间（用于统计"最晚还在听音乐的时间"） */
  val lastActiveTime: Long,
  /** 实际累计播放时长（毫秒），跨暂停累加 */
  val playedMs: Long,
  /** 会话中达到的最大播放进度（毫秒） */
  val maxPositionMs: Long,
  /** 歌曲总时长（毫秒） */
  val durationMs: Long,
  /** 是否听完（最大进度 >= 总时长 80%，无时长信息时按播放时长 >= 30s 判定） */
  val completed: Boolean,
  /** 是否未听完就切歌（换歌时未达到 80%） */
  val skipped: Boolean
) {
  companion object {
    const val TABLE_NAME = "PlayEvent"

    /** 完整播放的判定比例 */
    const val COMPLETE_RATIO = 0.8

    /** 无时长信息时的完整播放判定门槛（毫秒） */
    const val COMPLETE_FALLBACK_MS = 30_000L
  }
}
