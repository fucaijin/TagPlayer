package remix.myplayer.data.db.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import remix.myplayer.data.db.room.entity.TagEntity

/**
 * 标签表 DAO：用户手动创建的标签的增删改查。
 */
@Dao
abstract class TagDao {

  @Query("SELECT * FROM TagEntity ORDER BY id")
  abstract fun observeAll(): Flow<List<TagEntity>>

  @Query("INSERT OR IGNORE INTO TagEntity (name) VALUES (:name)")
  abstract suspend fun insert(name: String)

  @Query("DELETE FROM TagEntity WHERE name = :name")
  abstract suspend fun delete(name: String)

  @Query("UPDATE TagEntity SET name = :newName WHERE name = :oldName")
  abstract suspend fun rename(oldName: String, newName: String)
}
