package remix.myplayer.data.db.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签表：保存用户手动创建的标签名。
 *
 * 标签的唯一真相源仍是音频文件的 `AUDIO_TAGS` 字段（见 [SongTagCache]），
 * 本表仅用于让手动创建但尚未绑定到任何歌曲的标签，也能在标签管理弹窗和
 * 过滤区中显示；已绑定到歌曲的标签则通过 [SongTagCache] 聚合。
 *
 * 另外，本表还记录标签的创建时间与使用情况，供标签弹窗排序使用：
 * [createdAt] 用于"固定位置"排序，[lastUsedAt] / [useCount] 用于"智能排序"。
 */
@Entity(tableName = "TagEntity")
data class TagEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 标签名 */
  val name: String,
  /** 标签创建时间（ms）：手动创建或从音频文件首次扫描到的时间 */
  @ColumnInfo(defaultValue = "0")
  val createdAt: Long = 0,
  /** 历史使用次数：每保存一次包含该标签的歌曲就 +1 */
  @ColumnInfo(defaultValue = "0")
  val useCount: Int = 0,
  /** 最后一次使用时间（ms）：保存包含该标签的歌曲时更新，智能排序的主要依据 */
  @ColumnInfo(defaultValue = "0")
  val lastUsedAt: Long = 0,
) {
  companion object {
    const val TABLE_NAME = "TagEntity"
  }
}
