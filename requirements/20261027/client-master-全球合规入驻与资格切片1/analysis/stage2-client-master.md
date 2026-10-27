# 阶段 2 现状核对：client-master（只读，2026-10-27）

对象：`requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md` 第 4 节 R1–R4、R7、R11 与 7.1 节。
路径缩写（均相对 `paytech/client-master/`）：
- `API` = `paytech-client-master-api/src/main/java/com/paytech/client/contract/api/`（旧包）
- `API2` = `paytech-client-master-api/src/main/java/com/paytech/client/master/api/`（新包）
- `CORE` = `paytech-client-master-core/src/main/java/com/paytech/client/master/`
- `MAP` = `paytech-client-master-core/src/main/resources/mapper/`
- `APP` = `paytech-client-master-app/src/main/java/com/paytech/client/master/`
- `TEST` = `paytech-client-master-test/src/test/java/com/paytech/client/master/`
- `SQL` = `docs/database/sql/client-master-schema.sql`

模块规模：api 48、core 241、app 27 个 main Java 文件。Processor 不是 `@Component`，全部在 `APP/configuration/*Configuration.java` 中以 `@Bean` 显式装配（例 `APP/configuration/ClientDraftConfiguration.java:72-164`）。

---

## 0. 关键发现（先看）

1. **GAP-PROFILE 已在代码层面确认。** 审核通过只写 `approved_time`（`MAP/PartyProfileMapper.xml:28-32`），全库没有任何语句清空 `t_party_profile.draft_application_id` 或写 `content_hash`。Checkout 要求 `approvedTime!=null && draftApplicationId==null && validHash(contentHash)`（`CORE/domain/checkout/CheckoutEligibilityContext.java:209-213`），所以入驻通过的 Client 在 REAL 模式全部不合格。`agent-docs/CURRENT_IMPLEMENTATION.md:20` 已登记“须另立 client-master 需求”。本切片引入 `current_slot` 时顺带修复最省事，否则 7.1 需显式列为不做项。
2. **`t_party_profile` 每 Party 一行的假设遍布 9 处 SQL**（见第 6 节表格）；`current_slot` 改 uk 后都要加 `current_slot=1` 条件，Checkout 的 `unique()` 遇到 2 行直接抛主档冲突（`CORE/domain/checkout/CheckoutEligibilityContext.java:364-367`）。
3. **`ClientFormCatalogProperties` 不只服务入驻表单。** 运营分类 O03 校验行业（`CORE/service/processor/ReplaceClientClassificationProcessor.java:40,65`）和行业目录接口（`APP/ext/rest/ClientClassificationAdminController.java:33,51`）也读 `industries`。7.1 “删除 form-catalog”必须同时给 industries 找新家（目录表或保留独立配置）。
4. **Vault STABLE Token 按“精确原值”判同。** `ProtectCommand` 明确不做业务规范化（`paytech/vault/paytech-vault-client/src/main/java/com/paytech/vault/client/ProtectCommand.java:5-18`）。证件号要先规范化再 Protect，否则 Token 不能兼作唯一键。
5. **client-master 当前大量输出中文用户文案**（目录 label、提交问题 message、草稿字段错误、错误码 message），Client Web 直接展示 `issue.message`（`client-console-web/src/submission.ts:38-64`）。与 R2 的需求级 `code/reason/remediation` 和 Platform 多语言目标冲突，见第 11 节。
6. **测试无 Vault Mock 端点。** 测试配置注释写明“C14 不调用 Vault，任何调用记录为意外调用”（`paytech-client-master-test/src/test/resources/application-mock.yml:108-113`），`AssociateClientDocumentTest` 注明“本模块无 Vault 测试端点”（`TEST/auto/ext/http/onboarding/AssociateClientDocumentTest.java:27`）。R3 需新增，可参考 card / channel-connector / reconciliation 的 `MockVaultController`。
7. **撤回不存在。** `WITHDRAWN` 只在枚举与 Schema 注释中出现（`CORE/domain/enums/ClientApplicationStatus.java`，`SQL:239-240`），无任何写路径；重新申请只认 `REJECTED`（`MAP/ClientDraftMapper.xml:30-35`）。
8. **结果事件 v2 / EXPIRED 目前会进死信。** 转换器硬校验 `schemaVersion==1`（`CORE/ext/mq/ClientReviewResultMessageConverter.java:24-27`），请求注解只允许三种 outcome（`CORE/service/processor/request/ApplyClientReviewResultRequest.java:14-17`）。compliance 先发 v2 会让 client-master 全部拒收，发布顺序必须“client-master 先兼容 v1+v2，再升 compliance”。

---

## 1. 入驻链路现状

### 1.1 Processor 清单（`CORE/service/processor/`）

