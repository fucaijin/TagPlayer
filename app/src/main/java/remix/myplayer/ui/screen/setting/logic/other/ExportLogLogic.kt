package remix.myplayer.ui.screen.setting.logic.other

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.misc.log.LogFileWriter
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference

/**
 * 设置-其他-导出日志：将日志目录打包为 zip 导出到用户选择的位置。
 */
@Composable
fun ExportLogLogic() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val uriLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode != Activity.RESULT_OK) {
      return@rememberLauncherForActivityResult
    }
    val uri = result.data?.data ?: return@rememberLauncherForActivityResult
    scope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val zipFile = LogFileWriter.exportZip(context)
            ?: return@withContext false
          val copied = context.contentResolver.openOutputStream(uri)?.use { out ->
            zipFile.inputStream().use { it.copyTo(out) } > 0
          } ?: false
          zipFile.delete()
          copied
        }.getOrDefault(false)
      }
      MessageNotifier.show(if (ok) R.string.export_log_success else R.string.export_log_error)
    }
  }

  NormalPreference(
    stringResource(R.string.export_log),
    stringResource(R.string.export_log_tip)
  ) {
    uriLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
      type = "application/zip"
      addCategory(Intent.CATEGORY_OPENABLE)
      putExtra(Intent.EXTRA_TITLE, "logs.zip")
    })
  }
}
