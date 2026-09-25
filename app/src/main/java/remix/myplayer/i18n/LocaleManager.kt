package remix.myplayer.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.DisplayMetrics
import org.json.JSONObject
import java.util.Locale

/**
 * 多语言管理器：
 *
 * - 内置语言（跟随系统 / 英文 / 简体中文 / 繁體中文 / 日本語）通过 `Configuration` 包裹 `Context` 实现；
 * - 运行时导入的任意语言（JSON 模板，通常由 AI 翻译）通过 [OverlayResources] 拦截 `Resources.getString`
 *   在「整个 App」范围内覆盖，无需重新打包 APK，也无需改动任何 `stringResource` 调用点；
 * - 支持一键导出待翻译模板（以英文资源为源语言）、内置 AI 翻译提示词。
 *
 * 接入要点（宿主只需极少改动）：
 * 1. 在 `attachBaseContext` 中调用 `super.attachBaseContext(LocaleManager.applyLocale(newBase, LocaleManager.activeTag(newBase)))`
 * 2. 在 Compose 根部 `CompositionLocalProvider(LocalStringProvider provides StringProvider(...))`
 * 3. （可选）设置 `LocaleManager.defaultStringsProvider`，让「导出模板」能枚举全部 string 资源
 */
object LocaleManager {
  private const val KEY_ACTIVE = "active_language" // "system" | "en" | "zh" | "zh-rTW" | "ja" | <导入 tag>
  private const val KEY_IMPORTS = "imported_languages" // JSON: { "<tag>": {"displayName": "...", "strings": {name:value}} }

  const val SYSTEM = "system"

  /** 偏好文件名，建议在启动时保持默认即可。 */
  var prefsName: String = "tagplayer_locale"

  /** 内置语言列表（含 SYSTEM 项）。 */
  var builtIn: List<LanguageOption> = listOf(
    LanguageOption(SYSTEM, "System default", false),
    LanguageOption("en", "English", false),
    LanguageOption("zh", "简体中文", false),
    LanguageOption("zh-rTW", "繁體中文", false),
    LanguageOption("ja", "日本語", false),
  )

  /**
   * 默认（英文）字符串来源：返回「资源名 -> 英文文案」。
   * 宿主应在 Application 中设置，例如通过反射枚举 `R.string` 并以英文配置读取。
   * 不设置时导出的模板为空。
   */
  var defaultStringsProvider: () -> Map<String, String> = { emptyMap() }

  data class LanguageOption(val tag: String, val displayName: String, val imported: Boolean)

  data class ImportedLanguage(
    val tag: String,
    val displayName: String,
    val strings: Map<String, String>,
  )

  fun activeTag(ctx: Context): String =
    prefs(ctx).getString(KEY_ACTIVE, SYSTEM) ?: SYSTEM

  fun setActive(ctx: Context, tag: String) {
    prefs(ctx).edit().putString(KEY_ACTIVE, tag).apply()
  }

  /**
   * 写入偏好并**立即**把语言应用到整个进程。
   *
   * 只调 `setActive` 不够：`attachBaseContext` 里的包裹只会在 Activity / Service
   * 重建时才重新执行，而 Application 以及所有 `@ApplicationContext` 注入的 Context
   * 会一直持有启动时的 Resources。这里额外同步一次共享 Resources，
   * 让非 Activity 场景（设置项文案、Toast、通知、桌面部件等）也立刻跟着变。
   *
   * 注：界面仍需调用方 `Activity.recreate()` 才会重新走一次资源解析。
   */
  fun setActiveAndApply(ctx: Context, tag: String) {
    setActive(ctx, tag)
    syncProcessLocale(ctx)
  }

  /**
   * 把当前选择的语言重新套用到「进程内已经存在的」Resources 上。
   *
   * @see syncSharedResources
   */
  fun syncProcessLocale(ctx: Context) {
    syncSharedResources(ctx, configurationFor(ctx, activeTag(ctx)))
  }

  fun listLanguages(ctx: Context): List<LanguageOption> {
    val list = builtIn.toMutableList()
    val imports = readImports(ctx)
    imports.keys().forEach { tag ->
      val obj = imports.getJSONObject(tag)
      list.add(LanguageOption(tag, obj.optString("displayName", tag), true))
    }
    return list
  }

