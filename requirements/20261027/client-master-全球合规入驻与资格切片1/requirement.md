# client-master / compliance：全球合规入驻与资格 · 切片 1 · 需求

> 状态：**阶段 1 评审稿（2026-10-27）**。确认记录见 [delivery.md](./delivery.md)，差异与技术方案见 [technique.md](./technique.md)（阶段 2 起填写）。
> 依据：[2026-10-27 客户入驻与资格域决定](../../../agent-docs/decisions/2026-10-27-client-onboarding-global-kyc-kyb.md)（Proposed，①—⑦、Q1—Q17、落地前提）；完整设计 [design-discuss/client-onboarding/design.md](../../../design-discuss/client-onboarding/design.md)（下文简称"设计"，引用其节号）。本需求是设计第 17 节切片 **S1 + S1 补充（手工 CRR、筛查 Mock）+ S1b（入驻码与责任方）+ S2 的证件 Vault 部分**；修订申请、真实筛查、自动 CRR、持续复审、IDV、PayFac 计费、Partner 门户工作台分别另立需求。

## 1. 背景

- 现有入驻只支持一套全局表单：必填项写死在 `ClientSubmissionValidator`，组织类型 / 标识类型 / 行业目录在 client-master `application.yml`，地址西式六列，没有辖区维度；证件号在 `t_party_profile_identifier.identifier_value` 与 `t_party_identity_key.normalized_identifier_value` **明文**，提交时明文发 compliance。
- compliance 程序只按资格类型一个全局程序；案件 9 态只用 4 态；`t_dd_check`、`t_screening_*`、`t_dd_party_snapshot` 只有表没有代码；没有风险评级、没有责任方、没有重提多轮。
- 业务要求：面向 AE / SA / EG 三国的合规入驻（各国证件、登记号、UBO 规则、地址、材料不同），KYC / KYB 由 PayFac 审核决定、平台提供系统；后续扩展到任意国家时不改代码（设计 10.0、10.0.1、10.0.1a）。
- 存量 local / mor-test 数据按重写处理，不迁移（用户 2026-10-27 决定）。

## 2. 目标

1. 入驻资料按 **辖区 × 主体类型 × 角色（× 组织类型）** 的采集 Schema 采集与校验；AE / SA / EG 三套国家包（辖区、规则开关、目录、Schema、程序）以 DML 预置；Client 门户按 Schema 动态渲染。
2. compliance 程序辖区化、案件完整状态机与同案件多轮、材料 / 人工检查 / 筛查（Mock + 人工兜底）/ 手工 CRR / 推荐与决定四眼；决定冻结 CRR 与责任方；资格投影与事件。
3. 个人证件号进 Vault（`LEGAL_IDENTIFIER_V1`），库内只有 Token 与脱敏；机构登记号明文。
4. Client 创建申请时填 PayFac 入驻码确定责任方；案件按责任方隔离；首期由平台运营在 Operations 控制台代所有 PayFac 操作并记 `on_behalf_of_partner_id`。
5. 新国家零代码的可证明性：CI 中以虚构国家 `ZZ` 的纯 DML 跑通入驻—审核—批准全链。

## 3. 概念

| 术语 | 含义 | 设计节 |
|---|---|---|
| 辖区包 | `t_jurisdiction` 一行 + 其 `t_rule_setting` + 目录 + Schema + 程序 | 10.0 / 10.1 |
| 规则开关 | `t_rule_setting(scope_type, scope_code, rule_code, rule_value)`；代码只读已注册 `rule_code` | 10.1 注册表 |
| 采集 Schema | `t_onboarding_schema(_field)`：字段、控件、必填级别、二选一组、可见条件、文案键 | 10.1 |
| 需求清单 | `GET …/requirements` 实时计算的"缺什么 / 谁动 / 何时到期" | 8 / SM-Q |
| CRR | 客户风险评级 LOW / MEDIUM / HIGH；S1 手工必填 | 7.5 |
| 责任方 / 操作方 | `responsible_partner_id`（入驻码解析的 PayFac）/ `operated_by`（PLATFORM / PARTNER） | 14 |
| 入驻码 | `t_partner_onboarding_code`，PayFac 线下发给商户 | SM-O |

