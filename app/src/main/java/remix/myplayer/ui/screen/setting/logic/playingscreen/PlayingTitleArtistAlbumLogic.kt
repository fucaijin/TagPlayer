package remix.myplayer.ui.screen.setting.logic.playingscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.viewmodel.settingViewModel

@Composable
fun PlayingTitleArtistAlbumLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  SwitchPreference(
    stringResource(R.string.playing_title_show_artist_album),
    stringResource(R.string.playing_title_show_artist_album_tip),
    settingState.playingScreen.playingTitleShowArtistAlbum
  ) {
    settingVM.setPlayingTitleShowArtistAlbum(it)
  }
}
