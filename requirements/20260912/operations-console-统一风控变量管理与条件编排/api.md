# API 合同

阶段 3 评审稿，未实现。业务与兼容依据 [design.md](./design.md)。**新增 9 个业务操作，Risk、BFF 各 9 个端点；既有 9 个接口改合同，另 3 个门禁端点复核。** 不把每层转发重复算成新增业务功能。

## 1. 通用约定与授权

下表路径是 Risk 私有 HTTP 与 BFF 业务路径。浏览器路径统一加 `/api/operations`，由 Platform 数据库 Route 转发给既有 Operations BFF Target。Risk API 使用 `@HttpExchange`，新类型命名如 RiskVariableQueryApi、RiskVariableManagementApi、VariableOperand、RiskVariableService/Impl，不带 V1/V2 后缀。

沿用可信 Actor/Session、BFF 每次先调用 PLT-I02、Risk Owner 授权验证和 RFC 9457 错误。GET/DELETE 禁止 Body；GET 不写业务记录。读权限复用 RISK_DECISION_CONFIG_READ；新增 RISK_VARIABLE_MANAGE（新增、编辑、启停、成员）和 RISK_VARIABLE_DELETE。节点输出、系统定义和动作权限检查不能仅靠隐藏按钮。

- BIGINT ID、CAS 和 DURATION 均为规范十进制字符串，范围不超过有符号 BIGINT；编号/CAS >0。页码/页大小/序号/合同版本使用受限 JSON Integer。
- NUMBER 是无指数十进制字符串，最多 16 位整数、8 位小数，存为 DECIMAL(24,8)，规范输出固定 8 位小数；集合 value_key 使用去尾零规范数值，负零规范为零。不静默四舍五入。
- DATETIME 为 `YYYY-MM-DDTHH:mm:ss.ffffffZ`，UTC；BOOLEAN 为 JSON true/false。STRING 1–512 Unicode 字符，空字符串和 null 常量不接受；禁止 NUL/控制字符。字符串原值大小写和前导零不自动改变。
- 编码 `[A-Z][A-Z0-9_]{0,63}`；来源字段 `[a-z][a-z0-9_]{0,63}`；名称去首尾空白后 1–255，说明 null 或 1–500。显示名称、备注禁止敏感原值。
- 通用 formatCode：TEXT（安全字符串）；TOKEN（`[A-Z][A-Z0-9_]{0,63}`）；UPPER_ALPHA_2 / UPPER_ALPHA_3；DIGITS_4（保留前导零）；BCP47（沿用原语言标签解析与规范化能力）；POSITIVE_ID（正 BIGINT 十进制字符串）；非 STRING 为 NONE。具体字段关联来自库，不写字段名 switch。
- 查询输入错误、未知字段/枚举/重复 query、额外 JSON 键、required 键缺失及错误 null 均拒绝。URL code 不接受别名或不规范大小写。
- 成功响应直接返回下面的对象，分页沿用现有 PagingResponseBody 命名（items/page/size/total）。服务端不返回 Source Handler 指纹、输入实际值或可执行脚本。
- 普通变量维护不使用 Idempotency-Key，不创建回执表；唯一 code + CAS 处理重复/并发。网络响应未知时刷新确认，不能自动以最新 CAS 重发用户更新。规则/流程仍保持原幂等合同。

## 2. 新增接口

统一前缀 `/decision-variables/v1`。

| 编号 | Method / Path | Query / Body | 成功 | 权限 |
|---|---|---|---|---|
| N01 | GET /authoring-context | 可选 pointCode，缺省返回已支持点及来源合同 | 200 VariableManagementContext | READ |
| N02 | GET /variables | keyword/sourceType/valueType/status/pointCode/usage/nodeType/codes/page/size | 200 变量分页 | READ |
| N03 | GET /variables/{variableCode} | 无 | 200 VariableDetail | READ |
| N04 | POST /variables | VariableCreate，≤16 KiB | 201 VariableDetail | MANAGE |
| N05 | PUT /variables/{variableCode} | VariableUpdate，≤16 KiB | 200 VariableDetail | MANAGE |
| N06 | DELETE /variables/{variableCode} | 必填 expectedVersion | 200 {variableCode,status:"DELETED",version} | DELETE |
| N07 | GET /variables/{variableCode}/members | keyword/page/size/expectedVersion | 200 MemberPage | READ |
| N08 | PUT /variables/{variableCode}/members | {expectedVersion,members:[{value,displayName}]}，≤64 KiB | 200 {variableCode,version,memberCount,updateTime} | MANAGE |
| N09 | GET /variables/{variableCode}/references | scope/cursor/size | 200 ReferencePage | READ |

