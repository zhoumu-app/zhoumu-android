package com.zhoumu.android.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhoumu.android.MainActivity
import com.zhoumu.android.data.ScheduledClass
import com.zhoumu.android.data.SettingsRepository
import com.zhoumu.android.data.ZhoumuSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课 / 下课提醒。
 *
 * iOS 版靠灵动岛实时活动 + 本地通知；Android 没有灵动岛，
 * 所以这里只用通知：每节课**上课前**和**下课前**各提醒一次。
 *
 * 用 AlarmManager 精确闹钟，App 没开着也会响。
 */
object ClassReminders {

    const val CHANNEL_ID = "zhoumu.class"
    private const val REQUEST_BASE = 4200

    /** 建通知渠道。Application 启动时调一次。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "上下课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "每节课上课前、下课前提醒一次"
            }
        )
    }

    /**
     * 重排今天的提醒。
     *
     * 先全撤掉再排，避免改完课表后旧的闹钟还在。
     */
    fun reschedule(context: Context) {
        val alarm = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val settings = SettingsRepository.get(context).current()

        cancelAll(context)

        if (!settings.remindersEnabled) return

        val now = LocalDateTime.now()
        val lead = settings.reminderMinutes.toLong()
        for (c in settings.classes(LocalDate.now())) {
            // 上课前
            schedule(alarm, context, c, c.start.minusMinutes(lead), now, isStart = true)
            // 下课前
            schedule(alarm, context, c, c.end.minusMinutes(lead), now, isStart = false)
        }
    }

    private fun schedule(
        alarm: android.app.AlarmManager,
        context: Context,
        c: ScheduledClass,
        at: LocalDateTime,
        now: LocalDateTime,
        isStart: Boolean,
    ) {
        if (!at.isAfter(now)) return
        val trigger = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("subject", c.subject)
            putExtra("isStart", isStart)
            putExtra("minutes", SettingsRepository.get(context).current().reminderMinutes)
            putExtra("time", "${c.start.hour}:%02d".format(c.start.minute))
            // 每节课的每个提醒都要有不同的 requestCode，否则会互相覆盖
            data = android.net.Uri.parse("zhoumu://reminder/${c.id}/$isStart")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + (c.id + isStart).hashCode() and 0xFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 用精确闹钟，保证按点响
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun cancelAll(context: Context) {
        // PendingIntent 是按 requestCode + Intent 匹配的，这里靠重建同样的 Intent 来撤。
        // 简单起见：整体重排时只撤今天的（用上面同样的 hash 规则）。
        val settings = SettingsRepository.get(context).current()
        for (c in settings.classes(LocalDate.now())) {
            for (isStart in listOf(true, false)) {
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    data = android.net.Uri.parse("zhoumu://reminder/${c.id}/$isStart")
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    REQUEST_BASE + (c.id + isStart).hashCode() and 0xFFFF,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                pi?.let { context.getSystemService(android.app.AlarmManager::class.java)?.cancel(it) }
            }
        }
    }

    /** 通知权限有没有给。 */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 把通知发出来。 */
    fun notify(context: Context, subject: String, isStart: Boolean, minutes: Int, time: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (isStart) "$subject 快上课了" else "$subject 快下课了"
        val body = if (isStart) "距离上课还有 $minutes 分钟 · $time 开始"
                   else "距离下课还有 $minutes 分钟"

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((subject + isStart).hashCode() and 0xFFFF, n)
        }
    }
}

/** 闹钟到点后由它把通知发出来。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClassReminders.notify(
            context,
            intent.getStringExtra("subject") ?: "下一节课",
            intent.getBooleanExtra("isStart", true),
            intent.getIntExtra("minutes", 5),
            intent.getStringExtra("time") ?: "",
        )
    }
}

/** 开机 / 时间变更后重排。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED
        ) {
            ClassReminders.reschedule(context)
        }
    }
}
