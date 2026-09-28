# CancelOrder 业务流程与保存边界

本次修改 Commerce 的现有取消接口；新增订单取消时间一列，不新增表、索引、配置或其他系统接口。Direct Pay 创建、支付准备和消费者接受入口仍未实现，本次仅使取消用例能处理没有 Session 的订单事实。

## Processor 与按需查询

`CancelOrderProcessor` 继承 Foundation `AbstractProcessor`；Controller 只调用 Converter 和 Processor。查询及业务验证位于事务外，查询方法不建立事务。

1. CommerceOrderRepository 按可信 Client + orderNo 查询 CommerceOrder，不联表加载 OrderRecord。
2. CheckoutSessionRepository 按 orderId 查询实际关联的 CheckoutSession。HOSTED_CHECKOUT 缺失 Session 是数据错误；DIRECT_PAY 可以为空，若有关联仍按真实会话处理。
3. Order 已取消时校验订单取消时间与已有会话终态，直接构造 Context 返回原结果，不读取支付、不写入、不调用下游。
4. 新请求先检查订单及已有会话状态，再从 OrderPaymentProjectionRepository 查询取消需要的 Intent、成功事实和本地投影版本；OrderPaymentProjection 使用普通 class，通过自身的 hasSuccessfulPayment() 判断成功 Attempt、正捕获金额或成功 Intent，Processor 据此返回 REVERSAL_REQUIRED。完整 Context 只持有三个本库表对象，其中 Session 和 Payment 可以为空。
5. Context 的 `canCancelLocally()` 基于已加载数据判断：未开始准备、未选择 OE、没有最终金额和支付投影，且无 Session 或 Session 仍为 PREPARING 的基础订单，可进入本地取消事务。Processor 根据该结果选择分支；Context 不查询、不写入、不编排事务。SQL 同时以 NOT EXISTS 保护 Order Context 和支付投影确实不存在，不为此加载完整 Order Context。
6. 其他订单先由 Service 提交终止意图；投影尚未存在时返回 PENDING，不将投影缺失等同于无远端支付。已有 Intent 时，提交后调用 Payment；只有 TERMINATED 才调用取消 Service。
7. Payment PENDING 或依赖不可用返回 PENDING，REVERSAL_REQUIRED 返回业务失败，响应坐标错误为上游协议错误。响应来自同一 Context，不重新加载整单。

旧创建命令不再参与取消资格读取；新创建本地事务原子保存 Order 及其必要会话。取消资格依据订单、实际会话、支付准备和支付事实。

## 本地事务与并发

`requestTermination(context)` 在尚无终止意图时用 REQUIRED 短事务保存订单。`cancel(context)` 在一个 REQUIRED 事务内保存必要终止意图、更新 Order，只有 Session 实际存在才更新 Session。每个 Repository 仅更新自己的表，Service 不访问外部依赖、不重复查询整套上下文。

- Order 更新核对 Client、原版本、可取消状态、paid_at 与终止意图。
- 已有 Payment 时，取消条件要求同一 Intent、原投影版本、无成功 Attempt、captured_amount=0 且 Intent 未成功。
- 本地取消条件还要求没有 preparation_command_id、OE、最终金额、Order Context 和支付投影。
- Session 使用自身主键、orderId、原版本和允许状态更新；没有 Session 不执行会话 UPDATE。
- 每次条件更新核对行数，失败回滚完整事务并返回并发冲突，不自动重读重试。成功后直接修改传入的 CommerceOrder/CheckoutSession 原对象，不用 toBuilder 重建；Repository 更新方法使用 void，Context 用 final 引用持有同一实例。两类 DataObject 使用可修改 class，创建阶段仍可使用 Builder；事务失败时丢弃本次 Context 及其对象。

已取消的重复请求只读。未决订单的后续 POST 按当前业务事实重新处理，不接管租约、不恢复技术阶段。PAY-I12 仍沿用 `cancel-order-<orderId>` 作为原 Intent 的固定终止标识，不来自 Client Header，也不涉及 Commerce 请求幂等表。

## 取消时间的数据归属

`t_commerce_order.cancelled_at DATETIME(6) NULL` 保存订单实际进入 CANCELLED 的 UTC 时刻。首次取消与订单状态在同一 UPDATE 写入，重复取消读取原值。Session 保留其自身 cancelled_at，不再作为订单取消时间的来源；不使用 update_time 或 termination_requested_at 替代最终取消时间。

Hosted Checkout 的订单与会话在同一事务用同一时刻关闭；Direct Pay 无会话时只保存订单取消事实。支付准备终止的原有订单更新也同步保存 cancelled_at，保证该路径产生的取消终态可被取消接口重复读取。

[人工增量 SQL](db/cancelled-at.sql)增加列，并仅从既有 CANCELLED Session 的原 cancelled_at 回填对应 CANCELLED Order。无法从真实来源恢复的历史异常保留 NULL，需要迁移时处理，不伪造时间。模型、字段元数据、生成器和目标 Schema 同步维护；SQL 不自动执行。部署需要先完成 creation_mode 和本次 cancelled_at 两份待执行迁移。

## 接口与请求幂等

内部 Request 只接收 clientId/orderNo；取消不读写 CANCEL_ORDER 命令、不冻结成功或失败响应。共享命令表、历史记录及其他消费者保持。Idempotency-Key 仅由现有 HTTP 边界校验格式，当前取消不保证同 Key 固定回执或不同目标冲突，不能声称 Gateway 已实现请求幂等。

200 必有 orderNo、orderStatus=CANCELLED、cancelledAt；仅实际有关联 Session 时返回 checkoutStatus=CANCELLED。无 Session 的 Direct Pay 示例：

```json
{
  "orderNo": "ord_example_direct",
  "orderStatus": "CANCELLED",
  "cancelledAt": "2026-09-16T10:00:00Z"
}
```

202 仍返回 orderNo/cancellationStatus/reasonCode。内部版本不输出；路径、无 Body 和 200/202/409 的业务语义保持。OpenAPI 同步 checkoutStatus 的可选条件。

## 未接入与验证安排

PAY-I12 保留 UnavailablePaymentTerminationClient 与 TODO，不增加业务 Mock 或伪造成功。用户要求不处理单元测试、不验证；本轮没有运行测试、构建、格式化、静态验证或 SQL。

后续验证先迁移旧 OrderModels、Processor 构造器、取消 Context、CommerceOrder 构造器与命令幂等测试，再覆盖两种创建方式、无会话取消与重复取消、Hosted 缺会话拒绝、Direct 实际关联会话、待定/成功支付拒绝、投影竞争、Session CAS 失败整体回滚以及迁移后的原取消时间。
