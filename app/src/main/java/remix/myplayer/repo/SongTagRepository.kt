package remix.myplayer.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import remix.myplayer.App
import remix.myplayer.data.db.room.AppDatabase
import remix.myplayer.data.db.room.entity.SongTagCache
import remix.myplayer.data.db.room.entity.TagEntity
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.SongTagFile
import remix.myplayer.misc.MediaScanner
import remix.myplayer.util.MetaField
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

  /** 标签表流（未绑定歌曲的孤立标签也会出现），含排序所需的创建时间与使用情况 */
  fun knownTagsFlow(): Flow<List<TagEntity>> = tagDao.observeAll()

  /** 新建标签（同名已存在则忽略） */
  suspend fun addKnownTag(name: String) {
    val tag = name.trim()
    if (tag.isEmpty()) return
    tagDao.insert(TagEntity(name = tag, createdAt = System.currentTimeMillis()))
  }

  /**
   * 保证这些标签在标签表中存在记录（缺失的按当前时间创建）。
   * 从音频文件扫描到的标签也需要落表，才能参与"固定位置"（按创建时间）排序。
   */
  suspend fun ensureTagsExist(names: Collection<String>) {
    val valid = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (valid.isEmpty()) return
    val existing = tagDao.allNames().toSet()
    val missing = valid.filterNot { it in existing }
    if (missing.isEmpty()) return
    val now = System.currentTimeMillis()
    missing.chunked(INSERT_BATCH).forEach { batch ->
      tagDao.insertAll(batch.map { TagEntity(name = it, createdAt = now) })
    }
  }

  /**
   * 记录标签被使用：更新最后使用时间与使用次数（智能排序依据）。
   * 标签尚不存在时先创建（此时使用次数记为 1）。
   */
  suspend fun recordTagsUsage(names: Collection<String>) {
    val valid = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (valid.isEmpty()) return
    val now = System.currentTimeMillis()
    valid.forEach { name ->
      if (tagDao.increaseUsage(name, now) == 0) {
        tagDao.insert(TagEntity(name = name, createdAt = now, useCount = 1, lastUsedAt = now))
      }
    }
  }

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

    val existing = cacheDao.getAll().associateBy { it.path }
    // 只扫描缓存缺失或文件被修改过的歌曲。
    // MediaStore 的 date_modified 在文件被其他应用直接改写后可能不刷新（如 A/B 两个应用互写标签），
    // 因此以磁盘文件的 lastModified 为准，确保能感知标签变化；拿不到文件时间再回退 MediaStore。
    val decisions = localSongs.map { song ->
      val cached = existing[song.data]
      val fileTime = runCatching { File(song.data).lastModified() }.getOrDefault(0L)
      val mediaTime = song.dateModified * 1000L
      val needScan = when {
        cached == null -> true
        fileTime > 0 -> cached.updateTime < fileTime
        else -> mediaTime > 0 && cached.updateTime < mediaTime
      }
      ScanDecision(song, cached, fileTime, mediaTime, needScan)
    }
    val toScan = decisions.filter { it.needScan }.map { it.song }

    Timber.i(
      "tag refreshIndex: songs=%d cached=%d toScan=%d",
      localSongs.size, existing.size, toScan.size
    )
    // 命中缓存而未重扫的歌曲抽样记录：如果文件被其它 App 改过却没重扫，这里能看出时间戳判断是否失效
    decisions.filter { !it.needScan }.take(SKIP_LOG_SAMPLE).forEach { d ->
      Timber.v(
        "tag skip scan: %s cachedTags=%s cachedTime=%d fileTime=%d mediaTime=%d",
        d.song.data, d.cached?.tags, d.cached?.updateTime ?: 0L, d.fileTime, d.mediaTime
      )
    }
    val scanned = if (toScan.isEmpty()) {
      emptyList<SongTagCache>()
    } else withContext(Dispatchers.IO) {
      coroutineScope {
        toScan.chunked((toScan.size / PARALLELISM).coerceAtLeast(1)).map { partition ->
          async(Dispatchers.IO) { partition.mapNotNull(::scanSong) }
        }.flatMap { it.await() }
      }
    }

    // 标签发生变化的歌曲逐条记录（缓存值 -> 文件值），用于排查标签未刷新问题
    scanned.forEach { cache ->
      val old = existing[cache.path]
      if (old == null || old.tags != cache.tags) {
        Timber.i(
          "tag index changed: %s | cached=%s -> file=%s | fileTime=%d",
          cache.path,
          old?.tags ?: "<none>",
          cache.tags,
          runCatching { File(cache.path).lastModified() }.getOrDefault(0L)
        )
      }
    }

    scanned.chunked(INSERT_BATCH).forEach { cacheDao.upsertAll(it) }

    // 清理已不存在的歌曲缓存
    val validPaths = localSongs.map { it.data }.toSet()
    val stalePaths = existing.keys.filter { it !in validPaths }
    if (stalePaths.isNotEmpty()) {
      stalePaths.chunked(INSERT_BATCH).forEach { cacheDao.deleteByPaths(it) }
    }

    // 从音频文件扫描到的标签也落到标签表，这样它们才有创建时间，可参与"固定位置"排序
    val allNames = (existing.values.asSequence() + scanned.asSequence())
      .flatMap { SongTagFile.parseTags(it.tags).asSequence() }
      .toSet()
    if (allNames.isNotEmpty()) {
      ensureTagsExist(allNames)
    }
  }

  /** 将标签写入音频文件并更新缓存，返回写入结果 */
  suspend fun saveTags(song: Song, tags: Set<String>): Result<Unit> {
    if (!song.isLocal() || !song.valid()) {
      Timber.w("tag write skipped, invalid local song: %s", song.data)
      return Result.failure(IllegalStateException("Not a valid local song: ${song.data}"))
    }
    return withContext(Dispatchers.IO) {
      runCatching {
        val file = File(song.data)
        val cachedBefore = cacheDao.getByPath(song.data)
        val timeBefore = file.lastModified()
        val sizeBefore = file.length()
        Timber.i(
          "tag write start: %s | cached=%s -> new=%s | mtime=%d size=%d",
          song.data,
          cachedBefore?.tags ?: "<none>",
          tags.joinToString(SongTagFile.SEPARATOR),
          timeBefore,
          sizeBefore
        )

        val ok = SongTagFile.writeTags(file, App.context.cacheDir, tags)
        check(ok) { "Failed to write tags to file: ${song.data}" }
        cacheDao.upsert(
          SongTagCache(
            path = song.data,
            tags = tags.joinToString(SongTagFile.SEPARATOR),
            updateTime = System.currentTimeMillis()
          )
        )
        // 写入前后的 mtime/size 用于确认文件确实被改动（其它 App 依赖它判断是否需要重扫标签）
        Timber.i(
          "tag write done: %s | mtime %d -> %d | size %d -> %d",
          song.data,
          timeBefore,
          file.lastModified(),
          sizeBefore,
          file.length()
        )

        // 写入完成后通知 MediaStore 重新扫描该文件。
        // 否则 MediaProvider 可能在写入途中感知到文件变化并错误地把歌曲从媒体库移除。
        rescanFile(song.data)
      }.onFailure {
        Timber.e(it, "tag write failed: %s", song.data)
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

  /**
   * 导出标签为 JSON 字符串。
   * - [ExportMode.FILENAME]：key 为文件名（去后缀），导入按文件名忽略大小写匹配；
   * - [ExportMode.METADATA]：按 [fields]（默认 Title + Artist）写出每首歌的元数据与标签，
   *   [fields] 同时写入文件头部，导入时按同样的字段匹配。
   * 标签只与歌曲本身有关，与路径无关，故不再兼容旧版 path 格式。
   */
  suspend fun exportTagsJson(songs: List<Song>, mode: ExportMode, fields: List<MetaField> = listOf(MetaField.TITLE, MetaField.ARTIST)): String {
    val allTags = allTagsByPath()
    val localSongs = songs.filter { it.isLocal() && it.valid() }
    val items = localSongs.map { song ->
      val tags = (allTags[song.data] ?: emptySet()).toList()
      when (mode) {
        ExportMode.FILENAME -> TagExportItem(
          key = File(song.data).nameWithoutExtension,
          tags = tags
        )
        ExportMode.METADATA -> buildMetaItem(song, tags, fields)
      }
    }
    val file = TagExportFile(
      mode = mode.name.lowercase(),
      fields = if (mode == ExportMode.METADATA) fields.map { it.token } else emptyList(),
      exportedAt = System.currentTimeMillis(),
      items = items
    )
    return TagJson.encodeToString(file)
  }

  /**
   * 从 JSON 恢复标签：按文件头部记录的 [TagExportFile.mode] 与 [TagExportFile.fields]
   * 在本机曲库中匹配歌曲，写回音频文件与缓存表，并记录导入统计。
   */
  suspend fun importTagsJson(json: String, songs: List<Song>): TagImportSummary {
    val file = runCatching { TagJson.decodeFromString<TagExportFile>(json) }
      .getOrElse { return TagImportSummary(0, 0, 0, error = it.message) }
    if (file.mode.isBlank()) return TagImportSummary(0, 0, 0, error = "unknown mode")
    val localSongs = songs.filter { it.isLocal() && it.valid() }
    return when (file.mode) {
      "filename" -> importByFilename(file, localSongs)
      "metadata" -> importByMetadata(file, localSongs)
      else -> TagImportSummary(0, 0, 0, error = "unknown mode: ${file.mode}")
    }
  }

  private suspend fun importByFilename(file: TagExportFile, songs: List<Song>): TagImportSummary {
    val index = songs.groupBy { File(it.data).nameWithoutExtension.lowercase() }
    val missingItems = mutableListOf<String>()
    val failedItems = mutableListOf<String>()
    var imported = 0
    var failed = 0
    file.items.forEach { item ->
      val key = item.key?.trim().orEmpty()
      if (key.isEmpty()) {
        missingItems.add("<empty>")
        return@forEach
      }
      val matched = index[key.lowercase()] ?: emptyList()
      if (matched.isEmpty()) {
        missingItems.add(key)
        return@forEach
      }
      val tagSet = item.tags.filter { it.isNotBlank() }.toSet()
      matched.forEach { song ->
        if (writeTag(song, tagSet)) {
          imported++
        } else {
          failed++
          failedItems.add("$key (${File(song.data).name})")
        }
      }
    }
    return TagImportSummary(imported, failed, missingItems.size, missingItems = missingItems, failedItems = failedItems)
  }

  private suspend fun importByMetadata(file: TagExportFile, songs: List<Song>): TagImportSummary {
    val fields = file.fields.mapNotNull { MetaField.fromToken(it) }
    if (fields.isEmpty()) return TagImportSummary(0, 0, 0, error = "missing match fields")
    val missingItems = mutableListOf<String>()
    val failedItems = mutableListOf<String>()
    var imported = 0
    var failed = 0
    file.items.forEach { item ->
      // 待匹配字段在条目中必须都有非空值，否则无法可靠匹配
      val itemValues = fields.associateWith { f -> itemValue(item, f)?.trim().orEmpty() }
      if (itemValues.values.any { it.isEmpty() }) {
        missingItems.add(metaLabel(item, fields))
        return@forEach
      }
      val target = itemValues.mapValues { it.value.lowercase() }
      val matched = songs.filter { song ->
        fields.all { f -> songValue(song, f).trim().lowercase() == target[f] }
      }
      if (matched.isEmpty()) {
        missingItems.add(metaLabel(item, fields))
        return@forEach
      }
      val tagSet = item.tags.filter { it.isNotBlank() }.toSet()
      matched.forEach { song ->
        if (writeTag(song, tagSet)) {
          imported++
        } else {
          failed++
          failedItems.add(metaLabel(item, fields))
        }
      }
    }
    return TagImportSummary(imported, failed, missingItems.size, missingItems = missingItems, failedItems = failedItems)
  }

  /** 将标签写回音频文件、更新缓存并把标签登记到标签表，返回是否成功 */
  private suspend fun writeTag(song: Song, tagSet: Set<String>): Boolean = runCatching {
    SongTagFile.writeTags(File(song.data), App.context.cacheDir, tagSet)
    cacheDao.upsert(
      SongTagCache(
        path = song.data,
        tags = tagSet.joinToString(SongTagFile.SEPARATOR),
        updateTime = System.currentTimeMillis()
      )
    )
    tagSet.forEach { addKnownTag(it) }
  }.isSuccess

  private fun buildMetaItem(song: Song, tags: List<String>, fields: List<MetaField>): TagExportItem {
    var item = TagExportItem(tags = tags)
    fields.forEach { f ->
      item = when (f) {
        MetaField.TITLE -> item.copy(title = song.title.trim())
        MetaField.ARTIST -> item.copy(artist = song.artist.trim())
        MetaField.ALBUM -> item.copy(album = song.album.trim())
        MetaField.GENRE -> item.copy(genre = song.genre.trim())
        else -> item
      }
    }
    return item
  }

  private fun songValue(song: Song, field: MetaField): String = when (field) {
    MetaField.TITLE -> song.title
    MetaField.ARTIST -> song.artist
    MetaField.ALBUM -> song.album
    MetaField.GENRE -> song.genre
    else -> ""
  }

  private fun itemValue(item: TagExportItem, field: MetaField): String? = when (field) {
    MetaField.TITLE -> item.title
    MetaField.ARTIST -> item.artist
    MetaField.ALBUM -> item.album
    MetaField.GENRE -> item.genre
    else -> null
  }

  private fun metaLabel(item: TagExportItem, fields: List<MetaField>): String =
    fields.joinToString(" / ") { f -> itemValue(item, f)?.trim().orEmpty() }

  companion object {
    /** 并行扫描线程数 */
    private const val PARALLELISM = 8

    /** 单批数据库写入条数 */
    private const val INSERT_BATCH = 500

    /** 未重扫歌曲的日志抽样条数 */
    private const val SKIP_LOG_SAMPLE = 5
  }
}

/** 单首歌是否需要重扫标签的判定结果（仅用于日志排查） */
private data class ScanDecision(
  val song: Song,
  val cached: SongTagCache?,
  val fileTime: Long,
  val mediaTime: Long,
  val needScan: Boolean
)

/** 标签导出/导入的匹配方式 */
enum class ExportMode {
  /** 按文件名（不含后缀）匹配 */
  FILENAME,
  /** 按元数据字段匹配（字段列表见 [TagExportFile.fields]） */
  METADATA
}

/** 歌曲标签导出文件结构：头部记录 mode 与 fields，导入时据此匹配 */
@Serializable
private data class TagExportItem(
  val key: String? = null,
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  val genre: String? = null,
  val tags: List<String> = emptyList()
)

@Serializable
private data class TagExportFile(
  val format: String = FORMAT_TAG,
  val version: Int = 1,
  val mode: String = "",
  val fields: List<String> = emptyList(),
  val exportedAt: Long = 0,
  val items: List<TagExportItem> = emptyList()
)

private const val FORMAT_TAG = "TagPlayer/SongTags"

/** 专用于标签导出/导入的 JSON 配置：美化输出、容忍未知字段 */
private val TagJson = Json {
  prettyPrint = true
  ignoreUnknownKeys = true
}

/** 标签导入结果统计 */
data class TagImportSummary(
  val imported: Int,
  val failed: Int,
  val missing: Int,
  val error: String? = null,
  val missingItems: List<String> = emptyList(),
  val failedItems: List<String> = emptyList()
) {
  /** 生成可复制的文本报告（成功/失败/未匹配 与逐条明细） */
  fun reportText(): String {
    val sb = StringBuilder()
    sb.appendLine("TagPlayer 标签导入结果")
    sb.appendLine("成功: $imported    失败: $failed    未匹配: $missing")
    if (failedItems.isNotEmpty()) {
      sb.appendLine()
      sb.appendLine("【写入失败的条目】")
      failedItems.forEach { sb.appendLine("· $it") }
    }
    if (missingItems.isNotEmpty()) {
      sb.appendLine()
      sb.appendLine("【未匹配的条目】")
      missingItems.forEach { sb.appendLine("· $it") }
    }
    return sb.toString().trimEnd()
  }
}
