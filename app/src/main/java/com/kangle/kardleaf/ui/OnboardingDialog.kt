package com.kangle.kardleaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.AppIconManager
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_SCROLL_DURATION_MILLIS = 180
private const val DRAWER_GESTURE_DEMO_DURATION_MILLIS = 2600
private const val CREATE_GESTURE_DEMO_DURATION_MILLIS = 2600
private const val CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS = 2600
private const val NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS = 2600

enum class OnboardingTourTarget {
    Home,
    Drawer,
    Settings,
    History,
}

private enum class OnboardingScene {
    Intro,
    Home,
    Drawer,
    Create,
    CategoryPanel,
    NoteDetail,
    NoteProperties,
    Settings,
    History,
    TextCompare,
}

private data class OnboardingPageData(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val target: OnboardingTourTarget = OnboardingTourTarget.Home,
    val scene: OnboardingScene = OnboardingScene.Home,
)

private fun onboardingPages(): List<OnboardingPageData> = listOf(
    OnboardingPageData(
        title = "KardLeaf 使用介绍",
        description = "欢迎使用KardLeaf",
        icon = Icons.Outlined.Description,
        target = OnboardingTourTarget.Home,
        scene = OnboardingScene.Intro,
    ),
    OnboardingPageData(
        title = "侧边栏",
        description = "右滑手势滑出侧边栏",
        icon = Icons.Outlined.Folder,
        target = OnboardingTourTarget.Drawer,
        scene = OnboardingScene.Drawer,
    ),
    OnboardingPageData(
        title = "快捷新建",
        description = "上滑展开更多选项",
        icon = Icons.Outlined.Description,
        target = OnboardingTourTarget.Home,
        scene = OnboardingScene.Create,
    ),
    OnboardingPageData(
        title = "分类导航",
        description = "分类标签栏下滑展示分类面板",
        icon = Icons.Outlined.Folder,
        target = OnboardingTourTarget.Home,
        scene = OnboardingScene.CategoryPanel,
    ),
    OnboardingPageData(
        title = "笔记目录",
        description = "右滑打开当前笔记目录",
        icon = Icons.Outlined.Description,
        target = OnboardingTourTarget.Home,
        scene = OnboardingScene.NoteDetail,
    ),
    OnboardingPageData(
        title = "备注与属性",
        description = "侧滑面板查看属性并添加备注",
        icon = Icons.Outlined.Description,
        target = OnboardingTourTarget.Home,
        scene = OnboardingScene.NoteProperties,
    ),
    OnboardingPageData(
        title = "设置中心",
        description = "集中管理主题、界面、编辑和安全选项",
        icon = Icons.Outlined.Settings,
        target = OnboardingTourTarget.Settings,
        scene = OnboardingScene.Settings,
    ),
    OnboardingPageData(
        title = "历史版本",
        description = "查看历史保存记录并按需恢复",
        icon = Icons.Outlined.History,
        target = OnboardingTourTarget.History,
        scene = OnboardingScene.History,
    ),
    OnboardingPageData(
        title = "版本对比",
        description = "对比当前内容和历史版本差异",
        icon = Icons.Outlined.History,
        target = OnboardingTourTarget.History,
        scene = OnboardingScene.TextCompare,
    ),
)

