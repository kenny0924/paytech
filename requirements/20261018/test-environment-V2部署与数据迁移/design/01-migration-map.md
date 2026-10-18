# 01 · mor-test 旧库 → V2 库逐表映射与 Schema 升级差异

- 状态：阶段 1 评审稿（2026-10-18）。差异全部由演练库实测得出：mor-test 转储（2026-10-18）导入一次性 MySQL 8.4.11，与各服务评审 Schema 载入的参照库同实例比对 `information_schema`（[schema_diff.py](../scripts/schema_diff.py)），种子行按主键比对（[seed_diff.py](../scripts/seed_diff.py)）。不凭记忆。
- 归属依据：[table-ownership.yaml](../../../../paytech/tools/arch-rules/src/main/resources/table-ownership.yaml)、[design/03](../../../20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md) 第 2、3 节，及第 02a / 02d / 03 / 04 / 06 步报告的更名、删除列与过渡说明。
- 执行物：[sql/](../sql/)（评审 SQL）与 [migrate.sh](../scripts/migrate.sh)。行数为转储时刻的精确 `COUNT(*)`，阶段 2 以执行时刻的实际行数核对（40）。

## 1. 结论

1. **结构几乎不变。** 202 张旧表与 V2 目标表逐列比对：类型、可空、默认值、排序规则、索引全部一致；只有 `t_commerce_order` 少两列（第 03 步 C7 删除）。V2 目标表没有旧表缺失的列，**不需要任何 NOT NULL 回填**（生成器对此做了断言）。
2. **Platform、Vault 原库升级。** `mor_platform` 只缺 `t_currency_spec`（56 → 57 表）；`mor_vault` 结构零差异。两库的其余变化都是数据：Gateway Target 发现名、Vault 调用方规则与数据类型。
3. **三个旧库按归属拆入 6 个 V2 库。** 迁移方式是同实例 `INSERT … SELECT` 显式列清单，旧库只读不改；两张共享基础表按类型拆分（补偿事件、BCR）。
4. **V2 不承载的旧数据不丢弃，进归档库** `mor_v2_migration_archive`：三库的 `t_command_idempotency`、`t_commerce_order` 的两个删除列原值、`mor_risk` 的 7 张争议表。
5. **行数守恒（演练实测）：** 三个拆分旧库 4,210 行 = V2 六库 4,110 行 + 归档幂等记录 100 行 + 争议表 0 行。

| 旧库 | 表 | 精确行数 | V2 去向 |
|---|---:|---:|---|
| mor_platform | 56 | 23,272 | `mor_platform` 原库（+ `t_currency_spec` → 57 表） |
| mor_vault | 6 | 1,775 | `mor_vault` 原库（Schema 不变） |
| mor_cct | 38 | 513 | `mor_client_master` 509 行；`mor_commercial` 0 行；归档 4 行 |
| mor_commerce | 17 | 166 | `mor_acquiring` 166 行；`mor_consumer` 0 行；`mor_client_master` 0 行 |
| mor_risk | 85 | 3,531 | `mor_risk_decision` 3,060 行；`mor_compliance` 375 行；归档 96 行 |

需求盘点表中的"约行数"取自 `information_schema.table_rows` 估算，与上表精确值不同属正常。

## 2. 拆分、更名与删除规则

