# cm-dev 交付记录：client-master 切片 1（全球合规入驻与资格）

> 范围：technique.md §2.1 CM-1—CM-13、§2.3、§2.4、§3；requirement.md R1—R7、R9、AC-02/03/04/11；design.md §6.1、8、9、10.0—10.1。
> worktree `Code-MoR-onboard`（基于 `ea0272f3`）。未提交、未推送、未连接 mor-test、未改其他系统。
> 状态：**代码、模型、SQL、国家包、测试全部完成；client-master 141 个测试全部通过。** 发布 SQL（PENDING 清理、遗留回填与脱敏、增量国家包、schema_id 可空）由 cm-sql 负责，不在本记录内。

## 1. 改动文件

只改 `paytech/client-master/**`、`table-ownership.yaml` 的 client-master 段，以及本需求 `db/` 下的两份文案 JSON。共 113 个修改、15 个删除、211 个新增（含测试）。

### 1.1 数据库与国家包

| 文件 | 说明 |
|---|---|
| `paytech/client-master/docs/database/sql/client-master-schema.sql` | 45 张表（含 messaging 两表），新增 13 张，改 11 张 |
| `…/sql/changes/2026-10-27-onboarding-global-reset.sql` | 清空 Client 入驻数据，保留 OE / Partner |
| `…/sql/changes/2026-10-27-onboarding-global.sql` | 增量 DDL；验证 旧 Schema + 重置 + 增量 = 新 Schema（逐表一致） |
| `…/sql/client-master-initial-data.sql` | AE / SA / EG 国家包：3 辖区、20 组织类型、8 标识类型、79 规则开关、5 关系类型、27 地址组件、109 目录项、10 属性定义、8 个 Schema、275 个字段 |
| `…/docs/database/client-master.pdma.json` | 直接同步 45 个实体（不经生成器） |
| `…/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md` | 顶部新增本切片物理决策 |
| `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` | client-master 段 30 → 43 张 |
| `requirements/…/db/onboardingSchema.en.json`、`onboardingSchema.zh-CN.json` | 国家包全部 276 个文案键 |
| `paytech/client-master/compensation-event-catalog.yaml` | 新增 `CLIENT_REVIEW_WITHDRAWAL` |
| `…/paytech-client-master-test/src/test/resources/init/ddl.sql` | 与正式 Schema 同步 |

### 1.2 Java（api / core / app）

- **新 API 包**：`com.paytech.client.master.api.onboarding`（CM-1/2/6/7/8）与 `…api.onboardingadmin`（CM-10/11/12），以及 `…api.enums` 的需求清单与错误码枚举。
- **国家包领域**：`domain/onboarding/pack/`（`OnboardingPack`、`RuleSettings`、`IdentifierRules`），`domain/onboarding/requirement/`（`OnboardingRequirementEvaluator`、`RelationRules`）。提交、提交核对与需求清单三处共用这一个计算。
- **草稿准备**：`ClientDraftPreparer` 负责标识规范化、格式与校验位检查、Vault Protect、属性、地址格式和组织类型切换 Schema。`ClientIdentifierVaultClientImpl` 使用 LEGAL_IDENTIFIER_V1、STABLE 模式，用途为 CLIENT_ONBOARDING_IDENTIFIER。
- **compliance 接入**：`ComplianceProgramClientImpl` 调用程序解析接口，取代 `MaterialRequirementsApi`。I01 改为 v2 组装，并新增 I03 撤回。
- **新用例**：开始申请（辖区、Schema、入驻码、责任方）、声明、需求清单、撤回及其补偿任务 `ClientReviewWithdrawalHandler`、表单规格、辖区列表。运营侧有目录、Schema、Client 列表与详情、入驻码签发与作废。
- **消息**：`ClientReviewDecided` 同时兼容 v1 与 v2（读取 dueTime，EXPIRED 落为 WITHDRAWN）。新增 `QualificationChangedListener`，CLIENT 资格撤销时把 Client 置为 SUSPENDED。
- **GAP-PROFILE 修复**：批准时写入资料 content_hash，清空 draft_application_id，current_slot 置 1。
- **CM-13 party-profiles**：增加 partyType、givenName、middleName、familyName、birthDate、nationalityCountry、residenceCountry、legalName、incorporationCountry，允许 compliance-service 调用。
- **申请读回（C05）**：新增 responsiblePartnerId、responsiblePartnerName（责任 PayFac 法定名称）与 declarations（每种声明的最新一条），应 client-fe 要求。
- **删除**：form-options 接口及其目录配置、`ClientSubmissionValidator`、`PartyRelationType` 枚举。行业目录改为读取国家包的 INDUSTRY 目录。
- **本轮修正的循环依赖**：撤回 Handler 原先依赖 `ClientApplicationCommandService`，后者依赖补偿登记器，形成循环。现在 Handler 改为从 `ClientReviewRegistrationService` 读取撤回事实，与审核登记 Handler 一致。

