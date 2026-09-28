# Payment Action 状态与 Channel 恢复边界

- Decision status: Confirmed
- Decision date: 2026-09-14
- Implementation / verification / deployment status: 仅见 [CURRENT_IMPLEMENTATION](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。
- Scope: Execution 状态、capture mode、活动 Action 指针与 Channel 原操作恢复；其他 Owner 仍 Mock + TODO。
- Superseded sections: Execution 前序设计中的 payment_goal、Intent/Attempt/Action 状态投影、QUERY Action 与 target_payment_action_id、Execution 选择渠道查询/重发策略。未涉及的工具、资金、退款、并发和跨系统边界继续有效。

## 决定与理由

用户提出 capture mode、精简 Attempt 与 Action 状态、Attempt 保存执行中 Action，随后明确渠道标准接口内部组织查询、处理重复响应和补偿，业务 Action 不应包含 QUERY；对收敛方案回复“可以”。

1. `capture_mode=AUTOMATIC/MANUAL` 取代 `payment_goal`。模式表达是否由 Payment 自动完成收款，不等同某个渠道操作名。
2. Intent 为 OPEN/SUCCEEDED/CANCELLED/EXPIRED。Attempt 为 PROCESSING/SUCCEEDED/FAILED/CANCELLED；实际收款才成功，手动授权完成仍为 PROCESSING。
3. Attempt 的 `active_action_id` 表达当前未终结业务动作。创建及占位、终态及释放/接续由本地事务与资源并发保护保证，UNKNOWN 不释放。
4. Action 为 CREATED/PROCESSING/UNKNOWN/SUCCEEDED/FAILED。等待消费者并入 PROCESSING，必要交互事实和瞬时 nextAction 保留；认证动作继续使用 AUTHENTICATE。
5. Action 不含 QUERY；删除仅服务 QUERY 的 `target_payment_action_id`。Channel 负责同一业务动作的幂等重入、查询与安全重发，通信记录归 Channel。
6. 标准接口可重复调用不等于外部资金请求可盲目重发。原 Action 身份、请求和路由保持不变，渠道重复响应或查无记录不能直接当作成功或确定失败。
7. 支付业务流程仍用代码编排；可靠调度复用服务级补偿机制，不新建工作流表或第二套渠道业务状态机。Execution 应用归一化结果，Channel 不直接写 Execution 表。

详细合同、场景与实施影响由[修订设计](../../requirements/20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md)统一维护。[交付记录](../../requirements/20260913/payment-Execution设计/delivery.md)记录本次阶段与确认范围。
