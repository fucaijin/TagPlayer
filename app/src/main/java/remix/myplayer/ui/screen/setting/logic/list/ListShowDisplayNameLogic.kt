package remix.myplayer.ui.screen.setting.logic.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.settingViewModel

@Composable
fun ListShowDisplayNameLogic() {
  val libraryVM = libraryViewModel
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.show_displayname),
    stringResource(R.string.show_displayname_tip),
    settingState.list.showDisplayName
  ) {
    settingVM.setShowDisplayName(it)
    libraryVM.fetchMedia()
  }
}
