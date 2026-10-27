# 阶段 2 核对笔记：compliance 现状（对照 requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md §4 R5–R8、§7.2）

只读核对，基于工作树 HEAD（compliance 最后一次代码提交 `4b661830`，其后无 compliance 改动，`git status` 中 paytech/compliance 无未提交修改）。未运行任何测试。

路径缩写：
- `API` = paytech/compliance/paytech-compliance-api/src/main/java/com/paytech/compliance/api
- `APP` = paytech/compliance/paytech-compliance-app/src/main/java/com/paytech/compliance
- `CORE` = paytech/compliance/paytech-compliance-core/src/main/java/com/paytech/compliance
- `XML` = paytech/compliance/paytech-compliance-core/src/main/resources/mapper/compliance
- `TEST` = paytech/compliance/paytech-compliance-test/src/test/java/com/paytech/compliance
- `SCHEMA` = paytech/compliance/docs/database/sql/compliance-schema.sql
- `YML` = paytech/compliance/paytech-compliance-app/src/main/resources/application.yml

---

## 0. 结论速览（对阶段 2 修订最有影响的事实）

1. **没有 `QualificationChanged` 事件**：全仓 Java/YAML 无此名（仅出现在本需求 requirement.md）。资格手工登记 / 撤销只写库，不登记补偿、不发布任何事件（`CORE/service/processor/RevokeQualificationProcessor.java:59-67`、`CORE/domainservice/service/impl/QualificationServiceImpl.java:29-35`）。R7 的 REVOKED→SUSPENDED 需全新事件 + 补偿 Handler + 目录登记 + client-master 消费。
2. **`ClientReviewDecided` 升 v2 必须先改消费方**：client-master 转换器硬校验 `event.schemaVersion() != 1` 即拒（`paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/ext/mq/ClientReviewResultMessageConverter.java:25`）。发布端常量 `SCHEMA_VERSION = 1`（`API/event/ClientReviewDecidedPayload.java:18`），目录 `schemaVersion: 1`（`paytech/foundation/paytech-messaging/catalog/events.yaml:23-30`）。
3. **"同案件多轮 round_no" 与现有唯一键冲突**：`uk_dd_case_source` 含 `source_submission_id`（`SCHEMA:93`），`uk_dd_case_active` 按 role+qualification 只允许一个进行中案件（`SCHEMA:94`）。现行退回把主 / 子案件都关为 RETURNED（`CORE/domain/ClientReviewAction.java:11`），重提靠新 submission 新建案件。复用同一案件需改键或改 submission 身份语义。
4. **compliance 完全没有出站 HTTP**：无 `HttpServiceClientFactory`、`RestClient`、`VaultClient` 引用；README 明示（`paytech/compliance/README.md:17`）；且 `ComplianceArchitectureTest` 用**精确集合**锁死 core/app 依赖（`TEST/auto/architecture/ComplianceArchitectureTest.java:54-66`），加 Vault / http-client-support / loadbalancer 必须同步改该测试。
5. **ComplianceArchitectureTest 疑似已失败（未运行，静态推断）**：测试断言 metadata-only 前缀恰为 3 个（`TEST/auto/architecture/ComplianceArchitectureTest.java:174-175`），但 `4b661830` 已在 YML 加入第 4 个 `/compliance-admin`（`YML:67-68`），该测试文件最后修改在 `03adcc92`，未随之更新。阶段 3 实施前应先跑一次确认。
6. **筛查 / 检查 / 快照表只有 Schema 无代码**：`t_dd_check`、`t_screening_run/hit/resolution`、`t_dd_party_snapshot`、`t_dd_program_check`、`t_dd_program_screening` 无 DO、无 Mapper；只出现在测试清理器表清单。`t_dd_case_dependency` **有**完整 DO/Mapper/Repository（不属于"只有表"）。
7. **`SettlementControlApi` 没有任何实现**：全仓唯一引用是接口自身；compliance 只有一个只读 Mapper 查询 `findBlockedRoleIds`（`XML/SettlementControlMapper.xml:4-10`），供资格判定使用。
8. **案件状态实际只用到 5 个**：SUBMITTED / RETURNED / APPROVED / REJECTED / WITHDRAWN。IN_REVIEW、WAITING_EVIDENCE、EXPIRED、DRAFT 在代码中从未写入；PENDING_DEPENDENCY 连 Schema 注释都没有。

---

## 1. API 清单（paytech-compliance-api）

api 模块依赖被锁为 `paytech-common-core`、`spring-web`、`jakarta.validation-api`（`TEST/auto/architecture/ComplianceArchitectureTest.java:55-56`）。

### 1.1 ClientReviewRegistrationApi（I01 / I02，client-master 私网调用）
- 根路径 `@HttpExchange("/client-onboarding-review/v1")`（`API/ClientReviewRegistrationApi.java:17`）。
- I01 `POST /submissions`（`:31`），头 `X-Caller-Service`；请求 `ClientReviewRegistrationRequestBody`，响应 `ClientReviewRegistrationResponseBody{sourceSubmissionId, caseId}`（`API/response/ClientReviewRegistrationResponseBody.java:7,10`）。
  - 请求字段（`API/request/ClientReviewRegistrationRequestBody.java:21-83`）：sourceSubmissionId、applicationId、applicationNo、clientId、clientType、requestedByAccountId、submittedAt、business、subjects[]、relations[]、documents[]。
  - `ClientReviewSubjectBody`（`API/request/ClientReviewSubjectBody.java:13-135`）：partyId、profileId、subjectRole、qualificationType、givenName/middleName/familyName、birthDate、nationalityCountry、residenceCountry、legalName、tradeName、incorporationCountry、organizationType、incorporationDate、address 6 列、identifierType、issuingCountry、**identifierValue（原值）**、identityKeyId、validFrom、validTo。
  - `ClientReviewRelationBody`（`API/request/ClientReviewRelationBody.java:13-39`）：relationId、relatedPartyId、relationType、ownershipPercentage、effectiveFrom/To。
  - `ClientReviewDocumentBody`（`API/request/ClientReviewDocumentBody.java:13-38`）：documentId、subjectPartyId、requirementCode、documentType、**fileId**、contentHash。
  - `ClientReviewBusinessBody`（`API/request/ClientReviewBusinessBody.java:13-37`）：businessModelCode、websiteUrl、businessDescription、expectedVolume、expectedTicket、currency。
  - **控制器只取结构字段**，姓名 / 证件号 / 地址 / business 全部丢弃，不入库（`APP/ext/rest/ClientReviewRegistrationController.java:64-85` 只调用 getPartyId/getProfileId/getSubjectRole/getQualificationType/关系与材料 getter）；`CORE/service/processor/request/RegisterClientReviewRequest.java:25-107` 无任何 PII 字段。R3 "I01 只传 Token" 在 compliance 侧只需删 API 字段，不影响存储。
