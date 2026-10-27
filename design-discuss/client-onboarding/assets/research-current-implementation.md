# 入驻 + 合规链路梳理笔记（只读，未改任何文件）

路径均相对仓库根 /Users/kenny/Desktop/Workspace/Code-MoR。缩写：
- CM = paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master
- CMAPI = paytech/client-master/paytech-client-master-api/src/main/java/com/paytech/client/contract/api
- CO = paytech/compliance/paytech-compliance-core/src/main/java/com/paytech/compliance
- COAPI = paytech/compliance/paytech-compliance-api/src/main/java/com/paytech/compliance/api
- REQ = requirements/20260914/client-console-注册与客户入驻

## 0. 结论

- **提交校验：** 必填项写死在代码里（ClientSubmissionValidator + yml 目录）。材料要求在运行时读 compliance 的 MaterialRequirementsApi，来源是数据库 t_dd_program_requirement。
- **Vault：** 只有业务联系邮箱和电话进 Vault（EMAIL_ADDRESS_V1 / PHONE_E164_V1，STABLE 模式）。
  - 证件号 identifier_value 在 CCT 库中明文存放。
  - 提交时证件号也以明文经 I01 发给 compliance。
- **取值来源：** organization_type、identifier_type、business_model、industry 来自 client-master application.yml 的 onboarding.form-catalog。address_type 写死为 RESIDENTIAL/REGISTERED。contact_type 为闭合值 EMAIL/PHONE。
- **案件状态：** 表声明了 9 个状态，代码只用到 SUBMITTED、RETURNED、APPROVED、REJECTED，子任务另用 WITHDRAWN。DRAFT、IN_REVIEW、WAITING_EVIDENCE、EXPIRED 在代码中没有出现。
- **只有表没有代码：** t_dd_check、t_screening_run/hit/resolution、t_dd_program_check、t_dd_program_screening、t_dd_party_snapshot 只有 DDL，没有任何读写代码。
- **已有代码的资格表：** t_role_qualification、t_qualification_state_change、t_qualification_decision 自 2026-10-24 起已有读写代码。
- **撤回：** 没有自助撤回功能。WITHDRAWN 只是保留的状态值，没有对应的 Processor。
- **外部渠道：** channel-connector 的标准 API 目录是数据库数据，目前没有 KYC 或筛查类 API。
- **工作流：** Platform 工作流没有供业务服务调用的启动接口，只能人工发起，回调只通过 MQ 发出。

## 1. client-master 入驻链路

### 1.1 API 合同

所有接口都是 BFF 经私网调用，身份取自请求头 X-Gateway-Actor-Subject / X-Gateway-Session-Id，部分接口还要求 X-Caller-Service。

**客户入驻接口**
- C01 GET /client-onboarding/v1/start（CMAPI/onboarding/ClientOnboardingStartApi.java:8）
  - 响应 StartResponseBody：applications[ApplicationSummary(applicationId, applicationNo, clientType, status, active, savedAt)]、hasMore（ClientDraftModels.java:11-16）。
- C03 GET /client-onboarding/v1/form-options?clientType（ClientFormOptionsApi.java:16）
  - 响应字段：clientType、catalogStatus、organizationTypes、businessModels、identifierTypes{code, label, issuingCountryMode}。
  - 定义见 response/ClientFormOptionsResponseBody.java:14-22、IdentifierOptionResponseBody.java:13-20。
  - BFF 会另外组合 Risk 的材料要求。
- 申请与草稿（ClientDraftApi.java:9-19）
  - C04 POST /client-onboarding/v1/applications，请求体 StartApplicationRequestBody(clientType)。
  - C05 GET /{applicationId}。
  - C06 PATCH /{applicationId}/draft，请求体 DraftPatchRequestBody(dirtyFields, values: Map<分区, Map<字段, 值>>)。
  - ClientDraftResponseBody 字段：applicationId、applicationNo、clientId、clientType、subjectPartyId、status、savedAt、editable、values、people、relations、documents（ClientDraftModels.java:19-28）。
- 人员与关系 C07–C11（ClientPeopleApi.java:10-40）
  - POST /{id}/people，PATCH /people/{partyId}。
  - POST /relations，PATCH /relations/{relationId}，DELETE /relations/{relationId}。
  - RelationFields 字段：relationType、ownershipPercentage、effectiveFrom、effectiveTo（ClientPeopleModels.java:8-20）。
