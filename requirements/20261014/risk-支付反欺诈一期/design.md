# 支付反欺诈一期：方案、接口与数据设计

- 状态：**阶段 3 已确认**（2026-10-14）；决定记录见 [2026-10-14 决定](../../../agent-docs/decisions/2026-10-14-risk-payment-fraud-phase1.md)
- 需求：[requirement.md](./requirement.md)；差异分析：[technique.md](./technique.md)；确认记录：[delivery.md](./delivery.md)
- 已确认取舍：D1 发布冻结决策流；D2 一期只做 TEST 环境激活；D3 一期不做限额占用；D4 由 Payment Card 提供卡指纹和 BIN；D5 累计只计入评估事件。

## 0. 方案摘要

```text
Payment PAY_RISK
  → POST /payment-risk-assessment/v1/assessments   （risk-api，内部同步）
  → 按 payment_attempt_id 加锁；已有评估直接返回原结果
  → 读取 PAYMENT_ATTEMPT_RISK 在 TEST/PRODUCTION 环境的 ACTIVE 发布（发布 = 决策点版本 + 决策流版本 + 流程引用的冻结规则集）
  → 事务外计算：输入变量、常量/集合（当前值）、实时累计特征、名单命中特征
  → 事务外执行决策流：规则集节点内按优先级首个终态命中 → 连线条件 → 结束节点给出 ALLOW/REVIEW/DENY
  → ALLOW 时，经过的规则若命中“要求 3DS”动作，认证要求为 THREE_DS_REQUIRED，否则 NOT_REQUIRED
  → 一个本地短事务写入：评估、交易评估输入快照、特征结果、规则轨迹、动作、累计事件
  → 返回 assessmentId、evaluationId、结论、认证要求、原因码
```

对已有设计（[Risk 数据库设计](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md)）的修订，确认后写入决定记录 `agent-docs/decisions/2026-10-14-risk-payment-fraud-phase1.md`，并同步数据库设计文档：

| 编号 | 原设计 | 一期修订 |
|---|---|---|
| R1 | 发布 = 决策点版本 + 有序规则集 | 新格式发布额外冻结一个决策流版本，运行按流程执行；旧结账发布路径不变 |
| R2 | 两名不同审核人批准后激活 | TEST 环境由一人激活并留审计；PRODUCTION 仍要求双人审批，一期不实现 |
| R3 | 支付放行必须执行限额占用义务 | 限额能力交付前，支付决策点版本不绑定占用义务；累计限额用实时累计规则实现 |
| R4 | Feature 只由工程注册 Handler | 保持；新增“实时累计”“名单命中”两个工程 Handler，运营通过变量配置其参数，参数随 Feature Version 不可变 |
| R5 | 交易评估输入按 Order Context/网络证据冻结 | 按 Payment 实际可提供的字段冻结（第 1 节），删除已废弃的 assignment 等字段 |
| R6 | Payment 事件携带 `limit_reservation_group_id` | 按 2026-09-14 决定删除该描述（文档漂移修正） |

## 1. 决策点与输入合同

**决策点：** `PAYMENT_ATTEMPT_RISK`（业务域 PAYMENT）。初始化一个决策点版本：输入合同 `PAYMENT_ATTEMPT v1`、输出合同 `RISK_DECISION_RESULT`、默认决定 **ALLOW**（待确认 S1）、策略 FIRST_TERMINAL、失败策略 FAIL_CLOSED、超时 500 毫秒、无系统义务。

**输入合同 `PAYMENT_ATTEMPT v1`**（工程注册，同时生成系统管理的 INPUT 变量）：

