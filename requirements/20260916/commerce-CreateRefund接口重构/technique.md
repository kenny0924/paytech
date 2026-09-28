# CreateRefund 技术说明

规则入口：[公共开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[Processor 规范](../../../payment-orchestration-service/docs/development/processor-conventions.md)、[退款数据权威](../../../agent-docs/database/COMMERCE_DATABASE.md)、[补偿事件规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)。

## 请求流程

1. API Body 的标准注解和现有公共 Filter 校验单字段及 Header；Converter 合并可信 Client、orderNo 和 Body。
2. `CreateRefundProcessor` 继承 `AbstractProcessor`。先单表查询归属当前 Client 的 CommerceOrder，并以公共 Money 校验退款金额。
3. 单表查询 Client + clientRefundId。存在时比较原订单、金额、币种、reasonCode、reasonSummary，返回原 RefundRequest 创建事实；不以当前可退额度或支付状态阻断原申请重放。
4. 新申请检查订单 PAID，按需读取支付投影和退款位置。Context 持有四个实际数据库对象；对象负责金额不变量，Context 核对跨对象的已支付事实及可退额度。没有 Session 或整单 OrderRecord 依赖。
5. Service 使用现有 REQUIRED TransactionTemplate：插入 RefundRequest → 条件预占 OrderRefundPosition → 登记 RefundSubmissionHandler 任务。任何失败整体回滚；Service 不重复加载业务上下文。
6. Foundation 仅在提交后唤醒既有 Handler，Payment 调用仍位于其两次本地事务之间。创建响应由 Context 中的原对象产生，不另查响应快照。

## 防重与并发

Client 业务退款号仍由既有唯一索引永久约束。相同内容返回原创建号、金额和创建时间；不同内容返回 `CLIENT_REFUND_ID_CONFLICT`。并发唯一键失败发生在事务回滚后，Processor 只读取获胜的原退款并比较，不再次执行写事务。额度竞争由原 Position Version/CAS 和可用余额条件保护，订单及支付投影版本、成功状态、币种及捕获金额作为同库资格条件；失败按并发冲突返回。

保留本切片已有的数据库唯一约束和条件更新，不引入 Redis 运行依赖。预占 SQL 只写 Position，一次关联 Order、Payment Projection 两张表核对资格。终态提交路径继续使用原 Order → Position → Refund 的本地锁定流程。

HTTP `Idempotency-Key` 只负责接入格式校验，不进入内部 Request，不查询或写入 `CREATE_REFUND` 命令。相同 Key 使用不同 clientRefundId 是不同业务申请；Gateway 通用请求缓存不在本轮。补偿任务自身 Claim/Lease 与 Payment 下游的稳定退款身份继续保留，它们承担任务恢复和资金防重，不能一起删除。

## 持久化对象

`RefundRequest` 只包含本表字段。既有查询需要关联 orderNo 时使用两表 `RefundRequestView`；创建读取不使用该 View。Position 查询、预占和终态金额更新归 `OrderRefundPositionRepository`；`RefundRepository` 仅拥有退款表。Repository 先检查 SQL 行数，再修改传入对象的字段、version、updateTime，不复制或替换对象。

没有 DDL、数据迁移、PDManer 或 Gateway 配置变更。历史命令行与共享幂等组件不删除，其他消费者不变。旧退款查询和 Handler 仅做共享对象/仓储签名适配，其独立流程的进一步规范迁移另行处理。

## 接口与交付边界

HTTP 路径、Body 字段、201、Location 和金额主单位字符串不变。创建 Body 改为独立普通类，内部新增具名 Request/Response 和纯 Converter，旧创建命令/身份/回执模型及专用幂等 Service 删除。测试源码暂未适配已删除类型、构造器及 record accessor，不将旧测试结果当作当前编译或回归证据。

Payment 实际 PAY-I04、结果查询/MQ、Platform Webhook 保留现有 TODO 和不可用边界。具体实现、验证和交付状态只见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-refund-20260916)。
