# PayerMax 退款本机验证 harness

仅手工验证使用，不进入生产应用。运行环境、启动步骤与运行目录沿用 [PayerMax 接入 harness](../../../platform-support-PayerMax接入/assets/harness/README.md)（本机独立库 `mor_platform_external_api`，Web 代理 → Gateway → BFF → Platform）；本目录脚本从那里导入 `common.py` 与 `configure.py` 的字段、外层与脚本定义。

| 文件 | 作用 |
|---|---|
| `configure_refund.py` | 保存或按版本更新标准 API `REFUND`、`REFUND_QUERY` 与指定接入方下的 `REFUND`、`REFUND_QUERY` 原子 API 和两条流程（[技术方案第 3、4 节](../../technique.md#3-标准-api渠道无关草案)）；前提是该接入方已由 `configure.py` 保存 |
| `verify_mock_refund.py` | 对 `PAYERMAX_MOCK` 跑退款全部场景并写回执；合成渠道的退款接口在 `PayerMaxMock.java` |
| `uat_refund_cases.py` | 在接入方 `PAYERMAX` 下保存并经渠道沙箱验证运行 UAT 退款用例（前提：出站地址已标沙箱、验证账号 `UAT_MAIN` 已录入）；带“已付款订单号 金额”参数时运行 `UAT_REFUND_THEN_QUERY` |

步骤：按接入 harness 启动服务并执行其 `configure.py` 后，在本目录执行 `python3 configure_refund.py PAYERMAX_MOCK`，再执行 `python3 verify_mock_refund.py`。

阶段 2 结果见 [stage2-receipt.json](../stage2-receipt.json)。

mor-test：运行目录只放管理员登录信息（取自不提交的 `test-environment/private/access.md`）与 `web.json`（`http://localhost:28080`），依次 `configure_refund.py PAYERMAX`、`uat_refund_cases.py`。结果见 [test-uat-receipt.json](../test-uat-receipt.json)。
