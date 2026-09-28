# Customer Checkout 查询交付记录

2026-09-16，用户要求继续按已确认接口规范重构本入口，直接实施范围内的 Commerce 修改。

查询改为 Processor 直接读取本库对象，法律展示使用有限三表 View；普通 class Context 持有数据并承载纯判断，内部响应使用普通 class。外部只读访问与返回前有限版本比较保留，公开金额使用公共 Money。保留用户对 Controller 注入字段顺序的修改。

按公共示例第 11 节对应职责：原 Token 校验由 Processor 的 validate 完成，Controller/Converter 只适配，Context 一次构造且不访问外部；Repository 不接收 Context，HTTP 不返回持久化类型。GET 没有写入，原子保存、资源锁、SQL 行数回写及命令重放均不适用；未增加空 Service、生产 Mock 或跨模块持久化访问。

沿用用户要求，未修改测试，未运行构建、测试、格式化或其他验证，不声明编译与回归通过。内部访问器、构造器及旧查询语句名相关测试后续适配；原有历史验证结果不覆盖本轮修改。

未改其他系统、Gateway、数据库结构或 PDManer，未执行 SQL、提交、推送或部署。当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-customer-get-query-20260916)。

2026-09-17 根据用户反馈继续调整：过期校验归 CommerceOrder；国家读取移除旧创建快照回退，并连同 Client 订单查询、准备恢复的引用一并清理 Codec、快照模型及专用 SQL。支付摘要的纯校验收敛到 PaymentStatus，外部查询之后再比较观察时刻；Processor 保留不可用降级，未新增 Service 或 Client。保留用户移除自动重读及 ConsumerDetails 注解调整，更新相关描述。未处理测试，未运行任何构建或验证；被删历史类型对应测试尚待清理。
