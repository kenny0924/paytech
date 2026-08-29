# Risk Limit 平衡精简设计

## 1. 决策

Risk & Compliance 的 Limit 图组从 10 张表收敛为 7 张表：

1. `t_limit_version`
2. `t_decision_point_limit_binding`
3. `t_limit_position`
4. `t_limit_reservation_group`
5. `t_limit_reservation`
6. `t_limit_group_state_change`
7. `t_limit_movement`

删除：

- `t_limit_definition`
- `t_limit_plan`
- `t_limit_plan_version`

`t_limit_plan_item` 由 `t_decision_point_limit_binding` 取代。现有 `t_decision_point_obligation` 是准确 Point Version 下的组合根，不再为 Limit 重复建设 Plan/Plan Version 发布边界。

## 2. 不变业务能力

- Payment Attempt 的 PRIMARY 候选 ALLOW 必须执行工程绑定的 `RESERVE_LIMIT_GROUP` 系统义务。
- 义务可以包含 0..N 个 Limit Binding；零项义务仍创建空 Reservation Group，证明强制义务已经成功执行。
- 多 Position 按稳定顺序锁定并在同一本地事务全成全败。
- 任一 Limit 超额时不留下 Group、Reservation、Movement 或部分 Position 更新，正式决定调整为 `DENY/LIMIT_EXCEEDED`。
- `PAYMENT_UNKNOWN` 不自动过期、不释放占用，也不产生 Movement。
- 权威 Success 执行 COMMIT；安全失败、Provider 提交前 Cancel 或权威 Void 执行 RELEASE。
- Refund、Dispute 和已 Capture Reversal 默认不恢复原始额度。
- SHADOW/REPLAY 不创建 Group、Reservation、State Change、Movement，也不修改 Position。
- Redis 只能是非权威加速层；MySQL Position 继续是并发和额度守恒的权威。

## 3. 配置模型

### 3.1 Limit Version 吸收稳定目录身份

`t_limit_version` 保存：

```text
limit_version_id, limit_code, limit_name, version_no,
subject_type, metric_type,
window_unit, window_size,
amount_limit, count_limit,
currency_mode, fixed_currency,
content_hash, status, frozen_by, frozen_time
```

唯一键为 `(limit_code, version_no)`。运行时和历史事实只引用准确 `limit_version_id`；不再存在会与版本状态形成第二真相的 Definition 状态。

同一 `limit_code` 的语义由工程门禁保持稳定；若 `subject_type` 或 `metric_type` 改变，必须使用新的 `limit_code`。只有 `FROZEN` Version 可以进入 Point Limit Binding。

阈值矩阵：

- `AMOUNT`：只允许正 `amount_limit`。
- `COUNT`：只允许正 `count_limit`。
- `AMOUNT_AND_COUNT`：两者都必须为正。
- 含金额的 Limit 禁止 `NO_CURRENCY`，避免跨币种直接相加。
- `FIXED` 必须有 `fixed_currency`；其他模式必须为空。

`content_hash` 使用 `CODE-MOR:RISK:LIMIT_VERSION:1`，覆盖除名称、状态、冻结信息和审计时间外的可执行字段；采用 RFC 8785 JCS、UTF-8 和 SHA-256 小写十六进制。激活前必须重算校验。

Release 静态校验必须装载每个被绑定 `limit_code` 的完整本库版本闭包，逐行校验版本号、窗口、阈值、币种与冻结矩阵，重算 `content_hash`，并比较同 Code 全部版本的 `subject_type + metric_type`。闭包缺失应由未来 Repository/Processor 查询契约 Fail-Closed；设计期校验器以传入集合完整为前提。

### 3.2 Point Obligation 直接拥有 Limit Binding

`t_decision_point_obligation` 使用闭合 `obligation_type`：

```text
ACTION
RESERVE_LIMIT_GROUP
```

- `ACTION` 必须且只能填写 `action_version_id`，不得拥有 Limit Binding。
- `RESERVE_LIMIT_GROUP` 必须令 `action_version_id` 为空，可以拥有 0..N 个 Limit Binding。
- 同一 Point Version、同一触发决定最多一个 `RESERVE_LIMIT_GROUP`，与 Group 的 Assessment 唯一性保持一致。
- `PAYMENT_ATTEMPT_RISK + ALLOW` 必须恰有一个启用的 `RESERVE_LIMIT_GROUP`，并固定 `FAIL_CLOSED`。