### 1.3 测试（`paytech-client-master-test`）

- **新增 Mock**：`MockComplianceProgramApi`（程序解析）和 `MockVaultController` + `VaultMocker`。Vault Mock 的 STABLE Token 等于 `数据类型:值` 的 SHA-256（base64url），不保存原文。`MockRiskReviewRegistrationApi` 增加 I03 撤回。删除 `MockRiskOnboardingApi`。
- **新增夹具**：`JurisdictionPackMocker` 每例直接读取正式初始化 DML 装入 AE / SA / EG 包。`fixtures/client-master/zz-pack.sql` 是只用数据定义的测试辖区 ZZ。`OnboardingFixtures` 集中放夹具取值。
- **夹具重写**：`OnboardingTestMapper` 与 `OnboardingApplicationMocker` 改按 AE 包生成完整可提交的申请，包括 Vault Token 证件、属性、声明、辖区与 Schema，并新增 `staffedPerson`。`ClientMasterDataCleaner` 增加 13 张表。
- **新增测试类与用例**：关联人员缺身份证明的提交核对（`SubmitClientApplicationTest.test_relatedPersonMaterialRequired`），遗留形状 Client 的列表与详情读取（`OnboardingAdminQueryTest.test_legacyClientReadable`，辖区 LEGACY、schema_id 为空、证件已脱敏），以及 `OnboardingFormSchemaTest`、`DraftIdentifierVaultTest`、`OnboardingCodeTest`、`WithdrawApplicationTest`、`NewJurisdictionByDataTest`（AC-11）、`ClientReviewLifecycleV2Test`、`OnboardingAdminQueryTest`、`JurisdictionLiteralAutoTest`。
- **改写的既有测试**：`SubmitClientApplicationTest` 新增个人与组织规则矩阵，以及重提、重新申请、分类等相关测试。
- **修正的竞态**：`ClientReviewResultListenerTest.test_replayedEventSkipped` 原先在首次消费计数落定前就取基线，现在先等计数落定。

## 2. 本地数据库

- **备份**：先执行 `mysqldump`，备份到 `~/Code-MoR-backups/20261027-onboarding-local/mor_client_master-before-20261027.sql`。备份前该库各表均为 0 行。
- **重建**：`DROP DATABASE mor_client_master` 后执行新的测试 `init/ddl.sql`，得到 45 张表。cm-sql 把 `schema_id` 改为可空后又按同一方式重建一次（库内无数据）。
- **Maven**：全部命令使用 worktree 的 `.mvn/maven.config`（`~/.m3-onboard/repository`）。没有向 `~/.m3` 安装任何模块。`paytech-client-master-api` 已安装到 `~/.m3-onboard`，供 client-fe、ops-fe 与 cmp-dev 编译。
- **清理**：校验用的临时库 `zz_cmdev_old` 与 `zz_cmdev_new` 已删除。

## 3. 测试命令与结果

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
# 需要导出 .env.local 中的 MOR_REDIS_PASSWORD、PAYTECH_REDIS_PASSWORD、MOR_RABBITMQ_*、MOR_CLIENT_MASTER_DB_PASSWORD
cd paytech
./mvnw -B -o -pl client-master/paytech-client-master-test -am verify \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

worktree 里没有 `local-environment/.env.local`，messaging 测试会因此找不到本地 RabbitMQ。所以要用 `-Dpaytech.local-environment.dir` 指向主仓库。

最后一次全量结果（加 `-Dmaven.test.failure.ignore=true` 以跑完全部模块，用时 2 分 53 秒）：

| 模块 | 结果 |
|---|---|
| Client Master Test | 141 run / 0 failures / 0 errors，共 33 个测试类（最后一次为 `-Dtest='com.paytech.client.master.**'` 范围运行） |
| Messaging | 73 / 0 / 0 |
| Vault | 47 / 0 / 0 |
| Redis Starter | 122 / 0 / 1 |
| Architecture Rules | 68 / 1 / 0 |
| 其余 foundation 模块 | 全部通过 |

两个模块失败，都不在我的范围内。

1. **Redis Starter**：`ResourceLockRedisIntegrationAutoTest.sortsMultipleDeclarations…` 超过了 400ms 的 awaitility 等待。单独重跑通过（8/8），属于共享 Redis 下的时序抖动，与本切片无关。
2. **Architecture Rules**：`TableOwnershipAutoTest.test_normalFlow` 期望 375 张表，实际 392 张。差额 17 张由两部分组成：本切片 client-master 新增 13 张，cmp-dev 的 compliance 段新增 4 张。修正方法是在 `paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java:26` 的求和里加 `+ 13 + 4`，并补注释。该文件不在 cm-dev 或 cmp-dev 的授权范围内，需由 main 决定谁改。

