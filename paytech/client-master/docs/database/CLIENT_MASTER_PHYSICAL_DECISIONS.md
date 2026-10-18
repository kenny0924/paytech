# Client Master 物理数据库决策

> **2026-10-17 服务拆分：** 本文由原 `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` 按[模块与表归属](../../../../requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md) §2.4 拆出；决策正文逐字保留，只调整标题、归属说明、相对链接与本库生成统计。client-master（`mor_client_master`）拥有 29 张表（第 08 步加入 paytech-messaging 消费端 `consumed_event`、`consumed_aggregate_version` 两张表，见第 9 节）：Client & Contract Catalog 中 Party、角色、入驻、OE Scope/Affiliation/Source、Consumer Legal 与 Seller Disclosure 共 23 张，本库独立副本 `business_change_record`、`business_change_version`、`compensation_event`，以及从 Commerce 原样迁入的 `client_stored_card_setting`。Agreement 与定价 14 张表见 [Commercial 物理数据库决策](../../../commercial/docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md)。`command_idempotency` 按决定 S10 删除，不进入任何模型或 Schema。手工 Schema SQL 为 [client-master-schema.sql](./sql/client-master-schema.sql)（由 [generate-oe-client-schemas.mjs](../../../../scripts/pdmaner/generate-oe-client-schemas.mjs) 从模型生成），初始化 DML 为 [client-master-initial-data.sql](./sql/client-master-initial-data.sql)。

> **2026-09-14 Client 入驻修订：** Client 申请使用单份资料和 current_submission_id；Party/Profile/Relation 稳定标识及 Checkout 实际消费的批准 Hash 保留。本文旧的 Profile Version、Base/Target、Client Submission Hash 与 Relation Version 段落仅作前身设计记录；Partner/OE/Agreement 的共享引用不自动合并。完整合同见[数据设计](../../../../requirements/20260914/client-console-注册与客户入驻/data-design.md)和[迁移方案](../../../../requirements/20260914/client-console-注册与客户入驻/migration-plan.md)。当前模型、验证和执行状态见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

> **2026-09-11 模型切片：** 当前物理模型采用 [OE / Client / Pricing 四系统决定](../../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)。本页历史正文中的数字 OE、Assignment、独立 Acceptance、Client 多 OE 合同与 Agreement-OE 结算条款由该决定精确取代；本页摘要描述当前模型产物，运行代码、SQL 执行及待迁移状态仅见 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。Risk 本期不动。

最后更新：2026-09-14
状态：本期模型、完整人工 SQL 与自动门禁已同步；GUI 提示及 Java/SQL 执行/部署状态见当前实现台账的 OE-CLIENT-MODEL-01。

> **2026-09-05 横切规范更新：** 下文旧 `compensation_event`、Vault 元数据及物理 CHECK/FK 只描述当前生成模型，不再是新增实现目标。按 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 的 COMP-01、VAULT-01、DB-01 完成同步迁移前，不得复制旧字段，也不得声称模型已经迁移。

## 1. 文档定位与阶段边界

本文记录 `client-contract-service` 从冻结逻辑模型到 PDManer 4.9.3 物理模型的确定性映射。业务事实、状态机和不变量以 [Client & Contract 数据库设计](../../../../agent-docs/database/CLIENT_CONTRACT_DATABASE.md) 为权威来源；Payment Method Profile 与组合定价以[当前确认决策](../../../../agent-docs/decisions/2026-09-10-cct-payment-method-profile-pricing.md)为准；现实主体、流程与逐表字段示例见 [当前现实场景](../../../../agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md)；实时资格与 Retention 延期见 [当前设计](../../../../agent-docs/plans/2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)；Agreement/Legal 边界见 [七表精简设计](../../../../agent-docs/plans/2026-08-22-cct-agreement-legal-simplification-design.md)；自动分配边界见 [CCT 自动分配四表精简设计](../../../../agent-docs/plans/2026-08-22-cct-assignment-simplification-design.md)；通用历史语义见 [CCT 通用行级变更历史设计](../../../../agent-docs/plans/2026-08-20-cct-row-history-design.md)；全局边界以 [数据库设计总览](../../../../agent-docs/database/README.md)、[PDManer 数据库设计规范](../../../../agent-docs/DATABASE_DESIGN_GUIDE.md) 和 [可靠异步执行规范](../../../../agent-docs/COMPENSATION_EVENT_GUIDE.md) 为准。

目标模型位于 [client-master.pdma.json](./client-master.pdma.json)，只能由 [生成器](../../../../scripts/pdmaner/generate-client-master-model.mjs)（共享逻辑见 [client-contract-model-core.mjs](../../../../scripts/pdmaner/client-contract-model-core.mjs)）从共享模板、38 表 Catalog 中本库拥有的表和显式字段元数据确定性生成，并由 [模型契约测试](../../../../scripts/pdmaner/client-contract-model.test.mjs) 校验。当前生成工件已经同步；37、66、57、55、51、48、44 表模型的统计量或 Digest 只属于历史基线，不得作为当前验收证据，也不得直接手改 PDManer JSON 绕过生成器。

本阶段只批准设计，不包含：

- MySQL 实例、Schema、真实连接或对共享环境执行 DDL；
- Flyway/Liquibase 或应用启动时 Schema 迁移；后续数据库实现只允许审核后的手工 Schema SQL；
- Java DO、Mapper、Repository 或服务模块；
- 其他五个核心系统的并发策略或 PDManer 模型；它们的物理模型仍未获授权。

