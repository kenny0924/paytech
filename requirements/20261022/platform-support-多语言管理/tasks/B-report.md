# 任务卡 B 交付报告：Operations / Client BFF 转发与注册块 135

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`，只改了 `paytech/operations-console-bff/**`、`paytech/client-console-bff/**`；SQL 写在主工作区本需求 `db/`。未提交、未推送、未执行 SQL、未动容器。
- 依据：technique.md v3 第 3.7—3.9 节、tasks/A-platform.md；参照 data-query 转发（`/data-query-admin/v1`）、卡 BIN 库的 BFF 直调文件接口（块 132）与块 131 / 132 注册 SQL。开工后实际读取 DEVELOPMENT_CONVENTIONS 第 3（含 BFF 聚合层）、4、7、10 节，APPLICATION_ARCHITECTURE 第 3、4 节，文件上传原则决定。
- 本卡全部是简单转发：Controller → 本地 Client Port（integration）→ Platform `@HttpExchange`，不建 Processor，没有本地写入与事务。

## 1. 依赖的 Platform 合同（以开工时 worktree 版本为准）

`paytech-platform-support-api` 包 `com.paytech.platform.support.api.localization`：

- `LocalizationCatalogApi`（11）、`LocalizationContentApi`（6）、`LocalizationReleaseApi`（6）、`LocalizationDeliveryApi`（1）；
- `request.*`：12 个请求体全部原样作为浏览器请求体使用（含 `TranslationInput`）；
- `response.*`：全部响应体原样返回浏览器；
- `enums.LocalizationErrorCode`（4 个 Code）；文件上传用既有 `files.InternalFileApi`、`StartFileUploadRequestBody`、`FileAddress`。

卡 A 若改了路径、请求体字段或错误码，BFF 需同步：边界样例 `localization-boundary.json` 与测试会直接失败提示。

## 2. BFF 端点

Operations 管理前缀 `/localization-admin/v1`，Gateway 外部路径为 `/api/operations` + BFF 路径；下游为 Platform `/internal/localization/v1` + 同一后缀。所有管理接口经 PLT-I02，带 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`（读写都带，Platform 再次校验权限）。

| # | 方法 | BFF 路径 | Platform 合同方法 | 权限 | Route Code |
|---|---|---|---|---|---|
| C01 | GET | `/locales` | Catalog.listLocales | I18N_VIEW | operations-i18n-locale-list |
| C02 | POST | `/locales` | Catalog.createLocale | I18N_EDIT | operations-i18n-locale-create |
| C03 | PUT | `/locales/{localeCode}` | Catalog.updateLocale | I18N_EDIT | operations-i18n-locale-update |
| C04 | GET | `/applications` | Catalog.listApplications | I18N_VIEW | operations-i18n-application-list |
| C05 | GET | `/applications/{applicationCode}` | Catalog.getApplication | I18N_VIEW | operations-i18n-application-detail |
| C06 | POST | `/applications` | Catalog.createApplication | I18N_EDIT | operations-i18n-application-create |
| C07 | PUT | `/applications/{applicationCode}` | Catalog.updateApplication | I18N_EDIT | operations-i18n-application-update |
| C08 | GET | `/applications/{applicationCode}/namespaces` | Catalog.listNamespaces | I18N_VIEW | operations-i18n-namespace-list |
| C09 | POST | `/applications/{applicationCode}/namespaces` | Catalog.createNamespace | I18N_EDIT | operations-i18n-namespace-create |
| C10 | PUT | `/applications/{applicationCode}/namespaces/{namespaceCode}` | Catalog.updateNamespace | I18N_EDIT | operations-i18n-namespace-update |
| C11 | DELETE | `/applications/{applicationCode}/namespaces/{namespaceCode}?expectedVersion=` | Catalog.deleteNamespace | I18N_EDIT | operations-i18n-namespace-delete |
| T01 | POST | `/applications/{applicationCode}/key-imports/previews` | Content.previewKeyImport | I18N_EDIT | operations-i18n-key-import-preview |
| T02 | POST | `/applications/{applicationCode}/key-imports` | Content.confirmKeyImport | I18N_EDIT | operations-i18n-key-import-confirm |
| T03 | GET | `/applications/{applicationCode}/translations?locale=&namespaceCode=&state=&keyword=&page=&size=` | Content.listTranslations | I18N_VIEW | operations-i18n-translation-list |
| T04 | POST | `/applications/{applicationCode}/translations/batch` | Content.saveTranslations | I18N_EDIT | operations-i18n-translation-save |
| T05 | GET | `/applications/{applicationCode}/translations/export?locale=` | Content.exportTranslations | I18N_VIEW | operations-i18n-translation-export |
| T06 | POST | `/applications/{applicationCode}/translations/imports` | Content.importTranslations | I18N_EDIT | operations-i18n-translation-import |
| R01 | GET | `/applications/{applicationCode}/release-precheck` | Release.precheckRelease | I18N_VIEW | operations-i18n-release-precheck |
| R02 | POST | `/applications/{applicationCode}/releases` | Release.publishRelease | I18N_PUBLISH | operations-i18n-release-publish |
| R03 | GET | `/applications/{applicationCode}/releases?page=&size=` | Release.listReleases | I18N_VIEW | operations-i18n-release-list |
| R04 | GET | `/applications/{applicationCode}/releases/{releaseNo}` | Release.getRelease | I18N_VIEW | operations-i18n-release-detail |
| R05 | GET | `/applications/{applicationCode}/releases/{releaseNo}/diff?baseReleaseNo=` | Release.diffRelease | I18N_VIEW | operations-i18n-release-diff |
| R06 | POST | `/applications/{applicationCode}/current-release` | Release.activateRelease | I18N_PUBLISH | operations-i18n-release-activate |
| U01 | POST | `/uploads` | InternalFileApi.startUpload | I18N_EDIT | operations-i18n-upload-start |
| U02 | POST | `/uploads/{uploadId}/completion` | InternalFileApi.completeUpload + getFile | I18N_EDIT | operations-i18n-upload-complete |
| M1 | GET | `/i18n/v1/manifest?locale=&releaseNo=`（operations-console-bff） | Delivery.getManifest，应用固定 `operations-console` | PUBLIC | operations-i18n-manifest |
| M2 | GET | `/i18n/v1/manifest?locale=&releaseNo=`（client-console-bff，外部 `/api/client/i18n/v1/manifest`） | Delivery.getManifest，应用固定 `client-console` | PUBLIC | client-i18n-manifest |

实现要点：

- **入口边界：** 未知、重复或空 Query，非法路径变量（语言、应用、命名空间格式与合同请求体一致），`state` 不在三值内，分页条数不在 10/20/50/100/200，请求体未知字段 / 重复键 / 标量强转，查询、删除与完成上传带 Body，均在访问 Platform 前拒绝（400 / 422）。写 Body 上限 65536 字节。
- **错误透传：** 登记 `LocalizationErrorCode`，状态映射：`I18N_COVERAGE_INSUFFICIENT`、`I18N_NAMESPACE_NOT_FOUND` → 422，`I18N_IMPORT_PREVIEW_STALE`、`I18N_CONTENT_CHANGED` → 409；这两类 422 的 `errors[]`（字段与 Platform 文案）原样到达页面。公共 `BUSINESS_CHECK_FAILED` / `NOT_FOUND` / `CONCURRENT_REQUEST` 开启 Owner detail，Platform 原因原样到达页面。Platform 5xx → 503 `DEPENDENCY_UNAVAILABLE`，超时 504。
- **`VALIDATION_ERROR` 投影（Foundation 规定只保留字段路径，不传远端文案）：** 保存翻译只保留本次 Body 内存在的路径（如 `translations[0].text`），消息为通用"请检查此处配置"；翻译导入只保留 `namespaces.{命名空间}.{key}` 形态的路径（最多 200 条），消息"该条翻译未通过校验"；其余写接口按 Body 投影。见第 7 节问题 4。
- **回执核对：** 语言 / 应用 / 命名空间编码、发布号、差异基准号、切换后发布号、分页窗口、导出的应用与语言、manifest 的应用编码与指定发布号须与请求一致，否则 502 `UPSTREAM_INVALID_RESPONSE`。
- **读取超时 9 秒：** 发布在 Platform 事务外生成并上传语言包，沿用文件存储的 9 秒（小于 Gateway 10 秒）。
- **上传：** 请求 `{purpose: KEY_IMPORT | TRANSLATION_IMPORT, fileName: *.json, sizeBytes}`；BFF 固定原因码 `I18N_KEY_IMPORT` / `I18N_TRANSLATION_IMPORT`、类型 `application/json`、上传人为会话账号，来源系统由 `X-Caller-Service: operations-console-bff` 确定。开始上传 201 返回 Platform `FileUploadResponseBody`（PUT 授权）；完成上传返回 `{uploadId, fileId, fileAddress, scanStatus, completedAt}`。`/localization-admin/v1/uploads` 只记元数据日志，PUT 地址不进日志（有测试）。BFF 不做 2 MB 本地检查，由 Platform 导入接口拒绝。
- **manifest：** 不经 PLT-I02，不带身份（ops 在全局拦截器中排除该路径；client 本来不在任何会话拦截器范围内）。只转发 `locale`、`releaseNo`，页面传入的 `application` 等其他 Query 忽略。响应 `Cache-Control: no-cache`，强 ETag `"r{发布号|0}-{locale|default}-{内容摘要 16 位}"`，`If-None-Match` 命中返回 304 无正文。
- **改动的既有文件：** ops `PlatformClientConfiguration`（多语言代理工厂与 4 个合同 Bean）、`OperationsErrorStatusResolver`、`OperationsWebConfiguration`（排除 manifest 路径）、`application.yml`（metadata-only 前缀）、`OperationsConsoleBffApplicationAutoTest`（路由、日志前缀、代理工厂清单各加本卡条目）；client `PlatformClientConfiguration`（`LocalizationDeliveryApi` Bean）。

## 3. 注册块 135（`db/dml.sql`、`db/dml-rollback.sql`，目标 `mor_platform`，只写未执行）

第 1 部分为注册，休眠写入（权限 ACTIVE、管理员授权 REVOKED、菜单与路由 DISABLED），激活语句写在文件头注释中。

| 表 | 行数 | ID 段 |
|---|---|---|
| t_permission | 3 | 900000000000135101—103 |
| t_role_permission（PLATFORM_ADMIN 900000000000000510） | 3 | 135201—203 |
| t_console_resource | 11 | 135301—311 |
| t_gateway_route | 27 | 135401—427 |
| t_gateway_route_header | 114 | 135501—614 |
| t_gateway_route_query_param | 15 | 135701—715 |
| t_gateway_guard_rule | 158 | 135801—958 |
| 合计 | 331 | |

第 2 部分为初始数据：`t_i18n_locale` 2 行（135001—002），`t_i18n_application` 4 行（135011—014）。

- **ID 段与卡片的差异：** 请求头 114 行超出 1355xx，按块 131 的做法顺延到 1356xx。Guard 158 行占 1358xx—1359xx。初始数据放 1350xx，与块 132 把非注册数据放块内单独段的做法一致。
- **权限风险等级：**
  - I18N_VIEW 为 LOW：只读，不含敏感数据。
  - I18N_EDIT 为 MEDIUM：改动文案与配置，但发布前不影响用户。
  - I18N_PUBLISH 为 HIGH：一次改变整个控制台的线上界面文案，含切回。
- **请求头、Guard 与 Query 白名单：**
  - 管理路由照块 131。GET 带 authorization 与 origin；有 Body 的写再加 content-type 与 idempotency-key；DELETE 带 authorization、origin 与 idempotency-key。`idempotency_mode` 为 NONE。
  - 保存翻译服务于"保存并下一条"，CLIENT_IP 与 AUTHENTICATED_PRINCIPAL 限流取 120/min（同块 131 对模板校验的放宽）。其余写操作 10/min，读操作 30/min。
  - 两条 manifest 路由为 PUBLIC，只带 origin 与 if-none-match。限流为 SHARED_ROUTE 1200/min + 100 并发，CLIENT_IP 120/min + 10，无主体维度（Gateway 编译器对 PUBLIC 只允许这两个维度）。
  - Query 白名单：删除 expectedVersion；翻译列表 6 项；导出 locale；发布历史 page/size；差异 baseReleaseNo；两个 manifest 各 locale/releaseNo。
- **Client manifest 的 Target：** client-console-bff 的 Gateway Target 是经 gateway-admin 页登记的，各环境 ID 不同。SQL 用 `SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff'` 取值。不存在时 NOT NULL 约束使整块失败，文件头预检已写明。
- **文件原因码：** 现有表结构没有可登记的行，原因与后续动作见第 7 节问题 2。`I18N_RELEASE_BUNDLE`、`I18N_BUNDLE_JSON` 与公开桶路由按卡片不在本卡。

## 4. 给卡 C 的组件键、路由与用法

| 资源 | 类型 | 父级 | 前端路由 | component_key | 权限 |
|---|---|---|---|---|---|
| OPERATIONS_I18N「多语言」 | MENU（顶级，排序 70） | — | — | — | I18N_VIEW |
| OPERATIONS_I18N_TRANSLATIONS「翻译」 | MENU | 多语言 | `/i18n/translations` | `OPERATIONS_I18N_TRANSLATIONS` | I18N_VIEW |
| OPERATIONS_I18N_RELEASES「发布记录」 | MENU | 多语言 | `/i18n/releases` | `OPERATIONS_I18N_RELEASES` | I18N_VIEW |
| OPERATIONS_I18N_SETTINGS「语言与应用」 | MENU | 多语言 | `/i18n/settings` | `OPERATIONS_I18N_SETTINGS` | I18N_VIEW |
| OPERATIONS_I18N_TRANSLATION_EDIT「编辑翻译」 | BUTTON | 翻译 | | | I18N_EDIT |
| OPERATIONS_I18N_KEY_IMPORT「导入 key」 | BUTTON | 翻译 | | | I18N_EDIT |
| OPERATIONS_I18N_RELEASE_PUBLISH「发布」 | BUTTON | 翻译 | | | I18N_PUBLISH |
| OPERATIONS_I18N_RELEASE_ACTIVATE「切回此版本」 | BUTTON | 发布记录 | | | I18N_PUBLISH |
| OPERATIONS_I18N_APPLICATION_CREATE「新建应用」 | BUTTON | 语言与应用 | | | I18N_EDIT |
| OPERATIONS_I18N_NAMESPACE_MANAGE「维护命名空间」（新建、修改、删除命名空间） | BUTTON | 语言与应用 | | | I18N_EDIT |
| OPERATIONS_I18N_SETTINGS_EDIT「维护设置」 | BUTTON | 语言与应用 | | | I18N_EDIT |

- **按钮清单：** 卡片列了 5 个按钮。另加的「编辑翻译」控制保存与导入翻译，「维护设置」控制保存应用设置与语言维护，这样只有 I18N_VIEW 的人员看不到写入口。卡 C 不用可以忽略，主会话也可以删去这两行（行数相应减 2）。
- **接口：** 浏览器调用 `/api/operations/localization-admin/v1/...`，路径见第 2 节。请求、响应字段即 Platform 合同类，字段名不变。
- **导入流程：**
  1. `POST /uploads`，`purpose` 取 KEY_IMPORT 或 TRANSLATION_IMPORT，返回 201 与 PUT 授权。
  2. 浏览器按 `authorization.method/url/headers` 直传 JSON。
  3. `POST /uploads/{uploadId}/completion`（无 Body，仍需 JSON Content-Type 与 Origin），得到 `fileId`。
  4. 用 `fileId` 调 key 导入预览 / 确认，或调翻译导入。`scanStatus` 未到 AVAILABLE 时 Platform 会拒绝导入，页面需等待或重试。
- **错误码处理：**
  - 422 `I18N_COVERAGE_INSUFFICIENT`：`errors` 中 `missing.{语言}` 的 message 为 key，最后一条 `missingTotal` 的 message 为总数。
  - 422 `I18N_NAMESPACE_NOT_FOUND`：`errors[].field` 为 `namespaces.{编码}`。
  - 409 `I18N_IMPORT_PREVIEW_STALE`：提示重新预览。
  - 409 `I18N_CONTENT_CHANGED`：提示重新发布。
  - 409 `CONCURRENT_REQUEST`：版本冲突或同应用写入并发。
  - 422 `VALIDATION_ERROR` 只带字段定位，不带 ICU 原因，见第 7 节问题 4。
- **批量保存体积：** Gateway 请求 Body 绝对上限 65536 字节，前端要按字节分批（见第 7 节问题 3）。
- **manifest：** ops-web 读取 `GET /api/operations/i18n/v1/manifest?locale=&releaseNo=`（页面 `?release=7` 预览时传 `releaseNo=7`）；client-web 读取 `GET /api/client/i18n/v1/manifest?locale=&releaseNo=`（给卡 D）。

## 5. 需主会话合并到 Platform 的内容（本卡未改这些文件）

1. **`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`：** 末尾追加 `db/dml.sql` 的两部分，第 1 部分注册块 135、第 2 部分语言与应用初始数据，内容逐字取用，两段各自一个事务。合并前确认卡 A 的表结构与第 2 部分列名一致（取自 worktree `changes/2026-10-23-localization.sql`）。
2. **`paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json`：** 属卡 A 范围。块 131 发布时它随 Platform 一起改动，角色授权页据它展示页面动作。需追加 3 项：

```json
{"componentKey": "OPERATIONS_I18N_TRANSLATIONS", "displayName": "翻译", "frontendRoute": "/i18n/translations",
 "actions": [{"resourceCode": "OPERATIONS_I18N_TRANSLATION_EDIT", "displayName": "编辑翻译"},
             {"resourceCode": "OPERATIONS_I18N_KEY_IMPORT", "displayName": "导入 key"},
             {"resourceCode": "OPERATIONS_I18N_RELEASE_PUBLISH", "displayName": "发布"}]},
{"componentKey": "OPERATIONS_I18N_RELEASES", "displayName": "发布记录", "frontendRoute": "/i18n/releases",
 "actions": [{"resourceCode": "OPERATIONS_I18N_RELEASE_ACTIVATE", "displayName": "切回此版本"}]},
{"componentKey": "OPERATIONS_I18N_SETTINGS", "displayName": "语言与应用", "frontendRoute": "/i18n/settings",
 "actions": [{"resourceCode": "OPERATIONS_I18N_APPLICATION_CREATE", "displayName": "新建应用"},
             {"resourceCode": "OPERATIONS_I18N_NAMESPACE_MANAGE", "displayName": "维护命名空间"},
             {"resourceCode": "OPERATIONS_I18N_SETTINGS_EDIT", "displayName": "维护设置"}]}
```

## 6. 测试（JDK 25，均为前台执行）

```text
./mvnw -q -pl platform-support/paytech-platform-support-api -am install -DskipTests      → 成功
./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am test \
  -Dtest='com/paytech/operations/console/bff/**/*Locali*' -Dsurefire.failIfNoSpecifiedTests=false
  LocalizationManifestAutoTest   Tests run: 4,  Failures: 0, Errors: 0
  LocalizationBrowserAutoTest    Tests run: 63, Failures: 0, Errors: 0
  Tests run: 67, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
