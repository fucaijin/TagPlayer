package remix.myplayer.data.model.misc

import androidx.annotation.StringRes
import remix.myplayer.R

/**
 * 标签过滤模式（歌曲列表顶部过滤区）。
 *
 * - [INCLUDE_AND] 包含与：歌曲标签须包含左侧选中的全部标签
 * - [INCLUDE_OR]  包含或：歌曲标签包含左侧任意一个标签即可
 * - [EXCLUDE]     互斥：过滤区分为"包含/排除"左右两半，每侧可单独切换"与/或"
 * - [EXACT]       全匹配：歌曲标签须与选中的标签完全一致（不多不少）
 */
enum class TagFilterMode(@get:StringRes val labelRes: Int) {

  INCLUDE_AND(R.string.tag_mode_include_and),
  INCLUDE_OR(R.string.tag_mode_include_or),
  EXCLUDE(R.string.tag_mode_exclusive),
  EXACT(R.string.tag_mode_exact);

  /** 互斥模式：过滤区分为"包含/排除"左右两半，每侧可单独切换"与/或" */
  val isExclusive: Boolean
    get() = this == EXCLUDE

  companion object {

    fun fromName(name: String?): TagFilterMode = when (name) {
      // 兼容旧版本的"互斥与/互斥或"，统一映射为新的"互斥"
      "EXCLUDE_AND", "EXCLUDE_OR", "EXCLUDE" -> EXCLUDE
      else -> entries.firstOrNull { it.name == name } ?: INCLUDE_AND
    }
  }
}
