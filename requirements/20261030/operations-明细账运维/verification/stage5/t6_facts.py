"""T6 造数：经 billing 私网命令登记并批准 MANUAL_ADJUSTMENT 计费事实（与 FinanceBillingFactClientImpl 相同的请求头），正式 MQ 进入 account。
用法：python3 t6_facts.py <数量> [fe|client2]   fe = 带合作方二元组与 PLATFORM_SERVICE_CHARGE → Finance Entity 一行；client2 = 收方换成第二个合成 Client（产生第二个 Owner 账户，用于复数文案）。"""
import sys
import time
import uuid
import t6lib as L

st = L.load()
MAKER = st.setdefault("makerId", "236033080844881920")   # E2E 管理员 Account ID
CHECKER = st.setdefault("checkerId", "236033080844881921")  # 复核人（合成十进制 ID，与编制人不同）
L.save(st)
en = st["entityNo"]
mode = sys.argv[2] if len(sys.argv) > 2 else ""
if mode == "client2":
    st.setdefault("clientId2", "88" + str(int(st["clientId"][2:]) + 1)); L.save(st)
client = st["clientId2"] if mode == "client2" else st["clientId"]


def one(fe):
    lines = [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": en, "payeeRole": "CLIENT",
              "payeeId": client, "amount": "1.00", "direction": "ADJUSTMENT", "targetBucket": "PENDING"}]
    body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": "PLOPS-T6-" + uuid.uuid4().hex,
            "clientId": client, "entityNo": en, "currency": "USD", "lines": lines}
    if fe:
        body.update(localPartnerId=st["partnerId"], financeEntityId=st["financeEntityId"])
        lines.append({"componentType": "PLATFORM_SERVICE_CHARGE", "payerRole": "OPERATING_ENTITY", "payerId": en,
                      "payeeRole": "FINANCE_ENTITY", "payeeId": st["financeEntityId"], "amount": "0.10", "direction": "ADJUSTMENT"})
    s, b = L.private("billing", "POST", "/billing/v1/facts", body, L.actor_headers(MAKER))
    if s >= 300:
        raise SystemExit(f"submit {s} {b}")
    s2, b2 = L.private("billing", "POST", f"/billing/v1/facts/{b['factId']}/approve", None, L.actor_headers(CHECKER))
    if s2 >= 300:
        raise SystemExit(f"approve {s2} {b2}")
    return b["factId"], b2.get("status")


n = int(sys.argv[1])
fe = mode == "fe"
t0 = time.monotonic()
ids = []
for i in range(n):
    fid, status = one(fe)
    ids.append(fid)
    st.setdefault("factIds", []).append(fid)
    if i % 50 == 49:
        L.save(st)
L.save(st)
print(f"{n} facts fe={fe} {time.monotonic() - t0:.1f}s last={ids[-1]} status={status}")
