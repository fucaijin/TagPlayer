package remix.myplayer.ui.screen.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.switchColors
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.ext.clickWithRipple


@Composable
fun ArrowPreference(res: Int, onClick: () -> Unit) {
  val title = stringResource(res)
  Preference(onClick = onClick, title) {
    Icon(
      painter = painterResource(R.drawable.ic_navigate_next_white_24dp), contentDescription = title,
      tint = LocalTheme.current.secondary
    )
  }
}

@Composable
fun NormalPreference(title: String, content: String = "", onClick: () -> Unit) {
  Preference(onClick = onClick, title, content)
}

@Composable
fun ThemePreference(title: String, content: String, primary: Boolean = true, onClick: () -> Unit) {
  Preference(onClick = onClick, title, content) {
    Box(modifier = Modifier
      .size(32.dp)
      .background(if (primary) LocalTheme.current.primary else LocalTheme.current.secondary,
        CircleShape))
  }
}

@Composable
fun SwitchPreference(
  title: String,
  content: String? = null,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Preference(onClick = {
    onCheckedChange(!checked)
  }, title, content, verticalPadding = 6.dp) {
    // Switch 默认带 48dp 的最小交互尺寸，会让整行被撑高，这里关掉以压缩行高
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
      Switch(
        modifier = Modifier.scale(0.9f),
        checked = checked,
        colors = LocalTheme.current.switchColors(),
        onCheckedChange = {
          onCheckedChange(it)
        })
    }
  }
}

@Composable
fun Preference(
  onClick: () -> Unit,
  title: String,
  content: String? = null,
  verticalPadding: Dp = 10.dp,
  leading: (@Composable (() -> Unit))? = null,
  trailing: (@Composable (() -> Unit))? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clickWithRipple(false) {
        onClick()
      }
      .background(color = LocalTheme.current.mainBackground, shape = RectangleShape)
      .padding(horizontal = 16.dp, vertical = verticalPadding),
    verticalAlignment = Alignment.CenterVertically) {
    leading?.invoke()

    Column(
      modifier = Modifier
        .padding(end = 16.dp)
        .weight(1f),
      verticalArrangement = Arrangement.Center
    ) {
      TextPrimary(
        title,
        modifier = Modifier.padding(bottom = if (content != null) 4.dp else 0.dp),
        fontSize = 16.sp
      )
      if (content != null) {
        TextSecondary(content, fontSize = 14.sp, maxLine = Int.MAX_VALUE)
      }
    }

    trailing?.invoke()
  }
}
