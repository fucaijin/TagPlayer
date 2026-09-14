package remix.myplayer.ui.screen.setting.logic.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 设置-其他-清空搜索记录：删除搜索页保存的全部搜索关键词。
 */
@Composable
fun ClearSearchHistoryLogic() {
  val libraryVM = libraryViewModel
  val scope = rememberCoroutineScope()
  val state = rememberDialogState()

  NormalPreference(
    stringResource(R.string.clear_search_history),
    stringResource(R.string.clear_search_history_tip)
  ) {
    state.show()
  }

  NormalDialog(
    dialogState = state,
    titleRes = R.string.confirm_clear_search_history,
    onPositive = {
      scope.launch {
        withContext(Dispatchers.IO) { libraryVM.clearSearchHistory() }
        MessageNotifier.show(R.string.clear_search_history_success)
      }
    }
  )
}
