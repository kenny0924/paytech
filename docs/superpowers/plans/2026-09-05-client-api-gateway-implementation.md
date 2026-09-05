# Client API Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 Operations 认证合同、不创建独立 Client API 服务的前提下，使 API Gateway 能按可信 Channel 路由并通过 PLT-I03 认证 Client API Credential、执行 Credential/Tenant Guard 和 exact Scope、向真实 Owner Adapter 转发最小可信身份。

**Architecture:** 共享入口管线先固定 Nginx 提供的 Channel/Client IP/Request ID，再按 `(channel, method, path)` 捕获 Snapshot Route。Operations JWT 与 Client API Credential 使用不同 Security Chain，但都产出 Gateway typed identity，供通用认证后 Guard 使用。Client API 只把 Request/Client/Credential ID 注入 Owner；Platform、Owner 和 Nginx 的生产改造是独立前置，Gateway 自动测试使用真实 HTTP Loopback Simulator 与仓库自有 Redis。

**Tech Stack:** 仓库现有 Java 25、Spring Boot WebFlux、Spring Cloud Gateway、Spring Security Reactive、`@HttpExchange`、Reactor Netty、Redis Lua、JUnit 5、真实 Loopback HTTP Simulator；版本全部来自现有 Parent/BOM。

**Spec:** [`docs/superpowers/specs/2026-09-05-client-api-gateway-design.md`](../specs/2026-09-05-client-api-gateway-design.md)

## Global Constraints

- 本计划的生产代码范围只在 `api-gateway`；Task 8 对根目录 `agent-docs/CURRENT_IMPLEMENTATION.md` 的最小状态合并是显式文档例外。Platform Java/SQL/PDManer、Credential 管理、CCT 投影、Owner 业务 API、Nginx/Compose、受控 DML 和部署均由各自 Owner 的独立任务完成。
- 不提交、建分支、推送、部署、启动容器或应用 Schema/DML；用户明确要求这些动作前保持工作区改动未提交。
- 保留现有用户对 `GatewaySnapshotCompiler.java`、`GatewaySnapshotCompilerAutoTest.java`、`api-gateway/README.md` 和根目录 `CURRENT_IMPLEMENTATION.md` 的未提交修改，只做范围内最小合并。Channel Key 必须建立在现有 `method + segmentCount` overlap bucket 和不同变量数交集修复之上，不得回退。
- 生产代码只依赖 Platform Owner 在 `platform-support-api` 发布的 PLT-I03 typed 合同；禁止在 Gateway 复制 wire DTO、猜测 Endpoint 或从 Credential Prefix 推断身份。
- 所有行为变化按 TDD：先运行最小失败证据，再写最小生产实现，再跑聚焦回归。测试不使用 Mockito、`@MockBean`、spy、Testcontainers 或自动 Schema 初始化。
- 外部依赖使用真实随机 Loopback HTTP Simulator；Redis 测试复用手工启动的仓库自有 `mor-local`，每次只清理随机测试 Namespace。
- 不为测试向生产 `ApprovedTargetRegistry` 注册空 Client Target；使用 test-only Bean/构造注入提供 Simulator Target。
- Credential、Authorization、Scope 和可信身份原值不得进入异常、`toString()`、日志、Metric Tag 或 Simulator Journal。
- Gateway 代码与 auto 测试完成只可标记为 `integration-ready`。所有激活门禁满足前，不得把 `CURRENT_IMPLEMENTATION.md` 写成 Client API 已部署或可用。

---

## Gate 0: 等待 Platform Owner 发布 PLT-I03 typed contract

**Read-only evidence:**

- `platform-support-service/platform-support-api/src/main/java/**`
- `api-gateway/pom.xml`
- `agent-docs/CURRENT_IMPLEMENTATION.md`

- [ ] 用 `rg -n "ApiCredential|CredentialValidation|PLT-I03" platform-support-service/platform-support-api/src/main/java` 确认已有具体 typed API、请求/响应与合同常量；当前 2026-09-05 预期结果为空，因此此 Gate 尚未通过。
- [ ] 在 `api-gateway` 运行 `./mvnw -q -pl api-gateway-web -DskipTests dependency:tree -Dincludes=com.paytech:platform-support-api`，确认 Gateway 实际解析到包含新合同的 `com.paytech:platform-support-api:1.0.0-SNAPSHOT`，而不只是工作区源码存在。
- [ ] 在 `api-gateway` 运行 `./mvnw -q -pl api-gateway-web dependency:copy-dependencies -DincludeArtifactIds=platform-support-api -DoutputDirectory=target/contract-inspection`，再用 `jar tf api-gateway-web/target/contract-inspection/platform-support-api-1.0.0-SNAPSHOT.jar` 核对解析制品确实含 Gate 第一步发现的精确合同类；不要把个人 Maven Local Repository 绝对路径写进脚本或文档。
- [ ] 核对 published contract 是返回 `Mono<OwnerResponse>` 的 reactive `@HttpExchange` 接口，明确 HTTP Method/Path、Credential 传输位置，以及机器可复用的 Credential 长度/字符上限、响应 Scope 上限、无效认证与技术失败 wire 分类；Credential 只能位于受控 Header 或 Body，不得进入 Path/Query。V1 不批准在 Netty 线程调用 blocking Owner 接口或临时 bounded-offload 包装。
- [ ] 核对请求类型不包含 Tenant/Client/Owner/required Scope 覆盖字段；成功响应至少包含 `tenantId`、固定 `tenantType=CLIENT`、`ownerRefId`、`credentialId`、`effectiveScopeCodes`。
- [ ] 如果任一项缺失，停止 Task 5 及其后依赖任务并创建独立 Platform 设计/实施任务；Task 1–4 可继续。不得在 Gateway 建临时生产 DTO、手写重复 URI 或硬编码猜测的 Path。

