# ops-fe 交付报告：operations-console-bff + operations-console-web（切片 1）

> 状态：2026-10-27，worktree `Code-MoR-onboard`（基线 `ea0272f3`），未提交、未部署、未执行 SQL。
> 合同：technique.md §2.1 CM-10/11/12、§2.2 CP-5/CP-6；compliance 以已落地的 `ClientReviewAdminApi` 为准（cmp-dev），client-master 管理接口待 cm-dev 发布。

## 1. 结论

| 部分 | 状态 |
|---|---|
| ops-bff CP-5 案件工作台（6 个新命令、列表过滤、PayFac 选项、代操作核对、O05/O07/O08/O09 改用 compliance 请求体） | 完成，测试通过 |
| ops-bff CM-10/11/12（入驻配置、Client、入驻码转发） | 完成，已切换为 client-master `ClientOnboardingAdminApi`（`api.onboardingadmin`），删除 BFF 内镜像；测试通过 |
| ops-web 案件工作台、入驻配置页（含尽调程序）、Client 列表 / 详情、入驻码页、en + zh-CN | 完成，按 client-master / compliance 实际合同解码，测试 + 构建通过 |

## 2. 文件

**operations-console-bff**
- `ext/rest/ClientOnboardingReviewController.java`：新增 `GET /responsible-partners`、`POST cases/{id}/requirements/{rid}/waiver`、`POST cases/{id}/checks/{cid}/result`、`POST cases/{id}/screenings/run`、`POST cases/{id}/screening-hits/{hid}/resolution`、`PUT cases/{id}/risk-rating`、`POST cases/{id}/recommendation`；列表加 `responsiblePartnerId`、`jurisdictionCode`；所有 compliance 写命令直接使用其合同请求体并经 Processor。
- `service/processor/ClientReviewCommandProcessor.java` + `request/ClientReviewCommandRequest.java`：S-05 代操作核对——`onBehalfOfPartnerId` 非空时先读 client-master Partner，须 ACTIVE 且含 OE_PROVIDER，否则 422 `ON_BEHALF_PARTNER_INVALID`（Partner 404 同样 422），不调用 compliance。
- `integration/ClientReviewRiskClient(.java|Impl)`：CP-5 全部方法；O10 证据文件 ID 改由 `fileAddress`（`/files/v1/{fileId}`）解析，地址不合规为 502。
- `integration/EntityAdminClient(.java|Impl)`：`responsiblePartners`（client-master `local-partners?status=ACTIVE&role=OE_PROVIDER&page=1&size=100` 裁剪为选项）。
- `service/model/clientreview/response/ResponsiblePartner(List)ResponseBody.java`（新）；`ClientReviewModels` 删除已被 Owner 请求体取代的三个 record。
- `domain/enums/OperationsBffErrorCode`（+`ON_BEHALF_PARTNER_INVALID`）、`OperationsErrorStatusResolver`（422）、`ClientReviewRequestConfiguration`（9 个 compliance 请求体纳入严格解析）、`application.yml`（`/client-admin`、`/onboarding-code-admin` 只记元数据）。
- CM-10/11/12：`integration/OnboardingAdminClient(.java|Impl)` 调用 client-master `ClientOnboardingAdminApi`（结果须属于请求的辖区 / 目录 / Client / Partner / 入驻码）；目录项按辖区（`*` 与 null 为全局）、入驻码按状态由 BFF 过滤（Owner 尚不支持）；入驻码列表与作废结果必须是 `code=null` + `****-XXXX` 掩码，出现明文即 502，签发响应为 `{issued（掩码）, code（明文，一次）}`；作废无请求体；`service/model/onboardingcode/response/*`（BFF 页面模型）；`ext/rest/OnboardingConfigAdminController`、`ClientAdminController`、`OnboardingCodeAdminController`、`service/processor/ClientDetailsProcessor`（主档 + compliance Client 资格）；`EntityQualificationClientConfiguration` 代理 Bean（登记 `OnboardingErrorCode` + `CLIENT_NOT_FOUND`）。
- 测试：`OnboardingAdminBrowserAutoTest`（新 7 例）、`ClientOnboardingReviewBrowserAutoTest`（新 5 例 + 原例按新合同改写）、`OperationsConsoleBffApplicationAutoTest`（路由清单 + 元数据前缀）、`PlatformOperationsSimulator`（7 条新路由码）、`RiskClientConfigurationAutoTest`（compliance 合同消费者白名单，见第 5 节）。

