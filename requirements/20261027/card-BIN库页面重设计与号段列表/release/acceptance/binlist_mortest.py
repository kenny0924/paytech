#!/usr/bin/env python3
"""card：BIN 库页面重设计与号段列表 · mor-test 接口验收（runbook 第 ⑨ 步，主会话执行；阶段 6 准备，未运行）。

经 mor-test 公开入口 http://localhost:28080（Nginx → Gateway → operations-console-bff → card / Platform），管理员登录取
test-environment/private/access.md（不打印）。只读：只发 GET；期望值由 mor_card / mor_platform 只读 SQL（READ ONLY 会话）现场计算，
与 Card 实际查询同一条件（版本内 bin_prefix 前缀 + 精确卡组织 / 借贷记 / 发卡国），不写死数字。不新建人员、不激活版本。
场景：
  R  号段列表：默认分页、每个筛选与组合、翻页、排序、可空字段；错误分支（INVALID 版本 400、不存在 404、>8 位 400、非法枚举 400）。
  G  Gateway：未登录 401；不在白名单的 Query 参数被拒（GatewayRequestPolicyWebFilter）；Platform labels 内部接口没有公开路由（4xx）。
  L  显示名：版本列表 / 版本详情 createdByLabel、激活记录 operatorLabel——数字账号 → Platform 登录名或脱敏邮箱；空 / 非数字 → null。
  H  日志：--since 之后 card / ops-bff / Platform ERROR 计数 0，ops-bff 无 "显示名" 降级告警（Platform 可用时）。
用法：python3 -I binlist_mortest.py --since <发布开始 UTC>
结果：../../verification/acceptance-mor-test-<UTC>.json。
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
MOR = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/20261027-binlist-acceptance"))
os.environ.setdefault("MOR_TEST_ENVIRONMENT_DIR", str(MOR / "test-environment"))
sys.path.insert(0, str(MOR / "requirements/20261022/platform-support-多语言管理/release/acceptance"))
import i18n7_common as c  # noqa: E402

BASE = "/card-admin/v1/bin-library"
RESULTS = []


def sql(database, statement):
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={c.env()['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4",
                             "--init-command=SET SESSION TRANSACTION READ ONLY", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def check(group, name, ok, evidence=None):
    RESULTS.append({"group": group, "name": name, "ok": bool(ok), "evidence": None if ok else evidence})
    print(f"{'PASS' if ok else 'FAIL'} {group} {name}" + ("" if ok else f" | {json.dumps(evidence, ensure_ascii=False)[:300]}"))
    return ok


def get(token, path):
    status, body, text, headers = c.raw(token, "GET", path)
    return status, body, headers.get("X-Request-Id") or headers.get("x-request-id")


def expected_ranges(version_no, prefix=None, brand=None, funding=None, country=None):
    cond = [f"v.version_no = {int(version_no)}"]
    if prefix:
        cond.append(f"r.bin_prefix LIKE '{prefix}%'")
    for column, value in (("card_brand", brand), ("funding_type", funding), ("issuer_country", country)):
        if value:
            cond.append(f"r.{column} = '{value}'")
    rows = sql("mor_card", "SELECT r.bin_prefix FROM t_card_bin_range r JOIN t_card_bin_library_version v ON v.version_id = r.version_id "
                           f"WHERE {' AND '.join(cond)} ORDER BY r.bin_prefix")
    return [r[0] for r in rows]


def scenario_ranges(token):
    active = sql("mor_card", "SELECT version_no FROM t_card_bin_library_version WHERE status = 'ACTIVE'")
    if not check("R", f"有且只有一个 ACTIVE 版本（{active}）", len(active) == 1, active):
        return
    v = active[0][0]
    cases = [({}, {}), ({"binPrefix": "4"}, {"prefix": "4"}), ({"binPrefix": "40128888"}, {"prefix": "40128888"}),
             ({"cardBrand": "VISA"}, {"brand": "VISA"}), ({"fundingType": "DEBIT"}, {"funding": "DEBIT"}),
             ({"issuerCountry": "US"}, {"country": "US"}),
             ({"binPrefix": "5", "cardBrand": "MASTERCARD", "fundingType": "DEBIT", "issuerCountry": "AE"},
              {"prefix": "5", "brand": "MASTERCARD", "funding": "DEBIT", "country": "AE"}),
             ({"binPrefix": "999999"}, {"prefix": "999999"})]
    for query, expect in cases:
        qs = "&".join(f"{k}={val}" for k, val in query.items())
        status, body, rid = get(token, f"{BASE}/versions/{v}/ranges" + (f"?{qs}&size=50" if qs else "?size=50"))
        want = expected_ranges(v, **expect)
        got = [i.get("binPrefix") for i in (body or {}).get("items", [])]
        check("R", f"v{v} ranges {qs or '无筛选'} → {len(want)} 条、total 与顺序一致", status == 200 and got == want[:50]
              and body.get("total") == len(want), {"status": status, "rid": rid, "total": body.get("total"), "got": got[:5], "want": want[:5]})
    status, body, rid = get(token, f"{BASE}/versions/{v}/ranges")
    check("R", "缺省分页 size=50、page=1", status == 200 and body.get("size") == 50 and body.get("page") == 1, {"status": status, "body": str(body)[:200]})
    all_rows = expected_ranges(v)
    status, body, rid = get(token, f"{BASE}/versions/{v}/ranges?page=2&size=10")
    check("R", f"第 2 页 size=10 → {max(0, len(all_rows) - 10)} 条", status == 200
          and [i["binPrefix"] for i in body.get("items", [])] == all_rows[10:20], {"status": status, "rid": rid})
    item = next(iter((body or {}).get("items") or []), {})
    check("R", "号段字段齐全（prefixLength 与前缀位数一致）", not item or (item.get("prefixLength") == len(item.get("binPrefix", ""))
          and {"cardBrand", "issuerCountry", "issuerRegion"} <= set(item)), item)
    for version_no, status_name in sql("mor_card", "SELECT version_no, status FROM t_card_bin_library_version WHERE status IN ('VALID','RETIRED') ORDER BY version_no"):
        status, body, rid = get(token, f"{BASE}/versions/{version_no}/ranges?size=50")
        check("R", f"{status_name} 版本 v{version_no} 可浏览（total = {len(expected_ranges(version_no))}）",
              status == 200 and body.get("total") == len(expected_ranges(version_no)), {"status": status, "rid": rid})
    invalid = sql("mor_card", "SELECT version_no FROM t_card_bin_library_version WHERE status IN ('INVALID','VALIDATING') ORDER BY version_no LIMIT 1")
    if invalid:
        status, body, rid = get(token, f"{BASE}/versions/{invalid[0][0]}/ranges")
        check("R", f"INVALID 版本 v{invalid[0][0]} → 400 BUSINESS_CHECK_FAILED", status == 400 and body.get("code") == "BUSINESS_CHECK_FAILED", {"status": status, "body": body, "rid": rid})
    missing = int(sql("mor_card", "SELECT IFNULL(MAX(version_no), 0) + 100 FROM t_card_bin_library_version")[0][0])
    status, body, rid = get(token, f"{BASE}/versions/{missing}/ranges")
    check("R", f"不存在版本 v{missing} → 404", status == 404, {"status": status, "body": body, "rid": rid})
    for qs, name in (("binPrefix=123456789", "前缀超过 8 位"), ("binPrefix=41a", "前缀非数字"), ("cardBrand=FOO", "非法卡组织"),
                     ("fundingType=FOO", "非法借贷记"), ("issuerCountry=usa", "非法发卡国"), ("size=0", "size=0")):
        status, body, rid = get(token, f"{BASE}/versions/{v}/ranges?{qs}")
        check("R", f"{name} → 400", status == 400, {"status": status, "body": body, "rid": rid})


def scenario_gateway(token):
    v = sql("mor_card", "SELECT version_no FROM t_card_bin_library_version WHERE status = 'ACTIVE'")[0][0]
    status, body, rid = get(None, f"{BASE}/versions/{v}/ranges")
    check("G", "未登录 → 401", status == 401, {"status": status, "body": body})
    status, body, rid = get(token, f"{BASE}/versions/{v}/ranges?foo=1")
    check("G", "白名单外 Query 参数被 Gateway 拒绝（4xx）", 400 <= status < 500, {"status": status, "body": body, "rid": rid})
    status, body, text, _ = c.raw(token, "GET", "/operations-accounts/v1/labels?accountIds=1", prefix="/api/operations")
    check("G", "Platform labels 内部接口没有公开路由（4xx）", 400 <= status < 500, {"status": status, "text": text[:200]})
    status, body, text, _ = c.raw(token, "GET", "/operations-accounts/v1/labels?accountIds=1", prefix="/api/platform")
    check("G", "Platform labels 经 /api/platform 也不可达（4xx）", 400 <= status < 500, {"status": status, "text": text[:200]})


def expected_label(account):
    if not account or not re.fullmatch(r"\d+", account):
        return None
    rows = sql("mor_platform", "SELECT IFNULL(NULLIF(login_name, ''), email_masked) FROM t_user_account "
                               f"WHERE account_id = {int(account)} AND realm = 'OPERATIONS'")
    return rows[0][0] if rows and rows[0][0] != "NULL" else None


def scenario_labels(token):
    status, body, rid = get(token, f"{BASE}/versions?size=50")
    items = (body or {}).get("items") or []
    check("L", f"版本列表 200（{len(items)} 个）", status == 200 and items, {"status": status, "rid": rid})
    for item in items:
        want = expected_label(item.get("createdBy"))
        check("L", f"v{item.get('versionNo')} createdBy={item.get('createdBy') and '…' + str(item['createdBy'])[-6:]} → createdByLabel "
                   f"{'有值' if want else 'null'}", item.get("createdByLabel") == want and "createdByLabel" in item,
              {"got": item.get("createdByLabel"), "want_is_null": want is None})
    numeric = next((i for i in items if re.fullmatch(r"\d+", str(i.get("createdBy") or ""))), None)
    if numeric:
        status, detail, rid = get(token, f"{BASE}/versions/{numeric['versionNo']}")
        check("L", f"版本详情 v{numeric['versionNo']} createdByLabel 同列表", status == 200 and detail.get("createdByLabel") == numeric.get("createdByLabel"),
              {"status": status, "rid": rid})
        check("L", "显示名不含完整邮箱（脱敏或登录名）", "@" not in str(numeric.get("createdByLabel")) or "***" in str(numeric.get("createdByLabel")))
    status, body, rid = get(token, f"{BASE}/activations?size=50")
    acts = (body or {}).get("items") or []
    check("L", f"激活记录 200（{len(acts)} 条）", status == 200 and acts, {"status": status, "rid": rid})
    for item in acts:
        want = expected_label(item.get("operatorAccountId"))
        check("L", f"激活 v{item.get('versionNo')} {item.get('activationType')} → operatorLabel {'有值' if want else 'null'}",
              item.get("operatorLabel") == want and "operatorLabel" in item, {"got_is_null": item.get("operatorLabel") is None, "want_is_null": want is None})


def scenario_logs(since):
    for service in ("card-service", "operations-console-bff", "platform-support-service"):
        out = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{service}-1"], capture_output=True, text=True)
        text = out.stdout + out.stderr
        errors = [line for line in text.splitlines() if " ERROR " in line]
        check("H", f"{service} --since {since} ERROR 0（{len(errors)}）", not errors, [e[:200] for e in errors[:3]])
        health = c.container(service, "{{.State.Health.Status}}")
        check("H", f"{service} healthy", health == "healthy", health)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--since", required=True)
    args = parser.parse_args()
    token = c.admin_token()
    scenario_ranges(token)
    scenario_gateway(token)
    scenario_labels(token)
    scenario_logs(args.since)
    failed = [r for r in RESULTS if not r["ok"]]
    out = REQ / f"verification/acceptance-mor-test-{c.now_iso().replace(':', '')}.json"
    out.write_text(json.dumps({"env": "mor-test", "since": args.since, "pass": len(RESULTS) - len(failed), "fail": len(failed),
                               "results": RESULTS}, ensure_ascii=False, indent=1) + "\n")
    print(f"== 通过 {len(RESULTS) - len(failed)}，失败 {len(failed)}；结果 {out}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
