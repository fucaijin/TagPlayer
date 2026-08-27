package remix.myplayer.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import remix.myplayer.R
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote.setPlayQueue
import remix.myplayer.service.MusicServiceRemote.setPlayQueueKeepCurrent
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.icon
import remix.myplayer.ui.widget.library.TagFilterPanel
import remix.myplayer.ui.widget.library.list.ListSong
import remix.myplayer.util.MusicUtil
import remix.myplayer.util.ext.clickableWithoutRipple
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

  // "无标签"是过滤区的特殊默认标签：选中它表示过滤出未设置任何标签的歌曲
  val noTagLabel = stringResource(R.string.tag_no_tag)

  // 根据所选标签过滤歌曲（与=全部包含 / 或=任一包含；"无标签"特殊处理）
  val filteredSongs = remember(songs, songTags, selectedFilterTags, filterMatchAll, noTagLabel) {
    if (selectedFilterTags.isEmpty()) {
      songs
    } else {
      val noTagSelected = noTagLabel in selectedFilterTags
      val otherTags = selectedFilterTags - noTagLabel
      songs.filter { song ->
        val tags = songTags[song.data] ?: emptySet()
        val isNoTag = tags.isEmpty()
        val matchOthers = if (filterMatchAll) {
          otherTags.all { it in tags }
        } else {
          otherTags.any { it in tags }
        }
        when {
          // 未选"无标签"：只按其他标签过滤
          !noTagSelected -> matchOthers
          // 只选了"无标签"：过滤出所有未设置任何标签的歌曲
          otherTags.isEmpty() -> isNoTag
          // "无标签" + 其他标签：
          // 与模式要求同时满足（无标签与含其他标签互斥，通常无结果）
          filterMatchAll -> isNoTag && matchOthers
          // 或模式：无标签 或 命中其他标签
          else -> isNoTag || matchOthers
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
    // 仅在"有标签过滤"或"从有过滤变为取消全部标签"时同步队列；
    // 无标签时单纯切换"与/或"不改变列表，无需同步
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
    // 头部共享一行：左侧=随机播放全部，右侧=标签过滤 + 箭头（点击展开/收起过滤区）
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .background(LocalTheme.current.mainBackground),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 左侧区域：随机播放全部
      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickableWithoutRipple(remember { MutableInteractionSource() }) {
            if (filteredSongs.isEmpty()) {
              MessageNotifier.show(R.string.no_song)
            } else {
              setPlayQueue(filteredSongs, MusicUtil.makeCmdIntent(Command.SKIP_TO_NEXT, true))
            }
          },
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          modifier = Modifier.padding(start = 16.dp, end = 8.dp),
          painter = painterResource(R.drawable.ic_shuffle_white_24dp),
          tint = LocalTheme.current.secondary,
          contentDescription = "ListHeaderIcon"
        )
        Text(
          text = stringResource(R.string.play_random, filteredSongs.size),
          color = LocalTheme.current.textSecondary
        )
      }

      // 右侧区域：标签过滤 + 箭头按钮
      Row(
        modifier = Modifier
          .fillMaxHeight()
          .clickableWithoutRipple(remember { MutableInteractionSource() }) {
            filterExpanded = !filterExpanded
          }
          .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.tag_filter),
          color = LocalTheme.current.textSecondary
        )
        Icon(
          painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
          contentDescription = stringResource(if (filterExpanded) R.string.collapse else R.string.expand),
          tint = LocalTheme.current.icon(),
          modifier = Modifier
            .size(20.dp)
            .rotate(if (filterExpanded) -90f else 90f)
        )
      }
    }

    // 过滤区展开时显示在头部行下方
    if (filterExpanded) {
      TagFilterPanel(
        allTags = allTags + noTagLabel,
        selectedTags = selectedFilterTags,
        matchAll = filterMatchAll,
        searchQuery = filterSearchQuery,
        onSearchQueryChange = { filterSearchQuery = it },
        onMatchAllChange = { filterMatchAll = it },
        onToggleTag = { tag ->
          selectedFilterTags = if (tag in selectedFilterTags) {
            selectedFilterTags - tag
          } else {
            selectedFilterTags + tag
          }
        },
        onManageClick = { libraryVM.showTagManageDialog() }
      )
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
