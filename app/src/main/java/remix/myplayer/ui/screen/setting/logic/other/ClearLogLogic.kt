package remix.myplayer.ui.screen.setting.logic.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.misc.log.LogFileWriter
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference

/**
 * 设置-其他-清空日志：删除日志目录下所有日志文件。
 */
@Composable
fun ClearLogLogic() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = rememberDialogState()

  NormalPreference(
    stringResource(R.string.clear_log),
    stringResource(R.string.clear_log_tip)
  ) {
    state.show()
  }

  NormalDialog(
    dialogState = state,
    titleRes = R.string.confirm_clear_log,
    onPositive = {
      scope.launch {
        val ok = withContext(Dispatchers.IO) { LogFileWriter.clearLogs() }
        MessageNotifier.show(if (ok) R.string.clear_log_success else R.string.clear_log_error)
      }
    }
  )
}
