package com.zhoumu.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zhoumu.android.data.ScheduleKind
import com.zhoumu.android.data.SettingsRepository
import com.zhoumu.android.data.ZhoumuSettings
import com.zhoumu.android.ui.screens.HomeScreen
import com.zhoumu.android.ui.screens.ScheduleEditorScreen
import com.zhoumu.android.ui.screens.SettingsScreen

/** 顶层导航。 */
@Composable
fun ZhoumuApp(repo: SettingsRepository, settings: ZhoumuSettings) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(settings = settings, onOpenSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(
                repo = repo,
                settings = settings,
                onBack = { nav.popBackStack() },
                onOpenTable = { kind -> nav.navigate("table/${kind.name}") },
            )
        }
        composable("table/{kind}") { entry ->
            val kind = ScheduleKind.entries
                .firstOrNull { it.name == entry.arguments?.getString("kind") }
                ?: ScheduleKind.Regular
            ScheduleEditorScreen(
                repo = repo,
                settings = settings,
                kind = kind,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
