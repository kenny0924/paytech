# Client API Gateway 设计

日期：2026-09-05
文档性质：已确认设计；实现、验证与部署状态只读 `agent-docs/CURRENT_IMPLEMENTATION.md`
范围：只定义 `CLIENT_API` 公网入口；Route Key 与固定私有入口 Channel 标记的共享迁移同时适用于全部公网 Channel

> 2026-09-06 后继决定：本文第 12 节中 Problem Body 不含 `requestId` 的部分由
> [全系统 RFC 9457 Problem Details 设计](./2026-09-06-system-wide-rfc-9457-problem-details-design.md)取代；
> Gateway 的 Client API Code/Status、Owner 校验/透传和其他安全边界继续有效。

## 1. 权威关系

本设计细化 [Client API Gateway 合同决策](../../../agent-docs/decisions/2026-09-05-client-api-gateway-contract.md)，是 Client API 入口认证、可信上下文、Guard、路由、Request ID、失败和观察语义的当前权威。

它只窄幅取代以下前序内容：

- [统一公共 API 动态控制面设计](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)中跨 Channel 共用 Method + Path 冲突域、Client API 下游传 Tenant/Scope/Route、Client API 总是由 Gateway 生成 Request ID，以及把所有 Credential/Tenant/Scope/依赖失败混为同一失败码的描述；
- [Gateway 对 Platform Support 的需求](./2026-09-03-api-gateway-platform-support-requirements.md)中相同的无 Channel lookup/冲突域和 Client API 上下文描述；
- [MVC Web 日志、统一异常与内部 HTTP 精简设计](./2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md)中“Gateway 最外层可信 Filter 只接受 X-Real-IP”的绝对表述，仅增加本设计定义的入口 Channel 和 Request ID 协议。
- [公共控制面 PDManer 实施计划](../plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) Step 2 中 Route 的跨 Channel 全局 `(http_method, external_path_template)` 唯一键；后继唯一键加入 `channel`，历史生成与验证证据不因此改写。

以下前序约束继续有效：

- Platform PLT-I03 的 PHC verifier、ACTIVE CLIENT Tenant、effective ACTIVE Scope 和 `owner_ref_id` 权威；
- 六类 Route Channel、固定 Security Profile/Authorization Mode 兼容矩阵；
- Approved Target Registry、Discovery Service、Path Root 与绝对安全上限；
- 两阶段 Guard、Redis 状态、完整 Snapshot 校验和 Last Valid 行为；
- Operations 现有 JWT、Header、Cookie、CORS 和 Request ID 运行合同；
- BFF 与核心系统继续拥有资源授权和业务规则。

## 2. 目标与非目标

### 2.1 目标

- Client 系统只填写 Credential 和业务参数，不填写 Gateway 或 Tenant 上下文；
- 域名、Route 和认证 Profile 共同形成不可伪造的 Client API 入口；
- Gateway 用 PLT-I03 取得可信 Client/Credential/Tenant/Scope，再执行两阶段 Guard 和 exact Route Scope；
- Owner Adapter 只收到完成当前业务调用所需的最小可信身份；
- Client API 与 Operations 共用安全管线形状，但各自保留正确的认证算法；
- 同一业务 Path 可以被不同 Channel 安全复用；
- 失败、日志、指标、重试和激活条件可自动验证。

### 2.2 非目标

- 不创建独立 Client API 服务或第七个核心系统；
- 不设计 Client API 的具体订单、退款、Payout 等业务载荷；
- 不实现 Platform 的 PLT-I03、Credential 生命周期、表结构或初始化数据；
- 不实现任何 Owner Client API Adapter；
- 不在本设计中写生产 Nginx/DNS/TLS、Nacos、Route/Target/Scope DML 或部署脚本；
- 不增加 HMAC 请求签名、mTLS、Gateway Credential Cache、自动重试或 Tenant 配额 Override；
- 不改变 Operations、Client Console、Partner Console、Checkout 或 Provider Callback 的认证合同。

