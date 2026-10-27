# compliance 研发报告（切片 1 · 阶段 4—5，cmp-dev）

> 日期：2026-10-27。工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-onboard`（基于 `ea0272f3`，未提交）。Maven 本地库：`~/.m3-onboard/repository`（worktree 的 `.mvn/maven.config`）。
> 合同：[technique.md](../technique.md) 第 1.4、2.2、2.3、3 节；[requirement.md](../requirement.md) R5—R8、B6—B9、AC-05—AC-13；[design.md](../../../../design-discuss/client-onboarding/design.md) 6.1、7.3—7.5、10.2、13。

## 1. 结论

- compliance 切片 1 后端已实现：CP-1 程序解析、CP-2 I01 v2、CP-3 撤回、CP-4 结果扩展、CP-5 工作台全部命令、CP-6 门禁、`ClientReviewDecided` v2、`QualificationChanged` v1、`CASE_SCREENING` 筛查（client-master CM-13 + channel-connector `SCREENING_SEARCH`）、退回超期扫描。
- 测试：`./mvnw -B -o -pl compliance/paytech-compliance-test -am verify -Dtest='com.paytech.compliance.**'` **BUILD SUCCESS，133 项全部通过**（06:2x 复跑）（`-Dtest` 只限定 compliance 包，避免 `-am` 连带运行 Foundation 测试）。
- 本地 `mor_compliance` 已按新测试 DDL 重建（29 表）；重建前备份 `~/Code-MoR-backups/20261027-onboarding-local/mor_compliance-before-cmp-dev-020545.sql`（重建前库内无业务数据）。
- 增量脚本已按发布决策 P-8 改为**保留行**（不再 DROP 表）：ADD（NOT NULL 新列带临时默认值）→ 回填 → MODIFY 为最终定义。验证：临时库按 HEAD Schema 建表并装入旧形态数据（旧通用程序种子、APPROVED / RETURNED / REJECTED / SUBMITTED 案件族、证据、资格、MANUAL 资格、补偿任务）→ 执行增量 → 全部表 `SHOW CREATE TABLE` 与新 Schema 逐字相同 → 执行初始化 DML → 执行清理；临时库已删除。
- 新增 `changes/2026-10-27-onboarding-global-cleanup.sql`（P-8 在途清理）：同一验证中只剩 APPROVED 案件族、其决定与资格、MANUAL 资格及其补偿任务，19 项孤儿核验全为 0；首次运行因排序规则报错时事务随连接回滚，数据未变（已修正为显式 `COLLATE`）。
- 新增运营只读程序目录 `GET /client-onboarding-admin/v1/programs`（O14），见 6.3。

## 2. 文件清单

| 范围 | 文件 |
|---|---|
| API（`paytech-compliance-api`） | 新增 `ProgramResolutionApi` 与 `ProgramResolutionResponseBody` / `ProgramMaterialBody` / `ProgramCheckBody` / `ProgramScreeningBody`；I01 v2 改 `ClientReviewRegistrationRequestBody`、`ClientReviewSubjectBody`、`ClientReviewRelationBody`、`ClientReviewDocumentBody`，新增 `ClientReviewAttributeBody`、`ClientReviewDeclarationBody`；I03 `ClientReviewRegistrationApi.withdraw` + `ClientReviewWithdrawalRequestBody`；CP-4 `ClientReviewResultResponseBody` + `ClientReviewRequirementErrorBody`；CP-5 `ClientReviewAdminApi` 重写，新增 8 个请求体与 6 个响应体；事件 `ClientReviewDecidedPayload`（v2）、`ClientReviewDecidedErrorPayload`、`QualificationChangedPayload`；错误码增 `CLIENT_REVIEW_CHECK_RECORDED`、`CLIENT_REVIEW_HIT_CLOSED`；删除 `MaterialRequirementsApi`、`MaterialRequirementsModels`；pom 加 Lombok（provided） |
| core | 数据对象 12 改 / 新；Mapper（Java + XML）11 改 / 新；Repository 11 改 / 新；`DueDiligenceProgramService(Impl)`；`ClientReviewService(Impl)`、`QualificationService(Impl)` 改；Processor：`RegisterClientReviewProcessor`（重写）、`ClientReviewRegistrationBuilder`、`ClientReviewCommandSupport`、`ReviewClientEvidenceProcessor`、`WaiveClientReviewRequirementProcessor`、`RecordClientReviewCheckProcessor`、`RerunClientReviewScreeningProcessor`、`ResolveScreeningHitProcessor`、`RateClientReviewRiskProcessor`、`RecommendClientReviewProcessor`、`DecideClientReviewProcessor`（重写）、`WithdrawClientReviewProcessor`、`ExpireReturnedClientReviewsProcessor`、`GetClientReviewCaseProcessor`、`ListClientReviewCasesProcessor`、`GetClientReviewResultProcessor`、`ResolveProgramProcessor`；Handler：`CaseScreeningHandler`、`QualificationChangedHandler`、`ClientReviewResultHandler`（改）；出站端口 `ScreeningSearchGateway`、`ScreeningSubjectDirectory`、`QualificationChangePublisher`；`ScreeningProperties`；枚举 `DueDiligenceCaseStatus`、`ScreeningHitResolution`、`ReviewCodes`；门禁 `ClientReviewApprovalPolicy` 重写；删除材料目录相关 9 个类 |
| app | `ProgramResolutionController`、`ClientReviewAdminController`（重写）、`ClientReviewRegistrationController`（I01 v2 + I03）、`ClientReviewWireConverter`（重写）、`ProgramResolutionRequestConfiguration`、`ComplianceOutboundConfiguration`、`integration/http/ChannelConnectorScreeningGateway`、`integration/http/ClientMasterScreeningSubjectDirectory`、`ext/mq/QualificationChangedEventPublisher`、`ext/mq/ClientReviewDecidedEventPublisher`（v2）、`ext/job/ClientReviewReturnExpiryJobHandler`；pom 加 `paytech-http-client-support`、`paytech-channel-connector-api`、`paytech-client-master-api`、`spring-cloud-starter-loadbalancer`；`application.yml` 删 `onboarding.materials.*`、加 `compliance.screening.channel-code` |
| 数据库 | `docs/database/sql/changes/2026-10-27-onboarding-global.sql`（新，保留行）、`changes/2026-10-27-onboarding-global-cleanup.sql`（新，P-8）、`compliance-schema.sql`、`compliance.pdma.json`（28 实体，字段与索引和 Schema 逐项一致）、`compliance-initial-data.sql`（新，AE / SA / EG 九个程序）、`COMPLIANCE_PHYSICAL_DECISIONS.md` 10.4、`README.md` |
| 目录登记 | `compensation-event-catalog.yaml`（`QUALIFICATION_CHANGED`、`CASE_SCREENING`）；`paytech/foundation/paytech-messaging/catalog/events.yaml`（`ClientReviewDecided` v2、`QualificationChanged` v1）；`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` compliance 段加 4 张新表 |
| 测试 | 删除旧审核测试 9 个及其夹具 / Checker 15 个（模型已重写）；新增 `ProgramResolutionApiAutoTest`、`ClientReviewRegistrationApiAutoTest`、`ClientReviewWorkbenchApiAutoTest`、`ClientReviewReturnApiAutoTest`、`ClientReviewCaseDetailApiAutoTest`、`ZzCountryPackAutoTest`、`QualificationChangedEventAutoTest`、`ComplianceCompensationHandlerAutoTest`、`ComplianceHardcodingGuardTest`；夹具 `ProgramPackMocker`（执行初始化 DML，S-10 同源）、`ProgramBuilder`（含 ZZ）、`ClientReviewRegistrationRequestBuilder`（v2）、`ReviewTestMapper`、`ProgramTestMapper`、`ComplianceEventProbe`；外部 Mock：`MockServiceChannelExecutionApi` + `ScreeningMocker`、`MockClientMasterCheckoutApi` + `PartyProfileMocker`（`@EnableMockHttpServer` 独立 Loopback 服务，意外调用在全局 AfterEach 检查）；`ComplianceMockTestBase`、`ComplianceArchitectureTest`、`WebLoggingBoundaryAutoTest`、`ComplianceDataCleaner`、`ComplianceDatabasePrecondition`（29 表）、`init/ddl.sql`、`application-mock.yml`、test pom（Lombok） |

## 3. 测试结果（2026-10-27 02:2x，本地 local-environment）

| 测试类 | 项 | 覆盖 |
|---|---|---|
| ProgramResolutionApiAutoTest | 21 | 三国九程序解析与 DML 同源（S-10）、材料槽必需 / 可豁免 / 时效、辖区精确 > 全局、Owner > 平台、无程序 / 歧义 / 未批准 503、参数 400（AC-01 材料来源、AC-05） |
| ClientReviewRegistrationApiAutoTest | 10 | I01 v2 建主 + 子案件 + 依赖、冻结辖区 / 档次 / 责任方 / 版本、检查实例化、9 次 Mock 筛查与调用参数、命中落库、渠道不可用不虚构成功、重放 / 改内容 409、材料槽与类型 400、无程序 503、previousCaseId 校验、I03 撤回与幂等、已批准不可撤回（AC-05、AC-10、AC-13） |
| ClientReviewWorkbenchApiAutoTest | 10 | 完整批准链与冻结字段、资格投影（ACTIVE、next_review_time）、两事件；门禁列表与 422；START_REVIEW；证据拒绝原因与核验人；豁免；人工检查有效期与 409；命中处置与 SUPERSEDED（重跑）、TRUE_POSITIVE_REJECT 阻断；CRR 因子必填、改评级清推荐；拒绝四眼与子案件撤回；责任方 / 辖区过滤；O14 程序目录过滤与 400（AC-06、AC-08、AC-10、AC-13） |
| LegacyCaseCompatibilityAutoTest | 1 | 迁移后旧 APPROVED 案件（RETIRED 程序、辖区 LEGACY、无评级）详情 / 列表 / 客户结果可读；RETIRED 旧全局程序不再被解析、仍在目录中（P-8） |
| ClientReviewReturnApiAutoTest | 4 | 退回需求级错误与期限、v2 事件 errors / dueTime、结果接口、重提第 2 轮复用材料与 PASS 检查、过期扫描与 EXPIRED 事件、退回后撤回、请求校验（AC-07） |
| ClientReviewCaseDetailApiAutoTest | 3 | 详情结构、按查看人的四眼门禁、I02 重放 / 冲突、程序版本冻结（AC-12） |
| ZzCountryPackAutoTest | 1 | 仅夹具新增 ZZ（自定义材料类型、检查、关系类型）跑通 解析 → 立案 → 筛查 → 审核 → 批准 → 事件；四眼关闭记 four_eyes_waived=1（AC-11 compliance 部分） |
| QualificationChangedEventAutoTest | 1 | 手工登记 v1 APPROVED、撤销 v2 REVOKED 各发布一条 QualificationChanged（AC-09 compliance 部分） |
| ComplianceCompensationHandlerAutoTest | 4 | 三个 Handler 独立入口：畸形 / 缺数据终态、名称读取失败可重试、无名称终态、已结束案件不筛查、重入幂等 |
| ComplianceHardcodingGuardTest | 1 | 生产源码不含 DML 目录值字面量（S-17） |
| ComplianceArchitectureTest / ArchitectureAutoTest | 12 / 1 | 依赖集合、事件目录、metadata-only（S-16 修复）、R1—R15 |
| QualificationAdminApiAutoTest / QualificationEvaluationApiAutoTest / WebLoggingBoundaryAutoTest | 31 / 27 / 6 | 既有资格与日志边界回归 |
| **合计** | **133** | 失败 0 |

既有失败 F-11（`ComplianceArchitectureTest:174` metadata-only 前缀精确断言）已修正为按 yml 读取并要求包含三个敏感前缀（材料目录前缀随接口删除）。

## 4. 与合同的偏离与取舍（均按最保守解释）

1. **CP-4 不新增 `caseStatus`**：`ClientReviewResultResponseBody.status` 本身即案件状态，另加同义字段违反"不复制同值"；新增 `roundNo`、`errors[]`、`dueTime`，`outcome` 增 WITHDRAWN / EXPIRED。
2. **CM-13 字段**：client-master 已补 `residenceCountry`、`incorporationCountry`（cm-dev 06:xx 安装）；筛查检索的 `countries` 个人为国籍 + 居住国（去重），机构为注册国，`ClientReviewRegistrationApiAutoTest` 断言请求中的国家。
3. **筛查不保存命中实体姓名**（compliance 本切片不调 Vault，设计要求姓名进 Vault）：审核员凭 `externalHitRef` 在提供方查看原文。`search_term_vault_token`、`whitelisted_flag`、`name_vault_token` 未建。
4. **必需筛查未成功即阻断批准**（`SCREENING_INCOMPLETE`），同时 `MANUAL_SCREENING` 人工检查为阻断项；生产在接入真实供应商前不能批准（不虚构成功，B7）。
5. **四眼同样约束拒绝**：程序要求四眼时，拒绝也须有 REJECT 推荐且决定人不同于推荐人；批准须 APPROVE 推荐。推荐可重复，改评级清空推荐。
6. **任何 TRUE_POSITIVE_REJECT 命中都阻断批准**（设计只写 SANCTIONS）；以最新一次处置为准，被新执行取代的命中不计。
7. **条件性材料**（多股东章程、法人股东股权结构、已登记税号、签字人授权、AE / SA 地址证明）在 DML 中为非必需、可豁免：模型无法表达条件，审核员以退回错误要求补交。EG 按 design 9.5 必需。
8. **重提复用规则**：同材料槽本轮文件与上一轮已接受证据同地址同摘要时直接 ACCEPTED；本轮没提交该槽时复制上一轮已接受证据；有新文件则整个要求回到 RECEIVED。检查只复用 `valid_until` 晚于当前的 PASS；有效期为空的检查不复用。筛查、评级、推荐不复用。
9. **重提约束**：previousCaseId 必须是该申请最近一轮、仍为 RETURNED、未过补件期限、未被重提的主案件，且责任方与辖区不变；首次提交时申请不得已有案件。
10. **撤回 / 过期范围**：撤回作用于进行中或已退回（未被重提）的主案件及其同状态子案件；撤回不发布事件（S-14）。过期只发布 EXPIRED，不形成资格决定。
11. **未写入的状态**：`PENDING_DEPENDENCY`、`WAITING_EVIDENCE`、`DRAFT` 只进入枚举与注释。子案件随主案件一起决定，筛查异步执行不改变案件状态，故本期没有触发点。
12. **程序表未建 `priority_no`**：精确度完全决定优先级，同级歧义按配置错误 503。`t_dd_program_version` 删除无读取方的 `content_hash`、`review_frequency_code`；S4 的 `auto_approve_allowed` 与阈值未建。
13. **I01 的 `attributes[]`、`declarations[]`、姓名与 Token** 只做合同校验，不落库（snapshot 本期不做，S-13）。
14. **豁免、非人工检查等业务拒绝统一 400 `BUSINESS_CHECK_FAILED`**（合同草稿写 422 / 409），API 注释已同步；请求体字段校验为 422 `VALIDATION_ERROR`。
15. **工作台列表分页沿用原行为**（缺省 20，1—100），未改为公共 `PagingRequestBody` 档位，避免 ops-bff 现有调用变化。
16. **硬编码守门白名单**：`ClientReviewRegistrationBuilder` 的关系类型 → 依赖类型映射含种子值 `OWNERSHIP`（与材料要求代码同名）；目录外关系类型统一 `RELATED_PERSON_KYC`，ZZ 测试证明无需改代码。
17. **`CaseScreeningHandler` 以 `@Lazy` 注入 `ClientReviewService`**：Service 依赖补偿登记器、登记器收集全部 Handler，直接注入形成构造循环。
18. **P-8 清理以案件族为单位**：主案件 APPROVED 的整族保留（即使某子案件不是 APPROVED，避免留下断开的依赖；预检 0.3 列出这类子案件）；其余整族删除。资格只删 `case_id` 属于被删案件的决定及其角色资格；当前决定被删但还有保留决定的角色资格视为需人工处理，预检 0.5 非 0 即停止。compliance 不跨库读 client-master，ACTIVE Client 的资格靠"APPROVED 案件或 MANUAL 决定"保留，3.7 给出可选的只读交叉核对。
19. **旧程序不删除、置 RETIRED**：已批准旧案件需要回读冻结的程序版本；回填为全局程序（无辖区）后若保持 ACTIVE，会被任何没有专属程序的辖区解析到，故一律 RETIRED。旧案件辖区回填 `LEGACY`（与 client-master P-11 口径一致），档次 `STANDARD`。
20. **O14 程序目录**单次最多 200 个版本，超过返回 400 要求按辖区或状态过滤（材料模板查询上限 2001 行）；列出全部版本，含 DRAFT / RETIRED。CP-1 `ProgramResolutionResponseBody` 不变，目录体 `ClientReviewProgramResponseBody` 继承它并增加范围、状态与批准字段。

## 5. 未完成 / 需要他人完成

- **client-master（cm-dev）**：消费 `ClientReviewDecided` v2（schemaVersion 1、2 并存，outcome EXPIRED → 申请 WITHDRAWN）与 `QualificationChanged`；I01 改发 v2 字段（含 `fileAddress`、`previousCaseId`）；程序读取改用 `ProgramResolutionApi`；撤回登记 `CLIENT_REVIEW_WITHDRAWAL` 调 I03；CM-13 允许调用方 `compliance-service`。发布顺序必须是 client-master 先上线（S-02）。
- **client-console-bff / ops-bff**：`MaterialRequirementsApi` 已删除，改用 `ProgramResolutionApi`；ops-bff 适配 `ClientReviewEvidenceResponseBody.fileId → fileAddress`、新的退回请求体与工作台命令（见第 6 节）。
- **infra / channel-connector**：登记标准 API `SCREENING_SEARCH`（字段见第 6.4 节，已发给 infra-dev）、渠道 `S_SCREENING_MOCK`、调用授权 compliance-service × S_SCREENING_MOCK × SCREENING_SEARCH。
- **部署（阶段 6）**：停 compliance → mysqldump → 增量 DDL（保留行）→ `SET @approved_by=<运营账号>` 后执行初始化 DML（生产改 `@program_status='DRAFT'`）→ 清理 SQL（预检 0.4—0.6 必须为 0）→ 在 XXL 登记 `clientReviewReturnExpiryScan`（建议每 10 分钟、单实例路由）→ 可选环境变量 `COMPLIANCE_SCREENING_CHANNEL_CODE` → compliance 镜像。mor-test 需要 `onboarding.review.enabled=true` 与可达的 Mock 渠道，否则筛查停在失败 / 未知，批准被 `SCREENING_INCOMPLETE` 阻断。
- 未做：运营程序发布 / 编辑接口（R5 程序维护；本切片只提供只读目录 O14）、SettlementControl 实现、持续监控与复审触发（S3/S5）、自动 CRR（S4）。

## 6. 给前端 / BFF agent 的 API 说明

ID 一律十进制字符串，时间为 UTC 六位微秒 ISO-8601，日期 `YYYY-MM-DD`。工作台请求头：`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；私网调用头：`X-Caller-Service`。