- I02 `POST /cases/{caseId}/classifications`（`API/ClientReviewRegistrationApi.java:48`）；请求 `ClientReviewClassificationsRequestBody{applicationId, submissionId, confirmedBy, confirmedAt, classifications[{industryCode, mccCode, primary}]}`（`API/request/ClientReviewClassificationsRequestBody.java:17-37`、`ClientReviewClassificationBody.java:13-22`）；响应 `ClientReviewCommandResponseBody{caseId, status, accepted, processing}`（`API/response/ClientReviewCommandResponseBody.java:7-16`）。

### 1.2 ClientReviewAdminApi（Operations BFF 调用）
- 根路径 `/client-onboarding-admin/v1`（`API/ClientReviewAdminApi.java:25`）；接口注释写明覆盖 O01、O02 Risk 部分、O05、O07—O09（`:21`），**O03 / O04 / O06 不存在**。所有方法带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`。
- O01 `GET /cases`（`:47`）：Query page、size、status、qualificationType、applicationId（白名单 `APP/configuration/ClientReviewRequestConfiguration.java:22`）。响应 `PagingResponseBody<ClientReviewCaseResponseBody>`。
- O02 `GET /cases/{caseId}`（`:68`）：`ClientReviewCaseResponseBody{caseId, caseNo, applicationId, clientId, sourceSubmissionId, qualificationType, status, version, submittedAt, decidedAt, publicNote, returnSection, subjects[], classifications[], approvalBlockers[]}`（`API/response/ClientReviewCaseResponseBody.java:9-51`）；subject 含 caseId、caseStatus、partyId、profileId、roleType、qualificationType、dependencyTypes[]、requirements[]（`ClientReviewSubjectResponseBody.java:9-30`）；requirement 含 requirementId、requirementCode、documentType、required、status、evidence[]（`ClientReviewRequirementResponseBody.java:9-24`）；evidence 只有 evidenceId、fileId、status（`ClientReviewEvidenceResponseBody.java:7-13`）。
- O05 `POST /cases/{caseId}/evidence/{evidenceId}/review`（`:88`），请求只有 `outcome`（`API/request/ClientReviewEvidenceReviewRequestBody.java:15`）——**无拒绝原因字段**。
- O07 `POST /cases/{caseId}/return`（`:107`），请求 `{reasonCode, section, publicNote}`（`API/request/ClientReviewReturnRequestBody.java:13-26`）——单一分区，**无需求级 errors[] 与期限**。
- O08 `POST /cases/{caseId}/approval`（`:125`）、O09 `POST /cases/{caseId}/rejection`（`:143`），请求 `ClientReviewDecisionRequestBody{reasonCode, reason, publicNote}`（`API/request/ClientReviewDecisionRequestBody.java:13-22`）。
- 需求 §7.2 要新增的 assignment / checks result / screenings hits resolution / risk-factors / risk-rating / recommendation / waiver 全部不存在。

### 1.3 ClientReviewResultApi
- `GET /client-onboarding-review/v1/applications/{applicationId}/result`（`API/ClientReviewResultApi.java:11,25`），响应 `{applicationId, caseId, status, outcome, returnSection, publicNote, decidedAt}`（`API/response/ClientReviewResultResponseBody.java:7-25`）——**无 errors[]、requirementsDue[]**。
- 实现取申请最近一次主任务（`CORE/service/processor/GetClientReviewResultProcessor.java:34`），结果集合硬编码 RETURNED/APPROVED/REJECTED（`:27`）。

### 1.4 MaterialRequirementsApi（R5 要被 `programs/resolve` 取代）
- `GET /internal/due-diligence/material-requirements?qualificationType=`（`API/MaterialRequirementsApi.java:10,22`），响应 `RequirementsResponseBody{programId, programVersionId, items[{requirementCode, documentType, requiredFlag}]}`（`API/MaterialRequirementsModels.java:13,22`）。
- Query 只允许 `qualificationType`，不接受 Body，响应 `no-store`（`APP/configuration/MaterialRequirementsRequestConfiguration.java:20,30-38`）。
- 控制器受 `onboarding.materials.enabled` 门控（`APP/ext/rest/MaterialRequirementsController.java:24`）。调用方：client-master `ClientDocumentConfiguration`（`paytech/client-master/paytech-client-master-app/src/main/java/com/paytech/client/master/configuration/ClientDocumentConfiguration.java:45-47`）。

### 1.5 SettlementControlApi
- `POST /compliance/v1/settlement-controls`、`GET ...?ownerType&ownerId&status`（`API/SettlementControlApi.java:21,34,51`）。**无 Controller、无 Processor、无调用方**（全仓 `SettlementControlApi` 只出现在接口文件本身）。README 与物理决策均写"阶段 0 仅合同"（`paytech/compliance/README.md:19`、`paytech/compliance/docs/database/COMPLIANCE_PHYSICAL_DECISIONS.md:8`）。

### 1.6 QualificationEvaluationApi（2026-10-24）
- `POST /compliance/v1/qualifications/evaluations`（`API/QualificationEvaluationApi.java:17,32`），头 `X-Caller-Service` 可选；请求 `subjects[{roleType, roleId}]`（`API/request/QualificationEvaluationRequestBody.java:19`、`QualificationSubjectBody.java:13,22`）；响应 `{evaluatedAt, results[{roleType, roleId, eligible, status, qualificationType, qualificationId, validFrom, validTo}]}`（`API/response/QualificationEvaluationResponseBody.java:12-15`、`QualificationEvaluationResultBody.java:7-46`）。
- 判定状态 ELIGIBLE / NOT_REGISTERED / REJECTED / REVOKED / EXPIRED / NOT_YET_VALID / SETTLEMENT_BLOCKED（`API/enums/QualificationEvaluationStatus.java:11-29`）。

### 1.7 QualificationAdminApi（2026-10-24）
- 根 `/compliance-admin/v1`（`API/QualificationAdminApi.java:25`）：
  - `GET /qualifications`（`:49`）Query roleType、roleId、qualificationType、status、page、size；响应 `QualificationResponseBody{qualificationId, roleType, roleId, qualificationType, status, effectiveStatus, validFrom, validTo, currentDecisionId, decisionSource, reviewStatus, version, updatedAt}`（`API/response/QualificationResponseBody.java:10-73`）。
  - `GET /qualifications/{id}/history`（`:71`）→ `QualificationHistoryItemBody` 15 字段（`API/response/QualificationHistoryItemBody.java:10-91`）。
  - `POST /qualifications/manual-decisions`（`:87`），请求 `{roleType, roleId, qualificationType, validFrom, validTo, reasonCode, basisNote, expectedVersion}`（`API/request/QualificationManualDecisionRequestBody.java:16-65`）。
  - `POST /qualifications/{id}/revocation`（`:107`），请求 `{expectedVersion, reasonCode, basisNote}`（`API/request/QualificationRevocationRequestBody.java:16-29`）。
- 两个资格 Controller **没有** `@ConditionalOnProperty`，始终开启（`APP/ext/rest/QualificationEvaluationController.java:24`、`QualificationAdminController.java:32-34`），与审核 / 材料入口默认关闭不同。

### 1.8 event 包
- `ClientReviewDecidedPayload`：EVENT_TYPE `ClientReviewDecided`、SCHEMA_VERSION 1、AGGREGATE_TYPE `DD_CASE`（`API/event/ClientReviewDecidedPayload.java:15,18,21`）；字段顺序 caseId、caseVersion、applicationId、submissionId、outcome、returnSection、publicNote、decidedAt、decisionId、decidedBy（`:22-23`，FIELDS 列表）。注释写 outcome 只有 RETURNED/APPROVED/REJECTED。
- `SettlementRestrictionChangedPayload`：SCHEMA_VERSION 1、AGGREGATE_TYPE `SETTLEMENT_CONTROL`、22 字段（`API/event/SettlementRestrictionChangedPayload.java:13-25`）；**无发布调用方**（目录注释 `paytech/foundation/paytech-messaging/catalog/events.yaml:60-69`）。
- 枚举：`ClientReviewErrorCode` 6 个（`API/enums/ClientReviewErrorCode.java:7-17`）；`QualificationErrorCode` 4 个（`API/enums/QualificationErrorCode.java:13-19`）；`MaterialQualificationType` INDIVIDUAL_KYC / ORGANIZATION_KYB / RELATED_PERSON_KYC（`API/enums/MaterialQualificationType.java:5-9`）；`QualificationRoleType` CLIENT / OPERATING_ENTITY / LOCAL_PARTNER（`API/enums/QualificationRoleType.java:12-16`）。

---

## 2. 程序与材料

### 2.1 MaterialProgramResolver 与 `programs-json`
- `@Component`，按资格类型从配置 JSON 解析唯一程序编码；映射缺失 / 重复 / 未知字段 / 未知资格类型一律 503，不回退（`CORE/domainservice/policy/MaterialProgramResolver.java:17-51`）。行字段严格为 `{qualificationType, programCode}`（`:27,85-94`），programCode 正则 `[A-Z][A-Z0-9_]{0,63}`（`:73`），总长 ≤ 65536（`:24,55`），行数 ≤ 枚举数 3（`:59`）。
- 配置类 `MaterialRequirementsProperties`，前缀 `onboarding.materials`，缺省空串（`CORE/core/configuration/MaterialRequirementsProperties.java:11,18`）。
- 生产配置值三行：INDIVIDUAL_KYC→CLIENT_INDIVIDUAL_GENERIC、ORGANIZATION_KYB→CLIENT_ORGANIZATION_GENERIC、RELATED_PERSON_KYC→RELATED_PERSON_GENERIC（`YML:132`）；测试同值（`paytech/compliance/paytech-compliance-test/src/test/resources/application-mock.yml:176`）。
- **两处调用**：`MaterialRequirementsProcessor`（`CORE/service/processor/MaterialRequirementsProcessor.java:44`）和 I01 `RegisterClientReviewProcessor#catalog`（`CORE/service/processor/RegisterClientReviewProcessor.java:159`）。R5 迁入表后两处都要换。
- 资格类型→程序是一对一全局映射，程序表无辖区 / 主体类型 / 角色 / 档次 / Owner 维度（见 §10.2 列清单），这正是 R5 要补的。