## 3. 逻辑拓扑与执行顺序

Client API 是逻辑协议面，真实业务请求直接进入对应 Owner 的隔离外部适配器：

```text
Client System
  -> HTTPS Client API domain
    -> Nginx
       - terminates public TLS
       - overwrites X-Real-IP
       - writes X-Entry-Channel: CLIENT_API
       - preserves a valid X-Request-Id or generates a fallback
      -> private HTTP API Gateway
         - resolves (channel, method, path)
         - authenticates opaque Credential through PLT-I03
         - applies route scope and guards
         - rebuilds a minimal trusted request
        -> private Owner Client API Adapter
           - applies Client resource ownership and business rules
```

单个请求的固定顺序是：

```text
trusted ingress / request-id / anti-spoof
-> absolute request-header budgets
-> channel-aware route
-> query/header policy
-> pre-auth shared-route/client-ip guard
-> CLIENT_API_CREDENTIAL authentication
-> typed identity
-> post-auth credential/tenant guard
-> exact API scope
-> body size/body receive timeout
-> minimal trusted downstream context
-> approved target/response timeout/access log
```

认证后 Guard 必须先于 Scope。这样一个合法 Credential 即使缺少 Scope，也仍消耗其主体/Tenant 入口容量；Scope 失败不得调用 Owner。

## 4. 外部请求合同

### 4.1 调用方字段

| 输入 | 规则 | 用途 |
|---|---|---|
| `Authorization` | 恰好一个 `Bearer <opaque credential>` | 唯一 Client API 身份材料 |
| `X-Request-Id` | 可选、恰好一个合法值 | 跨系统关联，不是身份或幂等 |
| `Idempotency-Key` | 仅在具体 Owner 命令声明需要时提交 | 业务命令幂等，由 Owner 执行 |
| Path/Query/Body | 由具体 Route 与 Owner API 定义 | 业务输入 |
| 业务 Header | 必须在 Route forward allowlist 中 | 内容协商或业务协议 |

调用方不得提交 `X-Entry-Channel`、任何 `X-Gateway-*`、`X-Real-IP`、`Forwarded`/`X-Forwarded-*`、Tenant、Client、Credential 或 Scope 选择字段。Nginx/Gateway 必须覆盖或删除这些值，而不是相信调用方。

Owner 由认证结果隐含。Client API 使用不含调用方可写 Client ID 的资源路径；`/v1/orders/{orderId}`、`/v1/refunds/{refundId}` 仅说明建议形态，不是已冻结 Route Path。精确路径必须来自首个 Owner 的受控 Route Manifest。业务 Body 中即使出现名称相同的 Client/Tenant 字段，也不得覆盖可信身份；是否因业务语义允许该字段由 Owner API 单独决定。

### 4.2 Authorization 语法

Gateway 要求：

1. HTTP 请求只有一个解析后的 `Authorization` 值；
2. 值总长度不超过 Gateway 现有 8 KiB 绝对 Header 预算；
3. 语法精确为一个认证 Scheme、一个 ASCII 空格和非空 opaque Credential；Scheme 按 HTTP 规则大小写不敏感，必须等于 `Bearer`；
4. Credential 中不得出现空白、控制字符或逗号，不做 Key Prefix 身份推断；
5. 解析失败、重复或超限都返回统一 `401`，不得调用 PLT-I03 或 Owner。

Platform 发布的 PLT-I03 合同可以定义更小的 Credential 最大长度或更窄字符集；Gateway 接入时必须同时执行更严格上限，但不能自行复制另一套 Credential wire DTO 或推断业务结构。

## 5. Nginx 与可信入口协议

### 5.1 Channel

Route Key 的 Channel 化作用于共享 Catalog，因此 Channel-aware Gateway 部署前，每个已经对外服务的 Nginx `server`/`location` 都必须先覆盖写入自己的单值固定 Channel；当前 Operations 入口写入 `OPERATIONS`，Client API 专用域名写入 `CLIENT_API`。这只是 Nginx 到 Gateway 的私有协议，不改变 Operations 外部合同。

