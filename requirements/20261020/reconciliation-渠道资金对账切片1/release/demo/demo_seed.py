"""对账切片 1：mor-test 对账控制台演示数据（持久保留，不在脚本内清理；移除用 demo_cleanup.py）。

复用 release/acceptance/recon7_common.py（入口、IAM、合成事实发布器、只读 SQL）与 release/scripts/xxl_reconciliation.py（手动触发）。
合成 BillingFactRecorded 只投 reconciliation.billing.hash（ReconBillingFactPublisher.java），account 收不到。

场景（经营主体 AE001、USD、演示渠道 DEMO_NI；日期以运行当天 D0 倒推）：
  渠道账单  D-7 已到账（CASH_CONFIRMED，含月费调整行 CLASSIFY CHANNEL_FEE_OTHER）
            D-4 已对平、银行少到 0.20（部分到账 + BANK_AMOUNT_MISMATCH 未结）
            D-2 匹配中（AMOUNT_MISMATCH、COST_DEVIATION、保证金扣留 CLASSIFY CHANNEL_RESERVE_HOLD 待复核）
            D-1 第 1 版 REJECTED（表头合计错）、第 2 版 VOIDED（渠道误加调整行）、第 3 版 RECONCILED（在途）
            清算账户 B（等待 0 天）一行平台无对应流水 → XXL channelLineWaitExpiry → CHANNEL_LINE_UNMATCHED
  银行对账单（末四位 6021）BS1、BS2 连续；利息贷记 BANK_INTEREST 已结案；银行手续费借记未结；一张余额不平 REJECTED；BS3 期初接不上 → BANK_STATEMENT_GAP
  平台流水  一笔 D-10 的付款无账单行 → 人工 PLATFORM_ITEM_UNSETTLED 未结；一笔当天付款正常等待
人员：Maker = 管理员（private/access.md）；Checker = IAM 新建 DEMO_RECON_CHECKER_<ts>（对账读 + 审批 + 财务读），结束后停用（--keep-checker 保留）。
记录：~/Code-MoR-backups/recon-demo/demo-<ts>.json（0600，含 Checker 登录名与口令；不打印口令 / Token）。
每个写步骤完成即记入记录文件；中途失败时按提示 --resume <记录> 从失败步骤续跑。
CLASSIFY / 对平 / 银行匹配登记的四类事件目前没有订阅方（I-08），补偿任务会留存 FAILED，属预期。

用法：python3 -I demo_seed.py [--dry-run] [--keep-checker] [--backdate-exceptions] [--resume <demo-*.json>]
  --dry-run              只打印计划并做只读 GET / SELECT，不写任何数据
  --backdate-exceptions  可选：把未结演示异常的 opened_time 按业务日期前移（直接 UPDATE mor_reconciliation，汇总页账龄段才有 D4_7 / D8_30）
环境变量：DEMO_CHANNEL_CODE（默认 DEMO_NI）。
"""
import argparse
import hashlib
import json
import os
import secrets as pysecrets
import sys
import time
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "acceptance"))
sys.path.insert(0, str(HERE.parent / "scripts"))
import recon7_common as c  # noqa: E402
import xxl_reconciliation as xxl  # noqa: E402

R = "/finance/reconciliation/v1"
CH = "/channel-admin/v1"
EXT = "/external-api-admin/v1"
CHANNEL_CODE = os.environ.get("DEMO_CHANNEL_CODE", "DEMO_NI")
ENTITY = "AE001"
CCY = "USD"
CA_A = "NI-AE001-USD"
CA_B = "NI-AE001-USD-B"
CASH_LAST4 = "6021"
PAYOUT_LAST4 = "7710"
OPENING = Decimal("10000.00")
STATE = Path(os.environ.get("RECON_DEMO_STATE", Path.home() / "Code-MoR-backups/recon-demo"))
D0 = date.today()
TAG = D0.strftime("%m%d")
STAMP = datetime.now().strftime("%Y%m%d%H%M%S")
RECORD = STATE / f"demo-{STAMP}.json"
record = {"stamp": STAMP, "seedDate": D0.isoformat(), "channelCode": CHANNEL_CODE, "done": {}}
EXPECTED_IMAGE = c.CANDIDATE_IMAGE


def d(n):
    """D0 往前 n 天。"""
    return D0 - timedelta(days=n)


def ymd(n):
    return d(n).strftime("%Y%m%d")


def money(value):
    return str(Decimal(value).quantize(Decimal("0.01")))


# ---------- 演示数据定义（金额都是字符串，便于核对） ----------

def ord_ref(seq):
    return f"NI-ORD-{TAG}-{seq:04d}"


def rfd_ref(seq):
    return f"NI-RFD-{TAG}-{seq:04d}"


# key: (类型, 清算账户, 金额, 预估渠道成本, 外部交易号类型, 外部交易号, 业务日期天数)
FACTS = {
    "P01": ("PAYMENT", "A", "120.00", "0.36", "NI_ORDER_REF", ord_ref(1), 9),
    "P02": ("PAYMENT", "A", "85.50", "0.26", "NI_ORDER_REF", ord_ref(2), 9),
    "P03": ("PAYMENT", "A", "300.00", "0.90", "NI_ORDER_REF", ord_ref(3), 8),
    "R01": ("REFUND", "A", "20.00", None, "NI_REFUND_REF", rfd_ref(1), 8),
    "P04": ("PAYMENT", "A", "49.99", "0.15", "NI_ORDER_REF", ord_ref(4), 6),
    "P05": ("PAYMENT", "A", "1250.00", "3.75", "NI_ORDER_REF", ord_ref(5), 5),
    "P06": ("PAYMENT", "A", "210.00", "0.63", "NI_ORDER_REF", ord_ref(6), 5),
    "R02": ("REFUND", "A", "49.99", None, "NI_REFUND_REF", rfd_ref(2), 5),
    "P07": ("PAYMENT", "A", "75.00", "0.23", "NI_ORDER_REF", ord_ref(7), 3),
    "P08": ("PAYMENT", "A", "560.00", "1.68", "NI_ORDER_REF", ord_ref(8), 3),
    "P09": ("PAYMENT", "A", "99.00", "0.30", "NI_ORDER_REF", ord_ref(9), 3),
    "P10": ("PAYMENT", "A", "150.00", "0.45", "NI_ORDER_REF", ord_ref(10), 2),
    "P11": ("PAYMENT", "A", "42.00", "0.13", "NI_ORDER_REF", ord_ref(11), 2),
    "R03": ("REFUND", "A", "30.00", None, "NI_REFUND_REF", rfd_ref(3), 2),
    "P12": ("PAYMENT", "A", "64.00", "0.19", "NI_ORDER_REF", ord_ref(12), 10),
    "P13": ("PAYMENT", "A", "18.00", "0.05", "NI_ORDER_REF", ord_ref(13), 0),
    "P14": ("PAYMENT", "B", "65.00", "0.20", "NI_ORDER_REF", ord_ref(14), 2),
}


