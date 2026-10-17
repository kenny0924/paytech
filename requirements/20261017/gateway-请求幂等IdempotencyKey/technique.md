# Gateway 处理 Idempotency-Key — 技术方案

> 第 1—4 节为阶段 2 现状差异与影响分析（2026-10-17 已确认：D1—D7“其余没问题”，D8 见下）；第 5—12 节为阶段 3 方案（评审稿）。

## 1. 现状核对

### 1.1 Gateway

| 项 | 现状 | 依据 |
|---|---|---|
| 过滤器顺序 | 认证 +40、限流 +50、Client API Scope +60、请求体读取 +100、上游超时 +200、上游请求 +300；顺序由合同测试固定 | `GatewayGlobalFilterOrder.java:8-24`；`GatewayFilterOrderContractAutoTest` |
| 请求体 | +100 已完整读入可重放缓冲（受路由 Body 上限与接收超时约束），结束后清零 | `RequestBodyLimitGlobalFilter.java:33-83` |
| 响应捕获 | 已有先例：写出前包装响应，只缓冲下游错误体（64 KiB）后回写；`CLIENT_RESPONSE_ATTR` 存在即“下游确实返回了” | `OwnerProblemResponseGlobalFilter.java:44-144` |
| 无下游响应 | 连接失败、超时由 `GatewayExceptionWebHandler` 经 `GatewayErrorWriter` 写 503 / 504 / 502，不经包装的上游写出 | `GatewayUpstreamFailureClassifier.java`；`GatewayExceptionWebHandler.java:52-103` |
| Client API 响应头 | 白名单（Content-*、ETag、Last-Modified、Retry-After，201 的相对 Location），随后统一重写 `X-Request-Id`、`Cache-Control: no-store` 等 | `ClientApiResponseHeaderPolicy.java:13-45`；`TrustedTerminalResponseHeaders.java:20-41` |
| 错误码 | `GatewayErrorCode` 的 Client API 映射是穷举 switch；`GatewayErrorWriter` 已支持 `Retry-After`，总用当前请求的 Request ID | `GatewayErrorCode.java:7-66`；`GatewayErrorWriter.java:48-108` |
| Redis | 只有 Lua 执行器（返回 Long 列表），故障转为依赖不可用；并发租约（UUID 持有者、TTL、按持有者释放）可借鉴 | `ReactiveRedisScriptExecutor.java`；`RedisReactiveConcurrencyLimiter.java:73-90` |
| 调用方身份 | `AUTHENTICATED_IDENTITY`：Client API 为 `(tenantId, clientId, credentialId, scopes)`，`principalKey` 是凭证 | `GatewayExchangeAttributes.java:24` |
| 访问日志 / 指标 | outcome 枚举 14 个，指标测试按 `values()` 自动放行新值；README 列了 outcome 名 | `GatewayOutcome.java`；`api-gateway/README.md:104` |
| 快照解码 | 宽松（未知字段忽略）；`GatewayRouteSnapshot` 是 Platform API JAR 里的共享 DTO，约 40 个测试按位置构造 | `GatewaySnapshotConfiguration.java:85-91` |

### 1.2 Platform / Operations 路由配置

| 项 | 现状 |
|---|---|
| 表 | `t_gateway_route` 枚举用 `VARCHAR(64) NOT NULL`，无约束，`version` CAS；初始化 DML 13 处 INSERT 列清单；测试 DDL 另一份 |
| 模型 | PDManer 由脚本生成（`scripts/pdmaner/platform-support/manifests/06-gateway.mjs`），另有测试 oracle |
| 代码 | DataObject、两个 Mapper XML（快照查询、管理增改查）、Repository、Domain 命令与视图、编译器（`GatewayConfigurationCompiler` 校验与兼容规则）、管理服务与审计字段枚举、API 模型（创建、更新 PUT、详情、能力）、快照 DTO 与转换器 |
| 管理请求 | Platform 与 Operations BFF 对浏览器请求都拒绝未知字段 |
| Operations BFF | 代码白名单（Header 用途等）、请求响应转换、模型与浏览器边界测试 JSON |
| Operations Web | 路由类型、能力、编辑器字段与表单 |
| 脚本 | `sync_route_headers.py` 用固定字段列表重建 PUT 请求体，会丢掉新字段 |
| 他人改动 | Platform、Operations BFF / Web 工作区有其他会话大量未提交改动（通知、风控变量、Webhook 管理），本需求改动的文件须逐个核对 |

