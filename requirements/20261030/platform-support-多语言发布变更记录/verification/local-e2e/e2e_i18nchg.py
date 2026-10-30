#!/usr/bin/env python3
"""阶段 5 本机 e2e 验收：多语言发布变更记录（经本机运营入口 https://127.0.0.1:18444）。

AC-01/02 预检不读文件、耗时；AC-03 摘要与独立基准一致（改译文、改了又改回）；AC-04/05 发布写变化行；AC-06 差异；
AC-07 补录后可用。独立基准由 expected_summary.py 按现行算法从库与线上语言包计算（只读）。
运行：python3 e2e_i18nchg.py <expected_summary.py> <backfill_release_changes.py> <输出 JSON>
环境：MYSQL_PWD（目标 MySQL root，传给 docker exec，不打印）。改动的译文在结束前还原。
目标默认本机 e2e；mor-test（阶段 7）设 E2E_TARGET=test 与 I18NCHG_CONTAINER=mor-test-mysql-1、I18NCHG_DB=mor_platform、
I18NCHG_STORAGE=https://127.0.0.1:29400、I18NCHG_VAULT=mor-test-vault-service-1。I18NCHG_WRITE=0 跳过改译文与发布（只读验收）；
I18NCHG_PUBLISH 指定相同内容发布的应用（默认 client-console，none 不发布）。
"""
import json
import os
import statistics
import subprocess
import sys
import time

sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

EXPECTED, BACKFILL, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
CONTAINER = os.environ.get("I18NCHG_CONTAINER", "mor-local-mysql-1")
DB = os.environ.get("I18NCHG_DB", "mor_platform_v2e2e")
STORAGE = os.environ.get("I18NCHG_STORAGE", "https://127.0.0.1:19400")
VAULT = os.environ.get("I18NCHG_VAULT", "mor-local-vault-service-1")
WRITE = os.environ.get("I18NCHG_WRITE", "1") == "1"
PUBLISH = os.environ.get("I18NCHG_PUBLISH", "client-console")
BASE = "/localization-admin/v1/applications/"
OPS, CLIENT = "operations-console", "client-console"
result = {"checks": []}


def check(name, ok, evidence):
    result["checks"].append({"name": name, "ok": bool(ok), "evidence": evidence})
    print(("PASS" if ok else "FAIL"), name, "|", evidence)


def reveal_count(since):
    out = subprocess.run(["docker", "logs", "--since", since, VAULT], capture_output=True, text=True)
    return sum(1 for line in (out.stdout + out.stderr).splitlines() if '"REVEAL"' in line and "FILE_STORAGE_ACCESS" in line)


def timed(ops, path, times=5):
    """返回最后一次成功的响应与每次耗时；失败（如 504）记为 "HTTP <code> @<ms>" 并计入耗时比较时视为不达标。"""
    durations, body = [], None
    for _ in range(times):
        start = time.perf_counter()
        try:
            body = ops.get(path)
            durations.append(round((time.perf_counter() - start) * 1000))
        except e2e_http.Failure as failure:
            durations.append("HTTP %s @%d" % (failure.status, round((time.perf_counter() - start) * 1000)))
    return body, durations


def within(durations, limit, median=True):
    if any(isinstance(d, str) for d in durations):
        return False
    return (statistics.median(durations) if median else max(durations)) <= limit


def expected():
    out = subprocess.run([sys.executable, EXPECTED, BACKFILL, DB, STORAGE, CONTAINER],
                         capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def summary_of(precheck):
    s = precheck["changeSummary"]
    return {k: s[k] for k in ("baseReleaseNo", "addedKeyCount", "addedKeys", "deprecatedKeyCount", "deprecatedKeys",
                              "sourceChangedKeyCount", "sourceChangedKeys")} | {
        "translationChanges": [{"locale": t["locale"], "changedCount": t["changedCount"], "sampleKeys": t["sampleKeys"]}
                               for t in s["translationChanges"]]}


secrets = e2e_http.secrets()
ops = e2e_http.Operations(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])
since = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

