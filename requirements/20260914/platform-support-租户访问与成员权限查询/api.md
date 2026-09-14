# 租户访问 API 合同（阶段 3 已确认）

## 1. 路径、权限与传输

精确清单见 [api-inventory.json](./api-inventory.json)。下表 Path 同时为 BFF Path 和 Platform `@HttpExchange` Path；浏览器加 `/api/operations`，Platform 加既有 servlet 前缀 `/platform-support`。均默认 v1，Java/TS 类型不含版本号。

| ID | Method | Path | 当前操作者权限 | 响应 |
|---|---|---|---|---|
| A01 | GET | /tenant-access-admin/v1/tenants | TENANT_ACCESS_READ | Page<Tenant> |
| A02 | GET | /tenant-access-admin/v1/tenants/{tenantId} | TENANT_ACCESS_READ | Tenant |
| A03 | POST | /tenant-access-admin/v1/tenants/{tenantId}/security-status | TENANT_ACCESS_MANAGE | SecurityStatusResult |
| A04 | GET | /tenant-access-admin/v1/tenants/{tenantId}/members | TENANT_MEMBER_READ | Page<Member> |
| A05 | GET | /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId} | TENANT_MEMBER_READ | Member |
| A06 | GET | /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles | TENANT_MEMBER_READ | EvaluatedPage<RoleGrant> |
| A07 | GET | /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions | TENANT_MEMBER_READ | EvaluatedPage<MemberPermission> |
| A08 | GET | /tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources | TENANT_MEMBER_READ | EvaluatedPage<PermissionSource> |

- 八项成功都返回 HTTP 200 JSON；写接口必须 `Content-Type: application/json` 及 `Idempotency-Key`。浏览器 Bearer token 按既有 Gateway Profile 处理，可信 Operations Context 使用原有 X-Gateway-Actor-Subject 与 X-Gateway-Session-Id 内部合同；禁止外部 Tenant/Member Header 替换操作者。
- BFF 每次先走现有 PLT-I02；Owner 仍验证当前 Operations 普通会话及对应权限。三个权限无隐式继承。页面的租户 Header 需 READ，成员子功能需额外 MEMBER_READ，管理按钮需额外 MANAGE；直接 A03/A04–08 不要求 READ 叠加，但仍严格检查目标范围。
- 所有 64 位 ID 为正十进制字符串，值 <= signed BIGINT 最大值；版本为 1..2147483647 的 JSON integer。时间为 UTC ISO-8601 字符串，最高微秒；页面按用户时区展示。禁止以 JS number 传输 ID。
- 所有读响应 `Cache-Control: no-store`；不会返回原始邮箱、Vault Token、密码/PHC、来源 Hash 或 Credential Secret。错误及日志遵循现有 RFC 9457 合同，不记录请求中的认证秘密。
- A02–08 的 Tenant 必须是 CLIENT/PARTNER；不存在或 INTERNAL 返回 404。A05–08 的 memberId 不属于路径 Tenant 同样 404；先验证操作者再解析目标，不泄漏跨租户存在性。

## 2. 查询与字段

分页沿用现有 `{items,page,size,total}`：page 默认 1、1..100000；size 默认 10，可选 10、20、50、100；超出最后一页返回空 items 及真实 total。page/size 必须整数，沿用 Foundation 公共分页校验，offset 使用 long；不静默截断。未知/重复 Query 参数拒绝 400，参数为空字符串按未填写处理；标识/枚举非法 400。WHERE 使用绑定参数。

| API | 允许 Query | 搜索语义 |
|---|---|---|
| A01 | page,size,tenantType,tenantId,ownerRefId,tenantName,ownerStatus,securityStatus | ID 精确；type 仅 CLIENT/PARTNER；名称 trim 后最长 255，按现有 DB collation 包含匹配，转义 LIKE `% _`，空白无过滤 |
| A04 | page,size,accountId,emailMasked,memberType,memberStatus | accountId 精确；emailMasked trim 后最长 255，仅对已保存脱敏显示值包含匹配；类型 OWNER/ADMIN/MEMBER；状态 ACTIVE/SUSPENDED/REVOKED |
| A06/A07/A08 | page,size | 指定 Tenant+Member，A08 另指定 Permission；无隐式状态过滤 |
| A02/A03/A05 | 无 | A03 使用 JSON Body |

列表排序：Tenant tenantId ASC；Member memberId ASC；RoleGrant grantId ASC；MemberPermission permissionId ASC；PermissionSource grantId ASC,rolePermissionId ASC。同一次分页的 COUNT/内容/生效状态在一个 RR 只读快照内完成；不同请求会随当前事实变化，不提供跨页历史快照令牌。

