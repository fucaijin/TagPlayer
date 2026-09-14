package remix.myplayer.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.repo.AppUsageStat
import remix.myplayer.repo.DayPlayStat
import remix.myplayer.repo.ListeningTrendPoint
import remix.myplayer.repo.NameCountStat
import remix.myplayer.repo.PlayStatsRepository
import remix.myplayer.repo.SearchHistoryRepository
import remix.myplayer.repo.SongPlayStat
import remix.myplayer.repo.SongTagRepository
import remix.myplayer.repo.StatsTimeUtil
import remix.myplayer.repo.TagPlayStat
import remix.myplayer.ui.nav.MessageNotifier
import java.util.Calendar
import javax.inject.Inject

/** 数据分析的时间区间 */
enum class StatsRangePreset(@get:StringRes val labelRes: Int) {
  LAST_7_DAYS(R.string.stats_range_7d),
  LAST_30_DAYS(R.string.stats_range_30d),
  LAST_90_DAYS(R.string.stats_range_90d),
  LAST_YEAR(R.string.stats_range_1y),
  THIS_WEEK(R.string.stats_range_this_week),
  THIS_MONTH(R.string.stats_range_this_month),
  THIS_QUARTER(R.string.stats_range_this_quarter),
  THIS_YEAR(R.string.stats_range_this_year)
}

data class DataAnalysisState(
  val preset: StatsRangePreset = StatsRangePreset.LAST_30_DAYS,
  /** 是否使用自定义区间（两个 yyyyMMdd 输入框） */
  val customRange: Boolean = false,
  val from: Long = 0,
  val to: Long = 0,
  val loading: Boolean = false,
  /** 各排行榜是否倒序（默认倒序） */
  val playCountDesc: Boolean = true,
  val durationDesc: Boolean = true,
  val skippedDesc: Boolean = true,
  val dailyDesc: Boolean = true,
  val activeDesc: Boolean = true,
  val tagCountDesc: Boolean = true,
  val tagPlayDesc: Boolean = true,
  val searchDesc: Boolean = true,
  val playCountList: List<SongPlayStat> = emptyList(),
  val durationList: List<SongPlayStat> = emptyList(),
  val skippedList: List<SongPlayStat> = emptyList(),
  val dailyList: List<DayPlayStat> = emptyList(),
  /** 听歌时长趋势（折线图） */
  val trend: List<ListeningTrendPoint> = emptyList(),
  val activeList: List<DayPlayStat> = emptyList(),
  val tagCounts: List<NameCountStat> = emptyList(),
  val tagPlays: List<TagPlayStat> = emptyList(),
  val searchRanking: List<Pair<String, Int>> = emptyList(),
  val heatmap: List<List<Long>> = emptyList(),
  val appUsage: AppUsageStat = AppUsageStat(0, null, null, 0),
  /** 一天的起始小时（默认 5 点） */
  val dayStartHour: Int = 5,
  /** 是否有任何播放数据 */
  val hasPlayData: Boolean = false
)

/**
 * 数据分析：按选定时间区间统计播放次数/时长/跳过/标签/时段分布，以及应用使用习惯。
 *
 * 注意：数据从本功能上线后开始采集，历史播放数据无法追溯。
 */
