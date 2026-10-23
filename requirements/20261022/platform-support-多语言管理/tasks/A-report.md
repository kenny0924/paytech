# 任务卡 A 报告：Platform 多语言模块（后端核心）

日期：2026-10-23。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n`（detached，基于 main `22ede435`），未提交、未推送、未执行 SQL、未动 local-environment 容器，没有运行 Platform HTTP 入口测试。

## 0. 结论

- 新模块 `paytech-platform-support-localization` 已完成：7 张表的 DDL / DataObject / Mapper / Repository，ICU 校验，key 导入，翻译保存 / 导入 / 导出，发布三步（第 1 步生成逻辑 + 端口，第 2 步登记，第 3 步补偿 Handler），切换当前发布，发布历史 / 详情 / 差异，manifest（按发布 ID 缓存）。
- 24 个接口的 `@HttpExchange` 合同、Controller 与 Processor 已完成并编译通过。同一工作目录里 B 卡的 BFF 代码已经按本合同编译通过。
- 纯 Java 测试 31 例全部通过。Platform HTTP 入口测试 4 类 18 例只编译、未运行。
- 语言包存储（`ReleaseBundleStore`）与读取（`ReleaseBundleReader`）两个端口只有接口，没有实现类。所以本卡交付后，发布、差异与"已有发布时的 manifest"返回 503，留给集成卡接入。

开工前已实际读取：AGENTS.md、DEVELOPMENT_CONVENTIONS.md（全文）、JAVA_PROJECT_STRUCTURE.md、examples/WRITE_API_EXAMPLE.md、UNIT_TEST_GUIDE.md、examples/UNIT_TEST_EXAMPLE.md、DATABASE_DESIGN_GUIDE.md 第 5、8–11 节、COMPENSATION_EVENT_GUIDE.md、decisions/2026-10-22-file-upload-address-principles.md，以及 requirement.md、technique.md（v3）、data-query 与 workflow 模块源码。

## 1. 与任务卡的偏差（需要主会话知悉）

1. **Processor 放在 localization 模块，不在 app。** 任务卡写"Processor、Controller 放 app"。现有工程里 app 没有任何 Processor：data-query 的 Processor 在 `paytech-platform-support-core`，workflow 的 Processor 与 Handler 在自身业务模块。开发约定 5.2 要求 Processor 位于所属业务模块的 `service/processor`。本卡按 workflow 先例放在 `localization/service/processor`（补偿 Handler 在 `localization/service/handler`），Controller 在 app 的 `ext/rest/localization`。
2. **写接口的操作人 Header 用 `X-Gateway-Actor-Subject` + `X-Gateway-Session-Id`，不是 `X-Operator-Account-Id`。** 任务卡写"带 X-Operator-Account-Id（参照 data-query 的做法）"，但 data-query 实际使用这对 Gateway 可信 Header，并在 Platform 内按会话复核权限。`X-Operator-Account-Id` 只在内部文件接口出现，Platform 不核对其真实性，无法支撑 I18N_VIEW / I18N_EDIT / I18N_PUBLISH 三项权限。本卡选择与 data-query 一致；23 个管理接口全部带这对 Header，查询也复核权限。若主会话坚持 `X-Operator-Account-Id`，需要同时决定权限如何校验。
3. **移除了 Platform 父 POM 对 `paytech-caffeine-starter` 的 Enforcer 禁令。** 任务卡要求用 Foundation Caffeine 做 manifest 缓存，但 `paytech/platform-support/pom.xml` 的 bannedDependencies 禁止它（2026-09-01 应用基座时设定的范围限制）。2026-10-20 币种决定拒绝 Caffeine 的理由（过期回源可能落在事务内、Platform 不可用丢数据）不适用于这里：缓存在 Platform 自身、只缓存不可变发布的地址、回源在事务外。已删除该行 exclude，并在 app / mock 配置中登记 `caffeine.cache-names: [localization-manifest]`。
4. **验证命令改用私有本地仓库。** 任务卡第 1 条命令 `install -am` 会把本工作目录构建的全部上游 SNAPSHOT 装进共用 `~/.m3`。文件多存储并存 A 卡报告第 11 条记录过同样操作被列为误操作。本卡所有 Maven 命令都加了 `-Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository`：安装只进私有仓库，共用仓库只读。
5. **第 3 条命令按原样在本环境无法通过，原因与本卡无关。** `-pl platform-support -amd test-compile` 会编译 client-master、BFF、acquiring、card、billing、ledger 等 33 个模块。它们依赖的 `client-master-api`、`risk-decision-api` 等不在该 reactor 内，从仓库解析到的是共用 `~/.m3` 中的旧快照（例如 `ClientDocumentModels.DocumentReferenceRequestBody` 缺 `fileAddress()`），client-master-core 编译失败。本卡改为把这 33 个模块连同全部上游放进同一个 reactor 编译（`-pl <33 个模块> -am test-compile`），结果通过。
6. **manifest 缺省 `locale` 时只返回 en 的语言包。** 技术方案只规定 locale 参数裁剪 bundles，未定义缺省行为；本卡定为只含 en。
7. **翻译导出 / 导入文件格式定为 `code-mor-i18n-translation/1`**，结构为 `{format, application, locale, namespaces:{命名空间:{完整 key: 翻译}}}`。技术方案说"与上面相同结构"，但 key 导入格式没有 locale 字段，值也可以是对象。本卡加 `locale`、值只允许字符串。导出再原样导入不产生写入（AC-09），有 HTTP 测试覆盖。
8. **ICU 校验额外拒绝 choice 参数与 number / date / time 以外的简单类型。** ICU4J `MessagePattern` 能解析 `{n, choice, ...}` 与 `{x, currency}`，但前端选定的 intl-messageformat 无法格式化它们，发布后会显示异常。若主会话认为这超出 B5，删掉 `IcuMessageValidator.argumentProblem` 的前两个分支即可。
9. **只有发布与切换写审计。** 技术方案只规定 `I18N_RELEASE_PUBLISH` 与 `I18N_RELEASE_ACTIVATE` 两类审计，其余写接口没有加 `@Audit`。data-query 与 workflow 的全部写接口都有审计；是否补齐请主会话决定。

## 2. API 合同（`paytech-platform-support-api`，包 `com.paytech.platform.support.api.localization`）

**公共约定**

- 路径前缀 `/internal/localization/v1`；Platform `context-path` 为 `/platform-support`。
- 23 个管理接口都带 Header `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；manifest 不带身份。
- 权限：查询、预检与导出要 `I18N_VIEW`；语言、应用、命名空间、导入与翻译写入要 `I18N_EDIT`；发布与切换要 `I18N_PUBLISH`。无权限返回 403 `OPERATIONS_PERMISSION_DENIED`。
- ID 与时间都是字符串（UTC ISO-8601 微秒）；枚举字段以 Code 字符串传输。
- 写接口持资源锁：同一应用串行，语言按语言代码串行（TRY_LOCK 500ms）。锁冲突与 `expectedVersion` 不符都返回 409 `CONCURRENT_REQUEST`。
- 新增错误码 `LocalizationErrorCode`（`api/enums`）：`I18N_COVERAGE_INSUFFICIENT` 422、`I18N_IMPORT_PREVIEW_STALE` 409、`I18N_CONTENT_CHANGED` 409、`I18N_NAMESPACE_NOT_FOUND` 422。HTTP 状态映射加在 `PlatformApiErrorConfiguration`。其余失败使用公共 `BUSINESS_CHECK_FAILED` 400、`INVALID_REQUEST` 400、`VALIDATION_ERROR` 422、`NOT_FOUND` 404，以及 503。
- 字段级错误使用 Problem 的 `errors[{field,message}]`，最多 200 条。

