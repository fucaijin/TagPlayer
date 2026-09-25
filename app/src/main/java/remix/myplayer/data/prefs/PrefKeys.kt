package remix.myplayer.data.prefs

/**
 * 统一管理 SharedPreferences 的键名。
 */
object PrefKeys {

  object Setting : Keys() {

    /** Setting 文件名 */
    const val NAME = "Setting"

    /** 第一次读取数据 */
    const val FIRST_LOAD = "first_load"

    /** 是否开启屏幕常亮 */
    const val SCREEN_ALWAYS_ON = "key_screen_always_on"

    /** 通知栏是否启用经典样式 */
    const val NOTIFY_STYLE_CLASSIC = "notify_classic"

    /** 是否自动下载专辑封面 */
    const val AUTO_DOWNLOAD_ALBUM_COVER = "auto_download_album_cover_v1"

    /** 曲库配置 */
    const val LIBRARY = "library_category"

    /** 锁屏设置 */
    const val LOCKSCREEN = "lockScreen"

    /** 摇一摇 */
    const val SHAKE = "shake"

    /** 是否开启桌面歌词 */
    const val DESKTOP_LYRIC_SHOW = "desktop_lyric_show"

    /** 是否开启状态栏歌词 */
    const val STATUSBAR_LYRIC_SHOW = "statusbar_lyric_show"

    /** 沉浸式状态栏 */
    const val IMMERSIVE_MODE = "immersive_mode"

    /** 过滤大小 */
    const val SCAN_SIZE = "scan_size"

    /** 强制按拼音排序 */
    const val FORCE_SORT = "force_sort"

    /** 歌曲排序顺序 */
    const val SONG_SORT_ORDER = "song_sort_order"

    /** 专辑排序顺序 */
    const val ALBUM_SORT_ORDER = "album_sort_order"

    /** 艺术家排序顺序 */
    const val ARTIST_SORT_ORDER = "artist_sort_order"

    /** 播放列表排序顺序 */
    const val PLAYLIST_SORT_ORDER = "playlist_sort_order"

    /** 流派排序 */
    const val GENRE_SORT_ORDER = "genre_sort_order"

    /** 文件夹排序顺序 */
    const val FOLDER_SORT_ORDER = "folder_sort_order"

    /** 文件夹内歌曲排序顺序 */
    const val CHILD_FOLDER_SONG_SORT_ORDER = "child_folder_song_sort_order"

    /** 艺术家内歌曲排序顺序 */
    const val CHILD_ARTIST_SONG_SORT_ORDER = "child_artist_sort_order"

    /** 专辑内歌曲排序顺序 */
    const val CHILD_ALBUM_SONG_SORT_ORDER = "child_album_song_sort_order"

    /** 播放列表内歌曲排序顺序 */
    const val CHILD_PLAYLIST_SONG_SORT_ORDER = "child_playlist_song_sort_order"
    const val CHILD_PLAYLIST_SONG_SORT_ORDER_PREFIX = "child_playlist_song_sort_order_"

    /** 流派内歌曲排序顺序 */
    const val CHILD_GENRE_SONG_SORT_ORDER = "child_genre_song_sort_order"

    /** 播放次数排序 */
    const val HISTORY_SORT_ORDER = "history_sort_order"

    /** 移除歌曲 */
    const val BLACKLIST_SONG = "black_list_song"

    /** 黑名单 */
    const val BLACKLIST = "blacklist"

    /** 退出时播放时间 */
    const val LAST_PLAY_PROGRESS = "last_play_progress"

    /** 退出时播放的歌曲 */
    const val LAST_SONG = "last_song"

    /** 播放模式 */
    const val PLAY_MODEL = "play_model"

    /** 列表是否循环 */
    const val LIST_LOOP = "list_loop"

    /** 经典通知栏背景是否是系统背景色 */
    const val NOTIFY_SYSTEM_COLOR = "notify_system_color"

    /** 断点播放 */
    const val PLAY_AT_BREAKPOINT = "play_at_breakpoint"

    /** 是否忽略媒体缓存 */
    const val IGNORE_MEDIA_STORE = "ignore_media_store"

