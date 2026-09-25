package remix.myplayer

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.hjq.permissions.XXPermissions
import dagger.hilt.android.HiltAndroidApp
import remix.myplayer.R
import remix.myplayer.helper.AppMigration
import remix.myplayer.helper.AppUsageTracker
import remix.myplayer.helper.LanguageHelper.saveSystemCurrentLanguage
import remix.myplayer.helper.ThirdPartyInitializer
import remix.myplayer.i18n.LocaleManager
import remix.myplayer.i18n.StringKeys
import java.util.Locale
import remix.myplayer.misc.manager.APlayerActivityManager
import remix.myplayer.ui.appshortcuts.DynamicShortcutManager
import remix.myplayer.ui.screen.home.hackTabMinWidth
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by Remix on 16-3-16.
 */
@HiltAndroidApp
class App : Application() {

  @Inject
  lateinit var appMigration: AppMigration

  @Inject
  lateinit var appUsageTracker: AppUsageTracker

  override fun attachBaseContext(base: Context) {
    saveSystemCurrentLanguage()
    // 必须在任何字符串解析之前注册，否则 StringProvider 只能拿到 key 本身
    StringKeys.register(buildStringResIdMap())
    super.attachBaseContext(LocaleManager.applyLocale(base, LocaleManager.activeTag(base)))
  }

  override fun onCreate() {
    super.onCreate()
    context = this

    // 让「导出翻译模板」能够枚举全部 string 资源（以英文为源语言）
    LocaleManager.defaultStringsProvider = { buildDefaultStringMap(this) }

    appMigration.check()
    setUp()

    // AppShortcut
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      DynamicShortcutManager(this).setUpShortcut()
    }

    // 加载第三方库
    ThirdPartyInitializer.init(this@App)

    registerActivityLifecycleCallbacks(APlayerActivityManager())

    // 数据分析：记录应用使用会话
    registerActivityLifecycleCallbacks(appUsageTracker)

    hackTabMinWidth()
  }

  private fun setUp() {
    XXPermissions.setCheckMode(false)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // 系统语言/区域变化（含横竖屏）会重置共享 Resources 的配置，这里重新套用当前选择
    LocaleManager.syncProcessLocale(this)
  }

  /**
   * 反射枚举 `R.string` 的「资源名 -> id」，注册给 [StringKeys]。
   *
   * 有了这张表，[remix.myplayer.i18n.StringProvider] 才能按资源名回退到真实的
   * `strings.xml`（而不是返回裸 key），语言设置页等按 key 取值的界面才会跟随所选语言。
   */
  private fun buildStringResIdMap(): Map<String, Int> {
    val map = HashMap<String, Int>(1024)
    runCatching {
      R.string::class.java.fields.forEach { field ->
        runCatching { map[field.name] = field.getInt(null) }
      }
    }
    return map
  }

  /**
   * 枚举全部 string 资源名，并以英文配置读取其默认值，供「导出翻译模板」使用。
   */
  private fun buildDefaultStringMap(context: Context): Map<String, String> {
    val enConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.ENGLISH)
      setLayoutDirection(Locale.ENGLISH)
    }
    val enRes = context.createConfigurationContext(enConfig).resources
    val map = LinkedHashMap<String, String>()
    try {
      R.string::class.java.fields.forEach { f ->
        val name = f.name
        val id = f.getInt(null)
        val value = runCatching { enRes.getString(id) }.getOrNull() ?: return@forEach
        map[name] = value
      }
    } catch (_: Exception) {
    }
    return map
  }

  override fun onLowMemory() {
    super.onLowMemory()
    Timber.v("onLowMemory")
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    Timber.v("onTrimMemory, %s", level)
  }

  companion object {

    @JvmStatic
    lateinit var context: App
      private set
  }
}