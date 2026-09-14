package remix.myplayer.helper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.data.db.room.entity.PlayEvent
import remix.myplayer.data.model.audio.Song
import remix.myplayer.repo.PlayStatsRepository
import remix.myplayer.repo.StatsTimeUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放会话追踪（数据分析用）。
 *
 * 同一首歌在换歌之前只算一条会话：暂停后继续会累加播放时长与最大进度，
 * 因此"中途中断再继续听完"仍会被判定为完整播放（>=80%）。
 * 换歌时结束会话并写入数据库，未达到 80% 的会话标记为"跳过"。
 *
 * 所有方法都在主线程调用（由播放服务的回调与 1 秒 tick 驱动）。
 */
@Singleton
class PlayEventTracker @Inject constructor(
  private val repo: PlayStatsRepository,
) {

  /** 结束原因 */
  enum class FinishReason {
    /** 切换到了另一首歌（未听完则算跳过） */
    NEW_SONG,

    /** 停止播放/服务销毁 */
    STOP
  }

  private class Session(
    val song: Song,
    val startTime: Long,
    var lastActiveTime: Long,
    var playedMs: Long,
    var maxPositionMs: Long,
    var lastTickTime: Long,
    /** 整点小时时间戳 -> 该小时内实际播放时长，用于每日播放时长与时段热力图 */
    val hourlyPlayedMs: MutableMap<Long, Long> = mutableMapOf()
  )

  private var scope: CoroutineScope? = null
  private var session: Session? = null

  /** 播放服务创建时调用 */
  fun attach(scope: CoroutineScope) {
    this.scope = scope
  }

  /** 播放服务销毁时调用：结束当前会话 */
  fun detach() {
    finish(FinishReason.STOP)
    scope = null
  }

  /** 一首歌开始播放（切歌/首次播放/单曲循环重新开始） */
  fun onSongStarted(song: Song, positionMs: Long) {
    if (!song.isLocal()) {
      // 远程歌曲不参与统计（无本地文件标签）
      finish(FinishReason.STOP)
      return
    }
    if (session?.song?.data == song.data) {
      // 同一首歌再次开始（单曲循环等）：把之前的会话收尾后重新开始
      finish(FinishReason.NEW_SONG)
    } else if (session != null) {
      finish(FinishReason.NEW_SONG)
    }
    val now = System.currentTimeMillis()
    session = Session(
      song = song,
      startTime = now,
      lastActiveTime = now,
      playedMs = 0,
      maxPositionMs = positionMs.coerceAtLeast(0),
      lastTickTime = now
    )
  }

  /** 每秒 tick：累计播放时长（按秒归入所在的整点小时）与最大进度 */
  fun tick(isPlaying: Boolean, positionMs: Long) {
    val current = session ?: return
    val now = System.currentTimeMillis()
    if (isPlaying) {
      // 单次增量限制在 3 秒内，避免休眠/卡顿导致的异常累加
      val delta = (now - current.lastTickTime).coerceIn(0L, 3_000L)
      current.playedMs += delta
      if (delta > 0) {
        // 按秒切分：这段播放时长算在它实际发生的小时里，跨小时的会话会被拆到多个小时
        val hourStart = StatsTimeUtil.hourStartTime(now)
        current.hourlyPlayedMs[hourStart] = (current.hourlyPlayedMs[hourStart] ?: 0L) + delta
      }
      current.lastActiveTime = now
      current.maxPositionMs = maxOf(current.maxPositionMs, positionMs.coerceAtLeast(0))
    }
    current.lastTickTime = now
  }

  /** 暂停：记录"最后一次暂停时间"（用于统计最晚还在听音乐的时间） */
  fun onPaused() {
    session?.lastActiveTime = System.currentTimeMillis()
  }

  /** 结束当前会话并写入数据库 */
  fun finish(reason: FinishReason) {
    val current = session ?: return
    session = null

    // 太短的会话（误触/秒切）不记录
    if (current.playedMs < MIN_SESSION_MS && current.maxPositionMs <= 0) {
      return
    }

    val now = System.currentTimeMillis()
    val skipped = reason == FinishReason.NEW_SONG
    val song = current.song

    scope?.launch {
      // duration 在缺失时会读文件，放到后台线程取
      val durationMs = withContext(Dispatchers.IO) { song.duration }
      val completed = if (durationMs > 0) {
        current.maxPositionMs >= durationMs * PlayEvent.COMPLETE_RATIO
      } else {
        current.playedMs >= PlayEvent.COMPLETE_FALLBACK_MS
      }

      repo.addPlayEvent(
        PlayEvent(
          path = song.data,
          title = song.title,
          artist = song.artist,
          album = song.album,
          startTime = current.startTime,
          endTime = now,
          lastActiveTime = maxOf(current.lastActiveTime, current.startTime),
          playedMs = current.playedMs,
          maxPositionMs = current.maxPositionMs,
          durationMs = durationMs,
          completed = completed,
          skipped = skipped && !completed
        ),
        current.hourlyPlayedMs
      )
    }
  }

  private companion object {
    /** 小于该时长的会话不记录 */
    const val MIN_SESSION_MS = 2_000L
  }
}
