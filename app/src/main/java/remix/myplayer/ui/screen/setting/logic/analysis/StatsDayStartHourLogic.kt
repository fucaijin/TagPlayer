package remix.myplayer.ui.screen.setting.logic.analysis

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.Preference
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.viewmodel.dataAnalysisViewModel
import remix.myplayer.viewmodel.settingViewModel

/**
 * 设置-数据分析-一天分界点：几点之前算作前一天（默认 5 点），影响"每日"相关统计。
 * 行尾按钮可清除全部历史统计数据。
 */
@Composable
fun StatsDayStartHourLogic() {
  val settingVM = settingViewModel
  val analysisVM = dataAnalysisViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val state = rememberDialogState()
  val clearState = rememberDialogState()

  Preference(
    title = stringResource(R.string.stats_day_start),
    content = stringResource(R.string.stats_day_start_tip) + "  ·  " +
        stringResource(R.string.stats_day_start_value, settingState.analysis.dayStartHour),
    onClick = { state.show() },
    trailing = {
      val theme = LocalTheme.current
      Surface(
        shape = RoundedCornerShape(50),
        color = theme.secondary,
        onClick = { clearState.show() }
      ) {
        TextPrimary(
          stringResource(R.string.stats_clear),
          fontSize = 13.sp,
          color = theme.primaryReverse,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
      }
    }
  )

  NormalDialog(
    dialogState = state,
    title = stringResource(R.string.stats_day_start),
    items = (0..23).map { stringResource(R.string.stats_day_start_value, it) },
    positive = null,
    negative = null,
    itemsCallback = { index, _ ->
      settingVM.setStatsDayStartHour(index)
    }
  )

  NormalDialog(
    dialogState = clearState,
    titleRes = R.string.stats_clear,
    contentRes = R.string.stats_clear_confirm,
    onPositive = { analysisVM.clearAllStats() }
  )
}
