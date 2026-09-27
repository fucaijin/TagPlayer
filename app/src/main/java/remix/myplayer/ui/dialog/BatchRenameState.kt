package remix.myplayer.ui.dialog

import androidx.compose.runtime.Stable
import remix.myplayer.data.model.audio.Song

/** 批量修改页面状态（歌曲多选后触发）；默认模板使用固定常量 "{artist} - {title}" */
@Stable
data class BatchRenameState(
  val songs: List<Song> = emptyList()
)
