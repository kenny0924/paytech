# 03 · 服务坐标与表归属

- 状态：阶段 3 方案评审稿（2026-10-17）；第 2 节全部归属与第 3 节共享数据策略已由用户逐项确认（C1–C7、S7、六张建议表、`t_currency_spec`、旧服务名别名），待整体确认。
- 依据：[technique.md](../technique.md) 第 2、4、5 节；[requirement.md](../requirement.md) 第 2.2 节搬迁顺序；六份盘点 [analysis/](../analysis/)；[术语表](../../architecture-目标架构V2/glossary.md)；五份现行 Schema（`finance-schema.sql` 84 表、`risk-compliance-schema.sql` 85 表、`payment-orchestration-schema.sql` 31 表、`client-contract-schema.sql` 38 表、`commerce-schema.sql` 17 表，本文按 `CREATE TABLE` 实际逐表核对）。
- 本文只记录归属与坐标，不生成 SQL；第 11、12 步新增的账户余额表、卡域事件与授权表在各自方案文档（S1、S3）中以 SQL 归档，不计入本文表数。
- 约定：表格中"是否有代码"指生产 Java 或 Mapper XML 引用该表；`t_compensation_event` 由 Foundation `paytech-compensation-event` 库访问，服务源码不直接引用，记为"有（库）"。

## 1. 服务与 Maven 坐标表

### 1.1 命名约定

- 目录：`paytech/<service>/`；模块：`paytech-<service>-api`、`paytech-<service>-core`、`paytech-<service>-app`、`paytech-<service>-test`；groupId 统一 `com.paytech`，版本由 `paytech-dependencies` BOM 管理。
- 数据库：`mor_<service>`，连字符改下划线；每个逻辑服务独立库（D09 修订）。保留系统沿用现有库名 `mor_platform`、`mor_vault`。
- Spring 应用名：业务服务 `<service>-service`，网关与 BFF 沿用现名。应用名同时是 Nacos dataId、XXL-Job 执行器名和 `@ImportHttpServices` 的服务发现名，改名的服务在同一搬迁步骤内同步更新全部调用方的发现名；`risk-service`、`payment-orchestration-service` 等被多方消费的旧名**不保留**别名注册（用户已定，与 C6 同口径）：改名步骤同分支改完全部调用方的发现名，门禁靠各调用方 `-test`。
- 端口：业务服务 181xx、BFF 182xx、网关 18080；已有端口不变，新端口自 18108 起按下表顺序分配。管理端口 = 业务端口 + 1000（与 01 §3 一致；第 09 步 data-service 已按 18120 / 19120 实现），网关与 BFF 沿用 190xx / 192xx。
- 独立 app 的例外：Q3 曾决定卡域作为 payment-app 内模块，已被 Q6 / D36 修订（第 12 步"卡域独立进程与库"）覆盖，本文按独立服务登记。payout、dispute、fx、workflow-approval、data-service 阶段 0 只建骨架、Schema 归属与空 app，不实现能力（requirement.md 第 2.3 节）。

### 1.2 坐标总表

