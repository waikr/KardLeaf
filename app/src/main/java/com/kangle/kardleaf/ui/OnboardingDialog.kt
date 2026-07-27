package com.kangle.kardleaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafHomeCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_SCROLL_DURATION_MILLIS = 180

private val DemoBlue = Color(0xFF426A9E)
private val DemoBlueSoft = Color(0xFFD9E7F8)
private val DemoBackground = Color(0xFFF7F8FA)
private val DemoText = Color(0xFF20242A)
private val DemoMuted = Color(0xFF6B7280)
private val DemoBorder = Color(0xFFE1E5EA)

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
    Tasks,
    Reader,
    Editor,
    NoteInfo,
    History,
    Settings,
}

private data class OnboardingPageData(
    val title: String,
    val description: String,
    val target: OnboardingTourTarget,
    val scene: OnboardingScene,
)

private fun onboardingPages(): List<OnboardingPageData> =
    listOf(
        OnboardingPageData(
            title = "KardLeaf 使用介绍",
            description = "欢迎使用 KardLeaf",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.Intro,
        ),
        OnboardingPageData(
            title = "首页与新建",
            description = "默认使用 5 项底部工具栏，中间的 + 是新建笔记",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.Home,
        ),
        OnboardingPageData(
            title = "侧边栏与分类",
            description = "侧边栏汇总主要入口，首页标题可展开分类导航",
            target = OnboardingTourTarget.Drawer,
            scene = OnboardingScene.Drawer,
        ),
        OnboardingPageData(
            title = "任务清单",
            description = "底部工具栏的清单按钮进入任务页，同时汇总 Markdown 待办",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.Tasks,
        ),
        OnboardingPageData(
            title = "笔记阅读",
            description = "默认先进入查看模式，顶部提供搜索、编辑、大纲与属性备注",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.Reader,
        ),
        OnboardingPageData(
            title = "Markdown 编辑",
            description = "默认使用原生 Beta 内核，编辑字符工具栏始终显示在底部",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.Editor,
        ),
        OnboardingPageData(
            title = "属性与备注",
            description = "顶部的大纲和备注按钮分别打开笔记两侧面板",
            target = OnboardingTourTarget.Home,
            scene = OnboardingScene.NoteInfo,
        ),
        OnboardingPageData(
            title = "历史版本",
            description = "默认保留最新 20 个版本，可预览、对比或恢复",
            target = OnboardingTourTarget.History,
            scene = OnboardingScene.History,
        ),
        OnboardingPageData(
            title = "设置中心",
            description = "集中管理常规、编辑器、附件与文件、数据与安全设置",
            target = OnboardingTourTarget.Settings,
            scene = OnboardingScene.Settings,
        ),
    )