中途 messaging 的 `YamlEventCatalogAutoTest` 曾因 cmp-dev 在 `events.yaml` 新增 `QualificationChanged` 而失败。我已通知 cmp-dev，最后一次全量运行时该测试已通过。

## 4. 偏差与保守取舍

1. **一份资料一个主证件**。AE 的 TRN、SA 的 VAT 号、短地址和 EG 税卡号作为扩展属性采集，不进身份键。证件二选一由 Schema 的 `requirement_group` 表达，例如 PERSON_ID 为身份证、Iqama 或护照三选一。
2. **未预置 MANAGER 关系类型**。AE 的 LLC 使用 `REQUIRES_DIRECTORS`，DIRECTOR 关系类型标签为 Director/Manager。规则开关 `REQUIRES_MANAGER` 和规则角色 MANAGER 已经实现，ZZ 测试用自有关系类型 ZZ_MANAGER 证明可以只用数据配置。
3. **新增规则 `PARTY_TYPE`**（作用于组织类型）。它区分个人经营形态，例如 AE 的 SOLE_ESTABLISHMENT 属于 INDIVIDUAL 申请。
4. **未做的设计项**：材料表的 `document_number_masked`；OE 与 Local Partner 资料的 `jurisdiction_code` 保持为空；Partner 与 OE 申请不挂声明。
5. **422 与 409 的编码方式**。正式提交被拒时使用 Problem 的 `errors[{field, message}]`：主主体的 field 是需求键，关联人员的 field 是 `people[<partyId>].<需求键>`，message 为 `<错误码>/<补救动作>`。提交核对与需求清单返回带类型的结构。
6. **form-schema 的参数**。technique 原文写的是按责任 Partner 解析，实现改为传 `applicationId`。这样服务端读取申请冻结的 Schema 与责任方，前端不需要也不能自报 Partner。
7. **资格撤销挂起的操作人**。QualificationChanged 不带操作人，挂起历史沿用上一版的 Actor，触发 ID 为资格决定 ID。
8. **证件校验位**。阿联酋身份证的校验位算法未经确认，配置为 NONE，只校验 `784` 加 12 位数字。埃及身份证（EG_NID）只校验结构，即世纪位与出生日期，不校验末位。
9. **I01 只发 Token 与脱敏值**。compliance 合同没有公开登记号字段，所以营业执照号、商业登记号这类明文不发给 compliance，只发脱敏值和 identityKeyId。
10. **国家包编写规则**。I01 合同要求经营资料五项非空：businessModelCode、businessDescription、currency、expectedVolume、expectedTicket。因此每个 Schema 都必须把这五项设为 REQUIRED，否则提交后登记会失败。ZZ 测试最初漏了这五项，就是这样暴露的。AE / SA / EG 已满足。服务端目前没有启动时的 Schema 完整性检查，列为后续项。
11. **改证件类型会清空号码**。服务端不回显、也不 Reveal 旧号码，用户需要重新填写。
12. **入驻码只在签发时返回明文**。列表与作废结果只返回 `codeMasked`（`****-` 加末 4 位），与 ops-fe 的 Web 校验一致。库内仍存明文，因为开始申请要按码查找。
13. **退回与过期**。退回的补件期限存在 `t_client_application.review_due_time`。超期 EXPIRED 只接受当前为 RETURNED 的申请，把它置为 WITHDRAWN 并释放名额，不再调用 compliance。

## 5. 未完成与跨团队事项