| Processor | 基类 | 锁键 / 证据 |
|---|---|---|
| CreateClientApplicationProcessor（C02） | AbstractLockProcessor | `client-onboarding:account-slot:{accountId}`（`ClientOnboardingLocks.java:21-23`，用于 `CreateClientApplicationProcessor.java:47-48`） |
| SaveClientDraftProcessor（C04） | AbstractLockProcessor | `client-onboarding:application:{id}`（`ClientOnboardingLocks.java:16-18`；`SaveClientDraftProcessor.java:51-52`） |
| CreateClientPersonProcessor（C07） | AbstractLockProcessor | application（`CreateClientPersonProcessor.java:49-50`） |
| SaveClientPersonProcessor（C08） | AbstractLockProcessor | application + `cct:party:{partyId}`（`ClientOnboardingLocks.java:26-33`；`SaveClientPersonProcessor.java:58-59`） |
| CreateClientRelationProcessor（C09） | AbstractLockProcessor | application + party（`CreateClientRelationProcessor.java:41-42`） |
| SaveClientRelationProcessor（C10） | AbstractLockProcessor | application（`SaveClientRelationProcessor.java:41-42`） |
| RemoveClientRelationProcessor（C11） | AbstractLockProcessor | application（`RemoveClientRelationProcessor.java:38-39`） |
| AssociateClientDocumentProcessor（C12） | AbstractLockProcessor | application（`AssociateClientDocumentProcessor.java:51-52`） |
| RemoveClientDocumentProcessor（C13） | AbstractLockProcessor | application（`RemoveClientDocumentProcessor.java:37-38`） |
| SubmitClientApplicationProcessor（C14） | AbstractLockProcessor | application（`SubmitClientApplicationProcessor.java:61-62`） |
| CheckClientSubmissionProcessor（提交核对） | AbstractProcessor，无锁 | `CheckClientSubmissionProcessor.java:18` |
| GetClientFormOptionsProcessor（C03） | AbstractProcessor | `GetClientFormOptionsProcessor.java:18` |
| GetClientOnboardingStartProcessor（C01） / GetClientDraftProcessor（C05） / GetClientWorkspaceSummariesProcessor | AbstractProcessor | 各文件第 17 / 25 / 20 行 |
| GetClientReviewApplicationProcessor（O02） / GetClientClassificationProcessor | AbstractProcessor | 第 24 / 25 行 |
| ReplaceClientClassificationProcessor（O03） / ConfirmClientClassificationProcessor（O04） | AbstractLockProcessor | `client-onboarding:review-case:{caseId}`（`ReplaceClientClassificationProcessor.java:81-82`、`ConfirmClientClassificationProcessor.java:55-56`） |
| ApplyClientReviewResultProcessor（MQ 结果） | AbstractProcessor，**无锁**，靠条件更新 | `ApplyClientReviewResultProcessor.java:27-34` |
| ChangeAcquiringQualificationProcessor / ReplacePartnerRolesProcessor（主档运营） | AbstractLockProcessor | `client-master:operating-entity:{entityNo}`（`:72-73`）、`client-master:local-partner:{id}`（`:85-86`） |

锁等待 / 租约取 `redis.coordination.resource-lock`（`paytech-client-master-app/src/main/resources/application.yml:139-144`）。草稿写入要求 recovery 与 Redis 协调同时开启，否则装配失败（`APP/configuration/ClientDraftConfiguration.java:58-63`）。

**R4 新命令的锁建议依据：** 撤回可复用 `ClientOnboardingLocks.application`；入驻码签发 / 作废宜新键 `client-master:local-partner:{id}`，与 `ReplacePartnerRolesProcessor.java:86` 同域。

### 1.2 提交准备与校验

`ClientSubmissionPreparer`（`CORE/service/processor/ClientSubmissionPreparer.java`）：
- 第 83-95 行：装载主体、关系、材料，调用 validator，再追加证件冲突。
- 第 98-120 行：主主体 + 有 DRAFT/ACTIVE 关系的人员；人员可编辑性依赖 commercial 合同引用（第 111 行）。
- 第 123-147 行：在提交时才规范化证件号（第 135 行）并按 `issuingCountryMode` 计算签发作用域（第 136-141 行，硬编码 `"NONE"` / `"REQUIRED"`），再按 `(type, scope, normalized)` 查身份键（第 142-145 行）。
- 第 150-156 行：按资格类型调用 compliance `material-requirements`（R5 后改为 `programs/resolve`）。
- 第 159-182 行：材料文件 Hash 核对 Platform。
- 第 185-199 行：证件冲突（他人占用 / 本申请内重复），固定中文消息（第 48 行）。

`ClientSubmissionValidator`（`CORE/domainservice/validator/ClientSubmissionValidator.java`）的硬编码规则（R2 要删除 / 替换）：

| 行 | 规则 |
|---|---|
| 59-61 | 组织至少 1 名关联人员 |
| 69-73 | 组织必填 legalName、incorporationCountry、organizationType（目录码）、incorporationDate |
| 75-79 | 个人必填 givenName、familyName、birthDate、nationalityCountry、residenceCountry |
| 81-83 | 地址必填 addressLine1、city、countryCode |
| 90-96 | 证件类型须在目录且适用客户类型；`REQUIRED` 时必填签发国 |
| 97-101 | 证件号必填、规范化须成功 |
| 102-104 | `validTo` 未过期 |
| 105-107 | 不可编辑人员须已绑定身份键 |
| 111-124 | 每个 requiredFlag 材料要求至少一份 |
| 127-134 | 经营模式目录码、描述、预计流水 / 客单价 > 0、币种 |
| 137-145 | 联系邮箱、电话必填（硬编码 `"EMAIL"` / `"PHONE"`） |
| 148-159 | 材料主体在范围内、文件可用 |

问题只有 `ProblemFieldError(field, message)`，**没有 code**（第 92、98、100 行等）。提交接口遇问题只返回 `422 SUBMISSION_INCOMPLETE`，不带 errors[]（`CORE/service/processor/SubmitClientApplicationProcessor.java:105-107`），逐项问题须另调 `GET …/submission-check`（`API/onboarding/ClientSubmissionApi.java:33-37`）。R2 的“422 体改需求级 errors[]”需要两个接口一起改。

其他硬编码点（B1 字面量禁令会命中）：
- 关系类型闭合枚举 `OWNERSHIP/CONTROL/DIRECTOR/SIGNATORY/UBO`（`CORE/domain/enums/PartyRelationType.java`），持股比例只允许 OWNERSHIP/UBO（`CORE/domain/onboarding/ClientRelationValues.java:60-62`）。
- 地址类型 `RESIDENTIAL`/`REGISTERED`（`CORE/domain/onboarding/ClientDraftCreation.java:12`、`CORE/domainservice/repository/impl/ClientDraftRepositoryImpl.java:68`、`CORE/domainservice/repository/impl/ClientPeopleRepositoryImpl.java:18,35`）。
- 资格类型 `INDIVIDUAL_KYC/ORGANIZATION_KYB/RELATED_PERSON_KYC`（`CORE/domain/onboarding/ClientDraftCreation.java:11`、`CORE/integration/impl/ClientReviewRegistrationClientImpl.java:61-64`、`CORE/domainservice/service/impl/ClientDocumentServiceImpl.java:31,37`）。

### 1.3 `onboarding.form-catalog` 与 `ClientFormCatalogProperties`

