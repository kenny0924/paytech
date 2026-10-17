# 结账点实时累计：方案设计

- 阶段 3：方案、接口与数据设计（2026-10-17，**已确认**）
- 依据：[需求](./requirement.md)（Q1～Q7）、[差异与影响](./technique.md)（D1～D4 已确认，按建议）、[结账点运行期评估设计](../../20261016/risk-结账点运行期评估/design.md)、[支付反欺诈一期设计](../../20261014/risk-支付反欺诈一期/design.md)
- SQL：[db/ddl.sql](./db/ddl.sql)、[db/ddl-rollback.sql](./db/ddl-rollback.sql)（待执行，设计批准不改变任何环境）

**规模：**涉及 3 个系统（Risk、Operations BFF、Operations Web）；**新增 0 个接口、0 个路由变化**，1 个已有 Risk 接口（变量创作上下文）的响应新增 1 个字段 `featureCatalogs`，BFF 同名接口同步透传；**新增 0 张表**，DDL 3 条（2 条只改注释、1 条在线重建索引），**业务 DML 0 行**，Platform 注册 DML 0 行；新增页面 0 个，调整已有页面 1 个（变量创建页的实时累计与名单参数区）。

## 1. 主流程

```text
结账评估（Commerce → Risk，接口与请求不变）
  CheckoutPrepareEvaluator
    按 orderId 查快照：有 → 重放原结果（不执行、不写事件）
    读结账生效执行发布 → RuntimeEvaluationExecutor（Feature 读取 → 引擎）
    CheckoutRiskFacts.build（纯内存）
      评估行 + 快照 + Feature 结果 + 轨迹
      【新】SUCCEEDED：CHECKOUT_ASSESSED；结论非 ALLOW：再加 CHECKOUT_REJECTED
            每条事件的主体 = 本点声明的累计主体中本次有值的（Client、运营主体）
    CheckoutRiskAssessmentService.save：一个 TransactionTemplate 写入全部行（新增事件、事件主体）

Feature 读取（不改）：按 [评估时间 − 窗口, 评估时间) 查 t_velocity_event_subject，金额只合计同币种
变量创建（运营 → BFF → Risk）
  CreateVariableProcessor.validateFeature → FeatureHandlers.validate【改】按变量所属风控点校验事件、主体、指标与窗口
变量创作上下文（Web 打开变量页）
  VariableAuthoringContextProcessor【改】追加 featureCatalogs（每个支持 Feature 的风控点一项）
```

- **数据 Owner：**事件、Feature 定义、名单均归 Risk；BFF 只做透传与严格形状校验；Web 不再写死任何风控点、事件或主体。
- **事务：**事件与评估同一本地事务（同支付）；无外部调用，不新增异步任务或 MQ。
- **幂等：**重放命中快照直接返回，不再执行；并发同一订单由 `uk_checkout_order` 挡住，失败方整个事务（含事件）回滚；`uk_velocity_source(source_type, source_id, source_version, event_code)` 兜底。
- **并发计数（D4）：**同一 Client 的两笔并发结账互相看不到对方尚未提交的事件，计数偏低最多为并发数 − 1，与支付现状一致，不加锁。
- **影子：**影子回放不经过 `CheckoutRiskFacts.build`，不写事件；以原评估时间为锚点、窗口右开，不会把原评估自身的事件计入。

## 2. 接口

### 2.1 Risk：`GET /decision-variables/v1/authoring-context?pointCode=`（路由、请求、权限不变）

响应 `VariableManagementContextResponseBody` 末尾新增 `featureCatalogs`（非空数组）。规则：

- 只列支持 Feature 变量的风控点（当前为 `CHECKOUT_PREPARE`、`PAYMENT_ATTEMPT_RISK`，按描述符登记顺序）；指定 `pointCode` 时只返回该点一项，该点不支持时为空数组。
- 所有值来自工程登记（描述符 + 枚举 + `FeatureHandlers`），Processor 仍无数据库访问；不替代变量库与名单库查询。
- 其他字段不变；`sources[VELOCITY/LIST].createAllowed` 对结账改为 true（随描述符）。

