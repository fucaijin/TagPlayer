package remix.myplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.repo.DayPlayStat
import remix.myplayer.repo.ListeningTrendPoint
import remix.myplayer.repo.SongPlayStat
import remix.myplayer.repo.StatsTimeUtil
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.viewmodel.ActiveSortBy
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.viewmodel.StatsRangePreset
import remix.myplayer.viewmodel.DataAnalysisViewModel
import remix.myplayer.viewmodel.dataAnalysisViewModel
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.viewmodel.settingViewModel
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 数据分析报表：按日期区间统计播放次数/时长/跳过/标签/搜索/时段分布与应用使用习惯。
 * 内嵌在 设置-数据分析 页面（"一天分界点"下方）显示。
 *
 * 数据从本功能上线后开始采集，历史数据无法追溯。
 */
/**
 * 数据分析独立页面（从左侧抽屉进入）。内嵌 [DataAnalysisReport]，并带返回顶栏。
 */
@Composable
fun DataAnalysisScreen() {
  Scaffold(
    topBar = {
      CommonAppBar(
        title = stringResource(R.string.data_analysis),
        actions = { AnalysisSettingsButton() }
      )
    },
    containerColor = LocalTheme.current.mainBackground
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      DataAnalysisReport()
    }
  }
}

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

  Column(modifier = modifier.fillMaxSize()) {
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

    // 报表内容区可滚动，统计区间选择栏固定在顶部不随之滚动
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp)
    ) {
    val enabledModules = state.enabledModules
    // 各列表模块各自的行数设置：模块 key -> 行数
    fun rows(mod: AnalysisModule) = state.rowsOf(mod.key)

    if (!state.hasPlayData) {
      TextSecondary(
        stringResource(R.string.stats_no_data),
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
    }

    // 11.11 使用习惯：平均多久打开一次、平均每次听多久
    if (enabledModules.contains(AnalysisModule.APP_USAGE.key)) {
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
    }

    // 11.2 播放次数排行 / 11.3 播放时长排行
    if (enabledModules.contains(AnalysisModule.PLAY_COUNT.key)) {
      SongRankingCard(
        title = stringResource(R.string.stats_play_count),
        tip = stringResource(R.string.stats_play_count_tip),
        list = state.playCountList,
        descending = state.playCountDesc,
        onToggleOrder = vm::togglePlayCountOrder,
        maxRows = rows(AnalysisModule.PLAY_COUNT)
      ) { stringResource(R.string.stats_times, it.playCount) }
    }

    if (enabledModules.contains(AnalysisModule.PLAY_DURATION.key)) {
      SongRankingCard(
        title = stringResource(R.string.stats_play_duration),
        tip = null,
        list = state.durationList,
        descending = state.durationDesc,
        onToggleOrder = vm::toggleDurationOrder,
        maxRows = rows(AnalysisModule.PLAY_DURATION)
      ) { StatsTimeUtil.formatDuration(it.totalPlayedMs) }
    }

    // 11.4 / 11.10 跳过排行（未播到 80% 就切歌）
    if (enabledModules.contains(AnalysisModule.SKIPPED.key)) {
      SongRankingCard(
        title = stringResource(R.string.stats_skipped),
        tip = stringResource(R.string.stats_skipped_tip),
        list = state.skippedList,
        descending = state.skippedDesc,
        onToggleOrder = vm::toggleSkippedOrder,
        maxRows = rows(AnalysisModule.SKIPPED),
        trailing = {
          CardTitleAction(
            stringResource(if (state.skippedByRate) R.string.sort_rate else R.string.sort_count),
            onClick = vm::toggleSkippedRate
          )
        }
      ) { song ->
        stringResource(R.string.stats_times, song.skippedCount) +
            "  " + stringResource(
          R.string.stats_skip_rate,
          (song.skipRate * 100).roundToInt()
        )
      }
    }

    // 11.6 每日播放时长
    if (enabledModules.contains(AnalysisModule.DAILY_DURATION.key)) {
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
          ranked.take(rows(AnalysisModule.DAILY_DURATION)).forEach { day ->
            StatItem(
              StatsTimeUtil.formatDay(day.dayBucket, state.dayStartHour),
              StatsTimeUtil.formatDuration(day.playedMs)
            )
          }
        }
      }
    }

    // 听歌时长趋势折线图
    if (enabledModules.contains(AnalysisModule.TREND.key)) {
      StatsCard(
        title = stringResource(R.string.stats_trend),
        tip = stringResource(R.string.stats_trend_tip)
      ) {
        if (state.trend.isEmpty()) {
          EmptyTip()
        } else {
          ListeningTrendChart(state.trend)
        }
      }
    }

    // 11.5 每天最早/最晚听歌时间
    if (enabledModules.contains(AnalysisModule.DAILY_ACTIVE.key)) {
      StatsCard(
        title = stringResource(R.string.stats_daily_active),
        tip = null,
        descending = state.activeDesc,
        onToggleOrder = vm::toggleActiveOrder,
        trailing = {
          CardTitleAction(
            stringResource(
              if (state.activeSortBy == ActiveSortBy.EARLIEST) {
                R.string.sort_earliest
              } else {
                R.string.sort_latest
              }
            ),
            onClick = vm::toggleActiveSortBy
          )
        }
      ) {
        if (state.activeList.isEmpty()) {
          EmptyTip()
        } else {
          val ranked = when (state.activeSortBy) {
            ActiveSortBy.EARLIEST -> state.activeList.sortedBy { it.earliest }
            ActiveSortBy.LATEST -> state.activeList.sortedBy { it.latest }
          }.let { if (state.activeDesc) it.asReversed() else it }
          ranked.take(rows(AnalysisModule.DAILY_ACTIVE)).forEach { day ->
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
    }

    // 11.8 标签歌曲数量
    if (enabledModules.contains(AnalysisModule.TAG_SONG_COUNT.key)) {
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
          list.take(rows(AnalysisModule.TAG_SONG_COUNT)).forEachIndexed { index, item ->
            NameCountRow(index + 1, item.name, stringResource(R.string.song_count_1, item.count))
          }
        }
      }
    }

    // 11.9 最常播放的标签
    if (enabledModules.contains(AnalysisModule.FAVORITE_TAGS.key)) {
      StatsCard(
        title = stringResource(R.string.stats_favorite_tags),
        tip = null,
        descending = state.tagPlayDesc,
        onToggleOrder = vm::toggleTagPlayOrder
      ) {
        if (state.tagPlays.isEmpty()) {
          EmptyTip()
        } else {
          state.tagPlays.take(rows(AnalysisModule.FAVORITE_TAGS)).forEachIndexed { index, item ->
            NameCountRow(
              index + 1,
              item.tag,
              stringResource(R.string.stats_times, item.playCount)
            )
          }
        }
      }
    }

    // 11.7 搜索关键词排行
    if (enabledModules.contains(AnalysisModule.SEARCH_RANKING.key)) {
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
          list.take(rows(AnalysisModule.SEARCH_RANKING)).forEachIndexed { index, (keyword, count) ->
            NameCountRow(index + 1, keyword, stringResource(R.string.stats_times, count))
          }
        }
      }
    }

    // 11.12 7×24h 时段热力图
    if (enabledModules.contains(AnalysisModule.HEATMAP.key)) {
      StatsCard(
        title = stringResource(R.string.stats_heatmap),
        tip = stringResource(R.string.stats_heatmap_tip)
      ) {
        if (state.heatmap.isEmpty() || state.heatmap.all { row -> row.all { it <= 0 } }) {
          EmptyTip()
        } else {
          HeatmapGrid(state.heatmap, timeLabels = state.heatmapTimeLabels)
        }
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
    if (enabledModules.contains(AnalysisModule.HEATMAP_24H.key)) {
      StatsCard(
        title = stringResource(R.string.stats_heatmap_24h),
        tip = stringResource(R.string.stats_heatmap_24h_tip)
      ) {
        if (heatmap24.isEmpty() || heatmap24.all { it <= 0 }) {
          EmptyTip()
        } else {
          HeatmapGrid(listOf(heatmap24), showWeekday = false, timeLabels = state.heatmapTimeLabels)
        }
      }
    }
    }
  }
}

