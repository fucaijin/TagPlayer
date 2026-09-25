package remix.myplayer.i18n

import androidx.annotation.StringRes

/**
 * 可选：key -> R.string.id 注册表。
 *
 * 本项目对「内置语言」沿用 Android 资源限定符（values/、values-zh-rCN…），
 * 对「导入语言」通过 [LocaleManager] 的资源拦截（OverlayResources）全局生效，
 * 因此大多数情况下无需手动注册此表。保留它是为了兼容需要按 key 取字符串的场景，
 * 以及让导出的模板能够包含宿主自定义的额外 key。
 */
object StringKeys {
  @Volatile
  private var mappings: Map<String, Int> = emptyMap()

  /** 当前已注册的 key -> @StringRes 映射快照。 */
  val map: Map<String, Int> get() = mappings

  fun register(mappings: Map<String, Int>) {
    this.mappings = mappings
  }
}