    /** 桌面部件样式 */
    const val APP_WIDGET_SKIN = "app_widget_transparent"

    /** 是否默认开启定时器 */
    const val TIMER_DEFAULT = "timer_default"

    /** 定时器时长 */
    const val TIMER_DURATION = "timer_duration"

    /** 定时结束后等待当前歌曲播放完毕 */
    const val TIMER_EXIT_AFTER_FINISH = "timer_exit_after_finish"

    /** 封面下载源 */
    const val ALBUM_COVER_DOWNLOAD_SOURCE = "album_cover_download_source"

    /** 播放界面底部显示 */
    const val BOTTOM_OF_NOW_PLAYING_SCREEN = "bottom_of_now_playing_screen"

    /** 倍速播放 */
    const val SPEED = "speed"

    /** 回放增益开关 */
    const val REPLAY_GAIN_ENABLED = "replay_gain_enabled"

    /** 回放增益模式 */
    const val REPLAY_GAIN_MODE = "replay_gain_mode"

    /** 回放增益峰值保护 */
    const val REPLAY_GAIN_PEAK_PROTECTION = "replay_gain_peak_protection"

    /** 回放增益前置放大 */
    const val REPLAY_GAIN_PREAMP = "replay_gain_preamp"

    /** 无回放增益标签时的默认增益 */
    const val REPLAY_GAIN_MISSING_GAIN = "replay_gain_missing_gain"

    /** 移除是否同时源文件 */
    const val DELETE_SOURCE = "delete_source"

    /** 列表歌曲名是否取代为文件夹名 */
    const val SHOW_DISPLAYNAME = "show_displayname"

    /** 歌曲列表条目是否显示标签 */
    const val LIST_SHOW_TAG = "list_show_tag"

    /** 歌曲列表条目是否显示标签管理按钮 */
    const val LIST_TAG_MANAGE = "list_tag_manage"

    /** 是否显示"音乐标签编辑"入口（列表条目菜单/播放页菜单） */
    const val LIST_TAG_EDIT = "list_tag_edit"

    /** 歌曲列表底部栏的歌名是否使用文件名（而非元数据歌名） */
    const val BOTTOM_BAR_USE_FILENAME = "bottom_bar_use_filename"

    /** 播放页顶部标题是否使用文件名（而非元数据歌名） */
    const val PLAYING_TITLE_USE_FILENAME = "playing_title_use_filename"

    /** 歌曲列表排序菜单中可显示的排序规则集合（按此过滤，至少保留 1 项） */
    const val SONG_SORT_RULES = "song_sort_rules"

    /** 数据分析：各模块是否显示的开关集合 */
    const val ANALYSIS_MODULES = "analysis_modules"

    /** 数据分析：列表类模块的显示行数（旧版全局值，现仅作为未单独设置时的默认值） */
    const val ANALYSIS_LIST_ROWS = "analysis_list_rows"

    /** 数据分析：某个列表模块自己的显示行数（前缀 + 模块 key） */
    const val ANALYSIS_MODULE_ROWS_PREFIX = "analysis_module_rows_"

    /** 热力图横坐标显示的时间个数（24/12/8/6） */
    const val HEATMAP_TIME_LABELS = "heatmap_columns"

    /** 批量重命名默认模板（占位符：{title}{artist}{album}{track}{year}） */
    const val DEFAULT_RENAME_TEMPLATE = "default_rename_template"

    /** 歌曲列表底部栏的歌名下方是否显示该歌曲的标签 */
    const val BOTTOM_BAR_SHOW_TAG = "bottom_bar_show_tag"

    /** 播放页标题的歌名下方是否显示该歌曲的标签 */
    const val PLAYING_TITLE_SHOW_TAG = "playing_title_show_tag"

    /** 歌曲列表底部栏的歌名下方是否显示艺术家-专辑名 */
    const val BOTTOM_BAR_SHOW_ARTIST_ALBUM = "bottom_bar_show_artist_album"