## Task 1: 把运行时 Route Key 改为 Channel + Method + Path

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeRoute.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeCatalog.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotStore.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/AuthenticatedOperationsIdentityGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsBoundaryWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsBffTimeoutGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayAccessLogWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/DynamicRouteWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestHeaderLimitsWebFilter.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewayRuntimeCatalogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotPinningAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricDimensionsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/DynamicOperationsRoutingAutoTest.java`

### 1.1 Write the failing catalog/compiler tests

- [ ] 在 `GatewayRuntimeCatalogAutoTest` 先把调用点改成 typed Channel，并增加：同 Channel 精确重复拒绝、跨 Channel 相同 GET/Path 共存、同 Path 在错误 Channel 返回 empty。
- [ ] `ApprovedTargetRegistry` 增加接收完整 immutable Approved Target 集合的显式构造器，供编译测试和后续 test-only Bean 使用；无参生产构造器仍只含当前真实 Operations Target，绝不加入虚假 Client Target。
- [ ] 在 `GatewaySnapshotCompilerAutoTest` 用显式 Registry 加入 test-only Client Target，增加同 Channel 重叠模板拒绝和跨 Channel 相同/重叠模板允许。保留当前 64/65/4096 条、两种插入顺序、不同变量数量和固定 Path 优先全部用例。
- [ ] 运行 `./mvnw -q -Dtest=GatewayRuntimeCatalogAutoTest,GatewaySnapshotCompilerAutoTest test`，记录新用例因现有全局 Method/Path Key 失败。

### 1.2 Implement the typed key

- [ ] 将 `GatewayRuntimeRoute.channel` 从 `String` 改为 `GatewayRouteChannel`；`GatewaySnapshotCompiler.runtimeRoute(...)` 只解析一次枚举。
- [ ] 更新所有 runtime route Channel 消费点使用枚举比较；现有日志/Metric/target-count API 如果仍接收 String，只在该观察边界调用 `channel.getCode()`，不得重新解析任意字符串。
- [ ] 把 `GatewayRuntimeCatalog` 的公开签名改为：

```java
Optional<GatewayRuntimeRoute> find(
    GatewayRouteChannel channel, HttpMethod method, String rawPath);

Optional<GatewayRouteMatch> match(
    GatewayRouteChannel channel, HttpMethod method, String rawPath);

List<GatewayRouteMatch> matchesPath(
    GatewayRouteChannel channel, String rawPath);
```

- [ ] 内部 Key 固定为：

```java
private record RouteKey(
    GatewayRouteChannel channel, HttpMethod method, String rawPath) {}

private record ChannelMethodKey(
    GatewayRouteChannel channel, HttpMethod method) {}
```

- [ ] `GatewaySnapshotCompiler` 的 endpoint set 使用 `channel + method + externalPath`；`RouteShapeKey` 使用：

```java
private record RouteShapeKey(
    GatewayRouteChannel channel, HttpMethod method, int segmentCount) {}
```

- [ ] Route Code 仍全局唯一；只把 Channel 加入 endpoint/overlap key，不修改最大目录、Path 解析、变量集合和现有 overlap 算法。
- [ ] 同一原子任务更新 `DynamicRouteWebFilter` 与 `GatewayRequestHeaderLimitsWebFilter` 的三个 Catalog 调用点，先显式传入 `GatewayRouteChannel.OPERATIONS` 以保持当前唯一运行 Channel 并保证 Task 1 可编译；Task 2 再替换为可信 exchange Channel。禁止留下旧 overload 或跨 Channel 搜索兼容层。

### 1.3 Verify Task 1

- [ ] 重跑 Task 1 聚焦测试及 `DynamicOperationsRoutingAutoTest`，并确认整个 `api-gateway-web`/`api-gateway-test` 测试源码可编译，不允许以只编译两个测试类掩盖调用者遗漏。
- [ ] 用 `git diff -- api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java` 人工确认现有用户 overlap 修复未丢失。

## Task 2: 固定可信 Entry Channel 与外部可生成 Request ID

**Files:**

- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestIdPolicy.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayProtocol.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedRequestContextWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/DynamicRouteWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestHeaderLimitsWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayPublicTestClient.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayRequestIdAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ClientApiRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/DynamicOperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/OperationsGatewayAcceptanceAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisRateLimitFailureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayHealthAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwksAvailabilityAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwtValidationAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/RedisUnavailableGatewayAutoTestBase.java`

### 2.1 Write ingress/request-id failures first

