package remix.myplayer.ui.screen.setting.logic.common

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
import remix.myplayer.R
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.libraryViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** 歌曲标签导出/导入：经系统文件选择器读写 JSON（path -> tags） */
@Composable
fun SongTagIOLogic() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val libraryVM = libraryViewModel

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val uri = result.data?.data ?: return@rememberLauncherForActivityResult
      scope.launch(Dispatchers.IO) {
        runCatching {
          val json = libraryVM.exportTagsJson()
          context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.toByteArray(StandardCharsets.UTF_8))
          } ?: throw IllegalStateException("openOutputStream failed")
        }.onSuccess {
          MessageNotifier.show(R.string.tag_export_success)
        }.onFailure {
          MessageNotifier.show(R.string.import_fail, it.message ?: it.toString())
        }
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val uri = result.data?.data ?: return@rememberLauncherForActivityResult
      scope.launch(Dispatchers.IO) {
        val json = runCatching {
          context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
          } ?: throw IllegalStateException("openInputStream failed")
        }.getOrElse {
          MessageNotifier.show(R.string.import_fail, it.message ?: it.toString())
          return@launch
        }
        val summary = libraryVM.importTagsJson(json)
        if (summary.error != null) {
          MessageNotifier.show(R.string.import_fail, summary.error)
        } else {
          MessageNotifier.show(
            R.string.tag_import_summary,
            summary.imported,
            summary.failed,
            summary.missing
          )
        }
      }
    }
  }

  NormalPreference(
    stringResource(R.string.tag_export),
    stringResource(R.string.tag_export_tip)
  ) {
    exportLauncher.launch(
      Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "application/json"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, "song_tags.json")
      }
    )
  }

  NormalPreference(
    stringResource(R.string.tag_import),
    stringResource(R.string.tag_import_tip)
  ) {
    importLauncher.launch(
      Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "application/json"
        addCategory(Intent.CATEGORY_OPENABLE)
      }
    )
  }
}
