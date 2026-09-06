# Risk Operations 决策规则配置后端首期设计

- Chat design: Confirmed
- Written spec review: Confirmed
- Implementation status: Implemented and locally verified
- Deployment status: Not deployed
- Decision date: 2026-09-05
- Implementation, verification and deployment: see [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- Scope: Risk-owned `CHECKOUT_PREPARE` Rule Set authoring, freeze, TEST Candidate and eight static checks
- Error-contract supersession: 第 11 节的错误 Body 形状由 [`2026-09-06-system-wide-rfc-9457-problem-details-design.md`](./2026-09-06-system-wide-rfc-9457-problem-details-design.md) 取代；Risk 业务 Code/Status 映射继续由本文拥有

> 2026-09-06 Console 集成后继：[决策记录](../../../agent-docs/decisions/2026-09-06-risk-operations-rule-configuration-console-integration.md)
> 与[完整设计](./2026-09-06-risk-operations-rule-configuration-console-integration-design.md)批准 Operations
> Web/BFF/Platform/Gateway 接入，并只取代本文第 6.3 节未来 1 MiB Body Limit 建议；后端业务合同和本切片
> 历史实现范围不变。

> 实现、物理数据库、验证、人工 Review 与部署的当前状态及精确证据只以
> [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本设计不维护第二份
> 活态快照；本地实现或验证不构成共享环境、生产部署或端到端能力声明。UI/BFF/Gateway/Auth、Shadow、
> 审批、激活和运行期评估不在本设计的实现范围；全局 COMP-01、DB-01 与 Vault V1 仍未完成。

本书面规格承接已确认的会话设计和书面评审，冻结 Risk & Compliance 第一个面向 Operations 的
规则配置后端切片。当前实现、验证和部署事实仍只由
[`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 记录。它只建设
`risk-compliance-service` 内的类型化合同、规则聚合、持久化、冻结、TEST Candidate 和静态校验，
不代表 Operations Web/BFF/Gateway/Platform 权限、Shadow、审批、激活、真实评估或部署已经完成。

## 1. 目标与边界

### 1.1 目标

首期要交付一个供受控 Loopback 调用、并可在后续接入 Operations Console 的 Risk 私有后端：

1. 调用方可以查看工程登记的 Operations 编辑投影。
2. 调用方可以以一次完整聚合请求保存新的 Rule Set DRAFT 快照。
3. 调用方可以冻结准确的 DRAFT Version，冻结后不可改写。
4. 调用方可以用一个冻结快照创建 TEST Candidate。
5. Risk 可以执行并永久记录八项静态检查。
6. 每个本地事务原子提交或完整回滚；跨事务 Validate 具备明确的同键恢复、并发收敛和损坏检测。
7. 成功写入具备持久幂等、并发冲突和可解释的业务内容/状态证据；操作者身份来源在本切片不构成认证闭环。

### 1.2 明确排除

本切片不包含：

- Operations Web 页面、`operations-console-bff` 聚合或浏览器验收；
- Gateway Target/Route/Guard、Platform Console Resource、Permission 或 PLT-I02 授权；
- `PRODUCTION` Candidate、Shadow/Replay、质量门禁、双人审批、激活或回滚；
- Checkout 真实评估入口、Assessment、Evaluation、Trace、Intent 或 Action Effect；
- Feature 执行、名单、冷却、Velocity、Limit、Settlement Control 或其他副作用；
- Nacos、Redis、RabbitMQ、XXL-Job、Compensation Event 或任何外部调用；
- `risk-control` 和 `risk-compliance` 业务模块修改；
- `mor-test`、共享环境或生产部署。

## 2. 当前事实和实施前置

本设计确认时，Risk 已有 71 表 PDManer 模型和一个 RCO-I09 Manual Payout 合同骨架，但没有 Rule Set
配置 HTTP API、MyBatis 持久化、手工 Schema SQL、初始化 DML 或部署证据。默认 RCO-I09 仍以
`503` Fail-Closed；`local-evaluation-bootstrap` 仍只是进程内合同联调能力。

数据库实施前必须先完成三项已知跨切对齐：

- COMP-01：Risk `compensation_event` 模型对齐当前 4 KiB 最小载荷合同；
- DB-01：生成的物理 DDL 不创建 `CHECK` 或 `FOREIGN KEY`；
- Vault V1：清理 Risk 模型中仍与当前三表 Vault 能力冲突的字段。

这些是生成一份可执行完整 Schema 的前置，不是本切片要启用的运行能力。

### 2.1 与现行 Risk 权威的兼容范围

本设计新增的是一个可先实施的配置后端切片，不用九个可编辑字段取代 RCO-I03 的完整
`OrderPreparationContext` 或 `checkout_input_snapshot`：

- 九个字段只定义当前 Point Template 中 `rule_reference_flag=1` 的 Operations 规则可引用投影；
- 预分配 Order/Item ID、主体与来源 Tuple、Assignment/Agreement/Term/V5 Hash、Legal/Seller 等既有
  RCO-I03 运行事实仍属于未来运行合同和不可变 Snapshot，只是不能被本切片的 Operations 规则引用；
- 本切片没有 RCO-I03 运行入口、Snapshot 或 Evaluation 写入，因此不改变这些事实的所有权和完整性要求；
- 现行长期模型仍可在后续 Point Version 引入工程批准的 Feature、Obligation、Limit Binding 和更多
  可引用字段；当前最小 Template 的零集合不删除这些模型能力。

本设计同时新增两个窄生命周期决定，实施时必须写入专项决策记录并同步 Risk 数据库权威：

- 保存下一份完整 DRAFT 时，允许被替代的最新 DRAFT 直接 `DRAFT -> RETIRED`；
- 静态检查全部通过后，本切片暂留 `VALIDATING`，由后续 Shadow 命令在重新验证门禁后执行
  `VALIDATING -> SHADOWING`。这不删除长期状态图中的该迁移，也不伪造新的状态。

## 3. 模块与代码边界

### 3.1 `risk-api`

发布两个轻量内部 `@HttpExchange` 合同：

- `DecisionRuleSetManagementApi`：编辑上下文、Rule Set 列表/详情、保存新 Version、冻结；
- `DecisionReleaseManagementApi`：TEST Candidate 列表/详情、创建和静态验证。

API DTO 使用 `*RequestBody` / `*ResponseBody`，传输枚举使用 `String` Code，所有 BIGINT 业务标识
使用十进制字符串，不把 DataObject、Repository、领域枚举或异常实现放入 `risk-api`。

### 3.2 `risk-decision`

该 Owner JAR 负责：

- Rule Set 聚合与不可变版本生命周期；
- Point Template 克隆和工程登记目录解析；
- AST 形状、类型和 Canonical Hash 算法；
- ALLOW/REVIEW/DENY 纯决策 Action Handler 登记信息；
- Freeze、Candidate 组成和八项静态检查；
- Decision 领域 DataObject、Mapper XML、Repository 和转换器。

业务 Service 和 Domain Service 必须使用 `interface + *Impl`，Repository 使用
`interface + *RepositoryImpl`。不为每张表创建空 Service。

### 3.3 `risk-web`

`risk-web` 是唯一 HTTP 入口和事务编排层：

- Controller 直接实现 `risk-api` 接口，只做协议转换；
- 创建快照、Freeze、Candidate 和 Validate 由具体 Processor 编排；
- Processor 使用 `TransactionTemplate` 持有明确的本地事务；
- `command_idempotency` 是服务级技术表，由 `risk-web` 的服务级能力统一接入；
- 不新建本地 `@RestControllerAdvice` 或第二套错误 Body。

### 3.4 `risk-test`

`risk-test` 使用真实 Loopback HTTP 和仓库 `local-environment` MySQL 验证生产链。纯 Canonicalizer、
AST Validator 和状态机测试也统一放在 `risk-test/src/test/java/.../auto/domain`，直接实例化
`risk-decision` 的真实对象且不启动 Spring；生产业务 JAR 不承载可执行测试。

## 4. `CHECKOUT_PREPARE` 工程目录

首期工程模板是 `risk-decision` 中版本化的只读描述符 `CHECKOUT_PREPARE_AUTHORING_V1`。初始化 DML 只登记
稳定 `decision_point`、三个 `action_version` 及其准确 Handler Hash，不创建一个可被 Operations 修改的
“模板 Point Version”。每次保存 Rule Set 时，后端在事务内把描述符物化为新的 DRAFT Point Version、
九条 Input Field 和一条 Rule Set Binding。Authoring Context 将编译描述符与数据库 ACTIVE Action Version
交叉校验；缺失、重复或 Hash 漂移时失败关闭，不返回半套目录。

### 4.1 Decision Point

唯一可编辑事件点固定为：

| 字段 | 值 |
|---|---|
| `point_code` | `CHECKOUT_PREPARE` |
| `business_domain` | `CHECKOUT` |
| `input_contract_code` | `ORDER_PREPARATION` |
| `input_contract_version` | `4` |
| `output_contract_code` | `RISK_DECISION_RESULT` |
| `default_decision` | `REVIEW` |
| `evaluation_strategy` | `FIRST_TERMINAL` |
| `failure_strategy` | `FAIL_CLOSED` |
| `timeout_ms` | `500` |
| Point status | `ACTIVE` |

未命中规则是正常业务 `REVIEW`；运行技术失败仍是 `FAILED/TIMED_OUT`，不伪造为业务
`REVIEW` 或 `DENY`。本切片尚不实现运行评估。

### 4.2 Context Field 白名单

工程模板恰好声明九个 Operations 规则可引用字段。它们不是 RCO-I03 完整传输或 Snapshot 字段清单：

| Field Code | Value Type | Required | Classification | Rule Reference | Action Parameter |
|---|---|---:|---|---:|---:|
| `country_code` | `STRING` | yes | `INTERNAL` | yes | no |
| `locale` | `STRING` | yes | `INTERNAL` | yes | no |
| `industry_code` | `STRING` | yes | `INTERNAL` | yes | no |
| `mcc_code` | `STRING` | yes | `INTERNAL` | yes | no |
| `currency` | `STRING` | yes | `INTERNAL` | yes | no |
| `merchandise_amount` | `NUMBER` | yes | `INTERNAL` | yes | no |
| `discount_amount` | `NUMBER` | yes | `INTERNAL` | yes | no |
| `net_amount` | `NUMBER` | yes | `INTERNAL` | yes | no |
| `entity_source_type` | `STRING` | yes | `INTERNAL` | yes | no |

`entity_source_type` 只允许 `PLATFORM/CLIENT/PARTNER`。Country 使用 ISO 3166-1 大写二字母，Currency
使用 ISO 4217 大写三字母，MCC 使用四位十进制字符串；`industry_code` 使用 1–64 位大写 Code，
`locale` 使用规范 BCP 47 Language Tag。金额 Literal 使用第 5.3 节定义的 `DECIMAL(24,8)` 唯一规范文本。

本规则引用白名单不包含 Client ID、Operating Entity ID、Partner ID、Consumer Token、Email/Phone、IP、
User-Agent、网络原值、Vault Token 或 Device Identifier；这不表示未来运行 Snapshot 删除现行 RCO-I03
要求的不可变业务身份和合同事实。

### 4.3 Action 目录

唯一可选 Action 为：

| Action Code | Handler Code | Impl Version | Terminal | Decision | Merge Policy | Shadow | Parameters |
|---|---|---|---:|---|---|---:|---:|
| `ALLOW_CHECKOUT` | `DECISION_ALLOW_V1` | `1.0.0` | yes | `ALLOW` | `MOST_RESTRICTIVE` | yes | 0 |
| `REVIEW_CHECKOUT` | `DECISION_REVIEW_V1` | `1.0.0` | yes | `REVIEW` | `MOST_RESTRICTIVE` | yes | 0 |
| `DENY_CHECKOUT` | `DECISION_DENY_V1` | `1.0.0` | yes | `DENY` | `MOST_RESTRICTIVE` | yes | 0 |

三个 Action Version 的 Category 均为 `DECISION`、`shadow_supported_flag=1`、Status 均为 `ACTIVE`，且
`handler_code + implementation_version + implementation_hash` 与当前编译制品中的工程注册表唯一匹配。
具体 Hash 必须由版本化的纯决策 Handler 描述和黄金向量确定并写入 DML，不允许占位值。Shadow Flag
只证明 Handler 可安全用于未来模拟，不代表本切片创建或执行 Shadow Run。

Authoring、首次保存、Freeze 与 Candidate 创建是新增可执行配置的门禁，必须校验完整工程目录仍为
`ACTIVE`。历史 Rule Set/Candidate 的详情与成功幂等重放则只读取其实际引用的不可变 Action Version，
校验稳定 ID、Code、Version、Handler、Implementation Hash 和其他内容字段；引用版本后来合法变为
`RETIRED` 时仍须可读、可重放，无关 Action 缺失或退役也不能污染该历史投影。未知状态或内容漂移仍失败
关闭。

该 Point Template 恰好包含：

- 零个 Feature 和 Feature Dependency；
- 零个 Action Parameter Definition；
- 零个 System Obligation；
- 零个 Limit Binding。

Feature、Parameter 和 Obligation 的“零”必须被候选发布闭包查询证明，不是漏查后的默认通过；
Limit Binding 的“零”由已证明的零 Obligation 推导，并按第 10.2 节形成显式空投影。

## 5. Rule Set 聚合与版本生命周期

### 5.1 聚合边界

一个可保存快照包含：

```text
RuleSetVersion
├── RiskRule[]
│   ├── RuleNode[]
│   │   └── RuleOperand[]
│   └── one RuleAction -> one terminal ActionVersion
└── internal companion PointVersion
    ├── nine cloned InputField rows
    └── one DecisionPointRuleSet binding
```

Operations 只操作 Rule Set 业务投影。Companion Point Version、Input Field ID、Action Version ID 和 Binding ID
由后端从工程模板克隆、解析并回传为只读证据。

克隆发生在每次保存完整 DRAFT 快照时，而不是 Candidate 创建时。原因是 `rule_operand.input_field_id`
必须属于 Candidate 最终引用的准确 Point Version。Candidate 直接引用已冻结的完整快照，
不重新映射已冻结 Operand。

### 5.2 不可变 DRAFT 快照

本切片不对一个 Rule Set Version 原地更新子表。每次“保存”都创建新 Version：

- 首次保存要求 `baseRuleSetVersionId/baseContentHash` 同时为空，创建 Version 1；
- 后续保存要求两个基准字段同时存在，并指向该 Code 当前最新 Version；
- 如果基准是 `DRAFT`，则在同一事务把该 Rule Set 及 Companion Point Version 转为 `RETIRED`；
- 如果基准是 `FROZEN`，它保持不变，新增下一个 `DRAFT`；
- 已冻结或已退役 Version 的 Rule、Node、Operand 和 Action Binding 永不增改删；
- 不允许从非最新 Version 建立分支。

这里的“不可变”指内容子图不可原地修改；Root 仍可发生本节明确允许的 `DRAFT -> FROZEN` 或
`DRAFT -> RETIRED` 状态迁移。`RETIRED` Version 保留在列表/详情中用于比较和追溯，但不能 Freeze、
不能创建 Candidate。调用方可以读取任一历史内容作为新请求的编辑素材，然而并发基准仍必须指向
当前最新 Version，不能从历史 Version 建立第二条版本支线。

`version_no` 是业务版本号，不是乐观锁。并发校验使用
`baseRuleSetVersionId + baseContentHash + current status`。

### 5.3 内容 Hash

三种 Content Hash 和四种 Command Request Hash 必须分别使用版本化 Schema，不能共享一个含混的
“对象转 JSON”算法。通用编码规则固定为：

- 先把输入校验并转换成 Schema 的完整投影，再使用 RFC 8785 JCS 生成 JSON；
- 字符串在持久化和哈希前转 NFC，名称/原因去除首尾空白但保留内部空白；Code 必须已是规范大小写；
- Schema 中的可空属性始终存在并显式写 `null`，不因序列化配置省略；
- 数据库/传输 ID 使用正十进制字符串，普通 Sequence/Version 使用 JSON 整数；
- `DECIMAL(24,8)` 使用下述八位定标文本，Duration 毫秒使用无前导零的非负整数十进制字符串，
  Boolean 使用 JSON Boolean；
- Datetime 使用 UTC 微秒 `YYYY-MM-DDTHH:mm:ss.SSSSSSZ`；
- 最终值为 `SHA-256(UTF-8(JCS projection))` 的 64 位小写十六进制。

`DECIMAL(24,8)` 在 Request Hash、Content Hash、持久化和数据库回读重算中共用同一规范：输入必须是
JSON String，不接受 JSON Number、指数、前导 `+`、多余前导零或超过八位小数；数值范围是
`-9999999999999999.99999999..9999999999999999.99999999`。解析必须精确且不得舍入，随后补足八位
小数；负零输入被拒绝。因而 `"1"`、`"1.0"` 和 `"1.00000000"` 均接受并规范成
`"1.00000000"`，`"0"` 规范成 `"0.00000000"`；`"-0"`、`"01"`、`"1e0"`、
`"1.000000001"` 和 JSON Number `1` 均拒绝。Java `BigDecimal` 与 JavaScript Decimal 实现必须共享
这些向量，不能使用二进制浮点中转。

#### `RISK_RULE_SET_VERSION_V1`

Rule Set 投影字段全集为：

- `schema/pointCode/ruleSetCode/ruleSetName/versionNo/rules`；
- Rule 恰好包含 `ruleCode/ruleName/priorityNo/enabledFlag/reasonCode/condition/action`，按
  `priorityNo, ruleCode` 排序；
- `condition` 是去除 `nodeKey/parentNodeKey` 和数据库 ID 后的嵌套 AST。GROUP 包含
  `nodeType/groupOperator/children`，Child 按 `sequenceNo`；PREDICATE 包含
  `nodeType/comparisonOperator/operands`；
- Operand 包含 `operandRole/valueSequence/sourceType/fieldCode/valueType/literalValue`，按
  `LEFT` 先于 `RIGHT`、再按 `valueSequence` 排序。Context 的 `literalValue=null`；Literal 的
  `fieldCode=null`。`IN/NOT_IN` 和 `BETWEEN` 保留显式 `valueSequence`，不按 Literal 值重排；
- Action 恰好包含 `actionCode/actionVersionNo/handlerCode/implementationVersion/implementationHash` 和
  `actionCategory/terminalFlag/decisionCode/mergePolicy/shadowSupportedFlag`。

该投影不包含 Rule/Node/Operand/Binding 的数据库 ID、Root Status、审计人、时间或幂等键。显式
`priorityNo/sequenceNo/valueSequence` 决定语义顺序，请求数组自身的到达顺序不影响 Hash。

#### `RISK_DECISION_POINT_VERSION_V1`

Point Version 投影字段全集为：

- `schema/pointCode/businessDomain/pointVersionNo/inputContractCode/inputContractVersion` 和
  `outputContractCode/defaultDecision/evaluationStrategy/failureStrategy/timeoutMillis`；
- `inputFields` 按 `fieldCode` 排序，每项包含
  `fieldCode/valueType/requiredFlag/dataClassification/ruleReferenceFlag/actionParameterFlag`；
- `ruleSets` 按 `sequenceNo` 排序，每项包含
  `sequenceNo/ruleSetCode/ruleSetVersionNo/ruleSetContentHash`；
- `features/obligations/limitBindings` 是必须显式存在的空数组。

该 Hash 使用业务身份而不是克隆生成的 Row ID，使相同 Point 协议可独立重算；`pointVersionNo` 和准确
Rule Set Hash 仍使不同版本保持可区分。

#### `RISK_DECISION_RELEASE_V1`

Release Hash 冻结 Candidate 引用的准确数据库快照。投影包含：

- `schema` 和 Release 的 `releaseId/releaseNo/decisionPointId/pointVersionId/environmentCode`；
- Decision Point 的 `decisionPointId/pointCode/businessDomain`；
- Point Version 的全部不可变协议字段和 `contentHash`；
- Input Field 每行包含 `inputFieldId/pointVersionId/fieldCode/valueType/requiredFlag/dataClassification` 和
  `ruleReferenceFlag/actionParameterFlag`；
- Point Rule Set Binding 每行包含 `bindingId/pointVersionId/ruleSetVersionId/sequenceNo`；
- Rule Set Version 每行包含 `ruleSetVersionId/decisionPointId/ruleSetCode/versionNo/contentHash`；
- Rule 每行包含 `ruleId/ruleSetVersionId/ruleCode/priorityNo/enabledFlag/reasonCode`；
- Node 每行包含 `nodeId/ruleId/parentNodeId/nodeType/groupOperator/comparisonOperator/sequenceNo`；
- Operand 每行包含 `operandId/nodeId/operandRole/valueSequence/sourceType/featureVersionId/inputFieldId` 和
  `valueType/stringValue/numberValue/booleanValue/datetimeValue/durationValue`；
- Action Version 每行包含 `actionVersionId/actionCode/actionCategory/versionNo/handlerCode` 和
  `implementationVersion/implementationHash/terminalFlag/decisionCode/mergePolicy/shadowSupportedFlag`；
- Rule Action 每行包含 `ruleActionId/ruleId/actionVersionId/sequenceNo`；
- 上述集合按各自行主键数值升序，再按完整行 JCS Bytes 打破平局；
- Feature、Dependency、Action Parameter Definition/Binding、Obligation 和 Limit Binding 集合显式为空。

Release 投影有意包含稳定数据库 ID，因为它证明 Candidate 精确引用哪组不可变 Row，而不只是语义相似的
另一组配置。它排除 Release Status/Version/Actor/Time、Rule Set/Action Status 等可变运行字段。
现有 JavaScript Decision Engine Validator 必须迁移到这个命名 Schema，并与 Java 共享黄金向量。

#### Command Request Hash

四个命令分别使用以下完整投影；`schema` 值同时是哈希命名空间：

| Schema | 字段全集 |
|---|---|
| `RISK_CREATE_RULE_SET_VERSION_COMMAND_V1` | 第 6.4 节定义的 Create 语义投影 |
| `RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1` | `ruleSetVersionId/expectedContentHash` |
| `RISK_CREATE_TEST_RELEASE_COMMAND_V1` | `ruleSetVersionId/expectedRuleSetContentHash` |
| `RISK_VALIDATE_TEST_RELEASE_COMMAND_V1` | `releaseId/expectedVersion` |

Actor 已进入 `caller_scope`，Session/Request ID/传输时间不进入 Request Hash。三种 Content Hash 与四种
Request Hash 各司其职，不互相替代。

Create 语义投影的字段全集为
`schema/pointCode/ruleSetCode/ruleSetName/baseRuleSetVersionId/baseContentHash/changeReason/rules`。
`rules` 使用 `RISK_RULE_SET_VERSION_V1` 的 Rule 规范化和排序，但 Action 只包含请求允许选择的
`actionCode/actionVersionNo`；Handler、Implementation Hash、Decision 等目录属性由服务端解析，不接受客户端
提交。后端先用 `nodeKey/parentNodeKey` 验证并构造嵌套语义 AST，再从 Request Hash 排除这些局部键；兄弟
节点和 Operand 按显式 Sequence 排序，原始数组到达顺序不进入 Hash。Base 两字段的空值显式规范为
JSON Null。这样局部键重命名或数组搬动不改变同一业务请求的 Hash，而任何业务顺序、Literal、Action
坐标或审计原因变化都会改变 Hash。

### 5.4 成功配置的审计字段

当前 `rule_set_version` 缺少永久创建人和变更原因。为了让每个不可变版本自身保存完整的业务内容和
状态来源关联，物理模型增加：

- `created_by BIGINT NOT NULL`；
- `change_reason VARCHAR(500) NOT NULL`。

`change_reason` 是 1–500 字符的可展示中文或英文原因，不得包含密钥、凭据、个人数据或完整
Payload。输入先做 NFC 与首尾空白规范化；控制/格式字符、完整 JSON Object/Array、Bearer 或常见
Credential Assignment、可识别 Email/Phone，以及包含 Unicode 空格/横线分隔符变体的 Luhn 有效
13–19 位 PAN 候选必须在事务与幂等 Claim 前统一返回 400，错误不得回显原值。Freeze 继续使用现有
`frozen_by/frozen_time`。前一 DRAFT 的退役人和时间可由同 Code
下一 Version 的 `created_by/create_time` 与原行 `update_time` 重建，不增加专用 History 表。当前 Header
仅经过格式校验，所以 `created_by/frozen_by` 在本切片只表示调用方声明的 Actor Correlation；接入已认证
Operations BFF、PLT-I02 和受限服务调用边界前，不得把它宣称为已验证的人工身份审计。

## 6. 类型化 AST 合同

### 6.1 Rule

每个 Rule 包含：

- 一个在当前 Rule Set Version 内唯一的大写 `ruleCode`；
- `ruleName`、唯一正整数 `priorityNo`、`enabledFlag` 和可空 `reasonCode`；
- 一棵完整条件树；
- 恰好一个终态 Action，`sequenceNo=1`；
- 零个 Action Parameter。

被禁用的 Rule 仍必须结构和类型完整，不允许使用“先禁用、后补配置”保存半成品。
`reasonCode` 在首期保持可空：解释合同始终提供确定的 Rule Code/Name、Action 和 Trace；只有显式填写时
才额外提供业务原因 Code。本切片不暗建尚未批准的原因目录，也不承诺每个 REVIEW/DENY 都有 Reason Code。

### 6.2 Node

API 使用快照内局部 `nodeKey/parentNodeKey`。它们只用于一次请求的树结构关联，后端为新 Version
分配全新数据库 ID。

结构约束：

- 每个 Rule 恰好一个 Root，全部 Node 可从 Root 到达且无环；
- `GROUP/AND` 和 `GROUP/OR` 至少两个连续排序子节点；
- `GROUP/NOT` 恰好一个子节点；
- GROUP 没有 Operand 和 Comparison Operator；
- PREDICATE 没有子节点或 Group Operator；
- 同父节点 `sequenceNo` 从 1 开始连续且唯一。

### 6.3 Operator 和 Operand

可用 Operator 固定为：

`EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/BETWEEN/IS_NULL/IS_NOT_NULL`。

可用 Value Type 固定为：

`STRING/NUMBER/BOOLEAN/DATETIME/DURATION`。

长期数据库模型支持 `FEATURE`，但本切片 Authoring Context 和写 API 只接受 `CONTEXT/LITERAL`；提交
`FEATURE` 或任何其他 Source Code 均被拒绝。

- `EQ/NE/GT/GTE/LT/LTE`：左右各一个标量；
- `IN/NOT_IN`：一个非 Literal 值和对侧一个或多个连续 Literal；
- `BETWEEN`：一个非 Literal 值和对侧恰好两个连续 Literal；
- `IS_NULL/IS_NOT_NULL`：恰好一个非 Literal 值；
- 所有 Operand 类型必须一致；
- `GT/GTE/LT/LTE/BETWEEN` 只允许 `NUMBER/DATETIME/DURATION`；
- Literal 恰好一个对应类型值列非空；
- String Literal 最长 512 字符，Number 遵守 `DECIMAL(24,8)`。
- DATETIME Literal 必须是 MySQL `DATETIME(6)` 可表达的严格日历时间：年份 `1000..9999`、UTC `Z`、
  秒和恰好六位微秒；拒绝 `24:00`、闰秒、非法闰日/月日及其他可被宽松解析器改写的文本。

`valueSequence` 在同一 `(Node, Operand Role)` 内从 1 开始连续，不是跨 LEFT/RIGHT 的全局序号。
二元标量的 LEFT 与 RIGHT 均为 1；`IN/NOT_IN/BETWEEN` 的非 Literal 一侧为 1，另一侧 Literal
按 1..N 连续；`IS_NULL/IS_NOT_NULL` 的唯一 Operand 为 1。这个语义与
`uk_rule_operand(node_id, operand_role, value_sequence)` 及既有 Decision Validator 保持一致。

本书面规格建议首期工程防护上限固定为每个快照最多 200 条 Rule、2000 个 Node、10000 个 Operand、
AST 深度 12，以及每个 `IN/NOT_IN` 最多 100 个 Literal。任一结构超限都在写事务前返回
`400 INVALID_REQUEST`。后继 Console 集成设计已将 Gateway/BFF 保存 Route 的 `body_limit_bytes` 固定为
65,536 UTF-8 Bytes 并以 413 拒绝；该入口控制仍不属于本后端切片，也不在这里用本地 Filter 伪造。

### 6.4 Create Rule Set 精确请求合同

`CreateRuleSetVersionRequestBodyV1` 只允许下列字段；每个列出的属性都必须出现，可空值必须显式传
JSON Null，集合必须显式传数组，任何未知属性都返回 `400 INVALID_REQUEST`：

| 对象 | 精确属性 |
|---|---|
| Root | `pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules` |
| Rule | `ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, conditionNodes, action` |
| Node | `nodeKey, parentNodeKey, sequenceNo, nodeType, groupOperator, comparisonOperator, operands` |
| Operand | `operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue` |
| Action | `actionCode, actionVersionNo` |

具体类型和空值矩阵固定为：

- Root 的 `pointCode/ruleSetCode/ruleSetName/changeReason` 是 String，`rules` 是非空 Rule Array；首次保存
  的两个 Base 字段均为 Null，后续保存分别是正十进制 String 和 64 位小写十六进制 String；
- Rule 的 Code/Name 是 String，`priorityNo` 是正 JSON Integer，`enabledFlag` 是 Boolean，`reasonCode`
  是 String 或 Null，`conditionNodes` 是非空 Node Array，`action` 是一个 Action Object；
- `nodeKey` 是本请求 Rule 内唯一的 1–64 位可见 ASCII String；Root 的 `parentNodeKey=null` 且
  `sequenceNo=1`，非 Root 的 Parent Key 必须命中同 Rule Node，`sequenceNo` 是正 JSON Integer；
- GROUP 使用 `groupOperator=AND/OR/NOT`、`comparisonOperator=null`、`operands=[]`；PREDICATE 使用
  `groupOperator=null`、一个受支持的 `comparisonOperator` 和非空 `operands`；
- Operand 的 Role 为 `LEFT/RIGHT`，Sequence 是正 JSON Integer，Source 只允许 `CONTEXT/LITERAL`。
  CONTEXT 必须填写白名单 `fieldCode` 且 `literalValue=null`；LITERAL 必须 `fieldCode=null` 并按
  `valueType` 提交一个 String/Boolean 标量；
- STRING Literal 是最长 512 的 JSON String，NUMBER 是第 5.3 节的十进制 JSON String，BOOLEAN 是
  JSON Boolean，DATETIME 是 UTC 微秒文本，DURATION 是无前导零的非负整数毫秒 String；
- Action 只提交 Authoring Context 返回的 `actionCode` 和正 JSON Integer `actionVersionNo`。固定
  `sequenceNo=1` 以及 Handler、Implementation、Decision、Merge、Shadow 属性全部由服务端目录解析，
  任何客户端同名字段都会作为未知属性拒绝。

DTO 解析后先执行第 6.2–6.3 节的完整结构和类型校验，再生成第 5.3 节的语义投影。API 原始数组顺序、
局部 Node Key 和 JSON 文本写法不是业务语义，不能直接对原始 Body 做 Hash。

## 7. HTTP API

### 7.1 通用 Header 和媒体类型

所有方法要求：

```text
X-Gateway-Actor-Subject: <positive decimal Account ID>
X-Gateway-Session-Id: <nonblank session ID>
```

Actor 必须是 MySQL Signed BIGINT 范围内的正十进制字符串；Session ID 必须是 1–128 位可见 ASCII，
禁止控制字符和首尾空白。

所有写方法额外要求：

```text
Idempotency-Key: <8..128 chars, [A-Za-z0-9._:-]>
```

查询和命令成功均返回 HTTP 200 与 `application/json`，便于首次执行和幂等重放保持同一 Wire 语义；
当前共享错误响应仍使用 Foundation JSON Error Body。`X-Request-Id`
由 Foundation 请求过滤器统一处理，不进入业务幂等。

这两个 `X-Gateway-*` Header 是为未来的 Operations BFF 调用准备的可信上下文，在本切片中不构成
独立身份认证或权限证明。

### 7.2 Rule Set API

Base：`/decision-rule-sets/v1`

| Method | Path | 语义 |
|---|---|---|
| `GET` | `/authoring-context/{pointCode}` | 一次返回当前可编辑 Point、九个 Field、Operator 和三个 Action |
| `GET` | `/rule-sets` | 按 `pointCode/ruleSetCode/status/page/size` 分页查询 Version |
| `GET` | `/rule-sets/{ruleSetVersionId}` | 返回完整可编辑投影和冻结证据 |
| `POST` | `/rule-sets` | 根据完整聚合创建下一个 `DRAFT` Version |
| `POST` | `/rule-sets/{ruleSetVersionId}/freeze` | 根据 `expectedContentHash` 冻结准确 Version |

`POST /rule-sets` 请求严格使用第 6.4 节的 `CreateRuleSetVersionRequestBodyV1`：

```text
pointCode
ruleSetCode
ruleSetName
baseRuleSetVersionId?
baseContentHash?
changeReason
rules[]
```

上面的 `?` 表示 JSON 值可为 Null，不表示属性可缺失。

创建成功返回第 7.4 节定义的稳定响应。相同幂等请求返回原响应投影，不新建 Version。

Freeze 请求只接受 `expectedContentHash`。服务端重载完整聚合和工程目录，不信任客户端重传内容。
成功后 Rule Set 和 Companion Point Version 在同一事务转为 `FROZEN`。

### 7.3 Decision Release API

Base：`/decision-releases/v1`

| Method | Path | 语义 |
|---|---|---|
| `GET` | `/candidates` | 按 `pointCode/status/page/size` 分页查询 TEST Candidate |
| `GET` | `/candidates/{releaseId}` | 返回 Candidate、冻结组成、状态审计和已有 Static Check |
| `POST` | `/candidates` | 使用一个冻结 Rule Set Version 创建 `TEST/DRAFT` Candidate |
| `POST` | `/candidates/{releaseId}/validate` | 使用 `expectedVersion` 执行八项静态检查 |

Candidate 创建请求只包含：

```text
ruleSetVersionId
expectedRuleSetContentHash
```

`environmentCode` 由服务端固定为 `TEST`；请求不提供环境、Point Version、Feature、Obligation 或多 Rule Set
组合字段。Candidate 准确引用该 Rule Set 的冻结 Companion Point Version。

Validate 请求 Body 只包含正整数 `expectedVersion`。

Validate 返回：

```text
releaseId/releaseNo/environmentCode/status/version/contentHash
allStaticChecksPassed
checks[8]: checkCode/checkStatus/severity/errorCode/errorSummary
```

`checks` 严格按八项固定顺序返回。`PASSED` 行为 `INFO + errorCode=null + errorSummary=null`；`FAILED`
行为 `ERROR` 且两个错误字段非空。`allStaticChecksPassed` 是查询时派生值，不是新状态或数据库字段。

Freeze、Candidate Create 和 Validate 是结构确定的机械命令，不接受自由文本 Reason。Candidate 初始、
校验开始和校验失败 State Change 分别使用稳定 `reason_code=CANDIDATE_CREATED`、
`STATIC_VALIDATION_STARTED`、`STATIC_VALIDATION_FAILED`；Freeze 由 Rule Set 的 `frozen_by/frozen_time` 和
原 Version `change_reason` 解释。本切片不伪造人工审批意见。

### 7.4 命令响应和查询排序

四个命令响应使用以下版本化 Wire Schema；字段集合不允许随实现临时增减，所有 ID 继续使用十进制
String，时间使用第 5.3 节的 UTC 微秒文本：

| Response Schema | 精确字段 |
|---|---|
| `RISK_CREATE_RULE_SET_VERSION_RESPONSE_V1` | `ruleSetVersionId/pointVersionId/ruleSetCode/versionNo/status/contentHash/createdBy/createTime` |
| `RISK_FREEZE_RULE_SET_VERSION_RESPONSE_V1` | `ruleSetVersionId/pointVersionId/ruleSetCode/versionNo/status/contentHash/frozenBy/frozenTime` |
| `RISK_CREATE_TEST_RELEASE_RESPONSE_V1` | `releaseId/releaseNo/decisionPointId/pointCode/pointVersionId/ruleSetVersionId/environmentCode/status/version/contentHash/createdBy/createTime/initialStateChange` |
| `RISK_VALIDATE_TEST_RELEASE_RESPONSE_V1` | `releaseId/releaseNo/environmentCode/status/version/contentHash/allStaticChecksPassed/checks` |

Create Rule 的 `status=DRAFT`。Freeze 的 `status=FROZEN`。Create Candidate 的
`environmentCode=TEST/status=DRAFT/version=1`；其 `initialStateChange` 恰好包含
`auditEventId/eventTime/fromStatus/toStatus/reasonCode/triggerType/actorId/aggregateVersion`，其中
`fromStatus=null/toStatus=DRAFT/reasonCode=CANDIDATE_CREATED/triggerType=OPERATIONS_COMMAND/aggregateVersion=1`。
Validate 的 `checks` 形状和顺序按第 7.3、9.3 节；全部通过时返回
`status=VALIDATING/version=2`，任一失败时返回 `status=REJECTED/version=3`。这些常量和不可变事实使
`response_ref` 可以在 Root 后续演进后仍重建首次成功响应。

两个列表复用 Foundation `PagingRequestBody` 和 `PagingResponseBody<T>`，Wire 固定为
`items/page/size/total`：缺失值只由 `PagingRequestBody.of` 补 `page=1/size=10`，显式 Page 为
1–100000，Size 只允许 10/20/50/100/200。Rule Set 按
`create_time DESC, rule_set_version_id DESC`，Candidate 按 `create_time DESC, release_id DESC`，相同过滤
条件在数据库分别执行 `LIMIT/OFFSET` 和 `COUNT`，不自建分页 DTO 或内存分页。

五个 GET 的 typed Wire Schema 固定如下；公共规则仍是所有 ID 用十进制 String、时间用 UTC 微秒文本、
可空属性显式返回 JSON Null：

1. `RISK_RULE_AUTHORING_CONTEXT_RESPONSE_V1` 恰好包含
   `descriptorCode/point/fields/operators/actions/features/actionParameters/obligations/limitBindings`。
   `point` 包含 `pointCode/pointName/businessDomain/inputContractCode/inputContractVersion` 和
   `outputContractCode/defaultDecision/evaluationStrategy/failureStrategy/timeoutMillis/status`；Field 包含
   `fieldCode/valueType/requiredFlag/dataClassification/ruleReferenceFlag/actionParameterFlag`；Action 包含
   `actionCode/actionName/actionCategory/versionNo/handlerCode/implementationVersion/implementationHash` 和
   `terminalFlag/decisionCode/mergePolicy/shadowSupportedFlag/status`。末四个集合在本模板显式为空。
2. `RISK_RULE_SET_LIST_ITEM_V1` 恰好包含
   `ruleSetVersionId/pointVersionId/pointCode/ruleSetCode/ruleSetName/versionNo/status/contentHash` 和
   `createdBy/changeReason/createTime/frozenBy/frozenTime`，由 `PagingResponseBody` 承载。
3. `RISK_RULE_SET_DETAIL_RESPONSE_V1` 包含上一项全部字段和 `rules`；Rule/Node/Operand/Action 形状与第
   6.4 节相同。后端按 Rule 的 `priorityNo,ruleCode`、Node 树前序（Sibling `sequenceNo,nodeId`）、Operand
   Role 与 `valueSequence` 返回，并确定性令 `nodeKey=<node_id 十进制>`、
   `parentNodeKey=<parent_node_id 十进制或 null>`。这些键只供该响应的编辑树关联；再次提交时仍分配新 ID。
   Detail 不另返历史 Base；只有当该 Detail 仍为最新 Version 时，调用方才把它自身的
   `ruleSetVersionId/contentHash` 放入下一次请求的 `baseRuleSetVersionId/baseContentHash`。以历史内容为
   编辑素材时，必须另取当前最新 Version 作为 Base，服务端仍在锁后复验。
4. `RISK_TEST_RELEASE_LIST_ITEM_V1` 恰好包含
   `releaseId/releaseNo/decisionPointId/pointCode/pointVersionId/ruleSetVersionId/environmentCode` 和
   `status/version/contentHash/createdBy/createTime/allStaticChecksPassed`，由 `PagingResponseBody` 承载。
   `allStaticChecksPassed` 仅在恰好八条检查完成时为 Boolean，否则为 Null。
5. `RISK_TEST_RELEASE_DETAIL_RESPONSE_V1` 包含上一项全部字段以及
   `waitingForShadow/frozenComposition/stateChanges/staticChecks`。`waitingForShadow` 仅在第 9.3 节定义的
   全通过状态为 true；`frozenComposition` 恰好包含
   `decisionPointId/pointCode/pointVersionId/pointVersionNo/pointVersionContentHash` 和
   `ruleSetVersionId/ruleSetCode/ruleSetVersionNo/ruleSetContentHash`；State Change 包含
   `auditEventId/eventTime/fromStatus/toStatus/reasonCode/triggerType/triggerId/actorId/aggregateVersion`；Static
   Check 包含 `auditEventId/eventTime/checkCode/checkStatus/severity/errorCode/errorSummary`。

Authoring Field 按 `fieldCode`、Action 按 `actionCode,versionNo`，Operator 使用第 6.3 节固定顺序；State
Change 按 `aggregateVersion,auditEventId`，Static Check 按第 9.3 节固定顺序。若发现重复或未知 Check，
详情查询失败关闭，不任意挑一条展示。

## 8. 幂等、并发与锁

### 8.1 幂等命名空间

`command_idempotency` 唯一键语义为：

```text
caller_scope = OPERATIONS_ACCOUNT:<actorAccountId>
operation_type
idempotency_key
```

`idempotency_key` 按调用方原始 ASCII Bytes 做大小写敏感比较；`Key-1234` 与 `key-1234` 是两个不同
命令身份，不允许由表级的大小写不敏感排序规则折叠。物理字段使用 `ascii/ascii_bin`，服务不改写大小写。

`operation_type` 固定为：

- `CREATE_RULE_SET_VERSION`
- `FREEZE_RULE_SET_VERSION`
- `CREATE_TEST_DECISION_RELEASE`
- `VALIDATE_TEST_DECISION_RELEASE`

请求 Hash 覆盖 Path 业务身份、规范化 Body 和所有会影响业务或审计结果的字段，但不覆盖
Request ID、Session ID 或传输时间。

处理顺序固定为：

1. 验证 Header 和基本请求格式；
2. 计算 Canonical Request Hash；
3. 查询幂等命名空间；任意状态下同键异 Hash 都返回 409；
4. 同键同 Hash 且 `SUCCEEDED` 时，按稳定 `response_ref` 重建原命令结果；
5. 同键同 Hash 且 `PROCESSING` 时，只进入第 9.3 节的 Validate 恢复分支；
6. 只对首次命令检查当前状态、基准 Hash 或 Release Version；
7. 在对应本地事务中写业务事实和稳定 `response_ref`。

这保证成功命令的重放不会因资源后来已冻结或进入其他状态而被误报为冲突。

`response_ref` 保存操作类型和稳定业务 ID，不保存完整 JSON。重放按不可变事实重建原操作投影：Create Rule
固定返回创建时 `DRAFT`，Freeze 固定返回该 Version 的冻结证据，Create Candidate 固定返回 Version 1 的
初始 State Change，Validate 从八条 Check 和相应 State Change 重建完成时的 Version/Status；不能直接返回
Root 的后来当前状态冒充原响应。

本切片除跨事务 Validate 外不会提交可见的 `PROCESSING`，也不会主动持久化 `FAILED`；观察到 `FAILED`
或不符合第 9.3 节恢复矩阵的状态一律作为不一致失败关闭。`expire_time` 固定为首次接收时间加 24 小时，
只表示未来保留策略允许清理的最早时间；MVP 没有自动清理，现存记录即使过期仍参与重放/冲突判断。
任何删除或 Key 复用规则必须等待独立 Retention 设计。

### 8.2 并发保护

锁定真实资源，不锁 Tenant 或操作人：

```text
command idempotency row
  -> Decision Point root
    -> (decision_point_id, rule_set_code) indexed range / current Rule Set Version
      -> companion Point Version
        -> Decision Release root when present
```

- 每个命令只取得需要的子集，但相对顺序不可反转；
- 创建快照先锁 `decision_point` Root，在该锁内为全部 Rule Set Code 共享的 Point Version 序列分配
  `version_no`，避免不同 Code 并发撞击 `(decision_point_id, version_no)`；
- 首次 Rule Set 创建同时使用 Rule Set 唯一业务版本索引解决空集合并发；
- 后续保存在锁后重新验证最新 ID、Hash 和 Status；
- Freeze 以 `rule_set_version_id + status=DRAFT + content_hash` 条件更新；
- Candidate 状态以 `release_id + expectedVersion + expectedStatus` 条件更新；
- 唯一索引冲突只在证明命中准确业务索引时转为 `409 CONCURRENT_REQUEST`；
- 不增加 Redis Lock、JVM 全局锁或 Tenant 大锁。

### 8.3 多 SQL 只读一致性

Authoring Context、Rule Set List/Detail、Release List/Detail 五条读路径都由多次 SQL 共同组成一个返回
投影，必须分别在短 `read-only REPEATABLE_READ` 事务中完成，以同一 MVCC 快照读取 Root、Count、
分页项和闭包。只读路径不使用 `FOR UPDATE` 或其他读锁，也不进入写事务；这防止并发 Freeze/Validate
在两次查询之间提交时拼出不存在的混合状态。单次 HTTP 查询完成后立即结束快照。

## 9. Freeze 与 TEST Candidate

### 9.1 Freeze

Freeze 必须在一个本地事务内：

1. 命中或占用幂等命令；
2. 锁定 Rule Set 和 Companion Point Version；
3. 证明两者仍为当前 DRAFT 且 Hash 匹配；
4. 重载 Rule、Node、Operand、Action 和全部工程目录闭包；
5. 重算 Rule Set 和 Point Version Hash；
6. 验证目录仍为 ACTIVE，且 Action 注册版本/Hash 唯一匹配；
7. 同时把两个 Root 转为 `FROZEN`；只在 Rule Set 写入模型已有的 `frozen_by/frozen_time`，Point Version
   以同事务状态、Content Hash 和 Binding 追溯冻结证据；
8. 完成幂等响应引用。

任一失败都不得留下部分冻结结果。

### 9.2 Candidate 创建

创建命令只接受一个 `FROZEN` Rule Set Version。后端校验：

- Point 恰好为 `CHECKOUT_PREPARE`；
- Companion Point Version 为 `FROZEN`；
- Point 恰好绑定该 Rule Set；
- Rule Set Hash 与请求相同；
- 不存在 Feature、Parameter 或 Obligation；由于零 Obligation，可达 Limit Binding 闭包结构性为空；
- 环境由服务端固定为 `TEST`。

本首期把 `release_no` 冻结为 `1..2147483647` 的规范正十进制整数：数据库保存无前导零 String，API
返回同值 JSON Integer。Candidate 创建在幂等行之后锁定唯一 `CHECKOUT_PREPARE` Decision Point Root，
先证明现有 Release No 均为该规范，再在同一 Root 锁保护下以全表最大值加一分配；空表从 1 开始，溢出或
发现非规范值时失败关闭。禁止使用未锁定的 `MAX`、行数、随机数或截断 Snowflake 作为编号。

这个 Root 只在当前“单一可编辑 Decision Point、Risk 服务为唯一 Release 写入方”的首期内充当编号分配资源。
任何第二个可创建 Candidate 的 Point、另一写入方或超出 Integer 范围的需求，都必须先批准显式全局序列
资源及相应 API/数据库变更，不能复用本临时约束静默扩展。

同一事务创建 `decision_release(status=DRAFT, active_slot=NULL, version=1)` 和首条
`STATE_CHANGE(NULL -> DRAFT, aggregate_version=1)`。Candidate 的 Release No、Point、Rule Set、Environment 和
Content Hash 从此不变。

### 9.3 八项静态检查

检查集和返回顺序恰好为：

1. `REFERENCE_INTEGRITY`
2. `AST_SHAPE`
3. `AST_TYPES`
4. `FEATURE_DEPENDENCY`
5. `HANDLER_HASH`
6. `ACTION_PARAMETERS`
7. `TERMINAL_ACTION`
8. `SYSTEM_OBLIGATION`

每项必须生成恰好一条 `STATIC_CHECK` Audit Event，不得首错即停。通过行使用 `PASSED/INFO`；
失败行使用 `FAILED/ERROR`，并携带稳定 `error_code` 和脱敏 `error_summary`。

一个 Check 可以发现多个违规，但单行模型只保存一个确定性摘要。Validator 先收集该 Check 的全部违规，
把规范化五元组 `errorCode/path/ruleCode/nodeId/actionCode` 作为去重身份，再按相同字段顺序升序选首项。
所有 String 先 NFC，再按无符号 UTF-8 Bytes 字典序比较；`nodeId` 按正 BIGINT 数值比较；每一列均为
非 Null 在前、Null 在后，前列不同时不比较后列。渲染摘要时 Null Path 使用 `-`，但该替代值不参与
排序或去重。持久化首项的 `errorCode`，`error_summary` 只写
`first=<code>@<safe-path>; violation_count=<n>`，最长 1000 字符且不含 Literal。Java 和 JavaScript
Validator 必须共享能区分 Node ID `2/10`、Null、下划线和点号 Path 的黄金向量；现有 JavaScript
`localeCompare` 排序必须迁移，不能作为合同。API 只返回这八条可重取事实，不临时返回无法持久重建的
诊断明细。未来如需逐节点全部错误，另行设计诊断明细结构。

本切片的高层 Validate 命令使用两个可恢复的短事务：

1. 事务 A：按固定顺序锁幂等行和 `DRAFT` Candidate，写入 `PROCESSING + response_ref=release:<id>`，
   CAS 为 `VALIDATING/version=2`，追加 `DRAFT -> VALIDATING` State Change 后提交。
2. 短只读事务：使用 `read-only REPEATABLE_READ`、不加读锁地装载不可变冻结闭包，提交并释放快照。
3. 事务外：对已物化闭包执行无外部 I/O 的纯静态检查。
4. 事务 B：再次先锁幂等行、再锁 Candidate。若已 `SUCCEEDED`，直接重建原结果；只有
   `PROCESSING + VALIDATING/version=2 + zero checks` 才一次写入八条结果。若全过，Root 保持
   `VALIDATING/version=2`；若任一失败，同事务追加 `VALIDATING -> REJECTED/version=3` 并更新 Root。
   最后把同一幂等命令标记为 `SUCCEEDED`。

如果进程在 A 和 B 之间中断，Candidate 安全地保持在“VALIDATING 但尚无八条结果”。调用方必须
用原 Idempotency-Key 重试；后端短暂重锁并确认同 Hash `PROCESSING + VALIDATING/version=2 + zero checks`
后不改状态地释放锁，重新执行步骤 2 的一致性闭包装载和步骤 3 的八项纯校验，再进入事务 B。
原请求和同键重试同时到达事务 B 时由幂等行锁串行：先完成者写八行，后到者观察 `SUCCEEDED` 并重放。
观察到 1–7 条 Check、八条 Check 但幂等仍为 `PROCESSING`、错误 Candidate 状态/Version 或 `FAILED`
均属于不可达损坏，返回 500 并失败关闭，不尝试猜测修复。不使用后台任务、MQ 或额外恢复表。

全部通过后不进入 `SHADOWING`。详情查询只通过 `status=VALIDATING` 且 Checks 恰好八条、全部
`PASSED` 派生“等待 Shadow”。不新增伪状态或指针字段；后续独立 Shadow 命令必须重验八条事实后，
才可按长期状态图执行 `VALIDATING -> SHADOWING`。

同键恢复是本次已确认的后端首期取舍：若调用方永久丢失 Key，旧 Candidate 可能保持不可激活但可查询的
`VALIDATING + zero checks`，调用方仍可基于同一冻结 Rule Set 创建新 Candidate。后继 Operations Console
集成设计已批准由 Web 按 `risk-validate-v1-{releaseId}-{expectedVersion}` 确定性重建原 Key，并结合原
`DRAFT -> VALIDATING` State Change Actor 判断是否展示恢复动作；BFF 必须根据 Path 与 Body 独立重建并
精确校验该 Key，不一致时不得调用 Risk。该接入尚未实现和验证前，本写能力仍不得部署到共享或生产环境。

## 10. 数据库和持久化范围

### 10.1 完整 Schema DDL

Risk 第一次引入数据库实现时，必须交付一份与当前批准 PDManer 一致的完整 Schema DDL，
不得只创建本切片的少数表。完整 DDL 仍不表示 71 表业务 Java 全部实现。

DDL 必须：

- 匹配 PDManer 字段顺序、类型、精度、可空性、默认值、主键和索引；
- 保留完整表/列中文注释；
- 使用 `t_` 物理表名、`uk_` 唯一索引和 `idx_` 访问索引；
- 不创建物理 `CHECK` 或 `FOREIGN KEY`；
- 不引入 Flyway、Liquibase、Testcontainers 或应用启动 Schema Initialization。

初始化事实放在唯一 `risk-compliance-initial-data.sql`，使用准确、可重复审查的 ID、时间、Code、
Version 和 Hash。应用运行期不得创建或改写工程目录。

### 10.2 Java 直接使用的表

`risk-decision` 拥有并直接写入/查询以下 12 张 Decision 表：

- `decision_point`
- `decision_point_version`
- `decision_point_input_field`
- `decision_point_rule_set`
- `rule_set_version`
- `risk_rule`
- `rule_node`
- `rule_operand`
- `action_version`
- `rule_action`
- `decision_release`
- `release_audit_event`

为了让八项检查证明“零 Feature/零 Parameter/零 Obligation”而不是漏读，`risk-decision` 的只读闭包
还必须覆盖以下 6 张同 Owner 表：

- `decision_point_feature`
- `feature_version`
- `feature_dependency`
- `action_parameter_definition`
- `rule_action_parameter`
- `decision_point_obligation`

`command_idempotency` 是第 13 张运行期表，但其 DataObject、Mapper、Repository 和共享幂等 Service 归
`risk-web` 服务级基础设施；Processor 只调用该 Service，不直接访问 Mapper。它和 Decision 写入参加同一个
DataSource/`TransactionTemplate`，不另开事务。

`decision_point_limit_binding` 仍归 `risk-control`，本切片不跨 Owner 查询或映射它。当前 Point Version
恰好零条 Obligation，因此它的可达 Limit Binding 闭包按结构必为空；Java Release 投影显式提供空集合。
这不承担全库孤儿行扫描，也不改变长期规则：未来任何非空 Limit/Binding Release 必须通过
`risk-control` 的公开闭包能力校验，不能由 Decision 直接读 Control 表。

`release_shadow_run` 和全部评估/执行表本切片不做 Java 映射。

DataObject 留在上述 Owner JAR，类名不加 `DO`，每个字段使用中文 JavaDoc。Decision Mapper SQL 放在
`risk-decision/src/main/resources`，幂等 Mapper SQL 放在 `risk-web/src/main/resources`；Java Mapper 只保留
签名和绑定参数。数据库 Code 和 UTC `DATETIME(6)` 分别使用已批准的 Foundation MyBatis TypeHandler。

### 10.3 Maven 边界

当前 Risk 根 POM 使用 Enforcer 禁止 JDBC/MyBatis/MySQL/HikariCP，用于保证初始骨架没有偷渡数据库。
实施时只移除 `spring-jdbc`、`org.mybatis:*`、`org.mybatis.spring.boot:*`、`com.mysql:*`、`HikariCP`
五条已被本设计批准的 Exclude Pattern，并通过项目 Parent/BOM 按以下位置引入既定依赖：

- `risk-decision`：`paytech-mybatis-support`、MyBatis API、`spring-context`、`spring-tx`；
- `risk-web`：`spring-boot-starter-jdbc`、`mybatis-spring-boot-starter`、Runtime MySQL Connector；
- `risk-test`：依赖 `risk-web`，Test Scope 使用 `spring-jdbc`、Spring Boot Test、Spring Test 和 JUnit 5；
- `risk-api/risk-control/risk-compliance`：本切片不增加 JDBC/MyBatis/MySQL 依赖。

对 Flyway、Liquibase、Testcontainers、Mockito、Nacos、Redis、RabbitMQ、XXL-Job、OpenFeign、Dubbo 的禁令继续保留。

## 11. 错误合同

本 API 复用 `paytech-mvc-web-starter` 的统一 Code-MoR Problem：

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "detail": "规则集已产生新版本，请刷新后重试",
  "code": "CONCURRENT_REQUEST",
  "requestId": "request-id",
  "timestamp": "2026-09-06T10:20:30.123456Z"
}
```

状态和 Code：

| HTTP | Code | 语义 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | Header、字段、Code、集合上限或 DTO 形状非法 |
| 400 | `BUSINESS_CHECK_FAILED` | AST、目录、冻结或状态业务约束不满足 |
| 404 | `RISK_DECISION_CONFIGURATION_NOT_FOUND` | Point、Rule Set Version 或 Candidate 不存在 |
| 409 | `CONCURRENT_REQUEST` | 同幂等键异内容、过期 Base/Hash/Version 或唯一索引业务冲突 |
| 500 | `INTERNAL_ERROR` | 未预期系统错误 |
| 503 | `SERVICE_UNAVAILABLE` | 明确的必需依赖不可用；本切片没有远程依赖 |

`RISK_DECISION_CONFIGURATION_NOT_FOUND` 是本切片唯一新增的业务错误 Code，通过 Foundation
`ApiErrorStatusResolver` 映射 404。其余情况使用公共异常，不为每个校验原因创建异常类。

Candidate 静态检查失败是已完成的业务结果，返回 `200 + status=REJECTED + checks[8]`，不返回 4xx/5xx。
错误 Wire Contract、Request ID、时间戳、`HEAD` 与可选字段错误规则由系统级 Problem Profile 统一拥有，Risk 不创建本地 Writer 或错误 DTO。

## 12. 审计、日志和安全

### 12.1 不可变证据

- Rule Set 每次成功保存产生新的不可变 Version，并冻结 `created_by/change_reason`；
- Freeze 冻结 `frozen_by/frozen_time`；
- Candidate 根保存 `created_by/content_hash/version`；
- `release_audit_event` 只追加，不更新、不删除；
- Static Check、Approval 和 State Change 行字段矩阵严格互斥；本切片不产生 Approval 行；
- State Change `aggregate_version` 从 1 连续递增；
- State Change 使用 `trigger_type=OPERATIONS_COMMAND` 和由幂等键派生的不透明 Trigger ID，不复制原 Key。

成功配置行证明业务内容以及 Rule Set 的当前/冻结状态；Rule Set 状态迁移仅能通过版本、关联和当前状态
重建，本切片不把它宣称为完整的只追加审计链。Candidate 的 `release_audit_event` 则提供完整的只追加状态
迁移证据。这些证据只能把 Header 中的 Actor 保存为“调用方声明关联”，不能证明真实 Operations 人员身份。
被拒绝且未产生业务版本的尝试只保留 Request ID Metadata Log。
失败尝试若需要持久审计，必须另行批准 Risk-local Security/Command Audit 或明确的跨系统摄取合同；
不能把它默认归给 Platform，也不为填补该后续边界向 Risk 增加通用 Operations Audit 表。

### 12.2 日志与数据分类

规则 AST、Literal、阈值和 Handler 组成是机密风控策略，即使它们不含 PII 也不是公开数据。

- 两个管理 API Base Path 加入共享 MVC 日志 `metadata-only` 配置；
- 普通日志只记录 Request ID、Actor、Resource ID、Status、Version、Hash 和耗时；
- 不新建本地 Body Logging Filter；
- `error_summary` 最长 1000，只保存节点路径、稳定 Code 和脱敏概要；
- 请求、响应、日志和审计中禁止 PAN、CVV/CVC、完整磁道、PIN/PIN Block、Vault Token、
  Consumer 原值、网络原值、密钥、Secret 或完整请求 Payload。

### 12.3 暴露限制

Risk 服务继续绑定私网/Loopback，不新增 Gateway 目标或公开 Route。未来浏览器路径必须是：

```text
Operations Web
  -> API Gateway
    -> operations-console-bff
      -> Risk private management API
```

Gateway 不得把 Risk 核心服务直接暴露给浏览器。本切片的 `X-Gateway-*` 格式校验不得被宣称为
Operations 身份认证、Session 校验、PLT-I02 授权或强服务身份。

未来接入仍必须遵守以下边界，但不属于本期实现：

- 每个浏览器 Route 先由 BFF 执行 PLT-I02；读、保存、冻结、创建 Candidate、Validate 分配所需的独立
  Permission 并分别绑定对应 Route，不能用一个宽泛 `MANAGE` 覆盖；
- MENU/BUTTON 仅按 UI 需要独立引用相同 Permission，与 Route 不直接绑定；前端显隐不参与授权判断；
  BFF 只转交已经验证的 Actor/Session 上下文，并具备受限服务调用身份；
- 每个 BFF 写操作只转发一个 Risk-owned Command，不在 BFF 复制 Rule/Release 状态机或跨系统事务；
- Risk 继续拥有内容校验、幂等、并发和状态不变量。

## 13. 测试与验收

### 13.1 测试环境

自动化测试要求开发者手工启动仓库 `local-environment` MySQL，手工应用当前 Risk Schema DDL 和唯一
初始化 DML，并使用 `mor_risk/risk_app` 专属范围。

`auto` 测试：

- 使用随机 Loopback HTTP 端口；
- 不连接 Nacos、Gateway、BFF、Platform、其他核心服务或共享业务环境；
- 不使用 H2、Flyway、Liquibase、Testcontainers、Mockito、`MockBean`、Spy 或方法级 Stub；
- MySQL、凭据、Schema 或初始目录不可用时快速失败，并输出准确准备说明；
- 测试结束只清理当前 Fixture 的受控 ID，不清空整库或重建容器。

### 13.2 必测行为

API 和真实 MySQL 链路必须覆盖：

1. Authoring Context 恰好返回九 Field、三 Action、零 Feature/Parameter/Obligation 和结构性空 Limit Binding。
2. 第一个 DRAFT 一次原子写入，多表重建后与规范请求一致。
3. 三种 Content Hash 和四种 Request Hash 都通过版本化跨语言黄金向量；请求数组到达顺序变化但显式业务
   顺序相同时 Hash 相同，Priority/Sequence/Literal 顺序变化时 Hash 不同。
4. 同幂等键同 Hash 返回原 ID，异 Hash 返回 409 且不增加行。
5. 新快照原子退役前 DRAFT；同/不同 Rule Set Code 并发保存都不产生重复 Point Version，竞争同一下一
   Rule Set Version 时只有一个成功。
6. 非白名单 Context、FEATURE Operand、Action Parameter、非终态 Action、表达式/类名/URL 被拒绝且整笔回滚。
7. Root 数量、连通性、无环、Group 元数、Operand 元数、类型、操作符和安全上限全部有反例。
8. Freeze 重载全闭包、重算 Hash、原子冻结两个 Root，冻结后无子行写入通道。
9. 过期 Hash、不活跃 Action、Handler Hash 不匹配和并发 Freeze 安全失败。
10. Candidate 只接受 `CHECKOUT_PREPARE + FROZEN + TEST + one Rule Set`。
11. Candidate 创建后组成不可变，初始 Root 和 State Change 为 Version 1。
12. Validate 恰好写入固定顺序的八条事件；同一 Check 多违规按固定排序压缩为首错加计数，同幂等重放
    不重复事件。
13. 全过后仍为 `VALIDATING/version=2`，且没有 Shadow Run、Approval、Evaluation 或 ACTIVE Release。
14. 任一失败后为 `REJECTED/version=3`，八条结果和失败 State Change 原子存在。
15. 在事务 A 后故障时，用原 Key 可以完成事务 B；原请求与同键重试竞争只形成一条合法状态链；
    `SUCCEEDED` 重放、`PROCESSING + zero checks` 恢复及部分 Check/错误状态损坏失败关闭均有反例。
16. 未知数据库 Code、UTC `DATETIME(6)`、唯一索引、事务回滚和旧状态 CAS 在真实 MySQL 上验证。
17. 缺失 Actor/Session/Idempotency Header、超出结构上限和未知资源使用共享错误体。
18. 日志和 Audit Event 不包含完整 AST、Literal、凭据或不允许数据；伪造格式合法 Actor 也只能形成
    未认证关联，测试和文档不把它宣称为真实人员身份。
19. 五个 GET 严格匹配第 7.4 节的字段、Null 和排序合同；两个列表复用公共 1-based 分页的默认值、
    Size 白名单、稳定 Count，Rule Detail 的 Node Key 可确定性重建并可作为新请求局部键往返。

纯 Java 校验还必须使用当前 JavaScript Decision Engine Validator 的精选黄金向量做跨实现对照，防止
设计期和运行时对同一 AST/Release 得出不同结果。

### 13.3 验证命令类型

实施完成前至少运行：

- Risk PDManer 生成器与全部 Risk 模型/契约/场景测试；
- Schema DDL 静态一致性检查与人工 `SHOW CREATE TABLE/information_schema` 核对；
- `./mvnw clean verify` 的 Risk 全 Reactor；
- `npm --prefix requirements-site test`；
- 涉及的格式、依赖和架构边界检查。

只报告实际运行的命令、对应环境和结果。

## 14. 文档与需求治理

实施计划必须在业务代码前先同步文档权威：

1. 新增窄决策记录，准确声明本设计对最小 `CHECKOUT_PREPARE` Template、`DRAFT -> RETIRED`、
   Validate 停留 `VALIDATING`、Hash Schema 和 `rule_set_version` 两个审计字段的作用域；未命中的长期
   Decision Engine/Shadow 规则继续有效。
2. 在 `requirements-site/content/requirements.ts` 新增 RCO-I12：“管理决策规则集与 TEST Candidate”。
   它是现有 RCO-12 的 Risk 私有后端子切片，不包含 Shadow、双审或激活。后继 Operations Console 集成
   设计现已批准浏览器/BFF/权限链目标合同；实现和部署状态仍只能由 `CURRENT_IMPLEMENTATION.md` 声明。
3. 本后端切片当时没有已批准的浏览器/BFF 交互步骤，因此未修改全局用例时序。后继 Operations Console
   集成设计现已新增对应 Sequence；该目标态序列不证明相关组件已实现或部署。
4. 数据库语义改动同步 Risk 数据库权威、现实场景、物理决策、Manifest、Policy、PDManer 和文档路由，
   并明确九个字段只是规则引用投影；RCO-I03 完整运行输入不被收窄。
5. 设计和实施计划不写入“已实现”。只在相应实现/验证真正完成后更新
   `CURRENT_IMPLEMENTATION.md` 和 Risk README。

## 15. 分期顺序和完成定义

实施顺序固定为：

1. 文档和跨切数据库模型对齐；
2. 完整 Risk Schema DDL 和唯一初始化 DML；
3. Maven 持久化基础、TypeHandler 和真实 MySQL 测试基座；
4. Authoring Context 和类型化 AST/Hash 纯领域能力；
5. 完整不可变 DRAFT 快照与冻结；
6. TEST Candidate、审计流和八项静态检查；
7. 并发、恢复、日志安全、全 Reactor 和需求站门禁；
8. 只在证据齐全后更新当前实现状态。

本设计的 Definition of Done 是：

- Risk 内部两个 typed API 在 Loopback 可用；
- 完整规则聚合、冻结、TEST Candidate 和八项检查使用真实 MySQL 链路通过；
- 合同、PDManer、完整 DDL/DML、Java 映射、黄金向量和测试一致；
- 幂等、并发、中断恢复和安全日志有可重复证据；
- 当前状态台账只声称实际完成和验证的 Risk 后端首期。

即使 Definition of Done 全部满足，仍不得声称 Operations 页面/权限链、Shadow、双审、激活、
PRODUCTION Release、真实 Checkout 风控执行、完整 Decision Engine、完整 71 表 Risk 业务或任何部署已完成。