## 4. 本期范围

| 编号 | 内容 | 设计节 |
|---|---|---|
| R1 | client-master：辖区包与目录表（`t_jurisdiction`、`t_rule_setting`、`t_organization_type`、`t_identifier_type`、`t_relation_type`、`t_address_format_component`、`t_attribute_definition`、`t_party_attribute`、`t_onboarding_schema`、`t_onboarding_schema_field`）；profile / address / relation / application / client 加列；`t_application_declaration`；删除 `application.yml` 的 `onboarding.form-catalog`；AE / SA / EG + `ZZ` 国家包 DML | 10.1、9 |
| R2 | client-master：Schema 驱动的提交校验（格式 / 必填 / 校验位 / 到期 / 关系门槛 / 二选一 / 声明）与 `form-schema`、`jurisdictions`、`requirements`、`declarations` 接口；需求级 422 错误（`errors[{requirementKey, code, reason, remediation}]`） | 8、11.1 |
| R3 | client-master：个人证件号 Vault 化（`LEGAL_IDENTIFIER_V1` STABLE Token 兼唯一键、脱敏末 4 位）；I01 只传 Token；Vault 数据类型与访问规则 DML | 12 |
| R4 | client-master：入驻码表与运营代签发 / 作废接口；创建申请时解析 `responsible_partner_id`；`ONBOARDING_CODE_INVALID`；Client 撤回申请（WITHDRAWN，本期新增） | SM-A、SM-O |
| R5 | compliance：程序加 辖区 / 主体类型 / 角色 / 档次 / Owner 与 `programs/resolve`；要求加主体角色 / 时效 / 允许文件类型子表 / 可豁免；检查项（MANUAL 首期；AUTOMATED 定义预留）；筛查项含 `MANUAL_SCREENING` 人工项；AE / SA / EG + `ZZ` 程序 DML | 10.2 |
| R6 | compliance：案件完整状态机（含 WAITING_EVIDENCE / PENDING_DEPENDENCY / EXPIRED）、同案件多轮 `round_no`、责任方 / 操作方 / `on_behalf_of_partner_id`、证据拒绝原因、人工检查结果录入、筛查 Mock 渠道执行与命中处置、手工 CRR（因子 + 评级必填）、推荐 / 决定四眼（程序可关）、退回带需求级 errors[] 与期限、退回超期 EXPIRED | 6.1、SM-D…K |
| R7 | compliance：决定冻结 tier / CRR / 辖区 / 责任方 / 推荐人 / 决定人；资格投影 + `QualificationChanged`；`ClientReviewDecided` v2（errors[]、tier、riskRating、roundNo、outcome 增 EXPIRED）；资格 REVOKED → client-master 自动 Client SUSPENDED（Q15）；案件 EXPIRED → 申请 WITHDRAWN（Q16） | 11.3 |
| R8 | channel-connector：`SCREENING_SEARCH` 标准 API 定义 + 显式 Mock 渠道 `S_SCREENING_MOCK`（仅 local / mor-test，按请求配置 DTO）；compliance 唯一调用方授权 | 13、17a |
| R9 | Operations 控制台：国家包 / 目录 / Schema / 程序的查看与版本发布（首期 DML 导入 + 页面只读与发布）；案件工作台（材料审核、人工检查、命中处置、分类确认、CRR、推荐 / 决定、退回；按责任 PayFac 过滤与代操作）；Client 列表 / 详情（主档、关系人、申请、资格）；入驻码签发 / 作废；Platform 注册块（候选 138，阶段 2 核对 live mor_platform） | 15 |
| R10 | Client 门户：入驻单页改 Schema 驱动渲染（通用渲染器 + 控件注册表：TEXT / TEXTAREA / DATE / COUNTRY / SELECT / IDENTIFIER / ADDRESS(按组件表) / NAME / PERSON_LIST / DOCUMENT_SLOT / DECLARATION / URL / MONEY）、入驻码输入、需求清单面板、声明勾选、撤回；Platform 多语言键 | 15 |
| R11 | 文档同步：PDManer 两库、Schema SQL、`agent-docs/database/*`、CURRENT_IMPLEMENTATION、Vault 架构文档例外、决定记录状态 | 18 |