`t_decision_point_limit_binding` 保存准确 Limit Version、顺序以及主体/金额的 Feature 或 Context 来源：

```text
binding_id, obligation_id, limit_version_id, sequence_no,
subject_source_type, subject_feature_version_id, subject_input_field_id,
amount_source_type, amount_feature_version_id, amount_input_field_id
```

同一 Obligation 内 `limit_version_id` 和 `sequence_no` 分别唯一。绑定引用的 Feature/Input 必须属于 Obligation 的准确 Point Version；金额来源是否必填必须与 Limit 的 `metric_type` 一致。

任何 Limit Binding 变化都创建新的 Point Version，并沿用现有静态检查、Shadow、质量检查和双人审核发布链，不再单独发布 Limit Plan。

## 4. 运行模型

### 4.1 Position

`t_limit_position` 是固定 UTC Window 的权威计数器与并发锁点：

```text
position_id, limit_version_id, subject_key, currency_scope_key,
window_start, window_end,
reserved_amount, committed_amount, reserved_count, committed_count,
version
```

`subject_type` 由 Limit Version 推导；`currency` 由规范化 `currency_scope_key` 表达，因此删除两个重复字段。`currency_scope_key` 在 FIXED/PER_CURRENCY 下使用 ISO 4217 alpha-3，在 NO_CURRENCY 下固定为 `NO_CURRENCY`。

唯一键为：

```text
(limit_version_id, subject_key, currency_scope_key, window_start)
```

### 4.2 Reservation Group

Group 是占用状态的唯一当前权威：

```text
reservation_group_id, assessment_id, obligation_id,
status, hold_reason, resolved_time, version
```

`payment_attempt_id` 由唯一的 Transaction Risk Assessment 推导；`reservation_count` 由明细集合推导；没有可重算消费者的 Group `content_hash` 删除。空 Group 本身即可证明零项义务已执行。

状态机：

```text
NULL -> RESERVED
RESERVED -> PAYMENT_UNKNOWN | COMMITTED | RELEASED
PAYMENT_UNKNOWN -> COMMITTED | RELEASED
COMMITTED / RELEASED 为终态
```

`PAYMENT_UNKNOWN` 必须有 `hold_reason` 且 `resolved_time` 为空；其他状态的 `hold_reason` 为空；终态必须有 `resolved_time`。

### 4.3 Reservation

Reservation 是创建后不可变的占用合同：

```text
reservation_id, reservation_group_id, position_id, amount, count_value, create_time
```

删除可由 Position 或 Group 推导的 `limit_version_id`、`currency`、`status`、`hold_reason`、`reserved_time`、`resolved_time`、`version` 和 `update_time`。唯一键改为 `(reservation_group_id, position_id)`；同一 Group 对同一 Position 必须先聚合再写一行。

### 4.4 State Change 与 Movement

`t_limit_group_state_change` 继续保存 Group 完整状态时间线。它不能被 Movement 替代，因为 UNKNOWN 不改变计数器，空 Group 也没有 Reservation/Movement。

`t_limit_movement` 继续保存 RESERVE/COMMIT/RELEASE 的不可变计数器流水。删除可由 Reservation 推导的 `position_id`；按 Position 重建时通过不可变 Reservation Join。每条 Movement 至少有一个正 Delta，Delta 始终保存非负绝对量，方向由 `movement_type` 决定。

## 5. 原子性与幂等

正式占额事务同时提交：

```text
Evaluation
Transaction Risk Assessment
Reservation Group
0..N Reservation
0..N RESERVE Movement
0..N Position CAS
Group State Change
Compensation Event
```

Payment 权威事件先通过 `t_payment_attempt_fact.source_version` 拦截重复和乱序，再锁定准确 Group、Reservation 和 Position。Movement 的 Trigger 唯一键阻止同一事件重复改动计数器。

## 6. 取舍

本设计没有继续压到 6 张表。删除 `t_limit_group_state_change` 会让 Risk 无法单库证明 `RESERVED -> PAYMENT_UNKNOWN -> COMMITTED/RELEASED` 的完整时间线，尤其无法覆盖零项 Group；这不符合当前审计边界。

本设计也不删除 `t_limit_movement`。Movement 是 Position 守恒校验、问题定位和重建的唯一不可变账迹。