### 1.3 Commerce

| 项 | 现状 |
|---|---|
| 合同 | 6 个 POST 在 `paytech-commerce-client-api` 以必填 `@RequestHeader(IDEMPOTENCY_KEY)` 声明（常量来自 Foundation），只被 Commerce 自己使用 |
| 实现 | 6 个 Controller 签名带该参数但不读值 |
| OpenAPI | `client-v1.yaml` 3 处 `required: true` 与描述 |
| 测试 | 若干测试基类发送该 Header，没有“缺失返回 400”的断言 |
| 旧组件 | `paytech-commerce-web/pom.xml` 依赖与 `command-idempotency.enabled` 配置仍在，无代码引用；测试 POM 覆盖率包含一个空包 |
| 工作区 | Commerce、Gateway 无他人未提交改动 |

## 2. 差异（要新增或改动的）

| 编号 | 改动 | 系统 |
|---|---|---|
| G1 | `t_gateway_route.idempotency_mode`（`NONE`/`REQUIRED`，默认 `NONE`）：PDManer 生成源、Schema、变更 SQL、初始化 DML、测试 DDL | Platform 数据库 |
| G2 | 路由模型全链路加字段，编译器校验兼容规则（见 D3），快照下发，审计字段 | Platform |
| G3 | BFF 白名单、转换与模型；Web 路由编辑器加“请求幂等”选择 | Operations BFF / Web |
| G4 | 快照与运行时路由加字段（缺失按 `NONE`）；新过滤器（请求体读取之后、上游请求之前）：校验 Key、摘要、Redis 加锁、下发、捕获响应、记录、重放或拒绝；无下游响应时释放；4 个错误码与 outcome | Gateway |
| G5 | 去掉 6 个 POST 的必填 Key 参数、OpenAPI 必填、旧组件依赖与配置；测试基类不再发送 | Commerce |
| G6 | test：3 条写路由设为 `REQUIRED` 并停止转发 Key；`sync_route_headers.py`、`register_client_api.py` 带上新字段；验收脚本加幂等场景 | 脚本 / test |
| G7 | 文档：Commerce 设计第 7 节、Client API Gateway 合同决定、应用架构、Gateway README、台账 | 文档 |

## 3. 影响与风险

- **改动面大于预期**：Platform 路由字段牵动 DDL、PDManer、管理接口、BFF、Web 和约 40 个 Gateway 测试的快照构造。工作量主要在这里，幂等过滤器本身相对集中。
- **共享文件冲突**：Platform、Operations 的多个目标文件同时有他人未提交改动，必须按“HEAD + 本需求改动”的方式提交，并事先与 Platform（code-mor-87）、Operations 会话协调。
- **部署顺序**：BFF、Web 对未知字段严格。新 BFF 对旧 Platform、新 Web 对旧 BFF 都会 400，必须先 Platform 后 BFF、Web。Gateway 对缺字段按 `NONE` 时不受顺序约束。
- **Request ID**：错误格式决定要求 Problem 体 `requestId` 与响应头逐字一致，重放错误响应需要改写响应体里的 `requestId`。
- **存储敏感数据**：建单响应含结账公开链接，Redis 明文保存 72 小时（阶段 1 Q7 已接受）。
- **业务兜底**：无下游响应时释放 Key（Q6），依赖 Commerce 的业务防重。3 个接口都有防重，Customer 写接口不在 Client API 路由内。

## 4. 已确认的决定

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 重放时的 Request ID 与时间 | 响应头用**本次**请求的 `X-Request-Id`；重放 Problem 体时把 `requestId` 改写为本次值，`timestamp` 保留首次（表示错误发生时间）；成功体原样 |
| D2 | 管理接口更新路由时不传该字段怎么办 | 保持当前值（避免按固定字段列表 PUT 的脚本把 `REQUIRED` 悄悄降级）；创建时不传为 `NONE` |
| D3 | `REQUIRED` 允许配在哪些路由 | 本期只允许 **CLIENT_API 通道的非 GET 路由**；且该路由不得再把 `idempotency-key` 配为转发（Gateway 消费掉它）。其他通道等迁移时再放开 |
| D4 | 响应体超过 64 KiB | 照常返回给客户端，不记录，释放 Key 并记 WARN（Commerce 回执远小于该值） |
| D5 | Redis 不可用 | `REQUIRED` 路由 503 `GATEWAY_DEPENDENCY_UNAVAILABLE`，不绕过 |
| D6 | 部署顺序 | Commerce（不再要求该 Header）→ Platform（DDL + 代码）→ Gateway → Operations BFF → Web → 路由改为 `REQUIRED` 并停止转发 |
| D7 | Operations 后台 | 本期路由详情显示该字段、编辑页可设置（与其他路由字段同一方式） |
| D8 | 存储选型（用户询问为何用 Redis、担心数亿数据） | **本期先存 Redis；以后肯定要迁到数据库**（用户 2026-10-17）。Redis 只保存 72 小时窗口内的记录，内存约等于“72 小时写请求数 × 每条约 1.5—2 KB”。本期做到：响应体压缩；存储层为可替换接口、连接可单独配置；`noeviction` 与内存 / 记录数监控。迁库作为已登记的后续事项（见第 12 节） |

