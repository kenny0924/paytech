# Commerce Hosted Checkout 恢复检查点简化决策

- Decision status: Confirmed
- Implementation and deployment status: see [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)
- Decision date: 2026-09-09
- Supersedes:
  - [`2026-09-05-commerce-client-api-v1-design.md`](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 第 5.5 节中 Create Checkout 的五阶段定义；Cancel 与 Create Refund 不变
  - [`2026-09-08-commerce-client-checkout-structure-simplification-design.md`](../../docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md) 中“五阶段保持不变”的部分

## 1. 背景

旧实现把 Create Checkout 表达为五个恢复阶段，但 `AUTHORITIES_RESOLVED` 与 `ORDER_COMMITTED` 在同一个本地事务内提交，
`PAYMENT_INTENT_CONFIRMED` 与成功回执也在同一个本地事务内提交。两个中间值不可能成为真实、独立的持久化恢复点，反而扩大了
状态迁移、校验、测试和故障排查的复杂度。首次 Claim 已取得 Lease，随后立即开启事务再次续租也没有提供新的并发保护。

## 2. 已确认决定

### D1. 只保留三个真实恢复检查点

Create Checkout 的 `recovery_stage` 固定为：

```text
CLAIMED -> ORDER_COMMITTED -> COMPLETED
```

- `PROCESSING + CLAIMED`：Order Root 尚未提交，可重新调用 CCT、Risk 和 Finance；
- `PROCESSING + ORDER_COMMITTED`：Order Root 已提交，使用首次 Claim 冻结的 Payment 子幂等键重试 Payment；
- `SUCCEEDED + COMPLETED`：已冻结原始 `201` 回执；
- `FAILED + COMPLETED`：已冻结稳定 `4xx` 业务失败。

`process_status` 负责区分成功与失败，终态不再借用前置恢复阶段表达失败发生位置。技术暂态继续保持 `PROCESSING`，并停留在最后一个真实恢复检查点。

### D2. 正常成功路径保留三个短事务

三个事务分别拥有：命令 Claim、Prepared Order Root 与 `ORDER_COMMITTED`、Payment 绑定与最终回执。CCT、Risk、Finance、Payment
调用继续全部位于数据库事务之外。首次 Claim 的新 winner 直接复用已取得的 Lease；只有恢复已有 `PROCESSING` 命令时才单独接管 Lease。

这三个边界不能合并：合并会把远程调用放入数据库长事务，或让外部 Payment 副作用先于可恢复的本地 Order Root。

### D3. 兼容边界

本决定不改变 Client API、OpenAPI、请求 Hash、Snapshot Schema、Token、金额、过期、RFC 9457 或数据库列结构。Cancel 与 Create Refund
仍使用各自既有阶段。本决定本身不授权应用或迁移数据库；具体环境是否存在旧值、是否需要迁移以及迁移执行状态只记录在
[`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。

目标环境没有旧数据时可直接使用新阶段全集。若环境中已存在 `AUTHORITIES_RESOLVED` 或 `PAYMENT_INTENT_CONFIRMED` 行，必须先依据
命令快照、Order Root、Payment 结果和冻结回执做专项分类与迁移；尤其不能把 `PROCESSING + PAYMENT_INTENT_CONFIRMED` 直接改成
`ORDER_COMMITTED` 后盲目重放。

## 3. 状态归属

- 当前阶段合同由 [`COMMERCE_DATABASE.md`](../database/COMMERCE_DATABASE.md) 与本决策共同约束；
- Java、Mapper、PDManer 和人工 SQL 必须使用同一阶段全集；
- 当前实现、验证、数据库应用和部署状态只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。