./mvnw -o -pl client-console-bff/paytech-client-console-bff-test -am test \
  -Dtest='**/*Manifest*' -Dsurefire.failIfNoSpecifiedTests=false
  ClientLocalizationManifestHttpAutoTest  Tests run: 4, Failures: 0 — BUILD SUCCESS
全量（-Dtest 限定本 BFF 包）：
  client-console-bff   Tests run: 91,   Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
  operations-console-bff Tests run: 2303, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

- **全量命令限定 `-Dtest`：** 不带 `-Dtest` 时，`-am` 会连带运行 `tools/arch-rules` 的测试。其中 `TableOwnershipAutoTest.test_normalFlow` 失败，原因是 worktree 中卡 A 未完成的 `table-ownership.yaml` 改动（表数断言），与本卡无关。
- **首轮全量的 2 个失败：** `OperationsConsoleBffApplicationAutoTest` 的路由、日志前缀与代理工厂清单是精确比对，已补本卡条目后通过。
- **覆盖范围：**
  - 23 个接口逐条核对方法、路径、Query、Body、操作人头，以及不转发 Authorization / Cookie / Idempotency-Key。
  - 23 个接口逐条验证授权拒绝。
  - 覆盖率不足与命名空间不存在的 `errors` 透传；预览过期与文案已变 409；公共业务拒绝、不存在、并发冲突的 detail 透传。
  - 422 字段投影（保存与导入两种）；Platform 503；7 类回执不符 502；14 种本地边界拒绝。
  - 两个用途的上传原因码与 PUT 授权不进日志；完成上传、地址不符 502、文件策略拒绝 422；上传参数拒绝。
  - 两个 manifest：固定应用编码、不调 PLT-I02、不带身份、no-cache、ETag、304、内容变化换 ETag、无发布、404 / 503 / 502、非法参数。