- [ ] 增加 `CLIENT_API` 合法单值 Request ID 在本地 404/403 响应中原样保留、缺失/重复/非法/65 字符 fallback、64 字符边界通过的测试；下游传播留给 Task 6 的真实 Owner 测试。增加 `OPERATIONS` 即使收到合法外部值也生成新 Request ID 的回归。
- [ ] 增加缺失/重复/未知 `X-Entry-Channel` 返回通用 403 且 Snapshot/Redis/下游零调用的测试；管理端口继续绕过公网入口协议。
- [ ] 新建统一 `GatewayPublicTestClient`，普通公网 Operations 请求默认加入 `X-Entry-Channel: OPERATIONS`；边界测试可显式缺失、重复或替换 Channel。迁移 Files 清单中的全部公网端口调用，raw socket 用同一 helper 生成行；management-port 测试继续不带该 Header，验证真实 bypass。任何大小写的 `x-gateway-*`、原始 Entry Channel/代理 Header 最终都不能下传。
- [ ] 增加 Client API Authorization 8192/8193 总预算、Operations 同边界、重复/大小写 Authorization 回归：Client API Authorization 超出绝对预算为 401，其他超限保持 431，重复语义留给 Task 5 Converter 返回 401；未知 Operations Route 的超大受限 Header继续优先返回 431。
- [ ] 用隔离 test-only Registry/Snapshot 放入一个 Client API POST Route；发送带 `Origin + Access-Control-Request-Method + Access-Control-Request-Headers + Authorization` 的 OPTIONS，必须按真实 OPTIONS 返回 404、无 CORS/`Vary`、Redis 与 Operations BFF 零调用。Task 7 再以完整 Simulator 断言 PLT-I03/Owner 也为零调用。
- [ ] 运行 `./mvnw -q -Dtest=GatewayRequestIdAutoTest,TrustedRequestBoundaryAutoTest,DynamicOperationsRoutingAutoTest,ClientApiRoutingAutoTest,GatewayTransportAutoTest test` 并记录失败。

### 2.2 Implement the ingress protocol

- [ ] `GatewayProtocol` 增加 `ENTRY_CHANNEL = "X-Entry-Channel"`、`CLIENT_ID = "X-Gateway-Client-Id"`、`CREDENTIAL_ID = "X-Gateway-Credential-Id"`；保留 Operations 常量。
- [ ] `GatewayProtocol.isReservedIngressHeader` 对所有 `x-gateway-` 前缀返回 true，并继续显式保护 `X-Entry-Channel`、Request ID、Real IP 和 Forwarded 系列。
- [ ] `GatewayRequestIdPolicy` 先接收 typed Channel：只有 `CLIENT_API` 接受完整正则 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 的唯一外部 Header；其他 Channel 忽略外部值。所有未保留情况都用注入的 `Supplier<String>` 生成 UUID，便于纯算法边界测试而不伪造 HTTP 链。
- [ ] `GatewayExchangeAttributes` 增加 `ENTRY_CHANNEL` typed accessor；不要把原 Header 留在请求中。
- [ ] `TrustedRequestContextWebFilter` 对公网请求依次读取单值 Entry Channel、按 Channel 解析 Request ID、读取单值 Real IP，写 exchange 属性，再删除全部保留入口 Header。Request ID 永远得到 fallback；Channel/IP 失败返回通用 403。Operations 分支继续无条件生成 Request ID。
- [ ] 固定 WebFilter 次序为 `TrustedRequestContext(+4) -> HeaderLimits(+5) -> DynamicRoute(+12) -> GatewayRequestPolicy(+18) -> OperationsBoundary(+20) -> IngressGuard(+30)`，把全部值集中到 `GatewayGlobalFilterOrder`。这保留 Operations 对未知 Route 超大 Header 的 431 优先级，同时确保 HeaderLimits 已读取唯一可信 typed Channel 和最终 Request ID。
- [ ] `GatewayRequestHeaderLimitsWebFilter` 只用 exchange 中已固定的 Channel；`CLIENT_API` 的 Authorization 预算失败写通用 401，其他预算失败保持现有 431。为保持现有 Operations 431 CORS 终态，它可以用 typed Channel 查询 terminal Route，但不得自行解析 Header Channel、跨 Channel 搜索或覆盖已固定属性。
- [ ] `DynamicRouteWebFilter` 调用 `catalog.match(entryChannel, method, rawPath)`。只有 `OPERATIONS` 可按现有 CORS 规则用单值 `Access-Control-Request-Method` 匹配候选；`CLIENT_API` 始终使用实际 HTTP Method，OPTIONS 不得借 POST/GET Route 进入 Security、Guard 或 Owner。

### 2.3 Verify Task 2

- [ ] 重跑 Task 2 聚焦测试。
- [ ] 运行 `./mvnw -q -Dtest=OperationsCorsAndBodyLimitAutoTest,OperationsReadOriginAutoTest,GatewayErrorContractAutoTest test`，确认 Operations Request ID/CORS 现有外部行为未回归。

