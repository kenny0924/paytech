# 授权结果回流：技术方案

> 阶段 2 起编写。阶段 1 已核对的现状证据如下，供阶段 2 展开差异与逐系统影响。

## 已有依据

| 事实 | 证据 |
|---|---|
| Action 状态与渠道原始码 | `payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql`（`t_payment_action.action_status`、`channel_response_code`）；写入逻辑 `PayActionActor` |
| 渠道调用结果结构 | `ChannelOperationResult`（outcome、outputs、failureCode）；`PayOutputs`（paymentStatus、channelStatus、failureCode） |
| PayerMax 拒绝形态 | [PayerMax 卡直付技术方案](../payment-PayerMax卡直付对接/technique.md)；UAT 实测见其 delivery.md |
| Payment 事实发布设计（未实现） | [Execution 状态与恢复](../../20260913/payment-Execution设计/state-and-recovery.md) 的 `PAYMENT_FACT_PUBLICATION_V1` |
| Risk 风险事件表 | `t_velocity_event`、`t_velocity_event_subject`（支付反欺诈一期已实现写入） |
| Risk 交易评估冻结主体 | `t_transaction_risk_assessment`（payment_attempt_id 唯一；card_fingerprint、card_bin、client_ip_address、consumer_reference） |
