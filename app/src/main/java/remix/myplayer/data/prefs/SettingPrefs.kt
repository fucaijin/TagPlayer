package remix.myplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import remix.myplayer.data.model.misc.TagFilterMode
import remix.myplayer.data.model.misc.TagSortSetting
import remix.myplayer.helper.LanguageHelper.AUTO
import remix.myplayer.helper.SortOrder
import remix.myplayer.util.Constants.MB
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingPrefsEntryPoint {

  fun settingPrefs(): SettingPrefs
}

@Singleton
class SettingPrefs @Inject constructor(
  @ApplicationContext context: Context
) : AbstractPref(context, PrefKeys.Setting.NAME) {

  var firstLoad by PrefsDelegate(sp, PrefKeys.Setting.FIRST_LOAD, true)

  var libraryJson by PrefsDelegate(sp, PrefKeys.Setting.LIBRARY, "")

  var scanSize by PrefsDelegate(sp, PrefKeys.Setting.SCAN_SIZE, MB)

  var songSortOrder by PrefsDelegate(sp, PrefKeys.Setting.SONG_SORT_ORDER, SortOrder.SONG_A_Z)
  var albumSortOrder by PrefsDelegate(sp, PrefKeys.Setting.ALBUM_SORT_ORDER, SortOrder.ALBUM_A_Z)
  var artistSortOrder by PrefsDelegate(sp, PrefKeys.Setting.ARTIST_SORT_ORDER, SortOrder.ARTIST_A_Z)
  var playlistSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.PLAYLIST_SORT_ORDER,
    SortOrder.PLAYLIST_DATE
  )
  var genreSortOrder by PrefsDelegate(sp, PrefKeys.Setting.GENRE_SORT_ORDER, SortOrder.GENRE_A_Z)
  var folderSortOrder by PrefsDelegate(sp, PrefKeys.Setting.FOLDER_SORT_ORDER, SortOrder.FOLDER_A_Z)
  var historySortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.HISTORY_SORT_ORDER,
    SortOrder.PLAY_COUNT_DESC
  )

  var albumDetailSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.CHILD_ALBUM_SONG_SORT_ORDER,
    SortOrder.TRACK_NUMBER
  )
  var artistDetailSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.CHILD_ARTIST_SONG_SORT_ORDER,
    SortOrder.SONG_A_Z
  )

  @Deprecated("use getPlayListDetailSortOrder(playlistId) / setPlayListDetailSortOrder(playlistId, order) instead")
  var playListDetailSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.CHILD_PLAYLIST_SONG_SORT_ORDER,
    SortOrder.SONG_A_Z
  )
  var genreDetailSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.CHILD_GENRE_SONG_SORT_ORDER,
    SortOrder.SONG_A_Z
  )
  var folderDetailSortOrder by PrefsDelegate(
    sp,
    PrefKeys.Setting.CHILD_FOLDER_SONG_SORT_ORDER,
    SortOrder.SONG_A_Z
  )

  fun getPlayListDetailSortOrder(playlistId: Long): String {
    val key = playListDetailSortKey(playlistId)
    return sp.getString(key, null) ?: playListDetailSortOrder
  }

  fun setPlayListDetailSortOrder(playlistId: Long, sortOrder: String): Boolean {
    val key = playListDetailSortKey(playlistId)
    val current = sp.getString(key, null) ?: playListDetailSortOrder
    if (current == sortOrder) {
      return false
    }
    sp.edit(commit = true) {
      putString(key, sortOrder)
    }
    return true
  }

  private fun playListDetailSortKey(playlistId: Long): String {
    return PrefKeys.Setting.CHILD_PLAYLIST_SONG_SORT_ORDER_PREFIX + playlistId
  }

  var albumMode by PrefsDelegate(sp, PrefKeys.Setting.MODE_FOR_ALBUM, GRID_MODE)
  var artistMode by PrefsDelegate(sp, PrefKeys.Setting.MODE_FOR_ARTIST, GRID_MODE)
  var genreMode by PrefsDelegate(sp, PrefKeys.Setting.MODE_FOR_GENRE, GRID_MODE)
  var playlistMode by PrefsDelegate(sp, PrefKeys.Setting.MODE_FOR_PLAYLIST, GRID_MODE)

  var manualScanFolder by PrefsDelegate(sp, PrefKeys.Setting.MANUAL_SCAN_FOLDER, "")
  var deleteIds by PrefsDelegate(sp, PrefKeys.Setting.BLACKLIST_SONG, emptySet<String>())
  var blacklist by PrefsDelegate(sp, PrefKeys.Setting.BLACKLIST, emptySet<String>())
  var deleteSource by PrefsDelegate(sp, PrefKeys.Setting.DELETE_SOURCE, false)

  var lockScreen by PrefsDelegate(sp, PrefKeys.Setting.LOCKSCREEN, LOCKSCREEN_SYSTEM)
  var language by PrefsDelegate(sp, PrefKeys.Setting.LANGUAGE, AUTO)
  var uiFontScale by PrefsDelegate(
    sp,
    PrefKeys.Setting.UI_FONT_SCALE,
    UI_FONT_SCALE_DEFAULT
  )
  var playAtBreakPoint by PrefsDelegate(sp, PrefKeys.Setting.PLAY_AT_BREAKPOINT, false)
  var shake by PrefsDelegate(sp, PrefKeys.Setting.SHAKE, false)
  var showDisplayName by PrefsDelegate(sp, PrefKeys.Setting.SHOW_DISPLAYNAME, false)
  var bottomBarUseFilename by PrefsDelegate(sp, PrefKeys.Setting.BOTTOM_BAR_USE_FILENAME, false)
  var playingTitleUseFilename by PrefsDelegate(sp, PrefKeys.Setting.PLAYING_TITLE_USE_FILENAME, false)

  /** 歌曲列表排序菜单中可显示的排序规则集合（至少保留 1 项），默认全部显示 */
  var songSortRules by PrefsDelegate(
    sp,
    PrefKeys.Setting.SONG_SORT_RULES,
    setOf(
      SortOrder.SONG_A_Z, SortOrder.SONG_Z_A,
      SortOrder.DISPLAY_NAME_A_Z, SortOrder.DISPLAY_NAME_Z_A,
      SortOrder.ALBUM_A_Z, SortOrder.ALBUM_Z_A,
      SortOrder.ARTIST_A_Z, SortOrder.ARTIST_Z_A,
      SortOrder.DATE, SortOrder.DATE_DESC
    )
  )

  /** 数据分析：各模块是否显示的开关集合（默认全部显示） */
  var analysisModules by PrefsDelegate(
    sp,
    PrefKeys.Setting.ANALYSIS_MODULES,
    setOf(
      "app_usage", "play_count", "play_duration", "skipped",
      "daily_duration", "trend", "daily_active", "tag_song_count",
      "favorite_tags", "search_ranking", "heatmap", "heatmap_24h"
    )
  )
  /** 数据分析：列表类模块的显示行数（默认 10，范围 5~100）。旧版全局值，仅用于未单独设置时兜底 */
  var analysisListRows by PrefsDelegate(sp, PrefKeys.Setting.ANALYSIS_LIST_ROWS, DEFAULT_ANALYSIS_LIST_ROWS)

  /** 数据分析：某个列表模块自己的显示行数；未单独设置过则沿用全局行数（兼容旧设置） */
  fun analysisModuleRows(moduleKey: String): Int {
    val key = PrefKeys.Setting.ANALYSIS_MODULE_ROWS_PREFIX + moduleKey
    return if (sp.contains(key)) {
      sp.getInt(key, DEFAULT_ANALYSIS_LIST_ROWS)
    } else {
      analysisListRows
    }
  }

  /** 数据分析：设置某个列表模块自己的显示行数 */
  fun setAnalysisModuleRows(moduleKey: String, rows: Int) {
    sp.edit(commit = true) {
      putInt(
        PrefKeys.Setting.ANALYSIS_MODULE_ROWS_PREFIX + moduleKey,
        rows.coerceIn(MIN_ANALYSIS_LIST_ROWS, MAX_ANALYSIS_LIST_ROWS)
      )
    }
  }
  /** 热力图横坐标显示的时间个数（24/12/8/6，默认 24；格子始终 24 个） */
  var heatmapTimeLabels by PrefsDelegate(sp, PrefKeys.Setting.HEATMAP_TIME_LABELS, 24)

  /** 批量重命名默认模板（占位符：{title}{artist}{album}{track}{year}），打开弹窗时预填 */
  var defaultRenameTemplate by PrefsDelegate(
    sp,
    PrefKeys.Setting.DEFAULT_RENAME_TEMPLATE,
    "{artist} - {title}"
  )

  // 歌曲列表相关开关
  var listShowTag by PrefsDelegate(sp, PrefKeys.Setting.LIST_SHOW_TAG, true)
  var listTagManage by PrefsDelegate(sp, PrefKeys.Setting.LIST_TAG_MANAGE, true)
  var listTagEdit by PrefsDelegate(sp, PrefKeys.Setting.LIST_TAG_EDIT, true)
  var listShowNumber by PrefsDelegate(sp, PrefKeys.Setting.LIST_SHOW_NUMBER, true)
  var listShowArtistAlbum by PrefsDelegate(sp, PrefKeys.Setting.LIST_SHOW_ARTIST_ALBUM, true)

  /** 歌曲列表底部栏的歌名下方是否显示该歌曲的标签 */
  var bottomBarShowTag by PrefsDelegate(sp, PrefKeys.Setting.BOTTOM_BAR_SHOW_TAG, true)

  /** 播放页标题的歌名下方是否显示该歌曲的标签 */
  var playingTitleShowTag by PrefsDelegate(sp, PrefKeys.Setting.PLAYING_TITLE_SHOW_TAG, true)

  /** 歌曲列表底部栏的歌名下方是否显示艺术家-专辑名 */
  var bottomBarShowArtistAlbum by PrefsDelegate(
    sp,
    PrefKeys.Setting.BOTTOM_BAR_SHOW_ARTIST_ALBUM,
    true
  )

  /** 播放页标题的歌名下方是否显示艺术家-专辑名 */
  var playingTitleShowArtistAlbum by PrefsDelegate(
    sp,
    PrefKeys.Setting.PLAYING_TITLE_SHOW_ARTIST_ALBUM,
    true
  )

  /** 标签过滤模式（TagFilterMode.name），下次启动保持上次选择 */
  var tagFilterMode by PrefsDelegate(
    sp,
    PrefKeys.Setting.TAG_FILTER_MODE,
    TagFilterMode.INCLUDE_AND.name
  )

  /** 互斥模式下左侧"包含"侧是否按"与"过滤（同时带所有选中标签），否则按"或" */
  var tagFilterExcludeIncludeAnd by PrefsDelegate(
    sp,
    PrefKeys.Setting.TAG_FILTER_EXCLUDE_INCLUDE_AND,
    true
  )

  /** 互斥模式下右侧"排除"侧是否按"与"过滤（同时带所有选中标签才排除），否则按"或" */
  var tagFilterExcludeExcludeAnd by PrefsDelegate(
    sp,
    PrefKeys.Setting.TAG_FILTER_EXCLUDE_EXCLUDE_AND,
    true
  )

  /** 标签弹窗是否使用智能排序（最近使用时间 + 使用次数），默认关闭（按创建时间固定排列） */
  var tagSmartSort by PrefsDelegate(sp, PrefKeys.Setting.TAG_SMART_SORT, false)

  /** 固定位置排序时是否按标签创建时间倒序（新创建的在前），默认正序 */
  var tagCreatedDesc by PrefsDelegate(sp, PrefKeys.Setting.TAG_CREATED_DESC, false)

  /** 数据分析：一天的分界点小时（默认 5 点，凌晨 5 点前算前一天） */
  var statsDayStartHour by PrefsDelegate(sp, PrefKeys.Setting.STATS_DAY_START_HOUR, 5)

  /** 是否记录运行日志到文件，默认开启 */
  var logEnabled by PrefsDelegate(sp, PrefKeys.Setting.LOG_ENABLED, true)

  var ignoreAudioFocus by PrefsDelegate(sp, PrefKeys.Setting.AUDIO_FOCUS, false)
  var decoderMode by PrefsDelegate(
    sp,
    PrefKeys.Setting.AUDIO_DECODER_MODE,
    DECODER_MODE_DEFAULT
  )
  var autoPlay by PrefsDelegate(sp, PrefKeys.Setting.AUTO_PLAY, NEVER)
  var crossFade by PrefsDelegate(sp, PrefKeys.Setting.CROSS_FADE, false)
  var speed by PrefsDelegate(sp, PrefKeys.Setting.SPEED, "1.0")
  var replayGainEnabled by PrefsDelegate(sp, PrefKeys.Setting.REPLAY_GAIN_ENABLED, false)
  var replayGainMode by PrefsDelegate(sp, PrefKeys.Setting.REPLAY_GAIN_MODE, REPLAY_GAIN_MODE_TRACK)
  var replayGainPeakProtection by PrefsDelegate(
    sp,
    PrefKeys.Setting.REPLAY_GAIN_PEAK_PROTECTION,
    true
  )
  var replayGainPreampDb by PrefsDelegate(sp, PrefKeys.Setting.REPLAY_GAIN_PREAMP, 0f)
  var replayGainMissingGainDb by PrefsDelegate(
    sp,
    PrefKeys.Setting.REPLAY_GAIN_MISSING_GAIN,
    0f
  )
  val speedValue get() = speed.toFloat()
  var playModel by PrefsDelegate(sp, PrefKeys.Setting.PLAY_MODEL, MODE_LOOP)
  var listLoop by PrefsDelegate(sp, PrefKeys.Setting.LIST_LOOP, true)
  var lastSong by PrefsDelegate(sp, PrefKeys.Setting.LAST_SONG, "")
  var lastProgress by PrefsDelegate(sp, PrefKeys.Setting.LAST_PLAY_PROGRESS, 0)

  var playingScreenBackground by PrefsDelegate(
    sp,
    PrefKeys.Setting.PLAYER_BACKGROUND,
    BACKGROUND_ADAPTIVE_COLOR
  )
  var playingCoverAnimationStyle by PrefsDelegate(
    sp,
    PrefKeys.Setting.PLAYING_COVER_ANIMATION_STYLE,
    COVER_ANIMATION_CLASSIC
  )
  var playingCoverAnimationSpeed by PrefsDelegate(
    sp,
    PrefKeys.Setting.PLAYING_COVER_ANIMATION_SPEED,
    COVER_ANIMATION_SPEED_DEFAULT
  )
  var playingScreenBottom by PrefsDelegate(
    sp,
    PrefKeys.Setting.BOTTOM_OF_NOW_PLAYING_SCREEN,
    BOTTOM_SHOW_BOTH
  )
  var keepScreenOn by PrefsDelegate(sp, PrefKeys.Setting.SCREEN_ALWAYS_ON, false)

  var ignoreMediaStore by PrefsDelegate(sp, PrefKeys.Setting.IGNORE_MEDIA_STORE, false)
  var autoDownloadCover by PrefsDelegate(
    sp,
    PrefKeys.Setting.AUTO_DOWNLOAD_ALBUM_COVER,
    DOWNLOAD_COVER_ALWAYS
  )
  var downloadSource by PrefsDelegate(
    sp,
    PrefKeys.Setting.ALBUM_COVER_DOWNLOAD_SOURCE,
    DOWNLOAD_NETEASE
  )

  var classicNotify by PrefsDelegate(sp, PrefKeys.Setting.NOTIFY_STYLE_CLASSIC, false)
  var notifyUseSystemBackground by PrefsDelegate(sp, PrefKeys.Setting.NOTIFY_SYSTEM_COLOR, true)

  var exitAfterTimerFinish by PrefsDelegate(sp, PrefKeys.Setting.TIMER_EXIT_AFTER_FINISH, false)
  var timerStartAuto by PrefsDelegate(sp, PrefKeys.Setting.TIMER_DEFAULT, false)
  var timerDefaultDuration by PrefsDelegate(sp, PrefKeys.Setting.TIMER_DURATION, -1)

  var bassBoostStrength by PrefsDelegate(sp, PrefKeys.Setting.BASS_BOOST_STRENGTH, 0)
  var enableEq by PrefsDelegate(sp, PrefKeys.Setting.ENABLE_EQ, false)

  var checkMigration16600 by PrefsDelegate(sp, "check_migration_16600", false)
  var checkMigration20500 by PrefsDelegate(sp, "check_migration_20500", false)
  var checkMigration21100 by PrefsDelegate(sp, "check_migration_21100", false)

  companion object {

    // 数据分析：列表模块显示行数的默认值与取值范围（未修改过的模块一律为默认 10）
    const val DEFAULT_ANALYSIS_LIST_ROWS = 10
    const val MIN_ANALYSIS_LIST_ROWS = 5
    const val MAX_ANALYSIS_LIST_ROWS = 100

    // 播放界面底部
    const val BOTTOM_SHOW_NEXT = 0
    const val BOTTOM_SHOW_VOLUME = 1
    const val BOTTOM_SHOW_BOTH = 2
    const val BOTTOM_SHOW_NONE = 3

    // 播放界面背景
    const val BACKGROUND_THEME = 0
    const val BACKGROUND_ADAPTIVE_COLOR = 1
    const val BACKGROUND_CUSTOM_IMAGE = 2

    // 播放页封面切换动画
    const val COVER_ANIMATION_CLASSIC = "classic"
    const val COVER_ANIMATION_PARALLAX_PUSH = "parallax_push"
    const val COVER_ANIMATION_CARD_SQUEEZE = "card_squeeze"
    const val COVER_ANIMATION_PAGE_TURN = "page_turn"
    const val COVER_ANIMATION_SLICE_STAGGER = "slice_stagger"
    const val COVER_ANIMATION_DISSOLVE_ZOOM = "dissolve_zoom"
    const val COVER_ANIMATION_SPEED_DEFAULT = 1.5f

    // 封面下载
    const val DOWNLOAD_COVER_ALWAYS = 0
    const val DOWNLOAD_COVER_WIFI_ONLY = 1
    const val DOWNLOAD_COVER_NEVER = 2

    const val CLASSIC_NOTIFY_BACKGROUND_SYSTEM = 0

    // 0:软件锁屏 1:系统锁屏 2:关闭
    const val LOCKSCREEN_APLAYER: Int = 0
    const val LOCKSCREEN_SYSTEM: Int = 1
    const val LOCKSCREEN_CLOSE: Int = 2

    // 播放模式
    const val MODE_LOOP: Int = 1
    const val MODE_SHUFFLE: Int = 2
    const val MODE_REPEAT: Int = 3

    // 音频解码方式
    const val DECODER_MODE_DEFAULT: Int = 0
    const val DECODER_MODE_FFMPEG: Int = 1

    // 自动播放
    const val HEADSET_PLUG = 0
    const val OPEN_SOFTWARE = 1
    const val NEVER = 2

    // 回放增益模式
    const val REPLAY_GAIN_MODE_TRACK = 0
    const val REPLAY_GAIN_MODE_ALBUM = 1
    const val REPLAY_GAIN_GAIN_MIN_DB = -15f
    const val REPLAY_GAIN_GAIN_MAX_DB = 15f
    const val REPLAY_GAIN_GAIN_STEP_DB = 0.5f

    // 封面下载源
    const val DOWNLOAD_LASTFM = 0
    const val DOWNLOAD_NETEASE = 1

    const val LIST_MODE = 0
    const val GRID_MODE = 1

    const val UI_FONT_SCALE_DEFAULT = 1.0f
    const val UI_FONT_SCALE_MIN = 0.85f
    const val UI_FONT_SCALE_MAX = 1.5f
    const val UI_FONT_SCALE_STEP = 0.05f

    fun normalizeUiFontScale(scale: Float): Float {
      val snapped = (scale / UI_FONT_SCALE_STEP).roundToInt() * UI_FONT_SCALE_STEP
      return snapped.coerceIn(UI_FONT_SCALE_MIN, UI_FONT_SCALE_MAX)
    }

    fun normalizeReplayGainGainDb(gainDb: Float): Float {
      val snapped = (gainDb / REPLAY_GAIN_GAIN_STEP_DB).roundToInt() * REPLAY_GAIN_GAIN_STEP_DB
      return snapped.coerceIn(REPLAY_GAIN_GAIN_MIN_DB, REPLAY_GAIN_GAIN_MAX_DB)
    }
  }
}

