# 支付反欺诈一期：技术方案

- 状态：**阶段 2 已确认**（现状差异与影响分析）。方案、接口与数据设计见 [design.md](./design.md)（阶段 3 评审稿）。
- 需求：[requirement.md](./requirement.md)；确认记录：[delivery.md](./delivery.md)

## 1. 已有依据

| 依据 | 内容 |
|---|---|
| [Risk 数据库设计](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md)、[Risk Schema](../../../paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql) | 运行期表已在完整 Schema 中（81 表），包括评估、Feature、实时累计、名单、影子运行、交易评估、限额 |
| [内部风控 Action 修订](../../20260913/payment-Execution设计/internal-risk-action-amendment.md) | 请求带 Intent/Attempt/风控 Action 身份、方式、捕获模式、金额与工具；按原 Attempt 去重，同身份不同内容冲突；本地超时保持 UNKNOWN 并恢复原评估；验收 RSK-01～09 |
| [CARD + PAY 修订](../../20260913/payment-Execution设计/card-pay-action-flow-amendment.md) | 风控步骤 `PAY_RISK`；DENY/REVIEW 使 Attempt FAILED、Intent 保持 OPEN |
| [字段精简修订](../../20260913/payment-Execution设计/execution-field-simplification-amendment.md) | 选路先于风控；风控 Action 冻结认证要求 |
| [限额关联修订](../../20260913/payment-Execution设计/risk-limit-correlation-amendment.md) | Payment 不保存 Group；Risk 按 Attempt 定位；PaymentSucceeded 提交、AttemptFailed 释放、AttemptUnknown 保持占用 |
| `RCO-I04` 执行实时交易风险评估（`requirements-site/content/requirements.ts`） | 输入含 Order Context、消费者 Token、网络证据与 Geo/ASN；PAYMENT 放行执行 `RESERVE_LIMIT_GROUP` |
| [决策流配置](../../20260912/operations-console-决策流配置/requirement.md)、[统一变量管理](../../20260912/operations-console-统一风控变量管理与条件编排/requirement.md) | 现有配置：决策流不参与发布；变量按决策点和来源合同登记 |

## 2. 现状差异

### 2.1 Risk

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 建议 |
|---|---|---|---|---|
| 支付决策点 | 只有 `CHECKOUT_PREPARE` 种子（`risk-compliance-initial-data.sql`）；输入合同只有 `ORDER_PREPARATION v4`（`DecisionSourceContracts`）；配置代码 15 个类直接依赖 `CheckoutPrepareAuthoringDescriptor` | 新增 `PAYMENT_ATTEMPT_RISK` 及支付输入合同 | 未实现；配置侧按单决策点写死 | 扩展：把描述符改为按决策点注册，结账保持原 Hash 与行为 |
| 结论动作 | 只有 `ALLOW/REVIEW/DENY_CHECKOUT` 三个终态动作，`DecisionValue` 为 ALLOW/REVIEW/DENY | 支付三结论 + 认证要求 | 无支付动作，无“要求 3DS” | 新增支付三结论动作和一个非终态“要求 3DS”动作；放行时无 3DS 动作即 `NOT_REQUIRED`。一期不产生 `CHALLENGE_REQUIRED` |
| 运行期评估 | 生产代码不读写任何运行期表（`decision_evaluation`、`evaluation_*`、`action_effect` 引用数为 0） | 评估引擎与记录 | 全部未实现 | 新建，按已有表写评估、Feature 结果、规则轨迹 |
| 执行单元 | 发布 = 决策点版本 + 有序规则集（`t_decision_point_rule_set`）；决策流“不是运行发布版本”，多条流不选生效者（决策流需求第 69、85 行） | 规则按发布版本执行 | 发布不包含决策流；流程节点只有开始、规则集、结束 | **待决 D1**：见第 4 节 |
| 发布生效 | 只有 TEST 候选的创建、校验、查询（`CreateTestRelease`/`ValidateTestRelease`）；无冻结、审批、激活 | 有一个生效发布供评估读取 | 无激活 | **待决 D2** |
| 实时累计 | `velocity_event`、`velocity_event_subject` 已建表，无代码；`feature_version` 只有 handler_code，没有维度/窗口/算法参数 | 4 种算法 × 多维度 × 4 窗口 | 无实现；参数无处存放 | 新建；阶段 3 设计 Feature 参数（建议参数化的累计 Feature，而非每种组合注册一个 Handler） |
| 运营名单 | `risk_list`、`risk_list_entry` 已建表，无代码；`source_effect_id` 非空，只能由动作产生成员 | 手工维护黑白名单 | 无法人工添加；无维护接口 | 改表：来源改为“动作或人工”，人工记录操作人和原因；新增内部维护接口 |
| 变量来源 | `VariableSource` = INPUT/CONSTANT/COLLECTION/NODE_OUTPUT；`OperandSource` 已有 FEATURE | 规则引用累计值和名单命中 | 变量不能指向累计或名单 | 新增变量来源（累计、名单），规则仍经变量引用 |
| 交易评估记录 | `t_transaction_risk_assessment` 必填 `order_context_id`、`assignment_id`、数值 `operating_entity_id`、`network_evidence_id/version` 等；无认证要求列 | 按 Payment 实际可给字段冻结输入 | Payment 没有这些字段；`assignment_id` 已被 2026-09-11 决定废弃；OE 在 Payment 是字符串 `entityNo` | 改表：以 Payment 实际字段为准（第 2.2 节），加认证要求列 |
| 影子运行 | `release_shadow_run` 及评估 SHADOW 比较列已建表，无代码 | 候选版本旁路比较 | 未实现 | 新建，按已有表 |
| 限额占用 | `limit_*` 七表已建，无代码；设计要求 PAYMENT 放行必须执行占用；释放/提交依赖 Payment MQ 事件 | — | 未实现；Payment 未发布这些事件 | **待决 D3** |
| 文档不一致 | `RISK_COMPLIANCE_DATABASE.md` 第 409–411、704–706 行仍写 Payment 事件携带 `limit_reservation_group_id`，与 2026-09-14 决定冲突 | — | 文档漂移 | 阶段 3 一并修正 |

