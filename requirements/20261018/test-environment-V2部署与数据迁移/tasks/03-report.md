# 任务卡 03 报告 · mor-test 补齐补偿扫描执行器（XXL-Job）

- 分支：`test-env/compensation-executors`（worktree `Code-MoR-mt3`，起点 `main@d55bd0de`）。运行命令以主仓库为 `$REPO`。
- **结论：有补偿事件目录的 10 个服务都已在 XXL 注册执行器并运行 `compensationEventScan`。**
  - compliance 停在 ERROR 的 `CLIENT_REVIEW_RESULT` 在任务启动后 32 秒被重试成功，对应申请从 UNDER_REVIEW 变为 RETURNED。
  - 入驻验收：默认 29/29，`--approve` 41/41。
  - 只改 compose 环境变量，不改代码、不重打镜像。

## 0. 已读输入与改动归属

- 已读：任务卡 03、`tasks/02-report.md`、`tasks/01-report.md` 第 7 项、`runbook.md`、`sql/50-xxl-job-acquiring.sql`；`agent-docs/COMPENSATION_EVENT_GUIDE.md`（第 3、6 节）；`test-environment/compose.applications.yaml`、`compose.scheduler.yaml`、`compose.sh`；10 个 app 的 `application.yml` 中 `compensation` 段；`paytech-xxl-job-starter` 的 `XxlJobAutoConfiguration` / `XxlJobProperties`；`paytech-compensation-event` 的 `CompensationXxlJobAutoConfiguration`；card `CardInstrumentPurgeJobHandler`、account `BalanceReconciliationJobHandler`；10 个 `compensation-event-catalog.yaml` 的位置。
- 归属：只涉及 test-environment 部署配置与 XXL 登记，不新增或修改同步业务接口，AGENTS 的接口开发必读不适用；不涉及事务内外步骤。

## 1. 装配核对：无需改 app

| 核对 | 结果 |
|---|---|
| 依赖 | 10 个服务都依赖 `paytech-compensation-event`，它直接依赖 `paytech-xxl-job-starter` 与 `xxl-job-core` |
| 开关 | Starter 只在 `xxl.job.executor.enabled=true` 时创建执行器；扫描 Handler 在补偿扫描器与执行器都存在时注册 |
| `application.yml` | 10 个 app 都没有 `xxl.*` 键，完全由环境变量绑定，与 acquiring、platform-support 相同 |
| `compensation.enabled` | client-master 跟随 `ONBOARDING_RECOVERY_ENABLED`（compose 为 true）；consumer 未写，默认开启；其余为 true |
| 实例 ID | 已有来源的保持不变：client-master `COMPENSATION_INSTANCE_ID`、card `PAYTECH_INSTANCE_ID`、billing / account 的服务专用键。compliance、risk-decision、consumer、payment 新增 `COMPENSATION_INSTANCEID=<服务>-test` |

因此 client-master、account 不需要补接线。启动日志证实各服务都注册了 `compensationEventScan`，card 另有 `cardOrphanInstrumentPurge`，account 另有 `accountBalanceReconciliation`。

## 2. compose 改动

`test-environment/compose.applications.yaml` 为 8 个服务各加一段，与 acquiring 同形：

- `XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_ADMIN_ACCESSTOKEN`；
- `XXL_JOB_EXECUTOR_ENABLED`、`APPNAME`（服务发现名）、`IP`（该服务固定 IP）、`PORT` 9999、`LOGPATH` /tmp/xxl-job、`EXCLUDEDPACKAGES`；
- 第 1 节所列 4 个服务的 `COMPENSATION_INSTANCEID`。

| 核对 | 结果 |
|---|---|
| 10 段 XXL 配置的 APPNAME 等于服务名、IP 等于 `backend.ipv4_address` | 全部一致（脚本逐段核对） |
| `compose.sh config` 渲染 | 通过；10 个服务的 appname、IP、实例 ID 正确，Token 已替换 |
| `test-environment/scripts` 17 个单测文件 | 与改动前完全相同：14 个通过，3 个既有失败（`smoke_stage_contract`、`risk_rule_configuration_catalog`、`authorization_inventory`，阶段 2 报告已记录）。没有钉值需要重定 |

运行时按约定：先 `diff` 确认主仓库该文件等于 main，再把 worktree 版本复制过去执行 `./compose.sh`。主仓库该文件现与本分支相同，合并时由主会话处理。

## 3. XXL 登记

新增 [scripts/register_xxl_executors.py](../scripts/register_xxl_executors.py)，经 Admin API 操作（阶段 2 同法），不需要 MySQL root：

- `plan` 只读，列出缺失的分组、任务与停止状态；`apply` 幂等地创建并启动，再执行时全部跳过。
- 登录口令从主仓库 `.env.test` 读取，不回显。
- 文件头写明回滚步骤：停止并删除任务，再删除分组。