fun SettingPrefs.tagSortFlow(): Flow<TagSortSetting> {
  return callbackFlow {
    trySend(TagSortSetting(tagSmartSort, tagCreatedDesc))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == PrefKeys.Setting.TAG_SMART_SORT || key == PrefKeys.Setting.TAG_CREATED_DESC) {
        trySend(TagSortSetting(tagSmartSort, tagCreatedDesc))
      }
    }
    sp.registerOnSharedPreferenceChangeListener(listener)
    awaitClose {
      sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
  }.distinctUntilChanged()
}

fun SettingPrefs.playlistSortOrderFlow(): Flow<String> {
  return callbackFlow {
    trySend(playlistSortOrder)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == PrefKeys.Setting.PLAYLIST_SORT_ORDER) {
        trySend(playlistSortOrder)
      }
    }
    sp.registerOnSharedPreferenceChangeListener(listener)
    awaitClose {
      sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
  }.distinctUntilChanged()
}

fun SettingPrefs.historySortOrderFlow(): Flow<String> {
  return callbackFlow {
    trySend(historySortOrder)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == PrefKeys.Setting.HISTORY_SORT_ORDER) {
        trySend(historySortOrder)
      }
    }
    sp.registerOnSharedPreferenceChangeListener(listener)
    awaitClose {
      sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
  }.distinctUntilChanged()
}
