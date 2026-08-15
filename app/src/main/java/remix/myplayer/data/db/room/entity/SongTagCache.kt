package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌曲标签缓存表。
 *
 * 缓存音频文件 `AUDIO_TAGS` 自定义字段中的标签，key 为歌曲文件路径。
 * 该表只是缓存，音频文件本身才是标签的唯一真相源，启动时会增量同步。
 */
@Entity(tableName = "SongTagCache")
data class SongTagCache(
  /** 歌曲文件路径 */
  @PrimaryKey
  val path: String,
  /** 标签，使用分隔符拼接 */
  val tags: String,
  /** 最近一次同步的时间戳（毫秒） */
  val updateTime: Long
) {
  companion object {
    const val TABLE_NAME = "SongTagCache"
  }
}