| # | 接口类.方法 | 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|---|---|
| 1 | LocalizationCatalogApi.listLocales | GET | `/locales` | — | LocaleListResponseBody |
| 2 | LocalizationCatalogApi.createLocale | POST | `/locales` | CreateLocaleRequestBody | LocaleResponseBody |
| 3 | LocalizationCatalogApi.updateLocale | PUT | `/locales/{localeCode}` | UpdateLocaleRequestBody | LocaleResponseBody |
| 4 | LocalizationCatalogApi.listApplications | GET | `/applications` | — | ApplicationListResponseBody |
| 5 | LocalizationCatalogApi.getApplication | GET | `/applications/{applicationCode}` | — | ApplicationDetailResponseBody |
| 6 | LocalizationCatalogApi.createApplication | POST | `/applications` | CreateApplicationRequestBody | ApplicationResponseBody |
| 7 | LocalizationCatalogApi.updateApplication | PUT | `/applications/{applicationCode}` | UpdateApplicationRequestBody | ApplicationResponseBody |
| 8 | LocalizationCatalogApi.listNamespaces | GET | `/applications/{applicationCode}/namespaces` | — | NamespaceListResponseBody |
| 9 | LocalizationCatalogApi.createNamespace | POST | `/applications/{applicationCode}/namespaces` | CreateNamespaceRequestBody | NamespaceResponseBody |
| 10 | LocalizationCatalogApi.updateNamespace | PUT | `/applications/{applicationCode}/namespaces/{namespaceCode}` | UpdateNamespaceRequestBody | NamespaceResponseBody |
| 11 | LocalizationCatalogApi.deleteNamespace | DELETE | `/applications/{applicationCode}/namespaces/{namespaceCode}?expectedVersion=` | — | 200 无正文（void） |
| 12 | LocalizationContentApi.previewKeyImport | POST | `/applications/{applicationCode}/key-imports/previews` | PreviewKeyImportRequestBody | KeyImportPreviewResponseBody |
| 13 | LocalizationContentApi.confirmKeyImport | POST | `/applications/{applicationCode}/key-imports` | ConfirmKeyImportRequestBody | KeyImportResultResponseBody |
| 14 | LocalizationContentApi.listTranslations | GET | `/applications/{applicationCode}/translations?locale=&namespaceCode=&state=&keyword=&page=&size=` | — | PagingResponseBody\<TranslationEntryResponseBody\> |
| 15 | LocalizationContentApi.saveTranslations | POST | `/applications/{applicationCode}/translations/batch` | SaveTranslationsRequestBody | SaveTranslationsResponseBody |
| 16 | LocalizationContentApi.exportTranslations | GET | `/applications/{applicationCode}/translations/export?locale=` | — | TranslationFileResponseBody |
| 17 | LocalizationContentApi.importTranslations | POST | `/applications/{applicationCode}/translations/imports` | ImportTranslationsRequestBody | ImportTranslationsResponseBody |
| 18 | LocalizationReleaseApi.precheckRelease | GET | `/applications/{applicationCode}/release-precheck` | — | ReleasePrecheckResponseBody |
| 19 | LocalizationReleaseApi.publishRelease | POST | `/applications/{applicationCode}/releases` | PublishReleaseRequestBody | ReleaseSummaryResponseBody（status=PENDING_CHECK） |
| 20 | LocalizationReleaseApi.listReleases | GET | `/applications/{applicationCode}/releases?page=&size=` | — | PagingResponseBody\<ReleaseSummaryResponseBody\> |
| 21 | LocalizationReleaseApi.getRelease | GET | `/applications/{applicationCode}/releases/{releaseNo}` | — | ReleaseDetailResponseBody |
| 22 | LocalizationReleaseApi.diffRelease | GET | `/applications/{applicationCode}/releases/{releaseNo}/diff?baseReleaseNo=` | — | ReleaseDiffResponseBody |
| 23 | LocalizationReleaseApi.activateRelease | POST | `/applications/{applicationCode}/current-release` | ActivateReleaseRequestBody | CurrentReleaseResponseBody |
| 24 | LocalizationDeliveryApi.getManifest | GET | `/applications/{applicationCode}/manifest?locale=&releaseNo=` | — | ManifestResponseBody |

**主要错误语义**

- 3：停用 en，或停用被应用用作默认 / 必须覆盖语言的语言，返回 400。
- 6、7：语言不存在或未启用、必须覆盖语言重复，返回 400。
- 11：其下还有任何 key（含已废弃）时返回 400。
- 12、13：文件不存在、原因码不符、未通过检查或超过 2 MiB，返回 400。
- 13：预览后有人导入过返回 409 `I18N_IMPORT_PREVIEW_STALE`。有未创建的命名空间返回 422 `I18N_NAMESPACE_NOT_FOUND`，field 为 `namespaces.{编码}`。格式、前缀或 ICU 问题返回 422 `VALIDATION_ERROR`。
- 14：`state` 取 `UNTRANSLATED`、`SOURCE_CHANGED` 或 `TRANSLATED`；locale 为 en 或未启用时返回 400。
- 15：ICU 问题返回 422，field 为 `translations[i].text`，消息带“第 N 个字符”；key 不存在或已废弃时 field 为 `translations[i].keyCode`；任一条版本不符返回 409。
- 17：任一条失败整体返回 422，field 为 `namespaces.{ns}.{key}`。
- 19：覆盖率不足返回 422 `I18N_COVERAGE_INSUFFICIENT`，errors 逐条为 `missing.{locale}`，最后一条 field=`missingTotal`。存在 ICU 问题返回 422；没有 ACTIVE key 返回 400；生成期间文案被改返回 409 `I18N_CONTENT_CHANGED`；存储端口未接入返回 503。
- 22：读取端口未接入返回 503。
- 23：目标不是 READY 返回 400；已是当前发布时不写入。
- 24：应用不存在或停用、指定发布不是 READY 返回 404；没有 READY 发布时 `releaseNo` 为 null、`bundles={}`；存储端口未接入且已有发布时返回 503。

**请求体字段**

- `CreateLocaleRequestBody`：localeCode（BCP 47，如 en、zh-CN，≤35）、nativeName（≤64）、englishName（≤64）、textDirection（LTR / RTL）、sortOrder（0–9999）。
- `UpdateLocaleRequestBody`：expectedVersion、nativeName、englishName、textDirection、status（ENABLED / DISABLED）、sortOrder。
- `CreateApplicationRequestBody`：applicationCode（`[a-z][a-z0-9-]{1,63}`）、applicationName（≤128）、defaultLocale、requiredLocales（List，≤20）、coverageThresholdPercent（0–100）。
- `UpdateApplicationRequestBody`：expectedVersion、applicationName、defaultLocale、requiredLocales、coverageThresholdPercent。
- `CreateNamespaceRequestBody`：namespaceCode（`[a-z][a-z0-9-]{0,63}`，不含点号）、namespaceName（≤64）、description（≤512，可空）、sortOrder。
- `UpdateNamespaceRequestBody`：expectedVersion、namespaceName、description、sortOrder。
- `PreviewKeyImportRequestBody`：fileId（十进制字符串）。
- `ConfirmKeyImportRequestBody`：fileId、previewDigest（64 位小写十六进制）。
- `SaveTranslationsRequestBody`：locale、translations（1–200 条 `TranslationInput`）。
- `TranslationInput`：keyCode（≤191）、text（非空白，≤8000）、expectedVersion（0 表示新建）。
- `ImportTranslationsRequestBody`：fileId、locale。
- `PublishReleaseRequestBody`：releaseNote（≤512，可空）。
- `ActivateReleaseRequestBody`：releaseNo（≥1）、reason（≤256，写审计）。

**响应体字段**（全部为 Lombok `@Getter @Setter` 普通 class）

- `LocaleResponseBody`：localeCode:String、nativeName:String、englishName:String、textDirection:String、status:String、sortOrder:int、version:int、updatedBy:String、updateTime:String
- `LocaleListResponseBody`：items:List<LocaleResponseBody>
- `ApplicationResponseBody`：applicationCode:String、applicationName:String、defaultLocale:String、requiredLocales:List<String>、coverageThresholdPercent:int、status:String、currentReleaseNo:Integer、keySetRevision:int、contentRevision:int、version:int、updatedBy:String、updateTime:String
- `ApplicationListResponseBody`：items:List<ApplicationResponseBody>
- `LocaleCoverage`：locale:String、required:boolean、totalKeys:int、translatedKeys:int、sourceChangedKeys:int、coveragePercent:int、satisfied:boolean
- `ApplicationDetailResponseBody`：application:ApplicationResponseBody、currentRelease:ReleaseSummaryResponseBody、activeKeyCount:long、deprecatedKeyCount:long、namespaceCount:int、coverage:List<LocaleCoverage>
- `NamespaceResponseBody`：namespaceCode:String、namespaceName:String、description:String、sortOrder:int、activeKeyCount:long、deprecatedKeyCount:long、version:int、updatedBy:String、updateTime:String
- `NamespaceListResponseBody`：items:List<NamespaceResponseBody>
- `LocalizationProblem`：field:String、message:String
- `KeyImportItem`：namespaceCode:String、keyCode:String、sourceText:String、previousSourceText:String、description:String
- `KeyImportPreviewResponseBody`：importable:boolean、previewDigest:String、keySetRevision:int、added:List<KeyImportItem>、sourceChanged:List<KeyImportItem>、deprecated:List<KeyImportItem>、restored:List<KeyImportItem>、unchangedCount:int、missingNamespaces:List<String>、problems:List<LocalizationProblem>
- `KeyImportResultResponseBody`：addedCount:int、sourceChangedCount:int、deprecatedCount:int、restoredCount:int、descriptionChangedCount:int、keySetRevision:int、contentRevision:int
- `TranslationEntryResponseBody`：namespaceCode:String、keyCode:String、sourceText:String、description:String、parameters:List<String>、text:String、version:int、sourceChanged:boolean、updatedBy:String、updateTime:String
- `SavedTranslation`：keyCode:String、version:int
- `SaveTranslationsResponseBody`：translations:List<SavedTranslation>、contentRevision:int
- `TranslationFileResponseBody`：format:String、application:String、locale:String、namespaces:Map<ns,Map<key,text>>
- `ImportTranslationsResponseBody`：updatedCount:int、unchangedCount:int、emptyCount:int、contentRevision:int
- `LocaleKey`：locale:String、keyCode:String
- `ReleasePrecheckResponseBody`：publishable:boolean、contentRevision:int、nextReleaseNo:int、activeKeyCount:int、coverage:List<LocaleCoverage>、missing:List<LocaleKey>、missingTotal:int、sourceChanged:List<LocaleKey>、sourceChangedTotal:int、problems:List<LocalizationProblem>
- `ReleaseSummaryResponseBody`：releaseNo:int、status:String、current:boolean、keyCount:int、releaseNote:String、failureReason:String、createdBy:String、createTime:String、readyTime:String
- `ReleaseBundleSummary`：locale:String、namespaceCode:String、fileAddress:String、contentHash:String、byteSize:int
- `ReleaseReport`：missing:List<LocaleKey>、sourceChanged:List<LocaleKey>
- `ReleaseDetailResponseBody`：release:ReleaseSummaryResponseBody、basedOnContentRevision:int、coverage:List<LocaleCoverage>、report:ReleaseReport、bundles:List<ReleaseBundleSummary>
- `ReleaseTextChange`：locale:String、keyCode:String、before:String、after:String
- `ReleaseDiffResponseBody`：releaseNo:int、baseReleaseNo:Integer、added:List<ReleaseTextChange>、changed:List<ReleaseTextChange>、removed:List<ReleaseTextChange>
- `CurrentReleaseResponseBody`：applicationCode:String、releaseNo:int
- `ManifestLocale`：code:String、nativeName:String、direction:String
- `ManifestBundle`：hash:String、url:String
- `ManifestResponseBody`：application:String、releaseNo:Integer、publishedAt:String、locales:List<ManifestLocale>、defaultLocale:String、bundles:Map<String, Map<String, ManifestBundle>>

