# 联合查询 · Platform HTTP 合同（paytech-platform-support-api）

Package `com.paytech.platform.support.api.dataquery`（request / response 子包）。ID 与键值一律字符串。
Code pattern = `[A-Z][A-Z0-9_]{1,63}`（与 templateCode 相同写法，@Pattern 整串匹配）。
Bean Validation 失败 → 422 VALIDATION_ERROR（入口 `@Valid`）。

## QueryKeyTypeManagementApi  `@HttpExchange("/data-query-admin/v1")`
Headers（全部方法）: `X-Gateway-Actor-Subject` actor, `X-Gateway-Session-Id` session。
权限：K1 DATA_QUERY_READ；K2–K5 DATA_QUERY_TEMPLATE_MANAGE。

| # | Method | Java | Body → Return |
|---|---|---|---|
| K1 | GET `/key-types?status=&keyword=&page=&size=` | `listQueryKeyTypes(actor, session, status, keyword, page, size)` | → `PagingResponseBody<QueryKeyTypeResponseBody>`；sortNo, keyTypeCode 升序；size ∈ 10/20/50/100/200 |
| K2 | POST `/key-types` | `createQueryKeyType(actor, session, body)` | `CreateQueryKeyTypeRequestBody` → `ConfigurationMutationResponseBody`（objectType=QUERY_KEY_TYPE） |
| K3 | PUT `/key-types/{keyTypeId}` | `updateQueryKeyType(actor, session, keyTypeId, body)` | `UpdateQueryKeyTypeRequestBody` → `ConfigurationMutationResponseBody` |
| K4 | POST `/key-types/{keyTypeId}/status` | `changeQueryKeyTypeStatus(actor, session, keyTypeId, body)` | `ChangeQueryKeyTypeStatusRequestBody` → `ConfigurationMutationResponseBody`；同状态不加版本 |
| K5 | PUT `/key-type-order` | `reorderQueryKeyTypes(actor, session, body)` | `ReorderQueryKeyTypesRequestBody` → `ReorderQueryKeyTypesResponseBody` |

错误：版本不符 409；不存在 404；编码重复 400；被 ACTIVE 模板当前版本引用时停用 / 改 valueType 400（列前 5 个模板编码）；valuePattern 不合法 422 field=valuePattern；K5 items 未恰好覆盖全部键类型或重复 400。

### CreateQueryKeyTypeRequestBody
- keyTypeCode String @NotBlank @Pattern(code)
- keyTypeName String @NotBlank ≤128
- groupName String @NotBlank ≤32
- valueType String @NotBlank `INTEGER|STRING`
- defaultRole String @NotBlank `LINK|DIMENSION`
- entryAllowed Boolean @NotNull
- valuePattern String ≤256 可空（服务端：Java 可编译 + 拒绝 `\1` `\k<` `(?=` `(?!` `(?<=` `(?<!` `(?i`… `*+` `(?>`）
- inputHint String ≤128 可空
- sortNo Integer @NotNull 0..9999
- description String ≤512 可空

### UpdateQueryKeyTypeRequestBody
同上去掉 keyTypeCode，加 expectedVersion Integer @NotNull @Positive。

### ChangeQueryKeyTypeStatusRequestBody
- status String @NotBlank `ACTIVE|INACTIVE`  ← 注意字段名是 `status`（与既有模板 / 数据源启停一致），不是技术方案写的 `targetStatus`
- expectedVersion Integer @NotNull @Positive

### ReorderQueryKeyTypesRequestBody
- items List<QueryKeyTypeOrderItem> @NotEmpty ≤500
  - QueryKeyTypeOrderItem: keyTypeId String @NotBlank `[1-9][0-9]{0,18}`；expectedVersion Integer @NotNull @Positive
- 服务端：一个事务内 sortNo = 序号(从 1) × 10，每项 version+1；锁 `lock:data-query:key-type-order`

### ReorderQueryKeyTypesResponseBody
- items List<QueryKeyTypeOrderResult{keyTypeId String, sortNo int, version int}>（按新顺序）

### QueryKeyTypeResponseBody
keyTypeId String, keyTypeCode, keyTypeName, groupName, valueType, defaultRole, entryAllowed boolean, valuePattern (nullable), inputHint (nullable), sortNo int, description (nullable), status, version int, referencedTemplateCount long（ACTIVE 模板当前版本 joint 中 subjectKeyType / keyParameter.keyType / columns[].keyType 任一引用计 1）, updatedBy String, updateTime String（UTC ISO-8601 微秒）。