## 2. 前身设计基线（受顶部后继决定取代）

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

本节历史基线为 38 张表、428 个 Catalog 完整有序字段，分组为 `9+10+2+4+2+7+2+2`。批准的精简覆盖以下领域：

1. Client 入驻与资料变更统一为 `client_application`，`requested_by_account_id` 是外部稳定引用，ONBOARDING 必填；
2. Local Partner 入驻使用 `partner_application`，并在同一 CCT 事务创建或复用 PENDING Local Partner、Organization Party 与 Target Party Profile；
3. 三类申请材料统一为 `application_document`，三个本库 Parent 逻辑关系中恰好一个非空，并由写入事务锁定和校验实际 Parent；
4. Agreement 采用 `agreement`、`agreement_signatory`、`agreement_document_ref`、`agreement_acceptance` 与 `agreement_operating_entity` 五张直接事实表，一份已签内容对应一个稳定 Agreement ID；
5. 三类 Consumer Legal 语义直接落在 `consumer_legal_document`，Seller 公示直接落在 `seller_disclosure`，内容变化创建新稳定 ID；
6. 删除四张持久化 Eligibility 表；CCT-I10/I13 在使用点实时查询当前事实，不保存资格结果；
7. 删除四张通用 Retention 表；MVP 数据默认保留，不自动归档、匿名化或销毁；
8. 删除 `entity_source_ownership_path`、`entity_source_path_edge` 及两个 `ownership_path_id`；Client 来源改为 `SAME_PARTY|VERIFIED_ASSOCIATION` 的审核结论；
9. Entity Assignment 从八表精简为 `entity_source_relation`、`client_entity_affiliation`、`entity_assignment_rule`、`client_entity_assignment` 四表；删除 Policy/Policy Version/Binding/Assignment Version 以及 Assignment Hash、Revision 与派生 Owner Key；
10. Client Pricing、Partner Share 与 Platform Service Charge 复用全局 `payment_method_profile`；Commercial Term 使用 Plan → Version → 准确 OE Price → Component → Band，删除固定维度的 `commercial_term_scope`。Profile Condition 用一值一行的 `dimension_code + condition_value`，同维度 OR、跨维度 AND；价格支持百分比、固定金额、阶梯及多组件相加。
11. BCR 从“每条结构化业务行”收敛为20类 Record Type 白名单；Payment Method Profile 是自身 Root，Condition、Price、Component、Band 等冻结 Child 不重复审计，Child `current_version` 只作 Root 版本指针，不再执行独立 CAS。

上一代 66、57、55、51、48 和 44 表均是历史工件状态，不能继续作为当前表清单、关系图或统计口径。

数据库基线保持：

| 项目 | 决策 |
|---|---|
| 数据库 | MySQL 8.0 / InnoDB |
| 物理命名 | 表使用 `t_`，唯一索引使用 `uk_`，普通索引使用 `idx_`；中文 `defName` 不添加技术前缀 |
| 字符集与排序规则 | `utf8mb4` / `utf8mb4_0900_bin`；大小写折叠使用独立规范化键 |
| 时区 | 数据库存 UTC；应用边界使用带 Offset 的 ISO 8601 |
| 主键 | 服务侧 Snowflake `BIGINT`，Java/MyBatis `Long`，禁止 `AUTO_INCREMENT` |
| 金额与比率 | `DECIMAL(24,8)` / `DECIMAL(18,10)`，Java/MyBatis `BigDecimal` |
| 国家与币种 | ISO 3166-1 alpha-2 `CHAR(2)` / ISO 4217 alpha `CHAR(3)` |
| 枚举 | 稳定业务码字符串，禁止 Java ordinal |
| 删除 | 不增加通用 `deleted`；使用明确业务终态。MVP 默认保留数据，不执行通用自动归档、匿名化或销毁 |

每张表恰好一个非自增 `BIGINT` 主键，并默认包含非空 `create_time`、`update_time`。不可变 `business_change_version` 只保留 `create_time`；其他不可变追加事实的 `update_time` 初始等于 `create_time`，之后不得覆盖。

## 3. 类型映射、中文元数据与 Hash

### 3.1 日期与时间类型分离

- 瞬时时间继续使用共享模板的 `date` 映射：MySQL `DATETIME(6)`，Java/MyBatis `Instant`。
- 日期事实使用派生 `localDate` 映射：MySQL `DATE`，Java/MyBatis `LocalDate`。38 表目标中仅 `individual_profile.birth_date` 与 `organization_profile.incorporation_date` 使用该映射。
- 生成器只在派生 CCT 模型中加入 `localDate` 映射；共享模板保持字节级不变。

### 3.2 字段、内容与显示元数据

- 对外编号、幂等键、事件 Key、调用方 Scope 和 Vault Token 使用有界 `VARCHAR`；Token 默认 `VARCHAR(128)`。
- SHA-256 Hash、Blind Index 与 Metadata Hash 使用 `CHAR(64)`；JSON 内容最大 64 KiB，不得包含 Vault Reveal 原值、Secret 或卡数据。
- 每张表必须有简洁、唯一的中文 `defName`；每个字段必须有可独立理解的中文 `defName` 和 `comment`。禁止英文拆词兜底、表名机械前缀或空泛说明。
- 闭合值域列出完整允许值；开放目录码注明权威来源；Boolean/Slot 注释写清 `0/1/NULL` 状态矩阵。缺少显式中文元数据时生成必须失败。

