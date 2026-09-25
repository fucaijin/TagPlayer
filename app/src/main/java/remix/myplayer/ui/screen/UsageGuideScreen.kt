package remix.myplayer.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme

/**
 * 使用说明页（设置 -> 使用说明）：展示关键操作指引。
 * 当前包含「定位到播放歌曲」的说明。
 */
@Composable
fun UsageGuideScreen() {
  val theme = LocalTheme.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    UsageSection(
      title = stringResource(R.string.usage_locate_playing_title),
      body = stringResource(R.string.usage_locate_playing_desc),
    )

    UsageSection(
      title = stringResource(R.string.usage_tag_filter_title),
      body = stringResource(R.string.usage_tag_filter_desc),
    )

    // 后续可在此追加更多操作说明（如标签管理、数据分析等）
  }
}

@Composable
private fun UsageSection(title: String, body: String) {
  val theme = LocalTheme.current
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = theme.dialogBackground),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = theme.textPrimary,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = theme.textSecondary,
      )
    }
  }
}