## JointQueryApi  `@HttpExchange("/data-query/v1")`
Headers（全部方法）: `X-Caller-Service`（`DataQueryExecutionApi.CALLER_SERVICE_HEADER`）callerService, `X-Gateway-Actor-Subject` actor, `X-Gateway-Session-Id` session。
权限 DATA_QUERY_EXECUTE；调用方登记校验与 `DataQueryExecutionApi` 相同。

| # | Method | Java | Return |
|---|---|---|---|
| J1 | GET `/joint-query/settings` | `getJointQuerySettings(callerService, actor, session)` | `JointQuerySettingsResponseBody` |
| J2 | GET `/joint-query/graph?entryKeyType=` | `getJointQueryGraph(callerService, actor, session, entryKeyType)` | `JointQueryGraphResponseBody`；entryKeyType 缺失 / 不存在 / 停用 / 不可作入口 → **400 BUSINESS_CHECK_FAILED**（BizCheckFailException，消息“入口键类型不存在、未启用或不允许作为入口: X”；2026-10-29 调整，见文末） |
| J3 | POST `/joint-query/steps` | `executeJointQueryStep(callerService, actor, session, body)` | `JointQueryStepRequestBody` → `JointQueryStepResponseBody` |

### JointQuerySettingsResponseBody
- entryKeyTypes List<JointQueryEntryKeyType{keyTypeCode, keyTypeName, groupName, valueType, valuePattern?, inputHint?, sortNo int}>（sortNo, code 升序）
- groups List<JointQueryGroup{groupName, rank int}>（**只含泳道**：至少一个参与模板的 subjectKeyType 所在分组；rank = 组内 ACTIVE 键类型最小 sortNo，升序；只作维度的“归属维度”等不出现）
- limits JointQueryLimits{maxEntryValues, maxValuesPerKey, maxRowsPerTemplate, maxExecutionsPerStep, maxDepth, maxTemplateExecutions, maxTotalSeconds}（全部 int）

### JointQueryGraphResponseBody
- groups List<JointQueryGraphGroup{groupName, rank int, templates List<JointQueryGraphTemplate>}>（只含有可达模板的泳道，rank 口径同 J1）
  - JointQueryGraphTemplate{templateCode, templateName, dataSourceCode, subjectKeyType, triggerKeyType, minSteps int（入口直接触发 = 0）, linkKeyTypes List<String>}
- breaks List<JointQueryGraphBreak{keyType, reason}>
- excluded List<JointQueryExcludedTemplate{templateCode, reasonCode, reason}>（有 joint 但不参与；不限于可达范围）
- 空集合一律 []，不返回 null

### JointQueryStepRequestBody
- depth Integer @NotNull @Min(0)（上限 maxDepth-1 服务端校验，422）
- keys List<JointQueryKeyValues> @NotEmpty ≤20
  - JointQueryKeyValues{keyType String @NotBlank @Pattern(code), values List<@NotBlank ≤128 String> @NotEmpty ≤50}
  - 服务端：depth=0 → 恰 1 项、ACTIVE+entryAllowed、值 ≤ maxEntryValues(20)、按 valueType+valuePattern 校验，field `keys[0].values[3]`；depth>0 → 键类型不重复、ACTIVE、值 ≤ maxValuesPerKey，不做正则
  - 规范化：INTEGER 去前导零、0..9223372036854775807；STRING 原样；请求内去重
- templateCodes List<@NotBlank @Pattern(code) String> ≤20 可空（只执行这些；不参与 / 不存在的编码忽略）

### JointQueryStepResponseBody
- depth int
- nodes List<JointQueryNode>
- links List<JointQueryLink{nodeId, rowIndex int, column, keyType, value}>
- discoveredKeys List<JointQueryKeyValues>（复用 request 包的类；每键类型 ≤ maxValuesPerKey）
- truncations List<JointQueryTruncation{rule, keyType?, dropped int}>
  - rule（服务端）: MAX_VALUES_PER_KEY（keyType 有值，dropped=丢弃值数）/ STEP_EXECUTION_LIMIT / STEP_BUDGET（keyType=null，dropped=跳过的执行数）
  - 页面另产生: MAX_DEPTH、MAX_TEMPLATE_EXECUTIONS、MAX_TOTAL_SECONDS、USER_STOPPED
- elapsedMillis long

