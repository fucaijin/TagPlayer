package remix.myplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.repo.DayPlayStat
import remix.myplayer.repo.SongPlayStat
import remix.myplayer.repo.StatsTimeUtil
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.viewmodel.StatsRangePreset
import remix.myplayer.viewmodel.dataAnalysisViewModel
import kotlin.math.roundToInt

/**
 * 数据分析报表：按日期区间统计播放次数/时长/跳过/标签/搜索/时段分布与应用使用习惯。
 * 内嵌在 设置-数据分析 页面（"一天分界点"下方）显示。
 *
 * 数据从本功能上线后开始采集，历史数据无法追溯。
 */
@Composable
fun DataAnalysisReport(modifier: Modifier = Modifier) {
  val vm = dataAnalysisViewModel
  val state by vm.state.collectAsStateWithLifecycle()

  // 自定义区间输入框（yyyyMMdd，可留空）
  var fromText by rememberSaveable { mutableStateOf("") }
  var toText by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(Unit) {
    vm.load()
  }

  // 切换预设或点击“分析”后，把实际生效的起止日期回填到输入框
  LaunchedEffect(state.from, state.to) {
    if (state.to > 0) {
      fromText = if (state.from > 0) StatsTimeUtil.formatYyyyMMdd(state.from) else ""
      toText = StatsTimeUtil.formatYyyyMMdd(state.to)
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = 24.dp)
  ) {
    StatsRangeSelector(
      preset = state.preset,
      fromText = fromText,
      toText = toText,
      onFromChange = { fromText = it },
      onToChange = { toText = it },
      onAnalyze = {
        if (!vm.analyzeRange(fromText, toText)) {
          MessageNotifier.show(R.string.stats_range_invalid)
        }
      },
      onSelectPreset = { vm.setPreset(it) }
    )

    if (!state.hasPlayData) {
      TextSecondary(
        stringResource(R.string.stats_no_data),
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
    }

    // 11.11 使用习惯：平均多久打开一次、平均每次听多久
    StatsCard(
      title = stringResource(R.string.stats_app_usage),
      tip = null
    ) {
      StatItem(
        stringResource(R.string.stats_app_open_count),
        stringResource(R.string.stats_times, state.appUsage.openCount)
      )
      StatItem(
        stringResource(R.string.stats_app_avg_interval),
        state.appUsage.avgIntervalMs?.let { StatsTimeUtil.formatDuration(it) }
          ?: stringResource(R.string.stats_no_ranking)
      )
      StatItem(
        stringResource(R.string.stats_app_avg_session),
        state.appUsage.avgSessionMs?.let { StatsTimeUtil.formatDuration(it) }
          ?: stringResource(R.string.stats_no_ranking)
      )
    }

    // 11.2 播放次数排行 / 11.3 播放时长排行
    SongRankingCard(
      title = stringResource(R.string.stats_play_count),
      tip = stringResource(R.string.stats_play_count_tip),
      list = state.playCountList,
      descending = state.playCountDesc,
      onToggleOrder = vm::togglePlayCountOrder
    ) { stringResource(R.string.stats_times, it.playCount) }

    SongRankingCard(
      title = stringResource(R.string.stats_play_duration),
      tip = null,
      list = state.durationList,
      descending = state.durationDesc,
      onToggleOrder = vm::toggleDurationOrder
    ) { StatsTimeUtil.formatDuration(it.totalPlayedMs) }

    // 11.4 / 11.10 跳过排行（未播到 80% 就切歌）
    SongRankingCard(
      title = stringResource(R.string.stats_skipped),
      tip = stringResource(R.string.stats_skipped_tip),
      list = state.skippedList,
      descending = state.skippedDesc,
      onToggleOrder = vm::toggleSkippedOrder
    ) { song ->
      stringResource(R.string.stats_times, song.skippedCount) +
          "  " + stringResource(
        R.string.stats_skip_rate,
        (song.skipRate * 100).roundToInt()
      )
    }

    // 11.6 每日播放时长
    StatsCard(
      title = stringResource(R.string.stats_daily_duration),
      tip = null,
      descending = state.dailyDesc,
      onToggleOrder = vm::toggleDailyOrder
    ) {
      if (state.dailyList.isEmpty()) {
        EmptyTip()
      } else {
        DailyDurationChart(state.dailyList, state.dayStartHour)
        val ranked = if (state.dailyDesc) {
          state.dailyList.sortedByDescending { it.playedMs }
        } else {
          state.dailyList.sortedBy { it.playedMs }
        }
        ranked.take(MAX_LIST_ROWS).forEach { day ->
          StatItem(
            StatsTimeUtil.formatDay(day.dayBucket, state.dayStartHour),
            StatsTimeUtil.formatDuration(day.playedMs)
          )
        }
      }
    }

    // 11.5 每天最早/最晚听歌时间
    StatsCard(
      title = stringResource(R.string.stats_daily_active),
      tip = null,
      descending = state.activeDesc,
      onToggleOrder = vm::toggleActiveOrder
    ) {
      if (state.activeList.isEmpty()) {
        EmptyTip()
      } else {
        val ranked = if (state.activeDesc) state.activeList else state.activeList.reversed()
        ranked.take(MAX_LIST_ROWS).forEach { day ->
          StatItem(
            StatsTimeUtil.formatDay(day.dayBucket, state.dayStartHour),
            stringResource(R.string.stats_earliest) + " " +
                (day.earliest?.let { StatsTimeUtil.formatTime(it) } ?: "-") + "   " +
                stringResource(R.string.stats_latest) + " " +
                (day.latest?.let { StatsTimeUtil.formatTime(it) } ?: "-")
          )
        }
      }
    }

    // 11.8 标签歌曲数量
    StatsCard(
      title = stringResource(R.string.stats_tag_song_count),
      tip = null,
      descending = state.tagCountDesc,
      onToggleOrder = vm::toggleTagCountOrder
    ) {
      if (state.tagCounts.isEmpty()) {
        EmptyTip()
      } else {
        val list = if (state.tagCountDesc) state.tagCounts else state.tagCounts.reversed()
        list.take(MAX_LIST_ROWS).forEachIndexed { index, item ->
          NameCountRow(index + 1, item.name, stringResource(R.string.song_count_1, item.count))
        }
      }
    }

    // 11.9 最常播放的标签
    StatsCard(
      title = stringResource(R.string.stats_favorite_tags),
      tip = null,
      descending = state.tagPlayDesc,
      onToggleOrder = vm::toggleTagPlayOrder
    ) {
      if (state.tagPlays.isEmpty()) {
        EmptyTip()
      } else {
        state.tagPlays.take(MAX_LIST_ROWS).forEachIndexed { index, item ->
          NameCountRow(
            index + 1,
            item.tag,
            stringResource(R.string.stats_times, item.playCount)
          )
        }
      }
    }

    // 11.7 搜索关键词排行
    StatsCard(
      title = stringResource(R.string.stats_search_ranking),
      tip = null,
      descending = state.searchDesc,
      onToggleOrder = vm::toggleSearchOrder
    ) {
      if (state.searchRanking.isEmpty()) {
        EmptyTip()
      } else {
        val list = if (state.searchDesc) state.searchRanking else state.searchRanking.reversed()
        list.take(MAX_LIST_ROWS).forEachIndexed { index, (keyword, count) ->
          NameCountRow(index + 1, keyword, stringResource(R.string.stats_times, count))
        }
      }
    }

    // 11.12 7×24h 时段热力图
    StatsCard(
      title = stringResource(R.string.stats_heatmap),
      tip = stringResource(R.string.stats_heatmap_tip)
    ) {
      if (state.heatmap.isEmpty() || state.heatmap.all { row -> row.all { it <= 0 } }) {
        EmptyTip()
      } else {
        HeatmapGrid(state.heatmap)
      }
    }

    // 24h 时段热力图：把 7×24 的 7 行按小时纵向合并为 1 行
    val heatmap24 = if (state.heatmap.isEmpty()) {
      emptyList()
    } else {
      List(24) { hour ->
        state.heatmap.sumOf { row -> row.getOrElse(hour) { 0L } }
      }
    }
    StatsCard(
      title = stringResource(R.string.stats_heatmap_24h),
      tip = stringResource(R.string.stats_heatmap_24h_tip)
    ) {
      if (heatmap24.isEmpty() || heatmap24.all { it <= 0 }) {
        EmptyTip()
      } else {
        HeatmapGrid(listOf(heatmap24), showWeekday = false)
      }
    }
  }
}

private const val MAX_LIST_ROWS = 30

/** 统计区间：自定义起止日期输入 + “分析”按钮 + 预设下拉列表 */
@Composable
private fun StatsRangeSelector(
  preset: StatsRangePreset,
  fromText: String,
  toText: String,
  onFromChange: (String) -> Unit,
  onToChange: (String) -> Unit,
  onAnalyze: () -> Unit,
  onSelectPreset: (StatsRangePreset) -> Unit,
) {
  val theme = LocalTheme.current
  Column(modifier = Modifier.padding(vertical = 8.dp)) {
    TextSecondary(
      stringResource(R.string.stats_range),
      fontSize = 13.sp,
      modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      DateInput(value = fromText, onValueChange = onFromChange, modifier = Modifier.width(84.dp))
      TextSecondary("~", fontSize = 13.sp)
      DateInput(value = toText, onValueChange = onToChange, modifier = Modifier.width(84.dp))
      PresetDropdown(preset, onSelectPreset)
      Surface(
        shape = RoundedCornerShape(50),
        color = theme.secondary,
        onClick = onAnalyze
      ) {
        Text(
          stringResource(R.string.stats_range_analyze),
          fontSize = 13.sp,
          color = theme.primaryReverse,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
      }
    }
  }
}

/** yyyyMMdd 日期输入框（只允许数字，最多 8 位，可留空） */
@Composable
private fun DateInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
  val theme = LocalTheme.current
  BasicTextField(
    value = value,
    onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(8)) },
    singleLine = true,
    textStyle = TextStyle(color = theme.textPrimary, fontSize = 14.sp, textAlign = TextAlign.Center),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Number,
      imeAction = ImeAction.Done
    ),
    cursorBrush = SolidColor(theme.primary),
    modifier = modifier,
    decorationBox = { innerTextField ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.dialogBackground, RoundedCornerShape(6.dp))
          .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        if (value.isEmpty()) {
          TextSecondary(stringResource(R.string.stats_range_date_hint), fontSize = 13.sp)
        }
        innerTextField()
      }
    }
  )
}

