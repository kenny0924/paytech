import { CLIENT_COMPENSATION_FIELD_STORAGE } from "./client-compensation-field-storage.mjs";
import { CLIENT_ONBOARDING_FIELD_STORAGE } from "./client-onboarding-field-storage.mjs";
import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  BCR_EXCLUDED_BUSINESS_TABLES,
  BCR_RECORD_TYPES,
  BUSINESS_IDENTIFIER_CODE_PATHS,
  CLOSED_ENUM_VALUES,
  CONDITIONAL_VALUE_RULES,
  controlledValueCandidate,
  FIELD_DESCRIPTION_OVERRIDES,
  FIELD_METADATA,
  FIELD_NAME_OVERRIDES,
  OPEN_CODE_SOURCES,
  TABLE_DISPLAY_NAMES,
} from "./client-contract-field-metadata.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../..");
const templatePath = path.join(
  workspaceRoot,
  "agent-docs/templates/pdmaner/template.pdma.json",
);
const catalogPath = path.join(
  workspaceRoot,
  "agent-docs/database/CLIENT_CONTRACT_DATABASE.md",
);

const MODEL_KEY = "code-mor/client-contract/database/v1";
const PHYSICAL_TABLE_PREFIX = "t_";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

const AUDIT_FIELDS = ["create_time", "update_time"];
const CREATE_ONLY_AUDIT_TABLES = new Set(["business_change_version"]);
const JSON_FIELDS = new Set(["payload_json", "snapshot_json"]);
const LOCAL_DATE_FIELDS = new Set([
  "individual_profile.birth_date",
  "organization_profile.incorporation_date",
]);
const EXPLICIT_BIGINT_FIELDS = new Set([
  "base_entity_change_version",
  "acceptance_recorded_by",
  "create_by",
  "current_version",
  "lock_version",
  "qualification_decision_version",
  "source_version",
  "vault_record_version",
  "version_no",
]);
const MONEY_FIELD_PATHS = new Set([
  "client_business_profile.expected_ticket",
  "client_business_profile.expected_volume",
  "commercial_term_component.fixed_amount",
  "commercial_term_component.max_amount",
  "commercial_term_component.min_amount",
  "commercial_term_component_band.fixed_amount",
  "commercial_term_component_band.lower_bound",
  "commercial_term_component_band.upper_bound",
]);
const RATE_FIELD_PATHS = new Set([
  "commercial_term_component.rate",
  "commercial_term_component_band.rate",
  "party_relation.ownership_percentage",
]);
const INTEGER_FIELDS = new Set([
  "execute_count",
  "hash_schema_version",
  "priority_no",
  "component_no",
  "band_no",
  "schema_version",
  "settlement_business_days",
  "submission_hash_schema_version",
  "cutoff_second_of_day",
]);
const FLAG_OR_SLOT_FIELDS = new Set([
  "acceptance_ref_slot",
  "active_slot",
  "active_source_slot",
  "primary_slot",
  "required_acceptance_flag",
]);
const TEXT_FIELDS = new Set(["last_error_summary"]);
const VARCHAR_LENGTH_OVERRIDES = new Map(Object.entries({
  "agreement_party.acceptance_reference": 128,
  "operating_entity_scope.mcc_code": 4,
  "agreement.agreement_format": 32,
  "business_change_version.snapshot_format": 32,
  "agreement.agreement_name": 255,
  "agreement_acceptance.acceptance_reference": 128,
  "client_business_classification.mcc_code": 4,
  "client_business_profile.website_url": 2048,
  "client_entity_affiliation.mcc_code": 4,
  "client_entity_assignment.mcc_code": 4,
  "payment_method_profile.profile_name": 128,
  "payment_method_profile_cond.condition_value": 128,
  "command_idempotency.idempotency_key": 128,
  "compensation_event.event_key": 128,
  "consumer_legal_document.locale": 35,
  "entity_assignment_rule.mcc_code": 4,
  "party_address.address_line1": 255,
  "party_address.address_line2": 255,
  "party_address.city": 128,
  "party_address.postal_code": 32,
  "party_address.state_or_province": 128,
  "party_contact.contact_masked_value": 255,
  "party_contact.contact_vault_token": 128,
  "party_identity_key.identifier_value": 128,
  "party_identity_key.normalized_identifier_value": 128,
  "seller_disclosure.locale": 35,
  "seller_disclosure.public_legal_name": 255,
  "seller_disclosure.public_registration_number": 128,
  "seller_disclosure.public_tax_registration_number": 64,
  "seller_disclosure.public_address_text": 512,
  "seller_disclosure.public_support_email": 254,
  "seller_disclosure.public_support_phone": 32,
}));
const NULLABLE_FIELD_PATHS = new Set([
  "agreement.entity_no",
  "commercial_term_plan.agreement_id",
  "commercial_term_plan.entity_no",
  "commercial_term_price.settlement_business_days",
  "commercial_term_price.business_calendar_code",
  "commercial_term_price.settlement_timezone",
  "commercial_term_price.cutoff_second_of_day",
  "operating_entity_scope.active_slot",
  "operating_entity_scope.effective_from",
  "operating_entity_scope.effective_to",
  "agreement_party.acceptance_method",
  "agreement_party.accepted_by_actor_id",
  "agreement_party.accepted_time",
  "agreement_party.evidence_document_id",
  "agreement_party.acceptance_reference",
  "agreement_party.acceptance_ref_slot",
  "agreement_party.acceptance_recorded_by",
  "agreement_party.acceptance_recorded_at",

  "application_document.client_application_id",
  "application_document.partner_application_id",
  "application_document.operating_entity_application_id",
  "business_change_record.parent_history_id",
  "client.activation_time",
  "client.suspension_time",
  "client.termination_time",
  "client_application.active_slot",
  "client_application.base_business_profile_id",
  "client_application.base_party_profile_id",
  "client_application.due_diligence_case_id",
  "client_application.qualification_decision_id",
  "client_application.qualification_decision_version",
  "client_application.requested_by_account_id",
  "client_application.submitted_time",
  "client_business_classification.primary_slot",
  "client_business_profile.active_slot",
  "client_business_profile.effective_to",
  "client_business_profile.website_url",
  "client_entity_affiliation.active_slot",
  "client_entity_affiliation.effective_to",
  "client_entity_assignment.active_slot",
  "client_entity_assignment.effective_to",
  "commercial_term_component.currency",
  "commercial_term_component.band_mode",
  "commercial_term_component.fixed_amount",
  "commercial_term_component.max_amount",
  "commercial_term_component.min_amount",
  "commercial_term_component.rate",
  "commercial_term_plan.client_id",
  "commercial_term_plan.local_partner_id",
  "commercial_term_component_band.upper_bound",
  "commercial_term_component_band.rate",
  "commercial_term_component_band.fixed_amount",
  "commercial_term_version.active_slot",
  "commercial_term_version.effective_to",
  "command_idempotency.response_ref",
  "compensation_event.last_error_code",
  "compensation_event.last_error_summary",
  "compensation_event.lock_owner",
  "compensation_event.lock_until",
  "entity_assignment_rule.active_slot",
  "entity_assignment_rule.effective_to",
  "entity_source_relation.active_source_slot",
  "entity_source_relation.client_source_basis",
  "entity_source_relation.effective_to",
  "entity_source_relation.source_client_id",
  "entity_source_relation.source_partner_id",
  "individual_profile.middle_name",
  "local_partner.activation_time",
  "local_partner.termination_time",
  "operating_entity_application.active_slot",
  "operating_entity_application.approved_source_relation_id",
  "operating_entity_application.client_source_basis",
  "operating_entity_application.due_diligence_case_id",
  "operating_entity_application.qualification_decision_id",
  "operating_entity_application.qualification_decision_version",
  "operating_entity_application.source_client_id",
  "operating_entity_application.source_partner_id",
  "operating_entity_application.submitted_time",
  "partner_application.active_slot",
  "partner_application.due_diligence_case_id",
  "partner_application.qualification_decision_id",
  "partner_application.qualification_decision_version",
  "partner_application.submitted_time",
  "organization_profile.trade_name",
  "party_address.address_line2",
  "party_address.postal_code",
  "party_address.state_or_province",
  "party_identity_key.issuing_country",
  "party_profile.active_slot",
  "party_profile.effective_to",
  "party_profile_identifier.valid_to",
  "party_relation.active_slot",
  "party_relation.effective_to",
  "party_relation.ownership_percentage",
  "agreement.client_id", "agreement.local_partner_id", "agreement.effective_from", "agreement.effective_to",
  "agreement.signed_time", "agreement.activated_time",
  "consumer_legal_document.effective_to", "consumer_legal_document.approved_time", "consumer_legal_document.published_time",
  "seller_disclosure.public_tax_registration_number", "seller_disclosure.public_support_phone",
  "seller_disclosure.effective_to", "seller_disclosure.approved_time", "seller_disclosure.published_time",
]);

