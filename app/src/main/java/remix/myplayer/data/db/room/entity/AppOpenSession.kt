package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次应用使用会话（数据分析用）：进入前台到退到后台。
 */
@Entity(tableName = "AppOpenSession", indices = [Index("openTime")])
data class AppOpenSession(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 进入前台时间 */
  val openTime: Long,
  /** 退到后台时间，0 表示会话未正常关闭 */
  val closeTime: Long
) {
  companion object {
    const val TABLE_NAME = "AppOpenSession"
  }
}
