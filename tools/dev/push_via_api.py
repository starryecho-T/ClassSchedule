#!/usr/bin/env python3
"""通过 GitHub git data API 推送本地提交（当 github.com:443 不通但 api.github.com 可达时）。

用法：push_via_api.py <repo_owner/repo> <base_sha> <new_sha>
从本地 git 读取 new_sha 的提交信息和 diff 文件，在远端逐 blob 上传并建 commit。
"""
import base64
import json
import subprocess
import sys
import urllib.request

REPO = sys.argv[1]
BASE = sys.argv[2]
NEW = sys.argv[3]
API = f"https://api.github.com/repos/{REPO}"

def api(method, path, payload=None):
    token = subprocess.run(
        ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True).stdout
    token = [l for l in token.splitlines() if l.startswith("password=")][0].split("=", 1)[1]
    req = urllib.request.Request(
        API + path, method=method,
        data=json.dumps(payload).encode() if payload else None,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "push-via-api",
        })
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = resp.read()
        return json.loads(body) if body else {}

def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout

# 1. 提交信息（第一行做 message，全文做完整信息）
full = git("log", "-1", "--format=%B", NEW).strip()

# 2. 变更文件列表（status + 路径）
# BASE 可能是远端才有的 SHA（本地不可 diff）；此时用 NEW 的本地父提交做 diff，
# 因为推送语义是“把 NEW 相对其父的变化应用到远端 BASE 上”。
import subprocess as _sp
def _diff_base():
    r = _sp.run(["git", "cat-file", "-e", f"{BASE}^{{commit}}"], capture_output=True)
    return BASE if r.returncode == 0 else git("rev-parse", f"{NEW}^").strip()
diff = git("diff", "--name-status", "-z", _diff_base(), NEW)
items = []
parts = diff.split("\0")
i = 0
while i < len(parts):
    status = parts[i]
    if not status:
        break
    if status.startswith("R"):
        old, new = parts[i + 1], parts[i + 2]; i += 3
        items.append(("D", old)); items.append(("A", new))
    else:
        path = parts[i + 1]; i += 2
        items.append((status[0], path))

# 3. 上传 blob（新增/修改），构造 tree 条目
tree_items = []
for status, path in items:
    if status in ("A", "M"):
        content = git("show", f"{NEW}:{path}")
        blob = api("POST", "/git/blobs", {"content": content, "encoding": "utf-8"})
        tree_items.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    elif status == "D":
        tree_items.append({"path": path, "mode": "100644", "type": "blob", "sha": None})

# 4. 建树（基于远端 base 的树）
tree = api("POST", "/git/trees", {"base_tree": f"{BASE}", "tree": tree_items})

# 5. 建 commit
commit = api("POST", "/git/commits", {
    "message": full,
    "tree": tree["sha"],
    "parents": [BASE],
})

# 6. 更新 main 引用
ref = api("PATCH", "/git/refs/heads/main", {"sha": commit["sha"], "force": False})
print(f"PUSHED: main -> {commit['sha']}")
