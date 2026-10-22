# 任务卡 D 交付报告：BFF 转发与注册块 131

工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`。没有提交、推送或执行 SQL，也没有动 local-environment 容器，没有改 platform-support 的 Java 代码。

## 1. 已读与实现位置

- 已读：`AGENTS.md`、`DEVELOPMENT_CONVENTIONS.md`（BFF 职责、公共分页、DTO 形态、异常与日志）、`JAVA_PROJECT_STRUCTURE.md` §3.3、`UNIT_TEST_GUIDE.md` §5/§16–19、`examples/WRITE_API_EXAMPLE.md` §11、`tasks/C-api-contract.md`、`technique.md` §7，以及 Platform 的三个 Api 源码和 DTO。参照写法是币种（`CurrencySpec*`）、通知（严格 `convertValue`）和外部 API（`service/model/externalapi/{request,response}`、`ExternalApiRequestConfiguration`）。
- 这一层只做同路径简单转发：Controller 直接调用一个 Client Port，没有 Processor 或 Service 层，也没有本地写入和事务。所有业务判断、权限二次校验和调用方白名单都由 Platform 负责。

## 2. 改动文件

BFF app（`paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/...`）

| 文件 | 内容 |
|---|---|
| `ext/rest/DataQueryManagementController.java`（新） | 17 个管理端点，`/data-query-admin/v1`。路径 ID、模板编码、Query 枚举和关键字长度由 MVC 内置方法校验（与币种一致，不加 `@Validated`） |
| `ext/rest/DataQueryExecutionController.java`（新） | 执行端点，`/data-query/v1` |
| `integration/PlatformDataQueryClient.java`（新） | 本地端口，18 个方法 |
| `integration/impl/PlatformDataQueryClientImpl.java`（新） | 每个入口调用一次 Platform。全部带 `X-Gateway-Actor-Subject`/`X-Gateway-Session-Id`；执行另带 `X-Caller-Service=operations-console-bff`（`CALLER_SERVICE`）。内部 Token 沿用 `InternalCallTokenOptions` |
| `integration/impl/DataQueryContractConverter.java`（新） | 严格 `convertValue`，字段多出或缺失时返回 502。另外核对：分页窗口（size 允许 10/20/50/100/200）、当前页身份不重复、详情身份；写回执的 objectType、ID、编码和状态（新建数据源须为 INACTIVE，启停须等于目标状态，删除须为 DELETED）；模板详情的当前版本号 |
| `integration/impl/DataQueryValidationConverter.java`（新） | 把 422 投影到本次 Body，并**保留 Platform 文案**（见 §6 第 3 条）。缺少的叶子字段（如 `params.startDate`）也能定位；越界下标、未知顶层字段丢弃；不安全文案换成统一提示 |
| `service/model/dataquery/request/*`（13 个，新）、`response/*`（13 个，新） | 页面模型，与 Platform DTO 逐字段对应：普通 class，Lombok，中文 JavaDoc，Bean Validation |
| `configuration/PlatformClientConfiguration.java` | 注册 `DataSourceManagementApi`、`QueryTemplateManagementApi`、`DataQueryExecutionApi` 三个代理，共用新增的 `operationsDataQueryPlatformProxyFactory`：登记 `DataQueryErrorCode`，开启字段错误和 Owner detail，读取超时 9s；503 且 Code 为 `DATA_SOURCE_UNAVAILABLE` 时保留这个 Code。抽出 `platformClient`/`platformErrors` 两个私有方法，其他代理行为不变 |
| `configuration/DataQueryRequestConfiguration.java`（新） | 只对数据查询请求体做严格 JSON 解析：未知属性、重复键、标量强转、尾随内容都拒绝；Body 上限 65536 |
| `configuration/ConfigurationManagementRequestConfiguration.java` | Query 严格边界加入 `/data-query-admin/v1/**`、`/data-query/v1/**` |
| `configuration/OperationsErrorStatusResolver.java` | `DATA_QUERY_TIMEOUT`、`DATA_QUERY_RESULT_TOO_LARGE` 映射 400，`DATA_SOURCE_UNAVAILABLE` 映射 503 |
| `resources/application.yml` | 只记元数据日志的前缀：`/data-query-admin/v1/data-sources`、`/data-query-admin/v1/data-source-connection-tests`、`/data-query-admin/v1/template-trials`、`/data-query/v1`；新增 `operations.data-query-read-timeout: 9s` |

BFF test（`paytech-operations-console-bff-test/src/test/...`）

| 文件 | 内容 |
|---|---|
| `auto/DataQueryBrowserAutoTest.java`（新，57 例） | 真实 BFF 入站，Platform 用 Loopback Simulator 返回 DTO，没有 Mockito Stub |
| `auto/DataQueryRegistrationAutoTest.java`（新，5 例） | 注册断言：解析 `platform-support-initial-data.sql` 的块 131，与 BFF Controller 注解和边界清单交叉核对 |
| `resources/data-query-boundary.json`（新） | 18 条样例（Q01—Q18） |
| `auto/OperationsConsoleBffApplicationAutoTest.java` | 精确路由集合加 18 条，元数据前缀加 4 个，代理工厂集合加 `operationsDataQueryPlatformProxyFactory` |

注册 DML：`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾追加块 131，共 283 行（含 2026-10-22 主会话补充的 2 条 BUTTON）。

## 3. 18 条路径对照

BFF 路径与 Platform 路径相同；Gateway 外部路径是在前面加 `/api/operations`。

| # | Method + BFF/Platform 路径 | Platform 方法 | Route Code | 权限 | Query 白名单 |
|---|---|---|---|---|---|
| 1 | GET `/data-query-admin/v1/data-sources` | `listDataSources` | operations-data-source-list | DATA_QUERY_READ | status, engineType, keyword, page, size |
| 2 | GET `…/data-sources/{dataSourceId}` | `getDataSource` | operations-data-source-detail | DATA_QUERY_READ | — |
| 3 | POST `…/data-sources` | `createDataSource` | operations-data-source-create | DATA_SOURCE_MANAGE | — |
| 4 | PUT `…/data-sources/{dataSourceId}` | `updateDataSource` | operations-data-source-update | DATA_SOURCE_MANAGE | — |
| 5 | POST `…/data-source-connection-tests` | `testDataSourceConnection` | operations-data-source-connection-test | DATA_SOURCE_MANAGE | — |
| 6 | POST `…/data-sources/{dataSourceId}/status` | `changeDataSourceStatus` | operations-data-source-status | DATA_SOURCE_MANAGE | — |
| 7 | DELETE `…/data-sources/{dataSourceId}` | `deleteDataSource` | operations-data-source-delete | DATA_SOURCE_MANAGE | expectedVersion |
| 8 | GET `…/templates` | `listQueryTemplates` | operations-query-template-list | DATA_QUERY_READ | dataSourceId, status, keyword, page, size |
| 9 | GET `…/templates/{templateId}` | `getQueryTemplate` | operations-query-template-detail | DATA_QUERY_READ | — |
| 10 | POST `…/templates` | `createQueryTemplate` | operations-query-template-create | DATA_QUERY_TEMPLATE_MANAGE | — |
| 11 | PUT `…/templates/{templateId}` | `updateQueryTemplate` | operations-query-template-update | DATA_QUERY_TEMPLATE_MANAGE | — |
| 12 | POST `…/templates/{templateId}/status` | `changeQueryTemplateStatus` | operations-query-template-status | DATA_QUERY_TEMPLATE_MANAGE | — |
| 13 | GET `…/templates/{templateId}/versions` | `listQueryTemplateVersions` | operations-query-template-version-list | DATA_QUERY_READ | page, size |
| 14 | GET `…/templates/{templateId}/versions/{versionNo}` | `getQueryTemplateVersion` | operations-query-template-version-detail | DATA_QUERY_READ | — |
| 15 | POST `…/templates/{templateId}/restoration` | `restoreQueryTemplate` | operations-query-template-restore | DATA_QUERY_TEMPLATE_MANAGE | — |
| 16 | POST `…/template-validations` | `validateQueryTemplate` | operations-query-template-validate | DATA_QUERY_TEMPLATE_MANAGE | — |
| 17 | POST `…/template-trials` | `trialQueryTemplate` | operations-query-template-trial | DATA_QUERY_TEMPLATE_MANAGE | — |
| 18 | POST `/data-query/v1/templates/{templateCode}/executions` | `executeDataQuery`（带 X-Caller-Service） | operations-data-query-execute | DATA_QUERY_EXECUTE | — |

## 4. 块 131

| 子区间 | 表 | 行数 |
|---|---|---|
| 1311xx（101–104） | t_permission：READ LOW、SOURCE_MANAGE HIGH、TEMPLATE_MANAGE HIGH、EXECUTE LOW，均为 ACTIVE | 4 |
| 1312xx（201–204） | t_role_permission：管理员角色 900000000000000510，REVOKED | 4 |
| 1313xx（301–303） | t_console_resource：菜单组“数据查询”（OPERATIONS_DATA_QUERY，顶级，sort 65）及子菜单“数据源” `/data-query/data-sources`（OPERATIONS_DATA_SOURCES）、“查询模板” `/data-query/templates`（OPERATIONS_QUERY_TEMPLATES），均为 DISABLED，绑定 DATA_QUERY_READ | 3 |
| 1313xx（304–305） | t_console_resource BUTTON（照 125302）：`OPERATIONS_DATA_SOURCE_MANAGE`“维护”（父 131302，绑定 DATA_SOURCE_MANAGE）、`OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`“维护”（父 131303，绑定 DATA_QUERY_TEMPLATE_MANAGE），DISABLED | 2 |
| 1314xx（401–418） | t_gateway_route：上表 18 条，DISABLED；GET/DELETE body 0，其余 65536；接收 5000、响应 10000 | 18 |
| 1315xx（501–582） | t_gateway_route_header：照块 125。GET 2 条；POST/PUT 6 条；DELETE 4 条（无 content-type） | 82 |
| 1316xx（601–613） | t_gateway_route_query_param：见上表 | 13 |
| 1317xx–1318xx（131701–131808） | t_gateway_guard_rule：每条路由 6 条（见 §6 第 5 条） | 108 |
| 合计 | | 234 |

块头注释写明了子区间、行数和回滚方式，并注明执行前需按 live `mor_platform` 复核 900000000000131001–131999 未被占用，权限、资源、路由 Code 和（方法, 外部路径）没有重复。

## 5. 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech
./mvnw -q -o -pl platform-support/paytech-platform-support-api -am install -DskipTests   # EXIT=0
./mvnw -o -pl operations-console-bff -amd test                                          # BUILD SUCCESS
```

- BFF 全模块：**Tests run: 2080, Failures: 0, Errors: 0, Skipped: 0**。其中有 `ArchitectureAutoTest`、`OperationsHttpBodyNamingAutoTest`、`OperationsConsoleBffApplicationAutoTest`；`DataQueryBrowserAutoTest` 57 例、`DataQueryRegistrationAutoTest` 5 例全部通过。
- `DataQueryBrowserAutoTest` 覆盖：
  - 18 条路径：方法、路径、Query、Body 和操作人 Header 各转发一次；只有执行带调用方 Header；不转发 Authorization、Cookie、Idempotency-Key。
  - 18 条授权拒绝。
  - 业务错误原样透传：400 BUSINESS_CHECK_FAILED（含 GET/DELETE）、404、409、403、DATA_QUERY_TIMEOUT、DATA_QUERY_RESULT_TOO_LARGE，都保留 Code 和原因，requestId 用浏览器的。
  - 503 DATA_SOURCE_UNAVAILABLE（测试连接、试运行、执行）保留 Code；其他 503 仍为 DEPENDENCY_UNAVAILABLE。
  - 模板 422：同一字段的多条文案都保留，越界和未知字段丢弃。
  - 执行 422：`params.*` 能定位，缺少的必填参数也能定位，不安全文案换成统一提示。
  - 本地拒绝 19 例（未知/重复/非法 Query、GET 带 Body、非法路径、未知字段、类型强转、缺少必填字段、413 超长 Body 等）。
  - 上游无效响应 502 共 20 种。
  - 合同外字段（如 `passwordToken`）不投影。
  - 密码只出现在出站请求中。
  - 测试日志里确认没有密码 canary；`data-sources`、`connection-tests`、`template-trials`、`/data-query/v1` 只记元数据。
- 注册断言测试不需要数据库，读的是 SQL 文件。它核对：
  - 行数和 ID 号段；
  - 权限、授权和菜单逐行；
  - 路由集合与 BFF Controller 注解里的（方法, 路径）完全一致，外部路径为前缀加同路径，权限与合同一致，Body 上限与有无 `@RequestBody` 一致，Query 白名单与入口的 `@RequestParam` 一致；
  - Header 和 Guard 的形态；
  - 边界清单的 routeCode 与本块路由一致。
- 顺便跑了 `node --test scripts/pdmaner/platform-support-schema.test.mjs`：9 项里 4 项失败，都与块 131 无关。失败的是：表数（55）、凭证 bootstrap、Client API 127 行 `includes`（第 121 行）、Notification DDL。这些失败来自工作区已有的 schema/DDL 改动和更早的问题（HEAD 里初始化数据早已有 24 个 `START TRANSACTION`），不在本卡范围，留给主会话或卡 C。

## 6. 需要主会话裁决的问题

> **主会话裁决（2026-10-22）**
> - 第 1 条：Platform 执行硬上限改为 8 秒，由卡 C 处理；BFF 读取超时保持 9 秒，不改。
> - 第 2 条：Platform 正文上限改为 48 KB，由卡 C 处理；BFF 与 Gateway 的 65536 上限不变。
> - 第 3 条：接受。422 保留 Platform 原文，按字段排序输出。
> - 第 4 条：接受 LOW。
> - 第 5 条：接受现有限流方案。
> - 第 7 条与 Idempotency-Key：按钮已补（见第 4 节 131304–131305），写路由放行情况见第 6a 节。
> - 仍待处理：第 6 条（console-resource-capabilities.json 登记两个菜单组件与两个按钮，属 platform-support）和第 8 条（Platform 只记元数据日志的前缀与 BFF 一致），两项都在卡 C 范围。

1. **执行超时和 Gateway 上限冲突。** Gateway 的 `MAX_RESPONSE_TIMEOUT_MILLIS=10000`，而 Platform 的 `max-timeout` 是 30 秒，模板 `timeoutSeconds` 允许 1..30。BFF 对数据查询的读取超时取 9s（与 ledger 长耗时调用同一原则）。所以 9 秒以上的查询，浏览器会拿到 504 DEPENDENCY_TIMEOUT，而 Platform 仍会跑到自己的超时。建议二选一：把 Platform 的 `max-timeout`、`timeoutSeconds` 上限降到 8s；或者另立异步执行方案。
2. **模板正文大小和 Gateway Body 上限冲突。** Gateway 的 `MAX_BODY_LIMIT_BYTES=65536`，BFF 也是 65536；而模板 `body` 允许 `@Size(max=65536)`，Platform 的 `max-body-length` 是 64 KB。加上 JSON 转义、parameters、columns 之后，实际能保存的正文明显小于 64 KB，中文正文按 UTF-8 计更少。建议 Platform 把正文上限降到约 48 KB（前端同步），或另行评估提高 Gateway 上限。
3. **422 字段文案原样透传，和 Foundation 的默认做法不同。** `HttpRequestValidationException` 默认只给路径，其他配置页会把文案统一换成“请检查此处配置”。按卡片“Problem 原样保留”和合同里“第 N 行第 M 列：”的要求，`DataQueryValidationConverter` 从 Foundation 已校验的 Problem 中取回 `errors[].message`，但路径仍只保留本次 Body 内的；文案为空、超过 512 字符或含控制字符时才换成统一提示。另外，MVC 边界会按字段排序输出 errors，所以页面不能依赖 Platform 原始顺序。503 `DATA_SOURCE_UNAVAILABLE` 用自定义的 dependency mapper 保留 Code，detail 是枚举里的安全文案，不是 Platform 原文。这两处请确认可以接受。
4. **权限等级。** 卡片写的是 NORMAL，但 `t_permission.risk_level` 只允许 LOW/MEDIUM/HIGH/CRITICAL，所以 READ 和 EXECUTE 用了 LOW。如果希望执行权限更醒目，可以改成 MEDIUM（`DataQueryRegistrationAutoTest` 需同步）。
5. **Guard 限流和块 125 的差异。** 编辑器以 500 ms 防抖调用接口 16（模板校验），所以这条路由的 CLIENT_IP 和 AUTHENTICATED_PRINCIPAL 限流定为 120/分钟（块 125 的写路由是 30/10）。执行（18）按读路由，30/分钟。试运行和其他写路由与块 125 一致（主体 10/分钟）。需要的话请调整。
6. **Platform 侧缺页面能力登记。** `paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` 里没有 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`，Console 资源管理因此无法维护或绑定这两个组件。块 126 的渠道流水也同样缺失。这个文件属于 platform-support，不在本卡范围，没有改。另外，Platform 测试基线 `test/builder/OperationsCatalog.java` 如果需要这 4 项权限（ID 900000000000131101–104），由卡 C 添加。两件事都已发消息告诉 dq-card-c。
7. **按钮级资源（已按主会话要求补齐）。** 块 131 新增 131304 `OPERATIONS_DATA_SOURCE_MANAGE`、131305 `OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE` 两条 BUTTON，编码与卡 E 对齐；`DataQueryRegistrationAutoTest` 已同步断言。这两个按钮同样需要登记到第 6 条所说的 `console-resource-capabilities.json`，作为对应组件的 actions。
8. **只记元数据日志的前缀需与 Platform 一致。** BFF 用的是上面 4 个前缀，已告诉卡 C，请主会话确认 Platform 的 `application.yml` 用的是同一组。
9. **页面模型与 Platform DTO 同步。** BFF 页面模型是 Platform DTO 的逐字段副本。卡 C 之后如果改 DTO，需要同步 `service/model/dataquery/**`；严格 `convertValue` 在转发测试里会以 502 暴露差异。

## 6a. Idempotency-Key 放行（主会话补充项核对）

块 131 的 11 条 POST/PUT 路由和 1 条 DELETE 路由都有 `idempotency-key` 的 `CORS_REQUEST_ALLOWED` 和 `FORWARD_REQUEST` 两条 Route Header，`idempotency_mode` 为 `NONE`。这与块 125 的写路由（125402–125404：headers 125507/125508、125513/125514、125519/125520，`idempotency_mode=NONE`）一致，Gateway 会接受并转发这个请求头。6 条 GET 路由不放行，与块 125 的 GET 路由相同。BFF 不处理这个请求头，也不向 Platform 转发（`test_normalFlow` 断言下游没有收到）。`DataQueryRegistrationAutoTest.test_routeHeadersAndGuards` 和 `test_routesMatchBffEntries` 逐路由断言 Header 集合和 `idempotency_mode=NONE`。补充后重跑 `DataQuery*AutoTest`：62 例，0 失败。

## 7. 写接口示例第 11 节核对（BFF 简单转发）

- **API 与 Body：** class + 字段注释 + Hibernate Validator，入口用 `@Valid` 和 MVC 内置方法校验。
- **Controller：** 只做适配。
- **以下各项不适用：** Processor、Context、Repository、事务、锁、View、DataObject。BFF 无状态，没有本地写入，同步命令由 Platform 拥有。
- **请求幂等：** 不处理 Idempotency-Key，测试确认不向下游转发。
- **复用公共包：** 复用了 `HttpServiceClientFactory`、`PagingResponseBody`、公共 Problem 和 `RequestBodyValidationException`；没有生产 Mock，也没有为测试加生产分支。
- **验证：** 见第 5 节。未覆盖的是 9 秒读取超时的实际挂起路径：Simulator 的 delay 需要等满超时时长，本卡没有加这个慢测试。
