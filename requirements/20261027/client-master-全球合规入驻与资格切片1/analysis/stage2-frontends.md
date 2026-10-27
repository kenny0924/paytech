# 阶段 2 核对笔记：前端 / BFF / Platform 注册 / 多语言（只读，main ea0272f3 + 未提交工作区）

对应 requirement.md 7.4（`requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md:118`）与 7.5（同文件 :127）。未改任何文件。未连接 mor-test，live mor_platform 核对留给主会话。

## 0. 结论速览（影响 7.4 / 7.5 修订）

1. client-web `/onboarding` 是单个 1702 行组件，字段集合写死在常量数组，标签 key 由字段路径推导；没有 Schema 渲染器、控件注册表、表单库（无 react-hook-form / antd），只用原生 input/select。R10 等于重写渲染层，不是增量改造。`client-console-web/src/OnboardingPage.tsx:53-96`
2. client-web 与 client-bff 对外路径是 `/api/client/onboarding/v1/...` → BFF `/client-onboarding/v1/...`（Gateway 改写前缀）。新增 jurisdictions / form-schema / declarations / requirements / withdrawal 都要新增 CLIENT 通道 Gateway 路由；现有 39 条 Client 入驻路由是 2026-10-14 用 Platform 管理 API 登记的（ID 由 Platform 生成），不在任何块里。块 138 若要加 Client 路由，target_id 与既有 CLIENT_ONBOARDING_* 权限 ID 只能按 code 子查询解析，不能像块 137 那样写死。`requirements/20260914/client-console-注册与客户入驻/registration-design.md:5`、`requirements/20260914/client-console-注册与客户入驻/db/registration-plan.json`
3. ops-bff / compliance / client-master 全仓库没有任何 on-behalf / Partner 代操作字段或头（grep `onBehalf|on_behalf` 0 命中）。Operations 身份只有 Gateway 头 `X-Gateway-Actor-Subject` + `X-Gateway-Session-Id`；`on_behalf_of_partner_id` 需新设计（请求体字段还是新头），且 Partner 门户（business-portal-web / -bff）是空目录。
4. 块 138：仓库内无任何 `900000000000138xxx` 行（initial-data 只到 137，`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 共 8272 行，末块 137 起于 :8071）；无其它需求声明 138。块 128 号段从未使用（"128 行" 是行数，不是块号）。live mor_platform 仍需核对 900000000000138001—138999 为空。
5. 多语言：决定写明"业务内容另立需求，不进入本模块"，且"本期没有各端共享的命名空间"。Schema `label_key` 来自 DB DML 而非代码，跟现行"key 与 en 基准来自代码、构建时内置"的模型冲突；`onboarding-schema` 要在 client-console 与 operations-console 两个应用各建一次命名空间，并决定 en 基准文件放在哪个仓库位置。见第 6 节。
6. 现存不一致：registration-plan 登记了 `client-onboarding-c15`（review-history）与 `client-onboarding-o06`（`/cases/{caseId}/checks`）两条路由，但 client-bff / ops-bff 都没有对应 Controller 方法（R6 人工检查若复用 o06 路由码需先核对 live 状态）。

## 1. client-console-web

### 1.1 技术栈与测试
- 依赖只有 react 19.2.8、react-router-dom 7.18.3、intl-messageformat 12.1.3；无组件库、无表单库、无 Playwright / vitest。`client-console-web/package.json:15-28`
- 测试：`node --experimental-strip-types --test src/*.test.ts src/i18n/*.test.ts`，只测纯函数（onboarding / submission / review-result / catalog / i18n 覆盖）。`client-console-web/package.json:10`
- 页面级验证靠需求目录的合成协议预览 `requirements/20260914/client-console-注册与客户入驻/verification/preview-onboarding.mjs`（+ `.test.mjs`），README 说明见 `client-console-web/README.md`（"入驻页面"节）。
- 文本输入组件 `src/TextField.tsx`（认证页用，入驻页的 `Fields` 未用它）。`client-console-web/src/TextField.tsx:1-15`
- Dev 端口 18302，Vite 把 `/api/client` 代理到 `CLIENT_GATEWAY_URL`。`client-console-web/vite.config.ts`

### 1.2 路由与文件结构
- 路由：`/onboarding` 与 `/onboarding/applications/:applicationId` 都挂 `OnboardingPage`。`client-console-web/src/main.tsx:138`
- 文件：`src/OnboardingPage.tsx`（1702 行，含 Fields / Profile / PersonEditor / RelationEditor / UploadEditor / SubmissionPanel / ReviewResultBanner / Modal）、`src/onboarding.ts`（409 行，类型、命令冻结、上传状态机、结果横幅）、`src/submission.ts`（65 行，C16 核对结果解析与问题定位）、`src/catalog.ts`（57 行，form-options 选项）、`src/enum-label.ts`。**没有 `review-result.ts`**，只有 `src/review-result.test.ts`（测 `onboarding.ts` 的 `resultBanner`）。

### 1.3 表单渲染（硬编码）
- 字段定义：`type Field = [path, kind?, maxLength?]`；五个常量数组 individual / organization / address / identifier / business（含 contact.email / contact.phone）。`client-console-web/src/OnboardingPage.tsx:53-94`
- 控件种类：text、date、country、issuer-country、catalog、url、currency、email、tel、textarea。`client-console-web/src/OnboardingPage.tsx:390-494`
- 标签 key 由路径推导：`individual.givenName` → `onboarding.field.individual.given_name`。`client-console-web/src/OnboardingPage.tsx:96-98`
- 国家列表写死 249 个 ISO 码，名称用 `Intl.DisplayNames`；币种用 `Intl.supportedValuesOf('currency')`。`client-console-web/src/OnboardingPage.tsx:103-121`、`:466`
- 分区固定 basic / business / people（仅 ORGANIZATION）/ materials，锚点导航。`client-console-web/src/OnboardingPage.tsx:1296-1301`、`:1407-1409`、`:1511-1512`
- 前端无字段级校验规则；保存时把服务端 422 Problem `errors[]` 按字段路径挂回控件（`fieldIssues`）。`client-console-web/src/onboarding.ts:155-163`
- 保存只提交脏字段（`patch` 对比 flatten 前后值，值为 `{group:{field:value|null}}`）。`client-console-web/src/onboarding.ts:145-174`
- 写命令冻结路径、Body 与幂等键，失败重试原命令。`client-console-web/src/onboarding.ts:175-197`、`client-console-web/src/OnboardingPage.tsx:1139-1175`

### 1.4 form-options 的消费
- `useFormCatalog` 调 `GET /onboarding/v1/form-options?clientType=`，校验 `catalogStatus`、`organizationTypes`、`businessModels`、`identifierTypes` 结构，失败显示 `catalog.partial` 并可重试。`client-console-web/src/OnboardingPage.tsx:318-389`
- 只有三个 catalog 字段：`organization.organizationType`、`business.businessModelCode`、`identifier.identifierType`；`identifierTypes[].issuingCountryMode`（REQUIRED / NONE）控制签发国。`client-console-web/src/catalog.ts:1-57`
- 旧值不在目录中时显示 disabled 的 legacy 选项，不清空。`client-console-web/src/OnboardingPage.tsx:425-432`
- 材料要求 `requirements` 也随 form-options 返回（BFF 合并 compliance）。`client-console-web/src/catalog.ts:5-13`
- 7.1 删除 `form-options` 接口 → 这三处与 `FormCatalog` context 全部要换成 form-schema。

### 1.5 材料上传（文件服务直传 + 地址提交）
- 状态机 PUT → COMPLETE → SCAN → ASSOCIATE → DONE：`POST /onboarding-files/v1/uploads` 拿授权 → 浏览器直接 PUT（`credentials: omit`、拒绝 authorization/cookie/x-gateway-* 头）→ `POST .../uploads/{id}/completion` 得 `fileId` + `fileAddress`（必须等于 `/files/v1/{fileId}`）→ 轮询 `GET .../files/{fileId}` 直到 scanStatus=AVAILABLE → `POST /onboarding/v1/applications/{id}/documents {subjectPartyId, requirementCode, documentType, fileAddress}`。`client-console-web/src/onboarding.ts:198-395`
- sessionStorage 只存非敏感恢复坐标（phase、uploadId、fileId、三把 key）。`client-console-web/src/onboarding.ts:248-297`
- 下载：`POST /onboarding-files/v1/files/{fileId}/download`，只接受 https 短时地址。`client-console-web/src/OnboardingPage.tsx:1250-1251`、`client-console-web/src/onboarding.ts:228-240`
- 材料行按 `materialSubjects[].requirements[]`（requirementCode、documentType、requiredFlag）渲染，名称取 `enum.material.<type>`。`client-console-web/src/OnboardingPage.tsx:1511-1560`
- R5 的 `max_age_days`、允许文件类型子表、issue/expiry 日期目前在关联请求体里都没有字段（只传 4 个字段）。

### 1.6 审核结果与提交核对
- `ReviewResult = {outcome: RETURNED|APPROVED|REJECTED, returnSection: BASIC|BUSINESS|PEOPLE|MATERIALS|null, publicNote, decidedAt}`；只有单个退回分区 + 一段公开说明，没有需求级 errors[] / 期限。`client-console-web/src/onboarding.ts:64-130`
- 退回分区到锚点映射 `returnSections`，i18n 覆盖测试按它核对 key。`client-console-web/src/onboarding.ts:74-80`、`client-console-web/src/i18n-coverage.test.ts:10`
- 提交前核对 `GET .../submission-check` 返回 `{ready, issues[{field, message}]}`，`describeIssue` 解析 `people[partyId].x` / `materials[partyId].code` / `documents[id]` 路径。`client-console-web/src/submission.ts:13-65`、`client-console-web/src/OnboardingPage.tsx:198-280`
- 提交 `POST .../submissions`，`SUBMISSION_INCOMPLETE` 时自动重跑核对。`client-console-web/src/OnboardingPage.tsx:231-247`
- 状态枚举 `application_status` 只有 DRAFT/RETURNED/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED（无 WITHDRAWN）；`relation_type` 5 个固定值；`material` 9 个固定值；`business_model` 6 个固定值。`client-console-web/src/enum-label.ts:10-32`
- 需修订：R4 撤回、R7 EXPIRED、需求级 errors[]（requirementKey/code/reason/remediation）+ 期限、DECLARATIONS 分区、入驻码输入都无现成承载。

### 1.7 i18n
- 运行时 `src/i18n/runtime.ts`（ICU，按命名空间懒加载语言包），manifest `/api/client/i18n/v1/manifest`，`publishedOnly: true`（无发布时只显示英文）。`client-console-web/src/i18n/index.ts:22-33`
- 8 个命名空间：common / auth / onboarding / workspace / team / invitation / developer / enum，en 基准在 `src/i18n/en/<ns>.json`，构建时内置。`client-console-web/src/i18n/builtin.ts:1-21`
- key 为蛇形（`onboarding.field.address.address_line1`），按约定已发布的蛇形 key 不改名。`client-console-web/src/i18n/en/onboarding.json:1-30`、`agent-docs/DEVELOPMENT_CONVENTIONS.md:280`
- zh-CN 种子是单文件 `client-console-web/i18n/client-console.zh-CN.seed.json`（与 ops-web 按命名空间分文件不同）。
- 导出：`npm run i18n:export-source` → `i18n-out/client-console.en.json`（`code-mor-i18n-source/1`）+ zh-CN 导入文件。`client-console-web/scripts/i18n-export-source.mjs:1-37`
- 守卫：`src/i18n-coverage.test.ts` 扫源码禁中日韩字面量、核对枚举与种子覆盖。`client-console-web/src/i18n-coverage.test.ts:1-40`

## 2. client-console-bff

- 入驻 Controller：`@RequestMapping("/client-onboarding/v1")`，`client.onboarding-enabled=true` 才装配；接口 start、form-options、applications(POST)、applications/{app}(GET 聚合)、draft(PATCH)、people(POST/PATCH)、relations(POST/PATCH/DELETE)、documents(POST/DELETE)、submissions(POST)、submission-check(GET)。`paytech/client-console-bff/paytech-client-console-bff-app/src/main/java/com/paytech/client/console/bff/ext/rest/ClientOnboardingController.java:35-191`
- 文件 Controller：`/onboarding-files/v1`（uploads、completion、files/{file}、download），经 Platform `InternalFileApi`。`.../ext/rest/ClientOnboardingFileController.java:21-61`
- 写命令直接调 client-master 轻量 API（`ClientDraftApi`、`ClientPeopleApi`、`ClientDocumentApi`、`ClientSubmissionApi`），参数 `(CallerServiceCodes.CLIENT_CONSOLE_BFF, accountId, sessionId, ...)`。`.../ClientOnboardingController.java:65-190`
- 客户端装配：client-master 用 `HttpServiceClientFactory`（服务发现名 `client-master-service`），compliance 用 `MaterialRequirementsApi` / `ClientReviewResultApi`（`compliance-service`）；测试直连地址 `client.cct-test-base-url` / `client.risk-test-base-url` 只在 auto profile、127.0.0.1 生效。`.../configuration/ClientOnboardingConfiguration.java:25-140`
- 7.2 要求 `programs/resolve` 取代 `MaterialRequirementsApi`：BFF 的 `ClientFormOptionsProcessor` 与 `ClientApplicationDetailsProcessor` 都依赖它。`.../configuration/ClientOnboardingConfiguration.java:82-90`
- 聚合（Processor，继承 Foundation `AbstractProcessor`）：
  - `ClientFormOptionsProcessor`：并发 client-master 目录 + compliance 主主体材料要求，3 秒上限，任一方失败返回 UNAVAILABLE。`.../service/processor/ClientFormOptionsProcessor.java:23-73`
  - `ClientApplicationDetailsProcessor`：草稿为主，并发补材料要求（按资格类型）、审核结果（仅 RETURNED/APPROVED/REJECTED）、Platform 文件状态；8 并发、3 秒。`.../service/processor/ClientApplicationDetailsProcessor.java:30-81`
  - `ClientOnboardingStartProcessor`、上传 / 完成 / 读取 / 下载各一个 Processor。
- 鉴权头：`ClientActorResolver` 要求 `X-Gateway-Realm=CUSTOMER`、`X-Gateway-Channel=CLIENT`、`X-Gateway-Actor-Type=PLATFORM_USER`、`X-Gateway-Context-Type`，读取 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`、`X-Gateway-Tenant-Id`、`X-Gateway-Tenant-Member-Id`（单值、规范正整数）。`.../ext/rest/ClientActorResolver.java:28-41`
- 出站 `X-Caller-Service: client-console-bff`。`.../integration/CallerServiceCodes.java:3-6`
- 测试：`ClientOnboardingHttpAutoTest`（786 行，`@SpringBootTest RANDOM_PORT`，JDK `HttpServer` 模拟三个 Owner，`client.cct/risk/platform-test-base-url` 指向它）。`paytech/client-console-bff/paytech-client-console-bff-test/src/test/java/com/paytech/client/console/bff/auto/ClientOnboardingHttpAutoTest.java:23-66`、`:592`；另有 `ArchitectureAutoTest`、`ClientLocalizationManifestHttpAutoTest`。README 说明见 `paytech/client-console-bff/README.md:36-47`。
- 7.5 新增转发 jurisdictions / form-schema / declarations / requirements / withdrawal：Controller 新方法 + client-master API 新接口 + Gateway 新路由（第 0 节第 2 点）。form-schema 若仍需合并 `programs/resolve`，留在 Processor 聚合。

## 3. operations-console-web

### 3.1 入驻审核（/client-onboarding/reviews）
- 列表 + 详情同组件：`ClientOnboardingReview` 按路径段分流，列表筛选 applicationId / qualificationType / status，URL 记筛选与分页。`operations-console-web/src/pages/ClientOnboardingReview.tsx:20-56`
- BFF 前缀 `/client-onboarding-admin/v1`；`caseStatuses` = SUBMITTED/IN_REVIEW/RETURNED/APPROVED/REJECTED/WITHDRAWN，`returnSections` 4 个，`clientQualificationTypes` 2 个。`operations-console-web/src/clientReview/clientReview.ts:8-44`
- 页面模型：ReviewCase（subjects[].requirements[].evidence[]、classifications、approvalBlockers）、Application（values 按 group.field 原样展示）、ClassificationSet。严格解码，形状不符抛 502 INVALID_RESPONSE。`operations-console-web/src/clientReview/clientReview.ts:11-135`
- 详情区块：头部 Descriptions → 申请资料（`Values` 按 `client-review.application.group/field.<x>` 取标签，未知原样）→ 材料（逐份 ACCEPTED / REJECTED、下载）→ 行业与 MCC（编辑 + 确认）→ 结论（RETURN / APPROVE / REJECT 弹窗）。`operations-console-web/src/clientReview/ClientReviewDetail.tsx:22-104`
- 按钮权限：`CLIENT_ONBOARDING_REVIEW_EVIDENCE`、`CLIENT_ONBOARDING_CLASSIFY`、`CLIENT_ONBOARDING_RETURN`、`CLIENT_ONBOARDING_DECIDE`。`operations-console-web/src/clientReview/ClientReviewDetail.tsx:32`、`:47`、`:50`
- 证据核验 `POST cases/{id}/evidence/{evidenceId}/review {outcome}`（无拒绝原因码）。`operations-console-web/src/clientReview/ClientReviewDetail.tsx:105-128`
- 决定弹窗：RETURN `{reasonCode, section, publicNote}`；APPROVE/REJECT `{reasonCode, reason, publicNote?}`；422 / 409 在弹窗内提示。`operations-console-web/src/clientReview/ClientReviewDetail.tsx:178-212`
- 下载：`POST cases/{id}/files/{fileId}/download`，HTTPS 短时地址直接跳转。`operations-console-web/src/clientReview/ClientReviewDetail.tsx:228-236`、`operations-console-web/src/clientReview/clientReview.ts:136-143`
- 写命令 hook：同路径同内容重试复用 Idempotency-Key，403 刷新导航。`operations-console-web/src/clientReview/useReviewCommand.ts:9-37`
- 与 R6 差距：无人工检查、筛查命中、CRR、推荐 / 四眼、需求级退回 errors[] + 期限、时间线、指派、责任 PayFac 筛选；状态缺 WAITING_EVIDENCE / PENDING_DEPENDENCY / EXPIRED。

### 3.2 主体资格（/compliance/qualifications）
- 单页：顶部主体查找（roleType + roleId）+ 资格列表；行操作历史（Drawer）、手工登记、撤销（Modal）；按钮 `OPERATIONS_QUALIFICATIONS_MANAGE`。`operations-console-web/src/pages/Qualifications.tsx:27-60`、`operations-console-web/src/qualification/QualificationDialogs.tsx:124`
- BFF 前缀 `/qualification-admin/v1`；roleTypes CLIENT/OPERATING_ENTITY/LOCAL_PARTNER；qualificationTypes 4 个；decisionSources AUTOMATED/REVIEWER/OVERRIDE/MANUAL。`operations-console-web/src/qualification/qualification.ts:10-30`
- R7 新增 tier / risk_rating / jurisdiction / responsible_partner 需加列与解码。

### 3.3 master-data 参照（OE / Partner 列表 + 详情）
- `/master-data/operating-entities[/:entityNo]`、`/master-data/local-partners[/:localPartnerId]`：列表筛选写 URL，行点击进详情子路由，详情 Descriptions + 修改弹窗 + "在主体资格中查看"链接。`operations-console-web/src/pages/OperatingEntities.tsx:19-30`、`operations-console-web/src/masterData/OperatingEntityDetail.tsx:1-105`
- BFF 前缀 `/entity-admin/v1`。`operations-console-web/src/masterData/masterData.ts:12`
- Client 列表 / 详情可直接照此骨架（新 componentKey + `pageRoutePaths` 加 `/:clientId`）。

### 3.4 页面骨架与注册约定
- 详情子路由在 `pageRoutePaths` 按 componentKey 登记（审核、OE、Partner、渠道、对账等）。`operations-console-web/src/navigation/menu.ts:56-104`
- 菜单树只显示服务端授权且本地 `pageCapabilities` 已登记 componentKey + 路由一致的页面。`operations-console-web/src/navigation/menu.ts:19-40`
- `pageCapabilities.ts` 是 Web 侧唯一页面 / 按钮注册表（含中文 displayName，是禁中文守卫唯一白名单）。`operations-console-web/src/navigation/pageCapabilities.ts:1-2`、审核条目 `:330-350`、资格 `:194-202`、OE / Partner `:683-703`
- Platform 侧镜像 `paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json`（`ConsoleResourceCapabilities` 读取，`.../domain/configurationmanagement/ConsoleResourceCapabilities.java:14-25`）。现状 Platform 60 个组件、Web 74 个，Platform 缺 14 个（含块 137 的 QUALIFICATIONS / OPERATING_ENTITIES / LOCAL_PARTNERS），说明近期块直接 DML 插 console_resource 时没有同步该 JSON；是否同步要定。
- 组件映射：`ConsolePage.tsx` 的 `pages: Record<PageComponentKey, ...>` 懒加载。`operations-console-web/src/navigation/ConsolePage.tsx:10`、App 路由 `operations-console-web/src/App.tsx:126`
- 抽屉约定：对账需求用户要求"详情作为二级页面，不要从右侧抽屉拉出来"，录入表单仍可用抽屉。`requirements/20261021/reconciliation-对账控制台详情页与渠道流水/requirement.md:10-11`；控制台已有"列表 → 详情页"先例（同文件 :13）。资格页历史仍是 Drawer。原型偏好（记忆）：主从收件箱、居中模态，参照工作流 V2 画板。
- 并发：Operations 路由每主体并发 2，页面不可按行扇出请求（块 135 / i18n 页 429 教训）。

### 3.5 i18n 与守卫
- 23 个命名空间（含 `client-review`、`qualification`、`master-data`），新命名空间需：namespaces.ts 条目、`src/i18n/en/<ns>.json` 并在 builtin.ts 登记、`i18n/zh-CN/<ns>.json`、Platform 先建命名空间再导入。`operations-console-web/src/i18n/namespaces.ts:1-10`
- 根部 I18nGate 启动前加载全部命名空间。同上注释。
- 禁中文守卫全 src 严格执行，白名单只有 pageCapabilities.ts。`operations-console-web/src/i18nSourceGuard.test.ts:8-27`
- `i18nCatalog.test.ts`：en / zh-CN key 一致、ICU 合法、`nav.resource.*` 覆盖 pageCapabilities 全部 code。`operations-console-web/src/i18nCatalog.test.ts:10`、`:108-117`
- 新命名空间段名小驼峰，枚举 `<ns>.enum.<Type>.<VALUE>`。`agent-docs/DEVELOPMENT_CONVENTIONS.md:280-283`
- 迁移指南（命名空间归属、种子写法、自检命令）。`operations-console-web/docs/i18n-migration-guide.md:1-40`、`:140-220`
- 测试：`node --test 'src/**/*.test.ts'`（模型纯函数，如 `clientReview.test.ts` 83 行、`qualification.test.ts` 142 行、`masterData.test.ts` 146 行）+ tsc + vite build。`operations-console-web/package.json:13`

