# ClassSchedule 课表 📅

一款简洁美观的 Android 课表应用，帮助你一目了然地查看每周课程安排。

> 当前版本：**0.7.1**。已实现周课表、日程概览、课程管理和本地保存；教务页面抓取与快照导出可用于后续适配，真实课表解析尚未实现。

## ✨ 功能规划

- [x] 周视图课表、周次切换、日期与当前周显示
- [x] 日程概览：按天查看课程安排
- [x] 课程添加 / 查看 / 编辑 / 删除，支持每周、单周、双周
- [x] 课程颜色标签、教室与教师信息
- [x] 自动计算当前周（开学日期目前在 `TermCalendar.kt` 中配置）
- [x] Room 本地持久化，首次初始化保护已有课程
- [x] 隐藏部分或全部周次，并从“已隐藏课程”入口恢复
- [x] 教务 WebView 登录与页面抓取、用户主动导出 HTML 快照
- [ ] 教务课表解析与实际导入（当前解析器仍返回空列表）
- [ ] 应用内学期设置、Excel / 文本导入和课表数据导出
- [ ] 桌面小组件（今日课程一览）
- [ ] 上课前提醒通知
- [ ] 深色模式适配

## 🛠 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| 最低支持 | Android 8.0（API 26） |
| 目标版本 | Android 15（API 35） |
| UI | View 体系 + Material Design 3 |
| 架构 | MVVM（ViewModel + Repository） |
| 数据库 | Room 2.6.1 |
| 构建 | Gradle（Kotlin DSL）+ AGP 8.x |

## 📁 目录结构

```text
ClassSchedule/
├── app/                              # 应用主模块
│   ├── build.gradle.kts              # 模块构建脚本（依赖、SDK 版本等）
│   ├── proguard-rules.pro            # 代码混淆规则
│   └── src/
│       ├── main/                     # 主代码
│       │   ├── AndroidManifest.xml   # 应用清单
│       │   ├── java/com/classschedule/app/
│       │   │   ├── MainActivity.kt       # 主页面
│       │   │   ├── model/Course.kt       # 课程数据模型
│       │   │   ├── data/JluTimeTable.kt  # 吉大作息时间表（12 节次）
│       │   │   ├── data/SampleData.kt    # 首次使用时的内置课程数据
│       │   │   ├── data/CourseRepository.kt # 数据操作与初始化保护
│       │   │   ├── data/db/             # Room 数据库、DAO 与实体
│       │   │   ├── data/importer/       # 教务解析入口（待适配）
│       │   │   └── ui/                  # 周课表、日程、课程编辑与导入页面
│       │   └── res/                  # 资源文件
│       │       ├── layout/           # 布局文件
│       │       └── values/           # 字符串、主题等
│       ├── test/                     # 本地单元测试（JVM）
│       └── androidTest/              # 仪器化测试（真机/模拟器）
├── docs/                             # 设计文档、开发笔记
├── build.gradle.kts                  # 根构建脚本（插件版本）
├── settings.gradle.kts               # 工程与模块配置
├── gradle.properties                 # Gradle 属性
├── LICENSE                           # 开源许可证（MIT）
└── README.md
```

## 🚀 快速开始

### 环境要求

- Android Studio Ladybug 或更高版本
- JDK 17
- Android SDK Platform 35

### 构建运行

```bash
git clone https://github.com/starryecho-T/ClassSchedule.git
```

1. 用 Android Studio 打开项目根目录，等待 Gradle Sync 完成；
2. 连接手机（开启开发者模式 + USB 调试）或启动模拟器；
3. 点击 Run ▶️ 安装运行。

> 💡 提示：仓库已包含 Gradle Wrapper（`gradlew` / `gradle-wrapper.jar`），首次 Sync 时
> Gradle 会自动下载对应发行版；若网络较慢，可将 `gradle/wrapper/gradle-wrapper.properties`
> 中的 `distributionUrl` 换成腾讯镜像地址。

## 🎓 吉林大学适配

- 作息时间表集中在 `app/src/main/java/com/classschedule/app/data/JluTimeTable.kt`，
  默认为「上午 4 节 + 下午 4 节 + 晚上 4 节」共 12 节，可按校区实际作息修改；
- 周视图按节次数自动排布，改作息无需改界面代码；
- 示例数据中的教学楼名称（李四光楼、唐敖庆楼、经信教学楼等）仅作演示。

## 🗺 开发里程碑

- [x] **M0** 项目骨架搭建（目录结构 / README / 工程配置）
- [x] **M1** 周视图课表静态展示（自绘 TimetableView、吉大作息、今日高亮）
- [x] **M2** 课程增删改查 + Room 持久化
- [x] **M3** 周次切换、日期计算、日程概览与课程隐藏恢复
- [ ] **M4** 课表导入导出：页面抓取与 HTML 快照导出已实现，真实课表解析与数据导入待完成
- [ ] **M5** 桌面小组件与上课提醒

## 本轮完善（2026-10-17）

- 增加已隐藏课程管理入口，恢复全部被隐藏的课程。
- 保留编辑前的离散周次，拦截空单双周选择，防止重复保存。
- 首次初始化不再覆盖已有课表；主动删空后不重新填入内置课程；整表替换加入事务与空结果保护。
- 修正开学日前的周次计算，保留主页选择的周次。
- 改善教务页面加载、抓取和失败提示，支持将独立 HTML 快照导出到用户选择的位置。快照可能包含个人信息，分享前需检查并移除敏感内容。

**验证状态：** 29 个 JVM 回归测试已通过直接编译运行。完整 Gradle 构建、APK、Lint 和真机验收尚未完成，不能据此认定 Android 应用构建通过。测试方式、限制和设备验收步骤见 [验证记录](docs/verification-2026-10-17.md)。

## 🤝 贡献指南

- 欢迎通过 Issue 反馈问题或提出功能建议；
- 提交 PR 前请确保代码可编译通过；
- 提交信息建议使用 `feat: / fix: / docs: / chore:` 等常规前缀。

## 📄 许可证

[MIT License](./LICENSE) © 2026 starryecho-T
