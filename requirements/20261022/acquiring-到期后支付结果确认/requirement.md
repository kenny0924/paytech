# acquiring：订单到期后的支付结果确认 · 需求

> 状态：**阶段 1 评审稿（第 2 版）**（2026-10-23，主工作区 main `d3d39e40`）。第 1 版的"30 分钟宽限期 + 收银台只读视图"已按用户 2026-10-23 确认改为参照 Stripe 的"提交过支付即不受到期限制"，且只改结果查询。确认记录见 [delivery.md](./delivery.md)，现状证据见 [technique.md](./technique.md) 第 1 节。
> 来源：[acquiring-订单过期收尾](../acquiring-订单过期收尾/delivery.md) 阶段 5 行为发现 F1；用户 2026-10-23 要求处理。

## 1. 背景

- 持卡人在收银台提交卡支付后跳到银行做 3DS 验证。Payment 给这次交互的期限是 30 分钟（`PayActionActor.INTERACTION_WINDOW`），与订单 `expires_at`（默认 30 分钟、最短 5 分钟）各自计算。
- 订单一旦到期，查询支付提交结果（`GetCustomerPaymentProcessor`）在调用 Payment 刷新之前就返回 410 `CHECKOUT_EXPIRED`。
- 后果：持卡人在到期之后才从银行跳回，只能看到"已过期"。即使渠道已经扣款，订单之后照常变成 PAID（过期收尾遇到未决动作不写 EXPIRED，晚到成功照常推进），持卡人当时也看不到结果；结果确认只靠 Payment 退避查单（`compensationEventScan`）或渠道异步通知，最慢可达分钟级。
- 这是现行设计的规定，不是实现缺陷：[Client API v1 设计](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 第 523、622 行规定 `expiresAt` 前终态页面可只读显示统一结果，到期后任何状态都不能再发起支付。

## 2. 参照：Stripe Checkout

- Checkout Session 只有 `open` 状态能过期；客户提交支付、完成结账后会话变成 `complete`，不再过期（[Expire a Checkout Session](https://docs.stripe.com/api/checkout/sessions/expire)）。
- 付款结果与会话状态分开跟踪：延迟到账方式下会话已 `complete` 而 `payment_status` 仍 `unpaid`，之后以 `async_payment_succeeded` / `async_payment_failed` 收尾（[Fulfill orders](https://docs.stripe.com/checkout/fulfillment)）。
- 客户跳回商户 `success_url` 后按 session_id 查询结果，不受会话过期影响。
- 本需求采用相同原则：**提交过支付的订单，支付结果查询不受到期限制**；不设宽限期，因此不新增配置。客户正在做 3DS 时会话到期，Stripe 官方文档未明说，上述只是按状态模型推断，不作为依据。

## 3. 目标

1. 到期前已提交的支付，持卡人在到期后查询，仍能看到真实结果：成功、失败或"确认中"。
2. 到期后的查询仍能推动 Payment 立即查单（复用 3DS 的刷新接口与限频），不必等退避周期。
3. 不放开任何新的支付动作：到期后不能新建或重新提交支付，规则不变。

## 4. 本期范围

| 编号 | 内容 |
|---|---|
| R1 | **结果查询不受到期限制。** 查询支付提交结果接口：订单已有支付记录（`OrderPaymentProjection`，提交支付时写入）时，到期不拦截，照常调用 Payment 刷新并返回真实结果；没有支付记录维持现状 |
| R2 | **设计修订。** 修订 Client API v1 设计第 523、622 行：到期后仍禁止任何支付动作，Client 查询的 `checkoutUrl` 仍按原规则到期即为空；Consumer 侧对已提交支付的结果查询不受到期限制 |
| R3 | **测试与验收。** acquiring auto 测试从查询入口覆盖第 7 节场景；本机 e2e 用 3DS 挑战场景验证"到期后跳回仍能看到成功" |

## 5. 各系统改动点

| 系统 | 改动点 | 说明 |
|---|---|---|
| acquiring | `GetCustomerPaymentProcessor` 的到期检查 | 从入口挪到"无支付记录"与"Payment 未找到该提交"两个分支（阶段 3 定稿，不新增领域方法）；其余流程（Payment 刷新、不可用降级为处理中）不变；`checkExpiry` 继续用于所有写操作与查看收银台 |
| acquiring | auto 测试 | 查询入口场景矩阵（AC-01—AC-06） |
| 设计文档 | Client API v1 设计第 523、622 行 | 按 R2 修订 |
| payment | 无 | 刷新接口与 5 秒限频（3DS 已交付）满足需要 |
| Platform / Gateway | 无 | 路由与接口字段不变 |
| 数据库 | 无 | 不新增列、不需要 DDL |
| 收银台前端 | 无（本期） | `checkout-web` 尚无代码；见第 6 节对后续前端需求的约束 |

## 6. 不做项

| 项 | 原因 |
|---|---|
| 查看收银台接口（`GetCustomerCheckoutProcessor`）到期后放宽 | 用户 2026-10-23 确认推迟到 `checkout-web` 需求：只读页面显示什么（支付按钮、消费者资料是否回显）需随页面设计一起定。约束：前端从银行跳回后应先查询支付提交结果，再决定是否打开收银台页面 |
| 宽限期配置 | 改为提交过支付即不受限，无需时长 |
| 延长订单 `expires_at` 或到期后允许重新支付 | 设计禁止，规则不变 |
| Client API 的 `checkoutUrl` 在到期后继续返回 | 外部合同不变，避免 Client 侧把过期订单再分发给消费者 |
| acquiring 另加限频 | 沿用 Payment 刷新的 5 秒限频 |
| 已关闭订单收到渠道晚到成功的自动退款 | 另立需求，前置 Payment 退款链路 |

## 7. 业务规则

- B1 只放宽支付结果查询，不放宽任何写操作：保存资料、确认条款、提交支付在到期后仍返回 410。
- B2 只对已有支付记录的订单生效；没有支付记录的订单到期后与现在一样返回 410。
- B3 查询结果以 Payment 刷新结果为准；Payment 不可用时返回"确认中"，不推断失败。
- B4 订单已被过期收尾写成 EXPIRED 时仍可查询，返回 Payment 的最终未成功结果；不新增特殊分支。
- B5 不新增列和配置。

## 8. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 到期前提交、处于 3DS 挑战中的支付，到期后查询提交结果：触发 Payment 刷新；渠道成功时返回成功，订单随后 PAID |
| AC-02 | 同上场景，渠道失败时返回失败；订单在下一轮过期扫描后为 EXPIRED，此后再查询仍返回失败 |
| AC-03 | 到期后渠道仍处理中：返回"确认中"，刷新受原 5 秒限频约束 |
| AC-04 | 到期时没有支付记录的订单：查询提交结果返回 410，与现在一致 |
| AC-05 | 到期后提交支付、保存资料、查看收银台：仍返回 410 |
| AC-06 | Client API GET 订单在到期后 `checkoutUrl` 仍为空（外部合同不变） |

## 9. 已确认事项

| 编号 | 结论（用户 2026-10-23） |
|---|---|
| Q1 | 不设宽限期：提交过支付即不受到期限制（参照 Stripe） |
| Q2 | 查看收银台本期不放宽，推迟到 `checkout-web` 需求 |
| Q3 | 不在 acquiring 另加限频，沿用 Payment 5 秒限频 |
| Q4 | 直接修订 Client API v1 设计第 523、622 行并在本需求链接 |

## 10. 建议适用阶段

阶段 1—8 全部适用：只改 acquiring（一个领域方法、一个查询 Processor、测试）与设计文档；无 DDL、无 Platform 注册；需部署 mor-test（只换 acquiring，须与 code-mor-78 协调，其 2026-10-23 正在替换 payment / acquiring），本机 e2e 用合成 PayerMax 3DS 场景验收。
