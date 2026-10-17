# Gateway 处理 Idempotency-Key — 需求（阶段 1 评审稿）

> 状态：阶段 1 已确认（2026-10-17，“按建议”，Q1—Q9 按建议）。确认记录见 [delivery.md](./delivery.md)。

## 1. 来源与背景

- 来源：用户 2026-10-17 在 Client API 联通 mor-test 后选择“Gateway 处理 Idempotency-Key”。
- 职责已定：[2026-09-17 决定](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)与[开发规范 5.4](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)把 HTTP 请求幂等统一归 Gateway：
  - 记录保留 72 小时；
  - 同 Key、同参数重放首次响应（含已记录的系统异常、202 原样重放）；
  - 同 Key、不同参数拒绝；
  - 首次仍在执行时不并发下发；
  - 不接管业务恢复；
  - 不持久化原始敏感请求。
- 决定把实现细节留给 Gateway 方案：哪些接口启用、作用域编码、72 小时起算点、执行中的重复请求、响应未记录窗口。本需求就是这份方案。
- 公开协议早已写进 [Commerce Client API 设计 3.4 / 7.2](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)：
  - Key 规则：1—255 字符，`[A-Za-z0-9][A-Za-z0-9._:-]*`，值区分大小写；
  - 错误码：缺失 400 `IDEMPOTENCY_KEY_REQUIRED`，非法 400 `INVALID_IDEMPOTENCY_KEY`，复用冲突 409 `IDEMPOTENCY_KEY_REUSED`，执行中 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` 加 `Retry-After: 3`；
  - 作用域 `CLIENT:<clientId>`：凭证轮换不改变结果；
  - 认证、校验失败不占用 Key。

  其中“Gateway 只转发、零响应缓存”的旧写法已被 09-17 决定取代。

## 2. 现状（2026-10-17 只读核对）

| 项 | 现状 |
|---|---|
| Gateway | 没有任何幂等处理，只按路由白名单转发 `Idempotency-Key`。请求体已在 `RequestBodyLimitGlobalFilter`（+100）完整读入可重放缓冲，认证、Scope、限流在其之前完成。已有 Redis（Lua 租约模式）和上游错误响应缓冲先例（`OwnerProblemResponseGlobalFilter`，64 KiB） |
| 路由模型 | Platform 路由 Header 用途只有 `CORS_REQUEST_ALLOWED`、`FORWARD_REQUEST`，**没有“该路由要求幂等”的标记**；Operations BFF 与 Web 的路由维护页同样只认这两个值 |
| Commerce | 6 个 POST 声明必填 `Idempotency-Key` 请求头，但不使用其值；`paytech-command-idempotency` 依赖仍在，已无代码调用。test 上 3 条写路由（建单、取消、退款）把该 Header 转发给 Commerce |
| 其他存量 | Operations 管理路由转发 Key 给 Platform 自有 `OperationsCommandIdempotencyService`（`t_command_idempotency`）；Payment `PaymentIntentApi` 声明该 Header（内部调用，不经 Gateway）；Client Console 入驻路由只允许 CORS、不转发 |

## 3. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Client（接入方） | 网络超时后用同一个 Key 重试 POST，拿到与第一次完全相同的响应，不会重复建单或退款 |
| Commerce 等业务系统 | 不再处理 HTTP Key，只保留业务防重（clientOrderId、clientRefundId、状态） |
| 运营 | 在路由维护里决定哪些路由要求幂等 |

## 4. 本期范围（建议）

| 编号 | 功能 |
|---|---|
| F-01 | Platform 路由增加“请求幂等”配置（见 Q1），快照下发给 Gateway；Operations 路由维护页可查看和设置 |
| F-02 | Gateway 对启用幂等的路由：校验 Key（缺失、非法）；按“通道 + 调用方 + Key”判重；计算请求摘要；首次请求加锁并下发；记录下游响应；重复请求重放或拒绝 |
| F-03 | 记录存 Redis，保留 72 小时；Redis 不可用时启用幂等的路由失败关闭（503） |
| F-04 | Commerce 去掉 6 个 POST 的必填 Key 请求头与 `paytech-command-idempotency` 依赖、配置（见 Q8）；Gateway 不再把 Key 转发给 Commerce |
| F-05 | test 上 3 条 Client API 写路由启用幂等；验收脚本补重试、冲突、执行中场景 |
| F-06 | 文档：Commerce 设计 3.4 / 7.2 改为现行写法；Gateway README；台账 |

## 5. 本期不做

- Operations 管理路由与 Platform `OperationsCommandIdempotencyService` 的迁移（另立需求；本期 Operations 路由保持现状转发）。
- Client Console（CLIENT 通道）路由启用幂等。
- Payment 等内部接口的 Header 清理。
- 删除 `t_command_idempotency` 表或历史数据。

## 6. 验收标准

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | 启用幂等的路由不带 Key / Key 非法 | 400 `IDEMPOTENCY_KEY_REQUIRED` / `INVALID_IDEMPOTENCY_KEY`；不到达 Commerce，不占用 Key |
| AC-02 | 同 Key 同请求重试（首次已完成） | 返回与首次相同的状态码、响应体和约定响应头；Commerce 只收到一次 |
| AC-03 | 同 Key 不同请求（换 Body 或换路径） | 409 `IDEMPOTENCY_KEY_REUSED`；不到达 Commerce |
| AC-04 | 首次仍在执行时同 Key 再来 | 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`，`Retry-After: 3`；不并发下发 |
| AC-05 | 首次下游返回 4xx / 5xx | 同样被记录并重放（认证、Scope、限流、Gateway 自身校验失败除外，见 Q5） |
| AC-06 | 凭证轮换后用新凭证同 Key 重试 | 作用域按 Client，不按凭证：照常重放 |
| AC-07 | 不同 Client 用同一个 Key | 互不影响 |
| AC-08 | 72 小时后同 Key | 视为新请求，由业务防重决定结果 |
| AC-09 | Redis 不可用 | 启用幂等的路由 503 失败关闭，不绕过直接下发 |
| AC-10 | 日志与存储 | 不记录 Key 原值以外的请求体；Redis 只存摘要与响应，不存原始请求体 |

