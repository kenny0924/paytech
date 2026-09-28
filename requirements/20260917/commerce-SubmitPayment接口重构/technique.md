# SubmitPayment 技术说明

遵循[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)和[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。此次按用户补充要求移除支付请求幂等，取代先前保留 Key/Hash 的重构方案；真实 Payment 工具/pay 接入仍是后续工作。

## 入口与处理阶段

API 使用普通类 SubmitPaymentRequestBody、PaymentSubmissionResponseBody 与 Info；Controller/Converter 处理协议转换、连接证据和响应状态。SubmitCustomerPaymentProcessor 继承 Foundation AbstractProcessor，使用内部 Request/Response，按基础校验、业务查询、完整 Context、本地事务、事务外 Payment、响应的顺序编排。严格含卡 JSON、8 KiB 限制及 metadata-only 日志边界保留。

移除 Idempotency-Key 参数及支付 Filter 校验、卡指纹端口、requestHash、原命令查找/认领/完成、响应快照解码及 retryIdempotencyKey。每次 POST 生成新的 submissionId 作为 Payment 来源业务编号；不由该编号推断支付已经成功或必然执行，也不把协议接受 ID、网络证据或任务表伪装成请求幂等记录。Preparation 的共享命令组件、历史常量与记录不变。

Processor 直接读取 CheckoutSession、CommerceOrder、ConsumerContract、OrderPaymentProjection，消费者只查存在性。合同查询保留合同/法律/上下文三表绑定。Context 持有本库对象、此次 submissionId、是否新建接受和准确三份接受，不持有卡输入、Token、HTTP 或命令对象。已有接受及同 Order/Session 的 LEGAL_ACCEPTANCE 证据保持原身份和时刻；部分接受不补造。复用证据仅查询绑定字段。

## 本地事务和 Payment

Processor 直接调用 CheckoutSubmissionService.saveSubmissionContext，不再注入任务登记器或事务执行器。Service 自带 REQUIRED 事务，只保存本库事实，不调用其他业务 Service 或外部服务。条件 UPDATE 取得实际订单行锁并核对订单/会话/投影版本、状态、期限、原合同/Intent 及消费者存在性，成功后同步原订单对象。订单版本、首次证据/三份接受一起提交或回滚；不保存本地支付提交表或命令回执。

提交后 executePayment 直接复用 SubmitCustomerPaymentContext，不重复查询 Session、Order 或消费者存在性，也不重新验证 Context 自身的订单/会话绑定。Repository 在条件写入成功后已同步原订单版本，Payment 请求使用该版本；到期校验只比较 Context 中原期限与当前时间。本地并发保护由写入条件负责，额外 SELECT 无法消除查询结束后的竞争。Payment 收到来源 submissionId、原 Order/Intent、法律接受坐标、原期限、订单版本及当前卡输入；它必须保护同一 Intent 的在途执行、成功事实和取消栅栏。Commerce 不凭本地订单状态或新来源编号绕过 Payment 的保护。卡只在当前调用中使用，不写库、任务或日志；不再有 fingerprint/Hash 或 allowExecution=false 的原结果重放分支。

200 返回当前结果，202 表示处理或结果未知，不表示成功。外部提交超时仍返回当前 submissionId 供查询，但不保证再次 POST 得到同一个提交；再次 POST 是新的业务请求。禁止由轮询器自动再次 POST；是否允许新的支付依赖 Payment 当前业务状态。真实适配保持 TODO/不可用实现，没有增加生产 Mock 或更改其他系统。

## 结果查询

查询入口的分层及请求内复用规则由后续 [GetPayment 重构](../commerce-GetPayment接口重构/technique.md)补充；原路径和结果字段保持。

GetCustomerPaymentProcessor 按 Token 读取订单、会话及 Intent，构造最小 PaymentReference(submissionId, orderId, intentId)，直接只读查询 Payment，并校验返回归属及当前访问资格。它不依赖本地命令存在性，也不额外查询合同、接受或消费者。删除 CheckoutSubmissionQueryService 和 submissionKey SQL。Payment 没有该订单/Intent 下的提交时返回 PAYMENT_SUBMISSION_NOT_FOUND；查询时尚未找到不能证明在途请求未执行，不授权自动再付。

用户确认删除 CheckoutPaymentQueryHandler：它查询后不保存业务结果，且查询到非终态也结束，不能恢复未发送的支付。同步删除提交时的事件登记、Processor 外层事务包装及 Handler 独占的订单号查询 Repository/Mapper/SQL。消费者查询使用现有 GET；资金执行与恢复归 Payment，Commerce 订单更新仍按 Payment 业务事件集成，不在本次补造实现。

## SQL 与交付边界

ConsumerAcceptance 不再读写 command_idempotency_id。现有表该列 NOT NULL，必须先人工应用 [ddl.sql](db/ddl.sql) 改为可空；历史值保持，不填虚假命令 ID。没有新增表、字段、索引或数据回填。此增量为本轮现行列合同，旧基线 DDL/PDManer 尚未同步；从旧基线建库后也需应用此文件。

仅随功能删除 CheckoutPaymentQueryHandlerAutoTest，其余旧测试未适配；未运行编译、测试、格式或其他验证。原测试引用的类型、构造器、指纹与回放场景仍待适配。实际实现、SQL 与验证状态只由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-submit-payment-20260917)维护。
