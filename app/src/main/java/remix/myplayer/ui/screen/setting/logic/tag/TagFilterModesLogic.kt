package remix.myplayer.ui.screen.setting.logic.tag

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.misc.TagFilterMode
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.viewmodel.settingViewModel

/** 歌曲列表标签过滤区下拉列表中显示哪些过滤模式（至少保留 1 项） */
@Composable
fun TagFilterModesLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val visible = settingState.tag.filterModes

  Text(
    text = stringResource(R.string.tag_filter_show_modes),
    color = LocalTheme.current.textSecondary,
    fontSize = 13.sp,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
  )
  Text(
    text = stringResource(R.string.tag_filter_show_modes_tip),
    color = LocalTheme.current.textSecondary,
    fontSize = 12.sp,
    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
  )

  TagFilterMode.entries.forEach { mode ->
    SwitchPreference(
      stringResource(mode.labelRes),
      null,
      mode.name in visible
    ) { checked ->
      settingVM.setTagFilterModeVisible(mode, checked)
    }
  }
}
