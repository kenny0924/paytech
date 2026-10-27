# 全球商户入驻与 KYC/KYB 行业调研笔记

调研日期：2026-10-26。目标：为 MoR + PayFac 子商户模式支付平台（client-master / compliance 服务）设计全球商户入驻与 KYC/KYB 系统提供行业公开资料参考。只记录事实、字段、枚举、门槛与状态机，附来源 URL。

## 0. 检索说明与来源限制

- 字段名、枚举、门槛按来源原样给出（英文原文）。
- 访问受限：`mastercard.com / mastercard.us / mastercard.fr` 规则 PDF、`fatf-gafi.org`、`cfatf-gafic.org`、PwC PDF 对抓取返回 403 或反爬 HTML。Mastercard 现行规则用 2010 年 MasterCard Rules 镜像原文 + 二手来源；FATF 用解释性注释部分镜像 + EU AMLD4 / AMLR 法律原文替代。
- Visa 使用官方 Visa Core Rules and Visa Product and Service Rules（2026-04-18 版）与 Payment Facilitator and Marketplace Risk Guide（2021）PDF 原文（本地提取文本：`scratchpad/dl/visa-53.txt`、`scratchpad/dl/visa-pf-guide.txt`、`scratchpad/dl/mc2010.txt`）。
- Stripe 各国要求直接读取 `docs.stripe.com/_endpoint/get-requirements-for-setups` JSON。
- Dow Jones 无公开官方开发者文档，仅有非官方来源。

---

## 1. Stripe Connect

### 1.1 可借鉴的模型 / 字段 / 状态机

**Account 对象**
- `business_type`：`individual | company | non_profit | government_entity`（government_entity 仅 US）。
- `type`：`standard | express | custom | none`；`controller.requirement_collection`：`stripe | application`（谁负责收集 KYC）；`controller.fees.payer`、`controller.losses.payments`、`controller.stripe_dashboard.type`。
- `charges_enabled`、`payouts_enabled`、`details_submitted`、`capabilities{<name>: active|inactive|pending}`。
- `company.*`：`name, name_kana/name_kanji(JP), phone, tax_id_provided, tax_id_registrar(DE), vat_id_provided, registration_number, registration_date, structure, address, address_kana/kanji, administrative_address, physical_address, principal_place_of_business, directors_provided, executives_provided, owners_provided, ownership_exemption_reason, export_license_id / export_purpose_code (IN)`。
- 三个证据式声明哈希：`company.ownership_declaration`、`company.directorship_declaration`、`company.representative_declaration`，均含 `{date, ip, user_agent}`。
- `company.owners_provided`：Stripe 按"已提供 owner 数 + 各 owner `percent_ownership` 合计"自动判定是否足够。
- `company.verification.document {front, back, details, details_code}`；`details_code` ∈ `document_corrupt, document_expired, document_failed_copy, document_failed_greyscale, document_failed_other, document_failed_test_mode, document_fraudulent, document_incomplete, document_invalid, document_manipulated, document_not_readable, document_not_uploaded, document_type_not_supported, document_too_large`。
- `company.ownership_exemption_reason`：`qualified_entity_exceeds_ownership_threshold | qualifies_as_financial_institution`。提交后 Stripe 转而验证豁免理由；失败返回错误码 `verification_rejected_ownership_exemption_reason`，回退标准 UBO 流程。新加坡：qualified entity = 政府实体、上市公司、金融机构，门槛 75%。
- `company.structure` 按国家枚举：
  - UAE：`llc, sole_establishment, free_zone_llc, free_zone_establishment`
  - US company：`multi_member_llc, private_corporation, private_partnership, public_corporation, public_partnership, single_member_llc, sole_proprietorship, unincorporated_association`；non_profit：`incorporated_non_profit, unincorporated_non_profit`；government_entity：`government_instrumentality, governmental_unit, tax_exempt_government_instrumentality`
  - GB：`private_corporation, incorporated_partnership, unincorporated_partnership`；non_profit `incorporated_non_profit, unincorporated_non_profit`
  - DE：`private_corporation, public_corporation, incorporated_partnership, unincorporated_partnership`；`incorporated_non_profit, unincorporated_non_profit`
  - SG：`sole_proprietorship, private_partnership, private_company, public_company`
  - HK：`private_company, private_partnership, sole_proprietorship`
  - AU：`public_corporation, private_corporation, sole_proprietorship, private_partnership, trust`；`unincorporated_association`
  - CA：`private_corporation, private_partnership, sole_proprietorship, public_corporation`；`registered_charity`
  - TH：`private_corporation, incorporated_partnership, unincorporated_partnership, sole_proprietorship`
  - 不填 structure 时默认按 private 处理并要求 owners；public 公司不要求 owners。
- `documents.*`（账户级文件槽，`files[]` 由 `purpose=account_requirement` 上传）：`bank_account_ownership_verification, company_license, company_memorandum_of_association, company_ministerial_decree, company_registration_verification, company_tax_id_verification, proof_of_address, proof_of_business_presence, proof_of_registration(.signer.person), proof_of_ultimate_beneficial_ownership(.signer.person)`。
- `tos_acceptance {date, ip, user_agent}`；`business_profile {mcc, url, product_description, name, support_phone, support_email, support_url, support_address, annual_revenue, estimated_worker_count}`。

**Person 对象**
- `relationship {owner, director, executive, representative, authorizer, legal_guardian, percent_ownership, title}`：
  - `representative`：同一时间只能有一个；创建账户时应为开户负责人。
  - `director`：董事会成员或负责监管合规。
  - `executive`："significant responsibility to control, manage, or direct the organization"。
  - `authorizer`：授权 representative 的人（配合 Letter of Authorization）。
  - `legal_guardian`：representative 的法定监护人（配 `additional_tos_acceptances`）。
- `verification {status: unverified | pending | verified, details, details_code, document{front, back, details, details_code}, additional_document{front, back, details, details_code}}`。
  - person 级 `details_code`：`document_address_mismatch, document_dob_mismatch, document_duplicate_type, document_id_number_mismatch, document_name_mismatch, document_nationality_mismatch, failed_keyed_identity, failed_other`。
  - 文件级 `details_code`：`document_corrupt, document_country_not_supported, document_expired, document_failed_copy, document_failed_other, document_failed_test_mode, document_fraudulent, document_failed_greyscale, document_incomplete, document_invalid, document_manipulated, document_missing_back, document_missing_front, document_not_readable, document_not_uploaded, document_photo_mismatch, document_too_large, document_type_not_supported`。
- `political_exposure: existing | none`；`id_number_provided`、`id_number_secondary_provided`、`ssn_last_4_provided`；`nationality, dob, address, registered_address, full_name_aliases[], email, phone, maiden_name, gender, us_cfpb_data`。
- Person 也有 `requirements / future_requirements`。

**Requirements 状态模型（核心）**
- `requirements {currently_due[], eventually_due[], past_due[], pending_verification[], current_deadline, disabled_reason, errors[{requirement, code, reason}], alternatives[{original_fields_due[], alternative_fields_due[]}]}`。
  - `past_due ⊂ currently_due`；`current_deadline` 是所有能力/风险要求中最早的期限。
  - `eventually_due` 项达到阈值变为必需时同时进入 `currently_due`，并可提前 `current_deadline`。
  - `pending_verification` 为异步验证中；失败回落到 `eventually_due / currently_due / alternative_fields_due / past_due`。
  - 已验证字段无需重复提交（除非变化）。
- `disabled_reason` 枚举：`action_required.requested_capabilities, listed, rejected.fraud, rejected.incomplete_verification, rejected.listed, rejected.other, rejected.terms_of_service, requirements.past_due, requirements.pending_verification, under_review`。
- `future_requirements` 同结构；到 `future_requirements.current_deadline` 时迁入 `requirements`。合规判定：`future_requirements.currently_due` 与 `pending_verification` 皆空且 `disabled_reason` 为 null。测试：创建账户时 email 含 `enforce_future_requirements` 可把全部未来要求灌入 `requirements`。
- Capability 对象：`id, account, requested, requested_at, status: active | inactive | pending | unrequested, requirements{...}, future_requirements{...}`（能力级定位阻塞项）。
- 风险/合规类需求项格式：`<id>.<requirement_description>.<resolution_path>`，`id` 以 `interv_` 开头；`resolution_path ∈ challenge | form | notice | support | underwriting_case`；`requirement_description` 枚举：`business_model_verification, restricted_or_prohibited_industry_diligence, intellectual_property_usage, supportability_rejection_appeal, other_supportability_inquiry, credit_review, reserve_appeal, identity_verification, url_inquiry, address_verification, bank_account_verification, customer_service_contact, domain_verification, fulfillment_policy, other_compliance_inquiry, other_business_inquiry, platform_concern, product_description, rejection_appeal, statement_descriptor, sanctions_review, pep_review, legal_hold`。提交后路径变 `support` 并进入 `pending_verification`。
- 错误码族（`requirements.errors[].code`，v2 列表完整版见来源）：
  - 关系类：`information_missing, verification_failed_authorizer_authority, verification_failed_representative_authority, verification_missing_owners, verification_missing_directors, verification_missing_executives, verification_document_directors_mismatch, verification_directors_mismatch, verification_extraneous_directors, verification_rejected_ownership_exemption_reason, verification_requires_additional_memorandum_of_associations, verification_requires_additional_proof_of_registration`。
  - 身份类：`verification_failed_keyed_identity, verification_document_name_mismatch, verification_document_dob_mismatch, verification_document_address_mismatch, verification_document_id_number_mismatch, verification_document_photo_mismatch, verification_document_nationality_mismatch, verification_document_fraudulent, verification_document_manipulated, verification_selfie_face_mismatch, verification_selfie_manipulated, verification_selfie_document_missing_photo, verification_selfie_unverified_other, verification_failed_residential_address, verification_token_stale`。
  - 企业类：`verification_failed_keyed_match, verification_failed_document_match, verification_failed_tax_id_match, verification_failed_tax_id_not_issued, verification_failed_name_match, verification_failed_address_match, verification_failed_id_number_match, verification_document_name_missing, verification_document_address_missing, verification_document_id_number_missing, verification_legal_entity_structure_mismatch, verification_failed_business_iec_number, verification_supportability`。
  - 上传类：`verification_document_corrupt, _copy, _greyscale, _incomplete, _not_readable, _not_uploaded, _not_signed, _missing_back, _missing_front, _too_large, _country_not_supported, _invalid, _type_not_supported, _expired, _issue_or_expiry_date_missing, _duplicate_type, _failed_other, _failed_test_mode`。
  - 业务信息/URL：`invalid_business_profile_name, invalid_business_profile_name_denylisted, invalid_company_name_denylisted, invalid_product_description_length, invalid_product_description_url_match, invalid_url_denylisted, invalid_url_format, invalid_url_web_presence_detected, invalid_url_website_business_information_mismatch, invalid_url_website_empty, invalid_url_website_inaccessible, invalid_url_website_inaccessible_geoblocked, invalid_url_website_inaccessible_password_protected, invalid_url_website_incomplete, invalid_url_website_incomplete_cancellation_policy, invalid_url_website_incomplete_customer_service_details, invalid_url_website_incomplete_legal_restrictions, invalid_url_website_incomplete_refund_policy, invalid_url_website_incomplete_return_policy, invalid_url_website_incomplete_terms_and_conditions, invalid_url_website_incomplete_under_construction, invalid_url_website_other`。
  - 地址/个人：`invalid_address_city_state_postal_code, invalid_address_cmra_address, invalid_address_highway_contract_box, invalid_address_private_mailbox, invalid_address_registered_agent_address, invalid_street_address, invalid_dob_age_under_minimum(13), invalid_dob_age_under_18, invalid_dob_age_over_maximum(120), invalid_phone_number, invalid_tax_id, invalid_tax_id_format, invalid_representative_country, invalid_tos_acceptance(税号变更后需重签 ToS), invalid_value_other`。
  - 描述符：`invalid_statement_descriptor_length(≥5), invalid_statement_descriptor_business_mismatch, invalid_statement_descriptor_denylisted, invalid_statement_descriptor_prefix_denylisted, invalid_statement_descriptor_prefix_mismatch`。
- v2 Accounts `requirements.entries[]`：`awaiting_action_from: user | stripe`，`description`，`errors[{code, description}]`，`impact.restricts_capabilities[{capability, configuration: customer | merchant | recipient, deadline.status: currently_due | eventually_due | past_due}]`，`minimum_deadline{status, time}`，`reference{type: inquiry | payment_method | person, inquiry, resource}`，`requested_reasons[].code: routine_onboarding | routine_verification`；`requirements.summary.minimum_deadline{status, time}`。v2 字段路径示例：`identity.business_details.registered_name`、`identity.business_details.id_numbers.ae_crn`、`identity.individual.id_numbers.ae_eid`、`identity.individual.nationalities`、`identity.attestations.terms_of_service.account.date`、`configuration.merchant.mcc`、`defaults.profile.business_url`。

**事件驱动模式**
- v1 webhook：`account.updated`（requirements / future_requirements 变化时携带完整 account）、`person.updated`、`capability.updated`、`account.external_account.*`。
- 文件上传两步：Files API（`purpose = identity_document | additional_verification | account_requirement`，`Stripe-Account` 头）→ 附到 Account/Person；附后 `verification.status → pending`；验证完成/失败触发 `v2.core.account_person.updated` 或 `v2.core.account[identity].updated`；Stripe 验证耗时几分钟至数个工作日。
- v2 thin events：`v2.core.account.created/updated/closed`、`v2.core.account[requirements].updated`、`v2.core.account[future_requirements].updated`、`v2.core.account[identity].updated`、`v2.core.account[defaults].updated`、`v2.core.account[configuration.merchant|customer|recipient].updated`、`v2.core.account[configuration.merchant|customer|recipient].capability_status_updated`、`v2.core.account_link.returned`、`v2.core.account_person.created/updated/deleted`。
- Stripe Identity（用于 `proof_of_liveness` / `person.verification.document`）：`VerificationSession.status: requires_input | processing | verified | canceled`，`type: document | id_number | verification_template`，`options.document.require_matching_selfie`，`related_person{account, person}`，`last_error`，`verified_outputs`，`redaction`；事件 `identity.verification_session.{created, processing, requires_input, verified, canceled, redacted}`；`url` 48 小时一次性，`client_secret` 24 小时一次性。