- 结构：`record ClientFormCatalogProperties(organizationTypes, businessModels, identifierTypes, industries)`，`@ConfigurationProperties("onboarding.form-catalog")`，启动冻结、每类不超过 100、代码唯一（`CORE/core/configuration/ClientFormCatalogProperties.java:10-21`）。子结构 `OrganizationType(code,label)`、`Industry(code,label)`、`BusinessModel(code,label,clientTypes)`、`IdentifierType(code,label,clientTypes,issuingCountryMode∈{REQUIRED,NONE})`（第 23-48 行）。
- yml 内容（`paytech-client-master-app/src/main/resources/application.yml:82-116`）：组织类型 6 项、经营模式 6 项、证件 7 项（PASSPORT、NATIONAL_ID、DRIVING_LICENSE、RESIDENCE_PERMIT、COMPANY_REGISTRATION_NUMBER、TAX_ID、LEI）、行业 10 项，label 全部中文。
- 测试 yml 有精简副本（`paytech-client-master-test/src/test/resources/application-mock.yml:145-157`）。
- 消费者：`ClientSubmissionValidator`（第 26-31、72、128、162-165 行）、`ClientFormCatalogServiceImpl`（`CORE/domainservice/service/impl/ClientFormCatalogServiceImpl.java`）、O03 行业校验（`CORE/service/processor/ReplaceClientClassificationProcessor.java:65`）、行业目录接口（`APP/ext/rest/ClientClassificationAdminController.java:51`）。装配在 `APP/configuration/ClientDraftConfiguration.java:43-48` 与 `APP/configuration/ClientSubmissionConfiguration.java:77`、`APP/configuration/ClientReviewConfiguration.java:89`。

### 1.4 form-options 接口与 DTO

- `GET /client-onboarding/v1/form-options?clientType=`，Header `X-Caller-Service / X-Gateway-Actor-Subject / X-Gateway-Session-Id`（`API/onboarding/ClientFormOptionsApi.java:11-15`）。
- 只允许 `client-console-bff`（`APP/ext/rest/ClientFormOptionsController.java:9`）。
- 响应 `ClientFormOptionsResponseBody{clientType, catalogStatus, organizationTypes[], businessModels[], identifierTypes[]}`（`API/onboarding/response/ClientFormOptionsResponseBody.java`），元素 `CatalogOptionResponseBody{code,label}`、`IdentifierOptionResponseBody{code,label,issuingCountryMode}`。
- 目录任一类为空返回 `UNAVAILABLE`（`CORE/domainservice/service/impl/ClientFormCatalogServiceImpl.java:13-16`）。

### 1.5 草稿 values 分区键与字段键

`PATCH …/{applicationId}/draft` 体为 `DraftPatchRequestBody(dirtyFields, values: Map<分区, Map<字段, String>>)`（`API/onboarding/ClientDraftModels.java:19-21`）。全部支持路径与类型在 `CORE/domain/onboarding/ClientDraftPatch.java:45-75`：

| 分区 | 字段（长度） |
|---|---|
| individual | givenName(64)、middleName(64)、familyName(64)、birthDate、nationalityCountry、residenceCountry |
| organization | legalName(64)、tradeName(64)、incorporationCountry、organizationType(64)、incorporationDate |
| address | addressLine1(255)、addressLine2(255)、city(128)、stateOrProvince(128)、postalCode(32)、countryCode |
| identifier | identifierType(64)、issuingCountry、identifierValue(128)、validFrom、validTo |
| business | businessModelCode、websiteUrl(2048)、businessDescription(2000)、expectedVolume、expectedTicket、currency |
| contact | email(320)、phone(32，E.164) |

- 一次最多 50 个 dirtyFields（第 76 行）。列名由路径点换下划线（第 175-184 行），Mapper 以 `changes.containsKey('individual_givenName')` 方式写列（`MAP/ClientDraftMapper.xml:112-151`）。新增 attributes / 本地名 / 新地址列需同时扩 `FIELDS` 表与 Mapper。
- 人员只允许 individual/address/identifier（`ClientDraftPatch.java:154-157`）。
- 读出时 `ClientDraftConverter.values()` 回填同样分区，**含 `identifier.identifierValue` 明文**（`CORE/ext/rest/ClientDraftConverter.java:43-73`，第 64 行）。

### 1.6 people / relations / documents / submissions DTO

- 人员：`CreatePersonRequestBody(person: DraftPatchRequestBody, relation: RelationFields)`；`RelationFields(relationType, ownershipPercentage, effectiveFrom, effectiveTo)`；`CreateRelationRequestBody(relatedPartyId, relationType, ownershipPercentage, effectiveFrom, effectiveTo)`；`RelationPatchRequestBody(dirtyFields, values)`；回执 `PeopleCommandReceiptResponseBody(commandId, applicationId, partyId, relationId, operation, completedAt)`；读出 `PersonResponseBody(partyId, editable, values)` 与 `RelationResponseBody(relationId, relatedPartyId, relationType, ownershipPercentage, effectiveFrom, effectiveTo, status, editable)`（`API/onboarding/ClientPeopleModels.java:4-16`）。
- 材料：`DocumentReferenceRequestBody(subjectPartyId, requirementCode, documentType, fileAddress)`；`DocumentCommandReceiptResponseBody(commandId 恒空, applicationId, documentId, subjectPartyId, fileAddress, operation, completedAt)`；`DocumentResponseBody(documentId, subjectPartyId, requirementCode, documentType, fileAddress, subjectInScope)`（`API/onboarding/ClientDocumentModels.java:11-30`）。
- 提交：`SubmissionResponseBody{applicationId, submissionId, status}`；核对 `SubmissionCheckResponseBody{ready, issues[{field, message}]}`（`API/onboarding/response/Submission*.java`）。新建提交 202、已提交 200（`APP/ext/rest/ClientSubmissionController.java:22`）。
- 申请创建：`StartApplicationRequestBody(clientType)` 只有一个字段（`API/onboarding/ClientDraftModels.java:17`），R4 要加 `onboardingCode`。
- 申请读出：`ClientDraftResponseBody(applicationId, applicationNo, clientId, clientType, subjectPartyId, status, savedAt, editable, values, people, relations, documents)`（`API/onboarding/ClientDraftModels.java:24-29`）；O02 运营读出复用同一结构（`API/onboarding/ClientApplicationAdminApi.java:16-18`）。

