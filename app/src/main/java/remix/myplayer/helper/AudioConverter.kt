package remix.myplayer.helper

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.BRHist
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** 转换的目标格式，[extension] 为目标文件扩展名 */
enum class ConvertFormat(val extension: String) {
  /** AAC（保存在 MP4/m4a 容器里，可写标签） */
  AAC("m4a"),

  /** MP3 */
  MP3("mp3")
}

/**
 * 把音频文件转换为 MP3 / AAC(m4a)，写入调用方指定的目标文件。
 *
 * 解码：WAV 直接解析 RIFF 取 PCM；其它格式用 MediaExtractor + MediaCodec 解码为 16bit PCM。
 * 编码：MP3 用 jump3r（纯 Java 的 LAME 移植，不引入 native ffmpeg）；AAC 用系统 MediaCodec 编码器 + MediaMuxer。
 */
object AudioConverter {

  /** 转换失败（格式不支持、解码/编码失败等） */
  class ConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)

  /** MP3 默认输出码率（kbps） */
  private const val BITRATE_KBPS = 192

  /** LAME 质量 0(最好)~9(最差) */
  private const val QUALITY = 3

  /** MP3 输出缓冲（1.25 * CHUNK_FRAMES + 7200 已足够） */
  private const val MP3_BUFFER_SIZE = 256 * 1024

  /** AAC 输出码率（bps） */
  private const val AAC_BITRATE_MONO = 128_000
  private const val AAC_BITRATE_STEREO = 192_000

  /** 单次喂给编码器的最大字节数 */
  private const val AAC_MAX_INPUT_SIZE = 16 * 1024

  /** 等待编码器接收数据的重试上限 */
  private const val MAX_FEED_RETRY = 200

  /**
   * 将 [source] 转换为 [format] 并写入 [target]（会覆盖已存在的 target）。
   * [onProgress] 回调 0..100 的进度。
   */
  fun convert(
    context: Context,
    source: File,
    target: File,
    format: ConvertFormat,
    onProgress: ((Int) -> Unit)? = null
  ) {
    try {
      when (format) {
        ConvertFormat.MP3 -> convertToMp3(context, source, target, onProgress)
        ConvertFormat.AAC -> convertToM4a(context, source, target, onProgress)
      }
    } catch (e: ConvertException) {
      throw e
    } catch (e: Exception) {
      throw ConvertException("Convert failed: ${source.name}", e)
    }
  }

  /** MP3：jump3r（纯 Java LAME） */
  private fun convertToMp3(
    context: Context,
    source: File,
    target: File,
    onProgress: ((Int) -> Unit)?
  ) {
    val pcmSource = openPcmSource(context, source)
    try {
      val encoder = LameMp3Encoder(pcmSource.channels, pcmSource.sampleRate, BITRATE_KBPS, QUALITY)
      val mp3Buffer = ByteArray(MP3_BUFFER_SIZE)
      val totalFrames = pcmSource.totalFrames
      var writtenFrames = 0L
      try {
        FileOutputStream(target).use { out ->
          pcmSource.readAll { pcm, count ->
            val frames = count / pcmSource.channels
            if (frames <= 0) return@readAll
            val size = encoder.encode(pcm, frames, mp3Buffer)
            if (size < 0) throw ConvertException("LAME encode failed: $size")
            if (size > 0) out.write(mp3Buffer, 0, size)
            writtenFrames += frames
            if (onProgress != null && totalFrames > 0) {
              onProgress(((writtenFrames * 100L) / totalFrames).toInt().coerceIn(0, 99))
            }
          }
          val size = encoder.flush(mp3Buffer)
          if (size > 0) out.write(mp3Buffer, 0, size)
        }
      } finally {
        encoder.close()
      }
      onProgress?.invoke(100)
    } finally {
      pcmSource.closeQuietly()
    }
  }

  /** AAC：系统 MediaCodec 编码器 + MediaMuxer 输出 m4a（AAC in MP4，可写标签） */
  private fun convertToM4a(
    context: Context,
    source: File,
    target: File,
    onProgress: ((Int) -> Unit)?
  ) {
    val pcmSource = openPcmSource(context, source)
    try {
      val channels = pcmSource.channels
      val sampleRate = pcmSource.sampleRate
      val totalFrames = pcmSource.totalFrames

      val inputFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
          setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
          setInteger(
            MediaFormat.KEY_BIT_RATE,
            if (channels == 1) AAC_BITRATE_MONO else AAC_BITRATE_STEREO
          )
          setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_MAX_INPUT_SIZE)
        }

      val codec = try {
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
      } catch (e: Exception) {
        throw ConvertException("No AAC encoder", e)
      }
      val muxer = try {
        MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      } catch (e: Exception) {
        codec.release()
        throw ConvertException("Cannot create muxer: ${target.name}", e)
      }

      var trackIndex = -1
      var muxerStarted = false
      var sawOutputEos = false
      var framesFed = 0L
      var drainRetry = 0
      val bufferInfo = MediaCodec.BufferInfo()

      fun drainOutput(endOfStream: Boolean) {
        while (!sawOutputEos) {
          val outIndex = codec.dequeueOutputBuffer(
            bufferInfo,
            if (endOfStream) TIMEOUT_US else 0L
          )
          if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (!endOfStream) return
            // 等待编码器输出收尾，极端情况下也不要把线程卡死在这里
            if (++drainRetry > MAX_FEED_RETRY) return
            continue
          }
          if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (!muxerStarted) {
              trackIndex = muxer.addTrack(codec.outputFormat)
              muxer.start()
              muxerStarted = true
            }
            continue
          }
          if (outIndex < 0) continue

          // 编码器配置数据已由 MediaMuxer 通过 track format 写入
          if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            bufferInfo.size = 0
          }
          val outBuf = codec.getOutputBuffer(outIndex)
          if (outBuf != null && bufferInfo.size > 0 && muxerStarted) {
            outBuf.position(bufferInfo.offset)
            outBuf.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
          }
          codec.releaseOutputBuffer(outIndex, false)
          if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            sawOutputEos = true
          }
        }
      }

      try {
        codec.configure(inputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        pcmSource.readAll { pcm, count ->
          val bytes = shortsToBytes(pcm, count)
          var offset = 0
          var retry = 0
          while (offset < bytes.size) {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex < 0) {
              drainOutput(false)
              if (++retry > MAX_FEED_RETRY) {
                throw ConvertException("AAC encoder did not accept input")
              }
              continue
            }
            retry = 0
            val inBuf = codec.getInputBuffer(inIndex)
              ?: throw ConvertException("AAC encoder input buffer is null")
            inBuf.clear()
            val size = min(inBuf.capacity(), bytes.size - offset)
            inBuf.put(bytes, offset, size)
            codec.queueInputBuffer(inIndex, 0, size, framesFed * 1_000_000L / sampleRate, 0)
            framesFed += (size / 2 / channels).toLong()
            offset += size
            drainOutput(false)
          }
          if (onProgress != null && totalFrames > 0) {
            onProgress(((framesFed * 100L) / totalFrames).toInt().coerceIn(0, 99))
          }
        }

        // 输入结束
        var eosRetry = 0
        while (true) {
          val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
          if (inIndex < 0) {
            drainOutput(false)
            if (++eosRetry > MAX_FEED_RETRY) break
            continue
          }
          codec.queueInputBuffer(
            inIndex,
            0,
            0,
            framesFed * 1_000_000L / sampleRate,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
          )
          break
        }
        drainOutput(true)
      } finally {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) {
          runCatching { muxer.stop() }
        }
        runCatching { muxer.release() }
      }
      // MediaMuxer 产出的 MP4 用 64 位 mdat 与 co64 块偏移，jaudiotagger 无法读写，
      // 必须先规范化，否则后续写标签会失败
      normalizeMuxerMp4(target)
      onProgress?.invoke(100)
    } finally {
      pcmSource.closeQuietly()
    }
  }

  /** 交错 16bit PCM -> 小端字节数组 */
  private fun shortsToBytes(pcm: ShortArray, count: Int): ByteArray {
    val bytes = ByteArray(count * 2)
    var i = 0
    var o = 0
    while (i < count) {
      val v = pcm[i].toInt()
      bytes[o++] = (v and 0xFF).toByte()
      bytes[o++] = ((v shr 8) and 0xFF).toByte()
      i++
    }
    return bytes
  }

  // -------- MediaMuxer 输出的 MP4 规范化 --------
  // Android MediaMuxer 写出的 MP4 有两个 jaudiotagger 2.0.1 不支持的地方：
  // 1) mdat 用 64 位长度头（size==1 + largesize）
  // 2) 块偏移用 co64（64 位），而 jaudiotagger 写标签时强依赖 stco
  // 不规范化的话，转换出来的文件无法写入标签。

  private const val MDAT = "mdat"
  private const val MOOV = "moov"
  private const val STCO = "stco"
  private const val CO64 = "co64"
  private const val COPY_BUFFER_SIZE = 64 * 1024

  /** MP4 里需要继续向下解析的容器 box */
  private val MP4_CONTAINER_BOXES = setOf("moov", "trak", "mdia", "minf", "stbl")

  private data class Mp4RootBox(
    val type: String,
    val start: Long,
    val headerLen: Int,
    val size: Long
  )

  private class Mp4Box(val type: String) {
    /** 解析时占用的总字节数（写回时会按内容重算） */
    var totalSize: Int = 0
    var payload: ByteArray? = null
    var children: MutableList<Mp4Box>? = null
  }

  /**
   * 把 [file]（MediaMuxer 产出的 m4a）规范化成 jaudiotagger 可读写的形式。
   * 已经是规范形式的文件不做任何修改。
   */
  private fun normalizeMuxerMp4(file: File) {
    val roots = readRootBoxes(file)
    val mdatIndex = roots.indexOfFirst { it.type == MDAT && it.headerLen == 16 }
    if (mdatIndex < 0) return
    val moovIndex = roots.indexOfFirst { it.type == MOOV }
    if (moovIndex < 0) return

    val mdat = roots[mdatIndex]
    if (mdat.size - 8 > 0xFFFFFFFFL) {
      throw ConvertException("mp4 is too large to normalize: ${file.name}")
    }

    val moov = roots[moovIndex]
    val moovBytes = ByteArray(moov.size.toInt())
    RandomAccessFile(file, "r").use { raf ->
      raf.seek(moov.start)
      raf.readFully(moovBytes)
    }
    val moovBox = readMp4Box(moovBytes, 0, moovBytes.size) ?: return

    // 块偏移的修正量：mdat 头缩短 8 字节；moov 在 mdat 之前时还要算上 moov 自身的缩短量
    var shift = 8L
    if (moovIndex < mdatIndex) {
      shift += co64Shrink(moovBox)
    }
    fixChunkOffsets(moovBox, shift)

    val newMoov = ByteArrayOutputStream().also { writeMp4Box(moovBox, it) }.toByteArray()

    val temp = File(file.parentFile, file.name + ".norm")
    try {
      RandomAccessFile(file, "r").use { raf ->
        FileOutputStream(temp).use { out ->
          roots.forEachIndexed { index, box ->
            when {
              index == mdatIndex -> {
                writeBeInt(out, (mdat.size - 8).toInt())
                out.write(MDAT.toByteArray(Charsets.US_ASCII))
                raf.seek(mdat.start + 16)
                copyExactly(raf, out, mdat.size - 16)
              }

              index == moovIndex -> out.write(newMoov)

              else -> {
                raf.seek(box.start)
                copyExactly(raf, out, box.size)
              }
            }
          }
        }
      }
      temp.copyTo(file, overwrite = true)
    } finally {
      temp.delete()
    }
  }

  private fun readRootBoxes(file: File): List<Mp4RootBox> {
    val roots = ArrayList<Mp4RootBox>()
    RandomAccessFile(file, "r").use { raf ->
      val length = raf.length()
      val header = ByteArray(16)
      var pos = 0L
      while (pos + 8 <= length) {
        raf.seek(pos)
        raf.readFully(header, 0, 8)
        var size = beInt(header, 0).toLong() and 0xFFFFFFFFL
        val type = String(header, 4, 4, Charsets.US_ASCII)
        var headerLen = 8
        if (size == 1L) {
          raf.readFully(header, 8, 8)
          size = beLong(header, 8)
          headerLen = 16
        } else if (size == 0L) {
          size = length - pos
        }
        if (size < headerLen) break
        roots += Mp4RootBox(type, pos, headerLen, size)
        pos += size
      }
    }
    return roots
  }

  private fun readMp4Box(bytes: ByteArray, start: Int, end: Int): Mp4Box? {
    if (start + 8 > end) return null
    var size = beInt(bytes, start).toLong() and 0xFFFFFFFFL
    val type = String(bytes, start + 4, 4, Charsets.US_ASCII)
    var headerLen = 8
    if (size == 1L) {
      if (start + 16 > end) return null
      size = beLong(bytes, start + 8)
      headerLen = 16
    } else if (size == 0L) {
      size = (end - start).toLong()
    }
    val boxEnd = min(end.toLong(), start + size).toInt()
    if (boxEnd <= start) return null

    val box = Mp4Box(type)
    box.totalSize = boxEnd - start
    if (type in MP4_CONTAINER_BOXES) {
      val children = ArrayList<Mp4Box>()
      var pos = start + headerLen
      while (pos + 8 <= boxEnd) {
        val child = readMp4Box(bytes, pos, boxEnd) ?: break
        children += child
        pos += child.totalSize
      }
      box.children = children
    } else {
      val payloadStart = min(start + headerLen, boxEnd)
      box.payload = bytes.copyOfRange(payloadStart, boxEnd)
    }
    return box
  }

  /** co64 -> stco 会减少的字节数（每个偏移少 4 字节） */
  private fun co64Shrink(box: Mp4Box): Long {
    val children = box.children ?: return 0L
    var shrink = 0L
    children.forEach { child ->
      if (child.type == CO64) {
        val count = child.payload?.let { beInt(it, 4) } ?: 0
        shrink += (child.payload?.size ?: 0) - (8 + count * 4)
      } else {
        shrink += co64Shrink(child)
      }
    }
    return shrink
  }

  /** 按 [shift] 修正块偏移：co64 转成 stco，stco 原地减去偏移量 */
  private fun fixChunkOffsets(box: Mp4Box, shift: Long) {
    val children = box.children ?: return
    for (i in children.indices) {
      val child = children[i]
      when (child.type) {
        CO64 -> {
          val payload = child.payload ?: continue
          val count = beInt(payload, 4)
          val converted = Mp4Box(STCO)
          val out = ByteArrayOutputStream(8 + count * 4)
          writeBeInt(out, 0)
          writeBeInt(out, count)
          for (k in 0 until count) {
            val offset = beLong(payload, 8 + k * 8) - shift
            writeBeInt(out, offset.toInt())
          }
          converted.payload = out.toByteArray()
          children[i] = converted
        }

        STCO -> {
          val payload = child.payload ?: continue
          val count = beInt(payload, 4)
          for (k in 0 until count) {
            val pos = 8 + k * 4
            val offset = (beInt(payload, pos).toLong() and 0xFFFFFFFFL) - shift
            writeBeIntAt(payload, pos, offset.toInt())
          }
        }

        else -> fixChunkOffsets(child, shift)
      }
    }
  }

  private fun writeMp4Box(box: Mp4Box, out: ByteArrayOutputStream) {
    val payload = ByteArrayOutputStream()
    val children = box.children
    if (children != null) {
      children.forEach { writeMp4Box(it, payload) }
    } else {
      box.payload?.let { payload.write(it) }
    }
    val bytes = payload.toByteArray()
    writeBeInt(out, bytes.size + 8)
    out.write(box.type.toByteArray(Charsets.US_ASCII))
    out.write(bytes)
  }

  private fun beInt(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

  private fun beLong(b: ByteArray, off: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
      value = (value shl 8) or (b[off + i].toLong() and 0xFF)
    }
    return value
  }

  private fun writeBeInt(out: OutputStream, value: Int) {
    out.write(value ushr 24)
    out.write(value ushr 16)
    out.write(value ushr 8)
    out.write(value)
  }

  private fun writeBeIntAt(b: ByteArray, off: Int, value: Int) {
    b[off] = (value ushr 24).toByte()
    b[off + 1] = (value ushr 16).toByte()
    b[off + 2] = (value ushr 8).toByte()
    b[off + 3] = value.toByte()
  }

  private fun copyExactly(raf: RandomAccessFile, out: OutputStream, count: Long) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var remaining = count
    while (remaining > 0) {
      val toRead = min(buffer.size.toLong(), remaining).toInt()
      val read = raf.read(buffer, 0, toRead)
      if (read <= 0) throw ConvertException("unexpected end of file")
      out.write(buffer, 0, read)
      remaining -= read
    }
  }

  private fun openPcmSource(context: Context, source: File): PcmSource {
    val ext = source.extension.lowercase()
    return if (ext == "wav" || ext == "wave") WavPcmSource(source)
    else CodecPcmSource(context, source)
  }
}

