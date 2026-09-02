# MVC Web 日志、统一异常与内部 HTTP 精简设计

日期：2026-09-02
状态：已批准，进入实施

## 1. 决策摘要

本设计同时收敛三个基础设施边界：

1. `paytech-mvc-web-starter` 统一记录 Spring MVC 请求、响应与耗时；
2. `paytech-mvc-web-starter` 继续作为全局 HTTP 异常转换的唯一公共实现；
3. 公网 TLS 证书由外层 Nginx 终止和管理，Java 服务之间使用私有网络 HTTP，不在应用内实现 X.509、mTLS 或客户端证书认证。

当前日志按用户明确决定记录完整 Request Body 和 Response Body，不脱敏、不截断，也不增加开关。该选择的日志泄露和内存放大风险已知，后续通过独立设计处理；本轮不得擅自加入字段掩码、Body 长度上限或环境差异。

## 2. 当前问题

### 2.1 MVC Starter 缺少完整访问日志

`paytech-mvc-web-starter` 已提供 `RequestIdFilter`，但没有统一记录请求体、响应体、状态码和耗时。各业务服务如果自行实现，会重复处理 Servlet Body 缓存、异常响应、MDC 和 Filter 顺序。

### 2.2 异常响应存在重复实现

Foundation 已有 `GlobalExceptionHandler`、`ApiErrorResponse` 和 `ApiErrorResponseFactory`。Platform Support 又为 PLT-I20 定义 `PlatformHttpExceptionHandler` 和私有 `PlatformProblem`，导致同一应用出现两种错误响应形状。后者缺少公共响应已有的 `requestId` 和 UTC `timestamp`。

### 2.3 Platform 将内部网络访问实现为应用级证书认证

Platform Support 当前同时使用：

- `server.ssl.client-auth=need`；
- `InternalWorkloadSecurityConfiguration`；
- `X509WorkloadIdentityVerifier`；
- 硬编码 SPIFFE URI SAN 与 `GATEWAY_SNAPSHOT_READ` 能力；
- 仓库测试证书和完整 mTLS 测试客户端。

这使同一 Listener 上的 Health 请求也必须先通过 TLS 客户端证书握手，即使 Spring Security 将 Health 标记为 `permitAll`。证书签发、信任库、轮换和应用内 SAN 解析均超出当前内网只读 PLT-I20 的实际需要。

## 3. 目标网络边界

```text
External Client
  -> HTTPS
    -> Nginx                       # 公网证书、TLS 终止、覆盖 X-Real-IP
      -> Private HTTP API Gateway # 公网认证、Guard、路由
        -> Internal HTTP BFF/Core Service

Internal Java Service
  -> Private Network HTTP
    -> Internal Java Service
```

固定规则：

- Nginx 负责所有对公网证书和 TLS 终止；
- Nginx 必须覆盖写入单值 `X-Real-IP=$remote_addr`，不得沿用外部请求提供的同名值或代理链；
- 只有 Nginx 所在私有网络可以连接 API Gateway Listener，Gateway 不直接暴露公网端口；
- API Gateway 继续负责公网 JWT、API Credential、Guard 和可信边缘协议；
- API Gateway 与其他 Java 服务均不配置 Server Certificate、Client Certificate、Trust Store、X.509 或 mTLS；
- 服务间同步调用继续使用 `@HttpExchange`、服务发现和私有网络 HTTP，不经过公共 Gateway；
- 内部端口必须由部署网络、Security Group、Network Policy 或等价基础设施限制为非公网可达；
- 不把 `X-Caller-Service`、`X-Forwarded-Client-Cert` 或其他调用方可写 Header 当作认证；
- 去除传输证书不改变用户认证、Tenant 授权、API Scope、资源所有权和业务权限检查；这些仍由拥有相应事实的 Gateway、BFF、Platform 或核心系统执行。

Gateway 不启用 Spring/Reactor Netty 的通用 `Forwarded` 或 `X-Forwarded-*` 解析。最外层可信请求 Filter 只接受 Nginx 覆盖的一个合法 IPv4/IPv6 `X-Real-IP`，把解析后的 `InetAddress` 写入 Gateway 请求上下文，然后与其他保留代理 Header 一并从转发请求中删除。缺失、重复、逗号链或非法 IP 均 Fail-Closed，不再退回 Nginx 的 Socket IP。

Ingress Guard、认证后 Guard 与 Access Log 必须从同一个可信请求上下文读取 Client IP。这样 Route/IP 限流、并发控制和访问日志摘要不会因 Nginx 反向代理而退化为所有用户共享一个 Nginx IP，也不会出现不同组件各自解释代理 Header 的分歧。

本轮不新增通用内部安全 Starter。未来只有出现多个真实服务共享稳定的工作负载身份机制时，才评估独立的 `paytech-internal-security-starter`；不得把服务身份、固定路径或业务能力塞进 MVC Starter。

