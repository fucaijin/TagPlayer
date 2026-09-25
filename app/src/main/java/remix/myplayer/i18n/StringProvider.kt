package remix.myplayer.i18n

import android.content.res.Resources
import androidx.compose.runtime.compositionLocalOf

/**
 * 字符串解析器，优先级：导入语言覆盖表（[override]）→ 已注册的 `R.string` 资源 → 兜底值。
 *
 * - [override] 是「字符串资源名 -> 译文」的映射（由 [LocaleManager.importedStrings] 提供）。
 *   因为项目对导入语言已经用资源拦截层全局生效，这里保留它是为了按 key 取值时也能命中译文。
 * - 未命中 [override] 时，会走 [StringKeys] 找到 `R.string` 的 id，再用 [resources] 读取。
 *   **这一步是语言设置能影响界面文案的关键**：[resources] 来自被 [LocaleManager.applyLocale]
 *   包裹过的 Context，因此读取到的就是当前语言的资源值。
 * - 两者都失败时才返回 key 本身或调用方给的 [fallback]。
 *
 * 在 Compose 根部通过 `CompositionLocalProvider(LocalStringProvider provides ...)` 提供。
 */
class StringProvider(
  val resources: Resources,
  val override: Map<String, String>?,
) {
  /** 解析字符串，全部失败时返回原 key。 */
  fun get(key: String): String {
    override?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
    return resolveFromResources(key) ?: key
  }

  /** 解析字符串，全部失败时返回 [fallback]（不会返回裸 key）。 */
  fun getOr(key: String, fallback: String): String {
    override?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
    return resolveFromResources(key) ?: fallback
  }

  private fun resolveFromResources(key: String): String? {
    val resId = StringKeys.map[key] ?: return null
    return runCatching { resources.getString(resId) }.getOrNull()
  }
}

val LocalStringProvider = compositionLocalOf<StringProvider> {
  error("LocalStringProvider not provided")
}