/** 预设区间下拉列表：选中后立即刷新并把区间回填到输入框 */
@Composable
private fun PresetDropdown(preset: StatsRangePreset, onSelect: (StatsRangePreset) -> Unit) {
  val theme = LocalTheme.current
  var expanded by remember { mutableStateOf(false) }

  Box {
    Surface(
      shape = RoundedCornerShape(50),
      color = theme.dialogBackground,
      onClick = { expanded = true }
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
      ) {
        Text(stringResource(preset.labelRes), fontSize = 13.sp, color = theme.textPrimary)
        Icon(
          Icons.Filled.ArrowDropDown,
          contentDescription = null,
          tint = theme.textSecondary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      StatsRangePreset.entries.forEach { item ->
        DropdownMenuItem(
          text = { Text(stringResource(item.labelRes), fontSize = 13.sp) },
          onClick = {
            expanded = false
            onSelect(item)
          }
        )
      }
    }
  }
}

/** 统计卡片：标题 + 说明 + 排序切换 */
@Composable
private fun StatsCard(
  title: String,
  tip: String?,
  descending: Boolean? = null,
  onToggleOrder: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val theme = LocalTheme.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .background(theme.dialogBackground, RoundedCornerShape(10.dp))
      .padding(12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextPrimary(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
      if (descending != null && onToggleOrder != null) {
        TextPrimary(
          stringResource(
            if (descending) R.string.stats_order_desc else R.string.stats_order_asc
          ),
          fontSize = 13.sp,
          color = theme.secondary,
          modifier = Modifier
            .clickWithRipple { onToggleOrder() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
    }
    if (tip != null) {
      TextSecondary(
        tip,
        fontSize = 12.sp,
        maxLine = 3,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
    Column(modifier = Modifier.padding(top = 6.dp)) {
      content()
    }
  }
}

@Composable
private fun EmptyTip() {
  TextSecondary(
    stringResource(R.string.stats_no_ranking),
    fontSize = 13.sp,
    modifier = Modifier.padding(vertical = 4.dp)
  )
}

/** "名称  值" 的一行 */
@Composable
private fun StatItem(name: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextPrimary(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
    TextSecondary(value, fontSize = 13.sp)
  }
}

/** 带序号与值的统计行 */
@Composable
private fun NameCountRow(index: Int, name: String, value: String) {
  val theme = LocalTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextSecondary(
      index.toString(),
      fontSize = 12.sp,
      modifier = Modifier.width(26.dp)
    )
    TextPrimary(
      name,
      fontSize = 14.sp,
      maxLine = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    TextSecondary(value, fontSize = 13.sp)
  }
}

/** 歌曲排行卡片 */
@Composable
private fun SongRankingCard(
  title: String,
  tip: String?,
  list: List<SongPlayStat>,
  descending: Boolean,
  onToggleOrder: () -> Unit,
  valueText: @Composable (SongPlayStat) -> String,
) {
  StatsCard(title = title, tip = tip, descending = descending, onToggleOrder = onToggleOrder) {
    if (list.isEmpty()) {
      EmptyTip()
    } else {
      list.take(MAX_LIST_ROWS).forEachIndexed { index, song ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextSecondary((index + 1).toString(), fontSize = 12.sp, modifier = Modifier.width(26.dp))
          Column(modifier = Modifier.weight(1f)) {
            TextPrimary(song.title, fontSize = 14.sp, maxLine = 1, overflow = TextOverflow.Ellipsis)
            TextSecondary(song.artist, fontSize = 12.sp, maxLine = 1, overflow = TextOverflow.Ellipsis)
          }
          TextPrimary(
            valueText(song),
            fontSize = 13.sp,
            color = LocalTheme.current.secondary
          )
        }
      }
    }
  }
}

/** 每日播放时长条形图（取最近若干天，横向滚动） */
@Composable
private fun DailyDurationChart(days: List<DayPlayStat>, dayStartHour: Int) {
  val theme = LocalTheme.current
  val data = days.take(30).reversed()
  val max = data.maxOfOrNull { it.playedMs }?.coerceAtLeast(1L) ?: 1L

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(bottom = 6.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    data.forEach { day ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(18.dp)
      ) {
        Box(
          modifier = Modifier
            .width(12.dp)
            .height((4 + 56 * (day.playedMs.toFloat() / max)).dp)
            .clip(RoundedCornerShape(3.dp))
            .background(theme.secondary)
        )
        TextSecondary(
          StatsTimeUtil.formatDay(day.dayBucket, dayStartHour).takeLast(5),
          fontSize = 8.sp,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}

/**
 * 时段热力图：7(周一~周日) x 24 小时。
 * [showWeekday] 为 false 时只画一行（用于 24h 合并图，不显示星期标签）。
 */
@Composable
private fun HeatmapGrid(cells: List<List<Long>>, showWeekday: Boolean = true) {
  val theme = LocalTheme.current
  val max = cells.flatten().maxOrNull()?.coerceAtLeast(1L) ?: 1L

  Column(modifier = Modifier.fillMaxWidth()) {
    cells.forEachIndexed { dayIndex, row ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (showWeekday) {
          TextSecondary(
            weekdayLabel(dayIndex),
            fontSize = 10.sp,
            modifier = Modifier.width(24.dp)
          )
        } else {
          Box(modifier = Modifier.width(24.dp))
        }
        Row(modifier = Modifier.weight(1f)) {
          row.forEach { value ->
            val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
            Box(
              modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .padding(0.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                  if (value <= 0) {
                    theme.textSecondary.copy(alpha = 0.08f)
                  } else {
                    theme.secondary.copy(alpha = 0.2f + 0.8f * ratio)
                  }
                )
            )
          }
        }
      }
    }
    // 小时刻度（0/6/12/18/23）：按 24 列等分，使刻度与对应格子的左边界对齐
    val hours = cells.firstOrNull()?.size ?: 24
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(modifier = Modifier.width(24.dp))
      Row(modifier = Modifier.weight(1f)) {
        repeat(hours) { hour ->
          Box(modifier = Modifier.weight(1f)) {
            if (hour % 6 == 0 || hour == hours - 1) {
              TextSecondary(hour.toString(), fontSize = 8.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun weekdayLabel(index: Int): String {
  val res = when (index) {
    0 -> R.string.weekday_mon
    1 -> R.string.weekday_tue
    2 -> R.string.weekday_wed
    3 -> R.string.weekday_thu
    4 -> R.string.weekday_fri
    5 -> R.string.weekday_sat
    else -> R.string.weekday_sun
  }
  return stringResource(res)
}
