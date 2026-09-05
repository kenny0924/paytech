# Client API Gateway 合同决策

- Decision status: Confirmed
- Implementation status: Not started
- Deployment status: Not deployed
- Decision date: 2026-09-05
- Scope: `CLIENT_API` 外部入口；Route 冲突键加入 Channel 及支撑它的固定私有入口标记是共享迁移
- Supersedes:
  - `docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md` 第 2.1、6.3、9.2、9.4、10、12.2、13、15.2、17.2、18.2 节及第 19 节结论 2 中，与跨 Channel 全局 Method + Path 冲突、Client API Request ID、Client API 下游可信 Header 和失败码冲突的部分
  - `docs/superpowers/specs/2026-09-03-api-gateway-platform-support-requirements.md` 中无 Channel 的 Route lookup/冲突域与 Client API 旧下游上下文描述
  - `docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md` 第 3 节“Gateway 最外层 Filter 只接受 X-Real-IP”的绝对表述；共享入口增加固定 Channel 标记，Client API 再增加本决策的 Request ID 例外
  - `docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md` Step 2 中 Route 的 `(http_method, external_path_template)` 全局唯一键；后继唯一键为 `(channel, http_method, external_path_template)`

## 1. 背景与边界

Client API 面向 Client 的后端系统，是一种机器调用入口。它不是新的 Java 服务，也不改变六个核心系统的事实所有权。Gateway 把每条 Client API Route 直接转发到该业务能力 Owner 的隔离外部适配器；Owner 继续完成 Client 资源归属、业务权限、状态机、事务和幂等判断。

Operations 使用人工 Access JWT，Client API 使用 Tenant-owned opaque API Credential。两者的认证材料、生命周期和失败语义不同，不能为了代码表面统一而共用认证算法。Gateway 只统一可信入口、路由、策略、Guard、转发和观察管线的骨架。

## 2. 已确认决定

### D1. 调用方只提交最小外部合同

Client 调用方只需要提交：

- 唯一的 `Authorization: Bearer <opaque credential>`；
- 业务 Path、Query、Body 和允许的业务 Header；
- 只有业务命令要求时才提交 `Idempotency-Key`；
- 可选的单值 `X-Request-Id`。

调用方不得提交或选择 Client ID、Tenant ID、Credential ID、Scope、Route Code、Channel、Actor Type/Subject 或其他 `X-Gateway-*` 可信上下文。外部资源路径隐含当前 Client，不使用调用方可写的 Client ID 选择 Owner；`/v1/orders/{orderId}` 只用于说明这种资源形态，不是已冻结 Route Path。

### D2. 域名决定 Channel，Nginx 写入私有入口标记

Client API 使用专用域名。Nginx 根据命中的 Server/Host 覆盖写入唯一 `X-Entry-Channel: CLIENT_API`，不得保留或追加外部同名值。Gateway 只接受私有 Nginx 网络的请求，读取并验证该标记后将其删除，不向下游转发。

`X-Entry-Channel` 是入口基础设施协议，不是 `X-Gateway-Channel`，也不是调用方参数。

由于 Route Key 的 Channel 化作用于共享 Catalog，Channel-aware Gateway 部署前，Nginx 的每个既有公网 `server`/`location` 都必须先覆盖写入自己的固定 Channel；当前 Operations 入口写入 `OPERATIONS`。这只增加 Nginx 到 Gateway 的私有标记，不改变 Operations 的外部请求、JWT、CORS、下游 Header 或 Request ID 合同。不能让旧 Nginx 与强制 Channel 的新 Gateway 混合部署。

### D3. Route 身份加入可信 Channel

Gateway Route lookup 和模板冲突域改为：

```text
(trusted channel, HTTP method, external path)
```

同一 Channel 内的精确重复和可匹配模板重叠继续拒绝；不同 Channel 可以存在相同 Method/Path。`route_code` 继续保持全局稳定且唯一，用于 Gateway 内部 Guard、日志、指标和诊断，但 Client API 不接收 `X-Gateway-Route-Code`。

该键变化会同步影响 Platform Snapshot 编译、数据库唯一约束和 Gateway 运行时 Catalog，并要求所有既有公网 Nginx 入口向 Gateway 覆盖自己的固定私有 Channel 标记。Client API 的外部 Request ID、Credential 认证和最小下游 Header 语义只适用于 `CLIENT_API`；Operations 的外部请求、JWT、CORS、下游 Header 和 Request ID 合同不变。

### D4. Client API 使用独立 Credential 认证

Client API Route 固定为：

```text
channel              = CLIENT_API
security_profile     = CLIENT_API_CREDENTIAL
authorization_mode   = API_SCOPE
request_cookie_mode  = DROP
response_cookie_mode = NONE
```