- 材料 C12/C13（ClientDocumentApi.java:10,15）
  - POST /{id}/documents，DELETE /documents/{documentId}。
  - 请求体 DocumentReferenceRequestBody(subjectPartyId, requirementCode, documentType, fileAddress)（ClientDocumentModels.java:12）。
- 提交 C14 / 提交前核对 C16（ClientSubmissionApi.java:30,47）
  - C14 POST /{id}/submissions，C16 GET /{id}/submission-check。
  - 资料不完整返回 422 SUBMISSION_INCOMPLETE，证件冲突返回 409 IDENTITY_CONFLICT。
  - 不读取 Idempotency-Key，重复提交按申请当前状态处理。

**运营接口**
- O02 申请资料：GET /client-onboarding-admin/v1/cases/{caseId}/application（ClientApplicationAdminApi.java:26）。不 Reveal 联系方式。
- O03/O04 分类（ClientClassificationAdminApi.java:32-75）
  - GET /classification-options。
  - GET 和 PUT /cases/{caseId}/classifications。
  - POST /classification-confirmation。
  - 分类项为 items[industryCode, mccCode, primary]，1–20 项，恰好一项主分类。

**工作区接口**
- GET /client-workspace/v1/client-summaries（CMAPI/workspace/ClientWorkspaceApi.java:25）。
- 响应字段：clientId、clientNo、clientType、clientStatus、displayName、country、businessModelCode、approvedAt。

### 1.2 Processor 与职责（CM/service/processor/）

写操作 Processor 都继承 AbstractLockProcessor。申请锁为 client-onboarding:application:{id}，账号名额锁为 client-onboarding:account-slot:{accountId}。

- CreateClientApplicationProcessor（C02/C04）：一个账号只能有一份未完成申请；已有时返回原申请。
- SaveClientDraftProcessor：Vault 调用位于两段事务之间。
- 人员与关系：CreateClientPersonProcessor、SaveClientPersonProcessor、CreateClientRelationProcessor、SaveClientRelationProcessor、RemoveClientRelationProcessor。
  - SaveClientPersonProcessor 在事务前查询 commercial 合同是否引用该人员。
- 材料：AssociateClientDocumentProcessor、RemoveClientDocumentProcessor。
- 提交：SubmitClientApplicationProcessor 和 CheckClientSubmissionProcessor，共用 ClientSubmissionPreparer。
- 分类：ReplaceClientClassificationProcessor、ConfirmClientClassificationProcessor，锁 client-onboarding:review-case:{caseId}。
- 审核结果：ApplyClientReviewResultProcessor 落地 RETURNED/APPROVED/REJECTED（ApplyClientReviewResultProcessor.java:41-110）。
  - submissionId 或 caseId 不是当前值时返回 STALE。
  - 只有 APPROVED 时 Client 从 PENDING 变为 ACTIVE，同时写 CLIENT_ACTIVATED 历史，并登记 Platform 投影。
- 其他：只读的 Get* 系列；运营主档的 ChangeAcquiringQualificationProcessor、ReplacePartnerRolesProcessor。
- 撤回：没有撤回 Processor。WITHDRAWN 只出现在枚举 CM/domain/enums/ClientApplicationStatus.java:5-17。

### 1.3 审核结果消费

- 消费者：ClientReviewResultListener（CM/ext/mq/ClientReviewResultListener.java:23-25）。
- 订阅 compliance.events，事件类型 ClientReviewDecided。
- 队列路径：client-master.compliance.hash → p0..p3，失败进 dlq。
- 转换器 ClientReviewResultMessageConverter：
  - 校验 sourceService=compliance、aggregateType=DD_CASE、schemaVersion=1。
  - 读取 applicationId、caseId、submissionId、outcome、decisionId、decidedBy。

### 1.4 三个补偿 Handler（CM/service/handler/）

| Handler | 事件类型 | 作用 |
|---|---|---|
| ClientReviewRegistrationHandler（:24） | CLIENT_REVIEW_REGISTRATION | C14 提交后调用 Risk I01；eventKey 为 submissionId；成功后申请从 SUBMITTED 变为 UNDER_REVIEW |
| ClientClassificationHandler（:24） | CLIENT_REVIEW_CLASSIFICATION | O04 确认分类后调用 I02；eventKey 为 caseId |
| ClientTenantProjectionHandler（:12） | CLIENT_TENANT_PROJECTION | 调用 Platform PLT-I17 |

