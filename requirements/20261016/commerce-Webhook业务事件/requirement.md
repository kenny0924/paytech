# Commerce Client Webhook 业务事件 — 需求

> 状态：阶段 1 已确认（2026-10-17，“按建议”，Q1—Q5 按建议）；阶段 2 决定 D1 将本期缩为 3 种事件（见第 4 节注）。确认记录见 [delivery.md](./delivery.md)。

## 1. 来源与背景

- 来源：用户 2026-10-16 在 Client Console 接入自助发布 mor-test 后选择“先做第一点”，即让 Commerce 产生 Webhook 业务事件。
- 现状：
  - **Platform 侧已就绪**（`ee469e0`，mor-test 已部署）：内部受理 `POST /platform-support/webhooks/v1/events`，以及签名投递、重试、Replay；客户可在 Client Console 自助配置端点。
  - **Commerce 侧什么都没有**：没有 Webhook 代码，没有调 Platform 的 HTTP 客户端。客户现在只能收到测试事件，收不到任何业务事件。
- 外部合同已冻结：[Commerce Client API V1 设计第 13 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#13-client-webhook)、[决定 D11](../../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md)。传输方式已由 [2026-10-15 Webhook 投递决定](../../../agent-docs/decisions/2026-10-15-platform-webhook-delivery.md) 改为同步内部 HTTP 受理，载荷与 Hash 规则不变。
  - **8 种事件**：`order.paid`、`order.cancelled`、`order.expired`、`order.funds_available`、`refund.created`、`refund.succeeded`、`refund.failed`、`refund.cancelled`。
  - **Envelope 与 Hash**：Envelope 为 `{eventId, eventType, schemaVersion, createdAt, data:{object}}`，按 RFC 8785 计算 Hash。
  - **生产端责任**：业务变更与 `CLIENT_WEBHOOK_REQUESTED` 补偿任务写在同一个本地事务里，提交后提交冻结事件，重试时重放冻结内容，Platform 确认受理才算完成。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Client（接入方） | 订单支付成功、取消，以及退款创建和结果确定时，在自己的端点收到签名事件，不必轮询 |
| Commerce | 业务状态变化可靠地交给 Platform：不丢、不重复造事件，Platform 暂时不可用时自动重试 |
| 运营 | 能看到 Commerce 侧提交失败的事件（复用现有补偿任务查询与日志） |

## 3. Commerce 现状与事件来源

| 事件 | Commerce 是否已有该业务变化 | 触发点 |
|---|---|---|
| `order.paid` | 有 | `ApplyPaymentSucceededProcessor`（消费 PaymentSucceeded，订单置 PAID） |
| `order.cancelled` | 有 | `CancelOrderProcessor`（订单置 CANCELLED） |
| `refund.created` | 有 | `CreateRefundProcessor`（本地退款请求与额度预占提交） |
| `refund.succeeded` / `failed` / `cancelled` | 有 | `RefundServiceImpl.complete()` 首次进入对应终态（结果来自 Payment PAY-I04）；代码里已留 TODO 衔接 Webhook |
| `order.expired` | **没有**：没有任何代码会把订单置为 EXPIRED，也没有过期任务 | — |
| `order.funds_available` | **没有**：资金可用状态从未被置为 AVAILABLE（依赖 Finance 资金成熟，未实现） | — |

## 4. 本期范围（建议）

| 编号 | 功能 |
|---|---|
| F-01 | ~~6 种~~ **3 种**事件（阶段 2 D1：`refund.succeeded/failed/cancelled` 随“Commerce 接入 Payment 退款”另立需求）：`order.paid`、`order.cancelled`、`refund.created`、`refund.succeeded`、`refund.failed`、`refund.cancelled`。每种都在业务状态**首次**进入该状态时生成，重复处理同一事实不再生成 |
| F-02 | 冻结事件：在业务变更的同一本地事务内，按合同组装 `data.object` 和 Envelope，计算两个 Hash，写入 `CLIENT_WEBHOOK_REQUESTED` 补偿任务；载荷 ≤ 4 KiB，只含合同允许的字段（不含渠道退款号、内部 ID、个人信息） |
| F-03 | 提交到 Platform：Commerce 新增 Platform Webhook 受理接口的 HTTP 客户端，补偿任务在事务外调用，`X-Caller-Service: commerce-service`；Platform 返回 204 才完成任务 |
| F-04 | 失败处理：网络错误、超时、5xx 由补偿任务按现有退避重试；Platform 判定请求不合法（如客户未开通、内容与同一事件冲突）的 4xx 不再重试，任务记为失败并输出告警日志（见 Q3） |
| F-05 | 合同文档：更新设计第 13 节中“提交后发布 MQ”的旧描述，链接 2026-10-15 决定；Commerce README 与台账同步 |
| F-06 | 测试：从业务入口（支付成功消息、取消、创建退款、退款提交任务）到补偿 Handler 调用 Platform 测试端点的真实链路 auto 测试；本机全链路验证 Commerce → Platform → 测试接收端的送达与验签 |

## 5. 本期不做

- `order.expired`：需要先做订单过期（定时任务与状态迁移），另立需求。
- `order.funds_available`：需要 Finance 资金成熟与 Commerce 资金可用投影，另立需求。
- 在 Client Console 发起退款、订单列表（另立需求）。
- 把 Commerce 部署到 mor-test：Commerce 目前没有部署在 test（见 Q4）。
- 强服务身份：沿用私网加 `X-Caller-Service` 加白名单的过渡信任。

## 6. 验收标准

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | 订单支付成功 | 端点收到一次 `order.paid`；字段与 GET 订单一致；签名可用端点密钥校验 |
| AC-02 | 同一支付成功消息重复投递给 Commerce | 不生成第二个事件 |
| AC-03 | 取消未支付订单 | 收到 `order.cancelled`，带结构化原因码 |
| AC-04 | 创建退款 | 收到 `refund.created`；退款结果确定后收到对应终态事件；同一退款不会收到两个不同的终态 |
| AC-05 | 提交时 Platform 不可用 | 业务操作照常成功；Platform 恢复后事件补交成功，内容与当时冻结的一致（不按最新数据重建） |
| AC-06 | Commerce 重试同一事件 | Platform 按事件 ID 幂等受理，客户只收到一份（除 Platform 自身的至少一次重投外） |
| AC-07 | 载荷安全 | 不含渠道退款号、内部数字 ID、Consumer 个人信息、风控信息；Commerce 普通日志只记事件 ID 与类型，不记载荷 |
| AC-08 | 4xx 不可重试 | 任务不无限重试，留下可查的失败记录与告警日志 |

## 7. 已确认的决定

| 编号 | 问题 | 决定 |
|---|---|---|
| Q1 | 本期范围 | 只做已有业务变化的 6 种事件；`order.expired`、`order.funds_available` 等对应业务能力做完后再接 |
| Q2 | 事件 ID 规则 | 用可推导的稳定 ID：`evt_` + 资源编号 + 事件短名 + 资源版本（如 `evt_ord_xxx_paid_5`），不超过 128 字符。同一事实重放时 ID 不变，天然幂等；不额外存事件表 |
| Q3 | Platform 返回 4xx | 不重试，补偿任务记为失败并输出告警日志，交运营排查；不新建 Commerce 侧事件表或管理页面 |
| Q4 | 验证到哪个环境 | 本期只做本机验证（Commerce + Platform + 测试接收端）。Commerce 上 mor-test 依赖 Payment、Risk 等，另立部署需求 |
| Q5 | Commerce 代码归属 | Commerce 目录目前有其他会话未提交的改动（结账 Customer 测试）。建议研发前先与 Commerce 会话协调，由本会话只改 Webhook 相关文件；若你希望交给 Commerce 会话做，本会话只出需求与方案 |

## 8. 适用阶段

完整 8 阶段。阶段 6—7（test 发布）按 Q4 结论，可能不适用或另立需求。