### 6.1 CP-1 程序解析（client-master、client-bff）

`GET /compliance/v1/programs/resolve?jurisdiction=SA&partyType=ORGANIZATION|INDIVIDUAL&roleType=CLIENT|RELATED_PERSON[&tier=STANDARD][&responsiblePartnerId=]` → `ProgramResolutionResponseBody`：
`{programId, programCode, programVersionId, versionNo, qualificationType, jurisdictionCode(全局为 null), tier, ownerPartnerId, reviewIntervalMonths, fourEyesRequired, materials[{requirementCode, subjectRole, documentTypes[], required, maxAgeDays, waivable}], checks[{checkCode, checkType, checkMode, subjectRole, blocking, validityDays}], screenings[{screeningType, subjectRole, required, monitoringRequired}]}`。未知 Query 400；无程序或歧义 503；响应 `no-store`。

### 6.2 I01 / I03 / C05（client-master、client-bff）

- I01 `POST /client-onboarding-review/v1/submissions` 体：`{sourceSubmissionId, applicationId, applicationNo, clientId, clientType, jurisdictionCode, organizationTypeCode?, responsiblePartnerId?, previousCaseId?, requestedByAccountId, submittedAt, business{businessModelCode, websiteUrl?, businessDescription, expectedVolume, expectedTicket, currency}, subjects[{partyId, profileId, subjectRole PRIMARY|RELATED_PERSON, qualificationType, givenName?, middleName?, familyName?, birthDate?, nationalityCountry?, residenceCountry?, legalName?, tradeName?, incorporationCountry?, organizationType?, incorporationDate?, identifierType, issuingCountry?, identifierVaultToken?, identifierMasked?, identityKeyId, validFrom?, validTo?}], relations[{relationId, relatedPartyId, relationType, ownershipPercentage?, uboBasis?, jobTitle?, effectiveFrom, effectiveTo?}], documents[{documentId, subjectPartyId, requirementCode, documentType, fileAddress, contentHash, issueDate?, expiryDate?}], attributes?[{subjectPartyId, attributeCode, attributeValue}], declarations?[{declarationType, declaredValue, declaredAt}]}` → `{sourceSubmissionId, caseId}`。
- I03 `POST /client-onboarding-review/v1/cases/{caseId}/withdrawal` 体 `{applicationId, requestedByAccountId?}` → 命令结果。
- C05 `GET /client-onboarding-review/v1/applications/{applicationId}/result` → `{applicationId, caseId, status, outcome(RETURNED|APPROVED|REJECTED|WITHDRAWN|EXPIRED|null), roundNo, returnSection?, publicNote?, errors[{requirementKey, subjectPartyId, code, reason, remediation, dueTime}], dueTime?, decidedAt?}`。