| 字段 | 类型/格式 | 必填 | 可用于规则 | 说明 |
|---|---|---|---|---|
| client_id | STRING / POSITIVE_ID | 是 | 条件、动作主体 | |
| entity_no | STRING / TOKEN | 是 | 条件 | Operating Entity 编号 |
| payment_method_code | STRING / TOKEN | 是 | 条件 | 精确支付方式 |
| capture_mode | STRING / TOKEN | 是 | 条件 | AUTOMATIC/MANUAL |
| amount | NUMBER / MONEY（币种取 currency） | 是 | 条件 | |
| currency | STRING / UPPER_ALPHA_3 | 是 | 条件 | |
| industry_code | STRING / TOKEN | 是 | 条件 | |
| mcc_code | STRING / DIGITS_4 | 是 | 条件 | |
| consumer_country_code | STRING / UPPER_ALPHA_2 | 否 | 条件 | Intent 上的消费者国家 |
| consumer_reference | STRING / TOKEN | 否 | 条件（名单） | Client 命名空间内的不透明引用；作为主体时键为 `clientId:consumerReference` |
| client_ip_address | STRING / IP | 否 | 名单、累计主体 | 受限字段，不进日志、MQ；规范化：IPv4 原值，IPv6 取 /64 前缀 |
| card_fingerprint | STRING / TOKEN | 否 | 名单、累计主体 | 跨交易稳定卡指纹（D4，Payment Card 提供）；非卡方式为空 |
| card_bin | STRING / DIGITS_6_8 | 否 | 条件、名单 | 卡号前 6～8 位 |

只记录、不能用于规则的字段：`user_agent`。邮箱/手机 Token、设备指纹、IP 地理信息不在一期合同中。

## 2. 支付评估接口（risk-api）

```java
@HttpExchange("/payment-risk-assessment/v1")
public interface PaymentRiskAssessmentApi {
    @PostExchange("/assessments")
    PaymentRiskAssessmentResponseBody assess(@Valid @RequestBody PaymentRiskAssessmentRequestBody request);
}
```

**请求 `PaymentRiskAssessmentRequestBody`**：`paymentAttemptId`、`paymentIntentId`、`riskActionId`、`orderId`（均为正整数字符串），以及第 1 节全部字段（驼峰命名）；金额为主单位十进制字符串。

**响应 `PaymentRiskAssessmentResponseBody`**：

| 字段 | 说明 |
|---|---|
| assessmentId | 交易评估 ID，Payment 保存到风控 Action |
| evaluationId | 评估 ID |
| riskResult | `ALLOW / REVIEW / DENY / FAILED / TIMED_OUT` |
| authenticationRequirement | 仅 ALLOW 时非空：`NOT_REQUIRED / THREE_DS_REQUIRED`；一期不产生 `CHALLENGE_REQUIRED` |

**身份与重放：**
- 业务身份是 `paymentAttemptId`（`t_transaction_risk_assessment` 唯一）。同一 Attempt 只做一次首次评估。
- 重放时 `riskActionId` 与“稳定输入摘要”都相同 → 返回原结果（含原 FAILED/TIMED_OUT）。
- `riskActionId` 不同或稳定输入摘要不同 → 409，Payment 不得覆盖原评估。
- 稳定输入摘要只覆盖 Payment 能重放的字段：IP、User-Agent 在 Payment 不落库，**不进入摘要**，以首次评估冻结的值为准。
- 不使用 Gateway 请求幂等；内部调用不经 Gateway。

**错误与失败：**

| 情况 | 返回 | Payment 处理 |
|---|---|---|
| 字段缺失或格式不符 | 422 `VALIDATION_ERROR` | 停止（FAILED） |
| IP 不是合法 IPv4/IPv6 字面量 | 400 `INVALID_REQUEST`，先于发布读取，不写评估 | 停止（FAILED） |
| 重放内容不一致 | 409 | 停止（FAILED） |
| 该环境没有 ACTIVE 支付发布 | 503，code `PAYMENT_RISK_NOT_CONFIGURED`，不写评估 | 停止（FAILED），不重试 |
| 评估执行失败、特征计算失败 | 200，`riskResult=FAILED`，已落库 | 停止 |
| 评估超过 500 毫秒 | 200，`riskResult=TIMED_OUT`，已落库 | 停止 |
| 数据库不可用等未落库的系统错误、Payment 读超时 | 5xx / 无响应 | 保持 UNKNOWN，按原请求重试取回原结果 |

## 3. 评估执行语义

### 3.1 发布读取
按 `PAYMENT_ATTEMPT_RISK` + 当前部署环境（配置项 `decision.runtime.environment`，test 为 TEST）读取唯一 ACTIVE 发布，每次评估从数据库读取发布内容（一期不做实例缓存，见第 16 节）。

