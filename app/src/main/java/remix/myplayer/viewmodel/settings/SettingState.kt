package remix.myplayer.viewmodel.settings

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import remix.myplayer.data.model.misc.LyricOrder
import remix.myplayer.ui.screen.playing.PlayingCoverAnimationStyle

@Stable
data class CommonSettings(
  val scanSize: Int,
  val lockScreen: Int,
  val manualScanFolder: String,
  val blacklist: Set<String>,
  val deleteIds: Set<String>,
  val language: Int,
  val uiFontScale: Float,
  val shake: Boolean,
  val showDisplayName: Boolean,
  /** 歌曲列表底部栏的歌名是否使用文件名（而非元数据歌名） */
  val useFilenameInBottomBar: Boolean,
  /** 播放页顶部标题是否使用文件名（而非元数据歌名） */
  val useFilenameInPlayingTitle: Boolean,
)

@Stable
data class PlaySettings(
  val ignoreAudioFocus: Boolean,
  val decoderMode: Int,
  val playAtBreakPoint: Boolean,
  val crossFade: Boolean,
  val autoPlay: Int,
  val speed: String,
  val listLoop: Boolean,
  val replayGainEnabled: Boolean,
  val replayGainMode: Int,
  val replayGainPeakProtection: Boolean,
  val replayGainPreampDb: Float,
  val replayGainMissingGainDb: Float,
)

@Stable
data class ColorSettings(
  val primaryColor: Color,
  val secondaryColor: Color,
  val darkTheme: String,
  val blackTheme: Boolean,
  val coloredNaviBar: Boolean,
)

@Stable
data class LibrarySettings(
  val songSortOrder: String,
  val albumSortOrder: String,
  val artistSortOrder: String,
  val playlistSortOrder: String,
  val genreSortOrder: String,
  val folderSortOrder: String,
  val historySortOrder: String,
  val albumDetailSortOrder: String,
  val artistDetailSortOrder: String,
  @Deprecated("use SettingPrefs.getPlayListDetailSortOrder(playlistId) instead")
  val playListDetailSortOrder: String,
  val genreDetailSortOrder: String,
  val folderDetailSortOrder: String,
  val albumMode: Int,
  val artistMode: Int,
  val genreMode: Int,
  val playlistMode: Int,
  /** 歌曲列表排序菜单中可显示的排序规则集合（至少保留 1 项） */
  val songSortRules: Set<String>,
)

@Stable
data class PlayingScreenSettings(
  val background: Int,
  val bottom: Int,
  val keepScreenOn: Boolean,
)

@Stable
data class CoverSettings(
  val ignoreMediaStore: Boolean,
  val autoDownloadCover: Int,
  val downloadSource: Int,
  val coverAnimationStyle: PlayingCoverAnimationStyle,
  val coverAnimationSpeed: Float,
)

@Stable
data class LyricSettings(
  val desktopLyricEnabled: Boolean,
  val statusBarLyricEnabled: Boolean,
  val translationEnabled: Boolean,
  val fontScale: Float,
  val generalLyricOrder: List<LyricOrder>,
)

@Stable
data class NotificationSettings(
  val classicNotify: Boolean,
  val notifyUseSystemBackground: Boolean,
)

@Stable
data class ListSettings(
  /** 列表条目标签显示 */
  val showTag: Boolean,
  /** 批量重命名默认模板（占位符：{title}{artist}{album}{track}{year}） */
  val defaultRenameTemplate: String = "{artist} - {title}",
  /** 列表条目标签管理（五角星按钮） */
  val tagManage: Boolean,
  /** 是否显示"音乐标签编辑"入口（列表条目菜单/播放页菜单） */
  val showTagEdit: Boolean,
  /** 显示歌曲列表序号 */
  val showNumber: Boolean,
  /** 显示艺术家-专辑名 */
  val showArtistAlbum: Boolean,
  /** 歌曲列表底部栏的歌名下方是否显示该歌曲的标签 */
  val bottomBarShowTag: Boolean,
  /** 播放页标题的歌名下方是否显示该歌曲的标签 */
  val playingTitleShowTag: Boolean,
  /** 歌曲列表底部栏的歌名下方是否显示艺术家-专辑名 */
  val bottomBarShowArtistAlbum: Boolean,
  /** 播放页标题的歌名下方是否显示艺术家-专辑名 */
  val playingTitleShowArtistAlbum: Boolean,
)

@Stable
data class TagSettings(
  /** 标签弹窗是否智能排序（最近使用时间 + 使用次数），否则按创建时间固定排列 */
  val smartSort: Boolean,
  /** 固定位置排序时是否按标签创建时间倒序（新创建的在前） */
  val createdDesc: Boolean,
)

@Stable
data class AnalysisSettings(
  /** 一天的分界点小时（该小时之前算前一天，默认 5 点） */
  val dayStartHour: Int,
)

@Stable
data class OtherSettings(
  /** 是否记录运行日志到文件 */
  val logEnabled: Boolean,
)

@Stable
data class SettingsState(
  val common: CommonSettings,
  val play: PlaySettings,
  val color: ColorSettings,
  val library: LibrarySettings,
  val playingScreen: PlayingScreenSettings,
  val cover: CoverSettings,
  val lyric: LyricSettings,
  val notification: NotificationSettings,
  val list: ListSettings,
  val tag: TagSettings,
  val analysis: AnalysisSettings,
  val other: OtherSettings,
)