  fun importedStrings(ctx: Context, tag: String): Map<String, String>? {
    val imports = readImports(ctx)
    if (!imports.has(tag)) return null
    val strings = imports.getJSONObject(tag).optJSONObject("strings") ?: return null
    val map = LinkedHashMap<String, String>()
    strings.keys().forEach { k -> map[k] = strings.getString(k) }
    return map
  }

  fun importLanguage(ctx: Context, language: ImportedLanguage) {
    importLanguage(ctx, language.tag, language.displayName, language.strings)
  }

  fun importLanguage(ctx: Context, tag: String, displayName: String, strings: Map<String, String>) {
    val imports = readImports(ctx)
    val entry = JSONObject().apply {
      put("displayName", displayName)
      put("strings", JSONObject(strings))
    }
    imports.put(tag, entry)
    prefs(ctx).edit().putString(KEY_IMPORTS, imports.toString()).apply()
  }

  fun deleteImported(ctx: Context, tag: String) {
    val imports = readImports(ctx)
    imports.remove(tag)
    prefs(ctx).edit().putString(KEY_IMPORTS, imports.toString()).apply()
  }

  /** 解析翻译模板（JSON）。字段缺失时返回 null。 */
  fun parseTemplate(json: String): ImportedLanguage? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val tag = obj.optString("languageTag", "").trim()
    val stringsObj = obj.optJSONObject("strings") ?: return null
    if (tag.isEmpty()) return null
    val map = LinkedHashMap<String, String>()
    stringsObj.keys().forEach { k -> map[k] = stringsObj.getString(k) }
    val displayName = obj.optString("displayName", "").trim().ifBlank { tag }
    return ImportedLanguage(tag, displayName, map)
  }

  /**
   * 导入前的完整性校验，返回人类可读的问题列表（为空表示通过）。
   *
   * 以英文源文案（[defaultStringsProvider]）为基准检查两类问题：
   * 1. key 缺失 —— 这些界面会静默回落到英文；
   * 2. 占位符被改写或丢失 —— `String.format` 会抛异常，
   *    界面上会直接显示 `%1$d` 这类未替换的原始文本。
   *
   * 占位符比较的是排序后的多重集合，因此按目标语言调整语序是允许的，
   * 但数量与编号（`%s` / `%1$s` / `%1$d` / `%%` …）必须与原文完全一致。
   */
  fun validateTranslation(language: ImportedLanguage): List<String> {
    val source = defaultStringsProvider()
    if (source.isEmpty()) return emptyList()
    val problems = ArrayList<String>()

    val missing = source.keys.filter { it !in language.strings }
    if (missing.isNotEmpty()) {
      problems.add(
        "missing ${missing.size} key(s): " +
          missing.take(5).joinToString(", ") +
          if (missing.size > 5) ", ..." else "",
      )
    }

    language.strings.forEach { (key, translated) ->
      val src = source[key] ?: return@forEach
      val expected = placeholdersIn(src)
      val actual = placeholdersIn(translated)
      if (expected != actual) {
        problems.add("$key: ${render(expected)} -> ${render(actual)}")
      }
    }
    return problems
  }

  /** printf 风格占位符，含 `%%`（转义后的字面百分号）。 */
  private val PLACEHOLDER = Regex("""%%|%(\d+\$)?[a-zA-Z]""")

  private fun placeholdersIn(text: String): List<String> =
    PLACEHOLDER.findAll(text).map { it.value }.toList().sorted()

  private fun render(placeholders: List<String>): String =
    if (placeholders.isEmpty()) "(none)" else placeholders.joinToString(" ")

  /** 生成英文源模板 JSON（缩进 2）。 */
  fun exportTemplate(ctx: Context): String {
    val defaults = defaultStringsProvider()
    val strings = JSONObject()
    defaults.forEach { (name, value) -> strings.put(name, value) }
    return JSONObject()
      .put("languageTag", "")
      .put("displayName", "")
      .put("strings", strings)
      .toString(2)
  }

  /** 包裹 [base]，返回应用语言后的 Context。 */
  fun applyLocale(base: Context, tag: String?): Context {
    val t = tag ?: SYSTEM
    val config = configurationFor(base, t)
    val override = if (t != SYSTEM) importedStrings(base, t) else null
    if (override != null) {
      // 导入语言：以英文资源为底，叠加覆盖表
      val engCtx = base.createConfigurationContext(config)
      val overlay = OverlayResources(engCtx.resources, override)
      syncSharedResources(base, config)
      return object : ContextWrapper(engCtx) {
        override fun getResources(): Resources = overlay
      }
    }
    syncSharedResources(base, config)
    return base.createConfigurationContext(config)
  }

  /**
   * 生成 [tag] 对应的 [Configuration]，同时把 `Locale.setDefault` /
   * `LocaleList.setDefault` 一并更新。
   *
   * - 内置语言：取 [builtInLocale]；
   * - 导入语言：没有对应资源目录，以英文资源为底（由 [OverlayResources] 逐条覆盖）；
   * - 跟随系统：取系统首选语言（用 [Resources.getSystem] 读取，不受 App 内覆写影响）。
   */
  fun configurationFor(ctx: Context, tag: String?): Configuration {
    val t = tag ?: SYSTEM
    val locale = when {
      t == SYSTEM -> systemLocale()
      builtInLocale(t) != null -> builtInLocale(t)!!
      importedStrings(ctx, t) != null -> Locale.ENGLISH
      else -> systemLocale()
    }
    applyDefaults(locale)
    return localizedConfiguration(ctx, locale)
  }

  /** 系统当前首选语言。使用 [Resources.getSystem] 读取，不受本 App 的语言覆写影响。 */
  private fun systemLocale(): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Resources.getSystem().configuration.locales[0]
    } else {
      @Suppress("DEPRECATION")
      Resources.getSystem().configuration.locale
    }

  private fun applyDefaults(locale: Locale) {
    Locale.setDefault(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      LocaleList.setDefault(LocaleList(locale))
    }
  }

  private fun localizedConfiguration(ctx: Context, locale: Locale): Configuration =
    Configuration(ctx.resources.configuration).apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        setLocales(LocaleList(locale))
      }
      setLocale(locale)
      setLayoutDirection(locale)
    }

  /**
   * 就地更新进程内共享的 [Resources] 配置。
   *
   * `createConfigurationContext` 只能影响「新创建出来的」Context：Application 以及所有
   * 通过 `@ApplicationContext` 注入的 Context 会一直持有进程启动时那份 Resources，
   * 切换语言后不会重建，因此界面上就会出现「只有语言设置页变了、其它页面还是旧语言」。
   * 这里补一次就地更新，让它们同步生效。
   */
  private fun syncSharedResources(base: Context, config: Configuration) {
    runCatching {
      val res = base.resources
      @Suppress("DEPRECATION")
      res.updateConfiguration(config, res.displayMetrics)
    }
    runCatching {
      val appRes = base.applicationContext?.resources
      if (appRes != null && appRes !== base.resources) {
        @Suppress("DEPRECATION")
        appRes.updateConfiguration(config, appRes.displayMetrics)
      }
    }
  }

  /** 构建交给 AI 的翻译提示词。 */
  fun buildAiPrompt(uiLanguage: String, targetLanguage: String): String {
    val zh = uiLanguage.startsWith("zh")
    val target = targetLanguage.trim().ifBlank { if (zh) "我指定的目标语言" else "the target language I specify" }
    return if (zh) buildZh(target) else buildEn(target)
  }

  private fun builtInLocale(tag: String): Locale? = when (tag) {
    SYSTEM -> null
    "en" -> Locale.ENGLISH
    "zh" -> Locale.SIMPLIFIED_CHINESE
    "zh-rTW" -> Locale.TRADITIONAL_CHINESE
    "ja" -> Locale.JAPANESE
    else -> null
  }

  private fun buildEn(target: String): String = """
    You are a professional software localization expert. Translate the app UI string JSON template I provide into $target.

    [File structure]
    {
      "languageTag": "",
      "displayName": "",
      "strings": { "key": "English source text" }
    }

    [Your tasks]
    1. Translate every VALUE inside "strings" into $target. All KEYS must be kept exactly as-is: do not modify, remove, or add keys.
    2. Fill "languageTag" with the target language's BCP-47 code (e.g. "ja" for Japanese, "fr" for French, "ko" for Korean, "es" for Spanish).
    3. Fill "displayName" with the language's native name (e.g. 日本語, Français, 한국어, Español).

    [Rules]
    - Output the complete translated JSON ONLY, and it must be valid JSON. Do not wrap it in ```json code blocks and add no explanations or notes.
    - Preserve printf-style placeholders EXACTLY as they appear: %s, %d, %1${'$'}s, %1${'$'}d, %2${'$'}s, %2${'$'}d, %3${'$'}d ... (%s / %1${'$'}s = text, %d / %1${'$'}d = number). Never delete, add, duplicate, reorder or translate them; only their position inside the sentence may move to fit the target language's word order. The count and the indices must stay identical to the source.
    - %% (TWO percent signs) is an escaped literal percent sign: always keep both characters. For example "%1${'$'}d%%" may become "Skipped %1${'$'}d%%" but never "Skipped %1${'$'}d%".
    - Also keep \n line breaks and XML escapes such as &amp; exactly as they are.
    - Keep URLs, email addresses, brand names, technical terms and abbreviations (SSID, WiFi, QR, SMS) untranslated.
    - Keep translations concise, natural and idiomatic, matching the style of mobile app buttons and hints.
    - Your output must contain every key from the template — do not skip any; the number of entries must match exactly.

    Here is the template to translate:
  """.trimIndent()

  private fun buildZh(target: String): String = """
    你是一位专业的软件本地化翻译专家。请把我提供的 App 界面字符串 JSON 模板翻译成【$target】。

    【待翻译文件结构】
    {
      "languageTag": "",
      "displayName": "",
      "strings": { "key": "English source text" }
    }

    【你需要完成的工作】
    1. 将 "strings" 中每一个值（value）翻译成【$target】；所有键（key）必须原样保留，不得修改、删除、重排或新增。
    2. 在 "languageTag" 中填入目标语言的 BCP-47 语言代码（例如：日语填 "ja"，法语填 "fr"，韩语填 "ko"，西班牙语填 "es"）。
    3. 在 "displayName" 中填入该语言的本地自称（例如：日本語、Français、한국어、Español）。

    【翻译规则】
    - 只输出翻译后的完整 JSON 本身，必须是合法 JSON；不要使用 ```json 代码块包裹，不要输出任何解释或备注。
    - 原样保留 printf 风格的占位符：%s、%d、%1${'$'}s、%1${'$'}d、%2${'$'}s、%2${'$'}d、%3${'$'}d 等（%s / %1${'$'}s 代表文字，%d / %1${'$'}d 代表数字）。不得删除、新增、重复、改写或翻译占位符本身，只能按目标语言语序移动它们的位置；占位符的数量与编号必须与原文完全一致。
    - %%（两个百分号）是转义后的字面百分号，必须原样保留两个字符。例如 "%1${'$'}d%%" 可译为「已跳过 %1${'$'}d%%」，绝不能写成 "%1${'$'}d%"。
    - \n 换行、XML 转义（如 &amp;）同样原样保留。
    - URL、邮箱地址、品牌名、技术术语与缩写（SSID、WiFi、QR、SMS 等）保持原文不翻译。
    - 译文要简洁、自然、地道，符合手机 App 中按钮和提示信息的表达习惯。
    - 输出必须包含模板中的全部 key，一个都不能少；字符串数量必须与模板一致。

    下面是待翻译的模板内容：
  """.trimIndent()

  private fun prefs(ctx: Context) =
    ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private fun readImports(ctx: Context): JSONObject =
    JSONObject(prefs(ctx).getString(KEY_IMPORTS, "{}") ?: "{}")
}

/**
 * 叠加在真实 [Resources] 之上的拦截层：对 [getString] / [getText] 优先返回导入语言的译文，
 * 其余类型（drawable、dimen…）全部委托给底层资源。键是「字符串资源名」（通过 [getResourceEntryName] 反查）。
 */
private class OverlayResources(
  private val base: Resources,
  private val override: Map<String, String>,
) : Resources(base.assets, base.displayMetrics, base.configuration) {

  override fun getString(id: Int): String = resolve(id) ?: base.getString(id)

  override fun getString(id: Int, vararg formatArgs: Any?): String {
    val r = resolve(id)
    if (r != null) {
      return try {
        if (formatArgs.isNotEmpty()) String.format(Locale.getDefault(), r, *formatArgs) else r
      } catch (_: Exception) {
        r
      }
    }
    return base.getString(id, *formatArgs)
  }

  override fun getText(id: Int): CharSequence = resolve(id) ?: base.getText(id)

  private fun resolve(id: Int): String? {
    val name = try {
      getResourceEntryName(id)
    } catch (_: Exception) {
      return null
    }
    return override[name]?.takeIf { it.isNotEmpty() }
  }
}
