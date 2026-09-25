package remix.myplayer.ui.screen.setting.logic.tag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.dialog.ItemsCallbackSingleChoice
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.settingViewModel

/** 固定位置排序时，标签按创建时间正序（早→晚）还是倒序（晚→早）排列 */
@Composable
fun TagFixedSortLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val desc = settingState.tag.createdDesc

  val dialogState = rememberDialogState()

  NormalPreference(
    stringResource(R.string.tag_fixed_sort),
    stringResource(
      if (desc) R.string.tag_sort_created_desc else R.string.tag_sort_created_asc
    )
  ) {
    dialogState.show()
  }

  NormalDialog(
    dialogState = dialogState,
    titleRes = R.string.tag_fixed_sort,
    itemRes = listOf(
      R.string.tag_sort_created_asc,
      R.string.tag_sort_created_desc
    ),
    positiveRes = null,
    negativeRes = null,
    itemsCallbackSingleChoice = ItemsCallbackSingleChoice(if (desc) 1 else 0) {
      settingVM.setTagCreatedDesc(it == 1)
    }
  )
}
