# billing 计费事实模型重构与渠道成本迁出交付记录

## 适用阶段（2026-10-19 确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-19，与阶段 2 合并） |
| 2. 现状差异与影响分析 | 已确认（2026-10-19），见 [technique.md 第 1 节](./technique.md) |
| 3. 方案、接口与数据设计 | 已确认（2026-10-19）：[technique.md 第 2 节](./technique.md) + [db/](./db/) 三份待执行 SQL |
| 4. 研发实现 | 已完成待确认（2026-10-19）：T0 → A / B / C → D 五线报告见 [tasks/](./tasks/)；门禁见下「测试 / 验收证据」 |
| 5. 综合验证与本地验收 | 已完成（2026-10-19 23:1xZ）：[stage5-report.md](./verification/stage5-report.md) 59/59 两轮，四库清理回基线，无本需求缺陷 |
| 6–7. mor-test | 已完成（2026-10-19 19:44Z）：F 线发布包（[tasks/F-report.md](./tasks/F-report.md)）→ 主会话执行第 3—9 步、G 线复核 / 备份 / 记录（[tasks/G-report.md](./tasks/G-report.md)）；mor-test 验收 59/59，清理回基线；偏差：Snowflake Worker 32 → 26 |
| 8. 提交 | 单独授权 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-19 | 立项 | "一起吧"：写 billing 决定记录并登记本需求 | 决定记录草稿（Proposed）+ 阶段 1、2 评审稿 |
| 2026-10-19 | 3 | "继续"：阶段 3 设计包含 2.6 七项细化按稿确认 | 进入阶段 4 研发（不执行 SQL、不提交） |
| 2026-10-19 | 4 | 用户 AskUserQuestion 选择"授权（推荐）"：本机两测试库重建 | 备份后重建 `mor_channel_connector`（+4 表）、`mor_billing`（11 表） |
| 2026-10-19 | 4 → 8 | "我准备睡觉了，等下你可以帮我把这个需求做完发到test环境吗？注意别的chat别冲突了。" + AskUserQuestion 选择"部署 + 本地提交（推荐）" | 阶段 5 本地验收 → 6 发布准备 → 7 部署 mor-test（含两库 SQL 与四个镜像）→ 8 本地提交（私有索引、只含本需求文件、共享文件按 hunk 拆分、不推送）；不触碰其他会话的改动 |
| 2026-10-19 | 1 + 2 | "按照你的建议来吧"：决定记录转 Confirmed（第 14 条按建议：行可指定目标桶、默认 AVAILABLE）；范围、不做项、AC-01–07、Q-01–Q-04 按建议 | 进入阶段 3（只写文档与 SQL 文件，不改代码、不执行 SQL、不提交） |

## 当前阶段与下一确认点

当前：阶段 5–8 已一并授权，连续执行中（用户离线）。各阶段结果随本文件与 `verification/`、`release/` 登记。

## 变更记录

| 日期 | 环境 | 动作 | 依据 |
|---|---|---|---|
| 2026-10-19 18:12Z | 本机 local-environment MySQL | 备份 `mor_channel_connector`（13 表）、`mor_billing`（9 表，事实三表与成本四表均 0 行）到 `~/Code-MoR-backups/20261019T181208Z-billing-cc-local/`；对 `mor_channel_connector` 执行 channel-connector 测试 DDL 末尾四段 CREATE（13 → 17 表） | 用户 2026-10-19 授权"两库都执行，先备份" |
| 2026-10-19 22:56Z | 本机 local-environment MySQL（`*_v2e2e`） | E 线：备份到 `local-environment/e2e/backup/20261019225607/`；`mor_channel_connector_v2e2e` 执行 `channel-connector-schema.sql`（0 → 17 表）+ 50 号种子；`mor_billing_v2e2e` 执行 REQ `db/billing/ddl.sql` + `dml.sql`（9 → 11 表）；run-service.sh 的 channel-connector 段补环境变量 | 阶段 5–8 授权 |
| 2026-10-19 18:2xZ | 本机 local-environment MySQL | `DROP DATABASE mor_billing` 后执行 billing 测试 DDL（`paytech-billing-test/src/test/resources/init/ddl.sql`）：11 张表，`t_currency_spec` USD 与 `t_business_calendar` DEFAULT 各 1 行；REQ `db/billing/ddl.sql` 两张消费表改为与 Schema 一致后，9 张新表与测试 DDL 逐表一致 | 同上授权 |

## 测试 / 验收证据

**阶段 4 门禁（2026-10-19 22:3x–22:5xZ 本机，`./mvnw -B -o verify … -am`，local-environment）**

