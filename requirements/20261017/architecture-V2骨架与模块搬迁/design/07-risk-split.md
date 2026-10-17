# 07 · Risk 拆分：风控决策与合规（S2、S8）

现状见 [analysis/risk.md](../analysis/risk.md)：代码已按 decision / control / compliance 三模块划分，仅 decision → control 一条依赖；零跨模块 SQL 与事务；耦合在共享库、`risk-web` 装配、单一 `risk-api`、四条指向 decision 表的逻辑引用。

## 1. 目标服务

| 服务 | 来源 | 库 |
|---|---|---|
| `risk-decision` | `risk-decision` + `risk-control`（名单、Manual Payout 资格桩并入） | `mor_risk_decision`：规则配置 31 表、评估与运行期 22 表、名单 2 表、限额 7 表 |
| `compliance` | `risk-compliance` | `mor_compliance`：尽调 20 表、`t_qualification_decision` 等、**结算控制 3 表（C3 已定：合规持有）** |
| 争议 7 表 | 仅 Schema | 归 `dispute` 模块的库 `mor_dispute`，阶段 0 只建 Schema 归属，无代码 |

`risk-control` 并入 decision 的理由：名单属于反欺诈、decision 已依赖它 17 处；出款资格 Processor 的表 `t_settlement_risk_assessment` 属于评估组。

## 2. 结算控制归属（C3，用户已定：合规持有）

`t_settlement_control` 三表归 `compliance`。两条跨服务路径按 D17 / D18 设计（代码尚未实现，阶段 0 只定合同）：

- **创建**：决策 Action Effect 产生结算控制时，risk-decision 调 `compliance-api` `POST /compliance/v1/settlement-controls`（类 B 可逆命令，幂等键 = `source_action_effect_id`），由 compliance 本地事务落表并发布 `SettlementRestrictionChanged`（D27，出款与账户订阅）。
- **读取**：出款检查点评估（risk-decision）在评估事务前调 `GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=ACTIVE` 取控制集合，冻结 `control_set_hash` 到 `t_settlement_risk_assessment`；compliance 不可用则评估失败关闭。
- `t_settle_control_state_change.trigger_type = ACTION_EFFECT` 的 `trigger_id` 只存决策侧 ID，不联表。

## 3. 跨库逻辑引用的处理

| 引用 | 处理 |
|---|---|
| `t_qualification_decision.decision_evaluation_id` → decision | 保留为跨库 ID 引用（只存不联）；当前 `DecideClientReviewProcessor` 固定写 `REVIEWER`、评估 ID 为空，不触发 |
| `t_settlement_control.source_action_effect_id` | 同库，无影响 |
| `t_dispute_liability_decision` 两列 → decision | 争议模块后续切片以同步评估合同获取 ID，只存不联 |

## 4. `risk-api` 拆分（C6，用户已定：一次性拆分，无兼容期）

- 新增 `paytech-risk-decision-api`（`api.decision`、`api.evaluation` 中的 `RiskDecisionApi`、`RiskEvaluationQueryApi`、`ManualPayoutEligibilityApi`、`api.control`、相关枚举与 `api.validation`）和 `paytech-compliance-api`（`api.compliance`、`ClientReviewErrorCode`、`MaterialQualificationType`）。
- **不保留** `risk-api` 聚合坐标。第 6 步在同一分支内删除 `risk-api`，并同步修改五个消费方（CCT / client-master、Commerce / acquiring、Payment、operations-console-bff、client-console-bff）的依赖坐标与 import 包名；该步因此跨系统，是研发计划中唯一允许一步改多个系统的例外，合并门禁要求五个消费方的 `-test` 全部通过。为缩小冲突面，第 6 步排在第 3、4、5 步之后执行（见 11 文档）。
- 包名：`com.paytech.risk.api.decision.*` → `com.paytech.risk.decision.api.*`，`com.paytech.risk.api.compliance.*` → `com.paytech.compliance.api.*`。

## 5. 装配拆分

- 两个 app 各自 DataSource、`@MapperScan`（`mapper/decision/**` 与 `mapper/compliance/**`）、TypeHandler 注册（decision 40 余个枚举，compliance 自己的）、Snowflake worker、`Clock`、补偿配置（`instance-id` 各自）、`web.logging.metadata-only-path-prefixes` 各列各的。
- `RiskOperationRequestContextParser` 下沉到 Foundation `paytech-mvc-web-starter`（它是通用的运营请求上下文解析），两个 app 复用。
- 开关 `onboarding.review.enabled`、`onboarding.materials.enabled` 随 compliance。
- MQ 生产者 `ClientReviewResultRabbitPublisher` 随 compliance，改用 `paytech-messaging` 发布（信封见 08），exchange 与 routing key 不变以免影响 CCT 消费者；consumer 侧随 client-master 搬迁时再切到新信封。

## 6. 测试

- 596 例按目录归属：`ext/http/decision`、`evaluation`、`control` 与 `RiskModuleBoundaryAutoTest` 去 risk-decision；`ext/http/compliance` 去 compliance；`WebLoggingBoundaryAutoTest` 两边各一份。
- `RiskDatabasePrecondition` 拆为两个：分别要求 `mor_risk_decision` 与 `mor_compliance` 的表数与目录行。
- `MockTestBase` 拆为两个，各自只导入本服务的 Mocker / TestMapper / Checker。
- 没有任何现有测试同时涉及两边，拆分不需要重写用例。

## 7. PDManer 与脚本

`risk-compliance.pdma.json` 与 10 个 Manifest 按归属拆为 `risk-decision.pdma.json`（Manifest 02、03、04、05、06、09、10）与 `compliance.pdma.json`（01、08 中的补偿部分两边各一份）与 `dispute.pdma.json`（07）；生成器 `scripts/pdmaner/risk/*` 相应拆分（见 09 文档）。`CODE-MOR:RISK:*` 哈希 Schema 标识保持不变（`SETTLEMENT_ELIGIBILITY_INPUT:1` 是对 Finance 的线上合同）。