def tx_line(key, amount=None, fee=None, remark=None):
    """交易行：取事实的类型、方向、交易号与业务日期；金额 / 费用可覆盖。"""
    kind, _, gross, cost, ref_type, ref, days = FACTS[key]
    direction = "CREDIT" if kind == "PAYMENT" else "DEBIT"
    return {"lineType": kind, "direction": direction, "amount": amount or gross, "feeAmount": fee or cost or "0.00",
            "referenceType": ref_type, "externalTransactionId": ref, "transactionDate": d(days).isoformat(), "remark": remark}


def adj_line(amount, days, remark, direction="DEBIT"):
    return {"lineType": "ADJUSTMENT", "direction": direction, "amount": amount, "feeAmount": "0.00", "referenceType": None,
            "externalTransactionId": None, "transactionDate": d(days).isoformat(), "remark": remark}


def batch_ref(days, suffix="A"):
    return f"NI-{ymd(days)}-AE001-{suffix}"


def channel_statements():
    """渠道账单定义：key → (清算账户, 批次号, 结算日天数, 行, 表头总额覆盖)。"""
    d1_lines = [tx_line("P10"), tx_line("P11"), tx_line("R03")]
    return {
        "D7": ("A", batch_ref(7), 7, [tx_line("P01"), tx_line("P02"), tx_line("P03", fee="0.95", remark="实际费 0.95，预估 0.90，在容差内"),
                                      tx_line("R01", remark="部分退款"), adj_line("15.00", 7, "2026 年 10 月渠道月度服务费")], None),
        "D4": ("A", batch_ref(4), 4, [tx_line("P04"), tx_line("P05"), tx_line("P06"), tx_line("R02", remark="全额退款")], None),
        "D2": ("A", batch_ref(2), 2, [tx_line("P07", amount="78.00", remark="渠道按 78.00 结算，平台记 75.00"),
                                      tx_line("P08", fee="2.40", remark="跨境卡附加费，实际费 2.40"), tx_line("P09"),
                                      adj_line("56.00", 2, "滚动保证金扣留 10%（订单 " + ord_ref(8) + "）")], None),
        "D1_REJ": ("A", batch_ref(1), 1, d1_lines, "172.00"),
        "D1_VOID": ("A", batch_ref(1), 1, d1_lines + [adj_line("4.50", 1, "渠道重复计收退款手续费（渠道已确认误发）")], None),
        "D1": ("A", batch_ref(1), 1, d1_lines, None),
        "B1": ("B", batch_ref(1, "B"), 1, [tx_line("P14"), {"lineType": "PAYMENT", "direction": "CREDIT", "amount": "230.00",
                                                            "feeAmount": "0.69", "referenceType": "NI_ORDER_REF",
                                                            "externalTransactionId": f"NI-ORD-{TAG}-B002",
                                                            "transactionDate": d(2).isoformat(),
                                                            "remark": "平台侧尚无该交易"}], None),
    }


def totals(lines):
    gross = Decimal("0")
    fee = Decimal("0")
    for line in lines:
        fee += Decimal(line["feeAmount"])
        if line["lineType"] == "FEE":
            fee += Decimal(line["amount"])
            continue
        signed = Decimal(line["amount"]) if line["direction"] == "CREDIT" else -Decimal(line["amount"])
        gross += signed
    return gross, fee, gross - fee


def bank_statements():
    """银行对账单定义：key → (编号, 期间起天数, 期间止天数, 期初, 行, 期末覆盖)。行：(交易号, 起息天数, 方向, 金额, 对方, 摘要)。"""
    bs1 = [(f"ENBD{ymd(6)}000123", 6, "CREDIT", "468.93", "NETWORK INTERNATIONAL LLC", "NI SETTLEMENT " + batch_ref(7)),
           (f"ENBD{ymd(5)}000456", 5, "CREDIT", "3.12", "EMIRATES NBD", "利息 CREDIT INTEREST")]
    bs2 = [(f"ENBD{ymd(3)}000789", 3, "CREDIT", "1455.27", "NETWORK INTERNATIONAL LLC", "NI SETTLEMENT " + batch_ref(4)),
           (f"ENBD{ymd(2)}000812", 2, "DEBIT", "25.00", "EMIRATES NBD", "银行手续费 ACCOUNT MAINTENANCE FEE")]
    bs3 = [(f"ENBD{ymd(1)}000901", 1, "CREDIT", "500.00", "ACME GENERAL TRADING LLC", "INWARD TT ACME-7781")]
    close1 = OPENING + Decimal("468.93") + Decimal("3.12")
    close2 = close1 + Decimal("1455.27") - Decimal("25.00")
    open3 = Decimal("11950.00")
    return {
        "BS1": (f"ENBD-{CASH_LAST4}-{ymd(8)}-{ymd(5)}", 8, 5, OPENING, bs1, None),
        "BS2": (f"ENBD-{CASH_LAST4}-{ymd(4)}-{ymd(2)}", 4, 2, close1, bs2, None),
        "BS_REJ": (f"ENBD-{CASH_LAST4}-{ymd(1)}-PROV", 1, 0, close2, bs3, close2 + Decimal("500.00") + Decimal("97.68")),
        "BS3": (f"ENBD-{CASH_LAST4}-{ymd(1)}-{ymd(0)}", 1, 0, open3, bs3, None),
    }


def bank_closing(opening, lines):
    total = opening
    for _, _, direction, amount, _, _ in lines:
        total += Decimal(amount) if direction == "CREDIT" else -Decimal(amount)
    return total


# ---------- 记录与通用调用 ----------

def save():
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    os.chmod(RECORD, 0o600)


def fail(message, evidence=None):
    tail = "" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False, default=str)[:600]
    print(f"\n[失败] {message}{tail}", flush=True)
    if RECORD.exists():
        print(f"续跑：python3 -I {Path(__file__)} --resume {RECORD}", flush=True)
    raise SystemExit(1)