### 6.3 运营工作台（ops-bff，`/client-onboarding-admin/v1`）

| 方法 路径 | 请求体 |
|---|---|
| GET `/cases?page&size&status&qualificationType&applicationId&responsiblePartnerId(0=平台自营)&jurisdictionCode` | — |
| GET `/cases/{caseId}` | — |
| POST `/cases/{caseId}/evidence/{evidenceId}/review` | `{outcome ACCEPTED|REJECTED, rejectReasonCode?(REJECTED 必填), note?, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/requirements/{requirementId}/waiver` | `{reasonCode, note, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/checks/{checkId}/result` | `{outcome PASS|FAIL, reasonCode, evidenceRef?, note?, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/screenings/run` | `{onBehalfOfPartnerId?}`（响应 processing=true） |
| POST `/cases/{caseId}/screening-hits/{hitId}/resolution` | `{resolution NO_MATCH|FALSE_POSITIVE|POTENTIAL_MATCH|TRUE_POSITIVE_ACCEPT|TRUE_POSITIVE_REJECT|INSUFFICIENT_EVIDENCE, reasonCode, note?, onBehalfOfPartnerId?}` |
| PUT `/cases/{caseId}/risk-rating` | `{riskRating LOW|MEDIUM|HIGH, factors[{factorCode COUNTRY_RISK|NATIONALITY_RISK|PEP|INDUSTRY_RISK|VOLUME|ENTITY_STRUCTURE|ADVERSE_MEDIA|PRIOR_REJECTION|MANUAL, factorValue}](≥1), note, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/recommendation` | `{recommendation APPROVE|REJECT, note, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/return` | `{reasonCode, errors[{requirementKey, subjectPartyId?, code, reason?, remediation}](1—100), dueDays(1—90), section?, publicNote, onBehalfOfPartnerId?}` |
| POST `/cases/{caseId}/approval`、`/rejection` | `{reasonCode, reason, publicNote?, onBehalfOfPartnerId?}` |
| GET `/programs?jurisdictionCode&status(DRAFT\|ACTIVE\|RETIRED)` | —（O14 只读程序目录） |