**operations-console-web**
- 工作台：`clientReview/ClientReviewDetail.tsx`（外壳：头部、代 PayFac 选择、统计、分区切换、右侧决定栏）、`ReviewEvidence.tsx`（申请资料、材料接受 / 拒绝原因码 / 豁免、下载）、`ReviewChecks.tsx`（人工检查录入、筛查执行与命中处置、重跑）、`ReviewRisk.tsx`（CRR 评级 + 因子、时间线、行业与 MCC）、`ReviewDecision.tsx`（批准门禁、CRR 摘要、推荐 + 四眼提示、批准 / 拒绝、退回需求级 errors[] + 期限）、`reviewParts.tsx`（圆点状态、命令弹窗、Owner 409/422 原因）、`clientReview.ts`（模型与解码，按 compliance 实际合同：检查 / 筛查按主体返回后展平，`*At` 时间，`fileAddress`）。
- 列表：`pages/ClientOnboardingReview.tsx`（9 态、责任 PayFac 过滤与名称、辖区 / CRR / 轮次列）。
- 新页：`onboardingConfig/*` + `pages/OnboardingConfig.tsx`（辖区包 → 概要与规则开关、采集 Schema 分区 / 分组字段表、目录项）；`masterData/clients.ts`、`ClientDetail.tsx`、`pages/Clients.tsx`（列表筛选、主档 + 掩码证件、关系人、申请历史链接到案件、资格）；`onboardingCodes/*` + `pages/OnboardingCodes.tsx`（PayFac 列表、按状态分段、签发后只显示一次明文 `XXXX-XXXX` + 复制、作废需原因）。
- 注册：`pageCapabilities.ts`（3 个组件 + 4 个按钮：入驻码签发与作废、筛查、推荐、豁免）、`ConsolePage.tsx`、`menu.ts`（`/master-data/clients/:clientId`）。

**platform-support（S-11，主会话指派）**
- `paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` 与 Web `pageCapabilities.ts` 完全一致：补齐既有缺失的 14 个组件（含块 137 三个）+ 本切片 3 个组件，`OPERATIONS_CLIENT_ONBOARDING_REVIEW` 加 3 个按钮；共 77 个组件、97 个动作，无重复编码（加载器 fail-fast 条件已离线核对）。

## 3. 浏览器端点（BFF）

| Method Path | 下游 | 状态 |
|---|---|---|
| GET `/client-onboarding-admin/v1/cases?…&responsiblePartnerId&jurisdictionCode` | compliance O01 | 完成 |
| GET `/client-onboarding-admin/v1/responsible-partners` | client-master B4 | 完成 |
| POST `…/cases/{caseId}/evidence/{evidenceId}/review`（+`rejectReasonCode`、`note`） | compliance | 完成 |
| POST `…/cases/{caseId}/requirements/{requirementId}/waiver` | compliance | 完成 |
| POST `…/cases/{caseId}/checks/{checkId}/result` | compliance | 完成 |
| POST `…/cases/{caseId}/screenings/run` | compliance | 完成 |
| POST `…/cases/{caseId}/screening-hits/{hitId}/resolution` | compliance | 完成 |
| PUT `…/cases/{caseId}/risk-rating` | compliance | 完成 |
| POST `…/cases/{caseId}/recommendation` | compliance | 完成 |
| POST `…/cases/{caseId}/return`（errors[] + dueDays + reasonCode） / `approval` / `rejection` | compliance | 完成 |
| GET `/onboarding-config-admin/v1/jurisdictions`、`/onboarding-schemas?jurisdiction`、`/catalog-items?catalogCode&jurisdiction` | client-master CM-10 | 完成（镜像合同） |
| GET `/onboarding-config-admin/v1/programs?jurisdiction`（必填） | compliance O14 `listPrograms`（jurisdictionCode，不传 status：全部版本） | 完成；路由码 `operations-onboarding-config-programs`、ONBOARDING_CONFIG_READ，已告知 infra-dev |
| GET `/client-admin/v1/clients`、`/clients/{clientId}`、`/responsible-partners` | client-master CM-11 + compliance 资格 | 完成（镜像合同） |
| GET/POST `/onboarding-code-admin/v1/local-partners[/{id}/onboarding-codes[/{codeId}/revocation]]` | client-master CM-12 | 完成（镜像合同） |

## 4. 发给 infra-dev 的块 138（OPERATIONS）