const VERSION_TABLES = new Set([
  "commercial_term_version",
]);
const VERSION_COMMENT_CLAUSES = [
  "CHECK ((status='ACTIVE' AND active_slot=1) OR (status<>'ACTIVE' AND active_slot IS NULL))",
  "CHECK (effective_to IS NULL OR effective_from < effective_to)",
  "同一 Root 已批准或生效的有效区间不得重叠。",
];
const APPLICATION_COMMENT_CLAUSES = [
  "CHECK ((status IN ('DRAFT','SUBMITTED','UNDER_REVIEW') AND active_slot=1) OR (status IN ('APPROVED','REJECTED','WITHDRAWN') AND active_slot IS NULL))",
  "CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))",
];
const TABLE_COMMENT_CLAUSES = Object.freeze({
  operating_entity_application: [
    ...APPLICATION_COMMENT_CLAUSES,
    "CHECK ((status='APPROVED' AND approved_source_relation_id IS NOT NULL) OR (status<>'APPROVED' AND approved_source_relation_id IS NULL))",
    "Risk Tuple 固定映射：role_type=OPERATING_ENTITY、role_id=entity_no、ONBOARDING 的 qualification_type=ENTITY_UNDERWRITING；SCOPE_CHANGE 的 qualification_type=ENTITY_SCOPE_REVIEW。",
    "qualification_decision_id 与 qualification_decision_version 必须成组为空或成组非空。",
    "APPROVED/REJECTED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。",
    "qualification_type 由系统按申请类型固定为 ENTITY_UNDERWRITING 或 ENTITY_SCOPE_REVIEW，不接受调用方输入。",
    "提交后 source_type、source_client_id、source_partner_id、client_source_basis 与 qualification_type 组成的 Source/Qualification Tuple 冻结且不可修改。",
    "approved_source_relation_id 仅在 APPROVED 时非空，其他状态必须为空。",
    "Source Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
    "CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。",
    "SCOPE_CHANGE 必须核对 base_entity_change_version 与完整目标 Scope，沿用既有主体和唯一来源关系，不回退主体状态；过时或拒绝的审核不能撤销现有范围。Risk 新 Tuple 未接入时禁止该流程激活范围。",
  ],
  agreement: [
    "Owner XOR：agreement_type=CLIENT 要求 client_id IS NOT NULL AND local_partner_id IS NULL；agreement_type=PARTNER 要求 client_id IS NULL AND local_partner_id IS NOT NULL。",
    "Owner Party 规则：CLIENT 合同的实际签约 Owner 为 Client 对应 Party；PARTNER 合同的实际签约 Owner 为 Local Partner 对应 Party。",
    "状态时间矩阵：DRAFT、PENDING_SIGNATURE、CANCELLED 的 signed_time/activated_time 均为空；SIGNED 的 signed_time 非空且 activated_time 为空；ACTIVE、EXPIRED 两者均非空；TERMINATED 的 signed_time 非空且 activated_time 可为空。",
    "存在 activated_time 时必须满足 signed_time <= activated_time AND effective_from <= activated_time；EXPIRED、TERMINATED 必须填写 effective_to。",
    "CHECK (effective_to IS NULL OR effective_from < effective_to)，有效期采用半开区间 [effective_from,effective_to)。",
    "历史 As-Of 候选必须满足 signed_time <= t AND activated_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)，不得按当前 status=ACTIVE 排除历史行；零条或多条候选均 Fail Closed。",
    "进入 PENDING_SIGNATURE、SIGNED、ACTIVE 和显式替换时，按 Owner、Agreement Type、Operating Entity 的稳定顺序锁定全部相关 Root 并防御性复查区间冲突。",
    "替换在同一事务收窄旧合同 effective_to、写 BCR/事件和终止依据并冻结新合同；不得按当前状态过滤 EXPIRED/TERMINATED 行，不得回溯收窄到 operation_context_time 之前，旧合同内容、覆盖关系和 effective_from 不得覆盖。",
  ],
  agreement_party: [
    "party_profile_id 必须属于 party_id 且提交签署时已封存；CLIENT/LOCAL_PARTNER 签约方必须分别匹配合同 Owner，PLATFORM_SIGNATORY 为平台授权签约主体，OPERATING_ENTITY 必须属于合同覆盖经营主体。",
    "包含 PLATFORM_SERVICE_CHARGE 的 PARTNER Agreement 必须恰好一个 PLATFORM_SIGNATORY；零个或多个平台签约方时 CCT-I13/CCT-I14 均 Fail Closed。",
    "进入 PENDING_SIGNATURE 后签约方不得修改，同一合同 Party 的全部角色必须一次原子写入相同的最终签署结果；仅最小行占 acceptance_ref_slot=1。",

    "Acceptance Method Matrix：CLICK_ACCEPTANCE 的 evidence_document_id 必须指向唯一 SIGNING_DOCUMENT；全部 CLICK_ACCEPTANCE 的合同进入 SIGNED 无需 EXECUTED_DOCUMENT；E_SIGNATURE 和 MANUAL_SIGNATURE 必须指向 EXECUTED_DOCUMENT，任一 E_SIGNATURE 或 MANUAL_SIGNATURE 存在时进入 SIGNED 前必须恰有一份 EXECUTED_DOCUMENT。",
    "accepted_by_actor_id 是实际在线接受 Actor，create_by 是登记证据 Actor；CLICK_ACCEPTANCE 必须填写 accepted_by_actor_id，E_SIGNATURE/MANUAL_SIGNATURE 按受控签署记录填写，二者不得混同。",
    "最终接受证据仅允许从待签补齐一次，补齐后不得修改或删除。",
  ],
  commercial_term_price: [
    "每条 Price 属于准确 term_version_id + entity_no，并以 payment_method_profile_code 关联一个 ACTIVE Payment Method Profile；Profile 可跨 Client、Version 与 OE 复用。",
    "Operating Entity 必须被 Version 引用的 Agreement 覆盖；不允许 NULL、* 或全 OE 通配，旧全 Agreement-OE Scope 迁移时展开为准确 OE Price。",
    "匹配先选择不同 dimension_code 数量最多的 Profile，再以最小 priority_no 选择；零命中、事实缺失或仍有歧义均 Fail Closed。",
    "PLATFORM_SERVICE_CHARGE 使用受控 Calculation Base Catalog；不得用 Client Fee、Partner Share、Channel Cost 或其残差倒算平台服务费。",
    "CLIENT 的四个结算字段全部非空；PARTNER 的四个结算字段全部为空；初期 settlement_timezone=UTC、cutoff_second_of_day=86399。",
    "CHECK (settlement_business_days IS NULL OR settlement_business_days>=0)",
    "CHECK (cutoff_second_of_day IS NULL OR cutoff_second_of_day BETWEEN 0 AND 86399)",
    "UTC 截止时刻精确至秒的起点；23:59:59.000001 已超过 86399 秒截止，使用冻结的工作日日历和实际成功时刻计算。",
  ],
  commercial_term_plan: [
    "Owner XOR：term_type=CLIENT_PRICING 要求 client_id IS NOT NULL AND local_partner_id IS NULL；term_type IN (PARTNER_SHARE,PLATFORM_SERVICE_CHARGE) 要求 client_id IS NULL AND local_partner_id IS NOT NULL。",
    "term_type、client_id 与 local_partner_id 创建后不可修改；MVP 每份 Client Agreement 一个 Client Pricing Plan，每个 Local Partner 可分别拥有一个 Partner Share Plan 和一个 PLATFORM_SERVICE_CHARGE Plan，通过 local_partner_id + term_type 复合唯一。",
    "CLIENT_PRICING 的 agreement_id/entity_no/client_id 必须与同一 Client Agreement 一致，local_partner_id 为空；Partner 两类只填 local_partner_id，Version 冻结其 Partner Agreement。",
  ],
  application_document: [
    "CHECK ((client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1)",
    "client_application_id、partner_application_id 与 operating_entity_application_id 三个 Parent FK 均为本库 RESTRICT。",
  ],
  client_application: [
    "同账号个人/组织合计一份进行中申请；RETURNED 占用名额，终态释放。",
    "DRAFT/RETURNED 可编辑；批准后永久只读，最终拒绝不复活。",
    "提交和重提使用 current_submission_id；旧审核任务结果不得覆盖当前任务。",
    "资格类型由主 Party 类型推导，最终决定校验准确 Case/Decision/Role Tuple。",
  ],
  partner_application: [
    ...APPLICATION_COMMENT_CLAUSES,
    "Risk Tuple 固定映射：role_type=LOCAL_PARTNER、role_id=local_partner_id、qualification_type=PARTNER_DUE_DILIGENCE。",
    "requested_by_account_id 是 Platform CUSTOMER Account 的外部稳定引用，不建立跨库物理外键。",
    "due_diligence_case_id、qualification_decision_id 与 qualification_decision_version 是 Risk & Compliance 外部稳定引用，不建立跨库物理外键。",
    "qualification_type 固定由系统写入 PARTNER_DUE_DILIGENCE，不接受调用方输入。",
    "APPROVED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。",
    "创建申请时必须在同一 CCT 本地事务创建或复用一个 PENDING Local Partner、Organization Party 和 Target Party Profile；只有 APPROVED 激活 Role，REJECTED/WITHDRAWN 重提复用该 PENDING Role。",
  ],
  agreement_document_ref: [
    "每份合同必须且只能有一份 SIGNING_DOCUMENT，可有多份 ANNEX，EXECUTED_DOCUMENT 至多一份；文件与内容哈希成组冻结。",
  ],
  commercial_term_version: [
    "CLIENT_PRICING 必须引用同一 Client 的 CLIENT Agreement；PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 必须引用同一 Local Partner 的 PARTNER Agreement，同一交易命中的两类 Partner Term 必须属于同一 Agreement。",
    "Agreement 必须覆盖全部 Price 的准确 Operating Entity，Version 有效区间不得超出 Agreement；APPROVED 后 Version、Price、Component 与 Band 业务字段不可修改。",
  ],
  payment_method_profile: [
    "状态只允许 DRAFT、ACTIVE、RETIRED；DRAFT 可在 Profile Root BCR/CAS 下编辑，ACTIVE 后 payment_method_profile_code、payment_method_code 与全部 Condition 永久冻结。",
    "Condition 变化必须创建新 Profile 与新 Code；RETIRED 只阻止新 Commercial Term Version 引用，历史引用继续可读，Code 不得回收复用。",
    "一个 Profile 精确绑定一个 Payment Method 稳定代码；CCT 不建立 Payment Method 主表，也不复制名称、图标、路由能力或 Channel 配置。",
  ],
  payment_method_profile_cond: [
    "每行保存一个 dimension_code + condition_value 离散等值条件；同维度多行按 OR，不同维度按 AND，缺少维度表示不限制。",
    "不保存 CSV、*、Operator、Range，不建立 Dimension Definition、Profile Version 或 Condition Value 子表；维度和值由版本化应用/API 合同校验。",
    "Condition 是 Profile 聚合内部 Child，使用 payment_method_profile_id 关联，并由 Profile Root BCR/CAS 与冻结规则保护。",
  ],
  commercial_term_component: [
    "PERCENTAGE、FIXED、TIERED 必须满足批准的 rate、fixed_amount、currency、min_amount、max_amount 与 band_mode 矩阵。",
    "多个 Component 按 component_no 稳定计算并相加；PERCENTAGE/FIXED 不得拥有 Band，TIERED 头部 rate/fixed_amount 为空、band_mode 非空且至少拥有一档。",
    "CLIENT_PRICING 使用受控 Client Fee 计算基数目录；PARTNER_SHARE 固定 calculation_base=ACTUAL_CLIENT_FEE；PLATFORM_SERVICE_CHARGE 使用受控 Calculation Base Catalog，现实场景可使用 GROSS_PAYMENT_AMOUNT。",
    "所属 Version 进入 APPROVED 后结构化 Component 与 Band 不可修改；调整条款必须创建新 Version。",
  ],
  commercial_term_component_band: [
    "区间使用 [lower_bound,upper_bound)，第一档从 0 开始、连续且无重叠，最后一档 upper_bound 为空。",
    "每档 rate 与 fixed_amount 恰好一个非空；PROGRESSIVE 首期只允许 rate，固定金额组合使用独立 FIXED Component。",
    "Band 继承 Component 的 currency 与 calculation_base；同一组件 band_no 从 1 连续编号，所属 Version APPROVED 后不可修改。",
  ],
  consumer_legal_document: [
    "状态时间矩阵：DRAFT 的 approved_time/published_time 均为空；APPROVED 仅 approved_time 非空；ACTIVE、EXPIRED 两者均非空；WITHDRAWN 的 approved_time 非空，未发布撤回时 published_time 可为空。",
    "存在 published_time 时必须满足 approved_time <= published_time AND effective_from <= published_time；已经发布的 EXPIRED/WITHDRAWN 必须填写 effective_to。",
    "按 Operating Entity 稳定顺序锁定同一 OE/Country/Locale/Document Type 的全部历史行并检查半开有效区间冲突；进入 ACTIVE 时防御性复查。",
    "历史 As-Of 候选必须满足 published_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)，不得按当前 status=ACTIVE 排除 EXPIRED/WITHDRAWN 行；每个 Scope 缺失或并列均 Fail Closed。",
    "替换在同一事务收窄旧行 effective_to、写 BCR/事件并冻结新行；不得按当前状态过滤历史行，不得回溯收窄到 operation_context_time 之前；APPROVED 后文件、Scope 和 effective_from 不得修改，内容变化创建新 legal_document_id。",
  ],
  seller_disclosure: [
    "状态时间矩阵：DRAFT 的 approved_time/published_time 均为空；APPROVED 仅 approved_time 非空；ACTIVE、EXPIRED 两者均非空；WITHDRAWN 的 approved_time 非空，未发布撤回时 published_time 可为空。",
    "存在 published_time 时必须满足 approved_time <= published_time AND effective_from <= published_time；已经发布的 EXPIRED/WITHDRAWN 必须填写 effective_to。",
    "按 Operating Entity 稳定顺序锁定同一 OE/Country/Locale 的全部历史行并检查半开有效区间冲突；进入 ACTIVE 时防御性复查。",
    "历史 As-Of 候选必须满足 published_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)，不得按当前 status=ACTIVE 排除 EXPIRED/WITHDRAWN 行；缺失或并列均 Fail Closed。",
    "替换在同一事务收窄旧行 effective_to、写 BCR/事件并冻结新行；不得回溯收窄到 operation_context_time 之前；APPROVED 后公开字段、Scope 和 effective_from 不得修改，内容变化创建新 disclosure_id。",
  ],
  party_relation: [
    "稳定关系标识；Client 未批准资料只允许所属可编辑申请维护，批准后关系集合锁定。",
    "真实关系生效/结束日期保留；非 Client 历史引用不得自动迁移或归并。",
  ],
  entity_source_relation: [
    "CHECK ((status='ACTIVE' AND active_source_slot=1) OR (status='ENDED' AND active_source_slot IS NULL))",
    "CHECK (effective_to IS NULL OR effective_from < effective_to)",
    "Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
    "CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。",
    "source_type、source_client_id、source_partner_id、client_source_basis 创建后不可修改；来源失效时只允许推进为 ENDED 并填写 effective_to。",
  ],
  business_change_record: [
    "current_version > 0",
    "Record Type 白名单由 record_type 字段闭合枚举定义；白名单外不创建 BCR。",
    "非审计 Child 使用冻结的结构化事实或所属版本保留历史，完整目录见 BCR_EXCLUDED_BUSINESS_TABLES。",
    "CREATE 原子协议：创建受审计聚合时，必须在同一事务插入结构化业务数据、Root business_change_record(current_version=1)、Root Version 1 CREATE Snapshot、白名单内且本次确需审计的 Child Record/Snapshot 与本地 compensation_event。",
    "Root UPDATE CAS：外部命令必须携带 expected_change_version，固定 next_version=expected_change_version+1；Root business_change_record 必须以 current_version=expected_change_version 条件 CAS 到 next_version，影响行数必须为 1。",
    "受审计 Child 版本指针：只有 Record Type 白名单内且本次发生需留档变化的 Child 才追加最终 Snapshot，并把 current_version 推进到 Root next_version；Root CAS 已串行化同聚合写入，Child current_version 不是独立 CAS Token。",
    "非审计 Child：白名单外 Child 不创建 BCR、不接收 expected_change_version，也不写重复 Snapshot；其 Draft 改动由所属 Root CAS 保护，冻结后历史由结构化 Version 或追加式领域证据保留。",
    "DELETE 顺序：删除白名单内受审计业务行前必须先追加包含稳定身份与删除前安全字段的 DELETE Snapshot，再删除结构化行；Record 永久保留。",
    "System Transition 协议：定时激活、过期、撤销和系统回调必须作为锁内读取 expected_change_version 的 System Command，执行同一 Root CAS/History/Event 协议，不得绕过审计版本。",
    "Multi-Root 原子协议：受影响 Root 必须按 (aggregate_root_type,aggregate_root_id) 固定顺序锁定和 CAS；每个 Root 独立计算 next_version 并写 Root/白名单内 Changed Child Snapshot，所有 Snapshot 使用同一 _meta.triggerId 关联。",
    "整体回滚：任一 Root CAS、业务校验、结构化业务更新、应写 History Snapshot 或 compensation_event 登记失败，整个本地事务必须回滚。",
    "Event 原子与版本：权威业务变化、business_change_record/business_change_version History 与每个 compensation_event 必须在同一本地事务提交；每个 Event.source_version 必须等于它所代表 Root 的 next_version，不得借用其他 Root 版本。",
  ],
  business_change_version: [
    "version_no > 0",
    "operation_type IN (CREATE,UPDATE,DELETE)",
    "64 KiB",
  ],
  compensation_event: [
    "Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。",
    "Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。",
    "Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。",
    "登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。",
    "成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。",
    "失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。",
    "Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。",
  ],
  party_contact: [
    "Email 使用 EMAIL_ADDRESS_V1 标量 Schema",
    "Phone 使用 PHONE_E164_V1 标量 Schema",
  ],
});