## 7. 已确认的决定

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 怎么标记“这条路由要求幂等”？ | 路由增加字段 `idempotencyMode`（`NONE` / `REQUIRED`）。需要改 Platform 表、管理接口、快照，Operations BFF 与 Web 路由页，以及 Gateway。备选：给 Header 用途加一个值 `IDEMPOTENCY_KEY`，不加列，但语义上混进了“请求头用途” |
| Q2 | 作用域（谁和谁算同一个 Key 空间）？ | `通道 + 调用方`。Client API 的调用方是 Client（不是凭证，轮换不影响）；路由不进作用域，同一 Key 用在另一个接口算复用冲突（设计 7.2 原意） |
| Q3 | 请求摘要怎么算？ | `SHA-256(方法 + 路由编码 + 实际路径 + 规范化 Query + 请求体原始字节)`，不做 JSON 规范化。客户端重试必须发相同字节；设计 7.2 里“`10` 与 `10.00` 等价”的业务级规范化不在 Gateway 做 |
| Q4 | 72 小时从何时算？ | 从首次请求被 Gateway 接受（加锁成功）开始 |
| Q5 | 哪些响应会被记录？ | 下游返回的任何最终响应（2xx/4xx/5xx），响应体上限 64 KiB（超出则不记录并告警）。Gateway 自己在下游之前拒绝的（认证、Scope、限流、Body 超限、Key 非法）不记录、不占 Key |
| Q6 | 下游没有返回（超时、连接失败，Gateway 回 502/504）怎么办？ | 释放 Key，允许同 Key 再次下发，由业务防重兜底（3 个接口都有：clientOrderId 永久唯一、取消按状态幂等、clientRefundId 唯一）。备选：标记“结果未知” 72 小时，客户只能换 Key，结果与业务防重相同但体验更差 |
| Q7 | 响应里含 Hosted Checkout 公开链接（带访问 Token），存 Redis 72 小时可以吗？ | 可以：私网 Redis、有口令、TTL 72 小时，日志不打印。不另加密；如需加密另立需求 |
| Q8 | Commerce 侧怎么配合？ | 本需求内一并修改：去掉 6 个 POST 的必填 `Idempotency-Key` 参数、`paytech-command-idempotency` 依赖与 `command-idempotency` 配置；Gateway 对启用幂等的路由不再转发该 Header。改 Commerce 前与 Commerce 会话协调 |
| Q9 | 验证到哪个环境？ | 本机自动测试 + mor-test 部署（Gateway、Platform、Operations BFF/Web、Commerce 替换，需单独授权），用 Client API 验收脚本补幂等场景 |

## 8. 适用阶段

完整 8 阶段。