| 逻辑服务 | 目录 `paytech/` | artifactId（api / core / app / test） | 数据库 | spring.application.name | 端口 | 来源现有工程 / 模块 |
|---|---|---|---|---|---|---|
| acquiring | `acquiring/` | `paytech-acquiring-api`（合并现 `paytech-commerce-client-api` 与 `paytech-commerce-customer-api`）/ `paytech-acquiring-transaction-core`、`paytech-acquiring-checkout-core`、`paytech-acquiring-command-core`（三 core 共用一库，是否合并为单个 `-core` 分包见 01 父 POM 文档）/ `paytech-acquiring-app` / `paytech-acquiring-test` | `mor_acquiring`（现 `mor_commerce` 改名） | `acquiring-service`（现 `commerce-service`） | 18102（沿用） | `commerce-service`：client-api、customer-api、web 中除消费者外的全部代码、test |
| consumer | `consumer/` | `paytech-consumer-api` / `-core` / `-app` / `-test` | `mor_consumer` | `consumer-service` | 18109 | `commerce-service` web 中的 Customer / StoredCardSetting / StoredCardInvalidation 代码（见 commerce.md 第 1 节清单）；`ClientCustomerController` 路由 |
| payment | `payment/` | `paytech-payment-api`（现 `paytech-payment-api` 去掉 card 合同）/ `-core` / `-app` / `-test` | `mor_payment`（沿用） | `payment-service`（现 `payment-orchestration-service`） | 18103（沿用） | `payment-orchestration-service`：execution、routing、web 组合根中的 payment 部分、`PaymentResultRabbitPublisher` |
| channel-connector | `channel-connector/` | `paytech-channel-connector-api` / `-core` / `-app` / `-test` | `mor_channel_connector` | `channel-connector-service` | 18110 | `paytech-payment-channel`；web 中 `PaymentChannelClientImpl` 的渠道侧；`PayActionActor` / `PayOutputs` 的渠道结果映射（D06） |
| card | `card/` | `paytech-card-api`（现 `StoredCardApi`、`ChannelCardMaterialApi`、`PaymentInstrumentApi` 的卡部分）/ `-core` / `-app` / `-test` | `mor_card` | `card-service` | 18111 | `paytech-payment-card`；web 中 `StoredCardController`、`ChannelCardMaterialController` |
| client-master | `client-master/` | `paytech-client-master-api` / `-core` / `-app` / `-test` | `mor_client_master`（现 `mor_cct` 改名） | `client-master-service`（现 `client-contract-service`） | 18101（沿用） | `client-contract-service`：api 的 onboarding / workspace 合同、web 中入驻 / 人员 / 文档 / 审核 / 分类 / 工作区 / 投影代码、MQ 消费者、3 个补偿 Handler |
| commercial | `commercial/` | `paytech-commercial-api`（现 `CheckoutContextApi`）/ `-core` / `-app` / `-test` | `mor_commercial` | `commercial-service` | 18108 | `client-contract-service` web 中 Checkout 解析（`CheckoutResolutionProcessor`、`CheckoutQueryServiceImpl` 商务部分）、协议 / 价目 / 支付方式 Profile Mapper |
| risk-decision | `risk-decision/` | `paytech-risk-decision-api`（现 `risk-api` 的 decision / control / evaluation 包）/ `-core` / `-app` / `-test` | `mor_risk_decision`（现 `mor_risk` 改名） | `risk-decision-service`（现 `risk-service`） | 18104（沿用） | `risk-compliance-service`：`risk-decision`、`risk-control`（并入）、web 中 decision 装配与 Controller |
| compliance | `compliance/` | `paytech-compliance-api`（现 `risk-api` 的 compliance 包）/ `-core` / `-app` / `-test` | `mor_compliance` | `compliance-service` | 18112 | `risk-compliance-service`：`risk-compliance`、web 中 compliance Controller 与 `ClientReviewResultRabbitPublisher` |
| billing | `billing/` | `paytech-billing-api`（现 `paytech-finance-api` 的 channel-cost 合同）/ `-core` / `-app` / `-test` | `mor_billing` | `billing-service` | 18113 | `finance-service` accounting 中经济事实只读、渠道成本配置、`ChannelCostEstimationService`；web 中 `/channel-cost-plans/v1` Controller |
| account | `account/` | `paytech-account-api`（现 `paytech-finance-api` 的 balance 合同）/ `-core` / `-app` / `-test` | `mor_account` | `account-service` | 18114 | `finance-service` accounting 中 `BalanceQueryServiceImpl`、`/balances/v1` Controller；余额账户表与过账结果消费按 S1 新建 |
| ledger | `ledger/` | `paytech-ledger-api`（现 `paytech-finance-api` 的 accounting 合同）/ `-core` / `-app` / `-test` | `mor_ledger`（现 `mor_finance` 改名） | `ledger-service`（现 `finance-service`） | 18105（沿用 Finance 现端口） | `finance-service` accounting 其余全部代码（过账内核、规则、人工调整、试算、关账）；web 中 11 组 Controller |
| reconciliation | `reconciliation/` | `paytech-reconciliation-api` / `-core` / `-app` / `-test` | `mor_reconciliation` | `reconciliation-service` | 18115 | `finance-service` 空壳 `paytech-finance-treasury` 的对账职责；仅 `t_channel_account` 只读代码 |
| tax | `tax/` | `paytech-tax-api` / `-core` / `-app` / `-test` | `mor_tax` | `tax-service` | 18116 | `finance-service` 空壳 `paytech-finance-tax`；无代码 |
| payout | `payout/` | `paytech-payout-api` / `-core` / `-app` / `-test` | `mor_payout` | `payout-service` | 18117 | `finance-service` 空壳 treasury 的出款职责；无代码。Manual Payout 资格评估 API 留在 risk-decision |
| dispute | `dispute/` | `paytech-dispute-api` / `-core` / `-app` / `-test` | `mor_dispute` | `dispute-service` | 18118 | `risk-compliance-service` 争议 7 表 + `payment-orchestration-service` 渠道争议 3 表；无代码（争议处理留待业务切片） |
| fx | `fx/` | `paytech-fx-api` / `-core` / `-app` / `-test` | `mor_fx` | `fx-service` | 18119 | 新增骨架（FX Rate 与 FX Deal 未决，单服务） |
| data-service | `data-service/` | `paytech-data-service-api` / `-core` / `-app` / `-test` | Doris 库 `mor_data_service`（非 MySQL；CDC 白名单表只读同步） | `data-service` | 18120 | 新增骨架（D25 / D35，第 9 步） |
| platform-support | `platform-support/` | `paytech-platform-support-api` / core 层保留现有 5 个业务模块并加前缀 `paytech-platform-support-{access-control,audit-security,file-document,delivery,integration}` / `paytech-platform-support-app` / `paytech-platform-support-test` | `mor_platform`（沿用） | `platform-support-service`（沿用） | 18106（沿用） | `platform-support-service` 全部，业务不动（第 2 步） |
| workflow-approval | `workflow-approval/` | `paytech-workflow-approval-api` / `-core` / `-app` / `-test` | `mor_workflow_approval` | `workflow-approval-service` | 18121 | 新增骨架（D13），无代码 |
| api-gateway | `api-gateway/` | 无 api / core；`paytech-api-gateway-app`（现 `api-gateway-web`，WebFlux）/ `paytech-api-gateway-test` | 无（Redis 状态） | `api-gateway`（沿用） | 18080 / 管理 19080（沿用） | `api-gateway` |
| client-console-bff | `client-console-bff/` | 无 api；`paytech-client-console-bff-app`（现 `-web`）/ `paytech-client-console-bff-test` | 无 | `client-console-bff`（沿用） | 18202 / 管理 19202（沿用） | `client-console-bff` |
| operations-console-bff | `operations-console-bff/` | 无 api；`paytech-operations-console-bff-app`（现 `-web`）/ `paytech-operations-console-bff-test` | 无 | `operations-console-bff`（沿用） | 18201 / 管理 19201（沿用） | `operations-console-bff` |
| partner-console-bff（未来） | `partner-console-bff/` | `paytech-partner-console-bff-app` / `-test` | 无 | `partner-console-bff` | 预留 18203 / 管理 19203 | 现不存在，只预留坐标与端口 |
| vault（Go） | `vault/` | Go module `github.com/paytech/vault-service`（`cmd/vault-server`）；Java `paytech-vault-client`（等价 api）、`paytech-vault-test` | `mor_vault`（沿用） | `vault-service`（Nacos 注册名，沿用） | 18107（沿用） | `vault-service` |

端口分配汇总：沿用 18080、18101–18107、18201、18202；新分配 18108 commercial、18109 consumer、18110 channel-connector、18111 card、18112 compliance、18113 billing、18114 account、18115 reconciliation、18116 tax、18117 payout、18118 dispute、18119 fx、18120 data-service、18121 workflow-approval、18203 partner-console-bff（预留）。

## 2. 逐表归属表

迁移动作取值：**搬迁** = 表与代码一起迁入目标库；**仅 Schema 归属** = 无代码，只把 DDL 归入目标库的 Schema 文件与 PDManer 模型；**删除**；**每库一份** / **两边各一份** = 基础设施或审计表按目标库复制。