**导入文件格式**

- key 导入：`{"format":"code-mor-i18n-source/1","application":"client-console","namespaces":{"team":{"team.invite.submit":"Send invitation","team.member.count":{"text":"…","description":"…"}}}}`。规则：key 必须以“命名空间编码.”开头；JSON 重复键被拒绝。
- 翻译导入 / 导出：`{"format":"code-mor-i18n-translation/1","application":"…","locale":"zh-CN","namespaces":{"team":{"team.invite.submit":"发送邀请"}}}`。空字符串表示不改动。

## 3. 七张表字段清单（供 PDManer 图组 `localization`）

未改 `platform-support.pdma.json`。DDL 已写入 `platform-support-schema.sql`（末尾）、`docs/database/sql/changes/2026-10-23-localization.sql`（含预检、执行顺序与 7 条 DROP 回滚）与测试 `init/ddl.sql`。

三处 DDL 由同一份字段表生成，内容一致：

- 不建 CHECK / FK；全部 `DATETIME(6)`、utf8mb4_0900_ai_ci。
- 编码类列（语言代码、应用 / 命名空间编码、key、文件地址、摘要）用 `ascii_bin`，大小写敏感。
- 状态列 `VARCHAR`，由 `CodeEnumTypeHandler` 映射；JSON 列用 MySQL `JSON`。

#### `t_i18n_locale`（I18nLocale）

表注释：保存系统支持的界面语言目录：BCP 47 代码、显示名、文字方向与启停状态；en 为全系统基准语言且不可停用。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `locale_id` | BIGINT | 否 | 服务侧生成的语言 Snowflake 主键。 |
| `locale_code` | VARCHAR(35) ascii_bin | 否 | BCP 47 语言代码，如 en、zh-CN；全局唯一，创建后不可修改。取值来源：IETF BCP 47；非固定枚举。 |
| `native_name` | VARCHAR(64) | 否 | 本语言的自称，如“简体中文”，用于语言切换显示。 |
| `english_name` | VARCHAR(64) | 否 | 语言的英文名称，如 Simplified Chinese。 |
| `text_direction` | VARCHAR(8) | 否 | 文字方向；本期只登记 LTR 语言，RTL 仅预留。允许值：LTR、RTL。 |
| `status` | VARCHAR(16) | 否 | 语言状态；DISABLED 的语言不能被选为默认或必须覆盖语言，也不出现在 manifest 中，en 不能停用。允许值：ENABLED、DISABLED。 |
| `sort_order` | INT | 否 | 显示排序值，升序排列，取值 0 至 9999。 |
| `version` | INT NOT NULL DEFAULT 1 | 否 | 语言乐观并发版本，从 1 开始，每次修改加一。 |
| `created_by` | BIGINT | 否 | 创建该记录的操作人，取值为 t_user_account.account_id。 |
| `updated_by` | BIGINT | 否 | 最后修改该记录的操作人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 |
| `update_time` | DATETIME(6) | 否 | 该可变记录最后一次持久化更新的 UTC 微秒时间。 |

主键 `locale_id`；唯一索引 `uk_i18n_locale_code`(locale_code)。

#### `t_i18n_application`（I18nApplication）

表注释：保存语言包消费方应用：默认语言、必须覆盖语言与发布阈值、当前发布指针以及 key 集合与文案修订号；应用与前端渠道一一对应。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `application_id` | BIGINT | 否 | 服务侧生成的应用 Snowflake 主键。 |
| `application_code` | VARCHAR(64) ascii_bin | 否 | 全局唯一的应用编码，如 client-console；小写字母开头的小写字母、数字与连字符，创建后不可修改。 |
| `application_name` | VARCHAR(128) | 否 | 在 Operations 页面显示的应用名称。 |
| `default_locale` | VARCHAR(35) ascii_bin | 否 | 应用默认语言，取值为 ENABLED 的 t_i18n_locale.locale_code；取代需求中的渠道默认语言。 |
| `required_locales` | JSON | 否 | 必须覆盖的语言代码 JSON 数组，元素为 ENABLED 的 t_i18n_locale.locale_code、不重复、最多 20 个；发布时这些语言的覆盖率须达到阈值，可为空数组。 |
| `coverage_threshold_percent` | INT | 否 | 必须覆盖语言的翻译覆盖率阈值百分比，取值 0 至 100；translated × 100 ≥ 阈值 × total 才能发布。 |
| `current_release_id` | BIGINT | 是 | 当前发布，取值为状态 READY 的 t_i18n_release.release_id；NULL 表示还没有上线的发布，前端只用构建内置文案。 |
| `key_set_revision` | INT NOT NULL DEFAULT 0 | 否 | key 集合修订号，从 0 开始，每次确认 key 导入加一；导入预览摘要依据它判断预览是否过期。 |
| `content_revision` | INT NOT NULL DEFAULT 0 | 否 | 文案修订号，从 0 开始，每次导入 key、保存或导入翻译加一；发布登记时核对生成语言包期间它没有变化。 |
| `status` | VARCHAR(16) | 否 | 应用状态；DISABLED 时不提供 manifest。允许值：ENABLED、DISABLED。 |
| `version` | INT NOT NULL DEFAULT 1 | 否 | 应用设置的乐观并发版本，从 1 开始，修改名称、默认语言、必须覆盖语言或阈值时加一；修订号与当前发布变化不改变它。 |
| `created_by` | BIGINT | 否 | 创建该记录的操作人，取值为 t_user_account.account_id。 |
| `updated_by` | BIGINT | 否 | 最后修改该记录的操作人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 |
| `update_time` | DATETIME(6) | 否 | 该可变记录最后一次持久化更新的 UTC 微秒时间。 |

主键 `application_id`；唯一索引 `uk_i18n_app_code`(application_code)。

#### `t_i18n_namespace`（I18nNamespace）

表注释：保存应用内的命名空间：前端懒加载与语言包文件的粒度，在后台创建与维护；其下 key 必须以“编码.”开头。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `namespace_id` | BIGINT | 否 | 服务侧生成的命名空间 Snowflake 主键。 |
| `application_id` | BIGINT | 否 | 所属应用，取值为 t_i18n_application.application_id。 |
| `namespace_code` | VARCHAR(64) ascii_bin | 否 | 应用内唯一的命名空间编码，小写字母开头的小写字母、数字与连字符，不含点号，创建后不可修改。 |
| `namespace_name` | VARCHAR(64) | 否 | 在 Operations 页面显示的命名空间名称。 |
| `description` | VARCHAR(512) | 是 | 命名空间用途说明；NULL 表示未填写。 |
| `sort_order` | INT | 否 | 显示排序值，升序排列，取值 0 至 9999。 |
| `version` | INT NOT NULL DEFAULT 1 | 否 | 命名空间乐观并发版本，从 1 开始，每次修改加一。 |
| `created_by` | BIGINT | 否 | 创建该记录的操作人，取值为 t_user_account.account_id。 |
| `updated_by` | BIGINT | 否 | 最后修改该记录的操作人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 |
| `update_time` | DATETIME(6) | 否 | 该可变记录最后一次持久化更新的 UTC 微秒时间。 |

主键 `namespace_id`；唯一索引 `uk_i18n_namespace`(application_id, namespace_code)。

#### `t_i18n_key`（I18nKey）

表注释：保存由代码导入的文案 key 与英文基准；导入文件中没有的 key 标为废弃而不删除，重新出现时恢复并保留原翻译。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `key_id` | BIGINT | 否 | 服务侧生成的 key Snowflake 主键。 |
| `application_id` | BIGINT | 否 | 所属应用，取值为 t_i18n_application.application_id；与命名空间所属应用一致。 |
| `namespace_id` | BIGINT | 否 | 所属命名空间，取值为 t_i18n_namespace.namespace_id。 |
| `key_code` | VARCHAR(191) ascii_bin | 否 | 完整 key，如 team.invite.submit；必须以所属命名空间编码加点号开头。 |
| `source_text` | TEXT | 否 | 英文基准文案，ICU MessageFormat；由导入写入，运营不能修改。 |
| `description` | VARCHAR(512) | 是 | 开发者说明，由导入写入；NULL 表示未提供。 |
| `source_revision` | INT NOT NULL DEFAULT 1 | 否 | 英文基准修订号，从 1 开始，基准文案变化时加一；翻译依据的修订号小于它即为基准已变更。 |
| `status` | VARCHAR(16) | 否 | key 状态；只有 ACTIVE 参与翻译与发布。允许值：ACTIVE、DEPRECATED。 |
| `version` | INT NOT NULL DEFAULT 1 | 否 | key 乐观并发版本，从 1 开始，每次导入修改该行时加一。 |
| `created_by` | BIGINT | 否 | 创建该记录的操作人，取值为 t_user_account.account_id。 |
| `updated_by` | BIGINT | 否 | 最后修改该记录的操作人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 |
| `update_time` | DATETIME(6) | 否 | 该可变记录最后一次持久化更新的 UTC 微秒时间。 |

