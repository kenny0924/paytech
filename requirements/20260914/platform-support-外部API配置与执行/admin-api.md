# 外部 API 配置管理接口与注册数据

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户对第 9 节 R1～R7 回复“按建议”。代码未开始；注册 SQL 未执行，执行与激活需单独授权。
- 依据：[页面方案](./page-design.md)（已确认）、[六表物理设计](./physical-design.md)、[流程结构](./flow-definition-contract.md)、[原子 API 结构](./integration-api-definition-contract.md)；写法沿用[菜单权限与 API 配置管理](../../20260913/platform-support-菜单权限与API配置管理/api.md)与[租户访问](../../20260914/platform-support-租户访问与成员权限查询/db/dml.sql)的现行做法，请求幂等按 [2026-09-17 决定](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)由 Gateway 负责。
- 机器清单：[admin-api-inventory.json](./admin-api-inventory.json)；注册 SQL：[db/platform-support-service/registration-dml.sql](./db/platform-support-service/registration-dml.sql)。

## 1. 调用链与通用约定

```text
浏览器 /api/operations/external-api-admin/v1/...
  → Gateway（OPERATIONS_JWT + HUMAN_PERMISSION，按路由权限拦截；处理 Idempotency-Key）
    → operations-console-bff  /external-api-admin/v1/...（简单转发，不聚合）
      → platform-support-service /platform-support/external-api-admin/v1/...（重新鉴权、校验、保存）
```

