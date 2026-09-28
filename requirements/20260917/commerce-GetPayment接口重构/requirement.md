# Customer GetPayment 接口重构

按用户要求继续重构 `CustomerPaymentApi#getPayment`，延续已确认的接口分层、按需查询和请求内复用 Context 的约定。

- GET 路径、Path 参数、响应字段和公开支付状态保持，不增加请求 Body。
- Controller/Converter 只负责协议适配；Processor 查询所属 Repository、校验归属、构造 Context、调用 Payment 和构建内部响应。
- Session、CommerceOrder、OrderPaymentProjection 各查询一次；复用 Context，不因普通版本变化重查数据库或返回 409。
- 保留原期限校验和 Payment 结果绑定校验；查询可以读取有效期内已支付、取消等终态的原提交，不套用新支付资格。
- 不引入 Service、事务、锁、幂等、补偿任务或新数据库对象；不修改其他系统，真实 Payment 适配仍按既有 TODO 范围处理。

沿用用户要求，本轮不处理测试、不运行构建或验证，不提交、部署或执行 SQL。
