package remix.myplayer.ui.screen.setting.logic.tag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

/** 标签弹窗排序：智能排序（最后使用时间 + 使用次数）/ 固定位置（创建时间） */
@Composable
fun TagSmartSortLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.tag_smart_sort),
    stringResource(R.string.tag_smart_sort_tip),
    settingState.tag.smartSort
  ) {
    settingVM.setTagSmartSort(it)
  }
}