```json
"featureCatalogs": [
  {
    "pointCode": "CHECKOUT_PREPARE",
    "currencyVariableCode": "INPUT_CURRENCY",
    "velocityEvents": [
      { "eventCode": "CHECKOUT_ASSESSED", "eventName": "结账评估", "includeCurrentAllowed": true },
      { "eventCode": "CHECKOUT_REJECTED", "eventName": "结账被拦截（审核或拒绝）", "includeCurrentAllowed": false }
    ],
    "velocityAnchors": [
      { "subjectType": "CLIENT", "metrics": [
          { "metric": "COUNT", "windowSeconds": [600, 3600, 86400], "includeCurrentAllowed": true },
          { "metric": "SUM_AMOUNT", "windowSeconds": [600, 3600], "includeCurrentAllowed": true },
          { "metric": "LAST_AMOUNT", "windowSeconds": [600, 3600, 86400], "includeCurrentAllowed": false },
          { "metric": "SECONDS_SINCE_LAST", "windowSeconds": [600, 3600, 86400], "includeCurrentAllowed": false } ] },
      { "subjectType": "OPERATING_ENTITY", "metrics": [ "…同 CLIENT…" ] }
    ],
    "distinctSubjects": [],
    "listSubjects": ["CLIENT", "OPERATING_ENTITY"]
  },
  {
    "pointCode": "PAYMENT_ATTEMPT_RISK",
    "currencyVariableCode": "PAY_INPUT_CURRENCY",
    "velocityEvents": [
      { "eventCode": "PAYMENT_ATTEMPT_ASSESSED", "eventName": "支付尝试评估", "includeCurrentAllowed": true },
      { "eventCode": "PAYMENT_ATTEMPT_REJECTED", "eventName": "支付尝试被拦截（审核或拒绝）", "includeCurrentAllowed": false }
    ],
    "velocityAnchors": [
      { "subjectType": "CLIENT", "metrics": [ "…D1 限制，同结账…" ] },
      { "subjectType": "OPERATING_ENTITY", "metrics": [ "…D1 限制…" ] },
      { "subjectType": "CONSUMER", "metrics": [ "五种指标，窗口均为 [600, 3600, 86400, 604800]" ] },
      { "subjectType": "IP_ADDRESS", "metrics": [ "…同 CONSUMER…" ] },
      { "subjectType": "CARD_FINGERPRINT", "metrics": [ "…同 CONSUMER…" ] }
    ],
    "distinctSubjects": ["CLIENT", "OPERATING_ENTITY", "CONSUMER", "IP_ADDRESS", "CARD_FINGERPRINT"],
    "listSubjects": ["CLIENT", "OPERATING_ENTITY", "CONSUMER", "IP_ADDRESS", "CARD_FINGERPRINT", "CARD_BIN"]
  }
]
```

| 字段 | 含义 |
|---|---|
| `currencyVariableCode` | 金额类累计变量绑定的本点币种输入变量（系统变量，初始化数据登记） |
| `velocityEvents[].includeCurrentAllowed` | 该事件每次成功评估都会写入，可选“计入本次”（评估事件 true，拦截事件 false） |
| `velocityAnchors[].metrics[]` | 该锚定主体可选的指标与窗口；未列出的指标不可选（Client、运营主体没有 `DISTINCT_COUNT`） |
| `metrics[].includeCurrentAllowed` | 只有次数、金额合计可计入本次；最终可选 = 事件 ∧ 指标 |
| `distinctSubjects` | 可作为去重对象的主体（仍不能与锚定主体相同）；结账为空 |
| `listSubjects` | 名单命中变量可用的名单主体类型 |

**字段命名避让 BFF 严格校验的特殊键**（`sourceType`、`valueType`、`status`、`numericUnit`、`usages`）；`currencyVariableCode` 走已有编码正则，所有新字段非空。

### 2.2 变量创建 `POST /decision-variables/v1`（请求、响应不变，校验收紧）

`FeatureHandlers.validate` 先按变量 `pointCode` 取描述符，再依次校验（任一不满足返回现有“变量结构无效”错误，信息不含输入值）：