## 7. 待裁决问题

1. **Gateway 会抹掉 manifest 的缓存头。** `TrustedTerminalResponseHeaders` 对所有已匹配路由删除下游 Cache-Control，并统一设置 `no-store` 与 `Pragma: no-cache`。浏览器因此不缓存，也不会发 If-None-Match，technique 3.7 的 no-cache + ETag + 304 经 Gateway 后不生效（功能正确，只是每次全量返回）。Gateway 对下游 304 无正文响应的处理也未验证。需要 api-gateway 为 PUBLIC manifest 路由保留下游 Cache-Control，例如路由级缓存策略，这不在本卡范围。BFF 一侧已按方案实现并测试。
2. **导入文件当前过不了 Platform 文件检查。** 写入路由 `t_file_storage_route` 按来源系统选策略（公开桶需求后再加访问级别），不按原因码。operations-console-bff 的私有路由在块 132 发布时登记为 `CARD_BIN_CSV`，只允许 `text/csv`。需要二选一：
   - 新建同时允许 `text/csv` 与 `application/json` 的策略（如 `OPERATIONS_BFF_IMPORT`）并把该路由改指过去；
   - 给写入路由增加原因码维度（需新设计）。

   另外 Platform FORMAT 检查需支持 `application/json`，这属于集成卡。两者都完成前，上传会在完成阶段被检查拒绝。
