package remix.myplayer.data.model.misc

/**
 * 固定位置排序（关闭智能排序时）的标签排列方式。
 *
 * @property value 持久化到 SharedPreferences 的整型值。
 */
enum class TagFixedSortMode(val value: Int) {
  /** 按标签创建时间正序（早→晚） */
  CREATED_ASC(0),
  /** 按标签创建时间倒序（晚→早） */
  CREATED_DESC(1),
  /** 按标签首字母排序 */
  ALPHA(2),
  /** 美观排序（尽量紧凑）：FFD 装箱，每行尽量填满容器宽度 */
  AESTHETIC(3);

  companion object {
    fun fromValue(value: Int): TagFixedSortMode =
      entries.firstOrNull { it.value == value } ?: CREATED_ASC
  }
}

/**
 * 标签弹窗中标签的排序方式。
 *
 * @property smartSort 是否智能排序（按最后使用时间倒序，相同则使用次数多的在前）；
 *   关闭时按固定位置排列（见 [fixedSortMode]）。
 * @property fixedSortMode 固定位置排序模式：创建时间正序 / 创建时间倒序 / 首字母。
 */
data class TagSortSetting(
  val smartSort: Boolean = false,
  val fixedSortMode: TagFixedSortMode = TagFixedSortMode.CREATED_ASC,
)