| 序 | 条件 | 错误信息 |
|---|---|---|
| 1 | 事件编码所属风控点 = 变量风控点 | 统计事件不属于该决策点 |
| 2 | 锚定主体 ∈ 本点 `velocityAnchors` | 统计主体不适用于该决策点 |
| 3 | 指标、窗口 ∈ 该锚定主体的允许组合（D1） | 该统计主体不支持所选统计方式或时间窗口 |
| 4 | 去重：指标为 DISTINCT_COUNT ⇔ 有去重主体；去重主体 ∈ 本点 `distinctSubjects` 且 ≠ 锚定主体 | 去重主体只适用于不同主体数，且须为该决策点可用主体并与统计主体不同 |
| 5 | 计入本次：事件与指标的 `includeCurrentAllowed` 都为真 | 只有评估事件的次数和金额合计可以计入本次（原文保留） |
| 6 | 值类型、量纲与指标一致（原有） | 原文保留 |
| 名单 | 名单主体 ∈ 本点 `listSubjects`；名单存在、ACTIVE、主体一致（原有） | 名单主体不适用于该决策点 |

- **只在创建时校验**，不进入 `verifyIntegrity` 与执行快照读取：历史变量（mor-test 的两个卡指纹变量满足新约束）读取不受影响。
- 金额类变量的币种变量仍由 `VariableDefinitionValidator.validateCurrency` 校验“同点真实币种输入”，不新增规则。

### 2.3 Operations BFF：`GET /decision-variables/v1/authoring-context`（BFF 同名路由，不变）

响应新增同名字段 `featureCatalogs`，结构与 Risk 一致；严格形状校验由 risk-api 类字段自动覆盖。

## 3. Risk 领域规则

### 3.1 事件编码 `VelocityEventCode`

| 编码 | 名称 | 所属风控点 | 可计入本次 |
|---|---|---|---|
| `PAYMENT_ATTEMPT_ASSESSED` | 支付尝试评估 | `PAYMENT_ATTEMPT_RISK` | 是 |
| `PAYMENT_ATTEMPT_REJECTED` | 支付尝试被拦截（审核或拒绝） | `PAYMENT_ATTEMPT_RISK` | 否 |
| `CHECKOUT_ASSESSED`（新） | 结账评估 | `CHECKOUT_PREPARE` | 是 |
| `CHECKOUT_REJECTED`（新） | 结账被拦截（审核或拒绝） | `CHECKOUT_PREPARE` | 否 |

枚举增加 `eventName`、`pointCode`、`includeCurrentAllowed` 三个属性，并提供 `of(pointCode)`；`FeatureHandlers` 原 `PAYMENT_ATTEMPT_ASSESSED` 写死改为读属性。

### 3.2 风控点 Feature 目录（描述符）

`RuntimeCapabilities` 增加 `velocitySubjects`、`listSubjects`、`currencyVariableCode`；结账改为 `featureVariablesSupported=true`：

| 风控点 | 累计主体（写入事件主体 = 可锚定 = 可去重） | 名单主体 | 币种变量 |
|---|---|---|---|
| `CHECKOUT_PREPARE` | CLIENT、OPERATING_ENTITY | CLIENT、OPERATING_ENTITY | `INPUT_CURRENCY` |
| `PAYMENT_ATTEMPT_RISK` | CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT（= 现 `EVENT_SUBJECTS`） | 6 种（现 `LIST_SUBJECTS`） | `PAY_INPUT_CURRENCY` |

- 描述符的 `RuntimeCapabilities` 不进入点版本投影（`ExecutionReleaseContent.pointVersion`），**220001 的 content_hash 不变**，支付点版本同理。
- 全局常量 `VELOCITY_SUBJECTS`、`LIST_SUBJECTS`、`PaymentRiskFacts.EVENT_SUBJECTS` 删除，统一取描述符。
- 新增领域 record `FeatureCatalog`（由描述符投影，供创作上下文使用），与 2.1 的 Wire 结构一一对应。

### 3.3 Client、运营主体的指标与窗口（D1）

`FeatureHandlers` 中登记“低基数主体”{CLIENT, OPERATING_ENTITY} 的允许组合；其他主体为五种指标 × 四个窗口。两个风控点一致。

