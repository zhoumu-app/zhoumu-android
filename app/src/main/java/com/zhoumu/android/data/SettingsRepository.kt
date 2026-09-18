package com.zhoumu.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhoumu.android.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 一份完整的设置快照。 */
data class ZhoumuSettings(
    val startDate: LocalDate = LocalDate.now(),
    val cycleWeeks: Int = 3,
    val cyclingEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val regular: ScheduleTable = ScheduleTable.empty(),
    val evening: ScheduleTable = ScheduleTable.empty(),
    /** 上下课前几分钟提醒。 */
    val reminderMinutes: Int = 5,
    val remindersEnabled: Boolean = true,
) {
    fun table(kind: ScheduleKind): ScheduleTable =
        if (kind == ScheduleKind.Regular) regular else evening

    fun withTable(kind: ScheduleKind, table: ScheduleTable): ZhoumuSettings =
        if (kind == ScheduleKind.Regular) copy(regular = table) else copy(evening = table)

    val tables: Map<ScheduleKind, ScheduleTable>
        get() = mapOf(ScheduleKind.Regular to regular, ScheduleKind.Evening to evening)

    fun phase(date: LocalDate = LocalDate.now()): SemesterPhase =
        SemesterCalculator.phase(startDate, cycleWeeks, cyclingEnabled, date)

    /** 今天的课（按时间排好）。 */
    fun classes(date: LocalDate = LocalDate.now()): List<ScheduledClass> {
        val p = phase(date)
        if (p !is SemesterPhase.InSession) return emptyList()
        return ClassSchedule.classes(date, tables, p.info.displayWeek)
    }

    /** 今天有没有填过任何时间。 */
    val todayHasTimes: Boolean
        get() = ScheduleKind.entries.any { tables[it]?.hasAnyTime == true }
}

/**
 * 设置存储。
 *
 * 用 DataStore 存一份 JSON，和 iOS 版把课表编成 JSON 存 UserDefaults 是一个思路。
 */
class SettingsRepository private constructor(private val store: DataStore<Preferences>) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(loadInitialBlocking())
    val settings: StateFlow<ZhoumuSettings> = _settings.asStateFlow()

    fun current(): ZhoumuSettings = _settings.value

    /** 改一份设置并落盘。 */
    fun update(transform: (ZhoumuSettings) -> ZhoumuSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        scope.launch { persist(next) }
    }

    private suspend fun persist(s: ZhoumuSettings) {
        store.edit { p ->
            p[K.startDate] = s.startDate.toEpochDay()
            p[K.cycleWeeks] = s.cycleWeeks
            p[K.cycling] = s.cyclingEnabled
            p[K.theme] = s.themeMode.name
            p[K.regular] = s.regular.encoded()
            p[K.evening] = s.evening.encoded()
            p[K.reminderMinutes] = s.reminderMinutes
            p[K.remindersEnabled] = s.remindersEnabled
        }
    }

    /** 首次构造时同步读一次，免得界面闪一下默认值。 */
    private fun loadInitialBlocking(): ZhoumuSettings {
        val p = runCatching {
            kotlinx.coroutines.runBlocking { store.data.first() }
        }.getOrNull() ?: return ZhoumuSettings()

        return ZhoumuSettings(
            startDate = p[K.startDate]?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
            cycleWeeks = (p[K.cycleWeeks] ?: 3)
                .coerceIn(SemesterCalculator.cycleWeeksRange.first, SemesterCalculator.cycleWeeksRange.last),
            cyclingEnabled = p[K.cycling] ?: true,
            themeMode = ThemeMode.fromKey(p[K.theme]),
            regular = ScheduleTable.decode(p[K.regular]),
            evening = ScheduleTable.decode(p[K.evening]),
            reminderMinutes = p[K.reminderMinutes] ?: 5,
            remindersEnabled = p[K.remindersEnabled] ?: true,
        )
    }

    private object K {
        val startDate = androidx.datastore.preferences.core.longPreferencesKey("semester.startDate")
        val cycleWeeks = intPreferencesKey("semester.cycleWeeks")
        val cycling = booleanPreferencesKey("semester.cyclingEnabled")
        val theme = stringPreferencesKey("app.themeMode")
        val regular = stringPreferencesKey("schedule.regular")
        val evening = stringPreferencesKey("schedule.evening")
        val reminderMinutes = intPreferencesKey("app.reminderMinutes")
        val remindersEnabled = booleanPreferencesKey("app.remindersEnabled")
    }

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zhoumu")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext.dataStore).also { instance = it }
            }
    }
}