## 5. 不做项

| 项 | 原因 / 去向 |
|---|---|
| 修订申请（PROFILE_AMENDMENT）、`t_materiality_rule` | S2 |
| 真实筛查供应商、持续监控拉取、`t_screening_monitor` | S3 |
| 自动 CRR 评分规则 `t_dd_risk_rating_rule`、档次自动切换 | S4（本期手工评级 + 程序按档次解析保留） |
| 复审触发 `t_review_trigger`、定期 / 到期 / 变更触发、结算控制实现 | S5 |
| GOV_ID_VERIFY / KYB_REGISTRY_LOOKUP / TAX_ID_VERIFY / ADDRESS_VERIFY / IDV 的真实渠道与 `t_dd_check_finding` | S6（标准 API 定义可随 R8 一并登记） |
| PayFac 程序定制、证据包导出、计费事件与 commercial 产品 | S7 |
| Partner / OE 申请走新程序链 | S8 |
| Partner 门户案件工作台与自助入驻码 | S-P |
| 存量数据迁移 | 不做（重写） |
| 法务 26 项的结论 | 不阻塞开发；未确认的国家包行 DRAFT 入库不发布 |
| Platform workflow 接入四眼 | Q2 不接 |

## 6. 业务规则（草案）

- B1 辖区是数据不是分支：代码不得出现按辖区 / 组织类型 / 标识类型取值的分支；校验位算法按 `checksum_rule` 查注册表；`t_rule_setting` 未注册 `rule_code` 忽略并告警（设计 10.0.1a）。
- B2 提交校验顺序：字段格式与必填（Schema）→ 标识格式 / 校验位 / 到期 / 冲突（目录 + identity_key）→ 关系门槛（规则开关：UBO 阈值与兜底、控制人、签字人、经理、`UBO_COLLECTION_MODE`）→ 材料槽（程序解析）→ 声明；真伪判断不在 client-master。
- B3 需求清单实时计算、不落表；带期限的项来自案件 errors[]。
- B4 证件：个人证件 `VAULT_REQUIRED=1` 的类型只存 Token + 脱敏；identity_key 以 Token 建唯一键；日志 / MQ / 补偿载荷不含原值；I01 只传 Token；compliance 审核 Reveal 仅 `COMPLIANCE_REVIEW` 目的并审计。
- B5 入驻码：创建申请时校验 ACTIVE 且在有效期；无效只回 `ONBOARDING_CODE_INVALID`；不提供 Partner 查询；无码 = 平台自营（`responsible_partner_id` 空）。
- B6 案件：I01 幂等键 `source_submission_id`；退回后重提同案件 `round_no+1`，已 ACCEPTED 的要求与有效期内 PASS 的检查复用；批准门禁 = 必需要求 ACCEPTED | WAIVED、阻断检查 PASS、无 OPEN 命中、子案件全部 APPROVED、分类已确认、CRR 已评、四眼时 decided_by ≠ recommended_by（程序 `four_eyes_required=0` 时记 `four_eyes_waived=1`）。
- B7 筛查 Mock：`provider_code` 记录 channel-connector 渠道码，Mock 为 `S_SCREENING_MOCK`（2026-10-27 验收对齐），只在 local / mor-test；程序含 `MANUAL_SCREENING` 人工检查项（审核员录名单版本与结论）作为生产兜底；不虚构外部筛查成功。
- B8 责任方与隔离：案件 / 决定 / 资格冻结 `responsible_partner_id`；运营代操作记 `operated_by=PLATFORM` + `on_behalf_of_partner_id`；查询接口按责任方过滤（本期运营全可见，Partner 门户不开放）。
- B9 联动：`ClientReviewDecided(APPROVED)` 同事务激活 Client、冻结 Profile；`(RETURNED)` 申请回 RETURNED 并带 errors[] 与期限；`(REJECTED)` 终态；`(EXPIRED)` 申请 WITHDRAWN；`QualificationChanged(REVOKED)` → Client SUSPENDED；结果事件 submissionId / caseId 非当前则 STALE 忽略。
- B10 版本冻结：申请冻结 `schema_id`，案件冻结 `dd_program_version_id`；Schema / 程序版本发布（四眼）不影响在途。

