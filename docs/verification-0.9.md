# v0.9.0 验证记录

日期：2026-10-19。版本：0.9.0 / versionCode 21。数据库 schema 保持 2，未执行破坏性迁移。

## 已完成

| 项目 | 实际结果 |
| --- | --- |
| JVM 单元测试 | 131 项，0 失败 / 0 错误 / 0 跳过 |
| Android 真机测试 | Android 16 / PLF110，8 项通过 |
| 构建 | assembleDebug、assembleDebugAndroidTest、assembleRelease 成功 |
| Lint | 0 错误、32 警告；未关闭 lint 或以跳过 lint 的构建作为验收 |
| 导航 | 三 Tab 单实例切换、重建后选择页与课表周次保留 |
| 备份与恢复 | 严格输入校验；编解码往返；真实内存 Room 的重复保留、追加幂等、过期快照拒绝、触发器制造插入失败后的事务回滚 |
| 文件链路 | ContentResolver 文件往返设备测试；真机 SAF 导出成功；未对用户数据库执行替换测试 |
| 外观 | 三种选择切换、持久化值、Activity 重建、强制停止后冷启动仍保留深色选择、亮暗三 Tab 应用内截图检查；小组件 RemoteViews 颜色随选择变化 |
| 升级 | 经授权先备份 0.8.3 私有数据及 APK，再同签名覆盖安装；课程记录逐字段核对无变化 |
| 启动修复 | 未授权精确闹钟的真实设备冷启动正常；原 0.8.3 日志确认 SecurityException 来自 setExactAndAllowWhileIdle |
| 页面文案 | 移除“我的”页末尾的提醒与备份说明，保留 GitHub / Star 入口 |

备份测试覆盖原始文本、颜色、隐藏周次与重复记录，不要求保留数据库主键。恢复验证使用隔离数据库，未覆盖或清空用户课表。设备备份、应用日志和截图仅保存在被 Git 忽略的 app/build/device-validation 下，不上传仓库。

## 复现环境与命令

- Windows，JDK 17，Gradle 8.10.2，AGP 8.7.3，compileSdk / targetSdk 35，minSdk 26。
- 本机 socket 临时路径与代理问题见 tools/dev/TROUBLESHOOTING.md。依赖已缓存，最终构建使用 offline。

```powershell
$env:JAVA_HOME='D:/dev-tools/jdk-17'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:/dev-tools/tmp'
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --offline --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.classschedule.app.test/androidx.test.runner.AndroidJUnitRunner
```

报告：app/build/test-results/testDebugUnitTest、app/build/reports/lint-results-debug.html、app/build/device-validation/instrumentation-night.txt。

## 发布与覆盖安装

为保持已有用户覆盖升级能力，GitHub 分发包沿用历史 debug 签名，来源为 assembleDebug；assembleRelease 的无签名产物不作安装包分发。已与手机上原 0.8.3 APK 核对证书。

签名证书 SHA-256：f24988ece539c3ef363620bc4f618bed429762bc6fb158ad5d8761a3644e9319。该包仍为可调试构建，正式签名及迁移方案留待后续版本。

没有配置 GitHub Actions；本次为本地验证后发布。

## 未覆盖与已知限制

- 仅有 Android 16 真机结果；API 26 / 33 / 35、多厂商启动器、大字体和全量横屏组合未完成设备验收。
- 未验证后台进程被系统回收后的全部路径、长期待机通知准点性或所有 SAF 提供方；普通提醒可能受系统省电影响而延后。
- 教务第三方网页不强制反色；未重新执行所有学校的登录导入流程。
- 本版仅提供手动明文备份，不包含登录态、系统权限或外观偏好；仅支持当前硬编码学期配置。
- 硬编码开学日期、学期设置与 release 签名仍是后续事项；不能把本次测试描述为全平台覆盖。
