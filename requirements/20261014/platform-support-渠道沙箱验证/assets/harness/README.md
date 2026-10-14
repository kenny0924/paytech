# 渠道沙箱验证本机 harness

仅手工验证使用。沿用 [PayerMax harness](../../../platform-support-PayerMax接入/assets/harness/README.md) 的启动器、`common.py` 与运行目录 `E2E_RUNTIME`（库 `mor_platform_external_api`，需先执行本需求 `db/registration-dml.sql` 与 `db/local-e2e-activate.sql`）。

| 文件 | 作用 |
|---|---|
| `verify_mock_e2e.py` | 经 Web 代理 → Gateway → BFF → Platform：出站地址未标沙箱时运行在发请求前失败（合成渠道无新请求）→ 标记沙箱 → 登记验证账号 `MOCK_LOCAL`（合成密钥）→ 用例 `PAY_THEN_QUERY`、`QUERY_NOT_FOUND` 运行通过，输出回执 |

合成 PayerMax 按订单号末位决定结果：下单末位 1 受理（PENDING），查询同一订单返回 SUCCESS，末位 9 查询为不存在。期望值不渲染模板变量（只有输入渲染），跨步骤一致性用 `EXISTS` 或在后续步骤输入中引用。

回执见 [stage5-mock-receipt.json](../stage5-mock-receipt.json)。
