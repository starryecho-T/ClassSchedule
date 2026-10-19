#!/usr/bin/env python3
"""查看 v0.8.3 release 详情并补传 APK 资产。"""
import json
import subprocess
import sys
import urllib.request

token = subprocess.run(
    ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
    capture_output=True, text=True).stdout
token = [l for l in token.splitlines() if l.startswith("password=")][0].split("=", 1)[1]
HDR = {"Authorization": f"Bearer {token}", "User-Agent": "cs-release"}
API = "https://api.github.com/repos/starryecho-T/ClassSchedule"


def get(path):
    req = urllib.request.Request(API + path, headers=HDR)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


rel = get("/releases/tags/v0.8.3")
print("id:", rel["id"], "| draft:", rel["draft"], "| assets:", [(a["name"], a["size"]) for a in rel["assets"]])
print("url:", rel["html_url"])

if any(a["name"] == "ClassSchedule-0.8.3.apk" for a in rel["assets"]):
    print("APK 已存在，无需上传")
    sys.exit(0)

upload_url = rel["upload_url"].split("{")[0]
apk_path = "D:/github/ClassSchedule/ClassSchedule-0.8.3.apk"
with open(apk_path, "rb") as f:
    data = f.read()
req = urllib.request.Request(
    upload_url + "?name=ClassSchedule-0.8.3.apk", method="POST", data=data,
    headers={**HDR, "Content-Type": "application/vnd.android.package-archive",
             "Content-Length": str(len(data))})
with urllib.request.urlopen(req, timeout=300) as r:
    asset = json.loads(r.read())
    print("uploaded:", r.status, asset.get("browser_download_url"))
