package remix.myplayer.data.model.misc

/**
 * 标签弹窗中标签的排序方式。
 *
 * @property smartSort 是否智能排序（按最后使用时间倒序，相同则使用次数多的在前）；
 *   关闭时按标签创建时间固定排列。
 * @property createdDesc 固定位置排序时是否按创建时间倒序（新创建的在前）。
 */
data class TagSortSetting(
  val smartSort: Boolean = false,
  val createdDesc: Boolean = false,
)
