package remix.myplayer.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.data.model.misc.Library
import remix.myplayer.ui.dialog.CreatePlayListDialog
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.app.BottomBar
import remix.myplayer.ui.widget.app.Drawer
import remix.myplayer.ui.widget.app.FAButton
import remix.myplayer.ui.widget.app.MultiSelectBar
import remix.myplayer.ui.widget.app.ViewPager
import remix.myplayer.util.ext.clickableWithoutRipple
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.settingViewModel
import remix.myplayer.viewmodel.smbViewModel
import remix.myplayer.viewmodel.webDavViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen() {
  val mainVM = mainViewModel
  val libraryVM = libraryViewModel

  val multiSelectState by mainVM.multiSelectState.collectAsStateWithLifecycle()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()

  BackHandler(enabled = drawerState.isOpen || multiSelectState.isShowing()) {
    if (drawerState.isOpen) {
      scope.launch {
        drawerState.close()
      }
    } else if (multiSelectState.isShowing()) {
      mainVM.closeMultiSelect()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = { Drawer(drawerState) }) {

    val libraries by settingViewModel.enabledLibraries.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { libraries.size }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
      flingAnimationSpec = null,
      snapAnimationSpec = null
    )

    val showMultiSelect by remember {
      derivedStateOf {
        multiSelectState.isShowInLibrary()
      }
    }

    Scaffold(
      Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
      containerColor = LocalTheme.current.libraryBackground,
      topBar = {
        AnimatedContent(
          targetState = showMultiSelect,
          transitionSpec = {
            if (targetState) {
              slideInVertically() togetherWith slideOutVertically { height -> height / 2 }
            } else {
              slideInVertically { height -> height } togetherWith slideOutVertically()
            }
          }
        ) { isMultiSelect ->
          if (!isMultiSelect) {
            HomeAppBar(scrollBehavior, drawerState)
          } else {
            MultiSelectBar(
              state = multiSelectState,
              scrollBehavior = scrollBehavior,
            )
          }
        }
      },
      floatingActionButton = {
        val selectLibrary by remember(libraries) {
          derivedStateOf {
            libraries.getOrElse(pagerState.currentPage) { libraries.first() }
          }
        }

        CreatePlayListDialog()

        var showAddRemoteMenu by remember { mutableStateOf(false) }

        val webDavVM = webDavViewModel
        val smbVM = smbViewModel
        Column {
          if (showAddRemoteMenu) {
            DropdownMenu(
              expanded = true,
              containerColor = LocalTheme.current.dialogBackground,
              onDismissRequest = { showAddRemoteMenu = false }
            ) {
              DropdownMenuItem(
                text = {
                  Text(
                    stringResource(R.string.webdav),
                    color = LocalTheme.current.textPrimary
                  )
                },
                onClick = {
                  showAddRemoteMenu = false
                  webDavVM.showAddWebDavDialog()
                }
              )
              if (smbVM.supportSmb) {
                SmbDropDownMenu(smbVM) {
                  showAddRemoteMenu = false
                  smbVM.showAddSmbDialog()
                }
              }
            }
          }

          FAButton(
            selectLibrary.tag == Library.TAG_PLAYLIST || selectLibrary.tag == Library.TAG_REMOTE
          ) {
            if (mainVM.multiSelectState.value.isShowing()) {
              return@FAButton
            }

            if (selectLibrary.tag == Library.TAG_PLAYLIST) {
              libraryVM.showCreatePlaylistDialog()
            } else if (selectLibrary.tag == Library.TAG_REMOTE) {
              showAddRemoteMenu = true
            }
          }
        }

      })
    { contentPadding ->
      HomeContent(contentPadding, pagerState, libraries)
    }
  }
}

@Composable
private fun HomeContent(
  contentPadding: PaddingValues,
  pagerState: PagerState,
  libraries: List<Library>,
) {
  val scope = rememberCoroutineScope()
  val scrollToCurrentEvent = remember { MutableSharedFlow<Unit>() }

  Column(modifier = Modifier.padding(contentPadding)) {
    // 顶部 Tab：数量少时平分整行宽度铺满，数量多放不下时才横向滚动
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .background(LocalTheme.current.primary)
    ) {
      val tabCount = libraries.size.coerceAtLeast(1)
      val tabWidth = (maxWidth / tabCount).coerceAtLeast(MinTabWidth)
      val viewportWidth = constraints.maxWidth.toFloat()
      val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
      val tabScrollState = rememberScrollState()

      // 选中项滚入可视区域（总宽未超出时不产生滚动）
      LaunchedEffect(pagerState.currentPage, tabWidthPx, viewportWidth) {
        val maxScroll = (tabWidthPx * tabCount - viewportWidth).coerceAtLeast(0f)
        val target = tabWidthPx * pagerState.currentPage - (viewportWidth - tabWidthPx) / 2f
        tabScrollState.animateScrollTo(target.coerceIn(0f, maxScroll).roundToInt())
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(tabScrollState)
      ) {
        libraries.forEachIndexed { index, library ->
          val theme = LocalTheme.current
          var lastClickTime by remember { mutableLongStateOf(0L) }

          Box(
            modifier = Modifier
              .width(tabWidth)
              .height(TabRowHeight)
              .clickableWithoutRipple(remember { MutableInteractionSource() }) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                  if (library.tag == Library.TAG_SONG) {
                    scope.launch { scrollToCurrentEvent.emit(Unit) }
                  }
                  return@clickableWithoutRipple
                }
                lastClickTime = currentTime
                scope.launch { pagerState.animateScrollToPage(index) }
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              stringResource(library.stringRes),
              maxLines = 1,
              color = if (pagerState.currentPage == index) {
                theme.primaryReverse
              } else {
                colorResource(
                  if (theme.isPrimaryCloseToWhite) R.color.dark_normal_tab_text_color
                  else R.color.light_normal_tab_text_color
                )
              }
            )
            if (pagerState.currentPage == index) {
              Box(
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .height(3.dp)
                  .background(theme.primaryReverse)
              )
            }
          }
        }
      }
    }

    ViewPager(
      modifier = Modifier.weight(1f),
      libraries = libraries,
      pagerState = pagerState,
      scrollToCurrentEvent = scrollToCurrentEvent
    )

    BottomBar()
  }
}

// 修改tab最小宽度
fun hackTabMinWidth() {
  try {
    Class
      .forName("androidx.compose.material3.TabRowKt")
      .getDeclaredField("ScrollableTabRowMinimumTabWidth")
      .apply {
        isAccessible = true
      }.set(null, 72f)
  } catch (e: Exception) {
    e.printStackTrace()
  }
}

/** 顶部 Tab 的最小宽度，所有 Tab 平分整行超不过该值时才横向滚动 */
private val MinTabWidth = 72.dp

/** 顶部 Tab 行高 */
private val TabRowHeight = 48.dp

