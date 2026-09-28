# 决策流接口与业务合同

状态：已确认的工程合同；本文件不声明接口已上线，研发及验证证据见 [开发交付报告](./development-checks.md)。范围见 [requirement.md](./requirement.md)，数据与并发见 [data.md](./data.md)。

> **2026-09-21 管理接口幂等修订（用户确认）：** 保存、删除按[现行 Gateway 幂等职责](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)迁移，Risk 私网 API 和 BFF 不再要求、校验或转发 `Idempotency-Key`，也不提供本地响应回放。下文 N03/N08 的服务本地 Key、同键回放及幂等冲突描述保留为原合同记录，由本修订取代；Method/Path、Body/响应字段及权限保持。绕过 Gateway 的重复创建返回编码冲突，旧编辑基准返回版本冲突，再次删除返回 `RISK_FLOW_DELETED`。Gateway 是否已实现或启用请求重放、实际源码验证和部署状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#risk-flow-management-refactor-20260921)，本修订不承诺已上线。

已确认修订：[精简修订第 5 节](./simplification.md#5-接口与消费方) 拥有规则响应中旧 pointVersionId 的可空性及新规则不进入旧 Candidate 的边界，并取代下文对应旧依赖描述；受影响研发证据见 [修订开发检查](./simplification-development-checks.md)。所有路径仍沿用原 v1。

10 个新增业务操作、4 个既有操作调整；每个均在 Risk Owner 与 Operations BFF 各有一处落点，按业务 Method + Path 只计算一次。所有路径沿用或默认 v1，新增 Java 类型名不带版本后缀。

## 1. 入口、身份与权限

下表路径为 Risk 与 BFF 的一致路径；浏览器在其前加 `/api/operations`。Gateway 按 Platform Snapshot 转发到既有 Operations BFF Target。BFF 的统一拦截器先调用 PLT-I02，失败时 Risk 零调用；纯转发不增加空 Processor。Risk 的存取、引用校验和事务归 Risk，BFF 不访问数据库。

读取复用 `RISK_DECISION_CONFIG_READ`；新增 `RISK_DECISION_FLOW_SAVE`（新建、编辑、校验）与 `RISK_DECISION_FLOW_DELETE`。正常页面菜单需 READ，写按钮还需相应写能力；写权限不自动扩张到其他规则操作。来源为实际数据库注册，不新增 Gateway 业务白名单。

浏览器只提供 Authorization、必要 Content-Type 和写命令的 Idempotency-Key；可信 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`、请求 ID 等沿用既有入口传递，不允许浏览器伪造。Risk 请求 Body 不接收 actor/tenant/status/createdTime。所有接口在现有私网 HTTP 边界内使用。

| # | Method + Path | 请求 | 成功结果 / 权限 |
|---|---|---|---|
| N01 | GET /decision-flows/v1/authoring-context | pointCode 可省略；当前默认唯一支持的 CHECKOUT_PREPARE | 200 FlowAuthoringContextResponseBody；READ |
| N02 | GET /decision-flows/v1/flows | pointCode 必填；flowCode、keyword、page、size 可选 | 200 Paging&lt;FlowListItem&gt;；READ |
| N03 | POST /decision-flows/v1/flows | SaveFlowRequestBody；Idempotency-Key 必填 | 200 SaveFlowResponseBody；SAVE |
| N04 | POST /decision-flows/v1/flows/validate | 与 N03 相同的 Body；无 Idempotency-Key | 200 ValidateFlowResponseBody；SAVE |
| N05 | GET /decision-flows/v1/flows/{flowId} | 正 BIGINT ID | 200 FlowDetailResponseBody，当前版本；READ |
| N06 | GET /decision-flows/v1/flows/{flowId}/versions | maxVersionNo 必填；page、size 可选 | 200 Paging&lt;FlowHistoryItem&gt;；READ |
| N07 | GET /decision-flows/v1/flows/{flowId}/versions/{flowVersionId} | 两 ID 必须归属一致 | 200 FlowDetailResponseBody，准确版本；READ |
| N08 | DELETE /decision-flows/v1/flows/{flowId} | Query expectedVersion 必填；Idempotency-Key 必填；无 Body | 200 DeleteFlowResponseBody；DELETE |
| N09 | GET /decision-flows/v1/rule-set-reference | sourceType、sourceVersionId、nodeKey 均必填 | 200 RuleSetReferenceResponseBody；READ |
| N10 | GET /decision-flows/v1/legacy-point-versions/{pointVersionId} | 正 BIGINT ID | 200 LegacyPointResponseBody；READ |

路径中固定的 `validate` 不与 `{flowId}` 的 POST 路由竞争，因为没有 POST 单流详情。Query 未声明字段、重复单值、空值、错误类型均拒绝。Body 的未知属性及重复 JSON 属性拒绝，响应 Reader 同步跟进受控类型；Problem 的未知扩展成员按现行 Profile 忽略。

## 2. 公共标量、分页和大小

- BIGINT ID / CAS：正十进制字符串，1..9223372036854775807；不可用 JSON Number。业务 versionNo 为 1..2147483647 的整数，耗尽拒绝，不能回绕。
- 编码：`[A-Z][A-Z0-9_]{0,63}`，不自动 trim 或改大小写；首次创建后流程编码与决策点不可修改。
- 名称 1..100 Unicode 码点、首尾不能空白；changeReason 1..500；DENY reasonMessage 1..500。文本按 Risk 既有 NFC、安全控制字符规则处理，文案和原因允许换行。不得填入个人原值、卡数据或凭据。
- nodeKey / edgeKey：`[A-Za-z][A-Za-z0-9_-]{0,63}`，区分大小写；仅为流程图身份，不是数据库 ID。
- 时间：UTC RFC 3339 固定六位小数，例如 `2026-09-12T12:30:00.000000Z`。
- page 默认 1，范围 1..100000；size 默认 10，可选 10/20/50/100/200，与现有 Foundation 分页合同一致；复用现有 `PagingResponseBody` 的 `items/page/size/total` 形状；total 为非负 JSON 整数（Java long），Web 按既有安全整数上限校验。N06 固定 `versionNo <= maxVersionNo`，倒序读取；上界不能超过该根当前 versionNo。入口取 N05/N02 的当前版本号作为上界。
- N02 的 flowCode 精确匹配；keyword 只对当前版本 flowName 做包含匹配，1..100 码点；两者同时传表示 AND。转义 SQL LIKE 中的 `%/_/反斜线`，不把用户输入当通配语法。只返回 CONFIGURABLE 根，按 updateTime DESC、flowId DESC 排序；普通列表是当前快照，跨页更新可能变化。
- 保存和校验 Body 均最多 65,536 UTF-8 字节；Risk 对内部入口实施同样上限，Gateway Route 实施公网入口上限，BFF 不复制一个独立全局 Body Filter。最多 50 节点、100 连线；单条件树规范化后最多 8,192 UTF-8 字节、32 个树节点、深度 8。多个上限同时生效，不保证每个局部都达到上限仍能提交。
- 画布坐标为 -100000..100000 的整像素；Wire 使用有符号十进制字符串，拒绝 `-0`、指数和小数。Web 拖动结束时取整并立即回显，数据库存 INT。这样保留负坐标且不扩大 Foundation 只接受非负整数 JSON Number 的范围。

## 3. 保存结构

`SaveFlowRequestBody` 固定属性：

| 属性 | 类型 / 必填 | 语义 |
|---|---|---|
| pointCode | String，必填 | 本期 CHECKOUT_PREPARE；不得通过手写其他点绕过能力目录 |
| flowCode | String，必填 | 决策点内永久身份 |
| flowName | String，必填 | 本版名称快照 |
| baseFlowVersionId | String 或 null，键必填 | 新建为 null；编辑必须为当前准确版本 |
| expectedVersion | String 或 null，键必填 | 新建为 null；编辑必须为最近读取的根 CAS |
| changeReason | String，必填 | 本次保存说明 |
| graph | Graph，必填 | 受控流程结构与必要布局 |

新建两个 base 字段必须同时为 null；同编码根已存在（含已删除）返回冲突。编辑两个字段都非 null；只允许从当前版本继续保存，不允许历史分叉或覆盖历史。

`Graph` 固定属性：`schemaVersion:1`、`nodes:Node[]`、`edges:Edge[]`。这是流程数据格式标识，不是接口版本。节点和线数组的物理先后不决定执行顺序；保存后按 nodeKey、sourceNodeKey/priority/edgeKey 稳定排序返回。不会保存 React Flow 的 selection、measured、handles、viewport、zoom、dragging 等内部状态。

`Node` 所有键都必填：`nodeKey,type,name,position,ruleSetVersionId,decisionCode,reasonCode,reasonMessage`。position 只有字符串 x/y。

| type | ruleSetVersionId | decisionCode | reasonCode / reasonMessage |
|---|---|---|---|
| START | null | null | null / null |
| RULE_SET | 精确版本 ID | null | null / null |
| END + ALLOW/REVIEW | null | 显式 ALLOW 或 REVIEW | null / null |
| END + DENY | null | DENY | 原因字典 Code / 保存时内部文案，均必填 |

N04 接受未配置完整的合法 JSON（例如 END decisionCode=null），将其作为定位问题返回；N03 绝不保存该状态。N04 的结构/标量类型损坏仍返回请求错误，不能把任意 JSON 当合法草稿。

`Edge` 所有键都必填：`edgeKey,sourceNodeKey,targetNodeKey,type,priority,name,condition`。

| type | priority | condition |
|---|---:|---|
| UNCONDITIONAL | 0 | null；同源只能这一条 |
| CONDITIONAL | 1..N 连续且同源唯一 | Condition 必填 |
| ELSE | 0 | null；同源须至少一条条件线，且只存在一个 ELSE |

条件结构是受控 JSON Object：`{schemaVersion:1,root:Expression}`。Expression 只允许以下两种精确属性集合：

```text
GROUP = {kind:"GROUP", operator:"AND"|"OR"|"NOT", children: Expression[]}
```

```json
{"kind":"PREDICATE","source":{"type":"NODE_RESULT","fieldCode":"matched"},"operator":"EQ","values":[true]}
```

GROUP 支持 AND/OR（2..32 子项）或 NOT（恰好一个子项）；组顺序原样保留。PREDICATE 的 source 只有 type 和 fieldCode：CONTEXT 指本点可用于条件的字段；NODE_RESULT 只能是本出线来源规则集节点的 matched，不接收 nodeId/nodeKey 来选择其他来源。values 为字面量数组，不支持字段对字段、脚本或数据库查询。

CONTEXT 复用 Risk 当前输入合同的类型、字段规范化和比较约束：字符串按其域校验；NUMBER 仍用十进制字符串；EQ/NE 一值，GT/GTE/LT/LTE 仅数值型一值，BETWEEN 两值且下界不高于上界，IN/NOT_IN 1..100 值，IS_NULL/IS_NOT_NULL 零值。不接受 Literal null。NODE_RESULT.matched 为非空 BOOLEAN，仅 EQ/NE + 一个 Boolean 字面量；不提供字符串 "true" 或数值 0/1 的隐式转换。

可校验的完整示例见 [save-flow.example.json](./contracts/save-flow.example.json)；对应精确类型、key 集合及 null 矩阵见 [save-flow.schema.json](./contracts/save-flow.schema.json)。Schema 只验证结构，图拓扑和数据库资格仍由 Risk 验证。

## 4. 能力目录与所有响应

N01 `FlowAuthoringContextResponseBody`：

- `supportedPoints:[{pointCode,pointName}]`；本期仅 CHECKOUT_PREPARE，不硬编码在 Web 的下拉列表。
- `pointCode,inputContractCode,inputContractVersion,graphSchemaVersion`：当前 ORDER_PREPARATION / 4 / 1；服务端由实际目录得到并核对已支持描述符。
- `fields:[{fieldCode,label,valueType,required,operators,literalDomain}]`：只暴露当前 9 个可用于规则条件的字段；required=true，现有 IS_NULL 系列运算符保留，实际缺失不是 null。literalDomain 使用现有 COUNTRY、LOCALE、UPPER_CODE、MCC、CURRENCY、DECIMAL、ENTITY_SOURCE 展示/约束语义，不暴露运行数据。
- `nodeOutputs:[{nodeType:"RULE_SET",fieldCode:"matched",label:"是否命中规则",valueType:"BOOLEAN",required:true,operators:["EQ","NE"]}]`。
- `limits:{bodyBytes:65536,nodes:50,edges:100,conditionBytes:8192,conditionNodes:32,conditionDepth:8}`。

N02 `FlowListItem`：`flowId,pointCode,flowCode,flowName,currentFlowVersionId,currentVersionNo,version,ruleSetNodeCount,updatedBy,updateTime`。ID/CAS/actor 为字符串，两个 count 为整数；ruleSetNodeCount 按节点数统计，同一版本引用两次即 2 个节点。名称/保存人从当前配置版本头取，不加重复根字段。

N03 `SaveFlowResponseBody`：`flowId,flowVersionId,versionNo,version,savedBy,savedTime`。version 是该次保存后的 CAS；同键成功回放重建该次结果，不返回后来已更新的根 CAS。根只有保存/删除两个写动作，因此保存时 CAS 等于 versionNo；此不变量必须在测试与未来扩展中保持或显式替代。

N04 `ValidationResponseBody`：`valid:Boolean,issues:[{code,nodeKey,edgeKey,field,message}],truncated:Boolean`。无对应节点/线/Body 路径时相应键为 null；成功 issues=[]，truncated=false。最多 100 项，同一断线造成的级联不可达问题合并定位，独立不可达缺陷仍报告。按基础结构→节点配置→来源/目标→出线顺序→环路→可达/结束→引用资格的顺序稳定报告。code 是校验目录（MISSING_START、END_DECISION_REQUIRED、ELSE_REQUIRED、DUPLICATE_PRIORITY、CYCLE、UNREACHABLE_NODE、INVALID_CONDITION、REFERENCE_UNAVAILABLE、BASE_CONFLICT 等），field 使用 `graph.nodes[0].decisionCode` 一类外部路径。它是校验查询的正常结果，不是通用 Problem 扩展。校验不预留版本、不持有写锁；保存会再次核对。

N05/N07 `FlowDetailResponseBody`：`flowId,pointCode,flowCode,flowStatus,currentFlowVersionId,currentVersionNo,version,flowVersionId,versionNo,flowName,inputContractCode,inputContractVersion,createdBy,createTime,changeReason,graph,ruleSetReferences`。graph 是保存内容；根状态/CAS/当前指针是读取时事实，两类信息不能混用。N05 删除根为 404；N07 在已授权历史上下文内允许读取 DELETED 根，返回 flowStatus 并强制页面只读。

`ruleSetReferences:[{nodeKey,ruleSetId,ruleSetCode,ruleSetName,ruleSetVersionId,versionNo,ruleCount,ruleSetStatus,versionStatus}]`。名称与规则数来自准确不可变规则版本，后两个状态是当前引用资格，不重写 graph。只返回规则总数，不新增动作模式或分类统计字段；具体动作通过原规则详情读取，不把损坏旧记录解释为合法的空动作。旧规则集详情的全部规则不嵌入每次图查询，点击查看通过 N09。

N06 `FlowHistoryItem`：`flowVersionId,versionNo,flowName,createdBy,createTime,changeReason`；沿用公共分页结构。允许保留的 DELETED 根历史查询，页面无恢复、另存或编辑按钮。

N08 `DeleteFlowResponseBody`：`flowId,status:"DELETED",version,deletedBy,deletedTime`。同键回放原删除结果；不同键再次删除返回 FLOW_DELETED，不生成第二份删除事实。此接口没有单版本删除。

N09 `RuleSetReferenceResponseBody`：`sourceType,sourceVersionId,nodeKey,ruleSetStatus,ruleSet`。sourceType 只有 FLOW_VERSION / LEGACY_POINT。FLOW_VERSION 的 sourceVersionId 为真实流程版本，nodeKey 必须是其已保存的 RULE_SET 节点；LEGACY_POINT 为真实 Point Version，nodeKey 为 N10 返回的 `binding_<bindingId>`。Risk 先解析已持久化来源，再读取绑定的准确规则集版本；不能由请求附带一个任意 ruleSetVersionId。`ruleSet` 复用现有 RuleSetDetailResponseBody 的精确字段和历史构建规则，允许真实引用的根已删除；不放开原规则集详情的删除门禁。Web 一律使用只读规则工作区。

N10 `LegacyPointResponseBody`：`sourceType:"LEGACY_POINT",pointVersionId,pointCode,pointName,versionNo,inputContractCode,inputContractVersion,defaultDecision,evaluationStrategy,failureStrategy,nodes,edges,ruleSetReferences`。该结构是有来源的只读投影，不使用新 Graph 保存合同，不伪造 flowId/flowCode/保存人或流程历史版本。按原 sequenceNo 串接绑定，最后连到“原默认决定”节点；默认 DENY 缺少原因时显示原数据未提供原因，不伪造字典。保留旧点语义及 Hash。新入口放在既有规则集发布引用页的 Point Version 项“查看编排”；不向新流程列表回填旧对象。原绑定重复序号、缺记录等损坏必须报错，不能补一个 ALLOW。只读投影最多 200 个绑定、响应最多 2 MiB，独立于新建图 50 节点上限；超限明确失败，不截断图或改写源数据。

## 5. 原接口调整与复用

| # | 既有 Method + Path | 本期修改 |
|---|---|---|
| M01 | POST /decision-rule-sets/v1/rule-sets | CONFIGURATION 规则的命中后动作可选；action=null 且 additionalActions=[] 表示未配置动作；原 LEGACY 缺失附加动作仍要求终态。保存/聚合重建/Web 构建一致调整。 |
| M02 | GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId} | 准确回显未配置动作的规则；严格 Reader 接受上述组合，不增字段、不把缺失/损坏动作解释为合法。 |
| M03 | POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze | 执行就绪门禁显式拒绝含未配置动作规则的配置，原附加动作限制保持；不修改 Candidate 单终态闭包。 |
| M04 | GET /decision-dictionaries/v1/rejection-reasons/{reasonCode}/references | 原 scope/page/size 不变，加入流程结束节点来源。 |

M04 保留旧分页及所有旧规则引用字段，增加 `sourceType`、`flowId`、`flowCode`、`flowName`、`flowVersionId`、`flowVersionNo`、`nodeKey`、`nodeName`、`flowStatus`。RULE_SET 来源时新流程坐标为 null，旧值保持；FLOW_END 来源时规则集专属坐标/规则字段为 null，流程坐标必填；共享 pointCode、referenceKind=REJECTION_REASON、isCurrentConfiguration 保留，activeEnvironments=[]，因为本期没有流运行引用。旧 versionNo 在 FLOW_END 为 null，flowVersionNo 是明确流程版本，不借用规则集版本字段。actionSequenceNo/targetBindingCode 在原因引用中继续为 null。

scope 默认 ALL：ALL 返回所有来源；CURRENT 为未删除根当前版本；HISTORY 为未删除根非当前版本；DELETED 为已删除根的全部版本。四个值及原含义保持。实现必须使用一个 UNION ALL 结果集统一 count/排序/分页，按来源版本 ID DESC、sourceType、规则/节点行 ID ASC 稳定排序，不能两种来源各分页再合并。同版本同原因多个节点分别成行。标签引用 API 及其原 DTO 不变，新增原因专用 Reference 模型，避免为 Java 共享结构扩大标签接口。

规则集根列表、版本历史、拒绝原因列表与详情用于选择器，全部复用原 API，不另加“复制查询”。规则集先按精确编码检索或分页浏览，再用历史接口选准确版本；不能把当前页过滤伪装成全库搜索。选项资格按 [data.md](./data.md) 的引用矩阵执行。

发布顺序需要 Risk → 同步更新 BFF 的成功响应 Reader → Web；旧 Web 标签页遇到合法空动作或 FLOW_END 新变体时提示刷新，不要求旧 Reader 猜测新类型。M01–M04 仍在原 v1 上修改，不创建 V2 类型；既有 Hash Golden/回放必须保持。

## 6. 错误与静态校验

沿用 [Code-MoR Problem Profile](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)：六个必填成员 type/title/detail/code/requestId/timestamp；无 body status，无自定义 violations。错误日志不输出 graph、条件字面量、变更原因或内部文案。

| 情况 | HTTP / Code | 页面处理 |
|---|---|---|
| Body 字段、图结构、条件格式不合法 | 422 VALIDATION_ERROR + errors[{field,message}] | 定位 Body 路径到节点/连线，保留编辑 |
| JSON/Header/Path/Query 类型或语法不合法 | 沿用 400/405/415 等既有通用 Code | 顶层错误，不伪装成 Body 字段 |
| 请求 Body 超限 | 413，沿用 Body 超限 Code | 保留配置并提示减少节点/条件 |
| 同编码已存在 | 409 RISK_FLOW_CODE_EXISTS | 不覆盖已有流 |
| base/CAS 落后 | 409 RISK_FLOW_VERSION_CONFLICT | 保留编辑，用户选择重新加载；不自动重放成新版本 |
| 整体已删除 | 409 RISK_FLOW_DELETED；正常当前详情为 404 | 只保留历史查看 |
| 当前规则引用/原因资格已变化 | 422 RISK_FLOW_REFERENCE_UNAVAILABLE | 保留编辑，执行一次同 Body 静态校验以显示最新定位；不自动替换版本 |
| 根、版本或来源绑定不存在/不归属 | 404，复用配置不存在 Code | 不暴露别的资源，支持重试/浏览器返回 |
| 同幂等键不同请求 / 命令竞争 | 沿用 Risk 409 冲突 Code / 资源锁错误映射 | 不自动换键；重试原命令 |
| 元数据不支持或持久化内容损坏 | 依现有区分：不可编辑能力 / 500 INTERNAL_ERROR | 显示不可用，不清空为新图或默认放行 |

N04 对可报告的图/引用问题正常返回 200 valid=false；身份/授权/传输错误仍为非 2xx。N03 在写入前重新执行同一套纯图规则及事务内引用/CAS检查。开始唯一、无入边、一条无条件出口；END 无出口；所有端点存在且同版、不自连；RULE_SET 出口矩阵完整；条件优先级唯一连续；全图无环且所有节点从开始可达并可达 END。不增加条件重叠推理或运行求值。