### 1.5 提交校验

**必填项写死在 CM/domainservice/validator/ClientSubmissionValidator.java**
- checkProfile（67-84）
  - 组织：legalName、incorporationCountry、organizationType、incorporationDate。
  - 个人：givenName、familyName、birthDate、nationalityCountry、residenceCountry。
  - 共同：addressLine1、city、countryCode。
- checkIdentifier（87-108）：证件类型、签发国、号码、有效期。
- checkBusiness（127-134）：businessModel、description、expectedVolume、expectedTicket、currency。
- checkContacts（137-145）：EMAIL 和 PHONE 都必须有。
- 组织申请至少一名关联人员（约第 60 行）。

**材料要求在运行时读 compliance**
- ClientSubmissionPreparer.requirements() 对每个资格类型调用 ClientMaterialAuthorityClient.requirements()，再调用 MaterialRequirementsApi.read（CM/integration/ClientMaterialAuthorityClient.java:24-40）。
- checkMaterials（111-123）只检查 requiredFlag=true 的项。
- 资格类型：主体组织为 ORGANIZATION_KYB，主体个人为 INDIVIDUAL_KYC，关联人员为 RELATED_PERSON_KYC（ClientSubmissionPreparer.java:103,116）。
- 文件核验调用 Platform InternalFileApi.getFile（ClientMaterialAuthorityClient.java:57-70），检查上传人、扫描状态 AVAILABLE、对象状态 ACTIVE，并比对 contentHash。

### 1.6 Vault 使用

- ClientDraftContactClientImpl 只处理 email 和 phone（CM/integration/impl/ClientDraftContactClientImpl.java:12,25）。
  - Schema 分别为 EMAIL_ADDRESS_V1 / PHONE_E164_V1。
  - TokenMode.STABLE，purpose 为 CLIENT_ONBOARDING_CONTACT。
  - 掩码由 CCT 自己生成。
- 落库位置：t_party_contact.contact_vault_token / contact_masked_value（paytech/client-master/docs/database/sql/client-master-schema.sql:102-112，token 列在第 106 行）。
- 姓名、生日、地址、证件均为明文。

### 1.7 证件号存储

- t_party_profile_identifier.identifier_value 是明文 VARCHAR(128)（client-master-schema.sql:80）。
- 规范化值存在 t_party_identity_key.normalized_identifier_value，唯一键 uk_party_identity_norm(type, issuer_scope_key, normalized)（schema:55-67）。
- DO 类 PartyProfileIdentifier 不映射号码字段（CM/core/dal/dataobject/PartyProfileIdentifier.java）。
- I01 以明文发送 identifierValue（CM/integration/impl/ClientReviewRegistrationClientImpl.java:113）。
- 材料发给 compliance 时，用 fileAddress 解析出 fileId 后发送（同文件 :143）。

### 1.8 类型取值来源

- **yml 目录：** organization_type、identifier_type（含 issuing-country-mode）、business_model、industries。
  - 位置：paytech/client-master/paytech-client-master-app/src/main/resources/application.yml:82-115。
  - 绑定类：ClientFormCatalogProperties，@ConfigurationProperties("onboarding.form-catalog")。
  - 表注释（schema:48、89）仍写"司法辖区目录"，至今未改。
- **address_type 写死：**
  - 个人为 RESIDENTIAL，组织为 REGISTERED（CM/domainservice/repository/impl/ClientDraftRepositoryImpl.java:68）。
  - 关联人员为 RESIDENTIAL（ClientPeopleRepositoryImpl.java:18,35）。
- **contact_type：** 闭合值 EMAIL/PHONE（schema:105）。
- **t_client_application.qualification_type：** INDIVIDUAL_KYC 或 ORGANIZATION_KYB（schema:243）。
- **开关：** onboarding.draft-enabled 与 onboarding.recovery-enabled 默认都是 false（application.yml:78-80）。

### 1.9 CheckoutCandidate 两段式解析

- 接口（paytech/client-master/paytech-client-master-api/src/main/java/com/paytech/client/master/api/checkout/ClientMasterCheckoutApi.java:34-57）：
  - POST /client-master/v1/checkout-candidates。
  - POST /client-master/v1/checkout-candidates/verify。
  - GET /client-master/v1/party-profiles。