主键 `key_id`；唯一索引 `uk_i18n_key`(namespace_id, key_code)；普通索引 `idx_i18n_key_app`(application_id, status, key_code)。

#### `t_i18n_translation`（I18nTranslation）

表注释：保存 key 在一个非英文语言下的翻译；不存状态，基准已变更由翻译依据的修订号与 key 当前修订号比较得出。en 不在本表存储。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `translation_id` | BIGINT | 否 | 服务侧生成的翻译 Snowflake 主键。 |
| `key_id` | BIGINT | 否 | 所属 key，取值为 t_i18n_key.key_id。 |
| `locale_code` | VARCHAR(35) ascii_bin | 否 | 翻译语言，取值为 t_i18n_locale.locale_code，不为 en。 |
| `text` | TEXT | 否 | 翻译文案，ICU MessageFormat；参数名集合与英文基准一致，保存与导入时校验。 |
| `based_on_source_revision` | INT | 否 | 保存该翻译时 key 的英文基准修订号；小于 t_i18n_key.source_revision 表示基准已变更。 |
| `version` | INT NOT NULL DEFAULT 1 | 否 | 翻译乐观并发版本，从 1 开始，每次修改加一；批量保存按它逐条 CAS。 |
| `created_by` | BIGINT | 否 | 创建该记录的操作人，取值为 t_user_account.account_id。 |
| `updated_by` | BIGINT | 否 | 最后修改该记录的操作人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 |
| `update_time` | DATETIME(6) | 否 | 该可变记录最后一次持久化更新的 UTC 微秒时间。 |

主键 `translation_id`；唯一索引 `uk_i18n_translation`(key_id, locale_code)。

#### `t_i18n_release`（I18nRelease）

表注释：保存应用的不可变发布：发布号、依据的文案修订号、覆盖率与发布报告；文案在语言包文件中，状态只从 PENDING_CHECK 走到 READY 或 FAILED。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `release_id` | BIGINT | 否 | 服务侧生成的发布 Snowflake 主键。 |
| `application_id` | BIGINT | 否 | 所属应用，取值为 t_i18n_application.application_id。 |
| `release_no` | INT | 否 | 应用内发布号，从 1 开始，按应用内最大值加一。 |
| `based_on_content_revision` | INT | 否 | 生成语言包时应用的文案修订号；登记时与应用当前修订号一致。 |
| `status` | VARCHAR(16) | 否 | 发布状态；PENDING_CHECK 等待语言包文件检查，READY 可作为当前发布，FAILED 有文件被拒绝。允许值：PENDING_CHECK、READY、FAILED。 |
| `key_count` | INT | 否 | 发布包含的 ACTIVE key 数。 |
| `coverage` | JSON | 否 | 发布时各语言覆盖率 JSON 数组，每项含 locale、required、totalKeys、translatedKeys、sourceChangedKeys、coveragePercent、satisfied。 |
| `report` | JSON | 否 | 发布报告 JSON 对象，含 missing（未翻译而回退英文）与 sourceChanged（基准已变更仍沿用旧翻译），各为 {locale, keyCode} 数组。 |
| `release_note` | VARCHAR(512) | 是 | 发布说明；NULL 表示未填写。 |
| `failure_reason` | VARCHAR(512) | 是 | 检查失败原因；非 FAILED 时为 NULL。 |
| `ready_time` | DATETIME(6) | 是 | 检查通过变为 READY 的 UTC 微秒时间；未通过时为 NULL。 |
| `created_by` | BIGINT | 否 | 发布人，取值为 t_user_account.account_id。 |
| `create_time` | DATETIME(6) | 否 | 发布登记的 UTC 微秒时间。 |
| `update_time` | DATETIME(6) | 否 | 该记录最后一次持久化更新（状态变化）的 UTC 微秒时间。 |

主键 `release_id`；唯一索引 `uk_i18n_release`(application_id, release_no)。

#### `t_i18n_release_bundle`（I18nReleaseBundle）

表注释：保存发布中每个语言 × 命名空间的语言包文件地址与内容摘要；不存语言包正文，创建后不修改。

| 列 | 类型 | 可空 | 注释 |
|---|---|---|---|
| `bundle_id` | BIGINT | 否 | 服务侧生成的语言包 Snowflake 主键。 |
| `release_id` | BIGINT | 否 | 所属发布，取值为 t_i18n_release.release_id。 |
| `locale_code` | VARCHAR(35) ascii_bin | 否 | 语言包语言，取值为 t_i18n_locale.locale_code。 |
| `namespace_code` | VARCHAR(64) ascii_bin | 否 | 语言包命名空间编码，发布时的 t_i18n_namespace.namespace_code。 |
| `file_address` | VARCHAR(64) ascii_bin | 否 | 文件服务地址 /files/v1/{fileId}；文件位于公开桶，公开地址由文件服务在读取时计算，不落库。 |
| `content_hash` | CHAR(64) ascii_bin | 否 | 语言包内容 SHA-256，小写十六进制。 |
| `byte_size` | INT | 否 | 语言包字节数。 |
| `create_time` | DATETIME(6) | 否 | 该记录在 Platform Support 数据库中的 UTC 微秒创建时间；创建后不可变，没有更新时间。 |

主键 `bundle_id`；唯一索引 `uk_i18n_bundle`(release_id, locale_code, namespace_code)。

`tools/arch-rules/src/main/resources/table-ownership.yaml` 已登记七表；`TableOwnershipAutoTest` 计数同步改为 mor_platform 70 张，总数加 7。arch-rules 测试未运行。

## 4. ICU4J

- 版本 **79.1**，坐标 `com.ibm.icu:icu4j`，已登记在 `paytech/build/paytech-dependencies/pom.xml`（`icu4j.version`）。
- 来源：本机 Nexus `http://127.0.0.1:18081/repository/maven-public`（Central 代理）。maven-metadata 的 latest / release 均为 79.1。共用 `~/.m3` 只有 61.1、76.1，没有 79.1。
- 首次下载时 Nexus 回 500（上游拉取超时），重试成功。jar 15,257,985 字节，现位于本卡私有仓库。主会话首次构建时会从 Nexus 正常下载。
- 只使用 `MessagePattern`，没有在服务端格式化文案。
- TECHNOLOGY_STACK 的依赖登记由 E 卡负责，本卡未改 agent-docs。

## 5. 端口接口与留给集成卡的工作

| 端口 | 签名 | 本卡实现 |
|---|---|---|
| `integration.ReleaseBundleStore` | `FileAddress store(String applicationCode, int releaseNo, String locale, String namespaceCode, byte[] content, String sha256)`；`String publicUrl(FileAddress fileAddress)` | **无实现类**；通过 `ObjectProvider` 注入，缺失时发布与 manifest 返回 503 |
| `integration.ReleaseBundleReader` | `byte[] read(FileAddress fileAddress)` | **无实现类**；差异接口缺失时 503 |
| `integration.ReleaseBundleFileChecker` | `BundleFileState state(FileAddress fileAddress)`（AVAILABLE / PENDING / REJECTED） | `FileDocumentReleaseBundleFileChecker`：经 `FileLifecycleService.describe` 读取扫描与对象状态；不存在视为 REJECTED |
| `integration.UploadedFileReader` | `UploadedFile read(long fileId, String reasonCode)` | `FileDocumentUploadedFileReader`：核对原因码、AVAILABLE、ACTIVE、≤2 MiB，经 `FileStorageRegistry.openForRead` 读原版本字节并与登记 SHA-256 核对 |

`FileAddress` 即 `com.paytech.platform.support.api.files.FileAddress`。

**集成卡要做的**

1. 实现 `ReleaseBundleStore`：以 PUBLIC 访问级别、原因码 `I18N_RELEASE_BUNDLE`、`application/json` 上传，检查策略 `I18N_BUNDLE_JSON`。对象键带发布号与摘要，永不覆盖。`publicUrl` 由文件服务按地址计算。
2. 实现 `ReleaseBundleReader`：读原版本字节。
3. 注册块 135 的 DML：两个语言、四个应用、三项权限、菜单、23+1+1 条路由、三个文件原因码与路由、检查策略 `I18N_BUNDLE_JSON`、补偿事件目录项。
4. file-document 的 `application/json` 检测：导入文件按 `application/json` 读取；当前格式检查器能否放行 JSON 由文件公开桶会话决定。
5. PDManer 图组 `localization`。

**留给集成卡的 HTTP / Handler 测试场景**

这些都需要端口的真实实现，不能用测试替身绕过生产链路：