## 4. operations-console-bff

- 审核 Controller `/client-onboarding-admin/v1`：O01 列表、O02 详情（Processor 聚合 Risk 任务 + CCT 申请 / 分类 / 行业目录）、O03 分类 PUT、O04 确认、O05 证据核验、O07 退回、O08 通过、O09 拒绝、O10 下载。**无 O06 /checks**。`paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/ClientOnboardingReviewController.java:36-119`
- 客户端：`ClientReviewRiskClient` → compliance `ClientReviewAdminApi`（`@HttpExchange("/client-onboarding-admin/v1")`）；`ClientReviewCctClient` → client-master `ClientApplicationAdminApi` + `ClientClassificationAdminApi`；`ClientReviewFileClient` → Platform `InternalFileApi`。`.../configuration/ClientReviewClientConfiguration.java:45-68`、`paytech/compliance/paytech-compliance-api/src/main/java/com/paytech/compliance/api/ClientReviewAdminApi.java:25-145`
- 主档 / 资格：`EntityAdminController` `/entity-admin/v1` → client-master `ClientMasterEntityAdminApi`（`/client-master-admin/v1`）；`QualificationAdminController` `/qualification-admin/v1` → compliance `QualificationAdminApi`。`.../configuration/EntityQualificationClientConfiguration.java:31-45`、`paytech/client-master/paytech-client-master-api/src/main/java/com/paytech/client/contract/api/entityadmin/ClientMasterEntityAdminApi.java:28`
- 身份头：`OperatorContextArgumentResolver` 读 X-Gateway-Realm / Channel / Actor-Type / Actor-Subject / Session-Id / Route-Code / Context-Type / Tenant-*；下游只透传 `X-Gateway-Actor-Subject` + `X-Gateway-Session-Id`（Owner 合同的 `@RequestHeader`）。`.../ext/rest/OperatorContextArgumentResolver.java:20-33`、`ClientReviewAdminApi.java:47-50`
- 下载另带 `X-Operator-Account-Id`、出站统一 `X-Caller-Service: operations-console-bff`。`paytech/operations-console-bff/README.md:13`
- 严格解析：`ClientReviewRequestConfiguration` 对 `/client-onboarding-admin/v1/**`、`/entity-admin/v1/**`、`/qualification-admin/v1/**` 拒绝未声明 / 重复 / 空 Query，写 Body 上限 65536。新增 Query 参数（如 responsiblePartnerId、assignee）要同时进 Controller `@RequestParam` 与 Gateway query_param 行。`.../configuration/ClientReviewRequestConfiguration.java:41-94`
- 不接收、不转发 Idempotency-Key（Owner 按状态防重）。`.../ClientOnboardingReviewController.java:36-40`、`paytech/operations-console-bff/README.md:15`
- 代操作：无现成机制（第 0 节第 3 点）。
- 测试：`ClientOnboardingReviewBrowserAutoTest extends OperationsBffHttpTestBase` + `ClientReviewOwnerSimulator`（按 method + path 配 reply / problem）；`EntityQualificationAdminBrowserAutoTest`。`paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/ClientOnboardingReviewBrowserAutoTest.java:35`、`:103-177`