const FIELD_COMMENT_CLAUSES = Object.freeze({
  "agreement_party.update_time": "创建时等于create_time；待签时冻结身份，最终签署结果仅允许一次整组原子补齐并更新此时间。",
  "agreement_document_ref.update_time": "INSERT 时必须等于 create_time；合同文件引用冻结后不得更新。",

});

const ACCESS_INDEXES = {
  operating_entity_scope: [{ defKey: "idx_oe_scope_lookup", fields: ["entity_no", "consumer_country_code", "currency", "industry_code", "mcc_code", "status"] }],
  party_profile: [],
  party_relation: [{"defKey": "idx_party_relation_person", "fields": ["related_party_id"]}, {"defKey": "idx_party_relation_application", "fields": ["client_application_id"]}],
  client_business_profile: [],
  client_entity_affiliation: [
    { defKey: "idx_aff_priority", fields: ["client_id", "status", "priority_no", "effective_from", "effective_to"] },
  ],
  entity_source_relation: [
    ["entity_no", "status", "effective_from", "effective_to"],
  ],
  agreement: [
    { defKey: "idx_agreement_client_time", fields: ["client_id", "effective_from", "effective_to"] },
    { defKey: "idx_agreement_partner_time", fields: ["local_partner_id", "effective_from", "effective_to"] },
  ],
  agreement_party: [
    { defKey: "idx_agreement_party", fields: ["party_id"] },
    { defKey: "idx_agreement_profile", fields: ["party_profile_id"] },
  ],
  agreement_operating_entity: [
    { defKey: "idx_agreement_oe_entity", fields: ["entity_no"] },
  ],
  consumer_legal_document: [
    { defKey: "idx_legal_doc_resolve", fields: ["entity_no", "country_code", "locale", "document_type", "effective_from", "effective_to"] },
  ],
  seller_disclosure: [
    { defKey: "idx_seller_disclosure_resolve", fields: ["entity_no", "country_code", "locale", "effective_from", "effective_to"] },
  ],
  commercial_term_version: [
    ["term_plan_id", "status", "effective_from", "effective_to"],
    { defKey: "idx_comm_term_agreement", fields: ["agreement_id"] },
  ],
  payment_method_profile: [
    { defKey: "idx_pm_profile_lookup", fields: ["status", "payment_method_code", "payment_method_profile_code"] },
  ],
  payment_method_profile_cond: [
    { defKey: "idx_pm_cond_lookup", fields: ["dimension_code", "condition_value", "payment_method_profile_id"] },
  ],
  commercial_term_price: [
    { defKey: "idx_term_price_profile", fields: ["payment_method_profile_code", "term_version_id"] },
    { defKey: "idx_term_price_oe", fields: ["entity_no", "term_version_id"] },
  ],
  business_change_record: [
    ["aggregate_root_type", "aggregate_root_id", "history_id"],
  ],
  compensation_event: [
    { defKey: "idx_comp_event_ready", fields: ["execute_status", "next_execute_at", "id"] },
    { defKey: "idx_comp_event_lease", fields: ["execute_status", "lock_until", "id"] },
  ],
  command_idempotency: [["expire_time"]],
  individual_profile: [],

  organization_profile: [],

  party_address: [],

  party_contact: [],

  party_profile_identifier: [{"defKey": "idx_party_profile_ident_8f43e780", "fields": ["identity_key_id"]}],

  party_identity_key: [{"defKey": "idx_party_identity_key_party_id", "fields": ["party_id"]}],

  client_application: [{"defKey": "idx_client_app_requester", "fields": ["requested_by_account_id", "status"]}],

};

