# 任务卡 A：ledger

先读 `00-common.md` 与 `../technique.md`（D1、L1—L4、第 6 节）。范围：`paytech/ledger/**`、`paytech/tools/arch-rules`（仅当需要）。

1. **D1 操作人**：人工调整提交 / 审批 / 驳回，关账发起、重开发起、关账审批 / 驳回，规则版本新建 / 提交 / 审批 / 驳回（及新增作废），试算发起：API 改为 `@RequestHeader("X-Gateway-Actor-Subject")` + `@RequestHeader("X-Gateway-Session-Id")`，删除请求体 `makerId` / `checkerId`（RequestBody 中仅剩业务字段；审批体保留 `approvedContentHash` / `approvedCloseHash`）；Controller 校验 Header 格式（同基础资料命令的写法）并传入 Request；Processor 用它作为 maker / checker。业务规则不变。更新相关 API JavaDoc（删除"未接统一身份"的注释）。
2. **L1** 人工调整列表、**L2** 规则包列表、**L3** 作废规则草稿（`RuleVersionStatus.VOIDED`；新建版本的在途检查排除 VOIDED；只能制单人）。
3. **L4** 引入 Foundation `paytech-xxl-job-starter`（参照 account 的接入与配置方式），新增 Job Handler `accountingRuleVersionActivation`：查询到期的 APPROVED 版本，逐个调用现有生效逻辑（同一规则包锁），幂等；Handler 独立入口测试。
4. Schema：`t_accounting_rule_bundle_ver.status` 注释加 `VOIDED`；PDManer、Schema SQL（生成并通过逐字节校验）、测试 DDL、change SQL `docs/database/sql/changes/2026-10-20-finance-console-statuses.sql`（只改注释）。
5. 测试：所有受影响测试改为传 Header；新增 L1、L2、L3、L4 场景（筛选、分页、作废权限与状态、到期生效与未到期不动、重复执行幂等）。`./mvnw -o -pl ledger/paytech-ledger-test -am verify -Dtest='com/paytech/ledger/**/*Test'`，库 `mor_ledger_fin3`。
6. 报告写 `A-report.md`，列出最终 API 签名（供 BFF 卡 D 使用）。
