"""S1 之后的补充接口验收（续用 s1_flow.py 的运行记录）：
1. AC-02 经营主体不一致：先摘 MID（DELETE），挂到 AE002 清算账户被拒，再挂回原清算账户；
2. AC-14 reconciliation 自身的"提交人 ≠ 复核人"：运营管理员（同时有操作与审批权限，Gateway 放行）提交后自己复核被拒；
用法：python3 -I extra_checks.py <run-*.json>
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

R = "/finance/reconciliation/v1"


def main():
    path = Path(sys.argv[1])
    record = json.loads(path.read_text())
    people = record["people"]
    maker = c.login(people["MAKER"]["email"], people["MAKER"]["password"])
    checker = c.login(people["CHECKER"]["email"], people["CHECKER"]["password"])
    admin = c.admin_token()
    ca = record["clearingAccount"]
    mid = record["channel"]["midAE001"]
    ca_ae002 = record["clearingAccounts"][2]

    status, body = c.raw(maker, "DELETE", f"{R}/clearing-accounts/{ca['clearingAccountId']}/mids/{mid}")
    c.check("AC-02", f"摘除 MID（{status}）", status in (200, 204), body)
    status, body = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca_ae002}/mids", {"midId": mid})
    c.check("AC-02", f"MID 经营主体（AE001）与清算账户（AE002）不一致被拒（{body.get('detail')}）", c.rejected(status, body, "经营主体"), body)
    status, body = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca['clearingAccountId']}/mids", {"midId": mid})
    c.check("AC-02", f"挂回原清算账户（{status}）", status in (200, 201), body)

    status, created = c.raw(admin, "POST", R + "/cash-accounts", {
        "entityType": "OPERATING_ENTITY", "entityId": "AE001", "accountPurpose": "PAYOUT", "bankCode": "ENBD",
        "accountName": "self review probe", "currency": "USD", "accountNumber": "AE070000000000000005555"})
    c.check("准备", f"运营管理员提交新建银行账户（{status}）", status == 201, created)
    record.setdefault("cashAccounts", []).append(created.get("cashAccountId"))
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    review = {"decision": "APPROVED", "approvedContentHash": created.get("contentHash")}
    status, body = c.raw(admin, "POST", f"{R}/cash-accounts/{created['cashAccountId']}/approval", review)
    c.check("AC-14/AC-03", f"同时有操作与审批权限的提交人复核自己的提交 → reconciliation 拒绝（{body.get('detail')}）",
            c.rejected(status, body, "提交人不能复核"), body)
    status, body = c.raw(checker, "POST", f"{R}/cash-accounts/{created['cashAccountId']}/approval",
                         {"decision": "REJECTED", "approvedContentHash": created.get("contentHash")})
    c.check("AC-03", f"另一人拒绝 → CLOSED（{body.get('status')}）", status == 200 and body.get("status") == "CLOSED", body)
    record.setdefault("results", []).extend(c.results)
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
