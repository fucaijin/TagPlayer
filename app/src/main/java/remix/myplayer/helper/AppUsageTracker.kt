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
 * 一次“使用会话”从应用进入前台、或后台开始播放音乐时开始，
 * 在“界面退到后台 且 音乐停止播放”时才结束并写入一条会话。
 *
 * 修复：听歌多在后台进行，旧实现把“熄屏（Activity 停止）”当成会话结束，
 * 导致每条会话只有约 1 分钟的亮屏片段、且合并窗口过小无法合并，
 * “平均每次使用时长”恒为 ~1 分钟。现把“正在播放”也视为会话进行中，
 * 使后台听歌时长被正确计入。
 *
 * 用"已启动 Activity 计数"判断前后台，可避免旋转屏幕等重建被误记为多次打开。
 */
@Singleton
class AppUsageTracker @Inject constructor(
  private val repo: PlayStatsRepository,
) : Application.ActivityLifecycleCallbacks {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var startedActivityCount = 0
  private var activityForeground = false
  private var musicPlaying = false
  private var foregroundTime = 0L

  override fun onActivityStarted(activity: Activity) {
    if (startedActivityCount == 0 && foregroundTime == 0L) {
      foregroundTime = System.currentTimeMillis()
    }
    startedActivityCount++
    activityForeground = true
  }

  override fun onActivityStopped(activity: Activity) {
    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
    if (startedActivityCount == 0) {
      activityForeground = false
      tryCloseSession()
    }
  }

  /** 由 [remix.myplayer.service.MusicService] 在播放状态变化时调用，标记后台是否正在播放 */
  fun setMusicPlaying(playing: Boolean) {
    musicPlaying = playing
    if (playing && foregroundTime == 0L) {
      foregroundTime = System.currentTimeMillis()
    }
    if (!playing) {
      tryCloseSession()
    }
  }

  /** 当前既没有界面在前台、也没有音乐在播放时，才结束并写入本次使用会话 */
  private fun tryCloseSession() {
    if (!activityForeground && !musicPlaying && foregroundTime > 0) {
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
