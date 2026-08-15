package remix.myplayer.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.PermissionUtil
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 标签管理弹窗状态（过滤区齿轮按钮触发）
 */
@Stable
data class TagManageState(
  val dialogState: DialogState = DialogState()
)

/**
 * 标签管理弹窗：创建、重命名、删除标签。
 * 标签只存在于歌曲的音频文件中，重命名/删除会批量更新所有含该标签的歌曲。
 */
@Composable
fun TagManageDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.tagManageState.collectAsStateWithLifecycle()
  val allTags by libraryVM.allTags.collectAsStateWithLifecycle()
  val theme = LocalTheme.current

  // 新建/重命名输入
  val inputDialogState = rememberDialogState()
  var creating by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<String?>(null) }
  var inputText by remember { mutableStateOf("") }
  // 删除确认
  val deleteDialogState = rememberDialogState()
  var deleteTarget by remember { mutableStateOf<String?>(null) }
  // 重命名/删除标签会批量写歌曲文件，需要"所有文件访问"权限
  val storageDialogState = rememberDialogState()

  fun clearInputState() {
    creating = false
    renameTarget = null
    inputText = ""
  }

  NormalDialog(
    dialogState = state.dialogState,
    titleRes = R.string.tag_manage,
    negativeRes = R.string.cancel,
    onNegative = { libraryVM.dismissTagManageDialog() },
    custom = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickWithRipple(false) {
            creating = true
            renameTarget = null
            inputText = ""
            inputDialogState.show()
          }
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextPrimary(
          stringResource(R.string.create_tag),
          fontSize = 15.sp,
          color = theme.secondary
        )
      }

      if (allTags.isEmpty()) {
        TextSecondary(stringResource(R.string.no_tag), fontSize = 14.sp)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          allTags.forEach { tag ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              TextPrimary(
                tag,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
              )
              TextPrimary(
                stringResource(R.string.rename_tag),
                fontSize = 14.sp,
                color = theme.secondary,
                modifier = Modifier
                  .clickWithRipple(false) {
                    creating = false
                    renameTarget = tag
                    inputText = tag
                    inputDialogState.show()
                  }
                  .padding(horizontal = 8.dp, vertical = 6.dp)
              )
              TextPrimary(
                stringResource(R.string.delete_tag),
                fontSize = 14.sp,
                color = theme.secondary,
                modifier = Modifier
                  .clickWithRipple(false) {
                    deleteTarget = tag
                    deleteDialogState.show()
                  }
                  .padding(horizontal = 8.dp, vertical = 6.dp)
              )
            }
          }
        }
      }
    }
  )

  InputDialog(
    dialogState = inputDialogState,
    text = inputText,
    title = when {
      creating -> stringResource(R.string.create_tag)
      renameTarget != null -> stringResource(R.string.rename_tag)
      else -> null
    },
    positive = stringResource(R.string.confirm),
    negative = stringResource(R.string.cancel),
    onValueChange = { inputText = it },
    onNegative = { clearInputState() },
    onDismissRequest = { clearInputState() },
    onInput = { newName ->
      if (creating) {
        libraryVM.createTag(newName)
        clearInputState()
      } else if (PermissionUtil.canWriteAudioFiles()) {
        renameTarget?.let { libraryVM.renameTag(it, newName) }
        clearInputState()
      } else {
        // 无写权限时保留输入框，引导去开启
        storageDialogState.show()
      }
    }
  )

  deleteTarget?.let { target ->
    NormalDialog(
      dialogState = deleteDialogState,
      title = stringResource(R.string.delete_tag),
      content = stringResource(R.string.confirm_delete_tag, target),
      positive = stringResource(R.string.confirm),
      negative = stringResource(R.string.cancel),
      onPositive = {
        if (PermissionUtil.canWriteAudioFiles()) {
          libraryVM.deleteTag(target)
        } else {
          storageDialogState.show()
        }
        deleteTarget = null
      },
      onNegative = {
        deleteTarget = null
      }
    )
  }

  ManageStorageDialog(
    dialogState = storageDialogState,
    onCancel = { storageDialogState.dismiss() }
  )
}
