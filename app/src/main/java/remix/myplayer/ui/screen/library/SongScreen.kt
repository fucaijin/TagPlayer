package remix.myplayer.ui.screen.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote.setPlayQueue
import remix.myplayer.service.MusicServiceRemote.setPlayQueueKeepCurrent
import remix.myplayer.ui.widget.library.SongListHeader
import remix.myplayer.ui.widget.library.TagFilterPanel
import remix.myplayer.ui.widget.library.list.ListSong
import remix.myplayer.util.MusicUtil
import remix.myplayer.util.ext.verticalScrollbar
import remix.myplayer.viewmodel.MultiSelectState
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.playbackViewModel
import remix.myplayer.viewmodel.settingViewModel

@Composable
fun SongScreen(scrollToCurrentEvent: SharedFlow<Unit>? = null) {
  val libraryVM = libraryViewModel
  val mainVM = mainViewModel

  val playbackState by playbackViewModel.playbackUiState.collectAsStateWithLifecycle()
  val multiSelectState by mainVM.multiSelectState.collectAsStateWithLifecycle()
  val settingState by settingViewModel.settingsState.collectAsStateWithLifecycle()
  val songTags by libraryVM.songTags.collectAsStateWithLifecycle()
  val allTags by libraryVM.allTags.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val songs by libraryVM.songs.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val popupEnabled = !multiSelectState.isShowInLibrary()

  // 标签过滤区状态
  var filterExpanded by rememberSaveable { mutableStateOf(false) }
  var filterMatchAll by rememberSaveable { mutableStateOf(true) }
  var filterSearchQuery by rememberSaveable { mutableStateOf("") }
  var selectedFilterTags by remember { mutableStateOf(emptySet<String>()) }

  // 根据所选标签过滤歌曲（与=全部包含 / 或=任一包含）
  val filteredSongs = remember(songs, songTags, selectedFilterTags, filterMatchAll) {
    if (selectedFilterTags.isEmpty()) {
      songs
    } else {
      songs.filter { song ->
        val tags = songTags[song.data] ?: emptySet()
        if (filterMatchAll) {
          selectedFilterTags.all { it in tags }
        } else {
          selectedFilterTags.any { it in tags }
        }
      }
    }
  }

  // 标签过滤变化后，将过滤结果同步为播放队列（保持当前歌曲不中断播放），
  // 这样后续的下一首/顺序/随机/单曲循环都以过滤后的列表为播放列表。
  var lastFilter by remember { mutableStateOf<Pair<Set<String>, Boolean>?>(null) }
  LaunchedEffect(selectedFilterTags, filterMatchAll) {
    val currentFilter = selectedFilterTags to filterMatchAll
    val previous = lastFilter
    lastFilter = currentFilter
    // 首次组合不触发，避免覆盖恢复/已有的播放队列
    if (previous == null) {
      return@LaunchedEffect
    }
    // 仅在“有标签过滤”或“从有过滤变为取消全部标签”时同步队列；
    // 无标签时单纯切换“与/或”不改变列表，无需同步
    if (selectedFilterTags.isEmpty() && previous.first.isEmpty()) {
      return@LaunchedEffect
    }
    if (filteredSongs.isNotEmpty()) {
      setPlayQueueKeepCurrent(filteredSongs)
    }
  }

  LaunchedEffect(scrollToCurrentEvent) {
    scrollToCurrentEvent?.collect {
      val index = libraryVM.songs.value.indexOfFirst { it.id == playbackState.song.id }
      if (index != -1) {
        listState.scrollToItem(index)
      }
    }
  }

  Column {
    TagFilterPanel(
      allTags = allTags,
      selectedTags = selectedFilterTags,
      matchAll = filterMatchAll,
      searchQuery = filterSearchQuery,
      expanded = filterExpanded,
      onSearchQueryChange = { filterSearchQuery = it },
      onMatchAllChange = { filterMatchAll = it },
      onToggleTag = { tag ->
        selectedFilterTags = if (tag in selectedFilterTags) {
          selectedFilterTags - tag
        } else {
          selectedFilterTags + tag
        }
      },
      onManageClick = { libraryVM.showTagManageDialog() },
      onExpandChange = { filterExpanded = it }
    )

    if (filteredSongs.isNotEmpty()) {
      SongListHeader(filteredSongs)
    }

    val selectedIds by remember {
      derivedStateOf {
        multiSelectState.selectedModels(MultiSelectState.Where.Song)
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .verticalScrollbar(listState)
    ) {
      itemsIndexed(filteredSongs, key = { _, song ->
        song.id
      }) { pos, song ->
        val selected = selectedIds.contains(song.getKey())
        val isPlayingSong = playbackState.song.id == song.id

        ListSong(
          modifier = Modifier.height(64.dp),
          song = song,
          modelParent = song,
          selected = selected,
          playing = isPlayingSong,
          popupEnabled = popupEnabled,
          num = if (settingState.list.showNumber) pos + 1 else null,
          showArtistAlbum = settingState.list.showArtistAlbum,
          showTags = settingState.list.showTag,
          tags = songTags[song.data] ?: emptySet(),
          onManageTags = if (settingState.list.tagManage) {
            { libraryVM.showSongTagManageDialog(song) }
          } else {
            null
          },
          onClickSong = {
            if (filteredSongs.isEmpty()) {
              return@ListSong
            }

            if (multiSelectState.where == MultiSelectState.Where.Song) {
              mainVM.updateMultiSelectModel(song)
              return@ListSong
            }

            setPlayQueue(
              filteredSongs, MusicUtil.makeCmdIntent(Command.PLAY_AT)
                .putExtra(MusicService.EXTRA_POSITION, pos)
            )
          },
          onLongClickSong = {
            mainVM.showMultiSelect(context, MultiSelectState.Where.Song, song)
          })
      }
    }
  }
}