Client API 对应 Nginx `server`/`location` 还必须：

- 覆盖写入单值 `X-Entry-Channel: CLIENT_API`；
- 不读取外部同名值决定 Channel，也不追加多个值；
- 继续覆盖单值 `X-Real-IP=$remote_addr`；
- 只通过私有网络连接 Gateway Listener。

Gateway 只接受单值、精确枚举的 `X-Entry-Channel`。缺失、重复或未知值说明可信入口协议被绕过，统一 Fail-Closed 为 `403`，零 Platform/Redis/Owner 调用。解析后的 Channel 写入 exchange 属性，然后原 Header 被删除。

旧 Nginx 与强制 Channel 的新 Gateway 不可混合部署；必须先完成所有既有入口的覆盖写入，再部署 Channel-aware Gateway。外部调用方即使提交该 Header，也会被命中域名的 Nginx 配置覆盖，不能选择 Channel。

### 5.2 Request ID

合法 `X-Request-Id` 的完整规则是：

```regex
[A-Za-z0-9][A-Za-z0-9._-]{0,63}
```

Client API Nginx 对外部值执行单值与正则判断：合法时保留；缺失、重复或非法时用 `$request_id` 覆盖。Gateway 在 `CLIENT_API` Channel 再执行相同的单值与正则判断；若仍不合法则生成新的 UUID，并把最终值固定到 exchange、下游和响应。

每次 HTTP 尝试建议使用新的 Request ID；同一逻辑命令重试继续复用原 `Idempotency-Key`。Request ID 不进入认证、Scope、Redis Guard Key 或业务幂等判断。

Operations 继续忽略外部 `X-Request-Id` 并由 Gateway 生成新值。Nginx 新增固定 `OPERATIONS` 入口标记不改变这一行为，也不改变现有 JWT、CORS、Cookie 或下游 Header。

## 6. Channel-aware Route Catalog

### 6.1 Lookup 与唯一性

运行时 lookup 是：

```text
(GatewayRouteChannel channel, HttpMethod method, raw path)
```

规则如下：

- 同一 Channel + Method + 精确 Path 只能有一条 Route；
- 同一 Channel + Method 下，两个变量模板只要存在共同可匹配 Path 就拒绝；
- 固定 Path 继续优先于变量模板；
- 不同 Channel 可以使用相同 Method/Path；
- `route_code` 继续全局唯一；
- Method 大小写、Path 编码、变量集合一致性、HEAD/OPTIONS 和最大 Route 数沿用公共控制面设计。

这要求 Platform 当前配置唯一约束、Platform Snapshot Compiler 和 Gateway Snapshot Compiler 同时把 Channel 加入冲突键。只改 Gateway 会让 Platform 无法发布跨 Channel 同路径 Snapshot，因此 Platform 同步是 Route 激活前置。

### 6.2 Client API Route 固定形状

每条 Client API Route 必须满足：

| 字段 | 值/规则 |
|---|---|
| `channel` | `CLIENT_API` |
| `security_profile_code` | `CLIENT_API_CREDENTIAL` |
| `authorization_mode` | `API_SCOPE` |
| `required_scope_code` | 恰好一个符合公共 Scope 格式的非空值 |
| `permission_id/code` | 空 |
| `request_cookie_mode` | `DROP` |
| `response_cookie_mode` | `NONE` |
| `target_service_code` | 必须命中 Gateway 静态 Approved Target Registry |
| `downstream_path_template` | 必须位于该 Target 的已批准 Path Root 且变量集合与外部 Path 完全相同 |
| Header/Query | 精确 allowlist；保留 Header 不可进入 allowlist |
| Body/Timeout/Guard | 同时满足 Snapshot 值与 Gateway 编译期绝对上限 |

数据库或 Snapshot 不能把这些固定值变成另一套认证算法、任意 Target 或任意可信 Header。

## 7. 请求策略

