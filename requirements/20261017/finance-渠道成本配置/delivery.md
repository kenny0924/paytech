# Finance 渠道成本配置 — 交付记录

## 阶段确认

| 阶段 | 日期 | 结论 |
|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-17 | 用户回复“继续”：范围与 D1—D5 按建议执行 |
| 2. 现状差异与影响分析 | 2026-10-17 | 用户回复“继续”确认 |
| 3. 方案、接口与数据设计 | 2026-10-17 | 用户回复“可以”确认 |
| 4. 研发实现 | 2026-10-17 | 用户回复“可以”确认 |
| 5. 综合验证与本地验收 | 2026-10-17 | 结果已提交，待用户确认结束 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 模型变更与本地 SQL | 预计不需要 |
| 提交、推送、部署 | 未授权 |

## 阶段 2 现状差异与影响分析（2026-10-17）

### 现状

- 本地 `mor_finance` 已有 `t_channel_cost_plan / _plan_ver / _scope / _component`（另有 `_confirm / _alloc` 不在本期），字段、索引与注释与 PDManer 模型及 `finance-schema.sql` 一致，均 0 行；finance-service 无任何对应 Java 代码。
- 规则 Bundle（`ebb13bb`）已实现同类 Maker-Checker：`CreateRuleVersionProcessor` 等 4 个锁 Processor、`AccountingRuleService`、规则集锁 `AccountingLocks.accountingRuleBundle`；摘要沿用 `paytech-json-canonicalization`（`ManualAdjustmentContentHash`、`PeriodCloseContentHash` 已用）。
- 过账引擎按 `component_type=CHANNEL_COST` 汇总交易经济组件的 `posted_amount`（`TransactionPostingContext.amountOf`），所以一项成本组成对应一行 CHANNEL_COST 经济组件即可直接被消费。
- `t_transaction_economic_component` 为渠道成本冻结的证据列：`source_rule_type=CHANNEL_COST`、`source_rule_version_id`、`source_rule_scope_id`、`source_rule_component_id`、`source_component_code`、`source_trigger_code`、`source_calculation_base`、费率/固定费/币种/上下限、`source_rule_content_hash`、`basis_amount`、`raw_amount`、`posted_amount`、`rounding_mode`。估算结果按这组字段输出。

### 与规则 Bundle 的差异（需在方案中单独处理）

| # | 差异 | 影响 |
|---|---|---|
| G1 | 规则集由初始化 DML 预置；成本方案无预置（D5），需要“创建方案”接口 | 新增方案创建：`channel_id`、`plan_code` 各自唯一，状态 DRAFT、无当前版本；首个版本生效时转 ACTIVE |
| G2 | 版本状态目录不同：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE（规则 Bundle 用 RETIRED） | 新建独立枚举，不复用 `RuleVersionStatus` |
| G3 | `content_hash` 为 NOT NULL，规则 Bundle 版本无此列 | 内容随版本一次提交（D1），创建时即计算并冻结摘要；批准请求必须携带同一摘要 |
| G4 | `uk_channel_cost_current(current_ver_id)` 唯一 | 指针切换天然防止两个方案指向同一版本，无需额外处理 |
| G5 | 适用范围的经营主体列是 `entity_no CHAR(5)`，不是数字 ID | 请求与估算入参按 entityNo 传递 |
| G6 | `component_type`、`calculation_base` 为 Finance 自有非固定目录，`rounding_mode` 只允许 HALF_UP | 方案中给出最小目录（组件类型如处理费/跨境费/退款费/争议费，计算基础先只支持交易金额），以 Code 枚举校验 |
| G7 | 固定费、上下限与计费基数的币种关系模型未写明 | 本期不做换汇：固定费币种必须等于适用范围币种，上下限按适用范围币种解释；币种必须在 `t_currency_spec` 中存在（当前只有 USD） |
| G8 | 估算按哪个版本 | 建议按发生时间命中 `[effective_from, effective_to)` 覆盖它的 ACTIVE 或 INACTIVE 版本，延迟到达的事件仍用当时规则；没有覆盖版本即拒绝 |

### 影响范围

