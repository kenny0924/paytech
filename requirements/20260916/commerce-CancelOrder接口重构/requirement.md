# CancelOrder 按 Processor 规范重构

本次仅处理 Commerce `ClientOrderApi#cancelOrder`，用户已明确纠正：取消用例不要请求幂等组件，必须按 `processor-conventions.md` 重新组织业务流程。此前保留 Hash、Claim/Lease、恢复阶段和稳定回执的实现方向被本次要求取代。

1. HTTP 保留接入协议 Idempotency-Key，取消业务 Request 不接收或使用它；不增加 Gateway 实现。
2. 取消业务由订单原事实防重：已取消直接返回原取消时间，不重复写入或调用 Payment。
3. Processor 按归属查询、原结果返回、新请求资格校验、完整 Context、本地保存、事务外调用和响应顺序组织。Context 持有 CommerceOrder、可空 CheckoutSession 和按需读取的 OrderPaymentProjection，不保存命令或执行标记。
4. Service 只做本地保存并维护 REQUIRED 事务；Processor 不再为了组合命令表操作包事务，也不在事务内加载整单、执行普通业务验证。
5. Order、实际关联的 Session 和支付事实的条件更新保护并发；只有未开始支付准备的基础订单可本地取消，其他情况必须取得 Payment 确定终止结果。
6. 不冻结请求失败或响应；取消响应移除内部 version，保留业务状态和原取消时刻。
7. 取消链路不使用 OrderRecord 或整单联表加载；Processor 按需读取各表对象，不为共用查询加载商品、资金、法律或旧创建命令。
8. Hosted Checkout 必须有 Session；Direct Pay 允许无 Session，若实际关联了会话也需一起关闭。无 Session 时不返回 checkoutStatus。
9. 原取消时间归属 Order：增加 t_commerce_order.cancelled_at，并同步模型、目标 Schema 和人工增量 SQL。既有会话保留自己的取消时间；本次不执行迁移。

代码规范见 [Payment 示例](../../../payment-orchestration-service/docs/development/processor-conventions.md)、[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)和[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)。本次不改 Refund、Customer 的幂等合同，不删除共享命令表或历史数据，不修改公共 Foundation 组件。不执行 SQL、测试、构建、提交或部署，确认见 [delivery.md](delivery.md)。
