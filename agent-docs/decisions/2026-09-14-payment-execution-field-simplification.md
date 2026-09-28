# Payment Execution 字段精简决定

2026-09-14，用户确认整批字段精简一起整理并落模。本次范围为 Payment 数据设计及 PDManer/SQL 工件维护，具体字段与跨系统影响由[Execution 字段修订](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md)持有。

渠道三元组从 Action 移至 Attempt；认证要求随风控 Action 冻结，认证证明归 Channel；删除 Payment 复制的法律接受字段、Pricing Fact 表和通用状态流转表。保留精确原资金交易引用、交易级工具 Token、活动动作指针、最小交互事实及既有资金/退款/争议证据。

本决定只取代此前 Payment 字段位置、记录范围及关联读取方式；其他系统的数据 Owner 和原历史消费者不得随删列丢失。当前 Java、模型、SQL、实际数据库及验证状态只见[当前实施台账](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

后续 pay 研发中，用户确认 Processor 位于 REST 边界，并要求可使用已有数据对象时不重复创建 Input/View。当前 pay 直接接收 RequestBody，以现有数据库对象贯穿已批准上下文和本地事务，最后投影为 ResponseBody；删除同义输入和只为返回而生成的执行快照。该局部对象可见性与只读仓储例外见[修订第 7 节](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#7-pay-受理链合同)，不改变 HTTP 字段边界或其他业务模块的持久化 Owner。