## Task 3: 先建立通用 Approved Target transport 与 Client 测试基础设施

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteConfiguration.java` -> `GatewayRouteConfiguration.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsBffTimeoutGlobalFilter.java` -> `GatewayUpstreamTimeoutGlobalFilter.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/clientapi/ClientApiOwnerSimulator.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/clientapi/ClientApiOwnerScenarioStore.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/clientapi/ClientApiOwnerRequestJournal.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRouteDefinitionContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsHttpTestModeAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricDimensionsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`

### 3.1 Write transport and test-infrastructure failures

- [ ] 为 `ApprovedTargetRegistry` 多 Target immutable 构造、`routingTargets()`、Channel/Discovery/Path Root 不匹配、部署 Target 缺失和 Registry-driven RouteLocator 增加失败测试。
- [ ] 在 `GatewayAutoTestBase` 的 test-only `@Primary` Registry 中加入 `client-api-owner-test + CLIENT_API + approved path root + Owner Simulator URI`；生产无参 Registry 仍只含真实已批准 Target。`GatewaySnapshotFixture` 增加显式 Client API Target/Route、required Scope 和四维 Guard fixture，默认 Operations Snapshot 不变。
- [ ] Owner Simulator Journal 只捕获 Method/Path、受控业务 Header、Body 长度及 Header name/value；Credential canary 永不记录，unexpected call 立即失败。把 Simulator 加入共享 start/reset/verify 生命周期和 test discovery instance，但在 Task 5 认证成功前不伪造匿名 Client 成功流量。
- [ ] 用现有 Operations 安全链和第二个 test-only transport Target 验证多 Target locator、Owner timeout 504、非法上游 502；确认测试 Target 只能来自显式 test configuration。

### 3.2 Implement generic transport

- [ ] `ApprovedTargetRegistry` 暴露 immutable `routingTargets()`；显式构造器接收完整集合而不是在生产默认集合上隐式追加，避免测试 Target 混入生产。
- [ ] `GatewayRouteConfiguration` 为 Registry 中每个 Approved Target 建立一条基于固定 `targetServiceCode` 的 transport Route；URI 只能来自 Registry，不能来自 Snapshot、Header、Query 或 Path。
- [ ] `GatewayUpstreamTimeoutGlobalFilter` 读取当前 `GatewayRuntimeRoute.responseTimeout`，保持零 retry 和现有 502/504 分类；同一任务更新全部 import、直接构造和架构断言，包括 `OperationsGuardFlowAutoTest`，不留下半完成 rename。

### 3.3 Verify Task 3

- [ ] 运行 Task 3 聚焦测试及 `GatewaySnapshotPinningAutoTest,GatewaySnapshotRefreshAutoTest`。
- [ ] 架构测试确认生产 Registry 没有 test Target，且不存在 Snapshot URI、动态 Host/Port、Discovery Locator 或 Retry Filter。

## Task 4: 建立 typed identity 并通用化认证后 Guard

**Files:**

- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/identity/GatewayAuthenticatedIdentity.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/identity/OperationsIdentity.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/identity/GatewayAuthenticatedIdentityGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsChannelAuthorizationManager.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/AuthenticatedOperationsIdentityGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardAdmission.java` -> `GatewayGuardAdmission.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsIngressGuardWebFilter.java` -> `GatewayIngressGuardWebFilter.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardGlobalFilter.java` -> `GatewayAuthenticatedGuardGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardContext.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardDimension.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewayGuardProfileCompiler.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/guard/OperationsGuardAdmissionTestSupport.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/ClientApiGuardFlowAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayFilterOrderContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsTrustedContextAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/probe/GatewayBoundaryTestConfiguration.java`

### 4.1 Write guard contract failures

- [ ] 先为 `TENANT` 编译、Principal/Tenant 成对基线、Credential 与 Tenant 生成不同 Redis Key 增加失败测试；本任务用 test-only `GatewayAuthenticatedIdentity` 实现，不引用尚未创建的生产 `ClientApiIdentity`。Client API Route 缺少 typed identity 时认证后 Guard 必须返回 503，不能 skip 或读取伪造 Header。
- [ ] 增加认证后 Guard 从 typed identity 读取 `principalKey`/`tenantKey` 的测试；伪造 Header、Query、Path、Body 中的相同 ID 不改变 Key。
- [ ] 保留 Operations JWT Subject Guard 的现有 Redis Key code；增加回归断言，避免通用化导致现有 Bucket 被静默换 Namespace。
- [ ] 运行 `./mvnw -q -Dtest=GuardProfileContractAutoTest,GuardPolicyResolutionAutoTest,OperationsGuardFlowAutoTest,ClientApiGuardFlowAutoTest test` 并记录失败。

### 4.2 Implement typed identity

- [ ] 定义最小接口：

```java
public interface GatewayAuthenticatedIdentity {
    String principalKey();
    Optional<String> tenantKey();
    String accessSubject();
}
```

- [ ] `OperationsIdentity` 从已经验证的 `JwtAuthenticationToken` 映射 Subject，以及 Tenant Context 时的 Tenant ID；不改变 Operations 下游 Header 内容，并覆盖 record `toString()` 只显示字段是否存在。
- [ ] `GatewayGlobalFilterOrder` 新增 `TYPED_AUTHENTICATED_IDENTITY=HIGHEST+40`、`AUTHENTICATED_GUARD=+50`、`CLIENT_API_SCOPE=+60`、Body `+100`、Timeout `+200`、Upstream Rebuild `+300`、`TRUSTED_DOWNSTREAM_IDENTITY=+400`。增加顺序契约测试锁定严格不等式；+40 只建立内部身份，+400 只在请求重建后注入下游 Header，二者不得复用。
- [ ] `GatewayAuthenticatedIdentityGlobalFilter` 固定为 +40，在认证后、Guard 前把受支持的 Authentication 映射为 immutable typed identity并存入 `GatewayExchangeAttributes.AUTHENTICATED_IDENTITY`。不修改下游 Header，不把 identity 原值写入 `toString()`；成功路径用 `VersionedHmacDigester` 对 `ACCESS_SUBJECT_DIGEST` 执行 `putIfAbsent`。
- [ ] `OperationsChannelAuthorizationManager` 继续校验 Operations Channel/Actor，并保留 valid JWT 被拒绝路径的 subject digest（Security 拒绝时不会进入 SCG GlobalFilter）；可与 typed filter 共用安全 recorder，但不得删除 `GatewayAccessLogAutoTest` 已锁定的 403 digest。
- [ ] 现有 `AuthenticatedOperationsIdentityGlobalFilter` 仍只负责 Operations 下游 Header 重建，但改为从已固定的 `OperationsIdentity` 取值并使用 `TRUSTED_DOWNSTREAM_IDENTITY=+400`；缺少 typed identity 时沿用既有不伪造身份的行为。同步迁移 `OperationsTrustedContextAutoTest` 与 `GatewayBoundaryTestConfiguration` 等直接 order consumer，Operations 下游 Header 合同不变。

