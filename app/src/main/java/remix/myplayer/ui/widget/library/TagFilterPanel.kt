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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import remix.myplayer.R
import remix.myplayer.data.model.misc.TagFilterMode
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.icon
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.util.ext.clickWithRipple

/** 展开时的高度范围 */
private val MinExpandedHeight = 140.dp
private val DefaultExpandedHeight = 240.dp

/**
 * 歌曲列表顶部的标签过滤区（展开后的内容）。
 * 头部（标签过滤 + 箭头）由调用方放在"随机播放全部"同一行，点击后此面板显示在其下方。
 *
 * 支持拖拽调整高度、搜索标签、5 种过滤模式（包含与/包含或/互斥与/互斥或/全匹配）、管理入口。
 * 互斥模式下标签区分为左右两半：左侧用于"包含"过滤，右侧用于"排除"过滤。
 */
@Composable
fun TagFilterPanel(
  allTags: Set<String>,
  mode: TagFilterMode,
  includedTags: Set<String>,
  excludedTags: Set<String>,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onModeChange: (TagFilterMode) -> Unit,
  onToggleIncludeTag: (String) -> Unit,
  onToggleExcludeTag: (String) -> Unit,
  onManageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTheme.current
  val density = LocalDensity.current.density
  var panelHeightDp by rememberSaveable { mutableStateOf(DefaultExpandedHeight.value) }
  var modeMenuExpanded by rememberSaveable { mutableStateOf(false) }

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val maxPanelHeight = maxHeight * 0.6f
    val targetHeight =
      panelHeightDp.dp.coerceIn(MinExpandedHeight, maxPanelHeight)

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(targetHeight)
        .background(theme.mainBackground)
    ) {
      // 搜索 + 过滤模式 + 管理
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

        // 过滤模式：下拉选择（包含与/包含或/互斥与/互斥或/全匹配），选择后会保存
        Box {
          TextPrimary(
            stringResource(mode.labelRes),
            fontSize = 14.sp,
            color = theme.secondary,
            modifier = Modifier
              .clickWithRipple { modeMenuExpanded = true }
              .padding(horizontal = 6.dp, vertical = 4.dp)
          )
          DropdownMenu(
            expanded = modeMenuExpanded,
            containerColor = theme.dialogBackground,
            onDismissRequest = { modeMenuExpanded = false }
          ) {
            TagFilterMode.entries.forEach { item ->
              DropdownMenuItem(
                text = {
                  Text(
                    stringResource(item.labelRes),
                    color = if (item == mode) theme.secondary else theme.textPrimary
                  )
                },
                onClick = {
                  modeMenuExpanded = false
                  onModeChange(item)
                }
              )
            }
          }
        }

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
        allTags.toList()
      } else {
        allTags.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
      }

      if (mode.isExclusive) {
        // 互斥模式：左"包含"、右"排除"
        Row(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          TagChipColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            title = stringResource(R.string.tag_filter_include),
            tags = filteredTags,
            selectedTags = includedTags,
            onToggleTag = onToggleIncludeTag
          )
          Box(
            modifier = Modifier
              .width(1.dp)
              .fillMaxHeight()
              .background(theme.textSecondary.copy(alpha = 0.2f))
          )
          TagChipColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            title = stringResource(R.string.tag_filter_exclude),
            tags = filteredTags,
            selectedTags = excludedTags,
            onToggleTag = onToggleExcludeTag
          )
        }
      } else {
        TagChipColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          title = null,
          tags = filteredTags,
          selectedTags = includedTags,
          onToggleTag = onToggleIncludeTag
        )
      }

      // 拖拽把手：上下拖动调整高度（dragAmount 是像素，按屏幕密度换算成 dp，与手指移动保持一致）
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(20.dp)
          .pointerInput(Unit) {
            detectVerticalDragGestures { _, dragAmount ->
              panelHeightDp = (panelHeightDp + dragAmount / density)
                .coerceIn(MinExpandedHeight.value, maxPanelHeight.value)
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

/** 一列标签芯片（可滚动），用于非互斥模式的单一列表与互斥模式的"包含/排除"两半 */
@Composable
private fun TagChipColumn(
  modifier: Modifier,
  title: String?,
  tags: List<String>,
  selectedTags: Set<String>,
  onToggleTag: (String) -> Unit,
) {
  val theme = LocalTheme.current

  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 4.dp)
  ) {
    if (title != null) {
      TextPrimary(title, fontSize = 12.sp, color = theme.textSecondary)
    }
    if (tags.isEmpty()) {
      TextPrimary(
        stringResource(R.string.no_tag),
        fontSize = 13.sp,
        color = theme.textSecondary,
        modifier = Modifier.padding(top = 4.dp)
      )
    } else {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
      ) {
        tags.forEach { tag ->
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
}