### 2.2 MaterialRequirementsMapper.xml
- `programs`：`t_dd_program JOIN t_dd_program_version`，按 BINARY program_code + qualification_type、program/version 都 ACTIVE，`LIMIT 2` 用于检测歧义（`XML/MaterialRequirementsMapper.xml:21-27`）。
- `requirements`：按 program_version_id 取 requirement_code、document_type、required_flag，`LIMIT 1025`（`:29-32`）。
- 有效性另由 `MaterialProgram#isApprovedAuthorityAt` 判定（approved_by / approved_time 非空等），在 `MaterialRequirementsProcessor.java:55` 与 `RegisterClientReviewProcessor.java:160` 调用。读取在只读 REPEATABLE READ 快照内（`MaterialRequirementsProcessor.java:79-84`）。

### 2.3 现有初始化数据
- 唯一种子文件：`requirements/20260914/client-console-注册与客户入驻/db/implementation/risk-generic-materials-init.sql`（22 行，由 `generate-risk-generic-materials.py` 生成，头注释仍写目标库 `mor_risk`，`:1`；YML 注释已改称目标 `mor_compliance`，`YML:125`）。要求执行前 `SET @approved_by`（`:2`）。
  - CLIENT_INDIVIDUAL_GENERIC：program 9100000000000001（`:6`），version 9100000000000101（`:7`），要求 IDENTITY/IDENTITY_DOCUMENT 必需、ADDRESS/PROOF_OF_ADDRESS 可选、OTHER/SUPPORTING_DOCUMENT 可选（`:8-10`）。
  - CLIENT_ORGANIZATION_GENERIC：program …0002（`:11`），version …0102（`:12`），REGISTRATION/REGISTRATION_CERTIFICATE 必需，OWNERSHIP、AUTHORIZATION、ADDRESS、OTHER 可选（`:13-17`）。
  - RELATED_PERSON_GENERIC：program …0003（`:18`），version …0103（`:19`），IDENTITY 必需、OTHER 可选（`:20-21`）。
  - 无 t_dd_program_check / t_dd_program_screening 行。
- 补审批人：`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/sql/41-compliance-material-approver.sql:9-10`（把 3 个版本 approved_by 设为 236033080844881920）。
- V2 迁移：`requirements/20261018/test-environment-V2部署与数据迁移/sql/32-migrate-risk.sql:83-98` 把 mor_risk 的 program / program_check / program_requirement / program_screening 原样搬到 mor_compliance（mor-test 现有程序数据即来源于此）。

---

## 3. 案件

### 3.1 RegisterClientReviewProcessor（I01）
- 继承 `AbstractProcessor`（`CORE/service/processor/RegisterClientReviewProcessor.java:57`），Namespace 常量 `CCT_CLIENT_APPLICATION`（`:58`）。
- 结构校验：clientType 只能 ORGANIZATION / INDIVIDUAL；首个主体必须 PRIMARY 且资格与类型匹配；关联人员只允许机构客户、角色 RELATED_PERSON、资格 RELATED_PERSON_KYC；材料归属必须在受审主体内（`:75-98`）。关系类型映射 UBO/OWNERSHIP/CONTROL→UBO_KYC、DIRECTOR→DIRECTOR_KYC、SIGNATORY→SIGNATORY_KYC（`:62-63`），每名关联人员至少一条关系（`:123-136`）。
- 建案：主案件 role=CLIENT、roleId=clientId；每名关联人员一个子案件 role=RELATED_PERSON、roleId=partyId（`:139-154`）。每案件 1 个 PRIMARY 主体、按程序模板实例化全部要求（初始 PENDING，有材料置 RECEIVED）、证据 RECEIVED、初始状态变更 REGISTER（`:177-282`）。主案件 → 子案件依赖按类型去重（`:285-302`）。
- 新案件 status=SUBMITTED、active_slot=1、case_origin=APPLICATION、version=1（`:196-216`）。
- **不写 snapshot、不调 Vault**：类注释明确"资料快照本期不做（2026-10-13 用户决定），不写 t_dd_party_snapshot，也不调用 Vault"（`:53`）。
- 幂等：先按 namespace+application+submission 查已登记案件，内容（案件集合 + fileId:contentHash 集合）一致返回原主任务，否则 409（`:103-106,310-328`）；并发首登由 `uk_dd_case_source` 回滚后重放，`uk_dd_case_active` 冲突报"受审主体已有进行中的审核任务"（`:331-343`）。
- 冻结主体：案件只冻结 role/qualification/program_version/submission，`t_dd_case_subject` 只写 partyId、profileId、subjectRole=PRIMARY、primary_slot、required_flag（`:180-188`）；source_relation_* / ownership_percentage / source_profile_version_no 列存在但不写。
- 保存：`ClientReviewServiceImpl#register` 一个 READ COMMITTED 5 秒短事务插入全部行（`CORE/domainservice/service/impl/ClientReviewServiceImpl.java:53-69,122-127`）。

