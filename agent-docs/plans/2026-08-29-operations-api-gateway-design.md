# Operations API Gateway 首期设计

最后更新：2026-08-29

状态：已确认的首期设计基线。当前实现授权只覆盖 `api-gateway`；不授权 Platform Support、`operations-console-bff`、前端、数据库、Migration、PDManer 或部署环境代码。

## 1. 目标与范围

首期只建立内部运营平台的公网入口保护层：

```text
operations-console-web
  -> API Gateway
    -> operations-console-bff
      -> Platform Support 内部认证/授权接口
      -> 经授权的核心系统内部接口
```

Gateway 只负责外部入口、Operations Access JWT 验证、粗粒度渠道保护、非阻塞限流与并发控制、静态路由、可信请求标识和访问日志。Gateway 不处理账号密码登录、不签发或刷新 Token、不执行角色/资源权限判断，也不直接把 Operations 浏览器流量转发到 Platform Support 或任一核心系统。

首期不创建 `operations-console-bff`。Gateway 自动测试使用有状态 BFF Simulator 证明边界和协议行为。

## 2. 项目与渠道命名

入口项目命名固定为：

- `operations-console-*`：内部运营人员；
- `client-console-*`：Client；
- `partner-console-*`：Local Partner；
- `checkout-*`：Consumer。

Operations 对应 `operations-console-web` 和 `operations-console-bff`。现有空目录 `business-portal-web`、`business-portal-bff` 只是历史占位，不是后续 Client/Partner Console 的目标名称；本设计不删除或迁移这些目录。

`api-gateway` 使用两个 Maven 模块：

```text
api-gateway/
├── pom.xml
├── api-gateway-web/     # 唯一生产 Deployable
└── api-gateway-test/    # 自动化与显式人工测试
```

Gateway 不拥有业务接口契约或数据库，因此不创建空 `*-api`、Repository、Mapper 或 DDD 分层。生产代码按实际边界组织在 `routing`、`security`、`guard.policy`、`guard.ratelimit`、`guard.concurrency`、`web` 和 `observability` 包中。

## 3. 首期静态路由

外部 Operations 路径统一带 `/api/operations`，BFF 自身只定义其后的业务路径。Gateway 在转发前移除前两个 Path Segment：

| Route Code | 外部 Method/Path | BFF Path | 身份要求 |
|---|---|---|---|
| `operations-auth-login` | `POST /api/operations/auth/v1/login` | `POST /auth/v1/login` | 匿名；精确 Origin |
| `operations-auth-refresh` | `POST /api/operations/auth/v1/refresh` | `POST /auth/v1/refresh` | 不要求 Access JWT；必须携带 BFF Refresh Cookie；精确 Origin |
| `operations-auth-logout` | `POST /api/operations/auth/v1/logout` | `POST /auth/v1/logout` | 有效 Operations Access JWT；精确 Origin |

只注册上述 Method + Path 精确组合及其 CORS Preflight。首期不注册 `/api/operations/**`、`/api/operations/v1/**` 或任何能把未知 API 转发到 BFF 的通配路由；未知 Method/Path 在 Gateway 返回 `404`，不得触达 BFF。

Operations 业务 API 的精确 Allowlist、OpenAPI 对齐和发布检查是独立后续任务。在该任务完成前，不暴露 Operations 业务路由。

路由和 Upstream 属于部署时静态配置。未来 Platform Support Guard Release 最多只能给已存在的 Route Code 叠加更严格的安全策略，不能创建或删除公共 Path、改变 HTTP Method、选择 BFF/核心系统、修改 Upstream URI、服务发现目标或部署拓扑。首期不调用 `PLT-I12`，也不依赖 Platform Support Last-Known-Good 数据。

## 4. Operations JWT 验证

Platform Support 是 Operations Access JWT 的签发方；Gateway 是纯 Resource Server。登录、Refresh、Logout 的业务语义见 [Platform Support 内部运营认证需求](./2026-08-29-platform-support-operations-authentication-requirements.md)。

Gateway 通过受信任 HTTPS JWKS 验证 `RS256` 签名和 `kid`，并要求以下 Claim：