@HiltViewModel
class DataAnalysisViewModel @Inject constructor(
  private val statsRepo: PlayStatsRepository,
  private val songTagRepo: SongTagRepository,
  private val searchHistoryRepo: SearchHistoryRepository,
) : ViewModel() {

  private val _state = MutableStateFlow(DataAnalysisState())
  val state = _state.asStateFlow()

  /** 修改统计区间后重新加载 */
  fun setPreset(preset: StatsRangePreset) {
    _state.value = _state.value.copy(preset = preset, customRange = false)
    load()
  }

  /**
   * 自定义区间分析：两个参数都是 yyyyMMdd，均可留空
   * （from 空 = 全部历史，to 空 = 至今）。日期非法或 from > to 时返回 false 且不刷新数据。
   */
  fun analyzeRange(fromText: String, toText: String): Boolean {
    val from = if (fromText.isBlank()) 0L
    else StatsTimeUtil.parseYyyyMMdd(fromText) ?: return false
    val to = if (toText.isBlank()) System.currentTimeMillis()
    else StatsTimeUtil.parseYyyyMMdd(toText)?.let { StatsTimeUtil.endOfDay(it) } ?: return false
    if (from > to) return false
    _state.value = _state.value.copy(customRange = true, from = from, to = to)
    load()
    return true
  }

  /** 清除全部历史统计（播放会话、每日/时段统计、应用使用记录），随后重新加载报表 */
  fun clearAllStats() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { statsRepo.clearAllStats() }
      MessageNotifier.show(R.string.stats_clear_success)
      load()
    }
  }

  fun togglePlayCountOrder() {
    val desc = !_state.value.playCountDesc
    _state.value = _state.value.copy(playCountDesc = desc)
    load()
  }

  fun toggleDurationOrder() {
    val desc = !_state.value.durationDesc
    _state.value = _state.value.copy(durationDesc = desc)
    load()
  }

  fun toggleSkippedOrder() {
    val desc = !_state.value.skippedDesc
    _state.value = _state.value.copy(skippedDesc = desc)
    load()
  }

  fun toggleDailyOrder() {
    _state.value = _state.value.copy(dailyDesc = !_state.value.dailyDesc)
  }

  fun toggleActiveOrder() {
    _state.value = _state.value.copy(activeDesc = !_state.value.activeDesc)
  }

  fun toggleTagCountOrder() {
    _state.value = _state.value.copy(tagCountDesc = !_state.value.tagCountDesc)
  }

  fun toggleTagPlayOrder() {
    _state.value = _state.value.copy(tagPlayDesc = !_state.value.tagPlayDesc)
  }

  fun toggleSearchOrder() {
    _state.value = _state.value.copy(searchDesc = !_state.value.searchDesc)
  }

  fun load() {
    val current = _state.value
    val (from, to) = if (current.customRange) {
      current.from to current.to
    } else {
      resolveRange(current.preset)
    }
    _state.value = current.copy(from = from, to = to, loading = true)

    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        val dayStartHour = statsRepo.dayStartHour
        val events = statsRepo.events(from, to)
        val activeEvents = statsRepo.activeEvents(from, to)
        val hourStats = statsRepo.hourStats(from, to)
        val sessions = statsRepo.appSessions(from, to)
        val tagsByPath = songTagRepo.allTagsByPath()
        val searchRanking = searchHistoryRepo.ranking(DEFAULT_LIMIT)
        AnalysisResult(
          playCount = statsRepo.playCountRanking(events, !current.playCountDesc),
          duration = statsRepo.durationRanking(events, !current.durationDesc),
          skipped = statsRepo.skippedRanking(events, !current.skippedDesc),
          daily = statsRepo.dailyPlayDurations(hourStats),
          trend = statsRepo.listeningTrend(hourStats, from, to),
          active = statsRepo.dailyActiveTimes(activeEvents),
          tagCounts = statsRepo.tagSongCounts(tagsByPath),
          tagPlays = statsRepo.tagPlayRanking(events, tagsByPath, !current.tagPlayDesc),
          searchRanking = searchRanking,
          heatmap = statsRepo.hourHeatmap(hourStats),
          appUsage = statsRepo.appUsage(sessions),
          hasPlayData = events.isNotEmpty() || statsRepo.earliestEventTime() != null,
          dayStartHour = dayStartHour
        )
      }
      _state.value = _state.value.copy(
        loading = false,
        playCountList = result.playCount,
        durationList = result.duration,
        skippedList = result.skipped,
        dailyList = result.daily,
        trend = result.trend,
        activeList = result.active,
        tagCounts = result.tagCounts,
        tagPlays = result.tagPlays,
        searchRanking = result.searchRanking,
        heatmap = result.heatmap,
        appUsage = result.appUsage,
        hasPlayData = result.hasPlayData,
        dayStartHour = result.dayStartHour
      )
    }
  }

  private fun resolveRange(preset: StatsRangePreset): Pair<Long, Long> {
    val now = System.currentTimeMillis()
    val hour = statsRepo.dayStartHour
    val calendar = Calendar.getInstance()

    /** 今天（按分界点）的起始时间 */
    val todayStart = StatsTimeUtil.bucketStartTime(StatsTimeUtil.dayBucket(now, hour), hour)

    val start = when (preset) {
      StatsRangePreset.LAST_7_DAYS -> todayStart - 6 * DAY
      StatsRangePreset.LAST_30_DAYS -> todayStart - 29 * DAY
      StatsRangePreset.LAST_90_DAYS -> todayStart - 89 * DAY
      StatsRangePreset.LAST_YEAR -> todayStart - 364 * DAY
      StatsRangePreset.THIS_WEEK -> {
        calendar.timeInMillis = todayStart
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        todayStart - dayOfWeek * DAY
      }

      StatsRangePreset.THIS_MONTH -> {
        calendar.timeInMillis = todayStart
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
      }

      StatsRangePreset.THIS_QUARTER -> {
        calendar.timeInMillis = todayStart
        val firstMonth = (calendar.get(Calendar.MONTH) / 3) * 3
        calendar.set(Calendar.MONTH, firstMonth)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
      }

      StatsRangePreset.THIS_YEAR -> {
        calendar.timeInMillis = todayStart
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
      }
    }
    return start to now
  }

  private data class AnalysisResult(
    val playCount: List<SongPlayStat>,
    val duration: List<SongPlayStat>,
    val skipped: List<SongPlayStat>,
    val daily: List<DayPlayStat>,
    val trend: List<ListeningTrendPoint>,
    val active: List<DayPlayStat>,
    val tagCounts: List<NameCountStat>,
    val tagPlays: List<TagPlayStat>,
    val searchRanking: List<Pair<String, Int>>,
    val heatmap: List<List<Long>>,
    val appUsage: AppUsageStat,
    val hasPlayData: Boolean,
    val dayStartHour: Int
  )

  companion object {
    const val DEFAULT_LIMIT = 50
    private const val DAY = 24L * 3600_000L
  }
}
