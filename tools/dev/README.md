# 开发辅助工具

本目录存放仓库开发过程中沉淀的辅助脚本，不参与应用构建。

> **协作约定：每次踩坑必须记录到 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)**
> （日期 + 症状 + 根因 + 解法）。发布流程见其末尾的 checklist。

## push_via_api.py

当本机 `github.com:443` 直连不通但 `api.github.com` 可达时，用 GitHub git data API
推送单个提交（逐 blob 上传 → 建 tree → 建 commit → 更新 main 引用）。

用法：

```bash
python tools/dev/push_via_api.py <owner/repo> <base_sha> <new_sha>
```

- `<base_sha>`：远端 main 当前 HEAD（本地不存在该对象时脚本自动改用 `<new_sha>` 的本地父提交做 diff）
- `<new_sha>`：本地待推送提交
- 凭据从 `git credential fill` 读取，不落盘

限制：
- 一次只推一个提交（多提交逐个调用）
- API 推送的 commit SHA 与本地必然不同（tree 重建），网络恢复后应尽快
  `git fetch && git reset --hard origin/main` 对齐，避免历史分叉
- 不支持二进制 diff 之外的重命名检测简化处理（R 状态拆成删除+新增）

## test-import-capture.cjs（上级目录）

教务页抓取脚本的 Node mock 回归测试：`node tools/test-import-capture.cjs --mock`。

## 工作流文件备忘

`.github/workflows/release.yml` 尚未入库：当前 git 凭据的 OAuth token 缺
`workflow` scope，无法推送工作流文件。可通过 GitHub 网页端创建，内容参考
仓库早期备份（打 tag 自动构建 APK 并挂到 Release）。