- **arch-rules 表数断言**：需按第 3 节第 2 项补 `+ 13 + 4`，等 main 指派。
- **C05 读回（client-fe 提出）**：已完成。`ClientDraftResponseBody` 末尾追加 responsiblePartnerId、responsiblePartnerName、declarations 三个组件，record 位置构造的调用方需要补参数，已通知 client-fe。
- **关系人材料槽（client-fe、main 提出）**：已完成。form-schema 合并 RELATED_PERSON 程序，compliance 程序标为 PRIMARY 的材料改为 `ANY_RELATED`，其他角色代码原样保留。需求清单与提交使用同一合并结果，由 `OnboardingFormSchemaTest.test_aeOrganizationDefault` 与 `SubmitClientApplicationTest.test_relatedPersonMaterialRequired` 证明。
- **ops-fe 的 CM-10/11/12**：已把真实类名与字段发给 ops-fe，由其替换本地镜像合同。字段名与 ops-fe 提议不同（列表不包 items、Schema 字段用 displayGroup / widgetCode / appliesToSubject / displayOrder、详情按分区 values 给出），差异由 ops-fe 适配。catalog-items 暂不支持按辖区过滤，入驻码列表不分页、不按状态过滤。
- **遗留数据兼容（cm-sql 的 P-11 核对项）**：遗留申请 schema_id 为空时，`OnboardingPackService.load` 不再拆箱出错，而是返回 409 `APPLICATION_NOT_EDITABLE`，覆盖表单规格、提交核对与草稿写入；需求清单（CM-7）直接返回 200、`ready=false`、空清单，不调用 compliance 与 Platform。两类事件消费（ClientReviewDecided、QualificationChanged）不读取 schema_id，对遗留 Client 无影响。申请资料读取（C05）与运营列表、详情照常返回，辖区 `LEGACY`、证件只剩掩码。由 `LegacyApplicationReadTest` 与 `OnboardingAdminQueryTest.test_legacyClientReadable` 证明。辖区 `LEGACY` 只出现在已批准的遗留申请上，这些路径都不按它装载国家包。
- **测试 Vault Mock 支持 Reveal**：联系方式读取会 Reveal，Mock 现在只回显本例配置的 Token；夹具联系方式改为合法的 43 位稳定 Token（原 `vt_email_…` 会被 Vault 客户端拒绝）。
- **AC-07 / B3 本地验收失败的修复**：原先 CM-7 只列本地能算出的问题，审核员的判断（如银行函件不可读）不在其中，退回后清单没有带期限的项。现在退回中的申请在 CM-7 读取 compliance CP-4 结果（`ClientReviewResultClient`，调用方 client-master-service，不新增表），审核员的需求级错误覆盖同键同主体的本地项：awaitingActionFrom=CLIENT、带 dueTime，过期为 PAST_DUE，直到重新提交。compliance 的 subjectPartyId 为空表示申请级错误，归到申请主主体。这些项不改变 `ready`，客户处理后仍按本地计算决定能否重提。由 `ClientReviewLifecycleV2Test.test_returnedErrorsInRequirements`（从 RETURNED 事件入口开始）证明。
- **mor-test P0：地址任务无法保存（冻结期例外，只改此项）**：页面随地址组件提交 `address.addressFormatCode`，`ClientDraftPatch.FIELDS` 没有该路径，整个 PATCH（含同批联系方式）以 422 `dirtyFields 包含不支持的字段` 被拒。已加入该路径：取值须是本申请的辖区本地格式或 GENERIC，否则 422 指向 `address.addressFormatCode`；落库值始终由服务端按地址国家推导（境外地址存 GENERIC）。对照页面全部草稿路径（Schema 字段键、地址组件、contact、identifier.*、attributes.*）与 FIELDS，没有其他缺口。格式码可选：省略时同样保存，落库值按地址国家推导。人员新建（POST people）与修改（PATCH people/{partyId}）走同一校验与准备步骤，同一修复生效。`DraftAddressFormatTest` 以页面原样体覆盖：SA 国家地址 + 联系方式（带格式码与不带）、AE PO Box（带与不带）、境外地址存 GENERIC、非法格式码 422，以及人员新建（带 / 不带格式码）、人员修改（改为境外、只改一个组件）与非法格式码不建人员。
- **mor-test P0 #2：组织申请无法添加人员（冻结期例外，只改此项）**：线上页面的关系体只有 relationType、ownershipPercentage、uboBasis，`ClientRelationValues` 要求 effectiveFrom，以不带 errors[] 的 INVALID_DRAFT 拒绝。已修复：effectiveFrom 未提供时取当日（UTC），提供时照常校验；人员与关系的所有校验失败都返回 errors[]，路径为 relation.<字段>（结构问题为 relation、dirtyFields、values，缺人员体为 person）。对照页面 PeopleTask 的 relationBody、人员体与角色 PATCH：没有其他缺口；关联人员 Schema 字段都在人员接口允许的 individual / address / identifier / attributes 分区内。`PeopleWebBodyTest` 以页面原样体覆盖 AE 受益人 55%、英国地址董事、/relations 追加签字人、修改持股，以及四类字段级错误。
- **client-fe 提出、本期不做（main 决定，冻结期）**：C05 追加 submittedAt（取 submitted_time）与 withdrawnAt（WITHDRAWN 时取 update_time）；已记为后续项，页面暂不显示这两行。
- **ops-fe 提出、本期不做**：入驻码作废不带原因（表无原因列，需 DDL）；catalog-items 按辖区过滤与入驻码按状态过滤留在 ops-bff；Client 详情不返回 activatedAt / suspendedAt。
- **发布 SQL**：PENDING 清理、遗留数据回填与脱敏、增量国家包由 cm-sql 负责。Java 侧已兼容遗留形状（schema_id 为空、辖区 LEGACY、证件只剩掩码），有自动测试覆盖。
- **待法务确认**：初始化 DML 中国家包的部分行需要法务确认（design §9.5a，共 26 项）。生产执行前要把未确认的行改为 DRAFT，DML 文件头已写明。
- **未执行**：mor-test 的 SQL 与部署，git 提交。

