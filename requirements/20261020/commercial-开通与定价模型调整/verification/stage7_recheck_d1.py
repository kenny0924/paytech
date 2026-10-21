"""阶段 7 缺陷 D1 修复后复验（commercial capability-20261021b，修复 9ed4c39c）：价格行须锚定已开通的支付方式开通项。
1) DEMO2-CL-101 新建 v2：基于标准价 v2 生成不再出现只带存卡费的 USD 行；去掉境外卡行后补齐只追加 AED 境外卡；调价破标、签署、排期生效（v1 保持原状作修复前证据）。
2) DEMO2-CL-901（反例合同）开通 TOKEN_STORAGE 后，新版本含「USD 只带存卡费」行：保存返回 relevanceWarnings，排期被拒。
3) DEMO2-CL-101 v1（修复前生成）读模型按当前规则返回两条 USD 行的 relevanceWarnings。
复用 stage7_acceptance.py 的辅助函数；不打印 Token、口令。用法：python3 -I stage7_recheck_d1.py
"""
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("stage7", HERE / "stage7_acceptance.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
m.OUT = m.MAIN / f"local-environment/e2e/runtime/commercial-capability-stage7-recheck-d1-{m.STAMP}.json"

BASE = m.BASE
PLAN_101, V1_101, AG_101 = "238411010594115584", "238411010732527616", "238411008014618624"
PLAN_901, AG_901 = "238411684908175360", "238411684283224064"
STD_PLAN = "238410803311611904"


def token_only_usd(code):
    return m.client_price("USD", code, 1, [m.fixed("TOKEN_STORED_FIXED", "TOKEN_STORED", "0.15", "USD")])


def recheck_101(admin, checker, std_v2):
    rows = m.versions(admin, PLAN_101)
    if any(r["versionNo"] == "2" for r in rows):
        m.skipped("AC-05", "DEMO2-CL-101 v2", "已存在，跳过写入")
        return None
    change = m.plan_change(admin, AG_101, PLAN_101)
    v = m.must(admin, "POST", f"{BASE}/commercial-terms/plans/{PLAN_101}/versions",
               {"expectedChangeVersion": change, "sourceTemplateVersionId": std_v2}, "DEMO2-CL-101 基于标准价 v2 生成 v2")
    m.record["ids"]["version101v2"] = v["versionId"]
    ws = m.workspace(admin, v["versionId"])
    m.check("AC-05", "修复后：只收 Visa 借记 AED + 存卡，基于标准价生成只有 AED 三行（无只带存卡费的 USD 行）",
            m.price_rows(ws) == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_STANDARD"), ("AED", "CARD_VISA_DEBIT")], m.price_rows(ws))
    debit = next(p for p in ws["prices"] if p["pricingProfileCode"] == "CARD_VISA_DEBIT")
    m.check("AC-05", "修复后：AED Visa 借记行仍保留 TOKEN_STORED 组件（存卡已开通）",
            any(c["triggerCode"] == "TOKEN_STORED" for c in debit["components"]), [c["componentCode"] for c in debit["components"]])

    prices = []
    for p in ws["prices"]:
        if p["pricingProfileCode"] == "CARD_INTERNATIONAL":
            continue
        w = m.write_price(p)
        if p["pricingProfileCode"] == "CARD_VISA_DEBIT":
            for c in w["components"]:
                if c["componentCode"] in ("CAPTURE_PCT", "SALE_PCT"):
                    c["rate"] = "0.0150"
                if c["componentCode"] == "SALE_FIXED":
                    c["fixedAmount"] = "0.80"
        prices.append(w)
    v = m.must(admin, "PUT", f"{BASE}/commercial-terms/versions/{v['versionId']}/prices",
               {"expectedChangeVersion": ws["version"]["changeVersion"], "prices": prices}, "DEMO2-CL-101 v2 调价（破标）")
    m.check("AC-06", "修复后：相关价格保存无提示", v.get("relevanceWarnings") == [], v.get("relevanceWarnings"))
    v = m.must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/fill-from-template",
               {"expectedChangeVersion": v["changeVersion"], "templateVersionId": std_v2}, "DEMO2-CL-101 v2 从标准价 v2 补齐")
    ws = m.workspace(admin, v["versionId"])
    m.check("S5", "修复后：补齐只追加 AED 境外卡行（3 行，无 USD）",
            m.price_rows(ws) == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_STANDARD"), ("AED", "CARD_VISA_DEBIT")], m.price_rows(ws))
    m.check("AC-07", "v2 破标数 3（相对标准价 v2：Visa 借记 AED 1.70% → 1.50%、SALE_FIXED 1.00 → 0.80）",
            ws["version"]["belowStandardCount"] == 3, ws["version"]["belowStandardCount"])
    v = m.submit_approve(admin, checker, {"versionId": v["versionId"], "changeVersion": ws["version"]["changeVersion"]}, "DEMO2-CL-101 v2")
    v = m.must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance",
               m.sign_body(v, m.CLIENTS["DEMO2-CL-101"][1], "PA-DEMO2-CL-101-V2"), "DEMO2-CL-101 v2 登记客户签署")
    m.schedule(admin, v, "DEMO2-CL-101 v2")
    return v["versionId"]


def recheck_901(admin, checker):
    ag = m.agreement(admin, AG_901)
    if not any(c["capabilityCode"] == "TOKEN_STORAGE" for c in ag["capabilities"]):
        m.add_capability(admin, AG_901, "TOKEN_STORAGE", {}, label="DEMO2-CL-901 开通 TOKEN_STORAGE")
    if any(r["versionNo"] == "4" for r in m.versions(admin, PLAN_901)):
        m.skipped("AC-06", "DEMO2-CL-901 v4", "已存在，跳过写入")
        return
    aed = m.client_price("AED", "CARD_STANDARD", 1, m.acquiring("AED", "0.0260", "1.00", "3.00", "55.00", "0.50"))
    v = m.blank_version(admin, PLAN_901, AG_901, [aed, token_only_usd("CARD_STANDARD")], "DEMO2-CL-901-ACQ v4 存卡费锚定")
    warnings = [(w["currency"], w["pricingProfileCode"], w["reasonCode"]) for w in v.get("relevanceWarnings") or []]
    m.check("AC-06", "修复后：保存含「USD 只带存卡费」行（CARD 只开 AED，存卡已开通）返回 CURRENCY_NOT_ENABLED 提示且保存成功",
            warnings == [("USD", "CARD_STANDARD", "CURRENCY_NOT_ENABLED")], v.get("relevanceWarnings"))
    v = m.to_signed(admin, checker, v, "DEMO2-CL-901", "DEMO2-CL-901-ACQ v4 存卡费锚定", "V4")
    m.expect_reject("AC-06", "修复后：含该行的版本排期（激活校验）被拒，detail 含 USD/CARD_STANDARD", admin, "POST",
                    f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                    {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": m.now_iso(m.SCHEDULE_LEAD)}, needle="USD")


def recheck_v1_warnings(admin):
    ws = m.workspace(admin, V1_101)
    warnings = sorted((w["currency"], w["pricingProfileCode"], w["reasonCode"]) for w in ws["version"].get("relevanceWarnings") or [])
    m.check("AC-06", "修复后：DEMO2-CL-101 v1（修复前生成）读模型对两条 USD 存卡费行返回 CURRENCY_NOT_ENABLED",
            warnings == [("USD", "CARD_STANDARD", "CURRENCY_NOT_ENABLED"), ("USD", "CARD_VISA_DEBIT", "CURRENCY_NOT_ENABLED")],
            ws["version"].get("relevanceWarnings"))
    return ws["version"]["status"]


def main():
    s = m.e2e_http.secrets()
    admin = m.login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])["accessToken"]
    try:
        checker = m.create_checker(admin)
        std_v2 = next(t for t in m.templates(admin) if t["planId"] == STD_PLAN)["activeVersionId"]
        recheck_v1_warnings(admin)
        v2 = None
        try:
            v2 = recheck_101(admin, checker, std_v2)
        except m.Stop:
            m.check("AC-05", "DEMO2-CL-101 v2 复验", False, m.record["notes"][-1])
        try:
            recheck_901(admin, checker)
        except m.Stop:
            m.check("AC-06", "DEMO2-CL-901 v4 复验", False, m.record["notes"][-1])
        activated = m.activate_pending(admin)
        if v2:
            ws = m.workspace(admin, v2)
            m.check("AC-08", "DEMO2-CL-101 v2 签署后排期 → 到点 ACTIVE，无相关性提示，来源为最新模板（无「标准价已更新」）",
                    activated.get(v2) == "ACTIVE" and ws["version"]["relevanceWarnings"] == [] and ws["version"]["templateUpdate"] is None
                    and ws["version"]["sourceTemplateVersionId"] == std_v2,
                    {k: ws["version"].get(k) for k in ("status", "relevanceWarnings", "templateUpdate", "sourceTemplateVersionId")})
            v1 = next(r for r in m.versions(admin, PLAN_101) if r["versionNo"] == "1")
            m.check("AC-13", "DEMO2-CL-101 v1 保留为修复前证据（被 v2 取代）", v1["status"] in ("SUPERSEDED", "EXPIRED"), v1["status"])
    except m.Stop:
        print("[中止] 前置步骤失败")
    finally:
        try:
            m.disable_checker(admin)
        except m.Stop:
            m.record["notes"].append({"warning": "合成审批人停用失败"})
        checks = m.record["checks"]
        m.record["summary"] = {"passed": sum(c["ok"] is True for c in checks), "failed": sum(c["ok"] is False for c in checks),
                               "skipped": sum(c["ok"] is None for c in checks)}
        m.save()
        print(m.record["summary"], m.OUT)


if __name__ == "__main__":
    main()