### 4.3 Generalize Guard without changing serialized keys

- [ ] `GuardContext` 改为：

```java
public record GuardContext(
    String routeCode,
    InetAddress remoteAddress,
    String authenticatedPrincipal,
    String tenantIdentity) {}
```

构造器逐项校验 `RawGuardIdentity`，`toString()` 只报告 present flags。

- [ ] `GuardDimension` 新增 `TENANT("tenant", 4)`；现有 `AUTHENTICATED_SUBJECT("authenticated-subject", 3)` 的 code 保持不变，Platform `AUTHENTICATED_PRINCIPAL` 继续映射到它。
- [ ] 两个 Command Factory 增加 `createAuthenticated(...)`，允许 `AUTHENTICATED_SUBJECT + TENANT`，分别取 `authenticatedPrincipal` 和 `tenantIdentity`；Ingress 方法仍只允许 Shared Route + Client IP。
- [ ] `GatewayGuardProfileCompiler` 实现 Platform `TENANT` 映射并要求 Rate/Concurrency 成对。`CLIENT_API_CREDENTIAL` Route 必须具备 Shared Route、Client IP、Authenticated Principal、Tenant 四维基线；Operations 当前基线不变。
- [ ] 三个 Operations-named Guard 类只做行为等价重命名/通用化；Admission 的 cancel-safe Lease、错误映射和 Redis Fail-Closed 实现不改算法。`GatewayAuthenticatedGuardGlobalFilter` 固定 +50；任何需要 authenticated Guard 的 Client API Route 如果没有 `GatewayAuthenticatedIdentity` 或缺少 Tenant Key，写 503 dependency-unavailable，Owner 零调用。

### 4.4 Verify Task 4

- [ ] 重跑 Task 4 聚焦测试及 `RedisConcurrencyLeaseAutoTest,RedisGuardUnavailableAutoTest,GatewayMetricDimensionsAutoTest,GatewayAccessLogAutoTest`；锁定 valid JWT channel/actor mismatch 403 仍带安全摘要。
- [ ] 用架构测试确认生产 Guard 不再依赖 `JwtAuthenticationToken`，JWT 解析仅存在于 Operations identity mapper/security chain。

## Task 5: 接入 PLT-I03 Credential Security Chain 并执行 exact Scope

**Dependency:** Gate 0 必须通过。

**Files:**

- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiIdentity.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiAuthenticationToken.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiCredentialServerAuthenticationConverter.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiCredentialReactiveAuthenticationManager.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ReactiveClientApiCredentialValidationClient.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/PlatformClientApiCredentialValidationClient.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiCredentialDependencyUnavailableException.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiScopeGlobalFilter.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/clientapi/ClientApiSecurityConfiguration.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/configuration/PlatformCredentialValidationProperties.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/configuration/GatewayCredentialValidationConfiguration.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/identity/GatewayAuthenticatedIdentityGlobalFilter.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/platform/PlatformCredentialValidationSimulator.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/platform/CredentialValidationScenarioStore.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/platform/CredentialValidationRequestJournal.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/ClientApiCredentialFixtureBuilder.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/ClientApiCredentialAuthenticationAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/ClientApiScopeAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/PlatformCredentialValidationClientAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/configuration/PlatformCredentialValidationPropertiesAutoTest.java`

### 5.1 Build the strict HTTP Simulator from Owner types

- [ ] Simulator 直接 import Gate 0 发布的 Platform request/response/error types；Scenario Store 默认 unexpected-call failure，Journal 只记录调用次数、受控场景名和 Credential 的固定 canary 是否匹配，不保存明文。
- [ ] 覆盖：ACTIVE、错误 Secret、REVOKED、EXPIRED、Tenant 非 ACTIVE、Owner 明确 invalid-auth、401、5xx、超时、断连、非法 JSON、重复字段、空字段、2xx 却返回非 CLIENT tenant type、重复/超量 Scope、超限 Body。
- [ ] 将 Simulator 加入 `GatewayAutoTestRuntime` 与 `GatewayAutoTestBase` 的 start/reset/verify 生命周期，并通过 `gateway.platform-credential.*` direct URI 注入。

### 5.2 Write authentication/client failures

- [ ] `ClientApiCredentialServerAuthenticationConverter` 测唯一 Bearer、大小写 Scheme、缺失、重复、空 Secret、额外空格、控制字符、逗号，以及 `min(Gateway 8 KiB, Owner-published PLT-I03 credential limit)` 的通过/超限边界；上限直接引用 Owner 合同常量或 validator，不复制数字。
- [ ] `PlatformCredentialValidationClientAutoTest` 验证 published wire contract、连接/读取 timeout、response size、严格 JSON 和零重试。
- [ ] `ClientApiCredentialAuthenticationAutoTest` 从真实 Gateway 端口验证 401 与 503 分类、通用错误消息、PLT-I03/Owner 调用次数和 Credential canary 不泄露；`ClientApiScopeAutoTest` 验证 exact Scope 成功、缺失 403，以及 identity invariant 缺失 503。成功认证不得在 Scope Filter 尚未生效时转发 Owner。
- [ ] 运行四个新测试并记录失败。

### 5.3 Implement the local port and typed identity

- [ ] 本地端口只暴露 Gateway 所需语义：

```java
public interface ReactiveClientApiCredentialValidationClient {
    Mono<ClientApiIdentity> validate(String opaqueCredential);
}