/** 解码后的 PCM 数据源（交错 16bit） */
private interface PcmSource : Closeable {
  val sampleRate: Int
  val channels: Int

  /** 总采样帧数（未知时 0，仅用于进度显示） */
  val totalFrames: Long

  /** 推流式读取：每次回调一段交错 16bit PCM（[count] 为 short 个数） */
  fun readAll(onChunk: (ShortArray, Int) -> Unit)
}

/**
 * WAV（RIFF）解析：取 fmt/data 块，支持 8/16/24/32bit PCM 与 32bit float。
 */
private class WavPcmSource(private val file: File) : PcmSource {

  override val sampleRate: Int
  override val channels: Int
  private val bitsPerSample: Int
  /** 1 = PCM，3 = IEEE float */
  private val audioFormat: Int
  private val dataOffset: Long
  private val dataLength: Long

  init {
    var fmtFound = false
    var sr = 0
    var ch = 0
    var bps = 0
    var af = 1
    var dataOff = 0L
    var dataLen = 0L

    RandomAccessFile(file, "r").use { raf ->
      val header = ByteArray(12)
      if (raf.read(header) != 12 ||
        String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
        String(header, 8, 4, Charsets.US_ASCII) != "WAVE"
      ) {
        throw AudioConverter.ConvertException("Not a wav file: ${file.name}")
      }

      val chunkHeader = ByteArray(8)
      while (raf.filePointer + 8 <= raf.length()) {
        val chunkStart = raf.filePointer
        if (raf.read(chunkHeader) != 8) break
        val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
        val size = leInt(chunkHeader, 4).toLong() and 0xFFFFFFFFL
        val dataStart = chunkStart + 8

        when (id) {
          "fmt " -> {
            val fmt = ByteArray(min(size, 40L).toInt().coerceAtLeast(16))
            raf.readFully(fmt)
            af = leShort(fmt, 0)
            ch = leShort(fmt, 2)
            sr = leInt(fmt, 4)
            bps = leShort(fmt, 14)
            fmtFound = true
          }

          "data" -> {
            dataOff = dataStart
            dataLen = size
          }
        }

        // 块按偶数字节对齐
        raf.seek(dataStart + size + (size and 1L))
      }
    }

    if (!fmtFound || dataOff <= 0 || dataLen <= 0 || ch <= 0 || sr <= 0 || bps <= 0) {
      throw AudioConverter.ConvertException("Unsupported wav: ${file.name}")
    }
    if (af != 1 && af != 3) {
      throw AudioConverter.ConvertException("Unsupported wav format: $af")
    }

    sampleRate = sr
    channels = ch
    bitsPerSample = bps
    audioFormat = af
    dataOffset = dataOff
    dataLength = dataLen
  }