### 2.1 Finance → ledger / account / billing / reconciliation / tax / payout（84 表）

| 表 | 目标服务 | 目标库 | 是否有代码 | 迁移动作 | 备注 |
|---|---|---|---|---|---|
| t_chart_of_accounts | ledger | mor_ledger | 有 | 搬迁 | 会计科目表（种子） |
| t_chart_account | ledger | mor_ledger | 有 | 搬迁 | 叶子科目；`AccountMapper.selectOwnerBalances` 等联表随 S1 改写 |
| t_ledger_book | ledger | mor_ledger | 有 | 搬迁 | `BalanceQueryServiceImpl` 读账簿，S1 后改经 ledger-api |
| t_book_sequence | ledger | mor_ledger | 有 | 搬迁 | |
| t_fiscal_calendar | ledger | mor_ledger | 有 | 搬迁 | |
| t_accounting_period | ledger | mor_ledger | 有 | 搬迁 | |
| t_accounting_rule_bundle | ledger | mor_ledger | 有 | 搬迁 | 种子规则包 |
| t_accounting_rule_bundle_ver | ledger | mor_ledger | 有 | 搬迁 | |
| t_accounting_rule_line | ledger | mor_ledger | 有 | 搬迁 | 规则行以 `AmountSource` 代码匹配 billing 组件 |
| t_accounting_instruction | ledger | mor_ledger | 有 | 搬迁 | `transaction_economics_id` 更名 `economic_fact_id`，仅存 ID |
| t_instruction_line | ledger | mor_ledger | 有 | 搬迁 | |
| t_journal_entry | ledger | mor_ledger | 有 | 搬迁 | |
| t_journal_line | ledger | mor_ledger | 有 | 搬迁 | 联 `t_account` 的 4 条 SQL 随 S1 改写 |
| t_manual_adjustment | ledger | mor_ledger | 有 | 搬迁 | |
| t_manual_adjustment_line | ledger | mor_ledger | 有 | 搬迁 | 行引用 account ID |
| t_adjustment_approval | ledger | mor_ledger | 有 | 搬迁 | |
| t_ledger_period_balance | ledger | mor_ledger | 有 | 搬迁 | |
| t_trial_balance_run | ledger | mor_ledger | 有 | 搬迁 | |
| t_trial_balance_line | ledger | mor_ledger | 有 | 搬迁 | |
| t_trial_balance_exception | ledger | mor_ledger | 有 | 搬迁 | 投影重建同时写 account，S1 改为事件 |
| t_period_close | ledger | mor_ledger | 有 | 搬迁 | 关账步骤 2 全库计数补偿事件，拆库后只覆盖本库（热点 8） |
| t_period_close_book | ledger | mor_ledger | 有 | 搬迁 | |
| t_close_step | ledger | mor_ledger | 有 | 搬迁 | |
| t_close_approval | ledger | mor_ledger | 有 | 搬迁 | |
| t_close_account_rule | ledger | mor_ledger | 有 | 搬迁 | 测试清理器未覆盖，搬迁时补入 |
| t_account | ledger（会计科目账户）；余额投影列迁出到 account | mor_ledger；新余额表在 mor_account | 有 | 搬迁 + 更名 `t_ledger_account` 去余额列；account 新建 `t_account_balance`（2026-10-19 明细账重构改为 `t_owner_account` + 过账流 / 批次 / 流水表，ledger 只留控制科目） | C1 已定（design/04 §3）：余额只由 `EconomicFactRecorded` 维护，第 11b 步执行；2026-10-18 决定 account 为 Owner 明细账、ledger 消费 `AccountPosted` 过账控制科目 |
| t_transaction_economics | billing | mor_billing | 有（只读） | 搬迁 + 更名 `t_economic_fact`，补 `fact_type=MANUAL_ADJUSTMENT`、`supersedes_fact_id`、`status` | 唯一经济事实（C1）；ledger 与 account 均消费 `EconomicFactRecorded`，不再读表（04 §2）；**2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_transaction_economic_component | billing | mor_billing | 有（只读） | 搬迁 + 更名 `t_economic_fact_line` | **2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_transaction_economic_comp_link | billing | mor_billing | 无 | 搬迁 + 更名 `t_economic_fact_line_link` | 更正链；**2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_channel_cost_plan | billing | mor_billing | 有 | 搬迁 | `/channel-cost-plans/v1`；**2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)（接口改 `/channel-connector/v1/channel-cost-plans`） |
| t_channel_cost_plan_ver | billing | mor_billing | 有 | 搬迁 | **2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_channel_cost_scope | billing | mor_billing | 有 | 搬迁 | **2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_channel_cost_component | billing | mor_billing | 有 | 搬迁 | **2026-10-19 起：** `t_billing_fact*` 五表归 billing（本行经济事实表删除），`t_channel_cost_*` 四表归 channel-connector，见 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| t_channel_account | reconciliation | mor_reconciliation | 有（只读） | 搬迁 | Finance 渠道清算账户，与 Payment 同名表无关；ledger 自动记账解析 CHANNEL_ACCOUNT owner 改为事务外读 reconciliation-api |
| t_channel_open_item | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_channel_debit_item | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_duplicate_capture_item | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | `journal_entry_id` 仅存 ID |
| t_channel_settle_batch | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_channel_settle_item | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_channel_transaction_match | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_channel_cost_confirm | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | 实际成本确认 |
| t_channel_cost_alloc | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | `transaction_economics_id` 仅存 ID |
| t_cash_account | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | 已定（2026-10-17）；account 的 BANK_CASH owner_id 仅存 ID |
| t_cash_account_binding | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | 已定 |
| t_cash_account_binding_ver | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | 已定 |
| t_bank_statement | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_bank_statement_item | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_bank_recon_match | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | 引用 payout execution 与 tax payment 仅存 ID |
| t_recon_exception | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | |
| t_recon_resolution | reconciliation | mor_reconciliation | 无 | 仅 Schema 归属 | `instruction_id` 仅存 ID |
| t_tax_jurisdiction | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_authority | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_registration | tax | mor_tax | 无 | 仅 Schema 归属 | 引用 finance_entity 仅存 ID |
| t_tax_category | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_category_mapping | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_treatment | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_rule | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_place_of_supply_rule | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_rate | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_evidence_rule | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_exchange_rate | tax | mor_tax | 无 | 仅 Schema 归属 | 税务汇率；与 fx 服务的关系留业务切片 |
| t_tax_snapshot | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_evidence | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_line | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_fiscal_number_sequence | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_fiscal_record | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_fiscal_record_line | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_period | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_liability_summary | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_return | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_return_line | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_tax_payment | tax | mor_tax | 无 | 仅 Schema 归属 | 跨 tax / reconciliation / account / ledger 的列全部仅存 ID |
| t_tax_payment_event | tax | mor_tax | 无 | 仅 Schema 归属 | |
| t_beneficiary_account | payout | mor_payout | 无 | 仅 Schema 归属 | |
| t_payout | payout | mor_payout | 无 | 仅 Schema 归属 | `available_account_id` / `locked_account_id` 仅存 ID |
| t_payout_approval | payout | mor_payout | 无 | 仅 Schema 归属 | |
| t_payout_execution | payout | mor_payout | 无 | 仅 Schema 归属 | |
| t_payout_evidence_ref | payout | mor_payout | 无 | 仅 Schema 归属 | |
| t_payout_event | payout | mor_payout | 无 | 仅 Schema 归属 | |
| t_finance_restriction_proj | payout | mor_payout | 无 | 仅 Schema 归属 | CCT / Risk 限制投影，出款 fail-closed 门 |
| t_currency_spec | platform-support（Owner，全平台参考数据） | mor_platform；mor_ledger 与 mor_billing 各持只读副本 | 有 | 搬迁至 Platform + 复制只读 | 用户已定（2026-10-17）；见第 3.1 节 |
| t_finance_entity | ledger | mor_ledger | 有 | 搬迁 | 见第 3.2 节 |
| t_compensation_event | 全部六个 Finance 后继服务 | 每库一份 | 有（库） | 每库一份 | Finance 现无 Handler，仅关账计数 |
| t_command_idempotency | — | — | 无 | 删除（待 Owner 确认） | S10 |

