package remix.myplayer.ui.dialog

import androidx.compose.runtime.Stable
import remix.myplayer.data.model.audio.Song

/** 批量重命名弹窗状态（歌曲多选后触发）；默认模板取自设置的 defaultRenameTemplate */
@Stable
data class BatchRenameState(
  val dialogState: DialogState = DialogState(),
  val songs: List<Song> = emptyList()
)