### 3.2 ClientReviewFamily / FamilyLoader
- 组合对象：mainCase、childCases、dependencies、subjects、requirements（`ReviewRequirementView`）、evidence、classifications（`CORE/domain/ClientReviewFamily.java:20-34`）；`isActive()` = 主案件 active_slot==1（`:45-47`）。由 `CORE/service/processor/ClientReviewFamilyLoader.java` 加载（52 行）。

### 3.3 ClientReviewAction
- 枚举三值：RETURN→主 RETURNED / 子 RETURNED / 无决定；APPROVE→APPROVED / APPROVED / APPROVED；REJECT→REJECTED / 子 WITHDRAWN / REJECTED（`CORE/domain/ClientReviewAction.java:9-24`）。注释："客户改完重提生成新任务"（`:10`）。

### 3.4 各 Processor 实际情况
- **没有** ClassifyCase / Return / Approve / Reject 独立 Processor。实际为：
  - `ReceiveClientReviewClassificationsProcessor`（I02）：恰一个主分类、行业+MCC 不重复；身份不符按不存在；已接收只接受完全相同重放；保存时 `cases.touch` 递增主案件版本（`CORE/service/processor/ReceiveClientReviewClassificationsProcessor.java:44-112`、`ClientReviewServiceImpl.java:85-91`）。
  - `ReviewClientEvidenceProcessor`（O05）：证据须属主 / 子案件；同结果直接返回、已核验为他结果 409；要求状态由全部证据推导（任一 ACCEPTED→ACCEPTED，否则有 RECEIVED→RECEIVED，否则 REJECTED）；**不保存核验人**（`CORE/service/processor/ReviewClientEvidenceProcessor.java:22-69`）。
  - `DecideClientReviewProcessor`（O07/O08/O09 共用），见 3.6。
  - 只读：`GetClientReviewCaseProcessor`、`ListClientReviewCasesProcessor`、`GetClientReviewResultProcessor`。

### 3.5 ClientReviewApprovalPolicy 门禁
- 纯函数，5 项：CASE_NOT_ACTIVE、CHILD_CASE_NOT_ACTIVE、EVIDENCE_PENDING（仍有 RECEIVED 证据）、REQUIRED_MATERIAL_NOT_ACCEPTED（required_flag=1 且要求非 ACCEPTED）、CLASSIFICATION_NOT_RECEIVED（`CORE/domainservice/policy/ClientReviewApprovalPolicy.java:14-57`）。
- 无检查项 / 筛查 / CRR / 四眼 / 豁免 门禁，R6 全部需新增。

### 3.6 DecideClientReviewProcessor
- 校验：RETURN 必须有分区与客户可见说明；APPROVE/REJECT 必须有理由且不能带分区（`CORE/service/processor/DecideClientReviewProcessor.java:57-71`）。
- 已结束：同结论直接返回，异结论 409 CLIENT_REVIEW_CLOSED；APPROVE 前跑门禁，不满足 CLIENT_REVIEW_NOT_READY（`:76-89`）。
- 构造：主案件 + 仍进行的子案件各一条状态变更；REJECT 时子案件 action=WITHDRAW_WITH_PARENT；**决定行**：APPROVE 为主 + 每个子案件各一条，REJECT 只主案件一条，RETURN 无（`:97-119`）。决定 decision_source=REVIEWER、valid_from=now、无 valid_to、decided_by=操作人（`:140-154`）。
- **资格投影**只对主案件决定（`decisions.getFirst()`，`:116`）：APPROVE 新建或切换为 APPROVED；REJECT 只在已有 APPROVED 时切换为 REJECTED（`:129-137`），经 `QualificationChangeBuilder`（`CORE/service/processor/QualificationChangeBuilder.java:29-64`，新建版本 1、review_status 由状态推导、不排复审）。子案件不投影。
- 保存：`ClientReviewServiceImpl#decide` 同一短事务内先关子案件、再按版本条件关主案件、插决定、插状态变更、写资格投影、登记补偿事件 `CLIENT_REVIEW_RESULT`（`CORE/domainservice/service/impl/ClientReviewServiceImpl.java:94-119`）。关闭 SQL：`status=?, active_slot=NULL, …, version=version+1 WHERE case_id=? AND version=? AND active_slot=1`（`XML/DueDiligenceCaseMapper.xml:36-38`）。
- 观察：响应返回的是**决定前**读到的 `main.getStatus()`（`DecideClientReviewProcessor.java:93`），不是新状态。
- 决定不冻结 tier / CRR / 辖区 / 责任方 / 推荐人（表也无这些列，见 §10.2）。

### 3.7 状态值实际使用
- 案件：写入 SUBMITTED（`RegisterClientReviewProcessor.java:61,209`）、RETURNED/APPROVED/REJECTED/WITHDRAWN（`ClientReviewAction.java:11-17`）。Schema 允许 DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED（`SCHEMA:84`）——IN_REVIEW / WAITING_EVIDENCE / EXPIRED / DRAFT 从未写入；PENDING_DEPENDENCY 不在注释中。
- `t_dd_case_state_change.from/to_status` 注释缺 RETURNED（`SCHEMA:258` 起），但代码写 RETURNED——注释漂移。
- 要求：写 PENDING、RECEIVED、ACCEPTED、REJECTED；Schema 另有 WAIVED、EXPIRED 未用（`SCHEMA:165`）。
- 证据：写 RECEIVED、ACCEPTED、REJECTED；SUPERSEDED 未用（`SCHEMA:181`）。
- 资格：APPROVED、REJECTED、REVOKED（`CORE/core/enums/QualificationStatus.java:7-13`，另有 EXPIRED 枚举但只读判定）；review_status NOT_REQUIRED / COMPLETED 被写，ACTIVE / PAUSED 未用（`SCHEMA:306`）。