### 3.3 Hash 边界与三个固定 Submission Domain

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

三个申请提交 Hash 使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 字符十六进制文本：

- `CODE-MOR:CCT:CLIENT_APPLICATION_SUBMISSION:1`；
- `CODE-MOR:CCT:PARTNER_APPLICATION_SUBMISSION:1`；
- `CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:2`。

`BIGINT` 编码为无前导零十进制字符串；Decimal 去无意义尾零、禁止指数，零固定为 `"0"`；固定属性为空时写 JSON `null` 而非省略。属性、算法或 Domain 变化必须提升 `hash_schema_version` 和 Domain Tag，旧版本永不覆盖重算。当前 Node 固定 Fixture 是跨语言真值；未来 Java 实现必须复用同一批 Fixture。

Payment Method Profile、Condition 以及 Commercial Term 的 Version、Price、Component、Band 均保存完整结构化字段，不保存 `hash_schema_version/content_hash/scope_hash/canonical_hash`，也不以派生摘要改名恢复。ACTIVE Profile 的 Code、Payment Method 与 Condition 冻结；已批准 Version 的 Price、Component 与 Band 冻结，变更分别创建新 Profile Code 或下一 Term Version。`agreement_document_ref.content_hash` 与 `consumer_legal_document.content_hash` 只保存 Platform Support 返回的文件正文 SHA-256，并分别与 `file_id`、`content_file_id` 成组冻结；它们不是业务聚合 Hash，不需要 `hash_schema_version`。Seller Disclosure 不保存 Content Hash，历史订单由 Commerce 的不可变 Snapshot Hash 覆盖展示值。

CCT-I13 的 `CODE-MOR:CCT:COMMERCIAL_CONTEXT:5` 与 CCT-I14 Scope 查询仅是迁移期间的 legacy Wire。它们继续解释历史 V5 数据，但不能用于定义新 Price、推导晚到 Payment 事实或把 Scope ID 改名为 Price ID。新接口的解析时点、输入事实、返回证据与 Hash Domain 必须另行设计。

### 3.4 行业、MCC 与订单分类冻结

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

`client_business_classification` 是 Business Profile 已批准的准确交易分类表，字段为 `classification_id`、`business_profile_id`、`industry_code`、`mcc_code`、`primary_slot`、`create_time`、`update_time`。`uk_biz_class_pair(business_profile_id,industry_code,mcc_code)` 禁止同一 Profile 重复组合，`uk_biz_class_primary(business_profile_id,primary_slot)` 保证最多一组 Primary（Primary 为 `1`，其余为 `NULL`）。`industry_code` 是平台开放行业目录代码；`mcc_code` 必须是存在于批准 MCC 目录、保留前导零的四位数字字符串。该表不允许 `*`，也不保存子行 `canonical_hash`。

Business Profile 的 Hash Schema V2 在封存时将 Profile 头字段与完整 Classification 集合共同输入 `content_hash`：集合按 `industry_code,mcc_code` 的 UTF-8 字节序稳定排序，每项分别输入 `industry_code`、`mcc_code`、`primary_slot`。既有 Profile 保持原 `hash_schema_version` 与 `content_hash`，不得因 V2 输入形状升级而重算。

Assignment 的行业/MCC 继续使用准确结构化列；Payment Method Profile 则使用受控 Dimension Code 扩展支付维度：

| 模型 | 维度规则 |
|---|---|
| `client_entity_affiliation`、`client_entity_assignment`、`entity_assignment_rule` | 必须保存准确代码；不支持 `NULL/*/GLOBAL`，不保存 `scope_hash/content_hash/hash_schema_version` |
| `payment_method_profile_cond` | 每行保存一个 `dimension_code + condition_value`；同维度多行 OR、不同维度 AND，缺少维度表示不限制；不保存 `*`、CSV、Operator、Range 或 Definition 子表 |
| `commercial_term_price` | 必须保存准确非空 `operating_entity_id` 与稳定 `payment_method_profile_code`；旧 NULL-OE Scope 在迁移时按 Agreement 覆盖展开，不能在新模型中继续表达通配 |

Payment Method 是 Payment 拥有的稳定代码目录，CCT 不建立主表；Profile 条件中的 Dimension Code 与规范化值由版本化应用/API 合同控制。Business Classification、Assignment 四表、Agreement 的 OE 覆盖以及所有跨系统冻结证据必须始终使用准确值。

新价格候选先按 `payment_method_code` 与 Profile 的全部 Condition 匹配 Payment 标准化事实；多个候选命中时，先选择不同 `dimension_code` 数量最多的 Profile，再选择最小 `priority_no`，零命中、事实缺失或仍有歧义均 Fail Closed。卡资金类型、卡等级与实际 3DS 分类由 Payment 产生，CCT 不读取 PAN/BIN 或 Provider 原始 Payload，也不得用缺少 Challenge 记录推断非 3DS。跨系统新 Wire 尚未批准，当前物理模型不声称已完成交易链路接入。

## 4. 并发、版本与通用历史

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