已确认 `db/platform-support/block138-spec.json`（FINAL，主会话裁定）为最终清单：7 个新权限（ONBOARDING_CONFIG_READ、CLIENT_READ、ONBOARDING_CODE_READ、ONBOARDING_CODE_MANAGE、CLIENT_ONBOARDING_SCREENING / RECOMMEND / WAIVE）；菜单入驻配置 310、入驻码 320 为顶级，Client 在商务下 80；17 条路由使用 ops-fe 路由码，o01 增 `responsiblePartnerId`、`jurisdictionCode`。与 ops-fe 初稿的差异（已对齐 Web 与测试）：CRR 路由复用 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（Web 按钮用 `CLIENT_ONBOARDING_CLASSIFY`），不新建 `CLIENT_ONBOARDING_RISK_RATE`；`programs` 路由不登记（compliance 无运营只读程序接口，Web 不显示程序分区）；人工检查复用 `CLIENT_ONBOARDING_EVIDENCE_REVIEW`（无新按钮）。infra-dev 另一条消息提出的 CHECK_RESULT 新权限 / 去掉 CODE_READ / Client 顶级与该 FINAL spec 及主会话裁定矛盾，按已被取代处理并已告知 infra-dev。G4 programs 路由：cmp-dev 交付 O14 后恢复（见第 3 节），已告知 infra-dev 补登。

## 5. i18n

- 只 en + zh-CN；新增 key：`client-review` 约 380 个（工作台、配置页、入驻码、枚举）、`master-data` 50 个（`client.*`）、`nav` 7 个（`nav.resource.*`：3 菜单 + 4 按钮）。未新建命名空间。
- `onboardingSchema`（S-04）：新建 ops-web 命名空间（`namespaces.ts`、`builtin.ts`、`src/i18n/en/onboardingSchema.json`、`i18n/zh-CN/onboardingSchema.json`），从 `db/onboardingSchema.{en,zh-CN}.json`（与 client-console 同名命名空间一致）镜像配置页用到的四类 155 个 key：jurisdiction 3、field 30、catalog 109、group 13；配置页分组标题也取译文。新增 `onboardingConfig/onboardingSchemaCatalog.test.ts`：client-master 初始化 DML 中这四类 label_key（142 个）必须都有 en 基准。**发布前 Platform 需为 operations-console 应用先建 `onboardingSchema` 命名空间**再导入。
- 覆盖 / 守卫：`i18nCatalog.test.ts`（en / zh-CN 键一致、ICU 合法、nav 覆盖 pageCapabilities）与 `i18nSourceGuard.test.ts`（禁中文）均在全量测试中通过。

## 6. 测试与结果

- Web：`npm test` → 1540 / 1540 通过（含程序解码 1 例；含四眼拒绝与 NOT_READY 解析 1 例；含 onboardingSchema DML 覆盖测试 2 例；新增 `clientReview.test.ts` 7 例、`onboardingCodes.test.ts`、`onboardingConfig.test.ts`、`clients.test.ts`；`configurationModel.test.ts` 页面计数 74 → 77）；`npx tsc --noEmit` 通过；`npm run build` 通过（chunk > 500 kB 告警为既有）。
- BFF（JDK 25，`-pl operations-console-bff/paytech-operations-console-bff-test -am`，`-Dtest='com/paytech/operations/console/bff/**/*'`，无数据库）：全模块 2385 例中仅 1 例失败——`RiskClientConfigurationAutoTest.test_productionOwnerImportsAndFieldAccessRemainInsideApprovedBoundary`，为**既有失败**（块 137 的 `EntityQualificationClientConfiguration` 等直接使用 compliance 合同，白名单未同步，基线同样失败，并掩盖后续检查）。已把 compliance 合同消费者（块 137 + 本切片）列为显式白名单，Risk 决策合同限制不变；该测试与 `OperationsConsoleBffApplicationAutoTest`、`ClientOnboardingReviewBrowserAutoTest`、`EntityQualificationAdminBrowserAutoTest`、`ArchitectureAutoTest` 复跑均通过。修复并加入 CM-10/11/12 后全模块复跑（私有仓库 `-Dmaven.repo.local=$HOME/.m3-onboard/repository`）：**2393 / 2393 通过**（含程序路由 1 例；辖区格式统一为 compliance 的 `^[A-Z][A-Z0-9-]{1,15}$`，兼容迁移后的 LEGACY）。
- 构建注意：`~/.m3` 中 Foundation SNAPSHOT 与 worktree 源码不一致（`HttpRequestValidationException.remoteFieldErrors()` 缺失），只编 BFF 会失败；使用 `-am` 让上游从 reactor 编译。早先按授权向 `~/.m3` `install` 过 `compliance/paytech-compliance-api` 一次（主会话改用私有仓库前）；此后所有 mvn 命令使用 `~/.m3-onboard`。

## 7. 缺口与待确认

