package remix.myplayer.ui.widget.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.icon
import remix.myplayer.ui.widget.common.TextPrimary

/** 折叠时的高度 */
private val CollapsedHeight = 40.dp

/** 展开时的高度范围 */
private val MinExpandedHeight = 140.dp
private val DefaultExpandedHeight = 240.dp

/**
 * 歌曲列表顶部的标签过滤区。
 * 支持展开/收起、拖拽调整高度、搜索标签、"与/或"切换、管理入口。
 */
@Composable
fun TagFilterPanel(
  allTags: Set<String>,
  selectedTags: Set<String>,
  matchAll: Boolean,
  searchQuery: String,
  expanded: Boolean,
  onSearchQueryChange: (String) -> Unit,
  onMatchAllChange: (Boolean) -> Unit,
  onToggleTag: (String) -> Unit,
  onManageClick: () -> Unit,
  onExpandChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTheme.current
  var panelHeightDp by rememberSaveable { mutableStateOf(DefaultExpandedHeight.value) }

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val maxPanelHeight = maxHeight * 0.6f
    val targetHeight =
      if (expanded) panelHeightDp.dp.coerceIn(MinExpandedHeight, maxPanelHeight) else CollapsedHeight

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(targetHeight)
        .background(theme.mainBackground)
    ) {
      // 头部：标题 + 展开/收起按钮
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(CollapsedHeight)
          .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextPrimary(stringResource(R.string.tag_filter), fontSize = 15.sp)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { onExpandChange(!expanded) }) {
          Icon(
            painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
            contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            tint = theme.icon(),
            modifier = Modifier
              .size(20.dp)
              .rotate(if (expanded) 90f else -90f)
          )
        }
      }

      if (expanded) {
        // 搜索 + 与/或 + 管理
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_search_white_24dp),
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(18.dp)
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 8.dp)
          ) {
            BasicTextField(
              value = searchQuery,
              onValueChange = onSearchQueryChange,
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary)
            )
            if (searchQuery.isEmpty()) {
              TextPrimary(
                stringResource(R.string.search_tag),
                modifier = Modifier.align(Alignment.CenterStart),
                fontSize = 14.sp,
                color = theme.textSecondary
              )
            }
          }

          TextPrimary(
            stringResource(if (matchAll) R.string.tag_mode_and else R.string.tag_mode_or),
            fontSize = 14.sp
          )
          Switch(
            checked = matchAll,
            onCheckedChange = onMatchAllChange,
            colors = SwitchDefaults.colors().copy(
              checkedTrackColor = theme.secondary,
              uncheckedTrackColor = Color.Transparent
            )
          )

          IconButton(onClick = onManageClick) {
            Icon(
              painter = painterResource(R.drawable.ic_settings_24dp),
              contentDescription = stringResource(R.string.tag_manage),
              tint = theme.icon()
            )
          }
        }

        // 标签芯片（可按搜索词过滤、可滚动）
        val filteredTags = if (searchQuery.isBlank()) {
          allTags
        } else {
          allTags.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
        }
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
          if (filteredTags.isEmpty()) {
            TextPrimary(stringResource(R.string.no_tag), fontSize = 13.sp, color = theme.textSecondary)
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              filteredTags.forEach { tag ->
                val isSelected = tag in selectedTags
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (isSelected) theme.secondary else theme.mainBackground,
                  border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) theme.secondary else theme.textSecondary.copy(alpha = 0.5f)
                  ),
                  onClick = { onToggleTag(tag) }
                ) {
                  Text(
                    text = tag,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (isSelected) theme.primaryReverse else theme.textPrimary
                  )
                }
              }
            }
          }
        }

        // 拖拽把手：上下拖动调整高度
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
              detectVerticalDragGestures { _, dragAmount ->
                panelHeightDp = (panelHeightDp - dragAmount).toFloat()
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .width(36.dp)
              .height(4.dp)
              .background(theme.textSecondary.copy(alpha = 0.4f), CircleShape)
          )
        }
      }
    }
  }
}