3. **批量保存的合同上限与 Gateway 冲突。** 合同允许 200 条 × 8000 字符，但 Gateway 写 Body 绝对上限 65536 字节（编译器 `MAX_BODY_LIMIT_BYTES`），BFF 也按 65536 限制。需决定是前端按字节分批，还是在合同里写明体积上限（例如保持 200 条但总字节受限）。
4. **ICU 错误原因到不了页面。** Foundation 对 Platform 的 422 `VALIDATION_ERROR` 只保留字段路径，不外显远端文案。保存翻译时页面能定位到哪一条，但看不到"第 n 个字符缺少 other"这类原因。翻译导入只保留合同形态的路径。需要时有两种做法：
   - Platform 把 ICU 校验失败改为专有 Code（如 `I18N_MESSAGE_INVALID`，422 带 errors），BFF 登记后整体透传（与覆盖率不足同一机制）；
   - 页面在保存前用前端 ICU 解析器做同样检查。

   key 导入确认的 `VALIDATION_ERROR` 路径不在请求体内，全部被丢弃；预览阶段已在 200 响应中返回 problems，影响有限。
5. **ETag 比 technique 多了内容摘要。** technique 写的是"发布号 + locale"，实现为发布号、locale 加 manifest 内容摘要。原因是语言列表与默认语言在两次发布之间可以修改；只用发布号会让浏览器以 304 留住过期的语言列表。请确认。
6. **初始应用阈值取 100%。** technique 没有给阈值，取了最严格值。operations-console 默认 zh-CN、必须覆盖 zh-CN；另 3 个应用默认 en、必须覆盖 zh-CN。请确认，也可在页面调整。
7. **额外的两个按钮资源**（编辑翻译、维护设置）见第 4 节，请确认保留。
8. **两个 manifest 回执不符时都返回 502。** client-console-bff 既有 Port 习惯把回执不符映射为 503；这里与 ops 一致取 502 `UPSTREAM_INVALID_RESPONSE`。前端两种情况都回退内置文案，不影响行为。

