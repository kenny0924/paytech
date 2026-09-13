# API 合同（阶段 3 已确认）

新增端点逐条机器清单为 [api-inventory.json](./api-inventory.json)，权限、约束和事务见 [design.md](./design.md)。共 **18 个 Platform + 18 个 BFF 新端点**。清单 path 同时是 Platform 私网 HTTP 和 BFF 下游 path；浏览器地址在前面加 `/api/operations`，Gateway 注册 Route 指向既有 operations-console-bff Target。默认 v1，不新建带 V1/V2 名称的类型。

## 1. 新增接口

下表的路径可与清单互查；查询参数未列出者不接受额外 Query。

| ID | Method / path | 输入及结果 | 权限 |
|---|---|---|---|
| A01 | POST /gateway-admin/v1/target-services | CreateGatewayTargetRequestBody → ConfigurationMutationResponseBody | GATEWAY_CONFIG_MANAGE |
| A02 | PUT /gateway-admin/v1/target-services/{targetServiceCode} | UpdateGatewayTargetRequestBody → mutation | 同上 |
| A03 | POST /gateway-admin/v1/routes | CreateGatewayRouteRequestBody → mutation | 同上 |
| A04 | PUT /gateway-admin/v1/routes/{routeCode} | UpdateGatewayRouteRequestBody → mutation | 同上 |
| A05 | GET /console-admin/v1/resources | 无 Query；完整 Operations 平面树 → ConsoleResourcesResponseBody | CONSOLE_RESOURCE_READ |
| A06 | GET /console-admin/v1/resources/{resourceCode} | → ConsoleResourceResponseBody | 同上 |
| A07 | POST /console-admin/v1/resources | CreateConsoleResourceRequestBody → mutation | CONSOLE_RESOURCE_MANAGE |
| A08 | PUT /console-admin/v1/resources/{resourceCode} | UpdateConsoleResourceRequestBody → mutation | 同上 |
| A09 | PATCH /console-admin/v1/resources/{resourceCode}/status | ChangeConsoleResourceStatusRequestBody → mutation | 同上 |
| A10 | GET /permission-admin/v1/permissions | page,size,code,resourceType,status,riskLevel → PagingResponseBody<PermissionSummary> | PERMISSION_READ |
| A11 | GET /permission-admin/v1/permissions/{permissionCode} | → PermissionResponseBody | 同上 |
| A12 | POST /permission-admin/v1/permissions | CreatePermissionRequestBody → mutation | PERMISSION_MANAGE |
| A13 | PUT /permission-admin/v1/permissions/{permissionCode} | UpdatePermissionRequestBody → mutation | 同上 |
| A14 | PATCH /permission-admin/v1/permissions/{permissionCode}/status | ChangePermissionStatusRequestBody → mutation | 同上 |
| A15 | GET /permission-admin/v1/permissions/{permissionCode}/references | kind,page,size → PagingResponseBody<PermissionReference> | PERMISSION_READ |
| A16 | GET /gateway-admin/v1/capabilities | → GatewayConfigurationCapabilitiesResponseBody | GATEWAY_CONFIG_READ |
| A17 | GET /console-admin/v1/resource-capabilities | → ConsoleResourceCapabilitiesResponseBody | CONSOLE_RESOURCE_READ |
| A18 | GET /permission-admin/v1/permission-options | usage,page,size,keyword → PagingResponseBody<PermissionOption> | 按 usage 校验，见详细设计 |

GET 200；POST/PUT/PATCH 保存成功统一 200，POST 与重放均返回同一轻量结果，不依赖 Location。后续详情另行 GET。A05 的总量上限 2000，完整树仅筛选 OPERATIONS；Web 筛选名称/编码/类型/状态时保留祖先上下文，不能把不完整页当树。

分页复用 Foundation PagingRequestBody/PagingResponseBody 当前 envelope `{items,page,size,total}`，page 从 1 开始，默认 size=10、最大 100，非法分页返回 400。code 精确匹配，resourceType/status/riskLevel 精确匹配；keyword 是长度 1..128 的编码/类型/动作包含查询，对 `%/_` 做文字转义、不把它当 SQL 通配。按 code + ID 稳定排序。Reference kind 必填且仅 RESOURCE/ROUTE/ROLE，权限不存在用统一业务错误。

## 2. 请求字段

所有写请求有 `reason:string(1..500)`；更新/状态有 `expectedVersion:integer>0`。只 trim 明确的人类文本/代码，路径/Header 等按现行合同规范化，不改变路径大小写。ID 为十进制字符串，UTC 时间为 ISO-8601，受上限限制的计数/容量使用 JSON 数字，与已有详情一致。未知字段和与当前模式无关的非空字段拒绝，数组必传，空数组表示完整清空该可选集合。

