"""统一对外渠道出口第一段 · 第 11 步 platform 切换后冒烟（只读）。

覆盖 release.md 第 8 节"冒烟"行，以及同期会话请求顺带核对的页面接口：
  - 运营登录；导航含外部 API 两页与对账渠道流水页（块 126）；
  - 控制台外部 API 标准 API 列表 200；Platform 私网 /external-api-admin 已下线（404）；
  - 币种页列出 USD（币种会话）；对账渠道流水 / 渠道对账单 / 异常列表 200（对账会话）；
  - 商务合同列表与详情、费率卡、商务看板 200（商务会话）。
用法：python3 -I smoke_after_platform.py；只打印状态与计数，不打印 Token 或口令。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ccu_mortest as m  # noqa: E402

h = m.h
results = []


def check(name, ok, evidence=""):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name} {evidence}", flush=True)


def codes(node):
    """递归收集导航树中的页面编码。"""
    found = set()
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(value, str) and value.startswith("OPERATIONS_"):
                found.add(value)
            found |= codes(value)
    elif isinstance(node, list):
        for item in node:
            found |= codes(item)
    return found


token = m.login()
check("运营登录", bool(token))

status, nav = m.ops(token, "GET", "/authorization/v1/navigation")
pages = codes(nav)
for page in ("OPERATIONS_EXTERNAL_STANDARD_APIS", "OPERATIONS_EXTERNAL_INTEGRATIONS", "OPERATIONS_FINANCE_RECON_CHANNEL_LINES"):
    check(f"导航含 {page}", status == 200 and page in pages, f"HTTP {status}")

status, body = m.ops(token, "GET", "/external-api-admin/v1/standard-apis")
check("控制台外部 API 标准 API 列表", status == 200, f"HTTP {status}")
status, _ = h.private_call("platform", "GET", "/external-api-admin/v1/standard-apis")
check("Platform 私网 /external-api-admin 已下线", status == 404, f"HTTP {status}")

status, body = m.ops(token, "GET", "/currency-admin/v1/currency-specs")
check("币种列表含 USD", status == 200 and "\"USD\"" in json.dumps(body), f"HTTP {status}")

for path in ("/finance/reconciliation/v1/channel-statement-lines", "/finance/reconciliation/v1/channel-statements",
             "/finance/reconciliation/v1/recon-exceptions"):
    status, _ = m.ops(token, "GET", path)
    check(f"对账 GET {path}", status == 200, f"HTTP {status}")

status, body = m.ops(token, "GET", "/commercial-admin/v1/agreements")
check("商务合同列表", status == 200, f"HTTP {status}")
items = body.get("items") or body.get("records") or body.get("content") or [] if isinstance(body, dict) else []
if items:
    agreement_id = items[0].get("agreementId") or items[0].get("id")
    status, _ = m.ops(token, "GET", f"/commercial-admin/v1/agreements/{agreement_id}")
    check("商务合同详情（产品与开通）", status == 200, f"HTTP {status}")
    client_id = items[0].get("clientId")
    if client_id:
        status, _ = m.ops(token, "GET", f"/commercial-admin/v1/rate-cards?clientId={client_id}")
        check("商务费率预览 GET /commercial-admin/v1/rate-cards", status == 200, f"HTTP {status}")
status, _ = m.ops(token, "GET", "/commercial-admin/v1/dashboard")
check("商务 GET /commercial-admin/v1/dashboard", status == 200, f"HTTP {status}")

failed = [name for name, ok in results if not ok]
print(f"冒烟 {len(results) - len(failed)}/{len(results)} 通过" + (f"；失败：{failed}" if failed else ""))
sys.exit(1 if failed else 0)