## 6. API 说明（供前端与 BFF）

除特别说明外，ID 一律为十进制字符串，时间为 UTC ISO-8601（微秒），日期为 `YYYY-MM-DD`。Client 通道统一请求头：`X-Caller-Service: client-console-bff`、`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`、`X-Request-Id`。Operations 通道请求头：`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`。

### 6.1 Client 通道

**CM-1 GET `/client-onboarding/v1/jurisdictions`**：返回 `[{jurisdictionCode, countryCode, labelKey, acceptancePolicy, acceptanceReasonKey}]`，按代码排序，只含 ACTIVE 且准入策略不是 CLOSED 的辖区。

**CM-2 GET `/client-onboarding/v1/form-schema`**，参数二选一：
- 传 `applicationId`：返回该申请冻结的 Schema 及按其责任方解析的材料，忽略其他参数。
- 否则 `jurisdiction` 与 `partyType`（INDIVIDUAL / ORGANIZATION）必填，`organizationType` 可选。有该组织类型的专用 Schema 时返回专用版本，否则返回 `*` 默认版本。

响应 `FormSchemaResponseBody` 的字段：
- `schemaId`、`versionNo`、`jurisdictionCode`、`partyType`。
- `organizationTypeCode`：`*` 或具体类型代码。
- `sections[]`：每项为 `{section, labelKey, groups[]}`，分区为 BASIC / BUSINESS / PEOPLE / MATERIALS / DECLARATIONS。`groups[]` 每项为 `{group, labelKey, fields[]}`。
- `fields[]` 每项包含：`fieldKey`、`widget`、`catalogCode`、`requirementLevel`、`requirementGroup`、`requirementGroupLabelKey`、`appliesTo`、`validation`、`visibleWhen`、`labelKey`、`helpKey`、`order`。
  - `requirementLevel` 取 REQUIRED / EVENTUALLY / OPTIONAL。
  - `appliesTo` 取 PRIMARY / RELATED_PERSON / BOTH。
  - `validation` 为 `{pattern, minLength, maxLength, checksumRule}`。
  - `visibleWhen` 形如 `field_key=value`。
- `fieldKey` 等于草稿 values 的 `分区.字段` 路径。三类特殊字段键：`identifier.<标识类型>` 表示所选证件类型等于该类型时满足；`declaration.<类型>` 表示声明；`address`、`people`、`documents` 是整块控件。
- `catalogs`：`{目录代码: [{code, labelKey}]}`，包含 SELECT 字段与地址组件用到的全部目录，组织类型以 `ORGANIZATION_TYPE` 出现。
- `organizationTypes[]`：`{code, labelKey}`，只含当前主体类型可选的类型。
- `identifierTypes[]`：每项为 `{code, partyType, labelKey, pattern, checksumRule, issuerScopeMode, expiryRequired, acquireMode}`。
  - `checksumRule` 取 NONE / LUHN / EG_NID。
  - `issuerScopeMode` 取 NONE / COUNTRY / AUTHORITY；COUNTRY 必须填签发国，AUTHORITY 按主主体的登记机关确定作用域。
  - `acquireMode` 本期只有 MANUAL_ENTRY。
- `addressFormat`：用于地址国家等于辖区国家时，为 `{code, components[]}`，每个组件为 `{component, required, catalogCode, labelKey, order}`。`required` 已叠加 POSTAL_CODE_REQUIRED 规则。
- `fallbackAddressFormat`：境外地址使用的 GENERIC 格式。
- `relationTypes[]`：`{code, ruleRole, uboFlag, ownershipPctRequired, labelKey}`。
- `relationRules`：个人申请为 null。组织申请为 `{uboThresholdPct, uboFallbackRule, controlPersonRequired, minSignatories, requiresManager, requiresDirectors, uboCollectionMode}`，其中 `uboThresholdPct` 是字符串，`uboCollectionMode` 取 OWNERS_AND_CONTROL / CONTROL_ONLY / EXEMPT。
- `materials[]`：`{requirementCode, subjectRole, documentTypes[], required, maxAgeDays}`。主主体材料的 `subjectRole` 为 PRIMARY，组织申请的关联人员材料为 ANY_RELATED。
- `declarations[]`：`{declarationType, labelKey, requirementLevel}`。

