# Payment 移除限额占用组引用

> **后继：** [内部风控 Action 决定](./2026-09-14-payment-internal-risk-action.md)随后确认 RISK_CHECK 与评估字段移动；下文“仍讨论”保留为当时范围，限额归属结论继续有效。

- Decision status: Confirmed（移除 Payment 字段与归属方向）；详细消费及兼容方案见修订稿。
- Decision date: 2026-09-14
- Implementation / verification / deployment status: 仅见 [CURRENT_IMPLEMENTATION](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。
- Scope: Payment Attempt 的 limit_reservation_group_id 与 Payment→Risk 结果关联。
- Superseded sections: 前序 Payment Attempt 保存 Group ID、Payment 结果事件必须传 Group ID 的新合同要求；Risk 内部 Assessment/Group/Reservation 及限额业务规则保留。

用户明确要求“limit_reservation_group_id 先去掉吧，这个风控域应该也会记录吧”。Risk 已拥有唯一 payment_attempt_id 对应的 Transaction Assessment，以及每个 Assessment 唯一的 Limit Reservation Group；Payment 无需重复保存 Risk 内部占用标识。

目标由 Payment 发布原 Attempt 的可信业务事实，Risk 在自己的数据库中定位原 Assessment 和占用组，完成提交、保持或释放。不按当前限额规则重算、不创建替代 Group，也不跨库访问。旧 Wire 和消费方必须同步迁移，不能只删 Payment 字段而让旧消费者丢失关联。

详细边界见[限额关联修订](../../requirements/20260913/payment-Execution设计/risk-limit-correlation-amendment.md)。Internal Risk 是否成为 Payment Action、评估结果字段移动以及多风控检查点仍属于本轮设计讨论，不由这项限额字段决定自动确认。
