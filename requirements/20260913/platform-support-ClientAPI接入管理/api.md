# Client API 管控接口合同

以 [api-inventory.json](./api-inventory.json) 为逐端点清单。本期 **新增 Platform 9 + BFF 9 个端点，修改 Platform 4 + BFF 4 个既有端点**。除 A06 的 BFF 展示聚合外，新接口私网与 BFF path 相同；浏览器加 `/api/operations`。全部沿用 v1，类型名不带 V1/V2。本文为已确认阶段 3 合同；实现及验证状态见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。

## 1. 公共合同

- Platform 接受既有可信 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；Gateway 清理伪造身份头，BFF 执行 PLT-I02，Platform 在每个管理入口重新验证当前普通 Operations 会话及对应权限。BOOTSTRAP、强制改密、失效会话不能调用本期接口。
- A01/A02/A03 用 `API_CREDENTIAL_READ`；A04 用 `API_CREDENTIAL_REVOKE`；A05/A06 用 `API_SCOPE_READ`；A07/A08 用 `API_SCOPE_MANAGE`；A09 直接用既有 `GATEWAY_CONFIG_READ`。目录维护权限不授予单凭证 Scope，Scope 查看不附带凭证、路由或审计权限。撤销/维护角色若需页面入口，同时分配该页面 READ；拥有写权限不自动获得 READ。
- A09 仅服务 Route，采用 `HUMAN_PERMISSION`，不增加 usage 参数、不采用 HUMAN_AUTHENTICATED，不修改 PLT-I02 已认证允许清单或 Gateway 静态业务配置。
- 分页 `{items,page,size,total}`，page 默认 1，size 默认 10，允许 10/20/50/100、最大 100。A01、A03 按主键 ASC；A05/A09 按 scope_code ASC（编码唯一）。时间在一次查询开始时冻结 `asOf=UTC 微秒`，该次列表/总数及有效性计算使用同一一致性快照；不承诺跨页冻结。BFF 不合并不同查询的总数形成虚假全局快照。
- ID 为正十进制字符串，支持有符号 Long 上限，不允许负数、小数、前导加号或 JS Number；version 为正 INT。时间 ISO-8601 UTC 字符串，数据库 DATETIME(6)。枚举精确匹配，不自动改大小写。
- Scope 编码复用 PLT-I03 约束：1..128 位，`[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*`，不接受通配、空白或自动小写改写。编码在 JSON、Query、Path 使用同一规范，字符大小写敏感。
- 空/重复/未知 Query 拒绝；可选条件不填时省略。人类文本 trim，名称/说明的包含搜索对 `%`、`_`、反斜线按文字转义。秘密不得作为查询值；credentialId/keyPrefix 为明确字段，不能传整个 opaque Credential。
- A04/A07/A08 要求 `Idempotency-Key: [A-Za-z0-9_-]{16,128}`、JSON Body 和 `reason`（trim 后 1..500）。保存成功及同键重放都是 200；查询 200。更新必须有 `expectedVersion`，不允许调用方指定 actor、tenant、status、创建/更新时间。未知/重复 JSON 字段按既有严格解码拒绝。
- 所有响应 `Cache-Control: no-store`。BFF/日志/审计不读取或投影完整凭证、PHC；不会新建秘密查询/复制 API。

## 2. 新增端点

| ID | Method / path | 请求 | 返回 |
|---|---|---|---|
| A01 | GET /api-credential-admin/v1/credentials | page,size,clientId,clientName,credentialId,keyPrefix,status,scopeCode | PagingResponseBody&lt;ApiCredentialSummary&gt; |
| A02 | GET /api-credential-admin/v1/credentials/{credentialId} | 无 Query | ApiCredentialDetailResponseBody |
| A03 | GET /api-credential-admin/v1/credentials/{credentialId}/scopes | page,size | PagingResponseBody&lt;ApiCredentialScopeGrant&gt; |
| A04 | POST /api-credential-admin/v1/credentials/{credentialId}/revoke | `{expectedVersion,reason}` | ApiCredentialRevokeResponseBody |
| A05 | GET /api-scope-admin/v1/scopes | page,size,keyword | PagingResponseBody&lt;ApiScopeSummary&gt; |
| A06 | GET /api-scope-admin/v1/scopes/{scopeCode} | Platform 无 Query；BFF 可选 referenceKind,page,size | Platform ApiScopeDetailResponseBody；BFF ApiScopePageResponseBody |
| A07 | POST /api-scope-admin/v1/scopes | `{scopeCode,displayName,description,reason}` | ApiScopeMutationResponseBody |
| A08 | PUT /api-scope-admin/v1/scopes/{scopeCode} | `{displayName,description,expectedVersion,reason}` | ApiScopeMutationResponseBody |
| A09 | GET /api-scope-admin/v1/scope-options | page,size,keyword | PagingResponseBody&lt;ApiScopeOption&gt; |