| 指标 | 10 分钟 | 1 小时 | 24 小时 | 7 天 | 依据（热点主体演练中位数） |
|---|---|---|---|---|---|
| 次数 | ✓ | ✓ | ✓ | — | 24 小时 28 ms；7 天 209 ms |
| 金额合计 | ✓ | ✓ | — | — | 1 小时 12 ms；24 小时 278 ms |
| 最近金额、距上次秒数 | ✓ | ✓ | ✓ | — | 改索引后 ≤ 0.2 ms；7 天保守不开 |
| 不同主体数 | — | — | — | — | 24 小时 349 ms |

- 唯一来源：`FeatureHandlers.allowedWindows(anchor, metric)`，创建校验与目录投影共用。
- 需求示例“运营主体 24 小时净额合计”本期改为 1 小时；24 小时/7 天金额合计留作后续“小时预聚合”需求。
- 支付现有变量：两个库只有卡指纹主体，不受影响；现有自动测试中“支付 Client 锚定的不同卡数”改为消费者锚定（第 8 节）。

### 3.4 事件构造（`RuntimeEvaluationFacts`）

把 `PaymentRiskFacts.addEvent/subject` 搬为通用方法：

```java
/** 成功评估的累计事件与主体；结论非放行时再写拦截事件。 */
public static VelocityFacts velocityFacts(String sourceType, long sourceId, RuntimeRiskInput input, Run run,
    VelocityEventCode assessed, VelocityEventCode rejected, List<RiskSubjectType> subjects, LongSupplier ids)
```

| 项 | 支付（不变） | 结账（新） |
|---|---|---|
| `source_type` | `TRANSACTION_RISK_ASSESSMENT` | `CHECKOUT_INPUT_SNAPSHOT`（D3） |
| `source_id` | assessmentId | snapshotId |
| `source_version` | 1 | 1（快照不可变） |
| 金额、币种 | 支付金额、支付币种 | 税前净额、订单币种 |
| `occurred_time` = `create_time` | 评估时间 | 评估时间 |
| `event_hash` | `CODE-MOR:RISK:VELOCITY_EVENT:1`，字段 eventCode、sourceType、sourceId、occurredTime、去尾零金额、币种 | 同 |
| 主体 | 描述符累计主体中本次有值的 | 同（Client、运营主体均必填，恒为 2 个） |

- 支付事件摘要与主体必须逐字不变：由支付测试断言摘要等于按旧算法计算的值。
- 结账 build 改为先分配 snapshotId，再构造事件；ID 分配顺序变化不影响任何语义。

## 4. 数据库

### 4.1 Schema DDL（[db/ddl.sql](./db/ddl.sql)，目标库 `mor_risk`）

| 序 | 语句 | 性质 |
|---|---|---|
| 1 | `t_velocity_feature_definition.event_code` 注释允许值加 `CHECKOUT_ASSESSED`、`CHECKOUT_REJECTED` | 只改元数据 |
| 2 | `t_list_feature_definition` 表注释“支付输入字段”→“风控点输入字段” | 只改元数据 |
| 3 | `idx_velocity_subject` 重建为 `(subject_type, subject_key, event_code, occurred_time, velocity_event_id)` | `INPLACE, LOCK=NONE`；mor-test 事件量很小 |

- 表数仍为 85，列不变；PDManer 模型、完整 Schema、`RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`（索引理由）同步。
- 三条均兼容旧 Risk，可在换镜像前执行；执行后用 `information_schema` 核对注释与索引列序。
- **业务 DML：0。**系统变量 `INPUT_CURRENCY` 两库已存在（v5、ACTIVE）；验收用的 Client/运营主体名单经 Risk 接口创建（阶段 5/7 单独授权）。

### 4.2 查询（`VelocityEventSubjectMapper.xml`）

| 查询 | 改动 |
|---|---|
| `sumAmount` | `JOIN` → `STRAIGHT_JOIN`，固定以主体表为驱动表（演练中宽窗口会反转为主表全表扫描） |
| `selectLast` | `JOIN` → `STRAIGHT_JOIN`；`ORDER BY s.occurred_time DESC, s.velocity_event_id DESC LIMIT 1` 由新索引直接满足 |
| `countEvents`、`countDistinct` | 不改 |