public record ClientApiIdentity(
    String tenantId,
    String clientId,
    String credentialId,
    Set<String> effectiveScopes
) implements GatewayAuthenticatedIdentity {
    @Override public String principalKey() { return credentialId; }
    @Override public Optional<String> tenantKey() { return Optional.of(tenantId); }
    @Override public String accessSubject() { return credentialId; }
    @Override public String toString() {
        return "ClientApiIdentity[validated=true, scopeCount=" + effectiveScopes.size() + "]";
    }
}
```

- [ ] `GatewayCredentialValidationConfiguration` 用 `HttpServiceProxyFactory(WebClientAdapter)` 建立并注入 Gate 0 的 exact reactive `@HttpExchange` Proxy；`PlatformClientApiCredentialValidationClient` 只能调用该接口并把 Owner typed DTO 转为本地 `ClientApiIdentity`，不得手写重复 URI/DTO。它验证固定 CLIENT tenant type、所有 ID、Scope 格式/数量和去重，不保留 Credential。
- [ ] Platform 明确返回 invalid-auth（Credential 未连接 ACTIVE CLIENT Tenant）映射为 401；HTTP 2xx identity DTO 中 `tenantType != CLIENT` 或字段非法表示 Owner 合同损坏，映射为 503。两类路径分别测试，不互相吞并。
- [ ] Converter 与 HTTP Client 共同执行 Owner 发布的 Credential 长度/字符合同；Gateway 8 KiB 是外层绝对 Header 预算，不得把它误当 PLT-I03 Credential 业务上限。
- [ ] `PlatformCredentialValidationProperties` 固定 service-id/direct-uri、connect timeout、response timeout 和 max response bytes；生产默认用私网服务发现，只有唯一 `auto` Profile 可启用 direct URI。
- [ ] `GatewayCredentialValidationConfiguration` 使用独立有界 ConnectionProvider/LoopResources/WebClient，关闭 retry，应用严格 JSON/Body 上限并创建上述 Owner Proxy；不要复用业务 Target Client。

### 5.4 Implement the dedicated Security Chain

- [ ] Converter 只从唯一 Authorization 提取短生命周期 Credential；Authentication Token 的 `toString()`、credentials erase 和异常路径均不得暴露值。
- [ ] Reactive Authentication Manager 调本地 validation port；无效认证映射为 unauthenticated，dependency/illegal response 映射为专属 unavailable exception。
- [ ] `ClientApiSecurityConfiguration` 固定 `@Order(Ordered.HIGHEST_PRECEDENCE + 2)`，位于两个 Operations Chain 后、fallback 前；matcher 同时要求 typed `channel=CLIENT_API`、`securityProfile=CLIENT_API_CREDENTIAL`、`authorizationMode=API_SCOPE`。显式配置 `AuthenticationWebFilter + Converter + Manager + NoOpServerSecurityContextRepository`，不创建 Session；链只要求认证成功，不在 Spring Authorization Manager 内检查 Scope。
- [ ] Authentication failure handler 把无效 Credential 映射为 401、dependency/illegal response exception 映射为 503；entry point/access-denied handler覆盖未认证和授权拒绝，但不能代替 Manager 的 failure handler。不得落入 Target 502/504，也不得回退 Operations JWT/匿名。
- [ ] typed identity global filter 增加 `ClientApiAuthenticationToken -> ClientApiIdentity` 映射。
- [ ] `ClientApiScopeGlobalFilter` 固定 +60，在 +50 authenticated Guard 后、Body 前精确匹配 `route.requiredScopeCode()`；Client API Route 缺少 `ClientApiIdentity` 返回 503，Scope 缺失返回 403，两者 Owner 零调用。Task 5 完成前不得形成“认证成功但未检查 Scope”的可运行中间态。

### 5.5 Verify Task 5

- [ ] 重跑 Task 5 全部聚焦测试；断言每请求最多一次 PLT-I03 调用、零自动重试。
- [ ] 扫描 Surefire 输出、应用日志和 test reports，确认 Credential canary 不出现。

## Task 6: 重建最小 Owner 请求与固定 Client API 响应合同

**Files:**

- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/ClientApiDownstreamIdentityGlobalFilter.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestPolicyWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsBoundaryWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/UpstreamRequestGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewayRoutePolicyValidator.java`
- Rename: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsResponseContractGlobalFilter.java` -> `GatewayResponseContractGlobalFilter.java`
- Add: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/ClientApiResponseHeaderPolicy.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedTerminalResponseHeaders.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/ClientApiTrustedContextAutoTest.java`
- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/ClientApiResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsAuthenticationCookieAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricDimensionsAutoTest.java`

### 6.1 Write failing request/response vertical tests

- [ ] 复用 Task 3 的 test-only Registry、Client Snapshot Fixture 和 Owner Simulator；本任务不得再建立第二套 Target/Route/Journal。
- [ ] 先写伪造 Header、最小下游三 Header、Authorization/Cookie/Tenant/Scope/Route/Channel/Real-IP 不下传，以及响应 Request ID/Set-Cookie/CORS 清理的失败测试；Owner 返回 `X-Internal-Test` 和未知 Header 时必须被删除。
- [ ] 对 8 个无内容约束的允许响应 Header 分别测试大小写不敏感保留；只允许 `201 Created` 保留一条以 `/api/v1/` 开始且无 scheme/authority/query/fragment/反斜线/控制字符的 ASCII `Location`。缺失、重复、跨域或格式非法的创建 `Location` 返回 `502`；非 201（包括真实 3xx）的 `Location` 被剥离。成功和业务错误响应都删除 `Set-Cookie`、全部 `Access-Control-*`、`Vary`、`X-Gateway-*`、`Forwarded`/`X-Forwarded-*`、`Server` 和未知 Header；增加 gzip `Content-Encoding + Content-Length` 传输测试，确认正文未改写时长度一致，任何正文改写都移除旧 Length 让 Netty 重算。
- [ ] 为 Gateway 本地 400/401/403/408/413/429/431/502/503/504 编写 RFC 9457 失败测试：`Content-Type=application/problem+json`，Body 只有 `type/title/detail/code/errors?`，不含 `status/requestId`，最终 Request ID 只在响应 Header；Owner 的合规 Problem Body 保持状态码与正文。

### 6.2 Implement request policy and ordering

- [ ] `GatewayRequestPolicyWebFilter` 对所有 Route 执行精确 Query allowlist；`OperationsBoundaryWebFilter` 只保留 Operations Origin/CORS/Preflight，不再独占 Query 检查。
- [ ] `GatewayRoutePolicyValidator` 的 forbidden set 改为任意 `x-gateway-*` 前缀，加上 `X-Entry-Channel`、代理、Request ID、Cookie/Set-Cookie；Client API Profile 强制 cookie DROP/NONE、`corsRequestHeaderNames` 为空和 required Scope/permission 互斥，并以 Snapshot 编译反例锁定。
- [ ] `UpstreamRequestGlobalFilter` 继续先清空再重建 allowlisted 业务 Header、Body 长度和最终 Request ID；只有非 Client API 的现有合同才加入 Route Code。它不下发 Authorization、Entry Channel、Real IP、Cookie 或 typed identity。
- [ ] `ClientApiDownstreamIdentityGlobalFilter` 固定 +400，在 Upstream Request 空白重建后只加入 `X-Gateway-Client-Id` 和 `X-Gateway-Credential-Id`。不要加入 Tenant、Scope、Route 或 Channel；Client API Route 缺少 `ClientApiIdentity` 时返回 503，不能 skip、NPE 或转发。
- [ ] 响应合同通用化时保留 Operations refresh Cookie 行为，并在同一 Task 更新 `GatewayMetricDimensionsAutoTest` 等全部直接构造/import consumer，不留下半完成 rename。`ClientApiResponseHeaderPolicy` 保留代码固定、大小写不敏感的八个普通 Header，并实现上节 `201 Location` 校验；删除其他 `Location`、`Set-Cookie`、内部/代理及未知 Owner Header，不添加 Operations CORS/`Vary: Origin`。Gateway 重建 Request ID、`Cache-Control: no-store` 和 `Pragma: no-cache`；Gateway 本地 Client API 错误通过共享错误写入器生成无 Body `status/requestId` 的 RFC 9457 Problem。不得从 Snapshot/Route 读取可放宽的响应 Header 集合，Gateway 本地错误不得被 Owner Header Policy 污染。

### 6.3 Verify Task 6

- [ ] 运行 `./mvnw -q -Dtest=ClientApiScopeAutoTest,ClientApiTrustedContextAutoTest,ClientApiResponseContractAutoTest test`。
- [ ] 重跑 `OperationsTrustedContextAutoTest,OperationsAuthenticationCookieAutoTest,OperationsResponseContractAutoTest,OperationsCorsAndBodyLimitAutoTest`，确认 Operations Header/Cookie/CORS 未回归。

## Task 7: 完成 Client API 纵向验收和可观测性

**Files:**

- Add: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/ClientApiGatewayAcceptanceAutoTest.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayAccessLogWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExceptionWebHandler.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricDimensionsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`

