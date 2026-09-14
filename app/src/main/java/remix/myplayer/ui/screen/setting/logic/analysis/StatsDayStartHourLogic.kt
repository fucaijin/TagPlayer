package remix.myplayer.ui.screen.setting.logic.analysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.settingViewModel

/**
 * 设置-数据分析-一天分界点：几点之前算作前一天（默认 5 点），影响"每日"相关统计。
 */
@Composable
fun StatsDayStartHourLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val state = rememberDialogState()

  NormalPreference(
    title = stringResource(R.string.stats_day_start),
    content = stringResource(R.string.stats_day_start_tip) + "  ·  " +
        stringResource(R.string.stats_day_start_value, settingState.analysis.dayStartHour)
  ) {
    state.show()
  }

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
}
