package remix.myplayer.misc.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import remix.myplayer.App
import remix.myplayer.R
import remix.myplayer.data.model.github.Release
import remix.myplayer.data.prefs.InAppUpdatePrefs
import remix.myplayer.request.network.GithubApi
import remix.myplayer.ui.nav.MessageNotifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppUpdater @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val inAppUpdatePrefs: InAppUpdatePrefs,
  private val githubApi: GithubApi
) {

  private val workManager by lazy {
    WorkManager.getInstance(context)
  }

  private val notificationManager: NotificationManager by lazy {
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  init {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createNotificationChannelIfNeed()
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createNotificationChannelIfNeed() {
    val updateNotificationChannel = NotificationChannel(
      UPDATE_NOTIFICATION_CHANNEL_ID,
      context.getString(R.string.update_notification),
      NotificationManager.IMPORTANCE_LOW
    )
    updateNotificationChannel.setShowBadge(false)
    updateNotificationChannel.enableLights(false)
    updateNotificationChannel.enableVibration(false)
    updateNotificationChannel.description =
      context.getString(R.string.update_notification_description)
    notificationManager.createNotificationChannel(updateNotificationChannel)
  }

  fun cancelDownloadWorker() {
    workManager.cancelUniqueWork(UNIQUE_NAME)
  }

  fun startDownloadWorker(release: Release): Flow<WorkInfo?> {
    val json = Json.encodeToString(release)
    val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
      .setInputData(workDataOf("release" to json))
      .build()

    workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, downloadRequest)
    return workManager.getWorkInfoByIdFlow(downloadRequest.id)
  }

  suspend fun checkUpdate(force: Boolean): Release? {
    val showToast = force

    if (!force && inAppUpdatePrefs.ignoreForever) {
      return null
    }

    val release = try {
      githubApi.fetchLatestRelease(OWNER, REPO)
    } catch (e: Exception) {
      // 访问不到 GitHub（如国内网络受限）时不提示、不更新
      Timber.tag(TAG).v("e: $e")
      return null
    }
    Timber.v("assets: ${release.assets?.map { "asset: ${it.name}" }}")

    // 取第一个名称包含normal的
    val asset = release.assets?.run {
      firstOrNull { it.name?.contains("normal", ignoreCase = true) == true }
        ?: firstOrNull()
    }
    if (asset == null) {
      if (showToast) {
        MessageNotifier.show(R.string.no_update)
      }
      return null
    }

    // 从 tag（形如 v2.1.1.0）解析出版本号比较，解析失败视为无更新
    val versionCode = getOnlineVersionCode(release)
    if (versionCode <= 0 || versionCode <= getLocalVersionCode()) {
      if (showToast) {
        MessageNotifier.show(R.string.no_update)
      }
      return null
    }

    // ignore this update?
    if (!force && inAppUpdatePrefs.isVersionIgnored(versionCode)) {
      return null
    }

    // check args
    if (asset.size < 0 || asset.browser_download_url.isNullOrEmpty()) {
      if (showToast) {
        MessageNotifier.show("illegal args")
      }
      return null
    }

    return release
  }

  fun ignoreVersion(versionCode: Int) {
    inAppUpdatePrefs.setIgnoreVersion(versionCode, true)
  }

  fun ignoreForever() {
    inAppUpdatePrefs.ignoreForever = true
  }

  private fun getLocalVersionCode(): Int {
    var versionCode = 0
    try {
      versionCode =
        App.context.packageManager.getPackageInfo(App.context.packageName, 0).versionCode
    } catch (e: PackageManager.NameNotFoundException) {
      Timber.v(e)
    }
    return versionCode
  }

  /**
   * 从 release 的 tag 解析 versionCode。
   * tag 形如 v2.1.1.0（兼容 v2.1.1.0-tag 等后缀），映射为 a*10000 + b*1000 + c*100 + d*10；
   * 解析失败返回 0（视为无更新）。
   */
  fun getOnlineVersionCode(release: Release): Int {
    val raw = release.tag_name?.takeIf { it.isNotBlank() } ?: release.name.orEmpty()
    val match = VERSION_REGEX.find(raw) ?: return 0
    val (major, minor, patch, build) = match.destructured
    return major.toInt() * 10000 + minor.toInt() * 1000 + patch.toInt() * 100 + build.toInt() * 10
  }

  companion object {

    private const val TAG = "InAppUpdater"

    /** 更新检查指向自己的仓库 */
    private const val OWNER = "fucaijin"
    private const val REPO = "TagPlayer"

    private val VERSION_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)\.(\d+)""")

    private const val UNIQUE_NAME = "download_apk"

    private const val UPDATE_NOTIFICATION_CHANNEL_ID = "update_notification"
    private const val UPDATE_NOTIFICATION_ID = 3
  }
}
