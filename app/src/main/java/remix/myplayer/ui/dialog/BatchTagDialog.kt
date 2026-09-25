package remix.myplayer.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TagChip
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.PermissionUtil
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 批量标签弹窗状态（歌曲多选后触发）
 */
@Stable
data class BatchTagState(
  val dialogState: DialogState = DialogState(),
  val songs: List<Song> = emptyList()
)

/**
 * 批量标签弹窗：为多首歌曲批量添加/移除标签。
 * 支持勾选已有标签，也可直接输入新标签名（添加时会自动创建）。
 */
@Composable
fun BatchTagDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.batchTagState.collectAsStateWithLifecycle()
  // 按设置的排序方式（智能排序 / 按创建时间固定位置）排列后的标签
  val tags by libraryVM.orderedTags.collectAsStateWithLifecycle()
  val theme = LocalTheme.current

  var selected by remember { mutableStateOf(emptySet<String>()) }
  var newTagText by remember { mutableStateOf("") }
  // 批量写标签需要"所有文件访问"权限，未授权时先引导
  val storageDialogState = rememberDialogState()

  LaunchedEffect(state.dialogState.isOpen) {
    if (state.dialogState.isOpen) {
      // 预选当前选中歌曲已有的标签（并集），确保打开弹窗时能看到这些歌曲已带的标签，
      // 避免“无共同标签”时交集为空、看起来像没有回选。
      selected = if (state.songs.isEmpty()) {
        emptySet()
      } else {
        state.songs
          .map { libraryVM.songTags.value[it.data] ?: emptySet() }
          .fold(emptySet()) { acc, tags -> acc.union(tags) }
      }
      newTagText = ""
    }
  }

  NormalDialog(
    dialogState = state.dialogState,
    titleRes = R.string.tag_operation,
    negativeRes = R.string.cancel,
    onNegative = { libraryVM.dismissBatchTagDialog() },
    positiveRes = R.string.add_to_tag,
    onPositive = {
      if (PermissionUtil.canWriteAudioFiles()) {
        libraryVM.addTagsToSongs(state.songs, selected + newTagText.trim())
      } else {
        storageDialogState.show()
      }
    },
    neutralRes = R.string.remove_from_tag,
    onNeutral = {
      if (PermissionUtil.canWriteAudioFiles()) {
        libraryVM.removeTagsFromSongs(state.songs, selected + newTagText.trim())
      } else {
        storageDialogState.show()
      }
    },
    custom = {
      TextSecondary(stringResource(R.string.select_tag_tip), fontSize = 14.sp)

      if (tags.isNotEmpty()) {
        // 标签较多时可滚动，避免撑高弹窗把底部按钮顶出屏幕
        Column(
          modifier = Modifier
            .weight(1f, false)
            .verticalScroll(rememberScrollState())
        ) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp)
          ) {
            tags.forEach { tag ->
              TagChip(
                tag = tag,
                selected = tag in selected,
                fontSize = 14.sp,
                onClick = { selected = if (tag in selected) selected - tag else selected + tag }
              )
            }
          }
        }
      }

      // 新标签输入
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
      ) {
        BasicTextField(
          value = newTagText,
          onValueChange = { newTagText = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary)
        )
        if (newTagText.isEmpty()) {
          TextPrimary(
            stringResource(R.string.input_new_tag_tip),
            fontSize = 14.sp,
            color = theme.textSecondary,
            modifier = Modifier.align(Alignment.CenterStart)
          )
        }
      }
    }
  )

  ManageStorageDialog(
    dialogState = storageDialogState,
    onCancel = { storageDialogState.dismiss() }
  )
}