这些值即使存在于 Snapshot Schema 中，也不是每条 Client API Route 可自由选择的参数。Gateway 不允许降级为 JWT、匿名、HMAC 请求签名或其他 Profile。

Gateway 通过 Platform Owner 发布的 reactive typed `@HttpExchange` PLT-I03 合同校验 opaque Credential，不复制 DTO 或手写 URI；Credential 只能位于受控 Header 或 Body，不能进入 Path/Query。PLT-I03 必须验证 PHC verifier，将 Credential JOIN 到同一 ACTIVE `CLIENT` Tenant，并只返回该 Credential 当前 effective ACTIVE Scope，以及可信 `tenant_id`、`tenant_type=CLIENT`、`owner_ref_id` 和 `credential_id`。Gateway 不向 PLT-I03 提交可覆盖的 Tenant、Client 或 required Scope；Gateway 自己把 Route 的 `required_scope_code` 与返回 Scope 做精确匹配。

Client API 不启用 CORS 或 Preflight；实际 `OPTIONS` 不得通过 `Access-Control-Request-Method` 借用业务 GET/POST Route，也不得调用 PLT-I03、Redis 或 Owner。

### D5. Gateway 内使用完整身份，下游只收到最小身份

Gateway 内部 typed identity 保留：

```text
tenantId + tenantType=CLIENT + clientId(ownerRefId) + credentialId + effectiveScopes
```

其中 `credentialId` 用于认证主体 Guard，`tenantId` 用于 Tenant Guard，`effectiveScopes` 只用于当前 Route 的 Scope 决策。向 Owner Client API Adapter 转发时只注入：

```text
X-Request-Id
X-Gateway-Client-Id
X-Gateway-Credential-Id
```

不转发 `Authorization`、opaque Credential、Key Prefix/Verifier、Scope、Tenant ID/Type、Route Code、Channel、Actor、Realm、Session、Context 或 Tenant Member Header。`X-Gateway-Client-Id` 的值只能是 PLT-I03 在确认 Tenant Type 为 `CLIENT` 后返回的 `owner_ref_id`。

### D6. Guard 使用可信身份并 Fail-Closed

认证前执行 `SHARED_ROUTE` 与 `CLIENT_IP` Guard；认证成功后以 `credential_id` 执行 `AUTHENTICATED_PRINCIPAL` Guard，并以 Platform `tenant_id` 执行 `TENANT` Guard。Guard Key 不读取调用方 Header、Query、Path 或 Body 中的身份值。

Redis 不可用统一返回 `503`；V1 不提供 Tenant 特定 Override，也不自动重试。

### D7. Request ID 可以由外部生成，但必须经过两层校验

Client 可以为跨系统关联生成可选 `X-Request-Id`。合法值必须是唯一 Header，长度 1 至 64，首字符为 ASCII 字母或数字，其余字符只允许 ASCII 字母、数字、`.`、`_`、`-`。

Nginx 保留合法值；缺失、重复或非法时改用自己的 `$request_id`。Gateway 再按同一规则复核，必要时生成新值，并把最终唯一值传播到下游和响应。Request ID 不是认证、授权、Guard 或业务幂等依据；每个 HTTP 尝试应使用新的 Request ID，同一个业务命令重试继续复用原 `Idempotency-Key`。

该外部 Request ID 规则只适用于 `CLIENT_API`。Operations 继续忽略外部 `X-Request-Id` 并由 Gateway 生成新值，保持当前合同。

### D8. 失败、重试与观察合同固定

- Route 不存在：`404`；非法 Path/Query/Header：`400`；除 Client API Authorization 外的受限 Header 超过绝对预算：`431`；
- Credential 缺失、格式错误、无效、撤销、过期或所属 Tenant 非 ACTIVE：统一 `401`；
- Credential 有效但缺少 Route required Scope：`403`；
- Rate/Concurrency：`429`；Body 过大：`413`；Body 接收超时：`408`；
- PLT-I03 或 Redis 技术不可用/结果歧义：`503`；Approved Target 不存在：`503`；
- 已匹配 Client API Route 在 Guard、Scope 或下游注入阶段缺少 typed identity：`503`；
- 非法上游响应：`502`；上游响应超时：`504`。

Gateway 不自动重试 PLT-I03 或业务命令。认证失败响应不泄露 Credential、Tenant 或具体失败原因；下游业务错误保持 Owner 的状态码与业务响应体。

日志允许记录 Request ID、内部 Route Code、Client ID/Credential ID 的安全摘要、状态和耗时，不记录 Credential、Authorization、Scope、请求体或 Query 值。Metric 只使用低基数 Channel/Profile/Route/Outcome/Status/Guard 维度，禁止把 Tenant、Client、Credential、Path Variable 作为 Label。

