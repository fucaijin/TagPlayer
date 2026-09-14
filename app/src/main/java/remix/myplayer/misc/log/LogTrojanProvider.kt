package remix.myplayer.misc.log

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import remix.myplayer.BuildConfig
import timber.log.Timber

class LogTrojanProvider : ContentProvider() {

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    return 0
  }

  override fun getType(uri: Uri): String? {
    return null
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    return Uri.EMPTY
  }


  override fun onCreate(): Boolean {
    val ctx = context
    ctx?.let { LogFileWriter.init(it) }
    Timber.plant(LogTree())
    // 记录进程信息：同一台设备上装了多个 TagPlayer 时，据此区分日志来自哪个包
    Timber.i(
      "App start: package=%s version=%s(%d) sdk=%d logEnabled=%s",
      ctx?.packageName,
      BuildConfig.VERSION_NAME,
      BuildConfig.VERSION_CODE,
      Build.VERSION.SDK_INT,
      LogFileWriter.isEnabled()
    )
    return true
  }

  override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
    return null
  }

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
    return 0
  }
}
