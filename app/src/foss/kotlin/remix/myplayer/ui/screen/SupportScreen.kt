package remix.myplayer.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.QrCodeDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.AlipayUtil
import remix.myplayer.util.Util
import remix.myplayer.util.ext.clickWithRipple

/** 一个捐赠渠道（微信 / 支付宝 / Paypal） */
private data class DonateChannel(
  val icon: Int,
  val titleRes: Int,
  val onClick: (Activity) -> Unit,
)

/** 一个捐赠版块（支持 APlayer / 支持 TagPlayer） */
private data class DonateSection(
  val titleRes: Int,
  val channels: List<DonateChannel>,
)

@Composable
fun SupportScreen() {
  val theme = LocalTheme.current
  val activity = LocalActivity.current ?: return
  val scope = rememberCoroutineScope()

  // 各二维码弹窗状态
  val aplayerWechatQr = rememberDialogState()
  val tagplayerWechatQr = rememberDialogState()
  val tagplayerAlipayQr = rememberDialogState()

  // 支付宝（原 APlayer）跳转账号弹窗（保留原逻辑）
  val alipayJumpDialog = rememberDialogState()
  NormalDialog(
    alipayJumpDialog,
    titleRes = R.string.support_develop,
    positiveRes = R.string.jump_alipay_account,
    negativeRes = R.string.cancel,
    contentRes = R.string.donate_tip,
    onPositive = { AlipayUtil.startAlipayClient(activity) }
  )

  // 二维码弹窗（点击“保存到本地”写入系统相册）
  QrCodeDialog(
    dialogState = aplayerWechatQr,
    title = stringResource(R.string.wechat),
    qrResId = R.drawable.icon_wechat_qrcode,
    saveFileName = "wechat_qrCode.png"
  )
  QrCodeDialog(
    dialogState = tagplayerWechatQr,
    title = stringResource(R.string.wechat),
    qrResId = R.drawable.icon_wechat_qrcode_tagplayer,
    saveFileName = "wechatpay_qrCode.png"
  )
  QrCodeDialog(
    dialogState = tagplayerAlipayQr,
    title = stringResource(R.string.alipay),
    qrResId = R.drawable.icon_alipay_qrcode_tagplayer,
    saveFileName = "alipay_qrCode.png",
    actionText = stringResource(R.string.open_alipay),
    onAction = { AlipayUtil.startAlipayClient(activity, "fkx15256clqhjppcwrxrt3c", "fucaijin@qq.com") }
  )

  val sections = listOf(
    DonateSection(
      titleRes = R.string.support_aplayer,
      channels = listOf(
        DonateChannel(R.drawable.icon_wechat_donate, R.string.wechat) {
          aplayerWechatQr.show()
        },
        DonateChannel(R.drawable.icon_alipay_donate, R.string.alipay) {
          alipayJumpDialog.show()
        },
        DonateChannel(R.drawable.icon_paypal_donate, R.string.paypal) {
          val intent = Intent("android.intent.action.VIEW")
          intent.data = "https://www.paypal.me/rRemix".toUri()
          Util.startActivitySafely(it, intent)
        },
      )
    ),
    DonateSection(
      titleRes = R.string.support_tagplayer,
      channels = listOf(
        DonateChannel(R.drawable.icon_wechat_donate, R.string.wechat) {
          tagplayerWechatQr.show()
        },
        DonateChannel(R.drawable.icon_alipay_donate, R.string.alipay) {
          tagplayerAlipayQr.show()
        },
        DonateChannel(R.drawable.icon_paypal_donate, R.string.paypal) {
          val intent = Intent("android.intent.action.VIEW")
          intent.data = "https://www.paypal.me/fucaijin".toUri()
          Util.startActivitySafely(it, intent)
        },
      )
    ),
  )

  Scaffold(
    topBar = {
      CommonAppBar(
        title = stringResource(R.string.support_develop),
        actions = emptyList()
      )
    },
    containerColor = theme.mainBackground
  ) { contentPadding ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier.padding(contentPadding),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 应用说明
      item(span = { GridItemSpan(maxLineSpan) }) {
        TextSecondary(
          text = stringResource(R.string.support_page_desc),
          fontSize = 13.sp,
          maxLine = Int.MAX_VALUE,
          overflow = TextOverflow.Visible,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
        )
      }

      sections.forEach { section ->
        item(span = { GridItemSpan(maxLineSpan) }) {
          TextPrimary(
            text = stringResource(section.titleRes),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
          )
        }
        items(section.channels) { channel ->
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clickWithRipple(false) { channel.onClick(activity) },
            color = LocalTheme.current.mainBackground,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp
          ) {
            Column(
              modifier = Modifier.padding(16.dp).widthIn(min = 0.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Image(
                painter = painterResource(channel.icon),
                contentDescription = "Support${stringResource(channel.titleRes)}"
              )
              TextSecondary(
                text = stringResource(channel.titleRes)
              )
            }
          }
        }
      }

      item(span = { GridItemSpan(maxLineSpan) }) {
        TextSecondary(
          text = stringResource(R.string.support_donate_note),
          fontSize = 12.sp,
          maxLine = Int.MAX_VALUE,
          overflow = TextOverflow.Visible,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
        )
      }
    }
  }
}