### 1.2 规则 / 门槛
- 验证要求取决于：国家、capabilities、business_type、company.structure、service agreement type（`full | recipient`）、风险等级。
- 两种流程：Up-front 收 `eventually_due`，Incremental 收 `currently_due`（`collection_options.fields`）；`collection_options.future_requirements = include` 同时收未来要求。
- 阈值型要求（实测 US 平台 / US `company` / `private_corporation` / `card_payments + transfers`）：`company.tax_id`：`payout_limit_amount=300000`、`trigger_limit_amount=150000`、`verified_payout_limit_time=30`；`company.address.*` 与 `representative.address.*`：`verified_payout_limit_amount=60000`、`verified_payout_limit_time=30`；`business_profile.url`：`verified_payout_limit_time=14`，替代项 `business_profile.product_description`。`-1` 表示无限制；端点还返回 `-2 / -5` 哨兵值，语义未文档化。
- UBO 门槛：UAE "25% or more ownership share"，含通过实体最终持有 25% 以上者（"legally beneficial owners"）；UAE 对每个 ≥25% 的法人股东要求 Memorandum of Association；新加坡 25%，若无人达 25% 则降至 ≥10%，持股公司 >25% 需 business registry 文件，间接持股按链相乘（例：75% × 40% = 30%）。
- **UAE（AE）**：`get-requirement-selections-for-platform-country?platformCountry=US` 的 `country_map` 共 121 国，含 AE、SA、EG。AE：`account_types: standard, express, custom`；`dashboard_types: full, express, none`；`tos_types: full, recipient`；`capabilities: card_payments, transfers, link_payments`；`entity_type_structures` 仅 `company`（4 种 structure）。
  - AE company（v1, US 平台, dashboard none, tos full, card_payments+transfers）共 52 项：
    - `company.{owners_provided, executives_provided, name, tax_id, address.line1, address.state, phone, structure}`，`company.tax_id` 说明 "Company Registration Number"（v2 `identity.business_details.id_numbers.ae_crn`）。
    - `representative.{first_name, last_name, address.line1, address.state, id_number, dob.day, dob.month, dob.year, nationality, phone, relationship.title, email, relationship.executive, documents.passport.files}`。
    - `owners.*` 与 `executives.*` 各 10 项：`first_name, last_name, address.line1, address.state, id_number, dob.day/month/year, nationality, documents.passport.files`。
    - `id_number` 说明："Emirates ID (EID) - 15 digit number. For non-UAE nationals a passport number is acceptable"（v2 `identity.individual.id_numbers.ae_eid`）。
    - `documents.{company_license, company_memorandum_of_association, bank_account_ownership_verification}.files`（显示名：Trade license / Memorandum of Association / Proof of bank account）。
    - 账户级：`business_profile.{mcc, product_description, url, support_phone}`、`tos_acceptance.{date, ip}`、`external_account`。
    - 无 `directors.*`；所有 limit 为 -1（即上述都是前置要求）；无 alternatives。
  - AE individual（实为 sole_establishment 形态）27 项：`company.{name, tax_id, structure}` + `individual.{address.line1, address.state, phone, first_name, last_name, id_number, dob.*, nationality, relationship.title, email, relationship.executive, documents.passport.files}` + 同三类公司文件 + `business_profile.*`、`tos_acceptance.*`、`external_account`。
- **SA / EG**：同一查询（US 平台、company、card_payments+transfers）只返回 `tos_acceptance.date / tos_acceptance.ip / external_account`，无任何身份字段——说明该组合下 SA/EG 不提供本地商户收单 KYC（应为 transfers/recipient 场景），不能作为沙特/埃及 KYB 字段参考。
- 文件要求：彩色、≤8000×8000 像素、≤10MB；身份证件 JPG/PNG，地址/法人文件 JPG/PNG/PDF；法人文件含全部页、不加密；身份证件过期日须晚于提交日；地址文件签发 6 个月内；重复上传同一失败文件自动失败；Stripe Identity 不能用于 `additional_document` 与 `company.verification.document`。
- 年龄：person 至少 13 岁（部分国家 18），DOB 在 120 年内；representative 地址须与公司同国。
- 网站：请求 `card_payments` 的电商须提供 `business_profile.url`，否则提供 `product_description`；网站需含退款/退货/取消政策、条款、客服信息等；修复后需改 URL 再改回触发重验。

### 1.3 来源
- https://docs.stripe.com/api/accounts/object
- https://docs.stripe.com/api/accounts/object?query=company
- https://docs.stripe.com/api/persons/object
- https://docs.stripe.com/api/persons/object?query=relationship
- https://docs.stripe.com/api/persons/object?query=verification
- https://docs.stripe.com/api/capabilities/object
- https://docs.stripe.com/api/accounts/update?query=documents
- https://docs.stripe.com/connect/handling-api-verification
- https://docs.stripe.com/connect/custom-accounts/future-requirements （实际内容为 Handle verification updates）
- https://docs.stripe.com/connect/identity-verification
- https://docs.stripe.com/connect/required-verification-information
- https://docs.stripe.com/_endpoint/get-requirement-selections-for-platform-country?platformCountry=US
- https://docs.stripe.com/_endpoint/get-requirements-for-setups?account-setup-A[apiVersion]=v1&account-setup-A[platformCountry]=US&account-setup-A[accountCountry]=AE&account-setup-A[dashboardType]=none&account-setup-A[tosType]=full&account-setup-A[legalEntityType]=company&account-setup-A[capabilities][0]=card_payments&account-setup-A[capabilities][1]=transfers
- 同上端点，`accountCountry=AE&legalEntityType=individual`；`accountCountry=SA`；`accountCountry=EG`；`accountCountry=US&businessStructure=private_corporation`
- https://support.stripe.com/questions/beneficial-ownership-verification-for-holding-companies
- https://docs.stripe.com/changelog/acacia/2025-01-27/ownership-exemption-reason-accounts-api
- https://docs.stripe.com/api/v2/core/accounts/object?query=requirements
- https://docs.stripe.com/api/v2/core/events/event-types
- https://docs.stripe.com/api/identity/verification_sessions/object

---

## 2. Adyen Legal Entity Management API（LEM v4）

### 2.1 可借鉴的模型 / 字段 / 状态机
- 资源：`legalEntities`（含 `businessLines`、`checkVerificationErrors`、`confirmDataReview`、`requestPeriodicReview` 子路径）、`businessLines`、`documents`、`transferInstruments`、`termsOfService`、`onboardingLinks`（hosted onboarding）、`themes`。
- `legalEntity.type`：`individual | organization | soleProprietorship | trust`（文档另有 Unincorporated Partnership，限 AU/BE/CA/CZ/FR/DE/HK/HU/IE/NL/NZ/PL/RO/SG/SK/ES/CH/UK/US；Trust 仅 AU/NZ）。
- `organization` 字段：`legalName*, registeredAddress*, registrationNumber / registrationNumberAbsent, taxId, vatNumber / vatAbsenceReason, dateOfIncorporation, principalPlaceOfBusiness, doingBusinessAs / doingBusinessAsAbsent, countryOfGoverningLaw, stockData{marketIdentifier, stockNumber, tickerSymbol}, webData, taxInformation{number, type, country}, taxReportingClassification{type, businessType, mainSourceOfIncome, financialInstitutionNumber}, phone, email, description, economicSector, institutionalSector, financialReports, globalLegalEntityIdentifier, headOfficeIndicator, legalForm, residentialAddressIndicator, statusOfLegalProceeding, dateOfInitiationOfLegalProceeding, support{email, phone}, type`。
- `organization.type`：`associationIncorporated, governmentalOrganization, listedPublicCompany, nonProfit, partnershipIncorporated, privateCompany`。
- `individual` 字段：`name{firstName, lastName}, birthData{dateOfBirth YYYY-MM-DD}, nationality, residentialAddress, identificationData{type, number}, email, phone, taxInformation, support`。`identificationData.type`：AU `driversLicense, passport`；HK/SG `driversLicense, nationalIdNumber, passport`；NZ `driversLicense, passport`；其他国家 `nationalIdNumber`。
- `soleProprietorship`：`name*, registeredAddress*, countryOfGoverningLaw, dateOfIncorporation, doingBusinessAs, principalPlaceOfBusiness, registrationNumber, taxAbsent, taxInformation, vatNumber / vatAbsenceReason, financialReports`。
- `trust`：`name*, type*, countryOfGoverningLaw*, registeredAddress*, dateOfIncorporation, description(NZ charitable), doingBusinessAs, principalPlaceOfBusiness, registrationNumber, taxInformation, undefinedBeneficiaryInfo, vatNumber`。
- `unincorporatedPartnership`：`name*, countryOfGoverningLaw*, registeredAddress*, dateOfIncorporation, description, doingBusinessAs, principalPlaceOfBusiness, registrationNumber, taxInformation, vatNumber`。
- `entityAssociations[] {legalEntityId*, type*, jobTitle, nominee(NZ), relationship: parent | guardian, settlorExemptionReason[] (AU 信托: professionalServiceProvider, deceased, contributionBelowThreshold)}`。`type` 按主体：
  - individual → `legalRepresentative`
  - organization → `director, signatory, trustOwnership, uboThroughOwnership, uboThroughControl, ultimateParentCompany, immediateParentCompany`
  - soleProprietorship → `soleProprietorship`
  - trust → `trust`；信托成员 → `definedBeneficiary, protector, secondaryTrustee, settlor, uboThroughControl, uboThroughOwnership`
  - unincorporatedPartnership → `unincorporatedPartnership`；成员 → `secondaryPartner, uboThroughControl, uboThroughOwnership`
  - `jobTitle` 在 `uboThroughControl` / `signatory` 时必填。
- `capabilities{<capability>: {requested, allowed, enabled, requestedLevel / allowedLevel: notApplicable | low | medium | high, requestedSettings / allowedSettings, verificationStatus: pending | valid | invalid | rejected, transferInstruments[{id, requested, allowed, verificationStatus}]}}`。能力可用 = `allowed && enabled`（`enabled` 由平台控制，不触发验证）。
  - `verificationStatus` 语义：`pending` 检查中（已允许用户改数据时可保持 allowed）；`valid` 通过并 allowed；`invalid` 失败，修正后可重验；`rejected` 终态不允许。
- 能力列表（Platforms）：`receivePayments, receiveFromPlatformPayments, receiveFromBalanceAccount, sendToBalanceAccount, sendToTransferInstrument, receiveFromTransferInstrument`；其他产品：`issueBankAccount, issueCard, useCard, useCardInRestrictedCountries, useCardInRestrictedIndustries, withdrawFromAtm, withdrawFromAtmInRestrictedCountries, sendToThirdParty, receiveFromThirdParty, getGrantOffers, receiveGrants`。
- `problems[] {entity{id, type}, verificationErrors[{type: invalidInput | dataMissing, code, message, subErrors[{type, code, message, remediatingActions[]}], remediatingActions[{code, message}]}]}`；`remediatingActions` 在子错误有共同补救动作时出现。
- `verificationDeadlines[{expiresAt, capabilities[]}]`（仅 v3/v4）；`verificationPlan: upfront`（marketplace 前置验证）；`documentDetails[{id, type, active, description, fileName, modificationDate, pages[{pageName, pageNumber}]}]`。
- 一般错误码（`code` / `message`）：
  - 通用：`1_10 Information couldn't be verified`、`1_11 Document didn't meet requirements`、`1_12 Legal entity declined`、`1_13 Verification couldn't be completed`、`1_14 Capability not allowed for type individual`、`1_15 Capability not allowed due to legal entity's or associated individual's country`、`1_16 Capability not allowed for entity type`
  - 个人身份：`1_30 Individual details couldn't be verified`、`1_31 ID document is needed`、`1_32 ID document couldn't be processed`、`1_33 ID document didn't meet requirements`、`1_34 Image of the ID document didn't meet requirements`、`1_35 Individual details didn't match the ID document`、`1_36/1_37/1_38` 居住证明、`1_39/1_40/1_41` 国民 ID 证明、`1_42 Individual details didn't meet the format requirements`、`1_43/1_44/1_45` 个人税号证明、`1_46 Identity couldn't be verified`、`1_47 Image of the live selfie didn't meet requirements`、`1_48 Biometric verification couldn't be performed`
  - 组织：`1_50 Organization details couldn't be verified`、`1_51 Organization details didn't match the document`、`1_52 Registration document didn't meet requirements`、`1_53 Tax document didn't meet requirements`、`1_54 Proof of address didn't meet requirements`、`1_55 Proof of ownership is needed`、`1_56 Organization ownership couldn't be verified`、`1_57 Submitted affiliation document didn't meet the requirements`、`1_58 VAT document didn't meet requirements`
  - 法律安排：`1_60 … 1_63`；银行账户：`1_70 Bank account couldn't be verified`、`1_71`、`1_72`、`1_73 Bank account access could not be authenticated`、`1_74 Bank statement is needed`、`1_75`、`1_76 Bank details didn't match the bank document`、`1_77 The entity is in pending status`
  - 签字人/董事：`1_80 Proof of signatory missing`、`1_81`、`1_82`、`1_83 Signatory details couldn't be verified`、`1_84 Proof of director document missing`、`1_85`、`1_86`、`1_87 Director details couldn't be verified`
  - 行业/资金：`1_90 Industry couldn't be verified`、`1_91 Proof of industry didn't meet requirements`、`1_92 No web address available to perform verification`、`1_93 Source of funds details couldn't be verified`、`1_94 Proof of funds document didn't meet requirements`
  - 缺数据 `2_8xxx`（约 130 个）：如 `2_8009 individual.firstName was missing`、`2_8043 organization.registrationNumber was missing`、`2_8060 individual.identificationData was missing`、`2_8071 Identity document was missing`、`2_8107 Proof of residency was missing`、`2_8209 Proof of signatory was missing`、`2_8210 Live selfie was missing`、`2_8250 individual.support.phone.number was missing`
  - 合同：`2_901 PCI forms are not signed`、`2_902 Terms Of Service forms are not accepted`、`2_904 Integration is not fully implemented`
  - 数据复核：`3_10 Review of data is required`