| Claim | 要求 |
|---|---|
| `iss` | 精确匹配配置的 Platform Support Issuer |
| `sub` | 非空、稳定的内部平台用户标识 |
| `aud` | 必须包含 `operations-console` |
| `token_use` | `ACCESS` |
| `channel` | `OPERATIONS` |
| `actor_type` | `PLATFORM_USER` |
| `sid` | 非空登录会话标识 |
| `jti` | 非空 Token 唯一标识 |
| `iat`、`nbf`、`exp` | 完整且满足 `iat <= nbf < exp`、`exp - iat <= 15 分钟`；`iat` 不得超前当前时间超过 30 秒，`nbf`/`exp` 校验允许最多 30 秒时钟偏差 |

Gateway 拒绝 `alg=none`、非 `RS256` 算法、缺失/未知 `kid`、错误签名、错误 Issuer/Audience/Token Use、未来签发、时间顺序错误、寿命超过 15 分钟或过期 Token。不能只依赖 Spring Security 默认的 `exp/nbf` Validator；最大寿命、`iat` 和 Claim 组合必须使用显式 Validator。JWKS 使用有界缓存；未知 `kid` 可以触发一次受控刷新。已有缓存密钥仍有效时，短暂 JWKS 故障不影响使用该密钥的请求；无法取得验证所需密钥时失败关闭。

JWT 不携带姓名、邮箱、手机号等 PII，也不把 Role/Permission 当作资源授权事实。Gateway 只验证调用方是 Operations 渠道的平台用户；`operations-console-bff` 后续通过 Platform Support 当前授权事实执行页面/资源权限判断，核心系统继续执行资源所有权和业务规则校验。

自包含 Access JWT 在最长 15 分钟有效期内不承诺即时在线吊销。禁用账号、重置密码或撤销 Session 必须立即阻止 Login/Refresh；如需在 Access JWT 到期前逐请求吊销，另行设计在线 Revocation，不在首期 Gateway 范围内。

## 5. 浏览器、Cookie 与可信 Header 边界

- Access Token 只通过 `Authorization: Bearer` 传给 Gateway，Operations 前端只保存在内存，不写入 Local Storage、Session Storage 或普通 Cookie。
- Refresh Cookie 名称固定为 `__Secure-mor-operations-refresh`，属性固定为 `Secure; HttpOnly; SameSite=Strict; Path=/api/operations/auth/v1`，不设置 `Domain`，`Max-Age` 不超过当前 Session 剩余的 8 小时绝对期限。Logout/失效清理使用相同 Name/Path/SameSite/Secure 属性和 `Max-Age=0`。
- Cookie 只由 `operations-console-bff` 写入；Gateway 不解释、不保存、不生成 Token Value。Gateway 只在 Refresh/Logout 精确路由按名称转发这一枚 Cookie，Login 和其他请求移除全部外部 Cookie。
- CORS 只允许配置中的精确 Operations Console HTTPS Origin，固定 `allowCredentials=true`，不允许 `*` 或动态回显任意 Origin；只允许 `POST/OPTIONS` 和 `Authorization`、`Content-Type`、`X-CSRF-Token` Header，只暴露 `X-MoR-Request-Id`、`Retry-After`。Login、Refresh、Logout 都执行 Origin 校验，Console 与 Gateway 的生产域名必须保持 HTTPS Same-Site 才能使用 Strict Cookie。
- Refresh/Logout 的 CSRF 细节由 BFF 负责，Gateway 只转发已允许的最小 Header/Cookie。生产日志不得记录 Cookie、Authorization、密码或请求体。
- Gateway 先移除所有外部传入的保留身份 Header、`Forwarded`/`X-Forwarded-*` 和 `X-Request-Id`，再生成可信 `requestId`。
- JWT 验证成功后，Gateway 只向 BFF 写入最小可信上下文：`X-MoR-Request-Id`、`X-MoR-Actor-Subject`、`X-MoR-Session-Id`、`X-MoR-Actor-Type=PLATFORM_USER` 和 `X-MoR-Channel=OPERATIONS`。原始 Bearer Token 不继续转发。
- 匿名 Login/Refresh 不写入 Actor/Session Header；BFF 不能信任任何未由 Gateway 重新生成的外部身份 Header。