CCT 只为明确需要审计、追责或历史留档的业务对象建立 BCR。白名单内 Root 的唯一聚合 CAS 坐标是 `business_change_record.current_version`；外部写命令必须携带 `expected_change_version`。白名单内 Child 的 `current_version` 只表示最近一次审计快照采用的 Root 版本，不是独立 CAS Token。机械 Root 版本、九个 Current Pointer、材料版本号、主/备标记以及多层权属路径结构均不进入当前模型。

`record_type` 的20类闭合白名单为 `PARTY`、`CLIENT`、`LOCAL_PARTNER`、`OPERATING_ENTITY`、`PARTY_PROFILE`、`CLIENT_BUSINESS_PROFILE`、`COMMERCIAL_TERM_VERSION`、`CLIENT_APPLICATION`、`PARTNER_APPLICATION`、`OPERATING_ENTITY_APPLICATION`、`PARTY_RELATION`、`CLIENT_ENTITY_AFFILIATION`、`ENTITY_ASSIGNMENT_RULE`、`CLIENT_ENTITY_ASSIGNMENT`、`ENTITY_SOURCE_RELATION`、`AGREEMENT`、`CONSUMER_LEGAL_DOCUMENT`、`SELLER_DISCLOSURE`、`COMMERCIAL_TERM_PLAN`、`PAYMENT_METHOD_PROFILE`。其中 `PARTY_PROFILE -> PARTY`、`CLIENT_BUSINESS_PROFILE -> CLIENT`、`COMMERCIAL_TERM_VERSION -> COMMERCIAL_TERM_PLAN`，其余类型均为自身 Root。

`individual_profile`、`organization_profile`、`party_identity_key`、`party_profile_identifier`、`party_address`、`party_contact`、`client_business_classification`、`application_document`、`agreement_signatory`、`agreement_document_ref`、`agreement_acceptance`、`agreement_operating_entity`、`payment_method_profile_cond`、`commercial_term_price`、`commercial_term_component`、`commercial_term_component_band` 明确不重复写 BCR；它们依靠所属 Root 的 CAS、冻结后的结构化业务版本或追加式领域证据保存历史。`business_change_record`、`business_change_version`、`compensation_event`、`command_idempotency` 四张技术表不得自审计。

受审计 Root 的创建、更新或删除与结构化 Child 变化必须在同一本地事务内：CAS Root BCR、追加 Root 最终快照、仅为白名单内实际发生需留档变化的 Child 追加最终快照、写结构化业务行，并以同一下一版本登记可靠事件。Root CAS 已串行化同聚合写入；受审计 Child 只把 `current_version` 推进到 Root `next_version`，不接收 `child_expected_version` 或执行独立 CAS。白名单外 Child 不创建 BCR、不写重复 Snapshot，但仍受 Root CAS、冻结规则和结构化约束保护。

多 Root 命令按 `(aggregate_root_type, aggregate_root_id)` 确定顺序锁定并逐 Root CAS。每个 Root 独立取得下一版本、Root/白名单内 Changed Child History 和事件 `source_version`；共同 `trigger_id` 只负责关联。任一 Root CAS、业务校验、应写 History 或事件登记冲突必须回滚整个本地事务。

独立技术乐观锁只保留 `compensation_event.lock_version`，用于 Claim/Lease 并发。业务 `source_version`、`version_no`、`vault_record_version` 和 Hash/Schema 版本均不得被误作锁。`current_version_id` 在当前设计中被明确禁止。

`business_change_version.snapshot_json` 使用按 `record_type` 登记的 `_meta + data` 字段白名单信封和 `CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1` Integrity Domain；不得序列化 DO，也不得复制敏感原值、完整 Vault Token、Blind Index、密文、文件正文、Slot、Lease、锁或重试字段。BCR 只还原白名单覆盖的审计视图，不是完整业务聚合恢复源，也不作为 Agreement/Legal 下游交易坐标。

## 5. Application 物理约束

### 5.1 Client Application

`client_application.application_type` 仅为 `ONBOARDING|PROFILE_CHANGE`，状态精确为 `DRAFT|SUBMITTED|UNDER_REVIEW|APPROVED|REJECTED|WITHDRAWN`；仅前三态 `active_slot=1`。