- 在同一个只读快照中读取这些表（CM/domainservice/service/impl/CheckoutCandidateQueryServiceImpl.java:73-90）：
  - client、client_business_profile、client_business_classification、client_entity_affiliation
  - operating_entity、entity_source_relation、operating_entity_scope、operating_entity_application
  - local_partner、partner_role、party_profile
  - consumer_legal_document、seller_disclosure
  - business_change_record（根版本）、client_stored_card_setting

## 2. compliance 链路

### 2.1 接口

**ClientReviewRegistrationApi**，基础路径 /client-onboarding-review/v1（COAPI/ClientReviewRegistrationApi.java:17）
- I01 POST /submissions，请求体 ClientReviewRegistrationRequestBody，响应 {sourceSubmissionId, caseId}。
  - 顶层字段：sourceSubmissionId、applicationId、applicationNo、clientId、clientType、requestedByAccountId、submittedAt、business、subjects、relations、documents。
  - business：businessModelCode、websiteUrl、businessDescription、expectedVolume、expectedTicket、currency。
  - subjects：partyId、profileId、subjectRole、qualificationType、个人或组织字段、address*、identifierType、issuingCountry、identifierValue、identityKeyId、validFrom、validTo。
  - relations：relationId、relatedPartyId、relationType、ownershipPercentage、effectiveFrom、effectiveTo。
  - documents：documentId、subjectPartyId、requirementCode、documentType、fileId、contentHash。
- I02 POST /cases/{caseId}/classifications（:48），字段 applicationId、submissionId、confirmedBy、confirmedAt、items。
- I03 未实现。

**ClientReviewAdminApi**，基础路径 /client-onboarding-admin/v1（COAPI/ClientReviewAdminApi.java:25-144）
- O01 GET /cases，参数 page、size、status、qualificationType、applicationId。
- O02 GET /cases/{caseId}，响应字段：caseId、caseNo、applicationId、clientId、sourceSubmissionId、qualificationType、status、version、submittedAt、decidedAt、publicNote、returnSection、subjects[requirements[evidence]]、classifications、approvalBlockers。
- O05 POST /evidence/{evidenceId}/review，outcome 为 ACCEPTED 或 REJECTED。
- O07 POST /return，字段 reasonCode、section、publicNote。
- O08 POST /approval、O09 POST /rejection，字段 reasonCode、reason、publicNote。
- O06 /checks 未实现。

**ClientReviewResultApi**
- GET /client-onboarding-review/v1/applications/{applicationId}/result。
- 响应字段：applicationId、caseId、status、outcome、returnSection、publicNote、decidedAt。

**MaterialRequirementsApi**
- GET /internal/due-diligence/material-requirements?qualificationType。
- 响应 {programId, programVersionId, items[requirementCode, documentType, requiredFlag]}（MaterialRequirementsModels.java:15,25）。

**SettlementControlApi**，路径 /compliance/v1/settlement-controls
- POST 创建，幂等键 sourceActionEffectId。
- GET，参数 ownerType、ownerId、status。
- 只有合同，app 中没有 Controller。core 中只有 SettlementControlMapper.findBlockedRoleIds 被资格判定读取。

**资格相关接口**
- QualificationEvaluationApi：POST /compliance/v1/qualifications/evaluations，只供 commercial 调用，一次 1–50 个主体。
- QualificationAdminApi，路径 /compliance-admin/v1/qualifications：列表、history、manual-decisions、revocation。

### 2.2 材料要求目录

- **资格类型 → programCode 映射：** 配置项 onboarding.materials.programs-json（paytech/compliance/paytech-compliance-app/src/main/resources/application.yml:130-132），由 CO/domainservice/policy/MaterialProgramResolver.java 解析；缺映射时返回 503。
- **要求明细：** 在数据库中，按 t_dd_program → t_dd_program_version（ACTIVE）→ t_dd_program_requirement 读取（paytech/compliance/paytech-compliance-core/src/main/resources/mapper/compliance/MaterialRequirementsMapper.xml）。
- **初始化数据：** REQ/db/implementation/risk-generic-materials-init.sql:6-21

| 资格类型 | 必需 | 可选 |
|---|---|---|
| INDIVIDUAL_KYC | IDENTITY / IDENTITY_DOCUMENT | ADDRESS / PROOF_OF_ADDRESS、OTHER / SUPPORTING_DOCUMENT |
| ORGANIZATION_KYB | REGISTRATION / REGISTRATION_CERTIFICATE | OWNERSHIP / OWNERSHIP_STRUCTURE、AUTHORIZATION / AUTHORIZATION_LETTER、ADDRESS、OTHER |
| RELATED_PERSON_KYC | IDENTITY | OTHER |

