package remix.myplayer.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.AudioTagWriter
import remix.myplayer.misc.log.LogFileWriter
import remix.myplayer.ui.dialog.ManageStorageDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.AppTheme
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.highLightText
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.BatchRenameUtil
import remix.myplayer.util.MetaField
import remix.myplayer.util.PermissionUtil.canWriteAudioFiles
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.util.fileNameWithoutExtension
import remix.myplayer.viewmodel.libraryViewModel
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val MODE_META = 0 // 根据元数据修改文件名
private const val MODE_FILE = 1 // 根据文件名修改元数据
private const val MODE_MANUAL_RENAME = 2 // 手动修改文件名
private const val MODE_MANUAL_META = 3 // 手动修改元数据

/** 打开批量重命名时预填的默认模板（占位符：{title}{artist}{album}{track}{year}） */
private const val DEFAULT_RENAME_TEMPLATE = "{artist} - {title}"

/** 手动改元数据模式可选（且默认可选）的元数据字段，不含 track */
private val EDITABLE_META_FIELDS = listOf(
  MetaField.TITLE, MetaField.ARTIST, MetaField.ALBUM,
  MetaField.GENRE, MetaField.YEAR, MetaField.COMMENT
)

private enum class Phase { IDLE, SAVING, DONE }
private enum class CellColor { GRAY, GREEN, RED }

/** 单元格内边距（水平） */
private val CELL_PADDING = 6.dp

/** 单元格纵向内边距：与文字行高一起决定行高 */
private val CELL_VERTICAL_PADDING = 7.dp

/** 表格统一字号：表头与单元格一致，便于统一测量文本宽度 */
private val CELL_FONT_SIZE = 13.sp

/** 单元格内操作按钮（复制 / 清空）预留宽度与间距 */
private val CELL_ACTION_WIDTH = 44.dp
private val CELL_ACTION_GAP = 4.dp

/** 列宽取值区间：按内容测出宽度后裁剪，避免过窄或过宽 */
private val COLUMN_MIN_WIDTH = 56.dp
private val COLUMN_MAX_WIDTH = 300.dp

/** 内容宽度之外额外预留的宽度余量（字体测量误差、光标占位等） */
private val COLUMN_WIDTH_EXTRA = 8.dp

/**
 * 表格列：
 * [measureValues] 是参与宽度测量的原始文本（切换模式 / 修改格式后重新生成，编辑单元格时不变，避免输入过程中列宽抖动）；
 * [hasAction] 表示该列单元格内带有操作按钮，需要额外预留按钮宽度；
 * [content] 渲染该列第 row 行的单元格，宽度为动态计算出的列宽。
 */
private data class TableColumn(
  val title: String,
  val measureValues: List<String>,
  val hasAction: Boolean = false,
  val content: @Composable (row: Int, width: Dp) -> Unit
) {
  /** 列宽 = 该列（表头 + 所有单元格）最长文本的宽度 + 内边距 / 按钮预留 + 余量 */
  fun measureWidth(measureText: (String) -> Dp): Dp {
    val textWidth = (sequenceOf(title) + measureValues).maxOf { measureText(it) }
    val actionWidth = if (hasAction) CELL_ACTION_WIDTH + CELL_ACTION_GAP else 0.dp
    val total = textWidth + CELL_PADDING * 2 + actionWidth + COLUMN_WIDTH_EXTRA
    return total.coerceIn(COLUMN_MIN_WIDTH, COLUMN_MAX_WIDTH)
  }
}

private data class SongMeta(
  val song: Song,
  val meta: Map<String, String>,
  val oldName: String
)

private data class SaveResult(val ok: Int, val fail: Int, val skipped: Int)

/** 保存日志级别 */
private const val LOG_INFO = "INFO"
private const val LOG_WARN = "WARN"
private const val LOG_ERROR = "ERROR"

/** 批量保存日志统一 tag，便于在日志文件中检索 */
private const val LOG_TAG = "BatchRenameSave"

/** 导出报告 / 报告文件名的时间格式（仅在保存协程的单线程中使用） */
private val REPORT_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private val REPORT_FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

/** 日志目录中保留的批量保存报告份数 */
private const val MAX_REPORT_FILES = 10

/** 单条保存日志：同时写入 Timber（落日志文件）与页面内的日志列表（可导出） */
private data class SaveLogLine(val time: String, val level: String, val message: String)

/** 单首歌曲的失败原因，用于页面展示与导出排查 */
private data class SaveFailure(
  val displayName: String,
  val path: String,
  val reason: FailReason,
  /** 技术细节（路径、字段、异常堆栈等），直接写进导出日志 */
  val detail: String? = null
)

/** 失败原因：code 用于日志检索，res 为面向用户的文案 */
private enum class FailReason(val code: String, @StringRes val res: Int) {
  NOT_LOCAL("NOT_LOCAL", R.string.batch_rename_fail_not_local),
  SRC_MISSING("SRC_MISSING", R.string.batch_rename_fail_src_missing),
  TARGET_EXISTS("TARGET_EXISTS", R.string.batch_rename_fail_target_exists),
  RENAME_FAILED("RENAME_FAILED", R.string.batch_rename_fail_rename_failed),
  WRITE_TAG_FAILED("WRITE_TAG_FAILED", R.string.batch_rename_fail_write_tag_failed),
  EXCEPTION("EXCEPTION", R.string.batch_rename_fail_exception)
}

/** 本次保存的概要信息，导出日志时作为头部 */
private data class SaveSummary(
  val time: String,
  val mode: String,
  val rule: String,
  val total: Int,
  val ok: Int,
  val fail: Int,
  val skipped: Int,
  val elapsedMs: Long
)

private sealed interface UndoItem {
  data class Rename(val oldPath: String, val newPath: String) : UndoItem
  data class Tags(val song: Song, val oldValues: Map<String, String>) : UndoItem
}

/** 手动编辑单元格的撤销记录（操作前的值） */
private data class EditUndo(
  val row: Int,
  val kind: Int,
  val field: MetaField?,
  val prev: String
)

/**
 * 批量修改页面（四种模式）：
 * 1) 根据元数据修改文件名；2) 根据文件名修改元数据；
 * 3) 手动修改文件名（原文件名 + 复制，新文件名输入 + 清空）；
 * 4) 手动修改元数据（可选列，标题必填，每列可清空）。
 * 保存时显示进度条、可随时取消，并记录操作前信息以便撤销（回退）。
 * 顶部“撤销”用于回退手动填写的单元格（仅本次会话，退出页面即销毁）。
 */