### 2.2 Risk → risk-decision / compliance / dispute（85 表）

| 表 | 目标服务 | 目标库 | 是否有代码 | 迁移动作 | 备注 |
|---|---|---|---|---|---|
| t_dd_program | compliance | mor_compliance | 有 | 搬迁 | 材料程序目录 |
| t_dd_program_version | compliance | mor_compliance | 有 | 搬迁 | `content_hash` Schema `DD_PROGRAM_VERSION:1` |
| t_dd_program_requirement | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_program_check | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_dd_program_screening | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_due_diligence_case | compliance | mor_compliance | 有 | 搬迁 | I01 审核案件根 |
| t_dd_case_subject | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_case_classification | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_case_dependency | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_requirement | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_evidence_ref | compliance | mor_compliance | 有 | 搬迁 | |
| t_dd_case_state_change | compliance | mor_compliance | 有 | 搬迁 | `trigger_type` 可能引用 decision effect，仅存 ID |
| t_dd_party_snapshot | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_dd_check | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_screening_run | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_screening_hit | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_screening_resolution | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_qualification_decision | compliance | mor_compliance | 有 | 搬迁 | `decision_evaluation_id` 仅存 ID，现恒为空 |
| t_role_qualification | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_qualification_state_change | compliance | mor_compliance | 无 | 仅 Schema 归属 | |
| t_settlement_control | compliance | mor_compliance | 无 | 仅 Schema 归属（C3 已定：合规持有，决策经 compliance-api 读写）| D27 事件 `SettlementRestrictionChanged` 由 compliance 发布 |
| t_settlement_control_scope | compliance | mor_compliance | 无 | 仅 Schema 归属（C3 已定：合规持有，决策经 compliance-api 读写）| 同上 |
| t_settle_control_state_change | compliance | mor_compliance | 无 | 仅 Schema 归属（C3 已定：合规持有，决策经 compliance-api 读写）| 同上 |
| t_rule_reject_reason | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_subject_tag | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_point | risk-decision | mor_risk_decision | 有 | 搬迁 | 种子 CHECKOUT_PREPARE / PAYMENT_ATTEMPT_RISK |
| t_decision_point_version | risk-decision | mor_risk_decision | 有 | 搬迁 | 种子 |
| t_decision_point_input_field | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_point_feature | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_point_rule_set | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_point_obligation | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_feature_version | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_feature_dependency | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_velocity_feature_definition | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_list_feature_definition | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_set | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_set_version | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_risk_rule | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_node | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_operand | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_action_version | risk-decision | mor_risk_decision | 有 | 搬迁 | 种子 Action 目录 |
| t_action_parameter_definition | risk-decision | mor_risk_decision | 有 | 搬迁 | 种子 |
| t_rule_action | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_rule_action_parameter | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_release | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_release_audit_event | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_release_shadow_run | risk-decision | mor_risk_decision | 有 | 搬迁 | `DECISION_SHADOW_RUN` 补偿 Handler |
| t_decision_flow | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_flow_version | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_flow_node | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_flow_edge | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_flow_end_output | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_risk_variable | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_risk_variable_member | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_decision_evaluation | risk-decision | mor_risk_decision | 有 | 搬迁 | 被 compliance / dispute 以 ID 引用 |
| t_evaluation_feature_result | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_evaluation_rule_trace | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_evaluation_flow_trace | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_evaluation_action_intent | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_action_effect | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | settlement control 与 dispute 以 ID 引用 |
| t_checkout_input_snapshot | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_transaction_risk_assessment | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_settlement_risk_assessment | risk-decision | mor_risk_decision | 无（仅测试 Mapper） | 仅 Schema 归属 | Manual Payout 四检查点快照；`ManualPayoutEligibilityApi` 随之归 decision |
| t_risk_restriction | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | 反欺诈冷却 / 阻断 |
| t_risk_list | risk-decision | mor_risk_decision | 有（risk-control） | 搬迁 | risk-control 并入 decision |
| t_risk_list_entry | risk-decision | mor_risk_decision | 有（risk-control） | 搬迁 | 同上 |
| t_payment_attempt_fact | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_velocity_event | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_velocity_event_subject | risk-decision | mor_risk_decision | 有 | 搬迁 | |
| t_limit_version | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | `LIMIT_VERSION:1` 哈希 Schema |
| t_decision_point_limit_binding | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_limit_position | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_limit_reservation_group | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_limit_reservation | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_limit_group_state_change | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_limit_movement | risk-decision | mor_risk_decision | 无 | 仅 Schema 归属 | |
| t_dispute_case | dispute | mor_dispute | 无 | 仅 Schema 归属 | 争议处理留待业务切片 |
| t_dispute_state_change | dispute | mor_dispute | 无 | 仅 Schema 归属 | |
| t_dispute_evidence_package | dispute | mor_dispute | 无 | 仅 Schema 归属 | PDManer canonicalizer 随表迁移 |
| t_dispute_evidence_ref | dispute | mor_dispute | 无 | 仅 Schema 归属 | |
| t_dispute_submission_request | dispute | mor_dispute | 无 | 仅 Schema 归属 | |
| t_dispute_outcome | dispute | mor_dispute | 无 | 仅 Schema 归属 | |
| t_dispute_liability_decision | dispute | mor_dispute | 无 | 仅 Schema 归属 | `decision_evaluation_id` + `action_effect_id` 仅存 ID，需同步评估合同（Risk 热点 3） |
| t_compensation_event | compliance、risk-decision、dispute | 每库一份 | 有（库） | 每库一份 | 现两核心共写；按 event_type 拆分 Handler 与清理 |
| t_command_idempotency | — | — | 无 | 删除（待 Owner 确认） | S10 |