V1 不扩张 Platform Snapshot 来配置业务响应 Header。Client API 只保留代码固定且大小写不敏感的 Owner 响应 Header 集合：`Content-Type`、`Content-Length`、`Content-Encoding`、`Content-Language`、`Content-Disposition`、`ETag`、`Last-Modified`、`Retry-After`；另仅允许 `201 Created` 携带一条经校验的 `Location`：必须是以 `/api/v1/` 开始、无 scheme/authority/query/fragment/反斜线/控制字符的 ASCII absolute-path reference。非法创建 `Location` 视为 `502` 上游合同错误；其他状态的 `Location`、`Set-Cookie`、内部/代理 Header 和未知 Header 全部删除。Gateway 自己重建 `X-Request-Id`、`Cache-Control: no-store` 和 `Pragma: no-cache`；未来真实消费者需要新增 Header 时，必须通过 Gateway 代码和安全测试评审，不得由 Route 配置任意放宽。该窄例外首先由 [Commerce Client API Hosted Checkout V1](./2026-09-05-commerce-client-api-v1.md) 使用。

Gateway 本地产生的 Client API 4xx/5xx 使用 RFC 9457 `application/problem+json`，Body 只含 `type`、`title`、`detail`、稳定扩展 `code` 和可选 `errors`；不复制 HTTP `status` 或 Request ID。最终 Request ID 只在 `X-Request-Id` Header 中出现。外部错误码使用 `UNAUTHENTICATED`、`INSUFFICIENT_SCOPE`、`RATE_LIMIT_EXCEEDED`、`DEPENDENCY_UNAVAILABLE` 等业务无关协议码，不暴露 `GATEWAY_*` 内部实现命名；Owner 错误 Body 只要满足同一外部合同即保持原状态码与 Body。

### D9. Route 激活晚于代码完成

真实 Client API Route 的启用顺序固定为：

```text
Platform 发布并实现 PLT-I03 typed contract、Credential 数据与 Channel-aware Route 控制面
-> Owner Client API Adapter 在私网可发现、可达，并冻结受控 Route Manifest
-> Nginx 为所有既有公网入口覆盖固定 Channel，并准备 Client API 域名与 Request ID 协议
-> Approved Target Registry 加入真实 Owner Target，Gateway Client API 能力完成全量验证和构建
-> 部署含该 Target 的 Gateway，此时没有 ACTIVE Client API Target/Route
-> Platform 写入 DISABLED Target/Route/Scope 数据并完成静态核对
-> 受控切换 Target 与首条 Route 为 ACTIVE，等待每个 Gateway 实例成功加载可证明包含该首条 Route 的新 Snapshot
-> 使用尚未分发的测试 Credential 完成公开 HTTP smoke、指标和跨链路日志脱敏检查
-> 任一激活、收敛或 smoke 步骤失败，先撤销测试 Credential，再将 Target 与 Route 都改为 DISABLED，并等待每个实例成功加载排除它们的新 Snapshot
```

当前 Snapshot 没有业务 Revision，刷新失败还会保留 Last Valid。首条 Route 激活只能以“变更后的 `lastSuccessAt`、刷新成功且每实例 `CLIENT_API` Target/Route Count 从 0 收敛到 1”证明加载；回滚必须等待 Count 回到 0。后续不能产生唯一 Count 变化的修改，在增加可核对 Revision/Fingerprint 前不得按相同办法激活。测试 Credential 在 smoke 成功且确认收敛前不得分发生产 Credential。Gateway 使用严格 Loopback Simulator 完成自动测试，只能证明 Gateway 代码可集成；在上述前置全部完成前，不得声明 Client API 已可用或已部署。

## 3. 明确不做

- 不新建 `client-api-service`，不让 Gateway 编排核心业务；
- 不统一 Operations JWT 与 Client API Credential 的认证算法；
- 不把 Tenant/Scope/Route/Channel 下发给 Owner 作为授权捷径；
- 不把业务幂等改成 Request ID，也不在 Gateway 自动重试命令；
- 不预注册没有真实 Owner Adapter 的生产 Target；
- 不在本决策中实现 Platform PLT-I03、Credential 管理/DDL、Owner 业务 API、Nginx 生产配置或部署数据。

## 4. 权威与投影

- 完整 Client API Gateway 合同由 [`2026-09-05-client-api-gateway-design.md`](../../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md) 持有。
- Gateway-only 编码顺序与验收由 [`2026-09-05-client-api-gateway-implementation.md`](../../docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md) 持有。
- `APPLICATION_ARCHITECTURE.md`、需求目录和全局序列只保留必要薄投影。
- 当前实现、验证与部署差距只记录在 `CURRENT_IMPLEMENTATION.md`。