# 阶段 3：方案、接口与数据设计

## 5. 总体流程

```text
请求 → 认证 / 限流 / Scope（既有）→ 读请求体（+100，既有）
     → 幂等过滤器（新，+150，仅 idempotencyMode=REQUIRED 的路由）
         ① 校验 Key：缺失 400 IDEMPOTENCY_KEY_REQUIRED；多值或格式非法 400 INVALID_IDEMPOTENCY_KEY
         ② 计算摘要 → Redis 原子占位（claim）
            ├ 无记录：写入 PENDING（持有者 = 本次请求 UUID）→ 继续下发
            ├ PENDING 且摘要相同：409 IDEMPOTENCY_REQUEST_IN_PROGRESS + Retry-After: 3
            ├ 任一状态但摘要不同：409 IDEMPOTENCY_KEY_REUSED
            └ COMPLETED 且摘要相同：重放记录，不下发
     → 上游超时 / 转发（既有）
     → 下游返回：捕获状态、允许的响应头、≤64 KiB 响应体 → 记录 COMPLETED（按持有者校验）→ 正常写出
     → 下游无响应（超时、连接失败）或响应体超限：按持有者释放占位
```

## 6. Platform（路由字段）

| 项 | 设计 |
|---|---|
| 枚举 | `platform-support-api` 新增 `GatewayIdempotencyMode { NONE, REQUIRED }`（相邻枚举常量空一行） |
| 表 | `t_gateway_route.idempotency_mode VARCHAR(64) NOT NULL DEFAULT 'NONE' COMMENT '请求幂等模式的稳定业务代码。允许值：NONE、REQUIRED。'`，位于 `response_cookie_mode` 之后 |
| 变更 SQL | `docs/database/sql/changes/2026-10-17-gateway-route-idempotency-mode.sql`：`ALTER TABLE ... ADD COLUMN`；回滚 `DROP COLUMN`（旧 Platform 不读该列，可先加列后发版） |
| 同步 | PDManer 生成源 `manifests/06-gateway.mjs` 后重新生成模型；Schema、初始化 DML 13 处 INSERT（显式写 `NONE`）、测试 DDL、测试 oracle |
| 管理接口 | 创建：可选，缺省 `NONE`；更新（PUT）：可选，**缺省保持当前值**（D2）；详情、列表摘要返回；能力接口列出可选值 |
| 编译校验 | `REQUIRED` 只允许 `CLIENT_API` 通道且方法非 GET/HEAD；`REQUIRED` 路由不得把 `idempotency-key` 配为 `FORWARD_REQUEST`（D3）。不满足时创建 / 更新返回现有配置校验错误 |
| 快照 | `GatewayRouteSnapshot` 新增 `idempotencyMode`（字符串，追加在构造参数末尾） |
| 审计 | `ConfigurationField.IDEMPOTENCY_MODE`，修改前后值进入配置审计 |

## 7. Gateway

### 7.1 路由与配置

- `GatewayRuntimeRoute` 增加 `idempotencyMode`；快照缺字段或为空按 `NONE`（对旧 Platform 兼容），未知值整份快照编译失败（与现有枚举解析一致）。
- `GatewayRoutePolicyValidator` 复核 D3 规则（Platform 之外的第二道防线）。
- 配置 `gateway.idempotency`：
  - `retention`：默认 `72h`；
  - `pending-lease`：默认 = 路由响应超时 + 请求体接收超时 + 10 秒，按路由计算，可配置附加量；
  - `max-response-bytes`：默认 65536；
  - `redis.namespace`：默认沿用 Guard 的 namespace。

### 7.2 Key 与摘要