@Composable
fun BatchRenameScreen() {
  val libraryVM = libraryViewModel
  val state by libraryVM.batchRenameState.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  val nav = LocalNavController.current
  val theme = LocalTheme.current

  // 模式名（写入日志时使用）；stringResource 不能在 remember 的 lambda 中调用，故每次组合时取值
  val modeNames = mapOf(
    MODE_META to stringResource(R.string.batch_rename_mode_meta),
    MODE_FILE to stringResource(R.string.batch_rename_mode_file),
    MODE_MANUAL_RENAME to stringResource(R.string.batch_rename_mode_manual),
    MODE_MANUAL_META to stringResource(R.string.batch_rename_mode_meta_edit)
  )

  var mode by remember { mutableStateOf(MODE_META) }
  var template by remember { mutableStateOf(DEFAULT_RENAME_TEMPLATE) }
  var rule by remember { mutableStateOf(DEFAULT_RENAME_TEMPLATE) }

  var metaList by remember { mutableStateOf(emptyList<SongMeta>()) }
  var loading by remember { mutableStateOf(false) }

  val mode1NewNames = remember { mutableStateListOf<String>() }
  val mode1InitialNames = remember { mutableStateListOf<String>() }
  var mode2Fields by remember { mutableStateOf(emptyList<MetaField>()) }
  val mode2Values = remember { mutableStateListOf<Map<MetaField, String>>() }

  val mode3NewNames = remember { mutableStateListOf<String>() }
  val mode4Fields = remember { mutableStateListOf<MetaField>() }
  var mode4ShowFile by remember { mutableStateOf(false) }
  val mode4Values = remember { mutableStateListOf<Map<MetaField, String>>() }

  // 编辑撤销缓存（仅会话内有效）
  val editSnapshots = remember { mutableStateMapOf<String, String>() }
  val undoStack = remember { mutableStateListOf<EditUndo>() }

  var phase by remember { mutableStateOf(Phase.IDLE) }
  val progress = remember { mutableStateOf(0 to 0) }
  val result = remember { mutableStateOf<SaveResult?>(null) }
  val undoItems = remember { mutableStateListOf<UndoItem>() }
  val saveJob = remember { mutableStateOf<Job?>(null) }
  val cancelFlag = remember { mutableStateOf(AtomicBoolean(false)) }

  // 保存日志与失败明细：无论是否开启“记录日志”开关都会保留在内存中，并可导出
  val saveLog = remember { mutableStateListOf<SaveLogLine>() }
  val failures = remember { mutableStateListOf<SaveFailure>() }
  var showFailures by remember { mutableStateOf(false) }
  var reportText by remember { mutableStateOf("") }

  val storageDialogState = rememberDialogState()

  fun recompute() {
    val list = metaList
    undoStack.clear()
    editSnapshots.clear()
    when (mode) {
      MODE_META -> {
        val used = BatchRenameUtil.usedFields(template)
        mode1InitialNames.clear()
        mode1NewNames.clear()
        list.forEach { sm ->
          val name = BatchRenameUtil.buildNameFromMeta(template, sm.meta) ?: sm.oldName
          mode1InitialNames.add(name)
          mode1NewNames.add(name)
        }
      }

      MODE_FILE -> {
        val fields = BatchRenameUtil.usedFields(rule)
        mode2Fields = fields
        mode2Values.clear()
        list.forEach { sm ->
          val extracted = BatchRenameUtil.extractFromName(rule, sm.oldName) ?: emptyMap()
          mode2Values.add(fields.associate { f -> f to (extracted[f.fieldKey] ?: sm.meta[f.fieldKey].orEmpty()) })
        }
      }

      MODE_MANUAL_RENAME -> {
        mode3NewNames.clear()
        list.forEach { sm -> mode3NewNames.add(sm.oldName) }
      }

      MODE_MANUAL_META -> {
        mode4Values.clear()
        list.forEach { sm ->
          mode4Values.add(EDITABLE_META_FIELDS.associate { f -> f to sm.meta[f.fieldKey].orEmpty() })
        }
      }
    }
  }

  fun beginEdit(key: String, getValue: () -> String) {
    if (key !in editSnapshots) editSnapshots[key] = getValue()
  }

  fun endEdit(key: String, row: Int, kind: Int, field: MetaField?, getValue: () -> String) {
    val start = editSnapshots.remove(key)
    if (start != null) {
      val cur = getValue()
      if (cur != start) undoStack.add(EditUndo(row, kind, field, start))
    }
  }

  fun undoLastEdit() {
    if (undoStack.isEmpty()) return
    val a = undoStack.removeAt(undoStack.lastIndex)
    when (a.kind) {
      MODE_META -> mode1NewNames[a.row] = a.prev
      MODE_MANUAL_RENAME -> mode3NewNames[a.row] = a.prev
      MODE_FILE -> mode2Values[a.row] = mode2Values[a.row].toMutableMap().apply { if (a.field != null) this[a.field] = a.prev }
      MODE_MANUAL_META -> mode4Values[a.row] = mode4Values[a.row].toMutableMap().apply { if (a.field != null) this[a.field] = a.prev }
    }
    MessageNotifier.show(R.string.batch_rename_undo_done)
  }

  fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("filename", text))
    MessageNotifier.show(R.string.batch_rename_copied)
  }

  LaunchedEffect(Unit) {
    if (state.songs.isEmpty()) {
      nav.popBackStack()
      return@LaunchedEffect
    }
    mode = MODE_META
    template = DEFAULT_RENAME_TEMPLATE
    rule = DEFAULT_RENAME_TEMPLATE
    mode4Fields.clear()
    mode4Fields.addAll(EDITABLE_META_FIELDS)
    mode4ShowFile = false
    phase = Phase.IDLE
    result.value = null
    undoItems.clear()
    progress.value = 0 to 0
    cancelFlag.value.set(false)
    loading = true
    val list = withContext(Dispatchers.IO) {
      state.songs.map { SongMeta(it, BatchRenameUtil.readMetaMap(it), fileNameWithoutExtension(it.displayName)) }
    }
    metaList = list
    recompute()
    loading = false
  }

  fun startSave() {
    if (metaList.isEmpty()) return
    val needTag = mode == MODE_FILE || mode == MODE_MANUAL_META
    if (needTag && !canWriteAudioFiles()) {
      Timber.tag(LOG_TAG).w("Save aborted: no permission to write audio files")
      storageDialogState.show()
      return
    }
    undoStack.clear()
    editSnapshots.clear()
    phase = Phase.SAVING
    progress.value = 0 to metaList.size
    cancelFlag.value.set(false)
    result.value = null
    showFailures = false
    saveLog.clear()
    failures.clear()
    reportText = ""

    val list = metaList
    val startAt = System.currentTimeMillis()
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val timber = Timber.tag(LOG_TAG)
    val logBuffer = mutableListOf<SaveLogLine>()
    val failBuffer = mutableListOf<SaveFailure>()

    /** 同时写入 Timber（落日志文件）与内存日志（可导出） */
    fun log(level: String, message: String, t: Throwable? = null) {
      logBuffer.add(SaveLogLine(timeFormat.format(Date()), level, message))
      when (level) {
        LOG_ERROR -> timber.e(t, message)
        LOG_WARN -> timber.w(t, message)
        else -> timber.i(message)
      }
    }

    log(
      LOG_INFO,
      "Batch save start: mode=$mode(${modeNames[mode]}), " +
        "rule=${if (mode == MODE_FILE) rule else template}, total=${list.size}"
    )

    val job = scope.launch(Dispatchers.IO) {
      var done = 0
      var ok = 0
      var fail = 0
      var skipped = 0
      val undos = mutableListOf<UndoItem>()
      for (i in list.indices) {
        if (cancelFlag.value.get()) {
          log(LOG_WARN, "Save cancelled by user: processed=$done/${list.size}")
          break
        }
        val sm = list[i]
        var applied = false
        var skippedItem = false
        // 失败原因 + 技术细节（写入日志与导出文件）
        var failed: Pair<FailReason, String?>? = null
        var error: Throwable? = null
        try {
          if (mode == MODE_META || mode == MODE_MANUAL_RENAME) {
            if (!sm.song.isLocal()) {
              failed = FailReason.NOT_LOCAL to sm.song.data
            } else {
              val newName = (if (mode == MODE_META) mode1NewNames else mode3NewNames)[i]
              val oldName = sm.oldName
              when {
                newName.isBlank() -> {
                  skippedItem = true
                  log(LOG_WARN, "[$i] skipped, empty new name: ${sm.song.data}")
                }

                newName == oldName -> {
                  skippedItem = true
                  log(LOG_WARN, "[$i] skipped, name unchanged: ${sm.song.data}")
                }

                else -> {
                  val file = File(sm.song.data)
                  val ext = file.extension
                  val newFile = File(file.parent, if (ext.isNotEmpty()) "$newName.$ext" else newName)
                  when {
                    !file.exists() -> failed = FailReason.SRC_MISSING to file.absolutePath
                    newFile.exists() -> failed = FailReason.TARGET_EXISTS to newFile.absolutePath
                    !file.renameTo(newFile) -> failed = FailReason.RENAME_FAILED to
                      "src=${file.absolutePath}, dst=${newFile.absolutePath}, " +
                      "dirExists=${file.parentFile?.exists()}, dirWritable=${file.parentFile?.canWrite()}, " +
                      "fileWritable=${file.canWrite()}"

                    else -> {
                      MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null, null)
                      undos.add(UndoItem.Rename(sm.song.data, newFile.absolutePath))
                      applied = true
                      log(LOG_INFO, "[$i] renamed: ${file.absolutePath} -> ${newFile.absolutePath}")
                    }
                  }
                }
              }
            }
          } else {
            if (!sm.song.isLocal()) {
              failed = FailReason.NOT_LOCAL to sm.song.data
            } else {
              val values = (if (mode == MODE_FILE) mode2Values else mode4Values)[i]
              val fields = if (mode == MODE_FILE) mode2Fields else mode4Fields
              val current = sm.meta
              val fieldMap = fields.associate { f -> f to (values[f].orEmpty()) }
                .filter { (f, v) -> v != current[f.fieldKey] }
                .mapKeys { (f, _) -> f.fieldKey }
              if (fieldMap.isEmpty()) {
                skippedItem = true
                log(LOG_WARN, "[$i] skipped, no field changed: ${sm.song.data}")
              } else {
                try {
                  AudioTagWriter.saveTagFields(context, sm.song, fieldMap)
                  undos.add(
                    UndoItem.Tags(
                      sm.song,
                      fields.associate { f -> f.fieldKey to current[f.fieldKey].orEmpty() }
                    )
                  )
                  applied = true
                  log(LOG_INFO, "[$i] tags saved: ${sm.song.data}, fields=${fieldMap.keys}")
                } catch (e: Exception) {
                  failed = FailReason.WRITE_TAG_FAILED to
                    "path=${sm.song.data}, fields=$fieldMap, error=${e.stackTraceToString()}"
                  error = e
                }
              }
            }
          }
        } catch (e: Exception) {
          failed = FailReason.EXCEPTION to "path=${sm.song.data}, error=${e.stackTraceToString()}"
          error = e
        }

        when {
          applied -> ok++
          skippedItem -> skipped++
          else -> {
            fail++
            val reason = failed?.first ?: FailReason.EXCEPTION
            val detail = failed?.second
            failBuffer.add(
              SaveFailure(
                displayName = sm.song.displayName.ifBlank { sm.oldName },
                path = sm.song.data,
                reason = reason,
                detail = detail
              )
            )
            log(LOG_ERROR, "[$i] failed: ${reason.code}, ${sm.song.data}, ${detail.orEmpty()}", error)
          }
        }
        done++
        progress.value = done to list.size
      }

      val elapsed = System.currentTimeMillis() - startAt
      val notProcessed = list.size - done
      log(
        LOG_INFO,
        "Batch save end: ok=$ok, fail=$fail, skipped=$skipped, notProcessed=$notProcessed, elapsed=${elapsed}ms"
      )

      val summary = SaveSummary(
        time = REPORT_TIME_FORMAT.format(Date(startAt)),
        mode = modeNames[mode].orEmpty(),
        rule = if (mode == MODE_FILE) rule else template,
        total = list.size,
        ok = ok,
        fail = fail,
        skipped = skipped + notProcessed,
        elapsedMs = elapsed
      )
      val finalLog = ArrayList(logBuffer)
      val finalFails = ArrayList(failBuffer)
      val report = buildReportText(context, summary, finalLog, finalFails)
      val persisted = persistReport(context, report)
      if (persisted != null) {
        Timber.tag(LOG_TAG).i("Save log persisted: ${persisted.absolutePath}")
      }

      withContext(Dispatchers.Main) {
        libraryVM.fetchMedia(true)
        saveLog.clear()
        saveLog.addAll(finalLog)
        failures.clear()
        failures.addAll(finalFails)
        reportText = report
        result.value = SaveResult(ok, fail, summary.skipped)
        undoItems.addAll(undos)
        phase = Phase.DONE
      }
    }
    saveJob.value = job
  }

  fun cancelSave() {
    cancelFlag.value.set(true)
    saveJob.value?.cancel()
  }

  fun startUndo() {
    val items = undoItems.toList()
    if (items.isEmpty()) return
    phase = Phase.SAVING
    progress.value = 0 to items.size
    cancelFlag.value.set(false)
    val job = scope.launch(Dispatchers.IO) {
      var done = 0
      items.forEach { item ->
        try {
          when (item) {
            is UndoItem.Rename -> {
              val nf = File(item.newPath)
              val of = File(item.oldPath)
              if (nf.exists() && !of.exists()) {
                if (nf.renameTo(of)) {
                  MediaScannerConnection.scanFile(context, arrayOf(of.absolutePath), null, null)
                }
              }
            }

            is UndoItem.Tags -> {
              AudioTagWriter.saveTagFields(context, item.song, item.oldValues)
            }
          }
        } catch (e: Exception) {
          val path = when (item) {
            is UndoItem.Rename -> item.oldPath
            is UndoItem.Tags -> item.song.data
          }
          Timber.tag(LOG_TAG).w(e, "BatchRename undo failed: $path")
        }
        done++
        progress.value = done to items.size
      }
      withContext(Dispatchers.Main) {
        libraryVM.fetchMedia(true)
        undoItems.clear()
        result.value = null
        phase = Phase.IDLE
        MessageNotifier.show(R.string.batch_rename_undo_done)
      }
    }
    saveJob.value = job
  }

  // 导出本次保存日志（含失败明细）到用户选择的位置
  val exportReportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { res ->
    if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
    val uri = res.data?.data ?: return@rememberLauncherForActivityResult
    val text = reportText
    scope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val out = context.contentResolver.openOutputStream(uri)
            ?: return@runCatching false
          out.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
          true
        }.getOrDefault(false)
      }
      MessageNotifier.show(
        if (ok) R.string.batch_rename_log_exported else R.string.batch_rename_log_export_failed
      )
    }
  }

  // 导出完整应用日志（zip），用于在保存日志之外排查更底层的问题
  val exportFullLogLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { res ->
    if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
    val uri = res.data?.data ?: return@rememberLauncherForActivityResult
    scope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val zipFile = LogFileWriter.exportZip(context) ?: return@runCatching false
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

  fun exportReport() {
    if (reportText.isBlank()) {
      MessageNotifier.show(R.string.batch_rename_log_export_failed)
      return
    }
    exportReportLauncher.launch(
      Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "text/plain"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, "batch_rename_log.txt")
      }
    )
  }

  fun exportFullLog() {
    exportFullLogLauncher.launch(
      Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "application/zip"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, "logs.zip")
      }
    )
  }

  val canSave by remember { derivedStateOf {
    when (mode) {
      MODE_META -> BatchRenameUtil.usedFields(template).isNotEmpty()
      MODE_FILE -> mode2Fields.isNotEmpty() &&
        !(MetaField.TITLE in mode2Fields && mode2Values.any { it[MetaField.TITLE].isNullOrBlank() })

      MODE_MANUAL_RENAME -> true
      MODE_MANUAL_META -> mode4Fields.isNotEmpty() &&
        !(MetaField.TITLE in mode4Fields && mode4Values.any { it[MetaField.TITLE].isNullOrBlank() })

      else -> false
    }
  } }

  BackHandler(enabled = true) {
    cancelSave()
    nav.popBackStack()
  }

  Scaffold(
    containerColor = theme.mainBackground,
    topBar = {
      CommonAppBar(
        title = stringResource(R.string.batch_modify),
        onBack = {
          cancelSave()
          nav.popBackStack()
        },
        actions = {
          val undoEnabled = undoStack.isNotEmpty()
          val contentColor = theme.primaryReverse
          Box(
            modifier = Modifier
              .background(contentColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
              .clickWithRipple(false, enabled = undoEnabled) { undoLastEdit() }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              stringResource(R.string.batch_rename_undo),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = contentColor.copy(alpha = if (undoEnabled) 1f else 0.45f)
            )
          }
        }
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.primary)
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val barContentColor = theme.primaryReverse
        when (phase) {
          Phase.IDLE -> {
            Box(modifier = Modifier.weight(1f)) {
              TextButton(stringResource(R.string.cancel), color = barContentColor) {
                nav.popBackStack()
              }
            }
            TextButton(
              text = stringResource(R.string.batch_rename_save),
              enabled = canSave,
              color = barContentColor
            ) { startSave() }
          }

          Phase.SAVING -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
              TextButton(stringResource(R.string.batch_rename_cancel_save), color = barContentColor) { cancelSave() }
            }
          }

          Phase.DONE -> {
            Box(modifier = Modifier.weight(1f)) {
              TextButton(
                stringResource(R.string.batch_rename_undo),
                enabled = undoItems.isNotEmpty(),
                color = barContentColor
              ) { startUndo() }
            }
            TextButton(stringResource(R.string.batch_rename_done), color = barContentColor) {
              nav.popBackStack()
            }
          }
        }
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(theme.mainBackground)
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 模式切换
      val modeChips = modeNames.toList()
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        modeChips.forEach { (m, label) ->
          ModeToggleButton(
            selected = mode == m,
            text = label,
            onClick = { if (phase == Phase.IDLE) { mode = m; recompute() } }
          )
        }
      }

      when (phase) {
        Phase.IDLE -> IdleContent(
          mode = mode,
          template = template,
          rule = rule,
          metaList = metaList,
          loading = loading,
          mode1NewNames = mode1NewNames,
          mode1InitialNames = mode1InitialNames,
          mode2Fields = mode2Fields,
          mode2Values = mode2Values,
          mode3NewNames = mode3NewNames,
          mode4Fields = mode4Fields,
          mode4ShowFile = mode4ShowFile,
          mode4Values = mode4Values,
          onTemplateChange = { template = it; recompute() },
          onRuleChange = { rule = it; recompute() },
          onToggleFile = { mode4ShowFile = !mode4ShowFile },
          onToggleField = { f ->
            if (f in mode4Fields) mode4Fields.remove(f) else mode4Fields.add(f)
          },
          onBeginEdit = { key, getValue -> beginEdit(key, getValue) },
          onEndEdit = { key, row, kind, field, getValue -> endEdit(key, row, kind, field, getValue) },
          onCopy = { copyText(context, it) }
        )

        Phase.SAVING -> SaveContent(progress = progress.value)
        Phase.DONE -> ResultContent(
          result = result.value,
          failures = failures,
          showFailures = showFailures,
          onToggleFailures = { showFailures = !showFailures },
          onExportReport = { exportReport() },
          onExportFullLog = { exportFullLog() }
        )
      }
    }
  }

  ManageStorageDialog(dialogState = storageDialogState, onCancel = { storageDialogState.dismiss() })
}