- 补救动作码（节选）：`1_100 No remediation possible`、`1_101 Contact Support`、`1_102 Upload a different document`、`1_300 Update individual details`、`1_301 Upload an ID document`、`1_302/1_303` 更换证件/图片、`1_304 Upload proof of residency`、`1_316 Update the national Id number to a 9 digit SSN`、`1_317 Upload a different live selfie`、`1_500 Update organization details`、`1_501 Upload a registration document`、`1_503 Upload a proofOfOrganizationTaxInfo`、`1_505 Upload a proof of address`、`1_507 Update organization ownership details`、`1_508 Upload a proof of ownership`、`1_510 Ensure signing person has the rights to represent the organization...`、`1_511 Upload a document that proves the payout entity is 100% affiliated with the legal entity`、`1_512 Upload a VAT document`、`1_519 Update source of funds`、`1_522 Add a director`、`1_524/1_525` 主要营业地物理地址、`1_600–1_603` 法律安排、`1_700 Update bank account details`、`1_701 Use different bank account`、`1_703 Upload a bank statement`、`1_705 Upload an official bank document`、`1_800 Upload a proof of signatory`、`1_803 Upload a proof of director document`、`1_900 Upload a proof of industry`、`1_902 Update the web address...`、`2_101 Add individual.identificationData`、`2_115 Add bank account`、`2_117 Add organization.registrationNumber`、`2_118 Add organization.taxId`、`2_123 Add organization.entityAssociations of type uboThroughOwnership`、`2_124 ... of type signatory`、`2_125 Add organization.type`、`2_136 Add business line`、`2_151 ... of type uboThroughControl`、`2_156 Add entityAssociation.jobTitle`、`2_159–2_169` sourceOfFunds 各字段、`2_176 Add industryCode to business line`、`2_179–2_183` taxReportingClassification、`2_185 ... of type director`、`2_186/2_187/2_193` 信托 settlor/definedBeneficiary/undefinedBeneficiary、`2_188 Add proofOfOwnership document`、`2_190 partnership secondaryPartner`、`2_215 Upload a live selfie`、`2_216 Update Legal Entity Type`、`2_221 individual.entityAssociations of type legalRepresentative`、`2_225–2_228` 财务报告/员工数/资产负债/年营业额、`2_901 Sign PCI`、`2_902 Accept TOS`、`3_100 Use the /legalEntities/{id}/confirmDataReview to indicate that the data is confirmed`。
- 文件 `type` 枚举：organization `proofOfAddress, registrationDocument, vatDocument, proofOfOrganizationTaxInfo, proofOfOwnership, proofOfIndustry, proofOfSignatory, proofOfDirector, proofOfFundingOrWealthSource`；individual `identityCard, driversLicense, passport, liveSelfie, proofOfNationalIdNumber, proofOfResidency, proofOfIndustry, proofOfIndividualTaxId, proofOfFundingOrWealthSource, proofOfRelationship`；soleProprietorship `constitutionalDocument, proofOfAddress, proofOfIndustry`；trust / unincorporatedPartnership `constitutionalDocument`；transferInstrument `bankStatement`。`owner{type: legalEntity | bankAccount, id}`；`attachments[{content(base64), pageName, pageType: front | back}]`、`description*`、`number`、`expiryDate / issuerCountry / issuerState`(deprecated)。
- `transferInstrument.type` 仅 `bankAccount`；`bankAccount.accountIdentification` 15 种：`AULocalAccountIdentification, CALocalAccountIdentification, CZLocal..., DKLocal..., HKLocal..., HULocal..., IbanAccountIdentification, NOLocal..., NZLocal..., NumberAndBicAccountIdentification, PLLocal..., SELocal..., SGLocal..., UKLocalAccountIdentification, USLocalAccountIdentification`；`legalEntityId*`。
- 验证触发：新账户持有人请求能力；追加能力；已允许用户修改数据；Adyen 周期 data review 或平台主动请求。
- 周期复核流程：Adyen 立即对 LE 及关联实体（UBO、签字人）重跑验证 → `balancePlatform.accountHolder.updated` webhook 携带补救动作 `3_100` → 用户确认 `POST /legalEntities/{id}/confirmDataReview`（返回确认时间）且解决全部错误 → 逾期能力被取消。
- Webhook：`balancePlatform.accountHolder.updated` 仅在能力相关变化时触发；"Data changes to the legal entity of the account holder do not trigger webhooks"。

### 2.2 规则 / 门槛
- UBO through ownership："directly or indirectly own 25% or more of the total shares, voting rights or other equity"，提供 0–4 人；无人达标 → 必须提供 UBO through control（"ultimate effective control in making decisions for the whole organization"，再无则高级管理层）；至少 1 名 UBO through control 在"无人满足持股标准"或"组织在美国运营"时必需。
- 签字人（signatory）：法定代表且有权与 Adyen 签约，至少 1 名；多数 UBO 也是签字人，但并非所有签字人是 UBO。
- 董事：仅 AU、CA、HK、NZ、SG、UK；CA 至少 1 名，其余全部董事。
- 信托（AU/NZ）：settlor ≥1、trustee 全部、protector 如有、definedBeneficiary 全部、undefinedBeneficiary 仅无 defined 时、uboThroughControl 可选。
- 个人（仅 PF 场景）：须为支持国家公民；需姓名、联系方式、居住地址、出生日期、身份号（如需）、银行账户；若为 UBO through control 或签字人需 jobTitle。
- 年龄：<13 不可入驻；13–18 须 legalRepresentative（`relationship: parent | guardian`，需姓名、DOB、住址、SSN/国民 ID、同意声明）且其为授权签字人；HK/SG <18 不可。
- 验证期限（标准）：30 天：`sendToBalanceAccount, sendToTransferInstrument, receiveFromTransferInstrument, issueBankAccount, sendToThirdParty, getGrantOffers, receiveGrants`；60 天：`receiveFromBalanceAccount, receiveFromPlatformPayments, receivePayments, issueCard, useCard, useCardInRestrictedCountries, useCardInRestrictedIndustries, withdrawFromAtm, withdrawFromAtmInRestrictedCountries, receiveFromThirdParty`；期限内可继续使用能力；基于持续监控可缩短或取消。
- 文件规则：注册文件由公共/政府/司法机关签发，含法人名称与注册号，签发 12 个月内或法定代表签署日期 12 个月内，不接受 DBA 名；地址证明来自可靠独立来源（商业登记），12 个月；个人居住证明 3 个月内（银行对账单、水电单、税务文件、市政记录、工资单/雇佣合同、政府信函），按揭/选民登记 12 个月；所有权证明：签署的所有权与控制结构图（含所有实体、UBO、持股比例），签署 6 个月内，需外部官方文件（股东名册/章程）6 个月内，NL 可能需 UBO 摘录；proof of director 仅 UK；proof of signatory（business accounts）：贸易登记文件或内部文件（FR/UK/DE/NL 接受董事会纪要）；VAT/税务文件 12 个月；live selfie 24 小时内；照片证件未过期、实体、彩色，正反面同一请求，最多 15 页；银行证明：对账单/协议/存款单/网银截图/银行信函/支票，账户名与法人一致，12 个月内，含银行标识；不接受银行卡照片、ATM 小票、会计软件文档、AI 修饰文件。
- 文件格式：JPEG/JPG/PNG/PDF 单文件；银行对账单 ≤4MB；照片证件/自拍等 ≤2MB；章程/注册文件 ≤30MB；PDF ≥1KB，其他 ≥100KB。
- 国家特定税号：BE National Number；FR SPI；DE IdNr；NL BSN；ES DNI/NIE；UK UTR/NINO；US SSN/EIN/ITIN。
- 覆盖国家：欧洲（AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GI, GR, GG, HU, IE, IT, LV, LI, LT, LU, MT, NL, NO, PL, PT, RO, SK, SI, ES, SE, CH, UK 含海峡群岛/马恩岛）、北美（CA, US 含 PR）、亚太（AU, HK, NZ, SG）；收单区域 AU/CA/EEA/HK/NZ/SG/UK/US。**不含 UAE、沙特、埃及**（Adyen 迪拜办公室仅支持中东本地支付方式）。

### 2.3 来源
- https://docs.adyen.com/api-explorer/legalentity/latest/overview
- https://docs.adyen.com/api-explorer/legalentity/latest/post/legalEntities
- https://docs.adyen.com/api-explorer/legalentity/latest/post/documents
- https://docs.adyen.com/api-explorer/legalentity/latest/post/transferInstruments
- https://docs.adyen.com/api-explorer/legalentity/3/post/legalEntities/(id)/confirmDataReview
- https://docs.adyen.com/platforms/verification-requirements.md
- https://docs.adyen.com/platforms/verification-requirements/
- https://docs.adyen.com/platforms/verification-requirements/document-requirements
- https://docs.adyen.com/platforms/verification-overview.md
- https://docs.adyen.com/platforms/verification-overview/capabilities
- https://docs.adyen.com/platforms/verification-overview/data-review-process
- https://docs.adyen.com/platforms/kyc-verification-codes/
- https://docs.adyen.com/platforms/kyc-verification-codes/general-verification-errors
- https://docs.adyen.com/platforms/kyc-verification-codes/remediating-actions
- https://docs.adyen.com/platforms
- https://docs.adyen.com/platforms/onboard-users
- https://docs.adyen.com/hosted-onboarding-faqs
- https://kr-asia.com/adyen-expands-to-middle-east-opens-dubai-office

---

## 3. Checkout.com / Rapyd / PayPal / Mangopay

### 3.1 Checkout.com Platforms（sub-entity）
- 请求模型（文档示例可见字段）：`reference`、`contact_details{phone.number, email_addresses.primary}`、`profile{urls[], mccs[]}`（文档另提及 default holding currency）、`company{business_registration_number, legal_name, trading_name, principal_address{address_line1, city, zip, country}, registered_address{...}, representatives[{first_name, last_name, address{country}, ...}]}`、`individual{first_name, last_name, trading_name, national_tax_id, registered_address}`（Sole Trader）。完整字段在 API Reference `onboardEntity`（JS 渲染，未能抓取）。
- 响应：`id, reference, status, capabilities{payments{available, enabled}, payouts{available, enabled}}, requirements_due[{field, reason}], _links.self.href`；`field` 示例 `company.document`、`company.representatives[0].identification.document`、`individual.identification.document`。
- `requirements_due[].reason` 枚举：`required, document_expired, document_mismatch_submitted_address, document_mismatch_submitted_date_of_birth, document_mismatch_submitted_individual_name, document_submitted_mismatch_ssn, document_type_invalid, document_unreadable, document_unverifiable, invalid_company_document, invalid_company_name, ownership_data_missing, unidentified_details, verification_failed`。
- 状态：`draft`（未提交）、`pending`（PENDING，验证中）、`requirements_due`（ACTION REQUIRED，触发 Status changed + Full DD failed webhook）、`active`（ACTIVE，按账户类型启用 payments/payouts）、`restricted`（不能处理支付/付款）、`rejected`（REJECTED，不能恢复入驻，API 更新返回 HTTP 442 `status_invalid`，申诉经客户经理）、`inactive`。验证通过后修改信息会回到 `pending`。验证只在全部必需信息齐全后开始。
- Webhook：`sub_entity_created, full_dd_passed, full_dd_failed, payouts_enabled, payouts_disabled, payments_enabled, payments_disabled, status_changed`。
- 代表人规则：representatives = 持股或投票权 ≥25% 的股东/UBO；若无人 ≥25%，则为有权代表公司签字的人；所需数据按注册国不同。Dashboard 干系人角色：`Authorized signatory`、`Control person or designated ultimate beneficial owner`（CEO/CFO/VP 等高管）、`Delegated signatory`、`Director`、`Legal representative`、`Ultimate beneficial owner (UBO)`（"more than 25% of the company's shares or voting rights"，直接或经持股公司）。
- 文件（Full accounts）：身份（护照、带照片国民 ID/驾照/居留许可，正反面，彩色，四角可见，未过期）；公司（Certificate of Incorporation <3 个月、MoA/AoA 最新认证版、所有权结构若不在 MoA/AoA 中——欧洲版需主管机关认证且 3 个月内、US 版可加 EIN letter）；财务（资产负债表/利润表/现金流量表等最新版）；银行对账单；地址证明（3 个月内水电单/对账单，当年税务文件）。
- 周期复核：到期时状态置 ACTION REQUIRED / API `requirements_due`（Status changed webhook）；90 天内重新确认法律与联系信息、关键干系人（授权签字人、UBO）并提交 declaration；提交后 `pending` 内部复核，可能再次 ACTION REQUIRED；复核期间不影响处理；逾期 "we may restrict the sub-entity's processing capabilities or suspend services"。需 "Onboard and edit sub-entity details" 权限。
- 来源：
  - https://www.checkout.com/docs/platforms/onboard-sub-entities/submit-application/onboard-with-the-api
  - https://www.checkout.com/docs/platforms/onboard-sub-entities/full-sub-entity-onboarding/due-diligence-checks
  - https://www.checkout.com/docs/platforms/for-payfac/manage-sub-entities/sub-entity-status
  - https://www.checkout.com/docs/platforms/for-payfac/manage-sub-entities/manage-periodic-reviews
  - https://www.checkout.com/docs/platforms/onboard-sub-entities/collect-verification-information
  - https://www.checkout.com/docs/business-operations/use-the-dashboard/business-stakeholders
  - https://www.checkout.com/docs/platforms/onboarding
  - https://www.checkout.com/docs/platforms/onboarding/api/full
  - https://api-reference.checkout.com/tag/Platforms

