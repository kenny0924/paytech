# 任务卡 B：account

先读 `00-common.md`。范围：`paytech/account/**`、`scripts/pdmaner/account-model.test.mjs`。

## 模型与 SQL

1. `paytech/account/docs/database/account.pdma.json`：`t_owner_account` 加 `account_no` 与唯一索引、新增 `t_owner_account_no_sequence`（按 `db/account/ddl.sql`），9 → 10 表，放入合适图组；更新 `scripts/pdmaner/account-model.test.mjs`。
2. `docs/database/sql/account-schema.sql`、测试 DDL `paytech-account-test/src/test/resources/init/ddl.sql` 同步（account 模型直接维护 JSON，Schema 若有生成脚本则用脚本，否则手工保持一致）；新增 `docs/database/sql/changes/2026-10-19-owner-account-no.sql`（与需求 `db/account/ddl.sql` 一致）。

## 代码

- `OwnerAccountType` 增加静态属性 `chartAccountNo`（technique 2.1 末段六个编号），JavaDoc 说明 ledger 测试核对一致、运行时不读 ledger。
- `OwnerAccount` DataObject 加 `accountNo`；Mapper XML 列、插入、分页筛选（`accountNo` 精确）同步。
- 账户号序列 Repository（`t_owner_account_no_sequence`，同 A 卡的 `LAST_INSERT_ID` 方式，在开户同一事务中调用）；`AccountPostingWriter.resolveAccount` 新建分支生成 `{主体段}-{币种}-{科目编号}-S{序号≥6位}`，FE 主体段 `FE{entity_id}`；已存在账户不改。
- API：`OwnerAccountResponseBody` 加 `accountNo`；`OwnerAccountQueryApi` 列表加可选 `accountNo` 精确筛选（规则：提供 `accountNo` 时 entityType / entityId / currency 仍必填，保持现有合同简单）；`AccountPostingResponseBody` 的流水行加 `accountNo`。`AccountPosted` 事件载荷不加。
- 测试：入账 Handler 入口：首次开户账户号格式、同主体同科目第二个 Owner 序号递增、不同桶各自取号、同一账户再次入账账户号不变；查询入口按账户号筛选命中 / 不命中；批次详情带账户号；事件载荷不含账户号。
- 运行：`./mvnw -o -pl paytech/account/paytech-account-test -am verify`，测试库 `mor_account_chart`。

## 不做

不改 ledger、billing；不改 `BalanceQueryApi`。

## 补充

- 新表 `t_owner_account_no_sequence` 登记到 `paytech/tools/arch-rules` 的表归属（按现有 account 条目格式），只加这一项；A 卡同时会加 ledger 两项，注意同一文件增量编辑，不整文件覆盖。
- 运行 `./mvnw -o -pl paytech/tools/arch-rules -am verify` 确认归属规则通过。