@Composable
private fun IdleContent(
  mode: Int,
  template: String,
  rule: String,
  metaList: List<SongMeta>,
  loading: Boolean,
  mode1NewNames: SnapshotStateList<String>,
  mode1InitialNames: SnapshotStateList<String>,
  mode2Fields: List<MetaField>,
  mode2Values: SnapshotStateList<Map<MetaField, String>>,
  mode3NewNames: SnapshotStateList<String>,
  mode4Fields: List<MetaField>,
  mode4ShowFile: Boolean,
  mode4Values: SnapshotStateList<Map<MetaField, String>>,
  onTemplateChange: (String) -> Unit,
  onRuleChange: (String) -> Unit,
  onToggleFile: () -> Unit,
  onToggleField: (MetaField) -> Unit,
  onBeginEdit: (String, () -> String) -> Unit,
  onEndEdit: (String, Int, Int, MetaField?, () -> String) -> Unit,
  onCopy: (String) -> Unit
) {
  val theme = LocalTheme.current

  if (loading) {
    TextSecondary(stringResource(R.string.batch_rename_loading), fontSize = 14.sp)
    return
  }
  if (metaList.isEmpty()) {
    TextSecondary(stringResource(R.string.batch_rename_empty), fontSize = 14.sp)
    return
  }

  // 顶部设置区
  when (mode) {
    MODE_META -> {
      HintText(stringResource(R.string.batch_rename_mode_meta_hint, supportedFieldsText()))
      FormatInput(template, onTemplateChange)
    }

    MODE_FILE -> {
      HintText(stringResource(R.string.batch_rename_mode_file_hint, supportedFieldsText()))
      FormatInput(rule, onRuleChange)
      if (mode2Fields.isEmpty()) {
        Text(
          stringResource(R.string.batch_rename_no_field),
          fontSize = 13.sp,
          color = theme.errorColor()
        )
      }
    }

    MODE_MANUAL_META -> {
      HintText(stringResource(R.string.batch_rename_select_columns))
      FieldChips(
        showFile = mode4ShowFile,
        selectedFields = mode4Fields,
        onToggleFile = onToggleFile,
        onToggleField = onToggleField
      )
    }
  }

  // 图例
  Row(
    modifier = Modifier.padding(top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    LegendDot(CellColor.GRAY, stringResource(R.string.batch_rename_legend_nochange))
    LegendDot(CellColor.GREEN, stringResource(R.string.batch_rename_legend_ok))
    LegendDot(CellColor.RED, stringResource(R.string.batch_rename_legend_bad))
  }

  // 表格：先按模式构建列定义（含参与测宽的原始文本），再据此计算列宽
  val tableColumns: List<TableColumn> = buildColumns(
    mode = mode,
    metaList = metaList,
    template = template,
    rule = rule,
    mode1InitialNames = mode1InitialNames,
    mode1NewNames = mode1NewNames,
    mode2Fields = mode2Fields,
    mode2Values = mode2Values,
    mode3NewNames = mode3NewNames,
    mode4Fields = mode4Fields,
    mode4ShowFile = mode4ShowFile,
    mode4Values = mode4Values,
    onBeginEdit = onBeginEdit,
    onEndEdit = onEndEdit,
    onCopy = onCopy
  )
  MetaTable(
    columns = tableColumns,
    widths = rememberTableWidths(tableColumns),
    rowCount = metaList.size
  )
}

/** 模式说明文字：按可用宽度自动换行（不限制行数） */
@Composable
private fun HintText(text: String) {
  TextSecondary(text, fontSize = 13.sp, maxLine = Int.MAX_VALUE)
}

/** 当前支持的元数据字段占位符列表，如 {title}（歌曲名），用于模式说明 */
private fun supportedFieldsText(): String =
  MetaField.entries.joinToString("、") { "{${it.token}}（${it.zhLabel}）" }

/**
 * 按模式构建表格的列定义。
 *
 * 每一列都携带一份参与宽度测量的原始文本（[TableColumn.measureValues]）：
 * 取的数据是「按规则生成 / 从文件读取」的初始值，而不是用户正在输入的值，
 * 这样边输入边测量也不会出现列宽抖动。
 */
@Composable
private fun buildColumns(
  mode: Int,
  metaList: List<SongMeta>,
  template: String,
  rule: String,
  mode1NewNames: SnapshotStateList<String>,
  mode1InitialNames: SnapshotStateList<String>,
  mode2Fields: List<MetaField>,
  mode2Values: SnapshotStateList<Map<MetaField, String>>,
  mode3NewNames: SnapshotStateList<String>,
  mode4Fields: List<MetaField>,
  mode4ShowFile: Boolean,
  mode4Values: SnapshotStateList<Map<MetaField, String>>,
  onBeginEdit: (String, () -> String) -> Unit,
  onEndEdit: (String, Int, Int, MetaField?, () -> String) -> Unit,
  onCopy: (String) -> Unit
): List<TableColumn> {
  val theme = LocalTheme.current
  return when (mode) {
    // 模式一：元数据列 + 原文件名 + 新文件名
    MODE_META -> {
      val used = BatchRenameUtil.usedFields(template)
      buildList {
        used.forEach { f ->
          add(
            TableColumn(
              title = f.zhLabel,
              measureValues = metaList.map { it.meta[f.fieldKey].orEmpty() },
              content = { row, width ->
                MetaCell(metaList[row].meta[f.fieldKey].orEmpty(), CellColor.GRAY, width)
              }
            )
          )
        }
        add(
          TableColumn(
            title = stringResource(R.string.batch_rename_old_name),
            measureValues = metaList.map { it.oldName },
            content = { row, width ->
              MetaCell(metaList[row].oldName, CellColor.GRAY, width)
            }
          )
        )
        add(
          TableColumn(
            title = stringResource(R.string.batch_rename_new_name),
            measureValues = metaList.indices.map { mode1InitialNames.getOrElse(it) { metaList[it].oldName } },
            content = { row, width ->
              val name = mode1NewNames.getOrElse(row) { metaList[row].oldName }
              val key = "m1:$row"
              MetaCell(
                value = name,
                color = mode1CellColor(
                  metaList[row], name,
                  mode1InitialNames.getOrElse(row) { name }, used
                ),
                width = width,
                editable = true,
                onValueChange = { mode1NewNames[row] = it },
                onFocus = { onBeginEdit(key) { mode1NewNames.getOrElse(row) { metaList[row].oldName } } },
                onBlur = { onEndEdit(key, row, MODE_META, null) { mode1NewNames.getOrElse(row) { metaList[row].oldName } } }
              )
            }
          )
        )
      }
    }

    // 模式二：文件名 + 各元数据提取列
    MODE_FILE -> {
      // 提取结果只随规则变化，作为稳定的测宽样本
      val extracted = remember(mode, rule, metaList) {
        metaList.map { sm -> BatchRenameUtil.extractFromName(rule, sm.oldName) ?: emptyMap() }
      }
      fun rowValues(row: Int): Map<MetaField, String> =
        mode2Values.getOrElse(row) { emptyMap() }
      buildList {
        add(
          TableColumn(
            title = stringResource(R.string.batch_rename_filename),
            measureValues = metaList.map { it.oldName },
            content = { row, width ->
              MetaCell(metaList[row].oldName, CellColor.GRAY, width)
            }
          )
        )
        mode2Fields.forEach { f ->
          add(
            TableColumn(
              title = f.zhLabel,
              measureValues = metaList.indices.map { i ->
                extracted.getOrElse(i) { emptyMap() }[f.fieldKey]
                  ?: metaList[i].meta[f.fieldKey].orEmpty()
              },
              content = { row, width ->
                val current = metaList[row].meta[f.fieldKey].orEmpty()
                val target = rowValues(row)[f].orEmpty()
                val key = "m2:$row:${f.token}"
                MetaCell(
                  value = target,
                  color = mode2CellColor(current, target),
                  width = width,
                  editable = true,
                  onValueChange = {
                    mode2Values[row] = rowValues(row).toMutableMap().apply { this[f] = it }
                  },
                  onFocus = { onBeginEdit(key) { rowValues(row)[f].orEmpty() } },
                  onBlur = { onEndEdit(key, row, MODE_FILE, f) { rowValues(row)[f].orEmpty() } }
                )
              }
            )
          )
        }
      }
    }

    // 模式三：原文件名（带"复制"）+ 新文件名（带"清空"）
    MODE_MANUAL_RENAME -> {
      val linkColor = theme.highLightText()
      val copyLabel = stringResource(R.string.batch_rename_copy)
      val clearLabel = stringResource(R.string.batch_rename_clear)
      listOf(
        TableColumn(
          title = stringResource(R.string.batch_rename_old_name),
          measureValues = metaList.map { it.oldName },
          hasAction = true,
          content = { row, width ->
            MetaCell(
              value = metaList[row].oldName,
              color = CellColor.GRAY,
              width = width,
              action = {
                CellAction(copyLabel, linkColor) { onCopy(metaList[row].oldName) }
              }
            )
          }
        ),
        TableColumn(
          title = stringResource(R.string.batch_rename_new_name),
          measureValues = metaList.map { it.oldName },
          hasAction = true,
          content = { row, width ->
            val old = metaList[row].oldName
            val name = mode3NewNames.getOrElse(row) { old }
            val key = "m3:$row"
            MetaCell(
              value = name,
              color = if (name != old) CellColor.GREEN else CellColor.GRAY,
              width = width,
              editable = true,
              action = {
                CellAction(clearLabel, linkColor) { mode3NewNames[row] = "" }
              },
              onValueChange = { mode3NewNames[row] = it },
              onFocus = { onBeginEdit(key) { mode3NewNames.getOrElse(row) { old } } },
              onBlur = { onEndEdit(key, row, MODE_MANUAL_RENAME, null) { mode3NewNames.getOrElse(row) { old } } }
            )
          }
        )
      )
    }

    // 模式四：文件名（可选，只读）+ 各元数据列（带"清空"）
    else -> {
      val linkColor = theme.highLightText()
      val clearLabel = stringResource(R.string.batch_rename_clear)
      fun rowValues(row: Int): Map<MetaField, String> = mode4Values.getOrElse(row) { emptyMap() }
      buildList {
        if (mode4ShowFile) {
          add(
            TableColumn(
              title = stringResource(R.string.batch_rename_filename),
              measureValues = metaList.map { it.oldName },
              content = { row, width ->
                MetaCell(metaList[row].oldName, CellColor.GRAY, width)
              }
            )
          )
        }
        mode4Fields.forEach { f ->
          add(
            TableColumn(
              title = f.zhLabel,
              measureValues = metaList.map { it.meta[f.fieldKey].orEmpty() },
              hasAction = true,
              content = { row, width ->
                val current = metaList[row].meta[f.fieldKey].orEmpty()
                val target = rowValues(row)[f].orEmpty()
                val key = "m4:$row:${f.token}"
                MetaCell(
                  value = target,
                  color = mode2CellColor(current, target),
                  width = width,
                  editable = true,
                  action = {
                    CellAction(clearLabel, linkColor) {
                      mode4Values[row] = rowValues(row).toMutableMap().apply { this[f] = "" }
                    }
                  },
                  onValueChange = {
                    mode4Values[row] = rowValues(row).toMutableMap().apply { this[f] = it }
                  },
                  onFocus = { onBeginEdit(key) { rowValues(row)[f].orEmpty() } },
                  onBlur = { onEndEdit(key, row, MODE_MANUAL_META, f) { rowValues(row)[f].orEmpty() } }
                )
              }
            )
          )
        }
      }
    }
  }
}

/**
 * 表格容器：横向可滚动，行高为该行单元格的最大高度，
 * 每个单元格用 [Modifier.fillMaxHeight] 撑满整行，保证同一行背景等高、文字垂直居中。
 */
@Composable
private fun MetaTable(columns: List<TableColumn>, widths: List<Dp>, rowCount: Int) {
  val theme = LocalTheme.current
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
  ) {
    Column(
      modifier = Modifier
        .background(theme.outline())
        .border(0.5.dp, theme.outline())
    ) {
      Row(
        modifier = Modifier
          .background(theme.headerSurface())
          .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
      ) {
        columns.forEachIndexed { index, column ->
          HeaderCellText(column.title, widths.getOrElse(index) { COLUMN_MIN_WIDTH })
        }
      }
      repeat(rowCount) { row ->
        Row(
          modifier = Modifier.height(IntrinsicSize.Min),
          verticalAlignment = Alignment.CenterVertically
        ) {
          columns.forEachIndexed { index, column ->
            column.content(row, widths.getOrElse(index) { COLUMN_MIN_WIDTH })
          }
        }
      }
    }
  }
}

