# 发布与构建踩坑记录（TROUBLESHOOTING）

维护约定：**每次踩坑必须追加到本文件**，格式为「日期 + 症状 + 根因 + 解法」。后人遇到同样问题先查这里。

---

## 2026-10-19 · v0.8.3 发布全流程

### 1. Gradle 报错 "What went wrong: 25.0.3"

- **症状**：`./gradlew` 直接失败，错误信息只有一个版本号 `25.0.3`。
- **根因**：机器上的 java 是 JDK 25（Android Studio / PyCharm 自带的 JBR），Gradle 8.10.2 最高支持 JDK 21，不兼容时只吐出 java 版本号，非常迷惑。
- **解法**：本机已有现成 JDK 17 —— `D:\dev-tools\jdk-17`（同目录还有 gradle-8.10.2）。构建时 `JAVA_HOME="D:/dev-tools/jdk-17" ./gradlew ...` 即可。

### 2. release 构建卡在 lintVitalAnalyzeRelease 下载依赖失败

- **症状**：`assembleRelease` 失败于 `lintVitalAnalyzeRelease`，下载 `intellij-core-31.7.3.jar` 报 `Connect to 127.0.0.1:7892 failed: Connection refused`。
- **根因**：系统配置了本地代理（7892 端口）但代理进程没开；编译/测试依赖已缓存所以不受影响，唯独 lint 任务缺两个 jar 需要联网。
- **解法**：本次跳过 lint —— `./gradlew assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease`。根治办法是开代理或配镜像。

### 3. 签名之谜：GitHub Release 上的 APK 用什么签的？

- **症状**：仓库无 signingConfig、无 .jks 文件，但历史 Release APK 可安装。
- **排查**：对比 `D:\临时储存文件\ClassSchedule-0.8.2.apk`（即上传到 Release 的那份）与本地 `app/build/outputs/apk/debug/app-debug.apk` 的 MD5 —— **完全一致**。
- **结论**：本项目历史版本全部是 **debug 签名**（`~/.android/debug.keystore`）发布的。要发正式签名版需另行生成 keystore 并配 signingConfig；当前保持 debug 签名可保证老用户直接覆盖安装。

### 4. github.com:443 不通时的推送与发布

- **症状**：`git push` 报 `Failed to connect to github.com:443 over proxy 127.0.0.1`，但 `api.github.com` 可达（`curl https://api.github.com/zen` 返回 200）。
- **解法**（全部走 API，凭据从 `git credential fill` 读）：
  1. **推代码**：`python tools/dev/push_via_api.py <owner/repo> <远端base_sha> <本地new_sha>`（一次一个提交，多提交逐个推）
  2. **打 tag**：`POST /git/tags`（annotated tag 对象）→ `POST /git/refs`（`refs/tags/vX.Y.Z` 指向它）
  3. **建 Release**：`POST /releases`
  4. **传 APK**：`POST {upload_url}?name=xxx.apk`，Content-Type 用 `application/vnd.android.package-archive`
- **注意**：API 推送重建 commit，远端 SHA 与本地不同；网络恢复后尽快 `git fetch && git reset --hard origin/main` 对齐。本仓库 token scope 为 `gist, read:org, repo`（无 workflow scope，推不了 .github/workflows/）。

### 5. 一次 POST /releases "404" 的虚惊

- **症状**：release_v083.py 脚本尾部报 HTTP 404，看似创建 Release 失败。
- **根因**：Release 其实**创建成功**了（201），404 出在后续上传资产那一步——`upload_url` 处理逻辑有 bug。用 `GET /releases/tags/v0.8.3` 确认存在后，单独补传资产即成功。
- **教训**：脚本失败先查远端实际状态再重试，盲目整体重跑可能创建重复 Release。

### 6. 删不掉的文件：半截下载 zip

- **症状**：`rm` 删 `D:\tools\jdk17.zip` 反复失败（trash 通道异常）。
- **根因**：一个早先后台启动的 curl 下载进程还活着、正持有该文件写句柄。文件大小一直在涨是识别线索。
- **解法**：先 `tasklist | grep curl` + 强杀进程，再删。后台下载任务不用时要及时收尾。

---

## 版本发布 checklist（v0.8.3 实操版）

1. `app/build.gradle.kts`：versionCode +1、versionName 改新版本号
2. 提交 + 本地 tag：`git tag vX.Y.Z`
3. `JAVA_HOME="D:/dev-tools/jdk-17" ./gradlew assembleDebug`（**debug 签名，与历史一致**）
4. `cp app/build/outputs/apk/debug/app-debug.apk ClassSchedule-X.Y.Z.apk`
5. 网络通则 `git push origin main --tags` 后网页建 Release；不通则走第 4 条坑位的 API 流程
6. 手机装新 APK 验证 → 更新 `D:\临时储存文件\` 的本地备份


## 2026-10-19 · 0.9 开发验证

### Gradle 启动时 Unable to establish loopback connection

- 症状：JDK 17 下连接 Gradle daemon 失败，底层为 UnixDomainSockets.connect 的 Invalid argument。
- 定位：Windows JDK 的 PipeImpl 使用 AF_UNIX，自动 socket 路径来源首先为 jdk.net.unixdomain.tmpdir / TEMP。当前环境默认路径无法连接。
- 已验证解法：仅在构建进程设置 JAVA_HOME=D:/dev-tools/jdk-17、JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/dev-tools/tmp（先创建目录）；不修改系统全局配置。
- 设备测试依赖首次需要联网；本机旧代理不可用时，Gradle 命令增加 -Dhttp.proxyHost= -Dhttps.proxyHost=。下载完成后可用 --offline 验证。

### 0.8.3 手机启动崩溃

- 证据：Android 16 / PLF110 设备，0.8.3(20)，应用崩溃日志指向 ReminderReceiver.kt:72 的 setExactAndAllowWhileIdle。
- 根因：POST_NOTIFICATIONS 已授权，但 SCHEDULE_EXACT_ALARM 未授权；未处理的 SecurityException 从协程抛出导致进程退出。
- 修复：检查 canScheduleExactAlarms，并对授权检查后的权限撤销竞态捕获 SecurityException；无法精确调度时降级 setAndAllowWhileIdle。注册失败记录错误，不让可选提醒任务终止主应用。
- 同步修正：保存已注册闹钟身份以取消失效安排、恢复后重算、广播使用 goAsync、凌晨重新安排、通知前重新核对课程日期与隐藏状态。

### Git 显式清空代理后仍连接 127.0.0.1

- 症状：git -c http.proxy= fetch 仍报连接本地代理失败。
- 原因：进程还继承了 HTTP_PROXY / HTTPS_PROXY / ALL_PROXY 环境变量。
- 解法：仅在当前发布进程清空上述变量，并将 github.com、api.github.com 加入 NO_PROXY；本次 fetch 已验证可用，无需重建 API commit。
- 历史 API 推送造成的本地 / 远端 commit SHA 分叉，先确认对应 tree 完全相同，再将本次新提交 rebase 到 origin/main；不使用 force push，不删除未提交工作。
