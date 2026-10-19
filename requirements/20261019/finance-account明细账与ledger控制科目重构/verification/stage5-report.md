# 阶段 5 本机综合验证报告（任务卡 E，2026-10-19）

结论：本机 billing → account → ledger → Operations 控制台全链路验收通过。接口验收 **58/58**，浏览器验收首轮 **30/30**，C-1 修复后重跑 **31/31**（见第 6 节）。只读页面回归 **27/34**：7 个失败页都属于本次不在启动范围内的 risk-decision / compliance 服务（503），4 个财务页全部通过。清理后 billing / account / ledger 共 49 张表行数回到基线。发现 1 个本需求 SQL 缺陷与 1 个控制台小缺口，两者都已由主会话修复并复验；另有 1 项部署注意事项。

已读：`tasks/00-common.md`、`tasks/E-local-verification.md`、`T0/A/B/C/D-report.md`、`requirement.md` 第 5 节（AC-01—20）、`technique.md` 2.1—2.5，以及前序先例 `requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/*`、同目录 `delivery.md` 阶段 5、`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/{run-service.sh,e2e_http.py}`。

## 1. 环境事实与已执行动作

### 1.1 备份

目录 `local-environment/e2e/backup/20261019133614/`（已被 .gitignore 忽略），root 口令只从 `.env.local` 读取，未打印。

| 库 | 结果 |
|---|---|
| `mor_account_v2e2e` | rc 0，32 105 字节，8 张表 |
| `mor_ledger_v2e2e` | rc 0，88 973 字节，31 张表 |
| `mor_platform_v2e2e` | rc 0，3 221 139 字节，57 张表 |
| `mor_billing_v2e2e`（主会话追加授权） | rc 0，1 505 字节，空库 |

### 1.2 SQL（root，按序）

| 步骤 | 输出与核对 |
|---|---|
| account `REQ/db/account/ddl.sql` | 执行前核对**不是 0 行**：`t_account_balance` 2、`t_balance_applied_fact` 12、`t_balance_applied_entry` 12，其余 0。这些是当天 08:52 UTC 写入的 11b 合成测试数据（AE001 / SA001，Client 300001，ledger 侧无对应指令）。已备份，且设计 8.4 要求删除这些表，因此照常执行。执行后 9 张表：`t_account_entry`、`t_account_posting`、`t_account_posting_stream`、`t_balance_applied_fact`、`t_balance_movement`、`t_compensation_event`、`t_consumed_aggregate_version`、`t_consumed_event`、`t_owner_account`。遗留的 12 条 `ECONOMIC_FACT` 消费记录保留（基础设施表不动，不影响新事实） |
| ledger `REQ/db/ledger/ddl.sql`（首次） | 全部 ALTER 已生效；Owner 账户与指令计数都是 0。**第 49 行失败**：`ERROR 1054 Unknown column 'account_id' in 'IN/ALL/ANY subquery'`。`t_trial_balance_line` 与 `t_ledger_period_balance` 只有 `chart_account_id`，见第 4 节缺陷 D-1 |
| ledger 补跑（主会话修复后授权） | 只补跑中断后剩余的 `DELETE FROM t_ledger_account WHERE owner_type IN ('CLIENT','LOCAL_PARTNER')`（0 行）与两条执行后核对：31 张表，`account_entry_id` 列 1 个，`idx_instr_line_entry` 存在。注释已核对：`t_close_step.step_type` 含 `CHECK_ACCOUNT_POSTING_PROGRESS`，`origin_type` 含 `ACCOUNT_POSTING`，`resolution_type` 不再含 `PROJECTION_REBUILD`，`owner_type` 为五值 |
| ledger `REQ/db/ledger/dml.sql` | Owner 角色规则行 0，规则行 **36**（执行前 44） |
| platform `registration-dml.sql`（块 99） | 前置：块 97 / 98 的 18 条路由已 ACTIVE，`FINANCE_ACCOUNTING_READ` 与 `OPERATIONS_FINANCE` 已 ACTIVE，块 99 原为 0 行；阅读副本与初始化 SQL 末尾逐字一致 |
| platform `activation-local.sql` | 影响行数 3 / 1 / 1（路由、菜单、角色版本）。块 99 共 39 行：路由 3 与 Console Resource 1 均为 ACTIVE（Platform 的启用状态值），Header 6、Query 11、限流 18 |
| billing `billing-schema.sql` + `billing-initial-data.sql`（主会话追加授权，仅 `mor_billing_v2e2e`） | 9 张表；`t_currency_spec` USD ACTIVE |

执行输出原文保存在会话 scratchpad 的 `sqlout/01—06`（未入库）。