| 模块 | 结果 | 说明 |
|---|---|---|
| billing-test | 71/71 | 含 R4 架构测试；重建 `mor_billing` 后首轮 2 例是测试把 Body 校验误写为 400（实际 422），已改断言 |
| channel-connector-test | 125/125 | 渠道成本 50 例新增；建库后发现并修复全局 TypeHandler 覆盖既有枚举的缺陷 |
| account-test | 58/58 | 与基线一致 |
| ledger-test | 234/234 | 与基线一致 |
| arch-rules | 61/61（D 线 22:38Z）；主会话 22:5xZ 复跑 60/61 | 复跑失败项 `TableOwnershipAutoTest.test_normalFlow`（期望 334，实际 340）来自另一会话 22:43Z 向共享生成器 `generate-table-ownership.mjs` 加入 commercial 8 张表（`t_product*`、`t_agreement_*`、`t_pricing_profile*`），其断言尚未跟上；本需求的 billing 11 / channel-connector 17 集合正确，`--check` 通过 |
| foundation paytech-messaging | 单独 73/73；带 `-am` 的三次复跑中 `PartitionQueueBaselineAutoTest.test_normalFlow_residualMessagesPurged` 各失败 1 次 | 共享 RabbitMQ `/mor-local` 基线检查受并行会话测试干扰；非本需求改动（T0 只改事件名，`YamlEventCatalogAutoTest` 通过） |
| Foundation 其余上游 | 全部通过 | common-core 130、json-canonicalization 103、mvc-web 161、redis-starter 122、compensation-event 33、messaging-api 37 等 |
| `node --test scripts/pdmaner/*.test.mjs` | 573 例，524 通过，49 失败 | 本需求相关 `billing-model` 7/7、`payment-channel-query` 1/1、`pdmaner-canonical-paths` 3/3 通过；49 个失败均为 HEAD 既有（D 线用 HEAD 快照逐条比对） |
| `generate-channel-connector-model.mjs --check`、`generate-table-ownership.mjs --check` | 通过 | 17 表；265 + 56 − 7 + 26（含另一会话的 6 张 step-added） |
| `npm --prefix requirements-site test` | 137 例，127 通过 | 10 个失败与 HEAD 相同 |
| AC-05 | 通过 | billing 范围内 `EconomicFact* / economic_fact / EconomicsType / AmountSource / ChannelCostPlanApi` 零命中（仅历史 SQL `changes/2026-10-18-*` 与负例断言）；`AutoInstructionType` 只在 ledger-api |

各线第 11 节自查见 `tasks/T0-report.md` §6、`A-report.md` §4、`B-report.md` §4、`C-report.md` §4、`D-report.md` §4。

## 部署 / 恢复方案及 SQL 执行记录入口

- 发布清单、恢复方案与执行记录：[release/release.md](./release/release.md)（第 4 节执行顺序、第 5 节恢复、第 6 节执行记录）；候选镜像 [compose-candidate.yaml](./release/compose-candidate.yaml)；XXL 登记 [xxl-job-channel-cost-activation.md](./release/xxl-job-channel-cost-activation.md)。
- mor-test SQL（2026-10-19 19:37:14Z，root）：`mor_channel_connector` ← [db/channel-connector/ddl.sql](./db/channel-connector/ddl.sql)（13 → 17 表）；`mor_billing` ← [db/billing/ddl.sql](./db/billing/ddl.sql) + [dml.sql](./db/billing/dml.sql)（9 → 11 表，DEFAULT 日历 1 行）。输出在备份目录 `sql-4.*.out`。
- 镜像：channel-connector `194961f11db5…`、billing `eeec27f923b7…`、ledger `0e9390666b83…`、account `ad0621b12a80…`（tag `billing-fact-20261019`）。XXL 分组 13 / 任务 15 `channelCostVersionActivation`。
- 备份 / 回退位点：`~/Code-MoR-backups/20261019T193439Z-billing-fact-test/`（四库 dump、`compose-rollback.yaml`、`rowcounts-before.tsv` / `rowcounts-after.tsv`）。
- 偏差：Compose 中 `CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID` 由 32 改为 26（Snowflake 只允许 0—31），随阶段 8 提交。

## 提交记录

（阶段 8 登记）

## 遗留项及后续需求

- 需求 B：支付事实首个切片，待 commercial 定价模型与三项上游合同。
- 需求 B 前置：account 对自动 PAYMENT 行付方 / 收方的映射规则（讨论稿 C1 的"渠道 → Client 本金""Client → OE 手续费"与现行 `FactBalanceMappingRule` 不兼容，C 线 2026-10-19 发现）；本期无自动事实，不触发。
- 工作日日历 Owner 与同步机制（平台参考数据）另立。
- channel-connector 渠道成本：没有作废 APPROVED 版本的入口；到期切换 Job 遇到"生效时间不晚于当前版本"的 APPROVED 版本会持续 handleFail，本期按人工处理（B-report.md 第 6 节第 5 项）。
- 阶段 6 发布需同步 channel-connector 新配置键（`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID / _WORKER_ID` 必填、`CHANNEL_CONNECTOR_REDIS_*`、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`、XXL 执行器）到 Compose / `config/nacos/channel-connector-service.yaml`，并登记 XXL 任务 `channelCostVersionActivation`（B-report.md 第 6 节第 9 项）。

## 实现 / 验证 / 部署状态

以 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。
