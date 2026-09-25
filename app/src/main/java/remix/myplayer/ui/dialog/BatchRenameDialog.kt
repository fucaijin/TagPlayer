package remix.myplayer.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.RenameTemplate
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 批量重命名弹窗：按模板（{title}{artist}{album}{track}{year}）重命名选中的本地歌曲文件。
 */
@Composable
fun BatchRenameDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.batchRenameState.collectAsStateWithLifecycle()
  val theme = LocalTheme.current
  val scope = rememberCoroutineScope()

  var template by remember { mutableStateOf(libraryVM.settingPrefs.defaultRenameTemplate) }
  LaunchedEffect(state.dialogState.isOpen) {
    if (state.dialogState.isOpen) {
      // 打开弹窗时预填用户设置的默认重命名模板
      template = libraryVM.settingPrefs.defaultRenameTemplate
    }
  }

  // 实时预览：对第一首歌套用模板
  val first = state.songs.firstOrNull()
  val preview = if (first != null && template.isNotBlank()) {
    val ext = first.data.substringAfterLast('.', "")
    val name = RenameTemplate.buildName(template, first) ?: ""
    if (ext.isNotBlank()) "$name.$ext" else name
  } else {
    ""
  }

  NormalDialog(
    dialogState = state.dialogState,
    titleRes = R.string.batch_rename,
    negativeRes = R.string.cancel,
    onNegative = { libraryVM.dismissBatchRenameDialog() },
    positiveRes = R.string.confirm,
    onPositive = {
      libraryVM.dismissBatchRenameDialog()
      scope.launch(Dispatchers.IO) {
        val ok = libraryVM.renameSongs(state.songs, template)
        MessageNotifier.show(R.string.batch_rename_success, ok)
      }
    },
    custom = {
      TextSecondary(stringResource(R.string.rename_template_hint), fontSize = 13.sp)
      BasicTextField(
        value = template,
        onValueChange = { template = it },
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary)
      )
      if (preview.isNotEmpty()) {
        TextPrimary(
          stringResource(R.string.batch_rename_preview) + ": " + preview,
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 8.dp)
        )
      }
    }
  )
}