以下除标注 nullable 外均必返非 null；字符串长度继承数据字段上限。数组无结果为 []，布尔值真实 boolean。Page 中 total 为现有统一分页的非负 JSON integer，超出安全整数时服务报错，不能精度截断。EvaluatedPage 在 Page 上增加 `evaluatedAt`（固定计算时刻），不是持久化字段。

| 模型 | 字段及类型 |
|---|---|
| Tenant | tenantId:string、tenantType:CLIENT/PARTNER、ownerRefId:string、tenantName:string(<=255)、ownerStatus:PENDING/ACTIVE/SUSPENDED/TERMINATED、securityStatus:ACTIVE/SUSPENDED、version:int、tenantAccessAllowed:boolean、restrictionReasons:Reason[]、createdAt/updatedAt:Instant |
| Member | tenantId/memberId/accountId:string、emailMasked:string(<=255,nullable)、memberType:OWNER/ADMIN/MEMBER、memberStatus:ACTIVE/SUSPENDED/REVOKED、accountStatus:PENDING_VERIFICATION/ACTIVE/DISABLED/CLOSED、createdAt/updatedAt:Instant（成员时间） |
| RoleGrant | grantId/roleId:string、roleCode:string(<=64)、roleName:string(<=255)、roleScope:PLATFORM/TENANT、roleTenantId:string(nullable，仅 PLATFORM 为 null)、roleStatus:ACTIVE/INACTIVE、grantStatus:ACTIVE/REVOKED/EXPIRED、effectiveFrom:Instant、effectiveTo:Instant(nullable=长期)、effective:boolean、reasons:Reason[] |
| MemberPermission | permissionId:string、permissionCode:string(<=128)、resourceType/actionCode:string(<=64)、permissionStatus:ACTIVE/INACTIVE、effective:boolean、sourceCount/effectiveSourceCount:非负安全整数、reasons:Reason[] |
| PermissionSource | 完整 RoleGrant 字段 + rolePermissionId:string、rolePermissionStatus:ACTIVE/REVOKED；effective/reasons 按完整来源重新计算，包含 Permission 状态，不能只用 RoleGrant 的中间结果 |
| Reason | code:string（固定枚举见数据设计）、message:string（安全、可读说明）；数组按数据设计顺序去重，不含数据库内部异常 |
| SecurityStatusResult | tenantId:string、securityStatus:ACTIVE/SUSPENDED、version:int |

A07 只列该成员已有授权链涉及的权限，包含未生效来源；不铺满从未获得的全局目录。A08 的 Permission 不存在或在该成员下没有任何来源返回 404；合法存在但翻到空页返回空 items。A06 无角色、A07 无权限为 200 空列表。

Tenant 示例（A01 的 items 与 A02 同模型）：

```json
{"tenantId":"81001","tenantType":"CLIENT","ownerRefId":"31001","tenantName":"Atlas Cloud Store","ownerStatus":"ACTIVE","securityStatus":"SUSPENDED","version":4,"tenantAccessAllowed":false,"restrictionReasons":[{"code":"TENANT_SECURITY_SUSPENDED","message":"租户已安全暂停"}],"createdAt":"2026-09-10T09:00:00Z","updatedAt":"2026-09-14T06:00:00Z"}
```

Member 示例（A04 的 items 与 A05 同模型）：

```json
{"tenantId":"81001","memberId":"92001","accountId":"91001","emailMasked":"a***@atlas.example","memberType":"OWNER","memberStatus":"ACTIVE","accountStatus":"ACTIVE","createdAt":"2026-09-10T09:00:00Z","updatedAt":"2026-09-10T09:00:00Z"}
```

A06 示例：

```json
{"items":[{"grantId":"93001","roleId":"94001","roleCode":"CLIENT_OWNER","roleName":"客户所有者","roleScope":"TENANT","roleTenantId":"81001","roleStatus":"ACTIVE","grantStatus":"ACTIVE","effectiveFrom":"2026-09-10T09:00:00Z","effectiveTo":null,"effective":true,"reasons":[]}],"page":1,"size":20,"total":1,"evaluatedAt":"2026-09-14T06:00:00Z"}
```

A07 示例：

```json
{"items":[{"permissionId":"95001","permissionCode":"ORDER_READ","resourceType":"ORDER","actionCode":"READ","permissionStatus":"ACTIVE","effective":true,"sourceCount":2,"effectiveSourceCount":1,"reasons":[]}],"page":1,"size":20,"total":1,"evaluatedAt":"2026-09-14T06:00:00Z"}
```

