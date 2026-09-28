# Payment 与 Risk 限额关联修订

> **后继确认：** [内部风控 Action 修订](./internal-risk-action-amendment.md)已确认 RISK_CHECK 及两个风控字段移动，下文第 3 节的讨论状态是前序记录。该后继保留一 Attempt 一次首次评估和 Risk 原 Group 关联，不批准多个风控检查点。此文末尾的交付范围仅描述限额决定当时的工作。

本次目标涉及 Payment、Risk 两系统：Payment Attempt 删除 1 个字段，不新增表或索引；调整交易评估响应和 3 类 Payment→Risk 事件的 Group ID 消费要求，不新增 HTTP 路由、业务 DML 或页面。

用户确认的字段移除与归属方向见[2026-09-14 决定](../../../agent-docs/decisions/2026-09-14-payment-risk-limit-correlation.md)。本稿记录目标及实施时必须核对的消费边界；实际代码、模型、数据库及验证状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 1. 字段和归属

- 从 t_payment_attempt 目标中删除 limit_reservation_group_id。现有 Schema 没有该字段的独立索引，无需为此新增关联表或替代占位字段。
- Risk 的 t_transaction_risk_assessment 已用 payment_attempt_id 唯一定位本次交易评估；t_limit_reservation_group 已用 assessment_id 唯一定位原占用组。两个现有约束共同完成关联。
- Risk 的 Group ID、Reservation、Position、Movement 及状态历史仍由 Risk 保存；本次不删除 Risk 内部字段，也不要求 Payment 查询 Risk 数据库。

```text
Payment：原 payment_attempt_id + 可信支付结果
                         ↓
Risk：Transaction Assessment → 原 Limit Reservation Group
```

## 2. 新消费合同目标

Payment 调用 Risk 时传原 Attempt 身份与冻结业务输入；同一评估重放必须返回原结果。Risk 只有在正式 ALLOW 与必要占用在本地事务提交后才能返回 ALLOW。Payment 新合同不再消费或持久化 Group ID；Risk 可向其他已有消费者继续提供其内部标识，不能据此反向要求 Payment 保存。

| 原事件 | Risk 的定位与处理 |
|---|---|
| PaymentSucceeded | 按原 Attempt 定位 Assessment/Group，原子提交为 COMMITTED |
| PaymentAttemptFailed | 按原 Attempt 及可信安全终止语义释放原 Group；已获授权但尚未释放不属于安全失败 |
| PaymentAttemptUnknown | 按原 Attempt 保持原 Group 占用，不按超时自动释放 |

三个事件继续携带稳定事件身份、来源版本和发生时刻等既有去重/乱序信息；新合同不要求 Payment 传 Group ID。事件名的旧 UNKNOWN 语义指原资金结果未决，不要求新 Attempt 枚举恢复 UNKNOWN。Attempt=PROCESSING 本身不构成资金未知证据；手动授权等待 Capture 时同样不得释放原占用。成功 Void、安全取消与全额 Capture 的限额结果沿原业务规则处理。

Risk 在本地事务读取原关联、锁定占用组并应用单调结果；重复消息不得重复产生 Movement，迟到消息不得回退终态。已经持久化的原关联不随配置修改或重新选路改变。

缺少关联不得一概忽略：未形成评估或明确非 ALLOW 的前置失败可以没有 Group，但必须有可验证的来源状态；成功或曾 ALLOW 的业务事实缺少原 Group 属于缺口，保留待处理并告警，不能当作零占用成功消费。事件先到而原关联尚不可见时沿既有可靠消费机制重试，不能新建替代占用。

## 3. 评估基数与本轮讨论边界

本次移除依赖现行的一 Attempt 一次首次支付评估、一次 Assessment 一个 Group 的约束。一笔评估可以包含多个规则和技术重试，不等于重复建立评估或占额。

用户同时提出将 Internal Risk 作为 Payment Action，此项仍为设计讨论。若采纳，可用原风险 Action 承接评估结果与稳定重放；不能因此让所有渠道动作自动触发新评估。若以后允许同一 Attempt 多个风控检查点，应先明确检查点身份和哪些评估产生占用，再调整 Risk 的唯一键与结果关联，不能按 Attempt 任意选择最新 Group。

## 4. 实施与兼容检查

先更新 Risk 消费合同及 Mock，验证按 Attempt 能唯一找到原 Group，再移除 Payment DAL/快照/响应消费及模型字段。既有新旧消息必须有明确版本与消费边界；旧消息中的 Group ID 可按原合同核验，不能忽略错配。Risk 内部 payment_attempt_fact 若继续保存 Group ID，应由本地原关联填充，不再作为新事件必填输入。

最小验收包括：同评估重放不重复占额；支付成功提交；安全失败/取消/Void 释放；资金未知及手动授权等待保留；重复与乱序消息不重复 Movement；缺少预期关联不丢消息；新事件无 Group ID 仍能正确处理；旧事件关联错配明确拒绝。

本轮记录设计决定，不修改 Java、PDManer、SQL、数据库或 Excel；旧工件中的 Group ID 仍为待迁移项，不能作为新合同已生效的依据。
