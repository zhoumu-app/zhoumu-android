package com.zhoumu.android

import android.app.Application
import com.zhoumu.android.notify.ClassReminders

class ZhoumuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ClassReminders.ensureChannel(this)
        ClassReminders.reschedule(this)
    }
}
