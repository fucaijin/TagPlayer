package remix.myplayer.util

import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.AudioTagFile
import java.io.File
import kotlin.text.Regex

/**
 * 批量重命名 / 批量写标签涉及的元数据字段。
 * [token] 用于模板/规则中的占位符（大小写不敏感，如 {title} / {Title} / {歌曲名}）。
 * [fieldKey] 对应 [AudioTagFile] 的标签字段常量，用于实际读写文件标签。
 */
enum class MetaField(
  val token: String,
  val zhLabel: String,
  val fieldKey: String
) {
  TITLE("title", "歌曲名", AudioTagFile.TITLE),
  ARTIST("artist", "艺术家", AudioTagFile.ARTIST),
  ALBUM("album", "专辑", AudioTagFile.ALBUM),
  GENRE("genre", "流派", AudioTagFile.GENRE),
  YEAR("year", "年份", AudioTagFile.DATE),
  COMMENT("comment", "备注", AudioTagFile.COMMENT),
  TRACK("track", "曲目", AudioTagFile.TRACK_NUMBER);

  companion object {
    fun fromToken(token: String): MetaField? =
      entries.firstOrNull { it.token.equals(token.trim(), ignoreCase = true) }
  }
}

object BatchRenameUtil {

  /** 模板/规则中使用的、可被识别的元数据字段（按出现顺序去重） */
  fun usedFields(format: String): List<MetaField> {
    val regex = Regex("\\{([^}]+)\\}")
    return regex.findAll(format)
      .map { it.groupValues[1] }
      .distinct()
      .mapNotNull { MetaField.fromToken(it) }
      .toList()
  }

  /** 读取歌曲文件实际标签，返回 fieldKey -> 值（用于表格展示与写回） */
  fun readMetaMap(song: Song): Map<String, String> {
    val file = File(song.data)
    if (!file.exists()) return emptyMap()
    val pm = AudioTagFile.readMetadata(file, readPictures = false)?.propertyMap ?: return emptyMap()
    return MetaField.entries.associate { it.fieldKey to AudioTagFile.firstValue(pm, it.fieldKey) }
  }

  /** 按模板生成新文件名（不含扩展名）；空白返回 null。无法识别的占位符保留原样。 */
  fun buildNameFromMeta(format: String, meta: Map<String, String>): String? {
    val replaced = Regex("\\{([^}]+)\\}").replace(format) { m ->
      val field = MetaField.fromToken(m.groupValues[1])
      field?.let { meta[it.fieldKey].orEmpty() } ?: m.value
    }
    val cleaned = replaced.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    return if (cleaned.isBlank()) null else cleaned
  }

  /** 由规则构造正则：字面量转义，{token} 替换为命名捕获组 (?<token>.*?) */
  fun buildExtractRegex(rule: String): Regex? {
    val placeholder = Regex("\\{([^}]+)\\}")
    if (!placeholder.containsMatchIn(rule)) return null
    val sb = StringBuilder("^")
    var last = 0
    placeholder.findAll(rule).forEach { m ->
      sb.append(Regex.escape(rule.substring(last, m.range.first)))
      val token = m.groupValues[1].trim()
      sb.append("(?<${token}>.*?)")
      last = m.range.last + 1
    }
    sb.append(Regex.escape(rule.substring(last)))
    sb.append("$")
    return try {
      Regex(sb.toString(), RegexOption.IGNORE_CASE)
    } catch (e: Exception) {
      null
    }
  }

  /** 根据规则把文件名（不含扩展名）解析为 fieldKey -> 值；无法匹配返回 null */
  fun extractFromName(rule: String, fileNameWithoutExt: String): Map<String, String>? {
    val regex = buildExtractRegex(rule) ?: return null
    val m = regex.matchEntire(fileNameWithoutExt) ?: regex.find(fileNameWithoutExt) ?: return null
    val result = mutableMapOf<String, String>()
    Regex("\\{([^}]+)\\}").findAll(rule).forEach { pm ->
      val token = pm.groupValues[1].trim()
      val field = MetaField.fromToken(token) ?: return@forEach
      result[field.fieldKey] = m.groups[token]?.value?.trim().orEmpty()
    }
    return if (result.isEmpty()) null else result
  }
}

/** 文件名（含扩展名）去掉扩展名 */
fun fileNameWithoutExtension(name: String): String {
  val idx = name.lastIndexOf('.')
  return if (idx > 0) name.substring(0, idx) else name
}
