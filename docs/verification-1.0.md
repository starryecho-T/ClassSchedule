# v1.0.0 验证记录

验证日期：2026-10-24。范围：学期切换（含 TermCalendar 学期参数化重构、课程按学期隔离、更新说明格式化）。

## 环境与工具链

- Gradle 8.10.2 Wrapper + `D:\dev-tools\jdk-17`。注意：Android Studio 自带 JBR 为 Java 25（`25.0.3`），Gradle 8.10.2 不支持，直接运行 `gradlew` 会以 `What went wrong: 25.0.3` 失败，必须显式 `JAVA_HOME=D:/dev-tools/jdk-17`。
- 真机：OPPO PLF110，Android 16，USB 调试（设备号 3B6F5EEAZ2KD714V）。

## JVM 单元测试

| 阶段 | 结果 |
| --- | --- |
| 基线（干净 HEAD，git worktree 独立运行） | 136 项全部通过，0 失败 |
| 功能完成后 | 147 项全部通过，0 失败 |

新增 11 项测试：TermCalendarTest（第二学期校历、内置学期数据、defaultWeek 边界、TermConfig 校验）、TodayPlanTest（第二学期按自身开学日推算）、BackupCodecTest（跨学期备份拒绝、周次按目标学期校验）、CourseDaoPolicyTest（替换仅作用于目标学期）、UpdateCheckerTest（更新说明清理、句子边界截断、短文本保真）。

## 构建

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` 全部成功。
- release 构建新增 `signingConfig = signingConfigs.getByName("debug")`，与历史发布的 debug 签名一致（0.8.x/0.9.x 同签名覆盖安装的前提）。
- Room schema v3 已导出：`term_id TEXT NOT NULL DEFAULT '2026-2027-1'`，与 `MIGRATION_2_3` 的 SQL 一致。

## 真机验证（0.9.0 覆盖安装 1.0.0）

1. **数据库迁移 v2→v3**：覆盖安装成功（签名匹配），启动无崩溃、logcat 无 SQLite/Migration 错误；原课表（25 级地球物理学内置示例课表）完整显示，第 7 周“本周”高亮、日期表头 10/19–10/25 正确。安装前已用 `run-as` 快照数据库至 `device-backup-0.9.0/`（不入库）。
2. **学期切换**：课表页顶栏点击弹出学期列表；切换到第二学期后标题变“第 1 周课表”、副标题变“2026-2027 学年第二学期”、表头变 3/1–3/7、课表为空（课程按学期隔离）、Toast 确认；切回第一学期后课程与“第 7 周 · 本周”完整恢复。
3. **学期配置入口**：「我的」页显示“当前学期：2026-2027 学年第一学期”按钮，版本行显示 ClassSchedule 1.0.0。

## 真机发现并修复的问题

- **学期选择对话框不显示选项列表**：`setSingleChoiceItems` 与 `setMessage` 同时使用时列表被消息区吞掉（真机截图确认）。已去掉 message 并在代码注释说明该互斥约束；修复后重新构建、覆盖安装并复测通过。

## 尚未覆盖

- 上课提醒与桌面小组件按新学期重算：逻辑由 JVM 测试覆盖（TodayPlan/TermCalendar），未在真机布置小组件或等待真实闹钟触发。
- 教务导入按新学期开学日反推实验课周次：由 GridScheduleParserTest 覆盖，未做真实教务页面回归。
- `connectedDebugAndroidTest`（BackupDatabaseTest / NavigationTest 等）本轮未执行。
- 校历中的节假日（中秋、国庆、元旦、清明、端午等）仅参与周次推算，界面未做标注。