1. **client-master 合同缺口（主会话已接受为后续切片跟进，本切片不改）**：① 作废不收原因 / 备注（`t_partner_onboarding_code` 无原因列，需 DDL）；② Client 详情无激活 / 暂停时间；③ 目录项无辖区过滤参数（BFF 过滤，`*` 为全局，约 109 项）；④ 入驻码列表无分页 / 状态过滤（BFF 过滤状态，每 PayFac 数量小）；⑤ 受理策略只是规则开关 ACCEPTANCE_POLICY（OPEN / ENHANCED_ONLY / CLOSED，缺省 OPEN，Web 已按此显示）。已关闭：列表 / 作废只返回 `codeMasked`（code 为 null），明文只在 201 签发响应，BFF 掩码校验保留作纵深防御；Client 详情 values 与 C05 同一投影，证件只含 identifierMasked 等字段（cm-dev 确认并有 auto 测试）。
2. ~~CP-6 新门禁码~~ 已对齐 cmp-dev 答复：门禁码 CHECK_NOT_PASSED、SCREENING_INCOMPLETE、SCREENING_HIT_OPEN、SCREENING_TRUE_MATCH、RISK_RATING_MISSING、RECOMMENDATION_MISSING / NOT_APPROVE、FOUR_EYES_SAME_PERSON 等均有文案与“去处理”映射；四眼下最终拒绝须已有他人的 REJECT 推荐（按钮禁用并说明）；422 CLIENT_REVIEW_NOT_READY 的 detail 中冒号后的代码逐项显示；列表“平台自营”过滤以 responsiblePartnerId=0 转发（BFF 允许 0）；状态过滤只列本切片实际写入的 7 态。
3. **onboardingSchema 命名空间**：代码侧已完成；发布时需在 Platform 先建 operations-console 的 `onboardingSchema` 命名空间再导入语言包（`npm run i18n:export-source` 已校验：24 个命名空间、9416 个 key）。
4. **代操作 PayFac 选项上限 100**（client-master 单页上限）；超过时需分页或搜索接口。
5. ~~子案件的检查 / 命中 ID~~ cmp-dev 确认：一律用主案件路径，compliance 跨主案件与全部子案件解析。
6. 浏览器实测未做（无运行中的 BFF + Owner 环境）；交互只经类型检查、模型单测与 BFF HTTP 测试验证。

## 8. 既有问题（单独列出）

- `RiskClientConfigurationAutoTest.test_productionOwnerImportsAndFieldAccessRemainInsideApprovedBoundary` 在基线即失败：块 137 直接消费 compliance 合同的类（`EntityQualificationClientConfiguration`、资格 Processor 等）未进入白名单，且首个失败掩盖后续检查。本切片把 compliance 合同消费者（块 137 + 本切片）列为显式白名单修复，Risk 决策合同限制不变；修复后通过。

## 9. 迁移存量的兼容

- mor-test 迁移后保留的 APPROVED 旧案件：辖区 `LEGACY`、档次 STANDARD、无 CRR / 推荐、程序版本 RETIRED。工作台解码允许 LEGACY 辖区与空评级 / 推荐；配置页程序分区列出 RETIRED 版本但标为未生效。

## 10. 验收修复 P1（422 NOT_READY 未满足项）

- 现象：批准 / 拒绝被 422 CLIENT_REVIEW_NOT_READY 拒绝时，浏览器只收到错误码默认说明，门禁代码丢失（本地 requestId e25ecc0d-eacb-4b74-b7f6-cf1672b61d21）。
- 修复（仅 Web）：决定弹窗遇该 422 即刷新案件详情，按刷新后的 `approvalBlockers` 逐项列出未满足条件并可跳转分区（四眼下最终拒绝显示推荐 / 同人条件）；删除从错误文案解析代码的逻辑。ops-bff 不读取错误文案，无需改动。
- 验证：`npm test` 1541 / 1541、`tsc --noEmit`、`npm run build` 通过；新增 `isNotReady` / `unmetConditions` 测试。
- 镜像：rel-build 重建 ops-web `sha256:a6b61d812e57b8b02bf3621de56ac5f2ef0113512cbc3afb1b85f8f3f6a82c95`（03:17Z），候选覆盖与 runbook 已引用；新增 `client-review.decision.notReadyTitle`、`notReadyRefreshing` 两个 key 由主会话纳入语言包 ⑬b。

## 11. mor-test 修复（发布冻结例外，仅前端）