@Composable
private fun AnalysisSettingsButton(vm: DataAnalysisViewModel = dataAnalysisViewModel) {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val state by vm.state.collectAsStateWithLifecycle()
  val theme = LocalTheme.current
  val dialogState = rememberDialogState()
  val clearState = rememberDialogState()
  // 当前展开设置的模块（同时最多展开一个）
  var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

  IconButton(onClick = { dialogState.show() }) {
    Icon(
      Icons.Filled.Settings,
      contentDescription = stringResource(R.string.stats_modules)
    )
  }

  NormalDialog(
    dialogState = dialogState,
    custom = {
      Column(
        modifier = Modifier
          .weight(1f, false)
          .verticalScroll(rememberScrollState())
      ) {
        TextPrimary(
          stringResource(R.string.analysis_settings),
          fontSize = 16.sp,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        // 各分析模块：开关显示 + 该模块自己的设置
        AnalysisModule.entries.forEach { mod ->
          ModuleSettingSection(
            title = stringResource(mod.labelRes),
            enabled = state.enabledModules.contains(mod.key),
            expanded = expandedKey == mod.key,
            onEnabledChange = { show ->
              val newSet =
                if (show) state.enabledModules + mod.key else state.enabledModules - mod.key
              vm.setEnabledModules(newSet)
            },
            onExpandChange = { expandedKey = if (expandedKey == mod.key) null else mod.key }
          ) {
            // 列表类模块：自己的显示行数
            if (mod.hasList) {
              val rows = state.rowsOf(mod.key)
              RowsInputRow(
                label = stringResource(R.string.stats_module_rows),
                value = rows,
                onValueChange = { vm.setModuleRows(mod.key, it) }
              )
            }
            // 热力图模块：横坐标时间个数
            if (mod.key == AnalysisModule.HEATMAP.key || mod.key == AnalysisModule.HEATMAP_24H.key) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                TextSecondary(
                  stringResource(R.string.heatmap_time_labels),
                  fontSize = 13.sp,
                  modifier = Modifier.weight(1f)
                )
                listOf(24, 12, 8, 6).forEach { count ->
                  val selected = state.heatmapTimeLabels == count
                  TextPrimary(
                    count.toString(),
                    fontSize = 13.sp,
                    color = if (selected) theme.primary else theme.textSecondary,
                    modifier = Modifier
                      .clickWithRipple { vm.setHeatmapTimeLabels(count) }
                      .padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }
        }

        // 通用设置（作用于所有模块）
        TextSecondary(
          stringResource(R.string.stats_common_settings),
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        // 一天分界点（0~23 点循环的滚轮）
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextSecondary(
            stringResource(R.string.stats_day_start),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
          )
          HourWheelPicker(
            hour = settingState.analysis.dayStartHour,
            onHourChange = { settingVM.setStatsDayStartHour(it) }
          )
        }

        // 清除统计数据
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextPrimary(
            stringResource(R.string.stats_clear),
            fontSize = 14.sp,
            color = theme.primary,
            modifier = Modifier
              .clickWithRipple { clearState.show() }
              .padding(8.dp)
          )
        }
      }
    },
    positiveRes = R.string.close
  )

  NormalDialog(
    dialogState = clearState,
    titleRes = R.string.stats_clear,
    contentRes = R.string.stats_clear_confirm,
    onPositive = { vm.clearAllStats() }
  )
}

/**
 * 单个模块的设置分组：标题栏（点击展开该模块自己的设置）+ 是否显示该模块的开关。
 */
@Composable
private fun ModuleSettingSection(
  title: String,
  enabled: Boolean,
  expanded: Boolean,
  onEnabledChange: (Boolean) -> Unit,
  onExpandChange: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  val theme = LocalTheme.current
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickWithRipple { onExpandChange() }
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        Icons.Filled.ArrowDropDown,
        contentDescription = null,
        tint = theme.textSecondary,
        modifier = Modifier
          .size(20.dp)
          .rotate(if (expanded) 180f else 0f)
      )
      TextPrimary(
        title,
        modifier = Modifier
          .weight(1f)
          .padding(start = 4.dp),
        fontSize = 14.sp,
        color = if (enabled) theme.textPrimary else theme.textSecondary
      )
      Box(
        modifier = Modifier.size(39.dp, 24.dp),
        contentAlignment = Alignment.Center
      ) {
        Switch(checked = enabled, onCheckedChange = onEnabledChange, modifier = Modifier.scale(0.75f))
      }
    }
    AnimatedVisibility(visible = expanded) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, bottom = 4.dp),
        content = content
      )
    }
  }
}