### 3.8 锁机制与"禁 Redis"出处
- 全部写入靠唯一键 + 版本 CAS，无资源锁、无 `AbstractLockProcessor`、无 `FOR UPDATE`（grep 无匹配）。
- 出处：`RegisterClientReviewProcessor.java:52`（"唯一键 uk_dd_case_source 是最终保护，不加 Redis 锁"）；`RecordManualQualificationProcessor.java:35-36`、`RevokeQualificationProcessor.java:33`（"不使用资源锁"）；`paytech/compliance/README.md:17`（"不使用 Redis"）；`TEST/test/infrastructure/ComplianceDatabasePrecondition.java:17`；架构测试断言 classpath 无 `org/redisson/Redisson.class`（`TEST/auto/architecture/ComplianceArchitectureTest.java:105,118-121`）。
- 注意 AGENTS.md 要求新单资源写 Processor 用 Foundation `AbstractLockProcessor`；compliance 若引入需要锁的新命令（如分派、四眼），要么继续用版本 CAS 并在文档说明，要么引入 Redis 并改架构测试。

---

## 4. 资格（2026-10-24 `4b661830` 新增）

- `RoleQualificationMapper.xml`：insert、findById、findByRole（role_type+role_id+qualification_type）、findBySubjects（OR 拼接）、page/count、applyDecision（`SET current_decision_id, status, valid_from, valid_to … WHERE qualification_id=? AND version=?`）（`XML/RoleQualificationMapper.xml:8-43`）。
- `QualificationDecisionMapper.xml`：insert、findByCase（`decision_version IS NULL`）、findByIds（`XML/QualificationDecisionMapper.xml:7-18`）。
- `EvaluateQualificationsProcessor`：CLIENT 要 INDIVIDUAL_KYC 或 ORGANIZATION_KYB、OE 要 ENTITY_UNDERWRITING、LOCAL_PARTNER 要 PARTNER_DUE_DILIGENCE；有效期实时判定；合格 Client 再查 SETTLEMENT_BLOCK；无显式事务（`CORE/service/processor/EvaluateQualificationsProcessor.java:30-88`）。
- `RecordManualQualificationProcessor`：结论固定 APPROVED、NOT_REQUIRED；组合与 roleId 格式校验 422；首登不得带 expectedVersion，已有必须一致否则 409；MANUAL 决定不关联案件；触发类型 OPERATIONS_MANUAL（`CORE/service/processor/RecordManualQualificationProcessor.java:30-116`）。
- `RevokeQualificationProcessor`：只允许当前 APPROVED → REVOKED，valid_to=撤销时间，review_status COMPLETED（`CORE/service/processor/RevokeQualificationProcessor.java:29-87`）。
- 保存：`QualificationServiceImpl#saveChange` 一个短事务插决定 + 新建或 CAS 投影 + 状态变更，**不登记补偿事件**（`CORE/domainservice/service/impl/QualificationServiceImpl.java:28-53`）。
- roleId 格式：OE `[A-Z]{2}[0-9]{3}`、LOCAL_PARTNER `[1-9][0-9]{0,18}`（`CORE/core/enums/QualificationRoleType.java:16-18,45-46`）。decision_source 枚举 AUTOMATED/REVIEWER/OVERRIDE/MANUAL（`CORE/core/enums/QualificationDecisionSource.java:8-17`）。
- changes SQL：`paytech/compliance/docs/database/sql/changes/2026-10-24-qualification-manual-and-role-id.sql`——decision.case_id 改可空、两表 role_id 改 `VARCHAR(64) ascii_bin`、加 basis_note、decision_source 注释加 MANUAL（`:23-30`）；回退步骤在 `:10-20`。回填脚本 `…/2026-10-24-qualification-backfill.sql`（trigger_type BACKFILL）。
- **role_id 字符型范围**：只改了 `t_qualification_decision`、`t_role_qualification`（`SCHEMA:277,298`）；`t_due_diligence_case.role_id` 与 `t_settlement_control.role_id` 仍 BIGINT（`SCHEMA:71,338`），DO 同样 `DueDiligenceCase.roleId` 为 `long`（`CORE/core/dal/dataobject/DueDiligenceCase.java:20`），而 `RoleQualification.roleId`、`QualificationDecision.roleId` 为 String（`RoleQualification.java:26`、`QualificationDecision.java:30`）。物理决策写明"以后做 OE 尽调时再统一"（`paytech/compliance/docs/database/COMPLIANCE_PHYSICAL_DECISIONS.md:68`）。
- **QualificationChanged 不存在**（见 §0.1）。资格表也没有 tier、risk_rating、jurisdiction_code、responsible_partner_id、review_interval_months、last_review_time（`SCHEMA:295-313`），只有 review_frequency_code、next_review_time。

---

## 5. 事件发布

- 补偿 Handler `ClientReviewResultHandler`：EVENT_TYPE `CLIENT_REVIEW_RESULT`，eventKey=主案件 ID、payload 为空，从表重建结果（`CORE/service/handler/ClientReviewResultHandler.java:25-47`）；策略 20 秒 / 12 次 / 5 秒起指数退避至 30 分钟（`:57-59`）；只认 CCT_CLIENT_APPLICATION + CLIENT + 终态 RETURNED/APPROVED/REJECTED，通过 / 拒绝缺决定为终态失败（`:35,67-82`）。事务内调用直接终态失败（`:68-70`）。**EXPIRED / WITHDRAWN 不在发布集合**，R7 需扩。
- 补偿目录仅一项 `CLIENT_REVIEW_RESULT`（`paytech/compliance/compensation-event-catalog.yaml:3-4`），R12 规则要求新 Handler 必须在此登记。
- 出站端口 `ClientReviewResultPublisher`（`CORE/integration/ClientReviewResultPublisher.java:6-13`），实现 `ClientReviewDecidedEventPublisher`（`APP/ext/mq/ClientReviewDecidedEventPublisher.java:25`）：`DomainEvent.builder().eventType().schemaVersion(1).aggregate("DD_CASE", caseId).aggregateVersion(caseVersion).occurredAt(decidedAt).sourceService().instanceId().payload()`（`:53-58`）；payload 字段顺序 `:62-74`；eventId 默认取 AggregateKey 四段式键，重试不变（`:52`）。
- 信封类 `paytech/foundation/paytech-messaging-api/src/main/java/com/paytech/foundation/messaging/api/DomainEvent.java:20-58`（eventId、schemaVersion、aggregateVersion、occurredAt、sourceService、instanceId；eventId 缺省见 `:54-55`）。
- 目录：`paytech/foundation/paytech-messaging/catalog/events.yaml:23-30`（ClientReviewDecided v1，consumers [client-master]，strictOrder false，aggregateVersionField caseVersion，注释说明版本会跳号 `:18-22`）；SettlementRestrictionChanged v1 已登记但无发布方（`:60-69`）。新增 QualificationChanged 需在此登记，并更新 `ComplianceArchitectureTest#test_domainEventPublicationIsRegistered`（`TEST/auto/architecture/ComplianceArchitectureTest.java:182-196`）。
- 发布配置：`messaging.producer.service=compliance`、sequencer 开、`unroutable-policy: FAIL`（`YML:106-114`）；RabbitMQ publisher confirm + returns + mandatory（`YML:35-39`）。
- 消费者去重：client-master 用 paytech-messaging 的 `t_consumed_event`（eventId 去重）+ `t_consumed_aggregate_version`（`paytech/client-master/docs/database/sql/client-master-schema.sql:513,524`）；Handler 声明 `HANDLER_MANAGED`、strictOrder=false，业务幂等按提交编号与主任务 ID（`paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/ext/mq/ClientReviewResultListener.java:18-24`）。转换器硬校验 sourceService=compliance、aggregateType=DD_CASE、**schemaVersion==1**、aggregateId==payload.caseId（`…/ClientReviewResultMessageConverter.java:24-27`）。
- compliance 自身不消费事件，但测试 DDL 仍带一份 `t_consumed_event`（`paytech/compliance/paytech-compliance-test/src/test/resources/init/ddl.sql:2-3` 注释说明来源）。

