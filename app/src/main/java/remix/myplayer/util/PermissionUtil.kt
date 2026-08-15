package remix.myplayer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.hjq.permissions.Permission
import remix.myplayer.App
import remix.myplayer.BuildConfig

object PermissionUtil {
  private fun has(vararg permissions: String): Boolean {
    return permissions.all {
      ContextCompat.checkSelfPermission(
        App.context,
        it
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun hasNecessaryPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      has(Permission.READ_MEDIA_AUDIO)
    } else {
      has(Permission.READ_EXTERNAL_STORAGE)
    }
  }

  /**
   * 是否拥有修改公共目录音乐文件所需的写权限（写标签等操作需要）。
   * Android 11+ 需要"所有文件访问"（MANAGE_EXTERNAL_STORAGE），
   * Android 10 及以下需要 WRITE_EXTERNAL_STORAGE。
   */
  fun canWriteAudioFiles(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      has(Permission.WRITE_EXTERNAL_STORAGE)
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  fun hasManageExternalStorage(): Boolean {
    return Environment.isExternalStorageManager()
  }

  @RequiresApi(Build.VERSION_CODES.R)
  fun requestManageExternalStorage(context: Context) {
    context.startActivity(
      Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(
        Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
      )
    )
    // TODO: show toast when "a matching Activity not exists"
  }
}