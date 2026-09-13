# Platform Support 统一公共 API 动态控制面设计

> 2026-09-13 命名后继：[支付渠道 Channel 决定](../../../agent-docs/decisions/2026-09-13-payment-channel-terminology.md)取代本文支付领域 Provider 命名；业务边界与历史证据保持。现行实体、字段及自有回调代码使用 Channel；外部固定协议与原报文不改写。

> **2026-09-11 Target/Route 后继决定：** [数据库路由范围决定](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)取代本文静态 Approved Target Registry、具体 Discovery/Path Root 二次名单及关联校验/上线要求；Platform 数据库与完整快照成为业务目标/路由权威。其余认证、Channel、Header、协议和控制面规则保持；[技术修订方案](../../../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)待阶段评审，运行状态只读当前台账。

> 2026-09-05 审计专项后继：[Controller 尽力审计决策](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)
> 仅取代本文中既有 Operations Target/Route HTTP 状态命令的“配置与 Audit Event 同事务”约束。
> 配置事务、CAS、幂等、全局校验及尚未实现的管理命令范围不变；新审计方案的实施状态见当前实现台账。

> 2026-09-05 Client API 后继：[Client API Gateway 合同决策](../../../agent-docs/decisions/2026-09-05-client-api-gateway-contract.md)
> 及其[完整设计](./2026-09-05-client-api-gateway-design.md)将 Route lookup/冲突域改为
> `(trusted channel, method, path)`，并取代本文的 Client API Request ID、下游可信 Header 与精确失败码描述。
> Route Key 及其所需的固定私有 Nginx→Gateway Channel 标记是共享迁移；Client API 的认证、外部 Request ID
> 和最小下游 Header 语义只适用于 `CLIENT_API`，Operations 的外部、JWT、CORS、下游 Header 和 Request ID 合同不变。

> 本文第 13–16 行及其他实现、验证、运行或部署现在时只保存 2026-09-01 的历史快照，不是当前状态权威；
> 当前事实只读 `agent-docs/CURRENT_IMPLEMENTATION.md`。

