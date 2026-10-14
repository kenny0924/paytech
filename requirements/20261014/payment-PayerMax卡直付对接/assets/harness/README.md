# PayerMax 卡直付本机验证 harness

仅手工验证使用，不进入生产应用。运行环境、启动步骤与运行目录沿用 [PayerMax 接入 harness](../../../platform-support-PayerMax接入/assets/harness/README.md)（本机独立库 `mor_platform_external_api`，Web 代理 → Gateway → BFF → Platform）；本目录脚本从那里导入 `common.py` 与 `configure.py`。

| 文件 | 作用 |
|---|---|
| `configure_card.py` | 把给定接入方的 `PAY` 从收银台改为卡直付：删除旧流程 → 改标准 API `PAY` → 改原子 API `ORDER_AND_PAY` → 创建卡直付流程（[技术方案第 4、11 节](../../technique.md)）。同一库中实现 `PAY` 的接入方要一起传入 |
| `verify_card.py` | 经渠道沙箱验证运行卡直付用例：`mock` 对 `PAYERMAX_MOCK`（7 个），`uat` 对 `PAYERMAX`（4 个，按 `subject` 控制 UAT 结果）；检查证据卡号只有前 6 后 4、全部证据没有卡值原值 |

测试卡为 PayerMax 公布的 UAT 测试卡，录入验证账号凭据 `testCard.*`；沙箱运行不调用 Payment。

步骤：按接入 harness 启动服务并执行其 `configure.py` 后，在本目录执行 `python3 configure_card.py PAYERMAX_MOCK PAYERMAX`、`python3 verify_card.py mock`、`python3 verify_card.py uat`。

阶段 2 回执：[mock](../stage2-mock-receipt.json)、[uat](../stage2-uat-receipt.json)。