/** 首次启动和侧边栏“使用介绍”入口共用的全屏引导页。 */
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
    val safeInitialPage = remember(initialPage) { initialPage.coerceIn(0, pages.lastIndex) }
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentIndex = pagerState.currentPage.coerceIn(0, pages.lastIndex)
    val currentPage = pages[currentIndex]
    val compactTextMode = LocalDensity.current.fontScale >= 1.2f
    val outerPadding = if (compactTextMode) 14.dp else 18.dp
    val gap = if (compactTextMode) 8.dp else 12.dp

    fun animateToPage(page: Int) {
        val target = page.coerceIn(0, pages.lastIndex)
        if (target == pagerState.currentPage) return
        scope.launch {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = tween(ONBOARDING_PAGE_SCROLL_DURATION_MILLIS, easing = FastOutSlowInEasing),
            )
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        pages.getOrNull(pagerState.settledPage)?.let { onStepChanged(it.target) }
    }

    if (enableBackHandler) BackHandler(onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = outerPadding, vertical = if (compactTextMode) 12.dp else 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("使用介绍", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        currentPage.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onDismiss) { Text("跳过") }
            }

            Spacer(Modifier.height(gap))
            HorizontalPager(
                state = pagerState,
                pageSpacing = 14.dp,
                modifier = Modifier.weight(1f),
            ) { index ->
                key(index) {
                    OnboardingDemoTheme {
                        when (pages[index].scene) {
                            OnboardingScene.Intro -> AppIntroScene()
                            OnboardingScene.Home -> HomeScene()
                            OnboardingScene.Drawer -> DrawerScene()
                            OnboardingScene.Tasks -> TasksScene()
                            OnboardingScene.Reader -> ReaderScene()
                            OnboardingScene.Editor -> EditorScene()
                            OnboardingScene.NoteInfo -> NoteInfoScene()
                            OnboardingScene.History -> HistoryScene()
                            OnboardingScene.Settings -> SettingsScene()
                        }
                    }
                }
            }

            Spacer(Modifier.height(gap))
            Text(
                currentPage.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(gap))
            PageIndicators(
                count = pages.size,
                selectedIndex = currentIndex,
                pageOffsetFraction = pagerState.currentPageOffsetFraction,
                onPageClick = ::animateToPage,
            )
            Spacer(Modifier.height(gap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    enabled = currentIndex > 0 && !pagerState.isScrollInProgress,
                    onClick = { animateToPage(currentIndex - 1) },
                    modifier = Modifier.weight(1f),
                ) { Text("上一步") }
                Button(
                    enabled = !pagerState.isScrollInProgress,
                    onClick = {
                        if (currentIndex == pages.lastIndex) onFinish() else animateToPage(currentIndex + 1)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (currentIndex == pages.lastIndex) "开始使用" else "下一步") }
            }
        }
    }
}

@Composable
private fun OnboardingDemoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKardLeafThemeStyle provides PrefsManager.AppThemeStyle.CLEAN_LIST,
        LocalKardLeafGlobalCornerRadiusDp provides -1,
        LocalKardLeafHomeCornerRadiusDp provides -1,
    ) {
        MaterialTheme(
            colorScheme =
                lightColorScheme(
                    primary = DemoBlue,
                    onPrimary = Color.White,
                    primaryContainer = DemoBlueSoft,
                    onPrimaryContainer = DemoText,
                    background = Color.White,
                    onBackground = DemoText,
                    surface = Color.White,
                    onSurface = DemoText,
                    surfaceVariant = DemoBackground,
                    onSurfaceVariant = DemoMuted,
                    outlineVariant = DemoBorder,
                ),
            content = content,
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val distance = kotlin.math.abs(index - selectedIndex - pageOffsetFraction).coerceIn(0f, 1f)
            val width by animateDpAsState(
                targetValue = if (distance < 0.5f) 23.dp else 7.dp,
                animationSpec = tween(120, easing = FastOutSlowInEasing),
                label = "OnboardingIndicatorWidth",
            )
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(width)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (distance < 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        .clickable { onPageClick(index) },
            )
        }
    }
}

@Composable
private fun SceneSurface(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier.fillMaxSize().border(1.dp, DemoBorder, shape),
        shape = shape,
        color = Color.White,
        content = content,
    )
}

@Composable
private fun AppIntroScene() {
    val context = LocalContext.current
    SceneSurface {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(AppIconManager.current(context).iconResId),
                contentDescription = "卡叶笔记图标",
                modifier = Modifier.size(126.dp).clip(RoundedCornerShape(32.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text("卡叶笔记", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = DemoText)
            Text("KardLeaf · 作者：kangle", style = MaterialTheme.typography.titleMedium, color = DemoMuted)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntroChip("Markdown")
                IntroChip("本地优先")
                IntroChip("开放文件")
            }
            Spacer(Modifier.height(22.dp))
            IntroInfoRow("正文", "直接保存为 Markdown 文件")
            IntroInfoRow("管理", "分类、标签、任务与历史版本")
        }
    }
}