| 项 | 规则 |
|---|---|
| Key 格式 | 恰好一个 `Idempotency-Key` 头；1—255 字符；`[A-Za-z0-9][A-Za-z0-9._:-]*`；值区分大小写 |
| 作用域 | `channel + callerKey`；Client API 的 `callerKey = clientId`（不含凭证 ID，轮换不影响） |
| Redis 键 | `{namespace}:idem:v1:{channel}:{callerKey}:{SHA-256(Key) 十六进制}`（Key 原值不进键，长度固定） |
| 摘要 | `SHA-256(UTF-8("v1\n" + method + "\n" + routeCode + "\n" + 实际原始路径 + "\n" + 规范化 Query + "\n") ‖ SHA-256(请求体原始字节))`；规范化 Query = 按参数名排序、同名保持原顺序、保留原始编码 |

### 7.3 Redis 记录与原子操作

一个键一个 Hash，Lua 脚本保证原子性：

| 脚本 | 行为 |
|---|---|
| `idempotency-claim.lua` | 键不存在 → 写入 `state=PENDING, digest, owner, acceptedAt`，过期时间 = 待定租约；已存在 → 返回 `state, digest`，COMPLETED 时一并返回记录 |
| `idempotency-complete.lua` | `owner` 匹配且仍为 PENDING → 写入 `state=COMPLETED`、状态码、响应头 JSON、gzip 响应体、`completedAt`，过期时间设为 `acceptedAt + 72h`（`PEXPIREAT`）；不匹配返回失败（租约已过期被他人占用，只记 WARN，不覆盖） |
| `idempotency-release.lua` | `owner` 匹配且 PENDING → 删除 |

- 72 小时从首次被接受（claim 成功）起算（Q4）。
- 待定租约过期（如 Gateway 进程中断）等同释放：之后同 Key 可再次下发，由业务防重兜底，与 Q6 一致。
- Redis 故障（超时、连接失败）→ 503 `GATEWAY_DEPENDENCY_UNAVAILABLE`（D5）。complete 失败只记 WARN，响应照常返回（业务已执行，不能改成错误）。

### 7.4 记录什么、怎么重放

| 项 | 规则 |
|---|---|
| 记录条件 | 存在 `CLIENT_RESPONSE_ATTR`（下游确实返回），任意状态码；响应体 ≤ 64 KiB |
| 不记录（释放） | Gateway 自身生成的 502/503/504、客户端取消、响应体超限（D4，WARN） |
| 记录的头 | Client API 白名单内的头：`Content-Type`、`Content-Language`、`Content-Disposition`、`Content-Encoding`、`ETag`、`Last-Modified`、`Retry-After`，以及 201 的 `Location`。`X-Request-Id`、`Cache-Control` 等由 Gateway 统一重写，不记录 |
| 重放 | 状态码、记录的头、响应体原样写出；随后照常经过响应合同过滤器（白名单、`X-Request-Id`、`no-store`） |
| Problem 体（D1） | `Content-Type` 为 `application/problem+json` 时，解析后把 `requestId` 改为本次 Request ID 再写出，其余字段（含 `timestamp`）不变；解析失败按原样写出并 WARN |
| 访问日志 / 指标 | 新 outcome：`IDEMPOTENCY_REPLAYED`、`IDEMPOTENCY_CONFLICT`（复用、执行中）；Key 缺失或非法沿用 `INVALID_REQUEST` |

### 7.5 代码结构（`com.paytech.gateway.idempotency`）

| 类 | 职责 |
|---|---|
| `IdempotencyGlobalFilter` | 编排：判断路由、校验 Key、claim、重放 / 拒绝 / 下发、挂接响应捕获与释放 |
| `IdempotencyKeyPolicy` | Key 读取与格式校验 |
| `IdempotencyRequestDigest` | 摘要计算 |
| `IdempotencyRecordStore`（接口）+ `RedisIdempotencyRecordStore` | claim / complete / release（D8 可替换） |
| `IdempotencyResponseCapture` | 响应装饰器：缓冲 ≤64 KiB 后回写并记录 |
| `IdempotencyReplayWriter` | 重放与 Problem `requestId` 改写 |
| `IdempotencyProperties` | 配置 |

- 过滤器顺序常量 `IDEMPOTENCY = HIGHEST_PRECEDENCE + 150`，同步顺序合同测试。
- 错误码 `GatewayErrorCode` 增加 4 个，并补 Client API 映射。

## 8. Operations BFF / Web

