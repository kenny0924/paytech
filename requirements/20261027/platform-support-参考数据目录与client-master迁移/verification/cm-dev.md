# client-master 阶段 B 实施记录（cm-dev，2026-10-27）

范围：技术方案第 8.3 节、第 6.3 节，S-03—S-08。代码只在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refdata`（`aba76a1a` 起）的 `paytech/client-master` 内修改；未提交、未推送、未部署。

已读：`agent-docs/DEVELOPMENT_CONVENTIONS.md`（第 2、4、5 节）、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`（第 8—13 节）、需求 `requirement.md` / `technique.md`、决定 `2026-10-27-platform-reference-data.md`、Platform 5 个 QueryApi 合同。

## 1. 改动

### 出站端口与校验

| 文件（`paytech/client-master/` 下） | 改动 |
|---|---|
| `…-core/…/integration/ReferenceDataClient.java`（新） | 出站端口：`countries / currencies / subdivisions / industries / mccs(Collection<String> codes)` → `Map<code, ReferenceDataItem>`；只能在事务外调用 |
| `…-core/…/integration/impl/ReferenceDataClientImpl.java`（新） | 只用 `codes=` 调 5 个 QueryApi；去重、按格式过滤（格式不合法不发送，视为不存在），每批 ≤50 分批；MCC 固定 `page=1,size=50`；DTO 在边界转为 `ReferenceDataItem`；任何传输错误、Platform 拒绝、`items` 缺失、返回请求以外的编码 → `ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE)`（503）；状态只有 `ACTIVE` 视为可用（未知状态失败关闭）；处于事务内直接拒绝调用 |
| `…-core/…/domain/onboarding/pack/ReferenceCatalogCodes.java`（新） | `REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION`、`isReference()` |
| `…-core/…/domain/onboarding/pack/ReferenceDataItem.java`（新） | `record(code, active, countryCode, level)` |
| `…-core/…/domain/onboarding/requirement/ReferenceCodeCheck.java`（新） | 单次请求的内存结论；`codes(pack, subjects)` 收集要查的编码（地址国家、证件签发国、COUNTRY 控件字段、COUNTRY 类型扩展属性；`REF_CURRENCY` / `REF_COUNTRY_SUBDIVISION` 字段与当前地址格式组件）；`countryActive`、`contains(catalog, value, country)`（行政区要求国家一致） |
| `…-core/…/requirement/RequirementInput.java`、`OnboardingRequirementEvaluator.java` | 输入加 `references`；`REF_` 目录走 `ReferenceCodeCheck`，其余仍走辖区包；COUNTRY 字段、地址 `countryCode`、证件 `issuingCountry`（单独报 `identifier.issuingCountry`）校验国家；行政区与地址国家不一致同样 `FIELD_VALUE_NOT_IN_CATALOG` |
| `…-core/…/service/processor/ClientSubmissionPreparer.java` | `prepare` 在构造 `RequirementInput` 前按类型各查一次 Platform（提交、提交核对、需求清单三条路径同源，均在事务外） |
| `…-core/…/service/processor/ClientDraftPreparer.java` | 保存草稿：引用 `REF_COUNTRY_SUBDIVISION` 的地址组件只校验 `[A-Z]{2}-[A-Z0-9]{1,3}`（按修改后的地址国家选格式；GENERIC 省州仍是自由文本），不查 Platform |
| `…-core/…/domainservice/service/impl/OnboardingPackServiceImpl.java` | `catalogCodes` 去掉 `REF_` 码，不从 `t_catalog_item` 加载 |
| `…-core/…/ext/rest/FormSchemaConverter.java` | `catalogs` 不含 `REF_` 目录；字段与地址组件保留 `catalogCode=REF_*` |
| `…-core/…/service/processor/ReplaceClientClassificationProcessor.java` | 去掉 `CatalogItemRepository`；锁内、状态与已确认检查之后、`classifications.replace`（事务）之前调用 `checkReferenceCodes`：行业须存在、ACTIVE、`level=2`，MCC 须存在、ACTIVE；否则 `RequestBodyValidationException(CLASSIFICATION_INVALID, errors)`（422），`errors[]` 逐行给出 `items[i].industryCode` / `items[i].mccCode`，消息区分“行业不存在 / 行业未启用 / 须选择细类（大类）/ MCC 不存在 / MCC 未启用”并带编码（应 ops-fe 要求定位到行与控件）。`validate()` 不变 |
| `…-app/…/configuration/ClientDraftConfiguration.java` | `referenceDataClient` Bean：`platformTransport` + `HttpServiceClientOptions.strict().withStrictJsonResponses(8192)` + `InternalCallTokenOptions`（目标 `platform-support-service`） |
| `…-app/…/configuration/ClientSubmissionConfiguration.java`、`ClientReviewConfiguration.java` | 注入 `ReferenceDataClient` |
| `…-app/…/ext/rest/ClientClassificationAdminController.java` | 删除 `options` 与目录依赖 |
| `…-api/…/ClientClassificationAdminApi.java` | 删除 `GET /classification-options`；更新 O03 错误说明（422 `errors[]` 字段路径与消息、503） |
| `…-api/…/response/ClassificationOptionsResponseBody.java`、`CatalogOptionResponseBody.java` | 删除（client-master 内已无使用者） |
| `…-api/…/FormSchemaResponseBody.java`、`ClientOnboardingCatalogApi.java` | JavaDoc：`catalogs` 不含 `REF_` 目录与国家选项，由 client-console-bff 向 Platform 补全 |
| `…-api/…/ClientSubmissionApi.java`、`request/ClassificationItemBody.java` | JavaDoc：参考数据校验与 503 |