### 2.3 Payment → payment / channel-connector / card / dispute（31 表）

| 表 | 目标服务 | 目标库 | 是否有代码 | 迁移动作 | 备注 |
|---|---|---|---|---|---|
| t_payment_channel | channel-connector | mor_channel_connector | 有 | 搬迁 | 渠道身份 |
| t_channel_account | channel-connector | mor_channel_connector | 有 | 搬迁 | 技术 API 账号；`entity_no` 来自 client-master 仅存值；与 Finance 同名表无关 |
| t_channel_mid | channel-connector | mor_channel_connector | 有 | 搬迁 | |
| t_mid_capability | channel-connector | mor_channel_connector | 有 | 搬迁 | 能力代码解析统一到 channel-connector |
| t_mid_capability_detail | channel-connector | mor_channel_connector | 有 | 搬迁 | |
| t_routing_policy | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | S7 已定（2026-10-17）：归 channel-connector |
| t_routing_policy_version | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | S7 已定 |
| t_routing_policy_mid | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | S7 已定 |
| t_channel_interaction | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | S7 已定（2026-10-17）：归 channel-connector；`payment_action_id` / `channel_dispute_action_id` 仅存 ID |
| t_channel_callback | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | 验签与去重 |
| t_channel_callback_item | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | S7 已定（2026-10-17）：归 channel-connector；`target_type` / `target_id` 指向 payment 仅存 ID |
| t_channel_event_exception | channel-connector | mor_channel_connector | 无 | 仅 Schema 归属 | |
| t_routing_decision | payment | mor_payment | 无 | 仅 Schema 归属 | 以 `payment_attempt_id` 为键；渠道坐标仅存 ID |
| t_route_candidate_eval | payment | mor_payment | 无 | 仅 Schema 归属 | 冻结健康度证据，无维护代码 |
| t_payment_intent | payment | mor_payment | 有 | 搬迁 | |
| t_payment_attempt | payment | mor_payment | 有 | 搬迁 | 冻结 channel / account / mid ID |
| t_payment_instrument_token | payment | mor_payment | 有 | 搬迁 | `instrument_ref` 为卡工具 ID 字符串，是 payment ↔ card 唯一关联 |
| t_payment_action | payment | mor_payment | 有 | 搬迁 | |
| t_channel_transaction_ref | payment | mor_payment | 有 | 搬迁 | S7 已定（2026-10-17）：归 payment；唯一键含 channel / mid ID，未来回调反查经 payment-api |
| t_duplicate_payment_case | payment | mor_payment | 无 | 仅 Schema 归属 | |
| t_payment_refund_position | payment | mor_payment | 无 | 仅 Schema 归属 | |
| t_channel_refund | payment | mor_payment | 无 | 仅 Schema 归属 | 名称带 `channel_` 前缀但是退款动作根 |
| t_payment_card | card | mor_card | 有 | 搬迁 | `dek_vault_token`、`encrypted_pan`；S3 后支付成功写入改事件驱动 |
| t_payment_card_credential | card | mor_card | 有 | 搬迁 | `verified_time` 更新改事件驱动（第 12 步） |
| t_payment_card_instrument | card | mor_card | 有 | 搬迁 | 含 `client_id`、`customer_id`（consumer ID，保持不变，S6） |
| t_payment_stored_card | card | mor_card | 有 | 搬迁 | 以 consumer `customer_id` 为键 |
| t_channel_dispute_notice | dispute | mor_dispute | 无 | 仅 Schema 归属 | S7 已定（2026-10-17）：归 dispute；`original_channel_txn_ref_id` 仅存 ID |
| t_channel_dispute_event | dispute | mor_dispute | 无 | 仅 Schema 归属 | S7 已定 |
| t_channel_dispute_action | dispute | mor_dispute | 无 | 仅 Schema 归属 | S7 已定；冻结 channel / MID / account 坐标仅存 ID |
| t_compensation_event | payment、channel-connector、card | 每库一份 | 有（库） | 每库一份 | 现只用 `PAYMENT_SUCCEEDED`（payment） |
| t_command_idempotency | — | — | 无 | 删除（待 Owner 确认） | S10 |

### 2.4 CCT → client-master / commercial（38 表）