### 3.2 决策流
- 从 START 出发，沿连线执行；RULE_SET 节点执行规则集，END 节点给出最终决定和原因。
- 连线：CONDITIONAL 按优先级取首个为真的；都不成立走 ELSE；UNCONDITIONAL 直接走。条件可引用 INPUT/CONSTANT/COLLECTION 变量，以及上一规则集节点的 `NODE_MATCHED`、`NODE_DECISION`。
- 流程已在保存时校验无环、可达、每条路径到达 END；运行时仍设置节点执行上限（50）防御。

### 3.3 规则集节点（FIRST_TERMINAL）
- 规则按 `priority_no` 升序，只执行 `enabled_flag=1` 的规则。
- 条件为真：记录命中；收集该规则的附加动作；若规则有终态动作（ALLOW/REVIEW/DENY），该决定成为节点决定并**停止本节点**其余规则。
- 节点输出：`matched` = 本节点有规则命中；`decision` = 节点决定，没有终态命中时为空。
- 节点的终态决定只作为连线条件的输入；**最终决定只由 END 节点给出**。

### 3.4 条件求值
- 运算符和类型矩阵沿用现有配置校验（GT/GTE/LT/LTE/BETWEEN 只用于数值、时间、时长；BETWEEN 为闭区间）。
- 金额比较要求币种坐标一致：金额变量与字面量币种不同时，该比较为假，不做汇率换算。
- 空值：可选输入为空时，除 IS_NULL/IS_NOT_NULL 外的比较结果为假。NOT 作用于假为真，因此运营写“不在名单中”应使用名单特征为 false，而不是对空值取反。
- 特征**计算失败**（不同于取值为空）→ 整次评估 FAILED（fail-closed）。

### 3.5 动作与认证要求
- 支付决策点的规则终态动作为四个：`ALLOW_PAYMENT`、`ALLOW_PAYMENT_WITH_THREE_DS`（决定 ALLOW 且要求 3DS）、`REVIEW_PAYMENT`、`DENY_PAYMENT`。一期不开放附加动作；冷却、打标签等仅配置动作不允许出现在支付规则中。
- 阶段 4 实施修订（2026-10-14）：原稿的非终态附加动作 `REQUIRE_THREE_DS` 改为终态动作。现有附加动作模型强制主体/取值参数且只允许配置、不允许冻结，改造风险大；终态动作复用已有规则动作、冻结与发布链路。运营把 3DS 规则放在流程中独立的“认证要求”规则集节点即可，效果相同。
- 最终决定为 ALLOW，且实际经过的规则集节点中有命中规则的终态动作为 `ALLOW_PAYMENT_WITH_THREE_DS` → `THREE_DS_REQUIRED`；否则 `NOT_REQUIRED`。REVIEW/DENY 时认证要求为空。
- 命中规则及其终态动作记录在规则轨迹（`t_evaluation_rule_trace`）中；一期不写 `evaluation_action_intent`/`action_effect`（见第 16 节）。

### 3.6 超时与失败
整个计算阶段（读取发布后、写库前）计时；超过决策点版本的 500 毫秒即中止，记 TIMED_OUT。异常记 FAILED 和 failure_code。FAILED/TIMED_OUT 也写评估和交易评估快照，但不写累计事件、动作记录。

## 4. 特征：实时累计与名单命中

### 4.1 变量与 Feature 的关系
- 新增两类变量来源：`VELOCITY`（实时累计）、`LIST`（名单命中）。
- 创建此类变量时，Risk 同时创建一个不可变的 `feature_version` 及其参数行，变量记录 `feature_version_id`。**参数创建后不可修改**；要改口径就新建变量。原因：变量是“实时读取当前值”的，若累计口径可改，已冻结的规则含义会悄悄变化。
- 评估时每个被引用的特征写一行 `evaluation_feature_result`（取值、耗时、状态），满足“可查到变量取值”（AC-7）。INPUT 取值在交易评估快照中；CONSTANT/COLLECTION 是当前值，一期不逐次留存（已知局限，见第 12 节）。

### 4.2 实时累计 Feature（Handler `VELOCITY_WINDOW_V1`）

