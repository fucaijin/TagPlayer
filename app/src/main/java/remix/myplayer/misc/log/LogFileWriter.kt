package remix.myplayer.misc.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import remix.myplayer.data.prefs.PrefKeys
import remix.myplayer.util.ext.zipFrom
import remix.myplayer.util.ext.zipOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFileWriter {

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val logChannel = Channel<LogEntry>(capacity = Channel.UNLIMITED)
  private var logDir: File? = null

  /** 是否写入日志文件，由设置-其他-"记录日志"开关控制 */
  @Volatile
  private var enabled = DEFAULT_ENABLED

  private val logTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.US)
  private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  private val logFileNameRegex = Regex("\\d{4}-\\d{2}-\\d{2}\\.log")

  private const val MAX_KEEP_DAYS = 30
  private const val LOG_FOLDER_NAME = "logs"
  private const val DEFAULT_ENABLED = true

  fun init(context: Context) {
    // 开关状态直接从偏好读取，保证进程启动时与设置页一致
    enabled = context.getSharedPreferences(PrefKeys.Setting.NAME, Context.MODE_PRIVATE)
      .getBoolean(PrefKeys.Setting.LOG_ENABLED, DEFAULT_ENABLED)

    logDir = getLogDir(context)

    if (logDir?.exists() == false) {
      logDir?.mkdirs()
    }

    scope.launch {
      cleanOldLogs()
      for (entry in logChannel) {
        writeLog(entry)
      }
    }
  }

  fun isEnabled(): Boolean = enabled

  fun setEnabled(value: Boolean) {
    enabled = value
  }

  fun getLogDir(context: Context): File {
    val appDataDir = context.externalCacheDir?.parentFile
    return if (appDataDir != null) {
      File(appDataDir, LOG_FOLDER_NAME)
    } else {
      File(context.getExternalFilesDir(null), LOG_FOLDER_NAME)
    }
  }

  fun log(priority: Int, tag: String?, message: String, t: Throwable? = null) {
    if (!enabled) return

    logChannel.trySend(
      LogEntry(
        timestamp = System.currentTimeMillis(),
        priority = priority,
        tag = tag,
        message = message,
        threadName = Thread.currentThread().name,
        threadId = Thread.currentThread().id
      )
    )
  }

  private fun writeLog(entry: LogEntry) {
    val dir = logDir ?: return
    if (!dir.exists()) dir.mkdirs()

    val dateStr = fileNameFormat.format(Date(entry.timestamp))
    val fileName = "$dateStr.log"
    val file = File(dir, fileName)

    try {
      FileWriter(file, true).use { writer ->
        val timeStr = logTimeFormat.format(Date(entry.timestamp))
        val relativeTime = entry.timestamp - START_TIME
        val relativeTimeStr = String.format(Locale.US, "%-10d", relativeTime)

        val levelStr = String.format(Locale.US, "%-6s", getLevelString(entry.priority))

        val tagStr = String.format(Locale.US, "[%-23.23s]", entry.tag ?: "App")

        val line =
          "$timeStr $relativeTimeStr $levelStr $tagStr: [${entry.threadName}] ${entry.message}\n"
        writer.append(line)
      }
    } catch (e: Exception) {
      Log.e("LogFileWritter", "Failed to write log to file", e)
    }
  }

  /** 清空日志目录下的所有日志文件 */
  fun clearLogs(): Boolean {
    val dir = logDir ?: return false
    if (!dir.exists()) return true
    return runCatching {
      dir.listFiles { _, name -> logFileNameRegex.matches(name) }?.forEach { it.delete() }
    }.isSuccess
  }

  /** 将日志目录打包为 zip 文件（存放于 cacheDir），返回 zip 文件；无日志或失败时返回 null */
  fun exportZip(context: Context): File? {
    val dir = getLogDir(context)
    if (!dir.exists() || dir.listFiles()?.isEmpty() == true) return null
    return runCatching {
      val nameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
      val zipFile = File(context.cacheDir, "logs-${nameFormat.format(Date())}.zip")
      zipFile.delete()
      zipFile.createNewFile()
      zipFile.zipOutputStream().zipFrom(dir.absolutePath)
      zipFile
    }.getOrNull()
  }

  private val START_TIME = System.currentTimeMillis()

  private fun cleanOldLogs() {
    val dir = logDir ?: return
    if (!dir.exists()) return

    val files = dir.listFiles { _, name -> logFileNameRegex.matches(name) } ?: return
    if (files.size <= MAX_KEEP_DAYS) return

    files.sortBy { it.name }

    val filesToDelete = files.take(files.size - MAX_KEEP_DAYS)
    filesToDelete.forEach {
      try {
        it.delete()
      } catch (ignore: Exception) {
      }
    }
  }

  private fun getLevelString(priority: Int): String {
    return when (priority) {
      Log.VERBOSE -> "TRACE"
      Log.DEBUG -> "DEBUG"
      Log.INFO -> "INFO"
      Log.WARN -> "WARN"
      Log.ERROR -> "ERROR"
      Log.ASSERT -> "ASSERT"
      else -> "UNKNOWN"
    }
  }

  private data class LogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val threadName: String,
    val threadId: Long
  )
}