- Query Name 必须全部出现在当前 Route 的精确 allowlist 中；值原样传递，但不得写入普通日志；
- Gateway 清除 hop-by-hop、代理、Cookie、`Authorization`、`X-Entry-Channel`、`X-Gateway-*` 等 Header，再从 allowlist 重建业务 Header；
- Client API 不启用浏览器 CORS，不处理 Preflight 业务协议；
- `CLIENT_API` 的实际 `OPTIONS` 不读取 `Access-Control-Request-Method` 借用 GET/POST Route；未显式配置的 OPTIONS 返回 404，且不进入 PLT-I03、Redis 或 Owner；
- Request Cookie 一律丢弃，Owner 不接收 Cookie；
- Request Body 不做业务解析或改写，只在流式边界执行 Route body size 与 body receive timeout；
- `Idempotency-Key` 只有出现在具体 Route allowlist 中才可转发，Gateway 不解释、不缓存、不生成也不重试。

## 8. Credential 认证与 Scope

### 8.1 PLT-I03 Owner 合同

生产实现必须依赖 Platform Owner 在 `platform-support-api` 发布且返回 `Mono<OwnerResponse>` 的 reactive typed `@HttpExchange` 合同，不能在 Gateway 复制请求/响应 DTO、手写同一 URI 或在 Netty 线程调用 blocking Proxy。若实现时该合同尚未发布，Platform 专项任务必须先冻结其 HTTP Method、Path、Credential 传输位置、确切 Java 类型、最大响应集合与 wire error 形状，Gateway 相关任务保持阻塞。

PLT-I03 的具体消费合同如下；artifact 安装、发布、Provider 验证和部署事实只记录在 [当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)：

| 项目 | 合同 |
|---|---|
| Java API | `com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi` |
| 私有 HTTP | `POST /platform-support/api-credentials/v1/validate`；`/platform-support` 为服务 Context Path，接口注解拥有 `/api-credentials/v1/validate`，只拼接一次 |
| 请求 | 单值 `X-Platform-Api-Credential` 携带裸 opaque Credential，不含 `Bearer `；无请求 Body；内部 `X-Request-Id` 使用 Gateway 最终可信值 |
| 成功 | 仅接受 `200 application/json`，按 `ApiCredentialValidationModels.ValidatedCredentialResponseBody` 解码 |
| 身份字段 | `tenantId`、`ownerRefId`、`credentialId` 为 1–19 位正十进制字符串；`tenantType` 固定 `CLIENT` |
| Scope | `effectiveScopeCodes` 最多 64 项，每项最多 128 字符；使用 Owner `ApiCredentialConstraints` 的小写精确 Scope 语法，不允许通配；可为空，去重后不可变 |
| 凭据语法 | 使用 Owner `ApiCredentialConstraints` 校验：8–32 字符 Prefix、一个点分隔符、32–223 字符 Secret，总长 41–256；不得另建 Gateway 业务语法 |
| 无效认证 | 仅合法 RFC 9457 `401` 且 `code=API_CREDENTIAL_AUTHENTICATION_FAILED` 映射为对外 `401 UNAUTHENTICATED` |
| 依赖故障 | 其他状态、异常/超时、空响应、非法 JSON/类型、重复 JSON Key、尾随内容、非法身份/Scope 或超限响应，一律对外 `503 DEPENDENCY_UNAVAILABLE` |

响应最多 16 KiB、JSON 最大深度 16；连接默认 1 秒、认证总预算默认 2 秒，最大 10 秒。Client 使用独立的有界连接池与非阻塞 I/O 资源，禁用 Redirect 和自动 Retry。生产通过 `platform-support-service` 服务发现；直接 URI 只允许显式启用且唯一 active profile 为 `auto` 的 `http://127.0.0.1:<port>`，不能作为生产地址回退。

语义必须同时满足：

