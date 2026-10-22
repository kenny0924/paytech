"""S16 适配：mor-test 标准 API PAY（v3）已是 configure_card 目标字段的超集（card / card.token 可选，另有 APM 字段），
且被 ALIPAY_PLUS、DLOCAL、I_CYBS_NI、I_MPGS_NI、TOSS_PAY 实现；configure_card.py 的“改标准 API PAY”一步会删字段、把 card 改必填，
被 Platform 复核拒绝（400 业务校验未通过）。本脚本只执行 harness 中的接入方部分 configure_card(token, 'PAYERMAX')
（原子 API ORDER_AND_PAY + 卡直付 PAY 流程），不改标准 API。用法：E2E_RUNTIME=<0700 目录> python3 run_configure_card_only.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(ROOT / "requirements/20261014/payment-PayerMax卡直付对接/assets/harness"))
sys.path.insert(0, str(ROOT / "requirements/20261014/platform-support-PayerMax接入/assets/harness"))
import configure_card as cc  # noqa: E402
from common import login, results, runtime  # noqa: E402

cc.configure_card(login(), "PAYERMAX")
(runtime / "configure-card-PAYERMAX-only.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"calls": len(results), "saved": [r["name"] for r in results if r["name"].endswith(("-create", "-update", "-delete"))]},
                 ensure_ascii=False))