### 2.3 状态机

- 表声明 9 个状态（paytech/compliance/docs/database/sql/compliance-schema.sql:84）。代码中没有状态机类，状态变化分散在以下位置：
  - 登记时主任务和子任务都写 SUBMITTED（CO/service/processor/RegisterClientReviewProcessor.java:61）。
  - 结束动作定义在 CO/domain/ClientReviewAction.java:11-17：

| 动作 | 主任务 | 子任务 |
|---|---|---|
| RETURN | RETURNED | RETURNED |
| APPROVE | APPROVED | APPROVED |
| REJECT | REJECTED | WITHDRAWN |

- 是否在审靠 active_slot=1 判断（CO/domain/ClientReviewFamily.java:45）。
- **通过门禁**（CO/domainservice/policy/ClientReviewApprovalPolicy.java:36-56），以下全部满足才能通过：
  - 主任务处于活动状态；
  - 子任务处于活动状态；
  - 没有状态为 RECEIVED 的证据；
  - 必需材料都已 ACCEPTED；
  - 已收到分类。
- **决定处理**（CO/service/processor/DecideClientReviewProcessor.java）：
  - 通过或拒绝：写 t_qualification_decision，并投影到 t_role_qualification。
  - 退回：只写 public_note 和 return_section。

### 2.4 ClientReviewDecided 事件

- 字段：caseId、caseVersion、applicationId、submissionId、outcome（RETURNED/APPROVED/REJECTED）、returnSection、publicNote、decidedAt、decisionId、decidedBy（COAPI/event/ClientReviewDecidedPayload.java:24-55）。
- aggregateType=DD_CASE。
- 由 ClientReviewResultHandler（CLIENT_REVIEW_RESULT）补偿发布。

### 2.5 表是否有代码

- **只有 DDL 和测试清理代码，没有读写：**
  - t_dd_check（schema:190）
  - t_screening_run / t_screening_hit / t_screening_resolution（schema:207 起）
  - t_dd_program_check、t_dd_program_screening
  - t_dd_party_snapshot：RegisterClientReviewProcessor.java:53 注释明确不写。
- **有 Mapper 读写：** t_qualification_decision、t_role_qualification、t_qualification_state_change（mapper/compliance/RoleQualificationMapper.xml:8,39 等）。
  - 2026-10-24 的改动脚本：paytech/compliance/docs/database/sql/changes/2026-10-24-qualification-manual-and-role-id.sql。

### 2.6 qualification_type 取值

- Schema 允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC（schema:8,72,278,299）。
- Java 枚举 QualificationType 只有前 4 个，不含 RELATED_PERSON_KYC。
- Java 枚举 MaterialQualificationType 只有 3 个 KYC/KYB 类。
- client-master 的 OE 申请还提到 ENTITY_SCOPE_*（client-master-schema.sql:296）。
- QualificationRoleType 取值：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER。

## 3. 需求与设计文档

### 3.1 REQ/requirement.md

- 第 49 行：申请共 7 个状态。
- AC-13（:71）：
  - RETURNED 可以修改原申请；
  - REJECTED 不能复活；
  - APPROVED 后只读；
  - 一个账号的个人和组织申请合计只能有一份未完成。
- :36：人工审核也不能绕过程序要求的材料、检查和筛查。
- :96：不能虚构外部筛查成功。

### 3.2 REQ/simplification-design.md

- :9-17：单页分区，依次为基础、经营、组织人员（个人不显示）、材料；保存草稿和提交分开；草稿字段允许 NULL。
- :28-44：客户不填行业和 MCC，由运营在审核中确认；分类绑定当前 Case；Risk 未收到分类前不能批准。
- :46-66：状态表；取消资料版本；每次重提生成新 submissionId 和新任务；旧任务的结果不生效。
- :99-128：字段与表的映射。

### 3.3 REQ 其他文件

- **design.md :44-52：** 一账号一份申请由唯一键 uk(requested_by_account_id, active_slot) 保证；不做自助撤回。
- **api-contract.md：**
  - :23-75 列出 A01–I03 全部操作。
  - :126 ReturnForCorrection 的 section 取值为 BASIC/BUSINESS/PEOPLE/MATERIALS。
  - :125 CheckResult 对应 t_dd_check，未实现。
