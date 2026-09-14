# Execution 阶段 4 开发检查记录

> **基线范围：** 以下记录对应旧 SubmitCard 切片。交易级工具与统一 pay 的研发映射见[后继检查](./instrument-token-development-checks.md)，本页旧测试结果不能用来证明新合同。

本页记录本轮研发检查与阶段 5 的核对范围，不作为长期实施台账。当前结果、覆盖率、SQL 与制品证据统一见[当前实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)及其关联 JSON；不能将“代表场景通过”解释成整组所有组合已验收。

## 已交付代码边界

- Execution：Intent/来源 Binding、原 submission/Attempt、AUTHENTICATE/资金/QUERY Action、退款额度、原交易引用、Pricing Fact、Duplicate Case、状态历史和 MyBatis 持久化。
- Web：12 个内部接口、可信内部入口与业务 Owner 端口、短事务幂等受理、Redis 资源锁、类型化方式流程、Card 首次流程及四类补偿 Handler。
- Test：真实 MySQL/Redis/HTTP/Scanner/Dispatcher；外部业务 Owner 为明确场景的状态替身。独立 JVM 在 T3 后直接退出，渠道合成事实保留在测试侧，重启后从数据库恢复原操作。
- 模型与人工 SQL：按批准设计同步；空库创建工件和空旧表增量工件互斥。此次只应用前者，具体前置与执行摘要见台账。

## 验收编号映射

测试目录为 [auto/execution](../../../payment-orchestration-service/paytech-payment-test/src/test/java/com/paytech/payment/auto/execution/PaymentExecutionApiAutoTest.java)；表中省略共同的 `AutoTest` 后缀。四个业务 Handler 均有同名独立入口测试类。

| 编号 | 本轮运行的代表证据 | 阶段 5 仍需核对的组合或边界 |
|---|---|---|
| V01 | PaymentExecutionApi：单 SALE、原命令重放、单交易引用/Position/收款事实 | 正式事件 Wire 仍是 Owner TODO |
| V02 | PaymentExecutionApi：认证后仅授权，captured=0，无收款事件 | 强制 Challenge + AUTHORIZE_ONLY 的组合回归 |
| V03 | PaymentAttemptRecoveryHandler、PaymentTrustedResult：挑战到期、无感不满足强制挑战、非法挑战标记 | 明确认证失败、无感成功后的证明到期组合 |
| V04 | PaymentExecutionGuards：Risk DENY/REVIEW/不可用、Routing 无候选/错 OE | 能力缺失、同 Attempt 相互冲突的 Routing 返回 |
| V05 | PaymentExecutionApi、PaymentCommandContract：提交并发、异内容 Hash、换卡冲突 | 不同传输 key 对同 source 的重放及内容不一致 |
| V06 | PaymentExecutionApi、PaymentConcurrency：Close 先到、Create/Close 竞争、单关闭 Binding | Create 先到后的完整 HTTP 终止组合 |
| V07 | 原 key 换卡被拒绝；UNKNOWN 不重发；测试非 Card 注册分发 | 活动/UNKNOWN 中新 submission 换方式的组合 |
| V08 | PaymentFundsCommands、PaymentExecutionApi：Capture 成功/失败/UNKNOWN、全额限制、原失败命令重放、新命令重试 | 乱序 Capture 回执的综合组合 |
| V09 | PaymentConcurrency、PaymentFundsCommands：Capture/Reversal 竞争、Void 成功/失败后禁止 Capture | Void UNKNOWN 收敛与新命令重试组合 |
| V10 | PaymentExecutionApi、PaymentFundsCommands：两笔 70 竞争 100、UNKNOWN 保留占用、重复成功/失败只消费或释放一次 | 与 Reversal 混合并发的压力组合 |
| V11 | PaymentFundsCommands：已扣款返回 REFUND_REQUIRED、已预占拒绝新 Reversal、原 Reversal 决定可重放 | Commerce Mock 自动接续退款以及已有部分退款组合 |
| V12 | PaymentProcessRecovery：T3 后发送前/渠道成功后真实 JVM 退出，再启动查询，发送次数仍为一 | 当前统一查询；有保证的同 key 重发优化未启用、不作验收承诺 |
| V13 | PaymentProcessRecovery、PaymentExecutionApi：无 Transaction Ref 的原 key 查询与 UNKNOWN 后成功 | 显式 NOT_FOUND 后迟到成功及查询 transport 不可用 |
| V14 | PaymentExecutionApi、PaymentAttemptRecoveryHandler：补输入续原提交、换卡拒绝、任务无瞬时材料 | 3DS 成功之后再丢失输入的组合及日志专项扫描 |
| V15 | PaymentTrustedResult、PaymentExecutionGuards：成功不降级、重复结果无新历史、浏览器伪造字段被拒绝 | 回调先于同步响应的屏障竞态；正式回调验签属 Channel TODO |
| V16 | PaymentExecutionApi：失败 SALE 晚到第二笔真实成功、单 Case、canonical 不变、退款位置不受侵占 | Duplicate 补偿退款 UNKNOWN/FAILED 及人工处置组合 |
| V17 | PaymentExecutionApi：缺失 Pricing 后补齐，单收款事实 | 正式定价 Wire 与历史格式边界属 Owner TODO |
| V18 | PaymentFactHandler、PaymentActionRecoveryHandler：模拟确认丢失后真实 Scanner 重投、稳定 eventId、过期最终 Lease 超限不再执行 Handler | 真实 Broker 未接入；退款 UNKNOWN 的次数耗尽组合 |
| V19 | PaymentFlowRegistry、PaymentExecutionGuards、PaymentAttemptRecoveryHandler：非 Card 类型、重复/未匹配注册、恢复不重新路由 | 新 Wallet/Crypto 用例不在本期范围 |
| V20 | PaymentTrustedResult、PaymentExecutionGuards、Handler 专项：跨 Client/渠道/Account/MID/OE/方式/金额/币种/key 拒绝；当前资格失败无首次发送 | 业务 Owner Wire/版本/期限验证属接入 TODO；Caller 按[内部信任修订](./trusted-internal-amendment.md)移除 |
| V21 | PaymentConcurrency、PaymentTransaction：实际 Redis Lease 失效后 CAS 保证单发送；业务与任务回滚；所有 Owner Probe 检查事务外调用 | 长时间并发压测、基础设施断连与运营恢复组合 |

## 本轮实现收敛

1. 未启用任何 UNKNOWN 重发分支；原 Action 统一查询，避免在正式渠道幂等保证及期限尚未确认时假定安全。
2. 调度使用 Foundation 现有 Scanner/Dispatcher；本轮测试直接触发真实执行器。XXL Admin 的 Executor/Job 注册、告警和环境激活仍需后续范围确认，不以直接调用 Scanner 声称已配置定时调度。
3. Mock 事实接收器模拟确认丢失和接收端去重；实际 RabbitMQ 消息 Wire、路由、Confirm/Nack/Return 与 Finance 消费仍归 TODO-EX-FACT。
4. 测试模块显式依赖直接消费的 api/execution 以完整聚合覆盖率；生产四模块隔离和 Web 组合根不变。

阶段 4 交付后，由用户审阅实现与上述遗留组合，再确认进入[阶段 5 综合验证](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#阶段-5综合验证与本地验收)。这不是部署、真实卡收款、跨系统接入或数据库历史迁移授权。