CM-2 的错误：
- 422 `JURISDICTION_NOT_ACCEPTED`：辖区不存在、未启用、准入为 CLOSED 或没有 Schema。
- 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`：compliance 不可用。

**CM-3 / C01 POST `/client-onboarding/v1/applications`**：
- 请求体：`{clientType, jurisdictionCode, organizationTypeCode?, onboardingCode?}`。
- 返回 201（新建）或 200（账号已有未完成申请，返回原申请）：`{commandId, applicationId, applicationNo, clientId, clientType, operation, completedAt}`。
- 错误：
  - 422 `JURISDICTION_NOT_ACCEPTED`。
  - 422 `ONBOARDING_CODE_INVALID`：入驻码不存在、作废、过期、未生效、次数用尽或 Partner 不可用，一律返回此码。入驻码不区分大小写。
  - 422 `INVALID_DRAFT`：clientType 或组织类型不合法，`errors[].field` 指向对应字段。

**C04 PATCH `/applications/{id}/draft`**：
- 请求体：`{dirtyFields[], values{分区: {字段: 值}}}`。值为 null 表示清空。
- 分区为 individual、organization、address、identifier、business、contact、attributes。
- individual 新增 birthPlace、birthCountry、gender、sourceOfFundsCode、pepSelfDeclared（`"true"` / `"false"`）。
- organization 新增 registrationAuthorityCode、licenseExpiryDate、listedCompany；更换 organizationType 会切换 Schema。
- address 新增 buildingNumber、additionalNumber、district、poBox，以及 addressFormatCode（只接受辖区本地格式或 GENERIC；落库值由服务端按地址国家推导）。
- identifier 包含 identifierType、issuingCountry、identifierValue、issueDate、expiryDate。identifierValue 只写不读。
- attributes 的键是属性代码，值按属性定义转换。DATE 为 `YYYY-MM-DD`，COUNTRY 转大写，DECIMAL 为字符串。
- 错误：
  - 422 `INVALID_DRAFT` 一次列出全部字段问题，此时不调用 Vault，也不落库。典型问题有非法字符、类型不属于本辖区、属性不适用。
  - 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`：Vault 不可用。
- 证件号格式或校验位不对时不拒绝保存，而是记录检查结论，在需求清单中报告。

**C05 GET `/applications/{id}`**：
- 原有字段之外新增 `jurisdictionCode`、`organizationTypeCode`、`schemaId`、`reviewDueTime`、`responsiblePartnerId`（平台自营为空）、`responsiblePartnerName`（责任 PayFac 法定名称）、`declarations[]`（每种声明最新一条，字段同 CM-6 响应）。
- `values.identifier` 只给 identifierType、issuingCountry、`identifierMasked`（末 4 位）、`identifierCheckResult`（null、IDENTIFIER_FORMAT_INVALID 或 IDENTIFIER_CHECKSUM_FAILED）、issueDate、expiryDate。
- `values.attributes` 为属性代码到文本的映射。
- relations 增加 `uboBasis`（OWNERSHIP / CONTROL / SENIOR_MANAGING_OFFICIAL）、`jobTitle`、`nomineeFlag`、`controlNature`。
- documents 增加 `issueDate`、`expiryDate`。

**人员与关系**：
- `POST /applications/{id}/people` 请求体为 `{person: {dirtyFields, values}, relation: {relationType, ownershipPercentage?, effectiveFrom, effectiveTo?, uboBasis?, jobTitle?, nomineeFlag?, controlNature?}}`，返回 201。
- relationType 为国家包关系类型代码。只有 `ownershipPctRequired` 为 true 的类型可以填 ownershipPercentage，只有 `uboFlag` 为 true 的类型可以填 uboBasis。
- 关系补丁的字段集合与创建相同。effectiveFrom 可省略，省略时取当日（UTC）。校验失败返回 422 INVALID_DRAFT，errors[].field 为 relation.<字段>。

**材料**：`POST /applications/{id}/documents` 请求体为 `{subjectPartyId, requirementCode, documentType, fileAddress, issueDate?, expiryDate?}`，返回 201。

**CM-6 POST `/applications/{id}/declarations`**：
- 请求体：`{declarationType, declaredValue: "TRUE"|"FALSE", termsVersion?}`。
- 服务端记录 User-Agent 与 `X-Gateway-Client-Ip`。
- 返回 200：`{declarationId, applicationId, declarationType, declaredValue, termsVersion, declaredAt}`。
- 只追加。同类型、同取值、同条款版本重复提交返回原记录。
- 错误：非 DRAFT / RETURNED 状态返回 409 `APPLICATION_NOT_EDITABLE`；声明类型不在 Schema 中返回 422 `INVALID_DRAFT`。
- 重新申请时声明需要重新签署，因为声明属于申请而不属于 Client。

