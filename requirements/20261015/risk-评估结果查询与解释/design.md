# 评估结果查询与解释：方案设计

- 阶段 3：方案、接口与数据设计（2026-10-15，已确认；阶段 4 实施偏离见第 9 节）
- 依据：[需求](./requirement.md)（Q1～Q4 已决）、[差异与影响](./technique.md)（D1～D3 按建议）

**规模：**只涉及 Risk 1 个系统；新增 3 个接口（均为 GET，0 个修改）；新增 1 张表 11 个字段、1 个唯一索引；不修改旧表；业务 DML、注册 DML 均为 0；无页面设计（Operations 另做）。

## 1. 主流程

```text
评估（写）                                     查询（读）
POST /evaluations                               GET /evaluations/{id}                → 结论
  引擎执行 → 新增：收集流程路径                 GET /evaluations?pointCode&businessKey → 结论
  短事务写入：评估、快照、Feature、规则轨迹、     GET /evaluations/{id}/explanation     → 解释
            累计事件 + 新增流程轨迹               只读；BFF 再按需取规则集版本、流程版本详情渲染条件
影子回放（写）
  新增：写规则轨迹、流程轨迹
```

- **查询全部只读**，不开事务以外的写；读取时不调用外部服务。
- 查询按评估所属风控点分派给该点的评估器（复用统一评估的分派方式）；本期只有支付评估器实现。
- 原因码、原因文案、附加输出都**从到达的结束节点读取**（它们已冻结在流程版本中），不新增结论字段。支付正式评估的认证要求仍以 `t_transaction_risk_assessment` 中已保存的值为准，两者一致。

## 2. 运行期补记（D2、D3、Q3）

| 场景 | 现状 | 改为 |
|---|---|---|
| 成功 | 写 Feature 结果、规则轨迹 | 另写流程轨迹 |
| 超时（TIMED_OUT） | 引擎已算完，结果被丢弃 | 写 Feature 结果、规则轨迹、流程轨迹；评估仍为 TIMED_OUT、无结论、不写累计事件 |
| Feature 计算失败（FAILED） | 什么都不写 | 写已完成的 Feature 结果，以及失败的那一个（`technical_status=FAILED`、`failure_code=FEATURE_ERROR`）；评估 `failure_code` 改为 `FEATURE_ERROR` |
| 读取发布内容或引擎执行失败（FAILED） | 什么都不写 | 写已完成的 Feature 结果；`failure_code` 保持 `EVALUATION_ERROR`；不写部分轨迹 |
| 影子样本 | 只写评估与 Feature 结果 | 另写规则轨迹与流程轨迹；失败、超时样本同上 |

- 规则轨迹、流程轨迹都在原有的评估写入短事务内批量插入，不新增事务或外部调用。
- 现有表已经支持 FAILED 的 Feature 行（`technical_status`、`failure_code`），不改表。

## 3. 新表 `t_evaluation_flow_trace`

一次评估实际经过的流程节点，每节点一行，按顺序；最后一行是结束节点。节点与连线用流程版本内的键记录，流程版本由评估的发布得出。

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| `trace_id` | BIGINT | 否 | 主键，Snowflake |
| `evaluation_id` | BIGINT | 否 | 所属评估 |
| `step_no` | INT | 否 | 从 1 开始的经过顺序；开始节点为 1 |
| `node_key` | VARCHAR(64) ascii_bin | 否 | 流程版本内的节点键 |
| `node_type` | VARCHAR(64) | 否 | `START`、`RULE_SET`、`END` |
| `rule_set_version_id` | BIGINT | 是 | 规则集节点引用的规则集版本；其他节点为空 |
| `matched_flag` | BOOLEAN | 是 | 规则集节点是否有规则命中；其他节点为空 |
| `node_decision` | VARCHAR(64) | 是 | 规则集节点首个终态命中的决定（ALLOW/REVIEW/DENY），没有终态命中为空；结束节点为该节点的结论 |
| `reused_flag` | BOOLEAN | 是 | 规则集节点是否复用了同一规则集版本此前的结果（引擎对同一版本只执行一次）；其他节点为空 |
| `exit_edge_key` | VARCHAR(64) ascii_bin | 是 | 离开本节点所走的连线键；结束节点为空 |
| `create_time` | DATETIME(6) | 否 | UTC 微秒写入时间 |

- 主键 `trace_id`；唯一索引 `uk_eval_flow_trace_step (evaluation_id, step_no)`，同时服务按评估读取。
- 不加外键或 CHECK（项目规则）；只追加不修改，因此不设 `update_time`。
- 旧评估没有记录；查询返回 `pathAvailable=false`，不从规则轨迹推断。
- 执行文件：`db/ddl.sql`（只有一条 `CREATE TABLE`）；回滚 `DROP TABLE`，应用回退后表可保留。

