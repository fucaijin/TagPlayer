package remix.myplayer.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import remix.myplayer.data.db.room.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索记录仓库：记录用户手动搜索的关键词（去重 + 累加次数）。
 *
 * 数据由"搜索页历史记录"与"数据分析-搜索关键词排行榜"共用。
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
  private val database: AppDatabase,
) {

  private val dao get() = database.searchHistoryDao()

  /** 最近搜索的关键词（按时间倒序，最多 100 条） */
  fun histories(): Flow<List<String>> =
    dao.observeAll().map { list -> list.map { it.keyword } }

  /** 记录一次搜索（空字符串忽略） */
  suspend fun record(keyword: String) {
    val key = keyword.trim()
    if (key.isEmpty()) return
    dao.record(key, System.currentTimeMillis())
    dao.trimToLatest()
  }

  /** 删除单条搜索记录 */
  suspend fun remove(keyword: String) = dao.delete(keyword)

  /** 清空全部搜索记录 */
  suspend fun clear() = dao.clear()

  /** 搜索关键词排行榜（关键词 to 搜索次数），按次数倒序 */
  suspend fun ranking(limit: Int = 100): List<Pair<String, Int>> =
    dao.topByCount(limit).map { it.keyword to it.searchCount }
}