**CM-7 GET `/applications/{id}/requirements`**：
- 返回 `{applicationId, status, ready, dueTime, items[]}`。
- `items[]` 每项为 `{requirementKind, requirementKey, subjectPartyId, status, awaitingActionFrom, errorCode, remediationCode, dueTime}`。
- `requirementKind` 取 FIELD / IDENTIFIER / PERSON / RELATION / DOCUMENT / DECLARATION。
- `status` 取 CURRENTLY_DUE / EVENTUALLY_DUE / PAST_DUE / PENDING_VERIFICATION / SATISFIED / WAIVED。审核中的材料为 PENDING_VERIFICATION，等待 OPERATIONS。退回后未满足的项带 dueTime，超期为 PAST_DUE。
- `awaitingActionFrom` 取 CLIENT / OPERATIONS / SYSTEM。
- `requirementKey` 的取值：Schema 字段键；`group.<组>`（例如 `group.PERSON_ID`）；`address.<组件>`；`relation.<UBO|UBO_TOTAL|CONTROL|SIGNATORY|DIRECTOR|MANAGER>`；`people`；`document.<材料槽>`；`documents[<材料引用ID>]`（材料所属人员已被移除）；`declaration.<类型>`。
- 错误码见 `OnboardingRequirementCode`：FIELD_MISSING、FIELD_FORMAT_INVALID、FIELD_VALUE_NOT_IN_CATALOG、DOB_AGE_UNDER_MINIMUM、IDENTIFIER_FORMAT_INVALID、IDENTIFIER_CHECKSUM_FAILED、IDENTIFIER_EXPIRED、IDENTIFIER_CONFLICT、UBO_MISSING、UBO_OWNERSHIP_EXCEEDS_100、SIGNATORY_MISSING、DIRECTOR_MISSING、MANAGER_MISSING、CONTROL_PERSON_MISSING、RELATED_PERSON_INCOMPLETE、DOCUMENT_MISSING、DOCUMENT_EXPIRED、DOCUMENT_TOO_OLD、DOCUMENT_UNREADABLE、DOCUMENT_TYPE_INVALID、DOCUMENT_SUBJECT_REMOVED、DECLARATION_MISSING。
- 补救动作见 `OnboardingRemediationCode`：PROVIDE_FIELD、UPLOAD_DOCUMENT、ADD_PERSON、ADD_RELATION、SIGN_DECLARATION、CONTACT_SUPPORT、NONE。
- compliance 或 Platform 不可用时返回 503。
- 2026-10-27 之前的遗留申请没有采集 Schema，返回 200、`ready=false` 与空清单。
- 退回中的申请合并审核员的需求级错误：覆盖同键同主体的本地项，awaitingActionFrom=CLIENT，带 dueTime，过期为 PAST_DUE；errorCode 与 remediationCode 原样取自 compliance；不影响 ready。

**CM-8 POST `/applications/{id}/withdrawal`**：
- 无请求体，返回 200：`{applicationId, status: "WITHDRAWN", withdrawnAt}`。重复调用返回原结果。
- 只有 DRAFT 与 RETURNED 可以撤回，其他状态返回 409 `APPLICATION_NOT_WITHDRAWABLE`。
- 已立案的申请会由补偿任务调用 compliance I03 撤回案件。

**CM-9 GET `/applications/{id}/submission-check`**：
- 返回 `{ready, errors[]}`。
- `errors[]` 每项为 `{requirementKey, subjectPartyId, code, remediation, message}`。`message` 是 `code/remediation` 形式的过渡说明，前端不展示。