- ONBOARDING：两个 Base Profile ID 为空，两个 Target 非空，目标是 `PENDING` Client。
- ONBOARDING 必须写入非空 `requested_by_account_id`；该字段仅是 Platform CUSTOMER Account 的外部稳定引用，不建立 CCT→Platform Account FK。PROFILE_CHANGE 可按明确 Actor Contract 为空，但不得用它替代 `create_by`。
- PROFILE_CHANGE：提交时冻结 `ACTIVE` Base Party/Business Profile 和 Target Profile，四个 ID 均非空且至少一个 Target 不同；目标是 `ACTIVE` Client。
- Client/Partner ONBOARDING 的 CCT-I15 `START_OR_RESUME` 事务先创建或复用 PENDING Role 与 DRAFT Application，冻结 `requested_by_account_id` 并登记 Platform `ProvisionAccessTenant` Handler；不接受尚未归属 Tenant 的 File ID，也不登记 Risk。Client、Partner 与 Operating Entity 三类 Application 的 `SUBMIT` 事务才冻结各自 Role/Qualification/Profile/Document/Source 输入 Tuple 与完整 Submission Hash，并登记唯一 Risk `LOCAL_HANDLER`；此时不要求尚未产生的 Risk Case 或 Decision。
- 事务提交后，Client Application Handler 固定携带 `CCT_CLIENT_APPLICATION + application_id + CLIENT + client_id + qualification_type + submission_content_hash`，Partner Application Handler 固定携带 `CCT_LOCAL_PARTNER_APPLICATION + application_id + LOCAL_PARTNER + local_partner_id + PARTNER_DUE_DILIGENCE + submission_content_hash`，Operating Entity Application Handler 固定携带 `CCT_OPERATING_ENTITY_APPLICATION + application_id + OPERATING_ENTITY + operating_entity_id + ENTITY_UNDERWRITING + submission_content_hash`。Risk 以 Typed Source Namespace、Application ID 和完整 Role/Qualification Tuple 唯一定位主 Case，并用 Submission Hash 区分同内容幂等与异内容冲突。第二个 CCT 本地事务校验同一 Hash 未变，只回填本申请主 Role 的 `due_diligence_case_id` 并把申请推进到 `UNDER_REVIEW`；Organization Application 派生的关联人 KYC Case 由 Risk 内部管理，不回填到该单值列。
- `qualification_decision_id/qualification_decision_version` 只接受提交后的 Risk 结果。批准或拒绝前必须锁内校验完整 `due_diligence_case_id + qualification_decision_id + qualification_decision_version + qualification_type` Tuple 与冻结的 Role、Case 和 Submission Hash 一致；缺失或不一致必须拒绝。

### 5.2 Partner Application

`partner_application` 的 17 个字段严格按 Catalog 次序生成。`local_partner_id`、`target_party_profile_id` 与 `requested_by_account_id` 必填；后者仅是外部 Platform CUSTOMER Account 申请人引用，不证明法律主体或签约资格，也不建立跨库 FK。`create_by` 记录该可变生命周期申请行的 CCT 录入或命令 Actor；它不是不可变 BCR Snapshot Actor，且不得与 `requested_by_account_id` 混用。`qualification_type` 固定为 `PARTNER_DUE_DILIGENCE`，状态精确为 `DRAFT|SUBMITTED|UNDER_REVIEW|APPROVED|REJECTED|WITHDRAWN`，仅前三态 `active_slot=1`。Risk Case、Decision ID、Decision Version 与提交时间可空；Decision ID/Version 必须同时为空或非空，APPROVED 还必须拥有 `due_diligence_case_id` 与完整 Decision Tuple，WITHDRAWN 可没有 Decision。

首次 `START_OR_RESUME` 在同一 CCT 本地事务创建稳定 `PENDING` Local Partner、Organization Party、Target Party Profile、DRAFT Partner Application、Partner Application Root BCR/Version 和 Platform Provision `compensation_event`。拒绝或撤回不会终止该 Role；重提继续使用同一个 PENDING Local Partner ID。只有 APPROVED 将该 Role 激活。最终 `SUBMIT` 后的 Risk 命令固定携带 `CCT_LOCAL_PARTNER_APPLICATION + application_id + LOCAL_PARTNER + local_partner_id + PARTNER_DUE_DILIGENCE + submission_content_hash`；Risk 和 Platform 坐标均为外部稳定 ID，不建立 CCT 跨库 FK。

`START_OR_RESUME` 事务登记的 `LOCAL_HANDLER` 在 after-commit 调用 Platform `ProvisionAccessTenant(tenant_type, owner_ref_id, requested_by_account_id, source_version, payload_hash)`；Platform 返回 PENDING Tenant/Member 后，BFF 才能在 Account Context 调用 PLT-I04 上传文件。CCT `SUBMIT` 校验并冻结受控 File ID 后，才登记 Risk Typed Due Diligence Handler。随后状态变化按相同 Source Identity 发送版本化 Platform 更新。Platform/Risk 命令不是 CCT 数据库约束：同版本同 Hash 重放返回既有结果、同版本异 Hash 冲突，旧版本拒绝；状态更新早于 Provision 时可重试且不得在缺少 `requested_by_account_id` 时凭空建 Tenant。

Platform CUSTOMER Account 验证严格先于 CCT ONBOARDING。`requested_by_account_id` 只是 Platform Account 的稳定外部引用，不是 CCT Party 或 Agreement Signatory，也不产生法律主体或签约资格。CCT→Platform 命令固定为 `operation_type=PROVISION_ACCESS_TENANT`、`tenant_type=CLIENT|PARTNER`、`owner_ref_id`、`requested_by_account_id`、单调 `source_version` 和 64 位小写十六进制 `payload_hash`；完整命令 Tuple 为 `tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash`。同版本同 Hash 返回原 Tenant/Member，同版本异 Hash 为 `CONFLICT`，旧版本拒绝。首次 after-commit Provision 创建 PENDING Access Tenant、OWNER Member 和 `TENANT_ONBOARDING_OWNER`；PENDING 不签发普通 Tenant Context Token。只有 CCT APPROVED/Role ACTIVE 的后续状态投影才能激活 Tenant、撤销 Onboarding Role 并授予 Tenant Owner。Status 先到时只能重试，绝不创建缺 `requested_by_account_id` 的 Tenant。

### 5.3 Application Document