/** 表头单元格 */
@Composable
private fun HeaderCellText(text: String, width: Dp) {
  val theme = LocalTheme.current
  Box(
    modifier = Modifier
      .width(width)
      .fillMaxHeight()
      .border(0.5.dp, theme.outline())
      .padding(horizontal = CELL_PADDING, vertical = CELL_VERTICAL_PADDING),
    contentAlignment = Alignment.CenterStart
  ) {
    TextPrimary(
      text = text,
      fontSize = CELL_FONT_SIZE,
      fontWeight = FontWeight.SemiBold,
      color = theme.cellTextColor()
    )
  }
}

/** 按各列最长内容计算列宽：内容（含表头文本）没有变化时复用上一次结果 */
@Composable
private fun rememberTableWidths(columns: List<TableColumn>): List<Dp> {
  val measureText = rememberTextWidthMeasurer(CELL_FONT_SIZE, FontWeight.Normal)
  val key = columns.joinToString(separator = "\n") { column ->
    buildString {
      append(column.title).append('\u0002').append(column.hasAction).append('\u0003')
      column.measureValues.joinTo(this, separator = "\u0001")
    }
  }
  return remember(key, measureText) {
    columns.map { it.measureWidth(measureText) }
  }
}

/**
 * 文本宽度测量器：返回给定文本在当前字号 / 字重下的单行宽度（Dp）。
 * 直接用 Paint 测量，无需为测宽触发一次 Compose 排版，也不会随列表规模放大开销。
 */