- **data-design.md :60-80：**
  - Risk 新增 source_submission_id、public_note、resolution_note、return_section。
  - CheckResult 本应落 t_dd_check。
  - 资料快照原计划使用 Vault。
- **generic-material-form-proposal.md：**
  - :18-37：不按国家区分，每类资格一个全局程序；系统只做确定性检查，材料是否充分由人工判断。
  - :78-82：证件号规范化为 NFKC、去掉分隔符、转大写。
  - :84-88："材料充分"确认门禁未实现。
- **rejection-termination-design.md :22-61：**
  - 被拒后同类型重新申请时，复用原 Client 和 Party。
  - 关系转移到新申请；材料需要重新上传。

### 3.4 V2 架构设计（requirements/20261017/architecture-V2骨架与模块搬迁/design/）

- **06-cct-two-stage-resolution.md：**
  - :5-9：client-master 持有主档和法律文档；commercial 编排 /checkout-context/v1/resolutions。
  - :13-22：candidates、verify、party-profiles 三个接口。
  - :54：三个补偿 Handler 随 client-master 迁移。
- **07-risk-split.md：**
  - :5-21：结算控制三表归 compliance；risk-decision 经 compliance-api 读写。
  - :41-42：开关 onboarding.review.enabled 和 onboarding.materials.enabled 随 compliance。
- **03-module-and-table-ownership.md :143-169：** compliance 表清单；check、screening、snapshot 等表标注"无代码，仅 Schema 归属"。

### 3.5 PayFac 决定（agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md，状态 Proposed）

**第 5 节（:74-101）现状**
- compliance 只有 Client 入驻资格。
- 表中 OE/Partner 资格查询标为 ❌，但现在已有 QualificationEvaluationApi 和手工登记，此处已部分过时。
- OE 收单资质标为 ❌。

**第 6 节（:103-113）待修订**
- client-master 和 compliance 数据库文档需补：OE 收单资质三档、Partner 角色、按角色的资格、Client MCC 与 OE MCC 的兼容约束。
- 法务合同需写明子商户 KYC/AML 是 PayFac 的义务（R8）。

**第 9 节**
- 9.1 原则：
  - 模式即数据，用角色绑定代替模式分支；
  - fund_flow 显式表达；
  - 快照不是执行许可，资格要实时检查。
- 9.2 角色：CLIENT、SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM。
- U1（:168）：Party 可有多个角色；OE 增加收单资质。
- U2（:169）：compliance 按（角色, 主体）提供只读资格查询，支持 MANUAL 手工登记；子商户 KYC 复用同一流程。
- U14：分账收款方的入驻复用 U1 和 U2。

**第 10 节 R1–R21（:203-227）**

| 编号 | 要点 |
|---|---|
| R1 | D8 推荐方定价与 D10 冲突 |
| R2 | P3 已过时 |
| R3 | 外部 PayFac 需要自己操作 |
| R4 | MoR 资质分三档，加 MCC 约束；Q8 交法务 |
| R5 | "备付金"改称"服务费预存款" |
| R6 | 平台作为服务商需卡组织登记并满足 PCI |
| R7 | C5 只能叠加在 C2 上 |
| R8 | KYC/AML 责任写入合同 |
| R9 | 服务边界是 PayFac-as-a-Service |
| R10 | 区分 AFFILIATED / EXTERNAL |
| R11 | 收入集中风险 |
| R12 | 显式 fund_flow 加断言，防止静默失败 |
| R13 | 快照采用角色绑定行 |
| R14 | 第一步范围锁定为 U1/U2/U5/U6 |
| R15 | 区分 ACQUIRER_SUB_MERCHANT 与 SPLIT_PAYEE |
| R16 | 债务人为 Partner 法律主体，账簿主体为收单 OE |
| R17 | 快照不是执行许可；AFFILIATED 不等于有代操作权 |
| R18 | 启用前必须通过六项门禁 |
| R19 | Partner 与 OE 必须是同一法律主体 |
| R20 | 订单冻结 effective_relationship_model_version |
| R21 | 子商户身份随 Attempt 冻结 |

### 3.6 场景文档（agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md）

基线日期 2026-09-10，仍基于 BCR、Version、Hash 的旧模型，部分内容已过时。