`application_document.client_application_id`、`partner_application_id` 与 `operating_entity_application_id` 全部可空；Sidecar 表达式 `((client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1)` 只冻结逻辑不变量，不生成物理 `CHECK`。写入事务必须校验恰好一个非空并锁定其实际 Parent；三条 Parent Relation 都是 CCT 本库逻辑 `RESTRICT` 关系，不创建物理 FK，并分别以五列 `uk_app_doc_client`、`uk_app_doc_partner`、`uk_app_doc_entity` 保证同一材料身份唯一。`file_id` 仍是 Platform 文件外部引用。Client/Partner 入驻的 File 必须先由 Platform PLT-I04 在 Account Context 解析 PENDING Tenant 与 `TENANT_ONBOARDING_OWNER` 后创建，CCT 不接受调用方自报 Tenant 或未受控 File ID。Application Document 继续 BCR-excluded。

### 5.4 Operating Entity Application

`operating_entity_application.operating_entity_id` 始终非空且稳定。首次创建申请时，同事务创建 `PENDING` Operating Entity Role；重提申请复用该 Role。申请不保存重复的 Organization Party、经营国家或批准后 OE 字段，`qualification_type` 固定为 `ENTITY_UNDERWRITING`。

## 6. Legal、实时交易资格与 Source

### 6.1 Agreement 与 Consumer Legal 七表直接模型

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

Agreement/Legal 固定为 `agreement`、`agreement_signatory`、`agreement_document_ref`、`agreement_acceptance`、`agreement_operating_entity`、`consumer_legal_document`、`seller_disclosure` 七张表。一行 `agreement` 就是一份独立法律合同；一行 Consumer Legal 或 Seller Disclosure 就是一份可独立审批、发布和 As-Of 解析的内容。`agreement_operating_entity` 直接保存 CLIENT Agreement 的四项结算到期输入：`settlement_business_days`、`business_calendar_code`、`settlement_timezone`、`cutoff_second_of_day`。四列物理可空；CLIENT 必须四列全非空，PARTNER 必须全空，前者天数不得小于零、截止秒数必须在 `0..86399`。已签或已发布内容变化必须创建新的稳定业务 ID，不使用 Root/Version、Current Pointer 或 Active Slot。

Agreement As-Of 候选固定满足 `signed_time <= t AND activated_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)`；Consumer Legal 与 Seller 候选固定满足 `published_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)`。进入 `PENDING_SIGNATURE` 前必须完成 Agreement 覆盖与四项结算字段的 CLIENT/PARTNER 矩阵校验，进入后覆盖与四字段一同冻结；不建立日历/通用规则表、Version、Hash、附加索引或关系。进入待签署/已批准阶段即检查同 Owner/OE 或 OE/Country/Locale/Type 区间冲突，签署/激活/发布时防御性复查；历史解析不能只按当前 ACTIVE 状态过滤。替换命令按稳定顺序锁定全部 Root，在同一本地事务收窄旧 `effective_to`、写结构化行、BCR 与可靠事件并冻结新记录，且不得把旧区间回溯收窄到 `operation_context_time` 之前。零条或多条候选均 Fail Closed。

合同文件与 Consumer Legal 文件只以 `file_id/content_file_id + content_hash` 冻结 Platform Support 的字节级 SHA-256。Seller Disclosure 保存结构化公开字段，不保存业务 Hash。`commercial_term_version.agreement_id` 与 `agreement.agreement_id` 是本库逻辑关系，不创建物理 FK；写入事务必须锁定 Agreement 并校验完整 Owner、Agreement Type 与有效期 Tuple。Plan 不保存重复 Agreement 引用。所属 Plan 的 `term_type` 与 Owner、Agreement Type、OE 覆盖、Source Relation 和有效区间必须在写入及 As-Of 解析时一致，Agreement 提前终止后新交易 Fail Closed。

Agreement Association 与四项结算到期事实的 Owner 边界不因定价表重构而改变。现行 CCT-I04/I12 及 CCT-I13/I14 V5 的具体响应仍按各自既有合同读取；其中 Scope、Scope ID 与 V5 Hash 只属于 legacy 迁移兼容。新定价 Wire 必须在保留 Association 与 Legal/Seller 证据的同时另行定义 Profile Code、Price、Component/Band 证据，不能由本物理模型反向猜测接口字段。

### 6.2 使用点实时交易资格

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

CCT-I10 是非持久化诊断查询，按 `client_id/country_code/currency/industry_code/mcc_code` 精确范围实时检查 CCT 自有事实及 Risk、Payment、Platform 的当前事实。响应只含 `READY|NOT_READY|RESTRICTED`、`checked_at` 和非 READY 的稳定 `reason_code`；不返回 ID、版本、有效期、固定维度或 Hash，也不能被后续交易复用。

Legacy CCT-I13 V5 每次重新执行同一检查，业务不满足或技术依赖失败都 Fail Closed；其 Scope 返回形状尚未迁移到新定价模型。`context_as_of_time` 只解析 CCT 自有的历史商业/法律有效区间，不能用于选择外部系统的历史 READY。CCT 不建立 Evaluation 表、来源事实投影或资格变更事件。

### 6.3 Source 与审核关联

`source_type` 仅允许 `PLATFORM|CLIENT|PARTNER`；`DIRECT` 明确禁止作为当前值。来源 Owner 直接由 `source_type + source_client_id/source_partner_id` 的 XOR 表达，不保存派生 `source_owner_key`。