## 4. MVC 请求/响应日志

### 4.1 组件边界

在 `paytech-mvc-web-starter` 新增一个 Servlet `OncePerRequestFilter`，由 `WebAutoConfiguration` 自动注册。它只负责 HTTP 观察，不包含业务判断、鉴权或错误映射。

Filter 顺序固定在 `RequestIdFilter` 之后，因此每条日志都能读取已校验或新生成的 `requestId`，并共享同一个 MDC。

### 4.2 日志时机和字段

每个请求在完成后记录一条 `INFO` 日志；Controller 正常返回、全局异常转换以及未处理异常路径均进入同一 `finally` 收口。日志至少包含：

- `requestId`；
- HTTP Method；
- Request URI；
- Raw Query String；
- Request Body；
- Response Status；
- Response Body；
- `elapsedMs`。

本功能不记录全部 Request/Response Header；Header 与 Cookie 不属于本轮已确认的 Body 日志范围。现有 Request ID 仍作为独立响应 Header 返回。

### 4.3 Body 捕获

- 使用 Spring Servlet Content-Caching Wrapper 捕获真实生产链读写的 Body；
- 不提前消费 Request InputStream，避免 Controller 读取到空 Body；
- 在日志完成后把缓存的 Response Body 原样复制给客户端；
- 使用请求或响应声明的 Charset，缺失时使用 UTF-8；
- 无 Body 时记录空字符串；
- 当前服务接口按 JSON/文本 HTTP API 设计。新增 Streaming、SSE、文件上传下载或其他二进制 Body 前必须重新评估全量缓存和日志策略。

按照当前决定，本轮不实现：

- 敏感字段脱敏；
- Body 长度上限或截断标识；
- 按环境、Path、Content-Type 或注解开关日志；
- Header/Cookie 日志；
- 异步日志队列或额外日志存储。

## 5. 全局异常处理

### 5.1 公共职责

`GlobalExceptionHandler` 保留在 `paytech-mvc-web-starter`，所有引入 Starter 的 MVC 应用自动获得统一错误响应：

```json
{
  "code": "STABLE_ERROR_CODE",
  "message": "可外显消息",
  "requestId": "request-id",
  "timestamp": "UTC instant"
}
```

Foundation 不导入任何 Platform、Payment、Finance 或其他业务系统错误码。

### 5.2 503 语义

在 `paytech-common-core` 增加通用 `ServiceUnavailableException extends ErrorException`。它继续表达结果未知、允许调用方按幂等策略有界重试，但为 HTTP 适配器提供明确的不可用分类。

`GlobalExceptionHandler` 的映射顺序为：

1. `ServiceUnavailableException` -> `503 Service Unavailable`；
2. `FailException` -> `400 Bad Request`；
3. 参数校验和请求解析类异常 -> `400 Bad Request`；
4. 其他 `ErrorException` -> `500 Internal Server Error`；
5. 未处理 `Exception` -> `500 Internal Server Error`。

`ServiceUnavailableException` 和其他系统异常只向客户端返回其 `CodeMessageEnum` 的安全默认消息，不外显底层异常消息、SQL、类名或堆栈。服务仍可传入自己的 `PlatformErrorCode` 等 `CodeMessageEnum`。

Platform 的 `QueryGatewaySnapshotProcessor` 改为抛出携带 `PLATFORM_GATEWAY_SNAPSHOT_UNAVAILABLE` 的 `ServiceUnavailableException`。删除 `PlatformHttpExceptionHandler` 和 `PlatformProblem`，PLT-I20 的状态仍为 `503`，Body 改为统一公共结构。

## 6. Platform Support 精简

删除当前仅服务于 mTLS 的实现：

- `InternalWorkloadSecurityConfiguration`；
- `WorkloadIdentityVerifier`；
- `X509WorkloadIdentityVerifier`；
- `VerifiedWorkload`；
- `WorkloadCapability`；
- Spring Security 依赖；
- `server.ssl.*` 和 `client-auth` 配置；
- 测试证书、私钥、信任 CA 和 mTLS 专项测试。

PLT-I20 保持：

- `{context-path}/gateway-config/v1/public-snapshot` 路径；
- 每次全量返回 typed Snapshot；
- HTTP -> Controller -> Processor -> Domain Service -> Repository -> Mapper -> 本地 MySQL 完整链路；
- 配置无效或数据库不可用时 Fail-Closed，并返回统一 `503`；
- 不增加 ETag、缓存、持久化 Snapshot 或 Gateway 动态消费逻辑。

Health、Liveness、Readiness 和 PLT-I20 在测试及本地环境使用普通 HTTP。公网调用仍无法绕过 Nginx/Gateway 和部署网络边界直接访问服务端口。

## 7. TDD 与验证

### 7.1 Foundation