阶段 4 在本机用演练脚本（一次性容器）复测上述两条的执行计划与耗时，结果写入阶段 4 记录。

## 5. Risk 实现清单

| 类别 | 文件 | 改动 |
|---|---|---|
| 枚举 | `VelocityEventCode` | 新增 2 项与 3 个属性、`of(pointCode)` |
| 描述符 | `DecisionPointAuthoringDescriptor` | `RuntimeCapabilities` 扩展、结账开放、`velocitySubjects()/listSubjects()/featureCatalog()`；类注释更新 |
| Feature | `FeatureHandlers` | 按点校验（2.2）、D1 组合表与 `allowedWindows`、删全局主体常量 |
| Feature | `FeatureCatalog`（新 record） | 目录投影 |
| 事件 | `RuntimeEvaluationFacts`、`VelocityFacts`（内部 record） | 通用事件构造 |
| 支付 | `PaymentRiskFacts` | 改调通用方法，删 `addEvent/subject/EVENT_SUBJECTS` |
| 结账 | `CheckoutRiskFacts`、`CheckoutRiskAssessmentContext`、`CheckoutRiskAssessmentServiceImpl` | 构造并保存事件与主体（顺序同支付：轨迹之后） |
| 上下文 | `VariableAuthoringCapabilities`、`VariableAuthoringContextProcessor` | 追加 `featureCatalogs` |
| Wire | risk-api `RiskVariableModels`（`FeatureCatalog`、`VelocityEventOption`、`VelocityAnchor`、`VelocityMetricOption`）、`VariableManagementContextResponseBody`；risk-web `RiskVariableWireConverter` | 新字段 |
| Mapper | `VelocityEventSubjectMapper.xml` | 4.2 |
| 注释 | `CheckoutPrepareEvaluator`、`CheckoutRiskInput`、`VariableDefinitionValidator` | “结账不写累计事件/不计算 Feature”等表述更新 |
| 数据库 | PDManer、schema.sql、PHYSICAL_DECISIONS | 4.1 |
| 不改 | `FeatureValueReader`、`ExecutionSnapshotReader`、`ExecutionReleaseContent`、`ShadowRunHandler`、`EvaluationReader`、`EvaluationExplanations`、`CreateVariableProcessor`（调用点不变） | — |

## 6. Operations BFF 实现清单

| 文件 | 改动 |
|---|---|
| `pom.xml` | 无（已依赖 `risk-api:1.0.0-SNAPSHOT`，重新安装即可） |
| `service/model/RiskVariableModels` | `VariableManagementContextResponseBody` 加 `featureCatalogs`；新增 4 个 record |
| `integration/impl/RiskVariableConverter` | 新字段转换（保留 null 与原值） |
| 测试 `simulator/RiskVariableScenario` | 上下文 JSON 加两个点的目录 |
| 测试 `RiskVariableBrowserBoundaryAutoTest` | 断言新字段透传；Risk 多出未知键时 502（原有严格性不变） |

不改 `OperationsConsoleBffApplicationAutoTest`（其他会话有未提交改动）；若其中的上下文夹具因严格键失败，先与该会话协调再改。

## 7. Operations Web

| 文件 | 改动 |
|---|---|
| `risk/riskVariable.ts` | 删 `FEATURE_POINT`、`PAYMENT_CURRENCY_VARIABLE`、`velocityEvents`、`velocitySubjects`；`VariableContext` 加 `featureCatalogs` 并严格解码；`velocityAllowsCurrent/normalizeVelocity/velocityProblem/velocityPreview/velocityDerived/listDerived` 增加目录参数；`riskSubjects` 保留为主体标签表（名单页仍用） |
| `risk/FeatureVariableFields.tsx` | `defaultVelocity(catalog)` 工厂（首个事件、首个锚定主体、其首个指标与窗口）；事件、主体、指标、窗口、去重主体均按目录过滤；名单下拉按 `listSubjects` 过滤；文案去掉“支付” |
| `pages/RiskVariables.tsx` | 实时累计/名单：先选“适用决策点”（只列目录中的点；只有一个时自动选中），切换风控点时参数重置为该点默认；详情只读显示点名称 |
| 测试 | `velocityVariable.test.ts`（支付用例保留、新增结账用例）、`variableFixtures.ts`、`riskPageWiring.test.ts`（夹具加字段）、新增上下文解码测试（新字段、多余键拒绝） |

