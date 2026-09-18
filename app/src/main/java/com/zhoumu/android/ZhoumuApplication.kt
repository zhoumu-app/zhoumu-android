package com.zhoumu.android

import android.app.Application
import com.zhoumu.android.data.SettingsRepository
import com.zhoumu.android.notify.ClassReminders
import com.zhoumu.android.widget.WeekWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ZhoumuApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ClassReminders.ensureChannel(this)

        val repo = SettingsRepository.get(this)

        // 设置一变就重排提醒 + 刷小组件。
        //
        // 之前只在 onCreate 里排一次，结果用户改完课表提醒不会跟着变——
        // 实测出来的：加了一节课之后 dumpsys alarm 里还是空的。
        scope.launch {
            repo.settings
                .drop(1)                 // 首次由下面的 reschedule 负责，别重复
                .distinctUntilChanged()
                .collect {
                    ClassReminders.reschedule(this@ZhoumuApplication)
                    runCatching { WeekWidget.refresh(this@ZhoumuApplication) }
                }
        }

        // 冷启动先排一次
        ClassReminders.reschedule(this)
    }
}
