#!/usr/bin/env python3
"""诊断 token 权限与 Release API 404 原因。"""
import json
import subprocess
import urllib.request

token = subprocess.run(
    ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
    capture_output=True, text=True).stdout
token = [l for l in token.splitlines() if l.startswith("password=")][0].split("=", 1)[1]
HDR = {"Authorization": f"Bearer {token}", "User-Agent": "cs-release"}

# 1. token 身份与 scope（响应头 X-OAuth-Scopes）
req = urllib.request.Request("https://api.github.com/user", headers=HDR)
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        print("user:", json.loads(r.read()).get("login"), "| scopes:", r.headers.get("X-OAuth-Scopes"))
except urllib.error.HTTPError as e:
    print("user ERR", e.code, e.headers.get("X-OAuth-Scopes"))

# 2. 仓库权限
req = urllib.request.Request("https://api.github.com/repos/starryecho-T/ClassSchedule", headers=HDR)
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        d = json.loads(r.read())
        print("repo perms:", d.get("permissions"), "| can_admin:", d.get("permissions", {}).get("admin"))
except urllib.error.HTTPError as e:
    print("repo ERR", e.code, e.read().decode()[:150])

# 3. 列出已有 releases
req = urllib.request.Request("https://api.github.com/repos/starryecho-T/ClassSchedule/releases?per_page=5", headers=HDR)
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        for rel in json.loads(r.read()):
            print("release:", rel["tag_name"], rel["id"])
except urllib.error.HTTPError as e:
    print("releases ERR", e.code, e.read().decode()[:150])
