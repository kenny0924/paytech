# 任务卡 D · Platform 注册数据、环境配置、requirements-site 与文档回写

先读 `00-common.md`、`T0-report.md`。设计：`technique.md` 2.3「Platform 注册 DML」、2.2 BFF 路由表 R19–R21、`requirement.md` Q-07；前序先例：`requirements/20261018/operations-console-财务基础资料与账本余额查询/db/platform-support-service/`（registration-dml.sql、activation-local.sql）与 `release/test-activate.sql`、Platform 初始化 SQL 块 97 / 98（`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 2282 行起）。

## 允许改动的路径

`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（只追加新块）、`paytech/platform-support/**/operations/console-resource-capabilities.json`、`paytech/platform-support/paytech-platform-support-test/**`（只为新 Console Resource 的基线断言）、`test-environment/compose.applications.yaml`（只删 account 的 `ACCOUNT_LEDGER_SERVICE_URL` 一行）、`requirements-site/content/**`、`requirements-site/tests/**`、`REQ/db/platform-support/**`、`REQ/release/**`、`system-design/finance-account-ledger-v3.md`、`agent-docs/CURRENT_IMPLEMENTATION.md`、`agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md`、`paytech/account/README.md` 与 `paytech/ledger/README.md` 不归本卡（A / B 负责）。

## 1. Platform 注册数据

- 初始化 SQL 追加独立块 99（格式逐字照块 98：块头注释、`START TRANSACTION`、变量、`COMMIT`、Rollback 说明、行数统计）：`t_gateway_route` 3 条（`operations-finance-owner-accounts` GET `/api/operations/finance/owner-accounts/v1` → `/finance/owner-accounts/v1`；`operations-finance-owner-account-entries` GET `…/owner-accounts/v1/{accountId}/entries`；`operations-finance-account-posting` GET `…/account-postings/v1/{postingId}`），DISABLED，`OPERATIONS_JWT` / `HUMAN_PERMISSION` / 权限 `900000000000097101`，Target 同块 97；`t_gateway_route_header` 6 条；`t_gateway_route_query_param`：R19 `entityType, entityId, currency, ownerType, ownerId, accountCode, balanceBucket, page, size`，R20 `page, size`；`t_gateway_guard_rule` 每路由 6 条（SHARED_ROUTE 300 / 50，CLIENT_IP 30 / 3，AUTHENTICATED_PRINCIPAL 30 / 2，与 R08 一致）；`t_console_resource` 1 条 `OPERATIONS_FINANCE_SUBLEDGER`（`/finance/subledger`，父 `OPERATIONS_FINANCE`，READ 权限，排序在 BALANCES 之后；字段照块 97 的 `OPERATIONS_FINANCE_BALANCES`）。ID 用 `9000000000000993xx / 994xx / 995xx / 996xx / 997xx` 段。
- `console-resource-capabilities.json` +1；平台测试中若有 Console Resource / 路由基线计数断言则同步（`OperationsCatalog` 不加权限）。
- `REQ/db/platform-support/registration-dml.sql`（块 99 阅读副本）、`REQ/db/platform-support/activation-local.sql` 与 `REQ/release/test-activate.sql` / `test-deactivate.sql`（照前序需求：激活 3 条路由 + 1 个 Console Resource，角色授权已存在不重复）。
- 运行 `./mvnw -B -o verify -pl platform-support/paytech-platform-support-test -Dtest='OperationsRoleManagementTest,OperationsAuthorizationTest,OperationsAccountManagementTest'`（或该模块既有的 Console Resource 基线测试）通过。

## 2. test-environment

`compose.applications.yaml` account-service 的 `ACCOUNT_LEDGER_SERVICE_URL` 行删除；其余不动（ledger 的 `LEDGER_ACCOUNT_SERVICE_URL` 保留）。

## 3. requirements-site（Q-07：只改直接冲突条目）

- `content/requirements.ts`：E2E-06（出款锁定改为 account 桶移动 AVAILABLE → LOCKED，BANK_CASH 额度改为 account `CASH_ACCOUNT` 桶占用，UNKNOWN 不释放）、E2E-09（关账核对改为 `CHECK_ACCOUNT_POSTING_PROGRESS` 两段进度检查）、E2E-10（成熟改为 account 到期转可用桶移动，不产生 Journal）、E2E-12 与 FIN-21（缴税锁定 BANK_CASH 改为 account 桶移动）、FIN-18 / FIN-19（成熟与出款锁定 / 释放在 account，只记流水）、FIN-I16（桶说明以 account 为准）、Finance 系统发布事件（删除 `BalanceChanged` / `PayableBalanceMatured`，加 `AccountPosted`，订阅 `EconomicFactRecorded` 只有 account）；`content/global-use-case-sequences.mjs` 对应 E2E-06 / 08 / 09 / 10 / 11 / 12 步骤文字同步；三处钉住措辞的测试（`tests/site-content.test.mjs` L630 / L662 附近、`tests/global-use-case-sequences.test.mjs` L198–202、`tests/domain-decisions.test.mjs` L396）改为新措辞。不重写 Finance 单系统的整体结构。
- `npm --prefix requirements-site test`：本卡相关用例通过；既有 10 例失败（见 `agent-docs/CURRENT_IMPLEMENTATION.md`）保持原样并在报告中单列。

## 4. 文档回写

- `system-design/finance-account-ledger-v3.md`：第 4.1 节表数 8 → 9（新增 `t_account_posting_stream` 一行与关系图一行）、第 7 节聚合 ID 改 `{OE|FE}.{entity_id}.{currency}.{shard_no}`（分隔符 `.`，见 technique 2.1）、第 8.2 节补「ledger 自校验首条序号；修复后经 `catch-up` 命令按 HTTP 追平，死信重放按已消费跳过」、第 10 节 account 行补「`AccountPostingApi` / `OwnerAccountQueryApi` / `BalanceMovementApi`」。只改这些点。
- `agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md`：Implementation status 改为「In progress（2026-10-19 阶段 4，需求 requirements/20261019/…）」，第 6 节阶段 2 条目指向本需求目录。
- `agent-docs/CURRENT_IMPLEMENTATION.md`：在 account / Finance 相关条目下新增一条「account 明细账与 ledger 控制科目重构（2026-10-19，阶段 4 进行中，未提交、未执行 SQL、mor-test 仍为 11b 镜像）」，链接需求目录；不改其他条目。

## 5. 报告

`REQ/tasks/D-report.md`：块 99 行数统计、ID 段、激活 SQL 内容、requirements-site 改动条目与测试结果、文档改动点。
