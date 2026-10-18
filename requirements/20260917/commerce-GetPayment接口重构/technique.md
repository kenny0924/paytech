# GetPayment 技术说明

依据[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)及 [Commerce Customer 入口合同](../../../paytech/acquiring/docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)。

`GetCustomerPaymentRequest` 使用普通 class，移动到 Processor 下的 request 包，字段为 checkoutToken 和 submissionId。CustomerPaymentConverter 负责将 Path 中正十进制 ID 转为 long；Controller 委托 Processor 后使用已有 Converter 输出共享的 PaymentSubmissionResponseBody，不新增同义响应类。

GetCustomerPaymentProcessor 继承 Foundation AbstractProcessor，校验 Token 与内部查询编号；主流程依次读取会话、订单和支付投影，构造 CustomerPaymentQueryContext，查询 Payment，构建 CustomerPaymentResponse。每次本地查询只读取所属表；会话仅用于定位订单，Context 持有真正用于后续查询和响应的 CommerceOrder、OrderPaymentProjection 及提交编号。其 paymentReference() 纯方法由服务器数据派生订单和 Intent 坐标，不复制本表 ID，不访问数据库或外部服务。

Payment 查询使用固定的订单/Intent/提交编号。未找到返回 PAYMENT_SUBMISSION_NOT_FOUND，依赖不可用沿公共异常处理，返回值继续由 PaymentResult 校验绑定、状态及动作。查询不根据本地成功或关闭状态拒绝读取历史提交，不把 null 伪装为 PROCESSING 或支付失败。

删除 checkCurrentAccess 的第二次 Session/Order 查询与版本比较。查询返回前只使用 Context 中原期限和当前时间调用 CommerceOrder.checkExpiry，不新增事务或锁；普通状态版本改变不再导致该接口额外返回 409。此接口不聚合其他时点的新本地数据，不承诺跨系统原子快照。

数据库结构、Mapper、SQL、提交接口及真实 Payment 适配不变。没有本地写入，Service 原子事务、写入 CAS、补偿与对象回写均不适用于本次纯查询。实际实现和验证状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-payment-20260917)。