## 7. 逐系统变动点（阶段 1 清单，阶段 2 核对代码后修订）

### 7.1 client-master（`mor_client_master`）

| 变动点 | 内容 |
|---|---|
| 新表 10 张 | `t_jurisdiction`、`t_rule_setting`、`t_organization_type`、`t_identifier_type`、`t_relation_type`、`t_address_format_component`、`t_attribute_definition`、`t_party_attribute`、`t_onboarding_schema`、`t_onboarding_schema_field`（字段见设计 10.1） |
| 新表 2 张 | `t_application_declaration`、`t_partner_onboarding_code` |
| 改表 | `t_party_profile`（jurisdiction_code、current_slot、superseded_time；uk 改 (party_id,current_slot)）；`t_individual_profile`（birth_place、birth_country、gender、source_of_funds_code、pep_self_declared；middle_name 128）；`t_organization_profile`（registration_authority_code、license_expiry_date、listed_company）；`t_party_address`（address_format_code、building_number、additional_number、district、po_box）；`t_party_profile_identifier`（删 identifier_value，增 identifier_vault_token、identifier_masked_value、issue_date、expiry_date）；`t_party_identity_key`（normalized_identifier_value → identifier_vault_token，uk 改）；`t_party_relation`（relation_type 引用目录、ubo_basis、job_title、nominee_flag、control_nature）；`t_application_document`（issue_date、expiry_date、document_number_masked）；`t_client_application`（application_type、draft_profile_id、jurisdiction_code、organization_type_code、schema_id、onboarding_code_id、responsible_partner_id、materiality）；`t_client`（jurisdiction_code、responsible_partner_id）；`t_operating_entity` / `t_local_partner`（jurisdiction_code） |
| 删除 | `application.yml` `onboarding.form-catalog`、`ClientFormCatalogProperties`、写死的 `ClientSubmissionValidator` 规则、`form-options` 接口 |
| 新 / 改接口 | `GET /client-onboarding/v1/jurisdictions`、`GET …/form-schema`、`POST …/applications{clientType, onboardingCode?}`、`PATCH …/draft`（attributes、本地名、新地址列）、`POST …/declarations`、`GET …/requirements`、`POST …/withdrawal`、422 体改需求级；运营 `/client-master-admin/v1/jurisdictions|rule-settings|organization-types|identifier-types|relation-types|address-format-components|attribute-definitions|onboarding-schemas`（查看 + 版本发布）、`/clients`、`/local-partners/{id}/onboarding-codes` |
| 出站 | Vault PROTECT / REVEAL `LEGAL_IDENTIFIER_V1`（STABLE）；compliance `programs/resolve`；I01 v2 请求（辖区、组织类型、属性、Token、声明、责任方、轮次） |
| 入站事件 | `ClientReviewDecided` v2（EXPIRED → WITHDRAWN）；`QualificationChanged`（REVOKED → SUSPENDED） |
| 校验引擎 | Schema 驱动校验器、校验位注册表（LUHN、EG_NID；KR_BRN 等后续）、规则开关解释器、需求清单计算器 |
| DML | AE / SA / EG / ZZ 国家包（辖区、规则、目录、地址组件、属性、Schema v1）；未经法务确认的行 status=DRAFT |
| 测试 | 服务级入口：form-schema 解析、提交校验矩阵（每类错误码）、Vault Token 写入与唯一键冲突、入驻码、撤回、事件消费；**ZZ 国家包全链测试**；ArchUnit 字面量禁令 |
| PDManer / Schema SQL / 测试 DDL / 文档 | 同步；`CLIENT_CONTRACT_DATABASE.md` 修订 |

### 7.2 compliance（`mor_compliance`）