### 运营入驻配置：辖区地址组件（应 ops-fe 冒烟发现 F-5，用户已同意）

| 文件 | 改动 |
|---|---|
| `…-api/…/onboardingadmin/response/AdminJurisdictionResponseBody.java` | 新增 `addressComponents: List<AddressComponentItem{component, catalogCode, required, order}>`：辖区本地地址格式的组件，按展示顺序；未配置格式时为空列表；`required` 不叠加 POSTAL_CODE_REQUIRED |
| `…-core/…/domain/onboardingadmin/JurisdictionAdminDetail.java`、`…/service/impl/OnboardingAdminQueryServiceImpl.java` | 一次读出全部辖区本地格式的组件（`AddressFormatComponentRepository.findByFormats`），按格式分组 |
| `…-app/…/configuration/ClientOnboardingAdminConfiguration.java`、`…/ext/rest/OnboardingAdminConverter.java` | 注入仓储、转换字段 |
| 测试 `OnboardingAdminQueryTest.test_jurisdictionPacks` | 断言 AE 组件顺序、`stateOrProvince` 为 `REF_COUNTRY_SUBDIVISION`、required、order，EG 同样引用 |

### 数据与文档

| 文件 | 改动 |
|---|---|
| `docs/database/sql/changes/2026-10-27-reference-data-catalog.sql`（新）+ `-rollback.sql`（新） | 第 6.3 节 DML 与回退，前后计数输出，可重复执行 |
| `docs/database/sql/changes/2026-10-27-reference-data-catalog-comment.sql`（新） | 仅注释 DDL（见下） |
| `docs/database/sql/client-master-initial-data.sql` | 删除 48 行目录（109 → 61）；8 行 `business.currency` → `REF_CURRENCY`；2 行地址组件 → `REF_COUNTRY_SUBDIVISION`；文件头说明 |
| `docs/database/client-master.pdma.json`、`sql/client-master-schema.sql`、测试 `init/ddl.sql` | `t_catalog_item` 表注释与 `t_catalog_item` / `t_address_format_component` / `t_onboarding_schema_field` 的 `catalog_code` 列注释：平台参考数据不得在本表维护，`REF_` 码引用 Platform（直接编辑，未用生成器） |
| `docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md` | 分类表一段补充 Platform 行业 / MCC 归属与旧一级行业可读 |
| 需求目录 `db/client-master/README.md`（新） | 指向上述权威 SQL 与本地执行记录（不放副本） |

`2026-10-27-onboarding-country-pack.sql`（历史增量）未改；本 DML 之后不得重跑它（会重新插入 48 行目录项），已写入新 DML 文件头与 README。

### 测试

