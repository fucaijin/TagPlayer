package remix.myplayer.ui.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 标签流式布局。
 *
 * - 普通模式：按原顺序逐行装载（与 [androidx.compose.foundation.layout.FlowRow] 相同语义）。
 * - 美观（尽量紧凑）模式：使用 FFD（First Fit Decreasing）装箱算法，按标签真实宽度降序依次
 *   放入第一个能容纳它的行，使每一行都尽量填满容器宽度。
 *
 * [edgeAlign]（两端对齐展示）与排序/装箱模式相互独立：无论哪种模式，只要开启就按该方式展示。
 * 例外：若存在多行，且最后一行恰好是标签数量最少的一行（典型的"尾部零散行"），则该行不做两端
 * 对齐，改为左对齐 + 固定间距，避免少量标签被拉出巨大空隙；最后一行不是数量最少的行时正常生效。
 *
 * @param tags        标签列表（顺序在美观模式下会被 FFD 按宽度重排）。
 * @param aesthetic   是否启用美观（紧凑）装箱排列（FFD）。
 * @param edgeAlign   是否每行两端对齐（首尾贴边、中间均分剩余宽度），最后一行可能被豁免。
 * @param spacing     普通模式下的标签间距、行间距，以及美观模式未两端对齐时的行内/行间距。
 * @param itemContent 单个标签的渲染（应包裹内容、自适应宽度，如 [TagChip]）。
 */
@Composable
fun TagFlow(
  tags: List<String>,
  aesthetic: Boolean,
  edgeAlign: Boolean,
  modifier: Modifier = Modifier,
  spacing: Dp = 6.dp,
  itemContent: @Composable (String) -> Unit,
) {
  val density = LocalDensity.current
  val spacingPx = with(density) { spacing.roundToPx() }

  Layout(
    content = { tags.forEach { itemContent(it) } },
    modifier = modifier,
  ) { measurables, constraints ->
    val maxWidth = constraints.maxWidth
    // 宽度有界时按可用宽度测量，避免超长标签溢出容器；无界时按自然宽度测量
    val childMaxWidth = if (constraints.hasBoundedWidth) maxWidth else Constraints.Infinity
    val placeables = measurables.map {
      it.measure(constraints.copy(minWidth = 0, maxWidth = childMaxWidth))
    }

    // 普通模式按原顺序折行；美观模式按 FFD 紧凑装箱
    val rows = if (aesthetic) {
      packFirstFitDecreasing(placeables, maxWidth)
    } else {
      packSequentially(placeables, maxWidth, spacingPx)
    }

    val totalHeight = if (rows.isEmpty()) 0 else {
      rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * spacingPx
    }

    // 只有一行时不存在"尾部行"，保持两端对齐；多行且最后一行标签数量最少时豁免该行
    val skipLastRow = rows.size > 1 && rows.last().size == rows.minOf { it.size }

    layout(maxWidth, totalHeight) {
      var y = 0
      rows.forEachIndexed { rowIndex, row ->
        val rowHeight = row.maxOf { it.height }
        val totalItemWidth = row.sumOf { it.width }
        val gaps = row.size - 1
        // 两端对齐：剩余宽度平均分配到各项之间；否则左对齐并使用固定间距（避免溢出）
        val remaining = (maxWidth - totalItemWidth).coerceAtLeast(0)
        val justified = edgeAlign && !(skipLastRow && rowIndex == rows.lastIndex)
        val gap = if (justified) {
          if (gaps > 0) remaining / gaps else 0
        } else {
          if (gaps > 0) spacingPx.coerceAtMost(remaining / gaps) else 0
        }
        var x = 0
        row.forEachIndexed { i, item ->
          item.placeRelative(
            x = x,
            // 美观模式下一行内居中对齐；普通模式与 FlowRow 一致，顶对齐
            y = if (aesthetic) y + (rowHeight - item.height) / 2 else y
          )
          x += item.width
          if (i < gaps) x += gap
        }
        y += rowHeight + spacingPx
      }
    }
  }
}

/** 按 [placeables] 原顺序折行：当前行装不下（含行内间距）时换到下一行 */
private fun packSequentially(
  placeables: List<Placeable>,
  maxWidth: Int,
  spacingPx: Int,
): List<List<Placeable>> {
  if (placeables.isEmpty()) return emptyList()
  val rows = mutableListOf<MutableList<Placeable>>()
  var rowWidth = 0
  for (p in placeables) {
    val current = rows.lastOrNull()
    val used = if (current == null) 0 else rowWidth + spacingPx + p.width
    if (current != null && used <= maxWidth) {
      current.add(p)
      rowWidth = used
    } else {
      rows.add(mutableListOf(p))
      rowWidth = p.width
    }
  }
  return rows
}

/** FFD：按宽度降序，依次放入第一个能容纳它的行 */
private fun packFirstFitDecreasing(
  placeables: List<Placeable>,
  maxWidth: Int,
): List<List<Placeable>> {
  val descending = placeables.sortedByDescending { it.width }
  val rows = mutableListOf<MutableList<Placeable>>()
  for (p in descending) {
    val w = p.width
    val row = rows.firstOrNull { it.sumOf { item -> item.width } + w <= maxWidth }
    if (row != null) row.add(p) else rows.add(mutableListOf(p))
  }
  return rows
}
