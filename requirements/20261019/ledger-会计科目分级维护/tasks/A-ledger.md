# 任务卡 A：ledger

先读 `00-common.md`。范围：`paytech/ledger/**`、`scripts/pdmaner/ledger-model.test.mjs`、`scripts/pdmaner/oe-client-model.test.mjs`（仅当表数断言需要）。

## 模型与 SQL

1. `paytech/ledger/docs/database/ledger.pdma.json`：按 `db/ledger/ddl.sql` 加列、加两张新表（含图组归属，参照现有图组；新表放进最贴切的图组），31 → 33 表；更新 `scripts/pdmaner/ledger-model.test.mjs` 表数。
2. 由模型重新生成 `docs/database/sql/ledger-schema.sql`（用 `scripts/pdmaner/generate-oe-client-schemas.mjs` 或仓库现有命令；`oe-client-model.test.mjs` 要求逐字节一致）。
3. `docs/database/sql/ledger-initial-data.sql`：STANDARD 改为 technique 2.1 的树（19 个汇总科目 + 29 个末级带编号与上级，全部 ACTIVE）。
4. 新增 `docs/database/sql/changes/2026-10-19-ledger-chart-hierarchy.sql`：内容与需求目录 `db/ledger/ddl.sql` + `dml.sql` 一致（可直接引用其执行顺序说明，按现有 changes 文件格式写头部）。
5. 测试 DDL `paytech-ledger-test/src/test/resources/init/ddl.sql` 同步（Schema + 初始数据）。
6. `node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs` 通过。

## 代码（以 technique 2.2—2.3、2.8 为合同）

- `ChartAccount` DataObject 加 `accountNo`、`accountStatus`（内部枚举 `ChartAccountStatus`，CodeEnumTypeHandler 显式注册）；`LedgerAccount` 加 `accountNo`。Mapper XML 列与插入同步。
- 纯规则 `ChartAccountNoRule`（编号格式、类别首位、上级前缀与长度、级次）。
- 4 个维护 Processor（`CreateChartAccountProcessor`、`UpdateChartAccountProcessor`、`DisableChartAccountProcessor`、`EnableChartAccountProcessor`），继承 `AbstractLockProcessor`，`lockCommand` 使用新 `AccountingLocks.chart(chartId)`（LOCK）；`CreateLedgerBookProcessor` 改为同锁。Context `ChartAccountChangeContext`；`LedgerSetupService.saveChartAccountChange(context)` 一个事务写科目（插入或条件更新、校验行数、成功后回写原对象）+ `t_chart_account_change`。操作人来自 `X-Gateway-Actor-Subject`（Controller 传入 Request）。
- 规则见 technique 2.2：采用冻结（存在 `t_ledger_book.chart_id`）、有下级不能改编号 / 上级、无环、上级为启用的汇总科目且类别相同、无变化直接返回、停用的引用检查（当前生效规则版本规则行、`t_close_account_rule`、`StandardAccountCodes` 中 account 目录代码；汇总科目有启用下级）、启用要求上级启用。
- 停用生效四处：`CreateAccountProcessor`、`TransactionPostingServiceImpl.resolveAccount` 新建分支、`PeriodCloseServiceImpl.ensureResultAccount` 新建分支、`CreateRuleVersionProcessor.checkPostingAccount`；新错误码 `CHART_ACCOUNT_INACTIVE`（建账户），规则版本用既有方式拒绝。
- 账户号：`t_account_no_sequence` 的 Repository（`INSERT … ON DUPLICATE KEY UPDATE last_value = LAST_INSERT_ID(last_value + 1)` 后取 `LAST_INSERT_ID()`，同一连接、同一事务）；三条建户路径统一调用一个纯组装方法生成 `{主体段}-{币种}-{科目编号}-{序号≥4位}`；FE 主体段用 `t_finance_entity.entity_code`。
- `LedgerSetupErrorCode` 新增 technique 2.3 的 8 个 Code（相邻枚举项空一行）。
- 新接口 L1—L6（API 在 `paytech-ledger-api`，Controller 在已有 `LedgerSetupQueryController` / `LedgerSetupController` 或现有对应 Controller，按现有分组放置），既有接口 M1—M4 的字段与筛选（M1 排序改为 `account_no`；M2 `accountNo` 精确筛选与 `accountName`；M3 行加 `accountNo`、`accountName`；M4 `lines` 加字段 + `summaryLines`）。
- 级次汇总：一个纯计算组件（建议 `ChartBalanceRollup`），输入科目列表 + 末级聚合，输出带层级的行；L6 与 M4 共用。试算平衡持久化与 `TrialBalanceContentHash` 不改。
- 测试：technique 2.7 A 行全部场景；另加 STANDARD 树完整性测试，并断言 `OwnerAccountType` 每个代码的 `chartAccountNo`（B 卡新增，若 B 尚未完成则先断言科目表中这些代码的编号等于 technique 2.1 表）与科目表一致。所有既有测试保持通过（STANDARD 改为树后，涉及 `postingAllowed` 列表、科目数量、排序的断言按新事实调整）。
- 运行：`./mvnw -o -pl paytech/ledger/paytech-ledger-test -am verify`，测试库 `mor_ledger_chart`。

## 不做

不改 billing、account 代码；不改 BFF / Web；不注册路由；不碰 `TrialBalanceApi.runTrialBalance` 的合同。

## 补充

- 新表 `t_chart_account_change`、`t_account_no_sequence` 登记到 `paytech/tools/arch-rules` 的表归属（`TableOwnership` 及其生成来源 / `TableOwnershipAutoTest`，按现有 ledger 条目格式），只加这两项。
- 运行 `./mvnw -o -pl paytech/tools/arch-rules -am verify` 确认归属规则通过。
