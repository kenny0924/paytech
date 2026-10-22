"""阶段 7 S16：经运营接口为 AE001 建 PAYERMAX 技术账号（UAT 凭据）、MID、CARD/*/*/USD/5999 能力（ACTION.PAY、SECURITY.3DS_MODE=SUPPORTED）。

UAT 账号文件路径经环境变量 PMX_UAT_ACCOUNT_FILE 传入（仓库外 0600 JSON，键 appId / merchantNo / keyVersion / merchantPrivateKey /
payermaxPublicKey），只在内存中读入并经 HTTPS-less 本机运营入口提交；输出只含 ID 与状态，不含任何凭据值。
子命令：setup | teardown（MID 置 SUSPENDED、能力 DISABLED，账号保留）。状态写 accept-s16.json。
"""
import json
import os
import sys
from datetime import datetime, timezone

import mt

ADMIN = "/channel-admin/v1"
CODE = "PAYERMAX"
ENTITY_NO = "AE001"
REASON = "3DS + 卡 BIN 库 mor-test 发布 S16（AC-12 PayerMax UAT）"
OUT = "accept-s16.json"


def must(label, status, body, expected):
    if status not in expected:
        raise SystemExit(f"{label} 失败：HTTP {status} {json.dumps(body, ensure_ascii=False)[:400]}")
    return body


def channel_id():
    rows = mt.sql("channel-connector", f"SELECT channel_id, channel_status FROM t_channel WHERE channel_code='{CODE}'")
    return rows[0]


def setup():
    token = mt.login()
    keys = json.loads(open(os.environ["PMX_UAT_ACCOUNT_FILE"]).read())
    cid, status = channel_id()
    out = {"at": mt.now(), "channelId": cid, "channelStatusBefore": status}
    if status != "ACTIVE":
        must("激活渠道", *mt.ops(token, "POST", f"{ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    run = datetime.now(timezone.utc).strftime("%m%d%H%M")
    account = must("新建账号", *mt.ops(token, "POST", ADMIN + "/channel-accounts", {
        "channelId": cid, "entityNo": ENTITY_NO, "accountName": f"PayerMax UAT 卡直付 {run}", "remark": REASON}), (201,))
    aid = account["channelAccountId"]
    configured = must("写入账号配置", *mt.ops(token, "PUT", f"{ADMIN}/channel-accounts/{aid}/configuration", {
        "parameters": {k: str(keys[k]) for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
        "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"]}}), (200,))
    del keys
    must("激活账号", *mt.ops(token, "POST", f"{ADMIN}/channel-accounts/{aid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    mid = must("新建 MID", *mt.ops(token, "POST", ADMIN + "/mids", {
        "channelId": cid, "channelAccountId": aid, "externalMidCode": f"PMX-UAT-AE001-{run}", "entityNo": ENTITY_NO}), (201,))
    mid_id = mid["midId"]
    must("激活 MID", *mt.ops(token, "POST", f"{ADMIN}/mids/{mid_id}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    capability = must("新建能力组", *mt.ops(token, "POST", ADMIN + "/mid-capabilities", {
        "midId": mid_id, "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": "USD", "mccCode": "5999",
        "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True},
                    {"capabilityCode": "AMOUNT.MIN", "decimalValue": "0.01"}, {"capabilityCode": "AMOUNT.MAX", "decimalValue": "100000"},
                    {"capabilityCode": "SECURITY.3DS_MODE", "codeValue": "SUPPORTED"}]}), (201,))
    out.update(channelAccountId=aid, midId=mid_id, externalMidCode=f"PMX-UAT-AE001-{run}",
               midCapabilityId=capability["midCapabilityId"], vaultTokenSuffix=configured.get("vaultTokenSuffix"))
    mt.save(OUT, "setup", out)


def teardown():
    token = mt.login()
    state = json.loads((mt.HERE / OUT).read_text())["setup"]
    out = {"at": mt.now(),
           "capability": mt.ops(token, "POST", f"{ADMIN}/mid-capabilities/{state['midCapabilityId']}/status",
                                {"targetStatus": "DISABLED"})[0],
           "mid": mt.ops(token, "POST", f"{ADMIN}/mids/{state['midId']}/status",
                         {"targetStatus": "SUSPENDED", "reason": REASON + " 结束"})[0]}
    mt.save(OUT, "teardown", out)


if __name__ == "__main__":
    {"setup": setup, "teardown": teardown}[sys.argv[1]]()