- **BFF**：
  - 白名单增加 `IDEMPOTENCY_MODES`；
  - 详情、创建、更新、能力的模型与转换带 `idempotencyMode`；
  - 浏览器边界 JSON 同步；
  - 更新不传时不下发（Platform 保持原值）。
- **Web**：
  - 路由详情显示“请求幂等：不启用 / 必须”；
  - 编辑器增加选择项（默认不启用）；
  - 类型与模型测试同步。

## 9. Commerce

- `paytech-commerce-client-api`：去掉 6 个方法的 `Idempotency-Key` 参数。
- `paytech-commerce-web`：
  - 6 个 Controller 签名同步去掉该参数；
  - 移除 `paytech-command-idempotency` 依赖与 `command-idempotency` 配置。
- `paytech-commerce-test`：
  - 测试基类不再发送该 Header；
  - 移除 `application-mock.yml` 中的同名配置；
  - 移除 POM 覆盖率中的空包。
- `client-v1.yaml`：去掉 3 处 `required` Header 参数，改为说明“由 Gateway 校验与重放”。
- `IDEMPOTENCY_REQUEST_IN_PROGRESS` 在 Customer 结账流程中另有业务用途（`GetCustomerCheckoutProcessor`），保留。

## 10. 测试

| 系统 | 入口与场景 |
|---|---|
| Gateway | 新 `ClientApiIdempotencyAutoTest`（真实本地 Redis + 下游模拟器，记录下游调用次数），场景如下：<br>• 缺失、多值、非法 Key；<br>• 首次成功后重放（状态、头、体一致，下游 1 次，响应头为新 Request ID）；<br>• 换 Body、换路径复用；<br>• 执行中（下游延迟应答）409 + `Retry-After: 3`；<br>• 下游 4xx/5xx 被记录与重放（Problem `requestId` 改写、`timestamp` 不变）；<br>• 下游超时、连接失败后释放，同 Key 再次下发；<br>• 响应超 64 KiB 不记录；<br>• 不同 Client 同 Key 互不影响；<br>• 同 Client 不同凭证命中同一记录；<br>• `NONE` 路由不受影响；<br>• Redis 不可用 503。<br>另需：快照编译与策略测试（缺字段为 `NONE`、未知值拒绝、D3 违规拒绝）；顺序合同、错误码与访问日志测试同步；约 40 个快照构造按新签名更新 |
| Platform | 路由管理 HTTP 测试：创建缺省 `NONE`、更新缺省保持原值、`REQUIRED` 违规校验、快照带字段、审计字段 |
| Operations | BFF 浏览器边界测试同步；Web 模型测试 |
| Commerce | 全量回归（去掉 Header 后既有测试全部通过） |
| test 验收 | `client_api_flow.py` 增加：缺 Key 400、同 Key 重放（响应逐字一致、Commerce 只建 1 单）、换 Body 409、并发同 Key 409、凭证轮换后重放 |

## 11. 部署（test，需单独授权）

| 步骤 | 内容 |
|---|---|
| 1 | Commerce 新镜像：不再要求 Header（Gateway 仍转发，Commerce 忽略） |
| 2 | `mor_platform` 执行变更 SQL（加列，默认 `NONE`），再替换 Platform |
| 3 | 替换 Gateway |
| 4 | 替换 Operations BFF → Web |
| 5 | 3 条 Client API 写路由改为 `REQUIRED`，并去掉 `idempotency-key` 的转发配置（脚本或运营后台） |
| 6 | 验收脚本、日志检查、发布记录 |

**回退**：
- 先把路由改回 `NONE` 并恢复转发；
- 再按逆序回退镜像；
- 新增列保留（旧代码不读）。

## 12. 文档与已登记后续事项

- 文档：
  - Commerce 设计第 3.4 节与第 7 节改为“Gateway 处理”的现行写法；
  - `2026-09-05-client-api-gateway-contract.md` 第 17、145 行的过期表述；
  - 应用架构第 21、239 行；
  - Gateway README 第 297—300 行与 outcome 列表；
  - 台账。
- **后续事项（D8）**：幂等记录从 Redis 迁到数据库存储。迁移时保持 `IdempotencyRecordStore` 接口与第 7.3 节语义不变，届时另立需求，评估选型（Gateway 专用库或其他）、写入延迟与清理方式。
- 其他后续：Operations 路由与 Platform `OperationsCommandIdempotencyService` 迁移；Client Console 通道启用。