---

## 6. 只有表、无代码的部分

按 Mapper XML 引用统计（DO 目录 `CORE/core/dal/dataobject/` 共 12 个类）：

| 表 | DO / Mapper | 测试引用 |
|---|---|---|
| t_dd_check | 无 | 仅清理器 |
| t_screening_run | 无 | 仅清理器 |
| t_screening_hit | 无 | 仅清理器 |
| t_screening_resolution | 无 | 仅清理器 |
| t_dd_party_snapshot | 无 | 仅清理器 |
| t_dd_program_check | 无 | 仅清理器 |
| t_dd_program_screening | 无 | 仅清理器 |
| t_settlement_control_scope、t_settle_control_state_change | 无 | 清理器 + QualificationTestMapper |
| t_dd_case_dependency | **有**（`CaseDependency` DO、`CaseDependencyMapper.java/.xml`、Repository） | 有 |

- 清理器表清单：`TEST/test/clear/ComplianceDataCleaner.java:24-29`（25 张，含 t_consumed_event）。
- 这些表的现有列与需求差距：
  - `t_dd_check` 挂在 `case_subject_id`，无 case_id / check_mode / status / outcome / reviewer（`SCHEMA:190-205`）；`result_vault_token` 注释仍是 `RISK_DD_CHECK_RESULT_V1`（`SCHEMA:197`）。
  - `t_screening_run` 无 case_id、search_term_vault_token、trigger_source（`SCHEMA:207-223`）。
  - `t_screening_hit` 有 `name_vault_token`、status 仅 OPEN/RESOLVED（`SCHEMA:225-238`，`:231`）。
  - `t_screening_resolution.resolution` 允许 CONFIRMED_MATCH / FALSE_POSITIVE / INSUFFICIENT_EVIDENCE（`SCHEMA:244`）。
  - Vault Token 注释沿用 `RISK_*` 数据类型名（`SCHEMA:154,197`）。

---

## 7. 出站

- compliance 四个模块里没有 `HttpServiceClientFactory`、`RestClient`、`@HttpExchange` 客户端代理、`VaultClient` 的任何引用（grep 无结果）。基类注释亦写"本服务没有出站 HTTP，无外部 DTO Mock 需要做意外调用检查"（`TEST/test/base/ComplianceMockTestBase.java:40-41`）。
- app 依赖（`paytech/compliance/paytech-compliance-app/pom.xml`）：api、core、mvc-web-starter、internal-auth、mybatis-support、snowflake-id、compensation-event、messaging、amqp、webmvc、validation、actuator、jdbc、mybatis、mysql、prometheus、nacos-discovery。**无** `paytech-http-client-support`、`spring-cloud-starter-loadbalancer`、`paytech-vault-client`。
- channel-connector 的 `ServiceChannelExecutionApi`（`POST /service-executions`，`paytech/channel-connector/paytech-channel-connector-api/src/main/java/com/paytech/channel/connector/api/serviceexecution/ServiceChannelExecutionApi.java:33`）目前**没有任何生产调用方**，只有 connector 自身 Controller 与测试。
- 参照 client-master 的装配，compliance 要加的东西：
  1. app pom 加 `paytech-http-client-support`、`spring-cloud-starter-loadbalancer`、`paytech-channel-connector-api`；core 加 `paytech-vault-client`（client-master 放在 core：`paytech/client-master/paytech-client-master-core/pom.xml:42`）。
  2. 传输：仿 `ClientDocumentConfiguration#riskTransport`——生产经 `LoadBalancerClient` 按服务名发现，auto 测试只允许 loopback 根 URL 覆盖；JDK HttpClient，连接 2 秒、读 3 秒、禁重定向（`paytech/client-master/paytech-client-master-app/src/main/java/com/paytech/client/master/configuration/ClientDocumentConfiguration.java:51-66`）。
  3. 客户端：`HttpServiceClientFactory.create(transport, InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(N), tokens, "<目标服务名>"), <错误码枚举>).createClient(XxxApi.class)`（`…/ClientSubmissionConfiguration.java:131-140`）；`InternalCallTokenOptions` 在 internal-auth OFF 时原样返回（`…/InternalCallTokenOptions.java:26-33`）。
  4. Vault：client-master 在 `spring.autoconfigure.exclude` 排除 `VaultClientAutoConfiguration` 后在业务配置类 `@Import` 显式开启（`paytech/client-master/paytech-client-master-app/src/main/resources/application.yml:20-24`、`…/ClientDraftConfiguration.java:42`），配置 `vault.client.connect-timeout 2s / read-timeout 3s`（application.yml `:145-148`）。`VaultClient` 提供 `protect` / `reveal`（`paytech/vault/paytech-vault-client/src/main/java/com/paytech/vault/client/VaultClient.java:16,29`）。
  5. 规则约束：R6 他服务 api 接口实现只能来自 `HttpServiceClientFactory`（`paytech/tools/arch-rules/src/main/java/com/paytech/arch/rule/RemoteApiBindingRule.java:21`）；R7 用 TransactionTemplate 的类不得直接注入 `-api` 接口或 `VaultClient`（`…/TransactionExternalCallRule.java:23-24`）——现有 `ClientReviewServiceImpl`/`QualificationServiceImpl` 都用 TransactionTemplate，出站必须放 Processor 事务外；R15 外部 SDK 白名单（`…/ExternalEgressRule.java:22-29`）。
  6. 同步改 `ComplianceArchitectureTest` 的 core/app 依赖精确集合（`TEST/auto/architecture/ComplianceArchitectureTest.java:58-65`）和 README "没有出站"表述（`paytech/compliance/README.md:17`）。
  7. 测试：按 UNIT_TEST_GUIDE 需为 connector / Vault 建测试 HTTP 端点 DTO Mock + 意外调用检查，基类当前没有这类设施（`TEST/test/base/ComplianceMockTestBase.java:40-41`）。

---

## 8. 测试设施（paytech-compliance-test）