- 发布成功：生成语言包，按语言 × 命名空间上传，登记 PENDING_CHECK 与上线补偿任务。核对语言包字节、SHA-256 与地址。
- 发布期间文案被改：第 2 步返回 409 `I18N_CONTENT_CHANGED`，不登记发布，已上传文件成为孤儿。
- `ReleaseActivationHandler` 独立入口测试，经真实 Dispatcher：
  - 全部 AVAILABLE 时变为 READY 并上线；
  - 仍在检查时返回可重试；
  - 任一被拒绝时标 FAILED 并记录原因；
  - 手动切到更新发布后，旧发布变 READY 不改指针；
  - 发布不存在或任务身份不合法时进入终态失败。
- 切换当前发布（回滚）写 `I18N_RELEASE_ACTIVATE` 审计；切到非 READY 返回 400。
- 两次发布的差异（新增 / 变化 / 删除）；没有上一发布时全部为新增。
- manifest：
  - 当前发布与 `releaseNo` 预览；
  - 只含 en 与目标语言；
  - 停用语言立即从 `locales` 与 `bundles` 消失；
  - 同一发布第二次读取命中缓存，不再调用 `publicUrl`；
  - 指定非 READY 发布返回 404。
- 发布与切换的审计事件内容（action、目标 `I18N_RELEASE`/发布 ID、原因）。

## 6. 测试

### 6.1 纯 Java 测试（本模块 `src/test/java/.../localization/auto`，已运行）

| 测试类 | 例数 | 覆盖 |
|---|---|---|
| `auto/icu/IcuMessageValidatorTest` | 18 | 合法文案矩阵（plural / # / =0 / offset / select / selectordinal / 嵌套 / number,date,time / 撇号）、语法错误位置、缺少 other 的位置、choice 与未知类型、参数集合一致与缺失 / 多出、参数名提取 |
| `auto/source/KeyImportPlannerTest` | 5 | 四类差异与说明变化、未创建命名空间、key 前缀 / 格式 / ICU 问题、非 JSON / 重复键 / 格式与应用不符、预览摘要稳定与变化 |
| `auto/source/TranslationFileTest` | 2 | 翻译文件解析与问题 |
| `auto/release/BundleGeneratorTest` | 2 | 语言包排序、扁平 JSON 字节、SHA-256 稳定、未翻译与停用语言不进入、读回 |
| `auto/release/ReleaseAssessorTest` | 2 | 覆盖率阈值边界（75/76）、en 恒 100%、基准已变更计入、ICU 问题阻止发布、没有 key |
| `auto/release/ReleaseDiffTest` | 2 | 新增 / 变化 / 删除、语言整体增删、没有基准 |

### 6.2 Platform HTTP 入口测试（`paytech-platform-support-test`，只编译未运行）

- `test/base/LocalizationApiTestBase`：导入文件真实写入本机 MinIO，并经生产链路读回。与文件测试使用同一组属性（`files.enabled=true`），复用同一 Spring 上下文；每例插入 en、zh-CN。
- 新增测试工件：`test/localization/LocalizationMocker`、`LocalizationRequestBuilder`、`test/dal/LocalizationTestMapper`。
- 修改测试工件：
  - `OperationsCatalog`：新增 I18N_VIEW / EDIT / PUBLISH，ID 为 900000000000135101–103，位于块 135，VIEW 对只读角色可见；
  - `PlatformDataCleaner`：整表回收七张 `t_i18n_*`；
  - `application-mock.yml`：新增 `caffeine` 与 `localization` 配置段。

| 测试类 | 场景 |
|---|---|
| `LocalizationCatalogApiTest`（5） | 语言新增 / 修改 / 列表排序；en 不可停用、被引用语言不可停用且状态不变、版本冲突 409、重复代码 400；应用新增与未知语言拒绝；命名空间新建 / 列表 key 数 / 有 key 不能删 / 空命名空间删除；只读人员 403 |
| `KeyImportApiTest`（4） | 预览四类差异后确认、库内状态与修订号；过期摘要 409；未创建命名空间预览不可导入、确认 422；原因码不符与未通过检查 400 |
| `TranslationApiTest`（5） | 新建 + 按版本更新、依据当前基准、修订号；ICU 拒绝的字段与位置且零写入；版本冲突 409；工作台三种填写状态；导出再原样导入无写入 |
| `ReleaseApiTest`（3） | 预检覆盖率与缺口；覆盖率不足 422 与 errors；尚无发布时匿名 manifest、未知应用 404、只读人员发布 403 |

这些测试需要先在本地测试库执行 `init/ddl.sql` 中新增的七张表，并启动本机 MinIO。

### 6.3 命令与实际输出

所有命令都加了私有仓库参数：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-i18n/paytech
R="-Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository"
```

1. 安装本模块及上游（只进私有仓库）。

```bash
./mvnw -q $R -pl platform-support/paytech-platform-support-localization -am install -DskipTests
```

结果：退出码 0，无输出。

2. 运行本模块纯 Java 测试。

```bash
./mvnw $R -pl platform-support/paytech-platform-support-localization test
```

```text
Tests run: 5  ... KeyImportPlannerTest
Tests run: 2  ... TranslationFileTest
Tests run: 2  ... ReleaseDiffTest
Tests run: 2  ... BundleGeneratorTest
Tests run: 2  ... ReleaseAssessorTest
Tests run: 18 ... IcuMessageValidatorTest
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

3. 原样执行任务卡第 3 条命令。

```bash
./mvnw -q $R -pl platform-support -amd test-compile -DskipTests
```

结果：失败，与本卡无关，见第 1 节第 5 条。

```text
[ERROR] ... paytech-client-master-core: Compilation failure
ClientDocumentServiceImpl.java:[47,66] cannot find symbol  method fileAddress()
  (DocumentReferenceRequestBody 来自 ~/.m3 中的旧 client-master-api 快照)
```

改用私有仓库并预装上游后，又在 BFF 依赖的 risk-decision-api、ledger-api 等 9 个 api 快照上失败：

```text
Could not find artifact com.paytech:paytech-risk-decision-api:jar:1.0.0-SNAPSHOT
```

4. 等价验证：把 `-amd` 得到的 33 个模块连同全部上游放进同一个 reactor。

```bash
./mvnw -q $R -pl ":paytech-platform-support,…<platform-support 12 个模块>,:paytech-api-gateway-app,:paytech-api-gateway-test,
  :paytech-operations-console-bff-app,:paytech-operations-console-bff-test,:paytech-client-console-bff-app,
  :paytech-client-console-bff-test,:paytech-client-master-{core,app,test},:paytech-acquiring-{core,app,test},
  :paytech-card-{core,app,test},:paytech-billing-{core,app,test},:paytech-ledger-{core,app,test}" -am test-compile -DskipTests
```

结果：退出码 0。这次编译包含同一工作目录中 B 卡按本合同写的 ops-bff / client-bff 代码。

未运行：Platform HTTP 入口测试（任务卡要求只编译）、`ArchitectureAutoTest`、arch-rules 的 `TableOwnershipAutoTest`、整个 reactor 测试。

## 7. 改动文件清单（只列本卡）

**新增**

- `paytech/platform-support/paytech-platform-support-localization/**`：
  - `pom.xml`；
  - `core/{enums,configuration,dal/dataobject,dal/mapper}`；
  - `domain/{icu,source,release}` 与上下文；
  - `domainservice/{repository,service}`；
  - `integration`；
  - `service/{processor,handler}`；
  - `resources/mapper/localization/*.xml`（7 个）；
  - `src/test/**`（6 个测试类与 fixtures）。
- `paytech-platform-support-api/.../api/localization/**`：4 个 Api、13 个请求类、29 个响应类；以及 `api/enums/LocalizationErrorCode.java`。
- `paytech-platform-support-app/.../ext/rest/localization/`：4 个 Controller 与 `LocalizationConverter`。
- `paytech-platform-support-test/...`：
  - `auto/ext/http/localization/`（4 个测试类）；
  - `test/base/LocalizationApiTestBase`；
  - `test/dal/LocalizationTestMapper`；
  - `test/localization/{LocalizationMocker,LocalizationRequestBuilder}`。
- `paytech/platform-support/docs/database/sql/changes/2026-10-23-localization.sql`。

**修改**

- `paytech/build/paytech-dependencies/pom.xml`：icu4j 79.1。
- `paytech/platform-support/pom.xml`：
  - dependencyManagement 与 modules 增加 localization；
  - 删除 caffeine-starter 的 Enforcer 禁令。
- `paytech-platform-support-core/pom.xml`、`paytech-platform-support-app/pom.xml`：依赖 localization。
- `paytech-platform-support-app/.../configuration/PersistenceConfiguration.java`：MapperScan 与 5 个枚举 TypeHandler。
- `.../configuration/PlatformApiErrorConfiguration.java`：4 个错误码的 HTTP 状态。
- `paytech-platform-support-app/src/main/resources/application.yml`：`localization.*` 与 `caffeine.*`。
- `paytech/platform-support/compensation-event-catalog.yaml`：`I18N_RELEASE_ACTIVATE`。
- `paytech/platform-support/docs/database/sql/platform-support-schema.sql`、`paytech-platform-support-test/src/test/resources/init/ddl.sql`：七表。
- `paytech/platform-support/README.md`：模块表增加一行。
- 测试：`OperationsCatalog.java`、`PlatformDataCleaner.java`、`application-mock.yml`。
- `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`、`.../TableOwnershipAutoTest.java`。