---

## 2. 证件明文现状

### 2.1 DO / Mapper / Repository

- `PartyProfileIdentifier` DO 只有 `profileIdentifierId, profileId, identityKeyId, issuerScopeKey`，不含号码（`CORE/core/dal/dataobject/PartyProfileIdentifier.java:9-17`）；号码经 `ClientDraftFields.identifierIdentifierValue` 读取（`CORE/core/dal/dataobject/ClientDraftFields.java:26`）。
- `PartyIdentityKey` DO 含 `normalizedIdentifierValue`（`CORE/core/dal/dataobject/PartyIdentityKey.java:22-23`），`toString` 已脱敏。
- `MAP/PartyIdentityKeyMapper.xml:4-17`：`find` 按 `(identifier_type, issuer_scope_key, normalized_identifier_value)`；`insert` 写明文规范化值。
- `MAP/PartyProfileIdentifierMapper.xml:4-15`：`findByProfile`、`bind`（CAS on identity_key_id）。
- Repository：`CORE/domainservice/repository/impl/PartyIdentityKeyRepositoryImpl.java`、`PartyProfileIdentifierRepositoryImpl.java`。

### 2.2 `identifier_value` 明文写入与读取的全部位置

| 方向 | 位置 |
|---|---|
| 草稿写入（主主体） | `MAP/ClientDraftMapper.xml:137-143`（`saveIdentifier`），由 `CORE/domainservice/repository/impl/ClientDraftRepositoryImpl.java:56-61` 调用 |
| 草稿写入（人员） | 同一 `saveIdentifier`，由 `CORE/domainservice/repository/impl/ClientPeopleRepositoryImpl.java:36-41` 调用 |
| 读取 | `MAP/ClientDraftMapper.xml:68`（`fields` 查询） |
| 回显给 Client / 运营 | `CORE/ext/rest/ClientDraftConverter.java:64` |
| 规范化后进身份键 | `CORE/service/processor/ClientSubmissionPreparer.java:135`；`CORE/domainservice/service/impl/ClientSubmissionServiceImpl.java:55`；`MAP/PartyIdentityKeyMapper.xml:13-16` |
| 校验 | `CORE/domainservice/validator/ClientSubmissionValidator.java:97-101` |
| 发 compliance I01 | `CORE/integration/impl/ClientReviewRegistrationClientImpl.java:113` |
| 唯一冲突识别 | `CORE/service/processor/SubmitClientApplicationProcessor.java:90` 按索引名 `uk_party_identity_norm` 字符串匹配，改 uk 名时必须同步 |

草稿保存时不规范化；只做长度 128 校验（`ClientDraftPatch.java:65`）。

### 2.3 I01 请求组装

`ClientReviewRegistrationClientImpl.subject()`（`CORE/integration/impl/ClientReviewRegistrationClientImpl.java:84-122`）发送：姓名 / 组织名、出生日期、国家、地址、`identifierType`、`issuingCountry`、**`identifierValue` 明文**（第 113 行）、`identityKeyId`（第 114-118 行）、`validFrom/validTo`。compliance 侧 DTO 将 `identifierValue` 定为 `@NotBlank @Size(max=128)`（`paytech/compliance/paytech-compliance-api/src/main/java/com/paytech/compliance/api/request/ClientReviewSubjectBody.java:119-122`）。R3“I01 只传 Token”是两系统同时改的合同变更。I01 不传联系方式。

### 2.4 规范化函数

`CORE/domain/onboarding/ClientIdentifierNormalizer.java:23-30`：NFKC → strip → 删除空白、`-`、`.`、`/` → 大写 → 须匹配 `[A-Z0-9]{1,128}`，否则 null。`issuerScopeKey()` 在第 33-35 行（`GLOBAL` 哨兵）。按设计应迁到 `t_identifier_type` 的规范化 / 校验位规则，本函数可保留为默认规范化。

---

## 3. Vault 现状与可复用部分

