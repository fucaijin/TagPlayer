@file:OptIn(ExperimentalMaterial3Api::class)

package remix.myplayer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.app.MultiSelectBar
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.ui.widget.library.list.ListSong
import remix.myplayer.util.MusicUtil
import remix.myplayer.util.Util
import remix.myplayer.util.ext.clickableWithoutRipple
import remix.myplayer.viewmodel.MultiSelectState
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.playbackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
  val libraryVM = libraryViewModel
  val mainVM = mainViewModel

  val playbackState by playbackViewModel.playbackUiState.collectAsStateWithLifecycle()
  val multiSelectState by mainVM.multiSelectState.collectAsStateWithLifecycle()
  val librarySongs by libraryVM.songs.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  var songs by remember {
    mutableStateOf(emptyList<Song>())
  }
  var isLoading by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val showMultiSelect = multiSelectState.isShowInSearch()
  val popupEnabled = !showMultiSelect

  BackHandler(showMultiSelect) {
    mainVM.closeMultiSelect()
  }

  var searchKey by rememberSaveable {
    mutableStateOf("")
  }
  // 提升到此处，便于点击搜索记录时回填输入框
  val textFieldState = rememberTextFieldState(initialText = searchKey)

  val searchHistories by libraryVM.searchHistories.collectAsStateWithLifecycle()

  // 本次进入搜索页已入库的关键词，避免同一关键词重复计数
  val recordedKeywords = remember { mutableSetOf<String>() }

  /** 记录一次搜索（同一次进入搜索页内同一关键词只记一次） */
  fun recordSearchOnce(keyword: String) {
    val key = keyword.trim()
    if (key.isEmpty() || !recordedKeywords.add(key)) return
    libraryVM.recordSearch(key)
  }

  val latestSearchKey by rememberUpdatedState(searchKey)
  // 离开搜索页时把当前关键词入库，避免用户只输入而未按键盘“搜索”时没有记录
  DisposableEffect(Unit) {
    onDispose { recordSearchOnce(latestSearchKey) }
  }

  Scaffold(
    containerColor = LocalTheme.current.mainBackground,
    topBar = {
      AnimatedContent(
        targetState = showMultiSelect,
        transitionSpec = {
          if (targetState) {
            slideInVertically() togetherWith slideOutVertically { height -> height / 2 }
          } else {
            slideInVertically { height -> height } togetherWith slideOutVertically()
          }
        }
      ) { isMultiSelect ->
        if (!isMultiSelect) {
          SongSearchBar(textFieldState) { query, submitted ->
            searchKey = query
            // 点键盘"搜索"即入库；不点击时在离开搜索页时入库
            if (submitted) {
              recordSearchOnce(query)
            }
          }
        } else {
          MultiSelectBar(
            state = multiSelectState,
            scrollBehavior = null,
            onSelectAll = {
              mainVM.updateMultiSelectModelsAll(songs)
            }
          )
        }
      }
    }
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter
    ) {
      if (isLoading) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter),
          color = LocalTheme.current.primary
        )
      } else if (searchKey.isBlank()) {
        // 未输入时展示搜索记录，点击可直接搜索
        SearchHistoryList(
          histories = searchHistories,
          onClickHistory = { keyword ->
            textFieldState.edit { replace(0, length, keyword) }
            searchKey = keyword
            recordSearchOnce(keyword)
          },
          onDeleteHistory = { libraryVM.removeSearchHistory(it) }
        )
      } else if (songs.isEmpty()) {
        TextSecondary(
          modifier = Modifier.padding(top = 64.dp),
          text = stringResource(R.string.no_search_result), fontSize = 16.sp
        )
      } else {
        val selectedIds by remember {
          derivedStateOf {
            multiSelectState.selectedModels(MultiSelectState.Where.Search)
          }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
          itemsIndexed(songs, key = { _, song ->
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
              onClickSong = {
                if (songs.isEmpty()) {
                  return@ListSong
                }

                if (multiSelectState.where == MultiSelectState.Where.Search) {
                  mainVM.updateMultiSelectModel(song)
                  return@ListSong
                }

                recordSearchOnce(searchKey)

                Util.sendLocalBroadcast(
                  MusicUtil.makeCmdIntent(Command.PLAY_TEMP)
                    .putExtra(MusicService.EXTRA_SONG, song)
                )
              },
              onLongClickSong = {
                mainVM.showMultiSelect(context, MultiSelectState.Where.Search, song)
              })
          }
        }
      }

    }
  }

  LaunchedEffect(searchKey, librarySongs) {
    if (searchKey.isEmpty()) {
      songs = emptyList()
      isLoading = false
      return@LaunchedEffect
    }
    isLoading = true
    delay(300)
    val result = withContext(Dispatchers.IO) {
      libraryVM.searchSong(searchKey)
    }
    songs = result
    isLoading = false
  }
}

