package com.zhoumu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.zhoumu.android.data.ScheduleKind
import com.zhoumu.android.data.SettingsRepository
import com.zhoumu.android.data.ZhoumuSettings
import com.zhoumu.android.ui.screens.HomeScreen
import com.zhoumu.android.ui.screens.ScheduleEditorScreen
import com.zhoumu.android.ui.screens.SettingsScreen

/**
 * 顶层界面切换。
 *
 * **这里没用 Navigation Compose**，而是用一个状态变量手写切换。
 *
 * 原因：之前用 NavHost，用户反馈切换主题后出现过整屏空白。
 * dump 控件树看到 ComposeView 存在、Surface 的背景色也画出来了，
 * 但里面一个节点都没有 —— 组合是空的。日志里有 `popEntryFromBackStack`，
 * 判断是返回栈被弹空之后没有目的地可渲染。
 *
 * 这种"栈被弹空就白屏"的失败模式，用 NavHost 时总得额外兜底；
 * 而本 App 的层级就这么浅（首页 / 设置 / 课表编辑器），
 * 直接用一个状态变量切换，**结构上就不可能空**。
 */
@Composable
fun ZhoumuApp(repo: SettingsRepository, settings: ZhoumuSettings) {

    /** 当前在哪一层。用 rememberSaveable 保证被系统重建后还能恢复。 */
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    /** 正在编辑哪张课表；null = 没在编辑。 */
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    // 系统返回键：从编辑器/设置回上一层，在首页则交回系统（退出 App）
    BackHandler(enabled = editing != null) { editing = null }
    BackHandler(enabled = editing == null && screen != Screen.Home) { screen = Screen.Home }

    val kind = editing?.let { name -> ScheduleKind.entries.firstOrNull { it.name == name } }

    when {
        kind != null -> ScheduleEditorScreen(
            repo = repo,
            settings = settings,
            kind = kind,
            onBack = { editing = null },
        )

        screen == Screen.Settings -> SettingsScreen(
            repo = repo,
            settings = settings,
            onBack = { screen = Screen.Home },
            onOpenTable = { editing = it.name },
        )

        else -> HomeScreen(
            settings = settings,
            onOpenSettings = { screen = Screen.Settings },
        )
    }
}

/** 顶层页面。就两个，不值得上导航库。 */
private enum class Screen { Home, Settings }
