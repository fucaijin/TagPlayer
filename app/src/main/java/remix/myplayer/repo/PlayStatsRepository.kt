package remix.myplayer.repo

import remix.myplayer.data.db.room.AppDatabase
import remix.myplayer.data.db.room.entity.AppOpenSession
import remix.myplayer.data.db.room.entity.PlayEvent
import remix.myplayer.data.db.room.entity.PlayHourStat
import remix.myplayer.data.prefs.SettingPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 歌曲维度统计 */
data class SongPlayStat(
  val path: String,
  val title: String,
  val artist: String,
  /** 完整播放次数（>=80%） */
  val playCount: Int,
  /** 累计播放时长 */
  val totalPlayedMs: Long,
  /** 未听完就切歌的次数 */
  val skippedCount: Int,
  /** 总会话数（含未听完的） */
  val sessionCount: Int
) {
  /** 跳过率（0~1） */
  val skipRate: Float
    get() = if (sessionCount == 0) 0f else skippedCount.toFloat() / sessionCount
}

/** 日期维度统计 */
data class DayPlayStat(
  /** 天序号（按 [StatsTimeUtil.dayBucket] 计算，与分界点小时有关） */
  val dayBucket: Long,
  val playedMs: Long,
  val earliest: Long? = null,
  val latest: Long? = null
)

/** 名称 + 次数（标签歌曲数等） */
data class NameCountStat(val name: String, val count: Int)

/** 标签播放次数 */
data class TagPlayStat(val tag: String, val playCount: Int)

/** 听歌时长趋势折线图的一个点 */
data class ListeningTrendPoint(
  /** 横轴标签：按天/周为 MM-dd，按月为 yyyy-MM */
  val label: String,
  /** 桶内日均听歌分钟数 */
  val minutesPerDay: Float
)

/** 趋势图的归集单位 */
enum class TrendUnit { DAY, WEEK, MONTH }

/** 应用使用统计 */
data class AppUsageStat(
  val openCount: Int,
  /** 平均多久打开一次（毫秒），样本不足时为 null */
  val avgIntervalMs: Long?,
  /** 平均每次使用时长（毫秒） */
  val avgSessionMs: Long?,
  val totalSessionMs: Long
)

/**
 * 播放/使用数据统计仓库（数据分析用）。
 *
 * 播放会话由播放服务的 [remix.myplayer.helper.PlayEventTracker] 写入，
 * 这里负责查询与聚合（数据量为个人使用级别，直接取回内存聚合，便于处理"一天分界点"等规则）。
 */