- 请求只携带 opaque Credential 和必要的内部协议元数据；Credential 只能位于受控 Header 或 Body，不能进入 Path/Query；不得携带调用方选择的 Tenant、Client、Owner 或 required Scope；
- 成功结果唯一且包含 `tenantId`、固定 `tenantType=CLIENT`、`ownerRefId`、`credentialId`、有界去重的 `effectiveScopeCodes`；
- `ownerRefId` 只来自已验证 ACTIVE Client Tenant 行；
- Platform 明确判定错误、REVOKED/EXPIRED Credential 或没有连接同一 ACTIVE CLIENT Tenant 属于无效认证；
- 连接失败、超时、5xx、歧义、非法/超限响应属于依赖不可用；
- Credential 明文只能存在于当前认证调用的最短生命周期，不进入日志、异常、指标、缓存或下游。

### 8.2 Gateway 映射

Gateway 不缓存、不重试 PLT-I03，也不回退其他认证方式：

| PLT-I03 结果 | Gateway 结果 |
|---|---|
| 唯一合法 Client identity | 建立 typed identity，继续 post-auth Guard |
| 无效/撤销/过期 Credential 或 Tenant 非 ACTIVE | `401 UNAUTHENTICATED` |
| 2xx 响应为空/重复/非法字段、tenant type 非 CLIENT、歧义 | `503 DEPENDENCY_UNAVAILABLE` |
| 连接/读取超时、断连、5xx、超限 Body | `503 DEPENDENCY_UNAVAILABLE` |

typed identity 的语义是：

```text
principalKey = credentialId
tenantKey    = tenantId
clientId     = ownerRefId after tenantType == CLIENT
scopes       = immutable effectiveScopeCodes
```

### 8.3 Exact Scope

认证与 post-auth Guard 成功后，Gateway 用区分大小写的字符串精确判断：

```text
identity.effectiveScopeCodes contains route.requiredScopeCode
```

不支持 wildcard、resource pattern 推导、前缀、层级继承或调用方指定 Scope。缺少 Scope 返回 `403 INSUFFICIENT_SCOPE`，且 Owner 调用次数为零。PLT-I03 不替 Gateway 决定某条 Route 的 required Scope。

任何已匹配的 Client API Route 在 authenticated Guard、Scope 或下游身份注入阶段缺少 typed `ClientApiIdentity`，都表示 Gateway 内部合同损坏，统一返回 `503 DEPENDENCY_UNAVAILABLE`；不得 skip、NPE 或把请求转发给 Owner。

## 9. Guard

| 阶段 | 维度 | 可信 Key 来源 |
|---|---|---|
| pre-auth | `SHARED_ROUTE` | 内部稳定 `route_code` |
| pre-auth | `CLIENT_IP` | Nginx 覆盖并由 Gateway 解析的 `X-Real-IP` |
| post-auth | `AUTHENTICATED_PRINCIPAL` | PLT-I03 `credentialId` |
| post-auth | `TENANT` | PLT-I03 `tenantId` |

Client API Route 必须同时配置 Shared Route、Client IP、Authenticated Principal 和 Tenant 的 Rate/Concurrency 成对基线。Redis Bucket/Counter/Lease/TTL 继续由 Gateway 拥有；租约在成功、业务错误、超时、取消连接和异常终态都必须释放。Redis 失败统一 `503`，不得绕过 Guard。

## 10. Owner 下游合同

### 10.1 唯一可信 Header

Gateway 完成空白 Header 重建后，只额外注入：

| Header | 来源 | 用途 |
|---|---|---|
| `X-Request-Id` | 最终可信 Request ID | 关联当前 HTTP 尝试 |
| `X-Gateway-Client-Id` | PLT-I03 `ownerRefId` | Owner 确定当前 Client |
| `X-Gateway-Credential-Id` | PLT-I03 `credentialId` | 安全审计与 Credential 级诊断 |

允许的业务 Header 按 Route allowlist 另行转发。Owner Adapter 必须把 `X-Gateway-Client-Id` 作为可信 Client 身份入口，并拒绝业务 Path/Query/Body 覆盖；核心业务服务继续验证资源确实属于该 Client。