| 变动点 | 内容 |
|---|---|
| 改表 | `t_dd_program`（jurisdiction_code、party_type、role_type、tier、owner_partner_id、priority_no）；`t_dd_program_version`（review_interval_months、four_eyes_required、auto_approve_allowed、medium/high_threshold）；`t_dd_program_requirement`（subject_role、max_age_days、waivable_flag）+ 新子表 `t_dd_program_requirement_doc_type`；`t_dd_program_check`（check_mode、subject_role、standard_api_code、blocking_flag）；`t_dd_program_screening`（monitoring_required、standard_api_code）；`t_due_diligence_case`（case_type、jurisdiction_code、tier、risk_rating、risk_score、responsible_partner_id、operated_by、on_behalf_of_partner_id、round_no、assignee_id、previous_case_id、review_due_time；状态全集 + PENDING_DEPENDENCY）；`t_dd_case_subject`（ubo_basis、job_title）；`t_dd_requirement`（subject_role、waived_by、waived_reason_code、round_no）；`t_dd_evidence_ref`（file_address、document_type、issue_date、expiry_date、reject_reason_code、reviewer_id、reviewed_time）；`t_dd_check`（case_id、check_mode、status、outcome、reason_code、execution_ref、reviewer_id、reviewed_time、note、round_no）；`t_screening_run`（case_id、search_term_vault_token、trigger_source、list_version_ref、execution_ref）；`t_screening_hit`（entity_type、risk_level、whitelisted_flag；status + SUPERSEDED）+ 新子表 `t_screening_hit_list`；`t_screening_resolution`（resolution 枚举改）；`t_qualification_decision`（tier、risk_rating、jurisdiction_code、responsible_partner_id、recommended_by、recommended_time、four_eyes_waived、conditions_note）；`t_role_qualification`（tier、risk_rating、jurisdiction_code、responsible_partner_id、review_interval_months、last_review_time） |
| 新表 | `t_dd_risk_factor` |
| 新 / 改接口 | `GET /compliance/v1/programs/resolve`（取代 `MaterialRequirementsApi`）；I01 v2；`ClientReviewAdminApi` 增 assignment / checks result / screenings hits resolution / risk-factors / risk-rating / recommendation / approval / rejection / waiver；`result` 增 errors[]、requirementsDue[]；程序维护与版本发布接口 |
| 出站 | channel-connector `POST /channel-connector/v1/service-executions`（SCREENING_SEARCH，Mock 渠道）；Vault PROTECT 检索词 / Reveal 证件（COMPLIANCE_CHECK / COMPLIANCE_REVIEW） |
| 事件 | `ClientReviewDecided` v2（schemaVersion 2）、`QualificationChanged`（新） |
| 状态机 | 案件 SM-D 全态、要求 SM-E、证据 SM-F、检查 SM-G、筛查 SM-H/I、决定 SM-J、资格 SM-K（REVIEW_OVERDUE 本期不触发） |
| DML | AE / SA / EG / ZZ 程序（个人 / 机构 × STANDARD / ENHANCED）与模板行；程序 → 资格类型映射从 `onboarding.materials.programs-json` 迁入表 |
| 测试 | 服务级入口：I01 建案（主 + 子案件 + 依赖）、重提多轮复用、证据审核、人工检查、Mock 筛查 + 命中处置、CRR 必填门禁、四眼、退回 / 批准 / 拒绝 / 超期、事件发布、责任方过滤 |
| PDManer / Schema SQL / 文档 | 同步 |

### 7.3 channel-connector

| 变动点 | 内容 |
|---|---|
| 标准 API | `SCREENING_SEARCH`（输入 / 输出字段按设计 13）；`GOV_ID_VERIFY`、`KYB_REGISTRY_LOOKUP`、`TAX_ID_VERIFY`、`ADDRESS_VERIFY`、`IDV_SESSION_CREATE`、`IDV_RESULT_GET` 只登记定义，无渠道 |
| 渠道 | `S_SCREENING_MOCK`（SERVICE，仅 local / mor-test，按请求配置 DTO 的测试端点）；调用授权 compliance × S_SCREENING_MOCK × SCREENING_SEARCH |
| 日志 | SERVICE 渠道请求体不记全文（含证件原值） |

### 7.4 operations-console-bff / operations-console-web / platform-support（注册）

