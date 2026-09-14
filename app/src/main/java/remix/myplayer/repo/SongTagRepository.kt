package remix.myplayer.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import remix.myplayer.App
import remix.myplayer.data.db.room.AppDatabase
import remix.myplayer.data.db.room.entity.SongTagCache
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.SongTagFile
import remix.myplayer.misc.MediaScanner
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

  private val tagDao get() = database.tagDao()

  /** 歌曲路径 -> 标签集合 的实时流，来自缓存表 */
  fun tagsFlow(): Flow<Map<String, Set<String>>> =
    cacheDao.observeAll().map { list ->
      list.associate { it.path to SongTagFile.parseTags(it.tags) }
    }

  /** 用户手动创建的标签流（未绑定歌曲的孤立标签也会出现） */
  fun knownTagsFlow(): Flow<Set<String>> =
    tagDao.observeAll().map { list -> list.map { it.name }.toSet() }

  /** 新建标签（同名已存在则忽略） */
  suspend fun addKnownTag(name: String) = tagDao.insert(name)

  /** 删除标签 */
  suspend fun removeKnownTag(name: String) = tagDao.delete(name)

  /** 重命名标签 */
  suspend fun renameKnownTag(oldName: String, newName: String) = tagDao.rename(oldName, newName)

  /** 获取某首歌的标签，缓存未命中或音频文件比缓存新时直接读文件 */
  suspend fun tagsFor(path: String): Set<String> {
    val cached = cacheDao.getByPath(path)
    // 音频文件被修改（如其他应用写了标签）但缓存未更新时，直接读文件
    val fileNewer = cached != null &&
      runCatching { File(path).lastModified() }.getOrDefault(0L).let { it > 0 && cached.updateTime < it }
    if (cached == null || fileNewer) {
      return runCatching { SongTagFile.readTags(File(path)) }.getOrDefault(emptySet())
    }
    return SongTagFile.parseTags(cached.tags)
  }

  /** 所有标签名 */
  suspend fun allTagNames(): Set<String> =
    cacheDao.getAll().flatMap { SongTagFile.parseTags(it.tags) }.toSet()

  /** 歌曲路径 -> 标签集合 的快照（数据分析用） */
  suspend fun allTagsByPath(): Map<String, Set<String>> =
    cacheDao.getAll().associate { it.path to SongTagFile.parseTags(it.tags) }

  /** 清空标签缓存索引（下次扫描会重新从音频文件读取标签） */
  suspend fun clearIndex() = cacheDao.clearAll()

  /** 扫描歌曲文件的标签，增量同步到缓存表 */
  suspend fun refreshIndex(songs: List<Song>) {
    val localSongs = songs.filter { it.isLocal() && it.valid() }
    if (localSongs.isEmpty()) return

    val existing = cacheDao.getAll().associate { it.path to it.updateTime }
    // 只扫描缓存缺失或文件被修改过的歌曲。
    // MediaStore 的 date_modified 在文件被其他应用直接改写后可能不刷新（如 A/B 两个应用互写标签），
    // 因此以磁盘文件的 lastModified 为准，确保能感知标签变化；拿不到文件时间再回退 MediaStore。
    val toScan = localSongs.filter { song ->
      val cachedTime = existing[song.data]
      if (cachedTime == null) {
        true
      } else {
        val fileTime = runCatching { File(song.data).lastModified() }.getOrDefault(0L)
        if (fileTime > 0) {
          cachedTime < fileTime
        } else {
          song.dateModified > 0 && cachedTime < song.dateModified * 1000L
        }
      }
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

  /** 将标签写入音频文件并更新缓存，返回写入结果 */
  suspend fun saveTags(song: Song, tags: Set<String>): Result<Unit> {
    if (!song.isLocal() || !song.valid()) {
      return Result.failure(IllegalStateException("Not a valid local song: ${song.data}"))
    }
    return withContext(Dispatchers.IO) {
      runCatching {
        val ok = SongTagFile.writeTags(File(song.data), App.context.cacheDir, tags)
        check(ok) { "Failed to write tags to file: ${song.data}" }
        cacheDao.upsert(
          SongTagCache(
            path = song.data,
            tags = tags.joinToString(SongTagFile.SEPARATOR),
            updateTime = System.currentTimeMillis()
          )
        )
        // 写入完成后通知 MediaStore 重新扫描该文件。
        // 否则 MediaProvider 可能在写入途中感知到文件变化并错误地把歌曲从媒体库移除。
        rescanFile(song.data)
      }
    }
  }

  /** 通知 MediaStore 重新扫描单个文件（写入完成后调用，避免中途被误判无效） */
  private suspend fun rescanFile(path: String) {
    try {
      MediaScanner(App.context).scanSingleFile(App.context, File(path))
    } catch (e: Exception) {
      Timber.w(e, "rescan file failed: $path")
    }
  }

  /** 批量保存多首歌曲的标签，返回各歌曲写入失败的原因（空列表表示全部成功） */
  suspend fun saveTags(songs: List<Song>, tagsByPath: Map<String, Set<String>>): List<Pair<Song, Throwable>> {
    val failures = mutableListOf<Pair<Song, Throwable>>()
    for (song in songs) {
      val tags = tagsByPath[song.data] ?: continue
      saveTags(song, tags).onFailure { failures += song to it }
    }
    return failures
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
