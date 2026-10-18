# dispute

- spring.application.name：`dispute-service`
- 端口：18118
- 数据库：`mor_dispute`
- 来源现有工程：`risk-compliance-service` 与 `payment-orchestration-service`（仅 Schema 归属）

阶段 0 只有 Schema 归属、无代码：Risk 拆分（第 06 步）带来的争议 7 表见 [`docs/database/dispute.pdma.json`](docs/database/dispute.pdma.json) 与 [`docs/database/sql/dispute-schema.sql`](docs/database/sql/dispute-schema.sql)（`t_dispute_liability_decision` 的 `decision_evaluation_id`、`action_effect_id` 是对 risk-decision 的跨库 ID 引用，只存不联）；Payment 的渠道争议 3 表由后续步骤并入。本步不建 `mor_dispute` 库。
