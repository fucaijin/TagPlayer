package remix.myplayer.helper

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

  /** 从音频文件中读取标签 */
  fun readTags(file: File): Set<String> {
    val metadata = AudioTagFile.readMetadata(file, readPictures = false) ?: return emptySet()
    return parseTags(AudioTagFile.firstValue(metadata.propertyMap, TAGS_KEY))
  }

  /** 将标签写入音频文件，返回是否成功 */
  fun writeTags(file: File, tags: Set<String>): Boolean {
    val metadata = AudioTagFile.readMetadata(file, readPictures = false) ?: return false
    val propertyMap = metadata.propertyMap
    // 始终写入 AUDIO_TAGS（即使为空集也写入空值以清除文件中的旧标签）
    propertyMap[TAGS_KEY] = arrayOf(tags.joinToString(SEPARATOR))
    return AudioTagFile.savePropertyMap(file, propertyMap)
  }

  /** 解析分隔符拼接的标签字符串 */
  fun parseTags(value: String): Set<String> {
    return value.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }
}