### 2.2 支付评估可用输入（Payment 在 `PAY_RISK` 时实际掌握的事实）

| 事实 | Payment 现状 | 一期可用 | 风控用途 |
|---|---|---|---|
| Client、OE（`entityNo`）、金额、币种、行业、MCC、消费者国家 | Intent 上已有 | 是 | 限额、行业、国家规则 |
| 订单 ID、Intent、Attempt、风控 Action、支付方式、捕获模式 | 已有 | 是 | 身份、幂等、方式路由 |
| `consumerReference`、IP、User-Agent | pay 请求里有，但不落库，重放时缺失 | 是（Risk 首次评估时冻结） | 用户维度、IP 维度累计 |
| 交易级工具 Token | 有，但按 Client/Intent/提交唯一，**跨交易不稳定** | 不能作为卡维度 | — |
| 跨交易稳定的卡指纹、卡 BIN/发卡国家 | **没有**；卡身份查询只在交易作用域内 | 需 Payment Card 新增 | 多卡关联、卡频次、卡国家不一致 |
| 邮箱/手机 Vault Token、网络证据 ID | 只在 Commerce，Payment 未接收 | 需 Commerce→Payment 传递 | 账户/联系方式维度 |
| 设备指纹 | 无 | 否（一期不接供应商） | — |

结论：一期**不依赖其他系统新增字段**时，可用的累计维度只有 Client、OE、`consumerReference`、IP；需求中“多卡关联”和“IP 与卡国家不一致”两类模板依赖卡指纹与 BIN 国家，**待决 D4**。

### 2.3 Payment（由负责 Payment 的会话或另行授权处理）

| 项 | 当前 | 与设计的差距 |
|---|---|---|
| Risk 客户端 | Payment 自写草案 `POST /risk-assessment/v1/payment-assessments`，8 个字段，3 秒读超时，标注待迁至 `risk-api` | Risk 不存在该接口；需改用 Risk 发布的 `risk-api` 合同并补充字段 |
| DENY/REVIEW | Action 记为 SUCCEEDED，Attempt 仍为 PROCESSING，Intent 未释放 | 设计要求 Attempt FAILED、Intent OPEN；现有缺陷 |
| 超时 | 3 秒超时当作 FAILED | 设计要求 UNKNOWN 并恢复原评估；缺恢复任务 |
| 测试 | Mock 固定返回 assessmentId 7001；无 DENY/REVIEW 用例 | 接入后补充 |
| 端到端 | Commerce 目前不调用 Payment `pay`（只有测试 Mock 实现） | 一期本机联调只能直接调用 Payment `pay` 或 Risk 接口 |

