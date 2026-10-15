# 交接：运营后台查看评估结论与解释

- 来源：[评估结果查询与解释](../requirement.md)（Risk 侧）
- 接收方：负责 operations-console-bff / operations-console-web 的会话；Platform 资源、权限与路由登记按既有流程
- 合同：[design.md 第 4 节](../design.md#4-接口)；页面形态、权限拆分由 Operations 决定

## 1. Risk 提供的接口（`risk-api` 的 `RiskEvaluationQueryApi`，内部直连 `risk-service`）

| 用途 | 接口 |
|---|---|
| 按评估 ID 查结论 | `GET /risk-decisions/v1/evaluations/{evaluationId}` |
| 按支付尝试查正式评估 | `GET /risk-decisions/v1/evaluations?pointCode=PAYMENT_ATTEMPT_RISK&businessKey={支付尝试 ID}` |
| 评估解释 | `GET /risk-decisions/v1/evaluations/{evaluationId}/explanation` |

- 不需要 Gateway 身份头，带 Foundation 的 `X-Request-Id`；Risk 不校验调用方，BFF 先按 PLT-I02 做权限判断。
- 404 `NOT_FOUND`：评估或业务身份不存在；400 `INVALID_REQUEST`：ID、风控点、业务身份格式错误或带了未声明的 Query。

## 2. 页面需要注意

- **受限原值：**解释的 `inputs` 含 IP、User-Agent、卡指纹、消费者引用原值（用户确认可以展示）。BFF 不得在日志中记录响应体，Gateway/BFF 的访问日志对该路由只记元数据。
- **当前值标注：**`variables[].valueBasis=CURRENT` 的变量（常量、集合）是当前值，页面须标注“当前值，可能与评估时不同”。
- **路径不可用：**`pathAvailable=false` 表示评估早于本功能上线（mor-test 现有 4 条），路径为空，原因码为空；不要从规则推断路径。
- **条件渲染：**解释只给出谓词的 `nodeId` 与结果；条件结构用 `rules[].ruleSetVersionId` 调既有 `GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}`，节点名称已在 `path` 中，流程画布可用 `flowId`/`flowVersionId` 调既有流程版本详情。
- **影子：**正式评估的 `shadows` 最多 20 条，`outputChanged` 即认证要求变化；影子评估可按其 ID 再查结论与解释。
- **超时与失败：**`TIMED_OUT` 有路径与规则轨迹但没有结论；`FAILED + FEATURE_ERROR` 时 `variables` 中失败的变量 `technicalStatus=FAILED`。

## 3. 建议的入口

- 支付详情或风控工作台：输入支付尝试 ID → 结论卡片 → “查看解释” → 解释中的影子评估可再点开。
- Risk 目前没有“按影子批次列出样本”的接口；如需从执行发布详情直接浏览变化样本，另提需求。