- 命名空间更名：`onboardingSchema` → `onboarding-schema`（Platform 命名空间编码规则 `[a-z][a-z0-9-]{0,63}`）；文件与 key 为 `onboarding-schema.*`（155 个，与 client-console-web 同名文件一致），后端 label_key 仍为 `onboardingSchema.*`，配置页一处映射；DML 覆盖测试、命名空间登记与 `i18n:export-source` 已同步。
- 采集 Schema 页 AE 报“服务暂时不可用”（requestId 57e94cd8-2ec9-48b3-a092-59c9832d7654）：client-master 以 `organizationTypeCode: "*"` 表示默认 Schema，解码器拒绝。修复为 `*` → null，显示“默认（全部组织类型）/ Default (all types)”（新 key `client-review.config.defaultOrganizationType`）；新增真实形状 AE 测试。通配值审计（DML + 代码）：`*` 只出现在 `t_catalog_item.jurisdiction_code`（已处理）与 `t_onboarding_schema.organization_type_code`（本次修复）；compliance 无通配值。
- 验证：`npm test` 1542 / 1542、`tsc --noEmit`、`npm run build` 通过。
- 镜像：ops-web `sha256:4d4c11308b6d23a91b557748b030354303cacd6c894ea3799eaaf08bd4ad53da`（05:32Z，rel-build），覆盖文件与 runbook 已更新。

## 12. mor-test ui-smoke 修复（P0 / P1，仅前端）

- P0 工作台 INVALID_RESPONSE（rid 0d88f7fc…、da38b0fd…、b310d599…）：申请材料改收 `fileAddress` 并由 `/files/v1/{id}` 解析文件 ID；行业选项改收 `labelKey`，经 `onboarding-schema` 翻译（与配置页共用 `onboardingConfig/schemaLabel.ts`）。
- P1 Client 详情 / 工作台字段名：补 `client-review.application.group.{attributes,contact}` 与 20 个字段 key（en + zh-CN）；未登记字段 / 分组按拆词显示，不再显示原始代码。
- P1 审核列表：新增辖区过滤（`jurisdictionCode`）；列宽合计 1340 ≤ 滚动宽度 1360，1440px 下表头不折行、“决定时间”不被固定操作列遮挡。
- 回放测试：`src/uiSmokeReplay.test.ts` 以 ui-smoke 抓取的 43 个去重真实 200 响应（`src/testing/uiSmokeBodies.json`，仅测试）回放全部解码器，并校验文件 ID、行业文案与全部资料字段均有文案。
- 验证：`npm test` 1545 / 1545、`tsc --noEmit`、`npm run build` 通过。
- 镜像：ops-web `sha256:35b61f12ff26eddfa4d2581484124a98f583120891749b61c21189406ff8c1cb`（05:46Z，rel-build；测试文件未打包），覆盖文件与 runbook 已更新。

## 13. mor-test ui-smoke 第 2 批（仅前端）

- 入驻码：保存为 ACTIVE 但截止时间已过或次数用尽的按 EXPIRED 显示与过滤（不改服务端状态）；签发人标注为“账号 ID”。
- 目录值：工作台与 Client 详情按字段 → 目录映射经 `onboarding-schema` 翻译（组织类型按辖区、登记机关、经营模式、币种、性别、资金来源、城市、省 / 州、标识类型、自贸区、职业）；扩展属性字段名取 attribute 文案；目录外值显示代码。
- 资料网格固定表格布局、按词换行，修复逐字折行。
- `onboarding-schema` 镜像扩到 10 个键族 206 个 key（与 client-console-web en / zh-CN 一致）；配置页分区、二选一组、组织类型取该命名空间文案；覆盖测试扩到 10 个键族并比对门户文件。
- 历史轮次：申请资料 / 分类不可用时查同一申请的最新案件（一次列表读取），非当前案件显示“此为历史轮次，申请资料以当前轮次为准”并链接当前案件。
- 验证：`npm test` 1549 / 1549、`tsc --noEmit`、`npm run build` 通过；`i18n:export-source` onboarding-schema 206、24 个命名空间 / 9502 个 key。
- 镜像：ops-web `sha256:1274fefe07968596d77c7f48e22d94e9ff7bdc423b15a0729baae104375105a6`（06:09Z，rel-build）。

## 14. mor-test ui-smoke 第 3 批（仅前端）

- Client 详情的责任 PayFac 经既有 `GET /client-admin/v1/responsible-partners` 显示名称，读不到时显示 ID。
- 国家代码按当前语言显示国家名称（新增 `formatCountry`，基于 `Intl.DisplayNames`；非 ISO 代码如 LEGACY 原样），用于 Client 详情与工作台申请资料的全部国家字段。
- 验证：`npm test` 1551 / 1551、`tsc --noEmit`、`npm run build` 通过；无新增文案 key。
- 镜像：ops-web `sha256:88ab111a7fdabeea6893c307f36a32baec8082b74b218fc2400f12b2cd031872`（06:25Z，rel-build）。
