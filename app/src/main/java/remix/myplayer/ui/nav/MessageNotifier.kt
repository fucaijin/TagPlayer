package remix.myplayer.ui.nav

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import remix.myplayer.App
import remix.myplayer.util.Util

/**
 * app在前台时显示snackbar，否则展示toast
 */
object MessageNotifier {

  private const val MIN_INTERVAL_MS = 1000L

  @Volatile
  private var lastShownAt = 0L

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
  internal val messages = _messages.asSharedFlow()

  /** 屏幕正中央的短提示（标签相关操作使用，约 1 秒） */
  private val _centerMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
  internal val centerMessages = _centerMessages.asSharedFlow()

  private val mainHandler = Handler(Looper.getMainLooper())

  fun show(message: String) {
    val now = SystemClock.uptimeMillis()
    if (now - lastShownAt < MIN_INTERVAL_MS) {
      return
    }
    lastShownAt = now

    if (Util.isAppOnForeground) {
      _messages.tryEmit(message)
    } else {
      showToast(message, Gravity.BOTTOM)
    }
  }


  fun show(@StringRes resId: Int, vararg formatArgs: Any) {
    show(getMessage(resId, formatArgs))
  }

  /** 屏幕正中央的短提示（标签保存等操作结果，约 1 秒） */
  fun showCenter(message: String) {
    if (Util.isAppOnForeground) {
      _centerMessages.tryEmit(message)
    } else {
      showToast(message, Gravity.CENTER)
    }
  }

  /** 屏幕正中央的短提示（标签保存等操作结果，约 1 秒） */
  fun showCenter(@StringRes resId: Int, vararg formatArgs: Any) {
    showCenter(getMessage(resId, formatArgs))
  }

  private fun getMessage(@StringRes resId: Int, formatArgs: Array<out Any>): String {
    return if (formatArgs.isNotEmpty()) {
      App.context.getString(resId, *formatArgs)
    } else {
      App.context.getString(resId)
    }
  }

  private fun showToast(message: String, gravity: Int) {
    val show = {
      Toast.makeText(App.context, message, Toast.LENGTH_SHORT).apply {
        setGravity(gravity, 0, 0)
      }.show()
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      show()
    } else {
      mainHandler.post { show() }
    }
  }
}