| 参数 | 取值 |
|---|---|
| event_code | `PAYMENT_ATTEMPT_ASSESSED`（所有成功评估），`PAYMENT_ATTEMPT_REJECTED`（结论为 REVIEW/DENY） |
| anchor_subject_type | `CLIENT`、`OPERATING_ENTITY`、`CONSUMER`、`IP_ADDRESS`、`CARD_FINGERPRINT` |
| metric | `COUNT`、`SUM_AMOUNT`、`DISTINCT_COUNT`、`LAST_AMOUNT`、`SECONDS_SINCE_LAST` |
| distinct_subject_type | 仅 DISTINCT_COUNT：被去重的主体类型，如“同一 CONSUMER 用过的不同 CARD_FINGERPRINT 数” |
| window_seconds | 600 / 3600 / 86400 / 604800（一期固定这四种） |
| include_current | 仅 COUNT、SUM_AMOUNT：是否把本次计入（本次计 1 笔、本次金额），用于“含本次的累计限额” |
| 主体取值字段 | 由 anchor 类型决定：CLIENT←client_id，OPERATING_ENTITY←entity_no，CONSUMER←client_id+consumer_reference，IP_ADDRESS←client_ip_address，CARD_FINGERPRINT←card_fingerprint |

计算口径：
- 时间窗口为 `[评估时间 − window, 评估时间)`，以本次评估时间为锚点；窗口起点那一刻的事件计入，本次默认不计入，`include_current=true` 时再加上本次。
- SUM_AMOUNT 只累加与本次币种相同的事件，不换算。
- 本次主体取值为空（如无卡指纹）→ 特征值为空，不算失败。
- LAST_AMOUNT / SECONDS_SINCE_LAST：窗口内最近一次事件；没有则为空。
- 并发：两笔同一主体的请求几乎同时到达时，互相看不到对方，计数可能各少 1。这是累计规则（事后计数）的固有误差；需要严格不超额的场景属于二期限额占用。

**事件写入：** 正式（PRIMARY）评估成功时，在同一事务中写 `velocity_event`（来源为交易评估 ID，金额与币种）及其主体行（上表五类，取值为空的主体不写）；结论为 REVIEW/DENY 时再写一条 `PAYMENT_ATTEMPT_REJECTED`。影子运行不写事件。

### 4.3 名单命中 Feature（Handler `LIST_MEMBERSHIP_V1`）
参数：名单、主体类型；主体取值按类型取对应输入字段（同 4.2 映射，另加 `CARD_BIN`←card_bin）。结果为 BOOLEAN：在评估时间存在 ACTIVE、且 `effective_from ≤ 评估时间 < effective_to` 的成员即为 true。主体取值为空时为 false。

## 5. 名单维护（内部接口，无页面）

`RiskListManagementApi`，`@HttpExchange("/risk-lists/v1")`：

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | `/lists` | 创建名单：编码、名称、主体类型、用途（BLOCK/ALLOW，仅用于展示）；匹配方式固定 EXACT |
| GET | `/lists` | 分页查询名单（按编码升序） |
| POST | `/lists/{listCode}/entries` | 添加成员：主体取值、生效起止、原因、操作人；同名单同主体已有 ACTIVE 时返回 409 |
| POST | `/lists/{listCode}/entries/{entryId}/deactivate` | 失效成员：原因、操作人；只追加状态，不删除 |
| GET | `/lists/{listCode}/entries` | 按主体取值或状态分页查询 |

主体取值进入名单前按主体类型规范化（与 4.2 一致）。IP、卡指纹的查询响应只回显脱敏值。

## 6. 执行发布与激活（TEST）

现有“测试候选”路径只支持旧格式结账规则集，保持不变。新增执行发布路径（R1、R2），复用 `t_decision_release` 与审计表：

1. **创建候选** `POST /decision-releases/v1/executions`：入参决策点、决策流版本。要求流程引用的规则集版本全部 FROZEN、输入合同与决策点版本一致。发布冻结：决策点版本、决策流版本、规则集版本；内容摘要覆盖流程节点/连线、规则集摘要、引用的变量编码和 Feature Version。变量当前值不进入摘要（与现有规则集摘要一致）。
2. **静态校验** `POST .../executions/{releaseId}/validate`：引用完整性、条件形状与类型、Feature 存在且 ACTIVE、动作只允许本决策点开放的集合、每条路径终止于 END、无系统义务（R3）。通过后进入 SHADOWING 前状态。
3. **影子运行**（第 7 节）。存在 ACTIVE 基线时**必须**完成影子运行才能激活；首个发布没有基线，校验通过即可激活。
4. **激活** `POST .../executions/{releaseId}/activate`：仅 TEST 环境；记录一条 APPROVAL 审计（操作人）与状态变化；同一事务内候选 → ACTIVE、旧 ACTIVE → SUPERSEDED。影子运行结论变化不设自动阈值，由激活人查看差异后决定；技术门槛是影子运行中 FAILED/TIMED_OUT 为 0。
5. **回滚**：对历史发布的同一组合新建候选并激活，不修改旧发布。

