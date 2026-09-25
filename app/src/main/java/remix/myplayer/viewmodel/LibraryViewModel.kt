package remix.myplayer.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Audio
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.data.db.room.entity.PlayList
import remix.myplayer.data.db.room.entity.TagEntity
import remix.myplayer.data.model.misc.TagSortSetting
import remix.myplayer.data.prefs.tagSortFlow
import remix.myplayer.data.model.audio.APlayerModel
import remix.myplayer.data.model.audio.Album
import remix.myplayer.data.model.audio.Artist
import remix.myplayer.data.model.audio.Folder
import remix.myplayer.data.model.audio.Genre
import remix.myplayer.data.model.audio.Song
import remix.myplayer.data.prefs.SettingPrefs
import remix.myplayer.glide.UriFetcher
import remix.myplayer.helper.ConvertFormat
import remix.myplayer.helper.AudioConverter
import remix.myplayer.helper.SongTagFile
import remix.myplayer.misc.MediaScanner
import remix.myplayer.misc.log.LogFileWriter
import remix.myplayer.repo.AlbumRepository
import remix.myplayer.repo.ArtistRepository
import remix.myplayer.repo.FolderRepository
import remix.myplayer.repo.GenreRepository
import remix.myplayer.repo.HistoryRepository
import remix.myplayer.repo.PlayListRepository
import remix.myplayer.repo.SearchHistoryRepository
import remix.myplayer.repo.SongRepository
import remix.myplayer.repo.SongTagRepository
import remix.myplayer.App
import remix.myplayer.repo.TagImportSummary
import remix.myplayer.repo.usecase.ExportPlayListUseCase
import remix.myplayer.repo.usecase.PlayFromUriUseCase
import remix.myplayer.service.MusicEventCallback
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote
import remix.myplayer.ui.dialog.BatchRenameState
import remix.myplayer.ui.dialog.BatchTagState
import remix.myplayer.ui.dialog.DialogState
import remix.myplayer.ui.dialog.ConvertConflict
import remix.myplayer.ui.dialog.ConvertConflictChoice
import remix.myplayer.ui.dialog.ConvertItem
import remix.myplayer.ui.dialog.ConvertSource
import remix.myplayer.ui.dialog.ConvertState
import remix.myplayer.ui.dialog.ConvertStage
import remix.myplayer.ui.dialog.SongTagManageState
import remix.myplayer.ui.dialog.TagManageState
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.util.PermissionUtil
import remix.myplayer.util.ext.checkWorkerThread
import remix.myplayer.util.RenameTemplate
import remix.myplayer.util.ext.updateIf
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val savedStateHandle: SavedStateHandle,
  @param:ApplicationContext private val context: Context,
  private val songRepo: SongRepository,
  private val albumRepo: AlbumRepository,
  private val artistRepo: ArtistRepository,
  private val genreRepo: GenreRepository,
  private val playListRepo: PlayListRepository,
  private val folderRepo: FolderRepository,
  private val uriFetcher: UriFetcher,
  private val historyRepo: HistoryRepository,
  private val searchHistoryRepo: SearchHistoryRepository,
  val settingPrefs: SettingPrefs,
  private val songTagRepo: SongTagRepository,
  private val exportPlayListUseCase: ExportPlayListUseCase,
  private val playFromUriUseCase: PlayFromUriUseCase
) : ViewModel(), MusicEventCallback {

  private var hasPermission = false

  private val _songs = MutableStateFlow<List<Song>>(emptyList())
  val songs: StateFlow<List<Song>> = _songs.asStateFlow()

  // 歌曲路径 -> 标签集合（来自缓存表，音频文件为唯一真相源）
  val songTags: StateFlow<Map<String, Set<String>>> =
    songTagRepo.tagsFlow()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  // 所有标签名（歌曲缓存标签 ∪ 手动创建的标签）
  val allTags: StateFlow<Set<String>> =
    combine(songTags, songTagRepo.knownTagsFlow()) { tagMap, knownTags ->
      tagMap.values.flatten().toSet() + knownTags.map { it.name }
    }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  /**
   * 标签弹窗中标签的展示顺序。
   * 智能排序：最后使用时间倒序，时间相同则历史使用次数多的在前；
   * 固定位置：按标签创建时间正序/倒序（见设置-标签管理）。
   */
  val orderedTags: StateFlow<List<String>> =
    combine(allTags, songTagRepo.knownTagsFlow(), settingPrefs.tagSortFlow()) { tags, entities, sort ->
      val meta = entities.associateBy { it.name }
      sortTags(tags, meta, sort)
    }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private fun sortTags(
    tags: Set<String>,
    meta: Map<String, TagEntity>,
    sort: TagSortSetting
  ): List<String> {
    if (tags.isEmpty()) return emptyList()
    return if (sort.smartSort) {
      tags.sortedWith(
        compareByDescending<String> { meta[it]?.lastUsedAt ?: 0L }
          .thenByDescending { meta[it]?.useCount ?: 0 }
          .thenBy { it }
      )
    } else {
      // 没有创建时间记录的标签（理论上已由扫描补齐）统一排在最后，按名称兜底
      val known = tags.filter { meta.containsKey(it) }
      val unknown = tags.filterNot { meta.containsKey(it) }.sorted()
      val sortedKnown = if (sort.createdDesc) {
        known.sortedWith(
          compareByDescending<String> { meta[it]?.createdAt ?: 0L }.thenBy { it }
        )
      } else {
        known.sortedWith(compareBy<String> { meta[it]?.createdAt ?: 0L }.thenBy { it })
      }
      sortedKnown + unknown
    }
  }

  private val _albums = MutableStateFlow<List<Album>>(emptyList())
  val albums: StateFlow<List<Album>> = _albums.asStateFlow()

  private val _artists = MutableStateFlow<List<Artist>>(emptyList())
  val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

  private val _genres = MutableStateFlow<List<Genre>>(emptyList())
  val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

  val playLists = playListRepo.allPlayLists()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _folders = MutableStateFlow<List<Folder>>(emptyList())
  val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

  val historySongs = historyRepo.allHistories().map { histories ->
    histories.mapNotNull { history ->
      val song = withContext(Dispatchers.IO) { songRepo.song(history.audio_id) }
      song?.let { it to history.play_count }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  // -------- 搜索记录 ----------
  val searchHistories: StateFlow<List<String>> =
    searchHistoryRepo.histories()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  /** 记录一次手动搜索（空串忽略，重复关键词累加次数） */
  fun recordSearch(keyword: String) {
    viewModelScope.launch { searchHistoryRepo.record(keyword) }
  }

  /** 删除单条搜索记录 */
  fun removeSearchHistory(keyword: String) {
    viewModelScope.launch { searchHistoryRepo.remove(keyword) }
  }

  /** 清空全部搜索记录 */
  suspend fun clearSearchHistory() {
    searchHistoryRepo.clear()
  }

  private val _createPlaylistState = MutableStateFlow(CreatePlaylistState())
  val createPlaylistState = _createPlaylistState.asStateFlow()

  fun showCreatePlaylistDialog() {
    val defaultName = "${context.getString(R.string.local_list)}${playLists.value.size}"
    _createPlaylistState.update {
      it.dialogState.show()
      it.copy(name = defaultName)
    }
  }

  fun updateNewPlaylistName(name: String) {
    _createPlaylistState.update { it.copy(name = name) }
  }

  init {
    // load all media
    hasPermission = PermissionUtil.hasNecessaryPermission()
    if (hasPermission) {
      fetchMedia()
    }
  }

  fun insertPlayList(name: String, onSuccess: (Long) -> Unit) {
    viewModelScope.launch {
      if (playListRepo.checkPlayListExist(name)) {
        MessageNotifier.show(R.string.playlist_already_exist)
        return@launch
      }

      val id = playListRepo.insertPlayList(name)
      onSuccess(id)
    }
  }

  fun addSongsToPlayList(audioIds: List<Long>, playListName: String, createNew: Boolean = false) {
    viewModelScope.launch {
      try {
        if (createNew) {
          if (playListRepo.checkPlayListExist(playListName)) {
            MessageNotifier.show(R.string.playlist_already_exist)
            return@launch
          }

          playListRepo.insertPlayList(playListName)
        }

        val count = playListRepo.addSongsToPlayList(audioIds, playListName = playListName)
        MessageNotifier.show(R.string.add_song_playlist_success, count, playListName)
      } catch (ignore: Exception) {
        MessageNotifier.show(R.string.add_song_playlist_error)
      }
    }
  }

  suspend fun loadSongsByModels(models: List<APlayerModel>) = songRepo.getSongsByModels(models)

  fun loadSong(selection: String?, selectionValues: Array<String?>?, sortOrder: String? = null) =
    songRepo.getSongs(selection, selectionValues, sortOrder)

  fun loadLastAddedSongs() = songRepo.getLastAddedSongs()

  fun searchSong(key: String): List<Song> {
    checkWorkerThread()
    val likeKey = "%$key%"
    return songRepo.getSongs(
      "(" +
          Audio.Media.TITLE + " LIKE ? OR " +
          Audio.ArtistColumns.ARTIST + " LIKE ? OR " +
          Audio.AlbumColumns.ALBUM + " LIKE ? OR " +
          Audio.Media.DISPLAY_NAME + " LIKE ?" +
          ")",
      arrayOf(likeKey, likeKey, likeKey, likeKey),
      settingPrefs.songSortOrder
    )
  }

  fun updatePlayList(playList: PlayList) {
    viewModelScope.launch {
      try {
        val duplicate = playLists.value.find { it.name == playList.name && it.id != playList.id }
        if (duplicate != null) {
          MessageNotifier.show(R.string.playlist_already_exist)
          return@launch
        }

        playListRepo.updatePlayList(playList)
        uriFetcher.updatePlayListVersion()
        uriFetcher.clearAllCache()
        Glide.get(context).clearMemory()
        MessageNotifier.show(R.string.save_success)
      } catch (e: Exception) {
        MessageNotifier.show(R.string.save_error)
      }
    }
  }

  fun exportPlayListToFile(playList: PlayList?, uri: Uri) {
    viewModelScope.launch {
      exportPlayListUseCase(playList, uri)
    }
  }

  fun playFromUri(uri: Uri) {
    viewModelScope.launch {
      playFromUriUseCase(uri)
    }
  }

  fun fetchMedia(
    clear: Boolean = false,
    updateAlbumVersion: Boolean = false,
    updateArtistVersion: Boolean = false,
    updatePlayListVersion: Boolean = false
  ) {
    viewModelScope.launch {
      if (clear) {
        if (updateAlbumVersion) {
          uriFetcher.updateAlbumVersion()
        } else if (updateArtistVersion) {
          uriFetcher.updateArtistVersion()
        } else if (updatePlayListVersion) {
          uriFetcher.updatePlayListVersion()
        } else {
          uriFetcher.updateAllVersion()
        }
        uriFetcher.clearAllCache()
        Glide.get(context).clearMemory()
      }

      _songs.value = async(Dispatchers.IO) { songRepo.allSongs() }.await()
      _albums.value = async(Dispatchers.IO) { albumRepo.allAlbums() }.await()
      _artists.value = async(Dispatchers.IO) { artistRepo.allArtists() }.await()
      _genres.value = async(Dispatchers.IO) { genreRepo.allGenres() }.await()
      _folders.value = async(Dispatchers.IO) { folderRepo.allFolders() }.await()
      Timber.v("songCount: ${_songs.value.size} albumCount: ${_albums.value.size} artistCount: ${_artists.value.size} genreCount: ${_genres.value.size} folderCount: ${_folders.value.size}")
      // 增量同步歌曲标签索引到缓存表
      songTagRepo.refreshIndex(_songs.value)
    }
  }

  fun clearHistory() = viewModelScope.launch {
    historyRepo.clear()
  }

  /** 清空标签缓存索引并重新从音频文件读取标签（音频文件是标签的唯一真相源） */
  fun resyncTags() = viewModelScope.launch {
    songTagRepo.clearIndex()
    fetchMedia(true)
  }

  /** 导出所有歌曲标签为 JSON 字符串 */
  suspend fun exportTagsJson(): String = songTagRepo.exportTagsJson()

  /** 从 JSON 字符串导入标签，返回导入统计 */
  suspend fun importTagsJson(json: String): TagImportSummary = songTagRepo.importTagsJson(json)

  // -------- 单曲标签管理弹窗 ----------
  private val _songTagManageState = MutableStateFlow(SongTagManageState())
  val songTagManageState = _songTagManageState.asStateFlow()

  fun showSongTagManageDialog(song: Song) {
    _songTagManageState.updateIf(
      condition = { !it.dialogState.isOpen },
      transform = {
        it.dialogState.show()
        it.copy(song = song)
      }
    )
    logTagDiagnostics(song)
  }

  /**
   * 打开标签弹窗时记录该歌曲的标签诊断信息：文件里的标签 vs 缓存里的标签、文件时间戳。
   * 排查"另一个 App 设置了标签，本 App 没刷新"时，这行日志能直接看出差异。
   */
  private fun logTagDiagnostics(song: Song) {
    if (!LogFileWriter.isEnabled() || !song.valid()) return
    viewModelScope.launch {
      val cached = songTags.value[song.data]
      val file = File(song.data)
      val fileTags = withContext(Dispatchers.IO) {
        runCatching { SongTagFile.readTags(file) }.getOrNull()
      }
      Timber.i(
        "tag dialog: %s | fileTags=%s cachedTags=%s | fileTime=%d fileSize=%d",
        song.data, fileTags, cached, file.lastModified(), file.length()
      )
    }
  }

  fun dismissSongTagManageDialog() {
    _songTagManageState.update { state ->
      state.dialogState.dismiss()
      state.copy()
    }
  }

  /** 将标签写入音频文件并关闭弹窗；成功 toast，失败弹窗显示原因 */
  fun saveSongTags(song: Song, tags: Set<String>) {
    // 格式不支持写标签，或扩展名与真实容器不符（如 M4A 内容却用 .mp3 扩展名）：
    // 询问是否转换为 AAC/MP3 后再写。仅在有标签要写入（添加/设置）时才提示转换，
    // 清空标签走下方直接写入（临时文件兜底，不会弹转换窗）。
    if (tags.isNotEmpty() &&
      (!SongTagFile.isWritable(song.data) || SongTagFile.isContainerMismatch(song.data))
    ) {
      showConvertAsk(
        items = listOf(ConvertItem(song, tags, needConvert = true)),
        source = ConvertSource.SINGLE
      )
      return
    }
    viewModelScope.launch {
      val result = songTagRepo.saveTags(song, tags)
      dismissSongTagManageDialog()
      result.onSuccess {
        // 记录这些标签的最后使用时间与使用次数，供"智能排序"使用
        songTagRepo.recordTagsUsage(tags)
        MessageNotifier.showCenter(R.string.tag_save_success)
        syncPlayQueueAfterTagSave()
      }.onFailure { e ->
        reportTagError(listOf(song to e))
      }
    }
  }

  /**
   * 打标签保存后调用：MediaStore 重扫音频文件可能给歌曲重新分配 _id，
   * 用最新歌曲列表（按路径匹配）重建播放队列，避免当前播放歌曲的高亮丢失、
   * 以及上一首/下一首切回该歌时因旧 id 不存在而播放失败。
   */
  private fun syncPlayQueueAfterTagSave() {
    viewModelScope.launch {
      val freshSongs = withContext(Dispatchers.IO) { songRepo.allSongs() }
      _songs.value = freshSongs
      MusicServiceRemote.reconcilePlayQueue(freshSongs)
    }
  }

  // -------- 不支持标签的格式：转换为 AAC / MP3 ----------
  private val _convertState = MutableStateFlow(ConvertState())
  val convertState = _convertState.asStateFlow()

  private var conflictDeferred: CompletableDeferred<ConvertConflictChoice>? = null
  private var convertJob: Job? = null

  /** 询问是否把不支持标签的文件转换为目标格式（并关闭来源弹窗） */
  private fun showConvertAsk(items: List<ConvertItem>, source: ConvertSource) {
    val convertible = items.filter { it.needConvert }
    if (convertible.isEmpty()) return
    when (source) {
      ConvertSource.SINGLE -> dismissSongTagManageDialog()
      ConvertSource.BATCH -> dismissBatchTagDialog()
    }
    _convertState.updateIf(condition = { !it.dialogState.isOpen }) {
      it.dialogState.show()
      it.copy(
        stage = ConvertStage.ASK,
        source = source,
        items = items,
        convertCount = convertible.size,
        currentIndex = 0,
        progress = 0,
        conflict = null
      )
    }
  }

  /** 用户确认转换 */
  fun confirmConvert() {
    if (_convertState.value.stage != ConvertStage.ASK) return
    _convertState.update {
      it.copy(stage = ConvertStage.CONVERTING, currentIndex = 1, progress = 0)
    }
    convertJob = viewModelScope.launch { runConversion(_convertState.value.items) }
  }

  /** 切换转换的目标格式（AAC / MP3） */
  fun selectConvertFormat(format: ConvertFormat) {
    _convertState.update { it.copy(format = format) }
  }

  /** 取消（询问阶段取消转换，或转换中中止） */
  fun cancelConvert() {
    convertJob?.cancel()
    convertJob = null
    conflictDeferred?.complete(ConvertConflictChoice.CANCEL)
    conflictDeferred = null
    dismissConvertDialog()
  }

  fun chooseConflictRename() = resolveConflict(ConvertConflictChoice.RENAME)

  fun chooseConflictOverwrite() = resolveConflict(ConvertConflictChoice.OVERWRITE)

  fun chooseConflictCancel() = resolveConflict(ConvertConflictChoice.CANCEL)

  private fun resolveConflict(choice: ConvertConflictChoice) {
    val deferred = conflictDeferred ?: return
    conflictDeferred = null
    _convertState.update { it.copy(stage = ConvertStage.CONVERTING, conflict = null) }
    deferred.complete(choice)
  }

  private suspend fun askConflict(conflict: ConvertConflict): ConvertConflictChoice {
    val deferred = CompletableDeferred<ConvertConflictChoice>()
    conflictDeferred = deferred
    _convertState.update { it.copy(stage = ConvertStage.CONFLICT, conflict = conflict) }
    return deferred.await()
  }

  private fun dismissConvertDialog() {
    _convertState.update {
      it.dialogState.dismiss()
      it.copy(
        stage = ConvertStage.IDLE,
        items = emptyList(),
        convertCount = 0,
        currentIndex = 0,
        progress = 0,
        conflict = null
      )
    }
  }

  /**
   * 依次处理：需要转换的先转成同目录同名的目标格式（同名冲突时询问重命名/覆盖）再写标签，
   * 本来就支持写标签的歌曲直接写。原文件保留不删除。
   */
  private suspend fun runConversion(items: List<ConvertItem>) {
    val failures = mutableListOf<Pair<Song, Throwable>>()
    val convertedFiles = mutableListOf<File>()
    val format = _convertState.value.format
    var convertedCount = 0

    try {
      var index = 0
      for (item in items.filter { it.needConvert }) {
        index++
        _convertState.update {
          it.copy(
            stage = ConvertStage.CONVERTING,
            currentIndex = index,
            progress = 0,
            conflict = null
          )
        }

        val source = File(item.song.data)
        if (!source.exists()) {
          failures += item.song to IOException("File not found: ${item.song.data}")
          continue
        }

        // 目标：同目录同名 + 目标格式扩展名
        var target = File(source.parentFile, "${source.nameWithoutExtension}.${format.extension}")
        if (target.exists()) {
          val choice = askConflict(
            ConvertConflict(target.name, target.length(), target.lastModified())
          )
          when (choice) {
            ConvertConflictChoice.CANCEL -> {
              dismissConvertDialog()
              return
            }

            ConvertConflictChoice.RENAME -> target = uniqueTarget(source, format)
            ConvertConflictChoice.OVERWRITE -> Unit
          }
        }

        // 先写到临时文件，成功后覆盖到目标位置，避免留下半成品
        val temp = File(
          context.cacheDir,
          "convert_${System.currentTimeMillis()}.${format.extension}"
        )
        try {
          withContext(Dispatchers.IO) {
            AudioConverter.convert(context, source, temp, format) { progress ->
              _convertState.update { it.copy(progress = progress) }
            }
            temp.copyTo(target, overwrite = true)
            val ok = SongTagFile.writeTags(target, context.cacheDir, item.tags)
            check(ok) { "Failed to write tags: ${target.absolutePath}" }
          }
          convertedFiles += target
          convertedCount++
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          Timber.e(e, "convert to ${format.extension} failed: ${item.song.data}")
          failures += item.song to e
        } finally {
          temp.delete()
        }
      }

      // 不转换的歌曲直接写入标签
      val writable = items.filter { !it.needConvert }
      if (writable.isNotEmpty()) {
        failures += songTagRepo.saveTags(
          writable.map { it.song },
          writable.associate { it.song.data to it.tags }
        )
      }
    } finally {
      dismissConvertDialog()
    }

    songTagRepo.recordTagsUsage(items.flatMap { it.tags })

    if (convertedFiles.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        convertedFiles.forEach { file ->
          runCatching { MediaScanner(context).scanSingleFile(context, file) }
        }
      }
    }
    if (convertedCount > 0) {
      MessageNotifier.showCenter(R.string.convert_success, convertedCount)
    }
    if (failures.isNotEmpty()) {
      reportTagError(failures)
    }
    syncPlayQueueAfterTagSave()
  }

  /** 重命名后的目标：原名 + yyyyMMddHHmmss 时间戳 */
  private fun uniqueTarget(source: File, format: ConvertFormat): File {
    val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
    return File(
      source.parentFile,
      "${source.nameWithoutExtension}_$timestamp.${format.extension}"
    )
  }

  // -------- 标签管理弹窗（过滤区齿轮） ----------
  private val _tagManageState = MutableStateFlow(TagManageState())
  val tagManageState = _tagManageState.asStateFlow()

  fun showTagManageDialog() {
    _tagManageState.updateIf(condition = { !it.dialogState.isOpen }) {
      it.dialogState.show()
      it
    }
  }

  fun dismissTagManageDialog() {
    _tagManageState.update { state ->
      state.dialogState.dismiss()
      state.copy()
    }
  }

  /** 创建标签（写入标签表，未绑定歌曲前也会在管理/过滤区显示） */
  fun createTag(name: String) {
    val tag = name.trim()
    if (tag.isEmpty()) return
    if (tag in allTags.value) {
      MessageNotifier.showCenter(R.string.tag_exists)
    } else {
      viewModelScope.launch {
        songTagRepo.addKnownTag(tag)
        MessageNotifier.showCenter(R.string.tag_created, tag)
      }
    }
  }

  /** 重命名标签：更新标签表并批量更新所有含该标签的歌曲 */
  fun renameTag(oldTag: String, newTag: String) {
    val old = oldTag.trim()
    val new = newTag.trim()
    if (old.isEmpty() || new.isEmpty() || old == new) return
    viewModelScope.launch {
      songTagRepo.renameKnownTag(old, new)
      val tagMap = songTags.value
      val songs = _songs.value.filter { song -> tagMap[song.data]?.contains(old) == true }
      if (songs.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_renamed)
        return@launch
      }
      val tagsByPath = songs.associate { song ->
        song.data to ((tagMap[song.data] ?: emptySet()) - old + new)
      }
      val failures = songTagRepo.saveTags(songs, tagsByPath)
      if (failures.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_renamed)
      } else {
        MessageNotifier.showCenter(R.string.tag_rename_error)
        reportTagError(failures)
      }
      // 重写文件触发 MediaStore 重扫，歌曲 id 可能变化，同步播放队列
      syncPlayQueueAfterTagSave()
    }
  }

  /** 删除标签：从标签表和所有歌曲中移除 */
  fun deleteTag(name: String) {
    val tag = name.trim()
    if (tag.isEmpty()) return
    viewModelScope.launch {
      songTagRepo.removeKnownTag(tag)
      val tagMap = songTags.value
      val songs = _songs.value.filter { song -> tagMap[song.data]?.contains(tag) == true }
      if (songs.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_deleted)
        return@launch
      }
      val tagsByPath = songs.associate { song ->
        song.data to ((tagMap[song.data] ?: emptySet()) - tag)
      }
      val failures = songTagRepo.saveTags(songs, tagsByPath)
      if (failures.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_deleted)
      } else {
        MessageNotifier.showCenter(R.string.tag_delete_error)
        reportTagError(failures)
      }
      // 重写文件触发 MediaStore 重扫，歌曲 id 可能变化，同步播放队列
      syncPlayQueueAfterTagSave()
    }
  }

  // -------- 批量标签弹窗（歌曲多选后） ----------
  private val _batchTagState = MutableStateFlow(BatchTagState())
  val batchTagState = _batchTagState.asStateFlow()

  fun showBatchTagDialog(songs: List<Song>) {
    _batchTagState.updateIf(condition = { !it.dialogState.isOpen }) {
      it.dialogState.show()
      it.copy(songs = songs)
    }
  }

  fun dismissBatchTagDialog() {
    _batchTagState.update { state ->
      state.dialogState.dismiss()
      state.copy()
    }
  }

  // -------- 批量重命名弹窗（歌曲多选后） ----------
  private val _batchRenameState = MutableStateFlow(BatchRenameState())
  val batchRenameState = _batchRenameState.asStateFlow()

  fun showBatchRenameDialog(songs: List<Song>) {
    _batchRenameState.updateIf(condition = { !it.dialogState.isOpen }) {
      it.dialogState.show()
      it.copy(songs = songs)
    }
  }

  fun dismissBatchRenameDialog() {
    _batchRenameState.update { state ->
      state.dialogState.dismiss()
      state.copy()
    }
  }

  /**
   * 按模板批量重命名选中的本地歌曲文件，并重扫媒体库。
   * 模板占位符：{title} 歌名 {artist} 歌手 {album} 专辑 {track} 曲目 {year} 年份
   * 返回成功重命名的文件数。
   */
  suspend fun renameSongs(songs: List<Song>, template: String): Int {
    var ok = 0
    withContext(Dispatchers.IO) {
      songs.forEach { song ->
        if (song.id <= 0 || !song.isLocal()) return@forEach
        val file = File(song.data)
        if (!file.exists()) return@forEach
        val name = RenameTemplate.buildName(template, song) ?: return@forEach
        val newFile = RenameTemplate.newFile(file, name)
        if (newFile.exists() || name.isBlank()) return@forEach
        if (file.renameTo(newFile)) {
          runCatching { MediaScanner(App.context).scanSingleFile(App.context, newFile) }
          ok++
        }
      }
    }
    if (ok > 0) fetchMedia(true)
    return ok
  }

  /**
   * 规范化文件名（不含扩展名）：去除首尾空白与点，并将非法字符([\\/:*?"<>|])替换为下划线。
   */
  private fun normalizeFileName(name: String): String {
    return name.trim().trim('.').replace(Regex("[\\\\/:*?\"<>|]"), "_")
  }

  /**
   * 判断重命名后的文件是否与同目录下已有文件重名（忽略自身）。
   */
  suspend fun isSongNameConflict(song: Song, newName: String): Boolean {
    return withContext(Dispatchers.IO) {
      if (!song.isLocal()) return@withContext false
      val name = normalizeFileName(newName)
      if (name.isEmpty()) return@withContext false
      val file = File(song.data)
      val newFile = RenameTemplate.newFile(file, name)
      newFile.exists() && newFile.absolutePath != file.absolutePath
    }
  }

  /**
   * 重命名单首本地歌曲的文件名（不含扩展名），成功后重扫媒体库。
   * 返回实际生效的新文件名（不含扩展名），失败返回 null。
   */
  suspend fun renameSongFile(song: Song, newName: String): String? {
    val renamed = withContext(Dispatchers.IO) {
      if (song.id <= 0 || !song.isLocal()) return@withContext null
      val name = normalizeFileName(newName)
      if (name.isEmpty()) return@withContext null
      val file = File(song.data)
      if (!file.exists()) return@withContext null
      val newFile = RenameTemplate.newFile(file, name)
      if (newFile.exists()) return@withContext null
      if (!file.renameTo(newFile)) return@withContext null
      song.data = newFile.absolutePath
      song.displayName = newFile.name
      runCatching { MediaScanner(App.context).scanSingleFile(App.context, newFile) }
      name
    }
    if (renamed != null) {
      fetchMedia(true)
    }
    return renamed
  }

  /** 给多首歌曲批量添加标签 */
  fun addTagsToSongs(songs: List<Song>, tags: Set<String>) {
    val valid = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (valid.isEmpty()) return
    val tagMap = songTags.value
    val tagsByPath = songs.associate { song ->
      song.data to ((tagMap[song.data] ?: emptySet()) + valid)
    }
    // 存在不支持写标签的格式（或扩展名与真实容器不符）：询问是否转换
    val needConvert: (Song) -> Boolean = { song ->
      !SongTagFile.isWritable(song.data) || SongTagFile.isContainerMismatch(song.data)
    }
    if (songs.any(needConvert)) {
      showConvertAsk(
        items = songs.map { song ->
          ConvertItem(
            song = song,
            tags = tagsByPath[song.data] ?: valid,
            needConvert = needConvert(song)
          )
        },
        source = ConvertSource.BATCH
      )
      return
    }
    viewModelScope.launch {
      val failures = songTagRepo.saveTags(songs, tagsByPath)
      dismissBatchTagDialog()
      // 记录"添加"操作用到标签的最后使用时间与使用次数，供"智能排序"使用
      songTagRepo.recordTagsUsage(valid)
      if (failures.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_batch_success, songs.size)
      } else {
        MessageNotifier.showCenter(
          R.string.tag_batch_partial_fail,
          songs.size - failures.size,
          failures.size
        )
        reportTagError(failures)
      }
      // 重写文件触发 MediaStore 重扫，歌曲 id 可能变化，同步播放队列
      syncPlayQueueAfterTagSave()
    }
  }

  /** 从多首歌曲批量移除标签 */
  fun removeTagsFromSongs(songs: List<Song>, tags: Set<String>) {
    val valid = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (valid.isEmpty()) return
    viewModelScope.launch {
      val tagMap = songTags.value
      val tagsByPath = songs.associate { song ->
        song.data to ((tagMap[song.data] ?: emptySet()) - valid)
      }
      val failures = songTagRepo.saveTags(songs, tagsByPath)
      dismissBatchTagDialog()
      if (failures.isEmpty()) {
        MessageNotifier.showCenter(R.string.tag_batch_success, songs.size)
      } else {
        MessageNotifier.showCenter(
          R.string.tag_batch_partial_fail,
          songs.size - failures.size,
          failures.size
        )
        reportTagError(failures)
      }
      // 重写文件触发 MediaStore 重扫，歌曲 id 可能变化，同步播放队列
      syncPlayQueueAfterTagSave()
    }
  }

  // -------- 标签写入失败反馈（失败弹窗 + 日志） ----------
  private val _tagErrorState = MutableStateFlow(TagErrorState())
  val tagErrorState = _tagErrorState.asStateFlow()

  fun dismissTagError() {
    _tagErrorState.update { state ->
      state.dialogState.dismiss()
      state.copy()
    }
  }

  /** 标签写入失败：记录到日志文件（LogTree 同时写 logcat 与日志文件）并弹出原因弹窗 */
  private fun reportTagError(failures: List<Pair<Song, Throwable>>) {
    if (failures.isEmpty()) return
    val detail = failures.joinToString("\n\n") { (song, e) ->
      // 格式不支持写入标签时给出明确、可读的提示，而不是整段堆栈
      if (e is SongTagFile.UnsupportedFormatException) {
        context.getString(R.string.tag_unsupported_format, song.showName)
      } else if (e is AudioConverter.ConvertException) {
        context.getString(R.string.convert_failed, song.showName)
      } else {
        "${song.data}\n${e.stackTraceToString()}"
      }
    }
    failures.forEach { (song, e) ->
      Timber.e(e, "write tags failed: ${song.data}")
    }
    _tagErrorState.updateIf(condition = { !it.dialogState.isOpen }) {
      it.dialogState.show()
      it.copy(message = detail)
    }
  }

  override fun onMediaStoreChanged() {
    Timber.v("onMediaStoreChanged, hasPermission: $hasPermission")
    if (hasPermission) {
      fetchMedia()
    }
  }

  override fun onPermissionChanged(has: Boolean) {
    if (has && !hasPermission) {
      fetchMedia()
    }
    hasPermission = has
  }

  override fun onPlayListChanged(name: String) {
  }

  override fun onServiceConnected(service: MusicService) {
  }

  override fun onServiceDisConnected() {
  }

  override fun onTagChanged(
    oldSong: Song?, newSong: Song
  ) {
    fetchMedia(true, updateAlbumVersion = true, updatePlayListVersion = true)
  }
}

data class CreatePlaylistState(
  val dialogState: DialogState = DialogState(),
  val name: String = ""
)

/** 标签写入失败弹窗状态 */
data class TagErrorState(
  val dialogState: DialogState = DialogState(),
  val message: String = ""
)
