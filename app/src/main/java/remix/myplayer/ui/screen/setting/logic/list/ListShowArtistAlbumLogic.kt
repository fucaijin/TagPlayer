package remix.myplayer.ui.screen.setting.logic.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

@Composable
fun ListShowArtistAlbumLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.list_show_artist_album),
    stringResource(R.string.list_show_artist_album_tip),
    settingState.list.showArtistAlbum
  ) {
    settingVM.setListShowArtistAlbum(it)
  }
}
