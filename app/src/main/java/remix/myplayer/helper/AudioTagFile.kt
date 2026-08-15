package remix.myplayer.helper

import com.kyant.taglib.AudioProperties
import com.kyant.taglib.Metadata
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import remix.myplayer.data.model.audio.ReplayGain
import java.io.File
import java.io.IOException

object AudioTagFile {

  const val TITLE = "TITLE"
  const val ALBUM = "ALBUM"
  const val ARTIST = "ARTIST"
  const val ALBUM_ARTIST = "ALBUMARTIST"
  const val COMPOSER = "COMPOSER"
  const val GENRE = "GENRE"
  const val DATE = "DATE"
  const val TRACK_NUMBER = "TRACKNUMBER"
  const val DISC_NUMBER = "DISCNUMBER"
  const val LYRICS = "LYRICS"

  fun readAudioProperties(file: File): AudioProperties? =
    TagLib.getAudioProperties(file.absolutePath)

  fun readMetadata(file: File, readPictures: Boolean = true): Metadata? =
    TagLib.getMetadata(file.absolutePath, readPictures)

  fun readFrontCover(file: File): Picture? =
    TagLib.getFrontCover(file.absolutePath)

  fun readReplayGain(file: File): ReplayGain? =
    TagLib.getMetadata(file.absolutePath, readPictures = false)?.propertyMap?.let { map ->
      ReplayGain.fromPropertyMap(map)
    }

  fun savePropertyMap(file: File, propertyMap: PropertyMap): Boolean =
    TagLib.savePropertyMap(file.absolutePath, propertyMap)

  fun savePictures(file: File, pictures: Array<Picture>): Boolean =
    TagLib.savePictures(file.absolutePath, pictures)

  fun firstValue(propertyMap: Map<String, Array<String>>?, key: String): String =
    propertyMap?.get(key)?.firstOrNull().orEmpty()

  fun setValue(propertyMap: PropertyMap, key: String, value: String) {
    if (value.isBlank()) {
      propertyMap.remove(key)
    } else {
      propertyMap[key] = arrayOf(value)
    }
  }

  fun requireSaved(saved: Boolean, operation: String) {
    if (!saved) {
      throw IOException("TagLib failed to $operation")
    }
  }
}