| 文件 | 改动 |
|---|---|
| `test/mock/external/platform/MockPlatformReferenceDataApi.java`（新） | 进程内实现 5 个 QueryApi；只接受 `codes`（其他筛选参数、MCC 非 `page=1,size=50`、目录未配置 → 意外调用）；按配置返回 DTO；故障返回 Problem 503 |
| `test/mocker/platform/ReferenceDataMocker.java`（新） | 按目录配置条目（状态、行政区国家、行业层级）、`remove`、`mockUnavailable`、调用记录（目录 + `codes` 原文）、`Clear` |
| `MockPlatformAuthenticationApi`、`ClientMasterTestApplication` | 登记 `/platform-support` 前缀与扫描包 |
| `ClientMasterMockTestBase` | 全局 BeforeEach 公共基线 `referenceData.mockStandardCatalog()`（同 `agreementParties.mockNoReferences()` 先例） |
| `auto/architecture/ReferenceCatalogGuardTest.java`（新） | 读取初始化 DML 与 `zz-pack.sql` 中 `t_catalog_item` 的**全部** INSERT：不得出现 `INDUSTRY / CURRENCY / STATE_OR_PROVINCE` 或 `REF_*` |
| `SubmitReferenceDataTest.java`（新） | 见第 3 节 |
| `ClientClassificationAdminTest.java` | 改用细类；删除 `test_options`；新增只选大类 / 停用细类、MCC 不存在 / 停用、Platform 不可用、旧一级行业可读 |
| `DraftAddressFormatTest` | `AE_DU` → `AE-DU`；新增 `test_subdivisionFormat` |
| `OnboardingFormSchemaTest`、`OnboardingAdminQueryTest`、`PeopleWebBodyTest`、`ClientClassificationHandlerTest`、`NewJurisdictionByDataTest`、`OnboardingTestMapper`、`zz-pack.sql` | 按新目录码与 ISO 格式修正；ZZ 用例在本例配置国家 ZZ |

`SubmitClientApplicationTest`（F-17 第 255 行，`SA_RIYADH` 格式不合法 → 不发送 → 不在目录）与 `AbstractClientReviewTest` 无需修改。

## 2. SQL 执行（本地 `mor_client_master`，用户已授权）

- 备份：`/Users/kenny/mor-backups/refdata-20261027/mor_client_master.sql.gz`（29 KB；执行前本地各目录表为空——测试每例清空）。
- 为了有可验证的数据，先装入改动前的初始化 DML，并插入 4 行合成地址，再执行变更、重复执行、回退、再执行。计数（脚本自身输出）：

| 步骤 | 退役目录项 | CURRENCY→REF 字段 | 地址组件 | 下划线地址 | ISO 地址 |
|---|---|---|---|---|---|
| 执行前 | 48 | 8 | 2 | 2 | 0 |
| 执行后 | 0 | 8 (REF) | 2 (REF) | 0 | 2 |
| 重复执行 | 0 | 不变 | 不变 | 0 | 2 |
| 回退后 | 48 | 8 (CURRENCY) | 2 | 2 | – |
| 再次执行 | 0 | 8 (REF) | 2 (REF) | 0 | 2 |

  `London`、小写 `ae_du` 不被改动。验证后删除了 4 行合成地址。
- 注释 DDL 已在本地执行，`information_schema` 核对注释已更新、列类型 / 可空 / 排序规则不变。
- 新初始化 DML 的可执行性由测试验证：`JurisdictionPackMocker` 每例清空目录表并执行它（152 个用例全部经过）。我曾尝试在本地手动清空目录表后重新装入，被权限拦截，未执行。
- mor-test 未执行（本阶段不涉及远端环境）。

## 3. 测试

环境：`set -a; source local-environment/.env.local`（库、Redis、RabbitMQ 凭据经 `application-mock.yml` 的 `MOR_*` 环境变量传入）；`JAVA_HOME=jdk-25`；`.mvn/maven.config` 指向 `~/.m3-refdata`。只跑 client-master 四个模块（同一 reactor，未 install 到私有仓库）。

```
./mvnw -o -pl client-master/paytech-client-master-api,client-master/paytech-client-master-core,client-master/paytech-client-master-app,client-master/paytech-client-master-test test
```