### 7.1 Add one real vertical acceptance matrix

- [ ] 通过随机端口 Gateway + Snapshot Simulator + Credential Simulator + Owner Simulator + real Redis 验证完整成功顺序：Ingress/IP Guard -> PLT-I03 -> typed identity -> Credential/Tenant Guard -> Scope -> Body -> Owner。
- [ ] 覆盖代表性 `400/401/403/404/408/413/429/431/502/503/504`，并逐个断言哪些 Simulator/Redis 阶段必须为零调用；Client API OPTIONS+ACRM/ACRH 必须 404、无 CORS/`Vary` 且 PLT-I03/Redis/Owner 全零调用。
- [ ] 覆盖客户端取消连接和所有异常终态，确认两个 Guard 阶段的 Lease 都释放、测试 Namespace 无残留。

### 7.2 Harden logs and metrics

- [ ] Access Log 使用 typed identity 的版本化 digest；增加低基数 Channel/Profile/Outcome，禁止原 Client/Tenant/Credential/Request ID 作为 Metric Tag。
- [ ] 用固定 Credential、Scope、Client/Tenant canary 扫描捕获日志、异常、Meter IDs 和 Simulator Journals，任何命中都使测试失败。
- [ ] PLT-I03 dependency exception 在 Security entry point 映射为 503；`GatewayExceptionWebHandler` 不得误判为业务 Target 502/504。

### 7.3 Verify Task 7

- [ ] 运行 `./mvnw -q -Dtest=ClientApiGatewayAcceptanceAutoTest,GatewayAccessLogAutoTest,GatewayMetricDimensionsAutoTest,GatewayErrorContractAutoTest test`。
- [ ] 重跑 `OperationsGatewayAcceptanceAutoTest`，确认 Operations 完整纵向链未回归。

## Task 8: 全库验证与状态交付