### JointQueryNode
nodeId String（形如 `depth:templateCode:triggerKeyType`；标量键参数逐值执行时服务端追加区分后缀；页面不解析）, templateCode, templateName, versionNo int, dataSourceCode, subjectKeyType, groupName, laneRank int, subjectSortNo int, triggerKeyType, keyParameter（参数名）, keyParameterList boolean（true → 加载更多时 params[keyParameter] 传数组，false → 传单值）, triggerValues List<String>, status `SUCCEEDED|FAILED|SKIPPED`, errorCode?（FAILED: data-query 既有码或 INTERNAL_ERROR；SKIPPED: STEP_BUDGET_EXHAUSTED / STEP_EXECUTION_LIMIT）, errorMessage?（安全中文）, pagingMode `NONE|OFFSET|CURSOR`, truncated boolean, nextCursor String?（只有 CURSOR 且还有更多行时有值；经单模板执行接口原样带回 cursor）, elapsedMillis long（SKIPPED=0）, warnings List<String>（如 `LINK_COLUMN_MISSING:order_id`）, columns List<JointQueryNodeColumn{name, label, keyType?, keyRole?}>, rows List<List<Object>>（同单模板执行；FAILED/SKIPPED 时 columns/rows 为 []）。

## 模板接口增量（兼容）
- `CreateQueryTemplateRequestBody` / `UpdateQueryTemplateRequestBody`：新增 `joint` QueryTemplateJointConfig @Valid 可空（放在 changeNote 前）。null = 不参与。旧客户端不传会清空 → BFF 严格模型必须同步透传。
- `QueryTemplateVersionResponseBody`：新增 `joint` QueryTemplateJointConfig?、`jointQuery` QueryTemplateJointParticipation?（joint=null 时也为 null）。
- `QueryTemplateDetailResponseBody`：顶层同样新增 `joint`、`jointQuery`，值与 `currentVersion.joint / jointQuery` 相同。
- `ValidateQueryTemplateRequestBody`：同样新增 `joint` QueryTemplateJointConfig @Valid 可空；校验问题 field 形如 `joint.columns[2].keyRole`，参与情况以 WARNING 提示。
- `SaveQueryTemplateResponseBody.warnings`：结构不变，服务端新增参与情况条目（INFO / WARNING）。

### QueryTemplateJointConfig（request 包）
- subjectKeyType String @NotBlank @Pattern(code)
- keyParameter QueryTemplateJointKeyParameter @NotNull @Valid
  - name String @NotBlank ≤128（须为已声明参数；参数类型匹配规则见技术方案 8.4）
  - keyType String @NotBlank @Pattern(code)
- columns List<QueryTemplateJointColumn> ≤200 可空
  - name String @NotBlank ≤128；keyType String @NotBlank @Pattern(code)；keyRole String @NotBlank `LINK|DIMENSION`
- 业务校验错误 field 形如 `joint.subjectKeyType`、`joint.keyParameter.name`、`joint.columns[1].keyType`

### QueryTemplateJointParticipation（response 包）
- participating boolean
- reasonCode String?（KEY_PARAMETER_NOT_REQUIRED / OTHER_REQUIRED_PARAMETER / TEMPLATE_INACTIVE / DATA_SOURCE_INACTIVE；参与时 null）
- reason String（中文；参与时如“参与联合查询，触发键 ORDER_ID”）
- 历史版本按该版本内容 + 模板 / 数据源当前状态推算

## 未改
`TrialQueryTemplateRequestBody`、`DataQueryErrorCode`（节点 errorCode / truncation rule 仅为字符串，未新增枚举）。

## 已确认决定（team-lead 2026-10-29）
K4 字段保持 `status`；调用方登记与无权限行为与 `DataQueryExecutionApi` 完全一致；其余取舍按首版报告接受；JointQueryNode 新增 keyParameterList、nextCursor；校验接口新增 joint。

## 2026-10-29 调整（team-lead，来自 BFF 核对）
- J2 GET `/joint-query/graph`：entryKeyType 缺失、不存在、非 ACTIVE 或不允许作入口返回 **400 BUSINESS_CHECK_FAILED**（不是 422）。
  原因：Foundation HTTP 客户端只为 @RequestBody 方法映射 422 字段错误，GET 的 422 到 BFF 会变 502。J3（POST）仍返回 422 字段错误。
- J1 / J2 `groups` 只含泳道（有参与模板以组内键类型为行主体的分组），rank 仍为组内 ACTIVE 键类型最小 sortNo。
- 注册块 144 路由编码：operations-query-key-type-list / -create / -update / -status / -order（K1–K5），
  operations-joint-query-settings / -graph / -step（J1–J3）。
- `JointQueryNode.truncated` = 结果 hasMore（NONE 分页也多取 1 行判断，rowCap 计入上限）；SKIPPED 另有 `EXECUTOR_BUSY`（共享执行器队列满）。