## 4. 接口

三个接口在新接口类 `RiskEvaluationQueryApi`（同为 `/risk-decisions/v1`，见第 9 节），内部直连；Operations BFF 经 Platform 鉴权后调用。路径前缀已配置为 Web 日志只记元数据。ID 统一为十进制字符串，时间为 UTC ISO-8601。

### 4.1 按评估 ID 查结论

`GET /risk-decisions/v1/evaluations/{evaluationId}` → `EvaluationResponseBody`

```json
{
  "evaluationId": "236600000000000001",
  "pointCode": "PAYMENT_ATTEMPT_RISK",
  "businessKey": "235900000000000123",
  "mode": "PRIMARY",
  "result": "ALLOW",
  "reasonCode": null,
  "reasonMessage": null,
  "outputs": { "AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED" },
  "failureCode": null,
  "releaseId": "236518950932942848",
  "environment": "TEST",
  "flowId": "236518950786142208",
  "flowCode": "PAY_MAIN",
  "flowVersionId": "236518950786142209",
  "flowVersionNo": 3,
  "evaluationTime": "2026-10-15T16:01:52.383274Z",
  "elapsedMillis": 18,
  "baselineEvaluationId": null,
  "shadowRunId": null,
  "pathAvailable": true
}
```

| 字段 | 说明 |
|---|---|
| `mode` | `PRIMARY` / `SHADOW` |
| `result` | 与评估接口相同：ALLOW / REVIEW / DENY / FAILED / TIMED_OUT |
| `reasonCode`、`reasonMessage` | 到达结束节点的原因；放行或没有路径时为空 |
| `outputs` | 放行时为结束节点的附加输出；其他结论为空对象。支付正式评估取已保存的认证要求 |
| `failureCode` | FAILED / TIMED_OUT 时的失败码（`EVALUATION_ERROR`、`FEATURE_ERROR`、`EVALUATION_TIMEOUT`） |
| `businessKey` | 影子评估取其基线评估的业务身份 |
| `baselineEvaluationId`、`shadowRunId` | 仅影子评估有值 |
| `pathAvailable` | 是否有流程轨迹（本需求上线前的评估为 false） |

### 4.2 按业务身份查正式评估

`GET /risk-decisions/v1/evaluations?pointCode={pointCode}&businessKey={businessKey}` → 同 4.1

- 两个参数都必填；只返回该业务身份的**正式**评估（每个业务身份最多一条）。
- 业务身份格式按该点输入合同校验（支付：正整数）。

### 4.3 评估解释

`GET /risk-decisions/v1/evaluations/{evaluationId}/explanation` → `EvaluationExplanationResponseBody`

```json
{
  "evaluationId": "236600000000000001",
  "inputs": {
    "paymentIntentId": "...", "riskActionId": "...", "orderId": "...", "clientId": "...", "entityNo": "AE001",
    "paymentMethodCode": "CARD", "captureMode": "AUTOMATIC", "amount": "600.00", "currency": "USD",
    "industryCode": "GAMING", "mccCode": "5816", "consumerCountryCode": "AE", "consumerReference": "player-1",
    "clientIpAddress": "203.0.113.7", "userAgent": "Mozilla/5.0 ...", "cardFingerprint": "fp_...", "cardBin": "411111"
  },
  "pathAvailable": true,
  "path": [
    { "stepNo": 1, "nodeKey": "start", "nodeType": "START", "nodeName": "开始", "exitEdgeKey": "e1", "exitEdgeName": null },
    { "stepNo": 2, "nodeKey": "n_block", "nodeType": "RULE_SET", "nodeName": "拦截规则",
      "ruleSetVersionId": "...", "ruleSetCode": "PAY_BLOCK", "ruleSetVersionNo": 2,
      "matched": false, "decision": null, "reused": false, "exitEdgeKey": "e2", "exitEdgeName": "其他情况" },
    { "stepNo": 3, "nodeKey": "end_3ds", "nodeType": "END", "nodeName": "放行（需要 3DS）", "decision": "ALLOW" }
  ],
  "rules": [
    { "ruleSetVersionId": "...", "ruleId": "...", "ruleCode": "LARGE_AMOUNT", "ruleName": "Large amount",
      "priorityNo": 10, "matched": false, "terminal": false, "action": "REVIEW_PAYMENT",
      "predicates": [ { "nodeId": "...", "result": false } ] }
  ],
  "variables": [
    { "variableCode": "PAY_INPUT_AMOUNT", "variableName": "支付金额", "sourceType": "INPUT", "valueType": "NUMBER",
      "value": "600.00", "valueBasis": "EVALUATION" },
    { "variableCode": "CARD_DECLINES_1H", "sourceType": "VELOCITY", "valueType": "NUMBER", "value": "3",
      "valueBasis": "EVALUATION", "technicalStatus": "SUCCEEDED", "elapsedMillis": 2 },
    { "variableCode": "LARGE_AMOUNT_LIMIT", "sourceType": "CONSTANT", "valueType": "NUMBER", "value": "1000",
      "valueBasis": "CURRENT" }
  ],
  "shadows": [
    { "evaluationId": "...", "shadowRunId": "...", "result": "REVIEW", "decisionChanged": true,
      "outputChanged": false, "severity": "MEDIUM" }
  ]
}
```