@Composable
private fun rememberTextWidthMeasurer(fontSize: TextUnit, fontWeight: FontWeight): (String) -> Dp {
  val density = LocalDensity.current
  val sizePx = with(density) { fontSize.toPx() }
  // Typeface 的 weight 版本需 API 28，这里退化为系统 style，保证低版本可用
  val style = if (fontWeight.weight >= FontWeight.SemiBold.weight) Typeface.BOLD else Typeface.NORMAL
  val paint = remember(sizePx, style) {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = sizePx
      typeface = Typeface.create(Typeface.DEFAULT, style)
    }
  }
  return remember(paint, density) { { text: String ->
      if (text.isEmpty()) 0.dp else with(density) { ceil(paint.measureText(text)).toInt().toDp() }
  } }
}

@Composable
private fun FieldChips(
  showFile: Boolean,
  selectedFields: List<MetaField>,
  onToggleFile: () -> Unit,
  onToggleField: (MetaField) -> Unit
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Chip(stringResource(R.string.batch_rename_filename), showFile, onToggleFile)
    EDITABLE_META_FIELDS.forEach { f ->
      Chip(f.zhLabel, f in selectedFields) { onToggleField(f) }
    }
  }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
  SelectableSurface(
    label = label,
    selected = selected,
    fontSize = 13.sp,
    radius = 14.dp,
    onClick = onClick
  )
}

