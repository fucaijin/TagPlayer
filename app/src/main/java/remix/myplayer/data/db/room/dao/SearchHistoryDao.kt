package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import remix.myplayer.data.db.room.entity.SearchHistory

/**
 * 搜索记录 DAO。
 */
@Dao
abstract class SearchHistoryDao {

  /** 按最近搜索时间倒序（最多 100 条） */
  @Query("SELECT * FROM SearchHistory ORDER BY lastSearchTime DESC LIMIT 100")
  abstract fun observeAll(): Flow<List<SearchHistory>>

  /** 按搜索次数倒序（数据分析-搜索关键词排行榜） */
  @Query("SELECT * FROM SearchHistory ORDER BY searchCount DESC, lastSearchTime DESC LIMIT :limit")
  abstract suspend fun topByCount(limit: Int): List<SearchHistory>

  /** 记录一次搜索：已存在则累加次数并刷新时间 */
  @Query(
    "INSERT INTO SearchHistory (keyword, lastSearchTime, searchCount) VALUES (:keyword, :time, 1) " +
        "ON CONFLICT(keyword) DO UPDATE SET lastSearchTime = :time, searchCount = searchCount + 1"
  )
  abstract suspend fun record(keyword: String, time: Long)

  @Query("DELETE FROM SearchHistory WHERE keyword = :keyword")
  abstract suspend fun delete(keyword: String)

  @Query("DELETE FROM SearchHistory")
  abstract suspend fun clear()

  /** 只保留最近 100 条 */
  @Query(
    "DELETE FROM SearchHistory WHERE keyword NOT IN " +
        "(SELECT keyword FROM SearchHistory ORDER BY lastSearchTime DESC LIMIT 100)"
  )
  abstract suspend fun trimToLatest()
}
