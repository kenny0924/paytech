# Finance 期间关账 — 交付记录

## 阶段确认

| 阶段 | 日期 | 结论 |
|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-16 | 用户确认“按照建议来”：范围与 D1—D6 全部采用 |
| 2. 现状差异与影响分析 | 2026-10-16 | 用户确认 |
| 3. 方案、接口与数据设计 | 2026-10-16 | 用户确认，并授权本地 SQL |
| 4. 研发实现 | 2026-10-16 | 用户回复“可以”确认 |
| 5. 综合验证与本地验收 | 2026-10-16 | 结果已提交，待用户确认结束 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 模型变更与本地 SQL（D4、P6） | 2026-10-16 已执行（见下） |
| 提交、推送、部署 | 未授权 |

### 本地 SQL 执行（2026-10-16）

- 环境：`mor-local-mysql-1` / `mor_finance`，账号 `finance_app`。
- 前置核对：`t_period_close` 0 行、`t_close_account_rule` 0 行、`close_hash` IS_NULLABLE=NO。
- 执行：`db/ddl.sql`（`close_hash` 改可空）→ `db/dml.sql`（结转规则 7 行）。
- 执行后核对：`close_hash` IS_NULLABLE=YES；`t_close_account_rule` 7 行（ID 4001—4007）。

## 阶段 4 研发验证（2026-10-16）

- 三个并行 agent：A 关账生产代码（含过账内核期间门禁、试算按指定水位执行、模型/DML/文档）、C 投影重建生产代码（含提供给 A 的两个查询）、B 测试；主会话合并核对。
- 合并核对修正：测试 `@see` 中 1 个不存在的方法名（`requestProjectionRebuild` → `startProjectionRebuild`）并补全全部参数类型；测试中 `Map.of` 手写请求体改为正式 RequestBody；`TrialExceptionServiceImpl` 注入字段排序。`FinancePersistenceConfiguration` 因逐个显式注册枚举超过 30 行，按规范要求的显式写法保留。
- `JAVA_HOME=JDK 25 ./mvnw -o clean verify`：**BUILD SUCCESS，134/134 通过**（新增 25 个关账与投影重建用例，原 109 个保持通过）。
- 模型与文档：PDManer、`finance-schema.sql` 中 `close_hash` 改可空；`finance-initial-data.sql` 追加结转规则 4001—4007；`FINANCE_DATABASE.md` 追加 D2、D3/D4 两段说明。

## 阶段 5 综合验证与本地验收（2026-10-16）

- `clean verify` 134/134；JaCoCo（命令行，未改 pom）accounting 行 96.2%/分支 80.7%，api 100%，web 行 99.3%/分支 88.2%；合计行 **96.8%**、分支 81.2%。`StartPeriodCloseProcessor` 96.5%、`PeriodCloseServiceImpl` 95.4%、`TrialExceptionServiceImpl` 93.1%，审批与投影重建 Processor 91.3%—100%。
- 本地 `-exec.jar` 真实 HTTP 冒烟 **22/22**（同一主体上期收入 50，本期收入 300、费用 120）：前序期间未关时发起被拒；上期关账进入 PENDING_CHECK、期间 CLOSING；重复发起、Maker 自审、摘要不一致被拒；驳回后回到 OPEN 并可再次发起；批准后期间 CLOSED、流程 COMPLETED，重复批准被拒，CLOSED 期间人工调整不能过账；篡改资本账户投影后本期关账在 RESOLVE_EXCEPTIONS 失败、期间回到 OPEN；投影重建发起 UNDER_REVIEW、Maker 自批被拒、Checker 批准 RESOLVED 且投影恢复；重新关账进入待审批，关账中审批人工调整被拒；详情 7 步全部 SUCCEEDED、账簿 FINALIZED 且水位等于结转分录序号；结转后收入/费用归零、本期损益 230.00，结转分录类型 CLOSING、过账日为期末日（2026-09-30、2026-10-31）；批准后审批记录 2 条；列表倒序；不存在的编号被拒。
- 冒烟首轮脚本有两处预期写错（本期预置银行余额触发了步骤 3 的 Fail-Closed、批准后的状态写成 APPROVED），均按设计修正脚本后清库重跑，22/22 通过。冒烟后应用停止，业务表清空（科目 29、科目表 1、币种 1、结转规则 7 保留），Redis 无残留锁。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 已新增期间关账段落，并更新 Finance 行。
