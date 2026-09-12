# RCO-16 接口合同

> **Gateway 补改接口边界：** [数据库动态路由方案](./gateway-dynamic-routing-design.md)复用 PLT-I20 和既有 Target/Route 管理接口，保持 v1、schemaVersion=1 和全部 Wire 字段，不增加通用配置 CRUD；本文件的 14 个业务操作不变。

阶段 3 已确认合同：移除标签适用主体关系和字典变更回执；实现与发布状态见当前台账。业务范围见 [requirement.md](./requirement.md)，事务和兼容判据见 [data.md](./data.md)。本文件是本需求新增 Wire 的设计入口；阶段 4 同步到 Risk `*-api`、BFF 和前端合同，不创建另一套版本。

## 1. 传输与鉴权

- Owner 为 Risk，Operations BFF 使用轻量 `risk-api` 的 `@HttpExchange` 调用。浏览器路径为下表路径加 `/api/operations`；Gateway 转发到 BFF 时去掉此前缀。Risk 和 BFF 的下表相对路径相同。
- 所有查询需要现有 `RISK_DECISION_CONFIG_READ`。字典创建、编辑、启停分别使用下表六个新权限；配置人员选择字典不需要维护权限。每个请求均经过既有 Platform 授权链；按钮隐藏不能代替服务端鉴权。
- BFF 将认证得到的 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id` 及 Request ID 传给 Risk，不接受 Body 声明操作者。Actor 是 Account ID，本期不新增用户姓名聚合。
- 六个新字典写接口不采用幂等键或历史响应回放；Body 为 JSON、最多 16 KiB。浏览器不发送 Idempotency-Key，BFF 不向 Risk 转发该头；本轮新 Route 不开放其 CORS/转发注册。原规则集命令的幂等合同不变。新 GET 不接受 Body；Query 仅允许表内白名单，重复、未知参数或非法值返回 400，不静默忽略。新写 Body 不接受未知属性。
- 成功返回 HTTP 200、`application/json`；不加通用成功外壳。分页严格使用 `{items,page,size,total}`。`page` 默认 1、范围 1–100000；`size` 默认 10，只允许 10/20/50/100/200。`total` 是非负 JSON 安全整数，超过安全范围视为 Owner 协议错误。空页保留请求 page/size，不擅自回第一页。
- `Id`、并发 `version/expectedVersion` 是十进制字符串，范围 1–9223372036854775807，不含前导零；业务 `versionNo` 是 1–2147483647 的整数。时间为 UTC RFC 3339 字符串，固定六位小数。`?` 表示属性必定出现但允许 null，其余字段必填、非 null；列表不允许 null 元素。类名表达业务含义，不带 V1/V2。
- 错误沿用 [Code-MoR Problem Profile](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)。BFF 只投影已登记的安全错误语义，不能透传 Owner 原始消息或未知错误 Body。

## 2. 新增 API：14 个操作

`R` 表示现有 READ 权限；创建、编辑、状态切换的请求/响应在第 4 节统一定义，但每个类型都有独立 DTO 和权限，不接受动态表名或类型参数。

| # | Method / Path | Query / Body | 200 响应 | 权限 |
|---|---|---|---|---|
| 01 | GET `/decision-rule-sets/v1/rule-sets/{ruleSetId}/versions` | page,size,maxVersionNo（必填） | Page<RuleSetHistoryItem> | R |
| 02 | GET `/decision-rule-sets/v1/rule-sets/{ruleSetId}/release-references` | page,size,environmentCode,status,ruleSetVersionId | RuleSetReleaseReferences | R |
| 03 | GET `/decision-dictionaries/v1/rejection-reasons` | page,size,keyword,status | Page<RejectionReason> | R |
| 04 | GET `/decision-dictionaries/v1/rejection-reasons/{reasonCode}` | 无 | RejectionReason | R |
| 05 | POST `/decision-dictionaries/v1/rejection-reasons` | CreateRejectionReason | DictionaryWriteResult | `RISK_REJECTION_REASON_CREATE` |
| 06 | PUT `/decision-dictionaries/v1/rejection-reasons/{reasonCode}` | UpdateRejectionReason | DictionaryWriteResult | `RISK_REJECTION_REASON_UPDATE` |
| 07 | POST `/decision-dictionaries/v1/rejection-reasons/{reasonCode}/status` | ChangeDictionaryStatus | DictionaryWriteResult | `RISK_REJECTION_REASON_STATUS` |
| 08 | GET `/decision-dictionaries/v1/rejection-reasons/{reasonCode}/references` | page,size,scope | Page<DictionaryReference> | R |
| 09 | GET `/decision-dictionaries/v1/subject-tags` | page,size,keyword,status | Page<SubjectTag> | R |
| 10 | GET `/decision-dictionaries/v1/subject-tags/{tagCode}` | 无 | SubjectTag | R |
| 11 | POST `/decision-dictionaries/v1/subject-tags` | CreateSubjectTag | DictionaryWriteResult | `RISK_SUBJECT_TAG_CREATE` |
| 12 | PUT `/decision-dictionaries/v1/subject-tags/{tagCode}` | UpdateSubjectTag | DictionaryWriteResult | `RISK_SUBJECT_TAG_UPDATE` |
| 13 | POST `/decision-dictionaries/v1/subject-tags/{tagCode}/status` | ChangeDictionaryStatus | DictionaryWriteResult | `RISK_SUBJECT_TAG_STATUS` |
| 14 | GET `/decision-dictionaries/v1/subject-tags/{tagCode}/references` | page,size,scope | Page<DictionaryReference> | R |

共 8 个 GET、6 个写操作；撤销首稿的主体类型查询接口。无字典删除 API；停用不释放编码。无新增版本详情 API：复用现有 GET `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}`，此处 Path 参数仍是**版本 ID**，不能改为根 ID。原 DELETE 同形路径的参数仍是根 ID。

## 3. 历史与引用查询

### 3.1 RuleSetHistoryItem

精确字段：`ruleSetId:Id, ruleSetVersionId:Id, pointCode:Code, ruleSetCode:Code, ruleSetName:string(1..255), versionNo:int, status:DRAFT|FROZEN|RETIRED, isCurrent:boolean, createdBy:Id, changeReason:string(1..500), createTime:Time`。

按 `(version_no DESC, rule_set_version_id DESC)` 排序。`maxVersionNo` 是本次浏览的上界，来自主列表当前业务版本；要求正整数且不大于查询时该根当前业务版本。所有页和 total 都加 `version_no <= maxVersionNo` 条件，期间新增版本不造成翻页重复。新进入历史列表重新取上界，浏览器返回保留上界。直达链接缺上界时由 Web 先解析该规则集当前入口再用 replace 补齐，不把缺参请求发给 Risk。`isCurrent` 比较数据库当前指针，不等同于本次上界。

根不存在或已整体删除均 404。查询事务使用同一一致性视图读取根、total 和页面；既有 Hash/闭包校验失败按 Owner 数据错误处理。版本详情仍使用旧响应，Web 核对 `pointCode + ruleSetCode + ruleSetVersionId`；历史详情不调用“必须最新”的编辑检查，不依赖当前 Authoring Context 成功才能展示保存事实。

```json
{"items":[{"ruleSetId":"71001","ruleSetVersionId":"72003","pointCode":"CHECKOUT_PREPARE","ruleSetCode":"CHECKOUT_RISK","ruleSetName":"结账风险检查","versionNo":3,"status":"DRAFT","isCurrent":true,"createdBy":"501","changeReason":"新增地区限制配置","createTime":"2026-09-11T14:20:00.000000Z"}],"page":1,"size":10,"total":3}
```

### 3.2 RuleSetReleaseReferences

精确字段：`ruleSetId:Id, inUse:boolean, references:Page<ReleaseReference>`。这是带领域使用摘要的查询结果，分页仍放在标准 Page 中。`inUse` 始终覆盖该根**所有版本、所有环境**，与当前页或筛选条件独立；不能将筛选后 0 条误认为允许删除。

ReleaseReference 精确字段：`releaseId:Id, releaseNo:string(1..64), environmentCode:TEST|PRODUCTION, status:ReleaseStatus, pointCode:Code, pointVersionId:Id, pointVersionNo:int, ruleSetVersionId:Id, versionNo:int, activatedTime:Time?, supersededTime:Time?`。

ReleaseStatus 原值为 DRAFT/VALIDATING/SHADOWING/REVIEW_PENDING/APPROVED/ACTIVE/SUPERSEDED/REJECTED；UI 以明确状态区分，候选不是新的后台状态。按 release_id 倒序、rule_set_version_id 倒序；同一个发布到同一个规则集版本的边只返回一次。

三个筛选均可省略，省略表示全部；传入空值或未知枚举为 400。`ruleSetVersionId` 必须属于该根，否则 404。关系只来自 `Release → Point Version → Point/RuleSet Binding → RuleSet Version`。ACTIVE 摘要复用原严格占槽/闭包检查，不修改旧删除保护查询的行集合；关系损坏、异常占槽不能伪装成无引用。分页查询在同一只读事务内完成，不保证不同请求间的发布状态快照。

```json
{"ruleSetId":"71001","inUse":true,"references":{"items":[{"releaseId":"75001","releaseNo":"REL-20260911-03","environmentCode":"PRODUCTION","status":"ACTIVE","pointCode":"CHECKOUT_PREPARE","pointVersionId":"74006","pointVersionNo":6,"ruleSetVersionId":"72002","versionNo":2,"activatedTime":"2026-09-11T08:00:00.000000Z","supersededTime":null}],"page":1,"size":10,"total":3}}
```

图只把当前页的边投影为节点和连线，不新增完整图缓存/图数据库。BFF 本期没有跨系统数据聚合，负责授权转发和严格响应映射；Web 负责布局。将来若加 Account 名称等跨系统查询，应在 BFF 的 Processor 聚合，不能塞进 Risk。

## 4. 字典字段与命令

### 4.1 查询字段与约束

| 模型 | 精确字段 |
|---|---|
| RejectionReason | `reasonCode:Code, name:Name, description:Description?, defaultMessage:Message, status:DictionaryStatus, version:Id, createdBy:Id, updatedBy:Id, createTime:Time, updateTime:Time` |
| SubjectTag | `tagCode:Code, name:Name, description:Description?, status:DictionaryStatus, version:Id, createdBy:Id, updatedBy:Id, createTime:Time, updateTime:Time` |
| DictionaryWriteResult | `dictionaryType:REJECTION_REASON|SUBJECT_TAG, code:Code, version:Id, status:DictionaryStatus, changedBy:Id, changedTime:Time` |

Code 为 1–64 位 `[A-Z][A-Z0-9_]{0,63}`；编码创建后固定，两类字典各自永久唯一。Name 为 1–100 字符，Description 最多 500，Message 为 1–500。字符串长度按 Unicode code point 验证；禁止 NUL 和不可展示控制字符，Message/Description 可保留换行。Code/Name 不接受首尾空白；消息保留原换行且必须含非空白字符；空 Description 规范化为 null。不改变原规则消息的规范化合同。

DictionaryStatus 只有 ACTIVE/DISABLED；创建为 ACTIVE，页面按钮明确“创建并启用”。标签不设置适用主体字段或关系，不按主体类型过滤；标签的业务含义由运营通过名称与说明维护。规则动作的目标绑定仍由既有 Authoring Context 动态提供，仅能选择真实工程支持的绑定；移除字典约束不等于新增 Consumer 输入或执行能力。

列表 keyword 可省略；若提供则 1–100 字符且首尾无空白，按编码或名称包含匹配（数据库既有不区分大小写排序规则）；`%`、`_`、反斜杠作为字面量转义。status 可省略表示全部。字典按 code ASC、主键 ASC 排序；详情 404 表示未登记，停用记录仍可读。

选项查询复用分页列表，只固定 status=ACTIVE，不按标签主体筛选。不把整本字典塞进 Authoring Context。缺少字典/停用项在历史中用原代码展示；辅助名称只能标为“当前字典名称”。字典查询失败不得显示成“未登记”。

### 4.2 请求模型与人工确认结果

| 操作 | Body 精确字段 |
|---|---|
| CreateRejectionReason | `reasonCode, name, description?, defaultMessage` |
| UpdateRejectionReason | `expectedVersion, name, description?, defaultMessage`，代码仅在 Path |
| CreateSubjectTag | `tagCode, name, description?` |
| UpdateSubjectTag | `expectedVersion, name, description?` |
| ChangeDictionaryStatus | `expectedVersion, status` |

description 的键必须提供但允许 null；更新提交完整可编辑字段，不以省略表示“不修改”。状态单独切换。字典不再提交或保存逐次 changeReason，不新增历史表或替代 JSON 快照；根表仍记录创建/最后更新人和时间。原规则集版本的 changeReason 保持必填。

创建由永久唯一编码防止重复记录；更新/启停要求 expectedVersion，每次成功更新 version +1，包括新发起的同值更新。过期版本返回 409，不自动合并；停用不受引用数阻止。

以下是六类操作的合成 Body 示例，非初始化数据：

```json
{"reasonCode":"COUNTRY_NOT_SUPPORTED","name":"地区不支持","description":"用于地区准入规则","defaultMessage":"当前国家或地区不在支持范围内。"}
```

```json
{"expectedVersion":"1","name":"地区不支持","description":null,"defaultMessage":"当前地区暂不支持。"}
```

```json
{"tagCode":"REQUIRES_ATTENTION","name":"需要关注","description":"标识需要人工持续关注的情况"}
```

```json
{"expectedVersion":"1","name":"需要关注","description":"由运营判断使用场景"}
```

```json
{"expectedVersion":"2","status":"DISABLED"}
```

两类字典启停使用同一个上述 Body 形状；写操作返回本次事务形成的 DictionaryWriteResult，不提供以后重放该结果的接口或存储：

```json
{"dictionaryType":"REJECTION_REASON","code":"COUNTRY_NOT_SUPPORTED","version":"2","status":"ACTIVE","changedBy":"501","changedTime":"2026-09-11T15:30:00.000000Z"}
```

UI 成功后刷新详情/列表。写请求出现超时、断网、连接中断或 5xx 时，结果可能已提交；UI 保留草稿，提示“结果未确认，请刷新查询后确认”，不自动重发命令、不轮询恢复。操作人通过对应代码的 GET 查看当前值、version、更新人/时间后决定是否重新操作；新修改必须基于重新读取的版本。当前查询不能证明某次历史请求一定成功，不将内容相同伪装成首次成功回执。

同 code 再次创建即使内容相同也返回 409 CODE_EXISTS；重复旧 expectedVersion 更新返回 409 CHANGED。新字典请求不 claim/扩展 t_command_idempotency，不新增操作类型、response_ref 前缀、请求 Hash Schema 或历史响应 JSON。原规则保存/冻结/删除/Candidate 的幂等行为完全保持。

### 4.3 字典引用

`scope` 默认 ALL；CURRENT 为未删除根的当前配置，HISTORY 为未删除根的非当前版本，DELETED 为已删除根的全部版本。过滤不会改变 `activeEnvironments` 的事实；ALL 包含三类。编码未登记返回 404，登记但未引用返回空 Page。

DictionaryReference 精确字段：`ruleSetId:Id, pointCode:Code, ruleSetCode:Code, ruleSetName:string, ruleSetVersionId:Id, versionNo:int, ruleCode:Code, ruleName:string, referenceKind:REJECTION_REASON|SUBJECT_TAG, actionSequenceNo:int?, targetBindingCode:Code?, ruleSetStatus:CONFIGURABLE|DELETED, isCurrentConfiguration:boolean, activeEnvironments:(TEST|PRODUCTION)[]`。后两项分别表示根当前指针是否相等（删除根也可为 true）与该**精确版本**的 ACTIVE 发布环境，去重排序；不能混成单一“使用”标志。

拒绝原因只查终态动作 DENY 的实际原因字段；非 DENY 的历史 reasonCode 不纳入此字典治理。标签只查 `ADD_RISK_TAG` 的类型化 `TAG` 字面量参数，按规则与动作序号去重同一代码，同一规则不同动作允许各占一行。反查不得遍历全量版本发 N 次详情请求。按 rule_set_version_id DESC、rule_id ASC、action_sequence_no ASC 排序；原因的动作序号和目标绑定为 null。

例：`{"items":[{"ruleSetId":"71001","pointCode":"CHECKOUT_PREPARE","ruleSetCode":"CHECKOUT_RISK","ruleSetName":"结账风险检查","ruleSetVersionId":"72002","versionNo":2,"ruleCode":"COUNTRY_RESTRICTION","ruleName":"指定地区拒绝","referenceKind":"REJECTION_REASON","actionSequenceNo":null,"targetBindingCode":null,"ruleSetStatus":"CONFIGURABLE","isCurrentConfiguration":false,"activeEnvironments":["PRODUCTION"]}],"page":1,"size":10,"total":1}`。

已删除根可在引用结果标识，但 UI 不提供普通详情跳转；没有新增读取已删除历史的 API。后台仍保留已确认的普通详情 404。

## 5. 修改与保持不变的 API

原 POST `/decision-rule-sets/v1/rule-sets` 请求/响应、64 KiB、根 CAS、保存新版本和幂等合同保持。仅增加首次新保存的字典资格校验：DENY 选择有效原因，标签新选代码须已登记并启用；严格沿用基线的旧项例外见 data.md。前端在选择原因时填入默认消息，发送的仍是原 reasonCode/reasonMessage；Risk 不在保存时重新读默认消息覆盖请求。

原 Authoring Context、主列表、按版本详情、Freeze、Candidate、整体 DELETE 响应键均不增加字段。Candidate Tab 仍隐藏。既有 DELETE 的 ACTIVE 判定仍为全版本使用；字典状态不改变发布/冻结状态或删除资格。

## 6. 新错误与原错误

| HTTP / code | 分支与 UI 行为 |
|---|---|
| 404 `RISK_DICTIONARY_NOT_FOUND` | 字典代码不存在；详情显示不可用，历史原代码展示不依赖此查询 |
| 409 `RISK_DICTIONARY_CODE_EXISTS` | 两个创建竞争同一类型代码，包括已停用记录；保留表单，定位编码 |
| 409 `RISK_DICTIONARY_CHANGED` | expectedVersion 过期；保留草稿并提示重新载入，不自动覆盖他人修改 |
| 409 `RISK_DICTIONARY_SELECTION_UNAVAILABLE` | 首次保存的新选代码缺失/停用；返回安全提示，不返回原规则/文案；保留表单重新选择 |
| 422 `VALIDATION_ERROR` | Body 字段格式/长度/取值；按既有 Foundation Profile 可带 errors。非法 JSON、重复/未知字段或类型错误为 400 `INVALID_REQUEST` |
| 其他现有 400/401/403/404/409/413/429/500/502/503 | 沿用现有参数、会话、规则集不存在/变化、幂等冲突/处理中、大小、资源锁、Owner 数据与上游不可用错误码及映射，不批量改名 |

新增错误必须加入 Risk 错误枚举及 BFF 白名单；未知 Owner code 仍按现有上游协议错误处理。新 API 精确键校验独立登记，不能放宽旧 Shape 校验。示例 HTTP 409、`Content-Type: application/problem+json`：

```json
{"type":"about:blank","title":"Conflict","detail":"字典已由其他操作更新，请重新载入后确认。","code":"RISK_DICTIONARY_CHANGED","requestId":"rco16-example","timestamp":"2026-09-11T15:31:00.000000Z"}
```

响应 Header `X-Request-Id` 与 Body 一致。Body 不增加 status/instance，字段错误不新增第二层 code。