CLIENT 来源的 `client_source_basis` 仅允许 `SAME_PARTY|VERIFIED_ASSOCIATION`：前者要求 Client Party 与 OE Party 相同；后者要求两个 Party 不同，并由已批准的 Operating Entity Application、冻结申请材料和匹配的 Risk Qualification Decision 支撑。非 CLIENT 来源该字段必须为空。当前不结构化保存多公司多层持股路径，也不计算间接持股比例；`party_relation` 继续保存 KYB 所需的直接所有权、控制、董事、签字人与 UBO 关系。

### 6.4 自动分配四表闭环

当前仅支持系统自动分配，模型固定为 Source Relation → Affiliation / Assignment Rule → Assignment 四层：

- `entity_source_relation` 证明 Operating Entity 的唯一当前来源；
- `client_entity_affiliation` 证明 Client 在准确 Country/Currency/Industry/MCC Scope 下可以使用该 Operating Entity；
- `entity_assignment_rule` 是 Client 专属的准确规则。同一 Scope 可按不同 `priority_no` 配置多个候选主体，数字越小优先；不支持 GLOBAL、通配、Fallback、运行时覆盖或手工分配；
- `client_entity_assignment` 保存自动计算出的当前事实，并强制非空引用 `rule_id/affiliation_id/entity_source_relation_id/operating_entity_id`。

规则目标必须同时满足 ACTIVE Operating Entity、准确 ACTIVE Affiliation 与唯一 ACTIVE Source Relation；按最小 `priority_no` 唯一命中后才能生成 ACTIVE Assignment。无候选、同优先级冲突或任一证明失效时不猜测：先结束旧 Assignment，交易资格与 CCT-I11/I13 Fail Closed。触发者固定为 CCT 内部处理 Rule、Affiliation、Source Relation、Operating Entity 或 Business Classification 变化的 Processor；状态变更命令在同一本地事务内同步重算受影响的准确 Client/Scope，并在 Assignment 已生成、保持或结束后才提交。外部调用方、CCT-I11/I13 查询、定时任务和人工操作都不触发或补写 Assignment；当前不增加工作流、调度器、发布版本或额外事件表。

明确删除且不得在当前阶段恢复：`entity_assignment_policy`、`entity_assignment_policy_ver`、`assignment_policy_binding`、`client_entity_assignment_ver`、`source_owner_key`，以及 Assignment 域的 `scope_hash/content_hash/hash_schema_version/*_revision_no`。未来只有出现已确认的 GLOBAL、多层策略、手工覆盖、旁路发布或历史版本回放用例时才重新设计。

## 7. 索引、关系与事务约束

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

> 拆分说明：`command_idempotency` 已按决定 S10 删除，不进入 client-master 或 commercial 的模型与 Schema；本节关于它的句子仅为历史记录。

- 逻辑 Catalog 使用不带技术前缀的领域表名；PDManer `defKey` 映射为 `t_<逻辑表名>`。唯一索引使用 `uk_`，普通索引使用 `idx_`；均为小写 `snake_case` 且不超过 32 字符。中文 `defName` 不添加 `t_`。Catalog 每个显式 `uk_*` 都必须生成，不得用含糊说明代替。
- 每个本库强关系至少有可用左前缀索引。跨系统引用若 Owner 已有全局唯一、不可修改且永不复用的业务 Code 则优先使用 Code，否则使用稳定 ID 或明确复合坐标；所有这类引用与 Vault Token、多态 Tuple 都只保留字段与注释，不建立跨库 FK、View 或查询。
- 有效区间统一为 `[effective_from,effective_to)`；`effective_to IS NULL OR effective_from < effective_to`。
- 三个 Application Document Parent 的 XOR 和 Source Owner XOR 都作为 Sidecar 逻辑表达式冻结；Owner 写入口在同一本地事务中锁定相关 Root、校验完整 Tuple，并以 CAS/唯一索引保护并发，不依赖物理 `CHECK`。Agreement Owner XOR、跨行唯一 As-Of 解析、Party 关系无环、直接持股合计、父子封存与多 Root CAS 同样由应用事务和 Repository 承载。
- Active Slot 必须同时满足状态矩阵和唯一索引，不能只依赖 MySQL 允许多个 `NULL`。
- 所有模型关系都使用逻辑限制语义，Owner 应用在删除或终止前查询引用并拒绝级联删除领域事实；不创建物理 FK，也不以 PDManer Correlation 授权数据库 Constraint。

`compensation_event` 以 `(event_key,event_type,sub_event_type)` 唯一，保留 `(execute_status,next_execute_at,id)` 就绪索引和 `(execute_status,lock_until,id)` 过期租约索引；可选 `payload_json` 最大 4 KiB UTF-8，只允许无法从本库权威事实恢复的最小非敏感 JSON Object，禁止 Vault Token。Source/Version/Schema/Hash 不属于补偿表字段；本文业务事件中的 `source_version` 继续按 Root History 恢复。存量环境通过独立人工 SQL 对齐，执行状态以台账为准。`command_idempotency` 必须有 `(caller_scope,operation_type,idempotency_key)` 唯一约束；相同幂等 Key 对应不同 `request_hash` 时必须冲突。

## 8. Vault、默认保留与可靠执行

> 拆分说明：本节决策同时涉及 client-master 与 commercial 的表，两份物理决策文档逐字保留同一文本。