  override val totalFrames: Long
    get() {
      val bytesPerSample = (bitsPerSample / 8).toLong().coerceAtLeast(1L)
      return dataLength / (bytesPerSample * channels)
    }

  override fun readAll(onChunk: (ShortArray, Int) -> Unit) {
    RandomAccessFile(file, "r").use { raf ->
      raf.seek(dataOffset)
      val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
      val chunkBytes = CHUNK_FRAMES * bytesPerSample * channels
      val raw = ByteArray(chunkBytes)
      var remaining = dataLength

      while (remaining > 0) {
        val toRead = min(raw.size.toLong(), remaining).toInt()
        val read = raf.read(raw, 0, toRead)
        if (read <= 0) break
        remaining -= read
        val pcm = decodeToShorts(raw, read, bytesPerSample)
        if (pcm.isNotEmpty()) onChunk(pcm, pcm.size)
      }
    }
  }

  private fun decodeToShorts(raw: ByteArray, byteCount: Int, bytesPerSample: Int): ShortArray {
    val sampleCount = byteCount / bytesPerSample
    val out = ShortArray(sampleCount)
    var i = 0
    var o = 0

    when {
      audioFormat == 3 -> { // IEEE float 32bit
        while (i + 4 <= byteCount) {
          val f = Float.fromBits(leInt(raw, i))
          out[o++] = (f * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
          i += 4
        }
      }

      bitsPerSample == 8 -> { // 无符号 8bit
        while (i < byteCount) {
          out[o++] = (((raw[i].toInt() and 0xFF) - 128) shl 8).toShort()
          i++
        }
      }

      bitsPerSample == 24 -> {
        while (i + 3 <= byteCount) {
          val v = (raw[i].toInt() and 0xFF) or
              ((raw[i + 1].toInt() and 0xFF) shl 8) or
              ((raw[i + 2].toInt() and 0xFF) shl 16)
          // 24bit 有符号 -> 16bit
          out[o++] = (((v shl 8) shr 16)).toShort()
          i += 3
        }
      }

      bitsPerSample == 32 -> {
        while (i + 4 <= byteCount) {
          out[o++] = (leInt(raw, i) shr 16).toShort()
          i += 4
        }
      }

      else -> { // 16bit
        while (i + 2 <= byteCount) {
          out[o++] = leShort(raw, i).toShort()
          i += 2
        }
      }
    }
    return out
  }

  private fun leShort(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

  private fun leInt(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

  /** 每次读取时打开、读完即关，无需在此关闭 */
  override fun close() = Unit
}

/**
 * 安全读取 MediaFormat 的整型键。
 *
 * 注意：不能直接用 `getInteger(key) ?: default`。
 * MediaFormat 内部是 `((Integer) map.get(name)).intValue()`（部分版本），
 * 键不存在时它是在 Java 侧抛 NullPointerException，根本不会返回 null，
 * Kotlin 的 Elvis 兜不住，这也是之前 mp3/m4a 转换直接崩掉的原因。
 */
private fun MediaFormat?.intOrNull(key: String): Int? {
  val format = this ?: return null
  if (!format.containsKey(key)) return null
  return runCatching { format.getInteger(key) }.getOrNull()
}

/**
 * 其它容器/编码（如裸 ADTS .aac）走 MediaExtractor + MediaCodec 解码为 16bit PCM。
 */
private class CodecPcmSource(context: Context, file: File) : PcmSource {

  private val extractor = MediaExtractor()
  private val codec: MediaCodec
  private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

  override val sampleRate: Int
  override val channels: Int
  override var totalFrames: Long = 0
    private set

  init {
    try {
      extractor.setDataSource(file.absolutePath)
      var trackIndex = -1
      var trackFormat: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
          trackIndex = i
          trackFormat = format
          break
        }
      }
      if (trackIndex < 0 || trackFormat == null) {
        throw AudioConverter.ConvertException("No audio track: ${file.name}")
      }
      extractor.selectTrack(trackIndex)

      val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
      codec = try {
        MediaCodec.createDecoderByType(mime)
      } catch (e: Exception) {
        throw AudioConverter.ConvertException("No decoder for $mime", e)
      }
      codec.configure(trackFormat, null, null, 0)
      codec.start()

      val outFormat = runCatching { codec.outputFormat }.getOrNull()
      sampleRate = outFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
        ?: trackFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
        ?: throw AudioConverter.ConvertException("Unknown sample rate: ${file.name}")
      channels = outFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
        ?: trackFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
        ?: throw AudioConverter.ConvertException("Unknown channel count: ${file.name}")
      if (channels !in 1..2) {
        throw AudioConverter.ConvertException("Unsupported channel count $channels: ${file.name}")
      }
      pcmEncoding = outFormat.intOrNull(MediaFormat.KEY_PCM_ENCODING)
        ?: AudioFormat.ENCODING_PCM_16BIT

      val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
        trackFormat.getLong(MediaFormat.KEY_DURATION)
      } else {
        0L
      }
      totalFrames = if (durationUs > 0 && sampleRate > 0) durationUs * sampleRate / 1_000_000L else 0L
    } catch (e: AudioConverter.ConvertException) {
      closeQuietly()
      throw e
    } catch (e: Exception) {
      closeQuietly()
      throw AudioConverter.ConvertException("Unsupported format: ${file.name}", e)
    }
  }

