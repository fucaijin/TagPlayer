package remix.myplayer.helper

import com.kyant.taglib.TagLib
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 歌曲自定义标签的读写。
 *
 * 标签存储在音频文件的自定义字段 [TAGS_KEY] 中（MP3 的 TXXX 帧 / FLAC 的 Vorbis 注释 / M4A 的自定义 atom），
 * 多个标签使用 [SEPARATOR] 拼接。音频文件本身是标签的唯一真相源。
 */
object SongTagFile {

  /** 自定义字段名 */
  const val TAGS_KEY = "AUDIO_TAGS"

  /** 标签分隔符 */
  const val SEPARATOR = ";;"

  /** 音频格式不支持写入标签，上层据此给出明确提示 */
  class UnsupportedFormatException(val fileName: String) :
    Exception("Unsupported tag format: $fileName")

  /** 该格式是否支持写入标签（不支持时可提示转换为 MP3） */
  fun isWritable(path: String): Boolean = TagLib.isTagWritable(path)

  /**
   * 文件实际容器（魔数）与扩展名是否不符（例如内容是 M4A/AAC 却用了 .mp3 扩展名）。
   * 这种情况 [isWritable] 会按扩展名误判为“可写”，但实际写入时原生标签库选错写入器而失败，
   * 应提示用户转换为正确的格式（AAC/MP3）。
   */
  fun isContainerMismatch(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return false
    val realExt = detectRealContainerExt(file) ?: return false
    return !file.name.endsWith(realExt, ignoreCase = true)
  }

  /** 从音频文件中读取标签 */
  fun readTags(file: File): Set<String> {
    val metadata = AudioTagFile.readMetadata(file, readPictures = false) ?: return emptySet()
    return parseTags(AudioTagFile.firstValue(metadata.propertyMap, TAGS_KEY))
  }

  /**
   * 将标签写入音频文件，返回是否成功。
   * [cacheDir] 用于扩展名不标准的 MP4 容器（如 .aac）写入时的临时文件。
   * 格式不支持写入标签时抛 [UnsupportedFormatException]。
   */
  fun writeTags(file: File, cacheDir: File?, tags: Set<String>): Boolean {
    if (!TagLib.isTagWritable(file.absolutePath)) {
      Timber.w("writeTags unsupported format: %s", file.absolutePath)
      throw UnsupportedFormatException(file.name)
    }
    // 路径含非 ASCII 字符（中文等）时，com.kyant.taglib 底层按窄字符 (const char*) 打开文件会失败，
    // 表现为 readMetadata 返回 null、上层抛 IllegalStateException("Failed to write tags to file")。
    // 这里先复制到缓存目录下“纯 ASCII 文件名”的临时文件写入标签，成功后再覆盖回原文件
    // （原文件路径不变，不影响播放与媒体库）。
    if (cacheDir != null && hasNonAscii(file.absolutePath)) {
      val ext = file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
      val tmp = File(cacheDir, "tagtmp_${System.nanoTime()}$ext")
      return try {
        Files.copy(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val ok = writeTags(tmp, cacheDir, tags)
        if (ok) {
          Files.copy(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        ok
      } catch (e: Exception) {
        Timber.e(e, "writeTags non-ascii temp fallback failed: %s", file.absolutePath)
        false
      } finally {
        tmp.delete()
      }
    }
    val metadata = AudioTagFile.readMetadata(file, readPictures = false)
    if (metadata == null) {
      Timber.w("writeTags read metadata failed: %s", file.absolutePath)
      return false
    }
    val propertyMap = metadata.propertyMap
    // 始终写入 AUDIO_TAGS（即使为空集也写入空值以清除文件中的旧标签）
    propertyMap[TAGS_KEY] = arrayOf(tags.joinToString(SEPARATOR))

    // 扩展名与实际音频容器不一致时（例如内容是 M4A/AAC 却用了 .mp3 扩展名），
    // com.kyant.taglib 会按扩展名选择写入器，用 MP3/ID3 写入器去写 MP4 容器导致失败。
    // 这里先写到一个扩展名与真实容器匹配的临时文件，再覆盖回原文件（路径不变，播放不受影响）。
    val realExt = detectRealContainerExt(file)
    val needsTemp = realExt != null && !file.name.endsWith(realExt, ignoreCase = true)
    val saved = if (needsTemp && cacheDir != null) {
      val tmp = File(cacheDir, "tagtmp_${System.nanoTime()}$realExt")
      try {
        Files.copy(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val ok = AudioTagFile.savePropertyMap(tmp, propertyMap, cacheDir)
        if (ok) {
          Files.copy(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        ok
      } catch (e: Exception) {
        Timber.e(e, "writeTags temp-file fallback failed: %s", file.absolutePath)
        false
      } finally {
        tmp.delete()
      }
    } else {
      AudioTagFile.savePropertyMap(file, propertyMap, cacheDir)
    }

    // mtime/size 是其它 App 判断是否需要重新读取标签的依据，写入后必须发生变化
    Timber.i(
      "writeTags %s: %s -> [%s] | mtime=%d size=%d",
      if (saved) "ok" else "failed",
      file.name,
      tags.joinToString(SEPARATOR),
      file.lastModified(),
      file.length()
    )
    if (!saved) {
      throw IOException(
        "Failed to write tags to file: ${file.absolutePath}\n" +
          "（该文件实际是 ${realExt ?: "未知"} 容器但扩展名为 .${file.extension}，" +
          "原生标签库按扩展名选错写入器。请将其改名成正确扩展名，或用格式转换工具转成标准 MP3 后再打标签。）"
      )
    }
    return saved
  }

  /**
   * 通过魔数检测音频文件的真实容器格式，返回对应的正确扩展名（不含点则返回 null）。
   * 用于修正“扩展名与实际格式不符导致打标签失败”的情况。
   */
  private fun detectRealContainerExt(file: File): String? {
    val head = ByteArray(12)
    try {
      file.inputStream().use { it.read(head) }
    } catch (e: Exception) {
      Timber.w(e, "detectRealContainerExt read failed: %s", file.absolutePath)
      return null
    }
    // MP4 / M4A：4 字节 box 长度后跟 "ftyp"
    if (head.copyOfRange(4, 8).toString(Charset.forName("US-ASCII")) == "ftyp") return ".m4a"
    // FLAC：前 4 字节为 "fLaC"
    if (head.copyOfRange(0, 4).toString(Charset.forName("US-ASCII")) == "fLaC") return ".flac"
    return null
  }

  /** 解析分隔符拼接的标签字符串 */
  fun parseTags(value: String): Set<String> {
    return value.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }

  /** 路径是否包含非 ASCII 字符（中文、日文等），用于判断是否需要走临时文件兜底 */
  private fun hasNonAscii(path: String): Boolean = path.any { it.code > 0x7F }
}
