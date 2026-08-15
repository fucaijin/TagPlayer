package remix.myplayer.ui.dialog

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import remix.myplayer.R
import remix.myplayer.util.PermissionUtil

/**
 * 修改音乐文件标签需要写权限（Android 11+ 为"所有文件访问"）时的引导弹窗。
 * 确认后跳转系统设置/重新请求权限。
 */
@Composable
fun ManageStorageDialog(
  dialogState: DialogState,
  onCancel: () -> Unit,
) {
  val context = LocalContext.current
  NormalDialog(
    dialogState = dialogState,
    title = stringResource(R.string.need_manage_storage_title),
    content = stringResource(R.string.need_manage_storage_content),
    positive = stringResource(R.string.go_to_setting),
    negative = stringResource(R.string.cancel),
    onPositive = {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        PermissionUtil.requestManageExternalStorage(context)
      } else {
        XXPermissions.with(context)
          .permission(Permission.WRITE_EXTERNAL_STORAGE)
          .request(object : OnPermissionCallback {
            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) = Unit
            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) = Unit
          })
      }
    },
    onNegative = onCancel,
  )
}