- 代码：只在 `paytech-finance-accounting`（新增枚举、4 张表的 DataObject/Mapper/Repository、领域服务、锁 Processor、估算服务）、`paytech-finance-api`（新 `ChannelCostPlanApi`）、`paytech-finance-web`（Controller 与转换器）、`paytech-finance-test`（新测试与清理器纳入 4 张表）。新枚举在 `FinancePersistenceConfiguration` 注册 TypeHandler。
- 数据库：不需要 DDL/DML。
- 文档：数据库设计文档补充组件类型与计算基础目录；依赖清单把 X5 拆为“已实现：成本配置”和 X6；`CURRENT_IMPLEMENTATION.md` 与 `requirements/README.md` 的 Finance 行在阶段 5 更新。
- 其他系统：不涉及。

### 阶段 3 范围

接口与请求/响应字段、方案与版本状态机、内容摘要结构（`CODE-MOR:FINANCE:CHANNEL_COST:1`）、估算算法（舍入、上下限、多组成）与证据结构、G6 目录取值、测试矩阵、多 agent 分工。

## 阶段 4 研发验证（2026-10-17）

- 主会话先建共享骨架：5 个枚举并注册 TypeHandler、四表 DataObject 与 Repository 接口、锁 `channel-cost-plan:{planCode}`、估算服务接口与命令/结果模型。随后三个并行 agent：A 持久化、内容摘要、`ChannelCostPlanService`、5 个锁 Processor、`ChannelCostPlanApi`（C1—C7）与 Controller、文档（数据库设计 V1 目录句、依赖清单 X5 拆分与 X6）；C `ChannelCostEstimationServiceImpl`；B 测试。主会话转达合同（方法名、错误前缀、金额精度）并合并核对。
- 合并核对：发生时间为空单独报“发生时间不能为空”（原与计费基数同一前缀）；估算的计费基数精度按去除末尾零后的小数位判断（`100.000` 对 USD 合法）；新增生产代码无超过 30 行的方法；事务内只操作数据库，先锁方案行再锁版本行。
- 实现比方案更细的拒绝：C2 事务内复验到待处理版本按 409 拒绝；批准时新版本生效时间不晚于当前版本被拒；数据不一致（生效区间重叠、范围无组成、币种规则缺失）为 `ErrorException`。
- `JAVA_HOME=JDK 25 ./mvnw -o clean verify`：**BUILD SUCCESS，242/242 通过**（新增 49 个：方案接口 5、版本校验 15、版本生命周期 15、估算 8、估算拒绝 6；原 193 个保持通过）。估算测试的版本经真实 HTTP 创建 → 提交 → 批准链路准备。
- 测试基础设施：清理器把四张表纳入全清表；`init/ddl.sql` 同步四张表（与 `finance-schema.sql` 逐字一致）。

## 阶段 5 综合验证与本地验收（2026-10-17）

- `clean verify` 242/242；JaCoCo（命令行，未改 pom）accounting 行 96.0%/分支 82.8%，api 100%，web 行 99.5%/分支 90.9%；合计行 **96.6%**、分支 83.1%。`ChannelCostEstimationServiceImpl` 93.2%、`ChannelCostPlanServiceImpl` 93.9%、`CreateChannelCostVersionProcessor` 98.6%、`ApproveChannelCostVersionProcessor` 95.2%，其余 Processor、校验器、Controller 与转换器 100%；四个 RepositoryImpl 71%—82%，未覆盖的是唯一键冲突与 CAS 失败等并发防御分支。
- 本地 `-exec.jar` 真实 HTTP 冒烟 **20/20**：创建方案为 DRAFT 且无当前版本；同渠道再建方案、费率大于 1、两个范围维度相同被拒（400），非法组件类型 400 `INVALID_REQUEST`，缺 channelId 422；版本 1（两个范围：SALE 2.9% + 0.30 下限 0.50，REFUND 固定 0.25）为 DRAFT 且摘要 64 位；已有草稿再建、非编制人提交、Maker 自审、摘要不一致被拒；批准版本 1 后方案 ACTIVE、当前版本 1；批准版本 2 后版本 1 INACTIVE 且失效时间等于版本 2 生效时间；未来版本 3 仅 APPROVED、当前版本仍为 2；版本 4 驳回后不可批准；方案查询版本倒序 REJECTED/APPROVED/ACTIVE/INACTIVE；版本 1 详情摘要不变、金额按币种精度（0.30）、费率 0.029；不存在的方案被拒。估算服务无 HTTP 入口，验收以领域服务入口的 14 个业务测试为准。
- 冒烟后应用停止，业务表清空（科目 29、科目表 1、币种 1、结转规则 7、规则 Bundle 1/1/12 且 ACTIVE/5101 保留），Redis 无残留锁。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 已新增渠道成本配置段落，并更新 Finance 行。