## 8. 跟进修改

主会话对第 7 节的裁决（2026-10-23）如下：

| 问题 | 裁决 | 本卡动作 |
|---|---|---|
| 7.1 Gateway 抹掉 manifest 缓存头 | 接受现状，不改 Gateway | BFF 的 ETag / 304 代码保留 |
| 7.2 导入文件过不了 Platform 文件检查 | 由集成卡处理 | 无 |
| 7.3 批量保存与 Gateway Body 上限冲突 | 前端按字节分批，合同不变 | 无 |
| 7.4 ICU 错误原因到不了页面 | 改为专有错误码透传 | 见下文 |
| 7.5 ETag 加内容摘要 | 接受 | 无 |
| 7.6 初始应用阈值 100% | 接受 | 无 |
| 7.7 额外两个按钮资源 | 接受 | 无 |
| 7.8 两个 manifest 回执不符返回 502 | 接受 | 无 |

另有一处改动：应卡 C 对齐，命名空间按钮由 `OPERATIONS_I18N_NAMESPACE_CREATE「新建命名空间」` 改为 `OPERATIONS_I18N_NAMESPACE_MANAGE「维护命名空间」`。该按钮覆盖新建、修改与删除，ID 不变，`db/dml.sql` 与第 4、5 节已同步。

**7.4 的实现：**

