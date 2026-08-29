# Finance 退款 Funding 移除实施记录

> **已被后续设计取代（2026-08-27）：** 本文中仍称“通用 Funding 四表仅服务 Settlement 与 Tax Payment”的部分，已由 [Finance 通用 Funding 模块移除设计](./2026-08-27-finance-funding-module-removal-design.md) 取代；Reserve/Recovery 工作流又由 [Finance 保证金 Account 精简实施记录](./2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) 取代。退款成功后入账、退款不预占、PENDING→AVAILABLE→`CLIENT_RECOVERY_RECEIVABLE` 和 `RefundSucceeded` 语义继续有效；退款不得自动消耗 `CLIENT_SECURITY_DEPOSIT`，也不要据本文恢复任何 Funding、Reserve 或 Recovery Case 表。

日期：2026-08-27  
状态：已完成；自动验证通过，PDManer 4.9.3 应用内人工兼容检查待完成

## 1. 决策

退款额度与渠道执行由事实所属系统各自控制：

- Commerce 使用 `t_order_refund_position` 保证订单累计退款不超额；
- Payment 使用 `t_payment_refund_position` 保证渠道累计退款不超额，并创建和执行 `t_channel_refund`；
- Finance 不审批退款、不预留退款资金，也不位于 Provider 提交前；
- Finance 只消费 Payment 权威 `RefundSucceeded`，形成 REFUND Transaction Economics、Component Link、Provider Debit Item、Journal 和必要的财政更正。

平台明确承担 Provider 退款时点与 Client 可回收余额之间的流动性和信用缺口。该缺口不再通过退款前 Funding Reservation 隐藏；Client Principal 在成功入账时依次冲减 PENDING、AVAILABLE，LOCKED 不自动拆锁，已锁定、已结算或余额不足的部分形成 `CLIENT_RECOVERY_RECEIVABLE`。

## 2. 跨系统流程

1. Commerce 在本地事务创建 Refund Request，并 CAS 增加 Order Refund Position 的 `reserved_amount`。
2. Commerce 直接调用 PAY-I04；请求不携带 Finance ID 或 Version。
3. Payment 在本地事务 CAS 增加 Payment Refund Position，并创建唯一 Channel Refund。
4. `UNKNOWN` 只保留在 Payment；`FAILED/CANCELLED` 只通知 Commerce 释放本地额度。
5. `SUCCEEDED` 由 Payment 分别通知 Commerce 和 Finance；Commerce 消费本地额度，Finance 追加会计事实。

## 3. Finance 物理模型

删除：

- `t_refund_funding_reservation`；
- Funding Position 类型 `PAYMENT_REFUND_CAP/PROVIDER_RECEIVABLE/RESTRICTED_FUND`；
- Funding Commitment 类型 `REFUND`；
- Funding Allocation 角色 `REFUND_CAP/SOURCE_COVER`；
- `t_funding_allocation.source_component_id`、对应索引、关系和跨行约束。

保留并收紧：

- 通用 Funding 四表只服务 Settlement 与 Tax Payment；
- Position 只允许 `OWNER_PAYABLE/BANK_CASH/TAX_PAYABLE`，其中 OWNER_PAYABLE 只引用 LOCKED Account；
- Commitment 只允许 `SETTLEMENT/TAX_PAYMENT`；
- Allocation Role 只允许 `LIABILITY_LOCK/BANK_CASH`；
- REFUND Component Link 的累计 REVERSAL 封顶继续作为会计入账完整性约束，不构成退款授权。

新 Finance 基线为 107 表、1,346 字段、183 个唯一索引、173 个访问索引、185 条本库关系、34 条 1:1 关系和 8 个 Diagram Groups；Tax/Fiscal 仍为 23 表。

## 4. 合同成熟并发

未决退款不阻断 `client_available_time`。成熟金额在锁内按“原 Client Principal－已成功 Component Link 退款－此前成熟金额”重算。成熟与 RefundSucceeded 使用同一稳定锁顺序：原 PAYMENT Economics、Component Link、PENDING/AVAILABLE Account。

- 退款先完成：成熟只移动剩余金额；
- 成熟先完成：退款从 AVAILABLE 冲减；
- 两者都不建立退款 Funding Position 或补成熟任务。

## 5. 边界

本轮只修改 PDManer 设计、生成器、自动门禁、权威文档、现实场景和 requirements-site。没有创建 Java、Migration、物理数据库、运行环境或部署资源，也不提交 Git。

## 6. 验证记录

- [x] Finance/Commerce/Payment 模型生成器 `--check`
- [x] Finance/Commerce/Payment 模型专项测试
- [x] Finance/Commerce/Payment 场景专项测试
- [x] requirements-site 构建与全量测试（84/84）
- [x] 全量 PDManer 测试（153/153，无失败）
- [x] requirements-site ESLint 与 `git diff --check`

最终模型规模：Commerce 14 表 / 223 字段 / 20 唯一索引 / 11 访问索引 / 14 关系 / 4 Diagram Groups；Payment 29 / 373 / 41 / 44 / 63 / 7；Finance 107 / 1,346 / 183 / 173 / 185 / 34 条 1:1 / 8，Tax/Fiscal 仍为 23 表。自动门禁不替代 PDManer 4.9.3 应用内人工兼容检查。