## 3. 逐系统影响

| 系统 | 职责 | 影响点 | 改代码/模型/配置 | 依赖与风险 |
|---|---|---|---|---|
| Risk | 评估、累计、名单、发布、影子 | 新决策点与合同、动作、评估引擎、Feature、名单、发布激活、影子、`risk-api` 支付评估接口；改两张表（交易评估、名单成员）及种子数据 | 代码、PDManer、增量 DDL/DML | 配置侧去掉单决策点写死，必须保持结账既有 Hash 和接口行为不变 |
| Payment | 风控步骤调用与结果处理 | 换用 `risk-api`、补字段、修 DENY/REVIEW 状态、UNKNOWN 恢复；若选 D4-a，Card 提供稳定卡指纹与 BIN 国家 | 代码，可能有表 | 不在本会话范围；需授权或交给 Payment 会话 |
| Commerce | 消费者与网络输入 | 一期默认不改（邮箱 Token 等留到后续） | 否 | — |
| Operations BFF/前端 | 页面 | 一期不做页面；新决策点出现在现有规则配置页面时的显示需在阶段 3 核对 | 否（待核对） | 现有页面假设只有结账决策点 |
| Gateway/Platform | — | 内部服务调用不经 Gateway | 否 | — |

## 4. 待决事项（阶段 2 需要用户选择）

| 编号 | 问题 | 选项 | 建议 |
|---|---|---|---|
| D1 | 运行时执行什么 | a. 发布冻结一个决策流版本，按流程执行（节点内规则集首个命中）；b. 只按发布中的有序规则集执行，决策流留到二期 | **a**。运营已在用决策流配置，且外部体系也以流程编排白名单→黑名单→限额；需在发布中增加决策流版本绑定 |
| D2 | 发布如何生效 | a. 一期只做 TEST 环境冻结与激活（单人操作、留审计）；b. 同时做 PROD 审批流 | **a**。一期只到 test 环境；PROD 审批随上线需求做 |
| D3 | 限额占用 | a. 一期不做，修订设计：在限额能力交付前，支付决策点不要求占用义务；b. 只建空占用组；c. 完整实现，含 Payment 事件 | **a**。累计限额先用实时累计规则实现（事后计数，不预占）；b 会产生永远不结束的占用记录，c 依赖 Payment 新增三类 MQ 事件 |
| D4 | 卡维度 | a. 请 Payment Card 提供跨交易稳定的卡指纹和 BIN 国家，一期包含多卡与卡国家模板；b. 一期不含卡维度，两个模板移到二期 | **a**，若 Payment 侧排期不允许则 b。卡维度是收单反欺诈最核心的维度 |
| D5 | 累计计入哪些事件 | a. 一期只计入“评估发生”（含结论），成功/失败结果等 Payment 事件后续接入；b. 一期同时接入 Payment 结果事件 | **a**。外部规则多数按尝试计数；成功类累计依赖 Payment 新事件 |

延迟目标：Payment 现有读超时 3 秒；结账决策点超时 500 毫秒。阶段 3 按支付决策点 500 毫秒（P99）提出目标。

## 5. 阶段安排

| 阶段 | 范围 | 确认点 |
|---|---|---|
| 1 需求评审 | 已确认 | — |
| 2 现状差异 | 本文 | 用户确认 D1～D5 |
| 3 方案设计 | `risk-api` 支付评估合同、引擎、Feature 参数、名单、发布激活、影子、3DS 动作、PDManer 与增量 DDL/DML、延迟目标、测试计划；交给 Payment 的接入清单 | 用户确认后研发 |
| 4 研发 | 4a 决策点注册化与支付合同 → 4b 名单与实时累计 → 4c 评估引擎与记录、发布激活 → 4d 支付评估接口（幂等、fail-closed）→ 4e 影子运行与模板种子 | 每批汇报 |
| 5 本地验证 | Risk 全量自动化测试；Payment 接入后本机联调 | 用户确认 |
| 6～7 test 发布与验收 | 部署清单、SQL、恢复方案；执行需单独授权 | 用户授权 |
| 8 提交归档 | 提交和推送分别授权 | 用户确认结束 |