- **错误码：** 卡 A 在 `LocalizationErrorCode` 中新增 `I18N_MESSAGE_INVALID`。开工时 worktree 尚无该值，前台轮询约 6.5 分钟后出现，随后按真实枚举实现，没有使用字符串占位。
- **BFF 透传：** 多语言代理工厂登记的是 `LocalizationErrorCode.values()`，新 Code 自动登记，无需改代码。`OperationsErrorStatusResolver` 把它与覆盖率不足同组映射为 422。批量保存与翻译导入都带请求体，所以 Platform 的 `errors[{field, message}]` 原样到达页面，包括带字符位置的中文原因。
- **对 3.4 节问题 4 的影响：** 公共 `VALIDATION_ERROR` 的字段投影仍保留，用于其他字段校验失败；ICU 原因改由 `I18N_MESSAGE_INVALID` 携带。
- **新增测试：** `LocalizationBrowserAutoTest.test_messageInvalidKeepsIcuReasons`，共 2 例。分别为批量保存（field `translations[0].text`）与翻译导入（field `namespaces.team.team.invite.title`），断言 422、Code、本次 Request ID、errors 字段与原因原样，且不外露 Owner Request ID。
- **验证：** 重新安装 `paytech-platform-support-api` 后运行定向测试。

```text
./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am test \
  -Dtest='com/paytech/operations/console/bff/**/*Locali*' -Dsurefire.failIfNoSpecifiedTests=false
Tests run: 69, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## 9. 跟进修改（A-report §11 的新错误码与响应字段）

- **新错误码 `I18N_IMPORT_FILE_NOT_READY`（409）：** 多语言代理工厂登记的是整个错误码枚举，新 Code 自动登记，无需改代码。`OperationsErrorStatusResolver` 把它与导入预览过期、文案已变同组映射为 409。合同 Code 已开启 Owner detail，Platform 的原因原样到达页面。
- **新错误码的测试：** 新增 `test_importFileNotReadyPassesThrough`，共 3 例，覆盖 key 导入预览、确认与翻译导入。断言 409、Code、Platform 原因与本次 Request ID，且不外露 Owner Request ID。
- **新响应字段：** 包括预检与发布摘要（历史、详情、上线中的当前发布）的 `changeSummary`、发布详情的 `checkProgress`、应用的 `pendingChangeCount`。BFF 直接返回 Platform 响应类、不做重映射，重新安装 api 模块后这些字段随之透传。
- **新响应字段的验证：** 边界样例 `localization-boundary.json` 已补齐这些字段。逐接口转发测试按整棵 JSON 相等比较，能证明字段原样到达浏览器。涉及的接口：
  - 应用列表、详情、新建与修改（`pendingChangeCount`）；
  - 预检（`changeSummary`）；
  - 发布、发布历史与发布详情（`changeSummary`，详情另含 `checkProgress`）。
- **验证：** 重新安装 `paytech-platform-support-api` 后运行定向测试。

```text
./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am test \
  -Dtest='com/paytech/operations/console/bff/**/*Locali*' -Dsurefire.failIfNoSpecifiedTests=false
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## 10. 阶段 7 快照故障