/**
 * 全屏新手引导页。首次启动和侧边栏“使用介绍”入口复用同一 Composable。
 * 保留函数名是为了不改动调用方，实际展示不再是浮窗。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    onStepChanged: (OnboardingTourTarget) -> Unit = {},
    initialPage: Int = 0,
    enableBackHandler: Boolean = true,
) {
    val pages = remember { onboardingPages() }
    val safeInitialPage = remember(pages.size, initialPage) { initialPage.coerceIn(0, pages.lastIndex) }
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    val currentIndex = pagerState.currentPage.coerceIn(0, pages.lastIndex)
    val currentPage = pages[currentIndex]
    val isLastPage = currentIndex == pages.lastIndex

    fun animateToPage(targetPage: Int) {
        val safeTarget = targetPage.coerceIn(0, pages.lastIndex)
        if (safeTarget == pagerState.currentPage) return
        scope.launch {
            pagerState.animateScrollToPage(
                page = safeTarget,
                animationSpec = tween(
                    durationMillis = ONBOARDING_PAGE_SCROLL_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    fun goToPreviousPage() {
        animateToPage(currentIndex - 1)
    }

    fun goToNextPage() {
        if (isLastPage) {
            onFinish()
        } else {
            animateToPage(currentIndex + 1)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        pages.getOrNull(pagerState.settledPage)?.let { page ->
            onStepChanged(page.target)
        }
    }

    if (enableBackHandler) {
        BackHandler(onBack = onDismiss)
    }

    val compactTextMode = LocalDensity.current.fontScale >= 1.2f
    val outerHorizontalPadding = if (compactTextMode) 14.dp else 18.dp
    val outerVerticalPadding = if (compactTextMode) 12.dp else 20.dp
    val topGap = if (compactTextMode) 8.dp else 14.dp
    val bottomGap = if (compactTextMode) 8.dp else 12.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "使用介绍",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = currentPage.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("跳过")
                }
            }

            Spacer(modifier = Modifier.height(topGap))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 14.dp,
                modifier = Modifier.weight(1f),
            ) { index ->
                key(index, pagerState.settledPage == index) {
                    when (pages[index].scene) {
                        OnboardingScene.Intro -> AppIntroScene()
                        OnboardingScene.Home -> HomeTourScene()
                        OnboardingScene.Drawer -> DrawerGestureTourScene()
                        OnboardingScene.Create -> CreateButtonTourScene()
                        OnboardingScene.CategoryPanel -> CategoryPanelTourScene()
                        OnboardingScene.NoteDetail -> NoteDetailTourScene()
                        OnboardingScene.NoteProperties -> NotePropertiesTourScene()
                        OnboardingScene.Settings -> SettingsTourScene()
                        OnboardingScene.History -> HistoryTourScene()
                        OnboardingScene.TextCompare -> TextCompareTourScene()
                    }
                }
            }

            Spacer(modifier = Modifier.height(bottomGap))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentPage.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(bottomGap))
                PageIndicators(
                    count = pages.size,
                    selectedIndex = currentIndex,
                    pageOffsetFraction = pagerState.currentPageOffsetFraction,
                    onPageClick = { page -> animateToPage(page) },
                )
                Spacer(modifier = Modifier.height(bottomGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = currentIndex > 0 && !pagerState.isScrollInProgress,
                        onClick = { goToPreviousPage() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("上一步")
                    }
                    Button(
                        enabled = !pagerState.isScrollInProgress,
                        onClick = { goToNextPage() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isLastPage) "开始使用" else "下一步")
                    }
                }
            }
        }
    }
}


@Preview(name = "01 使用介绍", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewIntro() {
    OnboardingPreviewPage(initialPage = 0)
}

@Preview(name = "02 侧边栏", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewDrawer() {
    OnboardingPreviewPage(initialPage = 1)
}

@Preview(name = "03 新建按钮", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewCreate() {
    OnboardingPreviewPage(initialPage = 2)
}

@Preview(name = "04 分类导航", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewCategoryPanel() {
    OnboardingPreviewPage(initialPage = 3)
}

@Preview(name = "05 笔记详情", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewNoteDetail() {
    OnboardingPreviewPage(initialPage = 4)
}

@Preview(name = "06 备注属性", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewNoteProperties() {
    OnboardingPreviewPage(initialPage = 5)
}

@Preview(name = "07 设置中心", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewSettings() {
    OnboardingPreviewPage(initialPage = 6)
}

@Preview(name = "08 历史版本", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewHistory() {
    OnboardingPreviewPage(initialPage = 7)
}

@Preview(name = "09 文本对比", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingPreviewTextCompare() {
    OnboardingPreviewPage(initialPage = 8)
}

@Composable
private fun OnboardingPreviewPage(initialPage: Int) {
    MaterialTheme {
        OnboardingDialog(
            onDismiss = {},
            onFinish = {},
            onStepChanged = {},
            initialPage = initialPage,
            enableBackHandler = false,
        )
    }
}


@Composable
private fun PageIndicators(
    count: Int,
    selectedIndex: Int,
    pageOffsetFraction: Float,
    onPageClick: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val distance = kotlin.math.abs(index - selectedIndex - pageOffsetFraction).coerceIn(0f, 1f)
            val activeWeight = 1f - distance
            val indicatorWidth by animateDpAsState(
                targetValue = 7.dp + 18.dp * activeWeight,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                label = "OnboardingIndicatorWidth",
            )
            val indicatorHeight by animateDpAsState(
                targetValue = 7.dp + 2.dp * activeWeight,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                label = "OnboardingIndicatorHeight",
            )
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .clip(CircleShape)
                        .background(
                            if (activeWeight > 0.5f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                        .clickable { onPageClick(index) },
            )
        }
    }
}

@Composable
private fun AppIntroScene() {
    val context = LocalContext.current
    val largeTextMode = LocalDensity.current.fontScale >= 1.3f
    val sceneHorizontalPadding = if (largeTextMode) 16.dp else 24.dp
    val sceneVerticalPadding = if (largeTextMode) 14.dp else 18.dp
    val iconSize = if (largeTextMode) 104.dp else 138.dp
    val iconCorner = if (largeTextMode) 28.dp else 34.dp
    val topSpacer = if (largeTextMode) 12.dp else 18.dp
    val chipSpacer = if (largeTextMode) 12.dp else 16.dp
    val cardSpacer = if (largeTextMode) 14.dp else 20.dp

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sceneHorizontalPadding, vertical = sceneVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = AppIconManager.current(context).iconResId),
                contentDescription = "卡叶笔记图标",
                modifier =
                    Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconCorner)),
            )
            Spacer(modifier = Modifier.height(topSpacer))
            Text(
                text = "卡叶笔记",
                style = if (largeTextMode) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "KardLeaf · 作者：kangle",
                style = if (largeTextMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(chipSpacer))
            if (largeTextMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntroFeatureChip("Markdown")
                    IntroFeatureChip("目录分类")
                    IntroFeatureChip("历史版本")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntroFeatureChip("Markdown")
                    IntroFeatureChip("目录分类")
                    IntroFeatureChip("历史版本")
                }
            }
            Spacer(modifier = Modifier.height(cardSpacer))
            IntroInfoCard("定位", "卡片式 Markdown 笔记软件", stacked = largeTextMode)
            IntroInfoCard("设计重点", "本地优先、文件开放、轻量编辑", stacked = largeTextMode)
        }
    }
}

@Composable
private fun HomeTourScene() {
    TourPhoneFrame {
        HomeTopBar()
        Spacer(modifier = Modifier.height(18.dp))
        CategoryRows(expanded = true)
        Spacer(modifier = Modifier.height(10.dp))
        NoteCardPreview()
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            DemoFab("+")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TourHintCard(
            title = "分类标签栏",
            text = "向下滑展开完整分类导航",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp),
        )
        Text(
            text = "┃\n┃\n▼",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 148.dp),
        )
    }
}

@Composable
private fun DrawerGestureTourScene() {
    val transition = rememberInfiniteTransition(label = "DrawerGesture")
    val drawerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DRAWER_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 560
                1f at 980
                1f at 1760
                0f at 2220
                0f at DRAWER_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "DrawerGestureDrawerProgress",
    )
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DRAWER_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 200
                1f at 620
                1f at 760
                0f at 1050
                0f at DRAWER_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "DrawerGestureSwipeProgress",
    )
    val swipeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DRAWER_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 170
                1f at 280
                1f at 560
                0f at 700
                0f at DRAWER_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "DrawerGestureSwipeAlpha",
    )
    val edgeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DRAWER_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0.85f at 260
                0.85f at 920
                0f at 1250
                0f at DRAWER_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "DrawerGestureEdgeAlpha",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E2EC)),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp)),
        ) {
            val drawerWidth = maxWidth * 0.72f

            HomePreviewForDrawerTour()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.27f * drawerProgress)),
            )

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset(x = -drawerWidth + drawerWidth * drawerProgress),
            ) {
                MiniDrawerContent()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.56f)
                    .width(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = edgeAlpha)),
            )

            Text(
                text = "━━━━▶",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black.copy(alpha = swipeAlpha),
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 26.dp + 37.dp * swipeProgress),
            )

        }
    }
}

@Composable
private fun CreateButtonTourScene() {
    val transition = rememberInfiniteTransition(label = "CreateButtonGesture")
    val firstAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CREATE_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 700
                1f at 1020
                1f at 1900
                0f at 2240
                0f at CREATE_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CreateFirstAlpha",
    )
    val secondAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CREATE_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 760
                1f at 1120
                1f at 1900
                0f at 2240
                0f at CREATE_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CreateSecondAlpha",
    )
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CREATE_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 180
                1f at 560
                1f at 710
                0f at 1000
                0f at CREATE_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CreateSwipeProgress",
    )
    val swipeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CREATE_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 180
                1f at 280
                1f at 600
                0f at 840
                0f at CREATE_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CreateSwipeAlpha",
    )

    HtmlPreviewFrame {
        HomePreviewForDrawerTour(showFab = false)

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
        ) {
            DemoFabIcon(Icons.Outlined.Description, modifier = Modifier.offset(y = (-128).dp).alpha(secondAlpha))
            DemoFabIcon(Icons.Outlined.CreateNewFolder, modifier = Modifier.offset(y = (-66).dp).alpha(firstAlpha))
            DemoFab("+")
        }

        Text(
            text = "▲\n┃\n┃",
            color = Color.Black.copy(alpha = swipeAlpha),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 58.dp, bottom = 92.dp + 22.dp * swipeProgress),
        )

    }
}


@Composable
private fun CategoryPanelTourScene() {
    val transition = rememberInfiniteTransition(label = "CategoryPanelGesture")
    val panelProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 680
                1f at 1120
                1f at 1900
                0f at 2260
                0f at CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CategoryPanelProgress",
    )
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 180
                1f at 560
                1f at 720
                0f at 1020
                0f at CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CategorySwipeProgress",
    )
    val swipeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 170
                1f at 280
                1f at 620
                0f at 860
                0f at CATEGORY_PANEL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "CategorySwipeAlpha",
    )

    HtmlPreviewFrame {
        HomePreviewForDrawerTour(showFab = true)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f * panelProgress)))
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelHeight = maxHeight * 0.56f
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .align(Alignment.TopCenter)
                    .offset(y = -panelHeight + panelHeight * panelProgress),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
                        )
                    }
                    DemoFolderNavGrid(listOf("全部笔记"), selected = "全部笔记")
                    DemoFolderNavSection(
                        title = "01_工作办公",
                        chips = listOf("全部", "会议纪要", "任务看板", "项目资料", "周报月报", "客户资料"),
                        selected = "全部",
                    )
                    DemoFolderNavSection(
                        title = "02_学习笔记",
                        chips = listOf("全部", "Android", "Markdown", "设计参考", "读书记录", "灵感"),
                        selected = "",
                    )
                }
            }
        }
        Text(
            text = "┃\n┃\n▼",
            color = Color.Black.copy(alpha = swipeAlpha),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 102.dp + 20.dp * swipeProgress),
        )
    }
}

@Composable
private fun DemoFolderNavSection(
    title: String,
    chips: List<String>,
    selected: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        DemoFolderNavGrid(chips = chips, selected = selected)
    }
}

@Composable
private fun DemoFolderNavGrid(
    chips: List<String>,
    selected: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems.forEach { chip ->
                    DemoFolderNavChip(
                        text = chip,
                        selected = chip == selected,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DemoFolderNavChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .height(34.dp)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoteDetailTourScene() {
    val transition = rememberInfiniteTransition(label = "NoteDetailGesture")
    val panelProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 680
                1f at 1120
                1f at 1880
                0f at 2260
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NotePanelProgress",
    )
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 200
                1f at 620
                1f at 760
                0f at 1050
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NoteSwipeProgress",
    )
    val swipeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 170
                1f at 280
                1f at 560
                0f at 700
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NoteSwipeAlpha",
    )

    HtmlPreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(top = 54.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF456078))
                Text("⋮", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF456078))
            }
            Text("适合展示的亮点", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
            Spacer(modifier = Modifier.height(14.dp))
            Text("# Markdown 示例", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF172033))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "- 支持 **加粗** 和 `行内代码`\n- 支持待办清单\n> 也可以记录引用内容",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF172033),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF5F8FD)) {
                Text(
                    "```kotlin\nval note = \"KardLeaf\"\n```",
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFF5B6F84),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f * panelProgress)))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = maxWidth * 0.82f
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .offset(x = -panelWidth + panelWidth * panelProgress),
            ) {
                Column(modifier = Modifier.padding(top = 54.dp, start = 28.dp, end = 20.dp)) {
                    Text("目录结构", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlineDemoRow("Markdown 示例")
                    OutlineDemoRow("待办清单")
                    OutlineDemoRow("引用内容")
                    OutlineDemoRow("代码块")
                }
            }
        }

        Text(
            text = "━━━━▶",
            style = MaterialTheme.typography.titleLarge,
            color = Color.Black.copy(alpha = swipeAlpha),
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 26.dp + 37.dp * swipeProgress),
        )

    }
}


@Composable
private fun NotePropertiesTourScene() {
    val transition = rememberInfiniteTransition(label = "NotePropertiesGesture")
    val panelProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 680
                1f at 1120
                1f at 1880
                0f at 2260
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NotePropertiesPanelProgress",
    )
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 200
                1f at 620
                1f at 760
                0f at 1050
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NotePropertiesSwipeProgress",
    )
    val swipeAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
                0f at 0
                0f at 170
                1f at 280
                1f at 560
                0f at 700
                0f at NOTE_DETAIL_GESTURE_DEMO_DURATION_MILLIS
            },
        ),
        label = "NotePropertiesSwipeAlpha",
    )

    HtmlPreviewFrame {
        NoteDetailContentPreview()
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f * panelProgress)))
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = maxWidth * 0.82f
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .align(Alignment.CenterEnd)
                    .offset(x = panelWidth - panelWidth * panelProgress),
            ) {
                Column(
                    modifier = Modifier.padding(top = 54.dp, start = 16.dp, end = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "备注",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                    DemoPropertiesCard()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "新增一条备注",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已添加 2 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "添加",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    DemoRemarkCard("整理成最终说明后再同步到 README")
                    DemoRemarkCard("这里记录只和当前笔记绑定，不写进正文")
                }
            }
        }
        Text(
            text = "◀━━━━",
            style = MaterialTheme.typography.titleLarge,
            color = Color.Black.copy(alpha = swipeAlpha),
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-26).dp - 37.dp * swipeProgress),
        )
    }
}

@Composable
private fun NoteDetailContentPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 54.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF456078))
            Text("⋮", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF456078))
        }
        Text("适合展示的亮点", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
        Spacer(modifier = Modifier.height(14.dp))
        Text("# Markdown 示例", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF172033))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "- 支持 **加粗** 和 `行内代码`\n- 支持待办清单\n> 也可以记录引用内容",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF172033),
        )
    }
}

@Composable
private fun DemoPropertiesCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "属性",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DemoPropertyRow("标题", "适合展示的亮点")
        DemoPropertyRow("标签", "KardLeaf、编辑器")
        DemoPropertyRow("位置", "01_工作办公/项目资料")
    }
}

@Composable
private fun DemoPropertyRow(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DemoRemarkCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "2026-06-23 17:48",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsTourScene() {
    HtmlPreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
                Spacer(modifier = Modifier.width(14.dp))
                Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
            }
            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle("常规")
            SettingDemoRow("笔记库", "/storage/emulated/0/#tmp1/KardLeaf")
            SettingDemoRow("主题设置", "强调色和背景色")
            SettingDemoRow("应用界面", "布局、排序和启动分类")
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle("备注内容")
            SettingDemoRow("字符按钮位置", "调整工具按钮顺序")
            SettingDemoRow("默认编辑器模式", "查看模式")
            SettingDemoRow("双击进入编辑间隔", "当前 180ms")
            SettingDemoRow("笔记详情侧滑面板", "滑块开关")
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle("数据与安全")
            SettingDemoRow("数据备份", "导入或导出用户数据 JSON")
            SettingDemoRow("自动备份", "定时备份到指定目录")
            SettingDemoRow("安全", "应用锁、隐私和指纹")
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryTourScene() {
    HtmlPreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OnboardingHistoryColors.PageBackground),
        ) {
            DemoHistoryTopBar(
                title = "历史版本",
                subtitle = "当前笔记 · 3 个版本",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp),
            ) {
                DemoHistorySearchRow()
                DemoCompareSourceStrip()
                DemoSelectedVersionPanel()
                DemoVersionListHeader(count = 3)
                DemoHistoryVersionCard(
                    title = "当前版本",
                    meta = "正在使用 · 约 207 字",
                    content = "# Markdown 示例  支持加粗、图片、目录和备注面板",
                    badge = "当前",
                    selected = false,
                    current = true,
                    showDiff = false,
                )
                DemoHistoryVersionCard(
                    title = "版本 2",
                    meta = "2026-06-23 17:21 · 历史保存 · 约 198 字",
                    content = "# Markdown 示例  支持加粗、图片和目录",
                    badge = "可对比",
                    selected = true,
                    current = false,
                    showDiff = true,
                )
            }
            DemoHistoryBottomActions(compare = true)
        }
    }
}

@Composable
private fun TextCompareTourScene() {
    HtmlPreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OnboardingHistoryColors.PageBackground),
        ) {
            DemoHistoryTopBar(
                title = "版本对比",
                subtitle = "版本 2 → 当前版本",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp),
            ) {
                DemoCompareSourceStrip()
                DemoCompareModeSegment()
                DemoHistorySectionTitle("正文变化", "未变化内容已折叠")
                DemoDiffGroupCard(
                    title = "正文 · 第 2 行",
                    subtitle = "这就是上面统计的“1 处改写”",
                    type = OnboardingDiffKind.CHANGE,
                    oldText = "支持加粗、图片和目录",
                    newText = "支持加粗、图片、目录和备注面板",
                )
                DemoDiffGroupCard(
                    title = "正文 · 新增内容",
                    subtitle = "当前版本新增了 1 行",
                    type = OnboardingDiffKind.ADD,
                    oldText = "",
                    newText = "属性面板可以显示标签、路径和创建时间",
                )
                DemoFoldLine("未变化正文默认不占空间，只在需要时展开查看")
            }
            DemoHistoryBottomActions(compare = false)
        }
    }
}

private enum class OnboardingDiffKind {
    ADD,
    REMOVE,
    CHANGE,
}

private object OnboardingHistoryColors {
    val PageBackground = Color(0xFFF8FAFC)
    val TopBarBackground = Color(0xF0F8FAFC)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextTertiary = Color(0xFF475569)
    val TextMuted = Color(0xFF94A3B8)
    val Border = Color(0xFFE2E8F0)
    val SoftBorder = Color(0xFFEEF2F7)
    val IconButtonBackground = Color(0xFFF1F5F9)
    val DarkButton = Color(0xFF111827)
    val NeutralPill = Color(0xFFF1F5F9)
    val DisabledButton = Color(0xFFCBD5E1)
    val GreenBackground = Color(0xFFECFDF5)
    val GreenBorder = Color(0xFFBBF7D0)
    val GreenText = Color(0xFF047857)
    val RedBackground = Color(0xFFFFF1F2)
    val RedBorder = Color(0xFFFECDD3)
    val RedText = Color(0xFFBE123C)
    val YellowBackground = Color(0xFFFFFBEB)
    val YellowBorder = Color(0xFFFDE68A)
    val YellowText = Color(0xFF92400E)
}

@Composable
private fun DemoHistoryTopBar(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(OnboardingHistoryColors.TopBarBackground)
            .border(1.dp, OnboardingHistoryColors.Border)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(OnboardingHistoryColors.IconButtonBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                color = OnboardingHistoryColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OnboardingHistoryColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = OnboardingHistoryColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(OnboardingHistoryColors.DarkButton)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "完成",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun DemoHistorySearchRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "搜索版本内容、保存时间或备注",
                color = OnboardingHistoryColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = OnboardingHistoryColors.TextTertiary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DemoCompareSourceStrip() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "版本 2",
                    color = OnboardingHistoryColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "→",
                    color = OnboardingHistoryColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "当前版本",
                    color = OnboardingHistoryColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DemoSourceChip(text = "+1", type = OnboardingDiffKind.ADD)
                DemoSourceChip(text = "-0", type = OnboardingDiffKind.REMOVE)
                DemoSourceChip(text = "1 改写", type = OnboardingDiffKind.CHANGE)
            }
        }
        Text(
            text = "17:21 保存 · 约 198 字 → 正在使用 · 约 207 字",
            color = OnboardingHistoryColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DemoSourceChip(
    text: String,
    type: OnboardingDiffKind,
) {
    val background = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenBackground
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedBackground
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowBackground
    }
    val content = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenText
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedText
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowText
    }
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemoSelectedVersionPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前选中：版本 2",
                    color = OnboardingHistoryColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "2026-06-23 17:21 · 历史保存 · 约 198 字",
                    color = OnboardingHistoryColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DemoVersionBadge(text = "已选中", current = false)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(OnboardingHistoryColors.PageBackground)
                .border(1.dp, OnboardingHistoryColors.SoftBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Text(
                text = "# Markdown 示例\n支持加粗、图片和目录\n记录版本用于后续恢复",
                color = OnboardingHistoryColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DemoVersionListHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, top = 4.dp, end = 3.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "版本列表",
            color = OnboardingHistoryColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "$count 个",
            color = OnboardingHistoryColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun DemoHistoryVersionCard(
    title: String,
    meta: String,
    content: String,
    badge: String,
    selected: Boolean,
    current: Boolean,
    showDiff: Boolean,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else OnboardingHistoryColors.Border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OnboardingHistoryColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    color = OnboardingHistoryColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DemoVersionBadge(text = badge, current = current)
        }
        Text(
            text = content,
            color = OnboardingHistoryColors.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDiff) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DemoSourceChip(text = "+1", type = OnboardingDiffKind.ADD)
                DemoSourceChip(text = "-0", type = OnboardingDiffKind.REMOVE)
                DemoSourceChip(text = "1 改写", type = OnboardingDiffKind.CHANGE)
            }
        }
    }
}

@Composable
private fun DemoVersionBadge(text: String, current: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else OnboardingHistoryColors.NeutralPill)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (current) MaterialTheme.colorScheme.primary else OnboardingHistoryColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemoHistoryBottomActions(compare: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnboardingHistoryColors.TopBarBackground)
            .border(1.dp, OnboardingHistoryColors.Border)
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DemoHistoryActionButton(
            text = if (compare) "查看对比" else "返回列表",
            background = if (compare) OnboardingHistoryColors.DarkButton else OnboardingHistoryColors.IconButtonBackground,
            contentColor = if (compare) Color.White else OnboardingHistoryColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        DemoHistoryActionButton(
            text = "恢复版本 2",
            background = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DemoHistoryActionButton(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DemoCompareModeSegment() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(15.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("只看改动", "完整文本", "并排对比").forEachIndexed { index, mode ->
            val active = index == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) OnboardingHistoryColors.DarkButton else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode,
                    color = if (active) Color.White else OnboardingHistoryColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DemoHistorySectionTitle(
    title: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, top = 4.dp, end = 3.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = OnboardingHistoryColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = trailing,
            color = OnboardingHistoryColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DemoDiffGroupCard(
    title: String,
    subtitle: String,
    type: OnboardingDiffKind,
    oldText: String,
    newText: String,
) {
    val background = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenBackground
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedBackground
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowBackground
    }
    val border = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenBorder
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedBorder
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowBorder
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OnboardingHistoryColors.PageBackground)
                .border(1.dp, OnboardingHistoryColors.SoftBorder)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OnboardingHistoryColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = OnboardingHistoryColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DemoHistoryDiffPill(type)
        }
        if (type == OnboardingDiffKind.CHANGE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .border(1.dp, border, RoundedCornerShape(14.dp)),
            ) {
                DemoRewriteRow(label = "旧", text = oldText, old = true)
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(border.copy(alpha = 0.9f)))
                DemoRewriteRow(label = "新", text = newText, old = false)
            }
        } else {
            DemoDiffLineRow(type = type, lineNumber = "3", text = newText.ifBlank { oldText })
        }
    }
}

@Composable
private fun DemoHistoryDiffPill(type: OnboardingDiffKind) {
    val background = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenBackground
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedBackground
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowBackground
    }
    val content = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenText
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedText
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowText
    }
    val text = when (type) {
        OnboardingDiffKind.ADD -> "新增"
        OnboardingDiffKind.REMOVE -> "删除"
        OnboardingDiffKind.CHANGE -> "改写"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemoRewriteRow(
    label: String,
    text: String,
    old: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = OnboardingHistoryColors.YellowText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.size(width = 34.dp, height = 18.dp),
        )
        Text(
            text = text.ifBlank { "空行" },
            color = if (old) OnboardingHistoryColors.RedText else OnboardingHistoryColors.GreenText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (old) FontWeight.Normal else FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DemoDiffLineRow(
    type: OnboardingDiffKind,
    lineNumber: String,
    text: String,
) {
    val background = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenBackground
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedBackground
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowBackground
    }
    val content = when (type) {
        OnboardingDiffKind.ADD -> OnboardingHistoryColors.GreenText
        OnboardingDiffKind.REMOVE -> OnboardingHistoryColors.RedText
        OnboardingDiffKind.CHANGE -> OnboardingHistoryColors.YellowText
    }
    val mark = when (type) {
        OnboardingDiffKind.ADD -> "+"
        OnboardingDiffKind.REMOVE -> "−"
        OnboardingDiffKind.CHANGE -> "~"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = lineNumber,
            color = OnboardingHistoryColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.size(width = 30.dp, height = 20.dp),
        )
        Text(
            text = mark,
            color = content,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 18.dp, height = 20.dp),
        )
        Text(
            text = text.ifBlank { "空行" },
            color = content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DemoFoldLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(OnboardingHistoryColors.PageBackground)
            .border(1.dp, OnboardingHistoryColors.Border, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = OnboardingHistoryColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HtmlPreviewFrame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E2EC)),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp)),
            content = content,
        )
    }
}

@Composable
private fun CaptionPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.94f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TourPhoneFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), content = content)
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Menu,
            contentDescription = null,
            tint = Color(0xFF456078),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Color(0xFF456078),
            )
            Spacer(modifier = Modifier.width(18.dp))
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                tint = Color(0xFF456078),
            )
        }
    }
}

@Composable
private fun CategoryRows(expanded: Boolean) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPill("01_工作办公", selected = true)
            CategoryPill("02_学习笔记")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPill("01_会议纪要")
            CategoryPill("02_任务看板")
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("03_项目资料")
                CategoryPill("04_周报月报")
            }
        }
    }
}

@Composable
private fun CategoryPill(text: String, selected: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NoteCardPreview() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("00_分类说明", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text("01工作办公 - 分类说明", style = MaterialTheme.typography.bodyMedium)
            Text(
                "这个目录用于演示 KardLeaf 的多目录浏览能力。顶部分类标签栏切换目录，首页卡片展示笔记摘要。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DemoFab(
    text: String,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFFDDEBFF),
        tonalElevation = 4.dp,
        modifier = modifier.size(if (small) 48.dp else 56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "+") {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF123A58),
                )
            } else {
                Text(
                    text = text,
                    style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF123A58),
                )
            }
        }
    }
}

@Composable
private fun DemoFabIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFFDDEBFF),
        tonalElevation = 4.dp,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF123A58),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HomePreviewForDrawerTour(showFab: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(18.dp),
    ) {
        HomeTopBar()
        Spacer(modifier = Modifier.height(18.dp))
        CategoryRows(expanded = false)
        Spacer(modifier = Modifier.height(10.dp))
        NoteCardPreview()
        Spacer(modifier = Modifier.weight(1f))
        if (showFab) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                DemoFab("+")
            }
        }
    }
}

@Composable
private fun MiniDrawerContent() {
    Column(modifier = Modifier.padding(top = 38.dp, start = 24.dp, end = 12.dp)) {
        Text(
            text = "KardLeaf",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF111827),
        )
        Text(
            text = "卡叶笔记 · kangle",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF526B82),
        )
        Spacer(modifier = Modifier.height(18.dp))
        DrawerDemoItem(Icons.Outlined.Description, "全部笔记")
        DrawerDemoItem(Icons.Outlined.History, "最近修改")
        DrawerDemoItem(Icons.Outlined.BookmarkBorder, "收藏")
        DrawerDemoItem(Icons.Outlined.Drafts, "草稿")
        DrawerDemoItem(Icons.Outlined.Folder, "文件")
        DrawerDemoItem(Icons.Outlined.CalendarToday, "日期")
        DrawerDemoItem(Icons.Outlined.Image, "图片")
        DrawerDemoItem(Icons.Outlined.Archive, "归档")
        DrawerDemoItem(Icons.Outlined.Delete, "废弃")
        DrawerDemoItem(Icons.Outlined.Lock, "隐私")
        DrawerDemoItem(Icons.AutoMirrored.Outlined.MenuBook, "介绍")
        DrawerDemoItem(Icons.Outlined.Settings, "设置")
    }
}

@Composable
private fun TourHintCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF526B82),
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun IntroFeatureChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun IntroInfoCard(title: String, text: String, stacked: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        if (stacked) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun OutlineDemoRow(title: String) {
    Text(
        text = "• $title",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 5.dp),
    )
}

@Composable
private fun DiffDemoLine(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(9.dp))
    }
}

@Composable
private fun DrawerDemoItem(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(34.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF526B82),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF526B82),
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingDemoRow(title: String, subtitle: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
                .padding(13.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryVersionCard(title: String, subtitle: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "# 01_工作办公 - 分类说明  这个目录用于演示 KardLeaf 的多目录浏览能力...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


