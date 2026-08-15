package com.kyant.taglib

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Frame
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.asf.AsfTag
import org.jaudiotagger.tag.asf.AsfTagTextField
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import timber.log.Timber
import java.io.File

object TagLib {

    /** 应用自定义字段 */
    private const val AUDIO_TAGS_KEY = "AUDIO_TAGS"

    /** M4A 自由格式字段的 issuer（iTunes 惯例） */
    private const val MP4_ISSUER = "com.apple.iTunes"

    @JvmStatic
    fun getAudioProperties(path: String): AudioProperties? {
        return try {
            val audioFile = AudioFileIO.read(File(path))
            val header = audioFile.audioHeader
            AudioProperties(
                bitrate = header.bitRate?.toIntOrNull() ?: 0,
                sampleRate = header.sampleRate?.toIntOrNull() ?: 0,
                channels = header.channels?.toIntOrNull() ?: 0,
                duration = header.trackLength?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getMetadata(path: String, readPictures: Boolean = true): Metadata? {
        return try {
            val audioFile = AudioFileIO.read(File(path))
            val tag = audioFile.tag

            val propertyMap = mutableMapOf<String, Array<String>>()

            // 读取所有标准字段。
            // 注意：jaudiotagger 2.0.1 在 TRCK/TPOS 帧缺失（或标签被截断导致部分帧未解析）时，
            // getFirst(FieldKey) 内部对 null frame 直接调 getBody() 抛 NPE，必须逐字段保护。
            if (tag != null) {
                FieldKey.values().forEach { key ->
                    // COVER_ART 是图片字段，getFirst 返回的并非可写回的文本值，
                    // 写回会抛 UnsupportedOperationException，跳过
                    if (key == FieldKey.COVER_ART) return@forEach
                    val value = try {
                        tag.getFirst(key)
                    } catch (e: Exception) {
                        null
                    }
                    if (!value.isNullOrBlank()) {
                        propertyMap[key.name] = arrayOf(value)
                    }
                }

                // 读取应用自定义字段（MP3 存于 TXXX 帧）
                readCustomField(tag, AUDIO_TAGS_KEY)?.takeIf { it.isNotBlank() }?.let {
                    propertyMap[AUDIO_TAGS_KEY] = arrayOf(it)
                }
            }

            // 2.0.1 版本中图片处理 API 不完整，返回空
            val pictures = emptyArray<Picture>()

            Metadata(propertyMap, pictures)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getFrontCover(path: String): Picture? {
        return null
    }

    @JvmStatic
    fun savePropertyMap(path: String, propertyMap: PropertyMap): Boolean {
        if (saveOnExisting(File(path), propertyMap)) return true
        // 常规写入失败（通常是 jaudiotagger 无法完整解析的标签，例如 ffmpeg 写入的
        // ID3v2 标签末尾不足 10 字节），改为重建整个标签再写入
        Timber.w("jaudiotagger normal save failed, fallback to rebuild: $path")
        return saveByRebuild(File(path), propertyMap)
    }

    /** 常规保存：读取现有标签并修改 */
    private fun saveOnExisting(file: File, propertyMap: PropertyMap): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            applyPropertyMap(tag, propertyMap)
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 兜底保存：丢弃无法解析的旧标签，用全新默认标签写入（不删除文件其它内容） */
    private fun saveByRebuild(file: File, propertyMap: PropertyMap): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val freshTag = audioFile.createDefaultTag()
            applyPropertyMap(freshTag, propertyMap)
            audioFile.setTag(freshTag)
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 将 propertyMap 应用到标签：标准字段走 FieldKey，自定义字段走 TXXX */
    private fun applyPropertyMap(tag: Tag, propertyMap: PropertyMap) {
        propertyMap.forEach { (key, values) ->
            val value = values.firstOrNull().orEmpty()
            try {
                val fieldKey = try {
                    FieldKey.valueOf(key)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (fieldKey != null) {
                    if (value.isNotBlank()) {
                        tag.setField(fieldKey, value)
                    } else {
                        tag.deleteField(fieldKey)
                    }
                } else if (value.isNotBlank()) {
                    writeCustomField(tag, key, value)
                } else {
                    deleteCustomField(tag, key)
                }
            } catch (e: Exception) {
                // 单个字段写入失败不影响整体保存
                Timber.w(e, "failed to apply tag field: $key")
            }
        }
    }

    /** 写自定义字段：MP3 走 TXXX 帧，FLAC/OGG 走 Vorbis 注释，M4A 走自由格式 atom，WMA 走 ASF 描述符 */
    private fun writeCustomField(tag: Tag, key: String, value: String) {
        when (tag) {
            is AbstractID3v2Tag -> writeTXXXFrame(tag, key, value)
            is FlacTag -> tag.setField(key, value)
            is VorbisCommentTag -> tag.setField(key, value)
            is Mp4Tag -> writeMp4Freeform(tag, key, value)
            is AsfTag -> tag.setField(AsfTagTextField(key, value))
            else -> Timber.d("custom field %s not supported for tag %s", key, tag.javaClass.simpleName)
        }
    }

    /** 删除自定义字段（空值时调用） */
    private fun deleteCustomField(tag: Tag, key: String) {
        when (tag) {
            is AbstractID3v2Tag -> writeTXXXFrame(tag, key, "")
            is FlacTag -> tag.setField(key, "")
            is VorbisCommentTag -> tag.setField(key, "")
            is Mp4Tag -> writeMp4Freeform(tag, key, "")
            is AsfTag -> tag.setField(AsfTagTextField(key, ""))
            else -> {}
        }
    }

    /** 写 M4A 自由格式字段（同 descriptor 的旧字段会被替换，不会重复） */
    private fun writeMp4Freeform(tag: Mp4Tag, key: String, value: String) {
        val field = Mp4TagReverseDnsField("----:$MP4_ISSUER:$key", MP4_ISSUER, key, value)
        tag.setField(field)
    }

    /** 替换 TXXX 帧（按描述匹配），保留其它 TXXX 帧 */
    private fun writeTXXXFrame(tag: AbstractID3v2Tag, key: String, value: String) {
        val keep = tag.get("TXXX").filter { frame ->
            val body = (frame as? AbstractID3v2Frame)?.body
            !(body is FrameBodyTXXX && body.description == key)
        }
        tag.removeFrame("TXXX")
        keep.forEach { tag.setFrame(it as AbstractID3v2Frame) }

        if (value.isNotBlank()) {
            val frame = if (tag is ID3v24Tag) ID3v24Frame("TXXX") else ID3v23Frame("TXXX")
            val body = frame.body as? FrameBodyTXXX ?: return
            body.setDescription(key)
            body.setText(value)
            tag.setFrame(frame)
        }
    }

    /** 读取自定义字段：MP3 查 TXXX 帧，FLAC/OGG 查 Vorbis 注释，M4A 查自由格式 atom */
    private fun readCustomField(tag: Tag, key: String): String? {
        return when (tag) {
            is AbstractID3v2Tag -> {
                tag.get("TXXX").forEach { frame ->
                    val body = (frame as? AbstractID3v2Frame)?.body
                    if (body is FrameBodyTXXX && body.description == key) {
                        return body.firstTextValue
                    }
                }
                null
            }
            is FlacTag -> tag.getFirst(key).takeIf { it.isNotEmpty() }
            is VorbisCommentTag -> tag.getFirst(key).takeIf { it.isNotEmpty() }
            is Mp4Tag -> readMp4Freeform(tag, key)
            is AsfTag -> tag.getFirst(key).takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    /** 读取 M4A 自由格式字段 */
    private fun readMp4Freeform(tag: Mp4Tag, key: String): String? {
        val iterator = tag.getFields()
        while (iterator.hasNext()) {
            val field = iterator.next()
            if (field is Mp4TagReverseDnsField && field.issuer == MP4_ISSUER && field.descriptor == key) {
                return field.content.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    @JvmStatic
    fun savePictures(path: String, pictures: Array<Picture>): Boolean {
        // 2.0.1 版本不支持图片保存
        return false
    }
}
