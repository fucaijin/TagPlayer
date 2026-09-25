package remix.myplayer.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import remix.myplayer.i18n.LanguageScreen
import remix.myplayer.ui.nav.LocalNavController

/**
 * 语言设置页宿主：复用 [LanguageScreen]（自带顶栏与返回键），返回时弹出导航栈。
 */
@Composable
fun LanguageSettingsScreen() {
  val nav = LocalNavController.current
  LanguageScreen(onBack = { nav.popBackStack() })
}