生产网络必须保证 `operations-console-bff` 的外部入口只接受 Gateway 流量；BFF 到 Platform Support/核心系统使用内部服务信任边界，不绕行外部 Gateway。

## 6. Guard 组合语义

首期 Guard 支持 `SHARED_ROUTE`、`CLIENT_IP` 和 `AUTHENTICATED_SUBJECT` 三种独立计数维度。

同一计数维度存在多条匹配配置时，只选择最高优先级：

```text
EMERGENCY > CREDENTIAL > CLIENT > ROUTE > GLOBAL
```

不同维度必须累计生效，而不是互相覆盖。一个请求只有在每一个适用 Rate Limit 和每一个适用 Concurrency Limit 都允许时才能继续。该语义取代旧 Platform Support Gateway Guard 对所有同类型规则统一“最高层覆盖”的描述；Rate Limit/Concurrency 是按维度组合的例外。

IP Allow/Deny、请求大小和超时仍按各自匹配与优先级选择，不做数值相加。客户端 IP 只从受信任代理拓扑解析；公网直连首期以实际 Remote Address 为准，不信任调用方提交的 `X-Forwarded-For`。

## 7. Bootstrap 策略

| Route | Rate Limit | Concurrency | Body 上限 | Upstream Timeout |
|---|---|---|---:|---:|
| Login | Shared Route `300/min`；Client IP `5/min`、Burst `5` | Route `20`；IP `2` | 16 KiB | 10 s |
| Refresh | Shared Route `600/min`；Client IP `30/min`、Burst `10` | Route `50`；IP `3` | 16 KiB | 10 s |
| Logout | Shared Route `300/min`；Subject `10/min` | Route `50`；Subject `2` | 16 KiB | 10 s |
| Future Operations API hard ceiling | Shared Route `200/s`、Burst `400`；Subject `20/s`、Burst `40` | Route `100`；Subject `10` | 1 MiB | 15 s |

Future Operations API 一行只是未来业务路由不得放宽的硬上限，不会创建任何当前路由。未标 Burst 的规则默认 Burst 等于该窗口配额。

Gateway 不对 Upstream 请求自动重试。浏览器或 BFF 的命令重试必须使用业务拥有方定义的幂等键；Gateway 不猜测请求是否安全重放。

## 8. 非阻塞 Redis 实现

Gateway 不依赖 `paytech-redis-starter`，也不创建 `StringRedisTemplate`、阻塞式 Redis Client 或 Redisson。它直接使用 Spring Data Redis Reactive + Lettuce 提供的 `ReactiveRedisConnectionFactory`/Reactive Script 执行能力；过滤链中禁止 `.block()`、阻塞锁和同步网络调用。

Rate Limit 使用一段原子 Lua Token Bucket 脚本。脚本使用 Redis `TIME` 作为唯一时间源，并用整数 Microtoken 定点数（`1 Token = 1,000,000 Microtokens`）计算补充，禁止浮点累计漂移和应用实例时钟差。一次请求的所有适用 Bucket 在同一脚本内完成 Refill、全量校验和扣减；任一 Bucket 不满足时不扣减其他 Bucket，并返回所有限制都可恢复所需的最大整数秒 `Retry-After`。Bucket TTL 至少为从空桶补满所需时间的两倍且不短于 60 秒；每次脚本执行只延长自身适用 Bucket 的 TTL。

当同一 Rate 身份同时配置 Current/Previous HMAC 版本时，这一对 Bucket 必须在同一次 Lua 调用、同一个 Redis `TIME` 下评估。脚本在作出允许/拒绝决定和扣减前，先把两侧完整状态（`tokens`、`last_refill_millis`、`refill_remainder`）保守同步：选择 Token 更少的一侧；Token 相同时选择 `refill_remainder` 更小的一侧，并把选中的完整状态复制到两个版本。允许时在该同步状态上统一扣减并双写；拒绝时不扣减，但仍把同步状态双写到两个版本并刷新两侧 TTL。这样旧实例、双版本实例和新实例在滚动发布期间不会形成可利用的独立额度，也不会通过切换 HMAC 版本重置限额。