规则集冻结沿用现有 `/decision-rule-sets/v1/rule-sets/{id}/freeze`，需要放开新格式规则集的冻结（若现有限制阻止）。

## 7. 影子运行：回放最近样本

测试环境流量少，“等新流量旁路”可能长期攒不够样本。按原设计“使用同一输入快照和评估时间”的要求，影子运行改为**回放最近的正式评估**：

- `POST /decision-releases/v1/executions/{releaseId}/shadow-runs`：样本数（默认 200，上限 2000）。
- 后台经本地补偿任务分批执行：对基线发布最近 N 条成功的 PRIMARY 评估，用其交易评估快照和原评估时间，以候选发布执行一次 SHADOW 评估；累计特征按原评估时间回看，并排除该评估之后写入的事件。
- 每个样本写一行 `decision_evaluation`（SHADOW，带基线评估 ID 和差异标记）；动作只写模拟记录；不写累计事件。
- 完成后汇总到 `release_shadow_run`：ALLOW→REVIEW、ALLOW→DENY、DENY→ALLOW 数量，动作变化数，特征缺失、失败、超时数，P95/P99 耗时。
- 汇总随发布查询 `GET .../executions/{releaseId}` 的 `shadowRun` 返回（最近一个批次）；一期不提供单独的批次查询与逐样本分页（见第 16 节）。

## 8. 数据库变更（Risk 单库）

所有表当前没有生产代码写入，改动不涉及历史数据迁移。PDManer 通过 Risk 生成器 Manifest 修改并重新生成；增量 SQL 放在 `db/ddl.sql`、`db/dml.sql`，执行需单独授权。

| 表 | 变更 |
|---|---|
| `t_transaction_risk_assessment` | 删除 `order_context_id`、`assignment_id`、`operating_entity_id`、`network_evidence_id`、`network_evidence_version`；新增 `risk_action_id`、`entity_no`、`capture_mode`、`consumer_country_code`、`client_ip_address`、`user_agent`、`card_fingerprint`、`card_bin`、`stable_input_hash`、`authentication_requirement`；保留邮箱/手机 Token 列（可空，一期不写）；`restricted_input_hash` 改为覆盖全部冻结输入 |
| `t_risk_list` | 新增 `list_purpose`（BLOCK/ALLOW）；主体类型增加 `CARD_FINGERPRINT`、`CARD_BIN` |
| `t_risk_list_entry` | 主体类型增加卡维度；`source_effect_id` 改为可空；新增 `source_type`（ACTION/MANUAL，与 source_effect_id 成对）、`reason`、`created_by`、`deactivated_by`、`deactivated_reason` |
| `t_decision_release` | 新增 `decision_flow_version_id`（可空；旧发布为空） |
| `t_risk_variable` | 新增 `feature_version_id`（VELOCITY/LIST 来源必填，其他为空） |
| 新表 `t_velocity_feature_definition` | 与 `t_feature_version` 一对一：event_code、anchor_subject_type、metric、distinct_subject_type、window_seconds、include_current |
| 新表 `t_list_feature_definition` | 与 `t_feature_version` 一对一：list_id、subject_type（主体取值字段由类型决定，不单独存储） |
| 索引 | `t_velocity_event_subject` 已有 (subject_type, subject_key, event_code, occurred_time)；去重计数按 event_id 回连，现有唯一键可用；不新增 |

**初始化数据（dml）：** 决策点与决策点版本、`PAYMENT_ATTEMPT v1` 的系统 INPUT 变量、`NODE_*` 结果变量对支付决策点的登记、动作版本 `ALLOW_PAYMENT`/`ALLOW_PAYMENT_WITH_THREE_DS`/`REVIEW_PAYMENT`/`DENY_PAYMENT`。**不初始化规则和名单**：阈值是业务决定，第一批模板以文档和自动化测试规则交付（第 10 节）。