@Composable
private fun ModeToggleButton(selected: Boolean, text: String, onClick: () -> Unit) {
  SelectableSurface(
    label = text,
    selected = selected,
    fontSize = 14.sp,
    radius = 8.dp,
    onClick = onClick
  )
}

/** 统一的“可选中”标签/按钮：选中用主题主色，未选中用主题中性底色 + 描边，深浅色都可辨识 */
@Composable
private fun SelectableSurface(
  label: String,
  selected: Boolean,
  fontSize: TextUnit,
  radius: Dp,
  onClick: () -> Unit
) {
  val theme = LocalTheme.current
  val shape = RoundedCornerShape(radius)
  val backgroundColor = if (selected) theme.primary else theme.mutedSurface()
  val outline = if (selected) theme.primary else theme.outline()
  val contentColor = if (selected) theme.primaryReverse else theme.textPrimary
  Box(
    modifier = Modifier
      .background(backgroundColor, shape)
      .border(1.dp, outline, shape)
      .clickWithRipple(false, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp)
  ) {
    Text(
      text = label,
      color = contentColor,
      fontSize = fontSize,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    )
  }
}

@Composable
private fun FormatInput(value: String, onValueChange: (String) -> Unit) {
  val theme = LocalTheme.current
  val shape = RoundedCornerShape(6.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(theme.mutedSurface(), shape)
      .border(1.dp, theme.outline(), shape)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary),
      modifier = Modifier.fillMaxWidth()
    )
  }
}

