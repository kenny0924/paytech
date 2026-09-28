# SaveConsumer 接口规范重构

用户要求按既定接口规范重构 `CustomerCheckoutApi#saveConsumer`，随后明确去掉该接口的请求幂等。范围仅为 Commerce 保存消费者资料的同步链路及其专用模型、持久化方法。

- 保留 PUT 路径、Token、请求字段及成功响应 `orderNo/consumerStatus`，不再要求或处理 Idempotency-Key。
- 完整替换消费者资料，省略的可选地址成员清空；不改变订单计税国家。
- 保留规范化、准备前编辑；每次请求按当前状态保存，不比较 Hash 或重放历史响应，准备开始后的重复请求同样拒绝。
- 资料保护在事务外完成；数据库只保存保护引用及已批准维度，订单版本与资料原子提交。
- 按需读取本表对象；普通类 Request/Response/Context，Controller 只适配，Service 提供 REQUIRED 本地保存。

业务依据为 [Customer API 保存合同](../../20260912/commerce-CustomerCheckout接口设计/customer-api-contract.md#3-put-bconsumer保存消费者资料)。不改其他系统、Gateway、共享幂等机制、数据库结构或 PDManer；沿用用户要求，不处理测试、不执行构建或验证，不提交代码。
