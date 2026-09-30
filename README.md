# ClassSchedule 课表 📅

一款简洁美观的 Android 课表应用，帮助你一目了然地查看每周课程安排。

> 项目状态：🚧 初始化阶段（项目骨架已搭建，功能开发中）

## ✨ 功能规划

- [ ] 周视图课表展示（周次切换、当前课程高亮）
- [ ] 课程管理（添加 / 编辑 / 删除课程，支持单周 / 双周）
- [ ] 课程颜色标签与备注（教室、教师）
- [ ] 学期与周次管理（设置开学日期，自动计算当前周）
- [ ] 数据本地持久化（Room 数据库）
- [ ] 课表导入（从教务系统 / Excel / 文本快速导入）
- [ ] 桌面小组件（今日课程一览）
- [ ] 上课前提醒通知
- [ ] 深色模式适配

## 🛠 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| 最低支持 | Android 7.0（API 24） |
| 目标版本 | Android 15（API 35） |
| UI | View 体系 + Material Design 3 |
| 架构 | MVVM（ViewModel + Repository，规划中） |
| 数据库 | Room（规划中） |
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
│       │   │   └── MainActivity.kt   # 主页面（后续承载周视图课表）
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

> 💡 提示：仓库暂未提交 Gradle Wrapper 二进制文件，如首次打开提示缺失，
> 可在项目根目录执行 `gradle wrapper` 生成，或让 Android Studio 自动处理。

## 🗺 开发里程碑

- [x] **M0** 项目骨架搭建（目录结构 / README / 工程配置）
- [ ] **M1** 周视图课表静态展示
- [ ] **M2** 课程增删改查 + Room 持久化
- [ ] **M3** 学期周次管理与当前周高亮
- [ ] **M4** 课表导入导出
- [ ] **M5** 桌面小组件与上课提醒

## 🤝 贡献指南

- 欢迎通过 Issue 反馈问题或提出功能建议；
- 提交 PR 前请确保代码可编译通过；
- 提交信息建议使用 `feat: / fix: / docs: / chore:` 等常规前缀。

## 📄 许可证

[MIT License](./LICENSE) © 2026 starryecho-T
