# API Gateway 对 Platform Support 的需求与接口边界

日期：2026-09-03
状态：当前协作契约
范围：公共 API 动态配置、Console 权限协作及 Gateway 运行时配置消费

> 2026-09-05 Client API 后继：[Client API Gateway 合同决策](../../../agent-docs/decisions/2026-09-05-client-api-gateway-contract.md)
> 及其[完整设计](./2026-09-05-client-api-gateway-design.md)把 Route lookup/冲突域改为
> `(trusted channel, method, path)`，并冻结 Client API Credential、Scope、最小下游 Header 和 Request ID 合同。
> 本文中无 Channel 的 Method + Path、跨 Channel 冲突及 Client API 旧上下文表述按该后继解释；Operations 不变。

## 1. 目的

本文集中说明两件事：

1. API Gateway 自身需要实现和持有的能力；
2. API Gateway、Operations BFF 及其他入口适配器需要 Platform Support 提供的能力。

本文不把 Platform Support 变成公网 BFF，也不把业务授权搬入 Gateway。公共 Route、Target 和 Guard 配置由 Platform
Support 持久化；Gateway 消费完整运行时 Snapshot 并执行入口控制；BFF 和核心系统继续执行当前权限及资源授权。

## 2. 两类调用链必须分开

### 2.1 Gateway 自身的内部控制面调用

Gateway 为了启动和刷新自身运行时配置，直接调用 Platform Support：

```text
API Gateway background refresher
  -> deployment-private HTTP
    -> Platform Support PLT-I20 public Snapshot query
```

这是一条服务间控制面链路，不承载浏览器业务请求，不经过 BFF，也不注册到公共 Route Catalog。它只允许 Gateway
后台刷新任务使用。当前部署契约是私有网络 HTTP；不要求 mTLS、X.509 应用证书、调用方 Header、浏览器 Access Token
或 API Credential。网络与部署边界必须保证该 Endpoint 不可从公网到达。

### 2.2 公网 Operations 管理调用

运营人员在页面上新增 API、调整限流或维护菜单/按钮时，调用链必须是：

```text
operations-console-web
  -> API Gateway
    -> operations-console-bff
      -> Platform Support internal management / IAM API
```

Gateway 只把已配置的公网请求转发给 `operations-console-bff`，不能把浏览器请求直接转发给 Platform Support。
Operations BFF 负责页面协议适配，并携带 Gateway 已验证的可信身份上下文调用 Platform Support；Platform Support
再次校验当前 Session、Account、Channel 和 Permission。隐藏菜单或按钮不能替代后端授权。

Client Console 与 Partner Console 遵循相同原则，分别经过自己的 BFF。Checkout、Client API 和 Provider Callback
按 Route 指向已经批准的外部适配器，不能借用 Platform Support 的内部管理接口。

## 3. Gateway 负责什么

Gateway 负责以下运行时能力：

- 启动时立即拉取全量 Snapshot，首次成功前 Readiness Down、Liveness Up；
- 约每 5 秒以单飞、非阻塞、受限 HTTP 请求刷新配置；
- 对完整 Snapshot 做 Schema、Target、Route、Path、Header、Query、Cookie、Security Profile、Guard、Body 和 Timeout
  校验；
- 先完整编译，再原子替换不可变运行时 Snapshot；
- 每个请求只捕获一次 Snapshot 和 Route，保证在途请求一致性；
- 精确匹配 HTTP Method 与 Path，未注册 API 返回本地 `404`，不调用任何下游；
- 执行最小身份验证、Route Scope、精确请求头/Query 白名单、Body 限制、超时、限流、并发控制、请求 ID 和访问日志；
- 持有 Approved Target Registry、静态 Security Profile、可信下游协议和所有绝对安全上限；
- 在 Redis 中只保存 Bucket、Counter、Lease 和 TTL 等运行状态，不把 Redis 当作配置权威。

Gateway 不负责：

