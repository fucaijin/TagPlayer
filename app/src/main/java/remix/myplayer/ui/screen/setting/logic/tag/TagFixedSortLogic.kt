package remix.myplayer.ui.screen.setting.logic.tag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.misc.TagFixedSortMode
import remix.myplayer.ui.dialog.ItemsCallbackSingleChoice
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.settingViewModel

/** 固定位置排序时，标签的排列方式：创建时间正序 / 创建时间倒序 / 首字母 */
@Composable
fun TagFixedSortLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  val mode = settingState.tag.fixedSortMode

  val dialogState = rememberDialogState()

  val summaryRes = when (mode) {
    TagFixedSortMode.CREATED_ASC -> R.string.tag_sort_created_asc
    TagFixedSortMode.CREATED_DESC -> R.string.tag_sort_created_desc
    TagFixedSortMode.ALPHA -> R.string.tag_sort_alpha
    TagFixedSortMode.AESTHETIC -> R.string.tag_sort_aesthetic
  }

  NormalPreference(
    stringResource(R.string.tag_fixed_sort),
    stringResource(summaryRes)
  ) {
    dialogState.show()
  }

  val items = listOf(
    R.string.tag_sort_created_asc,
    R.string.tag_sort_created_desc,
    R.string.tag_sort_alpha,
    R.string.tag_sort_aesthetic
  )

  NormalDialog(
    dialogState = dialogState,
    titleRes = R.string.tag_fixed_sort,
    itemRes = items,
    positiveRes = null,
    negativeRes = null,
    itemsCallbackSingleChoice = ItemsCallbackSingleChoice(mode.value) {
      settingVM.setTagFixedSortMode(TagFixedSortMode.fromValue(it))
    }
  )
}