### 10.2 明确禁止下发

`Authorization`、opaque Credential、Prefix/Verifier、effective Scope、Tenant ID/Type、Route Code、Entry/Gateway Channel、Actor Type/Subject、Realm、Session、Context Type、Tenant Member、`X-Real-IP`、Forwarded 系列和 Cookie 一律不下发。

`tenantId` 仍在 Gateway 内用于 Tenant Guard，但不是 Owner 业务身份。`route_code` 仍在 Gateway 内用于 Guard/日志/指标，但不是 Owner 合同。

### 10.3 Approved Target

生产 Registry 只有在真实 Owner Adapter 已存在且路径根已评审时，才加入固定的：

```text
targetServiceCode + CLIENT_API + discoveryServiceName + approvedPathRoots + lb:// URI
```

不能为了 Gateway 测试预注册空 Owner。自动测试使用 test-only 注入的 Loopback Target；生产 Registry 保持无虚假 Target。

## 11. 响应合同

- 保留 Owner 的 HTTP Status 与业务响应体；
- V1 不增加 Route 可配置的响应 Header allowlist；按代码固定、大小写不敏感的集合保留 `Content-Type`、`Content-Length`、`Content-Encoding`、`Content-Language`、`Content-Disposition`、`ETag`、`Last-Modified`、`Retry-After`；
- `Location` 是唯一受内容约束的窄例外：只允许 `201 Created` 的单值 ASCII absolute-path reference，必须以 `/api/v1/` 开始且不含 scheme、authority、query、fragment、反斜线或控制字符；非法值返回 `502`，其他状态的 `Location` 被删除；
- Gateway 重建 `X-Request-Id`、`Cache-Control: no-store` 和 `Pragma: no-cache`，删除 hop-by-hop、`Set-Cookie`、内部/代理 Header 及其他 Owner Header；
- 新增任何可透传响应 Header 都需要 Gateway 代码和安全测试评审，不能由 Platform Route 配置任意放宽；
- 最终响应总是带与下游相同的 `X-Request-Id`；
- Client API 不添加 Operations CORS/`Vary: Origin`；
- Owner 超时返回 `504`，非法/截断/违反传输合同的上游响应返回 `502`；
- Gateway 不能用自定义成功 Envelope 包裹 Owner 响应。

## 12. 错误合同

| 场景 | Status | Error |
|---|---:|---|
| 缺失/重复/未知可信 Entry Channel | 403 | `FORBIDDEN` |
| `(channel, method, path)` 未命中 | 404 | `NOT_FOUND` |
| 非法 Path/Query/业务 Header | 400 | `INVALID_REQUEST` |
| Authorization 缺失、重复、格式/长度错误 | 401 | `UNAUTHENTICATED` |
| 除 Client API Authorization 外的受限 Header 超过绝对预算 | 431 | `REQUEST_HEADERS_TOO_LARGE` |
| Credential/Tenant 无效、撤销、过期、非 ACTIVE | 401 | `UNAUTHENTICATED` |
| exact required Scope 缺失 | 403 | `INSUFFICIENT_SCOPE` |
| Rate/Concurrency 拒绝 | 429 | `RATE_LIMIT_EXCEEDED` 与 `Retry-After` |
| Body 过大 | 413 | `REQUEST_TOO_LARGE` |
| Body 接收超时 | 408 | `REQUEST_TIMEOUT` |
| PLT-I03/Redis 不可用或 PLT-I03 响应非法/歧义 | 503 | `DEPENDENCY_UNAVAILABLE` |
| Route 引用的已批准 Target 在当前部署不存在 | 503 | `DEPENDENCY_UNAVAILABLE` |
| Owner 响应非法 | 502 | `UPSTREAM_PROTOCOL_ERROR` |
| Owner 响应超时 | 504 | `DEPENDENCY_TIMEOUT` |