**规则编辑器、评估查询、名单页：不改**（已通用）。

**线框（创建 · 实时累计，结账点）：**

```text
│ 来源类型 [实时累计 ▾]   适用决策点 [结账准备 ▾]（支付尝试 / 结账准备）                          │
│ 统计参数（创建后不可修改）                                                                    │
│ 统计事件 [结账评估 ▾]（结账评估 / 结账被拦截（审核或拒绝））   统计主体 [Client ▾]（Client / 运营主体）│
│ 统计方式 (次数)(金额合计)(最近一次金额)(距上次秒数)          ← Client/运营主体无“不同主体数”       │
│   提示：Client、运营主体事件量大，只支持最长 1 天的次数与最近一次，金额合计最长 1 小时。           │
│ 时间窗口 (10 分钟)(1 小时)(1 天)                              ← 随统计方式变化；金额合计无“1 天”    │
│ [ ] 计入本次   仅“评估”事件的次数 / 金额合计可选                                              │
│ ⓘ 口径预览：同一Client 1 小时内的结账评估次数（不含本次） · 值类型：数值                         │
│ 金额类：值类型 数值 · 金额（币种取 INPUT_CURRENCY）                                            │
```

名单命中（结账点）：名单下拉只列 ACTIVE 且主体为 Client 或运营主体的名单；无可选名单时提示“请先在【风控名单】创建 Client 或运营主体名单”。

## 8. 测试与验收映射

| 系统 | 测试（真实 HTTP 入口） | 场景 | AC |
|---|---|---|---|
| Risk | `CheckoutRiskAssessmentApiAutoTest`（扩展） | ALLOW 只写 ASSESSED；REVIEW/DENY 写两条；主体恰为 Client、运营主体；`source_type/source_id`、金额为净额、币种为订单币种、摘要按约定算法；重放不增加事件 | AC-1 |
| Risk | `CheckoutRiskAssessmentAvailabilityApiAutoTest`（扩展） | TIMED_OUT、FAILED 不写事件 | AC-1 |
| Risk | `CheckoutRiskVelocityFeatureApiAutoTest`（新） | Client/运营主体 × 次数（含本次）、金额合计（只合计同币种净额）、最近金额、距上次秒数；窗口边界；拦截事件次数；Client 名单与运营主体名单命中/未命中；有支付事件时结账累计不受影响（Q4）；规则按累计值拒绝；解释中显示 Feature 取值 | AC-2、AC-6 |
| Risk | `FeatureVariableCreateApiAutoTest`（改） | 结账正常创建累计与名单变量；结账选支付事件、卡指纹主体、IP 名单被拒；支付选结账事件被拒；Client 7 天次数、Client 24 小时金额合计、Client 不同主体数被拒（两点各一）；结账金额变量绑定 `PAY_INPUT_CURRENCY` 被拒；原“支付 Client 锚定不同卡数”改为消费者锚定 | AC-3 |
| Risk | `VariableAuthoringContextResponseChecker`（改） | 不带点：两项目录且与 2.1 完全一致；带结账点：仅结账一项，VELOCITY/LIST 可创建；带支付点：仅支付一项 | AC-5 |
| Risk | `CheckoutExecutionReleaseApiAutoTest`（扩展） | 含结账累计变量的发布创建、校验、激活；改阈值的候选发布影子回放，结论按原评估时间的累计值变化 | AC-4 |
| Risk | 支付回归 `PaymentRiskAssessmentApiAutoTest`、`PaymentRiskVelocityFeatureApiAutoTest` | 事件摘要等于旧算法值、主体集合不变；现有窗口/指标断言全部通过 | AC-6 |
| Risk | 全量 auto | — | AC-6 |
| BFF | `RiskVariableBrowserBoundaryAutoTest`（扩展） | 新字段透传；严格形状 | AC-5 |
| Web | `node --test`、`npm run build` | 目录解码、按点过滤事件/主体/指标/窗口、计入本次可用性、名单过滤、默认值、支付点用例不变 | AC-5 |
| 本机（阶段 5） | Commerce → Risk；浏览器 → 变量页、执行发布、评估查询 | 结账累计与名单变量创建、发布、拦截第 N 笔、解释；Client 名单拒绝；时延抽样 | AC-1～AC-6 |