| 部分 | 内容与规则 |
|---|---|
| `inputs` | 该点输入快照的全部字段原值（Q1）；未提供的字段为 null。影子评估取基线评估的快照 |
| `path` | 流程轨迹按 `stepNo`；节点名称、规则集编码与版本号、连线名称取自评估所用的冻结流程版本。`pathAvailable=false` 时为空数组 |
| `rules` | 实际执行过的规则，按执行顺序；编码、名称、优先级、动作取自冻结规则集版本；`predicates` 为实际求值的谓词结果。条件的完整结构由 BFF 用 `ruleSetVersionId` 调既有 `GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` 取得并渲染（BFF 聚合职责），本接口不重复返回 |
| `variables` | 发布引用的全部变量（不含节点输出）。`valueBasis=EVALUATION`：输入来自快照、实时累计与名单命中来自 Feature 结果；`valueBasis=CURRENT`：常量为当前值、集合为当前成员（D1），页面须标注“当前值，可能与评估时不同”。Feature 失败时该变量 `technicalStatus=FAILED`、`failureCode` 有值；未计算到的 Feature 不出现 |
| `shadows` | 仅正式评估：以其为基线的影子评估（按创建时间倒序，最多 20 条）；`outputChanged` 即认证要求是否变化 |

### 4.4 错误

| 情况 | 返回 |
|---|---|
| 评估 ID 或业务身份不存在 | 404 `NOT_FOUND`（沿用公共 Problem） |
| 评估 ID 非正整数、`pointCode` 未登记、`businessKey` 格式不符、缺少参数 | 400 `INVALID_REQUEST` |
| 未声明的 Query 参数、重复或空参数、GET 带 Body | 400 `INVALID_REQUEST`（新增 `RiskDecisionRequestConfiguration` 白名单：只有 4.2 接受 `pointCode`、`businessKey`） |
| 评估存在但引用的发布、流程或规则集版本缺失（数据损坏） | 500 |

## 5. 代码改动

| 模块 | 改动 |
|---|---|
| risk-api | `RiskDecisionApi` 增加 `getEvaluation`、`findEvaluation`、`getExplanation`；`response` 包新增 `EvaluationResponseBody`、`EvaluationExplanationResponseBody` 及嵌套类（普通 class，中文 JavaDoc） |
| risk-decision · 运行期 | `DecisionEngine` 收集 `FlowStep` 并放入 `EngineOutcome`；`FeatureValueReader` 单个 Feature 失败时返回已完成结果与失败项（不再整体抛出）；`PaymentRiskFacts` 按第 2 节写 Feature、规则轨迹、流程轨迹；`ShadowRunHandler` 写规则轨迹与流程轨迹 |
| risk-decision · 持久化 | `EvaluationFlowTrace` DataObject、`EvaluationFlowTraceMapper`（+XML，批量插入、按评估读取）、`EvaluationFlowTraceRepository`；`EvaluationTraceQueryRepository`（按评估读规则轨迹、Feature 结果，按基线读影子评估） |
| risk-decision · 查询 | `DecisionPointEvaluator` 增加“业务身份 → 正式评估 ID”和“评估 → 输入快照字段”；`EvaluationReader` 组装结论；`EvaluationExplanationReader` 组装解释（复用 `ExecutionSnapshotReader` 读冻结发布内容）；`GetEvaluationProcessor`、`FindEvaluationProcessor`、`GetEvaluationExplanationProcessor`（只读） |
| risk-web | `RiskDecisionController` 增加 3 个 GET；`RiskDecisionRequestConfiguration`（Query 白名单与 GET 无 Body） |
| 模型与 SQL | PDManer `risk-compliance.pdma.json` 增加表；`risk-compliance-schema.sql` 同步；需求级 `db/ddl.sql` |
| 文档 | Risk 数据库设计文档补表；风控标准目录 1.4 节两接口改为已确认；`requirements.ts` 中 RCO-I06/I10 描述；交接单 `handoff/operations.md` |

