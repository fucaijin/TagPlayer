package remix.myplayer.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.PermissionUtil
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 单曲标签管理弹窗状态
 */
@Stable
data class SongTagManageState(
  val dialogState: DialogState = DialogState(),
  val song: Song = Song.EMPTY_SONG
)

/**
 * 单曲标签管理弹窗：显示所有标签，高亮该歌曲已有标签，
 * 点击标签切换选中状态，点击"保存"才一次性写入音频文件，点击"取消"丢弃本次修改。
 */
@Composable
fun SongTagManageDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.songTagManageState.collectAsStateWithLifecycle()
  val allTags by libraryVM.allTags.collectAsStateWithLifecycle()
  val song = state.song
  // 写标签需要"所有文件访问"权限，未授权时先引导
  val storageDialogState = rememberDialogState()

  var selected by remember(song) { mutableStateOf(emptySet<String>()) }
  LaunchedEffect(state.dialogState.isOpen) {
    if (state.dialogState.isOpen) {
      selected = libraryVM.songTags.value[song.data] ?: emptySet()
    }
  }

  NormalDialog(
    dialogState = state.dialogState,
    titleRes = R.string.song_tag_manage,
    negativeRes = R.string.cancel,
    positiveRes = R.string.save,
    onNegative = { libraryVM.dismissSongTagManageDialog() },
    onPositive = {
      if (song.valid()) {
        if (PermissionUtil.canWriteAudioFiles()) {
          libraryVM.saveSongTags(song, selected)
        } else {
          storageDialogState.show()
        }
      } else {
        libraryVM.dismissSongTagManageDialog()
      }
    },
    custom = {
      if (allTags.isEmpty()) {
        TextSecondary(stringResource(R.string.no_tag))
      } else {
        // 标签较多时可滚动，避免超出弹窗显示范围
        Column(
          modifier = Modifier
            .weight(1f, false)
            .verticalScroll(rememberScrollState())
        ) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
          ) {
            allTags.forEach { tag ->
              val isSelected = tag in selected
              Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) LocalTheme.current.secondary else LocalTheme.current.mainBackground,
                border = BorderStroke(
                  width = 1.dp,
                  color = if (isSelected) {
                    LocalTheme.current.secondary
                  } else {
                    LocalTheme.current.textSecondary.copy(alpha = 0.5f)
                  }
                ),
                onClick = {
                  selected = if (isSelected) selected - tag else selected + tag
                }
              ) {
                Text(
                  text = tag,
                  fontSize = 14.sp,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  color = if (isSelected) {
                    LocalTheme.current.primaryReverse
                  } else {
                    LocalTheme.current.textPrimary
                  }
                )
              }
            }
          }
        }
      }
    }
  )

  ManageStorageDialog(
    dialogState = storageDialogState,
    onCancel = { storageDialogState.dismiss() }
  )
}
