# 交接：运营后台配置支付风控

- 来源：支付反欺诈一期（Risk 侧已交付并部署 mor-test，2026-10-15）
- 接收方：负责 operations-console-bff / operations-console-web 的会话；Platform 资源、权限与路由登记按既有流程
- 依据：[一期设计](../design.md)、[修订设计](../design-amendment-unified-evaluation.md)。页面形态、权限拆分与交互由 Operations 决定；本文只说明 Risk 已提供的能力与合同

## 1. 现状

| 项 | 当前 | 影响 |
|---|---|---|
| Web | 规则配置页写死 `CHECKOUT_PREPARE` | 无法配置支付规则 |
| BFF 依赖的 risk-api | 构建自提交 `34ecac9`：流程节点没有 `outputs` 字段 | 需升级到 `06025a2` 及以后的 `risk-api`；保存“放行（需要 3DS）”需要该字段 |
| 已接入的 Risk 接口 | 规则集、决策流、变量、旧候选发布、字典 | 名单维护、执行发布尚未接入 |
| mor-test | Risk 已支持支付点，但没有任何支付规则与生效发布 | 支付评估一律 503，直到运营在后台配置并激活 |

## 2. 需要的能力

### 2.1 风控点切换

规则集、决策流、变量的列表和创作都按 `pointCode` 区分，可选 `CHECKOUT_PREPARE`、`PAYMENT_ATTEMPT_RISK`。各点可用的输入字段、运算符、终态动作以创作上下文为准：
- 规则集：`GET /decision-rule-sets/v1/authoring-context/{pointCode}`
- 决策流：`GET /decision-flows/v1/authoring-context`

支付点的终态动作只有 `ALLOW_PAYMENT`、`REVIEW_PAYMENT`、`DENY_PAYMENT`，不开放冷却、打标签等附加动作。

### 2.2 变量：新增两种来源（`/decision-variables/v1/variables`）

| 来源 | 参数 | 说明 |
|---|---|---|
| `VELOCITY` 实时累计 | `eventCode`（如 `PAYMENT_ATTEMPT_ASSESSED`、`PAYMENT_ATTEMPT_REJECTED`）、`anchorSubjectType`、`metric`（COUNT / SUM_AMOUNT / DISTINCT_COUNT / LAST_AMOUNT / SECONDS_SINCE_LAST）、`distinctSubjectType`（仅 DISTINCT_COUNT）、`windowSeconds`（600 / 3600 / 86400 / 604800）、`includeCurrent` | 金额类需 `numericUnit=MONEY` 且 `currencyVariableCode=PAY_INPUT_CURRENCY` |
| `LIST` 名单命中 | `listCode`、`subjectType` | 布尔值 |

两类变量创建后参数不可改；值类型、主体与事件编码以《[风控标准目录](../../../../agent-docs/RISK_CONTROL_STANDARDS.md)》第 2～4 节为准。

### 2.3 名单维护（`/risk-lists/v1`，新接入）

| 操作 | 接口 |
|---|---|
| 创建名单 | `POST /lists`（listCode、listName、subjectType、purpose：BLOCK / ALLOW） |
| 名单列表 | `GET /lists?page&size` |
| 加入成员 | `POST /lists/{listCode}/entries`（subjectValue、reason，可选 effectiveFrom / effectiveTo） |
| 成员列表 | `GET /lists/{listCode}/entries?subjectValue&status&page&size` |
| 失效成员 | `POST /lists/{listCode}/entries/{entryId}/deactivate`（reason） |

主体值（IP、卡指纹、消费者引用等）属于受限值。响应中的主体键已经脱敏，页面和 BFF 日志都不得展示或记录原值。名单用途只用于展示，命中后怎么处置由规则决定。

### 2.4 决策流：结束节点附加输出

- 放行结束节点可以带 `outputs`，例如 `{"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}`；可选值为 `NOT_REQUIRED`、`THREE_DS_REQUIRED`。
- 只有放行结束节点能配置。保存时，未配置的输出会补成默认值 `NOT_REQUIRED`，所以流程详情里每个放行结束节点都会带 `outputs`。
- 校验错误码为 `INVALID_OUTPUT` 或 `INVALID_NODE_CONFIG`，字段路径形如 `graph.nodes[i].outputs.CODE`。
- **Risk 侧前置（待做）：** 创作上下文目前不返回附加输出的声明（编码、可选值、默认值），页面只能写死上面两个值。Risk 补上之前，可以先写死这两个值；Risk 补上后改为从上下文读取。

### 2.5 执行发布（`/decision-releases/v1/executions`，新接入）

| 操作 | 接口 | 规则 |
|---|---|---|
| 创建 | `POST /executions`（pointCode、flowVersionId） | DRAFT |
| 详情 | `GET /executions/{releaseId}` | 含八项静态检查与影子批次汇总 |
| 校验 | `POST /executions/{releaseId}/validate` | 通过后 SHADOWING，失败 REJECTED |
| 影子运行 | `POST /executions/{releaseId}/shadow-runs`（sampleTarget） | 只在已有生效发布时需要，回放最近的正式评估；异步完成，轮询详情 |
| 激活 | `POST /executions/{releaseId}/activate` | 首个发布校验后可直接激活；已有生效发布时须先完成影子运行，且技术门槛通过；原发布变为 SUPERSEDED |

影子汇总字段：`sampleCount`、`allowToReviewCount`、`allowToDenyCount`、`denyToAllowCount`、`actionChangeCount`（认证要求变化）、`failedCount`、`timedOutCount`、`featureMissingCount`、`p95Millis`、`p99Millis`、`checkStatus`。建议在激活确认时醒目展示“拒绝转放行”和“认证要求变化”。

### 2.6 需要处理的错误码

- `RISK_RULE_SET_IN_USE`（409）：规则集被生效中或影子中的执行发布引用，不能删除。
- 激活前置不满足：400 `BUSINESS_CHECK_FAILED`。
- 其余沿用既有 Problem 合同。规则集“发布引用”列表目前只显示旧候选发布，执行发布的引用要看执行发布详情。

## 3. 建议的上线顺序

1. 升级 BFF 的 `risk-api`，并让 Web 支持风控点切换（规则集、流程、变量），这是只读能力的回归。
2. 接入名单维护与两种新变量。
3. 接入执行发布与结束节点输出。
4. 在 mor-test 配置一套支付规则并激活，为 Payment 联调提供可用环境。