Email、Phone 原值只进入 Vault；CCT 保存批准的 Token Tuple 与 Masked Value。姓名、日期事实、法律标识和地址可按批准结构化落库，仍必须遵守授权与最小化访问。PAN、CVV/CVC、完整磁道、PIN/PIN Block、Ciphertext、DEK、KMS Key Ref、Provider Secret 和私钥禁止进入模型、Hash、事件、日志或文件。

`business_change_record/version` 是业务审计历史，不是异步队列。权威业务变化、历史和 `compensation_event` 登记在同一本地事务提交；提交后 Dispatcher 才 Claim，外部副作用按至少一次和业务 Key 幂等执行。XXL-Job 只负责兜底唤醒/分片，不能替代数据库 Claim/Lease 或业务 CAS。

MVP 暂不建设通用 Retention/Legal Hold 投影、Worker、归档、匿名化或销毁任务。业务与技术数据默认保留，不自动删除或销毁；只有业务专用、已经明确批准的生命周期可以继续使用自身过期字段。接入真实受监管生产数据前，必须完成数据分类、适用期限、Legal Hold、备份处置、Vault 生命周期、受控销毁和审计专项设计，并通过法律/合规评审。

## 9. PDManer 模板派生与统计门禁

共享模板 [template.pdma.json](../../../../agent-docs/templates/pdmaner/template.pdma.json) 保持字节级不变。生成器已经在派生 client-master 模型内完成：

- `BIGINT -> Long`、`DECIMAL -> BigDecimal`、`DATETIME(6) -> Instant`；
- 新增派生 `localDate`，映射 `DATE -> LocalDate`；
- 注入完整中文显示元数据、闭合/开放值域和 Slot 规则；
- 生成本库 29 张表的字段、唯一索引、普通索引、本库关系、关系图与稳定结构投影；
- 禁用模板自增主键假设，保留 Snowflake 非自增和空数据库连接。

以下为 2026-10-17 拆分后 client-master 模型统计快照（拆分前合并模型的 38 / 413 统计只属于历史基线）；现行生成统计及运行数据库迁移状态只见[当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。下方模型与结构指纹随当前产物更新：

| 项目 | 最终结果 |
|---|---|
| 表 / 字段 | 29 / 300 |
| 必填 / 可空字段 | 197 / 93 |
| 唯一键 / 访问索引 | 39 / 25 |
| 本库关系 / 1:1 关系 | 40 / 5 |
| Diagram Groups | 6 |
| Table Cells / Relation Cells | 36 / 40 |
| Structural Projection SHA-256 | `80dba108a6322e957ca7c652a1911979705388b47b71caa24ee51bb14f989802` |
| Model SHA-256 | `3101e35c5f5626dfdf201bc88c3bdfbe71a443a1edf351b312e374bbab4a55cc` |
| Template SHA-256 | `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540` |

全部 300 个字段中 285 个来自批准 Catalog，`client_stored_card_setting` 的 5 个字段原样复制自 Commerce 模型，`t_consumed_event`（6）与 `t_consumed_aggregate_version`（4）来自 paytech-messaging 消费端标准表，没有在 Catalog 之外重复注入公共字段。40 条关系均为本库关系；6 个图组覆盖全部 29 张表，并让每条关系恰好可视化一次。

第 08 步（消息拓扑接入）为 `ClientReviewDecided` 订阅新增 `t_consumed_event` 与 `t_consumed_aggregate_version`：列名、类型、可空性、主键与
`idx_consumed_event_aggregate` 索引取自 `paytech/foundation/paytech-messaging/src/main/resources/schema/messaging-consumed-event.sql`（design/08 §4），
字符串列为 `ascii_bin`，归入 `Local History and Reliability` 图组。两表不来自 CCT Catalog，由生成器 `additionalEntities` 注入；`t_consumed_event`
以 `event_id`（VARCHAR）为主键，`t_consumed_aggregate_version` 以 `(aggregate_type, aggregate_id)` 为复合主键，二者不使用 Snowflake 主键与
`create_time + update_time` 双审计时间，是本文“每张表恰好一个非自增 BIGINT 主键”规则的唯一例外，模型契约测试与 PDManer 规则检查对其豁免。

## 10. 验证与后续门禁

本轮以以下命令验证生成模型与场景文档；精确结果以当前执行输出为准：

```bash
PASS  node scripts/pdmaner/generate-client-master-model.mjs          # 仅此命令写入 PDManer JSON
PASS  node scripts/pdmaner/generate-client-master-model.mjs --check # up to date
PASS  node --test scripts/pdmaner/client-contract-model.test.mjs
PASS  node --test scripts/pdmaner/client-contract-scenario-doc.test.mjs
PASS  node -e 'JSON.parse(require("node:fs").readFileSync("paytech/client-master/docs/database/client-master.pdma.json","utf8")); console.log("client-master PDManer JSON OK")'
PASS  shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json paytech/client-master/docs/database/client-master.pdma.json
```

仍必须用本机 PDManer 4.9.3 打开模型并运行应用内检查；该人工兼容性门禁待执行，自动测试不能替代它。

只有用户明确授权数据库实现后，才创建经审核的完整手工 Schema SQL，并在同一变更同步 Java 映射、Repository 写入规则和基于仓库自有 `local-environment` 真实 MySQL 的约束/并发测试。PDManer 是设计来源，审核后的当前 Schema SQL 是可执行数据库定义；禁止 Flyway、Liquibase、Testcontainers 或应用启动时建库/迁移，SQL 由操作员按记录的顺序和回滚计划手工执行。