| 表 | 目标服务 | 目标库 | 是否有代码 | 迁移动作 | 备注 |
|---|---|---|---|---|---|
| t_party | client-master | mor_client_master | 有 | 搬迁 | 法律主体根 |
| t_party_profile | client-master | mor_client_master | 有 | 搬迁 | commercial 签署校验改经 client-master-api 读取（S4 候选 + 根版本） |
| t_individual_profile | client-master | mor_client_master | 有 | 搬迁 | |
| t_organization_profile | client-master | mor_client_master | 有 | 搬迁 | |
| t_party_identity_key | client-master | mor_client_master | 有 | 搬迁 | |
| t_party_profile_identifier | client-master | mor_client_master | 有 | 搬迁 | |
| t_party_address | client-master | mor_client_master | 有 | 搬迁 | |
| t_party_contact | client-master | mor_client_master | 有 | 搬迁 | Vault Token + 掩码 |
| t_party_relation | client-master | mor_client_master | 有 | 搬迁 | |
| t_client | client-master | mor_client_master | 有 | 搬迁 | |
| t_local_partner | client-master | mor_client_master | 有 | 搬迁 | |
| t_operating_entity | client-master | mor_client_master | 有 | 搬迁 | PK `entity_no` |
| t_client_business_profile | client-master | mor_client_master | 有 | 搬迁 | |
| t_client_business_classification | client-master | mor_client_master | 有 | 搬迁 | |
| t_client_application | client-master | mor_client_master | 有 | 搬迁 | |
| t_partner_application | client-master | mor_client_master | 有 | 搬迁 | 仅 `ClientPeopleMapper` 引用 |
| t_operating_entity_application | client-master | mor_client_master | 有 | 搬迁 | |
| t_operating_entity_scope | client-master | mor_client_master | 有 | 搬迁 | |
| t_application_document | client-master | mor_client_master | 有 | 搬迁 | |
| t_client_entity_affiliation | client-master | mor_client_master | 有 | 搬迁 | 11 条商务 SQL 的子查询来源，改为 `entityNos` 参数（第 4 步） |
| t_entity_source_relation | client-master | mor_client_master | 有 | 搬迁 | 同上，改为 `localPartnerIds` 参数 |
| t_agreement | commercial | mor_commercial | 有 | 搬迁 | `client_id`、`local_partner_id`、`entity_no` 仅存值 |
| t_agreement_party | commercial | mor_commercial | 有 | 搬迁 | `ClientPeopleMapper` 的"共享人员"反向读取改经 commercial-api（CCT 热点 9） |
| t_agreement_document_ref | commercial | mor_commercial | 有 | 搬迁 | |
| t_agreement_operating_entity | commercial | mor_commercial | 有 | 搬迁 | |
| t_payment_method_profile | commercial | mor_commercial | 有 | 搬迁 | |
| t_payment_method_profile_cond | commercial | mor_commercial | 无 | 仅 Schema 归属 | 代码从未引用 |
| t_commercial_term_plan | commercial | mor_commercial | 有 | 搬迁 | |
| t_commercial_term_version | commercial | mor_commercial | 有 | 搬迁 | |
| t_commercial_term_price | commercial | mor_commercial | 有 | 搬迁 | |
| t_commercial_term_component | commercial | mor_commercial | 有 | 搬迁 | |
| t_commercial_term_component_band | commercial | mor_commercial | 有 | 搬迁 | |
| t_consumer_legal_document | client-master | mor_client_master | 有 | 搬迁（C4 已定：客户主档持有，解析时随候选返回）| 只被解析消费 |
| t_seller_disclosure | client-master | mor_client_master | 有 | 搬迁（C4 已定：客户主档持有，解析时随候选返回）| 同上 |
| t_business_change_record | client-master 与 commercial | 两边各一份 | 有 | 两边各一份 | record_type 按核心拆分，见第 3.3 节 |
| t_business_change_version | client-master 与 commercial | 两边各一份 | 有 | 两边各一份 | 同上 |
| t_compensation_event | client-master、commercial | 每库一份 | 有（库） | 每库一份 | 现 3 个 Handler 全在 client-master |
| t_command_idempotency | — | — | 无 | 删除（待 Owner 确认） | S10 |

### 2.5 Commerce → acquiring（transaction / checkout / command 共库）+ consumer（17 表）

| 表 | 目标服务 | 目标库 | 是否有代码 | 迁移动作 | 备注 |
|---|---|---|---|---|---|
| t_commerce_order | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | `customer_id` 软引用 consumer，仅存 ID；准备阶段残留列按 S10 处理 |
| t_order_item | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | |
| t_order_context | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | 写入方在 `CustomerCheckoutMapper.insertContext`，搬迁时归位 |
| t_order_legal | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | 已定（2026-10-17）：创建时冻结、checkout 大量读 |
| t_order_payment_projection | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | |
| t_order_funds_availability_projection | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | |
| t_order_refund_position | acquiring（transaction） | mor_acquiring | 有 | 搬迁 | 已定（2026-10-17）：command 经同库 CAS 预留 |
| t_checkout_session | acquiring（checkout） | mor_acquiring | 有 | 搬迁 | `expire` 语句无调用方，随 S10 清理 |
| t_consumer_context | acquiring（checkout） | mor_acquiring | 有 | 搬迁 | 按订单的 Email / Profile Token，不属于 consumer（前提修正） |
| t_consumer_contract | acquiring（checkout） | mor_acquiring | 有 | 搬迁 | |
| t_checkout_network_evidence | acquiring（checkout） | mor_acquiring | 有 | 搬迁 | |
| t_consumer_acceptance | acquiring（checkout） | mor_acquiring | 有 | 搬迁 | |
| t_refund_request | acquiring（command） | mor_acquiring | 有 | 搬迁 | |
| t_customer | consumer | mor_consumer | 有 | 搬迁 | ID 全局稳定，Payment 已存卡以其为键（S6） |
| t_client_stored_card_setting | client-master | mor_client_master | 有（只读） | 搬迁 | 用户已定（2026-10-17）：Client 级配置归客户主档；收单结账时随候选接口的 `client.storedCardSetting` 返回（06 §2.1），不单独调用；无写入路径 |
| t_compensation_event | acquiring、consumer | 每库一份 | 有（库） | 每库一份 | `STORED_CARD_INVALIDATION` 归 consumer，其余归 acquiring |
| t_command_idempotency | — | — | 无 | 删除（待 Owner 确认） | S10；测试 DDL 已不含 |

