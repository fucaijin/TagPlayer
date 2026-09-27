package remix.myplayer.ui.screen.setting.logic.tag

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.repo.ExportMode
import remix.myplayer.repo.TagImportSummary
import remix.myplayer.ui.dialog.DialogState
import remix.myplayer.ui.dialog.ItemsCallbackMultiChoice
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.util.MetaField
import remix.myplayer.viewmodel.libraryViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** 导出/导入可选用的元数据字段（不含 year/comment/track） */
private val EXPORT_META_FIELDS = listOf(MetaField.TITLE, MetaField.ARTIST, MetaField.ALBUM, MetaField.GENRE)

/**
 * 歌曲标签导出/导入：经系统文件选择器读写 JSON。
 * 导出分「文件名-标签」「元数据-标签」两个入口，文件头部记录 mode 与 fields，
 * 导入时按同样的方式在本机曲库中匹配歌曲。
 */
@Composable
fun SongTagIOLogic() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val libraryVM = libraryViewModel

  // 元数据导出时用户勾选的字段（默认 Title + Artist）
  var metaFields by remember { mutableStateOf(listOf(MetaField.TITLE, MetaField.ARTIST)) }
  var pendingExport by remember { mutableStateOf<Pair<ExportMode, List<MetaField>>?>(null) }

  val metaDialog = rememberDialogState()
  val resultDialog = rememberDialogState()
  var resultSummary by remember { mutableStateOf<TagImportSummary?>(null) }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
    val uri = result.data?.data ?: return@rememberLauncherForActivityResult
    val (mode, fields) = pendingExport ?: return@rememberLauncherForActivityResult
    pendingExport = null
    scope.launch(Dispatchers.IO) {
      runCatching {
        val json = libraryVM.exportTagsJson(mode, fields)
        context.contentResolver.openOutputStream(uri)?.use { os ->
          os.write(json.toByteArray(StandardCharsets.UTF_8))
        } ?: throw IllegalStateException("openOutputStream failed")
      }.onSuccess {
        withContext(Dispatchers.Main) { MessageNotifier.show(R.string.tag_export_success) }
      }.onFailure {
        withContext(Dispatchers.Main) {
          MessageNotifier.show(R.string.import_fail, it.message ?: it.toString())
        }
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
    val uri = result.data?.data ?: return@rememberLauncherForActivityResult
    scope.launch(Dispatchers.IO) {
      val json = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
        } ?: throw IllegalStateException("openInputStream failed")
      }.getOrElse {
        withContext(Dispatchers.Main) { MessageNotifier.show(R.string.import_fail, it.message ?: it.toString()) }
        return@launch
      }
      val summary = libraryVM.importTagsJson(json)
      withContext(Dispatchers.Main) {
        if (summary.error != null) {
          MessageNotifier.show(R.string.import_fail, summary.error)
        } else {
          resultSummary = summary
          resultDialog.show()
        }
      }
    }
  }

  NormalPreference(
    stringResource(R.string.tag_export_filename),
    stringResource(R.string.tag_export_filename_tip)
  ) {
    pendingExport = ExportMode.FILENAME to emptyList()
    exportLauncher.launch(
      Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "application/json"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, "song_tags_filename.json")
      }
    )
  }

  NormalPreference(
    stringResource(R.string.tag_export_metadata),
    stringResource(R.string.tag_export_metadata_tip)
  ) {
    metaDialog.show()
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

  // 元数据字段选择弹窗
  MetaFieldSelectDialog(
    dialogState = metaDialog,
    selected = metaFields,
    onConfirm = { fields ->
      metaFields = fields
      pendingExport = ExportMode.METADATA to fields
      exportLauncher.launch(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
          type = "application/json"
          addCategory(Intent.CATEGORY_OPENABLE)
          putExtra(Intent.EXTRA_TITLE, "song_tags_metadata.json")
        }
      )
    }
  )

  // 导入结果弹窗（含复制按钮）
  ImportResultDialog(
    dialogState = resultDialog,
    summary = resultSummary,
    onDismiss = { resultSummary = null }
  )
}

/**
 * 元数据字段选择弹窗，复用统一的 NormalDialog 样式：
 * 标题 + 说明 + 多选列表 + 取消/确定按钮，深色/浅色主题下均清晰可读。
 */
@Composable
private fun MetaFieldSelectDialog(
  dialogState: DialogState,
  selected: List<MetaField>,
  onConfirm: (List<MetaField>) -> Unit
) {
  val fields = EXPORT_META_FIELDS
  // 每次重新打开弹窗时，以外部保存的选择为准初始化
  var current by remember(dialogState.isOpen) { mutableStateOf(selected.toSet()) }
  NormalDialog(
    dialogState = dialogState,
    autoDismiss = false,
    title = stringResource(R.string.tag_export_metadata_fields),
    content = stringResource(R.string.tag_export_metadata_fields_hint),
    items = fields.map { it.zhLabel },
    itemsCallbackMultiChoice = ItemsCallbackMultiChoice(
      selectedIndices = fields.mapIndexedNotNull { index, field -> index.takeIf { field in current } }.toSet(),
      onCheckChange = { index, checked ->
        val field = fields[index]
        current = if (checked) current + field else current - field
      }
    ),
    positive = stringResource(R.string.confirm),
    onPositive = {
      if (current.isEmpty()) {
        MessageNotifier.show(R.string.tag_export_metadata_fields_empty)
      } else {
        dialogState.dismiss()
        onConfirm(current.toList())
      }
    },
    negative = stringResource(R.string.cancel),
    onNegative = { dialogState.dismiss() }
  )
}

/**
 * 导入结果弹窗，复用统一的 NormalDialog 样式：
 * 标题 + 统计与失败/未匹配列表（可滚动）+ 复制/确定按钮。
 */
@Composable
private fun ImportResultDialog(
  dialogState: DialogState,
  summary: TagImportSummary?,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  NormalDialog(
    dialogState = dialogState,
    autoDismiss = false,
    title = stringResource(R.string.tag_import_result_title),
    positive = stringResource(R.string.confirm),
    onPositive = {
      dialogState.dismiss()
      onDismiss()
    },
    neutral = stringResource(R.string.tag_import_copy),
    onNeutral = { summary?.let { copyText(context, it.reportText()) } },
    negative = null,
    custom = {
      if (summary != null) {
        TextPrimary(
          stringResource(R.string.tag_import_summary, summary.imported, summary.failed, summary.missing),
          fontSize = 15.sp,
          maxLine = Int.MAX_VALUE
        )
        ItemList(R.string.tag_import_failed_list, summary.failedItems)
        ItemList(R.string.tag_import_missing_list, summary.missingItems)
      }
    }
  )
}

/** 弹窗中的条目列表（深色主题下同样清晰，避免次级色过暗看不清） */
@Composable
private fun ItemList(titleRes: Int, items: List<String>) {
  if (items.isEmpty()) {
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Spacer(Modifier.height(8.dp))
    TextPrimary(
      stringResource(titleRes),
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      maxLine = Int.MAX_VALUE
    )
    items.forEach { item ->
      TextPrimary(item, fontSize = 13.sp, maxLine = Int.MAX_VALUE)
    }
  }
}

private fun copyText(context: Context, text: String) {
  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  cm.setPrimaryClip(ClipData.newPlainText("tag_import_report", text))
  MessageNotifier.show(R.string.tag_import_copied)
}