  override fun readAll(onChunk: (ShortArray, Int) -> Unit) {
    val info = MediaCodec.BufferInfo()
    val pcm = ShortArray(CHUNK_FRAMES * channels)
    var inputDone = false
    var outputDone = false

    while (!outputDone) {
      if (!inputDone) {
        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIndex >= 0) {
          val inBuf = codec.getInputBuffer(inIndex)
          val sampleSize = if (inBuf == null) -1 else extractor.readSampleData(inBuf, 0)
          if (sampleSize < 0) {
            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
          } else {
            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }

      when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          val format = codec.outputFormat
          pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING)
            ?: AudioFormat.ENCODING_PCM_16BIT
        }

        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

        else -> if (outIndex >= 0) {
          val outBuf = codec.getOutputBuffer(outIndex)
          if (outBuf != null && info.size > 0) {
            outBuf.position(info.offset)
            outBuf.limit(info.offset + info.size)
            readPcm(outBuf, pcm, onChunk)
          }
          codec.releaseOutputBuffer(outIndex, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            outputDone = true
          }
        }
      }
    }
  }

  /** 把解码输出（16bit 或 float）转成交错 16bit PCM，按 [out] 大小分段回调 */
  private fun readPcm(buffer: ByteBuffer, out: ShortArray, onChunk: (ShortArray, Int) -> Unit) {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
      var remaining = buffer.remaining() / 4
      while (remaining > 0) {
        val count = min(remaining, out.size)
        for (i in 0 until count) {
          val f = buffer.float
          out[i] = (f * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        onChunk(out, count)
        remaining -= count
      }
    } else {
      var remaining = buffer.remaining() / 2
      while (remaining > 0) {
        val count = min(remaining, out.size)
        for (i in 0 until count) {
          out[i] = buffer.short
        }
        onChunk(out, count)
        remaining -= count
      }
    }
  }

  override fun close() {
    runCatching { codec.stop() }
    runCatching { codec.release() }
    runCatching { extractor.release() }
  }
}