- :63-159：Party 与 UBO、DIRECTOR、SIGNATORY 三种独立关系（Nova / Aisha 示例）。
- :210-295：申请与材料；requirement_code 和 document_type 是开放代码。
- :297-319：Partner 入驻流程为 Provision Tenant → SUBMIT → Risk Tuple → 激活；REJECTED 后复用原 Role 重新提交。

## 4. channel-connector SERVICE 渠道

**决定**
- agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md:16-27：
  - SERVICE 渠道明确包含"KYC 筛查"。
  - 每个服务渠道最多一个 ACTIVE 技术账号，凭据存为 Vault Token。
  - 两个入口：支付渠道走 ChannelOperationApi，服务渠道走通用执行入口。
  - 白名单维度为"调用方服务 × 渠道 × 标准 API"，存表并在控制台维护。
- 能力规则见 agent-docs/decisions/2026-10-13-platform-standard-api-flow.md:12-20：
  - 标准 API 定义统一的请求和响应；渠道用原子 API 组成同步流程来实现。
  - 不编排资金反向操作，不保存执行记录，不自动重试。

**调用入口**
- POST /channel-connector/v1/service-executions（paytech/channel-connector/paytech-channel-connector-api/src/main/java/com/paytech/channel/connector/api/serviceexecution/ServiceChannelExecutionApi.java）。
- 请求头 X-Caller-Service；请求体 {channelCode, standardApiCode, input: JsonNode}。
- 响应 {executeStatus: SUCCESS|FAIL|ERROR, outputs: JsonNode}。ERROR 和超时都表示结果未知。
- 未授权返回 403 CHANNEL_CALL_NOT_GRANTED。

**标准 API 的定义方式**
- 用数据库表定义，不是枚举（paytech/channel-connector/docs/database/sql/channel-connector-schema.sql:310-485）：
  - t_standard_api：standard_api_code 是开放代码；
  - t_api_field：字段定义；
  - t_channel_api、t_channel_script、t_channel_standard_api：渠道实现；
  - t_channel_call_grant：调用授权，唯一键 (caller_service, channel_id, standard_api_id)，状态 ACTIVE/DISABLED。
- 管理接口：externalapi/StandardApiManagementApi、grant/ChannelCallGrantApi（/channel-connector/v1/channel-call-grants）。
- ChannelType 枚举包含 SERVICE（enums/ChannelType.java:16）。

**现有标准 API**
- 本地 e2e 备份 local-environment/e2e/backup/20261022T151611Z-card3ds-stage5/mor_channel_connector_v2e2e.sql 中只有 PAY、PAY_QUERY、REFUND、REFUND_QUERY、S5P2_ECHO_PING。
- 表注释中举例提到 GEOCODE。
- 没有 KYC、筛查、制裁类 API；channel-connector 代码中也没有 KYC/SCREEN 字样。

## 5. Platform 工作流与文件

### 5.1 工作流

- **接口：** WorkflowInstanceApi，基础路径 /workflow/v1（paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/workflow/WorkflowInstanceApi.java:37-267）。
  - 只接受 Operations 人工身份。发起实例 POST /instances 需要请求头 X-Gateway-Actor-Subject。
  - 请求体 StartWorkflowInstanceRequestBody：definitionId、versionNo、title、businessKey、startValues（FILE 类型的值为 "/files/v1/x"）、approvers、executors。
- **没有给业务服务调用的系统级启动接口。**
- **回调只走 MQ：**
  - 事件 WorkflowCallbackRaised v1，发布到 platform-support.events，订阅方按 callbackCode 过滤。
  - 字段：callbackId、callbackCode、trigger、definitionCode、versionNo、instanceId、businessKey、nodeKey、roundNo、fromStatus、toStatus、actorId、comment（api/workflow/event/WorkflowCallbackRaisedPayload.java）。
  - 依据：requirements/20261021/platform-support-工作流/technique.md:189-206、requirement.md:75-81。
- **三条红线**（design-discuss/workflow/design.md:26-30）：
  1. 不写业务数据；
  2. 不当执行开关；
  3. 不复制业务对象。
- 本期不对 Client 和 Partner 开放（requirement.md:93）。

### 5.2 文件

- **原则**（agent-docs/decisions/2026-10-22-file-upload-address-principles.md，决定 1-4）：
  1. 上传与表单提交分开；
  2. 业务数据存地址 /files/v1/{fileId}；
  3. 下载不经过业务服务；
  4. 文件服务不反调业务系统。
