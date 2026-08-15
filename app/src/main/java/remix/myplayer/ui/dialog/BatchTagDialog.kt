package remix.myplayer.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
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
  val allTags by libraryVM.allTags.collectAsStateWithLifecycle()
  val theme = LocalTheme.current

  var selected by remember { mutableStateOf(emptySet<String>()) }
  var newTagText by remember { mutableStateOf("") }

  LaunchedEffect(state.dialogState.isOpen) {
    if (state.dialogState.isOpen) {
      selected = emptySet()
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
      libraryVM.addTagsToSongs(state.songs, selected + newTagText.trim())
    },
    neutralRes = R.string.remove_from_tag,
    onNeutral = {
      libraryVM.removeTagsFromSongs(state.songs, selected + newTagText.trim())
    },
    custom = {
      TextSecondary(stringResource(R.string.select_tag_tip), fontSize = 14.sp)

      if (allTags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 8.dp)
        ) {
          allTags.forEach { tag ->
            val isSelected = tag in selected
            Surface(
              shape = RoundedCornerShape(50),
              color = if (isSelected) theme.secondary else theme.mainBackground,
              border = BorderStroke(
                width = 1.dp,
                color = if (isSelected) theme.secondary else theme.textSecondary.copy(alpha = 0.5f)
              ),
              onClick = {
                selected = if (isSelected) selected - tag else selected + tag
              }
            ) {
              Text(
                text = tag,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (isSelected) theme.primaryReverse else theme.textPrimary
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
}
