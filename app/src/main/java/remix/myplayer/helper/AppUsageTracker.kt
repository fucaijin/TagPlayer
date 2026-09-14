package remix.myplayer.helper

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import remix.myplayer.data.db.room.entity.AppOpenSession
import remix.myplayer.repo.PlayStatsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用使用会话统计（数据分析用）：
 * 应用从后台进入前台时记录开始时间，全部界面退到后台时写入一条使用会话。
 *
 * 用"已启动 Activity 计数"判断前后台，可避免旋转屏幕等重建被误记为多次打开。
 */
@Singleton
class AppUsageTracker @Inject constructor(
  private val repo: PlayStatsRepository,
) : Application.ActivityLifecycleCallbacks {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var startedActivityCount = 0
  private var foregroundTime = 0L

  override fun onActivityStarted(activity: Activity) {
    if (startedActivityCount == 0) {
      foregroundTime = System.currentTimeMillis()
    }
    startedActivityCount++
  }

  override fun onActivityStopped(activity: Activity) {
    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
    if (startedActivityCount == 0 && foregroundTime > 0) {
      val openTime = foregroundTime
      foregroundTime = 0
      scope.launch {
        repo.addAppSession(
          AppOpenSession(openTime = openTime, closeTime = System.currentTimeMillis())
        )
      }
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityResumed(activity: Activity) = Unit

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit
}
