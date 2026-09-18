# 周目 · Android 版

**打开就知道今天是第几周、今天上什么课。**

这是 [周目](https://github.com/zhoumu-app/zhoumu) 家族的 Android 版，和 iOS 版功能一致，
只是去掉了 iPhone 上特有的灵动岛（Android 没有这东西）。

浅色白蓝、深色黑蓝，**不联网、不要账号、不收集任何数据**。

---

## 功能

### 周目

- **打开直接显示**：圈内是当前科目，圈下面是第 N 周，圆环按**课程完成进度**填充。
- **开学日期**：以这天为第 1 周的第 1 天，每 7 天进入下一周。
- **周目循环**：开启后按 N 周循环显示（N 可选 1–20 周）。

### 两张课表

- **正课表** 与 **晚课表**互相独立，可各自开启或**单独关闭**。
- 覆盖周一至周日，**每天节数可以不一样**（1–12 节）。
  改节数的加减控件上**直接显示当前是几节**，到上下限时按钮会变灰。
- 每张表可以选 **固定**（每周同一份）或 **按周目轮换**（每周换一排）。
- 每格只有「无」和「自定义」两个选项。
- 每节可**选填上下课时间**——不填也能用，只是不参与时间轴和提醒。
  时间可以**所有天共用一套**（统一），也可以**每天各设一套**（每天单独）。

### 首页两页

- **第一页**：圆环 + 周目 + 三行信息 —— 距下节课还有多久 / 这节还有多久结束 / 下节是什么。
- **第二页**：当天完整课表，当前这节高亮标「进行中」。
- 圆环在上课时按本节进度走，**课间休息时环是闭合的**，每节课结束后重新开始。

### 桌面小组件

不打开 App 也能看到第 N 周和今天的前三节课。

### 上下课提醒

- **每节课上课前、下课前各提醒一次**，可以设置提前几分钟（默认 5 分钟）。
- 用系统精确闹钟，**App 没开着也会响**。

### 外观

- **浅色 / 深色 / 跟随系统** 三态切换。

---

## 和 iOS 版的区别

| | iOS 版 | Android 版 |
| --- | --- | --- |
| 周目计算 | 有 | 有（**算法完全一致**） |
| 两张课表 / 每日节数 / 时间 | 有 | 有 |
| 首页两页 | 有 | 有 |
| 桌面小组件 | 有 | 有 |
| 上下课提醒 | 有 | 有 |
| **灵动岛 / 实时活动** | 有 | **没有**（Android 没这东西） |
| Apple Watch | 有 | 没有 |
| 锁屏卡片 | 有 | 没有 |

### 为什么算法能保证一致

核心逻辑（`SemesterCalculator.kt` / `ScheduleModel.kt`）是从 iOS 版逐行移植的，
单元测试里的断言也和 iOS 版 `Tools/CalculatorCheck` 一一对应。
两边算出来的周目、时间轴、进度**必须一样**。

---

## 自己编译

需要 JDK 17+ 和 Android SDK（compileSdk 35）。

```bash
# 指向你的 Android SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 编译
./gradlew :app:assembleDebug

# 跑单元测试
./gradlew :app:test

# 一键校验（编译 + 测试）
./Tools/verify.sh
```

摸不到 Android SDK 的话，至少可以单独跑核心逻辑的测试：

```bash
./gradlew :app:test --tests '*SemesterCalculatorTest*'
```

---

## 项目结构

```
app/src/main/java/com/zhoumu/android/
  MainActivity.kt              入口
  ZhoumuApplication.kt         建通知渠道 + 排提醒
  data/
    SemesterCalculator.kt      周目计算（和 iOS 版算法一致）
    ScheduleModel.kt           课表模型 + 时间轴 + 状态机
    SettingsRepository.kt      设置读写（DataStore）
  ui/
    ZhoumuApp.kt               导航
    theme/                     蓝色主题（浅色 / 深色）
    screens/
      HomeScreen.kt            首页两页 + 圆环
      SettingsScreen.kt        设置
      ScheduleEditorScreen.kt  课表编辑器
  notify/
    ClassReminders.kt          精确闹钟 + 通知
  widget/
    WeekWidget.kt              桌面小组件（Glance）
app/src/test/                  单元测试
Tools/verify.sh                一键校验
```

---

## 许可证

[MIT](LICENSE)