## 6. 测试与验收映射

| 测试 | 入口 | 场景 | AC |
|---|---|---|---|
| 新增 `EvaluationQueryApiAutoTest` | 真实 HTTP | 放行（含 3DS 输出）、审核（原因码与文案）、拒绝；按业务身份查询与按 ID 一致；404；400（ID 格式、未登记点、业务身份格式、缺参数、未知 Query） | AC-1、2、8 |
| 同上 | 真实 HTTP | 分支流程：路径节点顺序、所走连线、结束节点；同一规则集版本被两个节点引用时第二个节点 `reused=true`；规则与谓词结果与库内轨迹一致；变量取值与 `valueBasis`（常量改值后显示新值并标 CURRENT） | AC-3、4、5 |
| 同上 | 真实 HTTP | 输入原值完整返回；本次请求日志不含 IP、User-Agent、卡指纹、消费者引用 | AC-6 |
| 同上 | 真实 HTTP | 旧评估（Mocker 只写评估、不写流程轨迹）`pathAvailable=false` | AC-3 |
| 扩展现有超时测试 | 真实 HTTP | TIMED_OUT 评估有 Feature、规则轨迹与路径，无结论 | AC-1、5 |
| 新增 Feature 失败场景 | 真实 HTTP | 用 Mocker 使某个 Feature 在计算时出错：评估 FAILED + `FEATURE_ERROR`，解释中该变量 FAILED、此前的变量有值（具体触发方式阶段 4 确定，不加生产测试分支） | AC-5 |
| 扩展影子运行测试 | 真实 HTTP + 补偿处理 | 影子评估有规则轨迹与路径；正式评估解释列出影子并标出变化；影子评估可单独查询结论与解释 | AC-7 |
| 回归 | — | Risk 全量自动测试 | — |

阶段 5 另做：本机打包 jar 走真实 HTTP 验收，对比改动前后评估 P95。

## 7. 部署与恢复（阶段 6 细化）

- 顺序：备份 → 执行 `db/ddl.sql`（新增 1 表）→ 替换 Risk 镜像。无 DML。
- 回退：应用回退到上一镜像即可；新表不被旧代码读写，可保留或 `DROP`。
- Operations 页面上线依赖 Risk 先部署；交接单列出接口、`valueBasis` 标注要求、BFF 不记响应体、条件渲染需调用的两个既有详情接口。

## 8. 设计中的取舍

| 取舍 | 原因 |
|---|---|
| 条件结构不在解释接口里重复返回，由 BFF 调既有规则集版本详情 | BFF 负责页面聚合；避免两处维护同一份条件表示 |
| 节点用键而不是节点 ID 记录 | 引擎和冻结快照都按键工作，写入时无需再查 ID；键在流程版本内唯一 |
| 只保留最近 20 条影子评估 | 一个正式评估通常只被少数影子批次引用；需要更多时按批次查影子运行详情 |
| 不新增结论字段（原因码、输出） | 可由结束节点精确得出，避免重复存储与不一致 |

## 9. 阶段 4 实施偏离（2026-10-15）

| 原方案 | 实施 | 原因 |
|---|---|---|
| 查询方法加在 `RiskDecisionApi` | 新建 `RiskEvaluationQueryApi`，路径不变；`RiskEvaluationQueryController` 实现 | Payment 的测试桩 `MockRiskController implements RiskDecisionApi`，加方法会让 Payment 升级 `risk-api` 后测试编译失败 |
| 新建 `EvaluationTraceQueryRepository` | 不新建；复用各表已有的 `findByEvaluation`，`DecisionEvaluationRepository` 增加 `findByBaseline` | 一表一 Repository，已有读取方法足够 |
| `EvaluationReader` 与 `EvaluationExplanationReader` 两个读取器 | 一个 `EvaluationReader` + 纯组装 `EvaluationExplanations` | 两个查询共用评估、发布、风控点、快照与路径的加载 |
| 404 错误码 | `FailException(CommonErrorCode.NOT_FOUND)`，`RiskApiErrorConfiguration` 映射为 404 | 公共错误码此前在 Risk 中没有 404 映射 |
| Feature 失败的测试触发方式待定 | 名单状态改为未登记值，名单命中 Feature 读取名单时类型映射失败 | 真实的数据损坏路径，不加生产测试分支 |
| — | 超时评估的流程轨迹最后一行是所到结束节点（含其结论），评估本身仍无结论 | 解释“算到了哪里”；结论以评估的 `result` 为准 |

