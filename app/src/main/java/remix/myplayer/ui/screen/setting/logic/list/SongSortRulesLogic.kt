package remix.myplayer.ui.screen.setting.logic.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.misc.Library
import remix.myplayer.ui.screen.setting.SwitchPreference
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.viewmodel.settingViewModel
import remix.myplayer.viewmodel.settings.SortCategory

@Composable
fun SongSortRulesLogic() {
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  val songLibrary = Library(Library.TAG_SONG)
  val allOrders = songLibrary.sortOrders
  val allItems = songLibrary.menuItems
  val enabled = settingState.library.songSortRules

  Text(
    text = stringResource(R.string.song_sort_rules),
    color = LocalTheme.current.textSecondary,
    fontSize = 13.sp,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
  )

  allOrders.forEachIndexed { index, order ->
    val isOn = enabled.contains(order)
    SwitchPreference(
      stringResource(allItems[index]),
      null,
      isOn
    ) { checked ->
      val newEnabled = if (checked) enabled + order else enabled - order
      // 至少保留 1 个排序规则
      if (!checked && newEnabled.isEmpty()) return@SwitchPreference
      settingVM.setSongSortRules(newEnabled)
      // 关闭当前正在使用的排序规则时，切换到剩余中的第一个，避免列表按隐藏规则排序
      if (!checked && order == settingState.library.songSortOrder && newEnabled.isNotEmpty()) {
        settingVM.setSortOrder(SortCategory.SONG, newEnabled.first())
      }
    }
  }
}