### 3.2 Rapyd（Rapyd Verify / Onboarding KYB）
- 流程：Partner Portal 建 offering 与 pricing template → List Offerings → Create Organization（返回 `merchant_account_id`，字符串以 `org_` 开头，之后所有方法放 header）→ 商户收到邀请邮件注册 Client Portal（Create Shareable Sign-up Link / Create Invitation）→ Get Application Template（字段与合法值）→ List Industries（确定 MCC）→ Create Application（四种模板：Sole Prop. Card Present / Sole Prop. Card Not Present / Non-Sole Prop. Card Present / Non-Sole Prop. Card Not Present）→ Update Application → Upload Document to Application（每个需文件的字段）→ Submit Application → Retrieve Application（也可按 wallet ID）；Get Organization 查看当前 KYB 状态；可选：Upload Bank Statement Verification File → Add Settlement Bank Account；Sub-Merchant Application（Card Present / Card Not Present）；价格表 Update Merchant Price List Fees。拉美有额外要求。
- Rapyd Verify 申请状态：`new`（已创建）、`submitted`、`approved`（触发能力解锁与可处理交易类型更新）、`rejected`；三种流程含 hosted page（API 生成唯一 URL）。
- KYB 所需资料：企业注册信息、业务活动、行业、关键人员、财务报表。钱包类型（社区回复，可能过期）：Client wallet（1 个 business contact）、Business wallet（1 个 business contact + 无限 personal contacts）、Person wallet（1 个 personal contact）。
- 来源：
  - https://docs.rapyd.net/en/onboarding-kyb-workflow.html
  - https://docs.rapyd.net/en/account-verification.html
  - https://docs.rapyd.net/en/rapyd-verify-hosted-page.html
  - https://docs.rapyd.net/en/rapyd-verify-392374.html
  - https://community.rapyd.net/t/user-identity-verification-the-chicken-and-the-egg-dilemma/58104
  - https://www.rapyd.net/blog/onboard-merchants-with-rapyds-api/

### 3.3 PayPal Commerce Platform（Partner Referrals v2）
- 端点：`POST /v2/customer/partner-referrals`、`GET /v2/customer/partner-referrals/{partner_referral_id}`。
- `business_entity.business_type.type`：`ANY_OTHER_BUSINESS_ENTITY, ASSOCIATION, CORPORATION, GENERAL_PARTNERSHIP, GOVERNMENT, INDIVIDUAL, LIMITED_LIABILITY_PARTNERSHIP, LIMITED_LIABILITY_PROPRIETORS, LIMITED_LIABILITY_PRIVATE_CORPORATION, LIMITED_PARTNERSHIP, LIMITED_PARTNERSHIP_PRIVATE_CORPORATION, NONPROFIT, ONLY_BUY_OR_SEND_MONEY, OTHER_CORPORATE_BODY, PARTNERSHIP, PRIVATE_PARTNERSHIP, PROPRIETORSHIP, PROPRIETORSHIP_CRAFTSMAN, PROPRIETORY_COMPANY, PRIVATE_CORPORATION, PUBLIC_COMPANY, PUBLIC_CORPORATION, PUBLIC_PARTNERSHIP, REGISTERED_COOPERATIVE`；`subtype`：`ASSO_TYPE_INCORPORATED, ASSO_TYPE_NON_INCORPORATED, GOVT_TYPE_ENTITY, GOVT_TYPE_EMANATION, GOVT_TYPE_ESTD_COMM, GOVT_TYPE_ESTD_FC, GOVT_TYPE_ESTD_ST_TR`。
- `business_industry{category, mcc_code, subcategory}`（数字字符串 1–20）；`business_incorporation{incorporation_country_code, incorporation_date}`；`names[≤5]{business_name(≤300), type: DOING_BUSINESS_AS | LEGAL_NAME}`；`documents[≤20].type`：`SOCIAL_SECURITY_NUMBER, EMPLOYMENT_IDENTIFICATION_NUMBER, TAX_IDENTIFICATION_NUMBER, PASSPORT_NUMBER, PENSION_FUND_ID, MEDICAL_INSURANCE_ID, CNPJ, CPF, PAN, BUSINESS_REGISTRATION`。
- `beneficial_owners{individual_beneficial_owners[≤5]{person, percentage_of_ownership}, business_beneficial_owners[≤5]{business, percentage_of_ownership}}`；`office_bearers[≤5]{person, role: CEO | CHAIRMAN | DIRECTOR | SECRETARY | TREASURER | TRUSTEE}`；`annual_sales_volume_range / average_monthly_volume_range{minimum_amount, maximum_amount}`；`purpose_code`（印度：`P0104, P0301, P0801, P0802, P0803, P0805, P0806, P0902, P1004, P1005, P1006, P1007, P1008, P1009`）。
- `individual_owners[≤2]{person, type: PRIMARY}`；`person{id, names[≤5]{type: LEGAL}, citizenship, addresses[≤5]{type: HOME}, phones[≤5], birth_details, documents[≤20]{type: ... NATIONAL_ID_CARD}}`。
- `operations[].operation`：`API_INTEGRATION, BANK_ADDITION, BILLING_AGREEMENT, CONTEXTUAL_MARKETING_CONSENT, OFFLINE_ONBOARDING, SALESFORCE_REFERRAL`；`products[]`：`ALIPAY, BANCONTACT, BLIK, EPS, PPCP, EXPRESS_CHECKOUT, PAYMENT_METHODS, ADVANCED_VAULTING, IDEAL, MB_WAY, MULTIBANCO, PAYPAL_COMMERCE_PLATFORM_BUSINESS, PPPLUS, PRZELEWY24, SATISPAY, TRUSTLY, WECHAT_PAY, WEBSITE_PAYMENT_PRO, ZETTLE, HYPERWALLET_PAYOUTS, CRYPTO_PYMTS`；`capabilities[]`：`PAYPAL_WALLET_VAULTING_ADVANCED, PAY_UPON_INVOICE, APPLE_PAY, GOOGLE_PAY`；`legal_consents[1–5]{type: SHARE_DATA_CONSENT, granted}`；`partner_config_override{return_url, return_url_description, show_add_credit_card, partner_logo_url, action_renewal_url}`。
- `referral_data`：必填 `operations, legal_consents`；可选 `email, preferred_language_code, tracking_id, partner_config_override, financial_instruments, products, capabilities, outside_process_dependencies, payout_attributes, legal_country_code`；响应 `partner_referral_id, submitter_payer_id, submitter_client_id, referral_data, links`。无 UBO 百分比门槛说明。
- 来源：
  - https://developer.paypal.com/api/partner-referrals/v2/schema.json
  - https://developer.paypal.com/docs/api/partner-referrals/v2/

### 3.4 Mangopay
- Legal User：`LegalPersonType: BUSINESS | PARTNERSHIP | ORGANIZATION | SOLETRADER`（变更可能导致验证降级）；`UserCategory: PAYER | OWNER | PLATFORM`（PAYER 仅付款/转账，OWNER 可收款并做 KYC，PLATFORM 由 Mangopay 分配）；`UserStatus: ACTIVE | CLOSED`（仅 Mangopay 可改）；`KYCLevel: LIGHT | REGULAR`（默认 LIGHT；仅 REGULAR 可 payout）；`LegalRepresentative{FirstName, LastName(1–100), Email, Birthday, Nationality, CountryOfResidence, Address}`；`HeadquartersAddress{AddressLine1, AddressLine2, City, Region(US/CA/MX 必填), PostalCode, Country}`；`CompanyNumber`（OWNER + BUSINESS 必填）；`TermsAndConditionsAccepted`（OWNER 必须 true）+ `TermsAndConditionsAcceptedDate`；已验证文件引用 `ProofOfRegistration(REGISTRATION_PROOF), ShareholderDeclaration(SHAREHOLDERS_DECLARATION), Statute(ARTICLES_OF_ASSOCIATION), LegalRepresentativeProofOfIdentity(IDENTITY_PROOF)`；旧 User 端点 2025-12-15 后停用，由 SCA 端点与 hosted IDV Session 取代。
- KYC Document：`Type: IDENTITY_PROOF, REGISTRATION_PROOF, ARTICLES_OF_ASSOCIATION, SHAREHOLDER_DECLARATION, ADDRESS_PROOF`；`Status: CREATED, VALIDATION_ASKED, VALIDATED, REFUSED, OUT_OF_DATE`；`RefusedReasonType: DOCUMENT_DO_NOT_MATCH_USER_DATA, DOCUMENT_FALSIFIED, DOCUMENT_HAS_EXPIRED, DOCUMENT_INCOMPLETE, DOCUMENT_MISSING, DOCUMENT_NOT_ACCEPTED, DOCUMENT_UNREADABLE, SPECIFIC_CASE, UNDERAGE_PERSON`；`Flags[]` 细化原因。
- UBO Declaration：`Status: CREATED, VALIDATION_ASKED, INCOMPLETE, VALIDATED, REFUSED`；`Reason`、`Message`；`Ubos[]{Id, CreationDate, FirstName, LastName, Address{AddressLine1, AddressLine2, City, Region, PostalCode, Country}, Nationality, Birthday, Birthplace{City, Country}, IsActive}`（`IsActive=false` 排除且不可逆）。
- 规则：BUSINESS 与 PARTNERSHIP 必做受益所有人申报（其他法人类型与自然人不需要）；最多 15 个 UBO（2023-11-08 由 4 提升，旧定义限于持股 >25%，新定义加入控制权）；Shareholder Declaration 表格仍以直接/间接 >25% 表述；申报信息与注册证明、章程比对，不足时由法定代表签署 Shareholder Declaration；先交验证文件再交 UBO 申报。
- 来源：
  - https://docs.mangopay.com/api-reference/users/legal-user-object
  - https://docs.mangopay.com/api-reference/kyc-documents/kyc-document-object
  - https://docs.mangopay.com/api-reference/ubo-declarations/ubo-declaration-object
  - https://docs.mangopay.com/api-reference/ubo-declarations/ubo-object
  - https://docs.mangopay.com/guides/users/verification/beneficial-owners
  - https://docs.mangopay.com/release-notes/api/2023-11-08-mozart
  - https://mangopay.com/terms/shareholder-declaration/Shareholder_DeclarationV1-EN.pdf

---

## 4. KYB / IDV / 名单筛查供应商

### 4.1 Sumsub Business Verification（KYB）
- 流程：选国家（及州）→ 输入公司名 → 注册库搜索并建议匹配（找不到则手工录入再搜）→ 选定公司并确认 → 自动 KYB 秒级出结果；Expert-assisted KYB 由 Sumsub 法务团队最多 1 个工作日。组件：Automated Corporate Registry Check（全球+本地注册库收集公司与受益人信息）、Automated Company Document Reading（ACDR，文件 OCR 并与注册库交叉核验）、Corporate AML Screening（公司与受益人：制裁、负面媒体、watchlist）、Company tax check（US EIN 与名称匹配）、Manual Company Review / Manual Company Document Review（仅 Expert-assisted，建立所有权并验证关联方）。
- 受益人与关联方：ownership-based UBO（25% 或以上）、control-based UBO、substitute UBO、股东、董事、秘书等。
- 注册库覆盖档：Worldwide Basic（200+ 国，主信息+董事）、Worldwide Advanced（100+ 国，live 数据）、Worldwide Global（200+ 国，含完整所有权结构，人工补充）、Regional Asia（13 国 live，含股东）/ Europe（28 国，部分董事与股东）/ MENA（20 国，部分数据）、Local（14 国专线）、Manual（Sumsub 官员人工处理）；示例注册库：DE Handelsregister/Unternehmensregister、BR Receita Federal/CNPJ、NL KvK、EE e-Business Register、AT Manz、LV Lursoft；EU VIES 增值税校验。
- 受益人 API（KYB 2.0）`POST /resources/applicants/{applicantId}/fixedInfo/companyInfo/beneficiaries`：
  - `applicantId`（关联已有个人 applicant；此时省略 beneficiaryInfo）或 `beneficiaryInfo`（新建）。
  - `types[]*`：`ubo, shareholder, representative, director, companyOfficer(仅内部), secretary, authorizedSignatory, founder, legalAdvisor, investor(仅内部), fundInvestor, fundManager, investmentManager, investmentAdviser, fundAdministrator, trustee, trustBeneficiary, trustSettlor, trustProtector(仅信托), managingPartner, equityPartner, depositary, custodian, primeBroker(仅公司), accountUser`。
  - `subtypes[]`（仅 types 含 ubo）：`ownershipUbo`（直接/间接持股超门槛，配 `shareSize` 百分比）、`controlUbo`（投票权等控制，必填 `natureOfControl` ≤255 字符）、`substituteUbo`（Senior Managing Official，无自然人满足持股/控制时）。
  - `shareSize`（double，股东与 ownershipUbo）。
  - `beneficiaryInfo` 个人：`email, phone, firstName, lastName, middleName, dob(YYYY-mm-dd，不可未来/150 年前), countryOfBirth, stateOfBirth, placeOfBirth, gender(M/F), nationality, country(alpha-3), tin, taxResidenceCountry`；公司：`companyName, registrationNumber, registrationLocation, type, legalForm(固定码如 privateLimitedCompany, generalPartnership, trust, investmentFund), incorporatedOn, legalAddress, postalAddress, controlScheme, taxId, website`；地址：`street, subStreet, buildingNumber, flatNumber, town, state, postCode`；`legalName, externalUserId, metadata[]`。
  - 若该角色配置了验证 level → 创建嵌套 applicant 并关联公司；否则数据仅存于公司 applicant。
  - 响应：`id, applicantId, applicant, shareSize, types, subtypes, natureOfControl, beneficiaryInfo`。