# 1) 预检：耗时、与独立基准一致
base = expected()
for app in (OPS, CLIENT):
    body, durations = timed(ops, BASE + app + "/release-precheck")
    result[app + ".precheck.ms"] = durations
    check(app + " 预检耗时中位数 ≤ 2000 ms (AC-02)", within(durations, 2000), durations)
    check(app + " 预检摘要与独立基准一致 (AC-03)", body is not None and summary_of(body) == base[app], body and summary_of(body))

# 2) 差异：默认上一版、任意基准；耗时
for path in (OPS + "/releases/19/diff", OPS + "/releases/19/diff?baseReleaseNo=1", CLIENT + "/releases/5/diff"):
    body, durations = timed(ops, BASE + path, 3)
    result[path + ".ms"] = durations
    check("差异 " + path + " ≤ 1000 ms (AC-06)", within(durations, 1000, median=False),
          {"ms": durations} | ({"added": len(body["added"]), "changed": len(body["changed"]), "removed": len(body["removed"]),
                               "base": body["baseReleaseNo"]} if body else {}))

# 3) 改译文：A 改为新文字，B 改了又改回；预检与独立基准一致；之后还原 A（I18NCHG_WRITE=0 跳过）
def save(entry, text, version):
    saved = ops.send("POST", BASE + OPS + "/translations/batch",
                     {"locale": "zh-CN", "translations": [{"keyCode": entry["keyCode"], "text": text, "expectedVersion": version}]})
    return saved["translations"][0]["version"]


if WRITE:
    page = ops.get(BASE + OPS + "/translations?locale=zh-CN&page=1&size=20")
    entries = [e for e in page["items"] if e.get("text")][:2]
    a, b = entries[0], entries[1]
    va = save(a, a["text"] + " [i18nchg]", a["version"])
    vb = save(b, b["text"] + " x", b["version"])
    vb = save(b, b["text"], vb)
    try:
        after = ops.get(BASE + OPS + "/release-precheck")
        exp = expected()[OPS]
        check("改译文后预检与独立基准一致；改了又改回不计 (AC-03)", summary_of(after) == exp,
              {"api": summary_of(after)["translationChanges"], "expected": exp["translationChanges"], "changed": a["keyCode"], "reverted": b["keyCode"]})
        check("只有改动的一条计为变化", [t["changedCount"] for t in summary_of(after)["translationChanges"]] == [1], summary_of(after)["translationChanges"])
    finally:
        save(a, a["text"], va)
    restored = ops.get(BASE + OPS + "/release-precheck")
    check("还原后预检回到无变化", summary_of(restored) == expected()[OPS], summary_of(restored)["translationChanges"])

# 预检、差异、改译文期间不解密存储凭据（AC-01/06）；发布上传语言包会解密，故在发布前统计
time.sleep(2)
reveals = reveal_count(since)
result["file_storage_reveals_before_publish"] = reveals
check("预检 / 差异期间存储凭据解密 0 次 (AC-01, AC-06)", reveals == 0, reveals)

# 4) 发布 PUBLISH 应用（内容与线上相同）：变化 0 条，摘要相对线上（I18NCHG_WRITE=0 或 I18NCHG_PUBLISH=none 跳过）
if WRITE and PUBLISH != "none":
    published = ops.send("POST", BASE + PUBLISH + "/releases", {"releaseNote": "i18nchg e2e: identical content"})
    result["published"] = published
    release_no = published["releaseNo"]
    diff = ops.get(BASE + PUBLISH + "/releases/%d/diff" % release_no)
    check("相同内容发布：差异为空 (AC-05)", not diff["added"] and not diff["changed"] and not diff["removed"], {"releaseNo": release_no, "base": diff["baseReleaseNo"]})

with open(OUT, "w", encoding="utf-8") as handle:
    json.dump(result, handle, ensure_ascii=False, indent=1)
print("FAILED" if any(not c["ok"] for c in result["checks"]) else "ALL PASS", "->", OUT)
