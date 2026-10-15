# Operations 风控评估查询与解释：方案设计

- 阶段 3：方案、接口、页面与注册数据（2026-10-15，已确认；阶段 4 实施偏离见第 8 节）
- 依据：[需求](./requirement.md)（Q1～Q5 已决）、[差异与影响](./technique.md)、[Risk 合同](../risk-评估结果查询与解释/design.md#4-接口)

**规模：**涉及 3 处（Operations BFF、Operations Web、Platform 注册数据）；新增 3 个浏览器接口（均为 GET，BFF 与 Risk 同路径，0 个修改）；不新增表、不改表结构；Platform 注册 DML 32 行（权限 1、授权 1、菜单 1、路由 3、请求头 6、Query 2、限流 18）+ 激活 4 条 UPDATE；新增 1 个页面（[ui.md](./ui.md) 1 张线框图）。

## 1. 主流程

```text
浏览器 /risk/evaluations?type=attempt&id=…
  → Gateway  GET /api/operations/risk-decisions/v1/evaluations…（路由绑定 RISK_EVALUATION_READ）
  → BFF      OperationsRequestInterceptor：PLT-I02 实时授权 → Controller（Query 白名单、ID 格式）
             → Risk RiskEvaluationQueryApi（内部直连）
             解释：再按解释中出现的规则集版本逐个调 DecisionRuleSetManagementApi 详情（同次请求内去重），组装条件树
  ← 页面模型
```

- 全部只读；BFF 无状态、不缓存。日志前缀 `/risk-decisions` 只记元数据（响应含受限原值）。
- 规则集版本详情的条件节点键就是 Risk 条件节点 ID 的十进制文本（`DecisionRuleSetWireConverter`），解释的谓词 `nodeId` 可直接对应；规则按 `ruleCode` 在该版本内对应。**不需要改 Risk。**

## 2. BFF 接口

浏览器经 Gateway 访问 `/api/operations` 前缀；BFF 与 Risk 路径相同。ID 为十进制字符串。

| 接口 | Query | 说明 |
|---|---|---|
| `GET /risk-decisions/v1/evaluations/{evaluationId}` | 无 | 结论；字段同 Risk `EvaluationResponseBody`（[Risk 设计 4.1](../risk-评估结果查询与解释/design.md#41-按评估-id-查结论)）；BFF 核对回执 `evaluationId` 与路径一致 |
| `GET /risk-decisions/v1/evaluations` | `pointCode`、`businessKey`（都必填） | 按业务身份查正式评估；核对回执的 `pointCode`、`businessKey` 与请求一致、`mode=PRIMARY` |
| `GET /risk-decisions/v1/evaluations/{evaluationId}/explanation` | 无 | 解释；在 Risk 解释基础上为每条规则补 `condition`（见下）；核对回执 `evaluationId` |

**解释中规则项（BFF 页面模型）：**

```json
{
  "ruleSetVersionId": "…", "ruleId": "…", "ruleCode": "LARGE_AMOUNT", "ruleName": "大额审核", "priorityNo": 30,
  "matched": true, "terminal": true, "action": "REVIEW_PAYMENT",
  "conditionAvailable": true,
  "condition": {
    "nodeKey": "9001", "nodeType": "GROUP", "groupOperator": "AND", "result": true,
    "children": [
      { "nodeKey": "9002", "nodeType": "PREDICATE", "comparisonOperator": "GT", "result": true,
        "operands": [
          { "operandRole": "LEFT", "sourceType": "VARIABLE", "valueType": "NUMBER", "variableCode": "PAY_INPUT_AMOUNT",
            "variableName": "支付金额" },
          { "operandRole": "RIGHT", "sourceType": "LITERAL", "valueType": "NUMBER", "literalValue": "5000",
            "numericUnit": "MONEY", "currencyVariableCode": "PAY_INPUT_CURRENCY" } ] } ] }
}
```

- 谓词 `result` 取自 Risk 谓词轨迹；分组 `result` 按 AND/OR/NOT 由子节点结果计算，任一子节点无结果则为 null；未求值的谓词 `result` 为 null。
- `variableName` 取解释的变量表，缺失时取规则集详情的 `currentVariables`，仍缺失则为空（页面显示编码）。
- 规则集详情调用失败、规则或节点对不上：该规则 `conditionAvailable=false`、`condition=null`，其余内容照常返回（风险 1）。
- 其余字段（`inputs`、`pathAvailable`、`path`、`variables`、`shadows`）原样透传。

**错误：**Risk 的 404/400 原样映射（Problem 合同）；BFF 自身的 Query 未声明、ID 格式不符返回 400；无权限由 PLT-I02 返回 403；Risk 回执不一致返回 502 `UPSTREAM_INVALID_RESPONSE`。

## 3. 页面

见 [ui.md](./ui.md)。要点：

- 路由 `/risk/evaluations`，组件键 `OPERATIONS_RISK_EVALUATIONS`，风控菜单第 8 项“评估查询”。
- 查询条：类型（支付尝试 ID / 评估 ID）+ 输入框 + 查询；查询条件写入 URL（`?type=attempt&id=…`），刷新、前进后退保持。
- 结论卡片 + 五个分区（标签页）：路径、规则与条件、变量、输入、影子评估；影子评估点击后以 `type=evaluation` 打开该评估。
- `pathAvailable=false` 时路径区显示“路径未记录（早于该功能上线）”；`CURRENT` 变量显示“当前值”标签与说明；受限输入原值直接显示（不提供复制按钮之外的处理）。

## 4. Platform 注册

- **演练：**在一次性 `mysql:8.4.11` 容器中载入 Platform 完整 Schema 与初始化数据后执行：预检全部为 0、插入 32 行；激活文件语法通过（不含 COMMIT）。容器已删除。
- 文件：[db/platform-support-service/registration-dml.sql](./db/platform-support-service/registration-dml.sql)（32 行 INSERT，路由/菜单 DISABLED、授权 REVOKED，不含 COMMIT）；[activation-local.sql](./db/platform-support-service/activation-local.sql)（本机激活）。mor-test 激活与撤销在阶段 6 编写。
- ID 段 `900000000000111001～114018`：本机 `mor_platform_e2e`、mor-test `mor_platform` 与仓库全部 SQL 中均未使用（2026-10-15 查询，执行前预检再核对）；编码 `RISK_EVALUATION_READ`、`OPERATIONS_RISK_EVALUATIONS`、`operations-risk-evaluation-*` 均未占用。

| 表 | 行 | 内容 |
|---|---|---|
| `t_permission` | 1 | `RISK_EVALUATION_READ`（`RISK_EVALUATION` / `READ` / 风险等级 HIGH：含受限原值） |
| `t_role_permission` | 1 | PLATFORM_ADMIN 授权（REVOKED，激活时开放） |
| `t_console_resource` | 1 | 菜单 `OPERATIONS_RISK_EVALUATIONS`“评估查询”，父 `OPERATIONS_RISK_MANAGEMENT`，`/risk/evaluations`，排序 80 |
| `t_gateway_route` | 3 | `operations-risk-evaluation-detail` / `-find` / `-explanation`，GET，`OPERATIONS_JWT` + `HUMAN_PERMISSION`，权限 `RISK_EVALUATION_READ`，超时 10 秒（解释需多次下游调用） |
| `t_gateway_route_header` | 6 | 每条路由 `authorization`（CORS 允许）、`origin`（转发），同既有 GET 路由 |
| `t_gateway_route_query_param` | 2 | 按业务身份查询的 `pointCode`、`businessKey` |
| `t_gateway_guard_rule` | 18 | 每条路由 6 条，与既有风控 GET 路由相同（共享 300/分、并发 50；单 IP 30/分、并发 3；单操作者 30/分、并发 2） |

## 5. 代码改动

| 系统 | 改动 |
|---|---|
| BFF | `RiskClientConfiguration` 增加 `RiskEvaluationQueryApi` Bean；`ext/rest/RiskEvaluationController`；`integration/RiskEvaluationClient`（+Impl，回执核对）；`service/RiskEvaluationExplanationAssembler`（条件树组装，纯计算）；`service/model/riskevaluation/` 页面模型；`application.yml` 增加日志前缀 `/risk-decisions` |
| BFF 测试 | `RiskEvaluationBrowserAutoTest`（真实 HTTP 入口，Risk 用测试侧 DTO Mock）；如应用级测试有路由/Bean 清单断言则追加 |
| Web | `pages/RiskEvaluations.tsx`、`risk/riskEvaluationApi.ts`、`risk/riskEvaluation.ts`（模型与展示转换）、`risk/riskEvaluation.css`；`navigation/ConsolePage.tsx` 1 行；`navigation/pageCapabilities.ts` 1 段 |
| Web 测试 | API 与转换的单元测试；页面渲染测试（结论、路径不可用、当前值标注、条件结果） |
| 本机依赖 | 从 `a55b3b1` 安装 `risk-api` 到 `~/.m3`（只新增类） |
| 文档 | 需求目录；CURRENT_IMPLEMENTATION 的 Operations 行（只改本需求） |

## 6. 测试与验收映射

| 测试 | 入口 | 场景 | AC |
|---|---|---|---|
| `RiskEvaluationBrowserAutoTest` | BFF 真实 HTTP | 按 ID / 按业务身份 / 解释的转发与回执核对；条件树组装（AND/OR/NOT、谓词结果、未求值）；规则集详情失败时 `conditionAvailable=false`；404/400 映射；回执不一致 502；Query 白名单；PLT-I02 拒绝 403；日志不含受限原值 | AC-1～AC-8 |
| Web 单元与渲染测试 | Vitest | URL 查询参数、结论颜色、路径不可用提示、CURRENT 标注、条件结果高亮、影子跳转、错误与空状态 | AC-1～AC-7、UI-1～UI-3 |
| 本机全链路（阶段 5） | 浏览器 → Nginx → Gateway → BFF → 本机 Risk | 用合成支付尝试产生评估后逐项核对；验收数据清理 | AC-1～AC-9 |
| 回归 | — | BFF `verify`、Web 全量测试与生产构建 | — |

## 7. 部署与恢复（阶段 6 细化）

- 顺序：Platform 注册 DML → 替换 BFF、Web → 激活 SQL → 等 Gateway 快照生效 → 浏览器验收。
- 回退：撤销 SQL 把路由/菜单置 DISABLED、授权 REVOKED（页面消失、接口不可达）；BFF/Web 换回旧镜像。注册行保留不删。

## 8. 阶段 4 实施偏离（2026-10-15）

| 原方案 | 实施 | 原因 |
|---|---|---|
| 解释不新增形状校验 | 在既有 `RiskOwnerResponseShapeValidator` 登记评估结论与解释两个根（只校验键集合与 JSON 类型） | 现有 Risk 成功响应读取器只读取已登记类型，不登记无法读取 |
| 编排放在 service 层 | 解释编排（解释 → 规则集详情 → 组装）在 `RiskEvaluationClientImpl`，组装器 `RiskEvaluationExplanationAssembler` 在 service 包 | 既有应用级测试禁止同时含 “Risk” 与 “Processor/Service” 的类；两次调用同属 Risk Owner。代价：integration 依赖 service 包的纯计算类 |
| 规则集详情失败的处理 | 任何失败（含上游 403/404/超时/不符合协议）只让相关规则 `conditionAvailable=false`，整页 200，记一条只含版本 ID 与异常类名的 WARN | 入站已做 PLT-I02，会话与页面权限失效不会走到这里 |
| 页面渲染测试（Vitest） | 纯函数测试 + 页面源码检查（`node --test`） | 仓库没有渲染测试设施；渲染效果在阶段 5 浏览器验收确认 |
| 结论文案 | REVIEW 显示“审核”；运算符用变量页面的中文写法；时间按 UTC 显示 | 与需求文案及既有页面一致 |
| 解释加载失败 | 仍显示结论卡片，标签区显示错误与重试 | 结论与解释是两次请求 |
| — | Web 配置测试的页面总数 28 → 29 | 新增页面；若通知管理也新增页面需再调整 |