// Each tuple is child table, child field, parent table, parent field, child cardinality.
// Only references owned by the Client & Contract database are included.
export const LOCAL_RELATIONS = Object.freeze([
  ["party_profile", "draft_application_id", "client_application", "application_id", "n"],
  ["party_relation", "client_application_id", "client_application", "application_id", "n"],
  ["operating_entity_scope", "entity_no", "operating_entity", "entity_no", "n"],
  ["operating_entity_scope", "application_id", "operating_entity_application", "application_id", "n"],
  ["agreement", "entity_no", "operating_entity", "entity_no", "n"],
  ["agreement_party", "evidence_document_id", "agreement_document_ref", "agreement_document_id", "n"],
  ["commercial_term_plan", "agreement_id", "agreement", "agreement_id", "1"],
  ["commercial_term_plan", "entity_no", "operating_entity", "entity_no", "n"],
  ["agreement", "client_id", "client", "client_id", "n"],
  ["agreement", "local_partner_id", "local_partner", "local_partner_id", "n"],
  ["agreement_party", "agreement_id", "agreement", "agreement_id", "n"],
  ["agreement_party", "party_id", "party", "party_id", "n"],
  ["agreement_party", "party_profile_id", "party_profile", "profile_id", "n"],
  ["agreement_document_ref", "agreement_id", "agreement", "agreement_id", "n"],
  ["agreement_operating_entity", "agreement_id", "agreement", "agreement_id", "n"],
  ["agreement_operating_entity", "entity_no", "operating_entity", "entity_no", "n"],
  ["application_document", "client_application_id", "client_application", "application_id", "n"],
  ["application_document", "partner_application_id", "partner_application", "application_id", "n"],
  ["application_document", "operating_entity_application_id", "operating_entity_application", "application_id", "n"],
  ["application_document", "subject_party_id", "party", "party_id", "n"],
  ["business_change_record", "parent_history_id", "business_change_record", "history_id", "n"],
  ["business_change_version", "history_id", "business_change_record", "history_id", "n"],
  ["client", "party_id", "party", "party_id", "1"],
  ["client_application", "client_id", "client", "client_id", "n"],
  ["client_business_classification", "business_profile_id", "client_business_profile", "business_profile_id", "n"],
  ["client_business_profile", "client_id", "client", "client_id", "n"],
  ["client_entity_affiliation", "client_id", "client", "client_id", "n"],
  ["client_entity_affiliation", "entity_no", "operating_entity", "entity_no", "n"],
  ["commercial_term_component", "term_price_id", "commercial_term_price", "term_price_id", "n"],
  ["commercial_term_component_band", "term_component_id", "commercial_term_component", "term_component_id", "n"],
  ["commercial_term_plan", "client_id", "client", "client_id", "n"],
  ["commercial_term_plan", "local_partner_id", "local_partner", "local_partner_id", "n"],
  ["commercial_term_price", "entity_no", "operating_entity", "entity_no", "n"],
  ["commercial_term_price", "payment_method_profile_code", "payment_method_profile", "payment_method_profile_code", "n"],
  ["commercial_term_price", "term_version_id", "commercial_term_version", "term_version_id", "n"],
  ["commercial_term_version", "agreement_id", "agreement", "agreement_id", "n"],
  ["commercial_term_version", "term_plan_id", "commercial_term_plan", "term_plan_id", "n"],
  ["consumer_legal_document", "entity_no", "operating_entity", "entity_no", "n"],
  ["entity_source_relation", "entity_no", "operating_entity", "entity_no", "n"],
  ["entity_source_relation", "source_client_id", "client", "client_id", "n"],
  ["entity_source_relation", "source_partner_id", "local_partner", "local_partner_id", "n"],
  ["individual_profile", "profile_id", "party_profile", "profile_id", "1"],
  ["local_partner", "party_id", "party", "party_id", "1"],
  ["operating_entity", "party_id", "party", "party_id", "1"],
  ["operating_entity_application", "approved_source_relation_id", "entity_source_relation", "source_relation_id", "n"],
  ["operating_entity_application", "entity_no", "operating_entity", "entity_no", "n"],
  ["operating_entity_application", "party_profile_id", "party_profile", "profile_id", "n"],
  ["operating_entity_application", "source_client_id", "client", "client_id", "n"],
  ["operating_entity_application", "source_partner_id", "local_partner", "local_partner_id", "n"],
  ["organization_profile", "profile_id", "party_profile", "profile_id", "1"],
  ["partner_application", "local_partner_id", "local_partner", "local_partner_id", "n"],
  ["partner_application", "target_party_profile_id", "party_profile", "profile_id", "n"],
  ["party_address", "profile_id", "party_profile", "profile_id", "n"],
  ["party_contact", "profile_id", "party_profile", "profile_id", "n"],
  ["party_identity_key", "party_id", "party", "party_id", "n"],
  ["party_profile", "party_id", "party", "party_id", "n"],
  ["party_profile_identifier", "identity_key_id", "party_identity_key", "identity_key_id", "n"],
  ["party_profile_identifier", "profile_id", "party_profile", "profile_id", "n"],
  ["party_relation", "organization_party_id", "party", "party_id", "n"],
  ["party_relation", "related_party_id", "party", "party_id", "n"],
  ["payment_method_profile_cond", "payment_method_profile_id", "payment_method_profile", "payment_method_profile_id", "n"],
  ["seller_disclosure", "entity_no", "operating_entity", "entity_no", "n"],
]);

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function stableUuid(key) {
  const aliases = { agreement_party: "agreement_signatory", agreement_party_id: "agreement_signatory_id", party_role: "signatory_role" };
  key = key.split("/").map((part) => aliases[part] ?? part).join("/");
  if (key.startsWith("field/") && !key.startsWith("field/operating_entity/") && key.endsWith("/entity_no")) key = key.replace(/entity_no$/, "operating_entity_id");
  const bytes = createHash("sha256").update(`${MODEL_KEY}:${key}`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join("-");
}

function stripMarkdown(value) {
  return value
    .replaceAll("`", "")
    .replaceAll("**", "")
    .replace(/\s+/g, " ")
    .trim();
}

function humanize(value) {
  return value
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function fieldValueDescription(tableKey, fieldKey) {
  const fieldPath = `${tableKey}.${fieldKey}`;
  if (CLOSED_ENUM_VALUES[fieldPath]) {
    return `允许值：${CLOSED_ENUM_VALUES[fieldPath].join("、")}。`;
  }
  if (CONDITIONAL_VALUE_RULES[fieldPath]) {
    const allowedValues = fieldKey.endsWith("_flag") ? "0、1" : "1 或 NULL";
    return `允许值：${allowedValues}。取值规则：${CONDITIONAL_VALUE_RULES[fieldPath]}。`;
  }
  if (OPEN_CODE_SOURCES[fieldPath]) {
    const source = OPEN_CODE_SOURCES[fieldPath].replace(/[；。]/gu, "，");
    return `取值来源：${source}；非固定枚举。`;
  }
  assert(
    !controlledValueCandidate(fieldPath, fieldKey),
    `Controlled value is not classified: ${fieldPath}`,
  );
  return "";
}

function physicalFieldMetadata(table, fieldKey) {
  const fieldPath = `${table.defKey}.${fieldKey}`;
  const common = FIELD_METADATA[fieldKey];
  assert(common, `Missing Chinese field metadata: ${table.defKey}.${fieldKey}`);
  const description =
    FIELD_DESCRIPTION_OVERRIDES[fieldPath] ??
    common.description;
  const valueDescription = fieldValueDescription(table.defKey, fieldKey);
  const generatedClause = FIELD_COMMENT_CLAUSES[fieldPath] ?? "";
  return {
    defName: FIELD_NAME_OVERRIDES[fieldPath] ?? common.defName,
    comment: `${description}。${valueDescription}${generatedClause}`,
  };
}

function tableComment(table) {
  const clauses = [
    ...(VERSION_TABLES.has(table.defKey) ? VERSION_COMMENT_CLAUSES : []),
    ...(TABLE_COMMENT_CLAUSES[table.defKey] ?? []),
  ];
  return [table.purpose, stripMarkdown(table.constraints), ...clauses].join("；");
}

function validateFieldMetadataCatalog(catalog) {
  const tableKeys = new Set(catalog.map((table) => table.defKey));
  const displayNameKeys = Object.keys(TABLE_DISPLAY_NAMES);
  const displayNames = Object.values(TABLE_DISPLAY_NAMES);
  assert(
    displayNameKeys.length === tableKeys.size &&
      displayNameKeys.every((tableKey) => tableKeys.has(tableKey)),
    "Chinese table display names must exactly cover the generated table catalog",
  );
  assert(
    displayNames.every((displayName) => /[\u3400-\u9fff]/u.test(displayName)) &&
      new Set(displayNames).size === displayNames.length,
    "Chinese table display names must be non-empty and unique",
  );

  const fieldPaths = new Set();
  const fieldKeys = new Set();
  for (const table of catalog) {
    for (const fieldKey of [...table.fieldKeys, ...auditFieldsFor(table.defKey)]) {
      fieldKeys.add(fieldKey);
      fieldPaths.add(`${table.defKey}.${fieldKey}`);
    }
  }

  const metadataKeys = Object.keys(FIELD_METADATA);
  assert(
    metadataKeys.length === fieldKeys.size &&
      metadataKeys.every((fieldKey) => fieldKeys.has(fieldKey)),
    "Chinese field metadata must exactly cover the generated field catalog",
  );

  for (const [catalogName, entries] of Object.entries({
    CLOSED_ENUM_VALUES,
    CONDITIONAL_VALUE_RULES,
    FIELD_DESCRIPTION_OVERRIDES,
    FIELD_NAME_OVERRIDES,
    OPEN_CODE_SOURCES,
  })) {
    for (const fieldPath of Object.keys(entries)) {
      assert(fieldPaths.has(fieldPath), `${catalogName} references unknown field: ${fieldPath}`);
    }
  }
  for (const fieldPath of BUSINESS_IDENTIFIER_CODE_PATHS) {
    assert(fieldPaths.has(fieldPath), `Business identifier catalog references unknown field: ${fieldPath}`);
  }

  for (const fieldPath of fieldPaths) {
    const fieldKey = fieldPath.slice(fieldPath.indexOf(".") + 1);
    const classifications = [
      CLOSED_ENUM_VALUES,
      CONDITIONAL_VALUE_RULES,
      OPEN_CODE_SOURCES,
    ].filter((entries) => Object.hasOwn(entries, fieldPath)).length;
    if (controlledValueCandidate(fieldPath, fieldKey)) {
      assert(classifications === 1, `Controlled value must have one classification: ${fieldPath}`);
      continue;
    }
    assert(classifications === 0, `Non-enum field has an enum classification: ${fieldPath}`);
  }
}

export function parseClientContractCatalog(markdown) {
  const sectionStart = markdown.indexOf("## 3.");
  const sectionEnd = markdown.indexOf("## 4.", sectionStart);
  assert(sectionStart >= 0 && sectionEnd > sectionStart, "Unable to locate CCT section 3 catalog");

  const tables = [];
  for (const line of markdown.slice(sectionStart, sectionEnd).split(/\r?\n/)) {
    if (!line.startsWith("| `")) {
      continue;
    }
    const columns = line.split("|");
    if (columns.length !== 6) {
      continue;
    }
    const tableMatch = columns[1].trim().match(/^`([a-z][a-z0-9_]*)`$/);
    if (!tableMatch) {
      continue;
    }
    const fieldKeys = [...columns[3].matchAll(/`([a-z][a-z0-9_]*)`/g)].map(
      (match) => match[1],
    );
    assert(fieldKeys.length > 0, `No fields declared for ${tableMatch[1]}`);
    assert(new Set(fieldKeys).size === fieldKeys.length, `Duplicate fields in ${tableMatch[1]}`);
    tables.push({
      defKey: tableMatch[1],
      purpose: stripMarkdown(columns[2]),
      fieldKeys,
      constraints: columns[4].trim(),
    });
  }

  assert(new Set(tables.map((table) => table.defKey)).size === tables.length, "Duplicate CCT table names");
  assert(
    JSON.stringify(tables.map((table) => table.defKey).sort()) ===
      JSON.stringify(Object.keys(TABLE_DISPLAY_NAMES).sort()),
    "CCT catalog and approved Chinese table metadata must have identical table keys",
  );
  return tables;
}

function parseUniqueKeys(table) {
  const fieldSet = new Set(table.fieldKeys);
  const keys = [];
  const addKey = (fields, explicitName) => {
    assert(fields.length > 0, `Empty unique key declared for ${table.defKey}`);
    assert(
      fields.every((field) => fieldSet.has(field)),
      `Unknown unique key field declared for ${table.defKey}: ${fields.join(",")}`,
    );
    const signature = fields.join(",");
    if (!keys.some((key) => key.fields.join(",") === signature)) {
      keys.push({ fields, explicitName });
    }
  };

  for (const match of table.constraints.matchAll(/`([^`]+)`/g)) {
    const token = match[1].trim();
    const named = token.match(/^(uk_[a-z0-9_]+)\(([^)]+)\)$/);
    if (named) {
      addKey(named[2].split(",").map((field) => field.trim()), named[1]);
    }
  }

  return keys;
}

function indexName(prefix, tableKey, fields) {
  const candidate = `${prefix}_${tableKey}_${fields.join("_")}`;
  if (candidate.length <= 32) {
    return candidate;
  }
  const digest = createHash("sha256").update(candidate).digest("hex").slice(0, 8);
  const tablePartLength = 32 - prefix.length - digest.length - 2;
  return `${prefix}_${tableKey.slice(0, tablePartLength)}_${digest}`;
}

export function physicalTableKey(logicalTableKey) {
  assert(/^[a-z][a-z0-9_]*$/.test(logicalTableKey), `Invalid logical table key: ${logicalTableKey}`);
  return `${PHYSICAL_TABLE_PREFIX}${logicalTableKey}`;
}

export function logicalTableKey(physicalOrLogicalTableKey) {
  return physicalOrLogicalTableKey.startsWith(PHYSICAL_TABLE_PREFIX)
    ? physicalOrLogicalTableKey.slice(PHYSICAL_TABLE_PREFIX.length)
    : physicalOrLogicalTableKey;
}

function isTimeField(fieldKey) {
  return (
    fieldKey.endsWith("_time") ||
    fieldKey.endsWith("_at") ||
    fieldKey.endsWith("_from") ||
    fieldKey.endsWith("_to") ||
    fieldKey.endsWith("_until")
  );
}

function inferPhysicalType(tableKey, fieldKey, primaryKey, typeIds, domainIds) {
  const fieldPath = `${tableKey}.${fieldKey}`;
  const result = {
    type: "VARCHAR",
    len: 64,
    scale: "",
    baseType: typeIds.string,
    domain: "",
  };
  const storage = CLIENT_COMPENSATION_FIELD_STORAGE[fieldPath] ?? CLIENT_ONBOARDING_FIELD_STORAGE[fieldPath];
  if (storage) {
    const mapping = { BIGINT: "BIGINT", VARCHAR: "string", CHAR: "char", DATE: "localDate", DATETIME: "date", DECIMAL: "double", TINYINT: "int", INT: "int" };
    return { ...result, type: storage.type, len: storage.length === "" ? "" : Number(storage.length), scale: storage.scale === "" ? "" : Number(storage.scale),
      baseType: typeIds[mapping[storage.type]], domain: primaryKey ? domainIds.PrimaryKey : storage.type === "DATETIME" ? domainIds.DateTime : MONEY_FIELD_PATHS.has(fieldPath) ? domainIds.Money : RATE_FIELD_PATHS.has(fieldPath) ? domainIds.Decimal : fieldKey === "currency" ? domainIds.currency : "" };
  }
  if (fieldKey === "entity_no") return { ...result, type: "CHAR", len: 5, baseType: typeIds.char };
  if (tableKey === "business_change_record" && ["record_id", "aggregate_root_id"].includes(fieldKey)) return result;
  if (primaryKey || fieldKey === "id" || fieldKey.endsWith("_id")) {
    return { ...result, type: "BIGINT", len: "", baseType: typeIds.BIGINT, domain: primaryKey ? domainIds.PrimaryKey : "" };
  }
  if (EXPLICIT_BIGINT_FIELDS.has(fieldKey)) {
    return { ...result, type: "BIGINT", len: "", baseType: typeIds.BIGINT };
  }
  if (fieldKey.endsWith("_blind_index") || fieldKey.endsWith("_hash")) {
    return { ...result, type: "CHAR", len: 64, baseType: typeIds.char };
  }
  if (MONEY_FIELD_PATHS.has(fieldPath)) {
    return { ...result, type: "DECIMAL", len: 24, scale: 8, baseType: typeIds.double, domain: domainIds.Money };
  }
  if (RATE_FIELD_PATHS.has(fieldPath)) {
    return { ...result, type: "DECIMAL", len: 18, scale: 10, baseType: typeIds.double, domain: domainIds.Decimal };
  }
  if (fieldKey === "currency" || fieldKey === "scope_currency") {
    return { ...result, type: "CHAR", len: 3, baseType: typeIds.char, domain: domainIds.currency };
  }
  if (fieldKey.endsWith("country") || fieldKey.endsWith("country_code")) {
    return { ...result, type: "CHAR", len: 2, baseType: typeIds.char };
  }
  if (JSON_FIELDS.has(fieldKey)) {
    return { ...result, type: "JSON", len: "", baseType: typeIds.largeText };
  }
  if (TEXT_FIELDS.has(fieldKey)) {
    return { ...result, type: "TEXT", len: "", scale: "", baseType: typeIds.largeText };
  }
  if (LOCAL_DATE_FIELDS.has(fieldPath)) {
    return { ...result, type: "DATE", len: "", baseType: typeIds.localDate };
  }
  if (isTimeField(fieldKey)) {
    return { ...result, type: "DATETIME", len: 6, baseType: typeIds.date, domain: domainIds.DateTime };
  }
  if (INTEGER_FIELDS.has(fieldKey)) {
    return { ...result, type: "INT", len: "", baseType: typeIds.int };
  }
  if (FLAG_OR_SLOT_FIELDS.has(fieldKey)) {
    return { ...result, type: "TINYINT", len: 1, baseType: typeIds.int };
  }
  return { ...result, len: VARCHAR_LENGTH_OVERRIDES.get(fieldPath) ?? result.len };
}

function auditFieldsFor(tableKey) {
  return CREATE_ONLY_AUDIT_TABLES.has(tableKey)
    ? ["create_time"]
    : AUDIT_FIELDS;
}

function isNullable(tableKey, fieldKey, primaryKey) {
  const fieldPath = `${tableKey}.${fieldKey}`;
  const storage = CLIENT_COMPENSATION_FIELD_STORAGE[fieldPath] ?? CLIENT_ONBOARDING_FIELD_STORAGE[fieldPath];
  if (storage) return storage.nullable;
  assert(!primaryKey || !NULLABLE_FIELD_PATHS.has(fieldPath), `Primary key cannot be nullable: ${fieldPath}`);
  assert(!AUDIT_FIELDS.includes(fieldKey) || !NULLABLE_FIELD_PATHS.has(fieldPath), `Audit field cannot be nullable: ${fieldPath}`);
  return NULLABLE_FIELD_PATHS.has(fieldPath);
}

function normalizeTemplate(template, service) {
  const model = clone(template);
  model.name = service.modelName;
  model.describe = service.describe;
  model.dbConn = [];
  model.dbConns = [];
  model.profile.default.dbConn = "";

  const mappingByKey = new Map(
    model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]),
  );
  mappingByKey.get("BIGINT")[JAVA_MAPPING_ID] = "Long";
  mappingByKey.get("BIGINT")[MYBATIS_MAPPING_ID] = "Long";
  mappingByKey.get("double")[JAVA_MAPPING_ID] = "BigDecimal";
  mappingByKey.get("double")[MYBATIS_MAPPING_ID] = "BigDecimal";
  mappingByKey.get("date")[JAVA_MAPPING_ID] = "Instant";
  mappingByKey.get("date")[MYBATIS_MAPPING_ID] = "Instant";
  if (mappingByKey.has("char")) {
    mappingByKey.get("char")[MYBATIS_MAPPING_ID] = "String";
  }
  assert(!mappingByKey.has("localDate"), "Template unexpectedly defines localDate mapping");
  const localDateMapping = {
    defKey: "localDate",
    id: stableUuid("type-mapping/localDate"),
    defName: "本地日期",
    [MYSQL_MAPPING_ID]: "DATE",
    [JAVA_MAPPING_ID]: "LocalDate",
    [MYBATIS_MAPPING_ID]: "LocalDate",
  };
  model.dataTypeMapping.mappings.push(localDateMapping);
  mappingByKey.set("localDate", localDateMapping);

  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
  domains.get("Money").len = 24;
  domains.get("Money").scale = 8;
  domains.get("Decimal").len = 18;
  domains.get("Decimal").scale = 10;
  domains.get("DateTime").len = 6;
  domains.get("currency").applyFor = mappingByKey.get("char").id;
  domains.get("currency").len = 3;

  for (const auditField of model.profile.default.entityInitFields) {
    const auditMetadata = FIELD_METADATA[auditField.defKey];
    assert(auditMetadata, `Missing audit field metadata: ${auditField.defKey}`);
    auditField.type = "DATETIME";
    auditField.len = 6;
    auditField.scale = "";
    auditField.notNull = true;
    auditField.autoIncrement = false;
    auditField.defName = auditMetadata.defName;
    auditField.comment = `${auditMetadata.description}。`;
  }

  const ruleById = new Map(model.namingRules.map((rule) => [rule.id, rule]));
  for (const id of [
    "668CBEE6-E0B7-4ACE-B72E-63942963B191",
    "11BD987F-82E7-418E-A752-FDD84F1582A2",
    "29D0A8D9-ABE2-451F-8A39-52FAB02E62B9",
  ]) {
    const rule = ruleById.get(id);
    if (rule) {
      const objectName = rule.applyFieldType === "index" ? "index" : rule.applyFieldType;
      rule.programCode = `return (data.${objectName}.defKey||\"\").length <= 32`;
    }
  }
  const primaryKeyRule = ruleById.get("2BAB122B-8811-40BB-89F3-CDC24B5862D3");
  if (primaryKeyRule) {
    primaryKeyRule.defName = "OE使用不可变CHAR(5)主键，其余主键为非自增Snowflake BIGINT";
    primaryKeyRule.programCode = "let fields = data.entity.fields;\nfor(let i=0;i<fields.length;i++){\n  let field = fields[i];\n  if(field.primaryKey){\n    let type=(field.type||field.dbType||\"\").toUpperCase();\n    return !field.autoIncrement && (data.entity.defKey===\"t_operating_entity\" ? field.defKey===\"entity_no\" && type===\"CHAR\" && Number(field.len)===5 : type===\"BIGINT\");\n  }\n}\nreturn false;";
  }
  const auditFieldsRule = ruleById.get("0B2F0BD2-3B84-4AB1-BA29-9DE9620AF608");
  if (auditFieldsRule) {
    auditFieldsRule.defName = "可变表必须有 create_time/update_time，变更明细只保留 create_time";
    auditFieldsRule.programCode = "let actual=data.entity.fields.map(field=>(field.defKey||\"\").toLowerCase());\nlet required=data.entity.defKey===\"t_business_change_version\"?[\"create_time\"]:[\"create_time\",\"update_time\"];\nreturn required.every(field=>actual.indexOf(field)>=0) && (data.entity.defKey!==\"t_business_change_version\" || actual.indexOf(\"update_time\")<0);";
  }
  const tablePrefixRule = ruleById.get("1168C7C2-8E8E-4FB7-B639-B3DE839C395A");
  if (tablePrefixRule) {
    tablePrefixRule.defName = "物理表名使用 t_ 前缀和小写 snake_case";
    tablePrefixRule.programCode = "return /^t_[a-z][a-z0-9_]*$/.test(data.entity.defKey);";
  }
  const indexPrefixRule = ruleById.get("023450B3-AAE2-4DC1-AE63-2196DD82823D");
  if (indexPrefixRule) {
    indexPrefixRule.defName = "唯一索引以 uk_ 开头，普通索引以 idx_ 开头";
    indexPrefixRule.programCode = "if(data.index.unique){\n    return data.index.defKey.indexOf(\"uk_\")==0;\n}else{\n    return data.index.defKey.indexOf(\"idx_\")==0;\n}";
  }
  const fieldReservedWordRule = ruleById.get("BEC54F19-52D5-4882-BCE1-4439785F8001");
  if (fieldReservedWordRule) {
    // The generic template includes STATUS although it is not a MySQL 8 reserved word.
    // Keep the rest of the project-vetted list without rejecting canonical status fields.
    fieldReservedWordRule.programCode = fieldReservedWordRule.programCode.replace(
      ",status,",
      ",",
    );
  }
  const timeRule = ruleById.get("D330BCC3-DBAB-4677-8C5A-A301003A5878");
  if (timeRule) {
    timeRule.defName = "时间字段使用 DATETIME(6)";
    timeRule.programCode = "let key=(data.field.defKey||\"\").toLowerCase();\nif(key.endsWith(\"_time\")||key.endsWith(\"_at\")||key.endsWith(\"_from\")||key.endsWith(\"_to\")||key.endsWith(\"_until\")){\n  let type=(data.field.type||data.field.dbType||\"\").toUpperCase();\n  return type===\"DATETIME\" && parseInt(data.field.len)===6;\n}\nreturn true;";
  }
  const controlledTextRule = ruleById.get("1C563E17-262B-4EB6-87F0-203CAC667CF0");
  if (controlledTextRule) {
    controlledTextRule.programCode = "let key=(data.field.defKey||\"\").toLowerCase();\nlet type=(data.field.type||data.field.dbType||\"\").toLowerCase();\nif(type===\"blob\"){\n  return false;\n}\nif(type===\"text\"){\n  return key===\"last_error_summary\";\n}\nreturn true;";
  }
  const largeFieldRule = ruleById.get("1C563E17-262B-4EB6-87F0-203CAC667CF0");
  if (largeFieldRule) {
    largeFieldRule.defName = "仅允许获批的受控 TEXT 字段且禁止 BLOB";
    largeFieldRule.programCode = "let key=(data.field.defKey||\"\").toLowerCase();\nlet type=(data.field.dbType||\"\").toLowerCase();\nif(type===\"blob\"){\n  return false;\n}\nif(type===\"text\"){\n  return key===\"last_error_summary\";\n}\nreturn true;";
  }
  const mysqlDdlTemplate = model.profile.codeTemplates.find((templateSpec) => templateSpec.type === "dbDDL");
  const typeLengthTemplate = "{{=field.type}}{{?field.len>0}}{{='('}}{{=field.len}}{{?field.scale>0}}{{=','}}{{=field.scale}}{{?}}{{=')'}}{{?}}";
  if (!mysqlDdlTemplate?.createTable.includes(typeLengthTemplate)) throw new Error("MySQL DDL template lacks controlled type fragment");
  mysqlDdlTemplate.createTable = mysqlDdlTemplate.createTable.replace(
    typeLengthTemplate,
    `${typeLengthTemplate}{{? field.extProps && field.extProps.characterSet }} CHARACTER SET {{=field.extProps.characterSet}} COLLATE {{=field.extProps.collation}}{{?}}`,
  );
  return model;
}

export function buildIndexes(table, fieldsByKey, accessIndexSpecs = ACCESS_INDEXES[table.defKey] ?? []) {
  const specs = [];
  for (const unique of parseUniqueKeys(table)) {
    specs.push({
      fields: unique.fields,
      unique: true,
      defKey: unique.explicitName ?? indexName("uk", table.defKey, unique.fields),
    });
  }
  for (const spec of accessIndexSpecs) {
    const access = Array.isArray(spec)
      ? { fields: spec, defKey: indexName("idx", table.defKey, spec) }
      : spec;
    specs.push({
      fields: access.fields,
      unique: false,
      defKey: access.defKey,
    });
  }

  const primaryKey = table.fieldKeys[0];
  const relationFields = LOCAL_RELATIONS
    .filter(([childKey]) => childKey === table.defKey)
    .map(([, childFieldKey]) => childFieldKey);
  for (const fieldKey of relationFields) {
    const coveredByLeftPrefix =
      fieldKey === primaryKey || specs.some((spec) => spec.fields[0] === fieldKey);
    if (coveredByLeftPrefix) {
      continue;
    }
    specs.push({
      fields: [fieldKey],
      unique: false,
      defKey: indexName("idx", table.defKey, [fieldKey]),
    });
  }

  const signatures = new Set();
  const names = new Set();
  return specs.filter((spec) => {
    assert(typeof spec.defKey === "string" && spec.defKey.length > 0, `Index name is required: ${table.defKey}`);
    assert(/^(?:uk|idx)_[a-z0-9_]+$/.test(spec.defKey), `Invalid index name: ${table.defKey}.${spec.defKey}`);
    assert(spec.defKey.length <= 32, `Index name exceeds 32 characters: ${spec.defKey}`);
    assert(spec.fields.length > 0, `Index must include fields: ${table.defKey}.${spec.defKey}`);
    assert(spec.fields.every((field) => fieldsByKey.has(field)), `Unknown index field in ${table.defKey}.${spec.defKey}`);
    assert(!names.has(spec.defKey), `Duplicate index name in ${table.defKey}: ${spec.defKey}`);
    names.add(spec.defKey);
    const signature = `${spec.unique}:${spec.fields.join(",")}`;
    assert(!signatures.has(signature), `Duplicate index field signature in ${table.defKey}: ${signature}`);
    signatures.add(signature);
    return true;
  }).map((spec) => ({
    defKey: spec.defKey,
    defName: humanize(spec.defKey),
    unique: spec.unique,
    comment: `${spec.unique ? "Unique" : "Access"} index for ${table.defKey} on ${spec.fields.join(", ")}`,
    fields: spec.fields.map((fieldKey, position) => ({
      fieldDefKey: fieldsByKey.get(fieldKey).id,
      ascOrDesc: "A",
      id: stableUuid(`index-field/${table.defKey}/${spec.defKey}/${position}/${fieldKey}`),
    })),
    id: stableUuid(`index/${table.defKey}/${spec.defKey}`),
  }));
}

function buildEntities(model, catalog) {
  const typeIds = Object.fromEntries(
    model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]),
  );
  const domainIds = Object.fromEntries(
    model.domains.map((domain) => [domain.defKey, domain.id]),
  );

  return catalog.map((table) => {
    const fieldKeys = [...table.fieldKeys];
    for (const auditField of auditFieldsFor(table.defKey)) {
      if (!fieldKeys.includes(auditField)) {
        fieldKeys.push(auditField);
      }
    }
    const fields = fieldKeys.map((fieldKey, position) => {
      const primaryKey = position === 0;
      const physicalType = inferPhysicalType(table.defKey, fieldKey, primaryKey, typeIds, domainIds);
      const fieldMetadata = physicalFieldMetadata(table, fieldKey);
      return {
        defKey: fieldKey,
        defName: fieldMetadata.defName,
        comment: fieldMetadata.comment,
        type: physicalType.type,
        len: physicalType.len,
        scale: physicalType.scale,
        primaryKey,
        notNull: !isNullable(table.defKey, fieldKey, primaryKey),
        autoIncrement: false,
        defaultValue: CLIENT_COMPENSATION_FIELD_STORAGE[`${table.defKey}.${fieldKey}`]?.defaultValue ?? "",
        hideInGraph: false,
        refDict: "",
        baseType: physicalType.baseType,
        extProps: CLIENT_COMPENSATION_FIELD_STORAGE[`${table.defKey}.${fieldKey}`]?.extProps ?? (fieldKey === "entity_no" || (table.defKey === "business_change_record" && ["record_id", "aggregate_root_id"].includes(fieldKey))
          ? { characterSet: "ascii", collation: "ascii_bin" } : {}),
        domain: physicalType.domain,
        id: stableUuid(`field/${table.defKey}/${fieldKey}`),
      };
    });
    const fieldsByKey = new Map(fields.map((field) => [field.defKey, field]));
    return {
      id: stableUuid(`entity/${table.defKey}`),
      env: { base: { nameSpace: "", codeRoot: "" } },
      defKey: physicalTableKey(table.defKey),
      defName: TABLE_DISPLAY_NAMES[table.defKey],
      comment: tableComment(table),
      properties: {},
      sysProps: { nameTemplate: "{defKey}[{defName}]" },
      notes: {},
      headers: clone(model.profile.headers),
      fields,
      correlations: [],
      indexes: buildIndexes(table, fieldsByKey),
      type: "P",
    };
  });
}