**C14 POST `/applications/{id}/submissions`**：
- 返回 202 `{submissionId, status: "SUBMITTED"}`；重复提交返回 200，内容为原提交。
- 422 `SUBMISSION_INCOMPLETE` 与 409 `IDENTITY_CONFLICT` 的 Problem 都带 `errors[{field, message}]`，编码方式见第 4 节第 5 项。409 不透露占用者。
- 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`。

### 6.2 内部（compliance 与 Checkout）

- **CM-13 GET `/client-master/v1/party-profiles`**：每项增加 `partyType`、`givenName`、`middleName`、`familyName`、`birthDate`、`nationalityCountry`、`residenceCountry`、`legalName`、`incorporationCountry`，允许 `X-Caller-Service: compliance-service` 调用。
- **I01 v2（client-master 发往 compliance）**：
  - 申请级新增 `jurisdictionCode`、`organizationTypeCode`、`responsiblePartnerId`、`previousCaseId`、`attributes[{subjectPartyId, attributeCode, attributeValue}]`、`declarations[{declarationType, declaredValue, declaredAt}]`。
  - subject 用 `identifierVaultToken`、`identifierMasked`、`validFrom`、`validTo` 取代证件号原文。`validTo` 等于到期日加 1 天，因为对方的 validTo 不含当日。
  - document 用 `fileAddress`、`issueDate`、`expiryDate`。
  - relation 的 `relationType` 为开放代码，并带 `uboBasis` 等字段。
- **消费的事件**：`ClientReviewDecided` v1 与 v2，v2 读取 dueTime，outcome 可为 EXPIRED。`QualificationChanged` v1 只处理 roleType 为 CLIENT 且 status 为 REVOKED 的事件，结果是 Client 置为 SUSPENDED、记一条生命周期历史、向 Platform 投影 SUSPENDED。

### 6.3 Operations 通道（`/client-master-admin/v1`，只读与入驻码）

**GET `/jurisdictions`**：返回 `[AdminJurisdictionResponseBody]`，每项包含：
- `jurisdictionCode`、`countryCode`、`labelKey`、`addressFormatCode`、`status`、`changeVersion`。
- `rules[]`：`{scopeType, scopeCode, ruleCode, ruleValue, status}`，含辖区级、组织类型级与标识类型级规则。
- `organizationTypes[]`：`{typeCode, labelKey, displayOrder, status}`。
- `identifierTypes[]`：`{identifierTypeCode, jurisdictionCode, partyType, labelKey, formatRegex, normalizationRule, checksumRule, issuerScopeMode, status}`，含全局类型。
- `relationTypes[]`：`{relationTypeCode, jurisdictionCode, ruleRole, uboFlag, ownershipPctRequired, labelKey, status}`。

**GET `/onboarding-schemas?jurisdiction=`**：返回 `[{schemaId, jurisdictionCode, partyType, roleType, organizationTypeCode, versionNo, status, approvedTime, fields[]}]`。每个字段为 `{section, displayGroup, fieldKey, widgetCode, catalogCode, requirementLevel, requirementGroup, appliesToSubject, validationPattern, minLength, maxLength, visibleWhen, labelKey, helpKey, displayOrder}`。

**GET `/catalog-items?catalogCode=`**：返回 `[{catalogCode, itemCode, jurisdictionCode, labelKey, clientTypes, displayOrder, status}]`，包含全部辖区，全局项的 jurisdictionCode 为 `*`。

**GET `/clients?status&jurisdiction&responsiblePartnerId&page&size`**：
- 返回 `{items, page, size, total}`，按 Client ID 倒序。size 取 10 / 20 / 50 / 100 / 200。
- `items[]` 每项为 `{clientId, clientNo, clientType, status, jurisdictionCode, responsiblePartnerId, legalName, latestApplicationId, latestApplicationStatus, createdAt}`。个人 Client 的 legalName 为姓名。

**GET `/clients/{clientId}`**：返回以下字段，Client 不存在时返回 404 `CLIENT_NOT_FOUND`。
- `client`：同列表项。
- `values`：同 C05 的分区结构，只含脱敏证件与脱敏联系方式。
- `people[]`：`{partyId, values}`。
- `relations[]`：`{relationId, relatedPartyId, relationType, ownershipPercentage, uboBasis, jobTitle, status}`。
- `applications[]`：`{applicationId, applicationNo, status, jurisdictionCode, organizationTypeCode, schemaId, responsiblePartnerId, submissionRound, dueDiligenceCaseId, qualificationDecisionId, submittedAt, updatedAt}`。

**GET `/local-partners/{id}/onboarding-codes`**：返回 `[OnboardingCodeResponseBody]`，新签发的在前。每项为 `{codeId, code, codeMasked, localPartnerId, status, effectiveFrom, effectiveTo, maxUses, useCount, note, issuedBy, issuedAt, revokedBy, revokedAt}`。
- `code`（明文 `XXXX-XXXX`）只在签发响应中有值，列表与作废结果为空；`codeMasked` 为 `****-` 加末 4 位。
- `status` 取 ACTIVE / REVOKED / EXPIRED。
- Partner 不存在时返回 404 `LOCAL_PARTNER_NOT_FOUND`。

**POST `/local-partners/{id}/onboarding-codes`**：
- 请求体：`{effectiveFrom?, effectiveTo?, maxUses?(1—100000), note?(≤255)}`。
- 返回 201 与新入驻码。
- Partner 不是 ACTIVE，或没有 ACTIVE 的 OE_PROVIDER 角色时，返回 422 `PARTNER_NOT_OE_PROVIDER`。

**POST `/local-partners/{id}/onboarding-codes/{codeId}/revocation`**：
- 返回 200 与当前入驻码。已作废或已过期时原样返回。
- 入驻码不存在或不属于该 Partner 时，返回 404 `ONBOARDING_CODE_NOT_FOUND`。

**既有 GET `/client-onboarding-admin/v1/classification-options`**：行业目录改为读取国家包的 INDUSTRY 目录，共 10 项，按展示顺序排列，每项只有 `code` 与 `labelKey`。