| 项 | 规则 | 依据 |
|---|---|---|
| 库更名 | `mor_cct` → `mor_client_master` + `mor_commercial`；`mor_commerce` → `mor_acquiring` + `mor_consumer`；`mor_risk` → `mor_risk_decision` + `mor_compliance`（+ dispute） | design/03 §1.2 |
| 表更名 | 本次迁移范围内**没有**表更名（四个更名表都在 Finance，mor-test 无 Finance 数据） | table-ownership.yaml 头注释 |
| `t_compensation_event` | 按 `event_type` 归各服务的 `compensation-event-catalog.yaml`：acquiring `CLIENT_WEBHOOK_REQUESTED`、`SUBMISSION_EVENT_TYPE`；consumer `STORED_CARD_INVALIDATION`；client-master `CLIENT_REVIEW_*`、`CLIENT_TENANT_PROJECTION`；compliance `CLIENT_REVIEW_RESULT`；risk-decision `DECISION_SHADOW_RUN`。未登记类型由 40 的覆盖核对拦截 | design/03 §3；第 03、06 步 |
| `t_business_change_record` / `_version` | 按 `record_type` 拆：client-master 14 类、commercial 4 类（取自两个服务的 `BusinessChangeRecordRecordType` 枚举）；版本表随所属记录走 | design/03 §3.3；第 04 步偏差第 7 项 |
| `t_client_stored_card_setting` | `mor_commerce` → `mor_client_master` | design/03 §2.5 |
| `t_customer` | `mor_commerce` → `mor_consumer` | design/03 §2.5 |
| `t_commerce_order.context_format`、`preparation_command_id` | V2 已删除，不迁移；原值按 `order_id` 归档。转储中 `preparation_command_id` 全为空，`context_format` 全为同一取值 | 第 03 步 C7 |
| `t_command_idempotency` | V2 全部删除（S10，HTTP 幂等由 Gateway 承担）；整表归档。Platform 的同名表保留（V2 Platform Schema 仍有） | design/03 §2；AGENTS 请求幂等决定 |
| 争议 7 表 | 归 dispute（design/03 §2.2），但 mor-test 不部署 dispute-service、compose 无对应服务；转储中 0 行。整表结构与数据归档，不建 `mor_dispute` | 保守方案，见报告待确认 |
| 新增基础表 | `t_consumed_event`、`t_consumed_aggregate_version`（client-master、acquiring）与 Payment / Finance 等 8 个空库按 V2 Schema 新建，不迁入数据 | 各服务 Schema |
| 初始化 DML | 8 个新建空库执行各自 `*-initial-data.sql`；6 个迁移目标库不执行（client-master / acquiring / consumer 的初始化 DML 没有 INSERT；risk-decision 的目录种子已在旧库，随迁移带入，61 个种子主键由 migrate.sh 核对齐全） | initialize.py 同口径 |

## 3. 跨库引用

V2 各库之间没有物理外键，跨库关系只存值（design/03 §3）。迁移不改任何 ID，因此所有跨库引用保持成立：

- `client_id`、`entity_no`、`customer_id`、`order_id` 等在 acquiring / consumer / client-master / commercial 之间原值复制；
- compliance 案件的 `source_application_id` 指向 client-master 的申请 ID，`decision_evaluation_id` 指向 risk-decision 的评估 ID，均原值保留；
- Platform 的租户投影、文件归属（`t_file_owner_ref`）引用 client-master 的 Client / 申请 ID，Platform 原库不动；
- Vault Token 在 client-master（`t_party_contact`）与 Platform 中原值保留，Vault 记录不变，Reveal 只需调用方规则（见第 5 节）。

Snowflake：V2 各服务的 worker ID 与旧服务不同或相同都不会与历史 ID 冲突（历史 ID 的时间段已过去）。

## 4. 逐表映射

### 4.1 拆分的三个旧库

#### mor_cct（38 表）