> 状态：用户已于 2026-09-01 完成方案审阅并批准设计；Platform PDManer 后继模型已按
> [实施计划](../plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 生成并通过专项自动门禁。Platform Phase A
> Migration、受控初始化和 PLT-I20 全量 Snapshot 查询已经实现；Target/Route/Console 管理 API、Gateway 动态
> Snapshot 消费、运行环境与部署仍未实施。当前已部署 Gateway 在完成独立切换前继续使用静态 Operations Catalog。
>
> 路径说明：为保持已有链接稳定，本文继续沿用最初 Operations-only 草案的文件路径。本文内容已经将该草案扩展并
> 取代为统一公共 API 控制面，不再只描述 Operations。
>
> 传输取代说明：本文的公共控制面、六类 Channel、Route/Target/Guard、权限与 Scope 绑定继续有效；应用 TLS、
> Workload Identity 和 JWKS HTTPS 等传输描述已由
> [`2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`](./2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md)
> 取代。公网 TLS 在 Nginx 终止；Java 应用使用部署网络限制的私有 HTTP，不持有 X.509 应用证书。
>
> 错误语义取代说明：用户于 2026-09-03 决定 PLT-I20 不再捕获并转换 Snapshot 查询异常。当前配置或数据库
> 无法形成完整 Snapshot 时，由共享全局异常处理器返回 `500 INTERNAL_ERROR`；Gateway 对任意 `5xx` 继续保留
> Last Valid，因此消费端的 Fail-Closed 行为不变。

## 1. 权威关系与生效边界

本文定义 API Gateway 与 Platform Support 之间面向所有外部入口的动态 Route、Target、Guard、Console
Resource、Permission 和 API Scope 绑定契约。

本文批准后：

1. Platform Support 是公共 API Route 当前配置、Route Guard 参数、受控 Target 状态和三个人工 Console
   Resource 的数据库权威；
2. API Gateway 是公共入口运行时执行者，从 Platform 拉取一个全局不可变 Snapshot；
3. Operations、Client、Partner、Hosted Checkout、Client Public API 和 Provider Callback 全部纳入同一公共
   Route Catalog；
4. 内部服务间同步 HTTP 调用继续绕过 Gateway，也不进入公共 Route Catalog；
5. Gateway 只执行入口协议、最小身份认证、Route Scope、限流、路由、请求标识和访问日志；BFF 与核心系统继续
   执行当前权限、租户资源和业务资源授权；
6. 新增 Route 只要落在已批准 Target 和静态安全能力内，可以通过 Platform 当前配置生效，不需要为每条 Route
   修改 Gateway Java；
7. 新增 Target Service、Security Profile、可信 Header、认证算法或新的外部应用边界仍需要架构与安全评审，不能
   仅靠数据库配置完成。

本文取代以下旧结论中与动态公共控制面冲突的部分：

- “Gateway Route Catalog 和 Guard Profile 永远只由 Gateway 编译期代码维护”；
- “Platform 不保存 Gateway Route、Target 或 Guard 当前配置”；
- “动态 Gateway 配置只面向 Operations Channel”；
- 早期 Platform Gateway Policy Version、Binding Version、Release、Release Item、Ack、Rollback 等版本发布模型。

以下现有权威继续有效：

- [Operations Gateway hardening](./2026-08-30-operations-gateway-hardening-design.md) 对 Header、Cookie、
  JWT/JWKS 校验、Redis Lua、Lease、Timeout、错误分类和访问日志的非传输安全硬边界；应用传输和 Client IP
  信任来源以上述 2026-09-02 后继设计为准；
- Platform 统一人工认证设计中的全局 Account、Realm、三个人工 Channel、Session、Tenant IAM、Google OIDC 和
  Password 规则；
- Platform 57 表精简设计中的当前 IAM、API Credential/Scope、Audit/Security 和可靠执行边界；
- BFF 不拥有业务事实、核心系统保留资源与业务授权、Vault 不对公网开放等应用边界。

在对应 Platform/Gateway 实施和切换完成前，当前已部署 Gateway 的静态 Operations Catalog 仍是运行时事实。
本文是后续实施的设计权威，不把尚未实现的动态能力描述成已经上线。

## 2. 目标与非目标

### 2.1 目标

- 所有公网 HTTP 接口都能在 Platform Support 中被发现、启停、绑定 Target、选择已批准安全档案并配置 Guard；
- 公共 Route 在六类入口之间使用一个全局冲突域，避免两个 Channel 声明同一 Method + 可匹配 Path；
- Gateway 每次拉取一个完整全局 Snapshot，先验证、编译，再原子切换；
- Operations 是 Route、Guard、Target 状态和 Console Resource 的唯一管理主体；
- Client/Partner Tenant Admin 只能管理本 Tenant 的 Member、Role 和授权关系，不能创建公网 Route 或修改 Guard；
- 多租户限流使用统一 Route 参数，并按可信 Tenant 身份形成相互隔离的 Redis Bucket；
- 使用最少关系表，不增加 `*_ver`、Snapshot、Release、Rollback、Ack 或专用 Idempotency 表；
- 不增加数据库 `*_hash` 字段，也不为内部查询预建 HTTP 条件缓存协议；
- 配置异常必须 Fail-Closed，且不能把部分新配置暴露给业务请求。

### 2.2 非目标

本文不设计或授权：

- 内部服务间 HTTP、MQ、XXL-Job、数据库或 Vault 调用的 Gateway Route；
- 任意 URL、IP、Port、Scheme、脚本、正则、通配 Route、插件、动态 Java、Lua 或认证算法下发；
- Client 或 Partner Tenant 自定义公网 Route、认证方式、Guard 参数或 Target；
- 单 Tenant 定制配额、套餐 Tier、Guard Override 或按客户动态调高/调低限制；
- Draft、Approval、Maker-Checker、Release、Rollback、Fleet Activation Barrier 或 Gateway Instance Ack；
- Gateway 中的 Role/Permission 全量计算、Checkout 资源授权、Provider 签名业务规则或核心系统业务授权；
- 把 Redis 令牌、计数器、Lease、TTL 或运行时统计写回 Platform 数据库；
- 新建 `client-api-service`、`provider-callback-service` 或第七个核心业务系统；
- WebSocket、gRPC、任意 TCP 转发或前端静态资源/CDN 发布；本文只覆盖公共 HTTP API；
- 让 Platform 或 Vault 数据库被 Gateway、BFF、浏览器或外部调用方直接访问。

## 3. 外部入口与内部调用边界

### 3.1 纳入公共 Route Catalog 的入口

| 公共入口 | Gateway Route Channel | 目标边界 | 最终资源授权所有者 |
|---|---|---|---|
| Operations Console | `OPERATIONS` | `operations-console-bff` | Operations BFF + Platform PLT-I02 + 对应核心系统 |
| Client Console | `CLIENT` | `client-console-bff` | Client BFF + Platform Tenant IAM + 对应核心系统 |
| Partner Console | `PARTNER` | `partner-console-bff` | Partner BFF + Platform Tenant IAM + 对应核心系统 |
| Hosted Checkout | `CHECKOUT` | Commerce Checkout 外部适配器 | Commerce |
| Client Public API | `CLIENT_API` | 各核心系统已批准 Client API 外部适配器 | Gateway Route Scope + 对应核心系统 |
| Provider Callback | `PROVIDER_CALLBACK` | Payment Provider Callback 外部适配器 | Payment |

Client Public API 是逻辑 API 面，不是独立服务。`/api/v1/orders/**`、`/api/v1/refunds/**` 等具体外部 Route
仍路由到拥有该能力的核心系统外部适配器。Provider Callback 同样直接进入 Payment 的受控 callback adapter，
不新建 callback service。

### 3.2 内部调用

以下调用不进入本文模型：

```text
BFF -> Core System internal API
Core System -> Core System internal API
Core System -> Platform PLT-Ixx internal API
Gateway -> Platform Snapshot / PLT-I03 internal API
```

这些调用使用部署网络限制的私有 HTTP、Spring HTTP Service Client、Spring Cloud LoadBalancer 和 Nacos 服务发现，
直接调用内部 Endpoint。Java 服务不持有 X.509 应用证书；它们不穿过公共 Gateway、不创建 `t_gateway_route`、
不消耗公共 Guard Bucket。

Gateway -> Platform Snapshot 与 PLT-I03 是部署私网中的内部控制链路，不是公网 Route；Snapshot Endpoint 的地址和
私网可达边界来自 Gateway 部署配置，不能由待拉取 Snapshot 自举。Platform 当前不使用 Spring Security、调用方
Header、X.509 或 mTLS 对该链路做应用认证。

## 4. Channel、Security Profile 与 Authorization Mode

### 4.1 Gateway Route Channel

`t_gateway_route.channel` 和 `t_gateway_target.channel` 使用：

```text
OPERATIONS
CLIENT
PARTNER
CHECKOUT
CLIENT_API
PROVIDER_CALLBACK
```

这六个值是公共入口分类。它们不扩展 Platform 人工认证的 `t_auth_session.channel`。人工认证 Channel 继续严格为：

```text
OPERATIONS
CLIENT
PARTNER
```

Checkout Consumer、API Credential 和 Provider 不创建人工 Account Session，也不获得新的 Realm。

### 4.2 预编译 Security Profile

Route 只能从以下已批准实现中选择 `security_profile_code`：

```text
OPERATIONS_JWT
CLIENT_JWT
PARTNER_JWT
CLIENT_API_CREDENTIAL
CHECKOUT_SESSION
PROVIDER_CALLBACK
PUBLIC
```

Profile Code 只是安全实现选择器。Platform 不保存或下发 JWT Algorithm、Issuer、Audience、JWKS Trust、Cookie
名称、Provider 签名脚本、Secret、KMS Key、Redis TTL 或可信 Header 名称。

`PUBLIC` 表示 Gateway 不要求 Access JWT 或 API Credential，但并不跳过 Nginx 公网 TLS、可信 Client IP、Path、
Header、Query、Body、Guard、Target 和目标服务输入校验。Operations/Client/Partner 的 Login、Registration、OIDC Callback、Refresh 等需要匿名
进入的认证 Route 可使用 `PUBLIC`，其精确 Cookie 名称、CORS Origin 和 Channel 协议仍由 Gateway 静态实现。

### 4.3 Authorization Mode

Route 使用下列互斥模式：

| Mode | 用途 | Route 绑定 | 运行时授权 |
|---|---|---|---|
| `PUBLIC` | 登录、注册、OIDC Callback 或其他明确批准的匿名入口 | Permission/Scope 均为空 | Gateway 不建立业务授权；目标服务仍校验输入 |
| `HUMAN_AUTHENTICATED` | Logout、当前身份、Tenant 切换等只要求有效人工身份的接口 | Permission/Scope 均为空 | Gateway 校验最小 JWT；目标 BFF/Platform 校验当前 Session/Account 语义 |
| `HUMAN_PERMISSION` | Operations、Client、Partner Console 的受权 API | `permission_id` 必填 | Gateway 校验 JWT；BFF 调 PLT-I02 |
| `API_SCOPE` | Client Public API | `required_scope_code` 必填 | Gateway 调 PLT-I03 并要求有效 Scope；核心系统继续资源授权 |
| `CHECKOUT_RESOURCE` | Hosted Checkout | Permission/Scope 均为空 | Commerce 校验 Session、Order、Client 和业务资源 |
| `PROVIDER_CALLBACK` | Provider Callback | Permission/Scope 均为空 | Payment 校验 Provider 签名、防重放和业务资源 |

`HUMAN_AUTHENTICATED` 是必要的独立模式，不能把 Logout 或当前身份查询伪装成某个业务 Permission，也不能让
`HUMAN_PERMISSION.permission_id` 变成可空字段。

### 4.4 静态兼容矩阵

Platform 管理 API 和 Gateway Snapshot 编译器都必须执行至少以下兼容检查：

| Channel | 可用 Profile | 可用 Authorization Mode |
|---|---|---|
| `OPERATIONS` | `PUBLIC`、`OPERATIONS_JWT` | `PUBLIC`、`HUMAN_AUTHENTICATED`、`HUMAN_PERMISSION` |
| `CLIENT` | `PUBLIC`、`CLIENT_JWT` | `PUBLIC`、`HUMAN_AUTHENTICATED`、`HUMAN_PERMISSION` |
| `PARTNER` | `PUBLIC`、`PARTNER_JWT` | `PUBLIC`、`HUMAN_AUTHENTICATED`、`HUMAN_PERMISSION` |
| `CLIENT_API` | `CLIENT_API_CREDENTIAL` | `API_SCOPE` |
| `CHECKOUT` | `CHECKOUT_SESSION` | `CHECKOUT_RESOURCE` |
| `PROVIDER_CALLBACK` | `PROVIDER_CALLBACK` | `PROVIDER_CALLBACK` |

Human JWT Profile 与 Channel 必须一致。`PUBLIC` Profile 只能与 `PUBLIC` Mode 组合；不能用 `PUBLIC` Profile 配置
`HUMAN_PERMISSION`。每个 Profile 还拥有代码内静态的 Header、Cookie、Guard 必需维度和绝对上限矩阵，数据库
不能放宽。

## 5. Approved Target Registry

### 5.1 双重权威边界

Platform 数据库保存当前 Target 行，但数据库不能单独决定任意 Nacos Service 可以被公网访问。Gateway 必须维护
受控 Approved Target Registry，并对 Snapshot 中每个 Target 精确验证：

```text
targetServiceCode + channel + discoveryServiceName
```

三元组必须与 Registry 完全一致。任一值不匹配都拒绝整份新 Snapshot。

Registry 还必须为每个 Target 固定允许的下游外部适配器 Path Root。动态 Route 的
`downstream_path_template` 必须落在该 Target 已批准的 Root 内，不能借一个合法 Service Name 暴露同一进程中的
内部管理、内部服务或 Actuator Endpoint。Path Root 是 Gateway 静态安全事实，不进入 Platform 数据库。

Registry 至少覆盖以下目标类别：

- `operations-console-bff -> OPERATIONS`；
- `client-console-bff -> CLIENT`；
- `partner-console-bff -> PARTNER`；
- Commerce Checkout 外部适配器 `-> CHECKOUT`；
- 各核心系统已批准 Client API 外部适配器 `-> CLIENT_API`；
- Payment Provider Callback 外部适配器 `-> PROVIDER_CALLBACK`。

Registry 不包含 Vault、数据库、Platform 内部管理 API、核心系统任意内部 API 或任意用户提供的服务名。
Platform 自己拥有的 Client Webhook 等外部能力可以通过其明确隔离的 Client API 外部适配器 Path Root 注册，但这
不允许 Gateway Admin、Authorization、Snapshot 或其他 Platform 非公共接口直接暴露。

### 5.2 Target 变更权限

普通 Operations 页面可以：

- 查询已批准 Target；
- 在没有 ACTIVE Route 引用冲突时启停 Target；
- 为 Route 选择当前 ACTIVE Target。

普通 Operations 页面不能：

- 创建 Target 身份三元组；
- 修改 `target_service_code`、`channel` 或 `discovery_service_name`；
- 修改 Approved Downstream Path Root；
- 把任意 Nacos Service、URL、Host、IP 或 Port 变成公网目标。

新增 Target 必须完成架构与安全评审，同时更新 Gateway Registry 和 Platform 受控 Migration/Seed。新增已批准 Target
下的普通 Route 不需要修改 Gateway Java。

## 6. 最小关系模型

### 6.1 模型规模

在 Platform 57 表直接前序上增加六张表：

```text
t_gateway_target
t_gateway_route
t_gateway_route_header
t_gateway_route_query_param
t_gateway_guard_rule
t_console_resource
```

当前已生成的物理快照是精确 63 表 / 822 字段 / 123 唯一索引 / 99 访问索引 / 107 本库关系 / 5 个 1:1 / 7
Diagram Groups，另有 31 条复合 FK、85 条具名 CHECK sidecar 契约和仍然恰好 36 个 `_hash` 字段。生成事实以
[物理决策](../../../platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md) 和模型自动门禁为准。

本文明确不增加：

- `t_gateway_*_ver`；
- Policy/Binding Version；
- Draft、Release、Release Item、Rollback、Gateway Ack；
- Snapshot、Snapshot Item、Snapshot Hash 或 Current Pointer；
- Tenant Guard Override、Guard Tier 或单租户配额表；
- API Scope Catalog；
- Route History 或专用 Idempotency 表；
- 新的数据库 `*_hash` 字段。

只有 `t_gateway_target`、`t_gateway_route` 和 `t_console_resource` 三个独立并发根保留普通 `version` 字段。普通
乐观锁 `version` 不是历史版本表。三个 Route 子表由 Route 聚合整体替换，不保存 `status`、`version` 或历史行。

### 6.2 `t_gateway_target`

最小字段：

| 字段 | 语义 |
|---|---|
| `target_id` | 本地主键 |
| `target_service_code` | 稳定、全局唯一、不可复用的 Target Code |
| `discovery_service_name` | Approved Registry 中的精确 Nacos Service Name |
| `channel` | 六类 Gateway Route Channel 之一 |
| `status` | `ACTIVE` 或 `DISABLED` |
| `version` | 普通乐观锁版本 |
| 审计字段 | 创建人、修改人、创建时间、修改时间 |

约束：

- `uk_gateway_target_code(target_service_code)`；
- 为 Route 的 Channel 一致性提供 `(target_id, channel)` 唯一键和复合外键目标；
- 不保存 URI、Scheme、Host、IP、Port、Query、Credential、Secret 或任意路由脚本；
- `target_service_code` 不因 Service Name 调整而被重用成另一种能力；
- `DISABLED` Target 是该 Target 下全部 Route 的运行时 Kill Switch：Route 行可继续保持 ACTIVE，但不会进入
  Snapshot；Target 再次启用前必须重新校验其全部 ACTIVE Route。

### 6.3 `t_gateway_route`

最小字段：

| 字段 | 语义 |
|---|---|
| `route_id` | 本地主键 |
| `route_code` | 稳定、全局唯一、不可复用的 Route Code |
| `channel` | 六类 Gateway Route Channel 之一 |
| `http_method` | 精确 HTTP Method |
| `external_path_template` | 公网 Path Template |
| `downstream_path_template` | 目标适配器 Path Template |
| `target_id` | 当前 Target 引用，与 `channel` 组成复合外键 |
| `security_profile_code` | 七个预编译 Profile 之一 |
| `authorization_mode` | 六个互斥 Mode 之一 |
| `permission_id` | `HUMAN_PERMISSION` 时必填，其余为空 |
| `required_scope_code` | `API_SCOPE` 时必填，其余为空 |
| `request_cookie_mode` | `DROP` 或 `REQUIRE_PROFILE_COOKIE` |
| `response_cookie_mode` | `NONE`、`ISSUE_ONLY`、`ISSUE_OR_CLEAR` 或 `CLEAR_ONLY` |
| `body_limit_bytes` | `0` 禁止 Body；正数不得超过 Gateway 硬上限 |
| `body_receive_timeout_millis` | 完整接收 Body 的总时限 |
| `response_timeout_millis` | 完整目标响应时限 |
| `status` | `ACTIVE` 或 `DISABLED` |
| `version` | 普通乐观锁版本 |
| 审计字段 | 创建人、修改人、创建时间、修改时间 |

关键约束：

- `uk_gateway_route_code(route_code)`；
- 精确值唯一键至少覆盖 `(http_method, external_path_template)`，且该冲突域跨 Channel；
- `(target_id, channel)` 复合外键指向同 Channel Target；
- `HUMAN_PERMISSION` 只能有非空 `permission_id`；
- `API_SCOPE` 只能有非空 `required_scope_code`；
- 其他 Mode 的 `permission_id` 和 `required_scope_code` 必须同时为空；
- Security Profile、Authorization Mode、Channel 和 Cookie Mode 必须满足静态兼容矩阵；
- `external_path_template` 与 `downstream_path_template` 使用完全相同的变量集合；
- Route Code 是日志、审计、Guard Key 和可信下游上下文，不因 Path 修改而重用成无关能力；
- 不保存 Path Hash、Path Shape、配置 Hash、Policy Version、Release ID 或 Runtime Route Ref。

精确唯一索引不能识别 `/clients/{id}` 与 `/clients/{clientId}` 的模板等价冲突。Platform 使用串行化配置事务读取
完整当前 Catalog、规范化变量名并验证候选；Gateway 编译器再次验证。该低频全局校验不需要额外 Path Shape 字段、
Hash 字段或全局锁表。

### 6.4 `t_gateway_route_header`

每行声明一条完整 Header 名称及用途：

| 字段 | 语义 |
|---|---|
| `route_header_id` | 本地主键 |
| `route_id` | Route 外键 |
| `header_name` | 规范化、完整、大小写不敏感的 HTTP Header Name |
| `header_use` | `CORS_REQUEST_ALLOWED` 或 `FORWARD_REQUEST` |

约束：

- `uk_gateway_route_header(route_id, header_name, header_use)`；
- 同一 Header 同时承担两个用途时保存两行；
- 禁止前缀、通配、正则、Hop-by-hop Header、Gateway 保留 Header 和身份 Header；
- Gateway 生成的 Request ID、Route Code、Actor、Session、Tenant 和 Credential Header 不由数据库开放或改名；
- Gateway 按大小写不敏感的完整名称消费并删除当前有限代理 Header 清单：`Forwarded`、`X-Forwarded-For`、
  `X-Forwarded-Host`、`X-Forwarded-Port`、`X-Forwarded-Prefix`、`X-Forwarded-Proto`、
  `X-Forwarded-Server`、`X-Forwarded-Ssl` 与 `X-Real-IP`；这些 Header 在转发前删除，不得由数据库允许转发、
  改名或扩展为前缀匹配；
- 子行随 Route 聚合整体替换，不保存普通版本或状态。

### 6.5 `t_gateway_route_query_param`

最小字段：

| 字段 | 语义 |
|---|---|
| `route_query_param_id` | 本地主键 |
| `route_id` | Route 外键 |
| `parameter_name` | 允许的完整 Query Parameter Name |

`uk_gateway_route_query(route_id, parameter_name)` 保证唯一。未声明的 Query Name 由 Gateway 返回 `400`，不调用
目标服务。值的格式和业务语义仍由目标 BFF/核心适配器校验。

### 6.6 `t_gateway_guard_rule`

每行表示一个 Route 的一条当前 Guard Rule：

| 字段 | 语义 |
|---|---|
| `guard_rule_id` | 本地主键 |
| `route_id` | Route 外键 |
| `rule_type` | `RATE_LIMIT` 或 `CONCURRENCY` |
| `dimension` | `SHARED_ROUTE`、`CLIENT_IP`、`AUTHENTICATED_PRINCIPAL` 或 `TENANT` |
| `capacity` | RATE_LIMIT 必填 |
| `refill_tokens` | RATE_LIMIT 必填 |
| `refill_period_millis` | RATE_LIMIT 必填 |
| `concurrency_limit` | CONCURRENCY 必填 |

约束：

- `uk_gateway_guard_rule(route_id, rule_type, dimension)`；
- RATE_LIMIT 三元组非空且 `concurrency_limit` 为空；CONCURRENCY 相反；
- 所有数值为正并不超过 Gateway 代码/部署硬上限；
- `AUTHENTICATED_PRINCIPAL`、`TENANT` 只有在 Security Profile 能在 Guard 阶段产生可信值时才允许；
- `PUBLIC` 和 Provider Callback 等没有 Gateway 可信主体的 Route 不能配置 Principal/Tenant Rule；
- 每个 ACTIVE Route 至少满足对应 Profile 的静态 Guard 基线；最低公共基线为 Shared Route Rate +
  Shared Route Concurrency，不能使用空子表绕过边缘保护；
- 子行随 Route 聚合整体替换，不保存状态、版本、Tier、Policy Revision 或 Hash。

### 6.7 `t_console_resource`

Console Resource 只覆盖三个 Console：

```text
OPERATIONS
CLIENT
PARTNER
```

最小字段：

| 字段 | 语义 |
|---|---|
| `resource_id` | 本地主键 |
| `resource_code` | 稳定、全局唯一的界面资源代码 |
| `channel` | `OPERATIONS`、`CLIENT` 或 `PARTNER` |
| `resource_type` | `MENU` 或 `BUTTON` |
| `parent_resource_id` | 可空自关联，父项必须同 Channel |
| `display_name` | 展示名称 |
| `frontend_route` | 可空的当前 Console 内部相对路径 |
| `component_key` | 可空的已部署前端组件注册 Key |
| `permission_id` | 控制当前可见性的 Permission 外键 |
| `sort_order` | 同级顺序 |
| `status` | `ACTIVE` 或 `DISABLED` |
| `version` | 普通乐观锁版本 |

Checkout、Client API 和 Provider Callback 不创建 Console Resource。`frontend_route` 不能是外部 URL，
`component_key` 不能是远程脚本或动态组件代码。

Menu、Button 和 API Route 不建立直接一对一关系。Console Resource 与 HUMAN_PERMISSION Route 分别引用同一稳定
Permission，允许一个按钮调用多个 API，也允许一个 API 被多个页面复用。隐藏按钮只改善交互，不替代 BFF 授权。

### 6.8 复用现有授权与审计事实

本文复用：

```text
t_permission
t_role_permission
t_role
t_tenant_member_role
t_tenant_member
t_access_tenant
t_api_credential
t_api_credential_scope
t_audit_event
t_audit_event_attr
```

`t_gateway_route.permission_id` 直接外键到 `t_permission`。创建或激活 HUMAN_PERMISSION Route 时，Permission 必须
存在且当前 ACTIVE。Permission 后续被停用时不让整份 Gateway Snapshot 失效；PLT-I02 对该 Route 当前决策一律
拒绝，Console Navigation 同时隐藏对应资源。这样 Permission Revocation 可以立即 Fail-Closed，而不要求先修改
Route。

`t_tenant_member_role` 是原 `t_member_role` 的明确化命名，表示 Tenant Member 获得 Role，不表示全局 Account
直接获得 Role。`t_role.scope_key` 继续作为由 `tenant_scope + tenant_id` 推导的非空规范键：Platform Role 固定为
`PLATFORM`，Tenant Role 固定为十进制 `tenant_id`。它与 `t_tenant_member_role.role_scope_key` 的复合外键及
`role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)` CHECK 共同阻止跨 Tenant 错授角色；该值不接受
外部调用方输入。

`t_api_credential_scope` 是“某 Credential 当前获得什么”的 Grant Fact，不是 Scope Definition Catalog。因此
`required_scope_code` 不外键到某一条 Credential Grant，也不为此增加 `t_api_scope_catalog`。PLT-I03 返回当前有效
Grant，Gateway 按稳定 Scope Code 匹配。多个 Route 可以共享同一 Scope，一个 Credential Grant 也不因 Route
暂时停用而被物理删除。

API Credential Scope 管理只能授予至少被一条现有 `API_SCOPE` Route 引用的 `scope_code`。需要先配置后授权时，
Operations 先创建 `DISABLED` Route，再由允许的 Credential 管理流程授予 Scope，最后激活 Route；这仍是当前状态
模型，不是 Draft/Release。Route 改绑后不再被任何 Route 使用的旧 Grant 可以保留到正常撤销，但不会授权任何当前
Route。

管理命令继续写现有 Audit Event：

- `resource_type` 区分 Gateway Target、Gateway Route、Console Resource；
- `resource_id` 保存稳定 Code；
- `t_audit_event_attr` 保存 Reason、Changed Field 和安全的 Before/After Scalar；
- 复用现有 `source_event_key` 唯一性和现有 `payload_hash` 检测同一 Idempotency Key 的重放或异载荷；
- 不增加 Route History、Before Hash、After Hash 或专用 Idempotency 表。

同一 Idempotency Key 与同一规范请求重放时返回原命令语义；同 Key 不同载荷返回 `409`。Create 的稳定 Code 唯一键
和 Update 的 Expected Row Version 继续提供最终数据库防线。

## 7. Operations 管理 API

### 7.1 调用链与权限

唯一写入链路：

```text
Operations Web
  -> API Gateway
    -> operations-console-bff
      -> Platform Support internal management API
```

Operations BFF 先调用 PLT-I02 校验 `GATEWAY_CONFIG_READ`、`GATEWAY_CONFIG_MANAGE` 或
`CONSOLE_RESOURCE_MANAGE`。Platform 管理 Processor 必须基于 BFF 传入的 Gateway 可信 Actor、Session、Channel、
Route 和 Tenant Context 再次执行当前权限校验，不能只相信页面隐藏、BFF 路径存在或客户端声明。

Client/Partner BFF、浏览器、API Credential、Checkout 和 Provider Callback 都不能调用管理写接口。

所有 POST/PUT/PATCH 管理命令都必须携带 Idempotency Key 和 Reason；所有 Update/Status 命令还必须携带
Expected Row Version。该要求同样适用于 Target Status 和 Console Resource，不只适用于 Route。

以下 Path 均为 `platform-support-api` 的能力内路径，实际服务地址由 Platform Support 的
`/platform-support` Context Path 统一形成 `{context-path}/{capability-path}`；调用范围由网络和 Gateway Catalog 边界表达，不增加额外路径命名空间。

### 7.2 Target API

普通管理 API 只提供查询与状态切换：

```http
GET   /gateway-admin/v1/target-services
GET   /gateway-admin/v1/target-services/{targetServiceCode}
PATCH /gateway-admin/v1/target-services/{targetServiceCode}/status
```

不提供普通 `POST Target` 或修改身份三元组的 `PUT Target`。新增 Target 由受控 Migration/Seed 完成。

停用 Target 是一个原子运行时 Kill Switch：下一份 Snapshot 同时移除该 Target 及其全部 Route，不要求逐条更新
Route。启用 Target 时，Platform 必须把该 Target 下所有 ACTIVE Route 放回完整全局候选并重新验证；任一冲突使
启用命令返回 `409`。

### 7.3 Route API

```http
GET   /gateway-admin/v1/routes
GET   /gateway-admin/v1/routes/{routeCode}
POST  /gateway-admin/v1/routes
PUT   /gateway-admin/v1/routes/{routeCode}
PATCH /gateway-admin/v1/routes/{routeCode}/status
```

List 至少支持 `channel`、`status` 和 `targetServiceCode` 过滤。不存在物理 DELETE；稳定 Route Code 不被复用。

POST/PUT 请求必须一次提交：

- Route Root；
- 完整 Header 列表；
- 完整 Query Parameter 列表；
- 完整 Guard Rule 列表；
- Idempotency Key；
- Reason；
- Update 时的 Expected Row Version。

Platform 在一个本地串行化事务中：

1. 重检管理权限；
2. 检查 Idempotency Key；
3. 锁定目标 Root 或唯一 Code；
4. 读取完整当前 Target/Route Catalog；
5. 应用本次候选变更；
6. 验证全局 Path 冲突、Target Registry、Channel、Profile/Mode、Permission/Scope、Header、Query、Cookie、Body、
   Timeout 和 Guard；
7. 写 Root 并整体替换三个 Route 子集合；
8. 追加 Audit Event；
9. Commit。

任何一步失败都回滚，不留下半条 Route。Commit 成功即表示数据库当前配置已经变化，不存在第二次 Publish。
Response 返回稳定 Code 和新 Row Version，但不声称某个 Gateway Instance 已经刷新。

### 7.4 Console Resource API

```http
GET   /console-admin/v1/resources?channel={OPERATIONS|CLIENT|PARTNER}
GET   /console-admin/v1/resources/{resourceCode}
POST  /console-admin/v1/resources
PUT   /console-admin/v1/resources/{resourceCode}
PATCH /console-admin/v1/resources/{resourceCode}/status
```

Role、Permission、Member Role、Role Permission 和 Tenant Member 管理继续使用 Platform IAM 内部能力，不复制进
Gateway Admin API。

### 7.5 管理 API 稳定响应

| 场景 | 响应 |
|---|---|
| 查询或更新成功 | `200` |
| 创建成功 | `201` |
| 请求字段或组合非法 | `400` |
| 用户/服务身份无效 | `401` |
| 当前用户无管理权限 | `403` |
| Code 不存在 | `404` |
| Expected Version、全局 Route、引用或 Idempotency 冲突 | `409` |
| 必要 IAM/Database 依赖失败 | `503` 或稳定 `5xx`，Fail-Closed |

错误 Body 使用稳定 Error Code，不回传 SQL、Nacos Inventory、内部 Exception、Secret 或完整安全上下文。

## 8. 全局 Runtime Snapshot API

### 8.1 Endpoint

```http
GET /platform-support/gateway-config/v1/public-snapshot
```

调用方仅为 Gateway 后台刷新任务。Endpoint：

- 只在部署限制的 Gateway/Platform 私有 HTTP 网络中可达，不使用应用证书或 Caller Header 认证；
- 不在公共 Route Catalog 中注册；
- 不经任一 BFF；
- 不接受浏览器 Access Token 或 API Credential；
- 没有 Channel Query，一次返回六类入口的完整公共目录。

### 8.2 Body

示意：

```json
{
  "schemaVersion": 1,
  "targets": [
    {
      "targetServiceCode": "operations-console-bff",
      "channel": "OPERATIONS",
      "discoveryServiceName": "operations-console-bff"
    }
  ],
  "routes": [
    {
      "routeCode": "operations-auth-login",
      "channel": "OPERATIONS",
      "httpMethod": "POST",
      "externalPathTemplate": "/api/operations/auth/v1/login",
      "downstreamPathTemplate": "/auth/v1/login",
      "targetServiceCode": "operations-console-bff",
      "securityProfileCode": "PUBLIC",
      "authorizationMode": "PUBLIC",
      "permissionCode": null,
      "requiredScopeCode": null,
      "requestCookieMode": "DROP",
      "responseCookieMode": "ISSUE_ONLY",
      "corsRequestHeaderNames": ["Content-Type"],
      "forwardedRequestHeaderNames": ["Content-Type"],
      "queryParameterNames": [],
      "bodyLimitBytes": 16384,
      "bodyReceiveTimeoutMillis": 5000,
      "responseTimeoutMillis": 10000,
      "guardRules": [
        {
          "ruleType": "RATE_LIMIT",
          "dimension": "SHARED_ROUTE",
          "capacity": 100,
          "refillTokens": 100,
          "refillPeriodMillis": 60000,
          "concurrencyLimit": null
        },
        {
          "ruleType": "CONCURRENCY",
          "dimension": "SHARED_ROUTE",
          "capacity": null,
          "refillTokens": null,
          "refillPeriodMillis": null,
          "concurrencyLimit": 20
        }
      ]
    }
  ]
}
```

最终 DTO 由后续 `platform-support-api` 轻量模块提供，Gateway 与 Platform 不长期维护两份手写生产 DTO。

### 8.3 Snapshot 内容边界

Snapshot 只包含当前 ACTIVE Target，以及 `route.status=ACTIVE + target.status=ACTIVE` 的 Route 和当前子配置。
Target 被停用时，其 Route 行仍可保持 ACTIVE，但不进入运行时投影。Snapshot 不包含：

- DISABLED 行；
- Account、Session、Tenant Member、Role、Role Permission；
- API Credential、PHC Verifier 或 Credential Scope Grant；
- Console Resource；
- Audit、Reason、Actor、Row Version 或管理 Idempotency Key；
- Redis 状态；
- JWT/JWKS/Cookie/Provider Secret 等静态安全实现。

Snapshot Body 不返回 `generatedAt` 或 `contentHash`：两者都不是 Gateway 执行配置所需事实，也不能成为数据库外的
第二套版本或一致性权威。

### 8.4 一致性读取与全量返回

- Platform 在一个只读、Repeatable Read 事务中读取当前有效 Target、Route 和全部子配置；
- 事务关闭后再完整校验候选 Catalog，并组装轻量 `platform-support-api` typed DTO；
- 字段、Target、Route、Header、Query 和 Guard 使用固定输出顺序，保证结果可读、可测试，但该顺序不派生版本或 Hash；
- 每次成功调用都返回完整 DTO，不实现 ETag、`If-None-Match` 或 `304` 条件缓存；
- Snapshot 不持久化，也不是 Secret、业务版本、Release ID 或 Redis Policy Version。

### 8.5 响应

| 场景 | 响应 |
|---|---|
| 完整当前 Snapshot 存在 | `200` + 完整 typed Body |
| 调用方额外发送 `If-None-Match` | 不作为契约参数处理，仍返回 `200` + 完整 typed Body |
| 非部署私网来源访问 | 由网络边界阻断，不形成 PLT-I20 应用响应 |
| 请求契约非法 | `400` |
| 数据库尚未初始化 Target Catalog，或当前数据无法形成完整有效 Snapshot | `500 INTERNAL_ERROR`，不得返回部分列表 |
| Platform 暂时不可用 | 连接失败或 `5xx`；Gateway 保留 Last Valid |

受控初始化已经存在至少一条 Approved Target 行后，`routes=[]` 是合法完整 Snapshot。它用于停用全部公网 Route：
Gateway 必须原子切换到空 Catalog、保持 Ready，并对全部公网请求返回 `404`；不能因列表为空而保留旧 Route。

## 9. Gateway Snapshot 消费

### 9.1 刷新与切换

1. Gateway 启动后立即请求完整 Snapshot；
2. 没有有效 Snapshot 前 Readiness Down，Liveness Up；
3. 首次加载成功后约每五秒单飞拉取完整 Snapshot；
4. 前一轮未完成时不启动并发刷新；
5. 每个 `200` Body 都先受限读取、反序列化、完整验证和编译，再原子替换当前不可变 Snapshot；
6. 连接失败、超时、`5xx`、未知 Schema Version 或非法 `200` 保留 Last Valid；
7. Gateway 不从本地文件、Redis、Nacos Config、Java Route 常量或部分缓存恢复动态 Route；
8. Platform 恢复后同一实例继续刷新；
9. 多实例各自轮询并在约五秒窗口内最终收敛，不增加 Fleet Activation Barrier。

刷新使用非阻塞 HTTP Client，拥有独立连接、响应时限、最大 Payload 和反序列化深度边界，不占用业务请求线程。

### 9.2 双重校验

Platform 管理 API 与 Gateway 编译器都校验可由 Snapshot 判断的事实：

- Code、Service Name、Method、Header、Query 和 Path 的长度与字符集；
- Approved Target Registry 精确三元组；
- Downstream Path 位于该 Target 静态批准的外部适配器 Path Root；
- 全局 Method + 可匹配 Path 唯一；
- Snapshot 中的 Target ACTIVE，Route 与 Target Channel 一致；
- Channel/Profile/Mode/Cookie 兼容；
- Permission/Scope XOR 形状；
- Header 不包含保留或 Hop-by-hop Name；
- Path 不包含通配、正则、Dot Segment、重复斜杠、反斜杠、编码斜杠或非法 Percent Encoding；
- 外部与下游 Path 变量集合一致；
- Body、Timeout 和 Guard 数值不超过硬上限；
- Guard Type + Dimension 唯一且满足 Profile 最低基线。

只有 Platform 能解析 `permission_id -> permission_code` 并读取 Permission 当前状态。Route 创建/激活时必须引用
ACTIVE Permission；之后 Permission 被停用时，Snapshot 仍可携带稳定 Permission Code，PLT-I02 按当前状态拒绝。
Gateway 只验证 Permission Code 的格式和 XOR 形状，不加载完整 IAM Catalog，也不能声称已完成
HUMAN_PERMISSION 决策。

### 9.3 单请求一致性

Gateway 在请求开始时捕获当前 Snapshot 并解析一次 Route，把不可变 Route 对象保存到当前 Exchange。后续 Filter
不得重新读取全局 Snapshot。即使后台刷新发生，该请求的 Method、Path、Header、Query、Cookie、Profile、Guard、
Target 和 Timeout 都来自同一对象。

### 9.4 Route 匹配

- HTTP Method 精确匹配；
- Path 大小写敏感；
- 只允许固定 Segment 和单 Segment `{variable}`；
- 固定 Path 优先于变量 Path；
- `/clients/{id}` 与 `/clients/{clientId}` 结构等价，不能同时存在；
- 两条变量模板可能匹配同一 Path 时拒绝整份 Snapshot，不使用隐式优先级消歧；
- `HEAD` 不自动等于 `GET`；需要时单独配置；
- `OPTIONS` 只由 Gateway 为合法 CORS Preflight 自动处理，不转发成任意下游 Route；
- 未匹配 Method + Path 返回本地 `404`，零目标调用；
- 未声明 Query Name 返回 `400`，零目标调用；
- Path Variable 单 Segment 解码校验后重新编码到下游模板，不对完整 Path 二次解码。

## 10. 公网请求处理顺序

```text
Transport / Request Line / Header 硬边界
  -> 生成或验证 Request ID，并删除所有外部伪造可信 Header
  -> 捕获当前 Snapshot，匹配 Method + Path
  -> Query、CORS、Forward Header、Cookie 输入策略
  -> SHARED_ROUTE / CLIENT_IP Rate Limit
  -> SHARED_ROUTE / CLIENT_IP Concurrency（立即获取，不排队）
  -> 执行预编译 Security Profile
  -> 若 Profile 产生可信身份：AUTHENTICATED_PRINCIPAL / TENANT Rate + Concurrency
  -> API_SCOPE Route 校验 PLT-I03 返回的 requiredScopeCode
  -> Body Size 与完整接收总时限
  -> 注入 Route Code、Request ID 和最小可信身份上下文
  -> Target Code -> Approved Discovery Service -> Nacos 健康实例
  -> 下游 Path 替换和完整响应时限
  -> 成功、错误、取消路径幂等释放全部 Concurrency Lease
```

Rate Token 一旦消费不因后续 Concurrency、JWT、Scope、Body 或下游失败而退款。Concurrency 不等待、不排队；拒绝
立即返回 `429`。Redis 不可用 Fail-Closed，不能绕过 Guard。

## 11. 多租户 Guard 与 Redis 状态

### 11.1 维度

- `SHARED_ROUTE`：该 Route 所有请求共享；
- `CLIENT_IP`：按 Gateway 静态可信代理规则得到的规范客户端 IP；
- `AUTHENTICATED_PRINCIPAL`：人工 Account、API Credential 或 Profile 明确产生的其他可信主体；
- `TENANT`：仅当 Profile 已产生可信 `tenant_id` 时使用。

`TENANT` Rule 的配置参数仍只有一份，但 Redis Key 包含可信 Tenant ID，因此各 Tenant 拥有独立 Bucket。本文不允许
某 Tenant 单独覆盖 Capacity、Refill、Period 或 Concurrency Limit。未来确有商业套餐或合约配额需求时，必须单独
设计 Tenant Quota Authority、变更权限、缓存和失败语义，不能在当前表中加入 nullable override 列。

### 11.2 动态数值变化

Redis 只保存算法运行状态、计数器和 Lease，不保存当前配置版本：

- Rate Capacity 降低时把可用 Token 收缩到新上限；
- Capacity 提高时保留当前 Token，按新规则自然补充，不制造立即满桶突发；
- Concurrency Limit 降低时保留已有 Lease，在活跃数降到新上限前拒绝新请求；
- Limit 提高时立即允许新增容量；
- Route 停用后不扫描 Redis，旧 Key 由 TTL 清理；
- 不增加 Policy Version、State Epoch、双 Key、双写或滚动迁移；
- 多实例在刷新窗口内短暂使用新旧参数是批准的最终一致行为。

现有 Redis Lua、Server Time、Lease/TTL、Ambiguous Transport Outcome 恢复和 Guard Key 安全边界继续由 Gateway
hardening 权威控制。

## 12. 身份、权限与资源授权

### 12.1 Human Console

对于 `HUMAN_PERMISSION`：

1. Gateway 按 Channel Profile 验证最小 JWT Claim；
2. Gateway 注入稳定 Route Code 和最小可信 Actor/Session/Tenant Context；
3. BFF 使用 Route Code 调 PLT-I02；
4. Platform 按当前 Account、Session、Tenant、Member、Role、Role Permission、Permission 和 Route 绑定返回决策；
5. Platform 不可用、权限失效或事实歧义时 Fail-Closed；
6. 核心系统继续校验资源归属、状态和业务规则。

JWT 不携带完整 Role/Permission Catalog，Gateway 不调用 PLT-I02，也不注入“已完成 Permission 授权”Header。

对于 `HUMAN_AUTHENTICATED`，Gateway 只证明最小 JWT 有效；Logout、Tenant 切换或当前身份等下游能力仍校验当前
Session/Account/Tenant 语义，但不要求伪造一个业务 Permission。

### 12.2 Client Public API

1. Gateway 从静态协议位置提取 API Credential；
2. 调 PLT-I03 校验 PHC Verifier；
3. PLT-I03 必须 JOIN 同一 ACTIVE CLIENT Tenant，并只返回该 Credential 当前 effective ACTIVE Scope；
4. Gateway 要求结果包含 Route 的 `required_scope_code`；
5. Gateway 注入可信 `tenant_id`、`tenant_type=CLIENT`、`owner_ref_id`、`credential_id`、Route Code 和 Request ID；
6. 外部调用方不能覆盖这些 Header，也不能从 Key Prefix 推断 Owner；
7. 目标核心系统继续校验资源属于可信 Client/Tenant，以及命令状态、幂等和业务规则。

PLT-I03 不可用、Credential/Tenant/Scope 无效或结果歧义返回 `503`/认证失败并 Fail-Closed，不能降级为匿名调用。

### 12.3 Hosted Checkout

Gateway 执行 Checkout Profile 的静态入口协议与 Guard；Commerce 校验 Checkout Session、Order、Client、Consumer
Action 和当前业务状态。Gateway/Platform 不持久化 Checkout Resource，不从 Path ID 推断授权，也不允许 Checkout
浏览器直接调用 Payment、Risk、Finance、CCT 或 Vault。

### 12.4 Provider Callback

Gateway 执行 Callback Profile 的静态入口协议、Body 上限、Header 边界和 Guard。Payment 外部适配器校验 Provider
身份、签名、时间窗、防重放、目标 Provider Account/MID 和业务资源。数据库 Route 配置不能下发 Provider Secret、
签名算法或验证脚本。

### 12.5 Console Navigation

```http
POST /authorization/v1/console-navigation
```

Operations、Client、Partner BFF 分别调用。Platform 按当前身份、Tenant 和 Permission 返回当前 Channel 已过滤的
MENU/BUTTON 树；浏览器不直接调用 Platform，也不自行从 Role 推导菜单。即使按钮可见，BFF 对每次
HUMAN_PERMISSION API 请求仍必须调用 PLT-I02。

## 13. 静态安全硬边界

以下事实不能由数据库放宽或改名：

- Nginx 的公网 TLS Protocol/Cipher、证书、只向 Gateway 私网转发以及单值 `X-Real-IP` 覆盖契约；
- Gateway 私有 HTTP Listener、只接受 Nginx 私网流量、可信 Client IP 单次解析和转发前代理 Header 删除；
- JWT Algorithm、Issuer、Audience、JWKS Trust、Key Refresh 和 Claim Matrix；
- 三个 Console 的精确 CORS Origin；
- Refresh Cookie 名称、Secure、HttpOnly、SameSite、Domain 和 Path；
- Provider Callback 签名算法、Secret 和防重放实现；
- Gateway 保留 Header、Hop-by-hop Header、身份 Header，以及转发前删除全部已消费代理 Header 的规则；
- Transport、Request Line、Header Count/Size、Body、Timeout、Rate 和 Concurrency 绝对上限；
- Snapshot Endpoint 的私网边界和刷新间隔；
- Approved Target Registry；
- Redis Guard Lua、Lease Duration、State TTL、Failure-Closed 和 Ambiguous Outcome 恢复。

当前人工 Refresh Cookie 保持：

| Channel | Cookie | Path |
|---|---|---|
| OPERATIONS | `__Secure-operations-refresh` | `/api/operations/auth/v1` |
| CLIENT | `__Secure-client-refresh` | `/api/client/auth/v1` |
| PARTNER | `__Secure-partner-refresh` | `/api/partner/auth/v1` |

数据库只选择 `request_cookie_mode` 和 `response_cookie_mode`。对 Checkout、Client API、Provider Callback 等不使用
人工 Refresh Cookie 的 Channel，静态兼容矩阵必须拒绝 ISSUE/REQUIRE 模式。

Gateway 接收外部请求时按大小写不敏感的完整名称删除所有可信 Header，再按静态协议重建。至少包括现有：

```text
X-Request-Id
X-Gateway-Route-Code
X-Gateway-Actor-Subject
X-Gateway-Session-Id
X-Gateway-Actor-Type
X-Gateway-Channel
```

Client API 实施还需要静态定义最小 Tenant/Credential Header，例如可信 Tenant ID、Tenant Type、Owner Ref 和
Credential ID；确切名称必须在 Gateway 实施设计与测试中冻结，不能由 `t_gateway_route_header` 下发。

## 14. 自举、切换与恢复

### 14.1 初始 Seed

动态 Route 自身不能创建第一条管理链路。切换前受控 Migration/Seed 至少建立：

1. 已部署的 Approved Target 行；
2. 与现有 Operations 静态 Catalog 完全等价的 Login、Refresh、Logout Route；
3. Operations 当前身份、强制修改 Bootstrap 密码、创建首个 Email Admin 和 Navigation 所需 Route；
4. Route、Target 状态、Console Resource、Role 和 Permission 管理所需 Route；
5. `GATEWAY_CONFIG_READ`、`GATEWAY_CONFIG_MANAGE`、`CONSOLE_RESOURCE_MANAGE` 等 Permission；
6. 首个可管理配置的内部管理员 Role/Permission/Member 授权；
7. 每条 ACTIVE Route 所需 Header、Query、Cookie、Body、Timeout 和 Guard 完整子配置。

Client、Partner、Checkout、Client API 和 Provider Callback 只在对应外部适配器、契约、安全实现和自动化测试已
完成后创建 ACTIVE Route，不创建指向尚未部署能力的占位公网 Route。

### 14.2 切换

- 先部署 Platform 表、Seed、管理能力和 Snapshot API；
- 使用契约测试证明动态 Snapshot 与当前 Operations 三条静态 Route 的 Header/Cookie/Guard/Timeout 行为等价；
- 新 Gateway 版本每个实例只使用动态 Snapshot，不同时合并静态和动态 Route；
- 新实例首次 Snapshot 成功前不 Ready；
- 完成动态切换后删除运行时静态 Route Fallback，避免数据库停用 Route 时隐藏后门仍可访问。

### 14.3 Break-glass

普通页面误停用管理链路时，Gateway 不保留隐藏静态管理后门。恢复只能使用：

- 已审批的 Break-glass 数据库账号；
- 精确、显式、单事务 DML；
- 数据库账号和 SQL 审计；
- 修复后由正常 Snapshot 轮询恢复。

Break-glass 不是日常发布方式，不伪造应用 Actor，也不增加 Rollback 表。正常恢复旧值的方式是 Operations 重新提交
一次当前配置修改并产生新的 Audit Event。

## 15. 失败语义

### 15.1 配置链路

| 场景 | 结果 |
|---|---|
| Gateway 冷启动且 Platform 不可达 | Readiness Down、Liveness Up，不接收业务流量 |
| Gateway 已有 Snapshot，Platform 暂时不可达 | 继续 Last Valid 并告警 |
| 新 Snapshot 任意一项非法 | 拒绝整份新 Snapshot，保留 Last Valid |
| Approved Target Registry 不匹配 | 拒绝整份新 Snapshot |
| Snapshot Body 超限或 Schema 未知 | 拒绝整份新 Snapshot |
| Platform 当前数据无法形成完整 Catalog | Snapshot API `500 INTERNAL_ERROR`，不返回部分列表 |
| 已初始化 Catalog 的当前有效 Route 为空 | 返回合法空 Snapshot；Gateway 切换后全部请求 `404` |
| 管理写入候选冲突 | 整个事务回滚并返回 `409`/`400` |

非法 Snapshot 不影响已经捕获旧 Snapshot 的在途请求，也不能部分替换 Target、Route 或 Guard。

### 15.2 公网请求

| 场景 | 结果 |
|---|---|
| 未配置 Method + Path | Gateway `404`，零目标调用 |
| 非法 Path、未知 Query 或非法 Header | Gateway `400` |
| Request Line / Header 超过硬上限 | Gateway `414` / `431` |
| Body 超过 Route 上限 | Gateway `413` |
| Body 总接收时限到期 | Gateway `408` |
| JWT/API Credential 缺失或无效 | Gateway `401`，依赖不可用时按稳定分类 `503` |
| Client API 缺少 required Scope | Gateway `403` |
| BFF 当前 Permission 拒绝 | BFF `403` |
| PLT-I02 不可用 | BFF `503`，不伪装为无权限 |
| Rate/Concurrency 拒绝 | Gateway `429` |
| Redis 不可用 | Gateway `503`，Fail-Closed |
| Nacos 无可用目标实例 | Gateway `503` |
| 目标内部 HTTP 协议/无效响应 | Gateway `502` |
| 目标完整响应超时 | Gateway `504` |
| Checkout/Provider/Core 资源授权拒绝 | 由目标适配器返回稳定业务/授权错误 |

## 16. 可观测性与安全审计

Gateway 至少公开：

- 是否存在有效 Snapshot；
- 最近成功加载时间、距最近成功时长和连续失败次数；
- 最近刷新结果和稳定失败原因；
- 当前 Target/Route 总数及按 Channel 数量；
- Route Code/Channel/Profile 维度的请求、拒绝和上游延迟；
- Shared/IP/Principal/Tenant 各 Guard 维度的 Rate/Concurrency 拒绝；
- Last Valid 使用状态；
- `400/401/403/404/408/413/414/429/431/502/503/504` 分类计数。

日志和指标不能记录 Access Token、Refresh Cookie、API Credential、Provider Signature、请求体、完整 Query Value、
完整授权上下文或敏感 Header。

Platform 至少审计：

- Actor、Session、Channel、Request/Trace ID；
- Idempotency Key 的安全引用；
- Target/Route/Console Resource 稳定 Code；
- Action、Reason、结果和安全的字段级 Before/After；
- 权限拒绝、并发冲突和非法候选原因。

受控 DML 依赖数据库账号审计，不伪造成应用层操作者。

## 17. 实施分工

### 17.1 Platform Support 后续任务

- 在 PDManer Manifest 中加入六表及关系、索引、CHECK Sidecar；
- 更新 Platform 生成模型、Oracle、场景和权威数据库文档；
- 提供 `platform-support-api` Snapshot/Management DTO；
- 实现 Target 查询/状态、Route 聚合管理、Console Resource 管理；
- 扩充 PLT-I02、Console Navigation，并保持 PLT-I03 权威；
- 保持已实现的一致性 Snapshot 读取、完整校验和 typed DTO 组装，不增加 HTTP 条件缓存；
- 在同一事务写配置和现有 Audit Event；
- 提供受控 Seed/Migration，但不在设计阶段创建实际数据库。

### 17.2 Gateway 后续任务

- 用动态 Snapshot 替换运行时静态 Route Catalog；
- 保留现有 hardening 的 JWT/JWKS 校验、Header/Cookie/Guard/Timeout/Access Log 语义，并采用 2026-09-02 设计的 Nginx TLS、可信 `X-Real-IP` 和私有 HTTP 传输；
- 实现 Approved Target Registry；
- 实现 Snapshot Refresher、Validator、Compiler 和 Atomic Reference；
- 将 Route/Header/Query/Cookie/Body/Timeout/Guard 读取切换到捕获的不可变 Route；
- 增加 Client/Partner JWT、Client API Credential、Checkout 和 Provider Callback Profile；
- 增加可信 Tenant/Credential 下游协议；
- 保证所有成功、错误和取消路径释放 Concurrency Lease；
- 不把 PLT-I02 或核心业务授权搬入 Gateway。

### 17.3 BFF 与核心系统后续任务

- 三个 BFF 使用可信 Route Code 调 PLT-I02，并使用 Console Navigation；
- Client/Partner BFF 保持 Tenant 隔离；
- Commerce Checkout Adapter 保持 Checkout Resource 授权；
- 各 Client API Adapter 校验可信 Client/Tenant 资源归属；
- Payment Callback Adapter 保持签名、防重放和资源授权；
- 内部服务调用继续绕过 Gateway。

## 18. 验证要求

### 18.1 Platform 自动化验证

- 六表数量、字段、唯一键、访问索引、复合外键和 CHECK 精确 Oracle；
- 不存在 `*_ver`、Release、Snapshot、Ack、Tenant Override 或新增 `*_hash`；
- Route Authorization XOR 和 Channel/Profile/Mode 矩阵；
- Target/Channel 复合外键和 ACTIVE 引用规则；
- Route 子集合整体替换与事务回滚；
- 串行并发新增等价变量 Path 时只有一个成功；
- Idempotency 同 Key 同载荷重放与异载荷冲突；
- Snapshot 稳定顺序、完整 typed DTO、`200`/`500 INTERNAL_ERROR`，并确认不存在 ETag/304 分支；
- Invalid DML 状态不能返回部分 Snapshot；
- PLT-I02、PLT-I03、Navigation 和管理权限 Fail-Closed；
- Audit Event 与配置同事务。

### 18.2 Gateway 自动化验证

- 冷启动无 Snapshot 不 Ready；
- 200 全量响应、单飞刷新、连接失败、5xx、非法 Body 和未知 Schema；
- Last Valid、原子切换和在途请求 Snapshot 固定；
- 六 Channel 的 Profile/Mode/Target 兼容矩阵；
- Approved Target Registry 精确拒绝；
- 全局 Path 冲突、固定优先、编码斜杠、Dot Segment、未知 Query；
- 保留 Header 清理和最小可信 Header 注入；
- Human JWT、PLT-I03 Scope、Checkout、Provider Callback 边界；
- Shared/IP/Principal/Tenant Rate 与 Concurrency；
- 动态数值升降、Redis Failure-Closed、Lease 幂等释放和 TTL 恢复；
- 当前 Operations 三条 Route 的 Cookie/Header/Guard/Timeout 等价回归；
- `404/400/401/403/408/413/429/502/503/504` 和零目标调用断言。

Java `auto` 测试继续使用真实生产链、测试管理的 Redis/数据库和协议级状态 Simulator，不使用 MockBean/Mockito
替换生产行为，也不访问真实业务环境或共享基础设施。

### 18.3 仓库门禁

- Platform/Gateway 对应测试与覆盖率；
- requirements build、test、lint；
- PDManer Generator `--check` 和全量模型/场景测试；
- 相对链接、冲突标记、旧静态/Operations-only 权威措辞扫描；
- `git diff --check`；
- PDManer 4.9.3 应用内 Open/Validate/Diagram 人工门禁。

## 19. 最终冻结决策

1. 所有外部接口统一进入 Platform 公共 Route Catalog；内部调用绕过 Gateway。
2. 六类 Route Channel 共用一个全局 Snapshot 和 Path 冲突域；每次成功读取都返回完整 typed DTO。
3. Platform 动态管理 Route/Guard/Console Resource 和已批准 Target 状态；Gateway 执行运行时规则。
4. Target 身份三元组需要 Gateway Approved Registry + Platform 受控 Seed 双重批准，普通页面不能创建。
5. 安全实现是预编译 Profile；数据库不下发算法、密钥、脚本或可信 Header 名称。
6. Authorization Mode 精确区分 PUBLIC、HUMAN_AUTHENTICATED、HUMAN_PERMISSION、API_SCOPE、
   CHECKOUT_RESOURCE 和 PROVIDER_CALLBACK。
7. Human Permission 由 BFF + PLT-I02 决策；Client API 由 PLT-I03 Route Scope 加核心资源授权；Checkout 归
   Commerce；Provider Callback 归 Payment。
8. 新增六表；当前生成模型精确为 63 表 / 822 字段 / 123 唯一索引 / 99 访问索引 / 107 本库关系 / 5 个 1:1 / 7 图组，另有 31 条复合 FK、85 条具名 CHECK 与恰好 36 个 `_hash` 字段。
9. 不增加 `*_ver`、Release、Snapshot、Rollback、Ack、Tenant Override、Scope Catalog 或新 `*_hash`。
10. Route 子项整体替换，三个 Root 使用普通 Row Version；管理写事务串行化并同事务审计。
11. 每个 ACTIVE Route 至少有 Shared Rate + Concurrency；Tenant Rule 使用统一参数、独立 Redis Bucket。
12. Gateway 五秒单飞刷新、完整验证后原子切换；失败保留 Last Valid，冷启动无 Snapshot 不 Ready。
13. 保存即当前，无第二次发布；恢复旧值靠新命令或受审计 Break-glass，不保留静态隐藏后门。

以上决策构成后续 Platform PDManer、Platform Java、Gateway Java、BFF/Core Adapter、requirements 和迁移计划的
共同输入。任何新增外部 Channel、Target 类型、Security Profile、Tenant Override 或动态发布工作流都需要新的设计
审批。
