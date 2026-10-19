# D 线交付报告：Platform 注册数据、环境配置、requirements-site 与文档回写（2026-10-19）

结论：块 99（39 行，休眠）、Console 能力目录 +1、阅读副本与三份激活 / 停用 SQL、Compose 删行、requirements-site 冲突条目与钉住断言、三份文档回写全部完成。Platform 指定测试 44/44 通过；requirements-site 127/137，失败的 10 例与改动前完全相同（断言期望逐条比对一致）。未执行任何 SQL，未动容器，未执行改变索引的 git 命令。

**待用户决定（1 项，主会话 2026-10-19 已同意本线处理方式）：** 卡片要求从 Finance `publishes` 删除 `PayableBalanceMatured`，但 Commerce 仍订阅它（`requirements.ts` Commerce `subscribes`），COM-06 与 Funds Availability 决定行（原 L530）规定只有它能把 Order 投影推进到 AVAILABLE / FULLY_OFFSET，E2E-10 也有 finance → commerce / platform 两条该事件消息。删除会留下悬空订阅并改变本决定未涉及的 Commerce 合同。本线**保留** `PayableBalanceMatured`，只删除 `BalanceChanged`、新增 `AccountPosted`，并把 E2E-10 中该事件改为「account 成熟桶移动完成后发布（不来自 ledger Journal）」、载荷「Journal」改为「account 入账批次」。2026-10-18 决定未涉及该 Commerce 合同，需用户决定是否保留及由谁发布；若删除，需同步 Finance `publishes` 与 Commerce 三处。

## 1. 已读规范与依据

- `tasks/00-common.md`、`tasks/T0-report.md`、`tasks/D-registration-docs.md`
- `technique.md` 2.1（聚合 ID `.` 分隔）、2.2（R19–R21）、2.3（Platform 注册 DML）、2.4（画板 13–15 命名）；`requirement.md` Q-07
- 前序先例：`requirements/20261018/operations-console-财务基础资料与账本余额查询/db/platform-support-service/{registration-dml.sql,activation-local.sql}`、`release/{test-activate.sql,test-deactivate.sql}`，初始化 SQL 块 97 / 98
- `design-discuss/finance-account-ledger-v3/design.md` 4.1、4.4、7、8.2、8.3、8.5、10；决定记录第 6 节
- 本线不改 Java 生产代码与表结构，未重读 `DEVELOPMENT_CONVENTIONS.md` / `WRITE_API_EXAMPLE.md` 等实现规范；写接口第 11 节自查不适用（无接口实现）。

## 2. 改动清单