| 旧表 | 行数 | V2 去向 | 说明 |
|---|---:|---|---|
| `t_agreement` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_agreement_document_ref` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_agreement_operating_entity` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_agreement_party` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_application_document` | 35 | `mor_client_master` | 同名搬迁，列全同 |
| `t_business_change_record` | 32 | `mor_client_master`（拆分）、`mor_commercial`（拆分） | 按 record_type 拆分 |
| `t_business_change_version` | 57 | `mor_client_master`（拆分）、`mor_commercial`（拆分） | 按 record_type 拆分 |
| `t_client` | 32 | `mor_client_master` | 同名搬迁，列全同 |
| `t_client_application` | 34 | `mor_client_master` | 同名搬迁，列全同 |
| `t_client_business_classification` | 25 | `mor_client_master` | 同名搬迁，列全同 |
| `t_client_business_profile` | 32 | `mor_client_master` | 同名搬迁，列全同 |
| `t_client_entity_affiliation` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_command_idempotency` | 4 | `mor_v2_migration_archive.cct_t_command_idempotency` | 归档（V2 删除，S10） |
| `t_commercial_term_component` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_commercial_term_component_band` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_commercial_term_plan` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_commercial_term_price` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_commercial_term_version` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_compensation_event` | 0 | `mor_client_master`（拆分） | 按 event_type 拆分 |
| `t_consumer_legal_document` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_entity_source_relation` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_individual_profile` | 32 | `mor_client_master` | 同名搬迁，列全同 |
| `t_local_partner` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_operating_entity` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_operating_entity_application` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_operating_entity_scope` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_organization_profile` | 2 | `mor_client_master` | 同名搬迁，列全同 |
| `t_partner_application` | 0 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party` | 34 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_address` | 34 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_contact` | 58 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_identity_key` | 32 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_profile` | 34 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_profile_identifier` | 34 | `mor_client_master` | 同名搬迁，列全同 |
| `t_party_relation` | 2 | `mor_client_master` | 同名搬迁，列全同 |
| `t_payment_method_profile` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_payment_method_profile_cond` | 0 | `mor_commercial` | 同名搬迁，列全同 |
| `t_seller_disclosure` | 0 | `mor_client_master` | 同名搬迁，列全同 |

#### mor_commerce（17 表）

| 旧表 | 行数 | V2 去向 | 说明 |
|---|---:|---|---|
| `t_checkout_network_evidence` | 0 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_checkout_session` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_client_stored_card_setting` | 0 | `mor_client_master` | 改归 client-master |
| `t_command_idempotency` | 0 | `mor_v2_migration_archive.commerce_t_command_idempotency` | 归档（V2 删除，S10） |
| `t_commerce_order` | 17 | `mor_acquiring` | 不迁移列 `context_format`, `preparation_command_id`（原值归档） |
| `t_compensation_event` | 5 | `mor_acquiring`（拆分）、`mor_consumer`（拆分） | 按 event_type 拆分 |
| `t_consumer_acceptance` | 0 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_consumer_context` | 0 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_consumer_contract` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_customer` | 0 | `mor_consumer` | 改归 consumer |
| `t_order_context` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_order_funds_availability_projection` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_order_item` | 32 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_order_legal` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_order_payment_projection` | 5 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_order_refund_position` | 17 | `mor_acquiring` | 同名搬迁，列全同 |
| `t_refund_request` | 5 | `mor_acquiring` | 同名搬迁，列全同 |

#### mor_risk（85 表）

| 旧表 | 行数 | V2 去向 | 说明 |
|---|---:|---|---|
| `t_action_effect` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_action_parameter_definition` | 4 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_action_version` | 8 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_checkout_input_snapshot` | 17 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_command_idempotency` | 96 | `mor_v2_migration_archive.risk_t_command_idempotency` | 归档（V2 删除，S10） |
| `t_compensation_event` | 0 | `mor_risk_decision`（拆分）、`mor_compliance`（拆分） | 按 event_type 拆分 |
| `t_dd_case_classification` | 25 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_case_dependency` | 4 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_case_state_change` | 71 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_case_subject` | 38 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_check` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_evidence_ref` | 38 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_party_snapshot` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_program` | 3 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_program_check` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_program_requirement` | 10 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_program_screening` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_program_version` | 3 | `mor_compliance` | 同名搬迁，列全同 |
| `t_dd_requirement` | 118 | `mor_compliance` | 同名搬迁，列全同 |
| `t_decision_evaluation` | 22 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_flow` | 4 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_flow_edge` | 21 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_flow_end_output` | 2 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_flow_node` | 27 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_flow_version` | 6 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point` | 2 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_feature` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_input_field` | 304 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_limit_binding` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_obligation` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_rule_set` | 33 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_point_version` | 35 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_decision_release` | 13 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_dispute_case` | 0 | `mor_v2_migration_archive.risk_t_dispute_case` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_evidence_package` | 0 | `mor_v2_migration_archive.risk_t_dispute_evidence_package` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_evidence_ref` | 0 | `mor_v2_migration_archive.risk_t_dispute_evidence_ref` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_liability_decision` | 0 | `mor_v2_migration_archive.risk_t_dispute_liability_decision` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_outcome` | 0 | `mor_v2_migration_archive.risk_t_dispute_outcome` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_state_change` | 0 | `mor_v2_migration_archive.risk_t_dispute_state_change` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_dispute_submission_request` | 0 | `mor_v2_migration_archive.risk_t_dispute_submission_request` | 归档（归 dispute；mor-test 不部署 dispute-service） |
| `t_due_diligence_case` | 38 | `mor_compliance` | 同名搬迁，列全同 |
| `t_evaluation_action_intent` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_evaluation_feature_result` | 10 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_evaluation_flow_trace` | 38 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_evaluation_rule_trace` | 32 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_feature_dependency` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_feature_version` | 2 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_group_state_change` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_movement` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_position` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_reservation` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_reservation_group` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_limit_version` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_list_feature_definition` | 1 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_payment_attempt_fact` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_qualification_decision` | 27 | `mor_compliance` | 同名搬迁，列全同 |
| `t_qualification_state_change` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_release_audit_event` | 118 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_release_shadow_run` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_list` | 1 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_list_entry` | 1 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_restriction` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_rule` | 425 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_variable` | 35 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_risk_variable_member` | 12 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_role_qualification` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_rule_action` | 432 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_action_parameter` | 24 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_node` | 425 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_operand` | 850 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_reject_reason` | 4 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_set` | 30 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_set_version` | 38 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_rule_subject_tag` | 3 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_screening_hit` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_screening_resolution` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_screening_run` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_settle_control_state_change` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_settlement_control` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_settlement_control_scope` | 0 | `mor_compliance` | 同名搬迁，列全同 |
| `t_settlement_risk_assessment` | 0 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_transaction_risk_assessment` | 5 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_velocity_event` | 18 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_velocity_event_subject` | 57 | `mor_risk_decision` | 同名搬迁，列全同 |
| `t_velocity_feature_definition` | 1 | `mor_risk_decision` | 同名搬迁，列全同 |