| 请求 | 字段 |
|---|---|
| CreateGatewayTargetRequestBody | targetServiceCode `[a-z][a-z0-9-]{2,127}`、channel 六类枚举、discoveryServiceName 1..255 且符合当前 Discovery 语法、reason |
| UpdateGatewayTargetRequestBody | discoveryServiceName、expectedVersion、reason；无 Code/Channel/status |
| CreateGatewayRouteRequestBody | routeCode、channel、下述 RouteConfiguration 全部字段、reason |
| UpdateGatewayRouteRequestBody | RouteConfiguration、expectedVersion、reason；Code 来自 Path，Channel 从原记录读取 |
| CreateConsoleResourceRequestBody | resourceCode `[A-Z][A-Z0-9_]{0,127}`、resourceType MENU/BUTTON、displayName 1..255、parentResourceId 可空、componentKey 可空、permissionId、sortOrder、reason |
| UpdateConsoleResourceRequestBody | displayName、parentResourceId、componentKey、permissionId、sortOrder、expectedVersion、reason；frontendRoute 由服务端从清单派生，不由浏览器提交 |
| ChangeConsoleResourceStatusRequestBody | status ACTIVE/DISABLED、expectedVersion、reason |
| CreatePermissionRequestBody | permissionCode 同大写 Code 规则、resourceType/actionCode `[A-Z][A-Z0-9_]{0,63}`、riskLevel LOW/MEDIUM/HIGH/CRITICAL、reason |
| UpdatePermissionRequestBody | riskLevel、expectedVersion、reason |
| ChangePermissionStatusRequestBody | status ACTIVE/INACTIVE、expectedVersion、reason |

RouteConfiguration 为：`httpMethod,externalPathTemplate,downstreamPathTemplate,targetServiceCode,securityProfileCode,authorizationMode,permissionId,requiredScopeCode,requestCookieMode,responseCookieMode,bodyLimitBytes,bodyReceiveTimeoutMillis,responseTimeoutMillis,headers,queryParameterNames,guardRules`。与已有 GatewayRouteResponseBody 使用同名属性，无嵌套 configuration 包装，不复制 createdBy 等审计输出。

- 两个 path 最大 1000、Target Code 最大 128、Scope 最大 128；其完整语法复用现有编译器。
- headers 项为 `{headerName,headerUse}`，headerUse 是 CORS_REQUEST_ALLOWED/FORWARD_REQUEST；不存 Header 值，不给用户填 Token。每类用途同名唯一，每种用途最多 64 项，合计最多 128 项，与现有编译器一致。
- queryParameterNames 是完整字符串列表，最多 32 项，同名拒绝；不是 Query 的值。
- guardRules 项为 `{ruleType,dimension,capacity,refillTokens,refillPeriodMillis,concurrencyLimit}`；不适用字段显式 null，最多 8 项。Rate 与并发的形状、必需对和上限见 A16。保存完整配置不能只传屏幕当前展开的部分。
- 这三个集合的数量限制复用现有编译器，不改变存量可读能力；非法存量数据明确报错并反馈，不能默默截断。

创建 Target 示例：

```json
{"targetServiceCode":"example-client-api","channel":"CLIENT_API","discoveryServiceName":"paytech-example-client-api","reason":"登记已部署的 Client API 服务"}
```

创建资源示例（父级/权限 ID 由 A05/A18 取得，此处 ID 仅示意）：

```json
{"resourceCode":"EXAMPLE_RULE_MENU","resourceType":"MENU","displayName":"规则配置","parentResourceId":"900000000000000565","componentKey":"OPERATIONS_RISK_RULE_CONFIGURATION","permissionId":"900000000000000536","sortOrder":60,"reason":"增加已部署规则页面入口"}
```

同一个组件允许多个菜单入口；App 使用 componentKey 授权时聚合匹配，不能因另一条不可见实例否定已授权实例。按钮身份仍全局唯一，主菜单实例承载按钮父级，需手动配置可见父链。

Mutation 结果例：

```json
{"objectType":"GATEWAY_TARGET","objectId":"900000000000099001","objectCode":"example-client-api","status":"DISABLED","version":1}
```

## 3. 查询响应