- 调用：`ClientDraftContactClientImpl.protect()`（`CORE/integration/impl/ClientDraftContactClientImpl.java:15-32`）。Data Type `EMAIL_ADDRESS_V1` / `PHONE_E164_V1`，`TokenMode.STABLE`，purpose `CLIENT_ONBOARDING_CONTACT`（第 12、25-26 行），每次随机 UUID 作幂等键（第 22-23 行）。Reveal 同 purpose（第 33-41 行）。
- 掩码在 client-master 生成：邮箱 `***@域名`，电话 `***` + 末 4 位（第 27 行）。
- 事务约束：调用前断言无活动事务（第 42-44 行）；失败统一转 `ONBOARDING_DEPENDENCY_UNAVAILABLE`（503）。
- 调用时机：`SaveClientDraftProcessor` 先只读准备，再事务外 Protect，再短写事务（`CORE/service/processor/SaveClientDraftProcessor.java:57-66`）。**人员 Processor（C07/C08）目前不依赖 Vault**，证件 Token 化需给 `CreateClientPersonProcessor` / `SaveClientPersonProcessor` 增加同样的“事务外 Protect”步骤。
- 客户端装配：`@Import(VaultClientAutoConfiguration.class)` 只在 draft-enabled 时生效（`APP/configuration/ClientDraftConfiguration.java:42,110`）；全局自动配置被排除（`application.yml:24`）。属性 `vault.client.connect-timeout/read-timeout`（`application.yml:145-148`），服务发现名默认 `vault-service`、调用方取 `spring.application.name`（`paytech/vault/paytech-vault-client/src/main/java/com/paytech/vault/client/autoconfigure/VaultClientAutoConfiguration.java:55-58,83`）。
- `ProtectCommand(dataType, tokenMode, value, purpose, idempotencyKey)` 不做规范化（`paytech/vault/.../ProtectCommand.java:5-26`）；`RevealQuery(token, purpose)`；`TokenMode {REFERENCE, STABLE}`。
- Vault 策略 DML 只有联系方式四条，按环境分文件维护，调用方名已从 `client-contract-service` 改为 `client-master-service`（`requirements/20261018/test-environment-V2部署与数据迁移/sql/21-vault-upgrade.sql:30-33`，本机 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/sql/30-vault-v2-callers.sql:18-21`）。仓库中尚无 `LEGAL_IDENTIFIER_V1`。
- Token 列先例：`t_party_contact.contact_vault_token VARCHAR(128) NOT NULL`、`contact_masked_value VARCHAR(255)`（`SQL:102-112`）。

**可复用：** 事务外断言、STABLE + 随机幂等键模式、错误映射、Processor 内“只读准备 → Protect → 短写事务”顺序、`VaultClient` 装配。**需新增：** 规范化后再 Protect、末 4 位掩码、Reveal 用途 `CLIENT_ONBOARDING_IDENTIFIER`、按 `t_identifier_type.vault_required` 判断是否 Vault 化（机构登记号保持明文）。

---

## 4. 事件消费

- Listener：`@DomainEventHandler(eventType="ClientReviewDecided", strictOrder=false, transaction=HANDLER_MANAGED)`（`CORE/ext/mq/ClientReviewResultListener.java:23-25`）。`ConcurrentRequestException` 原样抛出重投；`IllegalArgumentException | FailException` 转 `DomainEventRejectedException` 进 DLQ（第 41-47 行）。
- 转换器校验：`eventType=ClientReviewDecided`、`sourceService=compliance`、`aggregateType=DD_CASE`、`schemaVersion==1`、`aggregateId==payload.caseId`（`CORE/ext/mq/ClientReviewResultMessageConverter.java:24-27`）。读取 `applicationId, caseId, submissionId, outcome, decisionId, decidedBy`（第 29-31 行）。
- outcome 枚举：`@Pattern("^(RETURNED|APPROVED|REJECTED)$")`（`CORE/service/processor/request/ApplyClientReviewResultRequest.java:14-17`），校验失败抛 `InvalidParameterException`（FailException 子类）而进 DLQ。
- 分支：`ApplyClientReviewResultProcessor.java`
  - 第 43-50 行：非 RETURNED 必须有 decisionId；APPROVED 必须有 decidedBy。
  - 第 55-75 行：submissionId 不是当前 → STALE；caseId 未落库 → 抛错重投；caseId 不同 → STALE；已是目标状态 → DUPLICATE；非 UNDER_REVIEW → STALE。
  - 第 83-89 行：RETURNED / APPROVED / 其余一律 REJECTED。**EXPIRED 若放进 Pattern 会被误判为 REJECTED**，要显式分支到 WITHDRAWN。
  - 第 92-108 行：APPROVED 时构造下一版生命周期历史 `CLIENT_ACTIVATED`。
- 落地：`ClientReviewOutcomeServiceImpl.conclude()` 同一事务做申请结论、Client 激活、`markApproved`、历史追加、登记 `CLIENT_TENANT_PROJECTION`（`CORE/domainservice/service/impl/ClientReviewOutcomeServiceImpl.java:28-38`）。终态释放 `active_slot`（`CORE/domainservice/repository/impl/ClientApplicationRepositoryImpl.java:71-72`，`MAP/ClientApplicationMapper.xml:35-41`）。
- 只更新主主体 Profile 的 approved_time（`markApproved(partyId)`）。关联人员 Profile 的 approved_time 不在此写入，需核对是否为预期行为。
- **QualificationChanged 消费者：不存在**（client-master 代码中无该字符串，仅测试方法名 `test_acquiringQualificationChanged`，与本事件无关）。
- paytech-messaging 消费端：`messaging.consumer.service: client-master`（`application.yml:121-123`）；拓扑注释为订阅 `compliance.events`、`client-master.compliance.hash` + `p0..p3`、Quorum、单活、delivery-limit 5、DLX `client-master.compliance.dlx` → `.dlq`（`application.yml:117-120`）。mor-test 设 `MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS: "4"`（`test-environment/compose.applications.yaml:385-387`）。Handler 随 `draft-enabled` 与 `recovery-enabled` 同时开启才装配（`APP/configuration/ClientReviewConfiguration.java:8,74-77`）。
- 新增 `QualificationChanged` Handler 可走同一 compliance 分区队列，只需加 `@DomainEventHandler(eventType="QualificationChanged")`。Client 状态 SUSPENDED：`t_client` 已有 `suspension_time` 列（`SQL:148-160`），Platform 投影 API 状态已接受 `SUSPENDED`（`paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/tenantprojection/request/StatusRequestBody.java:35`），可复用 `CLIENT_TENANT_PROJECTION` 补偿推送暂停状态。现 `ClientMapper.activate` 只改 `status/update_time`，不写 `activation_time`（`MAP/ClientMapper.xml:17-19`）。

---

## 5. 补偿 Handler

目录：`compensation-event-catalog.yaml:3-6` 登记三类。

| eventType | Handler | eventKey | 登记点 | 失败策略 |
|---|---|---|---|---|
| CLIENT_REVIEW_REGISTRATION | `CORE/service/handler/ClientReviewRegistrationHandler.java` | submissionId（UUID，第 17-22 行） | `ClientSubmissionServiceImpl.java:41`，与提交同事务 | 25s / 12 次 / 5s→30min / 倍数 2 / 抖动 0.2（第 29-32 行）；事务内或 payload 非空 → 终态；非 SUBMITTED → 成功；Risk FailException → 终态 `CLIENT_REVIEW_REJECTED`；其他 → 可重试（第 38-65 行） |
| CLIENT_REVIEW_CLASSIFICATION | `CORE/service/handler/ClientClassificationHandler.java` | caseId（第 17-22 行） | O04 确认 | 同上策略；`CLIENT_REVIEW_CLOSED` 视为成功，其他拒绝终态（第 38-59 行） |
| CLIENT_TENANT_PROJECTION | `CORE/service/handler/ClientTenantProjectionHandler.java` | 生命周期 changeVersionId（第 10-13 行） | 审核通过同事务 | 同上策略；`TENANT_PROJECTION_STALE` 重试；拒绝 / 不一致终态（第 18-60 行） |

R3 约束“补偿载荷不含原值”现状已满足（payload 一律 `"-"`，Handler 实时读库）。但 I01 Handler 会把库中读到的明文证件号经 `ClientReviewRegistrationClientImpl` 发出，改 Token 后自然消除。新增撤回 / 资格暂停如需通知 compliance 或 Platform，可按同模式新增 eventType 并登记到 catalog。

---

## 6. 状态与 `t_party_profile`

- `ClientApplicationStatus {DRAFT, SUBMITTED, UNDER_REVIEW, RETURNED, APPROVED, REJECTED, WITHDRAWN}`（`CORE/domain/enums/ClientApplicationStatus.java`）；`ClientStatus {PENDING, ACTIVE, SUSPENDED, TERMINATED}`。
- `active_slot`：插入为 1（`MAP/ClientDraftMapper.xml:93-95`）；终态置 NULL（`MAP/ClientApplicationMapper.xml:38`）；进行中状态必须为 1、终态必须 NULL（`CORE/domain/onboarding/ClientDraftPolicy.java:13-14`）；`uk_client_application_active(client_id, active_slot)` 与 `uk_client_app_account_active(requested_by_account_id, active_slot)`（`SQL:250-251`）；唯一冲突时重试（`CORE/service/processor/ClientOnboardingCommands.java:48`）。
- 撤回：无实现（见 0.7）。
- `draft_application_id` / `approved_time` 处理点：
  - 写：`MAP/ClientDraftMapper.xml:96-97`（新建）、`:40-41`（被拒重申请转移）、`MAP/ClientPeopleMapper.xml:47`（人员新建）、`MAP/PartyProfileMapper.xml:28-32`（批准只写 approved_time）。
  - 判定：`CORE/domain/onboarding/ClientDraftPolicy.java:21-29`（可编辑 = approved_time 空且 draft_application_id 等于当前申请）。
  - Schema 注释写“批准后清空”（`SQL:24`），代码未实现。

**按 party_id 关联 `t_party_profile`、隐含每 Party 一行的位置（current_slot 需改）：**

| 位置 | 用途 |
|---|---|
| `MAP/ClientDraftMapper.xml:12` | 申请身份（C01/C04/C05/C14 全部依赖） |
| `MAP/ClientApplicationMapper.xml:56` | 工作区摘要 |
| `MAP/ClientMapper.xml:21-25` | 法定名称子查询（注释直接写 `uk_party_profile_party`） |
| `MAP/EntitySourceRelationMapper.xml:27`、`MAP/OperatingEntityMapper.xml:27`、`MAP/LocalPartnerMapper.xml:23` | 运营主档名称 |
| `MAP/PartyProfileMapper.xml:13-18` | Checkout `findForCheckout` |
| `MAP/PartyProfileMapper.xml:28-32` | 批准 `markApproved(party_id)` |
| `MAP/ClientPeopleMapper.xml:14-19` | 人员范围 |

Checkout 用 `unique()` 取 Profile，多于一行抛主档冲突（`CORE/domain/checkout/CheckoutEligibilityContext.java:109,209,364-367`）；commercial 通过 `GET /client-master/v1/party-profiles?ids=` 按 profileId 读（`API2/checkout/ClientMasterCheckoutApi.java`、`MAP/PartyProfileMapper.xml:20-26`），按 ID 读不受 current_slot 影响。

---

## 7. 表、模型与 SQL 维护

- PDManer：`docs/database/client-master.pdma.json`，32 个实体（含 2026-10-24 新增的 `t_partner_role / t_party_role / t_client_partner_relation`）。README 仍写“29 张表”和两个生成器（`README.md` “数据库与 PDManer” 节，`scripts/pdmaner/generate-client-master-model.mjs`、`generate-oe-client-schemas.mjs`），**已过时**。依用户 2026-10-22 决定，PDManer 直接手改 JSON，不运行生成器，生成器漂移测试失败视为噪声（CURRENT_IMPLEMENTATION 第 208 行也记“client-master PDManer 脚本测试 2 例基线失败”）。
- Schema：`SQL`（530 行、32 表），文件头写“generated from the approved PDManer model”（第 1-4 行），实际已手工维护。
- 测试 DDL：`paytech-client-master-test/src/test/resources/init/ddl.sql`（534 行、32 表），头部注明“逐字摘自 client-master-schema.sql”并加建库 / 建账号 / 授权；与 Schema 表定义逐字一致（本次 diff 为空）。
- 初始化 DML：`docs/database/sql/client-master-initial-data.sql` 只有注释，无种子行。国家包 DML（AE/SA/EG/ZZ）将成为本库首份种子数据。按 AGENTS.md，“一个服务的初始化 DML 保存在一个 SQL 文件”，需决定国家包写入该文件还是需求目录。
- 增量 SQL：`docs/database/sql/changes/YYYY-MM-DD-<slug>.sql`，现有 3 个。约定见 `changes/2026-10-24-relationship-snapshot.sql:1-15`：头部写需求链接、内容、执行顺序、备份要求、DDL 隐式提交不可整段重跑、回退语句；结构与数据分两个文件（`-data.sql`）。
- 物理决定文档：`docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md`。
- 架构规则表归属：`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml:98-129` 列出 client-master 30 张业务表；新表必须登记，否则 `MapperTableOwnershipRule` 失败。`known-deviations.yaml` 中无 client-master 偏差。
- 相关现有列长度：`t_organization_profile.legal_name / trade_name VARCHAR(64)`、`t_individual_profile.given/middle/family_name VARCHAR(64)`（`SQL:30-55`），全球法定名称 64 可能偏短，7.1 只列了 middle_name→128。
- `t_party_relation` 唯一键 `uk_party_relation_identity(organization_party_id, related_party_id, relation_type)` 不含申请或状态（`SQL:114-128`），目录化关系类型后仍是同一组合只能一行。
- `t_party_identity_key` 唯一键 `uk_party_identity_norm(identifier_type, issuer_scope_key, normalized_identifier_value)`（`SQL:56-68`）；`t_party_profile_identifier.identifier_value VARCHAR(128) NULL` 与 `uk_profile_identity(profile_id, identity_key_id)`（`SQL:70-85`）。

---

## 8. 测试设施

- 基类：`TEST/test/base/ClientMasterMockTestBase.java`，`@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("mock")`；全局 BeforeEach 断言前例已清理、配置会话与 commercial 无引用基线（第 29-36 行）；全局 AfterEach 等待 Mock 请求与补偿线程空闲后检查意外调用（第 38-49 行）。入口基类：`test/base/onboarding/AbstractSubmissionApiTest.java`、`AbstractClientReviewTest.java`，以及 checkout、entityadmin 两个。
- Mocker：`test/mocker/onboarding/{OnboardingApplicationMocker, OnboardingOwnerMocker, ComplianceEventMocker}`、`test/mocker/commercial/CommercialAgreementPartyMocker`、`test/mocker/checkout/CheckoutCandidateFixture`。**没有 Builder 目录**。Checker 只有 `test/checker/checkout/CheckoutCandidateResponseChecker.java`。DAL：`test/dal/onboarding/OnboardingTestMapper.java`、`test/dal/checkout/CheckoutCandidateTestMapper.java`。
- 外部 DTO Mock：`@EnableMockHttpServer`（foundation testsupport）在随机 loopback 起测试 HTTP 服务（`TEST/ClientMasterTestApplication.java:6-12`）；Mock 实现正式 `@HttpExchange` 接口并经 `@Valid` 校验后记录，如 `test/mock/external/onboarding/MockRiskReviewRegistrationApi.java:4-30`（compliance I01/I02）、`MockRiskOnboardingApi`（材料要求）、`MockPlatformAuthenticationApi`、`MockPlatformInternalFileApi`、`MockPlatformTenantProjectionApi`、`test/mock/external/commercial/MockCommercialAgreementPartyApi`。服务发现由 SimpleDiscovery 把 platform / compliance / vault / commercial 指向该服务（`application-mock.yml:23-34`）。**无 Vault Mock。**
- 清理：`test/clear/ClientMasterDataCleaner.java:23-31` 硬编码 32 张表，每例全清。国家包 / 目录表若加入清理清单，每例都要重新插入目录夹具；若不加入，则需在测试库预置并从清理中排除，需在方案中决定。
- MQ：`PartitionQueueBaselineConfiguration` 在监听容器启动前清空 `client-master.compliance.p0..p3` 与 DLQ（`TEST/ClientMasterTestApplication.java:2-3,12`）；`ComplianceEventMocker` 发布结果事件。
- 架构测试：`TEST/auto/architecture/ArchitectureAutoTest.java` 执行共享规则（现 R1–R15，`paytech/tools/arch-rules/src/main/java/com/paytech/arch/rule/`）。**无字面量禁令规则**，7.1 的 “ArchUnit 字面量禁令”需在 arch-rules 新增规则或在本服务写专用测试。
- 数量：22 个测试类、100 个 `@Test`（`ResolveCandidatesHttpTest` 12、`SubmitClientApplicationTest` 10、`AssociateClientDocumentTest` 9 等），与 CURRENT_IMPLEMENTATION 第 18 行 “client-master 100” 一致。`paytech-client-master-test/README.md` 的表格仍写 “R1–R12”“10 类 37 例”，已过时。

---

## 9. API 模块

- 旧包 `com.paytech.client.contract.api`：`onboarding/`（Draft、People、Document、Submission、FormOptions、OnboardingStart、ApplicationAdmin、ClassificationAdmin）、`workspace/`、`enums/{ClientDraftErrorCode, ClientFormCatalogStatus}`。README 说明旧包“保留一个版本”。
- 新包 `com.paytech.client.master.api`：`checkout/`、`entityadmin/`、`storedcard/`、`enums/`。
- DTO 风格两种并存：旧包入驻模型多为嵌套 **record**、无校验注解（`API/onboarding/ClientDraftModels.java`、`ClientPeopleModels.java`、`ClientDocumentModels.java`）；旧包 response 子包与新包为 Lombok `@Data` / `@Getter @Setter` **class**，新包请求带 `@NotBlank` 等注解，接口参数加 `@Valid`（`API2/checkout/request/CheckoutCandidatesRequestBody.java`、`API2/checkout/ClientMasterCheckoutApi.java:18-19`）。
- `@HttpExchange` 用法：类级前缀 `@HttpExchange("/client-onboarding/v1/applications")` + 方法级 `@PostExchange/@PatchExchange/@DeleteExchange`（`API/onboarding/ClientDraftApi.java`、`ClientPeopleApi.java`），或方法级完整路径（`API/onboarding/ClientFormOptionsApi.java:11`、`API2/checkout/ClientMasterCheckoutApi.java:6,17`）。身份全部来自 Header `X-Caller-Service / X-Gateway-Actor-Subject / X-Gateway-Session-Id`；Controller 只放行 `client-console-bff`（`APP/ext/rest/ClientSubmissionController.java:15,27`）。
- 错误码到 HTTP 状态的映射集中在 `APP/configuration/ClientMasterWebConfiguration.java:22-40`；新增 `ONBOARDING_CODE_INVALID`、撤回相关错误码要在此登记。
- 建议：新接口（jurisdictions、form-schema、requirements、declarations、withdrawal、运营目录、入驻码）放新包 `com.paytech.client.master.api`，采用 class + 校验注解风格。

---

## 10. 配置

`paytech-client-master-app/src/main/resources/application.yml`：
- `onboarding.recovery-enabled: false`、`onboarding.draft-enabled: false`（第 78-80 行）；`form-catalog`（第 82-116 行）。
- 健康检查 redis / rabbit 跟随 draft-enabled（第 68-72 行）；`compensation.enabled` 跟随 recovery（第 125-126 行）；Redis 协调跟随 draft（第 139-144 行）。
- RabbitMQ 走 `CCT_RABBITMQ_*` 环境变量（第 28-34 行）。
- 日志只记元数据的路径前缀：`/client-onboarding`、`/internal/client-onboarding`、`/client-onboarding-admin`、`/client-workspace`、`/client-master-admin`（第 128-138 行）。新运营接口若放 `/client-master-admin/v1/...` 已自动覆盖。
- Vault 超时（第 145-148 行）；`internal-auth.mode: "OFF"`（第 150-151 行）。
- 出站地址不在 yml：Platform 走发现名 `platform-support-service` + `/platform-support`（`APP/configuration/ClientOnboardingRecoveryConfiguration.java:36-61`）；compliance 走 `compliance-service`（`APP/configuration/ClientDocumentConfiguration.java:52-70`）；commercial 走 `commercial-service`（`APP/configuration/ClientDraftConfiguration.java:115-143`）。测试直连 `onboarding.platform-test-base-url` / `onboarding.risk-test-base-url` 仅 `auto` profile 且 loopback 允许。
- 内部 Token 按目标服务名附加（`InternalCallTokenOptions.apply(..., "compliance-service")` 等）。
- mor-test：`ONBOARDING_DRAFT_ENABLED/RECOVERY_ENABLED=true`、Snowflake datacenter 0 / worker 26、消息分区 4（`test-environment/compose.applications.yaml:385-391`）；CURRENT_IMPLEMENTATION 第 20 行记 worker 26 与 channel-connector 冲突（既有问题）。

---

## 11. 多语言 / 用户文案

client-master 目前直接输出中文用户文案，Client Web 原样展示：
- 目录 label：yml 中文（`application.yml:84-116`），经 form-options 返回。
- 提交问题 message：`ClientSubmissionValidator.java:24,60,93,100,103,106,121,154,156,179,188`；冲突消息 `ClientSubmissionPreparer.java:48`。
- 草稿字段错误：`ClientDraftPatch.java:103,114-125,135,141,156,162,196,199,255-262`。
- 错误码 message：`API/enums/ClientDraftErrorCode.java:3-25`（RFC 9457 `detail` 来源）。
- 前端消费：`client-console-web/src/submission.ts:38,50,60,63,64` 显示 `issue.message`。

按 R2 改为 `errors[{requirementKey, code, reason, remediation}]` 时，建议 client-master 只返回稳定 code 与参数，展示文案由 Web 按 Platform 多语言键渲染；目录名称同理返回 code + 多语言键，或由 Schema 表带 `label_key`。旧 `message` 字段在过渡期是否保留需在方案中定。

---

## 12. CURRENT_IMPLEMENTATION 现状

- 最近一次 client-master 部署：mor-test 2026-10-25（tag `relsnap-20261025a`，镜像 `eeed4e63…`），本地提交 `4b661830` 未推送（`agent-docs/CURRENT_IMPLEMENTATION.md:14-19`）。
- 遗留：GAP-PROFILE（`draft_application_id` 不清、`content_hash` 不写，37 个已通过 Client 在 REAL 下会被拒，mor-test 保持 MOCK）；Snowflake worker 26 与 channel-connector 共用（`CURRENT_IMPLEMENTATION.md:20`）。
- 入驻切片：2026-10-16 部署 mor-test（`client-onboarding-20261016`，源 `21a1a51`），验收 39/39 与场景 85/85（含退回改证件、被拒重申请、业务防重）（`CURRENT_IMPLEMENTATION.md:1251-1255`）。退回改证件为“简化版”：先解绑再保存，旧键留原 Party，未覆盖人员 / 改回原号 / 撞他人号码等场景（同文件第 1258 行）。
- 汇总表第 1166 行仍写“CCT 应用未部署、Client 入口关闭”，与上述部署记录矛盾，属过时条目。
- 第 1264 行记录 I01 按“内部同步合同传 CCT 主体结构化原值（含证件号），Risk 收到后用 `RISK_DD_PARTY_SNAPSHOT_V1` Protect”；R3 改为只传 Token 后，compliance 侧这条快照逻辑与 Reveal 授权需同步修订。
- 2026-10-15 记录：联系方式 Vault 幂等键改为每次随机 UUID（第 1256 行），与现代码一致。

---

## 13. 对 7.1 清单的修订建议（供阶段 2 结论参考）

1. “删除”行补充：`ClientFormCatalogStatus` 枚举、`ClientFormOptionsApi` / Controller / Processor / `ClientFormCatalogService(Impl)` / `ClientFormOptions`、`ClientIdentifierNormalizer` 的去留，以及 **industries 的新归属**（O03/O04 与行业目录接口仍需要）。
2. “改表”行补充：GAP-PROFILE 修复（批准时清 `draft_application_id`、写 `content_hash`，并对存量 37 个 Client 回填）；`t_party_profile` 9 处 SQL 加 `current_slot=1`；`SubmitClientApplicationProcessor.java:90` 的索引名匹配同步改名；legal_name / 姓名列长度评估。
3. “出站”行补充：C07/C08 人员证件同样 Vault 化；`ClientDraftConverter` 回显改为掩码（Client 编辑时如需原值，用 Reveal `CLIENT_ONBOARDING_IDENTIFIER`）；O02 运营读出不再返回证件原值。
4. “入站事件”行补充：转换器同时接受 v1 / v2 的发布顺序要求；`target()` 默认分支改为显式 REJECTED，新增 EXPIRED → WITHDRAWN；SUSPENDED 需要追加生命周期历史并登记 `CLIENT_TENANT_PROJECTION`。
5. “测试”行补充：新增 Vault Mock；目录 / 国家包夹具与 `ClientMasterDataCleaner` 的关系；arch-rules 新增字面量规则与 `table-ownership.yaml` 登记；更新测试 README。
6. “文档”行补充：README 表数与生成器说明、CURRENT_IMPLEMENTATION 第 1166 行过时条目、Schema 文件头“generated”字样。
