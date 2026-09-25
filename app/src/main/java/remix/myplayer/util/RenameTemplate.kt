package remix.myplayer.util

import remix.myplayer.data.model.audio.Song
import java.io.File

/** 批量重命名模板工具：将占位符替换为歌曲元数据，并构造新文件名 */
object RenameTemplate {
  /** 按模板生成新文件名（不含扩展名）；非法字符会被替换为下划线；空白返回 null */
  fun buildName(template: String, song: Song): String? {
    val replaced = template
      .replace("{title}", song.title)
      .replace("{artist}", song.artist)
      .replace("{album}", song.album)
      .replace("{track}", song.track ?: "")
      .replace("{year}", song.year)
    if (replaced.isBlank()) return null
    return replaced.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
  }

  /** 在原目录基础上按新文件名构造目标文件（保留原扩展名） */
  fun newFile(file: File, name: String): File {
    return File(file.parent, "$name.${file.extension}")
  }
}
