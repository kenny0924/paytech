# 任务卡 B：billing

先读 `00-common.md` 与 `../technique.md`（D1、B1、B2、第 6 节）。范围：`paytech/billing/**`。

1. **D1**：`BillingFactCommandApi` 登记与审批改读 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`，删除请求体 `makerId` / `checkerId`；业务规则不变。
2. **B1** `GET /billing/v1/facts/search`（`BillingFactQueryApi`，分页 `PagingResponseBody`，筛选与排序见 technique）。
3. **B2** `POST /billing/v1/facts/{factId}/void`：DRAFT → `VOIDED`（`BillingFactStatus` 新增，条件更新校验行数），不发布事件、不登记补偿；已作废重复调用返回原事实；非草稿 400。确认 account / ledger 消费与读取逻辑对 VOIDED 无影响（VOIDED 从不发布）。
4. Schema：`t_billing_fact.status` 注释加 `VOIDED`；PDManer（billing 模型维护方式以仓库现状为准）、Schema、测试 DDL、change SQL `docs/database/sql/changes/2026-10-20-billing-fact-void.sql`。
5. 测试：受影响测试改传 Header；新增 B1（筛选、分页、含草稿）与 B2（作废、重复、非草稿拒绝、作废后不能批准）场景。库 `mor_billing_fin3`。
6. 报告 `B-report.md` 列出最终 API 签名。
