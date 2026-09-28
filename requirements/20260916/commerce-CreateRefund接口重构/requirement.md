# Commerce CreateRefund 接口重构

用户指定重构 `ClientRefundController#createRefund`，沿用此前确认的接口分层、按需查询、普通类对象及本地事务规范。本轮授权为该入口及必要调用链的代码重构；按用户要求暂不处理测试、不运行验证，不包含提交、部署或数据库操作。

- HTTP Controller 只转换协议，Processor 使用 Foundation 模板和内部 Request/Response。
- DB、Context、RequestBody、ResponseBody 使用普通 class；自身和上下文的纯判断归对应对象。
- 创建按可信 Client 与 clientRefundId 查询原申请，按订单、金额、币种、原因比较原内容；相同内容返回原 201 创建回执，不再预占或触发提交。
- 新申请在事务外读取 CommerceOrder、OrderPaymentProjection、OrderRefundPosition，Service 以 REQUIRED 原子保存退款、预占及可靠提交任务。
- 事务内只有本库数据库操作；Payment 仍通过提交后的既有 Handler 调用。
- 移除本入口的请求 Hash、Claim、Lease 和响应快照消费，HTTP Idempotency-Key 继续保留并校验格式；不为此修改 Gateway 或 Foundation。
- 创建支持已支付的 Hosted Checkout 和 Direct Pay 订单，不读取或要求 Session。

数据库字段、索引和外部响应 JSON 不变。涉及的普通类和方法变更需要 Java 消费者重新编译；退款查询及 Handler 的独立用例设计、Payment 真实对接均不扩大到本轮。

验收预期为：原业务退款重试不重复预占；改内容拒绝；跨 Client 统一不存在；不足额度或并发冲突不留下部分记录；事务失败不登记可执行任务；原始创建时间不刷新。实际验证状态只见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-refund-20260916)。