### 4.2 原库升级的两个库

`mor_platform` 56 张、`mor_vault` 6 张既有表与 V2 Schema 逐列逐索引一致，原地保留，数据不动（第 5 节的数据变更除外）。行数见演练报告附录。

## 5. Platform / Vault 升级差异清单

### 5.1 `mor_platform`（[20-platform-upgrade.sql](../sql/20-platform-upgrade.sql)）

| 类别 | 差异 | 处理 |
|---|---|---|
| 表 | 缺 `t_currency_spec`（第 02a 步 D41） | 逐字取自 `platform-support-schema.sql` 建表；写 USD 行（取自 initial-data） |
| 列 / 索引 | 56 张表 0 差异（含 `changes/` 下 2026-09-04 至 2026-10-17 的 6 个增量，mor-test 已全部执行） | 无 |
| 种子行 | 与 `platform-support-initial-data.sql` 按主键比：除 USD 外不缺行。`t_console_resource`、`t_gateway_route`、`t_role` 等的 `status` / `version` / 操作人差异是 mor-test 运营变更；`t_role_permission` 缺的 5 个种子主键在库中以其他主键存在同一角色 + 权限授权；`t_notification_provider` 已由运营后台录入账号 | 保留现值，不覆盖 |
| Gateway Target | `commerce-client-api` 的 `discovery_service_name=commerce-service`（第 03 步改名为 acquiring-service） | 改为 `acquiring-service`，版本 +1；9 条 CLIENT_API 路由、Scope、Guard 不变 |
| consumer Target | 第 03 步报告建议新增；mor-test 没有登记 Customer 路由 | 不新增（无路由可挂） |
| 历史数据中的旧服务名 | `t_webhook_event` 16 行冻结来源 `commerce-service`；审计属性含旧服务名 | 历史事实，不改 |
| 部署配置 | Webhook 调用方白名单 `acquiring-service`、Operations / Client 认证、补偿执行器 | 已在 `compose.applications.yaml`，不涉及数据 |

### 5.2 `mor_vault`（[21-vault-upgrade.sql](../sql/21-vault-upgrade.sql)）

| 类别 | 差异 | 处理 |
|---|---|---|
| 表 / 列 / 索引 | 6 张表 0 差异 | 无 |
| 数据类型 | 缺 `PAYMENT_CARD_DEK`（card-service 存卡前置） | 新建：REFERENCE、SECRET、TEXT、人工加解密关闭 |
| 访问规则 | 现有调用方 `client-contract-service`（4 条）、`platform-support-service`（9 条）与测试调用方；V2 改名 / 拆出 `client-master-service`、`card-service`、`channel-connector-service` 无规则 | 新增 7 条（同第 14 步本机组合）；旧调用方规则保留 |
| 策略版本 | 当前 15 | 写一条 SEED 变更记录，版本 16 |

## 6. 不在迁移 SQL 中、由 runbook 处理的配置

| 项 | 内容 |
|---|---|
| XXL-Job | [50-xxl-job-acquiring.sql](../sql/50-xxl-job-acquiring.sql)：新建执行器 `acquiring-service`，复制 commerce-service 的补偿扫描任务（先停），停 commerce-service 任务；acquiring 健康后在 Admin 启动 |
| RabbitMQ | 旧队列 `cg_commerce_payment_result`、`cg_cct_client_review`（及 DLQ）排空后删除；一致性哈希插件需按挂载重建容器 |
| Nacos | `mor-test` 命名空间与服务账号沿用；V2 服务名自动注册，旧实例为临时实例 |
| MySQL 连接数 | `--max-connections=400` 只在重建 mysql 容器后生效 |