@Singleton
class PlayStatsRepository @Inject constructor(
  private val database: AppDatabase,
  private val settingPrefs: SettingPrefs,
) {

  private val eventDao get() = database.playEventDao()
  private val hourDao get() = database.playHourStatDao()
  private val appDao get() = database.appOpenSessionDao()

  /** "一天"的分界点小时：该小时之前算前一天，默认 5 点 */
  val dayStartHour: Int
    get() = settingPrefs.statsDayStartHour

  // -------- 写入 --------

  /** 写入一条播放会话及其按小时拆分的播放时长 */
  suspend fun addPlayEvent(event: PlayEvent, hourlyPlayedMs: Map<Long, Long>) {
    eventDao.insert(event)
    val stats = hourlyPlayedMs
      .filterValues { it > 0 }
      .map { (hourStart, playedMs) -> PlayHourStat(hourStart = hourStart, playedMs = playedMs) }
    if (stats.isNotEmpty()) {
      hourDao.insertAll(stats)
    }
  }

  /** 记录一次应用使用会话（进入前台 -> 退到后台） */
  suspend fun addAppSession(session: AppOpenSession) {
    appDao.insert(session)
  }

  suspend fun openAppSession(): Long =
    appDao.insert(AppOpenSession(openTime = System.currentTimeMillis(), closeTime = 0))

  suspend fun closeAppSession(id: Long, time: Long = System.currentTimeMillis()) {
    appDao.close(id, time)
  }

  // -------- 查询 --------

  suspend fun events(from: Long, to: Long): List<PlayEvent> = eventDao.between(from, to)

  /** 区间内按整点小时拆分的播放时长（每日时长与热力图用） */
  suspend fun hourStats(from: Long, to: Long): List<PlayHourStat> = hourDao.between(from, to)

  suspend fun activeEvents(from: Long, to: Long): List<PlayEvent> =
    eventDao.betweenByActiveTime(from, to)

  suspend fun appSessions(from: Long, to: Long): List<AppOpenSession> = appDao.between(from, to)

  suspend fun earliestEventTime(): Long? = eventDao.earliestStartTime()

  /** 清理 3 年前的明细，避免数据库无限增长 */
  suspend fun cleanup(keepDays: Int = 365 * 3) {
    val before = System.currentTimeMillis() - keepDays * 24L * 3600_000L
    eventDao.deleteBefore(before)
    hourDao.deleteBefore(before)
    appDao.deleteBefore(before)
  }

  /** 清除全部历史统计（播放会话、每小时统计、应用使用会话），不影响标签与搜索记录 */
  suspend fun clearAllStats() {
    eventDao.clearAll()
    hourDao.clearAll()
    appDao.clearAll()
  }

  // -------- 聚合 --------

  /** 播放次数排行（"完整播放"才计数） */
  fun playCountRanking(
    events: List<PlayEvent>,
    ascending: Boolean = false,
    limit: Int = 50,
  ): List<SongPlayStat> =
    groupSongs(events)
      .sortedWith(statComparator(ascending) { it.playCount.toLong() })
      .filter { it.playCount > 0 }
      .take(limit)

  /** 播放时长排行 */
  fun durationRanking(
    events: List<PlayEvent>,
    ascending: Boolean = false,
    limit: Int = 50,
  ): List<SongPlayStat> =
    groupSongs(events)
      .filter { it.totalPlayedMs > 0 }
      .sortedWith(statComparator(ascending) { it.totalPlayedMs })
      .take(limit)

  /** 跳过排行（未播放到 80% 就切歌） */
  fun skippedRanking(
    events: List<PlayEvent>,
    ascending: Boolean = false,
    limit: Int = 50,
  ): List<SongPlayStat> =
    groupSongs(events)
      .filter { it.skippedCount > 0 }
      .sortedWith(statComparator(ascending) { it.skippedCount.toLong() })
      .take(limit)

  /** 每日播放时长（按分界点小时归入某一天；跨小时/跨天的会话已按小时拆分） */
  fun dailyPlayDurations(hourStats: List<PlayHourStat>): List<DayPlayStat> {
    val hour = dayStartHour
    return hourStats.groupBy { StatsTimeUtil.dayBucket(it.hourStart, hour) }
      .map { (bucket, list) ->
        DayPlayStat(
          dayBucket = bucket,
          playedMs = list.sumOf { it.playedMs }
        )
      }
      .sortedByDescending { it.dayBucket }
  }

  /** 每天最早/最晚听歌（按分界点小时归入某一天） */
  fun dailyActiveTimes(events: List<PlayEvent>): List<DayPlayStat> {
    val hour = dayStartHour
    return events.groupBy { StatsTimeUtil.dayBucket(it.lastActiveTime, hour) }
      .map { (bucket, list) ->
        DayPlayStat(
          dayBucket = bucket,
          playedMs = list.sumOf { it.playedMs },
          earliest = list.minOfOrNull { it.startTime },
          latest = list.maxOfOrNull { it.lastActiveTime }
        )
      }
      .sortedByDescending { it.dayBucket }
  }

  /** 时段热力图：7(周一~周日) x 24 小时，值为播放时长(ms) */
  fun hourHeatmap(hourStats: List<PlayHourStat>): List<List<Long>> {
    val cells = MutableList(7) { MutableList(24) { 0L } }
    hourStats.forEach { stat ->
      val day = StatsTimeUtil.dayOfWeekIndex(stat.hourStart)
      val hour = StatsTimeUtil.hourOfDay(stat.hourStart)
      cells[day][hour] += stat.playedMs
    }
    return cells
  }

  /**
   * 听歌时长趋势：把每小时统计按"自然天/自然周/自然月"归集，取桶内日均听歌分钟数。
   *
   * 区间 ≤ 30 天按天；≤ 182 天（约半年）按自然周（周一为首日）；更长按自然月。
   * [from] 为 0（全部历史）时从最早的数据开始；区间内没有播放的天按 0 计入日均。
   */
  fun listeningTrend(
    hourStats: List<PlayHourStat>,
    from: Long,
    to: Long,
  ): List<ListeningTrendPoint> {
    if (hourStats.isEmpty()) return emptyList()
    val hour = dayStartHour
    val fromBucket = if (from > 0) {
      StatsTimeUtil.dayBucket(from, hour)
    } else {
      StatsTimeUtil.dayBucket(hourStats.minOf { it.hourStart }, hour)
    }
    val toBucket = StatsTimeUtil.dayBucket(to, hour)
    if (toBucket < fromBucket) return emptyList()

    val unit = when {
      toBucket - fromBucket + 1 <= 30 -> TrendUnit.DAY
      toBucket - fromBucket + 1 <= 182 -> TrendUnit.WEEK
      else -> TrendUnit.MONTH
    }

    // 先把每小时统计归到"天"，再按归集单位分桶（没有播放的天也算进桶内天数）
    val playedMsByDay = mutableMapOf<Long, Long>()
    hourStats.forEach { stat ->
      val day = StatsTimeUtil.dayBucket(stat.hourStart, hour)
      playedMsByDay[day] = (playedMsByDay[day] ?: 0L) + stat.playedMs
    }

    val playedByKey = mutableMapOf<String, Long>()
    val daysByKey = mutableMapOf<String, Int>()
    for (day in fromBucket..toBucket) {
      val key = StatsTimeUtil.trendBucketKey(StatsTimeUtil.bucketStartTime(day, hour), unit)
      playedByKey[key] = (playedByKey[key] ?: 0L) + (playedMsByDay[day] ?: 0L)
      daysByKey[key] = (daysByKey[key] ?: 0) + 1
    }

    return playedByKey.keys.sorted().map { key ->
      val days = daysByKey.getValue(key).coerceAtLeast(1)
      ListeningTrendPoint(
        label = StatsTimeUtil.trendBucketLabel(key),
        minutesPerDay = playedByKey.getValue(key) / 60_000f / days
      )
    }
  }

  /** 标签播放次数排行（一首歌的每个标签都计入） */
  fun tagPlayRanking(
    events: List<PlayEvent>,
    tagsByPath: Map<String, Set<String>>,
    ascending: Boolean = false,
    limit: Int = 50,
  ): List<TagPlayStat> {
    val counter = mutableMapOf<String, Int>()
    events.forEach { event ->
      tagsByPath[event.path]?.forEach { tag ->
        counter[tag] = (counter[tag] ?: 0) + 1
      }
    }
    val sorted = counter.map { TagPlayStat(it.key, it.value) }
      .sortedWith(
        compareBy<TagPlayStat> { it.playCount }.let { if (ascending) it else it.reversed() }
      )
    return sorted.take(limit)
  }

  /** 标签对应的歌曲数量（基于当前曲库标签，不依赖日期区间） */
  fun tagSongCounts(
    tagsByPath: Map<String, Set<String>>,
    ascending: Boolean = false,
  ): List<NameCountStat> {
    val counter = mutableMapOf<String, Int>()
    tagsByPath.values.forEach { tags ->
      tags.forEach { tag -> counter[tag] = (counter[tag] ?: 0) + 1 }
    }
    return counter.map { NameCountStat(it.key, it.value) }
      .sortedWith(compareBy<NameCountStat> { it.count }.let { if (ascending) it else it.reversed() })
  }

  /** 应用使用统计：平均多久打开一次、平均每次使用多久 */
  fun appUsage(sessions: List<AppOpenSession>): AppUsageStat {
    if (sessions.isEmpty()) {
      return AppUsageStat(0, null, null, 0)
    }
    val sorted = sessions.sortedBy { it.openTime }
    val now = System.currentTimeMillis()
    val durations = sorted.map { session ->
      val close = if (session.closeTime > 0) session.closeTime else now
      (close - session.openTime).coerceAtLeast(0)
    }
    val intervals = sorted.zipWithNext { a, b -> b.openTime - a.openTime }
    return AppUsageStat(
      openCount = sorted.size,
      avgIntervalMs = intervals.takeIf { it.isNotEmpty() }?.average()?.toLong(),
      avgSessionMs = durations.average().toLong(),
      totalSessionMs = durations.sum()
    )
  }

  private fun groupSongs(events: List<PlayEvent>): List<SongPlayStat> =
    events.groupBy { it.path }.map { (path, list) ->
      val last = list.last()
      SongPlayStat(
        path = path,
        title = last.title,
        artist = last.artist,
        playCount = list.count { it.completed },
        totalPlayedMs = list.sumOf { it.playedMs },
        skippedCount = list.count { it.skipped },
        sessionCount = list.size
      )
    }

  private fun statComparator(
    ascending: Boolean,
    selector: (SongPlayStat) -> Long,
  ): Comparator<SongPlayStat> {
    val base = compareBy<SongPlayStat> { selector(it) }.thenBy { it.title }
    return if (ascending) base else base.reversed()
  }
}

