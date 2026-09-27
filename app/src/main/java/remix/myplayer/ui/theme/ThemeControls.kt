package remix.myplayer.ui.theme

import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 统一的选择类控件配色。
 *
 * MaterialTheme 在本应用中固定使用浅色配色（见 APlayerTheme），
 * 直接使用 Checkbox / RadioButton / Switch 的默认色时，未选中态在深色与纯黑主题下几乎不可见。
 * 这里提供跟随 AppTheme 的配色，全应用的选择类控件都应使用它们以保证深浅色下都清晰。
 */

/** 未选中态颜色：浅色用次级文字色，深色提亮到可见灰 */
@Composable
@ReadOnlyComposable
fun AppTheme.controlUncheckedColor(): Color =
  if (isLight) textSecondary else Color(0xFF9A9AA5)

/** 复选框配色：选中用主色，未选中用 [controlUncheckedColor]，勾号自动适配主色明暗 */
@Composable
fun AppTheme.checkboxColors(): CheckboxColors = CheckboxDefaults.colors(
  checkedColor = primary,
  uncheckedColor = controlUncheckedColor(),
  checkmarkColor = primaryReverse
)

/** 单选框配色 */
@Composable
fun AppTheme.radioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(
  selectedColor = primary,
  unselectedColor = controlUncheckedColor()
)

/** 开关配色：打开时轨道用副色，关闭时轨道透明 + 可见描边 */
@Composable
fun AppTheme.switchColors(): SwitchColors = SwitchDefaults.colors(
  checkedThumbColor = primaryReverse,
  checkedTrackColor = secondary,
  uncheckedThumbColor = controlUncheckedColor(),
  uncheckedTrackColor = Color.Transparent,
  uncheckedBorderColor = controlUncheckedColor()
)