### N01 管理能力

VariableManagementContext 返回：`supportedPoints[{pointCode,pointName}]`、`sources[{sourceType,createAllowed}]`、`valueTypes[]`、`formatProfiles[{formatCode,label,help}]`、`numericUnits[]`、`limits{memberCount:1000,memberBodyBytes:65536,literalSetCount:100}`、`contracts[]`。

每个合同：`{contractCode,contractVersion,pointCode,sourceType,nodeType,fields:[{fieldKey,valueType,required,dataClassification,allowedUsages,numericUnit,currencyFieldKey,semanticRole}]}`。不适用 nodeType/currencyFieldKey/semanticRole 为 null。节点合同 nodeType=RULE_SET，sourceType=NODE_OUTPUT；非节点 nodeType=null。semanticRole 仅实际合同批准的动作主体槽位等工程约束。此处是已接通输入/输出结构，不返回一份可脱离变量库直接保存的字段目录；成员标签/业务变量标签从库读。

### N02 查询行为与返回

- keyword：编码或名称包含，最长 128；sourceType/valueType/status 为准确枚举。缺省不含 DELETED；按 variableCode ASC 排序，page 默认 1，size 默认 20、1–100，page 上限 10000。
- 用于条件选项时必须传 pointCode + usage（RULE_CONDITION / FLOW_CONDITION / ACTION_PARAMETER），服务端绑定该点当前准确输入合同；只有 ACTIVE、用途兼容、范围覆盖的定义可选。NODE_OUTPUT 还要求 usage=FLOW_CONDITION、nodeType=RULE_SET。节点输出永不进入普通规则选项。
- codes：最多 100 个不同大写 code，用逗号连接；与 keyword/sourceType/valueType/status/usage/nodeType/分页互斥，pointCode 也不参与该详情批取模式。它返回包含停用/墓碑的指定定义及 `missingCodes`，供只读引用/草稿补齐；不能据此绕过保存作用域验证。
- 普通分页响应加 `valuesAsOf`（一致性查询时间），items 为 VariableSummary。批取响应 `{items,missingCodes,valuesAsOf}` 不返回误导的分页总数。
- VariableSummary：`{variableCode,variableName,sourceType,valueType,pointCode,usages[],systemManaged,status,version,numericUnit,currencyCode,currencyVariableCode,formatCode,allowedValuesVariableCode,currentValue,memberCount,previewMembers,updateTime}`。currentValue 仅 CONSTANT 有值，其他 null；memberCount 仅 COLLECTION 为整数，其他 null；previewMembers 最多 3 项 `{value,displayName}`，其他空数组。不返回字段/节点实际数据。
- 无引用统计 N+1 请求：列表不带当前/历史引用总数，点击“使用关系”查询 N09。

### N03/N04/N05 定义结构

VariableDetail = VariableSummary + `{description,sourceContractCode,sourceContractVersion,sourceField,createBy,updateBy,createTime,valuesAsOf,allowedValues}`。allowedValues 对引用的选项集合返回其 code/name/version/memberCount 与最多 100 项预览；更多通过 N07 分页，不把全集塞进每个下拉响应。N03 对 DELETED 返回墓碑元数据，不作为可选变量；未知返回 404。

VariableCreate 的**全部键**（除说明可 null，其他不适用键也显式 null）如下：