- 账号密码登录、Session 创建、Token 签发或刷新；
- Console 菜单/按钮可见性决策；
- `PLT-I02` 人员权限决策；
- 核心业务资源归属和业务状态授权；
- Route、Target、Permission 或 Console Resource 的持久化管理；
- 跨系统业务编排。

## 4. Platform Support 必须提供的运行时 Snapshot API

### 4.1 接口

```http
GET /platform-support/gateway-config/v1/public-snapshot
```

通过 `platform-support-api` 轻量模块暴露 Java 契约 `GatewaySnapshotQueryApi` 及 typed DTO。Gateway 直接依赖该 API
模块，不能复制另一套生产 DTO。

每次成功都返回 `200` 和完整 Snapshot；不使用 ETag、`If-None-Match` 或 `304`。Snapshot 至少包含：

- `schemaVersion`；
- 当前 ACTIVE Target 列表；
- 当前有效 Route 列表；
- 每条 Route 的稳定 `routeCode`、Channel、HTTP Method、外部 Path、下游 Path 和 `targetServiceCode`；
- Security Profile、Authorization Mode、Permission/Scope 形状；
- Cookie 模式、精确请求头列表、精确 Query Parameter 列表；
- Body 大小、Body 接收超时、下游响应超时；
- Rate Limit 与 Concurrency Guard。

`routes=[]` 是合法的完整配置。Gateway 收到后必须切换为空目录、保持 Ready，并让所有公共请求返回 `404`；不能保留
旧 Route。

### 4.2 Platform 侧保证

Platform Support 必须：

- 在一致性只读事务中读取当前 Target、Route 和所有子配置；
- 在返回前完整校验，不能返回部分 Catalog；
- 使用固定稳定顺序组装 DTO；
- 保证 Target 与 Route Channel 一致、Route Method + Path 全局无歧义；
- 解析并返回稳定 Permission Code，但不把完整 IAM Catalog 放进 Snapshot；
- 使用共享异常处理返回稳定错误；数据库、编译或内部错误返回 `500 INTERNAL_ERROR`。

Gateway 将连接失败、超时、任意 `5xx`、非法 Body、超限 Body 和未知 Schema Version 都视为刷新失败。首次成功后发生
刷新失败时继续使用 Last Valid Snapshot 并告警；冷启动尚无 Snapshot 时保持 Not Ready。

## 5. Platform Support 必须提供的 Operations 管理能力

这些接口是 Platform Support 的内部应用能力，只能由 `operations-console-bff` 调用。它们不是 Gateway 后台刷新接口，
也不能直接暴露给浏览器。

### 5.1 Target 管理

```http
GET   /gateway-admin/v1/target-services
GET   /gateway-admin/v1/target-services/{targetServiceCode}
PATCH /gateway-admin/v1/target-services/{targetServiceCode}/status
```

普通页面不能创建或修改 Target 身份。新增 `targetServiceCode` 必须先完成架构与安全审核，同时加入 Gateway 的
Approved Target Registry 和 Platform 的受控 Seed/Migration。当前 Operations API 的 Target 是
`operations-console-bff`。

### 5.2 Route 管理

```http
GET   /gateway-admin/v1/routes
GET   /gateway-admin/v1/routes/{routeCode}
POST  /gateway-admin/v1/routes
PUT   /gateway-admin/v1/routes/{routeCode}
PATCH /gateway-admin/v1/routes/{routeCode}/status
```

开发人员可以通过授权页面或受控 DML 新增 API。新增一条 Route 后，Gateway 不需要修改 Java 代码；下一次成功拉取
Snapshot 后即可识别该 API。前提是：

- `targetServiceCode` 已在双方批准的 Target Registry 中；
- 对应 BFF/后端接口已部署；
- Route 使用 Gateway 已预编译支持的 Security Profile、Authorization Mode 和 Guard 类型；
- 配置没有超过 Gateway 静态绝对上限，也没有 Path 或协议冲突。