## 3. 共享引用数据处理

| 数据 | 现状 | Owner | 其他方获取方式 | 备注 |
|---|---|---|---|---|
| `t_currency_spec` | Finance 种子表，billing（`ChannelCostEstimation` / `ChannelCostPlan`）与 ledger 均有代码读取 | **platform-support**（全平台参考数据；表与初始化 DML 随 Platform 维护，新增 `GET /platform/v1/currency-specs`） | **复制只读**：过账与计费的舍入不得依赖远程调用，mor_ledger 与 mor_billing 各持同结构只读副本，阶段 0 用同一份评审 SQL 初始化；副本同步机制（Platform 变更事件或迁移执行器）阶段 0 不实现；tax / account / reconciliation / payout 实现时再决定复制或经 platform-api 读取 | 用户已定（2026-10-17） |
| `t_finance_entity` | Finance 人工维护的会计法人主档，`/finance-entities/v1` 由 ledger Controller 提供；billing、tax 以 `finance_entity_id` 引用 | ledger | **API 读取**：billing / tax / payout 只存 ID，写入时经 `paytech-ledger-api` 校验存在性与 ACTIVE；不复制 | 无 |
| `t_business_change_record` / `t_business_change_version`（CCT 审计根） | 单表混存两核心的 record_type；client-master 还把它作为 Client 生命周期投影源 | 各核心各自持有（两边各一份） | **两边各一份**：client-master 实例只收 PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、PARTY_PROFILE、CLIENT_BUSINESS_PROFILE、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION；commercial 实例只收 AGREEMENT、COMMERCIAL_TERM_PLAN、COMMERCIAL_TERM_VERSION、PAYMENT_METHOD_PROFILE、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE。`BusinessChangeRecordRecordType` 与 `AggregateRootType` 枚举按此拆分；Checkout 解析所需的四类根版本由 client-master 随候选返回（S4），commercial 不读对方 BCR | 通用枚举 `BusinessChangeOperation`、`BusinessChangeSnapshotFormat` 两核心各保留一份副本，不新增 Foundation 模块（阶段 3 定稿，随整体方案确认） |
| `t_compensation_event` | 每个现有库一份，Handler 跨未来核心共用 | 每个业务库 | **每库一份**：16 个业务库各一张；Handler、`compensation.instance-id`、XXL 执行器按 app 拆分；测试按 event_type 的清理改为按库清理 | 无 |
| Finance 共享枚举 `AmountSource`、`EconomicsType`、`AutoInstructionType` | billing 组件代码与 ledger 规则行匹配键 | billing | 放入 `paytech-billing-api`；ledger 依赖 billing-api（流程层不依赖领域层内部包，符合 ArchUnit 规则） | **2026-10-19 起：** `AmountSource`、`AutoInstructionType` 已迁 `paytech-ledger-api`（值不变），`EconomicsType` 改名 billing-api `BillingFactType`，billing 行组件另用 `FactComponentType`；见[billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) |
| Finance 共享枚举 `OwnerType`、`OwnerRole`、`BalanceBucket`、`AccountIdentityRule`、`StandardAccountCodes`、`CurrencySpec` 值类型 | 跨 account / ledger / reconciliation / tax | account（账户身份规则）；`CurrencySpec` 值类型随 `t_currency_spec` 归 platform-support-api | 放入 `paytech-account-api` / `paytech-ledger-api`；S1 方案若把科目账户身份留在 ledger，则 `AccountIdentityRule` 改归 ledger-api | 随 S1 一并确认 |
| Finance DataObject `Account`、`LedgerBook`、`ChartAccount` | account 与 ledger 代码共用 | ledger | DataObject 不跨模块（Owner-JAR 边界）；account 经 ledger-api 的 Result 模型读取 | 无 |
| Payment 字符串代码 `ActionType` ↔ `ChannelOperation`、`ChannelExecutionOutcome` ↔ `ChannelOperationOutcome`、能力代码、`CaptureMode` 字面量 | web 两处映射，能力代码两处解析 | channel-connector 拥有 `ChannelOperation`、`ChannelOperationOutcome`、能力代码定义；payment 拥有 `ActionType`、`CaptureMode` | `paytech-channel-connector-api` 暴露枚举与能力代码常量；payment-core 做一次映射，`RouteServiceImpl` 改用 `CaptureMode` 枚举 | 无 |
| Payment ↔ card 关联 `InstrumentType.CARD` + `instrument_ref` | 字符串 ID | card 签发 ID | payment 仅存 ID；读卡经 S3 一次性授权票据 | 随 S3 确认 |
| Risk `RiskSubjectType`、`RiskSubjectKeys`、`RiskListView`（risk-control） | decision 17 文件引用 | risk-decision | 随 risk-control 并入 decision-core；compliance 不使用 | 无 |
| Risk `DecisionBusinessDomain`（含 DUE_DILIGENCE、SETTLEMENT、DISPUTE） | decision 内部 | risk-decision | 留在 decision-core；compliance / dispute 经 `paytech-risk-decision-api` 提交评估并只存 `evaluation_id` / `effect_id` | 无 |
| `risk-api` 单 JAR | 五方消费 | 拆为 `paytech-risk-decision-api` 与 `paytech-compliance-api` | C6 已定：一次性拆分，不保留旧坐标，第 6 步同分支改五个消费方；`ManualPayoutEligibilityApi` 归 decision-api；错误码枚举随 API 拆分 | 无 |
| CCT `CheckoutRecords`（451 行记录类）、`CheckoutErrorCode` | 混合两核心 | 按表拆到各 core；`CheckoutErrorCode` 归 commercial-api | client-master-api 新增候选 + 根版本 Result 模型（S4） | 无 |
| Commerce `CommerceCheckoutErrorCode` 中的 CUSTOMER_NOT_FOUND、INVALID_CONSUMER_REFERENCE、STORED_CARD_NOT_FOUND；`CustomerStatus`、`ConsumerReferencePolicy`、`CardSavingEligibility` | client-api 与 web 混放 | consumer | 移入 `paytech-consumer-api`；acquiring 的资格判断改为调用 consumer-api 并按 S6 降级为"不可存卡" | 无 |
| `customer_no` 作为 Payment `consumerReference`、`customer_id` 作为已存卡键 | 三方契约 | consumer | ID 与编号保持不变（S6）；payment / card 仅存值 | 无 |
| `entity_no`（OE 主键）在 commercial、acquiring、payment、channel-connector、reconciliation、tax 中出现 | 值复制 | client-master | 全部仅存值；校验经 `paytech-client-master-api`；不复制 OE 表 | 无 |