| 字段 | 规则 |
|---|---|
| variableCode / variableName / description | 稳定编码、展示名称与说明 |
| sourceType | 仅 INPUT / CONSTANT / COLLECTION；NODE_OUTPUT 只能工程登记 |
| valueType | 五种标量类型之一；集合是成员类型 |
| pointCode | INPUT 必填；CONSTANT/COLLECTION 可 null 表示全已支持点 |
| sourceContractCode / sourceContractVersion / sourceField | INPUT 必填并匹配 N01 的准确合同；其他 null |
| usages | 去重数组，至少一项；三种用途且不超过合同上限 |
| formatCode | 与值类型相容的通用格式 |
| numericUnit / currencyCode / currencyVariableCode | NUMBER 单位必填；MONEY 两币种来源二选一；其他键显式 null |
| allowedValuesVariableCode | 标量可引用同类型/覆盖范围的集合；COLLECTION 必须 null |
| currentValue | CONSTANT 唯一类型值且非空；其他 null |
| status | ACTIVE / DISABLED；不能直接创建 DELETED |

N04 不接受 variableId、systemManaged、version、操作者或时间；这些由服务端生成。集合创建空集，成员用 N08；新建页先显示“已创建空集合，可继续添加成员”，避免把第二步失败误报为未创建。

VariableUpdate **只接受** `{expectedVersion,variableName,description,currentValue,status}`。所有键必填，不适用 currentValue=null。不受理结构字段变更；系统管理定义只允许 name/description 改动，其 currentValue/status 必须与存储事实一致，系统集合成员由 N08 明确拒绝维护。操作人/UTC/version 更新由 Risk 决定。

### N07/N08 成员

N07 `keyword` 匹配规范字符串值或显示名称，最长 128；page 默认1、size 默认50，1–100。返回 `{variableCode,version,items:[{value,displayName,sortOrder}],page,size,total,valuesAsOf}`。第1页 expectedVersion 可省略；page>1 必填首次返回版本，否则400；版本变化409。非集合409，DELETED404。

N08 整体替换0–1000项；null成员、混合类型、重复规范值或任何格式错误使整批失败，不部分写入。成员顺序即 sortOrder=1..N；成员ID不是前端身份，无需回传。Web 粘贴按行解析、报告重复和错误位置，用户“去重并加入草稿”后提交唯一集合；API 仍独立校验，不能默默丢弃用户重复项。返回新的根 CAS 及成员数量，成员数据可 N07 回查。

### N09 引用、游标与完整性

scope 默认 ALL，可选 CURRENT / HISTORY。size 默认20、最大100。返回 `{items,nextCursor,hasMore,scannedRows}`，不提供假精确 total。每项 `{consumerType,rootId,versionId,versionNo,code,name,scope,location,variableCode}`；consumerType 为 RULE_SET / DECISION_FLOW / VARIABLE，VARIABLE 的 versionId/versionNo=null；location 为准确条件节点+角色/序号、动作参数、edgeKey+条件路径或变量依赖字段。根/版本的路由地址由 Web 构造，API 不返回任意 URL。

游标固定 `{phase,lastId,upperBounds,scope,variableCode}` 的 base64url JSON，≤2048字符；phase 顺序 OPERAND、OPERAND_CURRENCY、ACTION_PARAMETER、VARIABLE_DEPENDENCY、FLOW_EDGE，upperBounds 为首次查询各类最大行 ID，禁止负数/未知字段/变量或 scope 不匹配。服务端不信任游标里的权限，始终重做授权。每页最多检查1000候选行或返回size条，lastId记录实际扫描位置；某页可以 items=[] 且 hasMore=true，Web 自动继续或明确“继续查找”，不得误报无引用。流程树内部一个候选行至多返回一个版本/edge位置汇总（location含全部命中路径），避免半行游标漏报。

引用查询为基于初始上界的分段读取，不承诺跨请求事务快照；刷新从头获取新配置。CURRENT 按读取时根的当前版本判断；删除根的保留历史归 HISTORY。完整删除保护在 N06 事务内另做存在性复核，不依赖客户端翻页结果；查询损坏条件或超时报告错误，不当作0引用。

## 3. 原接口的准确改动

下表均保留原 Method/Path，不新增 v2 路径。