命令响应 `{caseId, status, accepted, processing}`。错误：已结束 409 `CLIENT_REVIEW_CLOSED`；门禁 / 四眼 422 `CLIENT_REVIEW_NOT_READY`（detail 列未满足项）；证据已核验 409 `CLIENT_REVIEW_EVIDENCE_REVIEWED`；检查已录入 409 `CLIENT_REVIEW_CHECK_RECORDED`；命中已关闭 409 `CLIENT_REVIEW_HIT_CLOSED`；业务拒绝 400 `BUSINESS_CHECK_FAILED`；字段校验 422 `VALIDATION_ERROR`。

详情 `ClientReviewCaseResponseBody`：`caseId, caseNo, caseType, applicationId, clientId, sourceSubmissionId, qualificationType, jurisdictionCode, programVersionId, tier, roundNo, previousCaseId, responsiblePartnerId, status, version, submittedAt, decidedAt, reviewDueTime, riskRating, recommendation, publicNote, returnSection, fourEyesRequired, riskRatingNote, riskRatedBy, riskRatedAt, riskFactors[{factorCode, factorValue, source, recordedBy, recordedAt}], recommendedBy, recommendedAt, recommendationNote, operatedBy, onBehalfOfPartnerId, returnErrors[同 C05 errors], subjects[{caseId, caseStatus, partyId, profileId, roleType, qualificationType, uboBasis, jobTitle, dependencyTypes[], requirements[{requirementId, requirementCode, documentTypes[], required, waivable, maxAgeDays, status, waivedBy, waivedReasonCode, waivedAt, reusedFromCaseId, evidence[{evidenceId, fileAddress, contentHash, documentType, issueDate, expiryDate, status, rejectReasonCode, reviewerId, reviewedAt, reusedFromCaseId}]}], checks[{checkId, checkCode, checkType, checkMode, blocking, required, status, outcome, reasonCode, evidenceRef, note, reviewerId, reviewedAt, validUntil, reusedFromCaseId}], screenings[{runId, screeningType, providerCode, status, triggerSource, startedAt, completedAt, hits[{hitId, externalHitRef, entityType, matchType, matchScore, riskLevel, listTypes[], status, resolutions[{resolutionVersion, resolution, reasonCode, note, reviewerId, resolvedAt, onBehalfOfPartnerId}]}]}]}], classifications[{industryCode, mccCode, primary}], timeline[{caseId, fromStatus, toStatus, actionType, reasonCode, actorId, operatedBy, onBehalfOfPartnerId, occurredAt}], approvalBlockers[]`。列表项只填摘要字段，集合为空数组。门禁代码：`CASE_NOT_ACTIVE、CHILD_CASE_NOT_ACTIVE、EVIDENCE_PENDING、REQUIRED_MATERIAL_NOT_ACCEPTED、CLASSIFICATION_NOT_RECEIVED、CHECK_NOT_PASSED、SCREENING_INCOMPLETE、SCREENING_HIT_OPEN、SCREENING_TRUE_MATCH、RISK_RATING_MISSING、RECOMMENDATION_MISSING、RECOMMENDATION_NOT_APPROVE、FOUR_EYES_SAME_PERSON`（按查看人计算）。

