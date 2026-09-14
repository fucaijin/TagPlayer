package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 搜索记录表：保存用户手动搜索的关键词（keyword 唯一，重复搜索累加次数）。
 * 用于搜索页的历史记录、以及"数据分析-搜索关键词排行榜"。
 */
@Entity(tableName = "SearchHistory")
data class SearchHistory(
  @PrimaryKey
  val keyword: String,
  /** 最近一次搜索时间 */
  val lastSearchTime: Long,
  /** 搜索次数 */
  val searchCount: Int
) {
  companion object {
    const val TABLE_NAME = "SearchHistory"
  }
}