先补失败测试，再写生产代码：

- 真实 Servlet Filter 链可以读取 Request Body，客户端仍收到完全一致的 Response Body；
- 日志包含 Method、URI、Query、Body、Status、Request ID 和耗时；
- 正常响应与异常响应各只记录一次；
- MDC 在调用结束后恢复，不污染下一请求；
- `ServiceUnavailableException` 返回 `503` 和统一四字段 Body；
- `FailException`、普通 `ErrorException`、校验错误和未知异常保持既有映射。

执行 Foundation 目标模块测试及完整 `clean verify`，并确认公共模块没有引入 Spring Security 或业务服务依赖。

### 7.2 Platform Support

先把当前 mTLS 期望改为普通 HTTP 期望，使测试失败，再删除生产安全代码：

- 真实随机端口 HTTP 可以读取完整 Snapshot；
- 配置变更在下一次查询可见；
- 无效目录返回统一 `503`，并包含 `requestId` 和 `timestamp`；
- Health/Liveness/Readiness 不需要客户端证书；
- 架构门禁确认 Spring Security、X.509 类、SSL 配置和测试证书均不存在；
- 完整 HTTP/MyBatis/MySQL 链保持通过；
- `./mvnw clean verify` 和既有覆盖率门禁通过。

### 7.3 根仓一致性

同步更新当前态文档中 Platform mTLS、服务间 Workload Identity 和应用证书的描述。历史实施记录保留历史事实，但必须标注已被本设计取代。Requirements、PDManer 结构和业务数据库统计不因本变更修改。

最终运行 Requirements 构建/测试/Lint、相关文档精确扫描和 `git diff --check`。

### 7.4 API Gateway

先修改边界测试，再修改生产实现：

- Production Listener 使用普通内网 HTTP，`application.yml` 不再包含证书、私钥或 `PUBLIC_TLS`；
- Gateway 到 Operations BFF 与 Platform JWKS 仅使用内部 HTTP，JWKS 不再加载 TLS Trust Anchor；
- Nginx 写入的单值 IPv4/IPv6 `X-Real-IP` 被解析一次，并供两阶段 Guard 与 Access Log 共用；
- 外部伪造的 `Forwarded`、`X-Forwarded-*` 和已消费的 `X-Real-IP` 均不会进入 BFF；
- 缺失、重复、逗号链和非法 `X-Real-IP` 请求 Fail-Closed；
- Test Runtime 默认模拟可信 Nginx Header，既有路由、JWT、Guard、Redis、CORS、Body、响应与可观察性门禁保持通过；
- `./mvnw clean verify` 和 Gateway Boundary 脚本通过。

## 8. 多仓实施与合并顺序

四个 Git 仓库分别提交：

1. `paytech-foundation`：公共异常与 MVC 日志；
2. `platform-support-service`：升级本地 Foundation Snapshot 后删除 mTLS，并验证 PLT-I20；
3. `api-gateway`：删除应用 Listener/JWKS TLS，改用内部 HTTP，并消费 Nginx 的可信 Client IP；
4. Code-MoR 根仓：架构、技术栈、开发规范和导航同步。

`paytech-foundation`、`platform-support-service` 与 `api-gateway` 主目录当前存在其他未提交改动，实施必须使用各自独立 worktree。不得覆盖 Compensation Event、Gateway Compiler 或现有 Gateway Guard 的未提交改动。Foundation 目标模块通过后先安装新的 `1.0.0-SNAPSHOT` 到本机 Maven 缓存，再验证 Platform；是否发布 Nexus 由现有 Snapshot 发布流程决定，不因本任务自动外发。

## 9. 非目标

本轮不实现：

- 日志脱敏、截断、采样、异步输出或日志保留策略；
- Nginx 配置文件、证书申请、证书轮换或生产部署资源；仓库文档只冻结其覆盖 `X-Real-IP` 的契约；
- Service Mesh、应用 mTLS、X.509、HMAC 服务令牌或调用方 Header 认证；
- Platform 人工认证、Tenant IAM 命令、管理 API 或 Gateway Snapshot 消费；
- 新的 Foundation Security Starter；
- 数据库、PDManer、DDL/DML 或业务表变更。

## 10. 验收标准

- 所有 MVC 服务只需引入 `paytech-mvc-web-starter` 即可获得统一 Request/Response/耗时日志和全局错误响应；
- Platform 不再包含或依赖应用级证书、X.509、mTLS 或 Spring Security；
- PLT-I20 使用内网 HTTP，并保持完整 Snapshot 与稳定 `503` 业务语义；
- 公网 TLS 终止明确归属 Nginx，Gateway 使用内网 HTTP，并以 Nginx 覆盖写入的 Client IP 执行 Guard 和访问日志；
- Foundation、Platform、Gateway 和根仓门禁全部通过，且不覆盖现有无关改动。