| 运行 | 结果 |
|---|---|
| 定向（Guard、JurisdictionLiteral、ClientClassificationAdmin、SubmitReferenceData、DraftAddressFormat、OnboardingFormSchema） | 28 / 28 通过 |
| 全模块第 1 次 | 152 个，1 失败：`NewJurisdictionByDataTest.test_zzFullChain`（地址国家 ZZ 不在参考数据基线）→ 在该用例配置国家 ZZ |
| 全模块第 2 次 | **152 个，0 失败，0 错误**，BUILD SUCCESS |
| 改为逐行 `errors[]` 后定向重跑（ClientClassificationAdmin、ClientClassificationHandler、ClientReviewResultListener） | 15 / 15 通过 |
| 加入 `addressComponents` 后全模块 | **152 个，0 失败，0 错误** |

新增场景覆盖（真实 HTTP 入口 → 真实出站客户端 → 测试 HTTP 端点）：

| 场景 | 用例 |
|---|---|
| 合规申请：国家 / 币种 / 行政区各查一次，只带本次编码，无参考数据问题 | `SubmitReferenceDataTest.test_normalFlow` |
| 国家不存在：国籍、出生国、证件签发国逐项报错，C14 422，无写入 | `test_countryNotFound` |
| 国家与币种停用：地址国家、居住国、COUNTRY 扩展属性、`business.currency` 报错 | `test_countryAndCurrencyInactive` |
| 行政区不存在 / 停用 | `test_subdivisionNotFound` |
| 行政区与地址国家不一致（AE 地址填 EG-C） | `test_subdivisionCountryMismatch` |
| Platform 5xx：核对与 C14 都 503，申请仍 DRAFT，无提交编号、无审核登记 | `test_platformUnavailable` |
| 分类只选大类 / 未知 / 停用细类 → 422，`errors[]` 逐行字段与原因 | `ClientClassificationAdminTest.test_industryNotDetail` |
| 未知 / 停用 MCC → 422，`errors[]` 逐行字段与原因 | `test_mccNotInCatalog` |
| Platform 不可用 → 503，原分类不变 | `test_platformUnavailable` |
| 正常保存（每次保存行业、MCC 各查一次）、确认、交付 | `test_normalFlow` |
| 旧一级行业可读，GET 不查 Platform | `test_legacyIndustryReadable` |
| 输入校验失败不查 Platform | `test_invalidParameter` |
| 草稿保存只校验 ISO 格式、境外省州自由文本、不查 Platform | `DraftAddressFormatTest.test_subdivisionFormat` |
| form-schema `catalogs` 不含 `REF_`，组件为 `REF_COUNTRY_SUBDIVISION` | `OnboardingFormSchemaTest.test_aeOrganizationDefault` |
| 守卫 | `ReferenceCatalogGuardTest` |

意外调用（未配置目录、多余筛选参数）由 `ownerServer.assertNoUnexpectedRequests` 在全局 AfterEach 检查；`ReferenceDataMocker` 纳入每例清理。

## 4. 写接口示例第 11 节核对

### O03 分类保存（`PUT /client-onboarding-admin/v1/cases/{caseId}/classifications`）

- **API / Body**：`ClientClassificationAdminApi.replace`，`ClassificationSetRequestBody` / `ClassificationItemBody`（class + Hibernate Validator：行业 `^[A-Z][A-Z0-9_]{0,63}$`、MCC `^[0-9]{4}$`）；入口 `@Valid`。只改 JavaDoc。
- **Controller**：`ClientClassificationAdminController.replace` 只做协议转换，删除了 `options` 与仓储依赖。
- **Processor**：`ReplaceClientClassificationProcessor`（`AbstractLockProcessor`），`validate()` 仍只做输入组合判断（锁前、无外部调用）；`processWithinLock`：当前任务与状态 → 已确认检查 → `checkReferenceCodes`（Platform，事务外）→ `classifications.replace`（`ClientClassificationServiceImpl` 内的写事务）→ 响应。没有新增 Context，沿用 `ClientClassificationContext`。
- **事务位置**：唯一事务在 `ClientClassificationServiceImpl.replace`；Platform 调用在它之前，且 `ReferenceDataClientImpl.query` 在事务内调用会直接抛错。Platform 失败时不进入 Service，不写入。
- **Repository / 并发**：未改；确认后的修改仍由原条件更新最终拒绝。
- **锁**：沿用 `client-onboarding:review-case:{caseId}`（与 O04 一致），未叠加其他机制。
- **原请求**：整体替换语义，按申请状态防重；不涉及 Idempotency-Key。
- **View / DataObject**：无新增；`ReferenceDataItem` 是集成边界的领域值，不出模块。
- **配置**：客户端集中在 `ClientDraftConfiguration.referenceDataClient`，无生产 Mock。
- **测试**：见第 3 节。

