# SubmitPayment 交付记录

2026-09-17，按用户要求重构后，继续按“幂等的逻辑没有去掉”的纠正移除支付请求幂等，并按用户确认删除无业务结果落地的支付查询 Handler。保留用户已有 CustomerCheckoutApi import 调整。

公共示例第 11 节对应：普通类 Body/Info、内部请求/响应与 Context；Controller/Converter 只适配；Foundation AbstractProcessor 按需查询并编排；Service 的 REQUIRED 事务只操作本库；Processor 直接调用本地 Service 保存接受和订单版本；条件 SQL/行数校验并更新原对象；Payment 提交和查询均在事务外。无请求 Claim/Lease/Hash/重放，支付查询事件不再登记，其他业务的补偿机制保留。业务编号供 Payment 来源绑定与查询，同一 Intent 的资金保护由 Payment 提供。

先前准备的接受表历史命令列可空 SQL 未执行；此次未新增 SQL，也未操作历史任务数据；未改 PDManer、Payment、Gateway、前端、自动准备或共享幂等组件。没有执行构建、测试、格式化或验证，删除了被移除 Handler 的专属测试，未适配其他旧测试，不声明编译或支付联调通过。未提交、推送或部署。

实际状态只由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-submit-payment-20260917)维护。

按用户补充要求，executePayment 改为直接使用已提交的 Context 和当前卡输入，移除 Session、Order、消费者存在性的重复查询；保留纯内存到期判断，使用 Repository 回写的订单版本。Service 事务和条件 SQL 保持，外部调用仍在事务外。本次未修改测试或执行验证。
