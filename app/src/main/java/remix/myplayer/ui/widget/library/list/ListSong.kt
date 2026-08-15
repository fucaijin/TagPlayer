package remix.myplayer.ui.widget.library.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import remix.myplayer.R
import remix.myplayer.data.model.audio.APlayerModel
import remix.myplayer.data.model.audio.Song
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.highLightText
import remix.myplayer.ui.theme.popupButton
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.ui.widget.library.GlideCover
import remix.myplayer.ui.widget.popup.SongPopupButton
import remix.myplayer.util.ext.clickWithRipple

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ListSong(
  modifier: Modifier = Modifier,
  song: Song,
  modelParent: APlayerModel,
  selected: Boolean,
  playing: Boolean,
  popupEnabled: Boolean = true,
  onClickSong: () -> Unit,
  onLongClickSong: () -> Unit,
  num: Int? = null,
  showArtistAlbum: Boolean = true,
  showTags: Boolean = false,
  tags: Set<String> = emptySet(),
  onManageTags: (() -> Unit)? = null,
) {
  val theme = LocalTheme.current

  Box(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = theme.ripple),
        onClick = { onClickSong() },
        onLongClick = { onLongClickSong() }
      )
      .background(if (selected) theme.select else theme.mainBackground),
    contentAlignment = Alignment.CenterStart
  ) {
    if (playing) {
      Box(
        modifier = Modifier
          .width(4.dp)
          .fillMaxHeight()
          .padding(vertical = 8.dp)
          .background(theme.highLightText())
      )
    }

    Row(
      modifier = modifier
        .fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (num != null) {
        TextPrimary(
          if (num > 999) "999+" else num.toString(),
          textAlign = TextAlign.Center,
          modifier = Modifier
            .width(40.dp)
            .padding(horizontal = 4.dp)
        )
      } else {
        Spacer(modifier = Modifier.width(16.dp))
      }

//      Box(modifier = Modifier
//        .background(Color.Red)
//        .size(40.dp))
      GlideCover(
        model = song,
        modifier = Modifier
          .size(40.dp)
      )

      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
          .weight(1f)
          .padding(start = 16.dp, end = 8.dp)
      ) {
        TextPrimary(song.showName)
        if (showArtistAlbum) {
          Spacer(modifier = Modifier.height(4.dp))
          TextSecondary(String.format("%s-%s", song.artist, song.album))
        }
        if (showTags && tags.isNotEmpty()) {
          Spacer(modifier = Modifier.height(2.dp))
          TextSecondary(
            tags.joinToString(" ") { "#$it" },
            fontSize = 12.sp
          )
        }
      }

      if (onManageTags != null) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .clickWithRipple {
              onManageTags()
            }
            .size(dimensionResource(id = R.dimen.item_list_btn_size))
        ) {
          Image(
            painter = painterResource(R.drawable.ic_star_24dp),
            contentDescription = "manage tags",
            colorFilter = ColorFilter.tint(LocalTheme.current.popupButton())
          )
        }
      }

      SongPopupButton(
        modifier = Modifier,
        song = song,
        parent = modelParent,
        enabled = popupEnabled
      )
    }
  }

}
