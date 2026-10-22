# acquiring：订单与会话过期收尾 · 需求

> 状态：**阶段 1 已确认**（2026-10-22 用户“可以”：需求稿与 Q1—Q7 全部按建议）。确认记录见 [delivery.md](./delivery.md)，现状证据与阶段 2 差异分析见 [technique.md](./technique.md)。
> 来源：2026-10-22 主链路（商户下单 → 收银台卡支付 → 3DS → 支付成功）缺口分析第 4 项。
> 关联：[payment-3DS全链路](../payment-3DS全链路/requirement.md)（阶段 4 进行中）负责交互过期后的查单收敛，是本需求"有未决动作时等待收敛"分支能结束的前提；两者都触及 Payment 终止接口，边界见 Q7。

## 1. 背景

现状（代码证据见 [technique.md](./technique.md) 第 1 节）：

- **读侧已按到期处理，写侧完全没有。** 订单表有 `expires_at`、EXPIRED 状态、`idx_order_expiry` 索引、`termination_requested_at` 与 `status_reason_code`；会话与 Payment Intent 也都允许 EXPIRED。Client 查订单到期后不再返回 `checkoutUrl`，消费者查收银台到期返回 `CHECKOUT_EXPIRED`，提交支付与 Payment 新尝试都拒绝过期订单，Cancel 对 EXPIRED 返回 `ORDER_NOT_CANCELLABLE`。
- **仓库里没有任何代码把订单、会话或 Intent 写成 EXPIRED。** acquiring 没有过期扫描任务，订单仓储只有 `requestTermination` / `cancel` / `markPaid`，会话仓储只有 `cancel` / `complete`；Payment 只在新提交时拒绝过期 Intent，不自行收尾。
- **Client Webhook 缺 `order.expired`。** acquiring 事件类型只有 `order.paid`、`order.cancelled`、`refund.created`；Platform 侧 `order.expired` 类型已登记。
- **Payment 终止仍是固定 Mock。** `FixedPaymentTerminationClient` 永远返回 TERMINATED（TODO PAY-I12）；它按 clientId + orderId 定位，Payment 只提供按 intentId 的终止接口，且客户端没有 ALREADY_PAID 的对应值。
- **已定规则。** Client API v1 设计 14.1 节：到达 `expiresAt` 只阻止新的支付动作；仍有已提交或 UNKNOWN 渠道动作时等待 Payment 收敛；最终只能收敛为 PAID 或在明确无成功支付后 EXPIRED，不能按本机时钟覆盖未知资金结果。`order.expired` 载荷与 `order.cancelled` 同结构。
- **与 3DS 需求的耦合。** 3DS 需求 D4 的"晚到成功照常推进"依赖"过期订单在库中仍是 OPEN"；一旦开始写 EXPIRED，`ApplyPaymentSucceededProcessor` 对 EXPIRED 订单只记支付投影并告警、资金转人工。因此过期收尾必须在 Payment 确认无未决动作之后才写，不能按时钟直接写。

## 2. 目标

1. 到期未支付的订单与会话最终都进入 EXPIRED，Client 收到 `order.expired`，GET 订单、收银台接口与库中状态一致。
2. 到期不覆盖资金结果：仍有未决渠道动作的订单等待 Payment 收敛，渠道已扣款的按晚到成功处理，不制造资金人工处理单。
3. Cancel 与过期收尾共用真实 PAY-I12 终止链路，取代固定 Mock。

## 3. 概念

| 术语 | 含义 |
|---|---|
| 到期 | 当前时刻不早于订单 `expires_at`。到期本身只阻止新的支付动作，不改状态 |
| 过期收尾 | 把到期且确认无成功支付、无未决动作的订单与会话写成 EXPIRED，并发出 `order.expired` 的过程 |
| 终止 | 调用 PAY-I12 让 Payment 关闭 Intent；Payment 返回决定：TERMINATED、ALREADY_TERMINATED、PENDING、REVERSAL_REQUIRED、ALREADY_PAID |
| 未决动作 | Attempt 存在进行中的渠道动作（PROCESSING / UNKNOWN）或受理后尚未建 Action；Payment 对此返回 PENDING |
| 晚到成功 | Payment 在订单到期后才确认成功；订单仍 OPEN 时照常置 PAID |

## 4. 本期范围