Concurrency 使用 Lua + ZSET Lease：

1. Acquire 脚本同样使用 Redis `TIME`，并清理所有适用 ZSET 的过期 Lease；
2. 全量检查 Route 与 IP/Subject 等独立维度；
3. 只有全部有容量时，才以同一个随机 Lease ID 原子写入所有 ZSET；
4. Reactor 终止信号（成功、错误或取消）都执行 Release 脚本；
5. Lease 到期时间必须覆盖 Upstream Timeout 和一个小的释放余量，以便进程崩溃时自动恢复容量；
6. 任一维度已满时，`Retry-After` 取“让所有已满维度都至少释放一个名额”所需时间的最大整数秒。

当前 Redis 是单机，因此多 Key Lua 是首期明确边界。Key 使用版本化 Gateway Namespace、稳定 Route Code 和分 Purpose、带密钥/版本的 HMAC-SHA-256 IP/Subject 摘要，不得使用裸 SHA-256；Redis Key 摘要与访问日志摘要使用不同 Purpose/Key，避免跨用途关联。HMAC Key 必须在实例间稳定并从 Secret 注入，不保存原始 IP、Subject、JWT 或 Cookie。

Rate HMAC 轮换必须先让全 Fleet 完成 Current+Previous 双版本部署并确认发布完成，再从该确认点完整等待至少当前最大 Guard TTL（120 秒），最后才允许移除 Previous；等待期内持续执行上述双版本保守状态同步。Concurrency HMAC 轮换仍按现有全量检查、同一 Lease ID 原子写入 Current/Previous ZSET、终止时统一 Release 和 Lease TTL 回收语义执行；它不复制或同步 Rate Bucket 状态，也不宣称具备 Rate 的 Token/Refill 状态收敛语义。所有 Redis Key 都有 TTL。未来切换 Redis Cluster 前必须重新设计 Hash Slot 和脚本边界。

Redis 是保护控制面的运行状态，不是业务事实。请求转发前的 Rate/Acquire 连接、命令或脚本失败一律失败关闭并返回 `503`，不得静默放行。Release 发生在成功、错误或取消的终止路径；若 Release 失败或响应已经提交，Gateway 记录有界安全指标/告警并依赖 Lease TTL 回收，不尝试改写已提交响应。Rate/Concurrency 拒绝返回 `429` 和整数秒 `Retry-After`。

## 9. 错误响应

Gateway 自己产生的错误统一为：

```json
{
  "code": "GATEWAY_RATE_LIMITED",
  "message": "请求过于频繁",
  "requestId": "...",
  "timestamp": "2026-08-29T00:00:00Z"
}
```

| HTTP Status | 场景 |
|---:|---|
| `401` | Bearer 缺失、格式/签名/Issuer/Audience/时间/Token Use 无效 |
| `403` | 有效身份不属于允许的 Channel/Actor Type，或 Origin/IP 策略拒绝 |
| `404` | 未注册 Method/Path；不转发 |
| `413` | 请求体超过 Route 上限 |
| `429` | Rate/Concurrency 拒绝；必须带 `Retry-After` |
| `503` | 所需 JWKS 无法取得、Redis 不可用、BFF 无法连接 |
| `504` | BFF 超时，且 Gateway 响应尚未提交 |

Gateway 不包装 BFF 的正常或业务错误响应。响应 Header 和 Gateway 错误体都带同一个可信 Request ID；错误消息不暴露 Token、策略 Key、内部地址、堆栈或依赖凭据。

Upstream Timeout 从 Concurrency Lease 获取成功后开始约束完整 Downstream Chain。超时发生时，如果 Gateway 响应尚未提交，则返回稳定的 `504` JSON 错误和同一个可信 Request ID；如果 BFF 的首块响应已经提交，则不得改写既有 HTTP 状态或正文，只终止响应流、释放全部已获取的 Concurrency Lease，并把唯一终态访问日志记录为 `statusFamily=NONE`、`status=NONE`、`outcome=UPSTREAM_TIMEOUT`。

