"""币种主数据阶段 7 mor-test 验收（release.md 第 7 节；块 125 激活、五个镜像替换之后执行）。

管理员经运营入口走完 XTS（ISO 4217 测试代码）的生命周期：新建草稿 → 重复新建拒绝 → 修改草稿 → 版本不符拒绝 → 启用 → 启用后修改拒绝 →
停用；核对审计、财务币种列表的状态筛选、ledger 与 billing 在一个刷新周期内回源成功（AC-08）、合成只读人员看得到列表但不能维护（AC-01）。
数据：只新增 XTS 一行与一名合成只读人员 / 角色，验收后由 stage7_cleanup.py 删除；USD 不动。
用法：python3 -I stage7_acceptance.py   → 运行记录 CCY7_STATE/run-<run>.json（0600）
"""
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ccy7_common as c  # noqa: E402

REASON = "币种主数据阶段 7 mor-test 验收（XTS 为 ISO 4217 测试代码，验收后删除）"
SNAPSHOT_WAIT_SECONDS = 75


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def save(record):
    path = c.STATE / f"run-{record['run']}.json"
    path.write_text(json.dumps(record, ensure_ascii=False, indent=1))
    os.chmod(path, 0o600)


def spec(token, currency):
    status, body = c.raw(token, "GET", c.SPECS)
    items = body.get("items") or []
    return status, next((item for item in items if item.get("currency") == currency), None), items


def mutation_ok(status, body, state, version):
    return status == 200 and body.get("objectCode") == c.TEST_CURRENCY and body.get("status") == state and body.get("version") == version


def finance_codes(token, status_filter=None):
    path = c.FINANCE_CURRENCIES + (f"?status={status_filter}" if status_filter else "")
    status, body = c.raw(token, "GET", path)
    return status, {item.get("currency"): item.get("status") for item in body.get("items") or []}


def await_snapshot_fetch(after_iso, baseline):
    """AC-08：启用后 ledger、billing 各自再完成一次 200 回源，且期间没有刷新失败 WARN。"""
    deadline = time.time() + SNAPSHOT_WAIT_SECONDS
    seen = {}
    while time.time() < deadline and len(seen) < len(c.SNAPSHOT_SERVICES):
        for service in c.SNAPSHOT_SERVICES:
            if service in seen:
                continue
            if c.platform_fetch_count(service).get("200", 0) > baseline[service]:
                seen[service] = round(time.time() - started, 1)
        time.sleep(2)
    for service in c.SNAPSHOT_SERVICES:
        c.check("AC-08", f"{service} 启用后 {SNAPSHOT_WAIT_SECONDS} 秒内完成一次成功回源（{seen.get(service)} 秒）", service in seen)
        c.check("AC-08", f"{service} 启用后没有快照刷新失败", c.refresh_failures_since(service, after_iso) == 0)


