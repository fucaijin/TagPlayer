package com.kyant.taglib

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File

object TagLib {

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

            // 读取所有标准字段
            FieldKey.values().forEach { key ->
                val value = tag?.getFirst(key)
                if (!value.isNullOrBlank()) {
                    propertyMap[key.name] = arrayOf(value)
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
        return try {
            val audioFile = AudioFileIO.read(File(path))
            // 获取或创建 Tag
            var tag = audioFile.tag
            if (tag == null) {
                // 2.0.1 版本中，通过 AudioFile 创建 Tag 的方式
                // 不同格式可能需要不同的 Tag 创建方式
                tag = audioFile.createDefaultTag()
            }

            propertyMap.forEach { (key, values) ->
                val value = values.firstOrNull() ?: ""
                try {
                    val fieldKey = FieldKey.valueOf(key)
                    if (value.isNotBlank()) {
                        tag.setField(fieldKey, value)
                    } else {
                        tag.deleteField(fieldKey)
                    }
                } catch (e: IllegalArgumentException) {
                    // 不是标准 FieldKey，忽略
                }
            }

            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun savePictures(path: String, pictures: Array<Picture>): Boolean {
        // 2.0.1 版本不支持图片保存
        return false
    }
}