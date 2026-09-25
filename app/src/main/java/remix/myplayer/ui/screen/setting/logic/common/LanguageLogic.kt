package remix.myplayer.ui.screen.setting.logic.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import remix.myplayer.R
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.RouteLanguage
import remix.myplayer.ui.screen.setting.NormalPreference

/**
 * 语言设置入口：跳转到「语言」页（支持内置语言切换 + 导入任意语言）。
 */
@Composable
fun LanguageLogic() {
  val nav = LocalNavController.current
  NormalPreference(title = stringResource(R.string.language)) {
    nav.navigate(RouteLanguage)
  }
}