def lifecycle(admin, record):
    status, xts, items = spec(admin, c.TEST_CURRENCY)
    usd = next((item for item in items if item.get("currency") == "USD"), None)
    c.check("AC-01", f"列表返回 USD 2 位 HALF_UP ACTIVE 版本 1（{status}）", status == 200 and usd and usd.get("minorUnit") == 2
            and usd.get("roundingMode") == "HALF_UP" and usd.get("status") == "ACTIVE" and usd.get("version") == 1, usd)
    if xts is not None:
        raise SystemExit("XTS 已存在，可能是上一次运行没有清理，先执行 stage7_cleanup.py")

    status, body = c.raw(admin, "POST", c.SPECS, {"currency": c.TEST_CURRENCY, "minorUnit": 3, "roundingMode": "HALF_UP", "reason": REASON})
    c.check("AC-01", f"新建 XTS 草稿（{status}）", mutation_ok(status, body, "DRAFT", 1), body)
    record["currencySpecId"] = body.get("objectId")
    record["createdAt"] = now_iso()
    save(record)
    status, body = c.raw(admin, "POST", c.SPECS, {"currency": c.TEST_CURRENCY, "minorUnit": 2, "roundingMode": "HALF_UP", "reason": REASON})
    c.check("AC-01", f"重复新建返回 400 且提示币种（{status}）", c.rejected(status, body, 400, "币种规则已存在: XTS"), body)

    status, body = c.raw(admin, "PUT", f"{c.SPECS}/{c.TEST_CURRENCY}",
                         {"minorUnit": 2, "roundingMode": "HALF_UP", "expectedVersion": 1, "reason": REASON})
    c.check("AC-01", f"修改草稿小数位 3 → 2，版本 2（{status}）", mutation_ok(status, body, "DRAFT", 2), body)
    status, body = c.raw(admin, "PUT", f"{c.SPECS}/{c.TEST_CURRENCY}",
                         {"minorUnit": 4, "roundingMode": "HALF_UP", "expectedVersion": 1, "reason": REASON})
    c.check("AC-01", f"过期版本修改返回 409（{status}）", status == 409, body)

    status, codes = finance_codes(admin, "ACTIVE")
    c.check("AC-01", f"财务币种列表 status=ACTIVE 不含草稿 XTS（{status}）", status == 200 and "USD" in codes and c.TEST_CURRENCY not in codes, codes)
    status, codes = finance_codes(admin)
    c.check("AC-01", f"财务币种列表不筛选时含 XTS DRAFT（{status}）", status == 200 and codes.get(c.TEST_CURRENCY) == "DRAFT", codes)

    baseline = {service: c.platform_fetch_count(service).get("200", 0) for service in c.SNAPSHOT_SERVICES}
    record["fetchBaseline"] = baseline
    status, body = c.raw(admin, "PATCH", f"{c.SPECS}/{c.TEST_CURRENCY}/status", {"status": "ACTIVE", "expectedVersion": 2, "reason": REASON})
    c.check("AC-01", f"启用 XTS，版本 3（{status}）", mutation_ok(status, body, "ACTIVE", 3), body)
    activated = now_iso()
    global started
    started = time.time()
    record["activatedAt"] = activated
    save(record)

    status, body = c.raw(admin, "PUT", f"{c.SPECS}/{c.TEST_CURRENCY}",
                         {"minorUnit": 3, "roundingMode": "HALF_UP", "expectedVersion": 3, "reason": REASON})
    c.check("AC-02", f"启用后修改小数位返回 400 且提示状态（{status}）", c.rejected(status, body, 400, "ACTIVE"), body)
    status, codes = finance_codes(admin, "ACTIVE")
    c.check("AC-01", f"财务币种列表 status=ACTIVE 含 XTS（{status}）", status == 200 and codes.get(c.TEST_CURRENCY) == "ACTIVE", codes)

    await_snapshot_fetch(activated, baseline)

    status, body = c.raw(admin, "PATCH", f"{c.SPECS}/{c.TEST_CURRENCY}/status", {"status": "INACTIVE", "expectedVersion": 2, "reason": REASON})
    c.check("AC-01", f"过期版本停用返回 409（{status}）", status == 409, body)
    status, body = c.raw(admin, "PATCH", f"{c.SPECS}/{c.TEST_CURRENCY}/status", {"status": "INACTIVE", "expectedVersion": 3, "reason": REASON})
    c.check("AC-01", f"停用 XTS，版本 4（{status}）", mutation_ok(status, body, "INACTIVE", 4), body)
    status, codes = finance_codes(admin, "ACTIVE")
    c.check("AC-03", f"停用后财务币种列表 status=ACTIVE 不含 XTS（{status}）", status == 200 and c.TEST_CURRENCY not in codes, codes)