- 基类 `ComplianceMockTestBase extends TestBase`：`@ActiveProfiles("mock")`、`@SpringBootTest(classes = ComplianceApplication.class, RANDOM_PORT)`、`ComplianceDatabasePrecondition` 初始化器（`TEST/test/base/ComplianceMockTestBase.java:43-50`）；全局 BeforeEach 断言前例异步已停且空基线（`:76-80`），AfterEach 等补偿 / sequencer / handler 池空闲最多 10 秒（`:83-112`）。
- 导入的组件：ComplianceDataCleaner、MaterialProgramTestMapper/Mocker、ClientReviewTestMapper/Mocker、ClientReviewStoreChecker、ClientReviewResultProbe、QualificationTestMapper/Mocker、QualificationStoreChecker（`:47-49`）。
- Mocker：`ClientReviewMocker#mock(ClientReviewCaseBuilder)`、`track`、`clear`（`TEST/test/mocker/ClientReviewMocker.java:142-243`）；`MaterialProgramMocker#mock/mockExtraVersion/mapPrograms/mapRaw/clear`（`TEST/test/mocker/MaterialProgramMocker.java:55-100`，通过改 `programs-json` 映射）；`QualificationMocker#mock/mockSettlementControl`（`TEST/test/mocker/QualificationMocker.java:49,71`）。
- HTTP 入口测试（`TEST/auto/ext/http/`，@Test 数 / 方法摘要）：
  - ClientReviewSubmissionApiAutoTest 9（组织 / 个人 / 重放 / 依赖去重 / 关系映射 / 多材料 / 无材料主体 / 参数 / 材料不在程序 / 程序不可用 / 映射缺失或空 / 同提交异内容 / 主体已有进行中）。
  - ClientReviewClassificationApiAutoTest 8、ClientReviewCaseListApiAutoTest 8、ClientReviewCaseDetailApiAutoTest 9、ClientReviewEvidenceReviewApiAutoTest 8、ClientReviewDecisionApiAutoTest 7、ClientReviewQualificationProjectionApiAutoTest 5、ClientReviewResultApiAutoTest 3、MaterialRequirementsApiAutoTest 5、QualificationAdminApiAutoTest 9、QualificationEvaluationApiAutoTest 6、logging/WebLoggingBoundaryAutoTest 3。
  - 注：@Test 计数低于方法数，多数类使用参数化或辅助方法；CURRENT_IMPLEMENTATION 记录 relsnap 时 compliance 共 437 项通过（`agent-docs/CURRENT_IMPLEMENTATION.md:18`）。
- 架构测试：
  - `ArchitectureAutoTest#test_allRulesPass` 对四模块跑共享规则（`TEST/auto/architecture/ArchitectureAutoTest.java:24-49`）。规则集实际是 R1–R12 + R14 + R15（`paytech/tools/arch-rules/src/main/java/com/paytech/arch/ArchitectureRules.java:31,50-54`）：R1 core 不依赖他服务 core/app；R2 api 只依赖 common-core/spring-web/validation；R3 领域层不依赖流程层 api；R4 Mapper XML 只引用本库表（白名单 `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml:154-178`，新表 `t_dd_risk_factor`、`t_dd_program_requirement_doc_type`、`t_screening_hit_list` 必须登记）；R5 core 无启动类 / Controller；R6 他服务 api 实现只能来自 HttpServiceClientFactory；R7 事务内不注入外部客户端；R8 只有 app 依赖 messaging 发布端；R9 DataObject 不进 api；R10 枚举常量空行；R11 卡域隔离；R12 补偿 Handler 只在 core 且 eventType 在目录登记；R14 Processor 继承模板；R15 唯一对外出口。`known-deviations.yaml` 无 compliance 条目。
  - `ComplianceArchitectureTest` 12 项（`TEST/auto/architecture/ComplianceArchitectureTest.java:47-234`）：模块集合、依赖精确集合、持久化依赖、enforcer 禁令、仅 app 可执行、运行时基础设施（禁 Mockito/Testcontainers/Redisson/决策引擎）、MapperScan 边界、Snowflake 命名空间、YML 严格配置与 metadata-only 前缀（**疑似已失败，见 §0.5**）、事件目录登记、mock 库目标固定、清理器 = DDL 表集合且表数 = `EXPECTED_TABLE_COUNT`。
- 测试 DDL：`paytech/compliance/paytech-compliance-test/src/test/resources/init/ddl.sql`，逐字摘自 SCHEMA 加 `t_consumed_event`，共 25 表（diff 只差建库 / 账号头与 t_consumed_event）；`EXPECTED_TABLE_COUNT = 25`（`TEST/test/infrastructure/ComplianceDatabasePrecondition.java:22`）。加表需同时改 DDL、清理器、计数。
- mock 配置：库 `127.0.0.1:13306/mor_compliance`、账号 `compliance_app`、RabbitMQ `/mor-local`、Snowflake 0/1、review / materials 都开（`paytech/compliance/paytech-compliance-test/src/test/resources/application-mock.yml:36-38,49-54,161-176`）。

---

## 9. 配置（application.yml）

- `onboarding.review.enabled: false`、`onboarding.materials.enabled: false`、`programs-json`（`YML:126-132`）；审核三个 Controller 与请求拦截器受 review 开关门控（`APP/ext/rest/ClientReviewRegistrationController.java:35`、`ClientReviewAdminController.java:33`、`ClientReviewResultController.java:20`、`APP/configuration/ClientReviewRequestConfiguration.java:19`）。资格 Controller 无开关。
- Snowflake：`compliance.identifier.region-id/worker-id` 必须由环境注入（`YML:53-57`），装配在 `APP/configuration/ComplianceRuntimeConfiguration.java:36-53`；架构测试校验命名空间（`ComplianceArchitectureTest.java:140-159`）。
- messaging：`YML:106-114`；补偿 `compensation.enabled: true`（`YML:102-103`）；`paytech.instance-id`（`YML:117-118`）。
- internal-auth：`mode: "${COMPLIANCE_INTERNAL_AUTH_MODE:OFF}"`（`YML:121-122`），仅入站校验；无出站 Token 装配。
- 日志 metadata-only 前缀 4 个（`YML:59-68`）。
- 端口 18112 / 管理 19112、仅 127.0.0.1 绑定（`YML:1-3,70-73`）；无 `spring.cloud.loadbalancer`、无 `vault.*` 配置。

---

## 10. 模型文件

### 10.1 文件与一致性
- PDManer：`paytech/compliance/docs/database/compliance.pdma.json`，24 个实体，与 SCHEMA 的 24 张 CREATE TABLE 一一对应；两处 role_id 已是 VARCHAR 64、`t_due_diligence_case.role_id` 仍 BIGINT（脚本读取确认）。
- Schema：`SCHEMA` 24 表（`:5-389`），无任何物理 CHECK / FOREIGN KEY（唯一 "CHECK" 字样是 `SCHEMA:197` 注释里的 Vault 类型名）。
- changes：仅 2026-10-24 两个文件（role_id/manual DDL、backfill）。
- 表数口径：Schema/PDManer 24；测试 DDL 与 mor-test 25（多 t_consumed_event），物理决策 "表数不变（25）"（`paytech/compliance/docs/database/COMPLIANCE_PHYSICAL_DECISIONS.md:74`），CURRENT_IMPLEMENTATION 表 `mor_compliance | 25`（`agent-docs/CURRENT_IMPLEMENTATION.md:304`）。