## 9. 配置侧改造（保持结账行为不变）

- 把 `CheckoutPrepareAuthoringDescriptor` 的单例用法改为按决策点注册（结账、支付两个描述符），`DecisionSourceContracts`、动作目录、规则/流程校验、规则编写上下文按 `pointCode` 取描述符。
- 结账的输入合同、动作 ID、Handler 摘要、发布摘要和测试候选流程保持逐字节不变，现有测试作为回归门禁。
- 支付决策点只走新格式规则集与执行发布路径。

## 10. 第一批规则模板

以下模板写入 `design.md` 附录并作为自动化测试的示例规则（阈值为测试值，不是上线建议）：

| 模板 | 示例 |
|---|---|
| 黑名单 | 消费者/IP/卡指纹/BIN 命中 BLOCK 名单 → DENY |
| 白名单 | 消费者命中 ALLOW 名单 → 流程直接走 ALLOW 结束，跳过后续限额节点 |
| 单笔限额 | amount > 常量阈值（同币种）→ REVIEW 或 DENY |
| 累计限额 | 同一消费者 24 小时 SUM_AMOUNT（含本次）> 阈值 → DENY |
| 频次 | 同一卡指纹 10 分钟 COUNT ≥ N → DENY；同一 IP 1 小时 REJECTED 次数 ≥ N → DENY |
| 多卡/多账户关联 | 同一消费者 24 小时 DISTINCT_COUNT(CARD_FINGERPRINT) ≥ N；同一卡指纹 24 小时 DISTINCT_COUNT(CONSUMER) ≥ N → REVIEW |
| 3DS 兜底 | 认证要求节点中：卡支付且 amount > 阈值或新卡（SECONDS_SINCE_LAST 为空）→ ALLOW_PAYMENT_WITH_THREE_DS |

需求中的“IP 与卡国家不一致”需要 IP 地理库和 BIN 国家数据，两者一期都没有来源（不接供应商），**建议移到二期**（S2）。

## 11. 事务、并发与性能

- Processor 使用 `AbstractLockProcessor`（TRY_LOCK，资源为 `payment_attempt_id`）；锁内先按 Attempt 查已有评估，存在则按第 2 节重放规则返回。
- 读取发布、计算特征、执行流程都在事务外（只读）；一个本地短事务写入全部评估事实与累计事件。数据库唯一键 `payment_attempt_id` 作为并发兜底，冲突时回读原结果。
- 没有任何外部调用。
- 延迟目标：本机 P99 ≤ 500 毫秒（即决策点超时），P95 ≤ 150 毫秒；每个特征是一次索引范围查询。热点主体（如大 Client 的 24 小时计数）在一期可接受，后续按原设计增加可重建的分桶投影。

## 12. 已知局限
- CONSTANT/COLLECTION 是当前值，评估不逐次留存；规则命中后若阈值被改，只能从变量的更新时间判断。
- 累计计数在并发下可能少计（4.2）。
- 无 IP 地理、设备指纹、邮箱维度。
- 一期没有页面，名单、发布、影子运行通过内部接口操作。

## 13. 测试计划（Risk，`auto`，真实本地 MySQL/Redis）

| 入口 | 覆盖 |
|---|---|
| PaymentRiskAssessmentApi | 三种结论；3DS；默认决定；重放同结果；改内容 409；无发布 503；特征失败 FAILED；超时 TIMED_OUT；并发同 Attempt 只一条评估；评估记录与轨迹完整（AC-1/2/5/7/8） |
| 累计 | 四窗口边界（起点计入、本次按参数计入）、四种算法、币种过滤、空主体、REJECTED 事件、影子不写事件（AC-3） |
| RiskListManagementApi + 评估 | 黑/白名单命中、生效区间、失效后不命中、重复 409（AC-4） |
| 执行发布 | 冻结约束、静态校验、首发直接激活、有基线必须影子、激活切换原子性、回滚（AC-6 前置） |
| 影子运行 | 回放样本、差异汇总、不写累计事件、不影响正式结论（AC-6） |
| 模板 | 第 10 节 6 类各一条规则经真实流程命中（AC-9） |
| 结账回归 | 现有 Risk 全量测试不变 |

