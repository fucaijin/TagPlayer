package remix.myplayer.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.taglib.AudioProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.AudioTagFile
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.util.Constants.MB
import remix.myplayer.util.Util
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.settingViewModel
import java.io.File

@Composable
fun SongDetailDialog() {
  val scope = rememberCoroutineScope()
  val libraryVM = libraryViewModel
  val state by settingViewModel.songDetailState.collectAsStateWithLifecycle()
  val song = state.song

  var audioProperties by remember(song) {
    mutableStateOf<AudioProperties?>(null)
  }

  // 歌曲名可编辑：回填当前文件名（不含后缀）
  val initialName = remember(song) { song.displayName.substringBeforeLast('.') }
  var songName by remember(song) { mutableStateOf(initialName) }
  var savedName by remember(song) { mutableStateOf(initialName) }
  var saving by remember(song) { mutableStateOf(false) }
  val canRename = song.isLocal()
  val nameChanged = canRename && songName.isNotBlank() && songName != savedName

  // 同目录重名检测
  var nameConflict by remember(song) { mutableStateOf(false) }
  LaunchedEffect(songName, savedName) {
    nameConflict = if (canRename && songName.isNotBlank() && songName != savedName) {
      libraryVM.isSongNameConflict(song, songName)
    } else {
      false
    }
  }

  NormalDialog(
    dialogState = state.dialogState,
    autoDismiss = false,
    title = stringResource(R.string.song_detail),
    negative = if (nameChanged) stringResource(R.string.save) else null,
    onNegative = {
      scope.launch {
        if (saving) return@launch
        saving = true
        // 二次确认重名：存在冲突则提醒并不执行重命名
        if (libraryVM.isSongNameConflict(song, songName)) {
          nameConflict = true
          saving = false
          MessageNotifier.show(R.string.song_name_conflict)
          return@launch
        }
        val renamed = libraryVM.renameSongFile(song, songName)
        saving = false
        if (renamed != null) {
          songName = renamed
          savedName = renamed
          MessageNotifier.show(R.string.save_success)
        } else {
          MessageNotifier.show(R.string.save_error)
        }
      }
    },
    custom = {
      Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
          .padding(top = 18.dp)
          .verticalScroll(rememberScrollState())
      ) {
        DetailItem(R.string.song_path, song.data, true)
        if (canRename) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailNameField(
              value = songName,
              conflict = nameConflict,
              onValueChange = { songName = it }
            )
            if (nameConflict) {
              Text(
                stringResource(R.string.song_name_conflict),
                fontSize = 13.sp,
                color = colorResource(R.color.md_red_primary)
              )
            }
          }
        } else {
          DetailItem(R.string.song_name, song.showName)
        }
        DetailItem(R.string.file_size, stringResource(R.string.cache_size, 1.0f * song.size / MB))
        DetailItem(
          R.string.format,
          song.data.substringAfterLast('.')
        )
        DetailItem(R.string.length, Util.getTime(song.duration))
        DetailItem(
          R.string.bitrate,
          if (song.isLocal()) "${audioProperties?.bitrate ?: 0} kb/s" else if (song is Song.Remote) "${song.bitRate} kb/s" else ""
        )
        DetailItem(
          R.string.sample_rate,
          if (song.isLocal()) "${audioProperties?.sampleRate ?: 0} Hz" else if (song is Song.Remote) "${song.sampleRate} Hz" else ""
        )
      }
    },
    positive = stringResource(R.string.close),
    onPositive = {
      state.dialogState.dismiss()
    }
  )

  LaunchedEffect(song) {
    if (song.id > 0 && song.isLocal()) {
      try {
        audioProperties = withContext(Dispatchers.IO) {
          AudioTagFile.readAudioProperties(File(song.data))
        }
      } catch (ignore: Exception) {
      }
    }
  }
}

@Composable
private fun DetailNameField(
  value: String,
  conflict: Boolean,
  onValueChange: (String) -> Unit,
) {
  val theme = LocalTheme.current
  val errorColor = colorResource(R.color.md_red_primary)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(R.string.song_name),
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold,
      color = theme.textSecondary
    )
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      cursorBrush = SolidColor(theme.primary),
      textStyle = TextStyle(
        fontSize = 16.sp,
        color = if (conflict) errorColor else theme.textPrimary
      ),
      modifier = Modifier
        .weight(1f)
        .padding(start = 6.dp),
      decorationBox = { innerTextField ->
        Column {
          Box(modifier = Modifier.padding(bottom = 2.dp)) {
            innerTextField()
          }
          HorizontalDivider(
            thickness = 1.dp,
            color = if (conflict) errorColor else theme.textSecondary
          )
        }
      }
    )
  }
}

@Composable
private fun DetailItem(titleRes: Int, content: String, selectable: Boolean = false) {
  Row {
    Text(
      stringResource(titleRes),
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold,
      color = LocalTheme.current.textSecondary
    )
    if (selectable) {
      SelectionContainer {
        Text(
          content,
          fontSize = 16.sp,
          maxLines = Int.MAX_VALUE,
          color = LocalTheme.current.textSecondary
        )
      }
    } else {
      Text(
        content,
        fontSize = 16.sp,
        maxLines = Int.MAX_VALUE,
        color = LocalTheme.current.textSecondary
      )
    }

  }
}