| 编号 | 内容 |
|---|---|
| R1 | **过期扫描。** acquiring 定时任务按 `idx_order_expiry` 扫描 `order_status IN (CREATED, OPEN) AND expires_at <= now` 的订单，分片分页，逐单触发收尾；机制见 Q1 |
| R2 | **单订单收尾。** 无 Intent（含仍为 CREATED）的订单在本地条件更新下把订单与会话置 EXPIRED，写 `status_reason_code`，同事务登记 `order.expired`。已有 Intent 的订单先写 `termination_requested_at` 阻止新提交，事务外调用 PAY-I12，按 B2 处理决定 |
| R3 | **真实 PAY-I12 终止客户端。** 用订单支付投影中的 Intent ID 调用 Payment 终止接口，映射五种决定，删除 `FixedPaymentTerminationClient`；Cancel 与过期收尾共用 |
| R4 | **Payment 终止原因。** 终止请求区分 CANCELLED / EXPIRED，Intent 按原因写对应终态；见 Q2 |
| R5 | **`order.expired` Webhook。** 新增事件类型，Event ID `evt_<orderNo>_expired`，载荷与 `order.cancelled` 对齐（`reasonCode`、终态时刻、`version`） |
| R6 | **读侧一致性。** GET 订单、GET 收银台、Cancel、提交支付、`PaymentSucceeded` 处理对 EXPIRED 订单的现有分支逐项核对，不改语义 |
| R7 | **测试与验收。** acquiring auto 测试覆盖 B2 的每个分支与并发；Payment 终止接口的测试 DTO 端点；mor-test 验收脚本新增过期场景 |

## 5. 不做项

| 项 | 原因 |
|---|---|
| Payment 自行扫描过期 Intent | Intent `expires_at` 与订单相同，由 acquiring 驱动终止即可；兜底扫描后置（Q6） |
| 延长 `expires_at`、自动创建替代订单 | 设计明确禁止；Client 需要新 `client_order_id` 重新下单 |
| REVERSAL_REQUIRED 时自动 Void / Reversal | Cancel 不静默升级，过期同样不升级；记录告警转人工 |
| 3DS 交互过期后的查单收敛 | 3DS 需求 R3 / B5 负责；本需求只在 PENDING 时等待 |
| 收银台页面、运营控制台"待人工处理"页面 | 收银台前端另立；人工处理形式见 Q5 |

## 6. 业务规则（草案）

- B1 到期只阻止新的支付动作。EXPIRED 只能在确认没有成功支付、没有未决动作后写入，不按本机时钟直接写。
- B2 已有 Intent 时按 Payment 决定处理：TERMINATED、ALREADY_TERMINATED 或 Intent 不存在 → 本地置 EXPIRED；PENDING → 订单保持 OPEN，下轮扫描重试；ALREADY_PAID → 不改状态，等 `PaymentSucceeded` 置 PAID；REVERSAL_REQUIRED → 保持 OPEN，记录告警转人工。
- B3 收尾写入是按 `version` 的条件更新：订单与会话同事务置 EXPIRED，`order.expired` 同事务登记；状态或版本已变则放弃本轮，不重试写入。
- B4 终止请求一经写入 `termination_requested_at`，收银台提交支付被拒（现有 `allowsPayment` 规则）；Payment 侧终止与 pay 共用 Intent 锁，不会交错写结果。
- B5 到期后仍 PENDING 的订单最长等待 24 小时（与 3DS Q4 一致）；超过后记录异常转人工，订单继续保持 OPEN，不自动判失败。
- B6 过期的终态业务时刻与 `status_reason_code` 取值见 Q3、Q4；`order.expired` 只发一次，重复扫描与多实例并发不产生第二个事件。
- B7 Cancel 遇 EXPIRED 返回 409 `ORDER_NOT_CANCELLABLE`（现有）；过期收尾遇已 CANCELLED 或 PAID 的订单直接跳过。

