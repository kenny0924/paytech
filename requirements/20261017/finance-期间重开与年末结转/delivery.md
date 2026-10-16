# Finance 期间重开与年末结转 — 交付记录

## 阶段确认

| 阶段 | 日期 | 结论 |
|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-17 | 用户回复“继续”：范围与 D1—D6 按建议执行 |
| 2. 现状差异与影响分析 | 2026-10-17 | 用户确认 |
| 3. 方案、接口与数据设计 | 2026-10-17 | 用户回复“可以”确认，并授权本地 DDL |
| 4. 研发实现 | 2026-10-17 | 用户回复“可以”确认 |
| 5. 综合验证与本地验收 | 2026-10-17 | 结果已提交，待用户确认结束 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 模型变更与本地 SQL | 2026-10-17 已执行（见下） |
| 提交、推送、部署 | 未授权 |

### 本地 SQL 执行（2026-10-17）

- 环境：`mor-local-mysql-1` / `mor_finance`，账号 `finance_app`。
- 前置核对：`t_period_close` 中 REOPEN 流程 0 行；存在唯一键 `uk_period_reopen_target(reopens_close_id)`。
- 执行：[`db/ddl.sql`](./db/ddl.sql)。
- 执行后核对：`t_period_close` 上 `reopens_close_id` 仅有普通索引 `idx_period_reopen_target`（Non_unique=1，注释“支持访问路径：reopens_close_id”）；`t_close_step.step_type` 注释含 `PLAN_REOPEN_REVERSAL`、`POST_REOPEN_REVERSAL`。

## 阶段 4 研发验证（2026-10-17）

- 三个并行 agent：A 期间重开（`POST /{periodId}/reopens`、审批/驳回按 `close_type` 分派、查询新增字段、`PeriodReopenReason`、`CloseStepType` 追加两步并以 `closeSteps()/reopenSteps()` 显式区分、模型与数据库设计文档同步）；C 年末结转（`postProfitClose` 链路追加 CPR → RE 行，生成条件改为“无任何结转行才不生成”）；B 测试。主会话转达三方断言要点并合并核对。
- 合并核对：
  - 非法原因代码返回 400 `INVALID_REQUEST`（`InvalidParameterException`）：开发规范第 97、167 行规定参数错误使用该异常，保留实现，更正 technique.md 中原写的 `BUSINESS_CHECK_FAILED`。
  - 关账测试基类 `AbstractPeriodCloseApiTest` 统一把当月期间固定为非年末（原只在重开基类），避免既有关账用例在 12 月运行时因年末结转失败。
  - 新增生产代码无超过 30 行的方法。
- 已知遗留：发起重开的“最近关闭期间”检查与关账的“前序期间已关闭”检查都只在事务前执行、各自持有不同期间的锁，对相邻期间同时关账与重开存在理论竞态（两者既有写法一致，未在本期修改）；年末期间即使本期损益累计为零也会确保本期损益账户存在（可能建出零余额账户，不影响账务）。
- `JAVA_HOME=JDK 25 ./mvnw -o clean verify`：**BUILD SUCCESS，193/193 通过**（新增 21 个：发起重开 8、复核重开 7、年末结转 6；原 172 个保持通过）。

## 阶段 5 综合验证与本地验收（2026-10-17）

- `clean verify` 193/193；JaCoCo（命令行，未改 pom）accounting 行 96.1%/分支 82.0%，api 100%，web 行 99.5%/分支 89.5%；合计行 **96.6%**、分支 82.4%。`StartPeriodReopenProcessor` 98.7%、`ApprovePeriodCloseProcessor` 97.7%、`RejectPeriodCloseProcessor` 100%、`StartPeriodCloseProcessor` 96.0%、`PeriodCloseServiceImpl` 94.8%。
- 本地 `-exec.jar` 真实 HTTP 冒烟 **18/18**（日历财年起始月设为下月，使本月为财年最后期间；上月收入 50，本月收入 300、费用 120）：上月（非年末）关账后无留存收益、本期损益 50；年末关账后本期损益 0、留存收益 230；上月非最近关闭期间重开被拒；非法原因代码 400 `INVALID_REQUEST`、缺 makerId 422；发起重开为 REOPEN/PENDING_CHECK、引用原关账 1、期间 REOPENING、三步骤 SUCCEEDED/SUCCEEDED/PENDING；已有活动重开再发起、Maker 自审、摘要不一致被拒；驳回后期间 CLOSED 且无新分录；同一原关账再次发起（编号 3）并批准，期间 OPEN、`reopen_count=1`，收入 300/费用 120 恢复、本期损益回到 50、留存收益 0，冲回分录 REOPENING 过账日为期末；重开后人工调整过账，重新关账编号 4、留存收益 240；原关账 1 详情不变；流程列表倒序 4 CLOSE/3 REOPEN/2 REOPEN REJECTED/1 CLOSE。
- 冒烟后应用停止，业务表清空（科目 29、科目表 1、币种 1、结转规则 7、规则 Bundle 1/1/12 保留），Redis 无残留锁。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 已新增期间重开与年末结转段落，并更新 Finance 行。