## 4. 每库表数小计与核对

### 4.1 来源表归属小计（255 张来源表各计一次）

| 来源 | 目标 | 表数 |
|---|---|---|
| Finance 84 | ledger（25 + t_account + t_finance_entity） | 27 |
| | platform-support（t_currency_spec，参考数据） | 1 |
| | account | 0（余额表由第 11 步新建） |
| | billing | 7 |
| | reconciliation | 17 |
| | tax | 23 |
| | payout | 7 |
| | 每库一份（t_compensation_event） | 1 |
| | 删除（t_command_idempotency） | 1 |
| | **小计** | **84** |
| Risk 85 | compliance（20 + C3 结算控制 3） | 23 |
| | risk-decision | 53 |
| | dispute | 7 |
| | 每库一份 | 1 |
| | 删除 | 1 |
| | **小计** | **85** |
| Payment 31 | channel-connector（5 有代码 + 7 Schema，含 S7 五张） | 12 |
| | payment（9 + S7 t_channel_transaction_ref） | 10 |
| | card | 4 |
| | dispute（S7 渠道争议 3 张） | 3 |
| | 每库一份 | 1 |
| | 删除 | 1 |
| | **小计** | **31** |
| CCT 38 | client-master（21 + C4 两张） | 23 |
| | commercial | 11 |
| | 两边各一份（BCR 2 张） | 2 |
| | 每库一份 | 1 |
| | 删除 | 1 |
| | **小计** | **38** |
| Commerce 17 | acquiring（transaction 7 + checkout 5 + command 1） | 13 |
| | consumer（t_customer） | 1 |
| | client-master（t_client_stored_card_setting） | 1 |
| | 每库一份 | 1 |
| | 删除 | 1 |
| | **小计** | **17** |
| **合计** | | **255** = 84 + 85 + 31 + 38 + 17 |

核对：第 2 节五张表的行数分别为 84、85、31、38、17，与五份 Schema 的 `CREATE TABLE` 数一致；每张来源表在第 2 节恰出现一次。

### 4.2 搬迁后每库物理表数（含复制与每库一份，不含删除，不含第 11、12 步新表）

| 目标库 | 归属表 | 复制 / 基础设施 | 物理表数 |
|---|---|---|---|
| mor_ledger | 27 | t_currency_spec 只读副本、t_compensation_event | 29 |
| mor_account | 0 | t_compensation_event | 1 |
| mor_billing | 7 | t_currency_spec 只读副本、t_compensation_event | 9 |
| mor_reconciliation | 17 | t_compensation_event | 18 |
| mor_tax | 23 | t_compensation_event | 24 |
| mor_payout | 7 | t_compensation_event | 8 |
| mor_compliance | 23 | t_compensation_event | 24 |
| mor_risk_decision | 53 | t_compensation_event | 54 |
| mor_dispute | 7 + 3 | t_compensation_event | 11 |
| mor_channel_connector | 12 | t_compensation_event | 13 |
| mor_payment | 10 | t_compensation_event | 11 |
| mor_card | 4 | t_compensation_event | 5 |
| mor_client_master | 24（21 + 法律文档 2 + 已存卡开关 1） | BCR 2 张、t_compensation_event | 27 |
| mor_commercial | 11 | BCR 2 张、t_compensation_event | 14 |
| mor_acquiring | 13 | t_compensation_event | 14 |
| mor_consumer | 1 | t_compensation_event | 2 |
| mor_platform | 1（t_currency_spec） | —（Platform 自有基础设施表不计） | 1 |
| **合计** | **243** | **22** | **265** |

核对：255 − 5（删除 `t_command_idempotency`）= 250；`t_compensation_event` 来源 5 张、目标 16 张，+11；BCR 两边各一份 +2；`t_currency_spec` 只读副本 +2（ledger、billing）；250 + 11 + 2 + 2 = 265，与上表合计一致。Doris 库 `mor_data_service`、`mor_vault`、`mor_fx`、`mor_workflow_approval` 不含本次搬迁的表，不计入；`mor_platform` 只计入迁入的 `t_currency_spec`。

> **2026-10-19 取代说明：** 上表是第 07 步搬迁时的口径。计费事实重构后 `mor_billing` 为 11 张（删经济事实三表与渠道成本四表，新增计费事实五表、工作日日历两表、消息消费两表），`mor_channel_connector` 为 17 张（迁入渠道成本四表）；归属以 [billing 计费事实决定](../../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md) 与 `paytech/tools/arch-rules/scripts/generate-table-ownership.mjs` 的 step-removed / step-added 登记为准，本表数字不改。

### 4.3 各库对应的验收前置

- 每个目标库需要：独立 PDManer 规范模型 `paytech/<service>/docs/database/<service>.pdma.json`、独立 Schema SQL、独立初始化 DML（一个服务一份）、按库的测试前置检查（替代 `RiskDatabasePrecondition` 固定 85 表、Finance 清理器 28 表等）。
- 0 张归属表的 mor_account 在第 11 步前只有 `t_compensation_event`；account-app 在第 7 步仍可启动并通过 `/balances/v1` 测试的前提是 S1 方案允许过渡期经 ledger-api 读余额，这一点在 S1 文档中说明。
- 原标注待决 / 待确认的 21 张表已于 2026-10-17 全部定案：S1 1 张、S2 3 张、S5 2 张按 C1 / C3 / C4；S7 9 张按建议；`t_cash_account*` 3 张、`t_order_legal`、`t_order_refund_position` 按建议；`t_client_stored_card_setting` 改归 client-master；`t_currency_spec` 改归 platform-support。第 4.1、4.2 节计数已按最终归属更新。
