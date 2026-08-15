package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签表：保存用户手动创建的标签名。
 *
 * 标签的唯一真相源仍是音频文件的 `AUDIO_TAGS` 字段（见 [SongTagCache]），
 * 本表仅用于让手动创建但尚未绑定到任何歌曲的标签，也能在标签管理弹窗和
 * 过滤区中显示；已绑定到歌曲的标签则通过 [SongTagCache] 聚合。
 */
@Entity(tableName = "TagEntity")
data class TagEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 标签名 */
  val name: String
) {
  companion object {
    const val TABLE_NAME = "TagEntity"
  }
}