O14 响应 `ClientReviewProgramListResponseBody{items[ClientReviewProgramResponseBody]}`：CP-1 全部字段 + `partyType, roleType, programStatus, versionStatus, approvedBy, approvedTime`；按辖区（全局在前）、程序编码、版本号倒序；超过 200 个版本 400。

### 6.4 事件与出站

- `ClientReviewDecided` v2：v1 字段 + `caseType, roundNo, tier, riskRating, dueTime, errors[{requirementKey, subjectPartyId, code, reason, remediation}]`；outcome `RETURNED|APPROVED|REJECTED|EXPIRED`。
- `QualificationChanged` v1：`qualificationId, qualificationVersion, roleType, roleId, qualificationType, previousStatus, status, decisionId, decisionSource, caseId, validFrom, validTo, tier, riskRating, jurisdictionCode, responsiblePartnerId, reasonCode, changedAt`；aggregate `ROLE_QUALIFICATION`。
- `SCREENING_SEARCH` 输入 `{subjectType PERSON|ORGANIZATION, fullName, birthYear?, countries[], screeningTypes[1], clientRef}`；读取输出 `{runRef?, hits[{externalRef, entityType, listTypes[], score, matchTypes[], riskLevel?}]}`。

## 7. 本地环境说明

- worktree 的 Maven 库是 `~/.m3-onboard/repository`（`.mvn/maven.config`），不与主工作区 `~/.m3` 共享；此前发给 main 的"共享 ~/.m3"提醒不成立。
- 运行 `-am install` 时会一并安装 worktree 内 cm-dev 进行中的 `paytech-client-master-api`（本次编译通过）。

## 8. 已答复的协作问题（06:xx）

- **ops-fe**：门禁代码与含义、闭合值集合、`fileAddress` 格式保证、子案件 ID 一律走主案件路径、命令体必填项、`onBehalfOfPartnerId` 由 ops-bff 校验、O14 程序目录。
- **client-fe**：CP-4 类名与字段（无 `caseStatus`，`returnSection` 保留一个版本，`reason` 为客户可见自由文本）；BFF 停止调用材料接口，改由 client-master CM-2 经 CP-1 合并；不提供客户时间线接口。
- **cm-dev**：worktree 内 `ClientReviewRegistrationClientImpl` 已按现行 I01 v2 类构造请求（`attributeValue`、`declaredAt`、证件 `validFrom/validTo`、材料 `issueDate/expiryDate`，不发 `identifierValue / roundNo / schemaId`），无需改 API；I03 已终态 409 `CLIENT_REVIEW_CLOSED`，已撤回重放 200。
- **infra-dev**：SQL 文件与执行顺序、XXL 任务参数、`SCREENING_SEARCH` 合同兼容（`birthYear` 字符串、`score` 字符串或数字均可、Mock FAIL 的 `errorCode` 被忽略）。
