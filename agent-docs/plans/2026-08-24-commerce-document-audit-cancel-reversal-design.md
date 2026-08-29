# Commerce 文档投影、审计与取消撤销精简设计

最后更新：2026-08-27  
状态：已确认，作为 Commerce 14 表物理模型和 Cancel/Void/Reversal 跨域语义的现行权威设计

> **同步说明（2026-08-27）：** 本文已按 [Finance 退款 Funding 移除实施记录](./2026-08-27-finance-refund-funding-removal-implementation-plan.md) 同步已 Capture Reversal 的退款链路：Commerce 本地预占后直接调用 PAY-I04，Payment 使用本地 Refund Position，Finance 只消费 `RefundSucceeded` 入账。

## 1. 决策

本设计固化已经确认的 V1 决策：

1. 删除 Commerce 的 `t_consumer_document`。Receipt、Credit Note 及其版本、文件和更正关系由 Platform Support 拥有；Finance 保留不可变 Fiscal Fact。Commerce 只在 Order 支付成功或 Refund 成功的本地事务登记可靠事件，不再保存三方 Current Projection。
2. 删除 Commerce 的 `t_business_change_record` 与 `t_business_change_version`。Commerce 当前没有必须以通用 JSON 历史模型保存的审计对象；在线状态仍由结构化主表表达，专项不可变事实继续由自己的表保存。
3. 保留业务主表上的必要 `version`，但不因删除 BCR 增加专用状态历史表。当前值和终态原因必须结构化保存。
4. `t_commerce_order` 增加可空 `status_reason_code`，取值来自 Commerce Order Status Reason Catalog，用于解释当前 `CANCELLED`、`EXPIRED` 或其他需要说明的终态；它不是状态历史。
5. 增加严格区分的 Cancel、Void 和 Reversal 能力，不新增 Cancel、Void 或 Reversal 业务表。

## 2. 三种操作的精确语义

### 2.1 Cancel

Cancel 是 Commerce 拥有的未支付订单关单命令。仅当不存在成功的 `AUTHORIZE`、`CAPTURE`、`SALE`，且不存在已提交 Provider 但结果未知的 Action 时允许完成。

- Commerce 调用 Payment `PAY-I12` 终止 Intent；只有 Payment 返回确定终止结果后，Commerce 才把 Order 和 Checkout Session 置为 `CANCELLED`。
- Provider 提交前的活动 Attempt 可由 Payment 原子结束为 `FAILED(CANCELLED_BEFORE_PROVIDER_SUBMIT)`。
- 已提交或 `UNKNOWN` 的 Provider Action 必须先 Query，不能假定未支付。
- Query 发现授权成功或已经捕获时，Cancel 返回 `REVERSAL_REQUIRED`，不得静默升级为 Reversal。
- Cancel 不产生 Finance、退款、Fiscal 或消费者文档事实。

### 2.2 Void

Void 是 Payment 内部对成功 Authorization 的渠道动作。项目术语中“授权成功但未 Capture”不是 `PaymentSucceeded`；`PaymentSucceeded` 只表示 `SALE` 成功或已完成全额 Capture。

- 前置条件为原 `AUTHORIZE Action=SUCCEEDED`、`captured_amount=0`，且不存在 Capture/Sale 已提交、成功或未知。
- V1 只允许全额释放原授权。
- `payment_action` 增加可空 `target_provider_transaction_ref_id`；`VOID` 时必填并精确引用同 Attempt、Provider、MID 和币种下的原 Authorization Ref。
- Void 成功产生的新 Provider Transaction Ref 继续通过 `original_provider_transaction_ref_id` 指向原 Authorization。
- 业务撤销触发的 Void 成功后，Intent、Order 和 Session 进入 `CANCELLED`；Attempt 以 `FAILED(AUTHORIZATION_VOIDED)` 表示没有完成收款目标，Void Action 自身仍为 `SUCCEEDED`。
- Void 不创建 Refund Request、Funding Reservation、Finance Journal、Fiscal Fact 或 Credit Note。

### 2.3 Reversal

Reversal 是 Commerce 接收的统一业务撤销命令，不是新的 Payment Action 类型：

- Payment 在锁/CAS 内读取权威状态并决定分支，避免 Commerce 查询后到执行前发生 Capture 竞争。
- 授权成功且 `captured_amount=0`：Payment 原子阻止 Capture 并执行 Void。
- `SALE` 成功或已经全额 Capture：Payment 返回 `REFUND_REQUIRED`，Commerce 创建标准全额 Refund Request，复用 Commerce Order Refund Position 与 Payment Channel Refund 主链路；Finance 只在 `RefundSucceeded` 后入账。
- Capture 正在提交或结果未知：返回 `PENDING`，Query 收敛后幂等重试。
- Captured Reversal 完成退款后 Order 仍为 `PAID`；退款事实由 Refund Request 和 Refund Position 表达，不增加 `REVERSED` Order 状态。
- V1 不支持 `0 < captured_amount < order.total_amount` 的自动 Reversal；该状态返回 `PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED` 并进入人工处置。后续若支持，必须设计“Void 未捕获余额 + Refund 已捕获金额”的复合聚合。

## 3. 接口与事件所有权

- `COM-I05 取消未支付订单`：Client System、Hosted Checkout、Client Console 和 Operations Console 通过 Commerce 发起。
- `COM-I08 撤销支付`：V1 作为 Operations 能力；Client 继续使用 Cancel 或已有 Refund API，不感知 Auth/Capture。
- `PAY-I12 终止支付意图`：扩展 Provider 提交前活动 Attempt 的安全终止；已提交或未知结果不得终止。
- `PAY-I13 解析并执行支付撤销`：在 Payment 权威锁内返回 `VOID_ACCEPTED`、`REFUND_REQUIRED`、`PENDING`、`USE_CANCEL` 或不支持结果。
- `OrderCancelled` 是 Commerce 订单终止事实；`PaymentIntentTerminated` 仍是 Payment Intent 终止事实。
- Reversal 不新增 `payment_action.action_type=REVERSAL`，也不新增 `t_reversal_request`。捕获分支创建现有 `t_refund_request`。

## 4. Commerce 物理结果

删除三张表并新增一个订单字段后形成的基线，连同 2026-08-26 Finance Entity 双账簿设计为 Order Context 新增的两个外部稳定 ID 字段，构成当前 Commerce V1：

- 14 张表
- 223 个字段
- 20 个唯一索引
- 11 个普通索引
- 14 条本库关系
- 4 个 Diagram Groups
- 7 张业务 Version 表
- 6 张创建后不可变表
- 0 个 Commerce BCR Record Type

第四个 Diagram Group 重命名为“可靠执行”，只包含 `t_command_idempotency` 和 `t_compensation_event`。Commerce PDManer 不增加跨库外键；Payment 当前物理模型已经生成，精确使用 `target_provider_transaction_ref_id` 表达原交易引用。

## 5. 范围

本设计授权需求目录、全局时序、逻辑数据库文档、现实场景、Commerce 生成器、自动契约测试与 PDManer 文件更新。它不授权 Java、Migration、物理数据库、基础设施、部署、分支或提交。