A01 所有条件 AND：clientId 精确匹配 Tenant.owner_ref_id 且 tenant_type=CLIENT；clientName 为 1..255 的 tenant_name 包含搜索；credentialId 精确；keyPrefix 为 8..32 位 PLT-I03 既有前缀语法、精确匹配（不接受点分隔的完整凭证）；status 过滤下述展示状态；scopeCode 匹配任一授权行，不限定其 ACTIVE，不因多条明细重复凭证。空结果 200，详情 ID 不存在 404；读取不调用认证、不要求客户 Tenant 当前 ACTIVE。

A03 返回全部授权明细，包括已撤销、过期及未来生效行；既有 `resource_pattern/action_code` 仅原值只读，不新增编辑或资源表达式执行。页内名称从目录按同一 code 精确关联，未登记为 null；目录缺项不使授权消失，不降低凭证认证能力。

A05/A09 keyword 可选，1..255，匹配 displayName 或 scopeCode；A09 只返回编码和名称，不返回凭证引用。当前已保存的 Scope 编码可直接显示，不依赖把整个目录下载到 Web；编辑器使用有界服务器搜索，失联不退回硬编码菜单。

### A06 BFF 聚合

`referenceKind` 可省略或为 `NONE`、`ROUTE`、`CREDENTIAL`。NONE 不允许 page/size；其余沿用公共分页。返回：

```json
{
  "scope": {
    "apiScopeId": "900000000000099001",
    "scopeCode": "orders.read",
    "displayName": "读取订单与 Checkout",
    "description": "查询自己 Client 的订单与 Checkout",
    "version": 1,
    "createTime": "2026-09-13T10:00:00.000000Z",
    "updateTime": "2026-09-13T10:00:00.000000Z"
  },
  "references": null
}
```

ROUTE 时 references=`{kind:"ROUTE",page:{items:[GatewayRouteSummary],page,size,total}}`；CREDENTIAL 时为类型明确的 ApiCredentialSummary 页，不使用任意 Map。同一次只请求一种引用，不预取另外一种引用数量。

BFF 先查 Scope 详情确认 Scope READ 和对象存在，再调用 M01(requiredScopeCode) 或 A01(scopeCode)。Platform 下游分别检查 GATEWAY_CONFIG_READ/API_CREDENTIAL_READ；没有对应权限的显式引用请求返回 403，不返回空集合/数量 0。Web 不展示无权访问的引用标签。Scope 不存在为 404，不对未知目录编码自动建一行。

凭证 A03 的“关联 API”直接进入现有 `/gateway-configuration`，带 requiredScopeCode 查询；只要求路由 READ，避免凭证查看者被迫取得整个 Scope 管理页权限。旧未登记 Scope 也可按代码定位 Route。

## 3. 精确响应字段

| 类型 | 字段（除显式 null 均必有） |
|---|---|
| ApiCredentialSummary | credentialId:string、tenantId:string、clientId:string、clientName:string、keyPrefix:string、storedStatus:ACTIVE/REVOKED/EXPIRED、status:ACTIVE/REVOKED/EXPIRED、tenantOwnerStatus:PENDING/ACTIVE/SUSPENDED/TERMINATED、tenantSecurityStatus:ACTIVE/SUSPENDED、expiresTime:Instant、version:int、createTime:Instant、updateTime:Instant |
| ApiCredentialDetailResponseBody | Summary 全字段 + rotatedFromId:string/null；不返回“全部 Scope”无界数组，授权页由 A03 独立取得 |
| ApiCredentialScopeGrant | scopeId:string（授权明细 ID）、scopeCode:string、displayName:string/null、resourcePattern:string、actionCode:string、storedStatus:ACTIVE/REVOKED/EXPIRED、status:ACTIVE/REVOKED/EXPIRED/PENDING、effectiveFrom:Instant、effectiveTo:Instant/null、version:int、createTime:Instant、updateTime:Instant |
| ApiScopeSummary | apiScopeId:string、scopeCode:string、displayName:string、description:string/null、version:int |
| ApiScopeDetailResponseBody | Summary + createTime:Instant、updateTime:Instant |
| ApiScopeOption | scopeCode:string、displayName:string |
| ApiCredentialRevokeResponseBody | credentialId:string、status:"REVOKED"、version:int（当次结果） |
| ApiScopeMutationResponseBody | apiScopeId:string、scopeCode:string、version:int（无虚构 ACTIVE 状态） |

凭证展示状态：REVOKED 优先；否则 storedStatus=EXPIRED 或 expiresTime<=asOf 显示 EXPIRED；其他合法 ACTIVE 显示 ACTIVE。Tenant 状态单独展示，不把“凭证有效”表示为“任意业务 API 可调用”。认证继续由 PLT-I03 验证 Tenant/有效 Scope，不用此展示结果代替认证。

