#!/usr/bin/env python3
"""创建 ClassSchedule v0.8.3 Release 并上传 APK（github.com 不通时走 API）。"""
import json
import subprocess
import sys
import time
import urllib.request

token = subprocess.run(
    ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
    capture_output=True, text=True).stdout
token = [l for l in token.splitlines() if l.startswith("password=")][0].split("=", 1)[1]
HDR = {"Authorization": f"Bearer {token}", "User-Agent": "cs-release"}
API = "https://api.github.com/repos/starryecho-T/ClassSchedule"

NOTES = """## 修复内容

**修复「运行一段时间后弹出崩溃提示」的问题**（MIUI 的“由于自身原因造成异常”）：

1. **更新弹窗崩溃**：启动时静默检查新版本，若网络较慢、返回时您已退出应用，会对已关闭的界面弹窗导致崩溃。现已在弹窗前检查界面状态，并随界面关闭自动取消后台检查。
2. **课表数据异常导致崩溃**：个别课程的节次数据超出作息表范围时，桌面小组件每日刷新 / 上课提醒会触发崩溃。现改为自动跳过异常数据，不再崩溃。
3. **全局崩溃兜底**：如仍有未预料的异常，现在会静默记录到应用内日志（`files/crash/`），不再弹出系统崩溃提示打扰使用。

全量单元测试 113 项通过。

> 注：本版本与之前版本使用相同的签名，可直接覆盖安装。
"""


def api(method, path, payload=None, headers=None, data=None):
    req = urllib.request.Request(
        API + path, method=method,
        data=json.dumps(payload).encode() if payload else data,
        headers={**HDR, **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as resp:
        body = resp.read()
        return resp.status, (json.loads(body) if body and body[:1] == b"{" else {})


# 1. 创建 Release（draft=False, prerelease=False）
status, rel = api("POST", "/releases", {
    "tag_name": "v0.8.3",
    "name": "ClassSchedule v0.8.3",
    "body": NOTES,
    "draft": False,
    "prerelease": False,
})
print("release:", status, rel.get("html_url") or rel.get("message", ""))
if status not in (201,):
    sys.exit(1)
upload_url = rel["upload_url"].split("{")[0]  # 去掉模板参数

# 2. 上传 APK 资产
apk_path = "D:/github/ClassSchedule/ClassSchedule-0.8.3.apk"
with open(apk_path, "rb") as f:
    apk_data = f.read()
status, asset = api("POST", upload_url + "?name=ClassSchedule-0.8.3.apk", data=apk_data,
                    headers={"Content-Type": "application/vnd.android.package-archive",
                             "Content-Length": str(len(apk_data))})
print("asset:", status, asset.get("browser_download_url") or asset.get("message", ""))