@Composable
private fun IntroChip(text: String) {
    Surface(shape = CircleShape, color = DemoBlueSoft) {
        Text(text, color = DemoBlue, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun IntroInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, DemoBorder, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DemoBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(54.dp))
        Text(value, color = DemoText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HomeScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            DemoTopBar(
                title = localizedText("全部笔记", "All notes"),
                navigationIcon = Icons.Outlined.Menu,
                actions = listOf(Icons.Outlined.Search, Icons.Outlined.Sort),
                titleTrailingIcon = Icons.Outlined.KeyboardArrowDown,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoChip(localizedText("全部笔记", "All notes"), selected = true)
                DemoChip("生活")
                DemoChip("学习")
                DemoChip("工作")
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DemoNoteCard(
                    title = "示例笔记1 生活目录说明",
                    preview = "这里适合记录日常安排、购物清单、习惯打卡和突然想到的小点子。",
                    meta = "生活  ·  2026.07.13 10:24",
                )
                DemoNoteCard(
                    title = "示例笔记2 学习复盘方法",
                    preview = "学习笔记可以分成概念、例子、疑问和复盘四块。",
                    meta = "学习  ·  2026.07.12 21:06",
                )
            }
            HomeBottomToolbar(
                items =
                    PrefsManager.HomeBottomToolbarItemId.DEFAULT_ORDER.filterNot {
                        it in PrefsManager.HomeBottomToolbarItemId.DEFAULT_HIDDEN_ITEMS
                    },
                buttonSizeDp = PrefsManager.DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
                onItemClick = {},
            )
        }
    }
}

@Composable
private fun DemoTopBar(
    title: String,
    navigationIcon: ImageVector,
    actions: List<ImageVector>,
    titleTrailingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DemoIconButton(navigationIcon, "导航")
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = DemoText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            titleTrailingIcon?.let { Icon(it, null, tint = DemoMuted, modifier = Modifier.size(20.dp)) }
        }
        actions.forEach { DemoIconButton(it, null) }
    }
    DemoDivider()
}

@Composable
private fun DemoIconButton(
    icon: ImageVector,
    description: String?,
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(if (selected) DemoBlueSoft else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (selected) DemoBlue else DemoMuted, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun DemoChip(
    text: String,
    selected: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) DemoBlueSoft else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DemoBlue.copy(alpha = 0.35f) else DemoBorder),
    ) {
        Text(
            text,
            color = if (selected) DemoBlue else DemoMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DemoNoteCard(
    title: String,
    preview: String,
    meta: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, DemoBorder),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = DemoText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(preview, color = DemoMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DrawerScene() {
    SceneSurface {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(0.82f).fillMaxSize().background(DemoBackground).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DemoBorder),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(DemoBlueSoft), contentAlignment = Alignment.Center) {
                                Text("K", color = DemoBlue, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("KardLeaf", color = DemoText, fontWeight = FontWeight.Bold)
                                Text("卡叶笔记 · kangle", color = DemoMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            DemoStat("12", "使用天数")
                            DemoStat("27", "笔记数量")
                            DemoStat("8.6k", "文字数量")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(18) { index ->
                                Box(
                                    Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(
                                        when (index % 5) {
                                            0 -> DemoBlue.copy(alpha = 0.85f)
                                            1, 2 -> DemoBlue.copy(alpha = 0.38f)
                                            else -> DemoBorder
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                DrawerGroup {
                    DemoDrawerRow(Icons.Outlined.Article, localizedText("全部笔记", "All notes"), selected = true)
                    DemoDrawerRow(Icons.Outlined.History, localizedText("最近修改", "Recent"))
                    DemoDrawerRow(Icons.Outlined.StarBorder, "收藏")
                    DemoDrawerRow(Icons.Outlined.EditNote, "速记")
                }
                DrawerGroup {
                    DemoDrawerRow(Icons.Outlined.Sell, "标签")
                    DemoDrawerRow(Icons.Outlined.Folder, "分类")
                    DemoDrawerRow(Icons.Outlined.EventNote, "日期")
                    DemoDrawerRow(Icons.Outlined.PhotoLibrary, "图片")
                }
                DrawerGroup {
                    DemoDrawerRow(Icons.Outlined.Inventory2, "归档")
                    DemoDrawerRow(Icons.Outlined.DeleteOutline, "废弃")
                    DemoDrawerRow(Icons.Outlined.Shield, "隐私")
                    DemoDrawerRow(Icons.Outlined.Settings, "设置")
                }
            }
            Box(Modifier.weight(0.18f).fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun DemoStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DemoText, fontWeight = FontWeight.Bold)
        Text(label, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DrawerGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, DemoBorder, RoundedCornerShape(20.dp)).padding(6.dp),
        content = { content() },
    )
}

@Composable
private fun DemoDrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) DemoBlueSoft else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) DemoBlue else DemoMuted, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) DemoBlue else DemoText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun TasksScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            DemoTopBar("任务", Icons.Outlined.Menu, listOf(Icons.Outlined.MoreVert))
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = DemoBlueSoft.copy(alpha = 0.55f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, null, tint = DemoBlue, modifier = Modifier.size(20.dp))
                        Text("提醒权限正常，可按时间通知", color = DemoMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, DemoBorder, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("添加独立任务…", color = DemoMuted, modifier = Modifier.weight(1f))
                    DemoIconButton(Icons.Outlined.AccessTime, "提醒")
                    DemoIconButton(Icons.Outlined.Add, "添加", selected = true)
                }
                DemoSectionTitle("独立任务", "应用内创建的任务")
                DemoTaskRow("整理本周学习笔记", "今天 20:00", checked = false)
                DemoTaskRow("备份 KardLeaf 笔记库", "明天 09:00", checked = false)
                DemoDivider()
                DemoSectionTitle("笔记中的 Markdown 任务", "识别 - [ ] 与 - [x]")
                DemoTaskRow("补充自己的内容", "学习/示例笔记1.md", checked = false)
                DemoTaskRow("建立示例分类", "已完成", checked = true)
            }
        }
    }
}