### 1.3 构建与重启

- 构建命令 `./mvnw -B -o package -DskipTests -pl billing/...,account/...,ledger/...,platform-support/...,operations-console-bff/... -am` 结果为 BUILD SUCCESS，用时 4.7 秒，因为是增量构建。5 个 exec JAR 的时间戳都晚于对应模块（含三个 api 模块）的全部源码与配置。
- `run-service.sh jars` 复制了 18 个服务 JAR。只有 `data-service` 没有 exec JAR，复制它时 `cp` 报错。它不在本次启动范围内，也不影响结果。
- `run-service.sh start/stop` 每次只接受一个服务名，因此按卡片顺序逐个调用：先 stop ops-web、gateway、ops-bff、ledger、account、platform（billing 原本未运行），再 start platform、ledger、account、ops-bff、gateway、ops-web。billing 建库后于 13:53:30 启动。ledger 先于 account 启动。
- 停止后核对，业务端口与管理端口都没有残留监听。所有被停止的进程都有本启动器的 PID 记录，没有动非本启动器的进程。nginx 与 Vault 容器未动。

| 服务 | 启动（+04） | 就绪 | 状态 |
|---|---|---|---|
| platform | 13:39:16 | 11s | UP |
| ledger | 13:39:27 | 4s | UP |
| account | 13:39:31 | 5s | UP |
| ops-bff | 13:39:36 | 4s | UP |
| gateway | 13:39:40 | 6s | UP |
| ops-web（vite，端口 18301） | 13:39:46 | 3s | LISTEN |
| billing | 13:53:30 | 4s | UP |
| nginx（18443—18446） | 沿用 | — | LISTEN |

RabbitMQ `/mor-e2e`：`account.billing.p0—p3` 与 `ledger.account.p0—p3` 各有 1 个消费者。billing、account、ledger 日志没有 ERROR。

## 2. 逐项结果

验收脚本：[local_acceptance.py](./local_acceptance.py)，运行记录 `local-environment/e2e/runtime/subledger-acceptance-20261019135339.json`，OE `ZE887`，事实 `237875841034399744`（100.00）→ 替代 `237875853206269952`（80.00）。浏览器脚本：[local_browser.py](./local_browser.py)，截图 [assets/stage5/browser/](../assets/stage5/browser/)。回归脚本：[test_regression.py](./test_regression.py)。

