# Customer Checkout 查询按接口规范重构

用户要求继续重构 `CustomerCheckoutApi#getCheckout`。本次调整 Commerce 查询链路，沿用 Processor 按需查询、Controller 仅适配、普通 class 对象及查询不使用事务的已确认要求。

- 保留原 GET 路径、Checkout Token、可选 `includeConsumer` 和公开响应字段；不新增 GET Body。
- Token 无效或不存在仍返回不存在；原期限不延长，过期不返回个性化内容。
- GET 只展示原订单及冻结事实，不产生支付准备、法律接受或支付操作。
- 按本次需要读取订单、会话、消费者、法律展示、支付投影及商品；不再装配旧整单聚合。
- 消费者回填只在显式要求时执行；文件链接来自三份原冻结文件。Payment 不可用仍显示不可用并提示轮询。
- 保留返回前版本比较，避免混合事实产生错误支付操作提示；2026-09-17 沿用用户移除自动重读的修改，变化时直接返回冲突。

2026-09-17 用户补充：到期判断收敛到 CommerceOrder，清理创建国家对旧幂等表及 Codec 的依赖，并简化支付状态查询。该范围同时清理订单查询、准备恢复中的同一旧创建快照 Reader，不删除其他操作仍使用的幂等表或数据。

业务合同见 [Customer API](../../20260912/commerce-CustomerCheckout接口设计/customer-api-contract.md)。不修改其他系统、Gateway、数据库结构或 PDManer；不接入真实下游。按用户要求，不处理测试、不运行构建或验证，不提交代码。
