package remix.myplayer.ui.screen.setting.logic.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.dialog.InputDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.settingViewModel

/** 批量重命名默认模板：打开批量重命名弹窗时预填此模板（占位符 {title}{artist}{album}{track}{year}） */
@Composable
fun DefaultRenameTemplateLogic() {
  val settingVM = settingViewModel
  val state by settingVM.settingsState.collectAsStateWithLifecycle()
  val dialogState = rememberDialogState()
  var text by remember { mutableStateOf("") }

  NormalPreference(
    stringResource(R.string.default_rename_template),
    stringResource(R.string.default_rename_template_tip)
  ) {
    text = state.list.defaultRenameTemplate
    dialogState.show()
  }

  InputDialog(
    dialogState = dialogState,
    title = stringResource(R.string.default_rename_template),
    text = text,
    onValueChange = { text = it },
    positive = stringResource(R.string.confirm),
    onInput = { settingVM.setDefaultRenameTemplate(it) }
  )
}