- **地址格式：** FileAddress 正则 ^/files/v1/[1-9][0-9]{0,18}$（paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/files/FileAddress.java:14-17）。
- **内部接口：** InternalFileApi，路径 /internal/files/v1（同目录 InternalFileApi.java:35-145）。
- **没有文件类型目录：**
  - 文件服务只记录 reason_code，由来源系统自定义，格式 [A-Z][A-Z0-9_]{0,63}（paytech/platform-support/docs/database/sql/platform-support-schema.sql:593）。
  - 入驻材料的 document_type 和 requirement_code 来自 compliance 的 t_dd_program_requirement。

## 6. 前端页面

**client-console-web**（路由定义在 client-console-web/src/main.tsx:137-144）
- 认证：/login、/register、/forgot-password、/reset-password、/verify-email、/check-email。
- 入驻：/onboarding、/onboarding/applications/:applicationId → OnboardingPage.tsx。
  - 单页包含表单、材料、审核结果。
  - 辅助模块：onboarding.ts、submission.ts、review-result.ts。
- 工作区：/workspace、/workspace/team、/workspace/developer/api-keys、/workspace/developer/webhooks。
- 账号：/account。

**operations-console-web**
- 入驻审核：/client-onboarding/reviews[/:caseId] → pages/ClientOnboardingReview.tsx 与 clientReview/ClientReviewDetail.tsx。
  - 材料核验与下载在 ClientReviewDetail.tsx:105-122。
  - 路由见 navigation/menu.ts:63、pageCapabilities.ts:330-332。
- 资格：/compliance/qualifications → pages/Qualifications.tsx。
- 主档：/master-data/operating-entities、/master-data/local-partners → OperatingEntities.tsx、LocalPartners.tsx，详情页在 masterData/*Detail.tsx。
- 文件：/file-records、/file-upload、/file-storage。
- 外部 API：/external-api/standard-apis。
- 工作流：/workflow/*。
- Vault：/vault/data-types、/vault/access-rules 等。
- **没有 Client 列表页或 Client 详情页。**

## 7. Vault

**当前 V1 能力**（agent-docs/VAULT_SERVICE_ARCHITECTURE.md:5-26）
- 只有 Protect 和 Reveal：POST /vault/v1/records/protect、POST /vault/v1/records/reveal。
- Token 模式有 REFERENCE 和 STABLE 两种。STABLE 依靠唯一键 uk(data_type, value_fingerprint) 保证同值得到同一 Token，目前用来代替 Blind Index 做等值关联。
- Blind Index 和脱敏属于"留待未来独立批准"（:54）；第 5 节 :172-207 是历史目标，不是现状。

**CCT 的限制**
- :112：CCT 只注册了 EMAIL_ADDRESS_V1 和 PHONE_E164_V1 两个单一标量 Schema，规范化由调用方负责。
- 明确禁止把姓名、生日、法律标识、地址包装后放进 Vault。证件号要进 Vault 需要新的决定。

**数据类型与访问规则的注册方式**（自 2026-10-14 起，见 agent-docs/decisions/2026-10-14-vault-policy-and-manual-access.md:9-17）
- 策略存在数据库中：
  - t_vault_data_type：data_type_code、token_mode、value_format（TEXT/JSON/REGEX）、data_class（STANDARD/SECRET）、人工加密和人工解密开关。
  - t_vault_access_rule：唯一键 (caller_service, action, data_type_code, purpose_code)。
  - 每次变更追加一条 t_vault_policy_change。
  - Vault 每 10 秒热加载一次。
- 新增数据类型的两种方式：
  1. 运营在 /vault/data-types 页面操作，经 /vault/v1/admin 接口维护；
  2. 按环境执行初始化 DML。参考：
     - requirements/20261014/operations-console-Vault数据类型与手动加解密/db/vault/dml-test.sql:10-19；
     - requirements/20261018/test-environment-V2部署与数据迁移/sql/21-vault-upgrade.sql:30-33（client-master-service 的 PROTECT/REVEAL，purpose 为 CLIENT_ONBOARDING_CONTACT）。
- 已有的专项例外都走"决定记录 + DML"的模式：CHANNEL_ACCOUNT_CONFIG_V1、PAYMENT_CARD_DEK、NOTIFICATION_PROVIDER_CREDENTIAL_V1。