A08 示例（RoleGrant 两条来源可有不同状态）：

```json
{"items":[{"grantId":"93002","roleId":"94002","roleCode":"ORDER_READER","roleName":"订单只读","roleScope":"PLATFORM","roleTenantId":null,"roleStatus":"ACTIVE","grantStatus":"ACTIVE","effectiveFrom":"2026-09-01T00:00:00Z","effectiveTo":"2026-09-12T00:00:00Z","rolePermissionId":"96002","rolePermissionStatus":"ACTIVE","effective":false,"reasons":[{"code":"GRANT_EXPIRED","message":"授权已过期"}]}],"page":1,"size":10,"total":1,"evaluatedAt":"2026-09-14T06:00:00Z"}
```

示例仅解释结构，非已有业务目录或数据库种子，不预设真实权限名、角色名。

## 3. A03：安全状态变更

`Idempotency-Key` 必填，`[A-Za-z0-9_-]{16,128}`，按既有命令机制绑定当前 Operations 操作者和操作。Body 三项均必填非 null，不接受额外可编辑字段：

```json
{"securityStatus":"SUSPENDED","expectedVersion":3,"reason":"发现异常访问，暂停后进行人工排查。"}
```

securityStatus 仅 ACTIVE/SUSPENDED；expectedVersion 为正 int；reason trim 后 1..500 字符，使用非敏感说明。成功只返回稳定写结果，例如 `{"tenantId":"81001","securityStatus":"SUSPENDED","version":4}`；不把重放时变化的 Owner 状态混入原结果。

| 场景 | 合同行为 |
|---|---|
| 版本匹配且状态不同 | CAS 改安全状态、version+1、update_time；完成幂等结果，提交后尽力审计 |
| 版本匹配且已是目标状态 | 200 当前三字段结果；完成该键结果，不推进版本、不伪造状态变更审计 |
| 非重放且版本不匹配 | 409；即使恰好已是目标状态也拒绝，要求重读 |
| 同键同规范化请求 | 重新校验当前操作者权限后返回原结果，不重新修改目标；暂停→恢复后重放旧暂停不会再次暂停 |
| 同键异内容/目标/版本 | 409；Hash 输入为 tenantId、目标状态、expectedVersion、trim 后 reason，使用现有机制 |
| 恢复时 ownerStatus 非 ACTIVE | 允许仅改 securityStatus；重读详情仍返回 tenantAccessAllowed=false |
| 并发、锁不可用、在处理重试 | 沿用现有锁/幂等错误；不自动改 expectedVersion 或换键悄悄重试 |
| version 已达 int 上限 | 409，不能溢出或重置 |

拟复用命令类型 `CHANGE_TENANT_SECURITY_STATUS`，幂等操作域 `operations-change-tenant-security-status`；稳定 response reference 由 ACCESS_TENANT、tenantId、写结果状态/版本组成，需符合现有长度上限。重放期限/回收遵循现有本地命令表策略，不承诺无限期幂等。原事务完成后网络结果不确定时 Web 保留原键原 Body；新操作或用户修改 Body 才创建新键。

## 4. 错误、旧接口与兼容

采用 [现行 RFC 9457](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md) 字段、Request ID/时间、错误码和 fieldErrors 约定。复用现有错误类型，最终 wire code 以 Foundation 枚举为准；不另造 `{code,message}` 包装。

| HTTP | 情况 |
|---|---|
| 400 | ID/枚举/分页/JSON/不允许参数格式错误 |
| 401 / 403 | 操作者会话无效 / 无对应权限；不伪装成目标空数据 |
| 404 | 目标不存在、INTERNAL、Tenant+Member 不匹配、Permission 不属于该成员来源 |
| 409 | CAS/期望版本、幂等键冲突、命令正在处理或版本上限（按现有具体错误） |
| 422 | 有结构但字段约束未满足，例如空原因/超长原因；沿用现有验证错误与字段定位 |
| 500（Owner） | 关联损坏或未知持久化状态；BFF 按现有内部错误合同映射为服务错误，页面保留 Request ID，绝不能返回空结果或 effective=true |
| 502 / 503 / 504 | 按既有内部 HTTP/服务不可用/超时边界透传或映射，不把下游 404 改成 502 |

既有员工/角色/权限 CRUD、PLT-I02、PLT-I03、凭证/Scope API：路径、请求和业务范围保持不变；不移除旧 INTERNAL/OPERATIONS 过滤。既有审计读取增加本期受控操作/字段的展示内容，既有页面能力读取增加组件/按钮实例能力；均无新 Method/Path 或请求字段。没有接口废弃或版本升级。