认证失败只返回通用消息，不区分错误 Secret、撤销、过期或 Tenant 状态。Gateway 本地 Client API 错误固定使用 RFC 9457 `application/problem+json`，Body 为 `type/title/detail/code/errors?`，不包含 `status` 或 `requestId`；最终 Request ID 只放在 `X-Request-Id` Header。Owner 错误在满足同一合同后保持其状态码与 Body。Route、认证、Scope、Guard、Body 失败必须保证 Owner 调用次数为零。

## 13. 重试与幂等

- Gateway 对 PLT-I03 和 Owner 命令均零自动重试；
- Client 对一次新的 HTTP 尝试使用新的 Request ID；
- 同一逻辑命令重试必须复用原 `Idempotency-Key`；
- Gateway 只转发该 Header，Owner 负责幂等 Namespace、重放和冲突；
- `client_order_id` 等业务唯一键不等于 transport idempotency key；
- 429/503/504 不自动表示命令未执行，调用方仍按 Owner 的幂等合同重试。

## 14. 可观测性与审计

Access Log 可以记录：

- Request ID、内部 Route Code、Channel/Profile、HTTP Method、Status、Outcome、耗时；
- Client ID 和 Credential ID 的版本化不可逆摘要；
- Client IP 的版本化不可逆摘要；
- Guard phase/dimension 与低基数失败分类。

Access Log 不记录：

- `Authorization`、Credential、Prefix、Verifier、Scope 集合；
- Tenant/Client/Credential 原值；
- Query Value、Request/Response Body 或业务敏感 Header；
- Redis Key 原值或 Path Variable。

Metric 只使用 Route Code、Channel、Profile、Outcome、Status 和 Guard phase/dimension 等有界标签。Tenant、Client、Credential、Request ID、原始 Path 和 Path Variable 禁止成为 Label。

职责分工：Gateway 记录入口和安全决策；Platform 记录 Credential 创建、轮换、撤销和验证侧安全事件；Owner 记录业务命令和资源访问审计。

## 15. 验收合同

Gateway 自动测试必须使用真实 WebFlux/Gateway/HTTP/Redis 生产链，不使用 Mockito、`@MockBean`、spy 或方法级 Stub 替代生产边界。外部依赖通过随机 Loopback 真实 HTTP Simulator 表达，Scenario Store 默认拒绝未声明调用，Request Journal 只保存安全摘要并在每测后 reset/verify。

至少覆盖：

1. 跨 Channel 相同 Method/Path 可以共存；错误 Channel 不命中；同 Channel 模板重叠拒绝，并保留当前 64/65/4096 条边界和不同变量数交集修复；
2. 合法外部 Request ID 端到端保持；缺失、重复、非法时生成 fallback；响应与下游一致；
3. 外部伪造 `X-Entry-Channel`/`X-Gateway-*`/代理 Header 不进入 Owner；
4. Credential 成功、错误、撤销、过期、Tenant 非 ACTIVE、PLT-I03 401/5xx/超时/断连/非法 JSON/非法字段/超限响应；
5. exact Scope 成功和缺失 403，缺失时 Owner 零调用；
6. IP Guard 在 PLT-I03 前，Credential/Tenant Guard 在认证后；Redis Fail-Closed；所有终态释放 Lease；
7. Owner 只收到 Request/Client/Credential ID 与 allowlisted 业务 Header，不收到 Authorization/Tenant/Scope/Route/Channel/Cookie；
8. 400/401/403/404/408/413/429/502/503/504 代表场景及零非预期调用；
9. Credential canary 不出现在日志、异常、Metric Tag 或 Simulator Journal；
10. Nginx 专项配置测试先验证每个既有公网入口都覆盖正确 Channel，再验证 Client API 域名、Real IP 和 Request ID fallback；Gateway auto 不冒充覆盖生产 Nginx。

## 16. 实施与激活门禁

### 16.1 Gateway 编码前硬前置

Platform Owner 必须先在 `platform-support-api` 发布 PLT-I03 typed HTTP 合同，并冻结：

