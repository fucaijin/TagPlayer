package remix.myplayer.ui.dialog

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.ConvertFormat
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.viewmodel.libraryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 音频转换弹窗的阶段 */
enum class ConvertStage { IDLE, ASK, CONFLICT, CONVERTING }

/** 触发转换的入口，转换结束后据此关闭来源弹窗 */
enum class ConvertSource { SINGLE, BATCH }

/** 同名文件冲突的处理方式 */
enum class ConvertConflictChoice { RENAME, OVERWRITE, CANCEL }

/** 待处理项：[needConvert] 为 true 表示格式不支持标签，需要先转成目标格式 */
data class ConvertItem(
  val song: Song,
  val tags: Set<String>,
  val needConvert: Boolean
)

/** 同名文件信息（用于冲突弹窗展示） */
data class ConvertConflict(
  val name: String,
  val sizeBytes: Long,
  val lastModified: Long
)

/** 转换弹窗状态 */
@Stable
data class ConvertState(
  val dialogState: DialogState = DialogState(),
  val stage: ConvertStage = ConvertStage.IDLE,
  val source: ConvertSource = ConvertSource.SINGLE,
  /** 转换的目标格式（默认 AAC） */
  val format: ConvertFormat = ConvertFormat.AAC,
  val items: List<ConvertItem> = emptyList(),
  /** 需要转换的文件数量 */
  val convertCount: Int = 0,
  /** 当前处理到的序号（从 1 起） */
  val currentIndex: Int = 0,
  /** 当前文件转换进度 0..100 */
  val progress: Int = 0,
  val conflict: ConvertConflict? = null
)

/**
 * 音频转换弹窗：
 * 1. 询问是否转换（可选目标格式 AAC/MP3）；2. 转换进度；3. 目标同名文件冲突时询问重命名/覆盖。
 */
@Composable
fun ConvertDialog() {
  val libraryVM = libraryViewModel
  val state by libraryVM.convertState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  when (state.stage) {
    ConvertStage.IDLE -> Unit

    ConvertStage.ASK -> NormalDialog(
      dialogState = state.dialogState,
      autoDismiss = false,
      titleRes = R.string.convert_title,
      positiveRes = R.string.convert_start,
      onPositive = { libraryVM.confirmConvert() },
      negativeRes = R.string.cancel,
      onNegative = { libraryVM.cancelConvert() },
      onDismissRequest = { libraryVM.cancelConvert() },
      custom = {
        TextPrimary(
          stringResource(R.string.convert_ask, state.convertCount),
          fontSize = 15.sp,
          maxLine = Int.MAX_VALUE
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextSecondary(stringResource(R.string.convert_format), fontSize = 13.sp)
        ConvertFormat.entries.forEach { item ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { libraryVM.selectConvertFormat(item) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = state.format == item,
              onClick = { libraryVM.selectConvertFormat(item) }
            )
            Column {
              TextPrimary(formatLabel(item), fontSize = 15.sp)
              if (item == ConvertFormat.AAC) {
                TextSecondary(stringResource(R.string.format_aac_tip), fontSize = 12.sp)
              }
            }
          }
        }
      }
    )

    ConvertStage.CONFLICT -> {
      val conflict = state.conflict
      NormalDialog(
        dialogState = state.dialogState,
        autoDismiss = false,
        titleRes = R.string.conflict_title,
        neutralRes = R.string.conflict_rename,
        onNeutral = { libraryVM.chooseConflictRename() },
        positiveRes = R.string.conflict_overwrite,
        onPositive = { libraryVM.chooseConflictOverwrite() },
        negativeRes = R.string.cancel,
        onNegative = { libraryVM.chooseConflictCancel() },
        onDismissRequest = { libraryVM.chooseConflictCancel() },
        custom = {
          if (conflict != null) {
            TextPrimary(
              stringResource(
                R.string.conflict_message,
                conflict.name,
                Formatter.formatFileSize(context, conflict.sizeBytes),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                  .format(Date(conflict.lastModified))
              ),
              fontSize = 15.sp,
              maxLine = Int.MAX_VALUE
            )
          }
        }
      )
    }

    ConvertStage.CONVERTING -> NormalDialog(
      dialogState = state.dialogState,
      autoDismiss = false,
      titleRes = R.string.convert_title,
      positiveRes = null,
      negativeRes = R.string.cancel,
      onNegative = { libraryVM.cancelConvert() },
      onDismissRequest = { libraryVM.cancelConvert() },
      custom = {
        TextPrimary(
          stringResource(
            R.string.convert_progress,
            state.currentIndex,
            state.convertCount,
            state.progress
          ),
          fontSize = 15.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    )
  }
}

@Composable
private fun formatLabel(format: ConvertFormat): String = when (format) {
  ConvertFormat.AAC -> stringResource(R.string.format_aac)
  ConvertFormat.MP3 -> stringResource(R.string.format_mp3)
}