### 10.2 需求 §7.2 改表的现有列（缺什么一目了然）
- `t_dd_program`：program_id、program_code、qualification_type、status（DRAFT/ACTIVE/RETIRED）、时间（`SCHEMA:5-14`）；`uk_dd_program_code`（`:13`）。
- `t_dd_program_version`：version_no、review_frequency_code、status、active_slot、content_hash、approved_by、approved_time（`SCHEMA:16-31`）。
- `t_dd_program_requirement`：requirement_code、document_type、required_flag（`SCHEMA:33-42`）。
- `t_dd_program_check`：check_code、check_type（受控目录、非固定枚举）、required_flag、validity_days（`SCHEMA:44-54`）。
- `t_dd_program_screening`：subject_role（PRIMARY/UBO/DIRECTOR/SIGNATORY）、screening_type（SANCTIONS/PEP/ADVERSE_MEDIA）、required_flag（`SCHEMA:56-65`）。
- `t_due_diligence_case`：见 §3；有 public_note、resolution_note、return_section（BASIC/BUSINESS/PEOPLE/MATERIALS）、scheduled_review_time、source_submission_version/hash（`SCHEMA:67-97`）。
- `t_dd_case_subject`：party_id、subject_role、primary_slot、source_profile_id、source_relation_id、source_profile_version_no、source_relation_version_id/no、ownership_percentage、required_flag（`SCHEMA:99-117`）。
- `t_dd_requirement`：program_requirement_id、status、**due_time**（已有，可承载期限）、version（`SCHEMA:161-173`）。
- `t_dd_evidence_ref`：requirement_id、case_subject_id、**file_id BIGINT**、content_hash、status、version（`SCHEMA:175-188`）；`uk_dd_evidence_req_file(requirement_id, file_id)`（`:186`）。改为 file_address 需同时处理该唯一键与 I01 `fileId`。
- `t_qualification_decision`：含 recommended_decision（APPROVE/REVIEW/REJECT）、decision_evaluation_id、decision_version、override_reason_code、basis_note（`SCHEMA:272-293`）。
- `t_role_qualification`：见 §4。
- 依赖：`t_dd_case_dependency` 有 accepted_decision_id、required_flag、version（`SCHEMA:133-147`）。

### 10.3 COMPLIANCE_PHYSICAL_DECISIONS.md 要点
- 2026-10-18 从 Risk 物理决策拆出，"通用物理规则、CHECK Sidecar…仍见原文"（`:3`）。
- 归属：尽调 / 筛查 / 资格 20 表 + 结算控制 3 表（代码未实现）+ t_compensation_event（`:7-9`）；跨库只存 ID：decision_evaluation_id、source_action_effect_id（`:10`）。
- §10.1 仍以 `ck_dd_case_origin`、`ck_dd_case_tuple`、"同一行 CHECK 固定 Namespace/Role 矩阵"等逻辑约束描述（`:28-36`），但按 2026-09-04 约定这些只由应用保证、不建物理 CHECK——10.3 节对 `ck_qual_decision_source` 明确写了"由应用保证（不建物理 CHECK）"（`:69`）。Schema 确无 CHECK。
- §10.1 设计意图包含 Snapshot 走 Vault、Hit 写入事务、Continuous Review 由 Qualification CAS 建 CONTINUOUS_REVIEW 案件（`:44-52`），均未实现。
- §10.3 role_id 决定：两张资格表字符型，案件与结算控制保留 BIGINT，"以后做 OE 尽调时再统一"（`:68`）；MANUAL 决定规则（`:69`）；判定实时计算 EXPIRED / NOT_YET_VALID（`:73`）。

---

## 11. CURRENT_IMPLEMENTATION 与 mor-test

- 最新 compliance 条目：商业关系快照（`agent-docs/CURRENT_IMPLEMENTATION.md:14-20`）。Implemented：资格 DO/Mapper、评估接口、4 个运营接口、入驻审核投影、role_id 字符型（`:17`）。Verified：compliance 437 通过（基于 `8a86a31c`，`:18`）。
- Deployed：mor-test 2026-10-25，tag `relsnap-20261025a`，compliance 镜像 `sha256:ca1585452f9c…`（`:19`；完整 ID 与 JAR `5bd849ff153c1cf3` 见 `requirements/20261023/commercial-商业关系快照与真实解析接入/release/compose-candidate.yaml:16`；回滚镜像 `2f62a26ad46c…` 见同目录 `release.md:33`）。cmp-ddl、cmp-backfill（37 个 Client 资格）已执行（`release.md:59-60,272`）。Platform 注册块 **137**（含 `/compliance-admin` 资格页路由）。
- 遗留：主 compose 的 compliance 仍指 `:development` 旧镜像，重建必须带 compose-candidate（`agent-docs/CURRENT_IMPLEMENTATION.md:20`、`release.md:289`）；GAP-PROFILE 属 client-master。
- 更早：V2 拆分 `mor_compliance` 25 表（`agent-docs/CURRENT_IMPLEMENTATION.md:304`）；补偿执行器 `compensationEventScan` 已在 XXL 登记，`CLIENT_REVIEW_RESULT` 重试成功记录（`:339`）。
- 本需求候选注册块 138 与 live mor_platform 的核对不在本笔记范围（需查库）。

---

## 12. 对 §7.2 的修订建议（供主会话参考）

- 删除 I01 原值 PII 字段只影响 api 合同与 client-master 发送端，compliance 存储无需迁移。
- R6 "同案件多轮"需先定：改 `uk_dd_case_source` 去掉 `source_submission_id` 还是把多轮记在新表 / 状态变更上；否则沿用"退回关案、重提新案、`previous_case_id` 串联"成本更低。
- R7 两个事件都要"消费方先上线、发布方后上线"：v2 需 client-master 放宽 `schemaVersion != 1`；QualificationChanged 需新补偿事件类型（目录 + Handler + 资格保存事务内登记）。
- 出站引入后 `ClientReviewServiceImpl` 与 `QualificationServiceImpl` 维持纯本地事务，Vault PROTECT / 筛查调用放 Processor 事务外，结果另开短事务落库（R7 规则 + 事务公理）。
- 新表 `t_dd_risk_factor`、`t_dd_program_requirement_doc_type`、`t_screening_hit_list` 要同步：SCHEMA、PDManer、测试 DDL、清理器、`EXPECTED_TABLE_COUNT`、`table-ownership.yaml`。
- `t_dd_case_state_change` 状态注释补 RETURNED / PENDING_DEPENDENCY；`t_due_diligence_case.status` 注释补 PENDING_DEPENDENCY。
- 先跑一次 `ComplianceArchitectureTest` 确认 §0.5 是否为既有失败，归入"既有问题"单独报告。