| 分组（id） | 任务（id） | 设置 |
|---|---|---|
| client-master-service（5） | compensationEventScan（5） | 与 acquiring 相同：BEAN、`*/30 * * * * ?`、分片广播、单机串行、超时 60 秒、失败不重试 |
| compliance-service（6） | compensationEventScan（6） | 同上 |
| risk-decision-service（7） | compensationEventScan（7） | 同上 |
| consumer-service（8） | compensationEventScan（8） | 同上 |
| payment-service（9） | compensationEventScan（9） | 同上 |
| card-service（10） | compensationEventScan（10）、cardOrphanInstrumentPurge（11） | 回收任务：`0 0 * * * ?`（每小时）、FIRST、单机串行、超时 300 秒。Handler 不按分片划分，多实例时只需执行一次 |
| billing-service（11） | compensationEventScan（12） | 同上 |
| account-service（12） | compensationEventScan（13） | 同上 |

执行顺序：先重建服务，使执行器在线，再 `apply`（16:15:29 UTC）。再次运行 `plan` 输出 0 项待处理。

## 4. 部署与验证（mor-test，2026-10-18，时间为 UTC）

| 步 | 时间 | 动作与结果 |
|---|---|---|
| 1 | 16:13 | 前置只读：8 个库中只有 `mor_compliance.t_compensation_event` 有 1 行：`CLIENT_REVIEW_RESULT`，ERROR，执行 1 次，错误码 `CLIENT_REVIEW_RESULT_UNPUBLISHED`。对应案件为 RETURNED，申请 `237605256547115008` 为 UNDER_REVIEW |
| 2 | 16:14:27—16:15:19 | `up -d --no-deps --wait` 分两组重建 8 个服务，全部 healthy；近 5 分钟 ERROR 日志 0 行 |
| 3 | 16:15:29 | `register_xxl_executors.py apply`：8 个分组、9 个任务已创建并启动 |
| 4 | 16:16:00 | 首次触发：client-master、compliance、risk-decision 成功；其余 5 个返回 `registry address is empty`。原因是分组刚建，Admin 还没刷新注册地址（每 30 秒一次），与 2026-10-17 记录的重启现象相同 |
| 5 | 16:16:01 | compliance 的 ERROR 行被处理并删除；申请变为 **RETURNED**（`update_time` 16:16:01） |
| 6 | 16:16:30、16:17:00 | 8 个扫描任务全部 trigger 200 / handle 200 |
| 7 | — | Admin 中 **10 个执行器在线**，各一个地址 `172.30.73.x:9999`；commerce-service 分组仍无地址（阶段 3 删除） |
| 8 | — | `E2E_TARGET=test acceptance_flow.py`：**29/29**；`--approve`：**41/41** |
| 9 | — | 验收后 8 个库的 `t_compensation_event` 均为空 |

任务卡写的 28/28 是 V1 的步数。V2 脚本在默认流程末尾多一步"MQ ClientReviewDecided 已消费（退回）"，所以是 29 步。

`cardOrphanInstrumentPurge` 每小时整点运行，本次没有手工触发：它会删除符合条件的工具数据，按计划运行即可。

### 内存（`docker stats`）

| 服务 | 改动前 MiB | 改动后 MiB | 差值 | 上限 |
|---|---:|---:|---:|---|
| client-master | 314 | 321 | +7 | 576 MiB |
| compliance | 229 | 241 | +12 | 576 MiB |
| risk-decision | 287 | 276 | −11 | 576 MiB |
| consumer | 219 | 233 | +14 | 448 MiB |
| payment | 266 | 275 | +9 | 576 MiB |
| card | 253 | 267 | +14 | 576 MiB |
| billing | 201 | 236 | +35 | 576 MiB |
| account | 169 | 185 | +16 | 448 MiB |
| mor-test 合计 | 6,493 | 6,613 | +120 | — |

改动前的读数来自已运行数小时的容器，改动后来自刚重建并跑过一次验收的容器，差值包含这一偏差。所有服务都低于上限的 60%。

## 5. 改动文件

| 文件 | 改动 |
|---|---|
| `test-environment/compose.applications.yaml` | 8 个服务的 XXL 执行器环境变量；4 个服务的 `COMPENSATION_INSTANCEID` |
| 本需求 `scripts/register_xxl_executors.py`（新增） | Admin API 登记脚本，`plan` / `apply` |
| `test-environment/README.md` | 执行器范围与登记脚本说明 |
| `agent-docs/CURRENT_IMPLEMENTATION.md` | mor-test V2 条目：去掉 `cardOrphanInstrumentPurge` 未登记，新增补偿执行器一行 |
| 本需求 `delivery.md`、`tasks/01-report.md` 第 7 项、`tasks/02-report.md` 第 9 节 | 同步完成状态 |

## 6. 遗留

1. account 的 `accountBalanceReconciliation`（日终余额核对）有 Handler，但没有登记任务，不在本任务范围内。需要时可以在脚本中加一项。
2. 新分组建立后的第一个周期会因注册地址未刷新而失败一次，下一周期自动恢复，不需要处理。
3. `sql/50-xxl-job-acquiring.sql` 文件头"不含 card 等执行器"是阶段 2 的历史说明，保持不变；现状以本报告为准。