/**
 * 单元格：固定宽度 + 撑满行高，内容为单行（不换行），超长时省略号或横向滚动。
 *
 * 背景放在最外层 Box 上，并用 [Modifier.fillMaxHeight] 跟随所在行的最高单元格，
 * 因此不会因为某个单元格是输入框（BasicTextField 的测量高度与普通 Text 不同）
 * 而出现同一行背景高度不一致的问题。
 * [action] 为单元格内的右侧操作按钮（复制 / 清空），与文本处于同一行、靠右显示。
 */
@Composable
private fun MetaCell(
  value: String,
  color: CellColor,
  width: Dp,
  editable: Boolean = false,
  action: (@Composable () -> Unit)? = null,
  onValueChange: ((String) -> Unit)? = null,
  onFocus: (() -> Unit)? = null,
  onBlur: (() -> Unit)? = null
) {
  val theme = LocalTheme.current
  Box(
    modifier = Modifier
      .width(width)
      .fillMaxHeight()
      .background(theme.cellBackground(color))
      .border(0.5.dp, theme.outline())
      .padding(
        start = CELL_PADDING,
        end = if (action != null) 0.dp else CELL_PADDING,
        top = CELL_VERTICAL_PADDING,
        bottom = CELL_VERTICAL_PADDING
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (editable && onValueChange != null) {
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          maxLines = 1,
          textStyle = TextStyle(fontSize = CELL_FONT_SIZE, color = theme.cellTextColor()),
          modifier = Modifier
            .weight(1f)
            .onFocusEvent { fs ->
              if (fs.isFocused) onFocus?.invoke() else onBlur?.invoke()
            }
        )
      } else {
        Text(
          text = value.ifBlank { "—" },
          color = theme.cellTextColor(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = TextStyle(fontSize = CELL_FONT_SIZE),
          modifier = Modifier.weight(1f)
        )
      }
      if (action != null) {
        Spacer(modifier = Modifier.width(CELL_ACTION_GAP))
        Box(
          modifier = Modifier.width(CELL_ACTION_WIDTH),
          contentAlignment = Alignment.CenterEnd
        ) {
          action()
        }
      }
    }
  }
}

/** 单元格内的操作按钮（复制 / 清空）：不单独占一行，位于单元格内部靠右 */
@Composable
private fun CellAction(text: String, color: Color, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clickWithRipple(false, onClick = onClick)
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
  }
}

@Composable
private fun LegendDot(color: CellColor, label: String) {
  val theme = LocalTheme.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier
        .width(10.dp)
        .height(10.dp)
        .background(theme.statusColor(color), CircleShape)
    )
    TextSecondary(label, fontSize = 12.sp)
  }
}

@Composable
private fun SaveContent(progress: Pair<Int, Int>) {
  val theme = LocalTheme.current
  val shape = RoundedCornerShape(4.dp)
  val fraction = if (progress.second > 0) progress.first.toFloat() / progress.second else 0f
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TextPrimary(stringResource(R.string.batch_rename_saving), fontSize = 15.sp)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .background(theme.headerSurface(), shape)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction)
          .height(8.dp)
          .background(theme.statusColor(CellColor.GREEN), shape)
      )
    }
    TextSecondary("${progress.first} / ${progress.second}", fontSize = 13.sp)
  }
}

