package remix.myplayer.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.viewmodel.libraryViewModel

/**
 * 标签写入失败弹窗：展示具体报错原因，支持一键复制。
 */
@Composable
fun TagErrorDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.tagErrorState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  NormalDialog(
    dialogState = state.dialogState,
    titleRes = R.string.tag_error_title,
    positiveRes = R.string.confirm,
    onPositive = { libraryVM.dismissTagError() },
    negativeRes = null,
    onNegative = null,
    neutralRes = R.string.copy,
    onNeutral = {
      copyText(context, state.message)
      MessageNotifier.show(R.string.copied)
    },
    custom = {
      TextPrimary(stringResource(R.string.tag_error_tip), fontSize = 13.sp)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 220.dp)
          .verticalScroll(rememberScrollState())
          .padding(top = 8.dp)
      ) {
        Text(
          text = state.message.ifEmpty { stringResource(R.string.tag_error_title) },
          fontSize = 12.sp,
          color = LocalTheme.current.textSecondary
        )
      }
    }
  )
}

private fun copyText(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("tag_error", text))
}