- 结果：`applicantReviewed` webhook `reviewResult.reviewAnswer: GREEN | RED`；RED 时附 `rejectLabels[]`、`reviewRejectType: FINAL | RETRY`（FINAL 永久拒绝如欺诈，RETRY 可重提交）、`moderationComment`（可展示）、`clientComment`（内部，FINAL 时含私密信息）、`buttonIds`；`reviewStatus` 示例 `completed`。API 主动拒绝 → completed/RED/FINAL + 标签 `EXTERNAL_DECISION_REJECTION`。
- 公司相关拒绝标签：`COMPANY_NOT_DEFINED_STRUCTURE`（控制结构无法建立）、`COMPANY_NOT_DEFINED_BENEFICIARIES`、`COMPANY_NOT_VALIDATED_BENEFICIARIES`、`COMPANY_NOT_DEFINED_REPRESENTATIVES`、`COMPANY_NOT_VALIDATED_REPRESENTATIVES`；通用：`COMPROMISED_PERSONS, PEP, SANCTIONS, ADVERSE_MEDIA, FRAUDULENT_PATTERNS, BLOCKLIST, BAD_PROOF_OF_IDENTITY, EXPIRATION_DATE, DOCUMENT_TEMPLATE, REGULATIONS_VIOLATIONS, UNSATISFACTORY_PHOTOS, SCREENSHOTS, FORGERY, INCOMPATIBLE_LANGUAGE, FRAUDULENT_LIVENESS, STOLEN_DOCS`；标签本身不决定 FINAL/RETRY。Dashboard 状态：Approved / Rejected / Awaiting user / Resubmission requested；标签 "Not all the beneficiaries verified"、"Problematic company structure"、"Not all checks succeeded"。
- Full KYB 规则：UBO = 直接或间接持有 "more than 25%" 或行使重大控制的自然人；公司文件须含名称、注册号、法律形式、地址、成立日期；法律存在类文件：注册证书、注册库摘录、incumbency 证书、good standing、章程/组织大纲、statement of information、合伙协议；所有权与控制类：注册库/UBO 登记摘录、股东/成员名册、董事名册、运营协议、信托契据等；签发 12 个月内或公开登记最新版（无期限文件如信托契据不限）；接受未公证副本、电子文件、照片、黑白扫描，不接受截图；内部表格与结构图不作 UBO 证明；优先注册机构/注册代理/公证签署，否则董事/秘书签署（最好盖章）。信托：settlor、trustee、protector 做 KYC，法人则穿透 >25% 持有人；受益人 16–18 岁可验证，<16 记备注。
- 来源：
  - https://docs.sumsub.com/reference/link-beneficiary-to-company-kyb-20
  - https://docs.sumsub.com/reference/сompany-beneficiaries
  - https://docs.sumsub.com/docs/how-business-verification-works
  - https://docs.sumsub.com/docs/full-kyb-requirements
  - https://docs.sumsub.com/docs/business-verification-sources
  - https://docs.sumsub.com/docs/receive-and-interpret-results-via-api
  - https://docs.sumsub.com/docs/rejection-labels
  - https://docs.sumsub.com/reference/reject-applicant
  - https://docs.sumsub.com/llms.txt
  - https://support.sumsub.com/business-verification/business-verification-overview

### 4.2 Onfido（现 Entrust Identity）
- 对象模型：`Applicant{id, created_at, delete_at, href, first_name, last_name, email, dob, id_numbers, address, sandbox, location, phone_number}` → Studio `Workflow Run{id, applicant_id, workflow_id, workflow_version_id, dashboard_url, status, tags, customer_user_id, output, reasons, error, sdk_token, link}`（`status: processing | awaiting_input | awaiting_client_input | approved | declined | review | abandoned | error`）/ legacy `Check` → `Report`；`Document{id, file_name, file_type, file_size, type, side, issuing_country, applicant_id}`；`Task{task_def_id, input, output}`；`Watchlist Monitor`。
- Check：请求 `applicant_id*, report_names[]*, document_ids[], applicant_provides_data(default false), asynchronous(default true), tags[], webhook_ids[], redirect_uri, privacy_notices_read_consent_given, consider[]`；响应 `id, created_at, href, status: in_progress | awaiting_applicant | complete | withdrawn | paused | reopened, result: clear | consider, form_uri, results_uri, report_ids[], sandbox, paused, version`。
- Report：`id, created_at, href, check_id, name, status: awaiting_data | awaiting_approval | cancelled | complete | withdrawn, result: clear | consider | unidentified, sub_result(仅 document): clear | rejected | suspected | caution, breakdown, properties, documents`。`report_name` 枚举：`document, document_video, document_video_with_address_information, document_with_address_information, document_with_driving_licence_information, document_with_driver_verification, facial_similarity_photo, facial_similarity_photo_fully_auto, facial_similarity_video, facial_similarity_motion, known_faces, identity_enhanced, watchlist_aml, watchlist_enhanced, watchlist_standard, watchlist_peps_only, watchlist_sanctions_only, proof_of_address, us_driving_licence, device_intelligence, india_pan`。
- sub_result 语义：`clear` 全部通过；`suspected` 疑似欺诈；`rejected` 无法处理或未达年龄；`caution` 非欺诈类失败（如姓名不符）。任一子项 consider/unidentified → 父 breakdown consider。
- Document breakdown：`data_comparison{issuing_country, gender, date_of_expiry, last_name, document_type, document_numbers, first_name, date_of_birth}`；`data_validation{gender, date_of_birth, document_numbers, document_expiration, expiry_date, mrz, barcode}`；`image_integrity{image_quality, supported_document, colour_picture, conclusive_document_quality}`；`visual_authenticity{fonts, picture_face_integrity, template, security_features, original_document_present, digital_tampering, other, face_detection}`；`data_consistency{date_of_expiry, document_numbers, issuing_country, document_type, date_of_birth, gender, first_name, nationality, last_name, multiple_data_sources_present}`；`compromised_document{document_database, repeat_attempts}`；`age_validation{minimum_accepted_age}`；`issuing_authority{nfc_active_authentication, nfc_passive_authentication}`；`police_record`。
- Facial similarity photo breakdown：`face_comparison{face_match{result, properties{score}}}`、`image_integrity{face_detected, source_integrity}`、`visual_authenticity{spoofing_detection}`。
- Watchlist AML breakdown：`sanction`（政府与国际组织制裁名单）、`politically_exposed_person`、`legal_and_regulatory_warnings`（执法与监管 monitored lists，含恐怖、洗钱、通缉）、`adverse_media`；`properties.records[]`（已 deprecated）。
- Watchlist Monitor：`{id, applicant_id, report_name: watchlist_standard | watchlist_aml, tags, sandbox / is_sandbox, created_at, deleted_at}`；端点 `POST/GET/DELETE /v3.6/watchlist_monitors`，`GET /watchlist_monitors?applicant_id=`，`GET/PATCH /watchlist_monitors/{id}/matches`（PATCH `enable[] / disable[]` match id），`POST /watchlist_monitors/{id}/new_report`；事件 `watchlist_monitor.matches_updated` payload `properties{monitor_id, records, matches}`、`breakdown{sanction, politically_exposed_person, warnings}`、`result`、`applicant_id`。
- Webhook 事件：`audit_log.created, watchlist_monitor.matches_updated, workflow_run.completed, workflow_timeline_file.created, workflow_task.started, workflow_task.completed, workflow_run_evidence_folder.created, check.started, check.reopened, check.withdrawn, check.completed, check.form_completed, report.withdrawn, report.resumed, report.cancelled, report.awaiting_approval, report.completed`；Webhook 对象 `{id, url, enabled, events, token, environments, payload_version, oauth_*}`。
- 来源：
  - https://documentation.identity.entrust.com/api/latest/
  - https://documentation.onfido.com/api/3.1.0/
  - https://documentation.onfido.com/v3/
  - https://github.com/onfido/onfido-openapi-spec/tree/master/schemas
  - https://github.com/onfido/onfido-openapi-spec/tree/master/schemas/reports
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/checks/definitions.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/reports/definitions.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/reports/document_breakdown.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/reports/facial_similarity_photo_breakdown.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/reports/watchlist_aml_breakdown.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/reports/watchlist_aml_properties.yaml
  - https://raw.githubusercontent.com/onfido/onfido-openapi-spec/master/schemas/watchlist_monitors/definitions.yaml

### 4.3 Jumio KYX
- 能力：`usability, extraction, imageChecks, dataChecks, liveness, similarity, authentication, watchlistScreening, AMLScreening(2026-07 新), classification, ctr(Cross Transaction Risk), addressValidation` + Risk Signals。
- 依赖链：Usability ← Classification（仅 100% 自动化客户）；Extraction ← Usability, Classification；Image Checks ← Usability, Extraction；Data Checks ← Usability, Extraction, Image Checks；Liveness ← Usability；Similarity ← Usability（仅人脸可检测）；Authentication ← Liveness；CTR ← Usability, Extraction, Image Checks。前置 REJECTED 或 NOT_EXECUTED 时下游不执行（例：extraction `TECHNICAL_ERROR` → imageChecks/dataChecks `PRECONDITION_NOT_FULFILLED`）。
- 决策结构：`capabilities.<name>[]{id, credentials, decision{type: PASSED | WARNING | REJECTED | NOT_EXECUTED, details{label}}, data}`。
  - NOT_EXECUTED 标签：`PRECONDITION_NOT_FULFILLED, TECHNICAL_ERROR, NOT_ENOUGH_DATA, VALIDATION_FAILED, INVALID_MERCHANT_SETTINGS, NO_VALID_ID_CREDENTIAL, EXTRACTION_NOT_DONE`。
  - dataChecks：PASSED `OK`；WARNING `DOCUMENT_EXPIRY_WITHIN_CONFIGURED_LIMIT, ID_DATA_MISMATCH_LOW_CONFIDENCE, ID_DATA_INVALID, UNREADABLE_DATASOURCE, LOOKUPS_MEDIUM_RISK_SAME_DOCUMENT_NUMBER`；REJECTED `HIGH_FRAUD_RISK, NFC_CERTIFICATE, MISMATCHING_DATAPOINTS, MRZ_CHECKSUM, MISMATCHING_DATA_REPEATED_FACE, CUSTOMER_FEEDBACK, ID_DATA_MISMATCH, LOOKUPS_HIGH_RISK_SAME_DATA, LOOKUPS_HIGH_RISK_SAME_FACE_MISMATCHING_DATA, LOOKUPS_HIGH_RISK_DEVICE, LOOKUPS_HIGH_RISK_IP_ADDRESS`。
  - watchlistScreening：PASSED `OK`（未命中）；WARNING `ALERT`（命中）；data `searchDate(UTC), searchStatus: SUCCESS | NOT_DONE | ERROR, searchId, searchReference, searchResultUrl, searchResults(int)`（后四项仅 SUCCESS）；持续监控需联系客户经理。
- 来源：
  - https://documentation.jumio.ai/docs/references/capabilities/
  - https://documentation.jumio.ai/docs/references/capabilities/watchlist
  - https://documentation.jumio.ai/docs/references/capabilities/data-checks
  - https://docs.jumio.com/production/Content/References/Capabilities/Capabilities%20Overview.htm

### 4.4 Veriff
- 会话决策：`approved, declined, resubmission_requested, expired, abandoned, review`；状态码 `9001 approved`、`9102 declined`、`9103 resubmission requested`、`9104 expired`、`9121 abandoned`；`verification` 为 null 表示决策未出。
- `verification.reasonCode` + `verification.reason` 仅随 declined / resubmission_requested（如 `102` 疑似文件篡改、`103` 人脸与证件照片不符、`106` 已知欺诈）；是否返回取决于集成配置。
- 重提交上限 9 次，第 10 次自动拒绝并返回 `539 - Resubmission limit exceeded`。
- 来源：
  - https://devdocs.veriff.com/docs/verification-session-decisions.md
  - https://devdocs.veriff.com/docs/verification-session-decision-codes-table
  - https://devdocs.veriff.com/docs/verification-session-status-codes-table.md
  - https://devdocs.veriff.com/docs/granular-reason-codes.md