**测试基础设施：**
- `PaymentRiskTestMapper.deleteAll` 改为按支付 `source_type` 删事件（主体按 `velocity_event_id` 子查询删）、按支付变量删 Feature 定义与版本。
- `CheckoutRiskTestMapper.deleteAll` 按结账 `source_type` 删事件与主体，按 `T_CHK%` 删 Feature 定义、版本与名单；新增 `insertVelocityEvent`（结账来源）、按 `source_id` 查事件与主体。
- 支付与结账共享的名单、影子批次表仍由共用清理器处理。

## 9. 实施顺序、部署与恢复

1. **阶段 4 研发：**Risk（枚举与描述符 → FeatureHandlers → 事件构造 → 上下文与 Wire → Mapper → 测试）→ 安装 risk-api → BFF → Web；本机 `mor_risk` 执行 `db/ddl.sql`（需单独授权，先备份）；一次性容器复测查询计划。
2. **阶段 5 本机：**新 Risk + BFF + Web + Commerce；经接口建 Client 与运营主体测试名单、累计变量、规则与发布（需授权）；验收后清理。
3. **阶段 6/7 mor-test：**备份 → `ddl.sql`（旧 Risk 运行时执行）→ 依次换 Risk → BFF → Web（D2；窗口内变量页约 1 分钟不可用，其他页面不受影响）→ 回归与验收。Operations BFF/Web 在 test 上由其他会话频繁更新，部署前需确认构建基线（只含已提交内容，还是包含当时 test 镜像的其他改动），阶段 6 定。
4. **恢复：**
   - **未创建结账 Feature 变量前：**三者可各自换回旧镜像（须三者同时回退，避免严格形状不匹配）；已写入的结账事件旧代码不读取，可保留；DDL 不必回滚。
   - **已创建结账 Feature 变量后：**旧 Risk 会因结账不支持 Feature 而无法读取这些变量，不能换回旧镜像，只能向前修复。这是本次的不可逆点。
   - `ddl-rollback.sql` 只在换回旧镜像且库中没有结账事件编码的 Feature 定义时使用。

## 10. 遗留与不在本期

- 24 小时/7 天的 Client、运营主体金额合计与去重数：小时预聚合（后续需求）。
- 累计事件保留期与清理任务。
- 上一需求遗留：Commerce CCT 不可用仍返回 `SERVICE_UNAVAILABLE`；规则配置页冻结检查比后端严（均不在本期）。
- 风控标准目录结账点状态与“待部署”修正、CURRENT_IMPLEMENTATION：阶段 8 随文档提交。

## 11. 阶段 4 实施偏离

### Risk
- `FeatureCatalog.of(descriptor)` 放在 `domain.feature`（不做成描述符方法），避免 `authoring` 与 `feature` 包循环依赖；投影内容不变。
- risk-api `VelocityFeatureParameters.eventCode` 原 `@Pattern` 只允许两个支付事件，放宽为四个事件（否则结账创建请求被 422 拒绝）；按点归属仍由 `FeatureHandlers` 校验。BFF 请求模型用通用编码正则，不受影响。
- 测试：Feature 变量辅助方法上移到 `AbstractRuntimeRiskApiTest`（新增抽象 `currencyVariableCode()`）；清理按来源收窄——支付按 `TRANSACTION_RISK_ASSESSMENT` 与支付测试变量，结账按 `CHECKOUT_INPUT_SNAPSHOT` 与 `T_CHK%`；名单、影子批次表仍由支付清理器整表清理（两点共用）。

### BFF
- 无偏离；`RiskVariableResponseShape` 不改（按 risk-api 字段自动覆盖）。

### Web
- `riskPageWiring.test.ts` 不改（其第 200 行是规则创作上下文，不是变量上下文）；变量上下文夹具在 `variableFixtures.ts`。
