"""阶段 5 本机：只做 Platform 侧的合成 PayerMax 接入方配置（PAYERMAX_MOCK），替代 channel_setup.py 的全部渠道侧步骤。

channel_setup.py（20261017 local-e2e）原本做三件事：Platform 外部 API 配置、经「手动加密」得到账号 Token、把 Token 直写
t_channel_account 并改 ACTIVE（夹具）。本需求之后后两步由运营控制台完成（local_acceptance.py：渠道 / 账号 / 真实 Vault Protect / MID / 能力），
因此这里只复用其 Platform 部分，且不改原脚本：
  install_harness()      以真实运营入口替换三个 PayerMax harness 的 common 模块
  configure_platform()   reset_stale_catalog（mor_platform_v2e2e 复制库里不完整的旧 PAY 配置，按依赖倒序经真实入口删除）
                         → 标准 API PAY / PAY_QUERY / REFUND / REFUND_QUERY、接入方 PAYERMAX_MOCK、出站地址（沙箱）、签名脚本、卡直付与退款流程
前置：run-service.sh 已起 platform、gateway、ops-bff、nginx、payermax-mock（密钥文件 local-environment/e2e/runtime/payermax）。
不执行 sql/50-channel-connector-payermax-mock.sql，不调用 /vault-admin/v1/manual/secret-protect，不写 mor_channel_connector_v2e2e。
用法：python3 platform_payermax_setup.py；不打印密钥、Token 或口令。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import channel_setup as cs  # noqa: E402

cs.REASON = "channel-connector 运营控制台阶段 5 本机验收（Platform 接入方 PAYERMAX_MOCK）"


def main():
    configure, configure_card, configure_refund = cs.install_harness()
    cs.mock_keys()
    cs.configure_platform(configure, configure_card, configure_refund)
    token = cs.login()
    status, integration = cs.call("integration-get-final", "GET", f"{cs.ADMIN}/integrations/{cs.INTEGRATION}", token)
    endpoints = integration.get("endpoints") or []
    print(f"接入方 {cs.INTEGRATION}：HTTP {status}，出站地址 {[(e['endpointCode'], e['baseUrl'], e.get('sandbox')) for e in endpoints]}")
    (cs.RUNTIME / "platform-payermax-setup.json").write_text(json.dumps(cs.results, ensure_ascii=False, indent=1) + "\n")
    print(f"管理调用 {len(cs.results)} 次；记录 {cs.RUNTIME / 'platform-payermax-setup.json'}")


if __name__ == "__main__":
    main()
