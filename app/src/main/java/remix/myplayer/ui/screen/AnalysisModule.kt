package remix.myplayer.ui.screen

import androidx.annotation.StringRes
import remix.myplayer.R

/** 数据分析报表中各模块的标识与展示信息 */
enum class AnalysisModule(
  val key: String,
  @StringRes val labelRes: Int,
  val hasList: Boolean
) {
  APP_USAGE("app_usage", R.string.stats_app_usage, false),
  PLAY_COUNT("play_count", R.string.stats_play_count, true),
  PLAY_DURATION("play_duration", R.string.stats_play_duration, true),
  SKIPPED("skipped", R.string.stats_skipped, true),
  DAILY_DURATION("daily_duration", R.string.stats_daily_duration, true),
  TREND("trend", R.string.stats_trend, false),
  DAILY_ACTIVE("daily_active", R.string.stats_daily_active, true),
  TAG_SONG_COUNT("tag_song_count", R.string.stats_tag_song_count, true),
  FAVORITE_TAGS("favorite_tags", R.string.stats_favorite_tags, true),
  SEARCH_RANKING("search_ranking", R.string.stats_search_ranking, true),
  HEATMAP("heatmap", R.string.stats_heatmap, false),
  HEATMAP_24H("heatmap_24h", R.string.stats_heatmap_24h, false);
}