| 编号 | 原接口 | 改动 |
|---|---|---|
| M01 | GET /decision-rule-sets/v1/authoring-context/{pointCode} | 增加 operandFormat/variableQueryContext；fields 不再作为可保存来源；目标绑定元数据提供库中主体变量条件 |
| M02 | POST /decision-rule-sets/v1/rule-sets | 顶层 operandFormat=VARIABLE_REFERENCE；VARIABLE/LITERAL 双侧、金额 literal 元数据；AdditionalAction.subjectVariableCode；新内容格式 |
| M03 | GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId} | 保留按原格式还原，增加 operandFormat、currentVariables、valuesAsOf；新操作数字段 |
| M04 | GET /decision-flows/v1/authoring-context | 增加 conditionSchemaVersion=2、variableQueryContext；节点输出选项通过统一 N02 查询，删除 Web matched 补项 |
| M05 | POST /decision-flows/v1/flows | condition.schemaVersion=2 的统一操作数；准确规则版本与其动态变量资格静态检查 |
| M06 | POST /decision-flows/v1/flows/validate | 与 M05 同一 Owner 校验器，问题定位到 edge/operand；不预留版本、不执行求值 |
| M07 | GET /decision-flows/v1/flows/{flowId} | 当前图读取旧/新条件格式，增加 currentVariables/valuesAsOf |
| M08 | GET /decision-flows/v1/flows/{flowId}/versions/{flowVersionId} | 精确历史图原样读取，动态值按当前值投影 |
| M09 | GET /decision-flows/v1/rule-set-reference | 嵌套 RuleSetDetail 同 M03，仍验证真实来源引用后读取准确版本 |

M01/M04 保留 point/actions/limits 等原事实，旧 `fields` / `nodeOutputs` 返回库中系统 INPUT/NODE_OUTPUT 的有限兼容投影，仅供历史对照，不能绕过变量 code 保存；新 Web 不以这些投影构建可选变量。descriptorCode 为 `RISK_VARIABLE_AUTHORING`，不以整库内容生成 descriptor Hash。variableQueryContext 为 `{pointCode,inputContractCode,inputContractVersion,usage}`；调用方在 N02 添加 nodeType 过滤。Graph 的 schemaVersion 保持1；仅条件数据 schemaVersion 增加2。

M02 新字段 `operandFormat` 为新鲜请求必填。每条新的 AdditionalAction 继续原 targetBindingCode，另显式传 subjectVariableCode；Risk 依据目标能力批准的 semanticRole 验证这个 INPUT 变量，生成内部主体参数 sourceType=VARIABLE，参数记录新增 variable_code。旧 targetBindingCode 不改名，现有duration/tag业务参数仍是原批准的 literal/字典能力，不顺带扩动作执行。

三项既有门禁（合同路径/回执不变）：POST rule-sets/{id}/freeze、POST /decision-releases/v1/candidates、POST /decision-releases/v1/candidates/{releaseId}/validate。首次新格式请求在已有配置就绪检查中拒绝；原成功命令重放不查最新变量。完整路径/原错误结构沿用现有 API，不统计为3个新增接口。

### 统一操作数

```json
{"operandRole":"LEFT","valueSequence":1,"sourceType":"VARIABLE","variableCode":"INPUT_NET_AMOUNT","fieldCode":null,"valueType":"NUMBER","literalValue":null,"numericUnit":null,"currencyCode":null,"currencyVariableCode":null}
```

```json
{"operandRole":"RIGHT","valueSequence":1,"sourceType":"LITERAL","variableCode":null,"fieldCode":null,"valueType":"NUMBER","literalValue":"8000.00000000","numericUnit":"MONEY","currencyCode":null,"currencyVariableCode":"INPUT_CURRENCY"}
```

新格式所有列出的键均必填；VARIABLE 的 valueType 必须与库一致，变量本身的量纲从不可变定义取得，三个金额键为null；LITERAL 的 variableCode=null，非NUMBER三个金额键为null。fieldCode只为兼容旧 DTO 保留，新格式恒null。集合变量的 valueType 是成员类型，右侧 IN 校验通过目录 sourceType识别集合，不能相信前端自报形态。