## 5. Platform 注册块

- 位置：每个需求在 `requirements/<date>/<topic>/db/platform-support/registration-block<N>.sql` 起草，同内容逐字追加到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾；发布目录 `release/sql/60-…-registration.sql`、`61-…-activate.sql`、`62-…-deactivate.sql`、`69-…-rollback.sql`。样板：`requirements/20261023/commercial-商业关系快照与真实解析接入/db/platform-support/registration-block137.sql`、`.../release/sql/61-platform-block137-activate.sql`、`62-…deactivate.sql`、`69-…rollback.sql`
- 近期块：134 文件公开桶、135 多语言、136 文件上传入口、137 商业关系快照（`requirements/README.md:61-73`）。ID 区间 `900000000000<N>001—<N>999`：1xx permission、2xx role_permission、3xx console_resource、4xx gateway_route、5xx route_header、6xx query_param、7xx guard_rule。`registration-block137.sql:42-44`
- 块头需写：执行库、utf8mb4、来源节、前置镜像、预期各表行数、执行前核对区间为空与 code / (method, path) 未用、回滚顺序（子表先删、按钮先于菜单）。`registration-block137.sql:1-37`
- 休眠：permission ACTIVE、PLATFORM_ADMIN（role 900000000000000510）授权 REVOKED、菜单 / 按钮 / 路由 DISABLED。激活：路由 ACTIVE、资源 ACTIVE、授权 ACTIVE、role.version+1。`registration-block137.sql:14-16`、`:39-63`、`61-platform-block137-activate.sql:1-19`
- 表与列：`t_permission`、`t_role_permission`、`t_console_resource`（resource_code、channel、resource_type MENU/BUTTON、parent_resource_id、display_name 中文、frontend_route、component_key、permission_id、sort_order、status）、`t_gateway_route`（route_code、channel、http_method、external_path_template、downstream_path_template、target_id、security_profile_code、authorization_mode、permission_id、required_scope_code、request/response_cookie_mode、idempotency_mode、body_limit_bytes、两个超时、status…）、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`。`registration-block137.sql:65-73`、`:126`、`:145`
- Operations target 固定 `900000000000000001`；父菜单例：风控管理 `900000000000000565`、商务 `900000000000095301`。`registration-block137.sql:20-23`、`:46-49`
- Guard 取值照抄同形路由：GET principal 30/min + 并发 2、IP 30 + 3、shared 300 + 50；写 10/min，Body 65536，加 content-type 与 idempotency-key 头。`registration-block137.sql:24-27`
- **DISABLED 也会被编译**：Gateway 快照编译器校验所有状态路由的 guard / path / header，只有路径冲突限 ACTIVE；块 135 曾因 guard 1200/min 超上限（600 / 50）打断快照刷新约 5 分钟。发布前须在无网 MySQL 复制库上分别以休眠 / 激活态跑真实编译器。`agent-docs/CURRENT_IMPLEMENTATION.md:35`
- 现有入驻注册不在块内：Client 入驻 37 条公共路由 + 运营审核 1 菜单 4 按钮 5 权限（`CLIENT_ONBOARDING` READ / CLASSIFY / REVIEW_EVIDENCE / RETURN / DECIDE）走 Platform 管理 API 生成 ID。`requirements/20260914/client-console-注册与客户入驻/registration-design.md:7-11`、`.../db/registration-plan.json`、`.../db/management-command-templates.json`。新增按钮挂在 `OPERATIONS_CLIENT_ONBOARDING_REVIEW` 下或给既有权限加路由时，块 138 须用 `(SELECT … WHERE resource_code / permission_code / target_code = …)` 解析 ID；回滚按自身 ID 段即可。
- 块 138：仓库内无占用；mor-test 风控控制台占 `101xxx—104xxx`、`111xxx—114xxx`（`agent-docs/CURRENT_IMPLEMENTATION.md:244`），与 138 不冲突。live 仍需 `SELECT` 区间为空。
- 若块内含 CLIENT 通道路由（7.5 新接口），沿用 registration-plan 的 Client 形状：PUBLIC/CLIENT 安全配置、响应 10000ms、Body 接收 5000ms、16/64 KiB、idempotency-key 仅 CORS 允许（2026-10-16 起不转发）。`requirements/20260914/client-console-注册与客户入驻/registration-design.md:15-21`、`agent-docs/CURRENT_IMPLEMENTATION.md:1255`

## 6. 多语言

- Platform 模块 `paytech/platform-support/paytech-platform-support-localization`，变更 SQL `paytech/platform-support/docs/database/sql/changes/2026-10-23-localization.sql`；7 张 `t_i18n_*` 表、发布不可变、按"语言 × 命名空间"生成 JSON 包。`agent-docs/CURRENT_IMPLEMENTATION.md:33`
- 命名空间由运营在后台创建，导入不创建命名空间，未建则拒绝导入。`agent-docs/decisions/2026-10-22-ui-text-localization.md:27`、`agent-docs/DEVELOPMENT_CONVENTIONS.md:280`
- 发布流程：改 en 基准 + zh-CN 种子 → `npm run i18n:export-source` → 控制台导入 → 翻译 → 发布。`agent-docs/DEVELOPMENT_CONVENTIONS.md:281`
- 范围边界：服务端报错文案与"运营录入的业务内容"另立需求，不进入本模块；本期没有各端共享的命名空间。`agent-docs/decisions/2026-10-22-ui-text-localization.md:21-22`、`:41`、`agent-docs/DEVELOPMENT_CONVENTIONS.md:283`
- 为 `onboarding-schema` 新命名空间需决定：
  1. 归属：client-console 与 operations-console 是两个应用，同名命名空间要各建一次，或在决定中开例外。
  2. en 基准来源：现行模型是"key 与 en 来自代码、构建时内置"。Schema 行的 `label_key / help_key` 在 DB DML 里，需要一份随代码提交的 `onboarding-schema.json`（client-web `src/i18n/en/`，ops-web 另需 `i18n/zh-CN/onboarding-schema.json`），并与 DML 一致性测试；client-web 的 i18n-coverage 与 ops-web 的 i18nCatalog 测试都不认识 DB 中的 key。
  3. 是否视为"业务内容"：label 由运营在 Schema 版本发布时改动则属业务内容，与决定冲突；若只随 DML / 代码发布则属 UI 文案。
  4. 段名：新命名空间用小驼峰；client-web 既有命名空间保持蛇形。
  5. ops-web 根 Gate 一次加载全部命名空间，Schema key 多时首屏变重。
  6. 新 ops 页面还要加 `nav.resource.<CODE>` key（否则英文界面显示中文 display_name）。`agent-docs/CURRENT_IMPLEMENTATION.md:28`
- 现状发布号：operations-console 语言包 #8（8813 key，2026-10-25）；client-console 首批发布一次（608 key，2026-10-23），之后仓库记录未见新发布，live 号待核。`agent-docs/CURRENT_IMPLEMENTATION.md:19`、`requirements/20261022/platform-support-多语言管理/release/release.md:8`
- 已知遗留：仓库 `operations-console-web/i18n/zh-CN/nav.json` 侧栏 3 个 key 仍为英文（线上中文）。`agent-docs/CURRENT_IMPLEMENTATION.md:20`

## 7. Pencil 原型

- Pencil 当前打开的是 `design-discuss/client-onboarding/client-onboarding.pen`（29 个画板，含"1 职责边界与责任链""3 入驻与审核流程""8 采集 Schema 如何驱动表单、校验与需求清单""9 三国国家包对照""12 AE 场景数据示例"等）。未切换编辑器，未读取 `operations-console-web/docs/operations-console.pen`。
- 文档中可查的 ops 画板：Client 入驻审核前缀 `CCT 入驻 ·`、`OPS · S05 行业与 MCC 确认`（`requirements/20260914/client-console-注册与客户入驻/ui-design.md:14`、`requirements/20260914/client-console-注册与客户入驻/design.md:30`）；资格 / OE / Partner 前缀 `RS-01—RS-08`（`requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md:393`）；交互基准为工作流 V2 画板（约 y=73000—76900）。Client 门户原型在 `client-console-web/client-console.pen`（认证 S01/S04，`client-console-web/README.md`）。

## 8. CURRENT_IMPLEMENTATION 基线

- Client 入驻切片锚点 `client-console-onboarding-20260914`：`agent-docs/CURRENT_IMPLEMENTATION.md:1251`；2026-10-14 首发 39 条入驻路由，2026-10-16 改 idempotency 头后 routeCount=231。`agent-docs/CURRENT_IMPLEMENTATION.md:1255`、`:1259`、`:1162`
- client-bff / client-web 最近镜像：client-bff `9033b8fa…`、client-web `280a2f38…`（多语言管理 2026-10-23）；文件公开桶曾换 client-bff `b8ecaaea…`、client-master `2e495b40…`。`agent-docs/CURRENT_IMPLEMENTATION.md:35`、`:51`
- 最新共享基线（relsnap-20261025a）：compliance `ca158545…`、client-master `eeed4e63…`、ops-bff `feefe73f…`、ops-web `4f44725e…`；块 137 激活，Gateway 574 → 585 条路由；语言包 #8。`agent-docs/CURRENT_IMPLEMENTATION.md:19`
- 遗留 GAP-PROFILE：client-master 审核通过不清 `draft_application_id`、不写 `content_hash`，mor-test acquiring 保持 MOCK；本需求改 `t_party_profile`（current_slot）时会碰到同一处。`agent-docs/CURRENT_IMPLEMENTATION.md:20`
- 发布约束：Platform 新增可空字段时须先换消费方（client-master、client-bff、ops-bff）再换 Platform。`agent-docs/CURRENT_IMPLEMENTATION.md:49`