/** 列表显示行数输入框：仅允许数字，取值 0~100（含） */
@Composable
private fun RowsInputRow(
  label: String,
  value: Int,
  onValueChange: (Int) -> Unit
) {
  val theme = LocalTheme.current
  var text by rememberSaveable(value) { mutableStateOf(value.toString()) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextSecondary(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
    BasicTextField(
      value = text,
      onValueChange = { input ->
        val filtered = input.filter { it.isDigit() }.take(3)
        val n = filtered.toIntOrNull()
        // 仅接受 0~100 的合法输入；空字符串暂不提交（占位提示为 0）
        if (filtered.isEmpty() || (n != null && n <= 100)) {
          text = n?.toString() ?: ""
          if (n != null) onValueChange(n)
        }
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(
        onDone = {
          if (text.isEmpty()) {
            text = "0"
            onValueChange(0)
          }
        }
      ),
      textStyle = TextStyle(color = theme.textPrimary, fontSize = 14.sp, textAlign = TextAlign.Center),
      cursorBrush = SolidColor(theme.primary),
      modifier = Modifier.width(72.dp),
      decorationBox = { innerTextField ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(theme.dialogBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          if (text.isEmpty()) {
            TextSecondary("0", fontSize = 14.sp)
          }
          innerTextField()
        }
      }
    )
  }
}

/** 0~23 点循环的上下滚轮（滚动停止后自动吸附到中间项） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourWheelPicker(
  hour: Int,
  onHourChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val theme = LocalTheme.current
  val itemHeight = 28.dp
  val visibleCount = 3
  val centerOffset = visibleCount / 2
  // 以中间位置作为起点，形成首尾相接的循环列表
  val startIndex = remember {
    val middle = Int.MAX_VALUE / 2
    middle - middle % HOURS_PER_DAY + hour - centerOffset
  }
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
  val scope = rememberCoroutineScope()

  // 滚动停止后取最靠近中间的项作为选中值，并居中对齐
  val scrolling = listState.isScrollInProgress
  LaunchedEffect(scrolling, hour) {
    if (scrolling) {
      return@LaunchedEffect
    }
    val info = listState.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val nearest = info.visibleItemsInfo.minByOrNull { item ->
      ((item.offset + item.size / 2) - center).absoluteValue
    } ?: return@LaunchedEffect
    val newHour = Math.floorMod(nearest.index, HOURS_PER_DAY)
    if (newHour != hour) {
      onHourChange(newHour)
    }
    if (nearest.index != listState.firstVisibleItemIndex + centerOffset) {
      scope.launch { listState.animateScrollToItem(nearest.index - centerOffset) }
    }
  }

  Box(
    modifier = modifier.height(itemHeight * visibleCount),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .width(WHEEL_WIDTH)
        .height(itemHeight)
        .background(theme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
    )
    LazyColumn(
      state = listState,
      modifier = Modifier
        .width(WHEEL_WIDTH)
        .height(itemHeight * visibleCount),
      horizontalAlignment = Alignment.CenterHorizontally,
      flingBehavior = rememberSnapFlingBehavior(listState)
    ) {
      items(Int.MAX_VALUE) { index ->
        val value = Math.floorMod(index, HOURS_PER_DAY)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickWithRipple {
              onHourChange(value)
              scope.launch {
                // 滚动到最近的同一个值，避免跨过多圈
                val current = listState.firstVisibleItemIndex + centerOffset
                var delta = Math.floorMod(value - Math.floorMod(current, HOURS_PER_DAY), HOURS_PER_DAY)
                if (delta > HOURS_PER_DAY / 2) {
                  delta -= HOURS_PER_DAY
                }
                listState.animateScrollToItem(current + delta - centerOffset)
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Text(
            stringResource(R.string.stats_day_start_value, value),
            fontSize = 16.sp,
            color = if (value == hour) theme.primary else theme.textSecondary,
            fontWeight = if (value == hour) FontWeight.Bold else FontWeight.Normal
          )
        }
      }
    }
  }
}

private const val HOURS_PER_DAY = 24

/** 小时滚轮的宽度 */
private val WHEEL_WIDTH = 100.dp

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
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 两个日期框撑开剩余宽度，"分析"按钮固定在最右侧
      DateInput(value = fromText, onValueChange = onFromChange, modifier = Modifier.weight(1f))
      TextSecondary("~", fontSize = 13.sp)
      DateInput(value = toText, onValueChange = onToChange, modifier = Modifier.weight(1f))
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
  trailing: @Composable (() -> Unit)? = null,
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
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TextPrimary(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
      trailing?.invoke()
      if (descending != null && onToggleOrder != null) {
        CardTitleAction(
          stringResource(
            if (descending) R.string.stats_order_desc else R.string.stats_order_asc
          ),
          onClick = onToggleOrder
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

/**
 * 标题栏右侧的可点击文字（排序方式等）。
 * 末尾不留内边距，使其右端与下方数据行的右端对齐；左侧留出间距与点击热区。
 */
@Composable
private fun CardTitleAction(
  text: String,
  onClick: () -> Unit,
) {
  TextPrimary(
    text,
    fontSize = 13.sp,
    color = LocalTheme.current.secondary,
    modifier = Modifier
      .clickWithRipple { onClick() }
      .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
  )
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
  maxRows: Int = MAX_LIST_ROWS,
  trailing: @Composable (() -> Unit)? = null,
  valueText: @Composable (SongPlayStat) -> String,
) {
  StatsCard(
    title = title,
    tip = tip,
    descending = descending,
    onToggleOrder = onToggleOrder,
    trailing = trailing
  ) {
    if (list.isEmpty()) {
      EmptyTip()
    } else {
      list.take(maxRows).forEachIndexed { index, song ->
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

/** 每日播放时长条形图（取最近若干天，横向滚动）。点击高亮并显示完整日期与数值。 */
@Composable
private fun DailyDurationChart(days: List<DayPlayStat>, dayStartHour: Int) {
  val theme = LocalTheme.current
  val data = days.take(30).reversed()
  val max = data.maxOfOrNull { it.playedMs }?.coerceAtLeast(1L) ?: 1L
  var selectedIndex by remember { mutableStateOf<Int?>(null) }
  // 横坐标过于密集，隔一个条形显示一个 MMdd 标签；其余点击后在下文显示完整时间

  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(bottom = 6.dp),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      data.forEachIndexed { index, day ->
        val selected = selectedIndex == index
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.width(18.dp)
        ) {
          Box(
            modifier = Modifier
              .width(12.dp)
              .height((4 + 56 * (day.playedMs.toFloat() / max)).dp)
              .clip(RoundedCornerShape(3.dp))
              .background(if (selected) theme.primary else theme.secondary)
              .clickWithRipple {
                selectedIndex = if (selectedIndex == index) null else index
              }
          )
          val showLabel = index % 2 == 0
          TextSecondary(
            if (showLabel) {
              StatsTimeUtil.formatDayMmdd(day.dayBucket, dayStartHour)
            } else {
              ""
            },
            fontSize = 8.sp,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
      }
    }

    // 选中后展示完整的横坐标时间与具体数值
    selectedIndex?.let { i ->
      val day = data[i]
      TextSecondary(
        StatsTimeUtil.formatDay(day.dayBucket, dayStartHour) + "   " +
          StatsTimeUtil.formatDuration(day.playedMs),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
      )
    }
  }
}

/** 听歌时长趋势折线图：横轴为所选区间（长区间按周/月归集），纵轴为日均听歌分钟数。
 *  支持长按吸附到最近的数据点并显示具体数值与横坐标时间。 */
@Composable
private fun ListeningTrendChart(points: List<ListeningTrendPoint>) {
  val theme = LocalTheme.current
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(color = theme.textSecondary, fontSize = 9.sp)
  val gridColor = theme.textSecondary.copy(alpha = 0.15f)
  val lineColor = theme.secondary
  val unit = stringResource(R.string.stats_trend_unit)
  var activeIndex by remember { mutableStateOf<Int?>(null) }
  val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
  // 气泡配色需在 Compose 作用域内取值，绘制回调内不可调用 Composable
  val bubbleBgColor = theme.dialogBackground
  val bubbleTextColor = theme.textPrimary

  // 纵轴上限取整：小值向上取整到 1 分钟，大值取整到 10 分钟
  val rawMax = points.maxOf { it.minutesPerDay }
  val maxY = when {
    rawMax <= 10f -> ceil(rawMax).coerceAtLeast(1f)
    else -> ceil(rawMax / 10f) * 10f
  }

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(150.dp)
      .pointerInput(points) {
        awaitPointerEventScope {
          while (true) {
            // 等待按下
            var pressed: PointerInputChange? = null
            while (pressed == null) {
              val event = awaitPointerEvent()
              pressed = event.changes.firstOrNull { it.changedToDown() }
            }
            val down = pressed ?: continue

            // 长按超时前等待抬起/取消；超时即为长按
            val releasedEarly: Boolean? = withTimeoutOrNull(longPressTimeoutMs) {
              while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.changedToUp() }) return@withTimeoutOrNull true
              }
              false
            }
            if (releasedEarly == null) {
              // 长按：吸附最近的数据点，手指移动时跟随，抬起后清除
              activeIndex = nearestIndex(down.position.x, size, points.size)
              while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.changedToUp() }) break
                activeIndex = nearestIndex(event.changes.first().position.x, size, points.size)
              }
              activeIndex = null
            }
          }
        }
      }
  ) {
    val leftPad = 30.dp.toPx()
    val topPad = 10.dp.toPx()
    val bottomPad = 16.dp.toPx()
    val plotWidth = (size.width - leftPad).coerceAtLeast(1f)
    val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

    fun xOf(index: Int): Float = if (points.size == 1) {
      leftPad + plotWidth / 2f
    } else {
      leftPad + plotWidth * index / (points.size - 1).toFloat()
    }

    fun yOf(minutes: Float): Float = topPad + plotHeight * (1f - minutes / maxY)

    // 横向网格线（0 / 中值 / 上限）
    listOf(0f, 0.5f, 1f).forEach { ratio ->
      val y = topPad + plotHeight * ratio
      drawLine(
        color = gridColor,
        start = Offset(leftPad, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx()
      )
    }

    // 纵轴刻度（上限与 0）
    listOf(maxY, 0f).forEach { value ->
      val layout = textMeasurer.measure(value.roundToInt().toString(), labelStyle)
      drawText(
        textLayoutResult = layout,
        topLeft = Offset(
          x = (leftPad - 4.dp.toPx() - layout.size.width).coerceAtLeast(0f),
          y = yOf(value) - layout.size.height / 2f
        )
      )
    }

    // 折线
    val path = Path()
    points.forEachIndexed { index, point ->
      val x = xOf(index)
      val y = yOf(point.minutesPerDay)
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = lineColor, style = Stroke(width = 2.dp.toPx()))

    // 数据点
    points.forEachIndexed { index, point ->
      drawCircle(
        color = lineColor,
        radius = 2.5.dp.toPx(),
        center = Offset(xOf(index), yOf(point.minutesPerDay))
      )
    }

    // 横轴标签（最多 4 个）
    trendLabelIndexes(points.size).forEach { index ->
      val layout = textMeasurer.measure(points[index].label, labelStyle)
      val centerX = xOf(index)
      drawText(
        textLayoutResult = layout,
        topLeft = Offset(
          x = (centerX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
          y = size.height - bottomPad + 2.dp.toPx()
        )
      )
    }

    // 长按吸附：高亮最近数据点并显示数值与横坐标时间气泡
    val idx = activeIndex
    if (idx != null && idx in points.indices) {
      val p = points[idx]
      val cx = xOf(idx)
      val cy = yOf(p.minutesPerDay)
      drawLine(gridColor.copy(alpha = 0.5f), Offset(leftPad, cy), Offset(size.width, cy), strokeWidth = 1.dp.toPx())
      drawLine(gridColor.copy(alpha = 0.5f), Offset(cx, topPad), Offset(cx, size.height - bottomPad), strokeWidth = 1.dp.toPx())
      drawCircle(color = theme.primary, radius = 4.dp.toPx(), center = Offset(cx, cy))

      val label = p.label
      val valueText = "${p.minutesPerDay.roundToInt()} $unit"
      val tl = textMeasurer.measure(label, labelStyle)
      val vt = textMeasurer.measure(valueText, labelStyle)
      val pad = 4.dp.toPx()
      val bubbleW = (maxOf(tl.size.width, vt.size.width) + pad * 2)
      val bubbleH = (tl.size.height + vt.size.height + pad * 2).toFloat()
      val bx = (cx - bubbleW / 2).coerceIn(0f, size.width - bubbleW)
      val by = (cy - bubbleH - 8.dp.toPx()).coerceAtLeast(topPad)
      drawRoundRect(
        color = bubbleBgColor,
        topLeft = Offset(bx, by),
        size = Size(bubbleW, bubbleH),
        cornerRadius = CornerRadius(4.dp.toPx()),
      )
      drawText(tl, topLeft = Offset(bx + pad, by + pad), color = bubbleTextColor)
      drawText(vt, topLeft = Offset(bx + pad, by + pad + tl.size.height), color = bubbleTextColor)
    }
  }
}

/** 根据横坐标像素位置，吸附到最近的数据点索引。 */
private fun Density.nearestIndex(x: Float, size: IntSize, count: Int): Int? {
  if (count == 0) return null
  val leftPad = 30.dp.toPx()
  val plotWidth = (size.width - leftPad).coerceAtLeast(1f)
  if (x <= leftPad) return 0
  if (x >= size.width) return count - 1
  val ratio = (x - leftPad) / plotWidth
  return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** 横轴最多显示 4 个标签：首尾 + 均分的两个中间点 */
private fun trendLabelIndexes(size: Int): List<Int> = if (size <= 4) {
  (0 until size).toList()
} else {
  listOf(0, size / 3, size * 2 / 3, size - 1).distinct()
}

/**
 * 时段热力图：7(周一~周日) x 24 小时，每行固定 24 个格子。
 * [showWeekday] 为 false 时只画一行（用于 24h 合并图，不显示星期标签）。
 * [timeLabels] 只控制横坐标显示的时间个数（24/12/8/6），不影响格子数量。
 * 渲染为单色模式：仅以主题次要色的透明度深浅表示数值强弱。
 */
@Composable
private fun HeatmapGrid(
  cells: List<List<Long>>,
  showWeekday: Boolean = true,
  timeLabels: Int = 24
) {
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
    // 小时刻度：格子固定 24 个，按 [timeLabels] 等分显示时间标签
    val labelCount = if (timeLabels in 1..24 && 24 % timeLabels == 0) timeLabels else 24
    val step = 24 / labelCount
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(modifier = Modifier.width(24.dp))
      Row(modifier = Modifier.weight(1f)) {
        repeat(24) { hour ->
          Box(modifier = Modifier.weight(1f)) {
            if (hour % step == 0) {
              TextSecondary(
                hour.toString(),
                fontSize = if (labelCount >= 24) 7.sp else 8.sp
              )
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