def database_checks(record):
    rows = c.sql("mor_platform", f"SELECT id, minor_unit, rounding_mode, status, version FROM t_currency_spec WHERE currency='{c.TEST_CURRENCY}'")
    c.check("AC-01", "库里 XTS 为 2 位 HALF_UP INACTIVE 版本 4", rows == [[str(record.get("currencySpecId")), "2", "HALF_UP", "INACTIVE", "4"]], rows)
    rows = c.sql("mor_platform", "SELECT minor_unit, rounding_mode, status, version FROM t_currency_spec WHERE currency='USD'")
    c.check("AC-04", "USD 未变（2 位 HALF_UP ACTIVE 版本 1）", rows == [["2", "HALF_UP", "ACTIVE", "1"]], rows)
    audit = dict(c.sql("mor_platform", "SELECT action_code, COUNT(*) FROM t_audit_event WHERE resource_type='CURRENCY_SPEC' "
                                       f"AND resource_id='{c.TEST_CURRENCY}' AND result='SUCCEEDED' GROUP BY action_code"))
    c.check("AC-01", "审计：新建 1、修改 1、启停 2（均 SUCCEEDED）", audit == {"OPERATIONS_CURRENCY_SPEC_CREATE": "1",
            "OPERATIONS_CURRENCY_SPEC_UPDATE": "1", "OPERATIONS_CURRENCY_SPEC_STATUS": "2"}, audit)
    record["auditOther"] = c.sql("mor_platform", "SELECT action_code, result, COUNT(*) FROM t_audit_event WHERE resource_type='CURRENCY_SPEC' "
                                                 "AND resource_id='CURRENCY_SPEC_CATALOG' AND result<>'SUCCEEDED' GROUP BY action_code, result")


def reader_checks(admin, record):
    reader = c.synthetic_reader(admin, record["run"], record)
    save(record)
    status, _, items = spec(reader, c.TEST_CURRENCY)
    c.check("AC-01", f"只读人员能看列表（{status}）", status == 200 and any(item.get("currency") == "USD" for item in items))
    status, body = c.raw(reader, "POST", c.SPECS, {"currency": "XTS", "minorUnit": 2, "roundingMode": "HALF_UP", "reason": REASON})
    c.check("AC-01", f"只读人员新建被拒 403（{status}）", status == 403, body)
    status, body = c.raw(reader, "PATCH", f"{c.SPECS}/{c.TEST_CURRENCY}/status", {"status": "ACTIVE", "expectedVersion": 4, "reason": REASON})
    c.check("AC-01", f"只读人员启停被拒 403（{status}）", status == 403, body)
    status, nav = c.raw(reader, "GET", "/authorization/v1/navigation")
    text = json.dumps(nav, ensure_ascii=False)
    record["readerNavigationHasCurrencyPage"] = "OPERATIONS_CURRENCY_SPECS" in text
    record["readerNavigationHasManageButton"] = "OPERATIONS_CURRENCY_SPEC_MANAGE" in text
    c.check("AC-01", "只读人员导航有币种页、没有维护按钮", status == 200 and record["readerNavigationHasCurrencyPage"]
            and not record["readerNavigationHasManageButton"], {"status": status})


def service_checks(since):
    for service in ("platform-support-service", "operations-console-bff", *c.SNAPSHOT_SERVICES):
        c.check("AC-06", f"{service} healthy 且未重启（{c.health(service)}）", c.health(service).startswith("healthy "))
        errors = c.errors_since(service, since)
        c.check("AC-06", f"{service} 验收期间 ERROR 0", not errors, errors[:3])


def main():
    started_iso = now_iso()
    record = {"run": datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S"), "startedAt": started_iso}
    save(record)
    admin = c.admin_token()
    c.install_probe()
    record["probeInstalled"] = True
    save(record)
    lifecycle(admin, record)
    database_checks(record)
    reader_checks(admin, record)
    service_checks(started_iso)
    record["results"] = c.results
    save(record)
    passed = sum(1 for _, _, ok in c.results if ok)
    print(f"\n验收 {passed}/{len(c.results)} 通过；运行记录 {c.STATE / ('run-' + record['run'] + '.json')}")
    sys.exit(0 if passed == len(c.results) else 1)


started = time.time()

if __name__ == "__main__":
    main()