## 8. 写接口自查（WRITE_API_EXAMPLE 第 11 节）

- **API 与 Body。** 普通 class，中文 JavaDoc，Hibernate Validator 注解。内部命令（`service/processor/request`）由 `AbstractProcessor.validateBasic` 触发校验。
- **Controller / Converter 只做协议适配。** 写操作各有一个 Processor：`CreateLocale`、`UpdateLocale`、`CreateApplication`、`UpdateApplication`、`CreateNamespace`、`UpdateNamespace`、`DeleteNamespace`、`ConfirmKeyImport`、`SaveTranslations`、`ImportTranslations`、`PublishRelease`、`ActivateRelease`。`PreviewKeyImport` 与 `GetManifest` 无锁。
- **Processor 只查本模块 Repository。** 写入经 Service：`LocalizationCatalogService`、`KeyImportService`、`TranslationService`、`ReleaseService`，均为 interface + Impl。Service 之间不互相调用，也不访问外部能力。
- **Context。** `KeyImportContext`、`TranslationWriteContext`、`PublishContext` 持有本模块 DataObject，不进入 Repository。
- **事务。** 由 `LocalizationProcessorSupport.write` 开启，读已提交，15 秒。事务内只有数据库操作和权限复核；文件读取、语言包上传与文件检查状态查询都在事务外。
- **Repository。** 一表一个，条件 SQL 加行数检查，成功后回写原对象。
- **锁。** 应用级 `localization:application:{编码}`：命名空间、导入、翻译、发布、切换共用。语言级 `localization:locale:{代码}`。数据库侧另有保护：版本 CAS、修订号 CAS、应用行 `FOR UPDATE`、语言行 `FOR SHARE`，以及唯一键。
- **业务防重。** 依据版本、修订号与预览摘要；不处理 `Idempotency-Key`，没有旧命令幂等组件。
- **View。** `TranslationEntryView` 用于工作台的多表查询；写接口没有 View。
- **补偿事件。** 由 Handler 的纯工厂 `ReleaseActivationHandler.buildEvent(releaseId)` 构造，在发布登记事务内经 Registrar 登记；Handler 自己编排短事务。
- **未伪造成功。** 端口缺失时明确返回 503，没有生产 Mock，也没有测试分支。
- **验证缺口。** 事务、并发与回滚的数据库证据要等 HTTP 入口测试实际运行后才能取得；本卡只完成编译。

## 9. 需要主会话裁决的问题

1. 第 1 节第 1、2 条：Processor 放置位置与操作人 Header，这两项决定 B 卡的 BFF 转发方式。B 卡目前已按 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 编译通过。
2. 第 1 节第 3 条：是否接受删除 Platform 对 Caffeine Starter 的 Enforcer 禁令；不接受时 manifest 只能每次计算 `publicUrl`。
3. 第 1 节第 8、9 条：ICU 额外拒绝 choice 与未知类型是否保留；其余写接口是否补审计。
4. 应用 `status` 只有 ENABLED / DISABLED 两个值，本期没有停用入口（技术方案未定义）。DISABLED 的应用 manifest 返回 404。
5. `ReleaseActivationHandler` 最多尝试 60 次（退避 2s–60s，约 1 小时）。用尽后发布停在 PENDING_CHECK，补偿记录为 FAILED，需要人工重开。是否需要更长窗口或其他提示，请确认。
6. `ArchitectureAutoTest.MODULES` 未加入 localization（workflow 也未加入）。加入后 `ProcessorTemplateRule` 等规则对两个查询 Processor（`LocalizationQueryProcessor`、`LocalizationContentQueryProcessor` 不继承模板，与 `WorkflowQueryProcessor` 相同）的判定未经验证。
7. `compensation-event-catalog.yaml` 中工作流的两个事件类型 `WORKFLOW_CALLBACK_EVENT`、`WORKFLOW_NOTIFICATION` 目前未登记，与本卡无关，顺带提示。

## 10. 跟进修改（2026-10-23，按主会话裁决）

主会话接受原样保留的项：第 1 节第 1、2、4、5、6、7、8 条，以及第 9 节第 4 条。以下四项已修改。第 1 节第 3、9 条与第 9 节第 5、6 条以本节为准。

### 10.1 撤销 Caffeine（第 1 节第 3 条被否决）

- 已恢复 `paytech/platform-support/pom.xml` 中 `<exclude>com.paytech:paytech-caffeine-starter</exclude>`。
- 已删除 localization 模块对 `paytech-caffeine-starter` 的依赖，以及 app `application.yml` 与测试 `application-mock.yml` 中的 `caffeine.*` 配置段。
- 已删除 `ManifestBundleCache`。`GetManifestProcessor` 每次请求读取该发布的语言包行，并经 `ReleaseBundleStore#publicUrl` 计算地址，没有任何缓存；ETag / 304 由 BFF 负责。端口未接入且已有发布时仍返回 503。
- 第 5 节“留给集成卡”中“同一发布第二次读取命中缓存”这一场景作废。

### 10.2 全部写接口写审计（第 1 节第 9 条）

做法与 data-query / workflow 相同：Controller 方法加 `@Audit`，Processor 在写事务内经 `LocalizationProcessorSupport.recordSucceeded` 登记 `Outcome.succeeded` 事实，事务提交后落审计。拒绝与失败由审计切面按调用结果记录。与当前完全相同、没有写入的修改请求不产生成功审计。

| 接口 | action | resourceType | 事实中的资源 ID | 说明（reason） |
|---|---|---|---|---|
| createLocale | `I18N_LOCALE_CREATE` | `I18N_LOCALE` | locale_id | 新增语言 {代码} |
| updateLocale | `I18N_LOCALE_UPDATE` | `I18N_LOCALE` | locale_id | 修改语言 {代码}，状态 {状态} |
| createApplication | `I18N_APPLICATION_CREATE` | `I18N_APPLICATION` | application_id | 新增应用 {编码} |
| updateApplication | `I18N_APPLICATION_UPDATE` | `I18N_APPLICATION` | application_id | 修改应用设置 {编码} |
| createNamespace | `I18N_NAMESPACE_CREATE` | `I18N_NAMESPACE` | namespace_id | 新建命名空间 {编码} |
| updateNamespace | `I18N_NAMESPACE_UPDATE` | `I18N_NAMESPACE` | namespace_id | 修改命名空间 {编码} |
| deleteNamespace | `I18N_NAMESPACE_DELETE` | `I18N_NAMESPACE` | namespace_id | 删除命名空间 {编码} |
| confirmKeyImport | `I18N_KEY_IMPORT` | `I18N_APPLICATION` | application_id | 导入 key：新增 / 基准变更 / 废弃 / 恢复数量 |
| saveTranslations | `I18N_TRANSLATION_SAVE` | `I18N_APPLICATION` | application_id | 保存 {语言} 翻译 N 条 |
| importTranslations | `I18N_TRANSLATION_IMPORT` | `I18N_APPLICATION` | application_id | 导入 {语言} 翻译：写入 N，文件 {fileId} |
| publishRelease | `I18N_RELEASE_PUBLISH` | `I18N_RELEASE` | release_id | 发布 {发布号} |
| activateRelease | `I18N_RELEASE_ACTIVATE` | `I18N_RELEASE` | release_id | 切换到发布 {发布号}：{原因} |

- `fallbackResourceId` 依次为 `I18N_LOCALE_CATALOG`、`I18N_APPLICATION_CATALOG`、`I18N_NAMESPACE_CATALOG`、`I18N_RELEASE_CATALOG`。路径参数是编码，不是数字 ID，所以真实资源 ID 由事实写入。
- 审计不记录文案正文。
- key 导入预览（`previewKeyImport`）不写库，未加审计。如需记录导入文件的读取，请告知。

### 10.3 上线检查超时标 FAILED（第 9 节第 5 条）

- 新增纯 Java 判定 `domain/release/ReleaseCheckDecision.decide(states, executeCount, maximumAttempts)`，返回内部枚举 `core/enums/ReleaseCheckVerdict`：READY、REJECTED、TIMED_OUT、RETRY。拒绝优先于等待；已到最后一次允许的尝试（`executeCount ≥ 60`）仍在检查时判为 TIMED_OUT。
- `ReleaseActivationHandler` 按判定处理：
  - REJECTED 与 TIMED_OUT 在短事务里把发布标为 FAILED。TIMED_OUT 的 `failure_reason` 为“文件检查超时”。
  - 最后一次尝试抛出异常时，若发布仍为 PENDING_CHECK，同样按“文件检查超时”标为 FAILED；若标记本身失败，交由补偿框架把任务置为 FAILED 并告警。
  - 事件键解析改为不抛出：超长数字视为身份不合法，返回终态失败。
- 测试：纯 Java `auto/release/ReleaseCheckDecisionTest`，2 例。覆盖三种结论；拒绝优先；最后一次与接管后超限次数都判为超时；到上限但全部可用时仍上线。
- 仍留给集成卡：Handler 级入口测试需要真实 Dispatcher 与文件检查状态，场景见第 5 节，另加“第 60 次仍在检查时发布变 FAILED、原因文件检查超时”。
- 残余窗口：进程在最后一次尝试执行中崩溃、接管 Claim 时次数已超限，Foundation 会在调用 Handler 之前把任务置为 FAILED。这时发布仍停在 PENDING_CHECK，只能通过补偿任务的告警发现。消除它需要 Foundation 提供“放弃前回调”，超出本卡范围。