@Composable
private fun ResultContent(
  result: SaveResult?,
  failures: List<SaveFailure>,
  showFailures: Boolean,
  onToggleFailures: () -> Unit,
  onExportReport: () -> Unit,
  onExportFullLog: () -> Unit
) {
  val theme = LocalTheme.current
  val r = result ?: SaveResult(0, 0, 0)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TextPrimary(
      text = stringResource(R.string.batch_rename_done_title),
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold
    )
    TextPrimary(
      text = stringResource(R.string.batch_rename_result, r.ok, r.fail, r.skipped),
      fontSize = 14.sp,
      color = if (r.fail > 0) theme.statusColor(CellColor.RED) else theme.statusColor(CellColor.GREEN)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ActionChip(stringResource(R.string.batch_rename_export_log), onClick = onExportReport)
      ActionChip(stringResource(R.string.batch_rename_export_full_log), onClick = onExportFullLog)
    }

    if (failures.isNotEmpty()) {
      TextPrimary(
        text = stringResource(R.string.batch_rename_fail_count, failures.size),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = theme.errorColor()
      )
      ActionChip(
        text = stringResource(
          if (showFailures) R.string.batch_rename_hide_detail else R.string.batch_rename_show_detail
        ),
        color = theme.errorColor(),
        onClick = onToggleFailures
      )
      if (showFailures) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(theme.errorColor().copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(0.5.dp, theme.outline(), RoundedCornerShape(6.dp))
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          failures.forEachIndexed { index, f ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              TextPrimary(
                text = "${index + 1}. ${f.displayName}",
                fontSize = 13.sp,
                maxLine = 2,
                fontWeight = FontWeight.SemiBold
              )
              TextPrimary(
                text = "${stringResource(R.string.batch_rename_fail_reason)}：${stringResource(f.reason.res)} (${f.reason.code})",
                fontSize = 13.sp,
                maxLine = 3,
                color = theme.errorColor()
              )
              TextSecondary(f.path, fontSize = 12.sp, maxLine = 3)
              if (!f.detail.isNullOrBlank()) {
                TextSecondary(f.detail, fontSize = 12.sp, maxLine = 6)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ActionChip(
  text: String,
  color: Color = LocalTheme.current.textPrimary,
  onClick: () -> Unit
) {
  val theme = LocalTheme.current
  Box(
    modifier = Modifier
      .border(0.5.dp, theme.outline(), RoundedCornerShape(6.dp))
      .clickWithRipple(false, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
  }
}

/** 生成可导出的保存日志文本：概要 + 失败明细 + 完整操作日志 */
private fun buildReportText(
  context: Context,
  summary: SaveSummary,
  logLines: List<SaveLogLine>,
  failures: List<SaveFailure>
): String {
  val sb = StringBuilder()
  sb.appendLine("===== BatchRename Save Log =====")
  sb.appendLine("Time    : ${summary.time}")
  sb.appendLine("Mode    : ${summary.mode}")
  sb.appendLine("Rule    : ${summary.rule}")
  sb.appendLine("Total   : ${summary.total}")
  sb.appendLine("Result  : ok=${summary.ok}, fail=${summary.fail}, skipped=${summary.skipped}")
  sb.appendLine("Elapsed : ${summary.elapsedMs}ms")
  sb.appendLine()
  sb.appendLine("----- Failures (${failures.size}) -----")
  if (failures.isEmpty()) {
    sb.appendLine("(none)")
  } else {
    failures.forEachIndexed { index, f ->
      sb.appendLine("${index + 1}. ${f.displayName}")
      sb.appendLine("   reason: ${context.getString(f.reason.res)} (${f.reason.code})")
      sb.appendLine("   path  : ${f.path}")
      if (!f.detail.isNullOrBlank()) {
        sb.appendLine("   detail: ${f.detail}")
      }
    }
  }
  sb.appendLine()
  sb.appendLine("----- Log (${logLines.size}) -----")
  logLines.forEach { sb.appendLine("${it.time} ${it.level} ${it.message}") }
  return sb.toString()
}

/**
 * 将本次保存日志写入日志目录（best effort），这样即使关闭了“记录日志”开关、
 * 或应用重启/崩溃后仍能找到本次记录；同时会随“导出完整日志”一起打包。
 */
private fun persistReport(context: Context, text: String): File? {
  return runCatching {
    val dir = LogFileWriter.getLogDir(context)
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "batch_rename-${REPORT_FILE_FORMAT.format(Date())}.log")
    file.writeText(text, StandardCharsets.UTF_8)
    dir.listFiles { _, name -> name.startsWith("batch_rename-") && name.endsWith(".log") }
      ?.sortedByDescending { it.name }
      ?.drop(MAX_REPORT_FILES)
      ?.forEach { it.delete() }
    file
  }.getOrNull()
}

@Composable
private fun TextButton(text: String, enabled: Boolean = true, color: Color = LocalTheme.current.textPrimary, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clickWithRipple(false, enabled = enabled, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Text(
      text,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (enabled) color else color.copy(alpha = 0.5f)
    )
  }
}

// 模式1：第三列（新文件名）颜色
private fun mode1CellColor(sm: SongMeta, newName: String, initial: String, used: List<MetaField>): CellColor {
  val willChange = newName != sm.oldName
  if (!willChange) return CellColor.GRAY
  val allPresent = used.all { !sm.meta[it.fieldKey].isNullOrBlank() }
  val edited = newName != initial
  return if (allPresent || edited) CellColor.GREEN else CellColor.RED
}

// 模式2/4：元数据子列颜色（current 为当前文件值，target 为编辑后值）
private fun mode2CellColor(current: String, target: String): CellColor {
  if (target == current) return CellColor.GRAY
  return if (target.isBlank()) CellColor.RED else CellColor.GREEN
}

// ---------------------------------------------------------------------------
// 主题适配：以下颜色在浅色 / 深色（含纯黑）主题下均保证足够的对比度
// ---------------------------------------------------------------------------

/** 未选中标签、无变化单元格等“低调”容器底色 */
@Composable
@ReadOnlyComposable
private fun AppTheme.mutedSurface(): Color =
  if (isLight) Color.Black.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.09f)

/** 表头等需要比普通单元格更明显的容器底色 */
@Composable
@ReadOnlyComposable
private fun AppTheme.headerSurface(): Color =
  if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.14f)

/** 表格文字：浅色主题下用纯黑，深色主题沿用主题主文本色 */
@Composable
@ReadOnlyComposable
private fun AppTheme.cellTextColor(): Color =
  if (isLight) Color.Black else textPrimary

/** 描边 / 表格网格线 */
@Composable
@ReadOnlyComposable
private fun AppTheme.outline(): Color =
  if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.18f)

/** 状态色叠加的不透明度：深色背景需要更高的叠加量才能区分出来 */
@Composable
@ReadOnlyComposable
private fun AppTheme.statusTintAlpha(): Float = if (isLight) 0.18f else 0.34f

/** “有效 / 有变更”标记色 */
@Composable
@ReadOnlyComposable
private fun AppTheme.successColor(): Color =
  if (isLight) colorResource(R.color.md_green_primary) else Color(0xFF57C785)

/** “失败 / 缺失”标记色 */
@Composable
@ReadOnlyComposable
private fun AppTheme.errorColor(): Color =
  if (isLight) colorResource(R.color.md_red_primary) else Color(0xFFF0667C)

/** “无变化”标记色（图例圆点等） */
@Composable
@ReadOnlyComposable
private fun AppTheme.neutralColor(): Color =
  if (isLight) Color.Gray else Color(0xFF9A9AA5)

/** 单元格 / 图例等使用的实色状态标记 */
@Composable
@ReadOnlyComposable
private fun AppTheme.statusColor(color: CellColor): Color = when (color) {
  CellColor.GRAY -> neutralColor()
  CellColor.GREEN -> successColor()
  CellColor.RED -> errorColor()
}

/** 单元格背景：状态色按主题叠加到页面上 */
@Composable
@ReadOnlyComposable
private fun AppTheme.cellBackground(color: CellColor): Color = when (color) {
  CellColor.GRAY -> mutedSurface()
  CellColor.GREEN -> successColor().copy(alpha = statusTintAlpha())
  CellColor.RED -> errorColor().copy(alpha = statusTintAlpha())
}