## 10. 可观测性与健康检查

访问日志只记录 Route Code、Method、状态码、耗时、Outcome、Request ID、Trace ID 以及 IP/Subject 的不可逆摘要。禁止记录 Query、Body、密码、JWT、Cookie、Refresh Token、Redis Key 原文或内部授权明细。

指标只使用有界标签，例如 Route Code、Outcome、Status Family 和 Limiter Dimension；禁止使用 IP、Subject、JTI、SID、Request ID 或原始 Path 作为标签。

- Liveness 只证明 Gateway 进程和 Reactive Event Loop 可运行；
- Readiness 要求 Bootstrap 配置有效且 Redis 可用；
- Readiness 不探测 BFF，也不因单个 Upstream 故障把所有入口摘除；
- Actuator 使用独立内部管理端口，不暴露在公网 Operations 路由上。

## 11. 自动化测试

`api-gateway-test` 从随机端口启动真实 Gateway，关闭 Nacos/外部配置中心，并通过真实 HTTP 调用有状态 BFF Simulator 和 JWKS Simulator。测试不得使用 `@MockBean`、Mockito 方法返回 Stub 或替换生产 Filter/Redis Client。

Gateway Redis 测试明确复用仓库 `local-environment/compose.yaml` 中已启动的专用 `mor-local` Redis：

- 地址固定为 `127.0.0.1:16379`，密码只从未提交的 `MOR_REDIS_PASSWORD` 注入；不得使用宿主机 `6379` 的其他 Redis；
- Gateway 测试不引入 Testcontainers；这是 Gateway Redis 测试相对通用测试规范的明确例外；
- 每次测试运行生成唯一 Namespace，Route Code 和 IP/Subject 摘要都位于该 Namespace 下；
- Setup/Teardown 只通过 `SCAN` 删除本次运行的已知前缀，禁止 `KEYS *`、`FLUSHDB` 或清理其他服务 Key；
- 并行执行必须使用不同 Namespace；Compose Redis 未启动、版本不符、鉴权失败时快速失败并给出本地启动提示；
- Redis 不可用场景通过测试配置连接一个未监听的 Loopback 端口验证 Fail-Closed，不停止共享 Compose Redis。

默认自动测试覆盖：精确路由和 Strip Prefix、未知 API 不转发、JWT/JWKS 正反例、保留 Header 清理、CORS/Origin、请求大小、超时、错误格式、Rate Token Bucket、Concurrency Lease 获取/释放/过期恢复、Redis 失败关闭、Request ID、日志脱敏和指标低基数。JWT/BFF 完整链路必须通过真实的 `operations-auth-logout` 精确 Route 覆盖；任何 Profile 都不得增加仅测试可见的第四 Route，Catalog 和运行时 `RouteLocator` 始终只能包含三条生产 Route。

## 12. 延期任务与验收边界

独立后续任务：

1. Operations 业务 API 精确 Allowlist、OpenAPI 对齐和未声明 API 发布阻断；
2. Platform Support 自有账号/密码、Refresh Session、JWKS/签名密钥的物理模型与实现；
3. BFF 的当前 Role/Permission/Resource 授权契约；
4. Platform Guard Policy 对静态 Route Code 的受限发布和 Last-Known-Good；
5. VPN/零信任接入、WAF、生产代理链和生产 Redis 高可用；
6. Client Console、Partner Console、Checkout 和机器 API 的各自认证方案。

首期 Gateway 完成的验收标准是：只有三个 Operations Auth 精确 Route 可达；Gateway 能独立验证 Operations JWT；未知 API 不转发；全部 Rate/Concurrency 维度以非阻塞 Redis 原子执行并失败关闭；可信 Header、错误、日志、指标和健康检查符合本文；Gateway 无 Platform Support 生产代码或 `paytech-redis-starter` 依赖。