授权展示状态：REVOKED 优先；其次 storedStatus=EXPIRED 或 effectiveTo<=asOf 为 EXPIRED；其余 effectiveFrom>asOf 为 PENDING；否则 ACTIVE。有效区间是 `[effectiveFrom,effectiveTo)`；父凭证撤销不伪造子授权为 REVOKED。未知存量状态、反向时间区间或缺失 Tenant 的详情报依赖/数据不一致错误，不猜默认 ACTIVE。

## 4. 写入与重复请求

- A07：displayName trim 后 1..255；description 可为 null，trim 后空串规范为 null，最大 500。scopeCode 不 trim/改写。编码精确唯一，名称允许重名；创建 version=1。
- A08：Path code 固定；全量传 displayName、description（null 表示清空），CAS 成功 version+1；即使值相同，作为一次带原因的显式保存递增一次，不增加 no-op 协议。冲突 409，前端保留草稿。
- A04：先当前会话/撤销权限，再幂等重放判断，再读取 CLIENT 凭证并核对 expectedVersion。ACTIVE 或 EXPIRED 均可改为 REVOKED；旧版本返回 409。已 REVOKED 且 expectedVersion 匹配返回当前版本 200，不再递增，但可形成该新命令的既有操作记录。同 key 同内容返回最初响应，不因后续对象变化重新执行；同 key 不同内容 409。权限被收回后旧 key 也不能绕过授权取回结果。
- 不级联修改授权明细、rotated_from_id 指向的旧凭证或后继凭证；不增加恢复、删除、批量撤销、改到期时间 API。
- 复用 Platform OperationsCommandIdempotencyService；资源锁、Hash、事务及审计细节见 [design.md](./design.md)。响应引用只保存重放必需的标识/版本，无秘密。Scope code 来自原请求/Path，immutable，无需复制完整响应或另建回执表。

## 5. 既有接口变化

| ID | 既有路径 | 变化 |
|---|---|---|
| M01 | GET /gateway-admin/v1/routes（两侧相同） | 新增 requiredScopeCode 可选精确筛选，目录未登记也可查；与既有过滤 AND，total 与 items 同口径，响应不改 |
| M02 | POST /gateway-admin/v1/routes | API_SCOPE 的 requiredScopeCode 必须在新目录存在；请求/响应字段保持 |
| M03 | PUT /gateway-admin/v1/routes/{routeCode} | 新/变更的 Scope 绑定校验目录；旧 API_SCOPE 且同一字符串原值允许保留；切离 API_SCOPE 仍按旧规则清空 Scope |
| M04 | Platform GET /operations/v1/audit-events；BFF GET /audit/v1/events | 新增 resourceType（大写代码 1..64）、resourceId（1..128）可选精确筛选；resourceId 存在时 resourceType 必填；沿用现有其他条件、分页/权限、Operations 固定来源和 actionCode 前缀 |

M02/M03 的所有其他 Catalog/Route/Guard 校验保持；未知目录不导致 PLT-I20 Snapshot 读取失败。既有 status 接口保留兼容范围：同一旧 Scope 的启停不借本期新增目录门禁；人为改绑必须先登记目录。目录无删除/停用/改编码，不需在 Gateway 运行时再查目录。

M04 目录审计使用 resourceType=`API_SCOPE`、resourceId=`scopeCode`；凭证用 `API_CREDENTIAL` + 十进制 credentialId。Route 审计仍为既有 GATEWAY_ROUTE 及 routeCode，不改变旧审计键。现有对象/属性明细查询仅新增合法展示字段类型，无新 Method/Path。自动返回新菜单/页面能力属于数据扩展，不另计改接口。

## 6. 错误与 Web

沿用现有 Problem 合同：`application/problem+json`，type/title/detail/code/requestId/timestamp；X-Request-Id 与 body 相同，不新增 status 字段。结构/Query 错误 400/INVALID_REQUEST；Bean Validation 422/INVALID_REQUEST；失效会话 401/OPERATIONS_SESSION_INVALID；无权限 403/OPERATIONS_PERMISSION_DENIED；新对象不存在 404/NOT_FOUND；重名 Scope code、非法引用 400/BUSINESS_CHECK_FAILED；CAS、同键异内容、资源锁超时 409/CONCURRENT_REQUEST；依赖不可用 503/DEPENDENCY_UNAVAILABLE。旧 Gateway NOT_FOUND 保持其原 code，未知异常按现行全局映射保留 500。

字段错误定位 displayName/description/reason/expectedVersion/requiredScopeCode；不返回 SQL、PHC 或实际凭证。BFF 不把全部 4xx 改成 502/503。400/422/409 保留草稿；503 可重试，同一次未改内容的写入复用 key。成功后刷新详情，不向用户承诺追回已认证的在途调用。
