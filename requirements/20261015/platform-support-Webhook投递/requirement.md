# Platform Support Client Webhook 投递需求

> 状态：阶段 1 已确认（2026-10-15）。确认记录见 [delivery.md](./delivery.md)；方案在阶段 2+3 写入 [technique.md](./technique.md)。

## 1. 背景与来源

Client 需要在订单支付、取消、过期、资金可用及退款结果发生时收到服务端通知，而不是轮询 GET。[Commerce Client API V1 决定](../../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md) D11 与[设计第 13 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#13-client-webhook)已冻结外部合同：8 种事件、Envelope、RFC 8785 规范化、`Code-MoR-Signature` HMAC 签名、至少一次投递；并约定 **Commerce 拥有业务内容，Platform 拥有 Endpoint、Secret、Delivery、Attempt、Retry、Replay 与审计**。

现状：Platform PDManer 中有 2026-09-01 设计的 8 张 `t_webhook_*` 表，但 Schema SQL 未建表，Java 无任何实现；[Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)仅保留 Webhook 模块边界。Commerce 生产端（`CLIENT_WEBHOOK_REQUESTED`）也未实现。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Operations 运营人员 | 为 Client 配置 Endpoint、订阅事件类型、轮换签名密钥、发送测试事件，查看投递记录并手动 Replay |
| 内部事件来源（首个为 Commerce） | 本地事务提交后经私网 HTTP 提交冻结事件，得到明确受理结果后再完成自己的可靠任务 |
| Client | 在自己的 HTTPS 地址收到签名事件，验签、按 `eventId` 去重、返回 2xx |
| Platform | 幂等保存事件，按订阅分发，签名投递、重试直到成功或耗尽，全程可审计 |

## 3. 本期范围

1. **事件受理（内部 HTTP）**：`platform-support-api` 新增 `@HttpExchange` 合同，私网 Controller 受理冻结事件；沿用[通知内部 HTTP 接口](../platform-support-通知内部HTTP接口/requirement.md)的过渡信任（私网 + `X-Caller-Service` + 部署配置白名单：调用方 → 允许的事件类型）。按 Client ID 解析 CLIENT Tenant；按 `(tenant, source_system, source_event_key)` 幂等：内容完全一致返回原受理结果，不一致拒绝。
2. **分发与投递**：受理事务内按 ACTIVE 订阅生成 Delivery 并登记 `compensation_event`；提交后唤醒执行。事务外 Reveal 密钥、签名、HTTP 发送；短事务记录 Attempt 与 Delivery 结果。2xx 成功，其他（含 3xx、超时、断连）按退避重试，耗尽记 DEAD。
3. **Endpoint 管理**：创建、修改地址、启用/停用、查询；仅 HTTPS，保存及每次发送均做 SSRF 校验，不跟随重定向。
4. **签名密钥**：创建 Endpoint 时生成首个密钥；运营可轮换（见第 6 节）；密钥值保存于 Vault，库内只存引用与指纹。
5. **订阅**：按 Endpoint 设置订阅的事件类型（V1 八种）。
6. **投递查询与 Replay**：按 Client/Endpoint/事件类型/状态/时间查询 Delivery，查看 Attempt 明细；对单条 Delivery 发起 Replay（新 Delivery，Event ID 与 Body 不变）。
7. **测试事件**：对 Endpoint 同步发送一次签名测试事件并返回结果，不落事件/投递记录。
8. **数据模型**：按第 7 节收敛后的表同步 PDManer、Schema SQL、增量 DDL；Operations 权限注册 DML 按阶段 3 确定。
9. **测试与文档**：从 HTTP 入口和补偿 Handler 入口跑真实链路 auto 测试（Client Endpoint 用测试 HTTP 端点）；同步 Platform 数据库文档、Vault/Commerce 相关合同的取代说明、Platform README 与当前台账。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| Commerce 生产端 | 冻结快照与推送属于 Commerce，另立需求；本期用测试调用方验证 |
| Operations Console 页面（BFF/Web） | 本期只提供 Platform 管理接口；页面另立 `operations-console-*` 需求 |
| Client Console 自助管理、公共 `/api/v1/webhooks/**` | 后续 |
| Endpoint Challenge 握手验证 | 不做；以 SSRF 校验加测试事件替代 |
| Readiness（PLT-I13）的 Webhook 条件 | 后续接入 |
| `WebhookDelivered` / `WebhookFailed` 事件发布 | 当前无消费者，不发布 |
| 连续失败自动停用 Endpoint、跨事件顺序保证、批量 Replay | 后续 |
| 强服务身份、test/生产部署 | 后续 |

## 5. 已确认的决定

| 编号 | 决定 | 取代 |
|---|---|---|
| D-01 | Endpoint URL 明文存 Platform 库，不经 Vault 保护；普通日志只记 host | [Platform 数据库文档](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)与 [Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)中“Webhook URL 经 Vault Token 保存”的描述 |
| D-02 | 管理入口首期只做 Operations 代配置 | — |
| D-03 | 签名密钥值存 Vault（新增 REFERENCE 类数据类型，与 SES 凭据同做法），Platform 发送前事务外 Reveal | Vault 架构中“Webhook Secret 进 Secrets Manager”的描述 |
| D-04 | 不做 Challenge 握手，删除端点修订与验证表 | 2026-09-01 设计的 Endpoint Revision / Verification |
| D-05 | 事件来源经内部 HTTP 同步受理，不走 MQ | Commerce 设计 13.5“提交后发布 `ClientWebhookRequested`”的传输方式（事件内容不变） |
| D-06 | 轮换时旧密钥保留 **1 小时**宽限，宽限期内双签名（见第 6 节） | Commerce 设计 13.6“每个 Attempt 只用一个 ACTIVE Secret、双签需另行冻结” |

## 6. 密钥轮换

- **谁触发**：Operations 运营人员。场景是 Client 申请更换（工单或线下），或平台怀疑泄露。Client 本期没有自助入口。
- **新密钥怎么交给 Client**：轮换接口只在本次响应里返回一次新密钥明文，之后任何人都无法再查看；运营通过安全渠道交给 Client。Webhook 本身绝不携带密钥。
- **Client 怎么感知、怎么切换**：
  1. 轮换后 1 小时内，每次投递的 Header 同时带两个签名：`t=…,v1=<新>,v1=<旧>`。合同已规定 Client 解析一个或多个 `v1`、任一匹配即通过，所以 Client 没换密钥时旧签名仍然有效，换了之后新签名也有效，切换不用卡时间点。
  2. 1 小时后只带新签名。若 Client 仍未更换，验签失败并返回非 2xx，Platform 会按重试计划继续投递（约 3 天）；Client 换好密钥后，后续重试就能成功，事件不丢。
- **紧急作废**：轮换时可选“立即作废旧密钥”，即宽限为 0，用于确认泄露的场景。
- **审计**：轮换人、时间、宽限截止时间都会记录；每次 Attempt 冻结所用的主密钥。

## 7. 数据模型方向

从 8 张收敛到 6 张，阶段 3 给出逐字段设计。

> 阶段 3 修订：订阅进一步并入端点（`event_types` 数组），最终为 5 张表，见 [technique.md 第 3 节](./technique.md#3-数据设计)。


| 表 | 方向 |
|---|---|
| `t_webhook_endpoint` | 保留；并入 URL 明文与 host，删除 Vault Token 和脱敏 URL |
| `t_webhook_endpoint_revision`、`t_webhook_endpoint_verification` | 删除 |
| `t_webhook_signing_secret` | 保留；状态适配“1 小时宽限”（ACTIVE / RETIRING / RETIRED / REVOKED，按阶段 3 定稿） |
| `t_webhook_subscription` | 简化为 Endpoint × Event Type 的启停行 |
| `t_webhook_event` | 保留；只存 inline JSON，删除 Vault 存储模式 |
| `t_webhook_delivery` | 保留；删除 Lease/Fencing 列，改用公共 compensation Claim/Lease 加 version CAS |
| `t_webhook_attempt` | 保留；结果简化为 SUCCEEDED / FAILED，并记录失败原因码与耗时 |

## 8. 其余由 Claude 确定的默认值（可调整）

| 项 | 默认 |
|---|---|
| 每个 Client 的 Endpoint 上限 | 5 个 |
| 请求超时 | 连接 5s，整体 10s，全局配置，不按 Endpoint 配置 |
| 重试节奏 | 1m、5m、30m、2h、6h、12h、24h，之后每 24h 一次；共 10 次，约 3 天，用完记 DEAD |
| 响应处理 | 只记 HTTP 状态码，响应体最多读取 1 KiB 后丢弃，不落库 |
| 停用 Endpoint | 未完成的 Delivery 被取出执行时记 CANCELLED；重新启用不会自动补发，可手动 Replay |
| 无订阅的事件 | 照常受理并保存 Event（0 条 Delivery），以便日后 Replay |
| 测试事件类型 | `webhook.ping`；Client 按合同忽略未知类型并返回 2xx |
| 事件体上限 | 与 Commerce 合同一致，Envelope 不超过 4 KiB |

## 9. 验收标准（草案）

| 编号 | 验收标准 |
|---|---|
| AC-01 | 白名单调用方提交合法事件，返回受理结果；Event 落库，并为每个 ACTIVE 订阅生成 1 条 Delivery；无订阅时 0 条 Delivery 仍受理成功 |
| AC-02 | 同一来源事件重复提交且内容一致，返回原结果、不新增记录；内容任一不同则返回冲突错误码；不同调用方使用相同 key 互不影响 |
| AC-03 | 缺少/未登记调用方、事件类型越权、Client 无 CLIENT Tenant、Envelope 超限或 Hash 不符：分别返回稳定错误码且不写库 |
| AC-04 | Client 端点收到的 Body 是 Envelope 的 RFC 8785 字节；Header `t` 为本次发送时间，`v1` 可用当前密钥验证 |
| AC-05 | 端点返回非 2xx、3xx、超时或断连，Delivery 按计划重试，第 10 次失败后记 DEAD；2xx 记 SUCCEEDED；每次尝试都有 Attempt 记录 |
| AC-06 | 多实例或并发唤醒同一 Delivery 时，同一时刻只发送一次（CAS 保护），不重复计数 |
| AC-07 | 轮换后 1 小时内 Header 带新、旧两个 `v1`，之后只带新签名；选择“立即作废”时只带新签名；新密钥明文只在轮换响应中出现一次 |
| AC-08 | 非 HTTPS、带 userinfo、解析到内网/回环/链路本地/元数据地址的 URL，保存时拒绝；发送时解析到上述地址的，该 Attempt 失败且不建立连接；不跟随重定向 |
| AC-09 | Replay 生成新 Delivery，Event ID 与 Body 不变，使用当前 URL 与密钥；停用 Endpoint 的待发 Delivery 记 CANCELLED |
| AC-10 | 测试事件同步返回端点状态码或失败原因，不写 Event/Delivery |
| AC-11 | 事务内不调用 Vault 或 Client 端点；密钥明文、签名原文不进入日志、错误正文或补偿 Payload |
| AC-12 | PDManer、Schema SQL、增量 DDL 与 DataObject/Mapper 一致；`./mvnw -o verify` 全部通过，Webhook 相关覆盖率不低于既有水平 |
| AC-13 | D-01、D-03 至 D-06 的取代关系写入相关权威文档或决定记录；Platform README 与当前台账同步更新 |

## 10. 本期阶段安排（待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审 | 本文 |
| 2+3. 现状差异与方案设计 | 合并：表结构逐字段设计、接口合同、Vault 数据类型与策略清单、SQL |
| 4. 研发 | Platform 代码、模型、SQL 文件；本地 SQL 执行单独授权 |
| 5. 本地验证 | 照常 |
| 6–7. test 发布 | 不适用：本期不部署 |
| 8. 提交 | 需单独授权 |