### 10.4 架构测试（第 9 节第 6 条）

- `ArchitectureAutoTest.MODULES` 已加入 `paytech-platform-support-localization`。
- 首次运行只有 R14 两条违规：

```text
[R14] com.paytech.platform.support.localization.service.processor.LocalizationContentQueryProcessor: 具体 Processor 未继承 com.paytech.foundation.processor.AbstractProcessor（需要资源锁时继承 AbstractLockProcessor）
[R14] com.paytech.platform.support.localization.service.processor.LocalizationQueryProcessor: 具体 Processor 未继承 com.paytech.foundation.processor.AbstractProcessor（需要资源锁时继承 AbstractLockProcessor）
```

- R14 没有只读查询豁免（`ProcessorTemplateRule` 只排除三个 BFF），`known-deviations.yaml` 也没有 R14 条目。因此没有改规则，也没有用改类名的方式绕开，而是按 data-query、access-control 的既有做法改为每个查询一个 `AbstractProcessor`。
- 新增 10 个查询 Processor：`ListLocalesProcessor`、`ListApplicationsProcessor`、`GetApplicationProcessor`、`ListNamespacesProcessor`、`PrecheckReleaseProcessor`、`ListReleasesProcessor`、`ListTranslationsProcessor`、`ExportTranslationsProcessor`、`GetReleaseProcessor`、`DiffReleaseProcessor`。
- 新增 6 个类型化请求：`OperatorQuery`、`ApplicationQuery`、`TranslationListQuery`、`TranslationExportQuery`、`ReleaseListQuery`、`ReleaseQuery`。
- 共用查找放在 `LocalizationQueryLookups`，返回体转换合并进 `LocalizationResponses`。原两个多方法查询类已删除，HTTP 合同不变。
- 修改后 `ArchitectureAutoTest` 通过（`Tests run: 1, Failures: 0`）。它是静态检查，不启动 Spring、不连数据库。

### 10.5 本轮验证（全部使用私有仓库，未运行 SQL 与 HTTP 入口测试）

| 命令 | 结果 |
|---|---|
| `./mvnw -q $R -pl platform-support/paytech-platform-support-localization -am install -DskipTests` | 退出码 0；Enforcer 已重新禁止 Caffeine，仍通过 |
| `./mvnw $R -pl platform-support/paytech-platform-support-localization test` | `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS（新增 ReleaseCheckDecisionTest 2 例） |
| `./mvnw $R -pl platform-support/paytech-platform-support-test -am test -Dtest=ArchitectureAutoTest -Dsurefire.failIfNoSpecifiedTests=false` | `Tests run: 1, Failures: 0`，BUILD SUCCESS |
| 第 6.3 节第 4 条的等价编译（33 个模块连同上游，`-am test-compile`） | 退出码 0；含 B 卡 BFF 代码 |

### 10.6 本轮改动文件

**新增**

- localization 模块：
  - `core/enums/ReleaseCheckVerdict.java`、`domain/release/ReleaseCheckDecision.java`；
  - `service/processor/` 下 10 个查询 Processor、`LocalizationQueryLookups.java`；
  - `service/processor/request/` 下 6 个查询请求类；
  - `src/test/.../auto/release/ReleaseCheckDecisionTest.java`。

**删除**

- `service/processor/ManifestBundleCache.java`、`LocalizationQueryProcessor.java`、`LocalizationContentQueryProcessor.java`。

**修改**

- `paytech/platform-support/pom.xml`：恢复 Caffeine 禁令。
- localization 的 `pom.xml`：去掉 Caffeine 依赖。
- app `application.yml`、测试 `application-mock.yml`：去掉 Caffeine 配置。
- `GetManifestProcessor`、`ReleaseBundleLinks`：不再缓存。
- `LocalizationProcessorSupport`：新增审计资源类型常量与 `recordSucceeded`。
- 10 个写 Processor：登记审计事实。
- `LocalizationResponses`：合并原查询类中的转换方法。
- `ReleaseActivationHandler`：超时处理与事件键解析。
- app 的三个 Controller：加 `@Audit`，改用新查询 Processor。
- `ArchitectureAutoTest.java`：MODULES 加入 localization。

### 10.7 ICU 校验专用错误码（主会话追加第 5 项）

- 新增常量 `LocalizationErrorCode.I18N_MESSAGE_INVALID`：枚举类为 `com.paytech.platform.support.api.enums.LocalizationErrorCode`，Code 为 `I18N_MESSAGE_INVALID`，默认消息为“翻译文案未通过 ICU 校验”。
- HTTP 状态为 422，映射加在 `PlatformApiErrorConfiguration`。B 卡可以像 `I18N_COVERAGE_INSUFFICIENT` 一样透传。

**使用范围**

- **批量保存：** 先检查 key。key 不存在或已废弃时返回 422 `VALIDATION_ERROR`，field 为 `translations[i].keyCode`。key 都有效后做 ICU 校验，失败时返回 422 `I18N_MESSAGE_INVALID`，field 为 `translations[i].text`，message 形如“第 4 个字符：花括号不匹配，缺少右花括号”或“缺少参数 {name}，英文基准包含该参数”。
- **翻译导入：** 文件格式、应用或语言不符、未知或已废弃 key、超长时返回 422 `VALIDATION_ERROR`。都通过后，ICU 问题返回 422 `I18N_MESSAGE_INVALID`，field 为 `namespaces.{命名空间}.{key}`，message 同上。
- 两处都最多列出 200 条，任一条失败整体不写入。

**未改动的范围**

- 发布与预检中“已有翻译与当前基准不一致”的 ICU 问题，仍按原合同返回 422 `VALIDATION_ERROR`（field 为 `translations.{语言}.{key}`）。
- key 导入确认时英文基准的 ICU 问题，同样仍按原合同返回 422 `VALIDATION_ERROR`。
- 主会话只要求批量保存与翻译导入改用新错误码。如果这两处也要统一为 `I18N_MESSAGE_INVALID`，请告知，改动只在 `PublishReleaseProcessor.requirePublishable` 与 `ConfirmKeyImportProcessor.requireImportable` 各一行。

**改动文件**

- `LocalizationErrorCode`、`PlatformApiErrorConfiguration`、`LocalizationContentApi` 的 JavaDoc。
- `LocalizationProblems`：新增 `rejectInvalidMessages`。
- `SaveTranslationsProcessor`：拆成 `keyProblems` 与 `messageProblems`。
- `ImportTranslationsProcessor`：`Outcome` 新增 `messageProblems`。
- HTTP 测试 `TranslationApiTest.test_icuRejected`：断言改为 `I18N_MESSAGE_INVALID`。仍只编译、未运行。

### 10.8 arch-rules 表归属（主会话追加第 6 项）

- **原因：** 第一轮已把七张 `t_i18n_*` 登记到 `table-ownership.yaml` 的 `platform-support / mor_platform`，`mor_platform` 计数也已改为 70。但 `TableOwnershipAutoTest.test_normalFlow` 总表数表达式末尾的 `+ 7` 当时没有写进去（替换未命中，我没有发现）。因此 yaml 实际 370 张，断言期望 363。
- **修复：** 已在表达式末尾补上 `+ 7`；表归属的唯一来源仍是 `tools/arch-rules/src/main/resources/table-ownership.yaml`。
- **结果：** 用私有仓库运行 arch-rules 全部测试：

```bash
./mvnw $R -pl tools/arch-rules -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='com.paytech.arch.**'
```

```text
TableOwnershipAutoTest   Tests run: 7, Failures: 0
合计                      Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 10.9 第 5、6 项后的复验（私有仓库，未运行 SQL 与 HTTP 入口测试）