@Composable
private fun SongSearchBar(textFieldState: TextFieldState, onSearch: (String, Boolean) -> Unit) {
  val theme = LocalTheme.current

  CommonAppBar(null, true) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 64.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val query = textFieldState.text.toString()
      val interactionSource = remember { MutableInteractionSource() }
      val fontSize = 16.sp

      BasicTextField(
        value = query,
        onValueChange = {
          textFieldState.edit { replace(0, length, it) }
          onSearch(textFieldState.text.toString(), false)
        },
        modifier = Modifier.fillMaxSize(),
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
          onSearch(query, true)
        }),
        interactionSource = interactionSource,
        decorationBox =
          @Composable { innerTextField ->
            TextFieldDefaults.DecorationBox(
              value = query,
              innerTextField = innerTextField,
              enabled = true,
              singleLine = true,
              visualTransformation = VisualTransformation.None,
              interactionSource = interactionSource,
              placeholder = {
                TextPrimary(stringResource(R.string.search_hint), fontSize = fontSize)
              },
              trailingIcon = {
                if (query.isNotEmpty()) {
                  IconButton(onClick = {
                    textFieldState.clearText()
                    onSearch("", false)
                  }) {
                    Icon(
                      modifier = Modifier.size(20.dp),
                      imageVector = Icons.Filled.Clear,
                      tint = theme.textPrimary,
                      contentDescription = "SearchClear"
                    )
                  }
                }
              },
              shape = SearchBarDefaults.inputFieldShape,
              colors = TextFieldDefaults.colors().copy(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                cursorColor = theme.primary,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                focusedIndicatorColor = theme.primary,
                unfocusedIndicatorColor = theme.primary
              ),
              contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
              container = {},
            )
          }
      )
    }
  }
}

/**
 * 搜索记录列表：未输入搜索词时展示，点击某条直接搜索，右侧按钮删除单条。
 * 全部清空入口在 设置-其他-"清空搜索记录"。
 */
@Composable
private fun SearchHistoryList(
  histories: List<String>,
  onClickHistory: (String) -> Unit,
  onDeleteHistory: (String) -> Unit,
) {
  if (histories.isEmpty()) {
    return
  }
  val theme = LocalTheme.current

  Column(modifier = Modifier.fillMaxSize()) {
    TextSecondary(
      text = stringResource(R.string.search_history),
      fontSize = 13.sp,
      modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
      items(histories) { keyword ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickableWithoutRipple { onClickHistory(keyword) }
            .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_history_24dp),
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(20.dp)
          )
          TextPrimary(
            keyword,
            fontSize = 15.sp,
            modifier = Modifier
              .weight(1f)
              .padding(start = 12.dp)
          )
          IconButton(onClick = { onDeleteHistory(keyword) }) {
            Icon(
              modifier = Modifier.size(18.dp),
              imageVector = Icons.Filled.Clear,
              tint = theme.textSecondary,
              contentDescription = stringResource(R.string.delete)
            )
          }
        }
      }
    }
  }
}
