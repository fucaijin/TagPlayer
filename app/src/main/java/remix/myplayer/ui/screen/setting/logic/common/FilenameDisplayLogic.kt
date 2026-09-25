package remix.myplayer.ui.screen.setting.logic.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

@Composable
fun FilenameDisplayLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.bottom_bar_use_filename),
    stringResource(R.string.bottom_bar_use_filename_tip),
    settingState.common.useFilenameInBottomBar
  ) {
    settingVM.setBottomBarUseFilename(it)
  }

  SwitchPreference(
    stringResource(R.string.playing_title_use_filename),
    stringResource(R.string.playing_title_use_filename_tip),
    settingState.common.useFilenameInPlayingTitle
  ) {
    settingVM.setPlayingTitleUseFilename(it)
  }
}
