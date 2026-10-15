# Finance Accounting 账务内核 — 交付记录

## 阶段确认

| 阶段 | 日期 | 结论 |
|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-15 | 用户确认：范围 S1—S4；币种/科目用初始化 DML，FE/账簿/日历/期间/账户用内部 HTTP 接口创建；附件只存 `file_id + file_content_hash`；本地 Schema 变更阶段 3 单独授权；适用阶段 1—5 |
| 2. 现状差异与影响分析 | 2026-10-15 | 用户回复“继续”确认；R1 按推荐方案进入阶段 3 |
| 3. 方案、接口与数据设计 | 2026-10-15 | 用户回复“可以”：确认 R1（Redis `AbstractLockProcessor` + 数据库行锁/CAS）、接口、过账日期规则、Content Hash 方案，并授权本地 SQL 执行 |
| 4. 研发实现 | 2026-10-15 | 用户回复“可以”确认，进入阶段 5 |
| 5. 综合验证与本地验收 | 2026-10-15 | 结果已提交，待用户确认结束 |

## 动作授权与执行记录

| 动作 | 状态 |
|---|---|
| 本地 `mor_finance` Schema/DML 执行 | 2026-10-15 已执行（见下） |
| 提交、推送、部署 | 不在本次范围 |

### 本地 SQL 执行（2026-10-15）

- 环境：`mor-local-mysql-1`（127.0.0.1:13306）/ `mor_finance`，账号 `finance_app`；未触碰 `mor-test-mysql-1` 或其他库。
- 前置核对：旧 3 表行数合计 0。
- 备份：`mysqldump --no-data` 旧结构（3 表）保存到本会话 scratchpad `mor_finance-before-20261015.sql`；无数据。
- 执行顺序：`db/ddl.sql`（删旧 3 表）→ `finance-schema.sql` → `finance-initial-data.sql`。
- 执行后核对：`mor_finance` 共 84 表；`t_chart_account` 29 行；`t_currency_spec` 含 USD（minor_unit 2）。

## 阶段 4 研发验证（2026-10-15）

- `JAVA_HOME=JDK 25 ./mvnw -o clean verify`（finance-service 全模块）：**BUILD SUCCESS，78/78 通过**，0 失败/错误/跳过。
- 测试后核对：本地 `mor_finance` 业务表 0 行，初始化 29 个科目保留。
- `npm --prefix requirements-site test`：137 项中 127 通过、10 失败；10 项均为 Commerce/Payment/Platform/ledger/convention 文档断言，
  本次未改 `requirements-site` 或 `agent-docs`，属于既有失败。本次曾临时新增 FIN-I23/I24 导致 1 项 Finance 断言失败，已撤回（见技术方案第 15 节）。

## 阶段 5 综合验证与本地验收（2026-10-15）

- 补充 2 个用例（FE 主体建日历、审批前账簿暂停整体回滚）后，`clean verify` **80/80 通过**。
- 覆盖率（命令行 JaCoCo 0.8.13，未改 pom）：accounting 行 96.2%/分支 79.9%，api 行 100%，web 行 98.5%/分支 90.0%；合计行 **96.7%**、分支 80.3%。
  未覆盖分支主要是数据损坏时 Fail-Closed 的防御分支（序号行缺失、投影漂移等）。
- 本地 `-exec.jar`（127.0.0.1:18105，本地 MySQL/Redis）真实 HTTP 冒烟 13 步全部通过：建财务主体、OE 账簿、两个账户、日历、期间并开放，
  提交调整，Maker 自审被拒，Checker 批准过账（序号 1），其他复核人重复批准被拒，查询详情（POSTED、250.00、两条审批），
  查询 Client 余额（AVAILABLE 250.00）。冒烟后应用已停止，`mor_finance` 业务表清空（科目 29 行、币种 1 行保留），Redis 无残留 Key。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 已新增 Finance 账务内核段落并更新 Finance 行。
- 遗留：Redis 不可用 503 路径未自动化；接口未接统一身份与 Gateway；提交未授权。