def ok(message):
    print(f"[完成] {message}", flush=True)


def once(key, fn):
    """幂等步骤：结果记入 record['done'][key]，续跑时直接返回。"""
    if key in record["done"]:
        return record["done"][key]
    value = fn()
    record["done"][key] = value
    save()
    return value


def call(token, method, path, body=None, expect=(200, 201), what=""):
    status, resp = c.raw(token, method, path, body)
    if status not in expect:
        fail(f"{what or method + ' ' + path} 返回 HTTP {status}: {resp.get('detail') or resp.get('code')}", resp)
    return resp


def wait(predicate, what, timeout=90, step=2.0):
    value = c.wait_until(predicate, timeout, step)
    if not value:
        fail(f"等待超时（{timeout}s）：{what}")
    return value


# ---------- 预检（只读） ----------

def preflight(admin, resume):
    image = os.popen("docker inspect --format '{{.Image}}' mor-test-reconciliation-service-1 2>/dev/null").read().strip()
    print(f"  reconciliation 运行镜像 {image[:19]}…（期望 {EXPECTED_IMAGE[:19]}…）")
    if image != EXPECTED_IMAGE:
        fail("reconciliation 运行镜像不是切片 1 候选镜像；先核对 mor-test 部署")
    nav = call(admin, "GET", "/authorization/v1/navigation", what="管理员导航")
    if "OPERATIONS_FINANCE_RECON" not in json.dumps(nav):
        fail("管理员导航没有对账分组（块 123 未激活？）")
    clearing = call(admin, "GET", R + "/clearing-accounts?page=1&size=100", what="清算账户列表")["items"]
    channels = call(admin, "GET", CH + f"/channels?keyword={CHANNEL_CODE}&page=1&size=50", what="渠道列表")["items"]
    cash = call(admin, "GET", R + f"/cash-accounts?entityType=OPERATING_ENTITY&entityId={ENTITY}&currency={CCY}&page=1&size=100",
                what="银行账户列表")["items"]
    status, integration = c.raw(admin, "GET", f"{EXT}/integrations/{CHANNEL_CODE}")
    summary = call(admin, "GET", R + f"/summary?entityNo={ENTITY}&currency={CCY}", what="汇总")
    existing = {
        "clearingAccounts": [a["clearingAccountCode"] for a in clearing if a["clearingAccountCode"] in (CA_A, CA_B)],
        "channel": [ch["channelId"] for ch in channels if ch["channelCode"] == CHANNEL_CODE],
        "activeSettlementCash": [a["cashAccountId"] for a in cash if a["accountPurpose"] == "CHANNEL_SETTLEMENT"
                                 and a["status"] in ("ACTIVE", "PENDING_VERIFICATION")],
        "integration": status == 200,
    }
    print(f"  现有：清算账户 {len(clearing)} 个（演示编码 {existing['clearingAccounts']}）；渠道 {CHANNEL_CODE} {existing['channel']}；"
          f"AE001 USD 结算户 {existing['activeSettlementCash']}；接入方 {CHANNEL_CODE} {'已存在' if existing['integration'] else '不存在（将新建）'}")
    print(f"  汇总（运行前）：{json.dumps(summary, ensure_ascii=False)}")
    conflict = existing["clearingAccounts"] or existing["channel"] or existing["activeSettlementCash"]
    if conflict and not resume:
        fail("mor-test 已有演示对象（或 AE001 USD 结算户），本脚本不覆盖；先用 demo_cleanup.py 清理，或 --resume 原记录续跑", existing)
    if not resume and int(c.recon_sql("SELECT COUNT(*) FROM t_platform_txn")[0][0]):
        print("  注意：mor_reconciliation 已有平台流水（非演示），演示数据会与之并存；demo_cleanup.py 遇到外来行会中止")
    return existing


# ---------- 渠道（channel-connector）与接入方 ----------

def ensure_integration(admin):
    def create():
        status, _ = c.raw(admin, "GET", f"{EXT}/integrations/{CHANNEL_CODE}")
        if status == 200:
            return {"created": False}
        body = {"integrationCode": CHANNEL_CODE, "integrationName": f"演示渠道 Network International（{CHANNEL_CODE}）",
                "description": "对账控制台演示数据用合成接入方：无原子 API、脚本与实现，不可执行",
                "endpoints": [{"endpointCode": "DEMO_NI_API", "baseUrl": "https://demo-ni.example.test/api", "sandbox": True}],
                "reason": "对账控制台演示数据：渠道激活需要同编码接入方（演示清理时删除）"}
        call(admin, "POST", EXT + "/integrations", body, what=f"新建演示接入方 {CHANNEL_CODE}")
        return {"created": True}
    result = once("integration", create)
    ok(f"接入方 {CHANNEL_CODE}（{'本次新建' if result['created'] else '已存在，沿用'}）")