| 命令 | 结果 |
|---|---|
| localization `-am install -DskipTests` | 退出码 0 |
| localization `test` | `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS |
| `ArchitectureAutoTest` | `Tests run: 1, Failures: 0`，BUILD SUCCESS |
| arch-rules 全部测试 | 68 例全部通过，BUILD SUCCESS |
| 33 模块等价 `test-compile` | 退出码 0 |

`ArchitectureAutoTest` 第一次复验输出过一次 `BUILD FAILURE`，但该次没有测试失败，也没有编译错误输出。随后连续两次重跑均 BUILD SUCCESS，判断是同一工作目录中其他卡的并发构建所致；未复现。

## 11. 第三批跟进（2026-10-23）

### 11.1 第 5、6 项（上一轮已完成，本轮复核）

- **第 5 项：** `com.paytech.platform.support.api.enums.LocalizationErrorCode.I18N_MESSAGE_INVALID`（HTTP 422）已在第 10.7 节完成，本轮未改。批量保存与翻译导入的 ICU 拒绝都使用它。Problem `errors[{field,message}]` 的 field 为 `translations[i].text`（保存）或 `namespaces.{命名空间}.{key}`（导入），message 为带字符位置的中文原因，例如“第 4 个字符：花括号不匹配，缺少右花括号”。
- **第 6 项：** 本轮复跑 arch-rules 全部测试，`TableOwnershipAutoTest` 7 例通过，合计 68 例 0 失败。

### 11.2 第 7 项：导入文件未检查完 `I18N_IMPORT_FILE_NOT_READY`（409）

- 新增常量 `LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY`，默认消息为“导入文件仍在检查，请稍后重试”。在 `PlatformApiErrorConfiguration` 映射为 409。
- `FileDocumentUploadedFileReader` 遇到扫描状态 `PENDING_SCAN`、`SCANNING` 时抛出该码，detail 带当前状态与文件 ID。key 导入预览、确认与翻译导入三个入口共用这个读取器，所以三处都返回 409。
- `QUARANTINED`、`REJECTED`、对象不可用、原因码不符、超长仍返回 400 `BUSINESS_CHECK_FAILED`。
- `LocalizationContentApi` 的接口说明已同步。HTTP 测试 `KeyImportApiTest.test_invalidFile` 改为断言 SCANNING 返回 409、REJECTED 返回 400；仍只编译。

### 11.3 第 8、9 项：相对线上发布的变更摘要

**计算方式**

- 当前内容由 `ReleaseContent.current(activeKeys, translations, locales)` 按发布口径展开：en 取 ACTIVE key 的英文基准，其他语言只含已填写的翻译（含基准已变更），停用语言与废弃 key 不计入。
- 线上发布的内容经 `ReleaseBundleReader` 读取该发布全部语言包，在事务外。
- 两者用既有 `ReleaseDiff` 比较，再由 `ReleaseChangeSummaryFact.of(diff, baseReleaseNo)` 汇总：
  - en 新增为 addedKeys，en 消失为 deprecatedKeys，en 文案变化为 sourceChangedKeys；
  - 非 en 语言的新增、修改与移除合计为该语言的 changedCount；
  - 每类最多 20 个示例 key，按字典序。

**取值规则**

- 应用没有当前发布时，与空内容比较，不需要端口：全部计为新增，`baseReleaseNo` 为 null。
- 已有当前发布但 `ReleaseBundleReader` 没有实现时，摘要为 null，不伪造。

**预检（第 8 项）**

`ReleasePrecheckResponseBody.changeSummary` 相对应用当前发布计算，由 `ReleaseChangeSummaries.summarize` 完成。

**发布（第 9 项）**

- 发布第 1 步在锁内、事务外，相对当时的线上发布计算同一摘要，写入 `t_i18n_release.report` 的 `changeSummary`。`report` 列 JSON 结构新增该键，表结构不变。
- 发布列表的每一项与发布详情的 `release` 都通过 `ReleaseSummaryResponseBody.changeSummary` 返回它。
- 早期发布或计算时端口缺失的发布，该字段为 null。

### 11.4 第 10 项：发布检查进度

`ReleaseDetailResponseBody.checkProgress` 在读取详情时，对每个语言包调用激活 Handler 使用的同一个 `ReleaseBundleFileChecker`（file-document 进程内查询，不开事务）。统计规则：

- `available`：AVAILABLE 且对象 ACTIVE；
- `pending`：PENDING_SCAN 或 SCANNING；
- `rejected`：其余情况（拒绝、隔离、对象不可用或文件不存在）。

### 11.5 第 11 项：未发布变更数

**统计口径**

`ApplicationResponseBody.pendingChangeCount` 出现在列表、详情与修改应用的响应中，新增应用时为 0。

- 有当前发布时，统计不同 key 的个数：key 行的 `update_time` 或该 key 任一翻译行的 `update_time` 晚于当前发布的 `create_time`。废弃 key 也算在内，因为废弃是一次变化。
- 没有当前发布时，为 ACTIVE key 数。

**SQL**

`I18nKeyMapper.countChangedSince` 的语句：

```sql
SELECT COUNT(*) FROM t_i18n_key k
WHERE k.application_id = ? AND (k.update_time > ?
  OR EXISTS (SELECT 1 FROM t_i18n_translation t WHERE t.key_id = k.key_id AND t.update_time > ?))
```

**索引**

**未新增索引。** 外层沿用 `idx_i18n_key_app (application_id, status, key_code)` 的前缀，EXISTS 沿用 `uk_i18n_translation (key_id, locale_code)` 的前缀。每个应用只有几千个 key，按应用扫描可以接受。

**为何以 `create_time` 作基准**

发布在锁内先读取文案、再登记 `create_time`，期间同一应用的写入都被应用锁阻止。因此“晚于发布登记时间的写入”就是“未进入当前发布的写入”。切换到旧发布（回滚）后，以该旧发布的登记时间计算，结果会变大，这符合“相对线上发布”的含义。

### 11.6 DTO 字段变化（供 B、C 卡接线）

**新增类**（包 `com.paytech.platform.support.api.localization.response`）

- `ReleaseChangeSummary`：
  - `baseReleaseNo: Integer`（没有线上发布时为 null）
  - `addedKeyCount: int`，`addedKeys: List<String>`（≤20）
  - `deprecatedKeyCount: int`，`deprecatedKeys: List<String>`（≤20）
  - `sourceChangedKeyCount: int`，`sourceChangedKeys: List<String>`（≤20）
  - `translationChanges: List<LocaleTranslationChange>`（按语言代码排列，没有变化的语言不出现）
- `LocaleTranslationChange`：`locale: String`，`changedCount: int`，`sampleKeys: List<String>`（≤20，字典序）。
- `BundleCheckProgress`：`total: int`，`available: int`，`pending: int`，`rejected: int`。

**已有类新增字段**

| 类 | 新字段 | 可空 / 说明 |
|---|---|---|
| `ApplicationResponseBody` | `pendingChangeCount: long` | 不为 null；列表、详情（`ApplicationDetailResponseBody.application`）、新增（恒 0）、修改都返回 |
| `ReleasePrecheckResponseBody` | `changeSummary: ReleaseChangeSummary` | 已有当前发布且读取端口未接入时为 null |
| `ReleaseSummaryResponseBody` | `changeSummary: ReleaseChangeSummary` | 发布列表项与 `ReleaseDetailResponseBody.release`；早期发布或计算时端口缺失为 null |
| `ReleaseDetailResponseBody` | `checkProgress: BundleCheckProgress` | 不为 null；没有语言包时全为 0 |

**新增错误码**（`LocalizationErrorCode`）

| 错误码 | HTTP 状态 |
|---|---|
| `I18N_MESSAGE_INVALID` | 422 |
| `I18N_IMPORT_FILE_NOT_READY` | 409 |

原有 4 个码不变。请求 DTO 没有变化。

### 11.7 本轮改动文件

**新增**

- api：`ReleaseChangeSummary`、`LocaleTranslationChange`、`BundleCheckProgress`。
- localization：
  - `domain/release/ReleaseContent`、`ReleaseChangeSummaryFact`；
  - `service/processor/ReleaseChangeSummaries`；
  - 测试 `auto/release/ReleaseChangeSummaryTest`（3 例）。

**修改**

- api：
  - `LocalizationErrorCode`（第 7 项）；
  - `ApplicationResponseBody`、`ReleasePrecheckResponseBody`、`ReleaseSummaryResponseBody`、`ReleaseDetailResponseBody`；
  - `LocalizationContentApi` 的 JavaDoc。
- app：`PlatformApiErrorConfiguration`。
- localization 数据访问与集成：
  - `I18nKeyMapper` 与对应 XML、`I18nKeyRepository` 与 `I18nKeyRepositoryImpl`（`countChangedSince`）；
  - `FileDocumentUploadedFileReader`、`UploadedFileReader`（JavaDoc）。
- localization 领域：
  - `ReleaseReportFact`（新增 `changeSummary`）；
  - `ReleaseAssessment.report(changeSummary)`。
- localization 处理器：
  - `LocalizationResponses`（`application` 增加参数，`release` 带摘要，新增 `changeSummary`、`checkProgress`）；
  - `LocalizationQueryLookups`（`pendingChangeCount`）；
  - `PrecheckReleaseProcessor`、`PublishReleaseProcessor`、`GetReleaseProcessor`；
  - `DiffReleaseProcessor`（改用 `ReleaseChangeSummaries.read`）；
  - `ListApplicationsProcessor`、`GetApplicationProcessor`、`UpdateApplicationProcessor`、`CreateApplicationProcessor`。
- test：`KeyImportApiTest`。

### 11.8 本轮验证（私有仓库，未运行 SQL 与 HTTP 入口测试）

| 命令 | 结果 |
|---|---|
| localization `-am install -DskipTests` | 退出码 0 |
| localization `test` | `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS（新增 ReleaseChangeSummaryTest 3 例） |
| `ArchitectureAutoTest` | `Tests run: 1, Failures: 0`，BUILD SUCCESS（见下方说明） |
| arch-rules 全部测试 | `TableOwnershipAutoTest` 7 例通过；合计 68 例 0 失败，BUILD SUCCESS |
| 33 模块等价 `test-compile` | 退出码 0 |

`ArchitectureAutoTest` 每轮第一次运行都输出过 `BUILD FAILURE`，但没有测试失败，也没有编译错误。随后三次重跑，其中两次保存了完整日志，全部 BUILD SUCCESS。当时本机另有 3 个 Maven 进程在同一工作目录构建，判断为并发构建争用 target 目录。合并后建议在没有其他构建时再跑一次确认。

### 11.9 需要知悉的点

- **预检与发布的耗时：** 两者都会在事务外读取线上发布的全部语言包。集成卡实现读取端口后，耗时取决于语言包数量（语言数 × 命名空间数）。
- **未发布变更数可能偏高：** 它按写入时间计，不比较内容。把翻译改回原值也会计入。精确的“相对线上内容”的差异以预检的 `changeSummary` 为准。