/**
 * 数据分析的时间工具：按"一天分界点"归天、格式化为可读文本。
 */
object StatsTimeUtil {

  /** 时间戳 -> 天序号（分界点之前算前一天） */
  fun dayBucket(timeMs: Long, dayStartHour: Int): Long {
    val offset = dayStartHour * 3600_000L
    return Math.floorDiv(timeMs - offset, 24L * 3600_000L)
  }

  /** 天序号 -> 该天的起始时间戳 */
  fun bucketStartTime(bucket: Long, dayStartHour: Int): Long =
    bucket * 24L * 3600_000L + dayStartHour * 3600_000L

  fun hourOfDay(timeMs: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMs }
    return calendar.get(Calendar.HOUR_OF_DAY)
  }

  /** 时间戳 -> 所在整点小时的时间戳（本地时区） */
  fun hourStartTime(timeMs: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMs }
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /** 0=周一 ... 6=周日 */
  fun dayOfWeekIndex(timeMs: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMs }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
  }

  /**
   * 趋势图归集键：同一天/同一周/同一月得到同样的键，且可直接按字典序排序。
   * 按周取该周周一的日期，因此键形如 yyyy-MM-dd（天/周）或 yyyy-MM（月）。
   */
  fun trendBucketKey(dayStartMs: Long, unit: TrendUnit): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    when (unit) {
      TrendUnit.DAY -> Unit
      TrendUnit.WEEK -> calendar.add(Calendar.DAY_OF_MONTH, -dayOfWeekIndex(dayStartMs))
      TrendUnit.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
    }
    val pattern = if (unit == TrendUnit.MONTH) "yyyy-MM" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(calendar.timeInMillis))
  }

  /** 趋势图横轴短标签：天/周显示 MM-dd，月显示 yyyy-MM */
  fun trendBucketLabel(key: String): String = if (key.length > 7) key.substring(5) else key

  /** 格式化天序号为 yyyy-MM-dd */
  fun formatDay(bucket: Long, dayStartHour: Int): String {
    val start = bucketStartTime(bucket, dayStartHour)
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(start))
  }

  /** 格式化时间点 HH:mm */
  fun formatTime(timeMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))

  /** 格式化时长：不足 1 小时用 "X min"，超过用 "Xh Ymin" */
  fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    return if (totalMinutes < 60) {
      "$totalMinutes min"
    } else {
      val hours = totalMinutes / 60
      val minutes = totalMinutes % 60
      "${hours}h ${minutes}min"
    }
  }

  fun formatMinutes(ms: Long): String = "${ms / 60_000} min"

  /** 时间戳 -> yyyyMMdd */
  fun formatYyyyMMdd(timeMs: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timeMs))

  /** 解析 yyyyMMdd（当天 00:00，本地时区），非法返回 null */
  fun parseYyyyMMdd(text: String): Long? {
    return try {
      val format = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply { isLenient = false }
      format.parse(text)?.time
    } catch (e: Exception) {
      null
    }
  }

  /** 某天 00:00 对应的当天最后一毫秒 */
  fun endOfDay(startMs: Long): Long = startMs + 24L * 3600_000L - 1
}