| AC | 结果 | 证据（检查数） |
|---|---|---|
| AC-01 每事实一批次、序号连续 | 通过 | 接口 4：事实入账、进度 `appliedFactCount=1` / 流 shard 0 序号 1、库中批次序号 1、2 分别对应两条事实 |
| AC-02 版本替代冲回 | 通过 | 接口 6：R20 冲回行借 100.00 且 `reversesEntryId` 为原流水，新行贷 80.00；R19 借 100 / 贷 180 / 净 80；R13 控制科目两行各带 `accountEntryId`；规则行净额替代（RECON 贷 100、借 80），借贷平衡 180 / 180 |
| AC-03 AccountPosted 发布与 ledger 过账 | 通过 | 接口 7：两条 `ACCOUNT_POSTING` 指令，`published_time` 非空，控制科目行 `accountEntryId` 等于 R20 流水 ID，非 Owner 行按规则推导，借贷平衡 |
| AC-04 控制科目 = 明细合计 | 通过 | 接口 2：R10 控制账户 Owner OE ZE887、桶 NONE、净 100.00；替代后 80.00，等于 account 该主体 + 币种 + 科目全部 Owner 账户合计 |
| AC-05 桶移动 | 部分 | 接口 1：AVAILABLE 无账户时 `PAYOUT_LOCK` 返回 400 `ACCOUNT_NOT_FOUND`。幂等与 CASH_ACCOUNT 成功路径没有本机数据来源，由 account auto 测试 `BalanceMovementTest` 覆盖 |
| AC-06 到期转可用 | N/A | billing 没有写 `balance_available_time` 的生产路径（technique Q-02），本机无法造到期事实；由 `BalanceMaturityJobTest` 覆盖 |
| AC-07 关账两段检查 | 通过（成功路径） | 接口 4：ledger 流进度 = account 最大序号 2；发起关账 201 PENDING_CHECK，7 个步骤全部 SUCCEEDED（含 `CHECK_ACCOUNT_POSTING_PROGRESS`，无 `RECONCILE_ACCOUNT_BALANCES`）；Checker 驳回为 REJECTED，期间回到 OPEN。失败关闭路径由 `StartPeriodCloseTest` 覆盖 |
| AC-08 追平 | 部分 | 接口 2：catch-up 200，`caughtUp=true`、`processedCount=0`、`lastSequence=2`；缺操作人 Header 返回 400。卡住后续跑由 `CatchUpPostingStreamTest` 覆盖 |
| AC-09 已关账期间顺延 | N/A | 需要真实关账后再到达事实；本机只做驳回，未关账。由 ledger auto 测试覆盖 |
| AC-10 人工调整拒绝控制科目 | 部分 | 接口 1：调整含 CLIENT_PAYABLE 控制账户时返回 400 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`。FROZEN / CLOSED 告警没有改状态入口，未测 |
| AC-11 按流重建 | 通过 | 接口 1：200，`accountCount=1`、`entryCount=3`、`maxPostingSequence=2`，净额不变，`balance_version` 加一 |
| AC-12 删除项 | 通过（运行面） | 接口 1：ledger `account-totals` 与 account `consistency` 都返回 404。依赖与规则测试属阶段 4 |
| AC-13 既有测试 | N/A | 阶段 4 门禁范围，本卡不重跑 |
| AC-14 总账账户五值 / NONE | 通过 | 接口 7：CHANNEL_ACCOUNT 新建 201；R11 的 CLIENT、LOCAL_PARTNER 被 BFF **422 VALIDATION_ERROR** 拒绝（Body 校验，卡片写的是 400，见第 4 节说明）；控制科目非 NONE 或 Owner 非账簿主体返回 `ACCOUNT_IDENTITY_INVALID`；R10 `ownerType=CLIENT` 返回 400。浏览器 8：画板 04 列表 4 行均为五值且 NONE，画板 05 下拉五值、余额桶 NONE 禁用、提示文案、重复账户错误挂在余额桶字段 |
| AC-15 分录来源与跳转 | 通过 | 接口 3：R12 / R13 返回 `ACCOUNT_POSTING`、`accountPostingId` 与序号、`sourceEventKey` 为批次 ID。浏览器 5：画板 08 三行均为「批次 …xxxx · 序号 n」，画板 09 显示「account 批次」与 1 个「流水 …1760 ↗」链接 |
| AC-16 余额查询不依赖账簿 | 通过 | 接口 3：pending 100.00 其余 0；未知 Owner、同 ID 作为 Partner 都返回 400 `OWNER_ACCOUNT_NOT_FOUND`。浏览器 2：画板 10 五张卡 Pending 80.00；画板 11 错误挂 Owner ID 且不显示零余额 |
| AC-17 明细账查询 | 通过 | 接口 5：导航含 `OPERATIONS_FINANCE_SUBLEDGER`，R19 / R20 / R21 字段、`factType`、`publishedTime`，非法 ownerType 返回 400。浏览器 6：菜单、画板 13 / 14 / 15 |
| AC-18 注册 DML | 通过 | 接口 8：Gateway 已加载 R19，块 99 五表 1 / 3 / 6 / 11 / 18 共 39 行，激活后 ACTIVE |
| AC-19 视觉与状态 | 通过 | 浏览器 9（重跑 9）：金额右对齐，未查询 / 无匹配 / 无账户空状态，无错误通知，无意外 HTTP ≥ 400，无控制台错误（antd 弃用告警单列，不计失败） |
| AC-20 逐级下钻与返回保留筛选 | 通过 | 浏览器 5：分录行跳转带 posting / entry，面包屑三级；Owner 类型筛选写入 URL，下钻后返回仍保留。C-1 修复后重跑加 1 项：经分录「流水 …↗」→ 批次抽屉「账户流水」进入时，摘要显示方向（贷 CREDIT）与当前净余额 80.00 USD |
| S6 权限 | 部分 | 接口 2：无会话访问 R19 返回 401；R19—R21 均为 `HUMAN_PERMISSION` + `FINANCE_ACCOUNTING_READ` 且 ACTIVE。**运行时 403 与只读账号无新建按钮为 N/A**：本机只有一个 ACTIVE 运营账号（PLATFORM_ADMIN）；新建运营账号会把邮箱写入 Vault（`mor_vault`），超出「只写 `*_v2e2e`」授权。403 由 BFF `FinanceSubledgerBrowserAutoTest` 的授权拒绝 3 例覆盖 |

**只读回归 27/34。** 页面总数断言 34/34（含 `/finance/subledger`），4 个财务页通过并截图。失败的 7 页是 `/risk/*` 6 页与 `/client-onboarding/reviews`，原因都是 risk-decision 与 compliance 服务未启动导致 503。它们不在卡片启动范围内，与本需求无关。

**运行轮次说明。** 接口验收首轮即 58/58。浏览器首轮 28/30，两项失败都已查明：
- 画板 08 的期望行数是脚本写错。每条 account 流水对应一行控制科目分录，正确值是 3 行（贷 100、冲回借 100、贷 80），已改正。
- 另一项是下面第 4 节的缺口 C-1。脚本改为在账户列表路径断言摘要，经抽屉路径的摘要内容改为打印说明。

改后重跑 30/30。

## 3. 清理与基线核对

`python3 -I local_acceptance.py --cleanup` 按运行记录执行 [acceptance-cleanup-local.sql](./acceptance-cleanup-local.sql)：ledger、account、billing 三段分别以各自的应用账号执行，删除账簿、日历、关账流程与试算、消费幂等、补偿事件、批次、流水、Owner 账户、入账流和 billing 事实。结果为「billing / account / ledger 共 49 张表行数回到运行前基线」。验收没有向 `mor_platform_v2e2e` 写入数据。块 99 与激活属于授权 SQL，保留不回滚。

RabbitMQ 残留见第 4 节 E-1。

## 4. 发现的问题

**本需求缺陷**

- **D-1（已修复）。** 文件为 `REQ/db/ledger/ddl.sql` 与 `paytech/ledger/docs/database/sql/changes/2026-10-19-ledger-control-accounts.sql`。原第 54—55 行按 `account_id` 删除 `t_trial_balance_line` / `t_ledger_period_balance`，但这两张表只有 `chart_account_id`，MySQL 报 1054 并中断。在 mor-test 上会导致 Owner 账户删除与执行后核对都不执行。主会话已把两行替换为说明注释，本机已补跑剩余语句（见 1.2）。阶段 6 应使用修复后的文件。
- **C-1（已修复，2026-10-19 重跑通过）。** 涉及 `operations-console-web/src/pages/FinanceSubledger.tsx` 的 `openFromPosting`。从批次抽屉的「账户流水」进入账户流水页（包括总账分录「流水 …↗」这条跳转链）时，下钻状态只带科目、Owner 和余额桶。因此摘要不显示画板 14 要求的「方向」和「当前净余额」。从账户列表进入时显示正常（已断言）。建议在该路径按 `accountId` 补读账户（例如 R19 加 `ownerId`、`accountCode` 和桶筛选取 1 条），或让 R20 返回账户摘要。主会话已修复 `FinanceSubledger.tsx`（Web 测试 1087 例），本机重跑通过，见第 6 节。

**说明（非缺陷）**

- **R11 的 HTTP 状态。** Body 中 Owner 类型为 CLIENT / LOCAL_PARTNER 时，BFF 按字段校验返回 **422** `VALIDATION_ERROR`，与 C 报告一致。卡片与 AC-14 写的是「BFF 400」。Query 中的同类请求是 400。建议回写文档口径。

**环境 / 部署注意（既有问题，未处理）**

- **E-1。** `/mor-e2e` 上仍有 11b 时期的 `ledger.billing.p0—p3` 队列及其对 `billing.events` 的绑定，没有消费者。本次两条 `EconomicFactRecorded` 因此滞留在 `ledger.billing.p1` / `p3` 各 1 条；`ledger.billing.dlq` 原有 16 条，`account.billing.dlq` 原有 2 条（都是历史残留）。本卡未授权操作 RabbitMQ，所以没有删除。mor-test 发布（阶段 6）时同样需要删除 ledger 对 `billing.events` 的旧队列和绑定，否则 billing 事件会持续堆积。
- **E-2。** `run-service.sh jars` 遇到不存在的 `data-service` JAR 时以 `cp` 报错结束；其余 JAR 已在报错前复制完成。

## 5. 本机服务现状

platform、billing、ledger、account、ops-bff、gateway、nginx、ops-web 仍在运行，入口为 `https://127.0.0.1:18444`。如需停止，用 `run-service.sh stop <服务>` 逐个停止。

## 6. C-1 修复后重跑（2026-10-19）

ops-web 是 vite 开发服务器，修复由热更新加载，没有重启服务。

| 步骤 | 结果 |
|---|---|
| `local_acceptance.py` 重新造数 | **58/58**，运行记录 `local-environment/e2e/runtime/subledger-acceptance-20261019135809.json`（OE `ZE825`） |
| `local_browser.py` | **31/31**。原先只打印经抽屉路径的摘要，现改为断言：经分录「流水 …↗」→ 批次抽屉「账户流水」进入时摘要含「方向 贷 CREDIT」与「当前净余额 80.00 USD」。账户列表路径的同一断言仍通过，截图已覆盖到 `assets/stage5/browser/`（画板 14 现显示方向与净余额） |
| `--cleanup` | billing / account / ledger 共 49 张表行数回到运行前基线 |