### 4.5 ComplyAdvantage（名单筛查命中 / 处置 / 持续监控）
- Search 请求：`search_term*(≤255), client_ref, search_profile | filters(互斥), fuzziness(0.0–1.0，被 exact_match 覆盖), offset, limit, tags, share_url, exact_match`；`filters`：`types[]`（`sanction, warning, fitness-probity, pep, pep-class-1, pep-class-2, pep-class-3, pep-class-4, adverse-media` 及子类 `adverse-media-financial-crime, adverse-media-fraud, adverse-media-general, ...`；FATF 对齐 `adverse-media-v2-cybercrime, adverse-media-v2-terrorism, ...`）、`birth_year, country_codes(ISO alpha-2), entity_type, remove_deceased`。
- Search 响应：`id, ref, searcher_id, assignee_id, filters, match_status, risk_level, search_term, total_hits, total_matches, created_at, updated_at, tags, share_url, hits[], labels, blacklist_hits, total_blacklist_hits`。
- `hits[]{doc{id, entity_type: person | company | organisation | vessel | aircraft, types[], sources[], fields[], aka[], associates[], media[], assets[], keywords, name, last_updated_utc}, score(非绝对排名), match_types[], match_types_details, is_whitelisted, match_status, risk_level}`；`match_types`：`name_exact, aka_exact, name_fuzzy, aka_fuzzy, phonetic_name, phonetic_aka, equivalent_name, equivalent_aka, year_of_birth, unknown` + 标志 `removed_personal_title, removed_personal_suffix, removed_organisation_prefix, removed_organisation_suffix, removed_clerical_mark`。
- 处置：搜索级 `match_status: unknown | no_match | potential_match | false_positive | true_positive | true_positive_approve | true_positive_reject`，`risk_level: low | medium | high | unknown`，`assignee_id`（`PATCH /searches/{id}`）；实体级 `PATCH /searches/{id}/entities {entities[], match_status: no_match | false_positive | potential_match | true_positive | unknown, risk_level, is_whitelisted}`（需 client reference）；评论 `POST /searches/{id}/comments {comment, entity_id}`、`GET` 返回 `{id, created_at, message, entity_id, user_id}`；标签 `DELETE /searches/{id}/tags/{tag_name}`；`PATCH /searches/{id}/search-profile`。
- 持续监控：`PATCH /searches/{id}/monitors {is_monitored: true | false}`（"Start on true and stop on false"）；`GET /searches/{id}/monitor/differences`（`content[]{action: added | removed | updated, name, entity_id, listing_types, sources, changes}`）；`POST /searches/{id}/monitor/acknowledge`。
- Webhook：`monitored_search_updated{search_id, updated[], new[], removed[], is_suspended}`；`match_status_updated{client_ref, search_id, entity_id, entity_match_status(仅变化时), is_whitelisted, tags}`；`search_status_updated{changes{before, after}, ref, client_ref, search_id, terms, filters}`（由 assigned_to / risk_level / match_status 变化触发）。
- 来源：
  - https://docs.complyadvantage.com/api-docs/
  - https://docs.complyadvantage.com/
  - https://github.com/api-evangelist/complyadvantage

### 4.6 Dow Jones Risk & Compliance（仅非官方来源）
- 未找到官方开发者文档。非官方 API 轮廓：提交筛查 → `GET /screenings/{screeningId}/matches`（归一化分数与来源元数据）→ `PATCH /screenings/{screeningId}/matches/{matchId}`（resolution status、notes、reviewer）→ `GET /alerts`（持续监控）；社区 SDK 匹配状态 `OPEN | CLEARED`，反馈 `PERMANENTLY_CLEARED | CONFIRMED`，`has_alerts`；第三方（Altares/D&B）处置值 "for action / false positive / mitigated risk / match confirmed" 并展示 Dow Jones 匹配置信度 %；Maxsight 用 DOB/国籍不匹配过滤误报。
- 来源：
  - https://supergood.ai/docs/dow-jones-risk-compliance-api
  - https://jentic.com/apis/dowjones.com/dowjones.md
  - https://npmjs.com/package/@eckidevs/dow-sdk
  - https://help.altares.nl/en/articles/6873119-false-positive-management
  - https://help.maxsight.com/en/configuring-dow-jones-risk-and-compliance

---

## 5. Ballerine（开源 KYB / 风险平台）

### 5.1 数据模型（Prisma schema，dev 分支）
- `Business{id, correlationId, companyName, registrationNumber, legalForm, country, countryOfIncorporation, dateOfIncorporation, address(Json), industry, taxIdentificationNumber, shareholderStructure(Json), numberOfEmployees, approvalState(默认 NEW), mccCode, metadata(Json), projectId}`。
- `EndUser{id, isContactPerson, correlationId, variant: director | ubo, endUserType(默认 "individual"), approvalState, stateReason, firstName, lastName, email, phone, dateOfBirth, gender, nationality, nationalId, passportNumber, address(Json), additionalInfo(Json), activeMonitorings(Json), amlHits(Json), createdFrom: analyst | user | registry, projectId}`。
- `EndUsersOnBusinesses{endUserId, businessId, position: BusinessPosition[]}`，`BusinessPosition: ubo | director | representative | authorized_signatory`（无独立 Ubo 表）。
- `ApprovalState: NEW | PROCESSING | APPROVED | REJECTED`。
- `Document{id, category, type, issuingVersion, issuingCountry, version, status: provided | requested, decision: approved | rejected | revisions, decisionReason, comment, properties(Json), businessId, endUserId, workflowRuntimeDataId, projectId}`；`File{mimeType, fileName, uri, fileNameInBucket}`。
- `WorkflowDefinition{id, crossEnvKey, displayName, reviewMachineId, name, version, projectId, isPublic, definitionType, definition(Json, XState), contextSchema, documentsSchema, config, extensions, variant, persistStates, submitStates}`（唯一键 `[name, version, projectId, definitionType]`）。
- `WorkflowRuntimeData{id, endUserId, businessId, assigneeId, actorEndUserId, actorUserId, workflowDefinitionId, uiDefinitionId, workflowDefinitionVersion, context(Json), config, state, tags, status: active | completed | failed, resolvedAt, assignedAt, parentRuntimeDataId, salesforceObjectName, salesforceRecordId, projectId, deletedAt}`。
- `WorkflowLog{workflowRuntimeDataId, type: EVENT_RECEIVED | STATE_TRANSITION | PLUGIN_INVOCATION | CONTEXT_CHANGED | ERROR | INFO, metadata, fromState, toState, message, eventName, pluginName}`。
- `UiDefinition{workflowDefinitionId, uiContext: back_office | collection_flow, definition, uiSchema, theme, locales}`；`Note{entityId, entityType: Business | EndUser, noteableType: Report | Alert | Workflow, content, parentNoteId, fileIds}`；`Filter{name, entity, query}`；`Project{name, customerId}`；`Customer{name, customerStatus: onboarding | during_pilot | active | terminated, config, features, subscriptions}`。
- 风险/监控：`BusinessReport{type: MERCHANT_REPORT_T1 | MERCHANT_REPORT_T1_LITE | ONGOING_MERCHANT_REPORT_T1, reportId, report(Json), riskScore(Int), status: new | in_progress | under_review | pending_review | completed, businessId, batchId}`；`RiskCategory: low | medium | high`；`Policy{rulesSets(Json)}`；`AlertDefinition{crossEnvKey, monitoringType: transaction_monitoring | ongoing_merchant_monitoring, name, enabled, rulesetId, ruleId, inlineRule(Json), dedupeStrategy(Json), config, defaultSeverity}`；`Alert{alertDefinitionId, dataTimestamp, status: new(100) | pending(200) | completed(300), severity: low(100) | medium(200) | high(300) | critical(400), state: triggered(101) | under_review(201) | escalated(202) | rejected(301) | dismissed(302) | cleared(303), decisionAt, executionDetails, assigneeId, dedupedAt, workflowRuntimeDataId, counterpartyId, businessId}`；`TransactionRecord{transactionCorrelationId, transactionDate, transactionAmount, transactionCurrency, transactionBaseAmount, transactionType, transactionStatus, transactionDirection, paymentMethod, reviewStatus, riskScore(Float), unusualActivityFlags, auditTrail(Json), counterpartyOriginatorId, counterpartyBeneficiaryId}`；`Counterparty{correlationId, businessId, endUserId}`。

### 5.2 工作流状态机（`kyb-kyc-workflow-definition.ts`）
- 初态 `pending_invitation_email`；状态：`pending_invitation_email, data_collection, run_ubos, run_kyb_enrichment, pending_kyc_response_to_finish, manual_review, pending_resubmission, revision, approved, rejected, auto_reject`。
- 转移：`pending_invitation_email` —`INVITATION_EMAIL_SENT`→ `data_collection`，—`INVITATION_EMAIL_FAILED`→ `auto_reject`；`data_collection` —`start`→ `run_ubos`；`run_ubos` —`CONTINUE`→ `run_kyb_enrichment`，—`FAILED`→ `auto_reject`；`run_kyb_enrichment` —`KYB_DONE`→ `pending_kyc_response_to_finish`，—`FAILED`→ `auto_reject`；`pending_kyc_response_to_finish` —`KYC_RESPONDED`（JMESPath 守卫：所有子 KYC 决策齐全）→ `manual_review`，无 UBO 时 `always` → `manual_review`；`manual_review` —`approve`→ `approved`，—`reject`→ `rejected`，—`revision`→ `revision`；`revision` `always` → `pending_resubmission`；`pending_resubmission` —`RESUBMITTED`→ `manual_review`。终态 `approved, rejected, auto_reject`。
- 插件：`apiPlugins` `invitation_email`（POST）、`open_corporates`（GET，KYB enrichment，成功与失败都发 `KYB_DONE`）、`resubmission-email`；`childWorkflowPlugins` `veriff_kyc_child_plugin`（子定义 `kyc_email_session_example`）；`commonPlugins` `ubos_iterative`（对 `entity.data.additionalInfo.ubos` 每项启动子 KYC；成功 `CONTINUE`，失败 `FAILED`）；`config.childCallbackResults` 在子流程到达 `kyc_manual_review` 时回传 `KYC_RESPONDED`；`createCollectionFlowToken: true`。上下文 schema：`documents[]`、`token`、`mainRepresentative{fullName, email}`、`customerName`。
- 通用 case 级状态生成器：`MANUAL_REVIEW`（tag MANUAL_REVIEW）—`REJECT`→ `REJECTED`(final)，—`APPROVE`→ `APPROVED`(final)，—`REVISION`→ `REVISION`；`REVISION` —`RETURN_TO_REVIEW`→ `MANUAL_REVIEW`；另有 `RESOLVED`(final)；auto-transition 变体对 `REVISION` 加 `always` 守卫 `length(documents[?decision.status]) < length(documents)`。
- 案件管理：收集流程完成 → 人工复核，审核员自派单，可 approve / reject / request resubmission（用户回到收集流程重传）。仓库自述 "currently undergoing a major rebuild and is not actively supported at this time"。
- 来源：
  - https://github.com/ballerine-io/ballerine
  - https://raw.githubusercontent.com/ballerine-io/ballerine/dev/services/workflows-service/prisma/schema.prisma
  - https://github.com/ballerine-io/ballerine/tree/dev/services/workflows-service/scripts/workflows
  - https://raw.githubusercontent.com/ballerine-io/ballerine/dev/services/workflows-service/scripts/workflows/kyb-kyc-workflow-definition.ts
  - https://raw.githubusercontent.com/ballerine-io/ballerine/dev/services/workflows-service/scripts/workflows/generate-base-case-level-states.ts

---

## 6. Visa / Mastercard 对 Payment Facilitator 子商户的要求