- A05 `{items:[ConsoleResourceSummary]}`：resourceId/resourceCode/channel/resourceType/parentResourceId/displayName/frontendRoute/componentKey/permissionId/permissionCode/sortOrder/status/version/managementProtection。A06 增加 createTime/updateTime。所有停用记录可读；权限状态另给 permissionStatus。
- A10/A11：permissionId/permissionCode/resourceType/actionCode/riskLevel/status/version；详情追加 createTime/updateTime、managementProtection、referenceCounts{resource,route,role}。Role 数量按未撤销映射算，角色是否有效另外返回。
- A15 Resource：resourceId/resourceCode/displayName/channel/resourceType/status；Route：routeCode/channel/httpMethod/externalPathTemplate/targetServiceCode/status；Role：roleId/roleCode/roleName/roleStatus/grantStatus/scopeKey。不同 kind 返回对应有类型的 variant，禁止任意 Map。只给配置引用，不含员工和凭据。查询只给当前关联；Role kind 默认未撤销映射。
- A16 `{channels:[{channel,profiles:[{securityProfileCode,authorizationModes,requestCookieModes,responseCookieModes,allowedGuardDimensions,requiredGuardDimensions}]}],httpMethods,limits,forbiddenHeaderNames,headerUses}`；limits 包含编译器的容量、超时、集合数和配置总量上限。实际实现从公共校验常量导出，不能写两份上限。
- A17 `{components:[{componentKey,displayName,frontendRoute,actions:[{resourceCode,displayName}]}],resourceTypes,maxDepth,maxResources}`。动作只列真实 Web handler 支持项；停用/冻结等是否适用于当前业务对象仍由该页面判断，目录不能绕过业务状态。
- A18 `usage=GATEWAY_ROUTE|CONSOLE_RESOURCE`；结果每项 permissionId/permissionCode/resourceType/actionCode/riskLevel，不返回 role grant。该 selector 支持全局 ACTIVE Permission，不因为尚未有人持有而消失。
- managementProtection 为 `{editableFields:[string],statusChangeAllowed:boolean,reason:string|null}`。创建页面不使用该对象，详情及 Gateway 列表由服务端返回；按钮仍须结合当前操作者权限。

## 4. 既有端点改动清单

只调整以下既有路径，全部沿用原 v1。共 Platform **15**、BFF **14**；同一端点多项变化只计一次。自动出现新数据而无合同/授权语义变化的导航查询不计作改接口，但列入回归。

| 既有能力 | Platform 端点数 | BFF 端点数 | 改动 |
|---|---:|---:|---|
| GET Target 列表、GET Target 详情 | 2 | 2 | 追加 managementProtection |
| GET Route 列表、GET Route 详情 | 2 | 2 | 追加 managementProtection；详情仍返回完整子集合 |
| PATCH Target/Route status | 2 | 2 | 增加内置管理对象服务端保护；原请求/响应不改 |
| GET role-permissions、GET role-resources | 2 | 1 | 平台管理员可分配目录和停用配置引用；BFF 原 `/iam/v1/role-permission-options` 聚合两项 |
| GET role 详情、POST role、PUT role、DELETE role | 4 | 4 | 统一首次授权资格、原有集合及 INACTIVE 关联处理，保持系统角色不可编辑 |
| POST account、PUT account roles、POST account enable | 3 | 3 | 同一委派资格用于角色分配及权限恢复 |

Role/Account 的 Platform 前缀为 `/operations/v1`，BFF 前缀 `/iam/v1`。新字段为追加型，旧字段和分页 envelope 保持。已有 enable/details 路由及 Query/Header 无变更，不需要重复注册这 29 个端点。

## 5. 幂等、错误及浏览器合同

写操作要求 `Idempotency-Key: [A-Za-z0-9_-]{16,128}`，同一提交失败重试复用，用户修改草稿后生成新 key。Body JSON、Origin、可信 actor/session 上下文和 Cache-Control:no-store 沿用现有边界。Gateway 负责净化可信头；BFF 不接受浏览器提供的操作人/租户事实，Platform 重新鉴权。

400/INVALID_REQUEST：JSON 结构错误、未知字段、重复字段、缺失必需数值字段、未知/重复/空 Query，以及领域输入格式或组合校验失败。Bean Validation 的字段约束按现行 Foundation 合同返回 422/INVALID_REQUEST；`errors[].field` 使用 `headers[1].headerName`、`guardRules[2].capacity` 等可定位路径，BFF 仅透传合法字段路径并使用通用中文文案。非法业务引用或受保护操作使用下述 400/BUSINESS_CHECK_FAILED。

未登录/会话失效为 401/OPERATIONS_SESSION_INVALID，权限不足为 403/OPERATIONS_PERMISSION_DENIED；Gateway Target/Route 不存在继续 404/GATEWAY_CONFIGURATION_NOT_FOUND，新增 Resource/Permission 详情不存在采用公共 404/NOT_FOUND。业务引用不合法、受保护对象、重复业务编码使用 BizCheckFailException，为 400/BUSINESS_CHECK_FAILED。409/CONCURRENT_REQUEST：Version CAS、并发锁超时/数据库并发、同幂等键不同内容；503/DEPENDENCY_UNAVAILABLE：依赖不可用。异常类型 HTTP 映射复用[现行 Problem 合同](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)，未知异常保持 500，不能都改成“服务不可用”。

所有错误使用 application/problem+json，必有 type/title/detail/code/requestId/timestamp，X-Request-Id 与 body 一致，不重复 status。Web 保留草稿和错误字段，409 不自动覆盖新版本；保存成功后的提示仅为“Platform 当前配置已保存”，不宣称 Gateway 全实例已加载。
