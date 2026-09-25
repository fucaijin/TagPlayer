package remix.myplayer.ui.dialog

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.util.Util

/**
 * 二维码弹窗：展示一张二维码图片，并提供“保存到本地”按钮（写入系统相册）。
 *
 * @param qrResId 二维码图片的 drawable 资源 id
 * @param saveFileName 保存到相册时的文件名（含扩展名）
 */
@Composable
fun QrCodeDialog(
  dialogState: DialogState,
  title: String,
  qrResId: Int,
  saveFileName: String,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()
  val theme = LocalTheme.current

  BaseDialog(
    show = dialogState.isOpen,
    onDismissRequest = { dialogState.dismiss() }
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      TextPrimary(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(16.dp))

      Image(
        painter = painterResource(qrResId),
        contentDescription = title,
        modifier = Modifier
          .fillMaxWidth(0.72f)
          .clip(RoundedCornerShape(8.dp))
      )

      Spacer(modifier = Modifier.height(16.dp))

      if (actionText != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(50),
            color = theme.secondary,
            onClick = {
              (activity as? Activity)?.let {
                scope.launch { Util.saveToAlbum(it, qrResId, saveFileName) }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            TextPrimary(
              text = stringResource(R.string.save_to_local),
              fontSize = 14.sp,
              color = theme.primaryReverse,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
            )
          }

          Surface(
            shape = RoundedCornerShape(50),
            color = theme.dialogBackground,
            border = BorderStroke(1.dp, theme.secondary),
            onClick = { onAction?.invoke() },
            modifier = Modifier.weight(1f)
          ) {
            TextPrimary(
              text = actionText,
              fontSize = 14.sp,
              color = theme.secondary,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
            )
          }
        }
      } else {
        Surface(
          shape = RoundedCornerShape(50),
          color = theme.secondary,
          onClick = {
            (activity as? Activity)?.let {
              scope.launch { Util.saveToAlbum(it, qrResId, saveFileName) }
            }
          }
        ) {
          TextPrimary(
            text = stringResource(R.string.save_to_local),
            fontSize = 14.sp,
            color = theme.primaryReverse,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
          )
        }
      }
    }
  }
}