**根因：** 两条 PUBLIC manifest 路由（`operations-i18n-manifest`、`client-i18n-manifest`）的 SHARED_ROUTE Guard 超出了 Gateway 编译器的绝对上限。

- **超出的数值：** 速率 1200/min 超过 `MAX_RATE_CAPACITY`、`MAX_RATE_REFILL_TOKENS` 的上限 600；并发 100 超过 `MAX_CONCURRENCY_LIMIT` 的上限 50。
- **抛出位置：** 两项分别违反 `validateGuardShape` 中的 RATE_LIMIT / CONCURRENCY 数值形状规则，`GatewayConfigurationCompiler.invalid(...)` 抛出 `IllegalArgumentException`，消息为 `RATE_LIMIT Guard 数值形状不合法: route=operations-i18n-manifest`。
- **DISABLED 也会失败的原因：** `compile()` 会对每一行路由执行 `compileRoute()`，Guard 校验在其中；只有路径冲突检查才限于生效路由。所以休眠写入也让整个快照失败，与新旧 Platform 代码无关。
- **我的疏漏：** 第 3 节当时只核对了 Guard 维度规则，没有核对数值上限。

**修复：** 只改两条路由的 SHARED_ROUTE Guard 共 4 行，ID 不变；行数（331）、其他路由与激活 / 回滚 SQL 都不变。CLIENT_IP 120/min + 10 在上限内，保持不变。