**Files:**

- Modify after implementation only: `api-gateway/README.md`
- Modify after implementation only: `agent-docs/CURRENT_IMPLEMENTATION.md`

### 8.1 Run proportional verification

- [ ] 先确认手工启动的 `mor-local` Redis 可用且 `PAYTECH_REDIS_PASSWORD` 只注入测试进程、不回显；不启动或重建容器。
- [ ] 在 `api-gateway` 运行 `./mvnw clean verify`。
- [ ] 在 `api-gateway` 运行 `node scripts/verify-gateway-boundaries.mjs`。
- [ ] 在 `api-gateway` 运行 `node scripts/verify-jacoco.mjs`。
- [ ] 在主工作区运行 `npm --prefix requirements-site test`。
- [ ] 分别运行主工作区和 `api-gateway` 的 `git diff --check`。
- [ ] 检查 Surefire/JaCoCo 报告、Simulator unexpected calls、Redis 测试 Namespace、Credential canary 和线程/连接资源释放。若完整套件仍有前置失败，逐项区分并记录，不能把聚焦测试冒充全库通过。

### 8.2 Update truthful status only

- [ ] `api-gateway/README.md` 记录实际实现类、配置和精确验证命令/结果；保留现有 overlap 修复说明。
- [ ] `CURRENT_IMPLEMENTATION.md` 只在证据充分时把 Gateway 代码部分改为 `integration-ready`；逐项重新核实 PLT-I03、Platform Route Key、Owner Adapter、Nginx 和受控数据的届时证据并记录 `ready/pending`，没有部署或运行证据的项目继续保持 `pending`，不得预写它们必然仍未就绪。
- [ ] 不改成 Deployed，不添加生产 Approved Target，不启用 ACTIVE Route。

## External Activation Checklist — not part of this Gateway plan

- [ ] Platform 独立任务发布 PLT-I03 typed artifact，并部署可发现、私网可达的 Provider；实际应用 Credential/Scope 手工 SQL，通过获批的 CCT→Platform 投影建立 ACTIVE CLIENT Tenant，再提供受控 Credential 生命周期。冻结最终 PLT-I03 Path/传输位置后，将其加入 Platform MVC metadata-only 日志规则，并以 Credential canary 自动测试证明请求、响应和异常日志不含 Credential。
- [ ] Platform 独立任务把 PDManer/SQL 唯一键、Compiler、管理 API 和测试统一改为 `(channel, method, external_path_template)`；Snapshot 配置校验按大小写不敏感前缀拒绝全部 `X-Gateway-*`，并显式拒绝 `X-Entry-Channel`、`X-Request-Id`、`X-Real-IP`、`Forwarded`/`X-Forwarded-*`、Cookie、`Set-Cookie` 和其他 Gateway 静态保留 Header。
- [ ] 首个业务 Owner 独立任务部署可发现、私网可达的隔离 Client API Adapter，并经接口/安全评审产出受控 Route Manifest：精确 `route_code + method + external/downstream path template + target/discovery/path root + required_scope_code + request header allowlist + query allowlist + body_limit_bytes + body_receive_timeout_millis + response_timeout_millis + 四维 Guard + Idempotency + 资源授权`。Client API 响应 Header 使用 Gateway 代码固定集合，不进入 Manifest；架构文档中的 `/api/v1/**` 或 `/v1/**` 只能视为示例。
- [ ] Nginx/部署独立任务先为每个既有公网入口覆盖固定 `X-Entry-Channel`（当前 Operations 为 `OPERATIONS`），再配置并部署 Client API 域名、TLS、单值 `X-Real-IP`、`X-Entry-Channel=CLIENT_API`、Request ID 校验/fallback 和私网 Gateway Listener；完成前禁止部署 Channel-aware Gateway。
- [ ] Owner 就绪后另做 Gateway Registry 变更，加入真实 Target，运行完整 Gateway 验证并 build/deploy 含该 Target 的 Gateway；此时 Platform Client API Target/Route 必须仍为 `DISABLED`。
- [ ] Platform 按受控 Manifest 写入 `DISABLED` Target/Route/Scope 数据并完成静态校验；不得使用未定义的 `inactive` 状态名。
- [ ] 首条 Route 切换前确认只有未分发的测试 Credential 可用并记录控制面提交时刻。受控把 Target/Route 切为 `ACTIVE` 后，逐个 Gateway 实例等待刷新成功、`lastSuccessAt` 晚于提交时刻、连续失败为零，且 `CLIENT_API` Target/Route Count 都从 0 收敛为 1；不能把管理 API 成功响应当作实例已切换。
- [ ] 收敛后才用测试 Credential 执行公开 HTTP smoke、错误矩阵与指标基数检查，并确认 Credential canary 不存在于 Nginx、Gateway、Platform 和 Owner 日志。全部成功后先撤销测试 Credential，再单独授权生产 Credential 发放与对外可用声明。
- [ ] 任一激活、收敛或 smoke 失败时先撤销测试 Credential，再把 Route/Target 改为 `DISABLED`；逐实例等待成功刷新且 `CLIENT_API` Count 回到 0 后，验证旧 Credential 失败且 Owner 零调用。Snapshot 刷新失败保留 Last Valid，禁止宣称控制面提交后“立即 Fail-Closed”。
- [ ] 上述 Count 证据只用于首条 Route 的 `0 -> 1 -> 0`。后续不改变 Count 的配置更新，在提供逐实例可核对的 Snapshot Revision/Fingerprint 前不得激活；回滚演练不依赖匿名/静态旁路。