/**
 * jump3r（纯 Java LAME 移植）的极简封装：只做 16bit PCM -> MP3。
 *
 * 不使用 jump3r 自带的 LameEncoder（其构造参数依赖 Android 上不存在的 javax.sound.sampled），
 * 这里按 LameEncoder 的做法手工装配 LAME 各模块。
 */
private class LameMp3Encoder(
  private val channels: Int,
  sampleRate: Int,
  bitrateKbps: Int,
  quality: Int,
) {

  private val lame = Lame()
  private val gfp: LameGlobalFlags

  init {
    require(channels == 1 || channels == 2) { "Unsupported channels: $channels" }

    val gaud = GetAudio()
    val ga = GainAnalysis()
    val bs = BitStream()
    val p = Presets()
    val qupvt = QuantizePVT()
    val qu = Quantize()
    val vbr = VBRTag()
    val ver = Version()
    val id3 = ID3Tag()
    val rv = Reservoir()
    val tak = Takehiro()
    val parse = Parse()
    val mpg = MPGLib()
    val intf = Interface()
    val common = Common()
    @Suppress("UNUSED_VARIABLE")
    val hist = BRHist()

    lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
    bs.setModules(ga, mpg, ver, vbr)
    id3.setModules(bs, ver)
    p.setModules(lame)
    qu.setModules(bs, rv, qupvt, tak)
    qupvt.setModules(tak, rv, lame.enc.psy)
    rv.setModules(bs)
    tak.setModules(qupvt)
    vbr.setModules(lame, bs, ver)
    gaud.setModules(parse, mpg)
    parse.setModules(ver, id3, p)
    mpg.setModules(intf, common)
    intf.setModules(vbr, common)

    gfp = lame.lame_init()
    gfp.num_channels = channels
    gfp.in_samplerate = sampleRate
    gfp.out_samplerate = sampleRate
    gfp.brate = bitrateKbps
    gfp.quality = quality
    id3.id3tag_init(gfp)
    // 不写 LAME 自带的 ID3（标签交给 jaudiotagger 写）
    gfp.write_id3tag_automatic = false
    gfp.findReplayGain = true

    val ret = lame.lame_init_params(gfp)
    if (ret < 0) throw AudioConverter.ConvertException("lame_init_params failed: $ret")
  }

  /** 编码 [frames] 个采样帧（[pcm] 为交错 16bit），返回写入 [out] 的字节数 */
  fun encode(pcm: ShortArray, frames: Int, out: ByteArray): Int {
    val left = IntArray(frames)
    val right = IntArray(frames)
    if (channels == 2) {
      for (i in 0 until frames) {
        left[i] = pcm[2 * i].toInt() shl 16
        right[i] = pcm[2 * i + 1].toInt() shl 16
      }
    } else {
      for (i in 0 until frames) {
        val v = pcm[i].toInt() shl 16
        left[i] = v
        right[i] = v
      }
    }
    // LAME 的 int 接口使用 32bit 满量程，故左移 16 位
    return lame.lame_encode_buffer_int(gfp, left, right, frames, out, 0, out.size)
  }

  fun flush(out: ByteArray): Int = lame.lame_encode_flush(gfp, out, 0, out.size)

  fun close() {
    runCatching { lame.lame_close(gfp) }
  }
}

private const val CHUNK_FRAMES = 8192
private const val TIMEOUT_US = 10_000L

private fun Closeable.closeQuietly() {
  try {
    close()
  } catch (e: Exception) {
    Timber.w(e)
  }
}