### 6.1 Visa（Visa Core Rules and Visa Product and Service Rules，2026-04-18 公开版）
- **5.3.1.1 PF 协议必含内容**（收单行与 PF/DWO）：PF 及其 Sponsored Merchant 遵守 Visa Rules；PF 与每个 Sponsored Merchant 签约；收单行有权因 good cause、欺诈或 Visa 要求立即终止 Sponsored Merchant、PF、DWO 或其零售商；声明 PF "Is liable for all acts, omissions, Cardholder disputes, and other Cardholder customer service-related issues caused by the Payment Facilitator's Sponsored Merchants"，"Is responsible and financially liable for each Transaction processed on behalf of the Sponsored Merchant"，不得让持卡人放弃争议权，不得为另一 PF 入账，不得与被 Visa/政府机关终止的 Sponsored Merchant 签约，"Must provide the names of principals and their country of domicile for each of its Sponsored Merchants ... and Transaction reports to its Acquirer and to Visa upon request"，确保 Sponsored Merchant 符合 PCI DSS 与 PCI SSF。
- **5.3.1.2** "A Sponsored Merchant will be treated as a Merchant of its Payment Facilitator's Acquirer"；收单行对 PF 及其 SM 的一切行为负责（含法律费用、结算、资金分发义务）；SM 的行为视同 PF，PF/SM 的行为视同收单行。
- **5.3.1.3 收单行与 PF 签约的要求**：在所有 Visa 风险管理项目中状态良好；财务稳健；PF 注册（含尽调 attestation）获 Visa 确认后才能入账；高风险 PF 须注册为 High-Integrity Risk Payment Facilitator；从 Visa 获得唯一 PF 标识并分配给 PF；"Ensure the Payment Facilitator determines and assigns a unique identifier to each Sponsored Merchant"；授权记录含 PF ID + Sponsored Merchant ID，清算记录含 PF ID（巴西另需税号、法人名）；结算款入收单行辖区内银行账户；不得允许 PF 为 outbound telemarketers 提供服务；应 Visa 要求提交每个 SM 的名称、DBA、PF 名、月交易笔数与金额、月争议笔数与金额；确保 SM 遵守所有商户规则；PF 直接或经 SM 提供客服；PF 可获得 VMSS（Visa Merchant Screening Service）命中结果；合同注明 PF 可签约 SM 的辖区与类别；持卡人可直接访问 PF 网站/应用时提供多语言客服并展示联系方式；PF 向收单行（及 Visa）报告 SM 与交易信息；"uses the appropriate MCC for each Sponsored Merchant"；CEMEA 区 PF 需与 Visa 签约并经 Visa 批准。
- **5.3.1.4 USD 1 million 直签规则**："An Acquirer that contracts with a Payment Facilitator must enter into a direct Merchant Agreement with a Sponsored Merchant that exceeds USD 1 million in annual Transaction volume"——新 SM 在处理任何交易前；既有 SM 在年交易量超过 USD 1 million 后 2 年内；PF 可继续提供含结算的支付服务。豁免（二选一）：(a) 全部满足：PF 与 SM 在同一收单行下关系满 2 年；PF 定期向收单行报告（至少含 SM 交易量、Disputes、Fraud Activity）；收单行持续监督 PF 与 SM 关系（脚注：三方须未被列入 Visa 风险项目且 3 年内无过多违规；可直签或三方协议，High-Integrity Risk / T&E / 预付商户除外）；(b) SM 属 MCC `4900, 6012, 6051(非 High-Integrity Risk), 6513, 8011, 8050, 8062, 8099, 8211, 8220, 8241, 8244, 8249, 8299, 9311, 9211, 9222, 9223`。Visa 保留修改权；LAC（巴西、智利）不适用。
- **5.3.1.5** PF 可签约 BPSP、CBPS、Stored Value DWO、Marketplace（收单行须为协议方且同国；Marketplace 需 Marketplace ID 作为 SM ID 且 75% 卖家同国）；"A Payment Facilitator must not contract with another Payment Facilitator, a Digital Wallet Operator that operates a Staged Digital Wallet, or Ramp Provider"。
- **5.3.2.1** PF 位置 = 主要营业地国家；附加位置需常设机构、持卡人信函/司法送达、纳税、受当地法律管辖。
- **5.3.2.2 Merchant/SM 认定**：出售商品给持卡人、主要以自己名义识别其 Merchant Outlet、对持卡人争议提供救济；否则归类 DWO / Marketplace / PF / Ramp Provider；Visa 可参考交易凭证名称、谁拥有商品、谁入账收入、谁提供客服处理退货。
- **5.3.4.1 Marketplace 认定**：撮合持卡人与零售商；品牌更突出；代零售商处理支付与退款并接收结算；对争议承担财务责任（有约束力决定或自筹资金的 money-back guarantee）；境外零售商不得同时超过 USD 10 million 年 Visa 量与 10% Marketplace 年 Visa 量；不可作为 Marketplace/零售商：franchisees（除非特许人书面同意）、travel agents、High-Integrity Risk Merchants、慈善/众筹。
- **1.9.1.4** Visa 可永久禁止商户/PF/SM 参与；12.3.1 High-Integrity Risk PF 违规评估。
- **PF Risk Guide（2021）** 承保与监控要求：
  - PF/Marketplace 须具备：书面承保政策与程序；收单行批准的商户协议（及后续更新）；正确收集卖家信息；尽调确保符合 Merchant Qualification Standards；拒绝禁止类卖家；高品牌风险商户做 EDD（仅 HRIPF）；使用 VMSS 或等效 Terminated Merchant File（PF）；Auto-Boarding 最佳实践（含申请速度检查等反欺诈控制）。承保政策含分层审批权限（按 MCC/销售额）。
  - 7.3 信息收集须符合 AML/KYC/隐私法；GARS 区分 "required" 与 "best practice" 数据元；信息须验证并认证商户防欺诈申请；决策为批准/有条件批准/拒绝。
  - 7.4 Merchant Qualification Standards：Financial responsibility；No harm to the Visa Payment System；Operating within an allowed jurisdiction；PF 还须确定 "no significant negative background information about any of the sponsored merchant's principals as legally permitted"。
  - 7.5 禁止：Marketplace 不得签高品牌风险类、franchises、travel agents，不得超量；PF 不得服务 outbound telemarketers、其他 PF、marketplaces，限制部分数字钱包；收单行可追加 prohibited / restricted 类别。
  - 7.6 高品牌风险 MCC：`5122, 5912, 5962, 5966, 5967, 5993, 7273, 7995`；活动级：`4816`（cyberlocker/文件分享）、`5816`（技能博彩如 daily fantasy）、`6051`（加密货币购买/钱包充值/ICO）；仅注册 HRIPF 可签；收单行需 High-Brand Risk Acquirer Registration；GBPP（Global Brand Protection Program）。
  - 7.7 VMSS：PF 须对每个申请人查询；VMSS 信息不得作为拒绝的唯一依据。
  - 8.2 最低速度监控参数：Monthly Sales Amount、Average Transaction Amount、Purchase Return Amount、Sales-to-Purchase Return Ratio、Real-time Authorization Attempt Count、Real-time Declined Authorization Count、Dispute Count and Amount Ratios、Fraud Advice (TC40) Amount and Ratio、Card-Absent to Card-Present Ratio、Cross-Border to Domestic Ratio、Force-Posted Transaction Amount；可疑模式：整数金额、微交易、同卡重复、异常跨境、休眠突增、load balancing。
  - 8.3 异常报告与案件管理，可暂扣结算或转入保证金；8.4 持续网站监控（非法活动、transaction laundering、欺骗营销）；8.5 Visa 项目 VDMP、VFMP、VAMP、GBPP、TLD；8.6 PF 不得持有/控制商户保证金（须由收单行持有）；8.7 因故终止须加入 VMSS；8.8 PF 须向收单行提供 onboarding 与风控报告。
- 来源：
  - https://usa.visa.com/content/dam/VCOM/download/about-visa/visa-rules-public.pdf （§5.3.1–5.3.4，PDF 第 365–377 页）
  - https://usa.visa.com/content/dam/VCOM/regional/na/us/partner-with-us/documents/visa-payment-facilitator-and-marketplace-risk-guide.pdf （§4、6、7、8）
  - https://www.rpyin.com/insights/blog-post-one-28b4n-99sm8-nncby-ma6pp-nk5pa-2ex9b-p2bbp-wfwsy-rnkte-x5npa-fkxaz-jkhrs-9cfej-pd427
  - https://www.venable.com/files/Publication/a9e594c3-4186-4fab-82a4-a353210e33a7/Presentation/PublicationAttachment/bfe1332b-5989-4586-9aa7-34f598ed7257/ACheatSheetforDistinguishingAggregatorModels.pdf
  - https://infinicept.com/payment-facilitator/archive/visa-rule-change-signals-support-for-pf-model/

### 6.2 Mastercard（现行文本不可抓取；2010 版原文 + 二手来源）
- 2010-12-10 版 MasterCard Rules §5.4 Use of a Payment Facilitator："The Acquirer is liable for all acts and omissions by a Payment Facilitator and any Sub-merchant"；"A Payment Facilitator may not be a Sub-merchant of any other Payment Facilitator, nor may a Payment Facilitator be a Payment Facilitator for another Payment Facilitator"；当时门槛 "any Sub-merchant that exceeds USD 100,000 in annual sales may not be or continue to be a Sub-merchant and must enter into a Merchant Agreement directly with a Member"。5.4.1：PF 与 SM 须在收单行许可区域内，SM 位置决定交易位置；结算资金只能用于支付 SM。终止 PF 须 1 周内通知 Mastercard。
- §5.5.1 Sub-merchant Screening Procedures（签约/延期/续约前）：验证 SM 是 bona fide business、有保护持卡人与交易信息的措施、遵守法律、每笔交易为 SM 与持卡人的真实交易；至少完成：(1) 信用检查、背景调查、推荐人核查，信用检查存疑时追加对业主（独资）/合伙人/主要股东（公司）的信用检查；(2) 检查场所或网站及记录（设施、设备、库存、协议、人员、执照/许可），多地点至少查一处；"The Payment Facilitator must retain all records concerning the investigation of any entity with which it has entered into a Sub-merchant agreement for a minimum of two years after the date the agreement is terminated or expires"。
- §5.5.2 MATCH Inquiry Required：签约前须请求收单行查询 MATCH（Member Alert to Control High-risk Merchants）；不得签约因 SRP 11.5.1 原因被列入者；终止的 SM 按 SRP 11.2.2 加入 MATCH。
- §5.5.3 Sub-merchant Agreement：书面协议；不得削弱 PF/收单行/Mastercard 的终止权；必含：SM 持续提供各办公地址、所有 DBA 名、商品/服务描述；与 Standards 冲突时 Standards 优先；PF 负责 SM 受理政策并可要求整改网站；PF 被注销或收单行失去会员资格/许可时自动终止；PF 可因欺诈或不当行为立即终止；SM 承认遵守 Standards、Marks 归 Mastercard、Mastercard 可随时禁止使用 Marks 等。§5.5.4 作为 SM 保荐人的义务：只提交有效交易（SM 对员工/代理行为负责）、确保 SM 持续合规、"maintain, on an ongoing basis, the names, addresses, and URLs if applicable of each of its Sub-merchants"、向 SM 付款（可留存 chargeback reserve）、提供受理材料、Sub-merchant Monitoring。
- 现行规则（二手）：年量门槛 2014/2016 起由 USD 100,000 提升到 **USD 1,000,000** 年 Mastercard 量；超过须有含收单行的协议并由收单行直接结算，Mastercard 无 Visa 式两年豁免（Infinicept）；旧 10 万时期要求三方协议；收单行条款示例："more than US$1,000,000 in transactions (or the then applicable threshold under the Payment Scheme Rules) on a Payment Scheme within any 12-month period"（Checkout.com Cross River 条款）。2020 版 Mastercard Rules 目录含 7.6.5.1 "Responsibility for Payment Facilitator and Submerchant Activity" 与 7.8 "Payment Facilitator Obligations"；"Submerchant Screening Procedures" 现为 Security Rules and Procedures 第 7.1.2 节（"verify that a submerchant is a bona fide business"）；2019 SPME：收单行须对自有商户与 PF 的 Submerchant 做筛查并分配最贴切 MCC；BRAM 要求监控 SM 网站与广告。
- 注册与高风险（二手）：Mastercard 注册费 3,000 USD/年、Visa 5,000 USD/年，未注册发现费 9,000 USD（Infinicept）；Mastercard MRP 需登记类别：非面对面成人内容、博彩、药品、烟草、部分彩票/技能游戏（Netpay 2016）；Infinicept 高风险 MCC 列表：`4816`（cyberlocker）、`6051`（加密货币）、`6211`（高风险证券）、`5967/7841`（成人内容）、`7995`（博彩/数字游戏）、`5122/5912`（药品）、`5993`（烟草/电子烟）、`5962/5966/5967`（直销）、`7273`（交友/陪护）。PF 禁止聚合其他 PF、非法辖区网络博彩、MATCH 商户、staged digital wallets。OFAC 至少每月重筛整个组合。
- 来源：
  - https://www.merchantservice.com/wp-content/uploads/2022/02/MERC-Entire_Manual.pdf （MasterCard Rules 2010-12-10，§5.4–5.5，PDF 第 102–110 页）
  - https://www.mastercard.us/content/dam/mccom/en-us/documents/rules/SPME-Manual-February-2019.pdf
  - https://www.mastercard.com/content/dam/public/mastercardcom/na/us/en/documents/SPME-Manual-September-2019.pdf
  - https://www.mastercard.com/content/dam/public/mastercardcom/globalrisk/pdf/Service-Provider-Categories-and-PCI-Updated.pdf
  - https://www.mastercard.fr/content/dam/public/mastercardcom/eu/fr/images/Vision/Regles/mastercard-rules.pdf （2020 版，目录经搜索摘要）
  - https://www.digitaltransactions.net/mastercard-sub-merchants-dont-need-to-own-accounts-until-volume-hits-1-million/
  - https://www.greensheet.com/ebooks/gs171101/files/basic-html/index.html
  - https://infinicept.com/payment-facilitator/wp-content/uploads/2022/05/Compliance-101_FINAL.pdf
  - https://www.netpay-intl.com/wp-content/uploads/2019/09/Netpay_Payment_Facilitator.pdf
  - https://www.checkout.com/legal/cross-river-bank-acquirer-terms
  - https://www.fatzebra.com/acquirer-terms-checkout
  - https://convesio.com/knowledgebase/article/mastercard-payment-facilitator-registration/

---

## 7. FATF / EU 法律框架（CDD、UBO、RBA、PEP、持续尽调、记录保存）

### 7.1 FATF 建议 10（CDD）
- 触发情形：建立业务关系；偶发交易超过指定门槛（USD/EUR 15,000）或 R.16 范围电汇；疑似 ML/TF（不论任何豁免或门槛）；对既有客户识别数据的真实性或充分性存疑。
- 四项措施：(a) 识别客户并用可靠独立来源验证；(b) 识别受益所有人并采取合理措施验证，含了解法人/安排的所有权与控制结构；(c) 了解并酌情获取业务关系目的与性质；(d) 持续尽调——审查交易与客户画像一致性，必要时资金来源，并保持 CDD 信息更新。
- 无法完成 CDD：不开户/不建立关系/不交易、终止关系、考虑提交 STR（R.20）。
- **解释性注释（INR.10）受益所有人瀑布（法人）**：(i) 最终持有 "controlling ownership interest" 的自然人（脚注：取决于公司所有权结构，可按门槛，例如持有超过一定比例如 25% 者）→ (ii) 存疑或无自然人通过持股控制时，"exercising control of the legal person or arrangement through other means" 的自然人 → (iii) 仍无法识别时，"the relevant natural person who holds the position of senior managing official"。法律安排（信托）：settlor、trustee(s)、protector（如有）、beneficiaries 或 class、其他最终有效控制人。受披露要求的上市公司及其多数持股子公司可免识别股东。若 CDD 会导致 tipping-off，可不继续并提交 STR。
- 风险为本：高风险 → 强化尽调（EDD），低风险 → 简化尽调（SDD），"never zero diligence"；INR.10 列出客户/国家/产品风险因子（如 PEP、复杂结构、高风险国家）。
- 来源：
  - https://40recommendations.com/r/10-customer-due-diligence/
  - https://studfile.net/preview/9876167 （INR.10 部分原文镜像）
  - https://financialcrime.lu/col_slider/2025-11-15-FATF-Recommendation-10/
  - https://www.fluxforce.ai/regulations/fatf-recommendation-10-customer-due-diligence
  - https://www.fatf-gafi.org/en/publications/Fatfrecommendations/Fatf-recommendations.html （官方页面，抓取被拒）
  - https://www.fatf-gafi.org/content/fatf-gafi/en/publications/Fatfrecommendations/Guidance-Beneficial-Ownership-Legal-Persons.html （2023-03 受益所有权指引，抓取被拒）

