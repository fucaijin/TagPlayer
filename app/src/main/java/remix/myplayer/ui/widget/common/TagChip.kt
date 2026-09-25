package remix.myplayer.ui.widget.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import remix.myplayer.ui.theme.LocalTheme

/** 标签芯片的内边距：所有展示标签的地方统一，保证样式一致 */
private val TagChipHorizontalPadding = 7.dp
private val TagChipVerticalPadding = 2.dp

/** 标签芯片（选中/未选中两种状态），所有展示标签的地方通用 */
@Composable
fun TagChip(
  tag: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fontSize: TextUnit = 13.sp,
) {
  val theme = LocalTheme.current
  // Material3 的 Surface(onClick) 会强制 48.dp 的最小可交互尺寸，导致每个标签被撑成 48dp 见方，
  // 行距因此显得很大。这里把最小值降到 0，让 chip 按文字内容自适应大小。
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Surface(
      shape = RoundedCornerShape(50),
      color = if (selected) theme.secondary else theme.mainBackground,
      border = BorderStroke(
        width = 1.dp,
        color = if (selected) theme.secondary else theme.textSecondary.copy(alpha = 0.5f)
      ),
      onClick = onClick,
      modifier = modifier
    ) {
    Text(
      text = tag,
      fontSize = fontSize,
      // 收紧行高并去掉字体留白，避免芯片内部出现多余空隙导致行间距过大
      style = TextStyle(
        lineHeight = 1.2.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
      ),
      modifier = Modifier.padding(
        horizontal = TagChipHorizontalPadding,
        vertical = TagChipVerticalPadding
      ),
      color = if (selected) theme.primaryReverse else theme.textPrimary
    )
    }
  }
}
