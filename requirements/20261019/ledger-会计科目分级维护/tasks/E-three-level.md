# 任务卡 E：STANDARD 改为三级科目树（2026-10-20 用户变更）

先读 `00-common.md`，但 **worktree 为 `/Users/kenny/Desktop/Workspace/Code-MoR-chart2`**（基于 `cf39a1ee`，已含 A—D 全部改动，改动已暂存在该 worktree 自己的 index，可以继续修改；不要提交）。Maven 在 `paytech/` 下运行，带 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，测试按 `-Dtest` 限定在本服务包，库 `mor_ledger_chart` / `mor_account_chart`、vhost `/mor-chart`。

合同：`technique.md` 2.1（已改为三级：一级 19 个 3101—3119 不变；新增二级 26 个 3201—3226；29 个既有科目 3001—3029 改为 8 位三级末级；共 74 个）。

## 要改的

1. **ledger 初始化与 SQL**：`paytech/ledger/docs/database/sql/ledger-initial-data.sql`、测试 DDL `paytech-ledger-test/src/test/resources/init/ddl.sql`、`docs/database/sql/changes/2026-10-19-ledger-chart-hierarchy.sql`（全新环境一次执行即得到三级树），以及需求目录 `db/ledger/dml.sql`、`db/ledger/ddl.sql` 中引用编号的部分（需求目录这两份文件属于本卡范围，可以修改），保持 change 文件与需求目录副本一致。dml 的核对 SQL 改为 74 个科目、`bad_parent` 校验三级前缀（`LEFT(c.account_no, LENGTH(p.account_no)) = p.account_no` 且长度 = 上级 + 2）。
2. **account**：`OwnerAccountType.chartAccountNo` 改为 8 位（technique 2.1 末段）；`db/account/ddl.sql` 与 account change 文件中回填 CASE 映射同步。
3. **测试**：所有断言旧编号 / 数量 / 级次的地方（如 `220101`、`AE001-USD-220101-0001`、`-S000001` 前缀、19/29/48、level 2）按三级更新；STANDARD 树完整性测试改为三级（每个末级有二级上级、每个二级有一级上级、编号前缀与长度、类别一致、74 个），并保留 `OwnerAccountType.chartAccountNo` 与科目表一致的断言。BFF / Web 测试夹具若用到旧编号也同步（只改数据，不改逻辑）。
4. **文档**：决定记录（worktree 与主工作区 `agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md` 两份都改：「19 个汇总科目 + 29 个末级的两级树」改为三级描述，账户号示例改 8 位）、worktree `FINANCE_DATABASE.md` §4.1.1 STANDARD 说明、ledger / account README 的示例编号。主工作区只允许改这一份决定记录和需求目录内文件。
5. **验证**：重建 `mor_ledger_chart` / `mor_account_chart`（从新的测试 DDL），运行 ledger、account、BFF（`com/paytech/operations/console/bff/**/*Test`）测试、arch-rules、`node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/account-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs`、`npm --prefix operations-console-web test`。另在一次性 Docker MySQL（`mysql:8.4.11`，`docker run --rm`，结束后删除）中验证：旧 Schema + 新 change 文件 = 新 Schema，且结果 74 个科目。

## 不做

不改代码逻辑（编号规则本已支持 4 级）；不碰 mor-test；不提交。完成后写 `tasks/E-report.md` 并回复摘要（改动文件、测试数、遗留）。