// A relation is a model correlation only when both tables belong to the same
// service database. Cross-database references keep their field and the access
// index derived from LOCAL_RELATIONS, but never become a PDManer correlation.
export function modelRelations(tableKeys) {
  const owned = new Set(tableKeys);
  return LOCAL_RELATIONS.filter(([childKey, , parentKey]) => owned.has(childKey) && owned.has(parentKey));
}

function applyRelations(entities, relations) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  for (const [childKey, childFieldKey, parentKey, parentFieldKey, childRows] of relations) {
    const child = entitiesByKey.get(childKey);
    const parent = entitiesByKey.get(parentKey);
    assert(child && parent, `Unknown local relation ${childKey}.${childFieldKey} -> ${parentKey}.${parentFieldKey}`);
    const childField = child.fields.find((field) => field.defKey === childFieldKey);
    const parentField = parent.fields.find((field) => field.defKey === parentFieldKey);
    assert(childField && parentField, `Unknown local relation field ${childKey}.${childFieldKey} -> ${parentKey}.${parentFieldKey}`);
    child.correlations.push({
      myField: childField.id,
      refEntity: parent.id,
      refField: parentField.id,
      myRows: childRows,
      refRows: "1",
      innerType: "",
    });
  }
}

function buildDiagrams(entities, diagramGroups, relations) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  return diagramGroups.map((group) => {
    const primaryTableSet = new Set(group.tables);
    const tableKeys = [...group.tables];
    for (const [childKey, , parentKey] of relations) {
      if (primaryTableSet.has(childKey) && !tableKeys.includes(parentKey)) {
        tableKeys.push(parentKey);
      }
    }
    const columnCount = 4;
    const tableHeights = tableKeys.map((tableKey) => {
      const entity = entitiesByKey.get(tableKey);
      assert(entity, `Unknown diagram table ${group.defKey}.${tableKey}`);
      return Math.max(220, 86 + entity.fields.length * 22);
    });
    const rowOffsets = [];
    for (let row = 0, y = 0; row * columnCount < tableKeys.length; row += 1) {
      rowOffsets.push(y);
      const first = row * columnCount;
      const rowHeight = Math.max(...tableHeights.slice(first, first + columnCount));
      y += rowHeight + 60;
    }
    const cellByTable = new Map();
    const cells = tableKeys.map((tableKey, index) => {
      const entity = entitiesByKey.get(tableKey);
      const cell = {
        id: stableUuid(`diagram/${group.defKey}/table/${tableKey}`),
        shape: "table",
        position: {
          x: (index % columnCount) * 430,
          y: rowOffsets[Math.floor(index / columnCount)],
        },
        count: 0,
        originKey: entity.id,
        type: "P",
        size: {
          width: 390,
          height: Math.max(220, 86 + entity.fields.length * 22),
        },
      };
      cellByTable.set(tableKey, cell);
      return cell;
    });

    for (const [childKey, childFieldKey, parentKey, parentFieldKey, childRows] of relations) {
      if (!primaryTableSet.has(childKey)) {
        continue;
      }
      const child = entitiesByKey.get(childKey);
      const parent = entitiesByKey.get(parentKey);
      const childField = child.fields.find((field) => field.defKey === childFieldKey);
      const parentField = parent.fields.find((field) => field.defKey === parentFieldKey);
      cells.push({
        id: stableUuid(`diagram/${group.defKey}/relation/${childKey}/${childFieldKey}/${parentKey}/${parentFieldKey}`),
        shape: "erdRelation",
        source: {
          cell: cellByTable.get(childKey).id,
          port: `${childField.id}%out`,
        },
        target: {
          cell: cellByTable.get(parentKey).id,
          port: `${parentField.id}%in`,
        },
        relation: `${childRows}:1`,
        vertices: [],
        fillColor: "#ACDAFC",
        attrs: { line: { strokeDasharray: "" } },
        isLock: true,
      });
    }

    return {
      defKey: group.defKey,
      defName: group.defName,
      relationType: "field",
      canvasData: { cells },
      id: stableUuid(`diagram/${group.defKey}`),
      comment: `${group.defName} bounded-context view`,
    };
  });
}

