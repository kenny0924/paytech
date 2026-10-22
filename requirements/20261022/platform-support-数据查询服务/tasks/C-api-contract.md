# 卡 C 接口合同：Platform 数据查询 18 个接口

> Java 合同源码（权威，本文是阅读摘要）：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/dataquery/`
> （`DataSourceManagementApi`、`QueryTemplateManagementApi`、`DataQueryExecutionApi`，`request/`、`response/`），错误码 `api/enums/DataQueryErrorCode`。已 `install` 到本地仓库（`paytech-platform-support-api:1.0.0-SNAPSHOT`）。

## 通用约定

- Platform 地址：`{context-path=/platform-support}` + 下表路径。管理类前缀 `/data-query-admin/v1`，执行类 `/data-query/v1`。
- 所有接口都带 Header `X-Gateway-Actor-Subject`（操作人账号 ID）、`X-Gateway-Session-Id`（会话）；执行接口另带 `X-Caller-Service`（本期固定 `operations-console-bff`）。
- ID 一律十进制字符串；时间为 UTC ISO-8601 微秒精度字符串；枚举为 String Code。
- 列表复用 `PagingRequestBody` 口径（Query `page` 默认 1、`size` 默认 10，只允许 10/20/50/100/200），响应 `PagingResponseBody {items, page, size, total}`。
- 写接口带 `expectedVersion`，版本不符 409（公共 `CONCURRENT_REQUEST`）；资源不存在 404 `NOT_FOUND`；无权限 403 `OPERATIONS_PERMISSION_DENIED`；会话无效 401 `OPERATIONS_SESSION_INVALID`。
- 错误体为公共 RFC 9457 Problem（`type,title,detail,code,requestId,timestamp`，字段错误附 `errors[{field,message}]`）。
- 保存/试运行的校验失败：**422 `VALIDATION_ERROR`**，`errors[].field` 取值：`body`（message 以“第 N 行第 M 列：”开头）、`parameters[i]...`、`constraints.xxx`、`columns[i].xxx`、`dataSourceId`；执行/试运行的参数错误 field 形如 `params.conditions[2].minAmount`（A 卡裁决 2）。
- 专有错误码（`DataQueryErrorCode`）：`DATA_QUERY_TIMEOUT`（400，提示缩小范围）、`DATA_QUERY_RESULT_TOO_LARGE`（400）、`DATA_SOURCE_UNAVAILABLE`（503）。其余业务拒绝为 400 `BUSINESS_CHECK_FAILED` + 中文 detail，参数格式为 400 `INVALID_REQUEST`。
- 写接口成功响应 `ConfigurationMutationResponseBody {objectType, objectId, objectCode, status, version}`（已有类型复用；`objectType` 为 `DATA_SOURCE` 或 `QUERY_TEMPLATE`）。

## 接口表

| # | Method + Path | 权限 | Request | Response |
|---|---|---|---|---|
| 1 | GET `/data-query-admin/v1/data-sources?status&engineType&keyword&page&size` | DATA_QUERY_READ | Query：status(INACTIVE/ACTIVE)、engineType(MYSQL/DORIS)、keyword(≤64) | `PagingResponseBody<DataSourceResponseBody>`，按 update_time 倒序，不含 DELETED |
| 2 | GET `/data-query-admin/v1/data-sources/{dataSourceId}` | DATA_QUERY_READ | — | `DataSourceResponseBody`（DELETED 也可读） |
| 3 | POST `/data-query-admin/v1/data-sources` | DATA_SOURCE_MANAGE | `CreateDataSourceRequestBody` | `ConfigurationMutationResponseBody`（status=INACTIVE, version=1） |
| 4 | PUT `/data-query-admin/v1/data-sources/{dataSourceId}` | DATA_SOURCE_MANAGE | `UpdateDataSourceRequestBody`（password 空=保留） | `ConfigurationMutationResponseBody`；ACTIVE 时先连接检查，不通过 400 |
| 5 | POST `/data-query-admin/v1/data-source-connection-tests` | DATA_SOURCE_MANAGE | `TestDataSourceConnectionRequestBody` | `DataSourceConnectionTestResponseBody`（失败也 200） |
| 6 | POST `/data-query-admin/v1/data-sources/{dataSourceId}/status` | DATA_SOURCE_MANAGE | `ChangeDataSourceStatusRequestBody` | `ConfigurationMutationResponseBody`；启用检查不通过 400 |
| 7 | DELETE `/data-query-admin/v1/data-sources/{dataSourceId}?expectedVersion=` | DATA_SOURCE_MANAGE | Query `expectedVersion` | `ConfigurationMutationResponseBody`（status=DELETED）；须 INACTIVE 且无 ACTIVE 模板引用 |
| 8 | GET `/data-query-admin/v1/templates?dataSourceId&status&keyword&page&size` | DATA_QUERY_READ | Query：dataSourceId、status(ACTIVE/INACTIVE)、keyword(≤64) | `PagingResponseBody<QueryTemplateSummaryResponseBody>` |
| 9 | GET `/data-query-admin/v1/templates/{templateId}` | DATA_QUERY_READ | — | `QueryTemplateDetailResponseBody`（含 `currentVersion`） |
| 10 | POST `/data-query-admin/v1/templates` | DATA_QUERY_TEMPLATE_MANAGE | `CreateQueryTemplateRequestBody` | `SaveQueryTemplateResponseBody`（version=1, currentVersionNo=1, status=ACTIVE, warnings） |
| 11 | PUT `/data-query-admin/v1/templates/{templateId}` | DATA_QUERY_TEMPLATE_MANAGE | `UpdateQueryTemplateRequestBody` | `SaveQueryTemplateResponseBody` |
| 12 | POST `/data-query-admin/v1/templates/{templateId}/status` | DATA_QUERY_TEMPLATE_MANAGE | `ChangeQueryTemplateStatusRequestBody` | `ConfigurationMutationResponseBody` |
| 13 | GET `/data-query-admin/v1/templates/{templateId}/versions?page&size` | DATA_QUERY_READ | — | `PagingResponseBody<QueryTemplateVersionSummaryResponseBody>`，版本号倒序 |
| 14 | GET `/data-query-admin/v1/templates/{templateId}/versions/{versionNo}` | DATA_QUERY_READ | — | `QueryTemplateVersionResponseBody` |
| 15 | POST `/data-query-admin/v1/templates/{templateId}/restoration` | DATA_QUERY_TEMPLATE_MANAGE | `RestoreQueryTemplateRequestBody` | `SaveQueryTemplateResponseBody` |
| 16 | POST `/data-query-admin/v1/template-validations` | DATA_QUERY_TEMPLATE_MANAGE | `ValidateQueryTemplateRequestBody` | `QueryTemplateValidationResponseBody`（有错也 200） |
| 17 | POST `/data-query-admin/v1/template-trials` | DATA_QUERY_TEMPLATE_MANAGE | `TrialQueryTemplateRequestBody` | `TrialQueryTemplateResponseBody` |
| 18 | POST `/data-query/v1/templates/{templateCode}/executions` | DATA_QUERY_EXECUTE + 调用方白名单 | `ExecuteDataQueryRequestBody` | `DataQueryResultResponseBody` |

Gateway Route（注册 DML 用）：`/api/operations` + 上表路径，各绑定表中一项权限。DELETE 带 Query `expectedVersion`；列表 Query 白名单：data-sources `status, engineType, keyword, page, size`；templates `dataSourceId, status, keyword, page, size`；versions `page, size`。

> 2026-10-22 主会话裁决：硬上限 `max-timeout` 改为 8 秒、`max-body-length` 改为 48 KB（正文 `@Size(max = 49152)`）。

## 字段

### 数据源

`CreateDataSourceRequestBody`

| 字段 | 类型 | 约束 |
|---|---|---|
| dataSourceCode | String | 必填，`[A-Z][A-Z0-9_]{1,63}`，全局唯一（含已删除），不可修改 |
| dataSourceName | String | 必填，≤128 |
| engineType | String | 必填，`MYSQL`/`DORIS` |
| host | String | 必填，≤255，`[A-Za-z0-9._:-]+`（不接受 JDBC URL） |
| port | Integer | 必填，1..65535 |
| databaseName | String? | 可选，`[A-Za-z0-9_$]{1,128}`；为空=整个实例（模板须写 `库.表`） |
| username | String | 必填，≤128 |
| password | String | 必填，≤256；只存 Vault Token，永不回显 |
| connectionParams | Map<String,String>? | 只允许 `sslMode, connectTimeout, socketTimeout, connectionTimeZone, characterEncoding`，值 1..64 |
| maxPoolSize | Integer | 必填，1..`data-query.limits.max-pool-size`(10) |
| description | String? | ≤512 |

`UpdateDataSourceRequestBody`：同上去掉 `dataSourceCode`；`password` 可选（空=保留）；加 `expectedVersion`(Integer, 必填, ≥1)。

`TestDataSourceConnectionRequestBody`：`dataSourceId?`、`engineType`、`host`、`port`、`databaseName?`、`username`、`password?`（无 dataSourceId 时必填）、`connectionParams?`。

`ChangeDataSourceStatusRequestBody`：`status`(`ACTIVE`/`INACTIVE`)、`expectedVersion`。

`DataSourceResponseBody`：`dataSourceId, dataSourceCode, dataSourceName, engineType, host, port(int), databaseName?, username, connectionParams(Map，空为 {}), maxPoolSize(int), status(INACTIVE/ACTIVE/DELETED), lastCheckResult?(PASSED/FAILED), lastCheckMessage?, lastCheckTime?, description?, templateCount(long), activeTemplateCount(long), version(int), createdBy, updatedBy, createTime, updateTime`。

`DataSourceConnectionTestResponseBody`：`result`(PASSED/FAILED)、`connected`(bool)、`privilegesPassed`(bool)、`grants`(List<String>，SHOW GRANTS 原文)、`violations`(List<String>，中文原因)、`checkedAt`。

### 模板

`QueryTemplateConstraints`（constraints_json）：`maxRows`(Integer 必填 1..10000)、`timeoutSeconds`(Integer 必填 1..8，`max-timeout` 默认 8 秒，低于 BFF 9 秒 / Gateway 10 秒)、`pagingMode`(NONE/OFFSET/CURSOR 必填)、`maxPageSize`(Integer，OFFSET/CURSOR 必填，≤maxRows)、`countAllowed`(bool，只对 OFFSET)、`cursorColumns`(List<String>，CURSOR 必填 1..8 个)。
> 与 technique.md 4.4 的差异：超时字段名为 `timeoutSeconds`（整数秒），代替未定义单位的 `timeout`。

`QueryTemplateColumn`（columns_json 一项）：`name`(必填)、`label?`、`mask?`(NONE/HIDE/FULL/KEEP_EDGES，缺省 NONE)、`keepStart?`、`keepEnd?`(0..64)。

`parameters`：`JsonNode` 数组，结构同 technique.md 3.2（`name,type,elementType,required,default,maxLength,min,max,allowedValues,maxSize,choices,fields`），缺省 `[]`。

`CreateQueryTemplateRequestBody`：`templateCode`(`[A-Z][A-Z0-9_]{1,63}`)、`templateName`(≤128)、`description?`(≤512)、`dataSourceId`、`body`(必填，≤48 KB UTF-8；`max-body-length` 默认 48 KB，Gateway Body 上限 65536 字节)、`parameters?`、`columns?`(≤200)、`constraints`、`changeNote?`(≤512)。
`UpdateQueryTemplateRequestBody`：同上去掉 `templateCode`，加 `expectedVersion`。
`ValidateQueryTemplateRequestBody`：`dataSourceId, body, parameters?, columns?, constraints`。
`TrialQueryTemplateRequestBody`：Validate 的字段 + 执行字段（见下）。
`ChangeQueryTemplateStatusRequestBody`：`status`(ACTIVE/INACTIVE)、`expectedVersion`。
`RestoreQueryTemplateRequestBody`：`versionNo`、`expectedVersion`、`changeNote?`。

`QueryTemplateSummaryResponseBody`：`templateId, templateCode, templateName, description?, dataSourceId, dataSourceCode, dataSourceName, status, currentVersionNo, pagingMode, parameters[{name,type,elementType?,required}], version, updatedBy, updateTime`。
`QueryTemplateDetailResponseBody`：`templateId, templateCode, templateName, description?, status, currentVersionNo, version, createdBy, updatedBy, createTime, updateTime, currentVersion(QueryTemplateVersionResponseBody)`。
`QueryTemplateVersionResponseBody`：`templateId, versionNo, dataSourceId, dataSourceCode, dataSourceName, dataSourceStatus, body, parameters(JsonNode), columns[], constraints, restoredFromVersionNo?, changeNote?, createdBy, createTime`。
`QueryTemplateVersionSummaryResponseBody`：`versionNo, dataSourceId, dataSourceCode, restoredFromVersionNo?, changeNote?, createdBy, createTime`。
`SaveQueryTemplateResponseBody`：`templateId, templateCode, status, version, currentVersionNo, warnings[QueryTemplateProblem]`。
`QueryTemplateProblem`：`level`(ERROR/WARNING)、`line?`、`column?`、`field?`、`message`。
`QueryTemplateValidationResponseBody`：`valid`(bool)、`problems[]`。

### 执行与试运行

请求执行字段（`ExecuteDataQueryRequestBody`，试运行同名字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| params | JsonNode(Object)? | 参数名→值；未声明、`__` 开头拒绝 |
| page | Integer? | 只用于 OFFSET，缺省 1；偏移 ≤ `max-offset`(100000) |
| size | Integer? | OFFSET/CURSOR，1..maxPageSize，缺省 maxPageSize |
| withTotal | boolean | 只有 OFFSET 且 countAllowed 时可为 true，否则 400 |
| cursor | String? | 只用于 CURSOR，上一页 `nextCursor` 原样带回；模板版本变化时 400 |

`DataQueryResultResponseBody`：`versionNo`(int，试运行为 0)、`columns[{name,label}]`（已去 HIDE 列）、`rows`(List<List<Object>>，与 columns 对齐；整数/DECIMAL/浮点/日期时间/其他为字符串，布尔为 bool，NULL 为 null，二进制为 `"[binary]"`)、`hasMore`、`total?`(Long)、`nextCursor?`、`elapsedMillis`。
`TrialQueryTemplateResponseBody`：上述字段 + `sql`（带 `?`，已追加分页）、`bindings`(List<String>)、`countSql?`。

## 给 BFF / Web 的提示

- BFF 同路径转发（`/api/operations/...` → Platform 上述路径），执行接口须补 `X-Caller-Service: operations-console-bff`；管理与执行均原样转发 Gateway 身份 Header。
- 密码字段只出现在 3、4、5 的请求中；BFF 不记录 Body（Platform 侧已把 3、4、5、17、18 路径加入 metadata-only 日志）。
