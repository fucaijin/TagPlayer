package remix.myplayer.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import remix.myplayer.data.db.room.AppDatabase
import remix.myplayer.data.db.room.entity.SongTagCache
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.SongTagFile
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌曲标签仓库。
 *
 * 标签的唯一真相源是音频文件的自定义字段 [SongTagFile.TAGS_KEY]，
 * 本仓库负责扫描文件构建内存/数据库索引，并提供读写入口。
 */
@Singleton
class SongTagRepository @Inject constructor(
  private val database: AppDatabase,
) {

  private val cacheDao get() = database.songTagCacheDao()

  /** 歌曲路径 -> 标签集合 的实时流，来自缓存表 */
  fun tagsFlow(): Flow<Map<String, Set<String>>> =
    cacheDao.observeAll().map { list ->
      list.associate { it.path to SongTagFile.parseTags(it.tags) }
    }

  /** 获取某首歌的标签，缓存未命中时直接读文件 */
  suspend fun tagsFor(path: String): Set<String> {
    val cached = cacheDao.getTags(path)
    if (cached != null) {
      return SongTagFile.parseTags(cached)
    }
    // 缓存未命中时回退读文件
    return runCatching { SongTagFile.readTags(File(path)) }.getOrDefault(emptySet())
  }

  /** 所有标签名 */
  suspend fun allTagNames(): Set<String> =
    cacheDao.getAll().flatMap { SongTagFile.parseTags(it.tags) }.toSet()

  /** 扫描歌曲文件的标签，增量同步到缓存表 */
  suspend fun refreshIndex(songs: List<Song>) {
    val localSongs = songs.filter { it.isLocal() && it.valid() }
    if (localSongs.isEmpty()) return

    val existing = cacheDao.getAll().associate { it.path to it.updateTime }
    // 只扫描缓存缺失或文件被修改过的歌曲
    val toScan = localSongs.filter { song ->
      val cachedTime = existing[song.data]
      cachedTime == null || song.dateModified <= 0 || cachedTime < song.dateModified * 1000L
    }
    if (toScan.isEmpty()) return

    val scanned = withContext(Dispatchers.IO) {
      coroutineScope {
        toScan.chunked((toScan.size / PARALLELISM).coerceAtLeast(1)).map { partition ->
          async(Dispatchers.IO) { partition.mapNotNull(::scanSong) }
        }.flatMap { it.await() }
      }
    }

    scanned.chunked(INSERT_BATCH).forEach { cacheDao.upsertAll(it) }

    // 清理已不存在的歌曲缓存
    val validPaths = localSongs.map { it.data }.toSet()
    val stalePaths = existing.keys.filter { it !in validPaths }
    if (stalePaths.isNotEmpty()) {
      stalePaths.chunked(INSERT_BATCH).forEach { cacheDao.deleteByPaths(it) }
    }
  }

  /** 将标签写入音频文件并更新缓存，返回是否成功 */
  suspend fun saveTags(song: Song, tags: Set<String>): Boolean {
    if (!song.isLocal() || !song.valid()) return false
    return withContext(Dispatchers.IO) {
      val ok = runCatching { SongTagFile.writeTags(File(song.data), tags) }.getOrDefault(false)
      if (ok) {
        cacheDao.upsert(
          SongTagCache(
            path = song.data,
            tags = tags.joinToString(SongTagFile.SEPARATOR),
            updateTime = System.currentTimeMillis()
          )
        )
      } else {
        Timber.w("write tags failed: ${song.data}")
      }
      ok
    }
  }

  /** 批量保存多首歌曲的标签，返回成功数量 */
  suspend fun saveTags(songs: List<Song>, tagsByPath: Map<String, Set<String>>): Int {
    var count = 0
    for (song in songs) {
      val tags = tagsByPath[song.data] ?: continue
      if (saveTags(song, tags)) {
        count++
      }
    }
    return count
  }

  private fun scanSong(song: Song): SongTagCache? {
    return runCatching {
      val tags = SongTagFile.readTags(File(song.data))
      SongTagCache(
        path = song.data,
        tags = tags.joinToString(SongTagFile.SEPARATOR),
        updateTime = System.currentTimeMillis()
      )
    }.getOrNull()
  }

  companion object {
    /** 并行扫描线程数 */
    private const val PARALLELISM = 8

    /** 单批数据库写入条数 */
    private const val INSERT_BATCH = 500
  }
}
