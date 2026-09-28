# Payment 内部风控 Action 与交易工具身份决定

- Date: 2026-09-14
- Status: Accepted design direction; implementation pending
- Owners: Payment Execution、Risk；Commerce 只消费统一支付与提交身份合同
- Scope: 首次支付 RISK_CHECK、评估字段位置、Payment 限额引用移除和工具切换的 Attempt 身份

用户提出将内部风控作为一个 Action、去掉 Payment 的限额占用组字段，并确认换支付工具或重新支付建立新 Attempt；对整理后的方向回复“可以，没问题”。据此记录[详细修订](../../requirements/20260913/payment-Execution设计/internal-risk-action-amendment.md)，同步场景附件。

RISK_CHECK 承接 transaction_assessment_id 与 risk_result，占用 Attempt 的单动作执行位。只有可信 ALLOW 才进入 Routing 和认证/资金动作。RISK_CHECK 不属于 Channel 请求；丢响应恢复原 Risk 评估，不建 QUERY Action。Risk 保留原 Attempt→Assessment→Group 的唯一关联，Payment 不重复存 Group ID。

换工具的新支付使用新提交、新 Token、新 Attempt；原命令重试和符合工具合同的同卡补材料复用原身份。原支付未知、授权未释放或已成功时不能换卡绕过占位。其他字段清理建议不随此决定自动批准，多风控检查点和真实 Owner 接入仍不在本次范围。

取代[前序限额决定](./2026-09-14-payment-risk-limit-correlation.md)中“Internal Risk Action 尚待讨论”的状态，其他已确认规则保留。实际代码、数据库和验证范围见[当前台账](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。
