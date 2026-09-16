# CreateCheckout 业务实现边界

本文件为已确认的 CreateCheckout 业务防重修订，替代早期创建流程的 Hash、Claim、Lease、ORDER_COMMITTED、成功/失败回执冻结要求；其他操作继续遵守其原有合同。

处理顺序：Foundation 入口门禁与 validate 输入关系校验 → Client 订单号校验 → 新建期限校验与完整 Context 构建 → CCT 只读预检 → CheckoutCreationService.saveCreateCheckoutContext → 构建响应。

- 时间以本次构建 Context 为基准；默认三十分钟，显式期限五分钟至二十四小时。订单 INSERT 的期限条件保护实际写入，慢预检不延长到期。
- 金额按行验证数量、币种精度、优惠和净额，并验证订单汇总 DECIMAL(24,8) 范围；不进行隐式舍入。
- 金额纯计算归属内部 `CreateCheckoutRequest.calculateAmounts()`，商品行与汇总结果在 Request 内定义；不创建独立 Calculator 或 Spring 组件。Processor 保留校验触发、汇总范围校验和错误映射，Request 不访问数据库或外部服务，外部 RequestBody 不增加计算职责。
- 预查询用于提前拒绝重复号，uk_order_client_order(client_id, client_order_id) 负责并发兜底。事务回滚后确认真实冲突再转换业务错误。
- Context 直接持有同模块数据库对象；按[单 Token 后继决定](../foundation-PublicToken公共生成/technique.md)，Session 在事务前只生成一个 Token，唯一冲突和其余 SQL 错误均回滚五类事实。
- HTTP Header 保留，业务 Request 移除 idempotencyKey；没有 Gateway 请求重放能力的启用或部署。

## 国家字段及历史边界

`t_commerce_order.consumer_country_code CHAR(2) NULL` 保存基础创建国家，新写必须提供；可空仅用于已有订单，不回填历史数据。最终 Order Context 的国家继续保留原有语义。

新订单读取 Order 国家；历史订单已有 Context 时使用 Context，否则使用受校验的历史创建快照。取消只对实际存在的旧创建命令检查完成状态，新订单随原子提交完成创建。

物理模型与生成器、空库目标 Schema 同步；现存本地库只手工执行 [增量 SQL](../../../commerce-service/docs/requirements/20260916-create-checkout-business/ddl.sql)。它仅加一列，不清理历史命令，不更新历史业务行。

## 验证范围

真实 HTTP → CCT Simulator → MySQL：重复业务号、并发竞争、Client 隔离、Key 不进入业务范围、金额/期限错误、依赖失败、创建后订单查询与 Customer 准备。

独立 Service 入口使用真实 MySQL 验证 REQUIRED 自行提交/加入事务、保存原 Session Token 和 Token 唯一冲突后五表回滚；历史快照 Reader 的合同测试保留。

本入口结构按 [processor-conventions.md](../../../payment-orchestration-service/docs/development/processor-conventions.md)第 6、8、9、11 节组织；创建订单查询归属 CommerceOrderRepository，Service 不再重复做入口有效期判断。