## 14. 交给 Payment 的接入清单（不在本会话范围）

1. 用 `risk-api` 的 `PaymentRiskAssessmentApi` 替换自写草案客户端，补齐第 2 节请求字段。
2. Card 提供跨交易稳定的 `cardFingerprint`（平台密钥下的 PAN 摘要）与 `cardBin`；非卡方式传空。
3. DENY/REVIEW → Attempt FAILED、Intent 释放（修复当前停在 PROCESSING 的缺陷）；FAILED/TIMED_OUT/400/409/422/`PAYMENT_RISK_NOT_CONFIGURED` → FAILED。
4. 读超时与其他 5xx → 风控 Action UNKNOWN，恢复任务以原 `paymentAttemptId + riskActionId` 重试。
5. 保存 `assessmentId`、`riskResult`、`authenticationRequirement`；补充 DENY/REVIEW/3DS/超时恢复测试。

## 15. 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| S1 | 支付决策点默认决定（流程无规则命中且 END 未另行指定时） | ALLOW。结账默认 REVIEW，但支付默认 REVIEW 会拒绝所有未命中规则的正常支付 |
| S2 | “IP 与卡国家不一致”模板 | 移到二期；AC-9 改为 6 类 |
| S3 | 影子运行用“回放最近正式评估”而不是“旁路新流量” | 采用回放 |
| S4 | 名单、发布、影子运行一期只提供内部接口 | 采用；页面二期 |

## 16. 阶段 4 实施修订（2026-10-14）

以下为实现时相对本设计的调整，均不改变已确认的 D1～D5、S1～S4：

| 项 | 原设计 | 实现 | 原因 |
|---|---|---|---|
| 3DS 动作 | 非终态附加动作 `REQUIRE_THREE_DS` | 终态动作 `ALLOW_PAYMENT_WITH_THREE_DS`（3.5） | 附加动作模型不允许冻结，改造风险大 |
| 累计“计入本次” | 固定口径 | `include_current` 参数，只允许评估事件的 COUNT/SUM_AMOUNT | 评估前不知道本次是否被拒绝 |
| 响应 `reasonCode` | 拒绝/审核时返回 | 删除；原因编码在评估与 END 节点配置中，排查查库 | Payment 不展示，避免对外合同多一个字段 |
| 动作记录 | 写 `evaluation_action_intent`/`action_effect` | 只写规则轨迹 | 一期动作无外部副作用，轨迹已可追溯命中规则与动作 |
| 发布缓存 | 实例缓存 | 每次读取数据库 | 先保证正确性；性能达标后再加 |
| 并发兜底 | 唯一键冲突回读原结果 | 同一 Attempt 由 TRY_LOCK 串行；锁外极端冲突返回系统错误，Payment 按 UNKNOWN 重试取回原结果 | 不重复实现两套重放路径 |
| 字段校验 | 400 | 字段合同 422；IP 字面量在构造输入时校验，非法返回 400 | 沿用项目 Bean Validation 映射；IP 无法用正则准确校验 |
| 名单查询 | 含单个名单查询 | 只有名单分页、成员分页 | 一期无页面，分页已够用 |
| 影子运行查询 | 单独批次查询与逐样本分页 | 只随发布返回最近批次汇总 | 同上 |
| 静态校验失败 | — | 发布内容无法读取（引用被删除等）时 8 项检查全部记为失败，发布 REJECTED | 无法读取就无法证明任何一项通过 |
| 旧发布接口 | — | 旧测试候选列表/详情排除执行发布（`decision_flow_version_id` 非空） | 两条发布路径互不干扰 |
| 流程输入合同 | 结账合同写死 | 流程行只校验合同已登记；流程图重建时按决策点严格比对 | 支持支付决策点流程 |

已知缺口（一期不处理）：
- 删除规则集时不检查其冻结版本是否被生效的执行发布引用；运营删除后，下次评估返回 FAILED（fail-closed），需新建发布恢复。
- 未自动化覆盖 TIMED_OUT（500 毫秒超时难以在测试中稳定制造）与同一 Attempt 并发评估。