- HTTP Method、Path 与 Credential 传输位置；
- 可由 Gateway 直接引用的 Credential 长度/字符合同常量或 validator；
- typed request/response 与 effective Scope 集合上限；
- 无效认证与技术不可用的 HTTP/error 分类；
- 请求不得包含 Tenant/Client/Owner/required Scope 覆盖值。

Gateway 可以先完成 Channel-aware Catalog、可信入口、Request ID 和通用 Guard 的独立部分；没有上述 artifact 时不能复制 wire DTO 来完成生产 PLT-I03 Client。

### 16.2 真实 Route 激活前置

以下条件全部满足前，Gateway 的 Client API 能力最多是 integration-ready，不是可用产品：

1. Platform 已发布 PLT-I03 typed artifact 并部署可发现、私网可达的 Provider；Credential/Scope 手工 SQL 已实际应用，ACTIVE CLIENT Tenant 通过获批的 CCT→Platform 投影形成，受控 Credential 生命周期可用；最终 PLT-I03 Path/传输位置已加入 Platform MVC metadata-only 日志规则，并由 Credential canary 测试证明请求、响应和异常日志不含 Credential；
2. Platform Route 唯一约束和 Compiler 已改为 `(channel, method, path)`；
3. Platform/Gateway Header 拒绝集都按大小写不敏感前缀拒绝所有 `X-Gateway-*`，并显式拒绝 `X-Entry-Channel`、`X-Request-Id`、`X-Real-IP`、`Forwarded`/`X-Forwarded-*`、Cookie、`Set-Cookie` 和其他现有静态保留 Header；
4. 至少一个真实 Owner Client API Adapter 已部署、可发现且私网可达，并通过接口/安全评审形成受控 Route Manifest：精确 Route Code、Method、外部/下游 Path Template、Target/Discovery/Path Root、required Scope、Request Header allowlist、Query allowlist、`body_limit_bytes`、`body_receive_timeout_millis`、`response_timeout_millis`、四维 Guard、Idempotency 和资源授权；响应 Header 使用本文代码固定集合，不进入 Manifest；本文中的路径只作示例；
5. Nginx 每个既有公网入口都已覆盖自己的固定 `X-Entry-Channel`，Client API 域名、TLS、`X-Real-IP` 和 Request ID 规则已部署；该步骤早于 Channel-aware Gateway 部署；
6. Gateway Approved Target Registry 加入真实 Target 后完成全量验证、构建与部署，此时 Client API Target/Route 仍为 `DISABLED`；
7. Platform 受控 Target/Route/Scope 数据以 `DISABLED` 准备并完成静态核对；
8. 在受控窗口把 Target 与首条 Route 切换为 `ACTIVE`；在使用未分发的测试 Credential 做公开 HTTP smoke 前，必须逐实例确认刷新成功、`lastSuccessAt` 晚于状态变更，且首条 Route 的 `CLIENT_API` Target/Route Count 从 0 收敛到 1。Smoke 同时检查低基数指标和 Nginx、Gateway、Platform、Owner 日志中的 Credential canary。任一激活、收敛或 smoke 失败时，先撤销测试 Credential，再把 Target 与 Route 都改为 `DISABLED`，等待每实例成功刷新且 Count 回到 0 后验证 Owner 零调用；不能把控制面提交响应当作 Gateway 已切换。

当前 Snapshot 不携带业务 Revision，刷新失败会保留 Last Valid。上述 Count 证据只允许首条 Route 的 `0 -> 1 -> 0` 受控窗口；后续 Route 更新若不能产生唯一 Count 变化，必须先增加逐实例可核对的 Snapshot Revision/Fingerprint，再执行激活或回滚。

启用顺序和回滚遵循决策 D9。任何前置缺失都不得加入空生产 Target、静态旁路 Route、临时匿名 Profile 或直连 Owner 的公网入口。

## 17. 状态路由

本文不保存当前实现、验证或部署快照。精确差距、证据与完成判据只读 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 的 `GATEWAY-CLIENT-API-01`；设计确认本身不是实现或部署证据。