| 类型 | 路径 | 内容 |
|---|---|---|
| 修改（追加） | `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾新增块 99「Operations finance subledger query console」 |
| 修改 | `paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` | +1 `OPERATIONS_FINANCE_SUBLEDGER`「明细账查询」`/finance/subledger`，无按钮（24 → 25 项） |
| 新增 | `REQ/db/platform-support/registration-dml.sql` | 块 99 阅读副本（头注释注明唯一执行源） |
| 新增 | `REQ/db/platform-support/activation-local.sql` | 本机 `mor_platform_v2e2e` 激活 |
| 新增 | `REQ/release/test-activate.sql`、`REQ/release/test-deactivate.sql` | mor-test 激活 / 停用 |
| 修改 | `test-environment/compose.applications.yaml` | account-service 删除 `ACCOUNT_LEDGER_SERVICE_URL` 一行，其余不动 |
| 修改 | `requirements-site/content/requirements.ts` | 见第 5 节 |
| 修改 | `requirements-site/content/global-use-case-sequences.mjs` | 见第 5 节 |
| 修改 | `requirements-site/tests/site-content.test.mjs`、`tests/global-use-case-sequences.test.mjs` | 钉住措辞改为新措辞 |
| 修改 | `design-discuss/finance-account-ledger-v3/design.md` | 见第 6 节 |
| 修改 | `agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md` | 实现状态与第 6 节阶段 2 条目 |
| 修改（追加一条） | `agent-docs/CURRENT_IMPLEMENTATION.md` | 在「account / ledger 明细账与控制科目（2026-10-18 决定…）」后新增一条 |

`paytech-platform-support-test/**` 未改：该模块没有 Console Resource / 路由数量基线断言（`OperationsCatalog` 只含权限，按卡不加）。

## 3. 块 99

前置：块 97 的 `FINANCE_ACCOUNTING_READ`（900000000000097101）与父菜单 `OPERATIONS_FINANCE`（900000000000097301）；不新增权限或角色授权。路由与菜单 `DISABLED`，Target `900000000000000001`（同块 97），`OPERATIONS_JWT` / `HUMAN_PERMISSION`，`body_limit_bytes 0`、5000 / 10000 ms。

| 表 | 行数 | ID 段 | 内容 |
|---|---|---|---|
| `t_console_resource` | 1 | 9000000000000993xx（99301） | `OPERATIONS_FINANCE_SUBLEDGER`，MENU，父 97301，「明细账查询」，`/finance/subledger`，componentKey 同 code，READ，sort 40（BALANCES 为 30） |
| `t_gateway_route` | 3 | 994xx（99401–99403） | `operations-finance-owner-accounts` GET `/api/operations/finance/owner-accounts/v1` → `/finance/owner-accounts/v1`；`operations-finance-owner-account-entries` GET `…/owner-accounts/v1/{accountId}/entries`；`operations-finance-account-posting` GET `…/account-postings/v1/{postingId}` |
| `t_gateway_route_header` | 6 | 995xx（99501–99506） | 每路由 `authorization` CORS_REQUEST_ALLOWED + `origin` FORWARD_REQUEST |
| `t_gateway_route_query_param` | 11 | 996xx（99601–99611） | R19：entityType, entityId, currency, ownerType, ownerId, accountCode, balanceBucket, page, size；R20：page, size；R21 无 |
| `t_gateway_guard_rule` | 18 | 997xx（99701–99718） | 每路由 6 条：SHARED_ROUTE 300 / 50，CLIENT_IP 30 / 3，AUTHENTICATED_PRINCIPAL 30 / 2（同 R08） |
| 合计 | **39** | | 回滚：按 9000000000000993xx–997xx 删除 |

全仓库检索确认上述 ID 段无既有使用。

## 4. 激活 SQL

三份文件结构同前序需求，只动本块 ID：

```sql
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@t
 WHERE route_id BETWEEN 900000000000099401 AND 900000000000099403 AND status='DISABLED';   -- 3
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@t
 WHERE resource_id = 900000000000099301 AND status='DISABLED';                             -- 1
UPDATE t_role SET version=version+1, update_time=@t WHERE role_id=900000000000000510;      -- 1
```

- `activation-local.sql`：本机 `mor_platform_v2e2e`，预期 3 / 1 / 1；前置为 20261018 的本机激活已执行。
- `test-activate.sql`：mor-test `mor_platform`，预期 3 / 1 / 1；前置为 20261018 mor-test 激活已执行、六个相关容器已换候选镜像。
- `test-deactivate.sql`：反向置 DISABLED，预期 3 / 1 / 1；不动 `FINANCE_ACCOUNTING_READ` 授权（属 20261018 需求），39 行注册数据保留。
- 角色授权已存在（READ），不重复写 `t_role_permission`。

## 5. requirements-site（Q-07，只改直接冲突条目）

`requirements.ts`：

| 条目 | 改动 |
|---|---|
| E2E-06 | Owner 锁定为 account 桶移动 AVAILABLE → LOCKED；银行现金额度为 account `CASH_ACCOUNT` 的 BANK_CASH 桶；UNKNOWN 占用且不释放 |
| E2E-09 | ledger 只记控制科目；关账以 `CHECK_ACCOUNT_POSTING_PROGRESS` 两段进度检查确认追平 |
| E2E-10 | 成熟改为 account `FACT_MATURITY` 桶移动，只记流水、不产生 Journal |
| E2E-12 | 税款资金承诺为 account 桶移动占用 BANK_CASH 额度 |
| FIN-18 | 删除 `PAYABLE_BALANCE_MATURED:<economics_id>` 与「同一 Economics 事务和 Journal」，改为 account 定时任务 `FACT_MATURITY` 桶移动（幂等），不产生 Journal |
| FIN-19 | PAYOUT_LOCK / PAYOUT_RELEASE 为 account 桶移动，只记流水；FAILED 只释放 account BANK_CASH 额度 |
| FIN-21 | 不再锁 `BANK_CASH t_account` 行：`TAX_PAYMENT_LOCK` / `TAX_PAYMENT_RELEASE` 桶移动；`available_cash` 即 BANK_CASH AVAILABLE 桶（原公式的占用由 LOCKED 桶表达）；对账完成仍写 `Dr TAX_PAYABLE / Cr BANK_CASH` Journal |
| FIN-I16 | 句首加「余额桶以 account 明细账为准」 |
| Finance `publishes` | 删除 `BalanceChanged`，新增 `AccountPosted`；`PayableBalanceMatured` 保留（见开头待确认项） |

`global-use-case-sequences.mjs`：

| 序列 | 改动 |
|---|---|
| E2E-06 | summary；创建 Payout 改为事务外 account `PAYOUT_LOCK` 桶移动（不追加 Journal）；Execution 创建先登记补偿事件再锁 BANK_CASH 额度（设计 8.3）；ABORTED / UNKNOWN / FAILED 三个分支改为 account 额度释放或保持；`PAYOUT_RELEASE` 为 account 桶移动 |
| E2E-08 | 现金 Journal 锁的是 ledger BANK_CASH 科目账户，额度在 account；LATE_DEBIT 同 |
| E2E-09 | 关账检查删除「统一账户余额 / 受限资金」核对，改为两段进度检查（billing 已发布事实数 = account 已应用事实数；每条流 ledger 已消费序号 = account 最大序号） |
| E2E-10 | 成熟步骤改为 account 按事实与账户的 `FACT_MATURITY` 移动（`source_ref = factId:factVersion:accountId`），不产生 Journal；`PayableBalanceMatured` 改为 account 成熟桶移动完成后发布（不来自 ledger Journal），载荷「Journal」改为「account 入账批次」 |
| E2E-11 | 退款冲减的 PENDING / AVAILABLE 标注为 account 明细账桶（扣减顺序本身属设计 8.6，未改） |
| E2E-12 | 三处 `BANK_CASH t_account` 行锁改为 `TAX_PAYMENT_LOCK` / `TAX_PAYMENT_RELEASE` 桶移动；`available_cash` 标注为 AVAILABLE 桶 |

测试断言改动：

- `site-content.test.mjs`：FIN-21 断言改为 `account 桶移动 TAX_PAYMENT_LOCK.*BANK_CASH.*FAILED.*TAX_PAYMENT_RELEASE.*完整对账.*BANK_CASH`，并新增不得出现 `BANK_CASH t_account`；FIN-18 断言改为 `account.*FACT_MATURITY.*PENDING → AVAILABLE.*不产生 Journal`，并新增不得出现 `PAYABLE_BALANCE_MATURED`。
- `global-use-case-sequences.test.mjs`：E2E-10 成熟消息断言改为 `account.*FACT_MATURITY.*source_ref.*不产生 Journal.*已成熟`；E2E-12 两条税务断言改为桶移动措辞。
- `domain-decisions.test.mjs` L396 无需改动（渲染内容不含被禁词，改后仍通过）。

未改、但仍与本决定措辞不完全一致的条目（卡片未列，按 Q-07「不重写整体结构」保留，供后续 Finance 目录重写需求处理）：

- `requirements.ts` 决定行「初次成熟键为 PAYABLE_BALANCE_MATURED:<economics_id>…」（原 L558）
- FIN-13 / FIN-14 中 `PAYOUT_LOCK` 与 `CREATED.*BANK_CASH` 的表述（被测试钉住，语义未与新模型矛盾）
- 卡片的「订阅 `EconomicFactRecorded` 只有 account」：requirements-site 中不存在 `EconomicFactRecorded`（Finance 以单系统出现，内部事件未列入），无可改条目。

## 6. 文档回写

- `design-discuss/finance-account-ledger-v3/design.md`：4.1 表格新增 `t_account_posting_stream` 一行，表数「8 → 8（删 1 增 1）」改为「8 → 9（删 1 张，增 2 张）」；4.2 关系图新增 `t_account_posting_stream ||--o{ t_account_posting`；第 7 节聚合 ID 改为 `{OE|FE}.{entity_id}.{currency}.{shard_no}`（例 `OE.AE001.USD.0`，写明 `.` 分隔原因并链接 technique 2.1）；8.2 新增首条序号自校验、`POST /posting-streams/v1/catch-up` 按 HTTP 追平、死信重放按已消费跳过；第 10 节 account 行补 `AccountPostingApi` / `OwnerAccountQueryApi` / `BalanceMovementApi`。
- 决定记录：Implementation status 改为「In progress（2026-10-19 阶段 4，需求 …；未提交、未执行 SQL，mor-test 仍为第 11b 步镜像）」；第 6 节阶段 2 条目指向本需求目录。
- `CURRENT_IMPLEMENTATION.md`：新增一条「account 明细账与 ledger 控制科目重构（2026-10-19，阶段 4 进行中，未提交、未执行 SQL、mor-test 仍为 11b 镜像）」，链接需求与技术方案，列出合同 / 实现 / 控制台 / 块 99 / Compose 范围；其他条目未改。

## 7. 测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
# 加载 local-environment/.env.local 所需键，PAYTECH_REDIS_PASSWORD 回落 MOR_REDIS_PASSWORD
cd paytech && ./mvnw -B -o verify -pl platform-support/paytech-platform-support-test -am \
  -Dtest='OperationsRoleManagementTest,OperationsAuthorizationTest,OperationsAccountManagementTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
npm --prefix requirements-site test
```

| 项 | 结果 |
|---|---|
| Platform：OperationsAccountManagementTest / OperationsRoleManagementTest / OperationsAuthorizationTest | 14 + 11 + 19 = **44/44**，BUILD SUCCESS |
| requirements-site | **127/137**；改动前同为 127/137 |

- 不带 `-am` 时测试模块从 `~/.m3` 解析到旧的 platform-support-api JAR，编译失败（`onboardingfiles` 包不存在），与本卡无关；加 `-am` 后通过（上游模块被 `-Dtest` 过滤为 0 例）。
- requirements-site 既有 10 例失败（未改动，与 `CURRENT_IMPLEMENTATION.md` 记载一致）：checkout renders the immutable order context…；Payment owns refund capacity…；Commerce Client API V1 freezes exactly ten…；Commerce authority freezes the generated 15-table…；the current implementation ledger owns core model snapshots…；the current ledger owns the Platform snapshot…；Project context owns exactly the current public control plane…；Commerce design and navigation expose…；Java tests are organized by executable entry…；shared writing conventions retain Payment responsibilities…。改动前后失败断言的 `expected` 逐条比对一致。
- 中途改完内容、未改测试时有 3 例新失败（FIN-21 税务、FIN-18 成熟、E2E-12 税务序列），均为钉住旧措辞，已按新措辞更新后通过。

## 8. 偏离与未完成

1. `PayableBalanceMatured` 保留（见开头），主会话已同意，作为待用户决定项。
2. Compose 中 account 注释「另承载 accountBalanceReconciliation，任务未登记」仍在：卡片要求「其余不动」，该任务由 A 线删除后可一并清理。
3. 块 99 菜单 sort_order 取 40（卡片只说排在 BALANCES 之后）。