### 7.2 UBO 定义（25% 门槛与控制权测试，法律原文）
- **EU AMLD4（Directive 2015/849）Art.3(6)(a)**：公司受益所有人 = 通过直接或间接所有权最终拥有或控制法人的自然人；"a shareholding of 25% plus one share or an ownership interest of more than 25%" 为直接所有权指标；经受控公司持有同样比例为间接所有权；成员国可设更低比例；(ii) 穷尽所有手段仍无人且无怀疑时，识别 senior managing official(s) 并记录所采取步骤。(b) 信托：settlor、trustee(s)、protector、beneficiaries/class、其他最终控制人。
- **EU AMLR（Regulation 2024/1624，2027-07-10 适用）Art.52**：ownership interest = 直接或间接持有 "25% or more of the shares or voting rights or other ownership interest"（含利润/其他内部资源/清算余额权利）；间接持股按每条中间实体链相乘再各链相加；评估时考虑每一层级所有持股；委员会可对高风险类别设更低门槛，最低 15%，且须 "less than 25%"；无法按持股计算的法人以其他方式控制者为受益所有人。
- **US 31 CFR 1010.230**：ownership prong = 直接或间接持有 25% 或以上股权的每个自然人 + control prong = 一名具有重大控制责任的个人。
- 来源：
  - https://www.legislation.gov.uk/eudr/2015/849/article/3
  - https://www.springlex.eu/en/packages/aml/amlr-regulation/article-52/
  - https://www.law.cornell.edu/cfr/text/31/1010.230
  - https://www.signzy.com/regulation-glossary/FinCEN-CDD-beneficial-ownership-rule
  - https://taxjustice.net/?p=4872

### 7.3 EU AMLR 的 CDD 触发与措施（Art.19 / Art.20）
- Art.19(1)：建立业务关系；偶发交易 ≥ EUR 10 000（或等值）；疑似 ML/TF（不论任何豁免/门槛）；对既有客户识别数据存疑；对交互对象是否为客户存疑。补充门槛：信贷/金融机构资金转移 ≥ EUR 1 000；加密服务商偶发交易 ≥ EUR 1 000（以下至少做识别）；现金交易 ≥ EUR 3 000 至少做识别；博彩 ≥ EUR 2 000。
- Art.20(1) 九项措施：识别并验证客户；识别受益所有人并合理验证、理解所有权与控制结构；了解目的与性质；核查客户/受益所有人是否受定向金融制裁（含被制裁者间接控制）；了解业务/职业；持续监控（含交易与必要时资金来源）；判定客户/受益所有人/相关收款人是否 PEP、家庭成员或密切关联人；识别并验证代表他人交易的自然人；验证代理人授权及身份。Art.20(2)：措施范围按个案风险分析（Art.10 业务风险评估、Annex I 变量、Annex II/III 风险因子），高风险 EDD、低风险 SDD；Art.20(4) 须随时向监管证明措施适当。
- 来源：
  - https://www.springlex.eu/en/packages/aml/amlr-regulation/article-19/
  - https://www.springlex.eu/en/packages/aml/amlr-regulation/article-20/

### 7.4 PEP（FATF R.12 + AMLD4 定义）
- R.12：对外国 PEP（及作为受益所有人的 PEP）须：风险管理系统识别；高级管理层批准建立或继续关系；合理措施确定财富来源与资金来源；强化持续监控。国内 PEP 与国际组织 PEP 按风险判断，高风险时同样适用。家庭成员与密切关联人适用同一制度。离任后按风险继续处理，行业常见 12–24 个月（二手）。
- AMLD4 Art.3(9) PEP：现任或曾任重要公共职能者（国家元首/政府首脑、部长、议员、最高/宪法法院法官、中央银行董事、大使、高级军官、国有企业与国际组织高管），不含中层及以下；Art.3(10) 家庭成员：配偶或等同者、子女及其配偶、父母；Art.3(11) 密切关联人：与 PEP 共同受益所有法人/安排或有密切业务关系者、为 PEP 事实利益设立实体的唯一受益所有人。
- 来源：
  - https://40recommendations.com/r/12-politically-exposed-persons/
  - https://www.legislation.gov.uk/eudr/2015/849/article/3

### 7.5 持续尽调、复审周期与记录保存
- INR.10 持续尽调：审查交易与客户画像一致性；风险变化时更新 CDD；高风险关系定期复审；对既有客户按重要性与风险在适当时机应用 CDD。
- 行业复审周期：高风险 1 年、中风险 2–3 年、低风险 3–5 年（PwC：高 1 年/中 2–3 年/低 3–5 年；Moody's：low 5 年、medium 3 年、high 1 年）。EU AMLR Art.26(2) 法定上限：适用 EDD 的高风险客户不超过 1 年，其他客户不超过 5 年（详见第 8 节）。
- FATF R.11 记录保存：所有交易记录至少 5 年（交易完成后）；CDD 记录、账户文件、业务往来、分析结果至少 5 年（业务关系结束或偶发交易后）；记录须足以重建单笔交易并能迅速提供给主管机关；不少辖区延长到 10 年。
- 来源：
  - https://40recommendations.com/r/11-record-keeping/
  - https://40recommendations.com/r/10-customer-due-diligence/
  - https://www.pwc.com/sg/en/consulting/assets/pkyc-a-new-approach-to-periodic-reviews.pdf （抓取被拒，内容来自搜索摘要）
  - https://www.moodys.com/web/en/us/kyc/resources/insights/innovative-risk-monitoring-with-perpetual-kyc.html

---

## 8. 持续监控 / Perpetual KYC（pKYC）

### 8.1 可借鉴的模型
- 两层机制：周期复审（risk-based 日历）+ 事件触发复审（trigger-based）；触发事件按优先级分层处置，而非一律全量复审。
- 触发事件清单（行业汇总）：新制裁/PEP 列名（Moody's、MemberCheck）；负面媒体（MemberCheck、CheckFile）；受益所有人/董事变更（Moody's、MemberCheck、CheckFile）；注册地址变更（Moody's）；交易模式偏离已知画像（MemberCheck）；证件到期与风险评级变化（FacePhi）；业务转向高风险活动（MemberCheck）；州级许可吊销（CheckFile）。
- 分级处置示例（CheckFile，US）：新增 OFAC SDN 指定 → 立即（OFAC 严格责任）；检测到可疑活动 → 30 天内（BSA 31 USC §5318(g)）；受益所有权变更（31 CFR 1010.230）→ 知悉后入队，30 天内更新 CDD；负面媒体 → 72 小时复核队列；州级不利行动 → 在复审周期内；地址小变更 → 仅更新记录不升级。每类触发需定义逻辑、阈值、责任人与 SLA；"over-triggering" 是主要风险。
- 数据来源：制裁/PEP/负面媒体实时或准实时数据流；公司注册库变更；交易监控；证件有效期。
- 供应商/网络侧实现：ComplyAdvantage `is_monitored` + `monitored_search_updated{new, updated, removed}` + `monitor/acknowledge`；Onfido `watchlist_monitors` + `watchlist_monitor.matches_updated`；Adyen 周期 data review（confirmDataReview，30/60 天 deadline，逾期能力取消）；Checkout.com 周期复核 `requirements_due` + 90 天；Visa PF 持续交易/网站监控与 VDMP/VFMP 阈值预警；Infinicept：OFAC 至少每月重筛整个组合；Jumio 持续监控需联系客户经理。

### 8.2 规则 / 门槛
- **EU AMLR Art.26**：(1) 对业务关系（含交易）持续监控，核对与客户、业务、风险画像一致，必要时资金来源与去向；多产品关系覆盖全部产品；集团内共享客户信息需纳入。(2) 客户文件/数据保持最新，更新间隔按风险且 "shall not exceed one year for higher risk customers（适用 EDD 者）, or five years for all others"。(3) 事件触发更新：客户相关情况变化；当年有联系客户核实受益所有人信息或履行 Directive 2011/16/EU 的法定义务；获悉与客户相关的新事实。(4) 定期核查定向金融制裁合规（Art.20(1)(d)），频率按风险；信贷/金融机构在每次新制裁指定时必须核查。(5) AMLA 在 2026-07-10 前发布持续监控指南（2026-06-03 发布征求意见稿，2026-09-03 截止；强调复审深度按风险）。
- 行业基线周期：高风险 1 年、中风险 3 年（或 2–3 年）、低风险 5 年（或 3–5 年）；MemberCheck 总结 "every one to five years depending on risk rating"。
- FinCEN CDD Final Rule（2018-05 生效）第四要素："Conducting ongoing monitoring to identify and report suspicious transactions and, on a risk basis, to maintain and update customer information"；FAQ Q14：持续监控含交易监控与在知悉相关信息时按风险更新客户信息。
- 来源：
  - https://www.springlex.eu/en/packages/aml/amlr-regulation/article-26/
  - https://didit.me/blog/eu-amlr-ongoing-monitoring-article-26/
  - https://cms.law/en/gbr/legal-updates/amla-publishes-draft-guidelines-on-ongoing-monitoring-under-article-26-5-amlr
  - https://www.moodys.com/web/en/us/kyc/resources/insights/innovative-risk-monitoring-with-perpetual-kyc.html
  - https://www.pwc.com/sg/en/consulting/assets/pkyc-a-new-approach-to-periodic-reviews.pdf
  - https://membercheck.com/blog/perpetual-kyc-continuous-aml-monitoring/
  - https://www.checkfile.ai/en-US/blog/perpetual-kyc-continuous-customer-monitoring
  - https://facephi.com/observatory/en/pkyc-continuous-identity-verification-2/
  - https://www.alloy.com/blog/perpetual-kyc-risk-based-compliance

---

## 9. 对 client-master / compliance 设计最直接可复用的事实归纳

1. 需求清单型状态机：Stripe `requirements{currently_due, eventually_due, past_due, pending_verification, current_deadline, disabled_reason, errors, alternatives}` + `future_requirements` + 能力级 requirements + v2 `awaiting_action_from` / `impact.restricts_capabilities` / `minimum_deadline`，是最成熟的"缺什么、何时到期、影响哪个能力、谁该行动"表达；Checkout.com `requirements_due[{field, reason}]` 是简化版。
2. 关联方关系两种建模：布尔集合 + 持股比例（Stripe `relationship.{owner, director, executive, representative, authorizer, legal_guardian} + percent_ownership`）或类型化关联（Adyen `entityAssociations.type`、Sumsub `types` + `subtypes: ownershipUbo | controlUbo | substituteUbo`、Ballerine `position[]`）；三家都显式区分"持股 UBO / 控制 UBO / 替代 UBO（高管）"，对应 FATF 瀑布。
3. 验证错误三层结构：Adyen `type / code / message / subErrors / remediatingActions`（含"补救动作码"）；Stripe `requirement / code / reason`；Sumsub `rejectLabels + reviewRejectType FINAL|RETRY + moderationComment(可展示)/clientComment(内部)`。
4. 证据式声明记录 `{date, ip, user_agent}`（所有权声明、董事声明、代表授权声明、ToS），Checkout.com 周期复核也要求提交 declaration。
5. 文件槽按用途命名（Stripe `documents.*`、Adyen document `type`、Mangopay `Type`），并有签发时效规则（注册/税务 12 个月、所有权结构图 6 个月、个人地址 3 个月、CoI <3 个月、自拍 24 小时）与结果码（`details_code` / `RefusedReasonType` / Adyen `1_xx`）。
6. 周期复核以 "deadline + capability 停用" 驱动：Adyen 30/60 天，Checkout.com 90 天，Stripe `current_deadline` 后禁 payouts/charges，AMLR 法定上限 1 年（高风险）/5 年。
7. 筛查命中处置枚举：ComplyAdvantage `no_match | potential_match | true_positive | false_positive | unknown`（+ `true_positive_approve/reject`）、`risk_level`、`is_whitelisted`、持续监控 diff 事件（new/updated/removed）；Onfido breakdown `sanction / politically_exposed_person / legal_and_regulatory_warnings / adverse_media`；Ballerine Alert `state: triggered → under_review → escalated / rejected / dismissed / cleared`。
8. PayFac 子商户义务：PF ID + Sponsored Merchant ID 入授权报文（清算仅 PF ID）；每个 SM 正确 MCC；VMSS（Visa）/ MATCH（Mastercard）查询与终止回报；principals 姓名与国籍可随时报送；principals 负面背景核查；信用/背景/推荐人核查与场所/网站检查（Mastercard）；USD 1 million 年量直签门槛（Visa 两年关系 + 报告 + 收单监督或特定 MCC 可豁免；Mastercard 无豁免）；禁止签约其他 PF / outbound telemarketer / Staged wallet / Ramp Provider；保证金由收单行持有；尽调记录保留 ≥2 年（Mastercard 2010）/ CDD 记录 5 年（FATF）；PF 对 SM 交易承担财务责任，SM 视同收单行的商户。
