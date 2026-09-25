package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import remix.myplayer.data.db.room.entity.TagEntity

/**
 * 标签表 DAO：用户手动创建的标签的增删改查，以及排序所需的创建时间/使用情况。
 */
@Dao
abstract class TagDao {

  @Query("SELECT * FROM TagEntity ORDER BY id")
  abstract fun observeAll(): Flow<List<TagEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(tag: TagEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertAll(tags: List<TagEntity>): List<Long>

  @Query("SELECT name FROM TagEntity")
  abstract suspend fun allNames(): List<String>

  /** 标签被使用一次：使用次数 +1 并记录最后使用时间，返回受影响的行数（0 表示标签不存在） */
  @Query("UPDATE TagEntity SET useCount = useCount + 1, lastUsedAt = :time WHERE name = :name")
  abstract suspend fun increaseUsage(name: String, time: Long): Int

  @Query("DELETE FROM TagEntity WHERE name = :name")
  abstract suspend fun delete(name: String)

  @Query("UPDATE TagEntity SET name = :newName WHERE name = :oldName")
  abstract suspend fun rename(oldName: String, newName: String)
}