### C14 提交（`POST /client-onboarding/v1/applications/{id}/submissions`）及提交核对、需求清单

- **API**：`ClientSubmissionApi.submit / check` 只改 JavaDoc。
- **Processor**：`SubmitClientApplicationProcessor` 未改；参考数据校验放在它调用的 `ClientSubmissionPreparer.prepare` 中（`buildContext` 阶段，本地事务 `submissions.submit` 之前）。核对（`CheckClientSubmissionProcessor`）与需求清单（`GetApplicationRequirementsProcessor`）共用同一准备步骤，结论同源。
- **事务外依赖**：`prepare` 原本就在事务外调用 compliance、commercial、Platform 文件；新增的 3 次 Platform 参考数据查询（国家、币种、行政区各 1 次，无值的类型不发请求）在同一阶段。
- **纯计算**：`OnboardingRequirementEvaluator` 仍不访问外部服务，只读 `ReferenceCodeCheck`（用完即弃，不缓存）。
- **失败**：参考数据不合规 → `SUBMISSION_INCOMPLETE`（422），逐项 `FIELD_VALUE_NOT_IN_CATALOG`；Platform 不可用 → 503，不写入（测试核对状态、提交编号与补偿任务）。
- **Context / Repository / 锁**：未改。
- **草稿保存**：`SaveClientDraftProcessor` 未改；`ClientDraftPreparer` 只加格式校验（无外部调用）。

## 5. 偏差与遗留

1. **ops-bff 编译依赖**：删除的 `ClassificationOptionsResponseBody`、`CatalogOptionResponseBody` 仍被 ops-bff 引用（`ClientReviewCctClient(+Impl).options`、`ClientReviewDetailsProcessor`、`ClientReviewModels`）。在 ops-fe 改为 Platform 行业树之前，ops-bff 不能针对新的 client-master-api 编译。我没有把 client-master-api install 到 `~/.m3-refdata`，以免中途打断 ops-fe 的构建；已通知主会话。
2. **Platform 编码格式（已与 pf-dev 确认）**：五类编码格式与 Platform 校验一致；MCC `size=50` 合法。Platform 把空 `codes` 视为不筛选，尾随或连续逗号报 400，超过 50 个报 400。本客户端在集合为空时不发请求，只拼接非空、格式合法、去重后的编码，每批 ≤50，三种情况都不会出现。Platform 会去空格、转大写；本客户端只发送已经是大写的编码，小写取值按不存在处理（草稿保存已把国家、币种规范为大写，行政区要求 ISO 大写格式）。
3. **测试 Mock 基线**：参考数据基线在全局 BeforeEach 配置（与 commercial 基线做法相同）。UNIT_TEST_GUIDE 10.3 建议不要预设外部成功；这里只预设"目录内容"，未配置的目录、多余参数和故障仍按意外调用或显式故障处理。
4. **扩展属性路径**：Schema 之外的 COUNTRY 扩展属性也会被查询，但只有 Schema 字段报错（求值器只评估 Schema 字段，与改动前一致）。
5. **ops 入驻配置页**（F-15）：`/client-master-admin/v1/catalog-items?catalogCode=REF_*` 返回空列表；ops-fe 从辖区的 `addressComponents` 与 Schema 字段识别 REF_ 目录，"来自 Platform"的标注由 ops-fe 负责。
6. **历史变更文件**：`2026-10-27-onboarding-country-pack.sql` 未改，在本 DML 之后重跑会恢复 48 行目录项（已在文件头和 README 写明）。
7. **生产数据**：存量地址转换只覆盖 AE / EG 下划线码。mor-test 发布时需要用本脚本的前后计数核对，并与新镜像同一窗口执行。