规则仍为 ConditionNode.operands，GROUP operands=[]，PREDICATE 按矩阵校验角色与顺序。流程新 PREDICATE 为 `{kind:"PREDICATE",operator,operands:[]}`，Group 仍 `{kind:"GROUP",operator,children:[]}`。条件树外层 `{schemaVersion:2,root:...}`；禁止旧 source/values 与新 operands 混入同一个格式。完整样例及机器校验 Schema 见 [contracts](./contracts/condition.schema.json)。

### 当前值投影

M03/M07/M08/M09 `currentVariables` 为对本响应实际引用及直接依赖去重后的 VariableSummary[]，`valuesAsOf` 为查询时间。节点/输入变量只显示来源，无运行currentValue；引用常量显示currentValue；集合只显示数量/最多3项previewMembers，展开走N07。变量缺失/数据损坏不改历史内容：增加 `variableIssues[{variableCode,code,message}]` 标记，并让编辑保存按错误阻断；历史页面仍展示原编码/原literal。未来执行用值证据不在本期伪造。

<a id="errors"></a>

## 4. 错误与容量

RFC 9457 + 当前 Request ID、错误时间及 fieldErrors 合同。以下业务错误接入现有错误注册方式，不另造 Error Envelope；Flow 保存、字段校验与执行门禁继续复用原 v1 错误响应：

| HTTP | 错误标识 / 场景 | 处理 |
|---|---|---|
| 400 | INVALID_VARIABLE_DEFINITION / INVALID_OPERAND / LEGACY_AUTHORING_NOT_SUPPORTED | 定位字段/条件，保留草稿；旧客户端刷新 |
| 401 / 403 | 沿用认证/权限错误 / SYSTEM_VARIABLE_READ_ONLY | 不绕过 Owner 或改角色 |
| 404 | VARIABLE_NOT_FOUND | 显示原编码和缺失问题 |
| 409 | VARIABLE_CODE_EXISTS / VARIABLE_VERSION_CONFLICT | 展示现值/当前CAS，不自动覆盖 |
| 409 | VARIABLE_IN_USE / VARIABLE_DISABLED / VARIABLE_SCOPE_MISMATCH | 列出可定位引用或作用域问题 |
| 409 | VARIABLE_TYPE_MISMATCH / VARIABLE_UNIT_MISMATCH / VARIABLE_DOMAIN_MISMATCH | 定位左右侧/集合成员 |
| 400 | BUSINESS_CHECK_FAILED | 本期格式不能 Freeze/Candidate，沿用既有门禁错误 |
| 413 | INVALID_REQUEST | 请求超过 UTF-8 字节上限，沿用严格 Reader 错误 |
| 422 | VALIDATION_ERROR | Flow 保存的变量/条件资格问题、Bean Validation 字段/成员数校验失败，使用原 errors 字段定位 |
| 5xx | 依赖/锁不可用、持久化结构损坏、引用扫描超时 | 返回现有 Problem，不退回硬编码、默认值或0引用 |

变量根与规则保存的变量资格错误按上表返回相应 409；Flow 保存将同类问题装配为原有 422 VALIDATION_ERROR。查询响应超过批准容量返回 400 INVALID_REQUEST 并提示缩小分页或批量数量，禁止返回截断结果。

Flow /validate 沿用 HTTP200 `{valid:false,issues,truncated}` 表达业务静态校验失败；结构/权限/容量错误仍是相应HTTP错误。字段定位样例：`rules[0].conditionNodes[0].operands[1].variableCode`、`graph.edges[0].condition.root.operands[1].literalValue`、`members[3].value`。

原规则保存64KiB、规则数1–200、每规则条件节点≤2000；原流程保存64KiB、50节点/100连线、单条件8KiB/32节点/深度8维持。新成员接口请求64KiB，N02/N07响应分别≤512KiB，详情/引用组合≤1MiB；超出不得截断真实内容，返回容量错误并提示缩小请求分页。选项分页、成员预览和引用游标保证不用返回无界数组。Gateway登记对应Body与query，BFF/Risk独立实施同一上限。