function validateGeneratedModel(model, expectedTableKeys, relations) {
  assert(
    JSON.stringify(model.entities.map((entity) => entity.defKey).sort()) ===
      JSON.stringify(expectedTableKeys.map((tableKey) => physicalTableKey(tableKey)).sort()),
    "Generated model must exactly cover its owned tables of the parsed approved catalog",
  );
  const entityIds = new Set(model.entities.map((entity) => entity.id));
  const covered = new Set();
  let relationCount = 0;
  for (const entity of model.entities) {
    assert(entity.fields.filter((field) => field.primaryKey).length === 1, `${entity.defKey} must have one primary key`);
    const logicalKey = logicalTableKey(entity.defKey);
    const expectedAuditFields = auditFieldsFor(logicalKey);
    assert(expectedAuditFields.every((key) => entity.fields.some((field) => field.defKey === key)), `${entity.defKey} lacks audit fields`);
    if (CREATE_ONLY_AUDIT_TABLES.has(logicalKey)) {
      assert(!entity.fields.some((field) => field.defKey === "update_time"), `${entity.defKey} must remain immutable`);
    }
    const fieldIds = new Set(entity.fields.map((field) => field.id));
    for (const index of entity.indexes) {
      assert(index.fields.every((field) => fieldIds.has(field.fieldDefKey)), `Dangling index in ${entity.defKey}`);
    }
    for (const correlation of entity.correlations) {
      relationCount += 1;
      assert(fieldIds.has(correlation.myField), `Dangling relation field in ${entity.defKey}`);
      assert(entityIds.has(correlation.refEntity), `Cross-model relation from ${entity.defKey}`);
    }
  }
  assert(relationCount === relations.length, `Expected ${relations.length} local relations, found ${relationCount}`);
  for (const diagram of model.diagrams) {
    for (const cell of diagram.canvasData.cells) {
      if (cell.shape === "table") {
        covered.add(cell.originKey);
      }
    }
  }
  assert(covered.size === model.entities.length, `Diagrams cover ${covered.size} of ${model.entities.length} tables`);
}