## 7. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 到期且无 Intent 的订单（CREATED，或 OPEN 但未提交支付）在下一轮扫描内订单与会话置 EXPIRED，写 `status_reason_code`；GET 订单返回 EXPIRED 且 `checkoutUrl` 为空 |
| AC-02 | 到期且有 Intent、无未决动作的订单：PAY-I12 返回 TERMINATED，Payment Intent 进入终态（EXPIRED 或 CANCELLED，按 Q2），订单与会话置 EXPIRED |
| AC-03 | 到期但 Payment 返回 PENDING：订单保持 OPEN，`termination_requested_at` 已写，收银台提交支付被拒；Payment 收敛后下一轮扫描完成收尾 |
| AC-04 | 到期但 Payment 返回 ALREADY_PAID：订单不写 EXPIRED；`PaymentSucceeded` 到达后置 PAID 并发 `order.paid` |
| AC-05 | 到期但 Payment 返回 REVERSAL_REQUIRED：订单保持 OPEN，记录告警，不创建 Refund / Void / Reversal |
| AC-06 | `order.expired` 经 Platform 受理并送达，Event ID `evt_<orderNo>_expired`，载荷字段与 `order.cancelled` 对齐，同一订单只有一个事件 |
| AC-07 | Cancel 使用真实 PAY-I12：PENDING 返回 202，ALREADY_PAID 返回 409 `REVERSAL_REQUIRED`，Intent 不存在时本地取消；`FixedPaymentTerminationClient` 删除 |
| AC-08 | 已 EXPIRED 的订单：Cancel 返回 409 `ORDER_NOT_CANCELLABLE`，消费者 GET 收银台返回 `CHECKOUT_EXPIRED`，提交支付被拒 |
| AC-09 | 同一订单被重复扫描或多实例并发处理时，EXPIRED 只写一次、事件只登记一次，其余实例无副作用 |
| AC-10 | 到期后 PENDING 超过 24 小时的订单被记录为待人工处理（形式按 Q5），订单仍 OPEN |
| AC-11 | Payment 终止接口按原因写 Intent 终态；重复终止返回 ALREADY_TERMINATED，不改写 |
| AC-12 | mor-test：验收脚本创建短期限 Checkout，等待扫描后 GET 为 EXPIRED，`order.expired` 签名送达并与 GET 一致 |

## 8. 待确认问题

2026-10-22 已确认：Q1—Q7 均按建议列采纳。

| 编号 | 问题 | 建议（已采纳） |
|---|---|---|
| Q1 | 扫描机制：XXL-Job 定时扫描 `idx_order_expiry`，还是创建订单时登记到期执行的补偿事件？ | **XXL-Job 定时扫描**（每分钟，分片 + 分页，单订单独立事务）。索引为此而设，不新增表；补偿事件库没有"指定时刻首次执行"的能力，且每单一条事件会在 24 小时内反复重试 |
| Q2 | Payment 终止接口是否区分原因？ | **区分。** 终止请求加原因（CANCELLED / EXPIRED），Intent 按原因写 CANCELLED 或 EXPIRED（schema 已允许）；不加则过期的 Intent 都显示为"取消"，与设计的 Intent 状态表不符 |
| Q3 | 过期的终态业务时刻用什么？订单表没有 `expired_at` 列 | **用 `expires_at`，不加列。** 到期时刻就是业务终态时刻，PENDING 延后收尾不改变它；`order.expired` 的 `expiredAt` 取该值，实际写入时刻在 `update_time` |
| Q4 | `status_reason_code` 取值 | `EXPIRY_REACHED`，与现有 `CLIENT_REQUESTED` 并列登记到 Commerce Order Status Reason Catalog |
| Q5 | 24 小时仍 PENDING 的"转人工"形式 | **本期只记告警日志并保持 OPEN**，不做运营页面；Operations 可用数据查询服务按 `termination_requested_at` 查出这些订单 |
| Q6 | Payment 侧是否要兜底扫描过期 Intent？ | **本期不做。** acquiring 驱动即可；Commerce 丢失投影的兜底后置 |
| Q7 | 与 3DS 需求的实施边界：两者都改 Payment 终止 Processor 周边与 acquiring 测试 `MockPaymentIntentApi` | 阶段 2—3 可与 3DS 并行；**阶段 4 待 3DS 合入主工作区后再开始**，以其为基线，避免同一文件两份未合并改动 |

## 9. 建议适用阶段

阶段 1—8 全部适用：涉及 acquiring（任务、Processor、仓储、Webhook、终止客户端）与 payment（终止原因）；预计无 DDL（Q3 不加列）、无 Platform 路由注册；需部署 mor-test（acquiring、payment）并用验收脚本验证 `order.expired`。
