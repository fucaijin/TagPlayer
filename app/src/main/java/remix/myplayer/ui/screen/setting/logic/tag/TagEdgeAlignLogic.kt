package remix.myplayer.ui.screen.setting.logic.tag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

/** 标签展示是否两端对齐（每行首尾贴父容器左右边，与排序模式无关） */
@Composable
fun TagEdgeAlignLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.tag_edge_align),
    stringResource(R.string.tag_edge_align_tip),
    settingState.tag.edgeAlign
  ) {
    settingVM.setTagEdgeAlign(it)
  }
}