@Composable
private fun DemoSectionTitle(
    title: String,
    subtitle: String,
) {
    Column {
        Text(title, color = DemoText, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = DemoMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DemoTaskRow(
    title: String,
    subtitle: String,
    checked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, DemoBorder, RoundedCornerShape(15.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (checked) DemoBlue else DemoMuted)
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = if (checked) DemoMuted else DemoText, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReaderScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(editing = false)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("学习  ·  2026.07.13 10:24  ·  236 字", color = DemoMuted, style = MaterialTheme.typography.labelMedium)
                Text("示例笔记2 学习复盘方法", color = DemoText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("学习笔记可以分成概念、例子、疑问和复盘四块。不要只摘抄原文，最好用自己的话解释一遍。", color = DemoText, style = MaterialTheme.typography.bodyLarge)
                Text("记录内容", color = DemoText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                DemoBullet("主题：学习复盘")
                DemoBullet("场景：课程笔记与错题整理")
                DemoBullet("建议：补充一个能验证理解的小例子")
                Text("待办", color = DemoText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                DemoTaskRow("补充自己的内容", "Markdown 待办", checked = false)
            }
        }
    }
}

@Composable
private fun EditorTopBar(editing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DemoIconButton(if (editing) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowBack, if (editing) "完成" else "返回")
        Spacer(Modifier.weight(1f))
        listOf(
            Icons.Outlined.AccountTree,
            Icons.Outlined.FolderOpen,
            Icons.Outlined.Search,
        ).forEach { DemoIconButton(it, null) }
        if (!editing) DemoIconButton(Icons.Outlined.Edit, "编辑")
        DemoIconButton(Icons.Outlined.Toc, "大纲")
        DemoIconButton(Icons.Outlined.StickyNote2, "属性备注")
        DemoIconButton(Icons.Outlined.MoreVert, "更多")
    }
    DemoDivider()
}

@Composable
private fun DemoBullet(text: String) {
    Row {
        Text("•", color = DemoBlue, modifier = Modifier.width(18.dp))
        Text(text, color = DemoText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditorScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(editing = true)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("示例笔记2 学习复盘方法", color = DemoText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                DemoDivider()
                Text(
                    "# 学习复盘方法\n\n学习笔记可以分成概念、例子、疑问和复盘四块。\n\n## 待办\n- [ ] 补充自己的内容",
                    color = DemoText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(DemoBackground).border(1.dp, DemoBorder).padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DemoFormatButton(Icons.Outlined.Undo, "撤销")
                DemoFormatButton(Icons.Outlined.Redo, "恢复")
                DemoFormatButton(Icons.Outlined.Image, "图片")
                DemoFormatButton(Icons.Outlined.Palette, "绘图")
                DemoFormatText("H1")
                DemoFormatText("H2")
                DemoFormatText("H3")
                DemoFormatButton(Icons.Outlined.Article, "预览")
                DemoFormatButton(Icons.Outlined.FormatBold, "加粗")
                DemoFormatButton(Icons.Outlined.FormatItalic, "斜体")
                DemoFormatButton(Icons.Outlined.FormatQuote, "引用")
                DemoFormatButton(Icons.Outlined.FormatListBulleted, "列表")
                DemoFormatButton(Icons.Outlined.CheckBox, "待办")
                DemoFormatButton(Icons.Outlined.Code, "代码")
                DemoFormatButton(Icons.Outlined.TableChart, "表格")
            }
        }
    }
}

@Composable
private fun DemoFormatButton(
    icon: ImageVector,
    description: String,
) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = DemoMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DemoFormatText(text: String) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        Text(text, color = DemoMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun NoteInfoScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(editing = false)
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(0.42f).fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("学习复盘方法", color = DemoText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("学习笔记可以分成概念、例子、疑问和复盘四块……", color = DemoMuted, style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    modifier = Modifier.weight(0.58f).fillMaxSize().background(DemoBackground).border(1.dp, DemoBorder).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.StickyNote2, null, tint = DemoBlue)
                        Text("属性与备注", color = DemoText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    DemoInfoCard("文件", "示例笔记2 学习复盘方法.md")
                    DemoInfoCard("分类", "学习")
                    DemoInfoCard("字数", "236 字")
                    DemoInfoCard("修改时间", "2026.07.13 10:24")
                    Text("标签", color = DemoText, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DemoChip("学习", selected = true)
                        DemoChip("复盘")
                    }
                    Text("备注", color = DemoText, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, DemoBorder)) {
                        Text("下次补充错题示例和延伸阅读。", color = DemoText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoInfoCard(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = DemoText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoryScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DemoIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                Surface(modifier = Modifier.weight(1f), shape = CircleShape, color = DemoBackground) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = DemoMuted, modifier = Modifier.size(18.dp))
                        Text("搜索版本", color = DemoMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text("完成", color = DemoBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
            }
            DemoDivider()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("对比来源", color = DemoText, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistorySource("版本 2", "07-12 21:06", selected = true, modifier = Modifier.weight(1f))
                    HistorySource("当前版本", "正在使用", selected = false, modifier = Modifier.weight(1f))
                }
                Surface(shape = RoundedCornerShape(16.dp), color = DemoBlueSoft.copy(alpha = 0.45f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("已选版本 2", color = DemoText, fontWeight = FontWeight.SemiBold)
                        Text("# 学习复盘方法\n学习笔记可以分成概念、例子和疑问。", color = DemoMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
                Text("全部版本 · 3", color = DemoText, fontWeight = FontWeight.SemiBold)
                HistoryVersionCard("当前版本", "正在使用 · 约 236 字", "当前", selected = false)
                HistoryVersionCard("版本 2", "2026-07-12 21:06 · 历史保存", "可对比", selected = true)
                HistoryVersionCard("版本 1", "2026-07-11 18:42 · 历史保存", "可恢复", selected = false)
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, DemoBorder).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = DemoBlueSoft) {
                    Text("对比", color = DemoBlue, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(11.dp))
                }
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = DemoBlue) {
                    Text("恢复此版本", color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(11.dp))
                }
            }
        }
    }
}

@Composable
private fun HistorySource(
    text: String,
    meta: String,
    selected: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) DemoBlueSoft else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DemoBlue else DemoBorder),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(text, color = DemoText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(meta, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryVersionCard(
    title: String,
    meta: String,
    badge: String,
    selected: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) DemoBlueSoft.copy(alpha = 0.35f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DemoBlue else DemoBorder),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DemoText, fontWeight = FontWeight.SemiBold)
                Text(meta, color = DemoMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(badge, color = if (selected) DemoBlue else DemoMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsScene() {
    SceneSurface {
        Column(Modifier.fillMaxSize()) {
            DemoTopBar("设置", Icons.AutoMirrored.Outlined.ArrowBack, emptyList())
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSection("常规") {
                    SettingsRow(Icons.Outlined.Folder, "笔记库", "KardLeaf")
                    SettingsRow(Icons.Outlined.Palette, "主题切换", "清爽列表 · 跟随系统 · 蓝色 · 白色")
                    SettingsRow(Icons.Outlined.Tune, "应用界面", "列表 · 修改时间降序")
                    SettingsRow(Icons.Outlined.ViewHeadline, "首页底部工具栏", "已显示 5 个图标，按钮 46dp")
                    SettingsRow(Icons.Outlined.Article, "侧边栏", "数据卡片")
                }
                SettingsSection("编辑器") {
                    SettingsRow(Icons.Outlined.FormatListBulleted, "字符按钮位置", "调整工具按钮顺序")
                    SettingsRow(Icons.Outlined.StickyNote2, "笔记顶部栏", "大纲与属性备注通过顶部按钮打开")
                    SettingsRow(Icons.Outlined.Edit, "默认打开模式", "查看模式")
                    SettingsRow(Icons.Outlined.Description, "编辑器内核", "原生 Beta 内核")
                    SettingsRow(Icons.Outlined.Checklist, "编辑底部工具栏常驻", "已开启")
                }
                SettingsSection("数据与安全") {
                    SettingsRow(Icons.Outlined.Backup, "数据备份", "导入或导出用户数据 JSON")
                    SettingsRow(Icons.Outlined.History, "历史版本数量", "保留最新 20 个版本")
                    SettingsRow(Icons.Outlined.Lock, "安全", "应用锁、隐私和指纹")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = DemoMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, DemoBorder, RoundedCornerShape(18.dp)).padding(vertical = 4.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(DemoBlueSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = DemoBlue, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, color = DemoText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = DemoMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = DemoMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DemoDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DemoBorder))
}

@Preview(name = "01 使用介绍", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewIntro() = OnboardingPreviewPage(0)

@Preview(name = "02 首页与新建", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewHome() = OnboardingPreviewPage(1)

@Preview(name = "03 侧边栏与分类", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewDrawer() = OnboardingPreviewPage(2)

@Preview(name = "04 任务清单", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewTasks() = OnboardingPreviewPage(3)

@Preview(name = "05 笔记阅读", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewReader() = OnboardingPreviewPage(4)

@Preview(name = "06 Markdown 编辑", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewEditor() = OnboardingPreviewPage(5)

@Preview(name = "07 属性与备注", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewNoteInfo() = OnboardingPreviewPage(6)

@Preview(name = "08 历史版本", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewHistory() = OnboardingPreviewPage(7)

@Preview(name = "09 设置中心", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewSettings() = OnboardingPreviewPage(8)

@Composable
private fun OnboardingPreviewPage(page: Int) {
    MaterialTheme {
        OnboardingDialog(
            onDismiss = {},
            onFinish = {},
            initialPage = page,
            enableBackHandler = false,
        )
    }
}
