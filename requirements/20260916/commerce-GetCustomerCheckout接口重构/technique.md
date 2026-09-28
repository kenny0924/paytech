# Customer Checkout 查询技术说明

遵循[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)和[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。

## 调用与数据

`CustomerCheckoutController.getCheckout` → `GetCheckoutConverter.request` → `GetCustomerCheckoutProcessor.process` → `GetCheckoutConverter.response`。

1. Foundation `AbstractProcessor` 拒绝已有事务；`validate` 校验原 Token 格式，保持错误语义。
2. Processor 从 `CheckoutSessionRepository` 读取 Token 实际绑定的 Session，再从 `CommerceOrderRepository` 读取原订单，调用 `CommerceOrder.checkExpiry(now)`。到期规则留在订单对象，时刻由 Processor 传入。
3. 直接查询 `ConsumerContextRepository`、`OrderContextRepository`、`OrderPaymentProjectionRepository` 和 `OrderItemRepository`。单表结果使用本库对象；法律展示使用三表 `CheckoutLegalView`，只取国家、语言、公开卖方和文件引用。关联缺失保留上下文行，版本稳定后才报告冻结事实不完整。
4. 国家优先使用原冻结上下文，准备前使用订单字段；不再读取历史创建命令快照，也不在代码中补造缺失国家。
5. 消费者 Reveal、三份原文件链接和 Payment 摘要都在事务外读取。只有实际保存消费者且 `includeConsumer=true` 才 Reveal；只有本地绑定 Intent 才查询 Payment。`PaymentStatus.validate(now)` 封装摘要的观察时刻、闭合状态和动作关系判断，使用外部查询完成后的时刻。Processor 仅保留查询、结果缺失检查和 ServiceUnavailable 降级；畸形协议继续失败，不新增协作者层。
6. 一次构造普通类 `CheckoutQueryContext`；就绪、开放状态、允许操作、轮询和版本比较都基于已有数据，Context 不访问 Bean 或外部服务，时间显式传入。
7. 返回前只读取 Order/Session/Consumer 版本及 Payment 投影版本，不重查商品和法律详情。按用户当前源码不自动重读，变化时使用原可重试冲突，由调用方重新查询；过期仍失败，动作资格按返回时刻计算。
8. 内部 Response 和嵌套金额、商品结果改为普通 class；外部 ResponseBody/Info 保持普通 class。Converter 使用公共 `Money.toAmountString()` 按币种输出，未确定金额保留 null，不加文本往返解析或防御性捕获。

查询无本地写入、事务、资源锁、幂等占位或提交后推进。Processor 不访问 Mapper；HTTP 边界不泄露 DataObject、Vault Token 或内部版本。

## 兼容与范围

扩展已有订单、Session、商品和支付投影 Repository；新增消费者表查询及上下文法律展示查询边界，不增加查询 Service。`OrderItem`、`CheckoutVersionView` 改为普通 class；商品保留既有 fluent 访问及字段序列化形状，避免扩大其他用例迁移。

移除旧 Service 的 GET 专用 `unchanged(CheckoutState)` 和旧聚合 Mapper 的版本查询。文件单项链接复用现有 Integration Client，不复制外部访问逻辑。

2026-09-17 同步清理 `CheckoutIdempotencySnapshotCodec`、`CheckoutCreationSnapshot`、`OrderCreationView`，以及 Customer / Order Repository 的创建快照读取、Mapper SQL 和 OrderRecord 中的三项创建命令字段。Client 订单查询与准备恢复直接使用原订单或冻结上下文国家。该调整不再支持依靠旧命令快照补齐缺失国家；不执行历史数据回填，也不删除其他 Customer 命令使用的共享幂等机制。

HTTP 路径、参数与字段不变。内部 Response 访问器、Processor 构造器和查询语句身份变化，相关测试需后续适配。无新表、字段、索引、DDL 或 DML，不涉及 PDManer。

后续验证应覆盖 Token/期限、准备前后展示、显式消费者回填、原文件链接、Payment 不可用及畸形摘要、版本变化直接冲突、无副作用、金额与空值、直接读取国家，以及查询数量和单条 SQL 表数。旧 Codec / 快照测试需后续清理，本次不执行验证，当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-customer-get-query-20260916)。