| 项 | 约定 |
|---|---|
| 路径 | Platform 与 BFF 使用同一能力路径 `/external-api-admin/v1/...`；浏览器地址前加 `/api/operations` |
| 契约类型 | Platform 轻量 `*-api` 模块中的 `ExternalApiAdminQueryApi`、`ExternalApiAdminCommandApi`（`@HttpExchange`）；请求 `*RequestBody`，响应 `*ResponseBody` |
| 操作者 | 沿用现有 Operations 管理接口的可信 Header `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；Platform 重新校验权限 |
| 请求幂等 | BFF、Platform 不接收、不转发 `Idempotency-Key`；前端照常发送给 Gateway。业务防重依赖编码唯一键和版本 CAS |
| 写请求 | 都带 `reason`（1～500 字符）；更新与删除带 `expectedVersion`（>0）。删除使用 `DELETE` + 请求体 |
| 写结果 | 统一返回 `ExternalApiMutationResponseBody {objectType, objectCode, version}`；删除返回删除前的版本 |
| 分页 | Foundation `PagingRequestBody` / `PagingResponseBody {items,page,size,total}`，默认 10，最大 100；`keyword` 为编码或名称包含查询（1～64 字符，`%`、`_` 按文字处理） |
| ID / 时间 | 编码作对外身份，不暴露内部 Snowflake ID；时间为 UTC ISO-8601 |
| 并发 | 写入使用 Foundation `AbstractLockProcessor` + TRY_LOCK，锁资源 `lock:external-api-config`（全模块一把锁，配置写入量低）；数据库版本 CAS 继续保护 |
| 事务 | 每次写入一个短本地事务，只读写 Platform 自有表，不调用外部服务 |

## 2. 端点清单（30 个 Platform + 30 个 BFF）

权限：查询用 `EXTERNAL_API_READ`，写入与校验用 `EXTERNAL_API_MANAGE`。

### 2.1 标准 API

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E01 | GET `/standard-apis` | `page,size,keyword` → 分页 `StandardApiSummary` |
| E02 | GET `/standard-apis/{standardApiCode}` | → `StandardApiResponseBody` |
| E03 | POST `/standard-apis` | `CreateStandardApiRequestBody` → mutation |
| E04 | PUT `/standard-apis/{standardApiCode}` | `UpdateStandardApiRequestBody` → mutation |
| E05 | DELETE `/standard-apis/{standardApiCode}` | `DeleteExternalApiConfigRequestBody` → mutation |

### 2.2 接入方

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E06 | GET `/integrations` | `page,size,keyword` → 分页 `IntegrationSummary` |
| E07 | GET `/integrations/{integrationCode}` | → `IntegrationResponseBody` |
| E08 | POST `/integrations` | `CreateIntegrationRequestBody` → mutation |
| E09 | PUT `/integrations/{integrationCode}` | `UpdateIntegrationRequestBody` → mutation |
| E10 | DELETE `/integrations/{integrationCode}` | 删除请求 → mutation |

### 2.3 原子 API

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E11 | GET `/integrations/{integrationCode}/apis` | `page,size,keyword` → 分页 `IntegrationApiSummary` |
| E12 | GET `/integrations/{integrationCode}/apis/{apiCode}` | → `IntegrationApiResponseBody` |
| E13 | POST `/integrations/{integrationCode}/apis` | `CreateIntegrationApiRequestBody` → mutation |
| E14 | PUT `/integrations/{integrationCode}/apis/{apiCode}` | `UpdateIntegrationApiRequestBody` → mutation |
| E15 | DELETE `/integrations/{integrationCode}/apis/{apiCode}` | 删除请求 → mutation |
| E16 | POST `/integrations/{integrationCode}/apis/validate` | `ValidateIntegrationApiRequestBody` → `ValidationResponseBody` |

### 2.4 公共脚本

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E17 | GET `/integrations/{integrationCode}/scripts` | `page,size,keyword,executionStage` → 分页 `IntegrationScriptSummary` |
| E18 | GET `/integrations/{integrationCode}/scripts/{scriptCode}` | → `IntegrationScriptResponseBody` |
| E19 | POST `/integrations/{integrationCode}/scripts` | `CreateIntegrationScriptRequestBody` → mutation |
| E20 | PUT `/integrations/{integrationCode}/scripts/{scriptCode}` | `UpdateIntegrationScriptRequestBody` → mutation |
| E21 | DELETE `/integrations/{integrationCode}/scripts/{scriptCode}` | 删除请求 → mutation |
| E22 | POST `/integrations/{integrationCode}/scripts/validate` | `ValidateIntegrationScriptRequestBody` → `ValidationResponseBody` |

### 2.5 渠道标准 API 实现

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E23 | GET `/integrations/{integrationCode}/implementations` | 无 Query → `{items:[ImplementationSummary]}`（数量受标准 API 总数限制，不分页） |
| E24 | GET `/integrations/{integrationCode}/implementations/{standardApiCode}` | → `ImplementationResponseBody` |
| E25 | POST `/integrations/{integrationCode}/implementations` | `CreateImplementationRequestBody` → mutation |
| E26 | PUT `/integrations/{integrationCode}/implementations/{standardApiCode}` | `UpdateImplementationRequestBody` → mutation |
| E27 | DELETE `/integrations/{integrationCode}/implementations/{standardApiCode}` | 删除请求 → mutation |
| E28 | POST `/integrations/{integrationCode}/implementations/validate` | `ValidateImplementationRequestBody` → `ValidationResponseBody` |

### 2.6 编辑器目录

| ID | Method / Path | 请求 → 响应 |
|---|---|---|
| E29 | GET `/integrations/{integrationCode}/flow-editor-context` | `standardApiCode` → `FlowEditorContextResponseBody` |
| E30 | GET `/editor-capabilities` | 无 Query → `EditorCapabilitiesResponseBody` |

## 3. 请求体

| 请求 | 字段 |
|---|---|
| `CreateStandardApiRequestBody` | `standardApiCode`、`standardApiName`、`description`（可空）、`requestFields[]`、`responseFields[]`、`reason` |
| `UpdateStandardApiRequestBody` | `standardApiName`、`description`、`requestFields[]`、`responseFields[]`、`expectedVersion`、`reason`；编码来自路径，不可改 |
| `CreateIntegrationRequestBody` | `integrationCode`、`integrationName`、`description`、`endpoints[]`、`reason` |
| `UpdateIntegrationRequestBody` | `integrationName`、`description`、`endpoints[]`、`expectedVersion`、`reason` |
| `CreateIntegrationApiRequestBody` | `apiCode`、`apiName`、`description`、`requestFields[]`、`responseFields[]`、`definition`、`reason` |
| `UpdateIntegrationApiRequestBody` | `apiName`、`description`、`requestFields[]`、`responseFields[]`、`definition`、`expectedVersion`、`reason` |
| `CreateIntegrationScriptRequestBody` | `scriptCode`、`scriptName`、`executionStage`、`parameterDefinition[]`、`sourceCode`、`description`、`reason` |
| `UpdateIntegrationScriptRequestBody` | `scriptName`、`executionStage`、`parameterDefinition[]`、`sourceCode`、`description`、`expectedVersion`、`reason` |
| `CreateImplementationRequestBody` | `standardApiCode`、`flowDefinition`、`reason` |
| `UpdateImplementationRequestBody` | `flowDefinition`、`expectedVersion`、`reason` |
| `DeleteExternalApiConfigRequestBody` | `expectedVersion`、`reason` |
| `Validate*RequestBody` | 与对应 Create 相同的配置字段，不含 `reason`；可带 `expectedVersion` 用于提示“基于旧版本” |

- 字段项 `ApiFieldBody`：`fieldLocation`、`fieldPath`、`fieldName`、`dataType`、`required`、`description`。数组顺序即 `sort_no`，方向由所在数组决定（`requestFields` / `responseFields`），不由浏览器传 `direction` 或 `api_id`。
- 出站地址项：`{endpointCode, baseUrl}`。
- `definition`、`flowDefinition` 是对象，结构分别见[原子 API 结构](./integration-api-definition-contract.md)与[流程结构](./flow-definition-contract.md)；服务端规范化后以 JSON 保存，序列化不超过 65,536 字节。
- 未知字段、重复字段拒绝；数组必传，空数组表示清空。

创建原子 API 示例（节选）：

```json
{
  "apiCode": "CREATE_ORDER",
  "apiName": "创建订单",
  "description": null,
  "requestFields": [
    {"fieldLocation":"PATH","fieldPath":"outletRef","fieldName":"门店引用","dataType":"STRING","required":true,"description":null},
    {"fieldLocation":"HEADER","fieldPath":"Content-Type","fieldName":"内容类型","dataType":"STRING","required":true,"description":null}
  ],
  "responseFields": [
    {"fieldLocation":"BODY","fieldPath":"reference","fieldName":"渠道订单号","dataType":"STRING","required":true,"description":null}
  ],
  "definition": {"params":[],"fixedValues":{},"transport":{"protocol":"HTTP","method":"POST","endpointCode":"NI_GATEWAY","pathTemplate":"/outlets/{outletRef}/orders","connectTimeoutMs":2000,"responseTimeoutMs":10000},"requestEncoding":"JSON","responseDecoding":"JSON","scripts":[],"statusRules":[],"outputs":{"SUCCESS":[],"FAIL":[]}},
  "reason": "接入 NI 下单接口"
}
```

## 4. 响应体

| 响应 | 主要字段 |
|---|---|
| `StandardApiSummary` | `standardApiCode`、`standardApiName`、`requestFieldCount`、`responseFieldCount`、`implementedBy[]`（接入方编码与名称）、`version`、`updateTime` |
| `StandardApiResponseBody` | 基本信息、`requestFields[]`、`responseFields[]`、`implementations[]`、`version`、`createTime`、`updateTime`、`updatedBy` |
| `IntegrationSummary` | 编码、名称、`endpointCount`、`apiCount`、`scriptCount`、`implementedStandardApis[]`、`version`、`updateTime` |
| `IntegrationResponseBody` | 基本信息、`endpoints[{endpointCode, baseUrl, referencedApiCodes[]}]`、三个计数、`version`、时间、`updatedBy` |
| `IntegrationApiSummary` | 编码、名称、`method`、`endpointCode`、`pathTemplate`、`requestScriptCount`、`responseScriptCount`、`referencedBy[]`（标准 API 编码）、`version`、`updateTime` |
| `IntegrationApiResponseBody` | 基本信息、`requestFields[]`、`responseFields[]`、`definition`、`referencedBy[{standardApiCode, nodeIds[]}]`、`version`、时间、`updatedBy` |
| `IntegrationScriptSummary` | 编码、名称、`executionStage`、`parameterNames[]`、`referencedApiCodes[]`、`version`、`updateTime` |
| `IntegrationScriptResponseBody` | 基本信息、`parameterDefinition[]`、`sourceCode`、`referencedApiCodes[]`、`version`、时间、`updatedBy` |
| `ImplementationSummary` | `standardApiCode`、`standardApiName`、`nodeCount`、`returnCount`、`initializerIds[]`、`timeoutMs`、`version`、`updateTime` |
| `ImplementationResponseBody` | `standardApiCode`、`flowDefinition`、`version`、时间、`updatedBy` |
| `FlowEditorContextResponseBody` | `standardApi {requestFields, responseFields}`；`apis[{apiCode, apiName, requestFields(含 fixed 标记), params[], outputs{SUCCESS[], FAIL[]}}]`：流程编辑器一次取得本接入方全部原子 API 的契约摘要 |
| `EditorCapabilitiesResponseBody` | 枚举（方向、位置、类型、Method、编解码、阶段）、`limits`（第 [物理设计](./physical-design.md) P7 与流程预算）、`functions[{functionCode, kind: INITIALIZER/PURE/SCRIPT, params[], returns, stages[]}]` |
| `ValidationResponseBody` | `valid`、`issues[{severity: ERROR/WARNING, code, location, message}]`；`location` 为请求体路径，如 `definition.statusRules[0].condition`、`flowDefinition.edges[3]`、`requestFields[2].fieldPath` |

`referencedBy` 等引用信息用于页面的删除保护弹窗；删除被拒时前端重新读取详情展示引用方。

## 5. 错误

沿用[全系统 Problem 合同](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)：

| HTTP / code | 场景 |
|---|---|
| 400 `INVALID_REQUEST` | JSON 结构错误、未知字段、非法 Query 或路径编码 |
| 422 `VALIDATION_ERROR` | Bean Validation 字段约束（长度、格式、必填）；**保存时静态校验失败**也用此码，`errors[{field,message}]` 给出与 `ValidationResponseBody.location` 相同的路径（2026-10-13 实现时修订：Foundation 只有 `RequestBodyValidationException` 能携带字段错误，原稿“400 + errors[]”无法表达） |
| 400 `BUSINESS_CHECK_FAILED` | 编码重复、被引用不能删除、引用不存在（如节点引用的原子 API 已删） |
| 401 / 403 | 会话失效 / 无权限 |
| 404 `NOT_FOUND` | 标准 API、接入方、原子 API、脚本或实现不存在 |
| 409 `CONCURRENT_REQUEST` | 版本不一致或锁等待超时 |

## 6. 注册数据

全部写入 Platform 库，初始为停用；激活另行处理。精确语句见 [registration-dml.sql](./db/platform-support-service/registration-dml.sql)。

| 表 | 行数 | 内容 |
|---|---:|---|
| `t_permission` | 2 | `EXTERNAL_API_READ`（LOW）、`EXTERNAL_API_MANAGE`（HIGH），资源类型 `EXTERNAL_API`，ACTIVE |
| `t_role_permission` | 2 | `PLATFORM_ADMIN` 的上述两个权限，REVOKED |
| `t_console_resource` | 5 | 一级分组“外部 API”；菜单“标准 API”`/external-api/standard-apis`、“接入方”`/external-api/integrations`；两个“维护配置”按钮；全部 DISABLED |
| `t_gateway_route` | 30 | E01～E30 各一条，指向现有 `operations-console-bff` Target，DISABLED |
| `t_gateway_route_header` | 111 | 每条 `authorization` CORS + `origin` 转发（60）；18 个带请求体的写或校验接口加 `content-type` CORS + 转发（36）；15 个写接口另加 `idempotency-key` 仅 CORS、不转发（15）；校验接口不需要请求幂等 |
| `t_gateway_route_query_param` | 14 | E01、E06、E11 各 `page,size,keyword`；E17 再加 `executionStage`；E29 `standardApiCode` |
| `t_gateway_guard_rule` | 180 | 每条路由三维度各一条速率、一条并发规则，数值沿用现有管理接口 |
| **合计** | **344** | 只 INSERT，无 UPDATE/DELETE，无新 Target、无表结构变更 |

- 路由：GET 请求体上限 0；写和校验 262,144 字节（覆盖 64 KiB 定义 + 字段）；接收超时 5000 ms，响应超时 10000 ms。
- 菜单排序：“外部 API”分组 sort_order 40（平台配置 20、租户访问 25、风控管理 30 之后）。
- ID 段 `900000000000071001`～`900000000000074180`，经检查仓库内所有 SQL 未占用；执行前仍需在目标库核对。
- 激活时需另写 UPDATE：30 条路由、5 个资源、2 条角色授权改为启用，并递增 `PLATFORM_ADMIN` 版本。
- 同时需要随代码修改 Platform `console-resource-capabilities.json`，登记两个组件 `OPERATIONS_EXTERNAL_STANDARD_APIS`、`OPERATIONS_EXTERNAL_INTEGRATIONS` 及其动作，否则菜单无法通过组件能力校验；该文件属于研发阶段。

## 7. 与页面的对应

| 页面 | 使用接口 |
|---|---|
| 标准 API 列表 / 编辑 | E01～E05 |
| 接入方列表 / 基本信息 | E06～E10 |
| 原子 API 页签与编辑 | E11～E16、E30 |
| 公共脚本页签与编辑 | E17～E22、E30 |
| 标准 API 实现页签 / 流程编辑 | E23～E29、E30；“新建实现”的可选标准 API 由 E01 结果减去 E23 已实现项 |
| 删除保护、并发冲突 | 各 DELETE / PUT 的 400、409，引用信息来自详情 |

## 8. 不在本稿

- Payment → Platform 的标准 API 执行接口（已在[内部接口](./internal-api.md)确认，不经 BFF 与 Gateway）。
- 配置变更的操作历史查询；审计沿用 Platform 现有管理写入方式，不新增查询页面。
- 试运行、导入导出（页面方案 U4、U5 已确认本期不做）。

## 9. 评审点与确认记录

2026-10-13 用户回复“按照你的来”，下列建议全部确认。

| 编号 | 问题 | 结论 |
|---|---|---|
| R1 | 菜单层级 | “外部 API”作为与“风控管理”并列的一级分组，下挂两个菜单。与第一版画板一致；页面方案原写的“平台配置 / 外部 API”三层导航在现有菜单里没有先例，改为两层 |
| R2 | 权限粒度 | 整个模块一对 READ / MANAGE（已在页面方案 U6 确认），校验接口归 MANAGE |
| R3 | 删除方式 | 物理删除 + `DELETE` 请求体带 `expectedVersion`、`reason`（无状态列，P2 已确认）；被引用时拒绝 |
| R4 | 并发控制 | 全模块一把 Redis 锁 + 版本 CAS；配置写入量低，避免跨对象引用校验时的竞争 |
| R5 | 流程编辑器数据 | 新增 E29 一次返回本接入方全部原子 API 的契约摘要，避免前端逐个读取详情 |
| R6 | 校验接口 | 只给原子 API、脚本、流程三个复杂编辑器提供独立校验（E16、E22、E28）；标准 API 与接入方的校验在保存时完成 |
| R7 | 注册数据 | 344 行，全部初始停用；`idempotency-key` 只允许 CORS、不转发给 BFF |
