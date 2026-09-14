package remix.myplayer.helper

import com.kyant.taglib.TagLib
import timber.log.Timber
import java.io.File

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
    val metadata = AudioTagFile.readMetadata(file, readPictures = false)
    if (metadata == null) {
      Timber.w("writeTags read metadata failed: %s", file.absolutePath)
      return false
    }
    val propertyMap = metadata.propertyMap
    // 始终写入 AUDIO_TAGS（即使为空集也写入空值以清除文件中的旧标签）
    propertyMap[TAGS_KEY] = arrayOf(tags.joinToString(SEPARATOR))
    val saved = AudioTagFile.savePropertyMap(file, propertyMap, cacheDir)
    // mtime/size 是其它 App 判断是否需要重新读取标签的依据，写入后必须发生变化
    Timber.i(
      "writeTags %s: %s -> [%s] | mtime=%d size=%d",
      if (saved) "ok" else "failed",
      file.name,
      tags.joinToString(SEPARATOR),
      file.lastModified(),
      file.length()
    )
    return saved
  }

  /** 解析分隔符拼接的标签字符串 */
  fun parseTags(value: String): Set<String> {
    return value.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }
}