| 变动点 | 内容 |
|---|---|
| ops-bff | client-master Admin 客户端（目录 / Schema 查看与发布、Client 列表详情、入驻码）；compliance Admin 客户端（案件工作台全部操作、程序查看与发布）；代操作时写 `on_behalf_of_partner_id` |
| ops-web | 页面：入驻配置（辖区包 / 目录 / Schema / 程序，只读 + 发布）；案件工作台（材料 / 人工检查 / 命中 / 分类 / CRR / 推荐 / 决定 / 退回 / 时间线）；Client 列表 / 详情；入驻码；现有 `/client-onboarding/reviews` 升级为工作台 |
| platform-support | 注册块（候选 138；权限、菜单、路由；默认休眠 + 激活 / 回滚脚本） |
| 多语言 | ops-web / client-web 文案走 key；Schema 的 `label_key / help_key` 登记到 Platform 多语言（命名空间 `onboarding-schema`） |

### 7.5 client-console-bff / client-console-web

| 变动点 | 内容 |
|---|---|
| client-bff | 转发 jurisdictions / form-schema / declarations / requirements / withdrawal；创建申请带入驻码 |
| client-web | `/onboarding` 单页改为 Schema 驱动渲染器 + 控件注册表；入驻码输入；需求清单面板；声明；撤回；审核结果页展示需求级错误与期限 |

### 7.6 Vault / Platform file

| 变动点 | 内容 |
|---|---|
| Vault | 数据类型 `LEGAL_IDENTIFIER_V1`（STABLE、TEXT、STANDARD）；访问规则 client-master PROTECT / REVEAL（CLIENT_ONBOARDING_IDENTIFIER）、compliance REVEAL（COMPLIANCE_REVIEW）；检索词类型 `RISK_SCREENING_SEARCH_TERM_V1`；DML 按环境 |
| Platform file | 不改；材料仍存 `/files/v1/{fileId}` + Hash |

### 7.7 不改代码但受影响

commercial（`GET /party-profiles` 读当前行，字段增加——兼容）、acquiring（无）、billing（本期无计费事件）、risk-decision（无）。

## 8. 验收标准

**功能**
- AC-01 `GET form-schema?jurisdiction=SA&partyType=ORGANIZATION&roleType=CLIENT&organizationType=LLC` 返回分区 / 字段 / 控件 / 必填 / 二选一组 / 可见条件 / 标识类型规则 / 地址组件 / 关系人规则 / 材料槽 / 声明；AE、EG 同理；辖区不存在或 `ACCEPTANCE_POLICY=CLOSED` 返回明确错误。
- AC-02 提交校验矩阵：字段缺失、格式、校验位（SA NID Luhn、EG NID 结构）、到期、证件冲突、UBO 不足 / 超 100、签字人缺失、二选一未满足、材料缺失、声明缺失——各返回对应 `errors[].code` 与 `remediation`；`GET requirements` 与 422 同源。
- AC-03 个人证件提交后库内只有 Token + 脱敏；同一证件号第二次绑定不同 Party 返回 `IDENTIFIER_CONFLICT`；日志与 MQ 载荷不含原值；I01 请求只含 Token。
- AC-04 入驻码：有效码解析责任方并冻结到申请与案件；无效 / 过期 / 用尽码返回 `ONBOARDING_CODE_INVALID`；无码为平台自营。
- AC-05 I01 建主案件 + 每个关系人子案件 + 依赖；程序按 (辖区, 主体类型, 角色, 档次) 解析；`programs/resolve` 无程序时提交失败关闭。
- AC-06 案件工作台：证据接受 / 拒绝（原因码）、人工检查录入、Mock 筛查命中处置（FALSE_POSITIVE / TRUE_POSITIVE_REJECT 等）、分类确认、CRR 选择 + 因子、推荐、决定；未评 CRR / 有 OPEN 命中 / 必需要求未 ACCEPTED 时批准被拒；四眼开启时推荐人 = 决定人被拒。
- AC-07 退回：Client 收到需求级 errors[] 与期限；重提同案件 `round_no=2`，已 ACCEPTED 要求与 PASS 检查不重做；退回超期案件 EXPIRED、申请 WITHDRAWN。
- AC-08 批准：决定冻结 tier / CRR / 辖区 / 责任方 / 推荐人 / 决定人；资格投影 APPROVED 带 `next_review_time`；`ClientReviewDecided` v2 与 `QualificationChanged` 发布；client-master 同事务激活 Client、冻结 Profile。
- AC-09 `QualificationChanged(REVOKED)`（手工撤销）→ Client SUSPENDED。
- AC-10 责任方隔离：运营按 PayFac 过滤案件；代操作的决定记录 `operated_by=PLATFORM` + `on_behalf_of_partner_id`。
- AC-11 **ZZ 国家包**：仅通过 DML 新增虚构国家 ZZ（含一种自定义标识类型、一种属性、一套 Schema 与程序），不改任何代码跑通 AC-01 → AC-08；ArchUnit 字面量禁令通过。
- AC-12 程序 / Schema 版本发布不影响在途申请与案件（冻结 schema_id / program_version_id）。
- AC-13 Mock 筛查只在 local / mor-test 生效，`provider_code=S_SCREENING_MOCK`（channel-connector 渠道码）；程序含 `MANUAL_SCREENING` 人工项并参与门禁。

