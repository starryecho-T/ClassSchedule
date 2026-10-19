#!/usr/bin/env python3
"""查询 ClassSchedule 历史 Release 资产构成，确认既有发布方式。"""
import json
import subprocess
import sys
import urllib.request

token = subprocess.run(
    ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
    capture_output=True, text=True).stdout
token = [l for l in token.splitlines() if l.startswith("password=")][0].split("=", 1)[1]
HDR = {"Authorization": f"Bearer {token}", "User-Agent": "cs-release"}


def get(path):
    req = urllib.request.Request(f"https://api.github.com/repos/starryecho-T/ClassSchedule/{path}", headers=HDR)
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


for path in ["releases/tags/v0.8.2", "releases/latest"]:
    try:
        d = get(path)
        assets = [(a["name"], a["size"]) for a in d.get("assets", [])]
        print(f"[{path}] tag={d.get('tag_name')} name={d.get('name')}")
        print("  assets:", assets if assets else "(无附件)")
    except Exception as e:
        print(f"[{path}] ERROR {e}")
