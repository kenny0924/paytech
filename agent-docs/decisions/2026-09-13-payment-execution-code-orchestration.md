# Payment Execution 代码编排与设计范围

- Decision status: Confirmed（阶段 1/2/3 与研发计划）；用户再次回复“可以”，进入阶段 4 本地切片研发。
- Decision date: 2026-09-13。
- Scope: Execution 需求与设计；其余模块和外部系统暂按 Mock + TODO 处理。
- 当前实现、验证与部署状态仅见 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 确认依据

用户认可“统一执行模型 + 多种代码流程 + 可配置 Routing”，随后明确要求“生成需求文档……目前只做execution的设计，其余的暂时mock + todo”。本轮材料与确认记录见[需求目录](../../requirements/20260913/payment-Execution设计/delivery.md)。

## 决定

1. Execution 拥有支付事实、动作合法性、金额、幂等和恢复；Payment Web Processor 组织跨模块流程。Commerce 拥有业务目标与后续命令触发时机，不拼接渠道技术步骤。
2. Card 首次支付使用一份代码流程，按认证要求和已冻结的立即支付/仅授权目标分支；后续 Capture、Void、Refund 使用明确的命令流程。
3. 本期不建设数据库流程定义、节点/连线、发布版本和通用运行实例；不为此前已放弃的配置编排建议新增流程版本字段。业务的支付状态和必要恢复事实仍持久化。
4. Routing 保持独立选择职责：根据流程所需能力，在固定 OE 下选择 Channel/Account/MID；原交易的后续动作不重新选路。当前设计通过 Mock 验证协作合同，不实现真实 Routing。
5. 以移除 t_payment_customer_action、将最小交互事实归入原 Action 为目标。AUTHENTICATE 是需要细化的独立认证能力，不代表现有模型或真实渠道已经支持。
6. Wallet、Crypto 是后续扩展方向；不新增空模块、空流程或推测性数据结构。具体场景和接入模式另行评审。
7. 当前只实施获批 Execution 切片；其余能力列明确测试 Mock 场景与 Owner TODO。生产不可默认装配模拟成功，后续真实接入需独立验证。
8. 用户随后确认进行差异与影响分析，并明确强调不同支付方式的处理流程应注意抽象。将“通用执行约束与方式专属流程分开、恢复沿原执行、不强制所有方式执行 Card 步骤”纳入需求；具体分发和类型契约仍按阶段 2/3 评审，不提前创建空 Wallet/Crypto 实现。

## 取代与保留范围

- 用户后续确认交易级 paymentInstrumentToken、统一 pay、Attempt 直接关联 Token、删除旧 Instrument，以及 Card 受控材料与渠道适配职责。其详细合同按[Token 修订稿](../../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)进入阶段 3 修订评审；不将这次方向确认解释为全部新字段/SQL 已批准或新接口已实现。

- 取代本次会话前序“按 Card 两种目标分别建立主流程”与“数据库配置流程及冻结流程版本”的候选建议。
- 对后续 Execution 设计，交互表的保留要求按本次移除目标重新评审；不表示现有 PDManer、Schema、代码或历史数据已经迁移，也不批准删除持久化所需的等待/未知事实。
- [四模块协作](../../requirements/20260913/payment-业务模块划分/technique.md)、[Commerce Customer 后继入口](../../commerce-service/docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)、[OE/Pricing 决定](./2026-09-11-cct-commerce-oe-pricing-model.md)、原交易及资金约束保持。
- 用户确认阶段 3 接口/状态/字段、Mock 与研发计划，授权阶段 4 研发；SQL 另按交付记录取得本机空库条件授权；真实渠道或其他系统改造不在授权内。阶段安排见[本轮需求](../../requirements/20260913/payment-Execution设计/requirement.md)和[技术入口](../../requirements/20260913/payment-Execution设计/technique.md)。