**视觉 / 交互**
- AC-14 Client 门户：三国各一份机构 + 一份个人表单按 Schema 渲染（SA 国家地址控件、AE PO Box、EG 省必填）；需求清单面板；声明；撤回；错误定位到字段；英文 + 简体中文。
- AC-15 Operations：入驻配置页（辖区包 / 目录 / Schema / 程序只读 + 发布）、案件工作台、Client 列表 / 详情、入驻码页；空状态与错误反馈；英文 + 简体中文。

> **2026-10-27 阶段 3 变更（用户决定）：不做阿拉伯语。** 界面语言只有 en / zh-CN，不做 RTL；表单不采集阿拉伯文名称（姓名、法定名称只收拉丁字母，与护照 / 执照拉丁名一致）；删除 `local_*` 名称列与 `name_script`、`NAME` 双文字控件。若将来某国确需本地文字名称，按扩展属性（`t_party_attribute`）加，不改表。

## 9. 待确认问题（阶段 1）

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 切片 1 是否合并 S2 的"证件 Vault 化"（因存量重写，避免先建明文再改） | 合并（本稿已按合并写） |
| Q2 | 运营代操作的 PayFac 列表从哪来：所有 ACTIVE Local Partner 还是仅 `OE_PROVIDER` 角色 | 仅 OE_PROVIDER 角色的 Partner 可被设为责任方 / 签发入驻码 |
| Q3 | Schema / 程序版本发布的"四眼"首期用 Operations 双人确认还是单人发布 | 首期单人发布 + 操作日志；四眼随 Platform workflow 系统发起能力后加 |
| Q4 | Platform 注册块号 | 候选 138，阶段 2 核对 live mor_platform |
| Q5 | 客户门户是否保留旧单页的 BASIC / BUSINESS / PEOPLE / MATERIALS 四分区名 | 保留作为 Schema `section` 的默认值，新增 DECLARATIONS |
| Q6 | AE 个人 Client（Sole Establishment）本期是否纳入 | 纳入：INDIVIDUAL + 属性 `SOLE_ESTABLISHMENT` + 执照标识（Q14） |

## 10. 阶段安排（建议）

| 阶段 | 范围 | 说明 |
|---|---|---|
| 1 需求评审 | 本稿 | 确认范围 R1—R11、规则、验收、Q1—Q6 |
| 2 差异与影响 | 核对两库代码、现有测试、前端、台账；块号；法务 26 项对 DML 的影响标注 | 输出 technique.md 第 2 节 |
| 3 方案与设计 | 接口定义、两库 DDL / DML（含 AE/SA/EG/ZZ 国家包与程序）、Vault DML、Platform 注册 DML、页面原型（operations-console.pen / client 门户）、研发与验证计划 | 规模总结一句话 |
| 4 研发 | 独立 worktree；两库本地 SQL 需单独授权 | 按系统拆分，优先 client-master → compliance → channel-connector → BFF / Web |
| 5 本地验收 | 服务级测试、ZZ 全链、浏览器验收 | |
| 6–8 | mor-test 发布准备 / 部署 / 提交 | 各自授权 |
