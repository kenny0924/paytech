# SaveConsumer 交付记录

2026-09-17，按用户“重构 saveConsumer”及随后“去掉幂等”的授权实施 Commerce 范围内修改。

入口改用独立普通类 Body、内部 Request/Response、Converter 和 SaveConsumerContext；Processor 复用 Foundation AbstractProcessor 并按表读取。规范化归 Request，资料保护留在事务外；直接调用 CustomerCheckoutService 的 REQUIRED 保存，原子更新订单版本与消费者资料。SQL 条件和行数保护并发，成功后同步原对象。

公共示例第 11 节对应：Controller 仅适配，Context 一次构造，Service 不调用外部或其他 Service，Repository 不接受 Context，HTTP 不返回 DB 对象。订单条件 UPDATE 沿用实际 Order 行锁，未叠加 Redis 锁；无提交后动作，不新增补偿。按用户要求，SaveConsumer 不再需要原请求比较：删除 Header/内部 Key、Hash、Claim/Complete、历史回执重放及 Filter 对保存接口的 Key 校验。每次请求按当前状态保存；其他接口、共享命令组件和历史数据保留。

按用户要求，未修改测试，未执行构建、测试、格式化或其他验证，不声明编译及回归通过；旧 Java 类型与装配相关测试待适配。未改其他系统、Gateway、数据库结构或 PDManer，未执行 SQL、提交、推送或部署。

当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-save-consumer-20260917)。
