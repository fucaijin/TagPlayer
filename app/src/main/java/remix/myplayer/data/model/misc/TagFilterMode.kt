package remix.myplayer.data.model.misc

import androidx.annotation.StringRes
import remix.myplayer.R

/**
 * 标签过滤模式（歌曲列表顶部过滤区）。
 *
 * - [INCLUDE_AND] 包含与：歌曲标签须包含左侧选中的全部标签
 * - [INCLUDE_OR]  包含或：歌曲标签包含左侧任意一个标签即可
 * - [EXCLUDE_AND] 互斥与：左侧按"包含与"过滤，右侧标签须一个都不包含
 * - [EXCLUDE_OR]  互斥或：左侧按"包含或"过滤，右侧标签须一个都不包含
 * - [EXACT]       全匹配：歌曲标签须与选中的标签完全一致（不多不少）
 */
enum class TagFilterMode(@get:StringRes val labelRes: Int) {

  INCLUDE_AND(R.string.tag_mode_include_and),
  INCLUDE_OR(R.string.tag_mode_include_or),
  EXCLUDE_AND(R.string.tag_mode_exclusive_and),
  EXCLUDE_OR(R.string.tag_mode_exclusive_or),
  EXACT(R.string.tag_mode_exact);

  /** 互斥模式：过滤区分为"包含/排除"左右两半 */
  val isExclusive: Boolean
    get() = this == EXCLUDE_AND || this == EXCLUDE_OR

  companion object {

    fun fromName(name: String?): TagFilterMode =
      entries.firstOrNull { it.name == name } ?: INCLUDE_AND
  }
}
