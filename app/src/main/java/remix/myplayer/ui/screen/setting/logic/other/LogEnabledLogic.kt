package remix.myplayer.ui.screen.setting.logic.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

/**
 * 设置-其他-记录日志：开启后把运行日志写入文件，关闭则不再记录（用于排查问题）。
 */
@Composable
fun LogEnabledLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.log_enabled),
    stringResource(R.string.log_enabled_tip),
    settingState.other.logEnabled
  ) {
    settingVM.setLogEnabled(it)
  }
}