Route 创建/修改必须一次提交完整 Root、Header、Query、Guard、Cookie、Body 和 Timeout 配置，在一个本地事务中全量
校验并整体替换子集合。上线即生效：事务提交后，不再增加 Publish、Release、Snapshot Version 或人工二次发布流程。

### 5.3 Console Resource 与 IAM

Platform Support 还需向各 Console BFF 提供：

- 当前账号、Session、Realm、Channel、Tenant Member、Role 和 Permission 管理/查询能力；
- `PLT-I02` 当前权限校验；
- 按当前身份与 Channel 过滤后的 MENU/BUTTON 导航树；
- Console Resource 与 Permission、Route 的绑定管理。

菜单和按钮是用户体验控制；每一次受保护 API 调用仍由 BFF 调用 `PLT-I02` 校验当前 Permission。Gateway 只验证
Token 的最小 Claim 和 Route Scope，不根据菜单或按钮做授权。

## 6. 新增 Operations API 的端到端流程

```text
1. 开发并部署 operations-console-bff Endpoint
2. 确认 targetServiceCode=operations-console-bff 已获批准
3. 在 Platform Support 页面或受控 DML 新增 ACTIVE Route 及完整 Guard
4. 如需页面权限，维护 Permission、Console Resource 与 Route 绑定
5. Platform 事务提交即生效
6. Gateway 下一轮拉取完整 Snapshot
7. Gateway 完整校验、编译并原子切换
8. 新请求命中 Route 后转发到 operations-console-bff
9. BFF 对受保护请求调用 PLT-I02，再执行业务适配或调用核心系统
```

只有新增一个全新的 Target、Security Profile、认证协议、Guard 类型或突破静态安全上限时，才需要修改 Gateway 代码。
普通新增 API、调整容量/速率/并发/超时、启停 Route 或调整菜单按钮，不需要修改 Gateway 代码。

## 7. 失败与安全边界

- 未配置 Method + Path：Gateway `404`，零下游调用；
- Snapshot 非法：整份拒绝，不允许部分生效；
- Platform 暂时不可达：有 Last Valid 时继续服务，无 Last Valid 时 Not Ready；
- Platform 返回合法空 Route：立即清空公共目录，不能回退静态 Route；
- 动态切换后不合并静态和动态 Catalog，也不保留隐藏静态管理后门；
- BFF 的当前 Permission 拒绝返回 `403`；PLT-I02 不可用时 Fail-Closed，并返回稳定 `503`；
- 管理接口不能相信浏览器声明的 Actor、Permission、Route 或 Tenant；
- Gateway、BFF 和浏览器都不能直读 Platform Support 数据库。

## 8. 当前交付拆分

Platform Support 当前应交付：

- `platform-support-api` 中唯一的 Snapshot Java 契约和 DTO；
- PLT-I20 全量 Snapshot Provider；
- 后续 Target、Route、Console Resource 管理 API；
- PLT-I02、Navigation 与对应 IAM 管理能力；
- 初始 Operations Target、Route、Permission 和 Console Resource 的受控 Seed。

Gateway 当前应交付：

- PLT-I20 非阻塞客户端；
- 启动/周期刷新、Readiness、Last Valid、单飞和可观测性；
- Snapshot Validator、Compiler、Approved Target Registry 与原子 Runtime Catalog；
- 将请求匹配、过滤、Guard、Target 和 Timeout 全部切换到单次捕获的动态 Route；
- 删除动态切换后的静态运行时 Fallback，同时保持现有 Operations 登录、刷新、退出的等价行为。

Operations BFF 当前不在本次 Gateway 实现范围内；未来实现时必须作为所有 Operations 公网业务请求进入 Platform
Support 和核心系统之前的适配与授权边界。

## 9. 权威关系

本文是 Gateway 与 Platform Support 团队之间的聚焦协作清单。字段约束、路由矩阵、管理事务和完整失败语义仍以
[统一公共 API 动态控制面设计](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)为准；
人工身份、Tenant IAM 和 BFF 授权仍以
[统一人工认证设计](./2026-08-30-platform-unified-human-authentication-design.md)为准。