    /** 播放页标题的歌名下方是否显示艺术家-专辑名 */
    const val PLAYING_TITLE_SHOW_ARTIST_ALBUM = "playing_title_show_artist_album"

    /** 标签过滤模式（包含与/包含或/互斥/全匹配） */
    const val TAG_FILTER_MODE = "tag_filter_mode"

    /** 互斥模式下，左侧"包含"侧是否按"与"（同时带所有选中标签）过滤，否则按"或" */
    const val TAG_FILTER_EXCLUDE_INCLUDE_AND = "tag_filter_exclude_include_and"

    /** 互斥模式下，右侧"排除"侧是否按"与"（同时带所有选中标签才排除）过滤，否则按"或" */
    const val TAG_FILTER_EXCLUDE_EXCLUDE_AND = "tag_filter_exclude_exclude_and"

    /** 标签弹窗是否使用智能排序（最近使用时间 + 使用次数），否则按创建时间固定排列 */
    const val TAG_SMART_SORT = "tag_smart_sort"

    /** 固定位置排序时是否按标签创建时间倒序（新创建的在前），否则正序 */
    const val TAG_CREATED_DESC = "tag_created_desc"

    /** 数据分析：一天的分界点小时（默认 5，即凌晨 5 点前算前一天） */
    const val STATS_DAY_START_HOUR = "stats_day_start_hour"

    /** 是否记录运行日志到文件（设置-其他-"记录日志"） */
    const val LOG_ENABLED = "log_enabled"

    /** 歌曲列表是否显示序号 */
    const val LIST_SHOW_NUMBER = "list_show_number"

    /** 歌曲列表是否显示艺术家-专辑名 */
    const val LIST_SHOW_ARTIST_ALBUM = "list_show_artist_album"

    /** 专辑列表的显示模式 */
    const val MODE_FOR_ALBUM = "mode_for_album"

    /** 艺术家列表的显示模式 */
    const val MODE_FOR_ARTIST = "mode_for_artist"

    /** 流派列表的显示模式 */
    const val MODE_FOR_GENRE = "mode_for_genre"

    /** 播放列表的显示模式 */
    const val MODE_FOR_PLAYLIST = "mode_for_playlist"

    /** 语言 */
    const val LANGUAGE = "language"

    /** 界面字体缩放 */
    const val UI_FONT_SCALE = "ui_font_scale"

    /** EQ */
    const val ENABLE_EQ = "enable_eq"

    /** Bass Boost 强度 */
    const val BASS_BOOST_STRENGTH = "bass_boost_strength"

    /** Virtualizer 强度 */
    const val VIRTUALIZER_STRENGTH = "virtualizer_strength"

    /** 音频焦点 */
    const val AUDIO_FOCUS = "audio_focus"

    /** 音频解码方式 */
    const val AUDIO_DECODER_MODE = "audio_decoder_mode"

    /** 自动播放 */
    const val AUTO_PLAY = "auto_play_headset_plug_in"

    /** 手动扫描目录 */
    const val MANUAL_SCAN_FOLDER = "manual_scan_folder"

    /** 自定义播放背景 */
    const val PLAYER_BACKGROUND = "player_background"

    /** 播放页封面切换动画 */
    const val PLAYING_COVER_ANIMATION_STYLE = "playing_cover_animation_style"

    /** 播放页封面切换动画速率 */
    const val PLAYING_COVER_ANIMATION_SPEED = "playing_cover_animation_speed"

    /** 版本号 */
    const val VERSION = "version"

    /** 淡入淡出 */
    const val CROSS_FADE = "cross_fade"

    override val latestVersion = 3
  }

  object Theme : Keys() {
    const val NAME = "aplayer-theme"
    const val PRIMARY_COLOR = "primary_color"
    const val SECONDARY_COLOR = "accent_color"
    const val DARK_THEME = "dark_theme"
    const val BLACK_THEME = "black_theme"
    const val COLOR_NAVIGATION = "color_navigation"

    override val latestVersion = 1
  }


  sealed class Keys {

    abstract val latestVersion: Int

    companion object {
      const val KEY_VERSION = "key_version"
    }
  }
}
