# Risk 支付反欺诈一期运行期取舍

- Decision status: Confirmed
- Decision date: 2026-10-14
- Implementation / verification / deployment status: 仅见 [CURRENT_IMPLEMENTATION](../CURRENT_IMPLEMENTATION.md)。
- Scope: Risk 支付尝试评估（`PAYMENT_ATTEMPT_RISK`）的发布、激活、限额义务、Feature 来源与交易评估输入。
- Superseded sections: [Risk 数据库设计](../database/RISK_COMPLIANCE_DATABASE.md)第 3.3、3.4、3.5、3.6 节中下列被修订的部分；其余 Decision Engine、Evaluation、Action、Limit 与 Settlement 规则保留。

用户在[支付反欺诈一期需求](../../requirements/20261014/risk-支付反欺诈一期/requirement.md)阶段 2、3 依次确认 D1～D5 与 S1～S4，完整方案见[方案设计](../../requirements/20261014/risk-支付反欺诈一期/design.md)。

| 编号 | 原设计 | 本决定 |
|---|---|---|
| R1 | 发布 = 决策点版本 + 有序规则集 | 新格式执行发布额外冻结一个决策流版本，运行按流程执行；结账既有测试候选路径不变 |
| R2 | 两名不同审核人批准后激活 | TEST 环境由一人激活并写入审计；PRODUCTION 仍要求双人审批，一期不实现 |
| R3 | 支付放行必须执行 `RESERVE_LIMIT_GROUP` | 限额能力交付前，支付决策点版本不绑定占用义务；累计限额以实时累计规则表达 |
| R4 | Feature 只由工程注册 Handler | 保持；新增实时累计、名单命中两个 Handler，参数由运营经变量创建、随 Feature Version 不可变 |
| R5 | 交易评估按 Order Context、网络证据等冻结 | 按 Payment 实际可提供的字段冻结，删除已废弃 assignment 等字段 |
| R6 | Payment 事件携带 `limit_reservation_group_id` | 按 [2026-09-14 决定](./2026-09-14-payment-risk-limit-correlation.md)删除该描述 |

另确认：支付决策点默认决定为 ALLOW；“IP 与卡国家不一致”模板移至二期；影子运行回放最近的正式评估；名单、执行发布与影子运行一期仅提供内部接口。名单由 `risk-control` 拥有，`risk-decision` 单向依赖其公开的名单命中查询能力。
