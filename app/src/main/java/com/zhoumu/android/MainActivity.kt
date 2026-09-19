package com.zhoumu.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zhoumu.android.data.SettingsRepository
import com.zhoumu.android.ui.ZhoumuApp
import com.zhoumu.android.ui.theme.ZhoumuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏边到边。个别旧 ROM 上这个调用可能抛异常，包一层别让它拦住启动。
        runCatching { enableEdgeToEdge() }

        val repo = SettingsRepository.get(applicationContext)

        setContent {
            val settings by repo.settings.collectAsState(initial = repo.current())
            ZhoumuTheme(mode = settings.themeMode) {
                ZhoumuApp(repo = repo, settings = settings)
            }
        }
    }
}