```diff
-  (900000000000135951, 900000000000135426, 'RATE_LIMIT', 'SHARED_ROUTE', 1200, 1200, 60000, NULL),
-  (900000000000135952, 900000000000135426, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 100),
+  (900000000000135951, 900000000000135426, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
+  (900000000000135952, 900000000000135426, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
-  (900000000000135955, 900000000000135427, 'RATE_LIMIT', 'SHARED_ROUTE', 1200, 1200, 60000, NULL),
-  (900000000000135956, 900000000000135427, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 100),
+  (900000000000135955, 900000000000135427, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
+  (900000000000135956, 900000000000135427, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
```

改动的副本有 3 处，`grep 900000000000135401` 确认没有其他副本。每处的头注释同步改为 `SHARED_ROUTE 600/min + 50 concurrent (the compiler ceilings)`；第 3 节中的"1200/min + 100 并发"以本节为准。

- 主工作区 `db/dml.sql`；
- `release/sql/10-block135-registration.sql`；
- `Code-MoR-i18n-main/paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 中的块 135。

`db/dml-rollback.sql`、`release/sql/20/21/92-*.sql` 与验收脚本按 ID 或计数工作，不需要改，已检查没有引用旧数值。

**证明：** 用一次性无网络容器 `docker run --rm --network none mysql:8.4.11` 载入 mor-test 备份 `/Users/kenny/Code-MoR-backups/20261023T082205Z-i18n-mor-test/mor_platform-full.sql`。未接触 mor-test 与 local-environment。

- **校验方式：** 每一步导出 6 张表（target、route、permission、header、query、guard），按生产 `GatewayConfigurationRepositoryImpl` 的加载方式组装 `LoadedConfiguration`（permission 只取 HUMAN_PERMISSION 路由引用的 ID）。然后调用 Code-MoR-i18n-main 当前源码编译的 `GatewayConfigurationCompiler.compile`，在 JDK 25 下以单文件 Java 运行。
- **脚本：** 校验程序为 scratchpad 中的 `snap/SnapshotCheck.java` 与 `snap/check.sh`。
- **执行方式：** 发布包 `release/scripts/apply_sql.sh` 用演练覆盖 `I18N_REL_MYSQL_CONTAINER` / `I18N_REL_MYSQL_PWD` 指向该容器。该脚本要求输出目录里有非空的 `mor_platform-full.sql`，这里放了占位文件。

| 步骤 | 结果 |
|---|---|
| 基线（备份原样） | COMPILE OK：547 路由，生效 542 |
| 原块 135（1200 / 100）提交，全部 DISABLED | **COMPILE FAILED：RATE_LIMIT Guard 数值形状不合法: route=operations-i18n-manifest**（复现 mor-test 现象） |
| `rollback-reg-dry` → `rollback-reg` | rollback registration ok / committed；COMPILE OK：547 / 542 |
| `reg-dry`（修复后，未提交） | registration 3/3/11/27/114/15/158 ok；COMPILE OK：547 / 542 |
| `reg`（修复后提交，全部 DISABLED） | committed；COMPILE OK：574 路由，生效 542，i18n 生效 0 |
| `activate-dry` → `activate` | activate 3/11/27/1 ok / committed；COMPILE OK：574 路由，生效 569，i18n 生效 27 |

最后一行说明激活后 27 条路由全部进入快照，没有 Method/Path 冲突。一次性容器已删除。

**后续建议：** 以后的注册块在提交前用同一方式（备份 + 编译器）做一次离线编译，可以挡住这类只在快照阶段暴露的约束。