def mid_with_capability(admin, channel_id, account_id, code):
    mid = call(admin, "POST", CH + "/mids", {"channelId": channel_id, "channelAccountId": account_id,
                                            "externalMidCode": code, "entityNo": ENTITY}, (201,), f"新建 MID {code}")
    call(admin, "POST", f"{CH}/mids/{mid['midId']}/status", {"targetStatus": "ACTIVE", "reason": "对账演示数据"}, (200,), "MID 激活")
    call(admin, "POST", CH + "/mid-capabilities", {
        "midId": mid["midId"], "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": CCY, "mccCode": "5999",
        "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True}, {"capabilityCode": "ACTION.REFUND", "booleanValue": True}]},
        (201,), "MID USD 能力组")
    return mid["midId"]


def channel_setup(admin):
    channel_id = once("channel.id", lambda: call(admin, "POST", CH + "/channels", {
        "channelCode": CHANNEL_CODE, "channelName": "演示渠道 Network International"}, (201,), "新建演示渠道")["channelId"])
    record["channelId"] = channel_id
    once("channel.active", lambda: call(admin, "POST", f"{CH}/channels/{channel_id}/status",
                                        {"targetStatus": "ACTIVE", "reason": "对账演示数据"}, (200,), "渠道激活") and True)
    account_id = once("channel.account", lambda: call(admin, "POST", CH + "/channel-accounts", {
        "channelId": channel_id, "entityNo": ENTITY, "accountName": "AE001 NI 技术账号（演示）"}, (201,), "新建技术账号")["channelAccountId"])
    configuration = {"parameters": {"merchantId": "DEMO" + pysecrets.token_hex(5).upper(), "outletRef": "demo-outlet-" + pysecrets.token_hex(6)},
                     "credentials": {"apiKey": "synthetic-demo-" + pysecrets.token_urlsafe(32)}}
    once("channel.config", lambda: call(admin, "PUT", f"{CH}/channel-accounts/{account_id}/configuration", configuration, (200,),
                                        "技术账号配置（合成参数，Vault Protect）") and True)
    once("channel.account.active", lambda: call(admin, "POST", f"{CH}/channel-accounts/{account_id}/status",
                                                {"targetStatus": "ACTIVE", "reason": "对账演示数据"}, (200,), "技术账号激活") and True)
    mid_a = once("channel.midA", lambda: mid_with_capability(admin, channel_id, account_id, "DEMO-NI-AE001-01"))
    mid_b = once("channel.midB", lambda: mid_with_capability(admin, channel_id, account_id, "DEMO-NI-AE001-02"))
    record["channel"] = {"channelId": channel_id, "accountAE001": account_id, "midA": mid_a, "midB": mid_b}
    save()
    ok(f"渠道 {CHANNEL_CODE} ACTIVE，技术账号 {account_id}，MID {mid_a} / {mid_b}（USD 能力组）")


# ---------- 人员 ----------

def create_checker(admin):
    label = f"DEMO_RECON_CHECKER_{STAMP[:12]}"
    role = call(admin, "POST", "/iam/v1/roles", {
        "roleCode": label, "roleName": f"对账演示复核人 {STAMP[:12]}",
        "permissionIds": [c.PERM["RECON_READ"], c.PERM["RECON_APPROVE"], c.PERM["ACCOUNTING_READ"]],
        "reason": "对账控制台演示数据：复核人角色（对账读 + 审批 + 财务读）"}, (200, 201), "IAM 新建演示复核角色")
    record.setdefault("platformRoles", []).append(role["roleId"])
    save()
    email = f"demo-recon-checker-{STAMP[:12]}@example.test"
    initial = "Demo initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Demo checker " + pysecrets.token_urlsafe(18) + "!"
    created = call(admin, "POST", "/iam/v1/accounts", {"email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
                                                       "reason": "对账控制台演示数据：复核人（演示后停用）"}, (200, 201), "IAM 新建演示复核人")
    record.setdefault("platformAccounts", []).append(created["accountId"])
    record["people"] = {"CHECKER": {"label": label, "email": email, "password": password, "accountId": created["accountId"],
                                    "roleId": role["roleId"]}}
    save()
    first = c.login(email, initial)
    status, _ = c.raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    if status >= 300:
        fail(f"演示复核人首登改口令失败 HTTP {status}")
    c.login(email, password)
    return True


_checker_token = {}


def checker():
    if "token" not in _checker_token:
        person = record["people"]["CHECKER"]
        _checker_token["token"] = c.login(person["email"], person["password"])
    return _checker_token["token"]


def disable_checker(admin):
    person = record["people"]["CHECKER"]
    # Gateway 没有按 ID 读账户的路由；按商务验收先例分页查找
    account, page = None, 1
    while account is None:
        result = call(admin, "GET", f"/iam/v1/accounts?page={page}&size=200", what="读取演示复核人")
        account = next((r for r in result["items"] if r["accountId"] == person["accountId"]), None)
        if account is None and page * 200 >= result["total"]:
            fail("账户列表中找不到演示复核人", person["accountId"])
        page += 1
    if account.get("status") == "DISABLED":
        return True
    call(admin, "POST", f"/iam/v1/accounts/{person['accountId']}/disable",
         {"expectedVersion": account["version"], "reason": "对账控制台演示数据已生成，停用演示复核人"}, (200,), "停用演示复核人")
    return True


# ---------- 银行账户与清算账户 ----------

def cash_accounts(admin):
    def settlement():
        number = "AE07" + "".join(str(pysecrets.randbelow(10)) for _ in range(15)) + CASH_LAST4
        created = call(admin, "POST", R + "/cash-accounts", {
            "entityType": "OPERATING_ENTITY", "entityId": ENTITY, "accountPurpose": "CHANNEL_SETTLEMENT", "bankCode": "ENBD",
            "accountName": "AE001 NI 结算户", "currency": CCY, "accountNumber": number}, (201,), "提交结算银行账户")
        reviewed = call(checker(), "POST", f"{R}/cash-accounts/{created['cashAccountId']}/approval",
                        {"decision": "APPROVED", "approvedContentHash": created["contentHash"]}, (200,), "复核人批准结算银行账户")
        if reviewed.get("status") != "ACTIVE":
            fail("结算银行账户批准后不是 ACTIVE", reviewed)
        return created["cashAccountId"]

    def payout():
        number = "AE07" + "".join(str(pysecrets.randbelow(10)) for _ in range(15)) + PAYOUT_LAST4
        created = call(admin, "POST", R + "/cash-accounts", {
            "entityType": "OPERATING_ENTITY", "entityId": ENTITY, "accountPurpose": "PAYOUT", "bankCode": "ENBD",
            "accountName": "AE001 出款户（待复核）", "currency": CCY, "accountNumber": number}, (201,), "提交出款银行账户（留待复核）")
        return created["cashAccountId"]
    cash_id = once("cash.settlement", settlement)
    payout_id = once("cash.payout", payout)
    record["cashAccounts"] = [cash_id, payout_id]
    save()
    ok(f"结算户 {cash_id} ACTIVE（末四位 {CASH_LAST4}）；出款户 {payout_id} PENDING_VERIFICATION")
    return cash_id


def clearing_accounts(admin, cash_id):
    def create(code, wait_days, mid_key):
        ca = call(admin, "POST", R + "/clearing-accounts", {
            "clearingAccountCode": code, "channelCode": CHANNEL_CODE, "entityNo": ENTITY, "currency": CCY,
            "settlementCashAccountId": cash_id, "unmatchedWaitDays": wait_days, "costToleranceAmount": "0.10", "cashGraceDays": 2},
            (201,), f"新建清算账户 {code}")
        call(admin, "POST", f"{R}/clearing-accounts/{ca['clearingAccountId']}/mids", {"midId": record["channel"][mid_key]},
             (200, 201), f"清算账户 {code} 挂 MID")
        return ca["clearingAccountId"]
    a = once("clearing.A", lambda: create(CA_A, 3, "midA"))
    b = once("clearing.B", lambda: create(CA_B, 0, "midB"))
    record["clearingAccounts"] = {"A": a, "B": b}
    record["clearingAccountCodes"] = [CA_A, CA_B]
    save()
    ok(f"清算账户 {CA_A}={a}（等待 3 天、容差 0.10）；{CA_B}={b}（等待 0 天）")
    return {"A": a, "B": b}


# ---------- 平台流水（合成事实） ----------

def publish_facts(admin, ca):
    def publish():
        facts = {}
        for key, (kind, account, gross, cost, ref_type, ref, days) in FACTS.items():
            fact = c.fact(kind, ca[account], gross, ref_type, ref, channel_cost=cost, payee_id=CHANNEL_CODE, entity_no=ENTITY)
            fact["businessTime"] = datetime.combine(d(days), datetime.min.time(), timezone.utc).replace(
                hour=9 + len(facts) % 9, minute=7 * len(facts) % 60).strftime("%Y-%m-%dT%H:%M:%S.000000Z")
            fact["sourceEventKey"] = "recon-demo-" + fact["sourceEventKey"].split("-", 1)[1]
            facts[key] = fact
        record["facts"] = {k: f["factId"] for k, f in facts.items()}
        save()
        c.publish_facts(list(facts.values()))
        return True
    once("facts.published", publish)

    def landed():
        found = {}
        for account in ("A", "B"):
            page = call(admin, "GET", f"{R}/platform-txns?clearingAccountId={ca[account]}&page=1&size=50", what="平台流水列表")
            found.update({i["billingFactId"]: i for i in page.get("items", [])})
        return found if all(str(f) in found for f in record["facts"].values()) else None
    found = wait(landed, "17 条合成事实落为平台流水", 120, 3)
    record["platformTxns"] = {k: found[str(f)]["platformTxnId"] for k, f in record["facts"].items()}
    save()
    ok(f"平台流水 {len(record['platformTxns'])} 条（PAYMENT 14、REFUND 3；事实只投 reconciliation.billing.hash）")


# ---------- 渠道账单 ----------

def statement_csv(code, ref, settle_days, lines, header_gross):
    gross, fee, net = totals(lines)
    gross = Decimal(header_gross) if header_gross else gross
    rows = ["#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction",
            f"#HEADER,{code},{ref},{d(settle_days)},{CCY},{money(gross)},{money(fee)},{money(net if not header_gross else gross - fee)},CREDIT",
            "line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date,remark"]
    for no, line in enumerate(lines, 1):
        rows.append(",".join([str(no), line["lineType"], line["direction"], line["amount"], line["feeAmount"], line["referenceType"] or "",
                              line["externalTransactionId"] or "", line["transactionDate"], (line["remark"] or "").replace(",", "，")]))
    return "\n".join(rows) + "\n", gross, fee, (net if not header_gross else gross - fee)


def import_channel_statement(admin, key, ca):
    """导入一张渠道账单（开始 → 分段追加 → 完成），返回 (statementId, 完成状态)。"""
    account, ref, settle_days, lines, header_gross = channel_statements()[key]
    code = CA_A if account == "A" else CA_B

    def run():
        text, gross, fee, net = statement_csv(code, ref, settle_days, lines, header_gross)
        started = call(admin, "POST", R + "/channel-statements", {
            "clearingAccountCode": code, "channelBatchRef": ref, "settlementDate": d(settle_days).isoformat(), "currency": CCY,
            "grossAmount": money(gross), "feeAmount": money(fee), "netAmount": money(net), "cashDirection": "CREDIT",
            "fileName": f"{ref}-{key.lower()}.csv", "fileContentHash": hashlib.sha256(text.encode()).hexdigest(),
            "declaredLineCount": len(lines)}, (201,), f"开始导入渠道账单 {key}")
        sid = started["statementId"]
        record.setdefault("channelStatements", {})[key] = sid
        save()
        body = []
        for no, line in enumerate(lines, 1):
            item = {k: line[k] for k in ("lineType", "direction", "amount", "feeAmount", "referenceType", "externalTransactionId",
                                         "transactionDate")}
            item["lineNo"] = no
            if line.get("remark"):
                item["rawAttributes"] = {"remark": line["remark"]}
            body.append(item)
        call(admin, "POST", f"{R}/channel-statements/{sid}/lines", {"lines": body}, (200,), f"追加渠道账单 {key} 明细")
        done = call(admin, "POST", f"{R}/channel-statements/{sid}/completion", None, (200,), f"完成导入渠道账单 {key}")
        return {"statementId": sid, "status": done.get("status"), "rejectReason": done.get("rejectReason")}
    result = once(f"stmt.{key}", run)
    return result["statementId"], result["status"], result.get("rejectReason")


def statement_detail(admin, sid):
    return call(admin, "GET", f"{R}/channel-statements/{sid}", what=f"渠道账单 {sid} 详情")


def wait_statement_status(admin, sid, status, what, timeout=90):
    return wait(lambda: (lambda det: det if det.get("statement", {}).get("status") == status else None)(statement_detail(admin, sid)),
                f"{what} → {status}", timeout)


def statement_exceptions(admin, sid, types):
    def found():
        page = call(admin, "GET", f"{R}/recon-exceptions?statementId={sid}&page=1&size=50", what="账单异常列表")
        by_type = {e["exceptionType"]: e for e in page.get("items", []) if e["status"] in ("OPEN", "UNDER_REVIEW")}
        return by_type if all(t in by_type for t in types) else None
    return wait(found, f"账单 {sid} 出现异常 {types}")


def classify(admin, exception, code, note, approve):
    sub = call(admin, "POST", f"{R}/recon-exceptions/{exception['exceptionId']}/resolutions", {
        "resolutionType": "CLASSIFY", "classificationCode": code, "amount": exception["differenceAmount"], "note": note},
        (201,), f"提交 CLASSIFY {code}")
    if sub.get("status") != "PENDING_CHECK":
        fail("处置提交后不是 PENDING_CHECK", sub)
    if not approve:
        return {"resolutionId": sub["resolutionId"], "status": "PENDING_CHECK"}
    approved = call(checker(), "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval",
                    {"decision": "APPROVED", "approvedContentHash": sub["contentHash"]}, (200,), f"复核人批准 CLASSIFY {code}")
    if approved.get("status") != "APPLIED":
        fail("处置批准后不是 APPLIED", approved)
    return {"resolutionId": sub["resolutionId"], "status": "APPLIED"}


def batch_d7(admin):
    sid, status, _ = import_channel_statement(admin, "D7", None)
    if status != "MATCHING":
        fail(f"D-7 账单导入后状态 {status}，期望 MATCHING")
    exceptions = statement_exceptions(admin, sid, ["UNEXPLAINED_ADJUSTMENT"])
    once("D7.classify", lambda: classify(admin, exceptions["UNEXPLAINED_ADJUSTMENT"], "CHANNEL_FEE_OTHER",
                                        "渠道 10 月月度服务费 15.00，归渠道其他费用", True))
    detail = wait_statement_status(admin, sid, "RECONCILED", "D-7 账单对平")
    record.setdefault("expectedMovements", {})["D7"] = detail["expectedMovementId"]
    save()
    ok(f"D-7 账单 {sid} RECONCILED，净额 {detail['statement']['netAmount']}，预期银行流水 {detail['expectedMovementId']}")


def batch_d4(admin):
    sid, status, _ = import_channel_statement(admin, "D4", None)
    if status != "MATCHING":
        fail(f"D-4 账单导入后状态 {status}，期望 MATCHING")
    detail = wait_statement_status(admin, sid, "RECONCILED", "D-4 账单自动对平")
    record.setdefault("expectedMovements", {})["D4"] = detail["expectedMovementId"]
    save()
    ok(f"D-4 账单 {sid} RECONCILED，净额 {detail['statement']['netAmount']}，预期银行流水 {detail['expectedMovementId']}")


def batch_d2(admin):
    sid, status, _ = import_channel_statement(admin, "D2", None)
    if status != "MATCHING":
        fail(f"D-2 账单导入后状态 {status}，期望 MATCHING")
    exceptions = statement_exceptions(admin, sid, ["AMOUNT_MISMATCH", "COST_DEVIATION", "UNEXPLAINED_ADJUSTMENT"])
    adjustment = exceptions["UNEXPLAINED_ADJUSTMENT"]
    if adjustment["status"] == "OPEN":
        once("D2.classify", lambda: classify(admin, adjustment, "CHANNEL_RESERVE_HOLD",
                                            "渠道按 10% 扣留滚动保证金 56.00，待复核", False))
    gate = statement_detail(admin, sid).get("gate") or {}
    ok(f"D-2 账单 {sid} 保持 MATCHING；异常 AMOUNT_MISMATCH {exceptions['AMOUNT_MISMATCH']['differenceAmount']}、"
       f"COST_DEVIATION {exceptions['COST_DEVIATION']['differenceAmount']}、保证金扣留 CLASSIFY 待复核；门禁 {gate}")


def batch_d1(admin):
    sid, status, reason = import_channel_statement(admin, "D1_REJ", None)
    if status != "REJECTED":
        fail(f"D-1 第 1 版（表头总额错）状态 {status}，期望 REJECTED")
    ok(f"D-1 第 1 版 {sid} REJECTED：{reason}")
    sid, status, _ = import_channel_statement(admin, "D1_VOID", None)
    if status != "MATCHING":
        fail(f"D-1 第 2 版导入后状态 {status}，期望 MATCHING")

    def void():
        statement_exceptions(admin, sid, ["UNEXPLAINED_ADJUSTMENT"])
        voided = call(admin, "POST", f"{R}/channel-statements/{sid}/void", None, (200,), "作废 D-1 第 2 版")
        return voided.get("status") or voided.get("statement", {}).get("status")
    once("D1_VOID.void", void)
    wait_statement_status(admin, sid, "VOIDED", "D-1 第 2 版作废", 30)
    ok(f"D-1 第 2 版 {sid} VOIDED（渠道误加调整行）")
    sid, status, _ = import_channel_statement(admin, "D1", None)
    if status != "MATCHING":
        fail(f"D-1 第 3 版导入后状态 {status}，期望 MATCHING")
    detail = wait_statement_status(admin, sid, "RECONCILED", "D-1 第 3 版对平")
    record.setdefault("expectedMovements", {})["D1"] = detail["expectedMovementId"]
    save()
    ok(f"D-1 第 3 版 {sid} RECONCILED（在途，银行未到账），净额 {detail['statement']['netAmount']}")


def batch_b(admin):
    sid, status, _ = import_channel_statement(admin, "B1", None)
    if status != "MATCHING":
        fail(f"清算账户 B 账单导入后状态 {status}，期望 MATCHING")
    wait(lambda: (statement_detail(admin, sid).get("matchedLineCount") or 0) >= 1, "B 账单 P14 行匹配")

    def unmatched():
        page = call(admin, "GET", f"{R}/recon-exceptions?statementId={sid}&exceptionType=CHANNEL_LINE_UNMATCHED&page=1&size=10",
                    what="B 账单未命中异常")
        return page.get("items") or None

    def trigger():
        if unmatched():
            return "already"
        try:
            xxl.trigger(xxl.Admin(), "channelLineWaitExpiry")
        except SystemExit as failure:
            print(f"  XXL 手动触发失败（{failure}）；任务每 10 分钟自动执行，稍后会开异常")
            return "cron"
        return "triggered"
    how = once("B1.expiry", trigger)
    found = c.wait_until(unmatched, 60, 3)
    if found:
        ok(f"清算账户 B 账单 {sid} MATCHING；平台无对应流水的行已开 CHANNEL_LINE_UNMATCHED（{how}）")
    else:
        print(f"[提示] B 账单 {sid} 的 CHANNEL_LINE_UNMATCHED 尚未出现；XXL 任务 channelLineWaitExpiry 每 10 分钟执行，之后会开立")


# ---------- 银行对账单 ----------

def import_bank_statement(admin, key, cash_id):
    ref, start_days, end_days, opening, lines, closing_override = bank_statements()[key]
    closing = closing_override if closing_override is not None else bank_closing(opening, lines)

    def run():
        payload = [{"lineNo": no, "bankTransactionRef": l[0], "valueDate": d(l[1]).isoformat(), "direction": l[2], "amount": l[3],
                    "counterpartyName": l[4], "referenceText": l[5]} for no, l in enumerate(lines, 1)]
        text = json.dumps({"ref": ref, "opening": money(opening), "closing": money(closing), "lines": payload}, ensure_ascii=False)
        started = call(admin, "POST", R + "/bank-statements", {
            "cashAccountId": cash_id, "bankAccountLast4": CASH_LAST4, "statementRef": ref, "periodStart": d(start_days).isoformat(),
            "periodEnd": d(end_days).isoformat(), "currency": CCY, "openingBalance": money(opening), "closingBalance": money(closing),
            "fileName": f"{ref}.csv", "fileContentHash": hashlib.sha256(text.encode()).hexdigest(), "declaredLineCount": len(lines)},
            (201,), f"开始导入银行对账单 {key}")
        bid = started["bankStatementId"]
        record.setdefault("bankStatements", {})[key] = bid
        save()
        call(admin, "POST", f"{R}/bank-statements/{bid}/lines", {"lines": payload}, (200,), f"追加银行对账单 {key} 流水")
        done = call(admin, "POST", f"{R}/bank-statements/{bid}/completion", None, (200,), f"完成导入银行对账单 {key}")
        return {"bankStatementId": bid, "status": done.get("status"), "gapExceptionId": done.get("gapExceptionId"),
                "rejectReason": done.get("rejectReason")}
    return once(f"bank.{key}", run)


def bank_lines(admin, bid):
    page = call(admin, "GET", f"{R}/bank-statements/{bid}/lines?page=1&size=10", what="银行流水")
    return {line["lineNo"]: line for line in page["items"]}


def open_exception(admin, source_type, source_id, what):
    return call(admin, "POST", R + "/recon-exceptions", {"sourceType": source_type, "sourceId": source_id}, (201,), what)


def bank_bs1(admin, cash_id):
    result = import_bank_statement(admin, "BS1", cash_id)
    if result["status"] != "IMPORTED":
        fail(f"BS1 导入状态 {result['status']}，期望 IMPORTED", result)
    lines = bank_lines(admin, result["bankStatementId"])
    settle, interest = lines[1], lines[2]
    movement = record["expectedMovements"]["D7"]

    def match():
        proposals = c.wait_until(lambda: [m for m in call(admin, "GET", f"{R}/bank-matches?bankStatementLineId={settle['bankStatementLineId']}",
                                                          what="匹配建议").get("items", [])
                                          if m["status"] == "PROPOSED" and str(m["expectedMovementId"]) == str(movement)], 20, 2)
        if proposals:
            confirmed = call(admin, "POST", f"{R}/bank-matches/{proposals[0]['bankMatchId']}/confirmation", None, (200,), "确认系统建议")
            return {"method": "SUGGESTED", "bankMatchId": confirmed["bankMatchId"]}
        created = call(admin, "POST", R + "/bank-matches", {"bankStatementLineId": settle["bankStatementLineId"],
                                                            "allocations": [{"expectedMovementId": movement, "amount": "468.93"}]},
                       (201,), "人工匹配 D-7 到账")
        return {"method": "MANUAL", "bankMatchId": (created.get("items") or [{}])[0].get("bankMatchId")}
    how = once("BS1.match", match)
    wait_statement_status(admin, record["channelStatements"]["D7"], "CASH_CONFIRMED", "D-7 账单到账确认")
    ok(f"BS1 {result['bankStatementId']} 导入；468.93 匹配 D-7（{how['method']}）→ D-7 CASH_CONFIRMED")

    def interest_resolved():
        exception = open_exception(admin, "BANK_STATEMENT_LINE", interest["bankStatementLineId"], "对利息流水开 BANK_LINE_UNMATCHED")
        record.setdefault("exceptions", {})["BS1.interest"] = exception["exceptionId"]
        save()
        return classify(admin, exception, "BANK_INTEREST", "银行季度存款利息 3.12，归利息收入", True)
    once("BS1.interest", interest_resolved)
    ok("BS1 利息 3.12：BANK_LINE_UNMATCHED → CLASSIFY BANK_INTEREST 已批准（RESOLVED）")


def bank_bs2(admin, cash_id):
    result = import_bank_statement(admin, "BS2", cash_id)
    if result["status"] != "IMPORTED" or result.get("gapExceptionId"):
        fail(f"BS2 导入状态 {result['status']}、连续性异常 {result.get('gapExceptionId')}，期望 IMPORTED 且无缺口", result)
    lines = bank_lines(admin, result["bankStatementId"])
    settle, fee = lines[1], lines[2]
    movement = record["expectedMovements"]["D4"]
    once("BS2.match", lambda: call(admin, "POST", R + "/bank-matches", {
        "bankStatementLineId": settle["bankStatementLineId"], "allocations": [{"expectedMovementId": movement, "amount": "1455.27"}]},
        (201,), "人工匹配 D-4 到账 1455.27（少 0.20）") and True)

    def mismatch():
        exception = open_exception(admin, "EXPECTED_BANK_MOVEMENT", movement, "对 D-4 预期流水开 BANK_AMOUNT_MISMATCH")
        if Decimal(exception["differenceAmount"]) != Decimal("0.20"):
            fail("D-4 预期流水差异不是 0.20", exception)
        return exception["exceptionId"]
    record.setdefault("exceptions", {})["D4.bankMismatch"] = once("BS2.mismatch", mismatch)
    record["exceptions"]["BS2.bankFee"] = once("BS2.fee", lambda: open_exception(
        admin, "BANK_STATEMENT_LINE", fee["bankStatementLineId"], "对银行手续费流水开 BANK_LINE_UNMATCHED")["exceptionId"])
    save()
    ok(f"BS2 {result['bankStatementId']} 导入（与 BS1 连续）；1455.27 部分匹配 D-4，BANK_AMOUNT_MISMATCH 0.20 未结；手续费 25.00 BANK_LINE_UNMATCHED 未结")


def bank_rejected_and_gap(admin, cash_id):
    result = import_bank_statement(admin, "BS_REJ", cash_id)
    if result["status"] != "REJECTED":
        fail(f"临时对账单（余额不平）状态 {result['status']}，期望 REJECTED", result)
    ok(f"临时对账单 {result['bankStatementId']} REJECTED：{result.get('rejectReason')}")
    result = import_bank_statement(admin, "BS3", cash_id)
    if result["status"] != "IMPORTED" or not result.get("gapExceptionId"):
        fail(f"BS3 状态 {result['status']}、连续性异常 {result.get('gapExceptionId')}，期望 IMPORTED 且开 BANK_STATEMENT_GAP", result)
    record.setdefault("exceptions", {})["BS3.gap"] = result["gapExceptionId"]
    save()
    ok(f"BS3 {result['bankStatementId']} 导入；期初 11950.00 ≠ BS2 期末 11902.32 → BANK_STATEMENT_GAP {result['gapExceptionId']}；500.00 来账未匹配")


def platform_unsettled(admin):
    txn = record["platformTxns"]["P12"]
    record.setdefault("exceptions", {})["P12.unsettled"] = once("P12.unsettled", lambda: open_exception(
        admin, "PLATFORM_TXN", txn, "对 D-10 未结算付款开 PLATFORM_ITEM_UNSETTLED")["exceptionId"])
    save()
    ok(f"平台流水 {txn}（{ord_ref(12)}，D-10）无账单行 → PLATFORM_ITEM_UNSETTLED 未结")


# ---------- 可选：异常账龄前移 ----------

def backdate(admin):
    """把未结演示异常的 opened_time / create_time 按业务日期前移（直接 UPDATE；只动本记录的异常）。"""
    ages = {"P12.unsettled": 9, "D4.bankMismatch": 5, "BS2.bankFee": 4}
    page = call(admin, "GET", f"{R}/recon-exceptions?statementId={record['channelStatements']['D2']}&page=1&size=50", what="D-2 异常")
    targets = {record["exceptions"][k]: v for k, v in ages.items() if k in record.get("exceptions", {})}
    targets.update({e["exceptionId"]: 2 for e in page["items"] if e["status"] in ("OPEN", "UNDER_REVIEW")})
    for exception_id, days in targets.items():
        c.recon_sql(f"UPDATE t_recon_exception SET opened_time = opened_time - INTERVAL {int(days)} DAY,"
                    f" create_time = create_time - INTERVAL {int(days)} DAY WHERE id = {int(exception_id)}"
                    " AND status IN ('OPEN','UNDER_REVIEW')")
    return {str(k): v for k, v in targets.items()}


# ---------- 计划与主流程 ----------

def print_plan():
    print(f"演示日期 D0 = {D0}；渠道 {CHANNEL_CODE}；清算账户 {CA_A}（等待 3 天）/ {CA_B}（等待 0 天）；结算户末四位 {CASH_LAST4}")
    print("平台流水（合成 BillingFactRecorded）：")
    for key, (kind, account, gross, cost, _, ref, days) in FACTS.items():
        print(f"  {key} {kind:7} {account} {gross:>8} 预估费 {cost or '-':>5} {ref} 业务日 {d(days)}")
    print("渠道账单：")
    for key, (account, ref, settle, lines, header_gross) in channel_statements().items():
        gross, fee, net = totals(lines)
        print(f"  {key:8} {account} {ref} 结算日 {d(settle)} 行 {len(lines)} 总额 {money(gross)} 费 {money(fee)} 净额 {money(net)}"
              + (f"（表头总额故意写 {header_gross} → REJECTED）" if header_gross else ""))
    print("银行对账单：")
    for key, (ref, start, end, opening, lines, override) in bank_statements().items():
        closing = override if override is not None else bank_closing(opening, lines)
        print(f"  {key:6} {ref} 期间 {d(start)}..{d(end)} 期初 {money(opening)} 期末 {money(closing)} 行 {len(lines)}"
              + ("（期末故意不平 → REJECTED）" if override is not None else ""))
    d7_net = totals(channel_statements()["D7"][3])[2]
    d4_net = totals(channel_statements()["D4"][3])[2]
    assert d7_net == Decimal("468.93") and d4_net == Decimal("1455.47"), (d7_net, d4_net)
    assert bank_closing(OPENING, bank_statements()["BS1"][4]) == bank_statements()["BS2"][3]


def main():
    global RECORD, record
    parser = argparse.ArgumentParser(description="对账控制台 mor-test 演示数据")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--keep-checker", action="store_true")
    parser.add_argument("--backdate-exceptions", action="store_true")
    parser.add_argument("--resume")
    args = parser.parse_args()
    if args.resume:
        RECORD = Path(args.resume)
        record = json.loads(RECORD.read_text())
    print_plan()
    admin = c.admin_token()
    print("预检（只读）：")
    preflight(admin, bool(args.resume))
    if args.dry_run:
        try:
            xxl.plan(xxl.Admin(), False)
        except SystemExit as failure:
            print(f"  XXL 只读核对失败：{failure}")
        print("\n--dry-run：未写任何数据。")
        return
    save()
    print(f"\n记录文件：{RECORD}（0600）")
    ensure_integration(admin)
    channel_setup(admin)
    once("checker.created", lambda: create_checker(admin))
    ok(f"演示复核人 {record['people']['CHECKER']['label']}（账号 {record['people']['CHECKER']['accountId']}；口令只在记录文件）")
    cash_id = cash_accounts(admin)
    ca = clearing_accounts(admin, cash_id)
    publish_facts(admin, ca)
    batch_d7(admin)
    batch_d4(admin)
    batch_d2(admin)
    batch_d1(admin)
    batch_b(admin)
    bank_bs1(admin, cash_id)
    bank_bs2(admin, cash_id)
    bank_rejected_and_gap(admin, cash_id)
    platform_unsettled(admin)
    if args.backdate_exceptions:
        record["backdated"] = once("backdate", lambda: backdate(admin))
        ok(f"未结异常账龄前移：{record['backdated']}")
    if not args.keep_checker:
        once("checker.disabled", lambda: disable_checker(admin))
        ok("演示复核人已停用（角色保留；需要演示审批时可在 IAM 启用，或下次用 --keep-checker）")
    summary = call(admin, "GET", R + f"/summary?entityNo={ENTITY}&currency={CCY}", what="汇总")
    record["summaryAfter"] = summary
    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    save()
    print("\n汇总（运行后）：" + json.dumps(summary, ensure_ascii=False))
    print("说明：CLASSIFY 批准、账单对平、银行匹配登记的 ChannelBatchReconciled / ActualChannelCostConfirmed / "
          "ChannelSettlementConfirmed / ReconAdjustmentApproved 目前没有订阅方（I-08），对应补偿任务会留存 FAILED，属预期。")
    print(f"记录：{RECORD}；移除：python3 -I {HERE / 'demo_cleanup.py'} {RECORD} [--dry-run]")


if __name__ == "__main__":
    main()