// Builds one service database model from the shared Client & Contract catalog.
// service = { label, modelName, describe, outputPath, tables, diagramGroups,
// additionalEntities(model) }. Field metadata stays validated against the
// complete catalog so the two service models cannot drift from the shared source.
export function generateServiceModel(service, {
  template = JSON.parse(readFileSync(templatePath, "utf8")),
  catalogMarkdown = readFileSync(catalogPath, "utf8"),
} = {}) {
  const model = normalizeTemplate(template, service);
  const catalog = parseClientContractCatalog(catalogMarkdown);
  validateFieldMetadataCatalog(catalog);
  const owned = new Set(service.tables);
  const catalogKeys = new Set(catalog.map((table) => table.defKey));
  for (const tableKey of service.tables) {
    assert(catalogKeys.has(tableKey), `${service.label} owns a table missing from the catalog: ${tableKey}`);
  }
  const ownedCatalog = catalog.filter((table) => owned.has(table.defKey));
  const additionalEntities = service.additionalEntities?.(model) ?? [];
  model.entities = [...buildEntities(model, ownedCatalog), ...additionalEntities];
  const tableKeys = model.entities.map((entity) => logicalTableKey(entity.defKey));
  const relations = modelRelations(tableKeys);
  applyRelations(model.entities, relations);
  model.diagrams = buildDiagrams(model.entities, service.diagramGroups, relations);
  validateGeneratedModel(model, tableKeys, relations);
  return model;
}

export function serializeModel(model) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

export function runGeneratorCli(service, generate) {
  const outputPath = path.join(workspaceRoot, service.outputPath);
  const unknownArguments = process.argv
    .slice(2)
    .filter((argument) => argument !== "--check");
  if (unknownArguments.length > 0) {
    console.error(`Unknown generator argument: ${unknownArguments[0]}`);
    process.exitCode = 1;
    return;
  }

  const serialized = serializeModel(generate());
  if (process.argv.includes("--check")) {
    if (!existsSync(outputPath)) {
      console.error(`${service.label} model is missing: ${outputPath}`);
      process.exitCode = 1;
      return;
    }
    const current = readFileSync(outputPath, "utf8");
    if (current !== serialized) {
      console.error(`${service.label} model is out of date; run the generator without --check`);
      process.exitCode = 1;
      return;
    }
    console.log(`${service.label} model is up to date`);
    return;
  }

  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, serialized);
  console.log(`Generated ${outputPath}`);
}
